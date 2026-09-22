package com.tactical.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.tactical.platform.speech.SpeechLanguagePreferences
import com.tactical.platform.speech.mms.MmsTtsEngine
import com.tactical.platform.speech.mms.MmsTtsLanguage
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class TacticalApplication : Application() {

    @Inject
    lateinit var speechLanguagePreferences: SpeechLanguagePreferences

    @Inject
    lateinit var ttsEngine: MmsTtsEngine

    private val ttsPreloadScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        cleanupSpeechExtractionCache()
        createNotificationChannels()
        preloadSelectedTtsLanguage()
    }

    private fun preloadSelectedTtsLanguage() {
        val language = MmsTtsLanguage.fromIsoCode(
            speechLanguagePreferences.selectedLanguageCode
        ) ?: return

        ttsPreloadScope.launch {
            runCatching {
                ttsEngine.preload(language)
            }.onFailure { error ->
                android.util.Log.w(
                    "TacticalApplication",
                    "TTS preload failed: " + (error.message ?: error.javaClass.simpleName)
                )
            }
        }
    }

    /**
     * Older speech-model extractors used cacheDir for temporary ZIP extraction.
     * A process kill during extraction can leave those temporary directories
     * behind permanently. They contain no live model state and are safe to
     * remove on application startup.
     */
    private fun cleanupSpeechExtractionCache() {
        val cache = cacheDir
        cache.listFiles()
            ?.filter {
                it.name.startsWith("tts_bundle_") ||
                    it.name.startsWith("tts_model_") ||
                    it.name.startsWith("moonshine_stt_") ||
                    it.name.startsWith("vosk_hi_bundle_")
            }
            ?.forEach { file ->
                runCatching { file.deleteRecursively() }
            }
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val meshChannel = NotificationChannel(
                CHANNEL_MESH,
                "Itantra Mesh Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitors tactical mesh network connections and incoming transmissions."
            }

            val messageChannel = NotificationChannel(
                CHANNEL_MESSAGES,
                "Itantra Messages",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications for incoming text and voice messages."
                setShowBadge(true)
            }

            val emergencyChannel = NotificationChannel(
                CHANNEL_EMERGENCY,
                "Itantra Emergency Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "High-priority distress alerts and squelch breaker announcements."
                enableVibration(true)
            }

            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(meshChannel)
            manager.createNotificationChannel(messageChannel)
            manager.createNotificationChannel(emergencyChannel)
        }
    }

    companion object {
        const val CHANNEL_MESH = "channel_sentinel_mesh"
        const val CHANNEL_EMERGENCY = "channel_sentinel_emergency"
        const val CHANNEL_MESSAGES = "channel_itantra_messages"
    }
}
