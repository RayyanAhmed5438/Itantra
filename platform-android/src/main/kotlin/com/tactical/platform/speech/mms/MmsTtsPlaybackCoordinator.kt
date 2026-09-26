package com.tactical.platform.speech.mms

import com.tactical.domain.audio.AudioFrame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Coordinates incoming voice-message TTS.
 *
 * Synthesis is always performed through the single shared MmsTtsEngine, which
 * already serializes model inference. The coordinator pipelines that
 * synthesis ahead of playback:
 *
 *   message 1: synthesize -> play
 *   message 2:          synthesize -> wait/play
 *
 * In ONE_BY_ONE mode, synthesized frames are played strictly in receive order.
 * In OVERLAPPING mode, each synthesized frame starts playback immediately, so
 * multiple AudioTracks may overlap.
 *
 * Only a small number of synthesized frames are buffered at once, preventing
 * a burst of long messages from consuming unbounded RAM. Mode changes never
 * interrupt a currently playing AudioTrack; messages that have not started
 * playback yet follow the mode active when they are dispatched.
 */
@Singleton
class MmsTtsPlaybackCoordinator @Inject constructor(
    private val ttsEngine: MmsTtsEngine
) {

    private data class Request(
        val language: MmsTtsLanguage,
        val text: String
    )

    private data class Synthesized(
        val frame: AudioFrame,
        val sampleRate: Int
    )

    private val requestQueue = Channel<Request>(Channel.UNLIMITED)

    // Keep a small RAM buffer so the next message can be synthesized while the
    // current message is playing, without allowing unlimited audio accumulation.
    private val synthesizedQueue = Channel<Synthesized>(SYNTHESIZED_BUFFER_CAPACITY)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    private var playbackMode = MmsTtsPlaybackMode.OVERLAPPING

    init {
        scope.launch {
            synthesizeLoop()
        }

        scope.launch {
            playbackLoop()
        }
    }

    fun setMode(mode: MmsTtsPlaybackMode) {
        playbackMode = mode
    }

    fun currentMode(): MmsTtsPlaybackMode = playbackMode

    fun enqueue(
        language: MmsTtsLanguage,
        text: String
    ) {
        requestQueue.trySend(
            Request(
                language = language,
                text = text
            )
        )
    }

    private suspend fun synthesizeLoop() {
        for (request in requestQueue) {
            try {
                val (frame, result) = ttsEngine.synthesize(
                    request.language,
                    request.text
                )

                synthesizedQueue.send(
                    Synthesized(
                        frame = frame,
                        sampleRate = result.sampleRate
                    )
                )
            } catch (error: kotlinx.coroutines.CancellationException) {
                throw error
            } catch (error: Exception) {
                android.util.Log.w(
                    TAG,
                    "Incoming voice-message synthesis failed: " +
                        (error.message ?: error.javaClass.simpleName)
                )
            }
        }
    }

    private suspend fun playbackLoop() {
        for (synthesized in synthesizedQueue) {
            if (playbackMode == MmsTtsPlaybackMode.ONE_BY_ONE) {
                runPlaybackSafely(synthesized)
            } else {
                // Do not wait for an earlier AudioTrack in overlapping mode.
                // Each synthesized message gets its own playback task.
                scope.launch {
                    runPlaybackSafely(synthesized)
                }
            }
        }
    }

    private suspend fun runPlaybackSafely(synthesized: Synthesized) {
        try {
            ttsEngine.play(
                synthesized.frame,
                synthesized.sampleRate
            )
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (error: Exception) {
            android.util.Log.w(
                TAG,
                "Incoming voice-message playback failed: " +
                    (error.message ?: error.javaClass.simpleName)
            )
        }
    }

    companion object {
        private const val TAG = "MmsTtsPlayback"
        private const val SYNTHESIZED_BUFFER_CAPACITY = 2
    }
}
