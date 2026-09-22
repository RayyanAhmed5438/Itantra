package com.tactical.platform.speech

import com.tactical.domain.audio.AudioFrame
import com.tactical.domain.speech.LanguageTag
import com.tactical.platform.api.speech.TextToSpeech
import com.tactical.platform.speech.mms.MmsTtsEngine
import com.tactical.platform.speech.mms.MmsTtsLanguage
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Adapts the currently verified MMS TTS implementation to the generic
 * TextToSpeech contract used by feature-emergency.
 */
@Singleton
class MmsTextToSpeechAdapter @Inject constructor(
    private val engine: MmsTtsEngine
) : TextToSpeech {

    override suspend fun synthesize(
        text: String,
        langTag: LanguageTag
    ): AudioFrame {
        val language = MmsTtsLanguage.fromIsoCode(langTag.isoCode)
            ?: throw IllegalArgumentException(
                "Emergency TTS language '" + langTag.isoCode + "' is not bundled"
            )

        return engine.synthesize(language, text).first
    }
}
