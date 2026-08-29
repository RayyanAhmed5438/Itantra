package com.tactical.platform.speech.backend.tflite

import com.tactical.domain.audio.AudioFrame
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Converts AudioFrame/String ⇄ TFLite ByteBuffer tensors — the numeric
 * plumbing TfliteSpeechToText/TfliteTextToSpeech sit on top of. Handles
 * generic PCM↔float conversion and ByteBuffer packing only; anything
 * semantic (tokenization, vocabulary lookup, phoneme mapping) is
 * explicitly out of scope here and flagged below, since it depends on
 * which specific STT/TTS checkpoint gets bundled — a detail neither
 * architecture.md nor dataStructures.md specifies.
 */
class TfliteTensorMapper {

    /**
     * Converts one AudioFrame's 16-bit PCM bytes into a direct,
     * native-order float32 ByteBuffer normalized to [-1.0, 1.0] — the
     * input format essentially every TFLite speech model (Conformer,
     * Whisper-style, etc.) expects instead of raw integer PCM.
     */
    fun audioFrameToInputBuffer(frame: AudioFrame): ByteBuffer {
        val sampleCount = frame.data.size / BYTES_PER_PCM16_SAMPLE
        val buffer = ByteBuffer.allocateDirect(sampleCount * Float.SIZE_BYTES).order(ByteOrder.nativeOrder())

        val samples = ByteBuffer.wrap(frame.data).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        for (i in 0 until sampleCount) {
            buffer.putFloat(samples.get(i) / PCM16_MAX_MAGNITUDE)
        }
        buffer.rewind()
        return buffer
    }

    /**
     * Reads a float32 output tensor back into a plain FloatArray for
     * whatever the caller does with it next — decoding STT logits into
     * tokens, or treating it as a raw synthesized waveform for TTS.
     * elementCount must match the tensor's actual output shape; this
     * class has no way to determine that on its own from a raw
     * ByteBuffer output binding.
     */
    fun outputBufferToFloatArray(buffer: ByteBuffer, elementCount: Int): FloatArray {
        val floatBuffer = buffer.order(ByteOrder.nativeOrder()).asFloatBuffer()
        val result = FloatArray(elementCount)
        floatBuffer.rewind()
        floatBuffer.get(result)
        return result
    }

    /**
     * Packs a float32 synthesized waveform (TTS model output, already
     * decoded via outputBufferToFloatArray) back into a 16-bit PCM
     * AudioFrame.
     */
    fun floatArrayToAudioFrame(samples: FloatArray, sampleRate: Int, timestamp: Long): AudioFrame {
        val pcmBytes = ByteArray(samples.size * BYTES_PER_PCM16_SAMPLE)
        val pcmBuffer = ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN)
        for (sample in samples) {
            val clamped = sample.coerceIn(-1f, 1f)
            pcmBuffer.putShort((clamped * PCM16_MAX_MAGNITUDE).toInt().toShort())
        }
        val durationMs = (samples.size * 1000L) / sampleRate
        return AudioFrame(data = pcmBytes, timestamp = timestamp, durationMs = durationMs.coerceAtLeast(1))
    }

    /**
     * ASSUMPTION FLAGGED — the biggest real gap in this file: there is no
     * tokenizer/vocabulary asset handling anywhere in this codebase.
     * ModelProvider hands out only raw model bytes, no accompanying
     * vocab.json/SentencePiece file. Real char/subword TTS models need
     * the exact vocabulary they were trained with to map text to token
     * IDs correctly, and getting this wrong doesn't fail loudly — it
     * just produces garbage speech. This method is a naive UTF-8
     * byte-level placeholder (one int32 token per input byte) that
     * compiles and runs, but almost certainly does NOT match whatever
     * tokenization the actual bundled TTS checkpoint expects. It MUST be
     * replaced once the specific model (Piper-style vs. AI4Bharat
     * Indic-TTS, per veryNew.md §5) is chosen and its vocabulary format
     * is known.
     */
    fun textToInputBuffer(text: String): ByteBuffer {
        val bytes = text.toByteArray(Charsets.UTF_8)
        val buffer = ByteBuffer.allocateDirect(bytes.size * Int.SIZE_BYTES).order(ByteOrder.nativeOrder())
        for (b in bytes) {
            buffer.putInt(b.toInt() and 0xFF)
        }
        buffer.rewind()
        return buffer
    }

    companion object {
        private const val BYTES_PER_PCM16_SAMPLE = 2
        private const val PCM16_MAX_MAGNITUDE = 32767f
    }
}