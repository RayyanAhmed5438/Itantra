package com.tactical.engine.discovery.service

import com.tactical.domain.identity.DeviceId
import com.tactical.domain.identity.DeviceNode
import com.tactical.domain.identity.LinkType
import com.tactical.engine.discovery.beacon.BeaconEmitter
import com.tactical.engine.discovery.catalog.DeviceCatalog
import com.tactical.engine.discovery.scanner.BeaconScanner
import com.tactical.platform.api.wifi.WifiDirectManager
import kotlinx.coroutines.*

class DefaultDiscoveryService(
    private val scanner: BeaconScanner,
    private val catalog: DeviceCatalog,
    private val emitter: BeaconEmitter,
    private val wifiDirectManager: WifiDirectManager,
    private val localDeviceId: String,
    private val localCallsign: String,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) : DiscoveryService {

    private var scanJob: Job? = null
    private var wifiJob: Job? = null
    private var beaconingStarted = false

    override fun peers() = catalog.all()

    override suspend fun start() {
        if (beaconingStarted) return
        beaconingStarted = true

        emitter.start()

        // Register the same app-specific identity over Wi-Fi Direct.
        // Failure here is non-fatal: BLE can still discover the peer.
        runCatching {
            wifiDirectManager.advertisePresence(localDeviceId, localCallsign)
        }
    }

    fun startDiscovery() {
        if (scanJob == null) {
            scanJob = scope.launch {
                while (isActive) {
                    try {
                        scanner.scan().collect { catalog.upsert(it) }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        // BLE can be unavailable; Wi-Fi discovery can still work.
                    }
                    delay(1500L)
                }
            }
        }

        if (wifiJob == null) {
            wifiJob = scope.launch {
                try {
                    wifiDirectManager.discoverPeers().collect { peers ->
                        peers.forEach { peer ->
                            val appDeviceId = peer.appDeviceId ?: return@forEach
                            if (appDeviceId == localDeviceId) return@forEach

                            runCatching {
                                catalog.upsert(
                                    DeviceNode(
                                        id = DeviceId(appDeviceId),
                                        callsign = peer.callsign ?: peer.deviceName,
                                        rssi = 0,
                                        lastSeen = java.time.Instant.now(),
                                        hopCount = 0,
                                        link = LinkType.DIRECT
                                    )
                                )
                            }
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // Wi-Fi Direct is optional; BLE remains the primary fallback.
                }
            }
        }
    }

    fun stopDiscovery() {
        scanJob?.cancel()
        wifiJob?.cancel()
        scanJob = null
        wifiJob = null
    }

    override suspend fun stop() {
        stopDiscovery()

        if (beaconingStarted) {
            emitter.stop()
            beaconingStarted = false
        }
    }
}
