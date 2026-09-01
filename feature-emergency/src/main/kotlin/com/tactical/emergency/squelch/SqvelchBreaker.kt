package com.tactical.feature.emergency.squelch

import com.tactical.domain.packet.EmergencyPacket

interface SquelchBreaker {
    suspend fun breakSquelch(packet: EmergencyPacket)
    suspend fun dismiss()
}