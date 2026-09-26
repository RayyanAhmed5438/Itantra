package com.tactical.platform.speech.mms

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Controls playback of incoming voice-message TTS without changing BLE or
 * message ordering.
 *
 * The queue stores only language/text playback jobs, not synthesized audio.
 * This keeps queued one-by-one messages from accumulating large audio buffers
 * in memory.
 *
 * In OVERLAPPING mode, each queued job is synthesized (the TTS engine still
 * serializes model inference) and its resulting AudioTrack may play while
 * another message is playing.
 *
 * In ONE_BY_ONE mode, a new job waits until all currently active playback
 * jobs have finished, then synthesizes and plays exactly one message.
 *
 * Changing modes never interrupts an AudioTrack that is already playing.
 * Messages received after the change follow the newly selected mode.
 */
@Singleton
class MmsTtsPlaybackCoordinator @Inject constructor() {

    private val queue = Channel<suspend () -> Unit>(Channel.UNLIMITED)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val stateMutex = Mutex()
    private var activePlaybacks = 0
    private var idleSignal = CompletableDeferred<Unit>().also { it.complete(Unit) }

    @Volatile
    private var playbackMode = MmsTtsPlaybackMode.OVERLAPPING

    init {
        scope.launch {
            for (job in queue) {
                if (playbackMode == MmsTtsPlaybackMode.ONE_BY_ONE) {
                    awaitIdle()
                    runJobSafely(job)
                } else {
                    beginPlayback()
                    scope.launch {
                        try {
                            runJobSafely(job)
                        } finally {
                            endPlayback()
                        }
                    }
                }
            }
        }
    }

    fun setMode(mode: MmsTtsPlaybackMode) {
        playbackMode = mode
    }

    fun currentMode(): MmsTtsPlaybackMode = playbackMode

    fun enqueue(job: suspend () -> Unit) {
        queue.trySend(job)
    }

    private suspend fun awaitIdle() {
        val signal = stateMutex.withLock {
            if (activePlaybacks == 0) null else idleSignal
        }
        signal?.await()
    }

    private suspend fun beginPlayback() {
        stateMutex.withLock {
            if (activePlaybacks == 0) {
                idleSignal = CompletableDeferred()
            }
            activePlaybacks++
        }
    }

    private suspend fun endPlayback() {
        stateMutex.withLock {
            activePlaybacks--
            if (activePlaybacks == 0 && !idleSignal.isCompleted) {
                idleSignal.complete(Unit)
            }
        }
    }

    private suspend fun runJobSafely(job: suspend () -> Unit) {
        runCatching { job() }
            .onFailure { error ->
                if (error is kotlinx.coroutines.CancellationException) throw error
                android.util.Log.w(
                    TAG,
                    "Incoming voice-message playback failed: " +
                        (error.message ?: error.javaClass.simpleName)
                )
            }
    }

    companion object {
        private const val TAG = "MmsTtsPlayback"
    }
}
