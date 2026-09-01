package com.tactical.emergency.receiver

import com.tactical.domain.packet.EmergencyPacket
import kotlinx.coroutines.flow.Flow

interface EmergencyReceiver {
    fun incoming(): Flow<EmergencyPacket>
}