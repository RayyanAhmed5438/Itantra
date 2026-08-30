package com.tactical.ptt.vox

import com.tactical.domain.audio.AudioConfig
import com.tactical.platform.api.audio.AudioRecorder
import com.tactical.ptt.controller.PttController
import com.tactical.ptt.session.SessionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * VOX ("phone mode") — decides WHEN to call PttController.press()/
 * release(); all recording/STT/transmission logic stays inside
 * PttController, not duplicated here.
 *
 * AudioRecorder.startMonitoring() emits Flow<Unit>, not RMS values — the
 * >150ms-above-threshold debounce already happens inside the
 * AudioRecorder implementation, so this class just reacts to each
 * emission as "speech detected." Since startMonitoring() has no
 * "silence resumed" signal, `speaking` is reset by watching
 * pttController.state() for a return to IDLE instead.
 */
class AmplitudeVoxController(
    private val audioRecorder: AudioRecorder,
    private val pttController: PttController,
    private val audioConfig: AudioConfig = AudioConfig(),
    private val thresholdDb: Double = -40.0,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) : VoxController {

    private var monitoringJob: Job? = null
    private var stateWatcherJob: Job? = null
    private var speaking = false

    override fun enable() {
        if (monitoringJob != null) return

        stateWatcherJob = pttController.state()
            .onEach { state -> if (state.sessionState == SessionState.IDLE) speaking = false }
            .launchIn(scope)

        monitoringJob = audioRecorder.startMonitoring(audioConfig, thresholdDb)
            .onEach {
                if (!speaking) {
                    speaking = true
                    pttController.press()
                }
            }
            .catch { disable() }
            .launchIn(scope)
    }

    override fun disable() {
        monitoringJob?.cancel()
        monitoringJob = null
        stateWatcherJob?.cancel()
        stateWatcherJob = null
        if (speaking) {
            speaking = false
            scope.launch { pttController.release() }
        }
    }
}
