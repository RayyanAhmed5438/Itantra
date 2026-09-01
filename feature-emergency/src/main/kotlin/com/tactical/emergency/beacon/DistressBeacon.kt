package com.tactical.emergency.beacon

interface DistressBeacon {
    suspend fun start()
    suspend fun stop()
}