package com.tactical.engine.discovery.beacon

interface BeaconEmitter {
    /** Starts periodic heartbeat broadcasts. */
    fun start()
    /** Stops periodic heartbeat broadcasts. */
    fun stop()
}
