package com.tactical.platform.ble

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
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
            close(
                SecurityException(
                    "Missing $requiredPermission"
                )
            )
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
            android.util.Log.d(
                TAG,
                "BLE scanner unavailable"
            )
            close()
            return@callbackFlow
        }

        val scanFilter = ScanFilter.Builder()
            .setManufacturerData(
                MANUFACTURER_ID,
                byteArrayOf(
                    MAGIC_1,
                    MAGIC_2
                )
            )
            .build()

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
BleBeaconPayloadCodec.decode(payload)?.let { packet ->
                        BlePeerAddressRegistry.remember(packet.sender.value, result.device.address)
                    }

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

                        BleBeaconPayloadCodec.decode(payload)?.let { packet ->
                            BlePeerAddressRegistry.remember(packet.sender.value, result.device.address)
                        }

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
            .setScanMode(
                ScanSettings.SCAN_MODE_LOW_LATENCY
            )
            .setLegacy(true)
            .build()

        try {
            le.startScan(
                listOf(scanFilter),
                settings,
                callback
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