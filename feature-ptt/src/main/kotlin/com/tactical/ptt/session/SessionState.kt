package com.tactical.feature.ptt.session

/**
 * Represents the distinct lifecycle states of a Push-To-Talk (PTT) session.
 */
enum class SessionState {
    /** Controller is dormant and awaiting user interaction. */
    IDLE,

    /** PTT button is triggered; preparing hardware resources and session metadata. */
    ARMED,

    /** Active microphone capture and real-time speech transcription in progress. */
    RECORDING,

    /** User released PTT; final transcription packet is being dispatched over the mesh network. */
    TRANSMITTING,

    /** Audio playback active (reserved for receiving voice payloads). */
    PLAYING
}