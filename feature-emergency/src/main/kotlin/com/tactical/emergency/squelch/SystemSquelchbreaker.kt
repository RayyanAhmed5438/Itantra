package com.tactical.feature.emergency.squelch

import com.tactical.domain.packet.EmergencyPacket
import com.tactical.domain.speech.LanguageTag
import com.tactical.platform.api.alarm.AlarmBypass
import com.tactical.platform.api.audio.AlertTone
import com.tactical.platform.api.audio.AudioPlayer
import com.tactical.platform.api.flashlight.FlashlightController
import com.tactical.platform.api.speech.TextToSpeech
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Sequence on breakSquelch(): stopPlayback() -> bypassDndAndMaxVolume()
 * -> strobe() (fire-and-forget, since it loops until off()) -> playAlert
 * siren -> TTS.synthesize(description, languageCode) -> play the result.
 * 30s auto-reset, or immediate dismiss() from the UI.
 *
 * ASSUMPTION FLAGGED (language type): EmergencyPacket.languageCode is a
 * raw String, but TextToSpeech.synthesize() takes a LanguageTag
 * (isoCode + backendId). Here it's constructed as
 * LanguageTag(isoCode = code, backendId = code) — a placeholder, since
 * feature-emergency doesn't depend on engine-speech's SupportedLanguages
 * registry per its own map.txt. Same gap flagged in engine-speech's
 * SupportedLanguages.kt (real backendId values don't exist yet either).
 *
 * ASSUMPTION FLAGGED (concurrent alerts): AlarmBypass's own doc comment
 * raises exactly this question — what happens if breakSquelch() fires a
 * second time before dismiss() from the first call has run? A Mutex
 * serializes breakSquelch()/dismiss() here, and a second incoming alert
 * simply restarts the 30s timer rather than stacking bypasses — not a
 * confirmed design decision, just a reasonable default.
 */
class SystemSquelchBreaker(
    private val audioPlayer: AudioPlayer,
    private val alarmBypass: AlarmBypass,
    private val flashlightController: FlashlightController,
    private val textToSpeech: TextToSpeech,
    private val scope: CoroutineScope,
    private val autoResetMs: Long = 30_000L
) : SquelchBreaker {

    private val mutex = Mutex()
    private var resetJob: Job? = null
    private var strobeJob: Job? = null
    private var active = false

    override suspend fun breakSquelch(packet: EmergencyPacket) {
        mutex.withLock {
            resetJob?.cancel()
            strobeJob?.cancel()

            audioPlayer.stopPlayback()
            alarmBypass.bypassDndAndMaxVolume()
            strobeJob = scope.launch { flashlightController.strobe(intervalMs = 500) }
            audioPlayer.playAlert(AlertTone.EMERGENCY_INCOMING)

            val langTag = LanguageTag(isoCode = packet.languageCode, backendId = packet.languageCode)
            val frame = textToSpeech.synthesize(packet.description, langTag)
            audioPlayer.play(frame)

            active = true
            resetJob = scope.launch {
                delay(autoResetMs)
                dismiss()
            }
        }
    }

    override suspend fun dismiss() {
        mutex.withLock {
            if (!active) return@withLock
            resetJob?.cancel()
            resetJob = null
            strobeJob?.cancel()
            strobeJob = null
            alarmBypass.resetVolume()
            flashlightController.off()
            active = false
        }
    }
}