package com.tactical.platform.speech.mms

data class MmsTtsLanguage(
    val modelCode: String,
    val isoCode: String,
    val displayName: String
) {
    companion object {
        val ALL: List<MmsTtsLanguage> = listOf(
            MmsTtsLanguage("eng", "en", "English"),
            MmsTtsLanguage("ben", "bn", "Bengali"),
            MmsTtsLanguage("guj", "gu", "Gujarati"),
            MmsTtsLanguage("hin", "hi", "Hindi"),
            MmsTtsLanguage("kan", "kn", "Kannada"),
            MmsTtsLanguage("mal", "ml", "Malayalam"),
            MmsTtsLanguage("mar", "mr", "Marathi"),
            MmsTtsLanguage("ory", "or", "Odia"),
            MmsTtsLanguage("tam", "ta", "Tamil"),
            MmsTtsLanguage("tel", "te", "Telugu")
        )

        private val byIso = ALL.associateBy { it.isoCode }

        fun fromIsoCode(isoCode: String): MmsTtsLanguage? = byIso[isoCode]

        fun fromModelCode(modelCode: String): MmsTtsLanguage? =
            ALL.firstOrNull { it.modelCode == modelCode }
    }
}
