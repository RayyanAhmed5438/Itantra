package com.tactical.platform.speech.mms

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.tactical.domain.audio.AudioFrame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.LongBuffer
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MmsTtsEngine @Inject constructor(
    private val modelStore: MmsTtsModelStore
) {
    private val environment: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val mutex = Mutex()

    private var loadedLanguage: MmsTtsLanguage? = null
    private var session: OrtSession? = null
    private var tokenizer: MmsVitsTokenizer? = null
    private var sampleRate: Int = DEFAULT_SAMPLE_RATE

    suspend fun synthesize(
        language: MmsTtsLanguage,
        text: String
    ): Pair<AudioFrame, MmsTtsTestResult> = withContext(Dispatchers.Default) {
        mutex.withLock {
            val directory = modelStore.modelDirectory(language)
            ensureLoaded(language, directory)

            val currentTokenizer = checkNotNull(tokenizer)
            val currentSession = checkNotNull(session)
            val tokenized = currentTokenizer.tokenize(text)

            val startNs = System.nanoTime()
            val inputIdsName = findInputName(currentSession, "input_ids")
                ?: throw IllegalStateException("MMS-TTS input_ids tensor is missing")
            val attentionMaskName = findInputName(
                currentSession,
                "attention_mask",
                required = false
            )

            val inputs = LinkedHashMap<String, OnnxTensor>()
            try {
                inputs[inputIdsName] = OnnxTensor.createTensor(
                    environment,
                    LongBuffer.wrap(tokenized.ids),
                    longArrayOf(1L, tokenized.ids.size.toLong())
                )

                if (attentionMaskName != null) {
                    val mask = LongArray(tokenized.ids.size) { 1L }
                    inputs[attentionMaskName] = OnnxTensor.createTensor(
                        environment,
                        LongBuffer.wrap(mask),
                        longArrayOf(1L, mask.size.toLong())
                    )
                }

                currentSession.inputInfo.keys
                    .filter { it != inputIdsName && it != attentionMaskName }
                    .forEach { name ->
                        if (name.contains("speaker", ignoreCase = true)) {
                            inputs[name] = OnnxTensor.createTensor(
                                environment,
                                LongBuffer.wrap(longArrayOf(0L)),
                                longArrayOf(1L)
                            )
                        } else {
                            throw IllegalStateException(
                                "Unsupported MMS-TTS input '" + name + "'. " +
                                    "Expected input_ids/attention_mask (and optionally speaker_id)."
                            )
                        }
                    }

                currentSession.run(inputs).use { result ->
                    val output = result[0] as? OnnxTensor
                        ?: throw IllegalStateException("MMS-TTS output[0] is not an ONNX tensor")

                    val buffer = output.floatBuffer.duplicate()
                    buffer.rewind()
                    val samples = FloatArray(buffer.remaining())
                    buffer.get(samples)

                    require(samples.isNotEmpty()) { "MMS-TTS returned empty waveform" }

                    val inferenceMs = (System.nanoTime() - startNs) / 1_000_000L
                    val frame = floatArrayToAudioFrame(
                        samples = samples,
                        sampleRate = sampleRate,
                        timestamp = System.currentTimeMillis()
                    )

                    frame to MmsTtsTestResult(
                        language = language,
                        requestedText = text,
                        normalizedText = tokenized.normalizedText,
                        tokenCount = tokenized.ids.size,
                        inferenceMs = inferenceMs,
                        audioDurationMs = frame.durationMs,
                        sampleRate = sampleRate
                    )
                }
            } finally {
                inputs.values.forEach { runCatching { it.close() } }
            }
        }
    }

    suspend fun synthesizeAndPlay(
        language: MmsTtsLanguage,
        text: String
    ): MmsTtsTestResult {
        val (frame, result) = synthesize(language, text)
        play(frame, result.sampleRate)
        return result
    }

    fun close() {
        closeLoadedSession()
    }

    private fun ensureLoaded(language: MmsTtsLanguage, directory: File) {
        if (loadedLanguage == language && session != null && tokenizer != null) return

        closeLoadedSession()

        val modelFile = File(directory, "model.int8.onnx")
        val modelBytes = modelFile.readBytes()

        session = environment.createSession(
            modelBytes,
            OrtSession.SessionOptions()
        )
        tokenizer = MmsVitsTokenizer(directory)
        sampleRate = JSONObject(
            File(directory, "config.json").readText(Charsets.UTF_8)
        ).optInt("sampling_rate", DEFAULT_SAMPLE_RATE)
        loadedLanguage = language
    }

    private fun findInputName(
        currentSession: OrtSession,
        expected: String,
        required: Boolean = true
    ): String? {
        val exact = currentSession.inputInfo.keys.firstOrNull { it == expected }
        if (exact != null) return exact

        val fuzzy = currentSession.inputInfo.keys.firstOrNull {
            it.replace("_", "").equals(
                expected.replace("_", ""),
                ignoreCase = true
            )
        }
        if (fuzzy != null) return fuzzy

        if (required) {
            throw IllegalStateException(
                "MMS-TTS ONNX model does not expose '" + expected +
                    "'. Inputs: " + currentSession.inputInfo.keys
            )
        }
        return null
    }

    private suspend fun play(frame: AudioFrame, sampleRate: Int) =
        withContext(Dispatchers.IO) {
            val channelMask = AudioFormat.CHANNEL_OUT_MONO
            val encoding = AudioFormat.ENCODING_PCM_16BIT
            val minBuffer = AudioTrack.getMinBufferSize(
                sampleRate,
                channelMask,
                encoding
            )
            require(minBuffer > 0) {
                "AudioTrack buffer size unavailable for " + sampleRate + " Hz"
            }

            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setChannelMask(channelMask)
                        .setEncoding(encoding)
                        .build()
                )
                .setBufferSizeInBytes(
                    maxOf(
                        minBuffer,
                        frame.data.size.coerceAtMost(minBuffer * 4)
                    )
                )
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            try {
                track.play()
                var offset = 0
                while (offset < frame.data.size) {
                    val written = track.write(
                        frame.data,
                        offset,
                        frame.data.size - offset
                    )
                    require(written > 0) {
                        "AudioTrack.write failed: " + written
                    }
                    offset += written
                }
                track.stop()
            } finally {
                track.release()
            }
        }

    private fun floatArrayToAudioFrame(
        samples: FloatArray,
        sampleRate: Int,
        timestamp: Long
    ): AudioFrame {
        val pcm = ByteArray(samples.size * 2)
        val buffer = ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN)

        samples.forEach { sample ->
            val value = (
                sample.coerceIn(-1f, 1f) * 32767f
            ).toInt().toShort()
            buffer.putShort(value)
        }

        val durationMs =
            (samples.size * 1000L) / sampleRate.coerceAtLeast(1)

        return AudioFrame(
            data = pcm,
            timestamp = timestamp,
            durationMs = durationMs.coerceAtLeast(1L)
        )
    }

    private fun closeLoadedSession() {
        runCatching { session?.close() }
        session = null
        tokenizer = null
        loadedLanguage = null
    }

    companion object {
        private const val DEFAULT_SAMPLE_RATE = 16_000
    }
}
