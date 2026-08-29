package com.tactical.engine.discovery.catalog

import com.tactical.domain.identity.DeviceId
import com.tactical.domain.identity.DeviceNode
import com.tactical.domain.identity.LinkType
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * ConcurrentHashMap-backed catalog with a 15s TTL eviction for stale nodes.
 */
class InMemoryDeviceCatalog(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob()),
    private val ttlMillis: Long = 15_000L,
    private val currentTimeMillis: () -> Long = { System.currentTimeMillis() }
) : DeviceCatalog {

    private val nodes = ConcurrentHashMap<DeviceId, DeviceNode>()
    private val _nodesFlow = MutableStateFlow<List<DeviceNode>>(emptyList())

    init {
        // Periodic eviction task
        scope.launch {
            while (isActive) {
                delay(1000)
                evictStaleNodes()
            }
        }
    }

    override fun all(): StateFlow<List<DeviceNode>> = _nodesFlow.asStateFlow()

    override suspend fun upsert(node: DeviceNode) {
        nodes[node.id] = node
        updateFlow()
    }

    private fun evictStaleNodes() {
        val now = currentTimeMillis()
        var changed = false
        val iterator = nodes.entries.iterator()
        
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val lastSeen = entry.value.lastSeen.toEpochMilli()
            if (lastSeen + ttlMillis < now) {
                iterator.remove()
                changed = true
            } else if (lastSeen + (ttlMillis / 2) < now && entry.value.link != LinkType.STALE) {
                // Mark as STALE if halfway to eviction
                nodes[entry.key] = entry.value.copy(link = LinkType.STALE)
                changed = true
            }
        }
        
        if (changed) {
            updateFlow()
        }
    }

    private fun updateFlow() {
        _nodesFlow.value = nodes.values.toList().sortedBy { it.id.value }
    }
}
