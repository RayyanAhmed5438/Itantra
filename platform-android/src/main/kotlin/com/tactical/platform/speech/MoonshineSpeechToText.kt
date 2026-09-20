package com.tactical.platform.speech

import ai.moonshine.voice.AssetDownloader
import ai.moonshine.voice.JNI
import ai.moonshine.voice.ModelSpec
import ai.moonshine.voice.Transcriber
import ai.moonshine.voice.TranscriptEvent
import android.content.Context
import com.tactical.domain.audio.AudioFrame
import com.tactical.platform.api.speech.SpeechToText
import com.tactical.domain.speech.TranscriptionChunk
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.function.Consumer
import javax.inject.Inject

/**
 * English-only on-device STT backed by Moonshine Voice.
 *
 * The existing AudioRecorder remains the microphone source. PCM16 mono audio
 * frames are converted to normalized float samples and fed into Moonshine's
 * streaming Transcriber. The model is downloaded once into filesDir and then
 * reused offline on later launches.
 */
class MoonshineSpeechToText @Inject constructor(
    @ApplicationContext private val context: Context
) : SpeechToText {

    private val loadMutex = Mutex()
    private var cachedTranscriber: Transcriber? = null

    override fun transcribe(audio: Flow<AudioFrame>): Flow<TranscriptionChunk> = callbackFlow {
        val transcriber = loadTranscriber()
        val streamHandle = transcriber.createStream()

        val listener = Consumer<TranscriptEvent> { event ->
            when (event) {
                is TranscriptEvent.LineTextChanged -> {
                    trySend(
                        TranscriptionChunk(
                            text = event.line.text.orEmpty(),
                            isFinal = false,
                            languageCode = LANGUAGE_CODE
                        )
                    )
                }

                is TranscriptEvent.LineCompleted -> {
                    trySend(
                        TranscriptionChunk(
                            text = event.line.text.orEmpty(),
                            isFinal = true,
                            languageCode = LANGUAGE_CODE
                        )
                    )
                }

                is TranscriptEvent.Error -> {
                    close(event.cause)
                }
            }
        }

        transcriber.addListener(listener)

        try {
            transcriber.startStream(streamHandle)

            audio.collect { frame ->
                val samples = pcm16ToFloat(frame.data)
                if (samples.isNotEmpty()) {
                    transcriber.addAudioToStream(
                        streamHandle,
                        samples,
                        SAMPLE_RATE_HZ
                    )
                }
            }
        } catch (t: Throwable) {
            close(t)
        } finally {
            runCatching {
                transcriber.stopStream(streamHandle)
            }.onFailure { stopError ->
                if (!isClosedForSend) {
                    close(stopError)
                }
            }

            runCatching { transcriber.removeListener(listener) }
            runCatching { transcriber.freeStream(streamHandle) }
            close()
        }

        awaitClose { }
    }.flowOn(Dispatchers.Default)

    private suspend fun loadTranscriber(): Transcriber = loadMutex.withLock {
        cachedTranscriber ?: run {
            val modelDir = context.filesDir.resolve("moonshine/stt-tiny-en").apply { mkdirs() }

            AssetDownloader().ensureModelPresent(
                modelDir,
                ModelSpec.stt(
                    LANGUAGE_CODE,
                    JNI.MOONSHINE_MODEL_ARCH_TINY_STREAMING,
                    false
                ),
                null
            )

            Transcriber().also {
                it.setUpdateInterval(0.5)
                it.loadFromFiles(
                    modelDir.absolutePath,
                    JNI.MOONSHINE_MODEL_ARCH_TINY_STREAMING
                )
                cachedTranscriber = it
            }
        }
    }

    private fun pcm16ToFloat(data: ByteArray): FloatArray {
        val sampleCount = data.size / PCM16_BYTES_PER_SAMPLE
        if (sampleCount <= 0) return FloatArray(0)

        val shorts = ByteBuffer
            .wrap(data, 0, sampleCount * PCM16_BYTES_PER_SAMPLE)
            .order(ByteOrder.LITTLE_ENDIAN)
            .asShortBuffer()

        val result = FloatArray(sampleCount)
        for (i in result.indices) {
            result[i] = shorts.get(i) / 32768f
        }
        return result
    }

    companion object {
        private const val LANGUAGE_CODE = "en"
        private const val SAMPLE_RATE_HZ = 16_000
        private const val PCM16_BYTES_PER_SAMPLE = 2
    }
}
