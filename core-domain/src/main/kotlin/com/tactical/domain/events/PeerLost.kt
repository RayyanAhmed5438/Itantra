package com.tactical.domain.events

import com.tactical.domain.identity.DeviceId

/**
 * A previously known peer dropped out (renamed from DeviceLost per
 * core.md). Carries only the DeviceId — nothing fresh to snapshot, just
 * an identity to remove from the roster.
 */
data class PeerLost(val deviceId: DeviceId) : DomainEvent