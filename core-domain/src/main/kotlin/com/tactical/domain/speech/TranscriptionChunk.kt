package com.tactical.domain.speech

data class TranscriptionChunk(
    val text: String,
    val isFinal: Boolean,
    val languageCode: String
)