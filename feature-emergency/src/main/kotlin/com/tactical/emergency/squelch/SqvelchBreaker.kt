package com.tactical.emergency.squelch

import com.tactical.domain.packet.EmergencyPacket

interface SquelchBreaker {
    suspend fun breakSquelch(packet: EmergencyPacket)
    suspend fun dismiss()
}
