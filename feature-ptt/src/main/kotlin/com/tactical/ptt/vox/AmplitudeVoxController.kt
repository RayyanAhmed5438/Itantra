package com.tactical.ptt.vox

import com.tactical.ptt.controller.PttController
import com.tactical.platform.api.audio.AudioRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * Energy-based VOX implementation using RMS monitoring.
 * Triggers PttController.press() when RMS exceeds thresholdDb for >150ms.
 */
class AmplitudeVoxController(
    private val audioRecorder: AudioRecorder,
    private val pttController: PttController,
    private val thresholdDb: Float = -30f,
    private val activationDurationMs: Long = 150L,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) : VoxController {

    private var monitoringJob: Job? = null
    private var aboveThresholdStartTime: Long? = null
    private var isTriggered: Boolean = false

    override fun enable() {
        if (monitoringJob != null) return

        monitoringJob = audioRecorder.startMonitoring(thresholdDb)
            .onEach { rmsDb ->
                val now = System.currentTimeMillis()
                if (rmsDb >= thresholdDb) {
                    if (aboveThresholdStartTime == null) {
                        aboveThresholdStartTime = now
                    } else if (now - aboveThresholdStartTime!! >= activationDurationMs && !isTriggered) {
                        isTriggered = true
                        scope.launch {
                            pttController.press()
                        }
                    }
                } else {
                    if (isTriggered) {
                        isTriggered = false
                        scope.launch {
                            pttController.release()
                        }
                    }
                    aboveThresholdStartTime = null
                }
            }
            .catch { disable() }
            .launchIn(scope)
    }

    override fun disable() {
        monitoringJob?.cancel()
        monitoringJob = null
        if (isTriggered) {
            isTriggered = false
            scope.launch {
                pttController.release()
            }
        }
        aboveThresholdStartTime = null
    }
}