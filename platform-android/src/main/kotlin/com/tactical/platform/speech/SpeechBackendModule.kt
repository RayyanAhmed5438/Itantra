package com.tactical.platform.speech

import com.tactical.platform.api.speech.SpeechToText
import com.tactical.platform.api.speech.TextToSpeech
import com.tactical.platform.speech.backend.tflite.TfliteTextToSpeech
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * THE ONE FILE YOU EDIT TO SWAP INFERENCE BACKENDS.
 *
 * Binds STT and TTS to their active platform backends. STT is routed by the
 * saved outgoing language: Hindi uses the offline Vosk small Hindi model,
 * English uses Moonshine Tiny Streaming. TTS remains on the existing
 * TFLite implementation used by this module's speech wiring.
 * Feature modules only see the backend-neutral interfaces from
 * core-platform-api.
 *
 * Deliberately does NOT bind ModelProvider or ModelDownloadManager here —
 * those are backend-independent (both backends read the same extracted
 * model files the same way, via AssetModelProvider), so they belong in a
 * general platform Hilt module, not this backend-swap seam. That general
 * module isn't among platform-android's listed files yet — flag for
 * whoever wires DI that AssetModelProvider/DynamicModelDownloadManager
 * still need a @Binds/@Provides home somewhere.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class SpeechBackendModule {

    @Binds
    @Singleton
    abstract fun bindSpeechToText(impl: RoutingSpeechToText): SpeechToText

    @Binds
    @Singleton
    abstract fun bindTextToSpeech(impl: TfliteTextToSpeech): TextToSpeech
}