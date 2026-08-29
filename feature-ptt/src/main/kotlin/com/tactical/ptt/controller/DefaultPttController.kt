package com.tactical.ptt.controller

import com.tactical.domain.audio.AudioConfig
import com.tactical.domain.identity.DeviceId
import com.tactical.domain.result.TacticalResult
import com.tactical.domain.speech.LanguageTag
import com.tactical.ptt.feedback.PttHapticFeedback
import com.tactical.ptt.relay.PttMeshDispatcher
import com.tactical.ptt.relay.PttPacketBuilder
import com.tactical.ptt.session.PttSession
import com.tactical.ptt.session.SessionState
import com.tactical.api.audio.AudioRecorder
import com.tactical.api.speech.SpeechToText
import com.tactical.api.speech.TranscriptionChunk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * State machine: IDLE -> ARMED -> RECORDING -> TRANSMITTING -> IDLE.
 * Transmits only on final, non-empty transcription chunk and surfaces TacticalResult.
 */
class DefaultPttController(
    private val localDeviceId: DeviceId,
    private val audioRecorder: AudioRecorder,
    private val speechToText: SpeechToText,
    private val packetBuilder: PttPacketBuilder,
    private val meshDispatcher: PttMeshDispatcher,
    private val hapticFeedback: PttHapticFeedback,
    private val defaultLanguageTag: LanguageTag = LanguageTag("en-US"),
    private val audioConfig: AudioConfig = AudioConfig(),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) : PttController {

    private val mutex = Mutex()
    private val _state = MutableStateFlow(PttState())
    private var currentSession: PttSession? = null
    private var transcriptionJob: Job? = null
    private var latestFinalChunk: TranscriptionChunk? = null

    override fun state(): StateFlow<PttState> = _state.asStateFlow()

    override suspend fun press() = mutex.withLock {
        if (_state.value.sessionState != SessionState.IDLE) return@withLock

        // Transition: IDLE -> ARMED
        _state.update { it.copy(sessionState = SessionState.ARMED, lastResult = null) }
        hapticFeedback.onPress()

        val session = PttSession(
            deviceId = localDeviceId,
            languageTag = defaultLanguageTag
        )
        currentSession = session
        latestFinalChunk = null

        val audioStream = audioRecorder.start(audioConfig)

        // Transition: ARMED -> RECORDING
        _state.update { it.copy(sessionState = SessionState.RECORDING) }

        transcriptionJob = speechToText.transcribe(audioStream, defaultLanguageTag)
            .onEach { chunk ->
                _state.update { it.copy(lastTranscription = chunk.text) }
                if (chunk.isFinal) {
                    latestFinalChunk = chunk
                }
            }
            .catch { e ->
                _state.update {
                    it.copy(
                        sessionState = SessionState.IDLE,
                        lastResult = TacticalResult.Failure(e)
                    )
                }
            }
            .launchIn(scope)
    }

    override suspend fun release() = mutex.withLock {
        if (_state.value.sessionState != SessionState.RECORDING) return@withLock

        hapticFeedback.onRelease()
        audioRecorder.stop()
        transcriptionJob?.cancel()
        transcriptionJob = null

        val session = currentSession
        val chunk = latestFinalChunk

        if (session != null && chunk != null && chunk.text.isNotBlank()) {
            // Transition: RECORDING -> TRANSMITTING
            _state.update { it.copy(sessionState = SessionState.TRANSMITTING) }

            val packet = packetBuilder.build(session, chunk)
            val result = meshDispatcher.dispatch(packet)

            if (result is TacticalResult.Success) {
                hapticFeedback.onTransmitComplete()
            }

            // Transition: TRANSMITTING -> IDLE
            _state.update {
                it.copy(
                    sessionState = SessionState.IDLE,
                    lastResult = result
                )
            }
        } else {
            _state.update { it.copy(sessionState = SessionState.IDLE) }
        }

        currentSession = null
        latestFinalChunk = null
    }
}