package com.tactical.platform.api.power

/**
 * Acquires/releases a partial CPU wake lock, keeping the device awake for
 * operations that must survive the screen turning off or the CPU sleeping
 * — engine-mesh's always-on relay and feature-sentry's cold-wake listener.
 * Implemented in platform-android, wrapping Android's
 * PowerManager.WakeLock.
 */
interface WakeLockManager {

    /**
     * Acquires a wake lock identified by tag. Must be idempotent — calling
     * this again with the same tag while already held is a no-op, not a
     * stacked double-acquire.
     */
    suspend fun acquire(tag: String)

    /**
     * Releases the wake lock identified by tag. Safe to call when not
     * currently held for that tag.
     */
    suspend fun release(tag: String)
}