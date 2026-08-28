package com.tactical.domain.identity

/**
 * Uniquely identifies a peer in the squad mesh. Wrapped in an inline value
 * class so it can't be accidentally swapped with any other raw String
 * (callsign, model name, etc.) at compile time, with zero runtime cost.
 */
@JvmInline
value class DeviceId(val value: String) {
    init {
        require(value.isNotBlank()) { "DeviceId must not be blank" }
    }

    override fun toString(): String = value
}