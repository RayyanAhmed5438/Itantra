package com.tactical.app.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.tactical.app.TacticalApplication
import com.tactical.app.ui.MainActivity
import com.tactical.engine.discovery.service.DiscoveryService
import com.tactical.engine.mesh.service.MeshService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class TacticalMeshService : Service() {

    @Inject
    lateinit var meshService: MeshService

    @Inject
    lateinit var discoveryService: DiscoveryService

    private val serviceScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val binder = MeshBinder()

    inner class MeshBinder : Binder() {
        fun getService(): TacticalMeshService = this@TacticalMeshService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification())

        // Injecting MeshService causes the mesh engine to be created.
        // DiscoveryService requires an explicit start().
        serviceScope.launch {
            try {
                discoveryService.start()
            } catch (e: SecurityException) {
                android.util.Log.e(
                    "TacticalMeshService",
                    "Missing permission required for mesh discovery",
                    e
                )
            } catch (e: Exception) {
                android.util.Log.e(
                    "TacticalMeshService",
                    "Failed to start mesh discovery",
                    e
                )
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        serviceScope.launch {
            discoveryService.stop()
        }

        serviceScope.cancel()
        super.onDestroy()
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, TacticalApplication.CHANNEL_MESH)
            .setContentTitle("Itantra Mesh Active")
            .setContentText("Wi-Fi Direct / BLE mesh service running")
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val NOTIFICATION_ID = 1001
    }
}