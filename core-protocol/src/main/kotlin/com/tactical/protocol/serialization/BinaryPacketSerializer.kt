package com.tactical.protocol.serialization

import com.tactical.domain.identity.DeviceId
import com.tactical.domain.location.GeoFix
import com.tactical.domain.packet.*
import com.tactical.protocol.constants.ProtocolConstants
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.util.zip.CRC32

class BinaryPacketSerializer : PacketSerializer {

    override fun serialize(packet: Packet): ByteArray {
        val payload = when (packet) {
            is TextPacket -> encodeTextPacket(packet)
            is VoicePacket -> encodeVoicePacket(packet)
            is EmergencyPacket -> encodeEmergencyPacket(packet)
            is BeaconPacket -> encodeBeaconPacket(packet)
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

        val relayPayload = ByteArrayOutputStream().use { bos ->
            DataOutputStream(bos).use { out ->
                out.writeString(packet.originalSender.value)
                out.writeString(packet.immediateSender.value)
                out.writeInt(packet.ttl)
                out.writeInt(packet.hopCount)
                out.writeInt(innerPayload.size)
                out.write(innerPayload)
            }
            bos.toByteArray()
        }

        return wrapInEnvelope(100.toByte(), relayPayload)
    }

    // ---- Per-type payload encoding ----

    private fun encodeTextPacket(packet: TextPacket): ByteArray = byteStream {
        writeString(packet.sender.value)
        writeString(packet.languageCode)
        writeString(packet.text)
        // Reserve the timestamp's least-significant bit as the Call Mode
        // marker. Millisecond timestamps are normalized to an even value,
        // so old protocol readers still see a valid timestamp.
        writeLong((packet.timestamp and -2L) or if (packet.isCallMode) 1L else 0L)
    }

    private fun encodeVoicePacket(packet: VoicePacket): ByteArray = byteStream {
        writeString(packet.sender.value)
        writeByte(packet.codec.ordinal)
        writeInt(packet.audioData.size)
        write(packet.audioData)
        writeLong(packet.timestamp)
    }

    private fun encodeEmergencyPacket(packet: EmergencyPacket): ByteArray = byteStream {
        writeString(packet.sender.value)
        writeByte(packet.severity.ordinal)
        writeString(packet.description)
        writeString(packet.languageCode)
        writeGeoFix(packet.location)
        writeLong(packet.timestamp)
    }

    private fun encodeBeaconPacket(packet: BeaconPacket): ByteArray = byteStream {
        writeString(packet.sender.value)
        writeString(packet.callsign)
        writeBoolean(packet.listenPort != null)
        val listenPort = packet.listenPort
        if (listenPort != null) {
            writeInt(listenPort)
        }
        writeLong(packet.timestamp)
    }

    private fun DataOutputStream.writeGeoFix(fix: GeoFix?) {
        writeBoolean(fix != null)
        if (fix == null) return
        writeDouble(fix.latitude)
        writeDouble(fix.longitude)
        writeBoolean(fix.altitude != null)
        val altitude = fix.altitude
        if (altitude != null) {
            writeDouble(altitude)
        }
        writeBoolean(fix.accuracyMeters != null)

        val accuracyMeters = fix.accuracyMeters
        if (accuracyMeters != null) {
            writeFloat(accuracyMeters)
        }
        writeLong(fix.timestamp)
    }

    private fun DataOutputStream.writeString(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        writeInt(bytes.size)
        write(bytes)
    }

    private inline fun byteStream(block: DataOutputStream.() -> Unit): ByteArray =
        ByteArrayOutputStream().use { bos ->
            DataOutputStream(bos).use { it.block() }
            bos.toByteArray()
        }

    private fun wrapInEnvelope(type: Byte, payload: ByteArray): ByteArray {
        val totalSize = 11 + payload.size
        require(totalSize <= ProtocolConstants.MAX_PACKET_SIZE) {
            "Serialized packet ($totalSize bytes) exceeds MAX_PACKET_SIZE (${ProtocolConstants.MAX_PACKET_SIZE})"
        }

        val buffer = ByteBuffer.allocate(totalSize)
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

    // ---- Deserialization ----

    override fun deserialize(bytes: ByteArray): Packet {
        val unwrapped = unwrapEnvelope(bytes)
        val input = DataInputStream(unwrapped.payload.inputStream())

        return when (unwrapped.type.toInt()) {
            1 -> input.use {
                val sender = DeviceId(it.readString())
                val languageCode = it.readString()
                val text = it.readString()
                val wireTimestamp = it.readLong()
                TextPacket(
                    sender = sender,
                    text = text,
                    languageCode = languageCode,
                    timestamp = wireTimestamp and -2L,
                    isCallMode = (wireTimestamp and 1L) != 0L
                )
            }
            2 -> input.use {
                val sender = DeviceId(it.readString())
                val codec = AudioCodec.entries[it.readByte().toInt()]
                val audioLen = it.readInt()
                val audioData = ByteArray(audioLen).also { buf -> it.readFully(buf) }
                val timestamp = it.readLong()
                VoicePacket(sender = sender, audioData = audioData, codec = codec, timestamp = timestamp)
            }
            3 -> input.use {
                val sender = DeviceId(it.readString())
                val severity = Severity.entries[it.readByte().toInt()]
                val description = it.readString()
                val languageCode = it.readString()
                val location = it.readGeoFix()
                val timestamp = it.readLong()
                EmergencyPacket(
                    sender = sender,
                    severity = severity,
                    description = description,
                    location = location,
                    languageCode = languageCode,
                    timestamp = timestamp
                )
            }
            4 -> input.use {
                val sender = DeviceId(it.readString())
                val callsign = it.readString()
                val hasPort = it.readBoolean()
                val listenPort = if (hasPort) it.readInt() else null
                val timestamp = it.readLong()
                BeaconPacket(sender = sender, callsign = callsign, listenPort = listenPort, timestamp = timestamp)
            }
            else -> throw IllegalArgumentException("Unknown packet type: ${unwrapped.type}")
        }
    }

    override fun deserializeRelay(bytes: ByteArray): MeshRelayPacket {
        val unwrapped = unwrapEnvelope(bytes)
        if (unwrapped.type.toInt() != 100) throw IllegalArgumentException("Not a relay packet")

        val input = DataInputStream(unwrapped.payload.inputStream())
        return input.use {
            val originalSender = DeviceId(it.readString())
            val immediateSender = DeviceId(it.readString())
            val ttl = it.readInt()
            val hopCount = it.readInt()
            val innerLen = it.readInt()
            val innerBytes = ByteArray(innerLen).also { buf -> it.readFully(buf) }

            MeshRelayPacket(
                originalSender = originalSender,
                immediateSender = immediateSender,
                ttl = ttl,
                hopCount = hopCount,
                payload = deserialize(innerBytes)
            )
        }
    }

    private fun DataInputStream.readString(): String {
        val len = readInt()
        val bytes = ByteArray(len)
        readFully(bytes)
        return String(bytes, Charsets.UTF_8)
    }

    private fun DataInputStream.readGeoFix(): GeoFix? {
        val hasLocation = readBoolean()
        if (!hasLocation) return null
        val latitude = readDouble()
        val longitude = readDouble()
        val altitude = if (readBoolean()) readDouble() else null
        val accuracyMeters = if (readBoolean()) readFloat() else null
        val geoTimestamp = readLong()
        return GeoFix(
            latitude = latitude,
            longitude = longitude,
            altitude = altitude,
            accuracyMeters = accuracyMeters,
            timestamp = geoTimestamp
        )
    }

    private data class Unwrapped(val type: Byte, val payload: ByteArray)

    private fun unwrapEnvelope(bytes: ByteArray): Unwrapped {
        val buffer = ByteBuffer.wrap(bytes)
        val magic = buffer.get()
        if (magic != ProtocolConstants.MAGIC_BYTE) throw IllegalArgumentException("Invalid magic")

        val version = buffer.get()
        if (version != ProtocolConstants.VERSION) throw IllegalArgumentException("Unsupported version: $version")

        val type = buffer.get()
        val len = buffer.getInt()
        val crc = buffer.getInt()

        if (buffer.remaining() < len) throw IllegalArgumentException("Truncated packet: expected $len bytes, got ${buffer.remaining()}")

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