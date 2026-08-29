package com.tactical.platform.ble

import com.tactical.domain.packet.BeaconPacket
import com.tactical.domain.packet.Packet
import com.tactical.protocol.serialization.PacketSerializer

/**
 * Converts ByteArray ↔ Packet via core-protocol's PacketSerializer — the
 * seam between raw bytes seen at the BLE advertising layer and the domain
 * Packet types everything above platform-android works with.
 *
 * Scoped to BeaconPacket specifically: that's the only Packet type that
 * ever travels as a BLE *advertisement* payload (AndroidBleAdvertiser /
 * AndroidBleScanner). VoicePacket/TextPacket/EmergencyPacket travel over
 * an already-established GATT connection instead (BleRadioTransport),
 * which hands raw bytes straight to core-protocol at the engine-mesh
 * layer — this mapper isn't involved in that path.
 */
class BlePayloadMapper(private val packetSerializer: PacketSerializer) {

    fun toBytes(beacon: BeaconPacket): ByteArray = packetSerializer.serialize(beacon)

    /**
     * Returns null rather than throwing on anything that doesn't parse as
     * a well-formed BeaconPacket. Malformed or foreign BLE advertisements
     * (another app's beacon, a torn payload from the 27-byte truncation
     * risk noted in AndroidBleAdvertiser, a non-beacon Packet type) are
     * expected on shared spectrum and shouldn't crash the scan Flow.
     */
    fun toBeaconPacketOrNull(bytes: ByteArray): BeaconPacket? =
        try {
            when (val packet: Packet = packetSerializer.deserialize(bytes)) {
                is BeaconPacket -> packet
                else -> null
            }
        } catch (e: Exception) {
            null
        }
}