package com.tactical.engine.speech.pipeline

import com.tactical.domain.audio.AudioFrame
import com.tactical.domain.speech.LanguageTag
import kotlinx.coroutines.flow.Flow

/**
 * Composes on-device STT and TTS into a single local loopback: speak into
 * the mic, get synthesized speech back out, all on one device. This is
 * NOT the real cross-device PTT/VOX path (that's split across two phones:
 * feature-ptt talks to SpeechToText directly on the sender, MeshService
 * carries the text, and the receiver's TTS is invoked separately — see
 * the data-flow doc §3/§4). This pipeline exists for local self-test /
 * "does STT+TTS round-trip correctly on this device" verification, per
 * architecture handbook §6.7 and file-structure doc's own note on
 * SpeechPipeline.kt.
 */
interface SpeechPipeline {
    /**
     * Transcribes the given audio, then synthesizes the transcribed text
     * back into speech in the given language.
     */
    suspend fun process(audio: Flow<AudioFrame>, lang: LanguageTag): AudioFrame
}
