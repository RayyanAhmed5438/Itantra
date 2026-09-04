package com.tactical.platform.api.ble

import com.tactical.domain.identity.DeviceId
import com.tactical.domain.packet.BeaconPacket
import kotlinx.coroutines.flow.Flow
import java.nio.ByteBuffer
import java.util.UUID

/**
 * Listens for nearby peers' BLE beacon advertisements. Implemented in
 * platform-android, wrapping Android's BluetoothLeScanner API. Must
 * transparently handle the API 28-30 (location permission) vs API 31+
 * (separate BLE scan/connect permissions) split — callers on the other
 * side of this interface shouldn't need to know which permission model
 * the running device uses.
 */
interface BleBeaconScanner {

    /**
     * Cold Flow of every BLE beacon seen nearby, one ScannedBleDevice per
     * advertisement received — including repeats from the same peer on
     * every beacon interval. No deduplication happens here; that's
     * engine-discovery's job.
     */
    fun scan(): Flow<ScannedBleDevice>
}

class BlePayloadMapper {

    fun toBytes(beacon: BeaconPacket): ByteArray {
        val uuid = UUID.fromString(beacon.sender.value)

        val callsignBytes = beacon.callsign.toByteArray(Charsets.UTF_8)

        require(callsignBytes.size <= MAX_CALLSIGN_BYTES) {
            "callsign too long for BLE beacon"
        }

        val buffer = ByteBuffer.allocate(
            UUID_SIZE +
                    CALLSIGN_LENGTH_SIZE +
                    callsignBytes.size
        )

        buffer.putLong(uuid.mostSignificantBits)
        buffer.putLong(uuid.leastSignificantBits)
        buffer.put(callsignBytes.size.toByte())
        buffer.put(callsignBytes)

        return buffer.array()
    }

    fun toBeaconPacketOrNull(bytes: ByteArray): BeaconPacket? {
        return try {
            if (bytes.size < UUID_SIZE + CALLSIGN_LENGTH_SIZE) {
                return null
            }

            val buffer = ByteBuffer.wrap(bytes)

            val mostSignificantBits = buffer.long
            val leastSignificantBits = buffer.long

            val callsignLength = buffer.get().toInt() and 0xFF

            if (callsignLength > MAX_CALLSIGN_BYTES) return null
            if (buffer.remaining() < callsignLength) return null

            val callsignBytes = ByteArray(callsignLength)
            buffer.get(callsignBytes)

            BeaconPacket(
                sender = DeviceId(
                    UUID(
                        mostSignificantBits,
                        leastSignificantBits
                    ).toString()
                ),
                callsign = String(callsignBytes, Charsets.UTF_8),
                timestamp = System.currentTimeMillis()
            )
        } catch (_: Exception) {
            null
        }
    }

    companion object {
        private const val UUID_SIZE = 16
        private const val CALLSIGN_LENGTH_SIZE = 1
        private const val MAX_CALLSIGN_BYTES = 10
    }
}