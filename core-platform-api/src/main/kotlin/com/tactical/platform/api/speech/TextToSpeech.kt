package com.tactical.platform.api.speech

import com.tactical.domain.audio.AudioFrame
import com.tactical.domain.speech.LanguageTag

/**
 * On-device text-to-speech. Backed by the single shared multilingual TTS
 * model (ModelIdentifier("tts_multilingual")) — langTag selects which
 * voice/language the model speaks in at inference time, since there's no
 * separate model file per language to pick instead. Must run fully
 * offline, same as SpeechToText. Implemented in platform-android.
 */
interface TextToSpeech {

    /**
     * Synthesizes text into speech in the given language, returning the
     * result as a single AudioFrame.
     */
    suspend fun synthesize(text: String, langTag: LanguageTag): AudioFrame
}