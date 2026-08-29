package com.tactical.feature.ptt

import com.tactical.domain.audio.AudioConfig
import com.tactical.domain.audio.AudioFrame
import com.tactical.domain.identity.DeviceId
import com.tactical.domain.packet.Packet
import com.tactical.domain.result.TacticalResult
import com.tactical.domain.speech.LanguageTag
import com.tactical.feature.ptt.controller.DefaultPttController
import com.tactical.feature.ptt.feedback.PttHapticFeedback
import com.tactical.feature.ptt.relay.PttMeshDispatcher
import com.tactical.feature.ptt.relay.PttPacketBuilder
import com.tactical.feature.ptt.session.SessionState
import com.tactical.engine.mesh.service.MeshService
import com.tactical.platform.api.audio.AudioRecorder
import com.tactical.platform.api.speech.SpeechToText
import com.tactical.platform.api.speech.TranscriptionChunk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultPttControllerTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    // Fake AudioRecorder
    private val fakeAudioRecorder = object : AudioRecorder {
        var startCalled = false
        var stopCalled = false
        override fun start(config: AudioConfig): Flow<AudioFrame> {
            startCalled = true
            return flowOf(AudioFrame(byteArrayOf(1, 2, 3), 0L))
        }
        override suspend fun stop() { stopCalled = true }
        override fun startMonitoring(thresholdDb: Float): Flow<Float> = flowOf(-20f)
    }

    // Fake SpeechToText
    private val fakeSpeechToText = object : SpeechToText {
        override fun transcribe(audio: Flow<AudioFrame>, languageTag: LanguageTag): Flow<TranscriptionChunk> {
            return flowOf(TranscriptionChunk(text = "Bravo Two, standing by", isFinal = true))
        }
    }

    // Fake HapticFeedback
    private val fakeHaptics = object : PttHapticFeedback {
        var pressTriggered = false
        var releaseTriggered = false
        var transmitTriggered = false
        override suspend fun onPress() { pressTriggered = true }
        override suspend fun onRelease() { releaseTriggered = true }
        override suspend fun onTransmitComplete() { transmitTriggered = true }
    }

    // Fake MeshService
    private val fakeMeshService = object : MeshService {
        var sentPacket: Packet? = null
        override suspend fun send(packet: Packet): TacticalResult<Unit> {
            sentPacket = packet
            return TacticalResult.Success(Unit)
        }
        override fun incoming(): Flow<Packet> = flowOf()
    }

    @Test
    fun testPttFlowTransitionsAndTransmission() = testScope.runTest {
        val dispatcher = PttMeshDispatcher(fakeMeshService)
        val builder = PttPacketBuilder()
        val controller = DefaultPttController(
            localDeviceId = DeviceId("device-001"),
            audioRecorder = fakeAudioRecorder,
            speechToText = fakeSpeechToText,
            packetBuilder = builder,
            meshDispatcher = dispatcher,
            hapticFeedback = fakeHaptics,
            scope = this
        )

        // 1. Initial state
        assertEquals(SessionState.IDLE, controller.state().value.sessionState)

        // 2. Press PTT
        controller.press()
        testScheduler.advanceUntilIdle()
        assertTrue(fakeAudioRecorder.startCalled)
        assertTrue(fakeHaptics.pressTriggered)
        assertEquals("Bravo Two, standing by", controller.state().value.lastTranscription)

        // 3. Release PTT
        controller.release()
        testScheduler.advanceUntilIdle()
        assertTrue(fakeAudioRecorder.stopCalled)
        assertTrue(fakeHaptics.releaseTriggered)
        assertTrue(fakeHaptics.transmitTriggered)
        assertTrue(controller.state().value.lastResult is TacticalResult.Success)
        assertEquals(SessionState.IDLE, controller.state().value.sessionState)
    }
}