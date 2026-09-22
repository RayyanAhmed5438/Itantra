package com.tactical.platform.speech

import com.tactical.domain.audio.AudioFrame
import com.tactical.domain.speech.TranscriptionChunk
import com.tactical.platform.api.speech.SpeechToText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Hindi offline STT using the Vosk small Hindi model.
 *
 * AudioRecorder already provides mono PCM16 at 16 kHz, which is exactly the
 * format the recognizer consumes.
 */
@Singleton
class VoskHindiSpeechToText @Inject constructor(
    private val modelStore: VoskHindiSttModelStore
) : SpeechToText {

    private val modelMutex = Mutex()
    private var cachedModel: Model? = null

    override fun transcribe(
        audio: Flow<AudioFrame>
    ): Flow<TranscriptionChunk> = flow {
        val model = loadModel()
        val recognizer = Recognizer(model, SAMPLE_RATE_HZ.toFloat())

        try {
            android.util.Log.d(TAG, "Hindi Vosk STT starting")

            audio.collect { frame ->
                val accepted = recognizer.acceptWaveForm(frame.data, frame.data.size)

                if (accepted) {
                    val text = extractText(recognizer.result)
                    if (text.isNotBlank()) {
                        android.util.Log.d(TAG, "Hindi STT FINAL: $text")
                        emit(
                            TranscriptionChunk(
                                text = text,
                                isFinal = true,
                                languageCode = LANGUAGE_CODE
                            )
                        )
                    }
                } else {
                    val partial = extractPartial(recognizer.partialResult)
                    if (partial.isNotBlank()) {
                        emit(
                            TranscriptionChunk(
                                text = partial,
                                isFinal = false,
                                languageCode = LANGUAGE_CODE
                            )
                        )
                    }
                }
            }

            val finalText = extractText(recognizer.finalResult)
            if (finalText.isNotBlank()) {
                android.util.Log.d(TAG, "Hindi Vosk STT FINAL (stream end): $finalText")
                emit(
                    TranscriptionChunk(
                        text = finalText,
                        isFinal = true,
                        languageCode = LANGUAGE_CODE
                    )
                )
            }
        } finally {
            recognizer.close()
            android.util.Log.d(TAG, "Hindi Vosk STT stopped")
        }
    }.flowOn(Dispatchers.Default)

    fun close() {
        modelMutex.tryLock().let { locked ->
            if (locked) {
                try {
                    runCatching { cachedModel?.close() }
                    cachedModel = null
                } finally {
                    modelMutex.unlock()
                }
            }
        }
    }

    private suspend fun loadModel(): Model =
        modelMutex.withLock {
            cachedModel ?: run {
                val modelDir = modelStore.ensureBundledModelAvailable()

                Model(modelDir.absolutePath).also {
                    cachedModel = it
                }
            }
        }

    private fun extractText(json: String): String =
        runCatching { JSONObject(json).optString("text").trim() }
            .getOrDefault("")

    private fun extractPartial(json: String): String =
        runCatching { JSONObject(json).optString("partial").trim() }
            .getOrDefault("")

    companion object {
        private const val TAG = "VoskHindiSpeechToText"
        private const val LANGUAGE_CODE = "hi"
        private const val SAMPLE_RATE_HZ = 16_000
    }
}
