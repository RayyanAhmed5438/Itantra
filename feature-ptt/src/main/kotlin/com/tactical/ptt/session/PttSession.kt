package com.tactical.ptt.session

import com.tactical.domain.identity.DeviceId
import com.tactical.domain.speech.LanguageTag
import java.util.UUID

/**
 * Encapsulates contextual metadata for an active or completed PTT transmission session.
 */
data class PttSession(
    val sessionId: String = UUID.randomUUID().toString(),
    val startTime: Long = System.currentTimeMillis(),
    val languageTag: LanguageTag = LanguageTag("en-US"),
    val deviceId: DeviceId,
    val isVox: Boolean = false
)