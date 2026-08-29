package com.tactical.platform.speech.backend.tflite

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
import org.tensorflow.lite.Interpreter
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject

/**
 * Implements SpeechToText using TFLite. Only file importing
 * org.tensorflow.lite for STT.
 *
 * Streaming design (per dataStructures.md §3 step 5): buffers incoming
 * AudioFrames, periodically runs inference on the accumulated buffer to
 * emit isFinal=false partials (for the sender's live-feedback UI), and on
 * >=400ms of silence runs one more inference pass and emits isFinal=true
 * — which is what PttController actually transmits.
 *
 * ASSUMPTION FLAGGED, load-bearing: decodeLogits() below — turning the
 * model's raw output tensor into actual text — is NOT implemented and
 * throws. Unlike TfliteTensorMapper.textToInputBuffer (a TTS *input*
 * placeholder, whose worst case is wrong-sounding audio), a fabricated
 * STT *output* would be invented message content silently relayed to the
 * whole squad as if it were real transcribed speech — actively
 * dangerous in a tactical/emergency app. Throwing here instead of
 * guessing is a deliberate choice, not an oversight: it means
 * transcribe() is real up through model inference (buffering, VAD,
 * TFLite invocation all genuinely run), but currently always fails at
 * the last step until the actual model's vocabulary/decoding scheme
 * (CTC greedy vs. beam search, subword vocab, etc.) is known and wired
 * in — that requires picking the real checkpoint first (veryNew.md §5).
 */
class TfliteSpeechToText @Inject constructor(
    private val modelProvider: ModelProvider,
    private val tensorMapper: TfliteTensorMapper
) : SpeechToText {

    private val interpreterMutex = Mutex()
    private var cachedInterpreter: Interpreter? = null

    override fun transcribe(audio: Flow<AudioFrame>): Flow<TranscriptionChunk> = flow {
        val interpreter = loadInterpreter()
        val pcmBuffer = ByteArrayOutputStream()
        var accumulatedDurationMs = 0L
        var silenceDurationMs = 0L
        var lastPartialEmittedAtMs = 0L

        suspend fun runInferenceOn(bytes: ByteArray, durationMs: Long): String {
            val frame = AudioFrame(data = bytes, timestamp = System.currentTimeMillis(), durationMs = durationMs.coerceAtLeast(1))
            val inputBuffer = tensorMapper.audioFrameToInputBuffer(frame)
            val sampleCount = bytes.size / PCM16_BYTES_PER_SAMPLE

            return interpreterMutex.withLock {
                // ASSUMPTION FLAGGED: assumes a single-input, single-output
                // model with a dynamically resizable time dimension
                // (typical for streaming Conformer/Whisper-style models),
                // input shape [1, sampleCount]. The real checkpoint's
                // actual tensor shape/count isn't known yet — this is a
                // reasonable default, not a confirmed contract.
                interpreter.resizeInput(0, intArrayOf(1, sampleCount))
                interpreter.allocateTensors()

                val outputTensor = interpreter.getOutputTensor(0)
                val outputBuffer = ByteBuffer.allocateDirect(outputTensor.numBytes()).order(ByteOrder.nativeOrder())
                interpreter.run(inputBuffer, outputBuffer)
                outputBuffer.rewind()

                val elementCount = outputTensor.numBytes() / Float.SIZE_BYTES
                val logits = tensorMapper.outputBufferToFloatArray(outputBuffer, elementCount)
                decodeLogits(logits)
            }
        }

        audio.collect { audioFrame ->
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

        // Recording stopped (PTT release) with unflushed speech still
        // buffered and no trailing silence to trigger the final chunk
        // above — flush it as final rather than dropping it.
        if (pcmBuffer.size() > 0) {
            val finalText = runInferenceOn(pcmBuffer.toByteArray(), accumulatedDurationMs)
            emit(TranscriptionChunk(text = finalText, isFinal = true, languageCode = UNKNOWN_LANGUAGE_CODE))
        }
    }.flowOn(Dispatchers.Default)

    private suspend fun loadInterpreter(): Interpreter = interpreterMutex.withLock {
        cachedInterpreter ?: Interpreter(modelProvider.getModelBuffer(ModelIdentifier(MODEL_ID))).also {
            cachedInterpreter = it
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
    private fun decodeLogits(logits: FloatArray): String {
        throw NotImplementedError(
            "STT output decoding isn't wired up yet — needs the real model's " +
                    "vocabulary/decoding scheme (see TfliteSpeechToText's class kdoc)"
        )
    }

    companion object {
        private const val MODEL_ID = "stt_multilingual"
        private const val PCM16_BYTES_PER_SAMPLE = 2
        private const val SILENCE_RMS_THRESHOLD = 500.0
        private const val SILENCE_THRESHOLD_MS = 400L
        private const val PARTIAL_EMIT_INTERVAL_MS = 800L
        private const val UNKNOWN_LANGUAGE_CODE = "und" // ISO 639 "undetermined" — see class kdoc
    }
}