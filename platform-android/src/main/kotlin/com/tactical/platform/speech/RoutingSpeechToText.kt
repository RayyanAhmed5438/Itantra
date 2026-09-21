package com.tactical.platform.speech

import com.tactical.domain.audio.AudioFrame
import com.tactical.domain.speech.TranscriptionChunk
import com.tactical.platform.api.speech.SpeechToText
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Selects the outgoing PTT STT backend from the user's saved language.
 *
 * This does not affect reception: incoming packets carry their own
 * languageCode on the wire.
 */
@Singleton
class RoutingSpeechToText @Inject constructor(
    private val moonshineSpeechToText: MoonshineSpeechToText,
    private val voskHindiSpeechToText: VoskHindiSpeechToText,
    private val languagePreferences: SpeechLanguagePreferences
) : SpeechToText {

    override fun transcribe(
        audio: Flow<AudioFrame>
    ): Flow<TranscriptionChunk> =
        when (languagePreferences.selectedLanguageCode) {
            "hi" -> voskHindiSpeechToText.transcribe(audio)
            "en" -> moonshineSpeechToText.transcribe(audio)
            else -> error(
                "No STT backend configured for language " +
                    languagePreferences.selectedLanguageCode
            )
        }
}
