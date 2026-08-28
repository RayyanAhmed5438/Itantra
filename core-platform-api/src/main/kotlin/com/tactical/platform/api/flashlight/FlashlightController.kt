package com.tactical.platform.api.flashlight

/**
 * Controls the camera flashlight for a visual emergency signal. Implemented
 * in platform-android, wrapping Android's CameraManager torch API.
 * Consumed by feature-emergency's squelch breaker, alongside the alert
 * tone, haptic pattern, and DND/volume bypass — the full multi-sensory
 * alert stack.
 */
interface FlashlightController {

    /**
     * Strobes the flashlight on/off at the given interval, repeating
     * until off() is called.
     */
    suspend fun strobe(intervalMs: Long)

    /**
     * Turns the flashlight off, stopping any active strobe.
     */
    suspend fun off()
}