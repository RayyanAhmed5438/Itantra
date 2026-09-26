package com.tactical.platform.ble

import java.nio.ByteBuffer
import java.util.UUID

/**
 * Fixed-size application control frames carried over the existing BLE GATT
 * characteristic. Every frame is exactly 20 bytes so it fits the default
 * ATT payload before MTU negotiation.
 */
internal object SquadControlCodec {
    private const val MAGIC_1: Byte = 0x53 // S
    private const val MAGIC_2: Byte = 0x51 // Q
    private const val VERSION: Byte = 1

    private const val TYPE_HELLO: Byte = 1
    private const val TYPE_REQUEST: Byte = 2
    private const val TYPE_ACCEPT: Byte = 3
    private const val TYPE_REJECT: Byte = 4
    private const val TYPE_REMOVE: Byte = 5

    private const val FRAME_BYTES = 20
    private const val HEADER_BYTES = 4
    private const val UUID_BYTES = 16

    sealed interface Message {
        val deviceId: String

        data class Hello(override val deviceId: String) : Message
        data class Request(override val deviceId: String) : Message
        data class Response(
            override val deviceId: String,
            val accepted: Boolean
        ) : Message
        data class Remove(override val deviceId: String) : Message
    }

    fun hello(deviceId: String): ByteArray =
        encode(TYPE_HELLO, deviceId)

    fun request(deviceId: String): ByteArray =
        encode(TYPE_REQUEST, deviceId)

    fun response(deviceId: String, accepted: Boolean): ByteArray =
        encode(if (accepted) TYPE_ACCEPT else TYPE_REJECT, deviceId)

    fun remove(deviceId: String): ByteArray =
        encode(TYPE_REMOVE, deviceId)

    fun decode(bytes: ByteArray): Message? {
        if (bytes.size != FRAME_BYTES) return null

        return runCatching {
            val buffer = ByteBuffer.wrap(bytes)
            if (buffer.get() != MAGIC_1 || buffer.get() != MAGIC_2) return null
            if (buffer.get() != VERSION) return null

            when (val type = buffer.get()) {
                TYPE_HELLO -> Message.Hello(readUuid(buffer))
                TYPE_REQUEST -> Message.Request(readUuid(buffer))
                TYPE_ACCEPT -> Message.Response(readUuid(buffer), true)
                TYPE_REJECT -> Message.Response(readUuid(buffer), false)
                TYPE_REMOVE -> Message.Remove(readUuid(buffer))
                else -> null
            }
        }.getOrNull()
    }

    private fun encode(type: Byte, deviceId: String): ByteArray {
        val uuid = UUID.fromString(deviceId)
        return ByteBuffer.allocate(FRAME_BYTES).apply {
            put(MAGIC_1)
            put(MAGIC_2)
            put(VERSION)
            put(type)
            putLong(uuid.mostSignificantBits)
            putLong(uuid.leastSignificantBits)
        }.array()
    }

    private fun readUuid(buffer: ByteBuffer): String {
        check(buffer.remaining() == UUID_BYTES)
        return UUID(buffer.long, buffer.long).toString()
    }
}
