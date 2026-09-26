package com.tactical.engine.discovery.beacon

import com.tactical.domain.identity.DeviceId
import com.tactical.domain.packet.BeaconPacket
import com.tactical.platform.api.ble.BleBeaconAdvertiser
import com.tactical.platform.api.ble.BleBeaconPayloadCodec
import com.tactical.platform.api.radio.RadioTransport
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.logging.Logger

class PeriodicBeaconEmitter(
    private val localDeviceId: DeviceId,
    private val callsignProvider: () -> String,
    private val transport: RadioTransport,
    private val bleAdvertiser: BleBeaconAdvertiser,
    private val scope: CoroutineScope =
        CoroutineScope(Dispatchers.Default + SupervisorJob())
) : BeaconEmitter {

    private var job: Job? = null
    private val running = AtomicBoolean(false)

    override fun start() {
        if (!running.compareAndSet(false, true)) return

        job = scope.launch {
            try {
                while (isActive) {
                    try {
                        emitBeacon()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // Keep advertising recoverable: one failed beacon
                        // attempt must not stop the discovery loop. Log the
                        // actual platform failure so OEM-specific advertising
                        // problems are visible during testing.
                        LOGGER.warning(
                            "BLE beacon advertisement failed: " +
                                (e.message ?: e.javaClass.simpleName)
                        )
                    }
                    delay(BEACON_INTERVAL_MS)
                }
            } catch (e: CancellationException) {
                throw e
            } finally {
                running.set(false)

                // A callsign change restarts the emitter by cancelling this
                // coroutine and immediately starting a replacement one.
                // The cancelled coroutine can still reach this finally block
                // after the replacement has already started advertising.
                // Only the currently registered job may stop the advertiser;
                // an old job must never tear down the new advertisement.
                if (job === coroutineContext[Job]) {
                    try {
                        bleAdvertiser.stopAdvertising()
                    } catch (_: Exception) {
                        // Nothing else to do during shutdown.
                    }
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
            callsign = callsignProvider(),
            timestamp = System.currentTimeMillis()
        )

        // BLE gets the compact, magic-byte-prefixed encoding — the magic
        // bytes let CompositeBeaconScanner reject any third-party BLE
        // device's unrelated advertisement instead of misparsing it.
        val bleBytes = BleBeaconPayloadCodec.encode(beacon)
        bleAdvertiser.advertise(bleBytes)

        // Normal radio transport still gets the full PacketSerializer
        // representation elsewhere — different wire format on purpose,
        // this emitter no longer touches that path.
    }

    companion object {
        private const val BEACON_INTERVAL_MS = 2000L
        private val LOGGER: Logger =
            Logger.getLogger(PeriodicBeaconEmitter::class.java.name)
    }
}
