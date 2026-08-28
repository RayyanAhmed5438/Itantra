package com.tactical.platform.api.power

/**
 * Requests the user whitelist this app from Doze/battery optimization.
 * Implemented in platform-android, wrapping the
 * ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS system dialog. Consumed by
 * app's settings screen (likely during first-run setup, alongside model
 * provisioning) and by feature-sentry, since its cold-wake listener has
 * the strongest reason of anything in the app to need this exemption.
 */
interface DozeModeHandler {

    /**
     * Opens the system dialog asking the user to exempt this app from
     * Doze. No return value — Android's own dialog flow doesn't give the
     * calling app a synchronous yes/no here; the app finds out indirectly
     * later (e.g. by checking isIgnoringBatteryOptimizations() separately,
     * which isn't part of this interface as specified).
     */
    suspend fun requestIgnoreBatteryOptimizations()
}