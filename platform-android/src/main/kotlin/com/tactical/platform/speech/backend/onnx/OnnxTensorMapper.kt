package com.tactical.platform.speech.backend.onnx

import com.tactical.domain.audio.AudioFrame
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.IntBuffer

/**
 * Same role as TfliteTensorMapper, for ONNX's tensor API. Converts
 * AudioFrame/String -> the FloatBuffer/IntBuffer shapes OnnxTensor.createTensor()
 * expects, rather than TfLite's raw BytwBuffer-in/ByteBuffer-out convention
 * the two runtimes disagree on this , which is exactly hwy this mapping lives ina backend
 * specefic file instead of being shared between the two
 *
 * Carries the same open assumption as TfliteTensorMapper: PCM16<-> float normalization
 * below is generic and safe, but textToInputBuffer() is a
 * UTF-8 byte-level placeholder that does NOT match any real model's vocabulary
 * - see that class's kdoc for the full reasoning unchanged here
 */

class OnnxTensorMapper {

    /**
     * Converts one AudioFrame's 16-bit PCM bytes into a normalized
     * [-1.0, 1.0] float32 FloatBuffer — same normalization as
     * TfliteTensorMapper.audioFrameToInputBuffer, just handed back as a
     * FloatBuffer since that's what OnnxTensor.createTensor() takes.
     */
    fun audioFrameToInputBuffer(frame: AudioFrame): FloatBuffer {
        val sampleCount = frame.data.size / BYTES_PER_PCM16_SAMPLE
        val samples = ByteBuffer.wrap(frame.data).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        val floatBuffer = FloatBuffer.allocate(sampleCount)
        for (i in 0 until sampleCount) {
            floatBuffer.put(samples.get(i) / PCM16_MAX_MAGNITUDE)
        }
        floatBuffer.rewind()
        return floatBuffer
    }

    /**
     * Copies an ONNX output tensor's FloatBuffer into a plain FloatArray.
     * Duplicates the buffer first so this doesn't consume/mutate the
     * position of a buffer the caller (e.g. OnnxTensor.floatBuffer) may
     * still hold a reference to.
     */
    fun outputBufferToFloatArray(buffer: FloatBuffer): FloatArray {
        val duplicate = buffer.duplicate()
        duplicate.rewind()
        val result = FloatArray(duplicate.remaining())
        duplicate.get(result)
        return result
    }

    /**
     * Packs a float32 synthesized waveform back into a 16-bit PCM
     * AudioFrame. Logic is identical to TfliteTensorMapper's version —
     * duplicated rather than shared, since forcing a shared base would
     * mean one backend depending on the other's package, which the "only
     * file importing ai.onnxruntime / org.tensorflow.lite" isolation
     * rule is explicitly meant to prevent.
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
     * ASSUMPTION FLAGGED — identical gap to
     * TfliteTensorMapper.textToInputBuffer, same unresolved cause (no
     * tokenizer/vocabulary asset exists anywhere in this codebase). Naive
     * UTF-8 byte-level placeholder as an IntBuffer (ONNX's typical
     * token-id tensor type). MUST be replaced once the real TTS
     * checkpoint's vocabulary is known.
     */
    fun textToInputBuffer(text: String): IntBuffer {
        val bytes = text.toByteArray(Charsets.UTF_8)
        val intBuffer = IntBuffer.allocate(bytes.size)
        for (b in bytes) {
            intBuffer.put(b.toInt() and 0xFF)
        }
        intBuffer.rewind()
        return intBuffer
    }

    companion object {
        private const val BYTES_PER_PCM16_SAMPLE = 2
        private const val PCM16_MAX_MAGNITUDE = 32767f
    }
}