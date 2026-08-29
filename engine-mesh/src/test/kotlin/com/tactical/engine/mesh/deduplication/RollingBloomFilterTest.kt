package com.tactical.engine.mesh.deduplication

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RollingBloomFilterTest {

    @Test
    fun `should remember added hashes`() {
        val filter = RollingBloomFilter()
        val hash = 12345L
        
        assertFalse(filter.mightContain(hash))
        filter.put(hash)
        assertTrue(filter.mightContain(hash))
    }

    @Test
    fun `should not have false negatives`() {
        val filter = RollingBloomFilter()
        val hashes = listOf(1L, 2L, 3L, 4L, 5L)
        
        hashes.forEach { filter.put(it) }
        hashes.forEach { assertTrue(filter.mightContain(it)) }
    }
}
