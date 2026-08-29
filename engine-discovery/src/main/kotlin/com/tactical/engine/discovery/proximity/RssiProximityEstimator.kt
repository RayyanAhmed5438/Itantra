package com.tactical.engine.discovery.proximity

import kotlin.math.pow

/**
 * Log-distance path-loss model for distance estimation.
 */
class RssiProximityEstimator(
    private val txPowerAtOneMeter: Int = -59, // Measured RSSI at 1 meter
    private val pathLossExponent: Double = 2.0 // 2.0 for free space, 3.0-4.0 for indoor
) : ProximityEstimator {

    override fun estimate(rssi: Int): Double {
        if (rssi == 0) return -1.0 // Unknown
        
        // d = 10 ^ ((txPower - rssi) / (10 * n))
        val ratio = (txPowerAtOneMeter - rssi) / (10.0 * pathLossExponent)
        return 10.0.pow(ratio)
    }
}
