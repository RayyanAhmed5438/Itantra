package com.tactical.app.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.tactical.app.TacticalApplication
import com.tactical.app.ui.MainActivity
import com.tactical.domain.packet.EmergencyPacket
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EmergencyAlertNotifier @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun show(packet: EmergencyPacket, senderName: String = packet.sender.value.take(8)) {
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val locationText = packet.location?.let {
            String.format(
                java.util.Locale.US,
                "Location: %.6f, %.6f",
                it.latitude,
                it.longitude
            )
        }

        val body = buildString {
            append(senderName)
            append(" • ")
            append(packet.severity.name)
            append("\n")
            append(packet.description)
            if (locationText != null) {
                append("\n")
                append(locationText)
            }
        }

        val notification = NotificationCompat.Builder(
            context,
            TacticalApplication.CHANNEL_EMERGENCY
        )
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("🚨 EMERGENCY ALERT")
            .setContentText(packet.description)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(pendingIntent)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(
                EMERGENCY_NOTIFICATION_ID,
                notification
            )
        }.onFailure {
            android.util.Log.w(
                TAG,
                "Emergency notification failed",
                it
            )
        }
    }

    fun clear() {
        NotificationManagerCompat.from(context)
            .cancel(EMERGENCY_NOTIFICATION_ID)
    }

    companion object {
        private const val TAG = "EmergencyAlertNotifier"
        private const val EMERGENCY_NOTIFICATION_ID = 2201
    }
}
