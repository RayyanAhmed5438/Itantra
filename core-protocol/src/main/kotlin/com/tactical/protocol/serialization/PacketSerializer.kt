package com.tactical.protocol.serialization

import com.tactical.domain.packet.MeshRelayPacket
import com.tactical.domain.packet.Packet

interface PacketSerializer {
    /**
     * Serializes a domain [Packet] into a byte array for transmission.
     */
    fun serialize(packet: Packet): ByteArray

    /**
     * Serializes a [MeshRelayPacket] envelope into a byte array.
     */
    fun serializeRelay(packet: MeshRelayPacket): ByteArray

    /**
     * Deserializes a byte array back into a domain [Packet].
     */
    fun deserialize(bytes: ByteArray): Packet

    /**
     * Deserializes a byte array into a [MeshRelayPacket].
     */
    fun deserializeRelay(bytes: ByteArray): MeshRelayPacket
}
