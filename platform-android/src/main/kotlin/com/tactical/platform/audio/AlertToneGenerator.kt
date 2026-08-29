package com.tactical.platform.audio

import com.tactical.domain.audio.AudioConfig
import com.tactical.domain.audio.AudioFrame
import com.tactical.platform.api.audio.AlertTone
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/**
 * Synthesizes alert tones as raw PCM — no TTS model, no audio asset file,
 * just generated waveforms. Exists specifically so an alert sound can play
 * even if the speech models haven't loaded yet (or ever will, on a device
 * where something's gone wrong with them) — the emergency path shouldn't
 * have a hard dependency on the same model pipeline it might be alerting
 * about.
 *
 * SCOPE LIMIT: only produces 16-bit PCM. AudioConfig technically permits
 * 8/24/32-bit, but every real config in this app is 16-bit (AudioConfig()
 * defaults), and correctly generating 24/32-bit (which map to
 * ENCODING_PCM_FLOAT, a different sample representation entirely — see
 * AndroidAudioTrackPlayer/AudioRecordConfigMapper) is meaningfully more
 * work for a case nothing in this app actually exercises. Throws clearly
 * rather than silently producing wrong-format bytes if that ever changes.
 */
class AlertToneGenerator {

    fun generate(tone: AlertTone, config: AudioConfig): AudioFrame {
        require(config.bitDepth == 16) {
            "AlertToneGenerator only supports 16-bit PCM, got bitDepth=${config.bitDepth}"
        }

        val spec = specFor(tone)
        val totalDurationMs = spec.segments.sumOf { it.durationMs }
        val pcmData = renderSegments(spec.segments, config, spec.amplitude)

        return AudioFrame(
            data = pcmData,
            timestamp = System.currentTimeMillis(),
            durationMs = totalDurationMs.toLong()
        )
    }

    /**
     * One tone at one frequency for one duration. A ToneSpec is a sequence
     * of these — a single beep is one segment; a warbling siren is several
     * alternating segments back to back.
     */
    private data class ToneSegment(val frequencyHz: Double, val durationMs: Int)
    private data class ToneSpec(val segments: List<ToneSegment>, val amplitude: Double)

    private fun specFor(tone: AlertTone): ToneSpec = when (tone) {
        AlertTone.EMERGENCY_INCOMING -> ToneSpec(
            // Two-tone warble, three full cycles — deliberately urgent and
            // attention-grabbing, distinct from any normal notification sound.
            segments = List(3) {
                listOf(
                    ToneSegment(frequencyHz = 900.0, durationMs = 300),
                    ToneSegment(frequencyHz = 1400.0, durationMs = 300)
                )
            }.flatten(),
            amplitude = 0.9
        )
        AlertTone.MESSAGE_RECEIVED -> ToneSpec(
            // Single short, quieter chirp — noticeable but not alarming.
            segments = listOf(ToneSegment(frequencyHz = 1000.0, durationMs = 180)),
            amplitude = 0.5
        )
    }

    private fun samplesForDuration(durationMs: Int, sampleRate: Int): Int =
        (durationMs.toLong() * sampleRate / 1000L).toInt()

    private fun renderSegments(segments: List<ToneSegment>, config: AudioConfig, amplitude: Double): ByteArray {
        val bytesPerSample = 2
        val totalBytes = segments.sumOf {
            samplesForDuration(it.durationMs, config.sampleRate) * config.channels * bytesPerSample
        }
        val out = ByteArray(totalBytes)
        var offset = 0

        for (segment in segments) {
            offset = renderSegment(segment, config, amplitude, out, offset)
        }

        return out
    }

    private fun renderSegment(
        segment: ToneSegment,
        config: AudioConfig,
        amplitude: Double,
        out: ByteArray,
        startOffset: Int
    ): Int {
        val sampleCount = samplesForDuration(segment.durationMs, config.sampleRate)
        var offset = startOffset
        val fadeSamples = min(sampleCount / 8, (config.sampleRate * 0.005).toInt())

        for (i in 0 until sampleCount) {
            val angle = 2.0 * PI * segment.frequencyHz * i / config.sampleRate
            val envelope = when {
                fadeSamples <= 0 -> 1.0
                i < fadeSamples -> i.toDouble() / fadeSamples
                i >= sampleCount - fadeSamples -> (sampleCount - i).toDouble() / fadeSamples
                else -> 1.0
            }
            val sampleValue = (sin(angle) * envelope * amplitude * Short.MAX_VALUE).toInt().toShort()

            repeat(config.channels) {
                out[offset] = (sampleValue.toInt() and 0xFF).toByte()
                out[offset + 1] = ((sampleValue.toInt() shr 8) and 0xFF).toByte()
                offset += 2
            }
        }

        return offset
    }
}