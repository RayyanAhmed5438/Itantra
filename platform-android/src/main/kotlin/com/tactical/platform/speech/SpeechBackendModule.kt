package com.tactical.platform.speech

import com.tactical.platform.api.speech.SpeechToText
import com.tactical.platform.api.speech.TextToSpeech
import com.tactical.platform.speech.backend.tflite.TfliteTextToSpeech
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Backend selection seam for speech.
 *
 * STT is Moonshine's English-only streaming recognizer.
 * TTS remains on the existing MMS implementation elsewhere in platform-android.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class SpeechBackendModule {

    @Binds
    @Singleton
    abstract fun bindSpeechToText(impl: MoonshineSpeechToText): SpeechToText

    // Kept for the legacy backend API; the app's active TTS path uses MMS directly.
    @Binds
    @Singleton
    abstract fun bindTextToSpeech(impl: TfliteTextToSpeech): TextToSpeech
}
