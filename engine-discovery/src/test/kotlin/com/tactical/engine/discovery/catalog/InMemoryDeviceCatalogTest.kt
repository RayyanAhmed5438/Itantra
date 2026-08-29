package com.tactical.engine.discovery.catalog

import com.tactical.domain.identity.DeviceId
import com.tactical.domain.identity.DeviceNode
import com.tactical.domain.identity.LinkType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class InMemoryDeviceCatalogTest {

    @Test
    fun `should evict stale nodes after TTL`() = runTest {
        var virtualTime = 1000L
        val catalog = InMemoryDeviceCatalog(
            scope = backgroundScope, 
            ttlMillis = 1000L,
            currentTimeMillis = { virtualTime }
        )
        val id = DeviceId("test-peer")
        val node = DeviceNode(
            id = id,
            callsign = "Test",
            rssi = -50,
            lastSeen = Instant.ofEpochMilli(1000L),
            hopCount = 0,
            link = LinkType.DIRECT
        )

        catalog.upsert(node)
        assertEquals(1, catalog.all().value.size)

        virtualTime = 2500L
        advanceTimeBy(1100)
        kotlinx.coroutines.yield()
        
        assertTrue(catalog.all().value.isEmpty(), "Node should have been evicted")
    }

    @Test
    fun `should mark nodes as STALE before eviction`() = runTest {
        var virtualTime = 1000L
        val catalog = InMemoryDeviceCatalog(
            scope = backgroundScope, 
            ttlMillis = 2000L,
            currentTimeMillis = { virtualTime }
        )
        val id = DeviceId("test-peer")
        val node = DeviceNode(
            id = id,
            callsign = "Test",
            rssi = -50,
            lastSeen = Instant.ofEpochMilli(1000L),
            hopCount = 0,
            link = LinkType.DIRECT
        )

        catalog.upsert(node)
        
        virtualTime = 2500L
        advanceTimeBy(1100)
        kotlinx.coroutines.yield()

        val current = catalog.all().value.first()
        assertEquals(LinkType.STALE, current.link)
    }
}
