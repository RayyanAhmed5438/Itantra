package com.tactical.platform.ble

import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import com.tactical.platform.api.ble.BleBeaconAdvertiser
import kotlinx.coroutines.suspendCancellableCoroutine

class AndroidBleAdvertiser(private val context: Context) : BleBeaconAdvertiser {

    private val bluetoothManager: BluetoothManager by lazy {
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    }

    private val advertiser: BluetoothLeAdvertiser?
        get() = bluetoothManager.adapter?.bluetoothLeAdvertiser

    private var activeCallback: AdvertiseCallback? = null

    override suspend fun advertise(payload: ByteArray) {
        stopAdvertising()

        val le = advertiser
            ?: throw IllegalStateException(
                "BLE advertising unavailable (adapter off, or chipset doesn't support it)"
            )

        require(payload.size <= MAX_MANUFACTURER_DATA_BYTES) {
            "payload too large for legacy BLE advertising: " +
                    "${payload.size} bytes, max $MAX_MANUFACTURER_DATA_BYTES"
        }

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(false)
            .build()

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addManufacturerData(MANUFACTURER_ID, payload)
            .build()

        suspendCancellableCoroutine { continuation ->
            val callback = object : AdvertiseCallback() {

                override fun onStartSuccess(
                    settingsInEffect: AdvertiseSettings
                ) {
                    activeCallback = this

                    if (continuation.isActive) {
                        continuation.resumeWith(Result.success(Unit))
                    }
                }

                override fun onStartFailure(errorCode: Int) {
                    if (continuation.isActive) {
                        continuation.resumeWith(
                            Result.failure(
                                IllegalStateException(
                                    "advertise() failed, errorCode=$errorCode"
                                )
                            )
                        )
                    }
                }
            }

            try {
                le.startAdvertising(settings, data, callback)
            } catch (e: SecurityException) {
                if (continuation.isActive) {
                    continuation.resumeWith(
                        Result.failure(
                            SecurityException(
                                "Bluetooth advertising permission is not granted",
                                e
                            )
                        )
                    )
                }
            }
        }
    }

    override suspend fun stopAdvertising() {
        val le = advertiser ?: return

        activeCallback?.let { callback ->
            try {
                le.stopAdvertising(callback)
            } catch (_: SecurityException) {
                // Permission was revoked; nothing else to clean up.
            }
        }

        activeCallback = null
    }

    companion object {
        private const val MANUFACTURER_ID = 0xFFFF
        private const val MAX_MANUFACTURER_DATA_BYTES = 27
    }
}