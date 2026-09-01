package com.tactical.engine.speech.config

import com.tactical.domain.speech.LanguageTag

/**
 * One entry in the app's fixed set of supported languages: the ISO code
 * used on the wire (TextPacket/EmergencyPacket.languageCode), a
 * human-readable name for UI display (language picker, settings), and the
 * LanguageTag the active multilingual model actually uses internally to
 * select a voice at TTS inference time.
 */
data class LanguageProfile(
    val isoCode: String,
    val displayName: String,
    val languageTag: LanguageTag
)
