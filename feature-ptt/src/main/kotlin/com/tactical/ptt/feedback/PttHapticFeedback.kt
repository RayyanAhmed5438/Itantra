package com.tactical.ptt.feedback

/**
 * Defines haptic notification hooks corresponding to PTT button actions.
 */
interface PttHapticFeedback {
    /** Triggered immediately upon PTT engagement or initial press. */
    suspend fun onPress()

    /** Triggered when the user releases the PTT button to stop recording. */
    suspend fun onRelease()

    /** Triggered when a packet is successfully handed off to the mesh transport. */
    suspend fun onTransmitComplete()
}