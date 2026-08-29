package com.tactical.platform.ble

import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import com.tactical.platform.api.ble.BleBeaconAdvertiser
import kotlinx.coroutines.resume
import kotlinx.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Implements BleBeaconAdvertiser by wrapping Android's
 * BluetoothLeAdvertiser. advertise() replaces any currently-active
 * advertisement rather than layering a second one, per the interface's
 * "starts (or replaces, if already advertising)" kdoc.
 *
 * Advertises non-connectable — this is a pure presence broadcast.
 * Establishing a data connection to a peer once discovered is a separate
 * GATT operation (see radio/BleRadioTransport / BleConnectionRegistry),
 * not something this class does.
 */
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
            ?: throw IllegalStateException("BLE advertising unavailable (adapter off, or chipset doesn't support it)")

        // ASSUMPTION FLAGGED: architecture.md specifies no max BeaconPacket
        // wire size and no extended-advertising fallback. Legacy (non-
        // extended) BLE advertising caps total AD data at 31 bytes; one
        // manufacturer-specific-data AD structure costs 4 bytes of that
        // (length + type + 2-byte company ID), leaving 27 usable bytes in
        // the best case. A BeaconPacket carrying a DeviceId + callsign
        // string + timestamp will likely exceed this once serialized —
        // worth confirming with whoever owns BeaconPacket/core-protocol
        // whether it needs to shrink, or whether this should instead use
        // BLE 5 extended advertising (isLeExtendedAdvertisingSupported) on
        // chipsets that support it, which isn't implemented here.
        require(payload.size <= MAX_MANUFACTURER_DATA_BYTES) {
            "payload too large for legacy BLE advertising: ${payload.size} bytes, max $MAX_MANUFACTURER_DATA_BYTES"
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
                override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                    activeCallback = this
                    if (continuation.isActive) continuation.resume(Unit)
                }

                override fun onStartFailure(errorCode: Int) {
                    if (continuation.isActive) {
                        continuation.resumeWithException(IllegalStateException("advertise() failed, errorCode=$errorCode"))
                    }
                }
            }
            le.startAdvertising(settings, data, callback)
        }
    }

    override suspend fun stopAdvertising() {
        val le = advertiser ?: return
        activeCallback?.let { le.stopAdvertising(it) }
        activeCallback = null
    }

    companion object {
        // ASSUMPTION FLAGGED: 0xFFFF is the Bluetooth SIG's reserved
        // testing/prototyping company ID — fine for development, but must
        // be swapped for a real registered company ID (or another AD
        // structure entirely) before this ships.
        private const val MANUFACTURER_ID = 0xFFFF
        private const val MAX_MANUFACTURER_DATA_BYTES = 27
    }
}