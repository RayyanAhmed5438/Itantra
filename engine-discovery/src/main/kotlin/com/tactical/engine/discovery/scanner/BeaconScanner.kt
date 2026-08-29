package com.tactical.engine.discovery.scanner

import com.tactical.domain.identity.DeviceNode
import kotlinx.coroutines.flow.Flow

interface BeaconScanner {
    /**
     * Cold flow of discovered device nodes as they are scanned.
     */
    fun scan(): Flow<DeviceNode>
}
