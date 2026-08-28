# core-domain

`core-domain` is the **pure Kotlin domain-model module** at the center of the Tactical Squad Communication App.

Its job is to define the shared vocabulary and data contracts used by the rest of the application: device identity, peer state, audio data, communication packets, mesh-relay envelopes, location fixes, speech/model metadata, domain events, and fallible-operation results.

It does **not** perform Android hardware access, networking, serialization, speech inference, routing, discovery, UI, or feature orchestration. Those responsibilities belong to higher-level modules.

Package root:

```text
com.tactical.domain
```

## Role in the architecture

`core-domain` is the lowest application module and has no dependency on any other project module. Every other module can depend on it directly or indirectly.

The architectural dependency direction is:

```text
core-domain
    ↑
core-platform-api / core-protocol
    ↑
platform-android / engines
    ↑
features
    ↑
app
```

`core-domain` is a plain Kotlin/JVM module. It has no Android dependency and no runtime third-party dependency. This keeps the domain model usable and testable without an Android device or emulator.

## What this module provides

The module currently provides eight logical groups of domain types:

```text
identity/   Device identity and peer snapshots
 audio/     PCM configuration and audio frames
 packet/    Communication packet models and relay envelope
location/   GPS/location data
 speech/    Language and model metadata/state
 events/    Application/domain events
 result/    Success/failure result type
```

These types are intentionally passive. They describe **what data means**, not **how the application performs an operation**.

---

## Design rules

### Pure domain layer

There is no Android API usage in this module. Android-specific implementations live in `platform-android`.

### Immutable contracts

The model is built from Kotlin `data class`, `value class`, `sealed interface`, and `enum class` types. State is represented as values rather than mutable services.

### Structural validation only

Constructors validate basic invariants such as blank strings, invalid ranges, and negative values. Policy decisions such as when a peer becomes stale, when a packet should be rebroadcast, or which emergency behavior to trigger do not belong here.

### Byte-array content equality

`AudioFrame` and `VoicePacket` contain `ByteArray`, so both implement explicit `equals()`/`hashCode()` using content equality. Kotlin's generated equality for a `ByteArray` property would otherwise compare the array reference instead of its contents.

---

# 1. Identity

## `DeviceId`

```kotlin
@JvmInline
value class DeviceId(val value: String)
```

A type-safe wrapper for the logical identity of a device in the squad.

### Validation

- `value` must not be blank.

### Why it exists

Using a dedicated value class prevents a device identifier from being accidentally mixed with unrelated strings such as callsigns, model identifiers, or language codes.

---

## `DeviceNode`

Represents the current snapshot of a discovered peer.

| Field | Type | Meaning |
|---|---|---|
| `id` | `DeviceId` | Logical identity of the peer |
| `callsign` | `String` | Human-readable peer callsign |
| `rssi` | `Int` | Received signal strength associated with the observation |
| `lastSeen` | `Instant` | Time at which the peer was last observed |
| `hopCount` | `Int` | Number of hops associated with the peer's current observation |
| `link` | `LinkType?`* | Current connection/link classification |
| `battery` | `Int?` | Optional battery percentage |

### Validation

- `callsign` must not be blank.
- `hopCount` cannot be negative.
- `battery`, when present, must be `0..100`.

### `LinkType`

The domain defines three link classifications:

```kotlin
DIRECT
RELAYED
STALE
```

- `DIRECT` — peer is directly reachable.
- `RELAYED` — peer information/path involves one or more intermediate hops.
- `STALE` — peer information is considered stale by the discovery layer.

> **Implementation note:** the current source still declares `link` as nullable (`LinkType? = null`). The revised architecture specifies `link: LinkType` and already defines `DIRECT`, `RELAYED`, and `STALE`; making the field non-null is the remaining alignment change recommended for the current code.

`core-domain` only defines these values. It does not decide when a peer becomes `STALE` or how RSSI is interpreted.

---

# 2. Audio

## `AudioConfig`

Describes the PCM format used by audio capture/playback and speech processing.

| Field | Type | Default | Meaning |
|---|---|---:|---|
| `sampleRate` | `Int` | `16000` | Samples per second |
| `channels` | `Int` | `1` | Number of audio channels |
| `bitDepth` | `Int` | `16` | Bits per sample |
| `chunkDurationMs` | `Long` | `20` | Duration represented by one audio chunk |

### Validation

- `sampleRate > 0`
- `channels` must be `1` or `2`.
- `bitDepth` must be `8`, `16`, `24`, or `32`.
- `chunkDurationMs > 0`.

### Derived property

```kotlin
val frameSizeInBytes: Int
```

Calculated from the format instead of stored independently:

```text
sampleRate × channels × (bitDepth / 8) × (chunkDurationMs / 1000)
```

This prevents the stored frame size from becoming inconsistent with the other configuration fields.

---

## `AudioFrame`

Represents one PCM audio chunk.

| Field | Type | Meaning |
|---|---|---|
| `data` | `ByteArray` | PCM audio bytes |
| `timestamp` | `Long` | Timestamp associated with the frame |
| `durationMs` | `Long` | Duration represented by the frame |

### Validation

- `data` must not be empty.
- `durationMs > 0`.

### Equality behavior

`equals()` and `hashCode()` compare the actual contents of `data`, not the array reference.

### Used by

`AudioFrame` is the domain representation passed between platform audio capture/playback and speech processing contracts.

---

# 3. Communication packets

## `Packet`

A sealed marker interface for every wire-level packet type.

Current implementations:

- `TextPacket`
- `VoicePacket`
- `EmergencyPacket`
- `BeaconPacket`

`Packet` intentionally has no shared fields. Individual packet types own their own `sender`, timestamps, and payload-specific data.

There is **no `packetId` field** in the current hierarchy. Packet identity/deduplication is handled outside the domain model by the protocol/mesh layers.

---

## `TextPacket`

Carries normal communication as text.

| Field | Type | Meaning |
|---|---|---|
| `sender` | `DeviceId` | Original sender |
| `text` | `String` | Transcribed message text |
| `languageCode` | `String` | Language of the sender's text |
| `timestamp` | `Long` | Message timestamp |

### Validation

- `text` must not be blank.
- `languageCode` must not be blank.

### Architecture behavior

Normal PTT/VOX communication is converted to text before transmission. Translation is not part of this model: the text remains in the speaker's original language and the receiving side uses the packet's language information for TTS.

---

## `VoicePacket`

Carries encoded audio data.

| Field | Type | Meaning |
|---|---|---|
| `sender` | `DeviceId` | Original sender |
| `audioData` | `ByteArray` | Encoded audio payload |
| `codec` | `AudioCodec` | Encoding used by `audioData` |
| `timestamp` | `Long` | Packet timestamp |

### Validation

- `audioData` must not be empty.

### `AudioCodec`

```kotlin
PCM_RAW
OPUS
```

- `PCM_RAW` — raw PCM representation.
- `OPUS` — compressed Opus representation.

The domain model records which codec is used; it does not perform encoding or decoding.

### Architecture usage

`VoicePacket` is reserved for the emergency raw-audio exception. Normal communication uses `TextPacket` to reduce mesh bandwidth usage.

### Equality behavior

`audioData` is compared by content, not array reference.

---

## `EmergencyPacket`

Represents a high-priority emergency alert.

| Field | Type | Meaning |
|---|---|---|
| `sender` | `DeviceId` | Device that triggered the alert |
| `severity` | `Severity` | Alert severity |
| `description` | `String` | Human-readable emergency description |
| `location` | `GeoFix?` | Optional location information |
| `languageCode` | `String` | Language in which the description was spoken/written |
| `timestamp` | `Long` | Alert timestamp |

### Validation

- `description` must not be blank.
- `languageCode` must not be blank.

### `Severity`

```kotlin
CAUTION
URGENT
CRITICAL
```

The packet records severity as domain data. Emergency handling, escalation, alert sounds, DND behavior, flashlight behavior, and display behavior are implemented by higher-level modules.

### Language behavior

`languageCode` is mandatory. It lets the receiving side pass the sender's language to TTS so an emergency description is spoken in the language encoded in the packet rather than assuming the receiver's default language.

---

## `BeaconPacket`

Represents a device-presence/heartbeat advertisement used by discovery.

| Field | Type | Meaning |
|---|---|---|
| `sender` | `DeviceId` | Device advertising its presence |
| `callsign` | `String` | Human-readable callsign |
| `listenPort` | `Int?` | Optional advertised connect-back/listening port |
| `timestamp` | `Long` | Beacon timestamp |

### Validation

- `callsign` must not be blank.
- `listenPort`, when present, must be in `1..65535`.

The domain defines the data carried by the beacon. It does not perform BLE/Wi-Fi discovery or decide how often beacons are sent.

---

## `MeshRelayPacket`

A **mesh-routing envelope**, not a `Packet` subtype.

| Field | Type | Meaning |
|---|---|---|
| `originalSender` | `DeviceId` | Device that originally created the payload |
| `immediateSender` | `DeviceId` | Device that most recently forwarded it |
| `ttl` | `Int` | Remaining relay lifetime |
| `hopCount` | `Int` | Number of forwarding hops recorded so far |
| `payload` | `Packet` | Actual domain packet being relayed |

### Validation

- `ttl >= 0`
- `hopCount >= 0`

### Important boundary

`MeshRelayPacket` stores a live `Packet` object. It does not serialize itself and it does not perform routing decisions.

Serialization/deserialization belongs to `core-protocol`; TTL decrementing, deduplication, and rebroadcast decisions belong to `engine-mesh`.

---

# 4. Location

## `GeoFix`

Represents a geographic position that can be attached to an emergency alert.

| Field | Type | Meaning |
|---|---|---|
| `latitude` | `Double` | Latitude in degrees |
| `longitude` | `Double` | Longitude in degrees |
| `altitude` | `Double?` | Optional altitude |
| `accuracyMeters` | `Float?` | Optional horizontal/position accuracy |
| `timestamp` | `Long` | Time associated with the fix |

### Validation

- `latitude` must be in `-90.0..90.0`.
- `longitude` must be in `-180.0..180.0`.
- `accuracyMeters`, when present, cannot be negative.

Location is optional on `EmergencyPacket`; an emergency can still be represented when a usable location fix is unavailable.

---

# 5. Speech and model metadata

The domain layer does not run STT/TTS models. It only defines the shared metadata and state needed by the speech/model APIs.

## `ModelIdentifier`

A value class wrapping the logical identifier of a model asset.

Examples used by the architecture include:

```text
stt_multilingual
tts_multilingual
```

The value is a logical name, not a file-system path. Platform-specific code resolves the actual model asset.

### Validation

- `value` must not be blank.

The type itself does not enforce whether an identifier is for STT or TTS; that distinction is currently conventional rather than represented by separate Kotlin types.

---

## `ModelStatus`

Represents the current availability/loading state of a model.

### `Ready`

The model is available and ready to use.

### `Loading(progress)`

The model is currently being prepared/loaded.

```kotlin
progress: Float
```

Validation:

```text
0.0 <= progress <= 1.0
```

### `Missing`

The model is not currently available.

### `Failed(reason)`

The model operation failed.

```kotlin
reason: String
```

Validation:

- `reason` must not be blank.

This distinct state allows callers to distinguish a genuinely unavailable model from one that failed during extraction/loading/checksum verification or another preparation step.

---

## `LanguageTag`

Represents the language identity needed by the speech backend.

| Field | Type | Meaning |
|---|---|---|
| `isoCode` | `String` | Language code used at the domain/API boundary |
| `backendId` | `String` | Backend-specific identifier used by the active speech implementation |

### Validation

- `isoCode` must not be blank.
- `backendId` must not be blank.

This keeps model/backend-specific language identifiers out of higher-level feature code.

`TextPacket` intentionally carries the simpler wire-level `languageCode: String`; `LanguageTag` is the richer type used at the TTS API boundary.

---

# 6. Domain events

## `DomainEvent`

A sealed marker interface for application/domain events.

Current event variants:

### `PacketReceived`

```kotlin
PacketReceived(packet: Packet)
```

Emitted when a packet has been received and delivered to the domain/event layer.

### `PeerLost`

```kotlin
PeerLost(deviceId: DeviceId)
```

Identifies a previously known peer that is no longer considered present by the discovery layer.

### `DeviceDiscovered`

```kotlin
DeviceDiscovered(node: DeviceNode)
```

Carries the peer snapshot created/refreshed when discovery finds a device.

### `EmergencyTriggered`

```kotlin
EmergencyTriggered(packet: EmergencyPacket)
```

Carries the emergency packet associated with an emergency event so subscribers have the alert data they need to react.

### What this layer does not do

`core-domain` only defines the event types. It does not publish events, maintain an event bus, or decide when an event should be emitted.

---

# 7. `TacticalResult`

## Purpose

`TacticalResult<T>` is the common success/failure contract for operations that may fail across module boundaries.

```kotlin
sealed interface TacticalResult<out T>
```

### `Success`

```kotlin
Success(value: T)
```

Carries the operation's successful result.

### `Failure`

```kotlin
Failure(error: String)
```

Carries a human-readable failure description.

### Helper functions

#### `getOrNull()`

Returns the success value when the result is `Success`; otherwise returns `null`.

#### `map(transform)`

Transforms the value inside `Success` while leaving `Failure` unchanged.

Example:

```kotlin
val result: TacticalResult<Int> = TacticalResult.Success(5)
val doubled = result.map { it * 2 }
```

The type is used by fallible platform/engine contracts such as mesh transmission and Wi-Fi Direct connection.

`core-domain` does not execute retries, log failures, display errors, or decide recovery behavior.

---

# 8. How the domain types fit into the communication flow

A normal PTT/VOX message is represented across the layers approximately as follows:

```text
Microphone
   ↓
AudioFrame
   ↓
SpeechToText
   ↓
Text + language code
   ↓
TextPacket
   ↓
MeshRelayPacket
   ↓
serialized by core-protocol
   ↓
transport / mesh
   ↓
MeshRelayPacket
   ↓
PacketReceived
   ↓
TextPacket
   ↓
TextToSpeech + LanguageTag
   ↓
AudioFrame
   ↓
Speaker
```

An emergency follows the same domain-model pattern but can additionally carry location and uses the emergency packet's language information for receiver-side TTS:

```text
Emergency trigger
   ↓
EmergencyPacket
   ├── severity
   ├── description
   ├── languageCode
   └── optional GeoFix
   ↓
MeshRelayPacket
   ↓
mesh transport
   ↓
PacketReceived / EmergencyTriggered
   ↓
feature-emergency
   ↓
TTS + alert handling
```

Presence/discovery uses:

```text
BeaconPacket
   ↓
engine-discovery
   ↓
DeviceNode
   ↓
DeviceDiscovered / PeerLost
   ↓
roster/UI
```

These flows describe how the domain types are consumed; the domain module itself does not implement any of these pipelines.

---

# 9. What `core-domain` intentionally does NOT provide

This module does **not** contain:

- Android APIs or Android hardware implementations.
- BLE scanning/advertising.
- Wi-Fi Direct discovery or connections.
- AudioRecord/AudioTrack or actual audio playback/capture.
- STT/TTS inference engines or model binaries.
- Model downloading/extraction.
- Packet serialization/deserialization or CRC calculation.
- Mesh routing, flooding, deduplication, or TTL decrementing.
- RSSI-to-distance calculations.
- Peer eviction/staleness policies.
- Emergency sirens, flashlight control, DND bypass, or volume control.
- UI state, Compose code, or user interaction logic.
- Retries, logging, or operational error recovery.

Those concerns belong to the platform API, protocol, Android implementation, engine, feature, and application layers.

---

# 10. Module contents at a glance

```text
com.tactical.domain
├── identity/
│   ├── DeviceId.kt
│   └── DeviceNode.kt
│
├── audio/
│   ├── AudioConfig.kt
│   └── AudioFrame.kt
│
├── packet/
│   ├── Packet.kt
│   ├── TextPacket.kt
│   ├── VoicePacket.kt
│   ├── EmergencyPacket.kt
│   ├── BeaconPacket.kt
│   └── MeshRelayPacket.kt
│
├── location/
│   └── GeoFix.kt
│
├── speech/
│   ├── ModelIdentifier.kt
│   ├── ModelStatus.kt
│   └── LanguageTag.kt
│
├── events/
│   ├── DomainEvent.kt
│   ├── PacketReceived.kt
│   ├── PeerLost.kt
│   ├── DeviceDiscovered.kt
│   └── EmergencyTriggered.kt
│
└── result/
    └── TacticalResult.kt
```

## Summary

`core-domain` is the application's **shared, platform-independent vocabulary**. If another module needs to agree on what a device, audio frame, packet, emergency, model state, event, or operation result means, that contract belongs here.

The key rule is simple:

> **`core-domain` defines the data and state; higher layers define the behavior.**
