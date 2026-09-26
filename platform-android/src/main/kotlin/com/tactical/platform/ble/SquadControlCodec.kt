package com.tactical.platform.ble

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * Small control protocol carried over the BLE GATT control characteristic.
 *
 * This is separate from the normal mesh packet protocol because squad
 * membership must work before a peer is a squad member.
 */
internal object SquadControlCodec {
    private const val MAGIC_1: Byte = 0x53 // S
    private const val MAGIC_2: Byte = 0x51 // Q
    private const val VERSION: Byte = 1

    private const val TYPE_HELLO: Byte = 1
    private const val TYPE_REQUEST: Byte = 2
    private const val TYPE_RESPONSE: Byte = 3
    private const val TYPE_REMOVE: Byte = 4

    private const val UUID_BYTES = 16
    private const val MAX_CALLSIGN_BYTES = 64

    sealed interface Message {
        val deviceId: String

        data class Hello(
            override val deviceId: String,
            val callsign: String
        ) : Message

        data class Request(
            override val deviceId: String,
            val callsign: String
        ) : Message

        data class Response(
            override val deviceId: String,
            val accepted: Boolean
        ) : Message

        data class Remove(
            override val deviceId: String
        ) : Message
    }

    fun hello(deviceId: String, callsign: String): ByteArray =
        encodeIdentity(TYPE_HELLO, deviceId, callsign)

    fun request(deviceId: String, callsign: String): ByteArray =
        encodeIdentity(TYPE_REQUEST, deviceId, callsign)

    fun response(deviceId: String, accepted: Boolean): ByteArray =
        encodeSimple(TYPE_RESPONSE, deviceId, if (accepted) 1 else 0)

    fun remove(deviceId: String): ByteArray =
        encodeSimple(TYPE_REMOVE, deviceId, null)

    fun decode(bytes: ByteArray): Message? {
        if (bytes.size < 3 + UUID_BYTES) return null

        return runCatching {
            val buffer = ByteBuffer.wrap(bytes)
            if (buffer.get() != MAGIC_1 || buffer.get() != MAGIC_2) return null
            if (buffer.get() != VERSION) return null

            val type = buffer.get()
            val deviceId = readUuid(buffer)

            when (type) {
                TYPE_HELLO,
                TYPE_REQUEST -> {
                    val length = buffer.get().toInt() and 0xFF
                    if (length > MAX_CALLSIGN_BYTES || buffer.remaining() < length) return null
                    val callsignBytes = ByteArray(length)
                    buffer.get(callsignBytes)
                    val callsign = String(callsignBytes, StandardCharsets.UTF_8)
                    if (callsign.isBlank()) return null
                    if (type == TYPE_HELLO) {
                        Message.Hello(deviceId, callsign)
                    } else {
                        Message.Request(deviceId, callsign)
                    }
                }

                TYPE_RESPONSE -> {
                    if (!buffer.hasRemaining()) return null
                    Message.Response(deviceId, buffer.get().toInt() != 0)
                }

                TYPE_REMOVE -> Message.Remove(deviceId)

                else -> null
            }
        }.getOrNull()
    }

    private fun encodeIdentity(
        type: Byte,
        deviceId: String,
        callsign: String
    ): ByteArray {
        val uuid = UUID.fromString(deviceId)
        val callsignBytes = callsign.toByteArray(StandardCharsets.UTF_8)
        require(callsignBytes.isNotEmpty()) { "callsign must not be empty" }
        require(callsignBytes.size <= MAX_CALLSIGN_BYTES) {
            "callsign is too long for BLE squad control"
        }

        return ByteBuffer.allocate(3 + 1 + UUID_BYTES + 1 + callsignBytes.size).apply {
            put(MAGIC_1)
            put(MAGIC_2)
            put(VERSION)
            put(type)
            putLong(uuid.mostSignificantBits)
            putLong(uuid.leastSignificantBits)
            put(callsignBytes.size.toByte())
            put(callsignBytes)
        }.array()
    }

    private fun encodeSimple(
        type: Byte,
        deviceId: String,
        flag: Int?
    ): ByteArray {
        val uuid = UUID.fromString(deviceId)
        return ByteBuffer.allocate(3 + 1 + UUID_BYTES + if (flag == null) 0 else 1).apply {
            put(MAGIC_1)
            put(MAGIC_2)
            put(VERSION)
            put(type)
            putLong(uuid.mostSignificantBits)
            putLong(uuid.leastSignificantBits)
            if (flag != null) put(flag.toByte())
        }.array()
    }

    private fun readUuid(buffer: ByteBuffer): String {
        val most = buffer.long
        val least = buffer.long
        return UUID(most, least).toString()
    }
}
