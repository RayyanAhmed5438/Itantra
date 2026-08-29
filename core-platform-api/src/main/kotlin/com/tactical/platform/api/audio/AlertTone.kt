package com.tactical.platform.api.audio

/**
 * The fixed set of built-in alert sounds the app can play. Lives here
 * rather than core-domain since it names actual bundled audio assets
 * platform-android ships, not a pure domain concept.
 */
enum class AlertTone {
    EMERGENCY_INCOMING,
    MESSAGE_RECEIVED
}