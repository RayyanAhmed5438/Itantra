package com.tactical.platform.speech.mms

data class MmsTtsTestResult(
    val language: MmsTtsLanguage,
    val requestedText: String,
    val normalizedText: String,
    val tokenCount: Int,
    val inferenceMs: Long,
    val audioDurationMs: Long,
    val sampleRate: Int
) {
    val realTimeFactor: Double
        get() = inferenceMs.toDouble() /
            audioDurationMs.coerceAtLeast(1L).toDouble()
}
