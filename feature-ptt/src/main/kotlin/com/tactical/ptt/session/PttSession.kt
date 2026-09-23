package com.tactical.ptt.session

import com.tactical.domain.identity.DeviceId
import com.tactical.domain.speech.LanguageTag
import java.util.UUID

/**
 * Encapsulates contextual metadata for an active or completed PTT
 * transmission session.
 *
 * ASSUMPTION FLAGGED: backendId = "en" is a placeholder — same gap as
 * engine-speech's SupportedLanguages.kt, since no real voice/backend IDs
 * exist yet (no bundled TTS model).
 *
 * OPEN QUESTION: isVox defaults to false and, given AmplitudeVoxController
 * only calls PttController.press()/release() (no parameters), there's
 * currently no way for DefaultPttController to know a session was
 * actually VOX-triggered vs. a manual button press — so isVox is
 * effectively always false as wired today. If that distinction matters
 * (e.g. for analytics or different haptic feedback), PttController.press()
 * would need an isVox parameter added to its interface.
 */
data class PttSession(
    val sessionId: String = UUID.randomUUID().toString(),
    val startTime: Long = System.currentTimeMillis(),
    val languageTag: LanguageTag = LanguageTag(isoCode = "en", backendId = "en"),
    val deviceId: DeviceId,
    val isVox: Boolean = false,
    val isCallMode: Boolean = false
)
