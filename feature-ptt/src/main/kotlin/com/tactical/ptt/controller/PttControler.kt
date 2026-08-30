package com.tactical.ptt.controller

import com.tactical.domain.result.TacticalResult
import com.tactical.ptt.session.SessionState
import kotlinx.coroutines.flow.StateFlow

data class PttState(
    val sessionState: SessionState = SessionState.IDLE,
    val lastTranscription: String? = null,
    val lastResult: TacticalResult<Unit>? = null
)

interface PttController {
    /** isVox = true when this session was triggered by VOX energy
     *  detection rather than a manual button press. */
    suspend fun press(isVox: Boolean = false)
    suspend fun release()
    fun state(): StateFlow<PttState>
}
