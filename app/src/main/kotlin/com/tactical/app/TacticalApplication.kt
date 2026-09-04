package com.tactical.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class TacticalApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val meshChannel = NotificationChannel(
                CHANNEL_MESH,
                "SENTINEL Mesh Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitors tactical mesh network connections and incoming transmissions."
            }

            val emergencyChannel = NotificationChannel(
                CHANNEL_EMERGENCY,
                "SENTINEL Emergency Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "High-priority distress alerts and squelch breaker announcements."
                enableVibration(true)
            }

            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(meshChannel)
            manager.createNotificationChannel(emergencyChannel)
        }
    }

    companion object {
        const val CHANNEL_MESH = "channel_sentinel_mesh"
        const val CHANNEL_EMERGENCY = "channel_sentinel_emergency"
    }
}
