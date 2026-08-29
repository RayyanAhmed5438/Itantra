package com.tactical.platform.power

import android.content.Context
import android.os.PowerManager
import com.tactical.platform.api.power.WakeLockManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class AndroidWakeLockManager(private val context: Context) : WakeLockManager {

    private val powerManager: PowerManager by lazy {
        context.getSystemService(Context.POWER_SERVICE) as PowerManager
    }

    private class Held(val wakeLock: PowerManager.WakeLock, val renewalJob: Job)

    private val heldLocks = ConcurrentHashMap<String, Held>()
    private val renewalScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override suspend fun acquire(tag: String) {
        heldLocks.computeIfAbsent(tag) {
            val wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$WAKE_LOCK_TAG_PREFIX:$tag").apply {
                setReferenceCounted(false)
                acquire(SAFETY_TIMEOUT_MS)
            }

            // Renews before the safety timeout lapses, so a still-legitimate
            // long-running hold (engine-mesh's relay, feature-sentry's cold-
            // wake listener) is never actually cut off by it — the timeout
            // is a leak safety net, not an intended hold duration.
            val renewalJob = renewalScope.launch {
                while (isActive) {
                    delay(RENEWAL_INTERVAL_MS)
                    if (wakeLock.isHeld) {
                        wakeLock.acquire(SAFETY_TIMEOUT_MS)
                    }
                }
            }

            Held(wakeLock, renewalJob)
        }
    }

    override suspend fun release(tag: String) {
        heldLocks.remove(tag)?.let { held ->
            held.renewalJob.cancel()
            if (held.wakeLock.isHeld) held.wakeLock.release()
        }
    }

    companion object {
        private const val WAKE_LOCK_TAG_PREFIX = "TacticalMesh"

        // Safety net against a leaked wake lock (e.g. a crash before
        // release() runs), not an intended hold duration — legitimate
        // long-running holds are kept alive via the renewal loop above.
        private const val SAFETY_TIMEOUT_MS = 10 * 60 * 1000L // 10 minutes
        private const val RENEWAL_INTERVAL_MS = SAFETY_TIMEOUT_MS - 60_000L // renew 1 min before expiry
    }
}