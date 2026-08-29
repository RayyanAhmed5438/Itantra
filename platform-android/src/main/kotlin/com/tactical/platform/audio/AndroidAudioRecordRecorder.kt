package com.tactical.platform.audio

import android.annotation.SuppressLint
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Process
import com.tactical.domain.audio.AudioConfig
import com.tactical.domain.audio.AudioFrame
import com.tactical.platform.api.audio.AudioRecorder
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Implements AudioRecorder via Android's AudioRecord. Two independent
 * capture modes, deliberately kept as two separate AudioRecord sessions
 * rather than sharing one: start() is the full-fidelity capture PTT/VOX
 * actually transmits, while startMonitoring() is a cheap, low-priority
 * energy scan used only to decide *when* to bother starting the expensive
 * one. Running both concurrently on the same physical mic is fine — only
 * one AudioRecord session of each *kind* may be active at a time, tracked
 * independently below.
 */
class AndroidAudioRecordRecorder : AudioRecorder {

    private val isRecording = AtomicBoolean(false)
    private val isMonitoring = AtomicBoolean(false)

    @SuppressLint("MissingPermission") // Enforced by PermissionGateway before this is ever called.
    override fun start(config: AudioConfig): Flow<AudioFrame> = callbackFlow {
        if (!isRecording.compareAndSet(false, true)) {
            throw IllegalStateException(
                "start() called while already recording — call stop() first."
            )
        }

        val bufferSizeBytes = maxOf(
            AudioRecordConfigMapper.minBufferSizeBytes(config),
            config.frameSizeInBytes
        )

        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            config.sampleRate,
            AudioRecordConfigMapper.toChannelConfig(config),
            AudioRecordConfigMapper.toAudioFormatEncoding(config),
            bufferSizeBytes
        )

        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            isRecording.set(false)
            audioRecord.release()
            throw IllegalStateException("AudioRecord failed to initialize for config: $config")
        }

        audioRecord.startRecording()

        val readThread = Thread({
            val chunk = ByteArray(config.frameSizeInBytes)
            try {
                while (isRecording.get()) {
                    val bytesRead = audioRecord.read(chunk, 0, chunk.size)
                    if (bytesRead <= 0) continue // transient read failure or recorder stopping

                    val frame = AudioFrame(
                        data = chunk.copyOf(bytesRead),
                        timestamp = System.currentTimeMillis(),
                        durationMs = config.chunkDurationMs
                    )
                    trySend(frame)
                }
            } finally {
                audioRecord.stop()
                audioRecord.release()
                close()
            }
        }, "AudioRecordReadThread")
        readThread.start()

        awaitClose {
            isRecording.set(false)
            readThread.join(500)
        }
    }

    override fun stop() {
        isRecording.set(false)
    }

    @SuppressLint("MissingPermission")
    override fun startMonitoring(config: AudioConfig, thresholdDb: Double): Flow<Unit> = callbackFlow {
        if (!isMonitoring.compareAndSet(false, true)) {
            throw IllegalStateException(
                "startMonitoring() called while already monitoring — cancel the existing collection first."
            )
        }

        // Deliberately tiny buffer, independent of AudioConfig.frameSizeInBytes —
        // monitoring only needs enough samples to compute a rough energy level,
        // not full-fidelity frames. 512 bytes ≈ 16ms at 16kHz/16-bit/mono.
        val monitorBufferSizeBytes = maxOf(
            AudioRecordConfigMapper.minBufferSizeBytes(config),
            512
        )

        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            config.sampleRate,
            AudioRecordConfigMapper.toChannelConfig(config),
            AudioRecordConfigMapper.toAudioFormatEncoding(config),
            monitorBufferSizeBytes
        )

        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            isMonitoring.set(false)
            audioRecord.release()
            throw IllegalStateException("AudioRecord failed to initialize for monitoring config: $config")
        }

        audioRecord.startRecording()

        val monitorThread = Thread({
            // Best-effort low priority — this thread should never compete with
            // anything transmit-critical for CPU time.
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)

            val chunk = ShortArray(monitorBufferSizeBytes / 2)
            try {
                while (isMonitoring.get()) {
                    val samplesRead = audioRecord.read(chunk, 0, chunk.size)
                    if (samplesRead <= 0) continue

                    val db = computeDbFullScale(chunk, samplesRead)
                    if (db >= thresholdDb) {
                        trySend(Unit)
                    }
                }
            } finally {
                audioRecord.stop()
                audioRecord.release()
                close()
            }
        }, "AudioMonitorThread")
        monitorThread.start()

        awaitClose {
            isMonitoring.set(false)
            monitorThread.join(500)
        }
    }

    private fun computeDbFullScale(samples: ShortArray, count: Int): Double {
        var sumSquares = 0.0
        for (i in 0 until count) {
            val normalized = samples[i] / Short.MAX_VALUE.toDouble()
            sumSquares += normalized * normalized
        }
        val rms = sqrt(sumSquares / count)
        if (rms <= 0.0) return Double.NEGATIVE_INFINITY
        return 20 * log10(rms)
    }
}