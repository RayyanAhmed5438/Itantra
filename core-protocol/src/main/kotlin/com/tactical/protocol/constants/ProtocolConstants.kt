package com.tactical.protocol.constants

object ProtocolConstants {
    const val MAGIC_BYTE: Byte = 0x5A
    const val VERSION: Byte = 0x01
    const val DEFAULT_TTL = 5
    const val MAX_HOPS = 10
    const val BEACON_INTERVAL_MS = 2000L
    const val MAX_PACKET_SIZE = 1024
}
