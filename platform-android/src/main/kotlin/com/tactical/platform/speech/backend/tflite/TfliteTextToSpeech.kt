package com.tactical.platform.speech.backend.tflite

import com.tactical.domain.audio.AudioFrame
import com.tactical.domain.speech.LanguageTag
import com.tactical.domain.speech.ModelIdentifier
import com.tactical.platform.api.speech.ModelProvider
import com.tactical.platform.api.speech.TextToSpeech
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject

/**
 * Implements TextToSpeech using TFLite. Only file importing
 * org.tensorflow.lite for TTS.
 *
 * ASSUMPTION FLAGGED (both load-bearing on the real checkpoint choice —
 * same root cause as TfliteSpeechToText's decodeLogits() gap, unanswerable
 * until veryNew.md §5's actual model is picked):
 *
 * 1. Text tokenization: textToInputBuffer() in TfliteTensorMapper is a
 *    naive UTF-8 byte placeholder, not real subword/phoneme tokenization.
 *    Output speech will be unintelligible until the real model's vocab
 *    is wired in.
 * 2. Language conditioning: this multilingual model needs to know which
 *    of the 10 languages to speak per call — modeled here as a second
 *    int32 input tensor (index 0 = text, index 1 = language/speaker id,
 *    from langTag.backendId — per LanguageTag's own kdoc, "e.g. ... a
 *    Piper speaker index"). That's how VITS-family multi-speaker models
 *    typically take this, but the real checkpoint's actual input
 *    signature needs confirming.
 *
 * SYNTHESIZED_SAMPLE_RATE_HZ is also a placeholder (22050 Hz, a common
 * VITS/Piper default) — the real checkpoint's output rate isn't
 * confirmed anywhere in this repo.
 */
class TfliteTextToSpeech @Inject constructor(
    private val modelProvider: ModelProvider,
    private val tensorMapper: TfliteTensorMapper
) : TextToSpeech {

    private val interpreterMutex = Mutex()
    private var cachedInterpreter: Interpreter? = null

    override suspend fun synthesize(text: String, langTag: LanguageTag): AudioFrame = withContext(Dispatchers.Default) {
        require(text.isNotBlank()) { "text must not be blank" }

        val interpreter = loadInterpreter()
        val textInput = tensorMapper.textToInputBuffer(text)

        val languageId = langTag.backendId.toIntOrNull()
            ?: throw IllegalArgumentException(
                "LanguageTag.backendId '${langTag.backendId}' is not an integer speaker/language " +
                        "index — TfliteTextToSpeech's language-conditioning assumption (see class kdoc) " +
                        "doesn't hold for this value"
            )
        val languageInput = ByteBuffer.allocateDirect(Int.SIZE_BYTES).order(ByteOrder.nativeOrder()).apply {
            putInt(languageId)
            rewind()
        }

        val audioSamples = interpreterMutex.withLock {
            interpreter.allocateTensors()

            val outputTensor = interpreter.getOutputTensor(0)
            val outputBuffer = ByteBuffer.allocateDirect(outputTensor.numBytes()).order(ByteOrder.nativeOrder())

            interpreter.runForMultipleInputsOutputs(arrayOf(textInput, languageInput), mapOf(0 to outputBuffer))
            outputBuffer.rewind()

            val elementCount = outputTensor.numBytes() / Float.SIZE_BYTES
            tensorMapper.outputBufferToFloatArray(outputBuffer, elementCount)
        }

        tensorMapper.floatArrayToAudioFrame(
            samples = audioSamples,
            sampleRate = SYNTHESIZED_SAMPLE_RATE_HZ,
            timestamp = System.currentTimeMillis()
        )
    }

    private suspend fun loadInterpreter(): Interpreter = interpreterMutex.withLock {
        cachedInterpreter ?: Interpreter(modelProvider.getModelBuffer(ModelIdentifier(MODEL_ID))).also {
            cachedInterpreter = it
        }
    }

    companion object {
        private const val MODEL_ID = "tts_multilingual"
        private const val SYNTHESIZED_SAMPLE_RATE_HZ = 22050
    }
}