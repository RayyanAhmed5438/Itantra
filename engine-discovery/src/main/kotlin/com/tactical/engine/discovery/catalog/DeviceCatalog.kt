package com.tactical.engine.discovery.catalog

import com.tactical.domain.identity.DeviceNode
import kotlinx.coroutines.flow.StateFlow

/**
 * Registry of all discovered peers in the mesh.
 */
interface DeviceCatalog {
    /**
     * Cold-to-Hot stream of all currently active device nodes.
     */
    fun all(): StateFlow<List<DeviceNode>>

    /**
     * Updates or inserts a device node into the catalog.
     */
    suspend fun upsert(node: DeviceNode)
}
