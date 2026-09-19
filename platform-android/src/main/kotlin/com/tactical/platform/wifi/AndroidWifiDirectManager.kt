package com.tactical.platform.wifi

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest
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

/** Wi-Fi Direct service discovery that reports only iTantra advertisers. */
class AndroidWifiDirectManager(
    private val context: Context,
    private val wifiP2pManager: WifiP2pManager,
    private val wifichannel: Channel
) : WifiDirectManager {

    override suspend fun advertisePresence(deviceId: String, callsign: String): TacticalResult<Unit> {
        if (!hasWifiDirectPermission()) return TacticalResult.Failure("Missing Wi-Fi Direct permission")

        val record = mapOf(
            "app" to "itantra",
            "id" to deviceId,
            "callsign" to callsign.take(32)
        )
        val serviceInfo = WifiP2pDnsSdServiceInfo.newInstance("_itantra", "_presence._tcp", record)

        return suspendCancellableCoroutine { continuation ->
            try {
                wifiP2pManager.clearLocalServices(wifichannel, object : WifiP2pManager.ActionListener {
                    override fun onSuccess() = addLocalService(serviceInfo, continuation)
                    override fun onFailure(reason: Int) = addLocalService(serviceInfo, continuation)
                })
            } catch (e: SecurityException) {
                if (continuation.isActive) continuation.resume(TacticalResult.Failure("Wi-Fi Direct permission denied"))
            }
        }
    }

    private fun addLocalService(serviceInfo: WifiP2pDnsSdServiceInfo, continuation: kotlinx.coroutines.CancellableContinuation<TacticalResult<Unit>>) {
        try {
            wifiP2pManager.addLocalService(wifichannel, serviceInfo, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    if (continuation.isActive) continuation.resume(TacticalResult.Success(Unit))
                }
                override fun onFailure(reason: Int) {
                    if (continuation.isActive) continuation.resume(TacticalResult.Failure("iTantra Wi-Fi service registration failed: $reason"))
                }
            })
        } catch (e: SecurityException) {
            if (continuation.isActive) continuation.resume(TacticalResult.Failure("Wi-Fi Direct permission denied"))
        }
    }

    override suspend fun discoverPeers(): Flow<List<WifiDirectPeer>> = callbackFlow {
        if (!hasWifiDirectPermission()) {
            close()
            return@callbackFlow
        }

        val discovered = linkedMapOf<String, WifiDirectPeer>()

        val txtListener = WifiP2pManager.DnsSdTxtRecordListener { _, record, device ->
            if (record["app"] != "itantra") return@DnsSdTxtRecordListener
            val id = record["id"] ?: return@DnsSdTxtRecordListener
            val callsign = record["callsign"] ?: device.deviceName

            try {
                java.util.UUID.fromString(id)
            } catch (_: IllegalArgumentException) {
                return@DnsSdTxtRecordListener
            }

            discovered[device.deviceAddress] = WifiDirectPeer(
                deviceAddress = device.deviceAddress,
                deviceName = device.deviceName,
                appDeviceId = id,
                callsign = callsign
            )
            trySend(discovered.values.toList())
        }

        val serviceListener = WifiP2pManager.DnsSdServiceResponseListener { instanceName, _, device ->
            android.util.Log.d(TAG, "iTantra Wi-Fi service: $instanceName @ ${device.deviceAddress}")
        }

        try {
            wifiP2pManager.setDnsSdResponseListeners(wifichannel, serviceListener, txtListener)
            val serviceRequest = WifiP2pDnsSdServiceRequest.newInstance()
            wifiP2pManager.addServiceRequest(wifichannel, serviceRequest, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    try {
                        wifiP2pManager.discoverServices(wifichannel, object : WifiP2pManager.ActionListener {
                            override fun onSuccess() { android.util.Log.d(TAG, "iTantra Wi-Fi service discovery started") }
                            override fun onFailure(reason: Int) { android.util.Log.w(TAG, "Wi-Fi service discovery failed: $reason") }
                        })
                    } catch (e: SecurityException) {
                        close(SecurityException("Wi-Fi Direct permission denied", e))
                    }
                }
                override fun onFailure(reason: Int) {
                    close(IllegalStateException("Wi-Fi service request failed: $reason"))
                }
            })
            awaitClose { runCatching { wifiP2pManager.removeServiceRequest(wifichannel, serviceRequest, null) } }
        } catch (e: SecurityException) {
            close(SecurityException("Wi-Fi Direct permission denied", e))
        } catch (e: Exception) {
            close(e)
        }
    }
    override suspend fun connect(deviceId: String): TacticalResult<Unit> {
        if (!hasWifiDirectPermission()) return TacticalResult.Failure("Missing Wi-Fi Direct permission")
        return suspendCancellableCoroutine { continuation ->
            val config = android.net.wifi.p2p.WifiP2pConfig().apply { deviceAddress = deviceId }
            try {
                wifiP2pManager.connect(wifichannel, config, object : WifiP2pManager.ActionListener {
                    override fun onSuccess() { if (continuation.isActive) continuation.resume(TacticalResult.Success(Unit)) }
                    override fun onFailure(reason: Int) {
                        if (continuation.isActive) continuation.resume(TacticalResult.Failure("connect(" + deviceId + ") failed, reason=" + reason))
                    }
                })
            } catch (e: SecurityException) {
                if (continuation.isActive) continuation.resume(TacticalResult.Failure("Wi-Fi Direct permission denied"))
            }
        }
    }

    private fun hasWifiDirectPermission(): Boolean {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.NEARBY_WIFI_DEVICES else Manifest.permission.ACCESS_FINE_LOCATION
        return context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    }

    companion object {
        private const val TAG = "AndroidWifiDirect"
    }
}