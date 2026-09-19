package com.tactical.platform.api.ble

import com.tactical.domain.identity.DeviceId
import com.tactical.domain.packet.BeaconPacket
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * Compact wire format used specifically inside BLE manufacturer data.
 *
 * Layout:
 *   2 bytes  magic
 *  16 bytes  UUID
 *   1 byte   callsign length
 *   N bytes  UTF-8 callsign (max 8 bytes)
 *   8 bytes  timestamp
 *
 * Maximum = 2 + 16 + 1 + 8 + 8 = 35 bytes,
 * so timestamp is intentionally omitted from the BLE payload.
 *
 * BLE is only being used for presence discovery. The full BeaconPacket
 * remains serialized by PacketSerializer for normal radio transport.
 *
 * Actual BLE payload:
 *   2 bytes magic
 *  16 bytes UUID
 *   1 byte callsign length
 *   8 bytes callsign
 * ----------------
 *  24 bytes maximum when the legacy advertisement is connectable: Android
 *  automatically needs advertising flags, leaving 28 bytes for the manufacturer AD structure
 *  including the 2-byte company identifier. Therefore the manufacturer payload itself is capped at 24 bytes.
 */
object BleBeaconPayloadCodec {

    private const val MAGIC_1: Byte = 0x53 // 'S'
    private const val MAGIC_2: Byte = 0x42 // 'B'

    private const val UUID_BYTES = 16
    private const val MAX_CALLSIGN_BYTES = 5

    private const val HEADER_BYTES = 2
    private const val LENGTH_BYTES = 1

    private const val MAX_PAYLOAD_SIZE =
        HEADER_BYTES +
                UUID_BYTES +
                LENGTH_BYTES +
                MAX_CALLSIGN_BYTES

    fun encode(packet: BeaconPacket): ByteArray {
        val uuid = try {
            UUID.fromString(packet.sender.value)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException(
                "BLE beacon requires DeviceId to contain a UUID: ${packet.sender.value}",
                e
            )
        }

        val callsignBytes = packet.callsign.toByteArray(StandardCharsets.UTF_8)

        require(callsignBytes.size <= MAX_CALLSIGN_BYTES) {
            "BLE callsign is too long: ${callsignBytes.size} bytes, " +
                    "maximum is $MAX_CALLSIGN_BYTES bytes"
        }

        val buffer = ByteBuffer.allocate(
            HEADER_BYTES +
                    UUID_BYTES +
                    LENGTH_BYTES +
                    callsignBytes.size
        )

        buffer.put(MAGIC_1)
        buffer.put(MAGIC_2)

        buffer.putLong(uuid.mostSignificantBits)
        buffer.putLong(uuid.leastSignificantBits)

        buffer.put(callsignBytes.size.toByte())
        buffer.put(callsignBytes)

        return buffer.array()
    }

    fun decode(bytes: ByteArray): BeaconPacket? {
        if (bytes.size < HEADER_BYTES + UUID_BYTES + LENGTH_BYTES) {
            return null
        }

        if (bytes[0] != MAGIC_1 || bytes[1] != MAGIC_2) {
            return null
        }

        return try {
            val buffer = ByteBuffer.wrap(bytes)

            buffer.get()
            buffer.get()

            val mostSignificantBits = buffer.getLong()
            val leastSignificantBits = buffer.getLong()

            val callsignLength = buffer.get().toInt() and 0xFF

            if (callsignLength > MAX_CALLSIGN_BYTES) {
                return null
            }

            if (buffer.remaining() < callsignLength) {
                return null
            }

            val callsignBytes = ByteArray(callsignLength)
            buffer.get(callsignBytes)

            val callsign = String(
                callsignBytes,
                StandardCharsets.UTF_8
            )

            if (callsign.isBlank()) {
                return null
            }

            BeaconPacket(
                sender = DeviceId(
                    UUID(
                        mostSignificantBits,
                        leastSignificantBits
                    ).toString()
                ),
                callsign = callsign,
                timestamp = System.currentTimeMillis()
            )
        } catch (_: Exception) {
            null
        }
    }
}