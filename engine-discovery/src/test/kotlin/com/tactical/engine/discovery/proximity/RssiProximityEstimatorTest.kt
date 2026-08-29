package com.tactical.engine.discovery.proximity

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

class RssiProximityEstimatorTest {

    @Test
    fun `should estimate 1 meter at txPower`() {
        val estimator = RssiProximityEstimator(txPowerAtOneMeter = -60)
        val distance = estimator.estimate(-60)
        assertTrue(abs(distance - 1.0) < 0.1)
    }

    @Test
    fun `distance should increase as RSSI decreases`() {
        val estimator = RssiProximityEstimator(txPowerAtOneMeter = -60)
        val d1 = estimator.estimate(-60)
        val d2 = estimator.estimate(-70)
        val d3 = estimator.estimate(-80)
        
        assertTrue(d2 > d1)
        assertTrue(d3 > d2)
    }
}
