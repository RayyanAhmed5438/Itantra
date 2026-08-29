package com.tactical.protocol.hashing

import com.tactical.domain.identity.DeviceId
import com.tactical.domain.packet.TextPacket
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class XxHashPacketHasherTest {

    private val hasher = XxHashPacketHasher()

    @Test
    fun `should produce same hash for identical packets`() {
        val p1 = TextPacket(DeviceId("a"), "hello", "en", 100)
        val p2 = TextPacket(DeviceId("a"), "hello", "en", 100)
        
        assertEquals(hasher.hash(p1), hasher.hash(p2))
    }

    @Test
    fun `should produce different hash for different content`() {
        val p1 = TextPacket(DeviceId("a"), "hello", "en", 100)
        val p2 = TextPacket(DeviceId("a"), "world", "en", 100)
        
        assertNotEquals(hasher.hash(p1), hasher.hash(p2))
    }
}
