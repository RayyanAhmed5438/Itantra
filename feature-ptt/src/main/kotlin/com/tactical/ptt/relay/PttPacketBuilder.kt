package com.tactical.ptt.relay

import com.tactical.domain.packet.TextPacket
import com.tactical.domain.speech.TranscriptionChunk
import com.tactical.ptt.session.PttSession

/**
 * Constructs a TextPacket from a finalized transcription chunk.
 *
 * The active STT backend is English-only, so the current packet language code
 * is "en". Keeping it on the transcription chunk preserves the existing wire
 * contract and leaves room for a future multilingual backend.
 */
class PttPacketBuilder {
    fun build(session: PttSession, chunk: TranscriptionChunk): TextPacket =
        TextPacket(
            sender = session.deviceId,
            text = chunk.text,
            languageCode = chunk.languageCode,
            timestamp = System.currentTimeMillis()
        )
}
