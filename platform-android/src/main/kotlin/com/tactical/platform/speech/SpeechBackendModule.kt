package com.tactical.platform.speech

import com.tactical.platform.api.speech.SpeechToText
import com.tactical.platform.api.speech.TextToSpeech
import com.tactical.platform.speech.backend.tflite.TfliteSpeechToText
import com.tactical.platform.speech.backend.tflite.TfliteTextToSpeech
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * THE ONE FILE YOU EDIT TO SWAP INFERENCE BACKENDS.
 *
 * Binds SpeechToText/TextToSpeech to exactly one backend package —
 * currently TFLite. To switch to ONNX Runtime Mobile, change the two
 * @Binds return types below to com.tactical.platform.speech.backend.onnx.
 * OnnxSpeechToText / OnnxTextToSpeech and nothing else needs to change —
 * engine-speech and every feature module only ever see the
 * SpeechToText/TextToSpeech interfaces from core-platform-api, never a
 * concrete backend type.
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
    abstract fun bindSpeechToText(impl: TfliteSpeechToText): SpeechToText

    @Binds
    @Singleton
    abstract fun bindTextToSpeech(impl: TfliteTextToSpeech): TextToSpeech
}