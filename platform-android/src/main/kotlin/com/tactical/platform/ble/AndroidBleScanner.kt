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

/**
 * Implements BleBeaconScanner by wrapping Android's BluetoothLeScanner.
 * Handles the API 28-30 (ACCESS_FINE_LOCATION-gated) vs API 31+
 * (BLUETOOTH_SCAN-gated) permission split per the interface's kdoc — but
 * only as a guard against a SecurityException crash on startScan(), not
 * as a request path. Actually requesting the permission from the user is
 * PermissionGateway's job (not yet built); callers are expected to have
 * gone through that before collecting this Flow.
 */
class AndroidBleScanner(private val context: Context) : BleBeaconScanner {

    private val bluetoothManager: BluetoothManager by lazy {
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    }
    private val scanner: BluetoothLeScanner?
        get() = bluetoothManager.adapter?.bluetoothLeScanner

    override fun scan(): Flow<ScannedBleDevice> = callbackFlow {
        val requiredPermission = scanPermissionForThisApiLevel()
        if (context.checkSelfPermission(requiredPermission) != PackageManager.PERMISSION_GRANTED) {
            close(SecurityException("Missing $requiredPermission — request it via PermissionGateway before collecting scan()"))
            return@callbackFlow
        }

        val le = scanner
        if (le == null) {
            close(IllegalStateException("BLE scanning unavailable (adapter off, or chipset doesn't support it)"))
            return@callbackFlow
        }

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                trySend(result.toScannedBleDevice())
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach { trySend(it.toScannedBleDevice()) }
            }

            override fun onScanFailed(errorCode: Int) {
                close(IllegalStateException("BLE scan failed, errorCode=$errorCode"))
            }
        }

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        le.startScan(null, settings, callback)

        awaitClose { le.stopScan(callback) }
    }

    private fun ScanResult.toScannedBleDevice(): ScannedBleDevice {
        val manufacturerData = scanRecord?.manufacturerSpecificData
        val payload = manufacturerData?.let { sparse -> if (sparse.size() == 0) null else sparse.valueAt(0) }
        return ScannedBleDevice(
            deviceId = device.address,
            rssi = rssi,
            advertisementPayload = payload
        )
    }

    private fun scanPermissionForThisApiLevel(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Manifest.permission.BLUETOOTH_SCAN
        } else {
            Manifest.permission.ACCESS_FINE_LOCATION
        }
}