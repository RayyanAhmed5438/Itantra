package com.tactical.platform.wifi

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pDeviceList
import android.net.wifi.p2p.WifiP2pManager
import android.net.wifi.p2p.WifiP2pManager.Channel
import android.os.Build
import com.tactical.domain.result.TacticalResult
import com.tactical.platform.api.wifi.WifiDirectManager
import com.tactical.platform.api.wifi.WifiDirectPeer
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class AndroidWifiDirectManager(
    private val context: Context,
    private val wifiP2pManager: WifiP2pManager,
    private val wifichannel: Channel
) : WifiDirectManager {

    override suspend fun discoverPeers(): Flow<List<WifiDirectPeer>> = callbackFlow {


        if (!hasWifiDirectPermission()) {
            android.util.Log.w(
                TAG,
                "Wi-Fi Direct discovery skipped: missing"
            )
            close()
            return@callbackFlow
        }

        if (!isLocationEnabled()) {
            android.util.Log.w(
                TAG,
                "Wi-Fi Direct discovery skipped: Location Mode is OFF"
            )
            close()
            return@callbackFlow
        }

        val peerListListener =
            WifiP2pManager.PeerListListener { deviceList: WifiP2pDeviceList ->
                trySend(
                    deviceList.deviceList.map { it.toWifiDirectPeer() }
                )
            }

        val receiver = object : BroadcastReceiver() {

            override fun onReceive(
                ctx: Context,
                intent: Intent
            ) {
                if (
                    intent.action ==
                    WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION
                ) {
                    if (hasWifiDirectPermission()) {
                        try {
                            wifiP2pManager.requestPeers(
                                wifichannel,
                                peerListListener
                            )
                        } catch (e: SecurityException) {
                            android.util.Log.w(
                                TAG,
                                "Wi-Fi Direct requestPeers permission denied",
                                e
                            )
                        }
                    }
                }
            }
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(
                    receiver,
                    IntentFilter(
                        WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION
                    ),
                    Context.RECEIVER_NOT_EXPORTED
                )
            } else {
                @Suppress("DEPRECATION")
                context.registerReceiver(
                    receiver,
                    IntentFilter(
                        WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION
                    )
                )
            }

            wifiP2pManager.discoverPeers(
                wifichannel,
                object : WifiP2pManager.ActionListener {

                    override fun onSuccess() {
                        android.util.Log.d(
                            TAG,
                            "Wi-Fi Direct peer discovery started"
                        )
                    }

                    override fun onFailure(reasonCode: Int) {
                        android.util.Log.w(
                            TAG,
                            "Wi-Fi Direct discovery failed: " +
                                    reasonCode.toReasonString()
                        )
                    }
                }
            )

        } catch (e: SecurityException) {
            android.util.Log.w(
                TAG,
                "Wi-Fi Direct discovery permission denied",
                e
            )
            close()
        } catch (e: Exception) {
            android.util.Log.e(
                TAG,
                "Wi-Fi Direct discovery failed",
                e
            )
            close()
        }

        awaitClose {
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Exception) {
                // Receiver was already unregistered.
            }
        }
    }

    override suspend fun connect(
        deviceId: String
    ): TacticalResult<Unit> {

        if (!hasWifiDirectPermission()) {
            return TacticalResult.Failure(
                "Missing Wi-Fi Direct permission"
            )
        }

        return suspendCancellableCoroutine { continuation ->

            val config = WifiP2pConfig().apply {
                this.deviceAddress = deviceId
            }

            try {

                wifiP2pManager.connect(
                    wifichannel,
                    config,
                    object : WifiP2pManager.ActionListener {

                        override fun onSuccess() {
                            if (continuation.isActive) {
                                continuation.resume(
                                    TacticalResult.Success(Unit)
                                )
                            }
                        }

                        override fun onFailure(
                            reasonCode: Int
                        ) {
                            if (continuation.isActive) {
                                continuation.resume(
                                    TacticalResult.Failure(
                                        "connect($deviceId) failed, " +
                                                "reason=${reasonCode.toReasonString()}"
                                    )
                                )
                            }
                        }
                    }
                )

            } catch (e: SecurityException) {
                if (continuation.isActive) {
                    continuation.resume(
                        TacticalResult.Failure(
                            "Wi-Fi Direct permission denied"
                        )
                    )
                }
            }
        }
    }

    // AndroidWifiDirectManager.kt
    private fun hasWifiDirectPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.checkSelfPermission(
                Manifest.permission.NEARBY_WIFI_DEVICES
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            context.checkSelfPermission(
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun isLocationEnabled(): Boolean {
        val locationManager =
            context.getSystemService(Context.LOCATION_SERVICE)
                    as android.location.LocationManager

        return locationManager.isLocationEnabled
    }
    private fun WifiP2pDevice.toWifiDirectPeer() =
        WifiDirectPeer(
            deviceAddress = deviceAddress,
            deviceName = deviceName
        )

    private fun Int.toReasonString(): String =
        when (this) {
            WifiP2pManager.ERROR -> "ERROR"
            WifiP2pManager.P2P_UNSUPPORTED -> "P2P_UNSUPPORTED"
            WifiP2pManager.BUSY -> "BUSY"
            else -> "UNKNOWN($this)"
        }

    companion object {
        private const val TAG = "AndroidWifiDirect"
    }
}