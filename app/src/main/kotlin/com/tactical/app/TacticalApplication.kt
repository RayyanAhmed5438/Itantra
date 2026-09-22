package com.tactical.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.tactical.platform.speech.SpeechLanguagePreferences
import com.tactical.platform.speech.mms.MmsTtsEngine
import com.tactical.platform.speech.mms.MmsTtsLanguage
import com.tactical.platform.speech.mms.MmsTtsModelStore
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Provider

@HiltAndroidApp
class TacticalApplication : Application() {

    @Inject
    lateinit var speechLanguagePreferences: SpeechLanguagePreferences

    @Inject
    lateinit var ttsEngineProvider: Provider<MmsTtsEngine>

    @Inject
    lateinit var ttsModelStore: MmsTtsModelStore

    private val ttsPreloadScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        cleanupSpeechExtractionCache()
        ttsModelStore.cleanupUnbundledModels()
        createNotificationChannels()
        preloadSelectedTtsLanguage()
    }

    private fun preloadSelectedTtsLanguage() {
        val language = MmsTtsLanguage.fromIsoCode(
            speechLanguagePreferences.selectedLanguageCode
        ) ?: return

        ttsPreloadScope.launch {
            runCatching {
                ttsEngineProvider.get().preload(language)
            }.onFailure { error ->
                android.util.Log.w(
                    "TacticalApplication",
                    "TTS preload failed: " + (error.message ?: error.javaClass.simpleName)
                )
            }
        }
    }

    /**
     * Speech model installers use app-private data for staging so large model
     * extraction does not inflate Android's disposable cache bucket. A process
     * kill during extraction can leave a partial staging directory behind, so
     * remove those directories on the next startup.
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

        filesDir.listFiles()
            ?.filter {
                it.name.startsWith(".tts_model_staging_") ||
                    it.name.startsWith(".moonshine_stt_staging_") ||
                    it.name.startsWith(".vosk_hi_bundle_staging_")
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
