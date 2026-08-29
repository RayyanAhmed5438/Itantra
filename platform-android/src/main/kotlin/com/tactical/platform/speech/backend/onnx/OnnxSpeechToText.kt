package com.tactical.platform.speech.backend.onnx

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.tactical.domain.audio.AudioFrame
import com.tactical.domain.speech.ModelIdentifier
import com.tactical.platform.api.speech.ModelProvider
import com.tactical.platform.api.speech.SpeechToText
import com.tactical.platform.api.speech.TranscriptionChunk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject

/**
 * Implements SpeechToText using ONNX Runtime. Only file importing
 * ai.onnxruntime for STT.
 *
 * Same streaming design as TfliteSpeechToText (buffer → periodic partial
 * inference → 400ms-silence final inference) — duplicated rather than
 * shared with the TFLite version for the same package-isolation reason
 * given in OnnxTensorMapper's kdoc.
 *
 * One real difference from the TFLite version worth calling out:
 * OrtSession.run() is documented thread-safe for concurrent calls (it
 * doesn't mutate session state), unlike TFLite's Interpreter, which
 * requires exclusive single-threaded access. So the mutex here only
 * guards lazy session initialization, not each run() call — narrower
 * locking than TfliteSpeechToText's, deliberately.
 *
 * ASSUMPTION FLAGGED, load-bearing — identical gap to
 * TfliteSpeechToText.decodeLogits(), same reasoning (fabricated STT
 * output is dangerous, not just low-quality, in this app): decodeOutput()
 * below throws rather than guessing at a vocabulary/decoding scheme that
 * depends on the real checkpoint (veryNew.md §5), not yet chosen.
 */
class OnnxSpeechToText @Inject constructor(
    private val modelProvider: ModelProvider,
    private val tensorMapper: OnnxTensorMapper
) : SpeechToText {

    private val sessionMutex = Mutex()
    private var cachedSession: OrtSession? = null
    private val environment: OrtEnvironment = OrtEnvironment.getEnvironment()

    override fun transcribe(audio: Flow<AudioFrame>): Flow<TranscriptionChunk> = flow {
        val session = loadSession()
        val inputName = session.inputNames.first()
        val pcmBuffer = ByteArrayOutputStream()
        var accumulatedDurationMs = 0L
        var silenceDurationMs = 0L
        var lastPartialEmittedAtMs = 0L

        fun runInferenceOn(bytes: ByteArray, durationMs: Long): String {
            val frame = AudioFrame(data = bytes, timestamp = System.currentTimeMillis(), durationMs = durationMs.coerceAtLeast(1))
            val inputFloats = tensorMapper.audioFrameToInputBuffer(frame)
            val sampleCount = bytes.size / PCM16_BYTES_PER_SAMPLE

            // ASSUMPTION FLAGGED: shape [1, sampleCount] — same
            // single-input, dynamic-time-axis assumption as
            // TfliteSpeechToText, unconfirmed against a real checkpoint.
            OnnxTensor.createTensor(environment, inputFloats, longArrayOf(1, sampleCount.toLong())).use { inputTensor ->
                session.run(mapOf(inputName to inputTensor)).use { result ->
                    val outputTensor = result[0] as OnnxTensor
                    val logits = tensorMapper.outputBufferToFloatArray(outputTensor.floatBuffer)
                    return decodeOutput(logits)
                }
            }
        }

        audio.collect { audioFrame ->
            @Suppress("BlockingMethodInNonBlockingContext")
            pcmBuffer.write(audioFrame.data)
            accumulatedDurationMs += audioFrame.durationMs

            val isSilent = rmsAmplitude(audioFrame.data) < SILENCE_RMS_THRESHOLD
            silenceDurationMs = if (isSilent) silenceDurationMs + audioFrame.durationMs else 0L

            if (!isSilent && accumulatedDurationMs - lastPartialEmittedAtMs >= PARTIAL_EMIT_INTERVAL_MS) {
                val partialText = runInferenceOn(pcmBuffer.toByteArray(), accumulatedDurationMs)
                emit(TranscriptionChunk(text = partialText, isFinal = false, languageCode = UNKNOWN_LANGUAGE_CODE))
                lastPartialEmittedAtMs = accumulatedDurationMs
            }

            if (silenceDurationMs >= SILENCE_THRESHOLD_MS && pcmBuffer.size() > 0) {
                val finalText = runInferenceOn(pcmBuffer.toByteArray(), accumulatedDurationMs)
                emit(TranscriptionChunk(text = finalText, isFinal = true, languageCode = UNKNOWN_LANGUAGE_CODE))
                pcmBuffer.reset()
                accumulatedDurationMs = 0L
                silenceDurationMs = 0L
                lastPartialEmittedAtMs = 0L
            }
        }

        if (pcmBuffer.size() > 0) {
            val finalText = runInferenceOn(pcmBuffer.toByteArray(), accumulatedDurationMs)
            emit(TranscriptionChunk(text = finalText, isFinal = true, languageCode = UNKNOWN_LANGUAGE_CODE))
        }
    }.flowOn(Dispatchers.Default)

    private suspend fun loadSession(): OrtSession = sessionMutex.withLock {
        cachedSession ?: run {
            val modelBuffer = modelProvider.getModelBuffer(ModelIdentifier(MODEL_ID))
            val modelBytes = ByteArray(modelBuffer.remaining())
            modelBuffer.duplicate().get(modelBytes)
            environment.createSession(modelBytes, OrtSession.SessionOptions()).also { cachedSession = it }
        }
    }

    private fun rmsAmplitude(pcmBytes: ByteArray): Double {
        val samples = ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        if (samples.remaining() == 0) return 0.0
        var sumSquares = 0.0
        while (samples.hasRemaining()) {
            val s = samples.get().toDouble()
            sumSquares += s * s
        }
        return kotlin.math.sqrt(sumSquares / samples.capacity())
    }

    /** See the class-level ASSUMPTION FLAGGED note — deliberately unimplemented. */
    private fun decodeOutput(logits: FloatArray): String {
        throw NotImplementedError(
            "STT output decoding isn't wired up yet — needs the real model's " +
                    "vocabulary/decoding scheme (see OnnxSpeechToText's class kdoc)"
        )
    }

    companion object {
        private const val MODEL_ID = "stt_multilingual"
        private const val PCM16_BYTES_PER_SAMPLE = 2
        private const val SILENCE_RMS_THRESHOLD = 500.0
        private const val SILENCE_THRESHOLD_MS = 400L
        private const val PARTIAL_EMIT_INTERVAL_MS = 800L
        private const val UNKNOWN_LANGUAGE_CODE = "und"
    }
}