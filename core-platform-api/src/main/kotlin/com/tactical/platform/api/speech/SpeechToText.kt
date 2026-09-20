package com.tactical.platform.api.speech

import com.tactical.domain.audio.AudioFrame
import com.tactical.domain.speech.TranscriptionChunk
import kotlinx.coroutines.flow.Flow

/**
 * On-device speech-to-text.
 *
 * The active Android implementation is English-only Moonshine Voice. The
 * interface stays backend-neutral so the STT engine can be replaced without
 * changing feature-ptt or engine-speech.
 */
interface SpeechToText {

    /**
     * Receives a stream of PCM audio frames and emits in-progress hypotheses
     * plus finalized English sentences.
     *
     * The active backend emits languageCode = "en".
     */
    fun transcribe(audio: Flow<AudioFrame>): Flow<TranscriptionChunk>
}
