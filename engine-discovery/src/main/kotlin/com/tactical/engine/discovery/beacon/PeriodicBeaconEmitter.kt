package com.tactical.engine.discovery.beacon

import com.tactical.domain.identity.DeviceId
import com.tactical.domain.packet.BeaconPacket
import com.tactical.platform.api.ble.BleBeaconAdvertiser
import com.tactical.platform.api.radio.RawPacket
import com.tactical.platform.api.radio.RadioTransport
import com.tactical.platform.api.ble.BlePayloadMapper
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean

class PeriodicBeaconEmitter(
    private val localDeviceId: DeviceId,
    private val callsign: String,
    private val transport: RadioTransport,
    private val bleAdvertiser: BleBeaconAdvertiser,
    private val scope: CoroutineScope =
        CoroutineScope(Dispatchers.Default + SupervisorJob())
) : BeaconEmitter {

    private var job: Job? = null

    private val running = AtomicBoolean(false)

    private val blePayloadMapper = BlePayloadMapper()

    override fun start() {
        if (!running.compareAndSet(false, true)) return

        job = scope.launch {
            try {
                while (isActive) {
                    emitBeacon()
                    delay(BEACON_INTERVAL_MS)
                }
            } catch (e: CancellationException) {
                throw e
            } finally {
                running.set(false)

                try {
                    bleAdvertiser.stopAdvertising()
                } catch (_: Exception) {
                    // Nothing else to do during shutdown.
                }
            }
        }
    }

    override fun stop() {
        job?.cancel()
        job = null
    }

    private suspend fun emitBeacon() {
        val beacon = BeaconPacket(
            sender = localDeviceId,
            callsign = callsign,
            timestamp = System.currentTimeMillis()
        )

        /*
         * BLE gets the compact BLE-specific representation.
         */
        val bleBytes = blePayloadMapper.toBytes(beacon)


        bleAdvertiser.advertise(bleBytes)

        /*
         * Normal radio transport still gets the normal
         * PacketSerializer representation elsewhere.
         *
         * This emitter no longer has PacketSerializer because
         * BLE discovery and radio transport have different wire formats.
         */
    }

    companion object {
        private const val BEACON_INTERVAL_MS = 2000L
    }
}