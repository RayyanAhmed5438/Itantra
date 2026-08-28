package com.tactical.domain.packet

import com.tactical.domain.identity.DeviceId

/**
 * Transcribed text with a single language code — no translation fields,
 * per the team's decision to drop translation entirely. Built from a
 * finished PTT/VOX session; the emitted text is already in the speaker's
 * language, unmodified end to end.
 */
data class TextPacket(
    val sender: DeviceId,
    val text: String,
    val languageCode: String,
    val timestamp: Long
) : Packet {

    init {
        require(text.isNotBlank()) { "text must not be blank" }
        require(languageCode.isNotBlank()) { "languageCode must not be blank" }
    }
}

/**
 *
 * One thing worth flagging since it came up in core.md's
 * LanguageTag section but wasn't reflected in
 * TextPacket's own field list: LanguageTag.kt's
 * doc says it "provides to... TextPacket," implying
 * languageCode: String here might eventually become
 * languageCode: LanguageTag instead of a plain string,
 * for consistency with how TextToSpeech.synthesize(text, langTag: LanguageTag)
 * takes the richer type. I kept it as String since
 * that's explicitly what TextPacket's own field list
 * says — but this is a real inconsistency inside
 * core.md itself worth raising with whoever wrote it,
 * since it affects whether engine-speech's receive
 * path needs to look up a LanguageTag from a raw ISO
 * string every time, or gets one directly off the
 * packet.
 */