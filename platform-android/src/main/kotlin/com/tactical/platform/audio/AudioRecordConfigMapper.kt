package com.tactical.platform.audio

import android.media.AudioFormat
import com.tactical.domain.audio.AudioConfig

/**
 * Converts our bearer-agnostic AudioConfig into the parameters Android's
 * AudioRecord builder actually expects. Kept as its own small object
 * rather than inlined into AndroidAudioRecordRecorder so the
 * config-to-Android-constant mapping is independently testable and
 * doesn't clutter the recorder's own logic.
 */

object AudioRecordConfigMapper {

    fun toChannelConfig(config: AudioConfig): Int = when (config.channels) {
        1 -> AudioFormat.CHANNEL_IN_MONO
        2 -> AudioFormat.CHANNEL_IN_STEREO
        else -> throw IllegalArgumentException(
            "Unsupported channel count: ${config.channels}. AudioConfig only permits 1 or 2."
        )
    }

    fun toAudioFormatEncoding(config: AudioConfig): Int = when (config.bitDepth) {
        8 -> AudioFormat.ENCODING_PCM_8BIT
        16 -> AudioFormat.ENCODING_PCM_16BIT
        24, 32 -> AudioFormat.ENCODING_PCM_FLOAT
        else -> throw IllegalArgumentException(
            "Unsupported bitDepth: ${config.bitDepth}. AudioConfig only permits 8, 16, 24, or 32."
        )
    }

    /**
     * Minimum buffer size Android's AudioRecord needs for this config,
     * queried from the platform itself rather than computed by hand —
     * device/driver-dependent, so there's no fixed formula that's safe
     * across all hardware.
     */
    fun minBufferSizeBytes(config: AudioConfig): Int {
        val minSize = android.media.AudioRecord.getMinBufferSize(
            config.sampleRate,
            toChannelConfig(config),
            toAudioFormatEncoding(config)
        )
        require(minSize > 0) {
            "AudioRecord.getMinBufferSize returned $minSize for this config — " +
                    "likely an unsupported sampleRate/channel/bitDepth combination on this device"
        }
        return minSize
    }
}