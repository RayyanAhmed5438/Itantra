package com.tactical.ptt.controller

import com.tactical.domain.audio.AudioConfig
import com.tactical.domain.identity.DeviceId
import com.tactical.domain.result.TacticalResult
import com.tactical.platform.api.audio.AudioRecorder
import com.tactical.platform.api.speech.SpeechToText
import com.tactical.domain.speech.TranscriptionChunk
import com.tactical.ptt.feedback.PttHapticFeedback
import com.tactical.ptt.relay.PttMeshDispatcher
import com.tactical.ptt.relay.PttPacketBuilder
import com.tactical.ptt.session.PttSession
import com.tactical.ptt.session.SessionState
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
    private val releaseGraceMs: Long = 5000L
) : PttController {

    private val _state = MutableStateFlow(PttState())
    override fun state(): StateFlow<PttState> = _state.asStateFlow()

    private var sessionJob: Job? = null
    private var currentSession: PttSession? = null

    override suspend fun press(isVox: Boolean) {
        if (_state.value.sessionState != SessionState.IDLE) return

        val session = PttSession(deviceId = deviceId, isVox = isVox)
        currentSession = session

        _state.update {
            it.copy(
                sessionState = SessionState.ARMED,
                sessionId = session.sessionId,
                lastTranscription = null,
                lastResult = null
            )
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
                    transmit(session, chunk)
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

    private suspend fun transmit(session: PttSession, chunk: TranscriptionChunk) {
        val packet = packetBuilder.build(session, chunk)
        val result = meshDispatcher.dispatch(packet)
        _state.update { it.copy(lastResult = result) }

        when (result) {
            is TacticalResult.Success -> hapticFeedback.onTransmitComplete()
            is TacticalResult.Failure -> hapticFeedback.onTransmitFailed()
        }
    }
}
