package com.tactical.protocol.serialization

import com.tactical.domain.identity.DeviceId
import com.tactical.domain.packet.*
import com.tactical.protocol.constants.ProtocolConstants
import java.nio.ByteBuffer
import java.util.zip.CRC32

class BinaryPacketSerializer : PacketSerializer {

    override fun serialize(packet: Packet): ByteArray {
        val payload = when (packet) {
            is TextPacket -> packet.text.toByteArray()
            is VoicePacket -> ByteArray(0)
            is EmergencyPacket -> ByteArray(0)
            is BeaconPacket -> ByteArray(0)
        }

        val type: Byte = when (packet) {
            is TextPacket -> 1
            is VoicePacket -> 2
            is EmergencyPacket -> 3
            is BeaconPacket -> 4
        }

        return wrapInEnvelope(type, payload)
    }

    override fun serializeRelay(packet: MeshRelayPacket): ByteArray {
        val innerPayload = serialize(packet.payload)
        
        val origSenderBytes = packet.originalSender.value.toByteArray()
        val immSenderBytes = packet.immediateSender.value.toByteArray()
        
        val relayPayload = ByteBuffer.allocate(
            1 + origSenderBytes.size + 1 + immSenderBytes.size + 4 + 4 + 4 + innerPayload.size
        ).apply {
            put(origSenderBytes.size.toByte())
            put(origSenderBytes)
            put(immSenderBytes.size.toByte())
            put(immSenderBytes)
            putInt(packet.ttl)
            putInt(packet.hopCount)
            putInt(innerPayload.size)
            put(innerPayload)
        }.array()

        return wrapInEnvelope(100.toByte(), relayPayload)
    }

    private fun wrapInEnvelope(type: Byte, payload: ByteArray): ByteArray {
        val buffer = ByteBuffer.allocate(11 + payload.size)
        buffer.put(ProtocolConstants.MAGIC_BYTE)
        buffer.put(ProtocolConstants.VERSION)
        buffer.put(type)
        buffer.putInt(payload.size)

        val crc = CRC32()
        crc.update(payload)
        buffer.putInt(crc.value.toInt())
        buffer.put(payload)

        return buffer.array()
    }

    override fun deserialize(bytes: ByteArray): Packet {
        val unwrapped = unwrapEnvelope(bytes)
        return when (unwrapped.type.toInt()) {
            1 -> TextPacket(
                sender = DeviceId("unknown"), 
                text = String(unwrapped.payload), 
                languageCode = "en", 
                timestamp = System.currentTimeMillis()
            )
            else -> throw IllegalArgumentException("Unknown packet type: ${unwrapped.type}")
        }
    }

    override fun deserializeRelay(bytes: ByteArray): MeshRelayPacket {
        val unwrapped = unwrapEnvelope(bytes)
        if (unwrapped.type.toInt() != 100) throw IllegalArgumentException("Not a relay packet")
        
        val buffer = ByteBuffer.wrap(unwrapped.payload)
        
        val origLen = buffer.get().toInt()
        val origSender = ByteArray(origLen).also { buffer.get(it) }
        
        val immLen = buffer.get().toInt()
        val immSender = ByteArray(immLen).also { buffer.get(it) }
        
        val ttl = buffer.getInt()
        val hopCount = buffer.getInt()
        
        val innerLen = buffer.getInt()
        val innerBytes = ByteArray(innerLen).also { buffer.get(it) }
        
        return MeshRelayPacket(
            originalSender = DeviceId(String(origSender)),
            immediateSender = DeviceId(String(immSender)),
            ttl = ttl,
            hopCount = hopCount,
            payload = deserialize(innerBytes)
        )
    }

    private data class Unwrapped(val type: Byte, val payload: ByteArray)

    private fun unwrapEnvelope(bytes: ByteArray): Unwrapped {
        val buffer = ByteBuffer.wrap(bytes)
        val magic = buffer.get()
        if (magic != ProtocolConstants.MAGIC_BYTE) throw IllegalArgumentException("Invalid magic")
        
        val version = buffer.get()
        val type = buffer.get()
        val len = buffer.getInt()
        val crc = buffer.getInt()
        
        val payload = ByteArray(len)
        buffer.get(payload)

        val computedCrc = CRC32()
        computedCrc.update(payload)
        if (crc != computedCrc.value.toInt()) {
            throw IllegalArgumentException("CRC mismatch")
        }
        
        return Unwrapped(type, payload)
    }
}
