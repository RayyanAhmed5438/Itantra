package com.tactical.platform.api.permissions

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