package com.tactical.feature.emergency.trigger

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Requires a verified 2-second continuous press before transitioning to
 * TRIGGERED. Deliberately does NOT call EmergencyBroadcaster itself —
 * per the file structure doc, that composition happens one layer up in
 * app's EmergencyViewModel, which observes state() and reacts to
 * TRIGGERED. Keeps this a pure gesture-verification state machine.
 *
 * ADDITION BEYOND SPEC: neither doc says how TRIGGERED gets back to
 * IDLE for the next press. Added reset(), to be called by the app layer
 * once it has finished handling the TRIGGERED alert (i.e. after
 * broadcasting the SOS) — otherwise the trigger would be permanently
 * stuck at TRIGGERED after the first use.
 */
class HoldPanicTrigger(
    private val scope: CoroutineScope,
    private val holdDurationMs: Long = 2_000L
) : PanicTrigger {

    private val _state = MutableStateFlow(PanicTriggerState.IDLE)
    override fun state(): StateFlow<PanicTriggerState> = _state.asStateFlow()

    private var holdJob: Job? = null

    override fun startHold() {
        if (_state.value != PanicTriggerState.IDLE) return
        _state.update { PanicTriggerState.HOLDING }
        holdJob = scope.launch {
            delay(holdDurationMs)
            _state.update { PanicTriggerState.TRIGGERED }
        }
    }

    override fun releaseHold() {
        holdJob?.cancel()
        holdJob = null
        // Releasing early (before the 2s hold completes) aborts back to
        // IDLE. Releasing after TRIGGERED has already fired does nothing
        // — the alert already went out.
        _state.update { if (it == PanicTriggerState.HOLDING) PanicTriggerState.IDLE else it }
    }

    fun reset() {
        holdJob?.cancel()
        holdJob = null
        _state.update { PanicTriggerState.IDLE }
    }
}