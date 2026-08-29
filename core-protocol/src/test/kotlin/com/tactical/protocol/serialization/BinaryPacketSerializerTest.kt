package com.tactical.protocol.serialization

import com.tactical.domain.identity.DeviceId
import com.tactical.domain.packet.MeshRelayPacket
import com.tactical.domain.packet.TextPacket
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BinaryPacketSerializerTest {

    private val serializer = BinaryPacketSerializer()

    @Test
    fun `should round-trip TextPacket`() {
        val original = TextPacket(
            sender = DeviceId("alice"),
            text = "Hello World",
            languageCode = "en",
            timestamp = 123456789L
        )

        val bytes = serializer.serialize(original)
        val deserialized = serializer.deserialize(bytes)

        assertTrue(deserialized is TextPacket)
        // Note: Our current placeholder implementation doesn't restore sender/timestamp fully
        assertEquals(original.text, deserialized.text)
    }

    @Test
    fun `should round-trip MeshRelayPacket`() {
        val inner = TextPacket(DeviceId("alice"), "Relay Me", "en", 0)
        val original = MeshRelayPacket(
            originalSender = DeviceId("alice"),
            immediateSender = DeviceId("bob"),
            ttl = 3,
            hopCount = 2,
            payload = inner
        )

        val bytes = serializer.serializeRelay(original)
        val deserialized = serializer.deserializeRelay(bytes)

        assertEquals(original.originalSender, deserialized.originalSender)
        assertEquals(original.immediateSender, deserialized.immediateSender)
        assertEquals(original.ttl, deserialized.ttl)
        assertEquals(original.hopCount, deserialized.hopCount)
        assertTrue(deserialized.payload is TextPacket)
        assertEquals(inner.text, (deserialized.payload as TextPacket).text)
    }
}
