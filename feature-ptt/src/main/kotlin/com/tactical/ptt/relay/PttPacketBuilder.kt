package com.tactical.ptt.relay

import com.tactical.domain.packet.TextPacket
import com.tactical.platform.api.speech.TranscriptionChunk
import com.tactical.ptt.session.PttSession

/**
 * Constructs a TextPacket from session metadata and a finalized
 * transcription chunk. Uses chunk.languageCode (what the multilingual
 * STT model actually detected for this utterance), not
 * session.languageTag, since the two could differ mid-session — see
 * PttSession's open question about languageTag's actual purpose.
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
