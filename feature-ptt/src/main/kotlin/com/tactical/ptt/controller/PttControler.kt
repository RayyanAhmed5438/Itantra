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
    suspend fun press()
    suspend fun release()
    fun state(): StateFlow<PttState>
}
