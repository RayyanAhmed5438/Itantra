package com.tactical.platform.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.tactical.domain.audio.AudioConfig
import com.tactical.domain.audio.AudioFrame
import com.tactical.platform.api.audio.AlertTone
import com.tactical.platform.api.audio.AudioPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Implements AudioPlayer via Android's AudioTrack. Plays both mic-captured
 * frames (short, ~20ms, from AudioRecorder) and TTS-synthesized frames
 * (a whole sentence in one AudioFrame, potentially seconds long) through
 * the same STREAM-mode AudioTrack — AudioTrack.write() blocks internally
 * as needed regardless of buffer size, so no special-casing is needed for
 * the two very different frame durations that flow through this class.
 *
 * ASSUMPTION FLAGGED: AudioFrame carries no AudioConfig of its own (a gap
 * noted when SpeechToText/TextToSpeech were designed — TTS output and mic
 * input both produce plain AudioFrames with no format metadata attached).
 * This class assumes every AudioFrame it receives matches the app's one
 * standard format (AudioConfig() defaults: 16kHz, mono, 16-bit PCM) and
 * configures its single AudioTrack accordingly at construction. If a
 * frame from a source using a different format is ever played through
 * this class, it will be audibly wrong (pitch-shifted/garbled) with no
 * error raised. Worth confirming this "one global format" assumption is
 * actually guaranteed end to end — by AndroidAudioRecordRecorder,
 * TfliteTextToSpeech/OnnxTextToSpeech, and AlertToneGenerator all
 * agreeing — before this ships.
 */
class AndroidAudioTrackPlayer(
    private val alertToneGenerator: AlertToneGenerator,
    private val playbackConfig: AudioConfig = AudioConfig()
) : AudioPlayer {

    private val audioTrack: AudioTrack by lazy { buildAudioTrack() }

    override suspend fun play(frame: AudioFrame) = withContext(Dispatchers.IO) {
        ensurePlaying()
        writeBlocking(frame.data)
    }

    override suspend fun playAlert(tone: AlertTone) = withContext(Dispatchers.IO) {
        val toneFrame = alertToneGenerator.generate(tone, playbackConfig)
        ensurePlaying()
        writeBlocking(toneFrame.data)
    }

    override suspend fun stopPlayback() = withContext(Dispatchers.IO) {
        // pause() halts output immediately; flush() discards whatever was
        // still buffered internally so a stale tail doesn't play once
        // playback resumes on the next play()/playAlert() call.
        if (audioTrack.playState == AudioTrack.PLAYSTATE_PLAYING) {
            audioTrack.pause()
        }
        audioTrack.flush()
    }

    private fun ensurePlaying() {
        if (audioTrack.playState != AudioTrack.PLAYSTATE_PLAYING) {
            audioTrack.play()
        }
    }

    private fun writeBlocking(data: ByteArray) {
        var offset = 0
        while (offset < data.size) {
            val written = audioTrack.write(data, offset, data.size - offset)
            if (written < 0) {
                throw IllegalStateException("AudioTrack.write failed with error code $written")
            }
            offset += written
        }
    }

    private fun buildAudioTrack(): AudioTrack {
        val channelOutConfig = when (playbackConfig.channels) {
            1 -> AudioFormat.CHANNEL_OUT_MONO
            2 -> AudioFormat.CHANNEL_OUT_STEREO
            else -> throw IllegalArgumentException(
                "Unsupported channel count for playback: ${playbackConfig.channels}"
            )
        }
        val encoding = when (playbackConfig.bitDepth) {
            8 -> AudioFormat.ENCODING_PCM_8BIT
            16 -> AudioFormat.ENCODING_PCM_16BIT
            24, 32 -> AudioFormat.ENCODING_PCM_FLOAT
            else -> throw IllegalArgumentException(
                "Unsupported bitDepth for playback: ${playbackConfig.bitDepth}"
            )
        }

        val minBufferSize = AudioTrack.getMinBufferSize(playbackConfig.sampleRate, channelOutConfig, encoding)
        require(minBufferSize > 0) {
            "AudioTrack.getMinBufferSize returned $minBufferSize for $playbackConfig"
        }

        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(playbackConfig.sampleRate)
                    .setChannelMask(channelOutConfig)
                    .setEncoding(encoding)
                    .build()
            )
            .setBufferSizeInBytes(minBufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
    }
}