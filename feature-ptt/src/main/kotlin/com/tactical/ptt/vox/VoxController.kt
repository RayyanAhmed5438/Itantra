package com.tactical.ptt.vox

/**
 * Interface controlling hands-free Voice Operated Exchange (VOX).
 */
interface VoxController {
    /** Begins low-power background energy monitoring to detect vocal activation. */
    fun enable()

    /** Halts background monitoring and resets any active triggers. */
    fun disable()
}