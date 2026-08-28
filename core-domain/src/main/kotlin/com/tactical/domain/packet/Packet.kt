package com.tactical.domain.packet

/**
 * Marker sealed interface for every wire packet type — VoicePacket,
 * TextPacket, EmergencyPacket, BeaconPacket. No shared fields: each
 * subtype declares its own `sender`/`timestamp` independently, since they
 * don't need to be forced into a common shape here.
 *
 * No packetId field, unlike our original design. Mesh deduplication
 * (XxHashPacketHasher, in core-protocol) now hashes each packet's full
 * serialized content instead of relying on a stored unique ID — a
 * deliberate architecture change from the earlier version of this file.
 *
 * Still sealed, still gives every `when (packet)` across the codebase
 * (core-protocol's serializer, engine-mesh's router, UI rendering)
 * exhaustiveness checking — that part didn't change.
 */
sealed interface Packet


/**
 * One thing worth double-checking with whoever owns
 * core-protocol's XxHashPacketHasher, since it's
 * directly downstream of this change: content-hashing
 * a whole packet for dedup means the hash must be
 * computed identically regardless of how many times
 * a packet has been relayed (i.e., hashing shouldn't
 * accidentally include anything that changes per-hop,
 * like inside MeshRelayPacket). Since MeshRelayPacket.payload
 * is now a live Packet per the new spec, the hasher
 * needs to hash payload's content specifically, not
 * the outer relay envelope — worth confirming that's
 * the intended scope before it's built.
 */