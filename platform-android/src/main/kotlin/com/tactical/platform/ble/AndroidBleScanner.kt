package com.tactical.platform.ble

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.tactical.platform.api.ble.BleBeaconPayloadCodec
import com.tactical.platform.api.ble.BleBeaconScanner
import com.tactical.platform.api.ble.ScannedBleDevice
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class AndroidBleScanner(
    private val context: Context
) : BleBeaconScanner {

    private val bluetoothManager: BluetoothManager by lazy {
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    }

    private val bluetoothAdapter: BluetoothAdapter?
        get() = bluetoothManager.adapter

    private val scanner: BluetoothLeScanner?
        get() = bluetoothAdapter?.bluetoothLeScanner

    override fun scan(): Flow<ScannedBleDevice> = callbackFlow {
        val requiredPermission = if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        ) {
            Manifest.permission.BLUETOOTH_SCAN
        } else {
            Manifest.permission.ACCESS_FINE_LOCATION
        }

        if (
            context.checkSelfPermission(requiredPermission) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            close(SecurityException("Missing $requiredPermission"))
            return@callbackFlow
        }

        val adapter = bluetoothAdapter

        if (adapter == null || !adapter.isEnabled) {
            android.util.Log.d(
                TAG,
                "BLE scan skipped: Bluetooth is OFF or unavailable"
            )
            close()
            return@callbackFlow
        }

        val le = scanner

        if (le == null) {
            android.util.Log.d(TAG, "BLE scanner unavailable")
            close()
            return@callbackFlow
        }

        /*
         * Do not rely on a hardware manufacturer-data filter here.
         * Some OEM BLE stacks can register the scanner successfully but
         * silently return no results for partial/custom manufacturer filters.
         * We filter by the iTantra manufacturer ID + magic bytes in software.
         */
        val callback = object : ScanCallback() {

            override fun onScanResult(
                callbackType: Int,
                result: ScanResult
            ) {
                val manufacturerData =
                    result.scanRecord?.manufacturerSpecificData
                        ?: return

                for (index in 0 until manufacturerData.size()) {
                    val manufacturerId = manufacturerData.keyAt(index)

                    if (manufacturerId != MANUFACTURER_ID) {
                        continue
                    }

                    val payload = manufacturerData.valueAt(index)
                        ?: continue

                    val packet = BleBeaconPayloadCodec.decode(payload)
                        ?: continue

                    BlePeerAddressRegistry.remember(
                        packet.sender.value,
                        result.device.address
                    )

                    android.util.Log.d(
                        TAG,
                        "iTantra beacon detected from " +
                            packet.callsign +
                            " (" + result.device.address + ")"
                    )

                    trySend(
                        ScannedBleDevice(
                            deviceId = result.device.address,
                            rssi = result.rssi,
                            advertisementPayload = payload
                        )
                    )
                }
            }

            override fun onBatchScanResults(
                results: MutableList<ScanResult>
            ) {
                results.forEach { result ->
                    val manufacturerData =
                        result.scanRecord?.manufacturerSpecificData
                            ?: return@forEach

                    for (index in 0 until manufacturerData.size()) {
                        val manufacturerId = manufacturerData.keyAt(index)

                        if (manufacturerId != MANUFACTURER_ID) {
                            continue
                        }

                        val payload = manufacturerData.valueAt(index)
                            ?: continue

                        val packet = BleBeaconPayloadCodec.decode(payload)
                            ?: continue

                        BlePeerAddressRegistry.remember(
                            packet.sender.value,
                            result.device.address
                        )

                        android.util.Log.d(
                            TAG,
                            "iTantra beacon detected from " +
                                packet.callsign +
                                " (" + result.device.address + ")"
                        )

                        trySend(
                            ScannedBleDevice(
                                deviceId = result.device.address,
                                rssi = result.rssi,
                                advertisementPayload = payload
                            )
                        )
                    }
                }
            }

            override fun onScanFailed(errorCode: Int) {
                android.util.Log.w(
                    TAG,
                    "BLE scan failed, errorCode=$errorCode"
                )

                close(
                    IllegalStateException(
                        "BLE scan failed, errorCode=$errorCode"
                    )
                )
            }
        }

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setLegacy(true)
            .setReportDelay(0L)
            .build()

        try {
            /*
             * Use an unfiltered scan for OEM compatibility. The application
             * beacon is still filtered strictly in the callback using the
             * manufacturer ID and BleBeaconPayloadCodec magic bytes.
             */
            le.startScan(
                null,
                settings,
                callback
            )

            android.util.Log.d(
                TAG,
                "BLE scan started (software beacon filtering)"
            )
        } catch (e: SecurityException) {
            close(
                SecurityException(
                    "Bluetooth scan permission is not granted",
                    e
                )
            )
            return@callbackFlow
        } catch (e: Exception) {
            close(
                IllegalStateException(
                    "Unable to start BLE scan",
                    e
                )
            )
            return@callbackFlow
        }

        awaitClose {
            try {
                le.stopScan(callback)
            } catch (_: SecurityException) {
                // Permission was revoked.
            } catch (_: Exception) {
                // Bluetooth stack may already be unavailable.
            }
        }
    }

    companion object {
        private const val TAG = "AndroidBleScanner"

        private const val MANUFACTURER_ID = 0xFFFF

        private const val MAGIC_1: Byte = 0x53 // 'S'
        private const val MAGIC_2: Byte = 0x42 // 'B'
    }
}
