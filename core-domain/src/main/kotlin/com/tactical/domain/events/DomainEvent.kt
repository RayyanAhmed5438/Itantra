package com.tactical.domain.events

/**
 * Marker sealed interface for the app-wide event vocabulary, published on
 * a shared event bus so engines/features stay decoupled from each other.
 * No common fields — same "marker only" shape as Packet.kt, since core.md
 * doesn't specify a shared field (like timestamp) across all event types
 * here, unlike our earlier version which required one.
 */
sealed interface DomainEvent