package com.tactical.platform.alarm

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import com.tactical.platform.api.alarm.AlarmBypass
import java.util.concurrent.atomic.AtomicInteger

/**
 * Implements AlarmBypass wrapping NotificationManager (DND) and
 * AudioManager (volume).
 *
 * Addresses the interface's own flagged gap — overlapping
 * bypassDndAndMaxVolume() calls before a matching resetVolume() — with a
 * reference count: only the FIRST call captures pre-bypass DND/volume
 * state, and only the resetVolume() that brings the count back to zero
 * actually restores it. A second emergency arriving mid-alert extends the
 * bypass instead of the first alert's resetVolume() prematurely
 * restoring real original settings. ASSUMPTION FLAGGED: this is this
 * implementation's choice, not something core.md specifies — confirm
 * with whoever owns feature-emergency that ref-counting (vs. some other
 * overlap policy) is actually the intended behavior.
 */
class AndroidAlarmBypass(private val context: Context) : AlarmBypass {

    private val notificationManager: NotificationManager by lazy {
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }
    private val audioManager: AudioManager by lazy {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    private val activeBypassCount = AtomicInteger(0)
    @Volatile private var savedInterruptionFilter: Int? = null
    @Volatile private var savedStreamVolume: Int? = null

    override suspend fun bypassDndAndMaxVolume() {
        if (activeBypassCount.getAndIncrement() == 0) {
            if (notificationManager.isNotificationPolicyAccessGranted) {
                savedInterruptionFilter = notificationManager.currentInterruptionFilter
                notificationManager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
            }
            savedStreamVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        }

        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxVolume, 0)
    }

    override suspend fun resetVolume() {
        if (activeBypassCount.updateAndGet { (it - 1).coerceAtLeast(0) } != 0) return

        savedInterruptionFilter?.let { filter ->
            if (notificationManager.isNotificationPolicyAccessGranted) {
                notificationManager.setInterruptionFilter(filter)
            }
        }
        savedInterruptionFilter = null

        savedStreamVolume?.let { volume -> audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0) }
        savedStreamVolume = null
    }
}