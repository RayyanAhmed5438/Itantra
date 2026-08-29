package com.tactical.engine.mesh.router

import com.tactical.domain.identity.DeviceId
import com.tactical.domain.packet.MeshRelayPacket
import com.tactical.domain.packet.TextPacket
import com.tactical.engine.mesh.deduplication.DeduplicationFilter
import com.tactical.engine.mesh.forwarding.ForwardDecision
import com.tactical.engine.mesh.ttl.DecrementingTtlTracker
import com.tactical.protocol.hashing.PacketHasher
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FloodMeshRouterTest {

    private val localId = DeviceId("local")
    private val otherId = DeviceId("other")
    
    private val mockHasher = object : PacketHasher {
        override fun hash(packet: com.tactical.domain.packet.Packet): Long = packet.hashCode().toLong()
    }
    
    private val mockDedup = object : DeduplicationFilter {
        private val seen = mutableSetOf<Long>()
        override fun mightContain(hash: Long): Boolean = seen.contains(hash)
        override fun put(hash: Long) { seen.add(hash) }
    }
    
    private val router = FloodMeshRouter(localId, mockHasher, mockDedup, DecrementingTtlTracker())

    @Test
    fun `should drop packets originating from self`() {
        val packet = TextPacket(localId, "hello", "en", 0)
        val relay = MeshRelayPacket(localId, localId, 5, 0, packet)
        
        val decision = router.handle(relay)
        assertTrue(decision is ForwardDecision.Drop)
    }

    @Test
    fun `should rebroadcast new packets from others`() {
        val packet = TextPacket(otherId, "hello", "en", 0)
        val relay = MeshRelayPacket(otherId, otherId, 5, 0, packet)
        
        val decision = router.handle(relay)
        assertTrue(decision is ForwardDecision.AcceptAndRebroadcast)
        val rebroadcast = (decision as ForwardDecision.AcceptAndRebroadcast).relayPacket
        assertEquals(4, rebroadcast.ttl)
        assertEquals(1, rebroadcast.hopCount)
    }

    @Test
    fun `should drop duplicate packets`() {
        val packet = TextPacket(otherId, "hello", "en", 0)
        val relay = MeshRelayPacket(otherId, otherId, 5, 0, packet)
        
        router.handle(relay) // First time
        val decision = router.handle(relay) // Second time
        
        assertTrue(decision is ForwardDecision.Drop)
        assertEquals("Duplicate packet", (decision as ForwardDecision.Drop).reason)
    }

    @Test
    fun `should accept but not rebroadcast if TTL is exhausted`() {
        val packet = TextPacket(otherId, "hello", "en", 0)
        val relay = MeshRelayPacket(otherId, otherId, 0, 0, packet)
        
        val decision = router.handle(relay)
        assertTrue(decision is ForwardDecision.AcceptLocal)
    }
}
