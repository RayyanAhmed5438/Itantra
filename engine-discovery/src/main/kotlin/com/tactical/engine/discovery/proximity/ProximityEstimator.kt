package com.tactical.engine.discovery.proximity

interface ProximityEstimator {
    /**
     * Estimates distance in meters based on RSSI.
     */
    fun estimate(rssi: Int): Double
}
