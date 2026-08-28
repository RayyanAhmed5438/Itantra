package com.tactical.domain.speech

/**
 * Keys a model asset by logical name (e.g. "stt_multilingual",
 * "tts_multilingual"), not by file path — platform-android's
 * AssetModelProvider resolves the actual file location. Equality is by
 * string, same as any other value class.
 */
@JvmInline
value class ModelIdentifier(val value: String) {
    init {
        require(value.isNotBlank()) { "ModelIdentifier must not be blank" }
    }

    override fun toString(): String = value
}

/**
 * One real consequence worth flagging to whoever
 * calls this: since there's no compiler-enforced
 * distinction anymore, any code doing
 * ensureModelAvailable(ModelIdentifier("stt_multilingual"))
 * vs (ModelIdentifier("tts_multilingual")) is now
 * relying on getting the string exactly right by
 * convention alone — worth those two literal strings
 * living in exactly one shared constant somewhere
 * (not re-typed at every call site) to avoid a typo
 * silently creating a third, always-Missing "model."
 */