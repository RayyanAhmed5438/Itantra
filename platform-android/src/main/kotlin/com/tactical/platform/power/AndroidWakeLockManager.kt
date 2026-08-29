package com.tactical.platform.power

import android.content.Context
import android.os.PowerManager
import com.tactical.platform.api.power.WakeLockManager
import java.util.concurrent.ConcurrentHashMap

/**
 * Implements WakeLockManager wrapping PowerManager.WakeLock, keyed by tag.
 * acquire() is idempotent per the interface's kdoc — a second acquire()
 * for an already-held tag is a no-op. Android's own WakeLock *does*
 * support ref-counted stacking (setReferenceCounted(true)), but that's a
 * different contract than "idempotent," so it's deliberately turned off
 * here in favor of the ConcurrentHashMap itself being the source of truth
 * for whether a tag is held.
 */
class AndroidWakeLockManager(private val context: Context) : WakeLockManager {

    private val powerManager: PowerManager by lazy {
        context.getSystemService(Context.POWER_SERVICE) as PowerManager
    }
    private val heldLocks = ConcurrentHashMap<String, PowerManager.WakeLock>()

    override suspend fun acquire(tag: String) {
        heldLocks.computeIfAbsent(tag) {
            powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$WAKE_LOCK_TAG_PREFIX:$tag").apply {
                setReferenceCounted(false)
                acquire()
            }
        }
    }

    override suspend fun release(tag: String) {
        heldLocks.remove(tag)?.let { lock -> if (lock.isHeld) lock.release() }
    }

    companion object {
        // Android expects wake lock tags to carry an "app:purpose" prefix
        // for battery-attribution in system dumps (adb shell dumpsys power).
        private const val WAKE_LOCK_TAG_PREFIX = "TacticalMesh"
    }
}