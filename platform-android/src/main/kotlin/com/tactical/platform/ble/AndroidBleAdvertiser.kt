package com.tactical.platform.ble

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import com.tactical.platform.api.ble.BleBeaconAdvertiser
import kotlinx.coroutines.suspendCancellableCoroutine

class AndroidBleAdvertiser(
    private val context: Context
) : BleBeaconAdvertiser {

    private val bluetoothManager: BluetoothManager by lazy {
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    }

    private val bluetoothAdapter: BluetoothAdapter?
        get() = bluetoothManager.adapter

    private val advertiser: BluetoothLeAdvertiser?
        get() = bluetoothAdapter?.bluetoothLeAdvertiser

    private var activeCallback: AdvertiseCallback? = null
    private var activePayload: ByteArray? = null

    override suspend fun advertise(payload: ByteArray) {

        val adapter = bluetoothAdapter

        if (adapter == null || !adapter.isEnabled) {
            throw IllegalStateException(
                "Bluetooth is OFF or unavailable"
            )
        }

        val le = advertiser
            ?: throw IllegalStateException(
                "BLE advertising unavailable"
            )

        require(payload.size <= MAX_MANUFACTURER_DATA_BYTES) {
            "payload too large for legacy BLE advertising: " +
                    "${payload.size} bytes, max $MAX_MANUFACTURER_DATA_BYTES"
        }

        // Already advertising this exact payload.
        if (
            activeCallback != null &&
            activePayload?.contentEquals(payload) == true
        ) {
            return
        }

        stopAdvertising()

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(
                AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY
            )
            .setTxPowerLevel(
                AdvertiseSettings.ADVERTISE_TX_POWER_HIGH
            )
            .setConnectable(false)
            .build()

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addManufacturerData(
                MANUFACTURER_ID,
                payload
            )
            .build()

        suspendCancellableCoroutine { continuation ->

            val callback = object : AdvertiseCallback() {

                override fun onStartSuccess(
                    settingsInEffect: AdvertiseSettings
                ) {
                    android.util.Log.d(
                        TAG,
                        "BLE ADVERTISEMENT STARTED"
                    )

                    activeCallback = this
                    activePayload = payload.copyOf()

                    if (continuation.isActive) {
                        continuation.resumeWith(
                            Result.success(Unit)
                        )
                    }
                }

                override fun onStartFailure(
                    errorCode: Int
                ) {
                    android.util.Log.w(
                        TAG,
                        "BLE ADVERTISEMENT FAILED: errorCode=$errorCode"
                    )

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
                le.startAdvertising(
                    settings,
                    data,
                    callback
                )
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
            } catch (e: Exception) {
                if (continuation.isActive) {
                    continuation.resumeWith(
                        Result.failure(
                            IllegalStateException(
                                "Unable to start BLE advertising",
                                e
                            )
                        )
                    )
                }
            }
        }
    }

    override suspend fun stopAdvertising() {

        val le = advertiser
        val callback = activeCallback

        if (le != null && callback != null) {
            try {
                le.stopAdvertising(callback)
            } catch (_: SecurityException) {
                // Permission was revoked.
            } catch (_: Exception) {
                // Bluetooth stack may already be unavailable.
            }
        }

        activeCallback = null
        activePayload = null
    }

    companion object {
        private const val TAG = "AndroidBleAdvertiser"

        private const val MANUFACTURER_ID = 0xFFFF

        private const val MAX_MANUFACTURER_DATA_BYTES = 27
    }
}