package com.tactical.platform.speech.mms

import org.json.JSONObject
import java.io.File
import java.util.Locale

/**
 * Android implementation of the MMS VitsTokenizer configuration used by the
 * supplied models. The supplied checkpoints are configured as character-level
 * VITS tokenizers with add_blank=true, normalize=true, phonemize=false and
 * is_uroman=false.
 */
class MmsVitsTokenizer(modelDirectory: File) {

    private val config =
        JSONObject(File(modelDirectory, "tokenizer_config.json").readText(Charsets.UTF_8))
    private val vocab =
        JSONObject(File(modelDirectory, "vocab.json").readText(Charsets.UTF_8))

    private val normalize = config.optBoolean("normalize", true)
    private val addBlank = config.optBoolean("add_blank", true)
    private val phonemize = config.optBoolean("phonemize", false)
    private val isUroman = config.optBoolean("is_uroman", false)
    private val language = config.optString("language", "")

    init {
        require(!phonemize) {
            "MMS TTS tokenizer '" + language +
                "' requires phonemization, which is not supported by this Android tester"
        }
        require(!isUroman) {
            "MMS TTS tokenizer '" + language +
                "' requires uroman, which is not supported by this Android tester"
        }
        require(vocab.length() > 0) { "vocab.json is empty for language '" + language + "'" }
    }

    data class TokenizedText(
        val normalizedText: String,
        val ids: LongArray
    )

    fun tokenize(text: String): TokenizedText {
        require(text.isNotBlank()) { "text must not be blank" }

        val normalized = if (normalize) {
            text.lowercase(Locale.ROOT)
        } else {
            text
        }

        val filtered = buildString {
            for (char in normalized) {
                val token = char.toString()
                if (vocab.has(token)) append(char)
            }
        }.trim()

        require(filtered.isNotBlank()) {
            "No characters from the input are present in the '" + language + "' vocabulary"
        }

        val ids = LongArray(
            if (addBlank) filtered.length * 2 + 1 else filtered.length
        )

        if (addBlank) {
            for (i in filtered.indices) {
                val base = i * 2
                ids[base] = 0L
                ids[base + 1] = vocab.getLong(filtered[i].toString())
            }
            ids[ids.lastIndex] = 0L
        } else {
            for (i in filtered.indices) {
                ids[i] = vocab.getLong(filtered[i].toString())
            }
        }

        return TokenizedText(filtered, ids)
    }
}
