package com.tactical.engine.mesh.deduplication

interface DeduplicationFilter {
    /**
     * Returns true if the hash might already be present in the filter.
     */
    fun mightContain(hash: Long): Boolean

    /**
     * Adds the hash to the filter.
     */
    fun put(hash: Long)
}
