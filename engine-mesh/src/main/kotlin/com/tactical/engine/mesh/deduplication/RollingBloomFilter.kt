package com.tactical.engine.mesh.deduplication

import java.util.BitSet
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Time-windowed Bloom filter using two BitSet buckets rotated every 10 seconds.
 * Provides a rolling window of deduplication to prevent packet loops while
 * avoiding memory growth over time.
 */
class RollingBloomFilter(
    private val size: Int = 1024 * 8, // 8Kb per bitset
    private val numHashes: Int = 3
) : DeduplicationFilter {

    private var currentBucket = BitSet(size)
    private var previousBucket = BitSet(size)
    private val lock = Any()

    init {
        // In a real Android app, we might use a Coroutine or a Lifecycle-aware timer.
        // For a JVM module, a ScheduledExecutor is fine.
        Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate({
            rotate()
        }, 10, 10, TimeUnit.SECONDS)
    }

    override fun mightContain(hash: Long): Boolean = synchronized(lock) {
        val indices = getIndices(hash)
        val inCurrent = indices.all { currentBucket.get(it) }
        if (inCurrent) return true
        
        return indices.all { previousBucket.get(it) }
    }

    override fun put(hash: Long) = synchronized(lock) {
        val indices = getIndices(hash)
        indices.forEach { currentBucket.set(it) }
    }

    private fun rotate() = synchronized(lock) {
        previousBucket = currentBucket
        currentBucket = BitSet(size)
    }

    private fun getIndices(hash: Long): List<Int> {
        val result = mutableListOf<Int>()
        var h = hash
        for (i in 0 until numHashes) {
            h = h xor (h ushr 33)
            h *= -0xae502812aa7333L
            h = h xor (h ushr 33)
            h *= -0x3b3146010f6d7dL
            h = h xor (h ushr 33)
            
            val index = (h % size).toInt()
            result.add(if (index < 0) index + size else index)
        }
        return result
    }
}
