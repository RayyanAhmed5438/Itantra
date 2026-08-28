package com.tactical.domain.events

import com.tactical.domain.identity.DeviceNode

/**
 * ADDITION BEYOND SPEC: core.md's "common variants" list only names
 * PacketReceived, PeerLost, and EmergencyTriggered — it doesn't mention
 * the discovery-side counterpart to PeerLost. Kept from our original
 * design since engine-discovery needs some way to announce a newly-seen
 * (or meaningfully-changed) peer, not just report losing one. Flag with
 * whoever wrote core.md — likely an omission given "common variants"
 * phrasing suggests the list isn't exhaustive, but worth confirming this
 * exact name/shape rather than assuming.
 */
data class DeviceDiscovered(val node: DeviceNode) : DomainEvent