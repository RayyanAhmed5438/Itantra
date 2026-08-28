package com.tactical.domain.identity

import java.time.Instant

/**
 * Immutable snapshot of a peer device for discovery and mesh status.
 * Produced by engine-discovery, consumed by the roster UI and mesh
 * quality monitors.
 */
data class DeviceNode(
    val id: DeviceId,
    val callsign: String,
    val rssi: Int,
    val lastSeen: Instant,
    val hopCount: Int,
    val link: LinkType,
    val battery: Int? = null
) {
    init {
        require(callsign.isNotBlank()) { "callsign must not be blank" }
        require(hopCount >= 0) { "hopCount cannot be negative" }
        require(battery == null || battery in 0..100) { "battery must be in 0..100" }
    }
}

/**
 * Connection health for a peer. Nullable on DeviceNode (rather than a
 * required 4th "unknown" case) so a freshly-discovered node with no
 * computed link quality yet can simply omit it, rather than engine-discovery
 * having to invent a placeholder value.
 *
 * ASSUMPTION FLAGGED: core.md specifies `link: LinkType?` but never lists
 * LinkType's actual values. These three are carried over from our original
 * LinkStatus design since they cover the states engine-discovery's beacon
 * TTL / RSSI trend logic needs. Confirm with whoever owns engine-discovery
 * before this is treated as final — the values themselves aren't confirmed
 * by core.md, only the nullable-enum shape is.
 */
enum class LinkType {
    DIRECT,
    RELAYED,
    STALE
}