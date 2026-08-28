package com.tactical.platform.api.permissions

/**
 * Requests a runtime permission and reports whether it was granted.
 * Implemented in platform-android, wrapping Android's runtime permission
 * request flow (ActivityCompat.requestPermissions / the Activity Result
 * API). Consumed by app's UI (initial permission onboarding) and
 * feature-sentry (which may need to re-check/re-request before starting
 * its cold-wake listener).
 */
interface PermissionGateway {

    /**
     * Requests the given permission, suspending until the user responds
     * (or until the OS auto-denies, e.g. if the permission was previously
     * denied with "don't ask again"). Returns true if granted.
     */
    suspend fun request(permission: Permission): Boolean
}

/**
 * ASSUMPTION FLAGGED: core.md's signature is `request(permission:
 * Permission): Boolean` but Permission itself is never defined anywhere
 * in the document. Modeled as a logical enum, deliberately NOT wrapping
 * Android's Manifest.permission string constants directly — doing so
 * would mean this interface (in core-platform-api, which must have zero
 * Android imports) would need android.Manifest.permission.* strings
 * passed into it from above, breaking the "no Android imports below
 * platform-android" rule at the exact boundary meant to prevent it.
 * AndroidPermissionGateway is responsible for mapping each case here to
 * the real Android permission string(s) it corresponds to — including
 * cases where one logical permission maps to different actual strings
 * depending on API level (see BLUETOOTH_SCAN/BLUETOOTH_ADVERTISE below).
 */
enum class Permission {
    RECORD_AUDIO,
    LOCATION,          // ACCESS_FINE_LOCATION - needed for BLE scan on API < 31
    BLUETOOTH_SCAN,    // BLUETOOTH_SCAN (API 31+) or implied by LOCATION below it
    BLUETOOTH_ADVERTISE,
    BLUETOOTH_CONNECT,
    NEARBY_WIFI_DEVICES,  // or ACCESS_WIFI_STATE + CHANGE_WIFI_STATE pre-API 33
    CAMERA,            // for FlashlightController, if the device requires it
    NOTIFICATIONS      // POST_NOTIFICATIONS, API 33+, for the foreground service
}