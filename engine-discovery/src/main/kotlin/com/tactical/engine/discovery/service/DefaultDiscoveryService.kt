package com.tactical.engine.discovery.service

import com.tactical.domain.identity.DeviceNode
import com.tactical.engine.discovery.beacon.BeaconEmitter
import com.tactical.engine.discovery.catalog.DeviceCatalog
import com.tactical.engine.discovery.scanner.BeaconScanner
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

class DefaultDiscoveryService(
    private val scanner: BeaconScanner,
    private val catalog: DeviceCatalog,
    private val emitter: BeaconEmitter,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) : DiscoveryService {

    private var scanJob: Job? = null

    override fun peers(): StateFlow<List<DeviceNode>> = catalog.all()

    override suspend fun start() {
        emitter.start()
        
        if (scanJob == null) {
            scanJob = scanner.scan()
                .onEach { catalog.upsert(it) }
                .launchIn(scope)
        }
    }

    override suspend fun stop() {
        emitter.stop()
        scanJob?.cancel()
        scanJob = null
    }
}
