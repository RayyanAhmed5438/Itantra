package com.tactical.ptt.relay

import com.tactical.domain.packet.Severity
import com.tactical.domain.packet.TextPacket
import com.tactical.feature.ptt.session.PttSession
import com.tactical.platform.api.speech.TranscriptionChunk
import java.util.UUID

/**
 * Constructs a TextPacket from session metadata and a finalized transcription chunk.
 */
class PttPacketBuilder {

    fun build(session: PttSession, chunk: TranscriptionChunk): TextPacket {
        return TextPacket(
            id = UUID.randomUUID().toString(),
            senderId = session.deviceId,
            recipientId = null, // Broadcast across the tactical mesh
            timestamp = System.currentTimeMillis(),
            ttl = 3,
            text = chunk.text,
            languageTag = session.languageTag,
            severity = Severity.NORMAL
        )
    }
}