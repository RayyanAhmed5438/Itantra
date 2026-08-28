package com.tactical.domain.events

import com.tactical.domain.packet.EmergencyPacket

/**
 * ASSUMPTION FLAGGED: core.md lists "EmergencyTriggered" as a variant name
 * only, with no fields specified. Kept carrying the EmergencyPacket itself
 * (as our original version did) since a fieldless EmergencyTriggered
 * couldn't tell a subscriber (SystemSquelchBreaker) anything about the
 * alert it needs to act on — confirm this is the intended shape rather
 * than something even more minimal.
 */
data class EmergencyTriggered(val packet: EmergencyPacket) : DomainEvent