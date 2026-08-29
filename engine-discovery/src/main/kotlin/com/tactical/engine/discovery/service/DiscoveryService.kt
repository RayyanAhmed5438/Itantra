package com.tactical.engine.discovery.service

import com.tactical.domain.identity.DeviceNode
import kotlinx.coroutines.flow.StateFlow

interface DiscoveryService {
    /** All currently known peers. */
    fun peers(): StateFlow<List<DeviceNode>>

    /** Starts discovery and beaconing. */
    suspend fun start()

    /** Stops discovery and beaconing. */
    suspend fun stop()
}
