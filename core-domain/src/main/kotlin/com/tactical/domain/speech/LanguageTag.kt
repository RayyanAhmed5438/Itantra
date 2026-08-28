package com.tactical.domain.speech

/**
 * ISO 639 code plus the backend-internal identifier the active multilingual
 * model uses for that language (e.g. a NeMo language code, or a Piper
 * speaker index). Keeps backend-specific language IDs out of engine-speech
 * entirely — engine-speech deals in LanguageTag, never in whatever raw
 * identifier a specific STT/TTS backend happens to expect internally.
 */
data class LanguageTag(
    val isoCode: String,
    val backendId: String
) {
    init {
        require(isoCode.isNotBlank()) { "isoCode must not be blank" }
        require(backendId.isNotBlank()) { "backendId must not be blank" }
    }
}