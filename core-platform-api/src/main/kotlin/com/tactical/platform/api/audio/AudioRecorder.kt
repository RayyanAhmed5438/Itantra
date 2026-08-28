package com.tactical.platform.api.audio

import com.tactical.domain.audio.AudioConfig
import com.tactical.domain.audio.AudioFrame
import kotlinx.coroutines.flow.Flow

/**
 * Captures microphone audio as a stream of AudioFrames. Implemented in
 * platform-android, wrapping Android's AudioRecord API.
 *
 * Multiple start() calls without an intervening stop() are illegal per the
 * team's spec — calling start() while already recording should throw
 * (likely IllegalStateException), not silently restart or stack a second
 * session. Enforcing that is the implementation's responsibility; this
 * interface just documents the contract.
 */
interface AudioRecorder {

    /**
     * Starts capturing at the given config, emitting one AudioFrame every
     * config.chunkDurationMs until stop() is called. Cold Flow — nothing
     * happens until a collector subscribes.
     */
    fun start(config: AudioConfig): Flow<AudioFrame>

    /**
     * Stops any active recording and releases the underlying hardware
     * resource. Safe to call even if nothing is currently recording.
     */
    fun stop()

    fun startMonitoring(config: AudioConfig, thresholdDb: Double): Flow<Unit>
}