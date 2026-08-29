package com.tactical.protocol.validation

import com.tactical.protocol.constants.ProtocolConstants
import java.util.zip.CRC32
import java.nio.ByteBuffer

class Crc32Validator : PacketValidator {
    override fun validate(bytes: ByteArray): Boolean {
        if (bytes.size < 11) return false // Magic(1) + Version(1) + Type(1) + Len(4) + CRC(4)

        val buffer = ByteBuffer.wrap(bytes)
        val magic = buffer.get()
        if (magic != ProtocolConstants.MAGIC_BYTE) return false

        val version = buffer.get()
        if (version != ProtocolConstants.VERSION) return false

        buffer.get() // Skip Type
        val len = buffer.getInt()
        val receivedCrc = buffer.getInt()

        if (bytes.size < 11 + len) return false

        val crc = CRC32()
        crc.update(bytes, 11, len)
        return receivedCrc == crc.value.toInt()
    }
}
