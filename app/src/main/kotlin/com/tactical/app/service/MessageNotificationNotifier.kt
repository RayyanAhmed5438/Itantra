package com.tactical.app.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.tactical.app.TacticalApplication
import com.tactical.app.ui.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageNotificationNotifier @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val activeNotificationIds = ConcurrentHashMap.newKeySet<Int>()

    fun show(
        senderName: String,
        message: String,
        isVoice: Boolean
    ) {
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val title = if (isVoice) {
            "VOICE MESSAGE • $senderName"
        } else {
            "NEW MESSAGE • $senderName"
        }

        val notification = NotificationCompat.Builder(
            context,
            TacticalApplication.CHANNEL_MESSAGES
        )
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .build()

        runCatching {
            val notificationId = nextNotificationId()
            NotificationManagerCompat.from(context).notify(
                notificationId,
                notification
            )
            activeNotificationIds.add(notificationId)
        }.onFailure { error ->
            android.util.Log.w(
                TAG,
                "Message notification failed",
                error
            )
        }
    }

    /**
     * Cancels only Itantra's message notifications. Emergency and mesh
     * notifications use different notification IDs and remain untouched.
     */
    fun clearMessageNotifications() {
        val manager = NotificationManagerCompat.from(context)
        activeNotificationIds.forEach { id ->
            runCatching { manager.cancel(id) }
        }
        activeNotificationIds.clear()
    }

    private fun nextNotificationId(): Int =
        notificationId.updateAndGet { current ->
            if (current >= 2999) 2000 else current + 1
        }

    companion object {
        private const val TAG = "MessageNotifier"
        private val notificationId = AtomicInteger(2000)
    }
}
