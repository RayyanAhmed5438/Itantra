package com.tactical.speech.config

import com.tactical.domain.speech.LanguageTag

/**
 * Static registry of the 10 languages required by the problem statement:
 * Hindi, Gujarati, Marathi, Kannada, Malayalam, Tamil, Telugu, Odia,
 * Bengali, English.
 *
 * ASSUMPTION FLAGGED: `backendId` is a placeholder equal to `isoCode` for
 * every entry. The real value depends entirely on platform-android's
 * `assets/models/tts_multilingual.voices.json` (the language → internal
 * speaker/embedding map mentioned in the architecture handbook's asset
 * list), which doesn't exist yet on my end. Whoever bundles the actual
 * TTS model needs to update every `backendId` here to match that file's
 * real speaker indices/language codes — until then, TextToSpeech.synthesize()
 * calls made through this registry will very likely select the wrong
 * voice (or fail) once a real backend is wired in.
 *
 * Edit values here if the model's internal language IDs change — this is
 * the single shared source of truth `platform-android`'s speech backends
 * should read from, so a language ID typo can't silently create a
 * mismatched voice at only one call site.
 */
object SupportedLanguages {

    val ALL: List<LanguageProfile> = listOf(
        LanguageProfile("hi", "Hindi", LanguageTag("hi", "hi")),
        LanguageProfile("gu", "Gujarati", LanguageTag("gu", "gu")),
        LanguageProfile("mr", "Marathi", LanguageTag("mr", "mr")),
        LanguageProfile("kn", "Kannada", LanguageTag("kn", "kn")),
        LanguageProfile("ml", "Malayalam", LanguageTag("ml", "ml")),
        LanguageProfile("ta", "Tamil", LanguageTag("ta", "ta")),
        LanguageProfile("te", "Telugu", LanguageTag("te", "te")),
        LanguageProfile("or", "Odia", LanguageTag("or", "or")),
        LanguageProfile("bn", "Bengali", LanguageTag("bn", "bn")),
        LanguageProfile("en", "English", LanguageTag("en", "en"))
    )

    private val byIsoCode: Map<String, LanguageProfile> = ALL.associateBy { it.isoCode }

    /** Returns the profile for the given ISO code, or null if unsupported. */
    fun findByIsoCode(isoCode: String): LanguageProfile? = byIsoCode[isoCode]
}
