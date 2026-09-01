package com.tactical.engine.speech.pipeline

import com.tactical.domain.audio.AudioFrame
import com.tactical.domain.speech.LanguageTag
import com.tactical.platform.api.speech.SpeechToText
import com.tactical.platform.api.speech.TextToSpeech
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * Feeds audio through [speechToText], takes the first finalized sentence
 * (isFinal == true), and hands its text to [textToSpeech], per
 * architecture handbook §6.7 ("STT emits final chunks; the pipeline takes
 * the first finalized sentence and feeds it to TTS").
 *
 * ASSUMPTION FLAGGED: neither doc says what should happen if the audio
 * flow completes before any chunk is finalized (e.g. a clip too short to
 * trigger the ~400ms silence pause SpeechToText waits for). `first {}`
 * would throw NoSuchElementException in that case rather than returning
 * anything — reasonable for a self-test utility surfacing a clear error,
 * but worth confirming that's the intended behavior rather than, say,
 * falling back to the last partial chunk seen.
 *
 * Note: SpeechToText.transcribe() takes no language parameter — the
 * shared multilingual model detects/handles the spoken language itself.
 * [lang] here only selects the *output* voice for TextToSpeech.synthesize().
 */
class DefaultSpeechPipeline(
    private val speechToText: SpeechToText,
    private val textToSpeech: TextToSpeech
) : SpeechPipeline {

    override suspend fun process(audio: Flow<AudioFrame>, lang: LanguageTag): AudioFrame {
        val finalChunk = speechToText.transcribe(audio).first { it.isFinal }
        return textToSpeech.synthesize(finalChunk.text, lang)
    }
}
