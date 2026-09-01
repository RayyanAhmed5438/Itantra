package com.tactical.emergency.beacon

import com.tactical.domain.location.GeoFix
import com.tactical.domain.packet.EmergencyPacket
import com.tactical.emergency.broadcast.EmergencyBroadcaster
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * ASSUMPTION FLAGGED: nothing in core-platform-api (verified against the
 * real repo) exposes a location-provider interface — GeoFix isn't
 * produced by anything with a documented contract yet. Rather than
 * invent an interface that doesn't exist in the actual codebase, this
 * class takes a `currentLocation` supplier injected from the call site
 * (presumably backed by Android's FusedLocationProviderClient inside
 * platform-android, wired up in app's DI). Swap this for a real
 * LocationProvider interface once one exists.
 */
class PeriodicDistressBeacon(
    private val basePacket: EmergencyPacket,
    private val currentLocation: suspend () -> GeoFix?,
    private val broadcaster: EmergencyBroadcaster,
    private val scope: CoroutineScope,
    private val intervalMs: Long = 5_000L
) : DistressBeacon {

    private var job: Job? = null

    override suspend fun start() {
        if (job != null) return
        job = scope.launch {
            while (true) {
                val fresh = basePacket.copy(
                    location = currentLocation(),
                    timestamp = System.currentTimeMillis()
                )
                broadcaster.broadcastSos(fresh)
                delay(intervalMs)
            }
        }
    }

    override suspend fun stop() {
        job?.cancel()
        job = null
    }
}
