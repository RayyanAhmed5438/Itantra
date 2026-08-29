package com.tactical.platform.speech.backend.onnx

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.tactical.domain.audio.AudioFrame
import com.tactical.domain.speech.LanguageTag
import com.tactical.domain.speech.ModelIdentifier
import com.tactical.platform.api.speech.ModelProvider
import com.tactical.platform.api.speech.TextToSpeech
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Implements TextToSpeech using ONNX Runtime. Only file importing
 * ai.onnxruntime for TTS.
 *
 * Mirrors TfliteTextToSpeech's two load-bearing assumptions exactly (same
 * root cause — no real checkpoint chosen yet, see veryNew.md §5):
 * 1. textToInputBuffer() is a UTF-8 byte placeholder, not real
 *    tokenization — output will be unintelligible until replaced.
 * 2. Language conditioning modeled as a second input tensor
 *    ("language_id", from langTag.backendId) — the real checkpoint's
 *    actual input signature (input names, count, whether language is
 *    even a separate tensor vs. baked into the text tokens) isn't
 *    confirmed anywhere in this repo.
 *
 * Unlike OnnxSpeechToText, this class does NOT narrow its locking to only
 * session init — synthesize() is called once per finalized sentence
 * (comparatively rare vs. STT's per-partial-chunk calls), so the
 * simplicity of one mutex covering the whole call was preferred over the
 * narrower-but-more-complex split OnnxSpeechToText uses. Both are valid
 * given OrtSession.run()'s documented thread-safety — this is a
 * readability choice, not a correctness requirement.
 */
class OnnxTextToSpeech @Inject constructor(
    private val modelProvider: ModelProvider,
    private val tensorMapper: OnnxTensorMapper
) : TextToSpeech {

    private val sessionMutex = Mutex()
    private var cachedSession: OrtSession? = null
    private val environment: OrtEnvironment = OrtEnvironment.getEnvironment()

    override suspend fun synthesize(text: String, langTag: LanguageTag): AudioFrame = withContext(Dispatchers.Default) {
        require(text.isNotBlank()) { "text must not be blank" }

        val languageId = langTag.backendId.toIntOrNull()
            ?: throw IllegalArgumentException(
                "LanguageTag.backendId '${langTag.backendId}' is not an integer speaker/language " +
                        "index — OnnxTextToSpeech's language-conditioning assumption (see class kdoc) " +
                        "doesn't hold for this value"
            )

        sessionMutex.withLock {
            val session = loadSession()
            val textInputName = session.inputNames.elementAt(0)
            val languageInputName = session.inputNames.elementAt(1)

            val textTokens = tensorMapper.textToInputBuffer(text)
            val tokenCount = textTokens.remaining()

            OnnxTensor.createTensor(environment, textTokens, longArrayOf(1, tokenCount.toLong())).use { textTensor ->
                val languageBuffer = java.nio.IntBuffer.wrap(intArrayOf(languageId))
                OnnxTensor.createTensor(environment, languageBuffer, longArrayOf(1)).use { languageTensor ->
                    session.run(mapOf(textInputName to textTensor, languageInputName to languageTensor)).use { result ->
                        val outputTensor = result[0] as OnnxTensor
                        val samples = tensorMapper.outputBufferToFloatArray(outputTensor.floatBuffer)
                        tensorMapper.floatArrayToAudioFrame(
                            samples = samples,
                            sampleRate = SYNTHESIZED_SAMPLE_RATE_HZ,
                            timestamp = System.currentTimeMillis()
                        )
                    }
                }
            }
        }
    }

    private suspend fun loadSession(): OrtSession =
        cachedSession ?: run {
            val modelBuffer = modelProvider.getModelBuffer(ModelIdentifier(MODEL_ID))
            val modelBytes = ByteArray(modelBuffer.remaining())
            modelBuffer.duplicate().get(modelBytes)
            environment.createSession(modelBytes, OrtSession.SessionOptions()).also { cachedSession = it }
        }

    companion object {
        private const val MODEL_ID = "tts_multilingual"
        private const val SYNTHESIZED_SAMPLE_RATE_HZ = 22050
    }
}