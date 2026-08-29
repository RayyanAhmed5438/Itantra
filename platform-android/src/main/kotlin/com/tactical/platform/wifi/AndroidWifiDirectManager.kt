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

/**
 * Implements WifiDirectManager by wrapping Android's WifiP2pManager.
 * Handles peer discovery (WIFI_P2P_PEERS_CHANGED_ACTION) and connection
 * initiation only. The data path once a group actually forms is
 * WifiDirectRadioTransport's job (see radio/) — it listens for
 * WIFI_P2P_CONNECTION_CHANGED_ACTION independently rather than being
 * driven by this class, matching WifiDirectManager's own kdoc note that
 * engine-mesh talks to RadioTransport,
 * not necessarily to this interface.
 *
 * discoverPeers()/connect() both guard against the missing-permission
 * lint (@RequiresPermission on WifiP2pManager's discoverPeers/
 * requestPeers/connect) the same way AndroidBleScanner guards BLE scan —
 * a check, not a request. Requesting is PermissionGateway's job; callers
 * are expected to have gone through that before calling either method
 * here.
 */
class AndroidWifiDirectManager(
    private val context: Context,
    private val wifiP2pManager: WifiP2pManager,
    private val wifichannel: Channel
) : WifiDirectManager {

    override suspend fun discoverPeers(): Flow<List<WifiDirectPeer>> = callbackFlow {
        val requiredPermission = wifiDirectPermissionForThisApiLevel()
        if (context.checkSelfPermission(requiredPermission) != PackageManager.PERMISSION_GRANTED) {
            close(SecurityException("Missing $requiredPermission — request it via PermissionGateway before collecting discoverPeers()"))
            return@callbackFlow
        }

        val peerListListener = WifiP2pManager.PeerListListener { deviceList: WifiP2pDeviceList ->
            trySend(deviceList.deviceList.map { it.toWifiDirectPeer() })
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                if (intent.action == WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION) {
                    if (context.checkSelfPermission(requiredPermission) == PackageManager.PERMISSION_GRANTED) {
                        wifiP2pManager.requestPeers(wifichannel, peerListListener)
                    }
                }
            }
        }
        context.registerReceiver(receiver, IntentFilter(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION))

        wifiP2pManager.discoverPeers(wifichannel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                // Peers arrive via the broadcast above, not this callback —
                // this only confirms the scan request itself was accepted.
            }

            override fun onFailure(reasonCode: Int) {
                close(IllegalStateException("discoverPeers() request failed, reason=${reasonCode.toReasonString()}"))
            }
        })

        awaitClose { context.unregisterReceiver(receiver) }
    }

    override suspend fun connect(deviceId: String): TacticalResult<Unit> {
        val requiredPermission = wifiDirectPermissionForThisApiLevel()
        if (context.checkSelfPermission(requiredPermission) != PackageManager.PERMISSION_GRANTED) {
            return TacticalResult.Failure("Missing $requiredPermission — request it via PermissionGateway before calling connect()")
        }

        return suspendCancellableCoroutine { continuation ->
            val config = WifiP2pConfig().apply { deviceAddress = deviceId }

            wifiP2pManager.connect(wifichannel, config, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    // Negotiation accepted only — actual group formation is
                    // reported asynchronously via WIFI_P2P_CONNECTION_CHANGED_ACTION,
                    // which WifiDirectRadioTransport listens for on its own.
                    if (continuation.isActive) continuation.resumeWith(Result.success(TacticalResult.Success(Unit)))
                }

                override fun onFailure(reasonCode: Int) {
                    if (continuation.isActive) {
                        continuation.resumeWith(
                            Result.success(TacticalResult.Failure("connect($deviceId) failed, reason=${reasonCode.toReasonString()}"))
                        )
                    }
                }
            })
        }
    }

    private fun WifiP2pDevice.toWifiDirectPeer() = WifiDirectPeer(
        deviceAddress = deviceAddress,
        deviceName = deviceName
    )

    private fun Int.toReasonString(): String = when (this) {
        WifiP2pManager.ERROR -> "ERROR"
        WifiP2pManager.P2P_UNSUPPORTED -> "P2P_UNSUPPORTED"
        WifiP2pManager.BUSY -> "BUSY"
        else -> "UNKNOWN($this)"
    }

    private fun wifiDirectPermissionForThisApiLevel(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.NEARBY_WIFI_DEVICES
        } else {
            Manifest.permission.ACCESS_FINE_LOCATION
        }
}