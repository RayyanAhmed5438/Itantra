package com.tactical.engine.discovery.service

import com.tactical.domain.identity.DeviceNode
import com.tactical.engine.discovery.beacon.BeaconEmitter
import com.tactical.engine.discovery.catalog.DeviceCatalog
import com.tactical.engine.discovery.scanner.BeaconScanner
import com.tactical.platform.api.wifi.WifiDirectManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class DefaultDiscoveryService(
    private val scanner: BeaconScanner,
    private val catalog: DeviceCatalog,
    private val emitter: BeaconEmitter,
    private val wifiDirectManager: WifiDirectManager,
    private val scope: CoroutineScope =
        CoroutineScope(Dispatchers.Default + SupervisorJob())
) : DiscoveryService {

    private var scanJob: Job? = null
    private var wifiJob: Job? = null

    override fun peers(): StateFlow<List<DeviceNode>> =
        catalog.all()

    override suspend fun start() {

        emitter.start()

        if (scanJob == null) {
            scanJob = scanner.scan()
                .catch {
                    // A scanner source failing (no BLE hardware, permission
                    // revoked mid-run) must not propagate as an uncaught
                    // exception here — matches the existing wifiJob guard
                    // below. Losing beacon updates is the acceptable
                    // degradation; crashing the whole service is not.
                }
                .onEach { node ->
                    catalog.upsert(node)
                }
                .launchIn(scope)
        }

        if (wifiJob == null) {
            wifiJob = wifiDirectManager
                .discoverPeers()
                .onEach { peers ->
                    peers.forEach { peer ->
                        scope.launch {
                            try {
                                wifiDirectManager.connect(peer.deviceAddress)
                            } catch (e: Exception) {

                            }
                        }
                    }
                }
                .catch {
                }
                .launchIn(scope)
        }
    }

    override suspend fun stop() {
        emitter.stop()

        scanJob?.cancel()
        scanJob = null

        wifiJob?.cancel()
        wifiJob = null
    }
}