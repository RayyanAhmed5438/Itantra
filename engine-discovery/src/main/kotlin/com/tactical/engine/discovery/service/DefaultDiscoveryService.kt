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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
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
            scanJob = scope.launch {
                while (isActive) {
                    try {
                        scanner.scan()
                            .collect { node ->
                                catalog.upsert(node)
                            }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        System.err.format(
                            "DiscoveryService",
                            "BLE scanner stopped: ${e.message}"
                        )
                    }

                    // Give Bluetooth a moment to recover before trying again.
                    delay(2000L)
                }
            }
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