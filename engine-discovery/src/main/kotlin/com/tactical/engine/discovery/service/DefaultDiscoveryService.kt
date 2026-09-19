package com.tactical.engine.discovery.service

import com.tactical.domain.identity.DeviceNode
import com.tactical.engine.discovery.beacon.BeaconEmitter
import com.tactical.engine.discovery.catalog.DeviceCatalog
import com.tactical.engine.discovery.scanner.BeaconScanner
import com.tactical.platform.api.wifi.WifiDirectManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class DefaultDiscoveryService(
    private val scanner: BeaconScanner,
    private val catalog: DeviceCatalog,
    private val emitter: BeaconEmitter,
    private val wifiDirectManager: WifiDirectManager,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) : DiscoveryService {
    private var scanJob: Job? = null
    private var wifiJob: Job? = null
    private var beaconingStarted = false

    override fun peers(): StateFlow<List<DeviceNode>> = catalog.all()

    override suspend fun start() {
        if (beaconingStarted) return
        beaconingStarted = true
        emitter.start()
    }

    fun startDiscovery() {
        if (scanJob == null) {
            scanJob = scope.launch {
                while (isActive) {
                    try { scanner.scan().collect { catalog.upsert(it) } }
                    catch (e: CancellationException) { throw e }
                    catch (_: Exception) { }
                    delay(1500L)
                }
            }
        }
        if (wifiJob == null) {
            wifiJob = scope.launch {
                try {
                    wifiDirectManager.discoverPeers().collect { peers ->
                        peers.forEach { peer ->
                            launch { runCatching { wifiDirectManager.connect(peer.deviceAddress) } }
                        }
                    }
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) { }
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
