package com.tactical.ptt.controller

import com.tactical.domain.audio.AudioConfig
import com.tactical.domain.identity.DeviceId
import com.tactical.platform.api.audio.AudioRecorder
import com.tactical.platform.api.speech.SpeechToText
import com.tactical.platform.api.speech.TranscriptionChunk
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

/**
 * Builds a fresh PttSession on every press(), reused for the whole
 * recording so PttPacketBuilder.build(session, chunk) has consistent
 * metadata across possibly-multiple partial chunks. onTransmitComplete()
 * fires after every dispatch attempt (success or failure) — flagged
 * earlier as an open question against PttHapticFeedback's doc comment
 * ("Triggered when a packet is successfully handed off"); kept as-is
 * since unconfirmed, but a UI probably wants different feedback for a
 * failed transmission than a successful one.
 */
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
    private var currentSession: PttSession? = null

    override suspend fun press() {
        if (_state.value.sessionState != SessionState.IDLE) return

        val session = PttSession(deviceId = deviceId)
        currentSession = session

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
        hapticFeedback.onTransmitComplete()
    }
}
