package com.tactical.ptt.controller

import com.tactical.domain.audio.AudioConfig
import com.tactical.domain.identity.DeviceId
import com.tactical.ptt.feedback.PttHapticFeedback
import com.tactical.ptt.relay.PttMeshDispatcher
import com.tactical.ptt.relay.PttPacketBuilder
import com.tactical.ptt.session.SessionState
import com.tactical.platform.api.audio.AudioRecorder
import com.tactical.platform.api.speech.SpeechToText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class DefaultPttController(
    private val deviceId: DeviceId,
    private val audioRecorder: AudioRecorder,
    private val speechToText: SpeechToText,
    private val packetBuilder: PttPacketBuilder,
    private val meshDispatcher: PttMeshDispatcher,
    private val hapticFeedback: PttHapticFeedback,
    private val scope: CoroutineScope,
    private val audioConfig: AudioConfig = AudioConfig(),
    private val releaseGraceMs: Long = 600L
) : PttController {

    private val _state = MutableStateFlow(PttState())
    override fun state(): StateFlow<PttState> = _state.asStateFlow()

    private var sessionJob: Job? = null

    override suspend fun press() {
        if (_state.value.sessionState != SessionState.IDLE) return

        _state.update {
            it.copy(sessionState = SessionState.ARMED, lastTranscription = null, lastResult = null)
        }
        hapticFeedback.onPress()

        _state.update { it.copy(sessionState = SessionState.RECORDING) }
        val frames = audioRecorder.start(audioConfig)

        sessionJob = scope.launch {
            speechToText.transcribe(frames).collect { chunk ->
                if (chunk.isFinal) {
                    _state.update {
                        it.copy(lastTranscription = chunk.text, sessionState = SessionState.TRANSMITTING)
                    }
                    transmit(chunk.text, chunk.languageCode)
                    _state.update { it.copy(sessionState = SessionState.IDLE) }
                } else {
                    _state.update { it.copy(lastTranscription = chunk.text) }
                }
            }
        }
    }

    override suspend fun release() {
        hapticFeedback.onRelease()
        audioRecorder.stop()

        val job = sessionJob
        if (job != null) {
            withTimeoutOrNull(releaseGraceMs) { job.join() }
            job.cancel()
            sessionJob = null
            if (_state.value.sessionState != SessionState.TRANSMITTING) {
                _state.update { it.copy(sessionState = SessionState.IDLE) }
            }
        } else {
            _state.update { it.copy(sessionState = SessionState.IDLE) }
        }
    }

    private suspend fun transmit(text: String, languageCode: String) {
        val packet = packetBuilder.build(text = text, languageCode = languageCode, sender = deviceId)
        val result = meshDispatcher.dispatch(packet)
        _state.update { it.copy(lastResult = result) }
        hapticFeedback.onTransmitComplete()
    }
}
