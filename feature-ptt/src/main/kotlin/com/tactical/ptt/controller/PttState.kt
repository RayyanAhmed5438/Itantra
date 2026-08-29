package com.tactical.ptt.controller

import com.tactical.feature.ptt.session.SessionState

/**
 * Everything the PTT screen needs to render: the current lifecycle state,
 * a live partial-transcript preview (from non-final TranscriptionChunks,
 * per architecture handbook §6.7), and whether the last transmission
 * succeeded — surfaced from MeshService's TacticalResult so the UI can
 * show the red "transmission failed" indicator (data-flow doc §3 step 11).
 */
data class PttState(
    val sessionState: SessionState = SessionState.IDLE,
    val livePartialText: String = "",
    val lastTransmissionFailed: Boolean = false,
    val lastError: String? = null
)
