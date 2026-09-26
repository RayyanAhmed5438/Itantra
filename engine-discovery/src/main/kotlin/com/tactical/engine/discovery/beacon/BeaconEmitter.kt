package com.tactical.engine.discovery.beacon

interface BeaconEmitter {
    /** Starts periodic heartbeat broadcasts. */
    fun start()
    /** Stops periodic heartbeat broadcasts and waits for the old job to finish. */
    suspend fun stop()
}
