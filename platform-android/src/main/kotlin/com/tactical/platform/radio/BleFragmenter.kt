package com.tactical.platform.radio

import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap

/**
 * Splits a packet larger than one GATT write can carry into numbered
 * fragments, and reassembles them on the receiving side. Exists because
 * ProtocolConstants.MAX_PACKET_SIZE (1024 bytes) routinely exceeds BLE's
 * negotiated MTU (commonly 20-512 usable bytes depending on both devices'
 * stacks) — without this, anything but the shortest packets silently fail
 * to transmit over BLE.
 *
 * Fragment wire format (8-byte header + payload):
 * [transferId: Int (4B)][chunkIndex: Short (2B)][totalChunks: Short (2B)]
 *
 * transferId only needs to be unique among a sender's concurrently
 * in-flight fragmented transmissions to the same peer — it has nothing to
 * do with mesh-level dedup (XxHashPacketHasher), which only ever sees the
 * fully reassembled bytes, never individual fragments.
 */
object BleFragmenter {

    private const val HEADER_SIZE = 8

    fun fragment(data: ByteArray, transferId: Int, maxChunkPayloadSize: Int): List<ByteArray> {
        val maxPayload = maxChunkPayloadSize - HEADER_SIZE
        require(maxPayload > 0) {
            "MTU too small to fragment: need at least ${HEADER_SIZE + 1} usable bytes, got $maxChunkPayloadSize"
        }

        val totalChunks = ((data.size + maxPayload - 1) / maxPayload).coerceAtLeast(1)
        require(totalChunks <= Short.MAX_VALUE) {
            "Packet too large to fragment at this MTU: would need $totalChunks chunks"
        }

        return (0 until totalChunks).map { chunkIndex ->
            val start = chunkIndex * maxPayload
            val end = minOf(start + maxPayload, data.size)
            val chunkPayload = data.copyOfRange(start, end)

            ByteBuffer.allocate(HEADER_SIZE + chunkPayload.size).apply {
                putInt(transferId)
                putShort(chunkIndex.toShort())
                putShort(totalChunks.toShort())
                put(chunkPayload)
            }.array()
        }
    }
}

/**
 * Buffers incoming fragments per (peer, transferId) and hands back the
 * complete bytes once every chunk has arrived. One instance is meant to
 * be shared across all connections a BleRadioTransport is receiving from.
 */
class BleFragmentReassembler {

    private data class TransferKey(val peerAddress: String, val transferId: Int)
    private class InProgress(totalChunks: Int) {
        val chunks = arrayOfNulls<ByteArray>(totalChunks)
        var received = 0
    }

    private val inProgress = ConcurrentHashMap<TransferKey, InProgress>()

    /**
     * Feed one received fragment. Returns the fully reassembled bytes once
     * this was the last missing chunk for its transfer, null otherwise.
     * Malformed fragments (too short to contain a header) are dropped
     * silently rather than crashing the receive path — a single corrupted
     * BLE write shouldn't take down the whole incoming Flow.
     */
    fun onFragmentReceived(peerAddress: String, fragment: ByteArray): ByteArray? {
        if (fragment.size < 8) return null

        val buffer = ByteBuffer.wrap(fragment)
        val transferId = buffer.int
        val chunkIndex = buffer.short.toInt()
        val totalChunks = buffer.short.toInt()
        if (chunkIndex !in 0 until totalChunks) return null

        val chunkPayload = ByteArray(fragment.size - 8)
        buffer.get(chunkPayload)

        val key = TransferKey(peerAddress, transferId)
        val transfer = inProgress.getOrPut(key) { InProgress(totalChunks) }

        if (transfer.chunks[chunkIndex] == null) {
            transfer.chunks[chunkIndex] = chunkPayload
            transfer.received++
        }

        if (transfer.received < totalChunks) return null

        inProgress.remove(key)
        val totalSize = transfer.chunks.sumOf { it!!.size }
        val result = ByteArray(totalSize)
        var offset = 0
        for (chunk in transfer.chunks) {
            chunk!!.copyInto(result, offset)
            offset += chunk.size
        }
        return result
    }

    /** Drops any incomplete transfer state for a peer — call on disconnect
     *  so a half-received transmission doesn't leak memory forever if the
     *  remaining fragments never arrive. */
    fun clearPeer(peerAddress: String) {
        inProgress.keys.removeAll { it.peerAddress == peerAddress }
    }
}