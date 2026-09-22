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
import com.tactical.app.di.LocalAppDataStore
import com.tactical.emergency.receiver.EmergencyReceiver
import com.tactical.emergency.squelch.SquelchBreaker
import com.tactical.engine.discovery.service.DiscoveryService
import com.tactical.engine.mesh.service.MeshService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class TacticalMeshService : Service() {

    @Inject
    lateinit var meshService: MeshService

    @Inject
    lateinit var bleConnectionManager: com.tactical.platform.api.ble.BleConnectionManager

    @Inject
    lateinit var discoveryService: DiscoveryService

    @Inject
    lateinit var emergencyReceiver: EmergencyReceiver

    @Inject
    lateinit var emergencySquelchBreaker: SquelchBreaker

    @Inject
    lateinit var emergencyAlertNotifier: EmergencyAlertNotifier

    @Inject
    lateinit var localAppDataStore: LocalAppDataStore

    private val serviceScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val binder = MeshBinder()
    private var emergencyJob: kotlinx.coroutines.Job? = null

    inner class MeshBinder : Binder() {
        fun getService(): TacticalMeshService = this@TacticalMeshService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification())

        // Injecting MeshService causes the mesh engine to be created.
        // DiscoveryService requires an explicit start().
        if (emergencyJob?.isActive != true) {
            emergencyJob = serviceScope.launch {
                emergencyReceiver.incoming().collect { packet ->
                    val senderName =
                        localAppDataStore.callsignForPeer(packet.sender.value)
                            ?: packet.sender.value.take(8)

                    emergencyAlertNotifier.show(
                        packet = packet,
                        senderName = senderName
                    )

                    runCatching {
                        emergencySquelchBreaker.breakSquelch(packet)
                    }.onFailure { error ->
                        android.util.Log.e(
                            "TacticalMeshService",
                            "Emergency alert playback failed",
                            error
                        )
                    }
                }
            }
        }

        // Re-establish paired GATT client sessions from the foreground
        // service after process/activity recreation.
        serviceScope.launch {
            delay(1000L)
            bleConnectionManager.pairedDeviceIds().forEach { peerId ->
                runCatching {
                    bleConnectionManager.reconnectPaired(peerId)
                }
            }
        }

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
        emergencyJob?.cancel()
        emergencyJob = null

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