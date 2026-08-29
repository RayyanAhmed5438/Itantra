package com.tactical.platform.api.speech

import com.tactical.domain.audio.AudioFrame
import com.tactical.domain.speech.TranscriptionChunk
import kotlinx.coroutines.flow.Flow

/**
 * On-device speech-to-text. Backed by the single shared multilingual STT
 * model (ModelIdentifier("stt_multilingual")) — no language parameter,
 * since the model handles all supported languages itself. Implemented in
 * platform-android; the only module allowed to import the actual
 * TFLite/ONNX inference SDK is platform-android's speech backend, per the
 * swappable-architecture rule.
 */
interface SpeechToText {

    /**
     * Receives a stream of audio frames, performs on-device transcription,
     * and emits one finalized sentence per detected pause. The emitted
     * strings are in whatever language the speaker used — this interface
     * has no language parameter to set, and does no translation.
     */
    fun transcribe(audio: Flow<AudioFrame>): Flow<TranscriptionChunk>
}

