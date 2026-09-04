package com.tactical.engine.discovery.service

import com.tactical.domain.identity.DeviceNode
import com.tactical.engine.discovery.beacon.BeaconEmitter
import com.tactical.engine.discovery.catalog.DeviceCatalog
import com.tactical.engine.discovery.scanner.BeaconScanner
import com.tactical.platform.api.wifi.WifiDirectManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.StateFlow
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
                .onEach { node ->
                    catalog.upsert(node)
                }
                .launchIn(scope)
        }

        if (wifiJob == null) {
            wifiJob = wifiDirectManager
                .discoverPeers()
                .onEach { peers ->

                    /*
                     * Wi-Fi Direct discovery tells us that a physical
                     * peer exists, but it does not contain the logical
                     * DeviceId/callsign from our beacon.
                     *
                     * Therefore BLE beacon discovery remains the source
                     * of the DeviceNode identity.
                     *
                     * Here we initiate the Wi-Fi Direct connection so
                     * that the RadioTransport can subsequently establish
                     * the TCP data path.
                     */
                    peers.forEach { peer ->
                        scope.launch {
                            wifiDirectManager.connect(peer.deviceAddress)
                        }
                    }
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