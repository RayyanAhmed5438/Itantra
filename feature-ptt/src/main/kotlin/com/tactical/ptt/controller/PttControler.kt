package com.tactical.ptt.controller

import com.tactical.domain.result.TacticalResult
import com.tactical.ptt.session.SessionState
import kotlinx.coroutines.flow.StateFlow

enum class PttTransmissionStatus {
    SENDING,
    SENT,
    QUEUED
}

data class PttTransmission(
    val id: String,
    val text: String,
    val languageCode: String,
    val timestampEpochMs: Long,
    val status: PttTransmissionStatus = PttTransmissionStatus.SENDING
)

data class PttState(
    val sessionState: SessionState = SessionState.IDLE,
    val sessionId: String? = null,
    val lastTranscription: String? = null,
    val lastResult: TacticalResult<Unit>? = null,
    /**
     * True when the current/most recent session is continuous call-mode
     * speech rather than a manual push-to-talk session.
     */
    val continuousSession: Boolean = false,
    /**
     * Recent sentence-level transmissions from the active/most recent
     * continuous session. Kept bounded by the controller.
     */
    val transmissions: List<PttTransmission> = emptyList()
)

interface PttController {
    /** isVox = true when this session was triggered by VOX energy
     *  detection rather than a manual button press. */
    suspend fun press(isVox: Boolean = false)

    suspend fun release()

    suspend fun cancel()

    /** Starts continuous microphone -> STT -> sentence transmission mode. */
    suspend fun startContinuous()

    /** Stops continuous mode after allowing the active STT/send queue to flush. */
    suspend fun stopContinuous()

    fun state(): StateFlow<PttState>
}
