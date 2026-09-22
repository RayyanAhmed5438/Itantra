package com.tactical.platform.speech

import com.tactical.domain.audio.AudioFrame
import com.tactical.domain.speech.TranscriptionChunk
import com.tactical.platform.api.speech.SpeechToText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Selects the outgoing PTT STT backend from the user's saved language.
 *
 * This does not affect reception: incoming packets carry their own
 * languageCode on the wire.
 *
 * Only the currently selected backend is retained as a live native model.
 * When the selected language changes, the previously active backend is closed.
 */
@Singleton
class RoutingSpeechToText @Inject constructor(
    private val moonshineSpeechToText: MoonshineSpeechToText,
    private val voskHindiSpeechToText: VoskHindiSpeechToText,
    private val languagePreferences: SpeechLanguagePreferences
) : SpeechToText {

    private val switchMutex = Mutex()
    private var activeLanguage: String? = null

    override fun transcribe(
        audio: Flow<AudioFrame>
    ): Flow<TranscriptionChunk> {
        val selectedLanguage = languagePreferences.selectedLanguageCode

        return flow {
            switchMutex.withLock {
                if (activeLanguage != selectedLanguage) {
                    when (activeLanguage) {
                        "hi" -> voskHindiSpeechToText.close()
                        "en" -> moonshineSpeechToText.close()
                    }
                    activeLanguage = selectedLanguage
                }
            }

            emitAll(
                when (selectedLanguage) {
                    "hi" -> voskHindiSpeechToText.transcribe(audio)
                    "en" -> moonshineSpeechToText.transcribe(audio)
                    else -> error(
                        "No STT backend configured for language " +
                            selectedLanguage
                    )
                }
            )
        }
    }
}
