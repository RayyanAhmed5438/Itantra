package com.tactical.platform.api.audio

import com.tactical.domain.audio.AudioFrame

/**
 * Plays PCM audio out through the device speaker/earpiece. Implemented in
 * platform-android, wrapping Android's AudioTrack API.
 */
interface AudioPlayer {

    /**
     * Plays a single AudioFrame of raw PCM.
     */
    suspend fun play(frame: AudioFrame)

    /**
     * Plays a pre-defined siren/tone without needing an AudioFrame — for a
     * built-in alert sound rather than decoded speech.
     */
    suspend fun playAlert(tone: AlertTone)

    suspend fun stopPlayback()
}

/**
 * The fixed set of built-in alert sounds the app can play. Lives here
 * rather than core-domain since it names actual bundled audio assets
 * platform-android ships, not a pure domain concept.
 */
enum class AlertTone {
    EMERGENCY_INCOMING,
    MESSAGE_RECEIVED
}