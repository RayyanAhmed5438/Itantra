package com.tactical.platform.speech.mms

import com.tactical.domain.audio.AudioFrame
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
 * In ONE_BY_ONE mode, all synthesized frames are played strictly in receive
 * order.
 *
 * In OVERLAPPING mode, voices from different senders may play concurrently,
 * but messages from the same sender are always serialized. This prevents one
 * device's long voice message from overlapping that same device's next,
 * shorter message while still allowing simultaneous voices from different
 * devices.
 *
 * Only a small number of synthesized frames are buffered at once, preventing
 * a burst of long messages from consuming unbounded RAM. Mode changes never
 * interrupt a currently playing AudioTrack. When switching to ONE_BY_ONE,
 * playback waits for any already-running overlapping tracks to finish before
 * starting the next pending track.
 */
@Singleton
class MmsTtsPlaybackCoordinator @Inject constructor(
    private val ttsEngine: MmsTtsEngine
) {

    private data class Request(
        val senderId: String,
        val language: MmsTtsLanguage,
        val text: String
    )

    private data class Synthesized(
        val senderId: String,
        val frame: AudioFrame,
        val sampleRate: Int
    )

    private val requestQueue = Channel<Request>(Channel.UNLIMITED)

    // Keep a small RAM buffer so the next message can be synthesized while the
    // current message is playing, without allowing unlimited audio accumulation.
    private val synthesizedQueue = Channel<Synthesized>(SYNTHESIZED_BUFFER_CAPACITY)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val playbackStateMutex = Mutex()
    private var activePlaybacks = 0
    private val activeSenders = mutableMapOf<String, CompletableDeferred<Unit>>()
    private var idleSignal = CompletableDeferred<Unit>().also { it.complete(Unit) }

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
        senderId: String,
        language: MmsTtsLanguage,
        text: String
    ) {
        requestQueue.trySend(
            Request(
                senderId = senderId,
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
                        senderId = request.senderId,
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
                // ONE_BY_ONE is global: no sender can start while any other
                // received voice message is still playing.
                awaitIdle()
                beginPlayback(synthesized.senderId)

                try {
                    runPlaybackSafely(synthesized)
                } finally {
                    endPlayback(synthesized.senderId)
                }
            } else {
                // OVERLAPPING only permits overlap between different senders.
                // Messages from the same sender always wait for that sender's
                // current playback to finish.
                awaitSenderIdle(synthesized.senderId)
                beginPlayback(synthesized.senderId)

                scope.launch {
                    try {
                        runPlaybackSafely(synthesized)
                    } finally {
                        endPlayback(synthesized.senderId)
                    }
                }
            }
        }
    }

    private suspend fun awaitIdle() {
        val signal = playbackStateMutex.withLock {
            if (activePlaybacks == 0) null else idleSignal
        }
        signal?.await()
    }

    private suspend fun awaitSenderIdle(senderId: String) {
        val signal = playbackStateMutex.withLock {
            activeSenders[senderId]
        }
        signal?.await()
    }

    private suspend fun beginPlayback(senderId: String) {
        playbackStateMutex.withLock {
            if (activePlaybacks == 0) {
                idleSignal = CompletableDeferred()
            }
            activePlaybacks++
            activeSenders[senderId] = CompletableDeferred()
        }
    }

    private suspend fun endPlayback(senderId: String) {
        playbackStateMutex.withLock {
            activePlaybacks--
            activeSenders.remove(senderId)?.let { signal ->
                if (!signal.isCompleted) signal.complete(Unit)
            }
            if (activePlaybacks == 0 && !idleSignal.isCompleted) {
                idleSignal.complete(Unit)
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
