package com.tactical.platform.ble

import android.Manifest
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
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

    private val scanner: BluetoothLeScanner?
        get() = bluetoothManager.adapter?.bluetoothLeScanner

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

        val adapter = bluetoothManager.adapter

        if (adapter == null || !adapter.isEnabled) {
            close()
            return@callbackFlow
        }

        val le = scanner

        if (le == null) {
            close(
                IllegalStateException(
                    "BLE scanning unavailable"
                )
            )
            return@callbackFlow
        }

        val callback = object : ScanCallback() {

            override fun onScanResult(
                callbackType: Int,
                result: ScanResult
            ) {
                android.util.Log.d(
                    "BLE_DEBUG",
                    "SCAN RESULT: device=${result.device.address}, " +
                            "rssi=${result.rssi}"
                )
                val manufacturerData =
                    result.scanRecord?.manufacturerSpecificData

                if (manufacturerData == null) {
                    return
                }

                android.util.Log.d(
                    "BLE_DEBUG",
                    "MANUFACTURER DATA: $manufacturerData"
                )

                for (index in 0 until manufacturerData.size()) {

                    val payload = manufacturerData.valueAt(index)

                    if (payload == null) continue

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

                        val payload = manufacturerData.valueAt(index)

                        if (payload == null) continue

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
                null,
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
        }

        awaitClose {
            try {
                le.stopScan(callback)
            } catch (_: SecurityException) {
                // Permission was revoked.
            }
        }
    }
}