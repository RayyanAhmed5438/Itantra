# core-platform-api

Pure Kotlin/JVM API layer that defines the contracts between the app's domain/engine/feature modules and the platform implementation.

This module answers **what platform capabilities are available** — recording and playing audio, BLE advertising/scanning, radio I/O, Wi-Fi Direct discovery/connection, haptics, power management, emergency alert controls, permissions, and on-device speech/model access. It does **not** contain Android implementations or business logic.

Package root: `com.tactical.platform.api`.

## Role in the architecture

The module sits directly above `core-domain` and directly below the Android/platform implementation:

```text
core-domain
    ↑
core-platform-api   ← this module
    ↑
platform-android
    ↑
engines / features / app
```

Per the current architecture:

- `core-platform-api` depends on `core-domain`.
- It is a plain Kotlin/JVM module, not an Android library.
- No `android.*` APIs are exposed here.
- Platform-specific SDKs such as Android Bluetooth, Wi-Fi Direct, `AudioRecord`, `AudioTrack`, TFLite, or ONNX belong in `platform-android`.
- Engines and feature modules depend on these interfaces rather than on concrete Android classes.

The revised architecture explicitly defines `core-platform-api` as the hardware/model contract layer and keeps Android implementations behind `platform-android`. fileciteturn0file1L66-L70

## What this module provides

### `audio/`

#### `AudioRecorder`

Microphone capture and low-power VOX monitoring.

```kotlin
fun start(config: AudioConfig): Flow<AudioFrame>
fun stop()
fun startMonitoring(config: AudioConfig, thresholdDb: Double): Flow<Unit>
```

**`start(config)`**
- Starts microphone capture using the supplied `AudioConfig`.
- Emits `AudioFrame` objects through a `Flow`.
- The current implementation treats repeated `start()` calls without an intervening `stop()` as illegal runtime usage.
- The recorder implementation is responsible for acquiring/releasing the actual microphone resource.

**`stop()`**
- Ends an active recording session.
- Releases the underlying recording resource.
- Safe to call when nothing is currently recording.

**`startMonitoring(config, thresholdDb)`**
- Provides the low-power audio monitoring contract used by VOX mode.
- The purpose is energy detection without running the full STT pipeline continuously.
- The flow emits `Unit` when the configured audio energy threshold is crossed.
- `thresholdDb` is supplied by the caller rather than hard-coded into the API.

This contract exists specifically to support the revised VOX design: monitor cheaply first, then start full recording/STT only after speech is detected. fileciteturn0file1L164-L165

#### `AudioPlayer`

Playback of synthesized audio and predefined alert tones.

```kotlin
suspend fun play(frame: AudioFrame)
suspend fun playAlert(tone: AlertTone)
suspend fun stopPlayback()
```

**`play(frame)`**
- Plays an `AudioFrame` through the device audio output.
- Used for normal TTS playback.

**`playAlert(tone)`**
- Plays a predefined alert tone without requiring the caller to create an `AudioFrame`.

**`stopPlayback()`**
- Immediately interrupts current playback.
- Exists so an incoming emergency can cut off routine TTS/audio before the emergency alert is announced.

The revised architecture explicitly added `stopPlayback()` for emergency interruption. fileciteturn0file1L165-L165

#### `AlertTone`

Current predefined alert identifiers:

```text
EMERGENCY_INCOMING
MESSAGE_RECEIVED
```

These are platform-facing audio identifiers rather than domain packet types.

---

### `radio/`

#### `RawPacket`

The bearer-agnostic physical-layer representation of received data.

```kotlin
val data: ByteArray
val rssi: Int
val timestamp: Long
```

Validation:

- `data` must not be empty.

The type deliberately does not know what is inside `data`; `core-protocol` is responsible for interpreting serialized packet bytes.

`rssi` preserves signal strength associated with the received packet, while `timestamp` records when it was received.

#### `RadioTransport`

Generic send/receive contract used by `engine-mesh` without knowing whether the active bearer is BLE or Wi-Fi Direct.

```kotlin
fun incoming(): Flow<RawPacket>
suspend fun broadcast(raw: RawPacket): TacticalResult<Unit>
```

**`incoming()`**
- Emits received raw packets.
- Carries both bytes and physical metadata through `RawPacket`.

**`broadcast(raw)`**
- Sends a raw packet to the connected/available radio peers.
- Returns `TacticalResult<Unit>` rather than being fire-and-forget.
- `Success(Unit)` means the transport accepted the broadcast operation.
- `Failure(error)` surfaces a send failure to the caller so higher layers can provide user feedback or decide whether to retry.

This result-based contract is one of the explicit reliability fixes in the current architecture. fileciteturn0file1L193-L197

---

### `ble/`

#### `ScannedBleDevice`

One raw BLE scan result.

```kotlin
val deviceId: String
val rssi: Int
val advertisementPayload: ByteArray?
```

Validation:

- `deviceId` must not be blank.

Important identity distinction:

- `deviceId` here represents the BLE scan-level identifier.
- It is **not** automatically a `core-domain.DeviceId`.
- `engine-discovery` decodes the advertisement payload and resolves the logical domain identity from the `BeaconPacket` when available.

The revised architecture explicitly requires logical `DeviceId` resolution from beacon data so that the same peer is not duplicated when it is visible through multiple transports. fileciteturn0file0L55-L61

#### `BleBeaconAdvertiser`

Advertises this device's presence beacon.

```kotlin
suspend fun advertise(payload: ByteArray)
suspend fun stopAdvertising()
```

**`advertise(payload)`**
- Starts or replaces BLE advertising with the supplied beacon bytes.

**`stopAdvertising()`**
- Stops the active advertisement.
- Safe to call when nothing is being advertised.

#### `BleBeaconScanner`

Discovers nearby BLE advertisements.

```kotlin
fun scan(): Flow<ScannedBleDevice>
```

The flow represents the stream of observed advertisements. Deduplication and peer-catalog management are not responsibilities of this API; those belong to the discovery engine.

---

### `wifi/`

#### `WifiDirectManager`

Wi-Fi Direct discovery and peer connection control.

```kotlin
suspend fun discoverPeers(): Flow<List<WifiDirectPeer>>
suspend fun connect(deviceId: String): TacticalResult<Unit>
```

**`discoverPeers()`**
- Starts/initializes Wi-Fi Direct discovery and exposes peer-list updates as a `Flow`.
- The emitted value is a complete current peer list rather than a single peer event.

**`connect(deviceId)`**
- Requests a connection to the specified peer.
- Returns `TacticalResult<Unit>` so connection failures can be propagated explicitly.
- A successful connection is represented by `Success(Unit)`.
- Failure is represented by `Failure(error)`.

The revised architecture explicitly changed `connect()` to return `TacticalResult<Unit>`. fileciteturn0file1L168-L168

#### `WifiDirectPeer`

Current peer representation:

```kotlin
val deviceAddress: String
val deviceName: String
```

Validation:

- `deviceAddress` must not be blank.

These fields are the API's current representation of a discovered Wi-Fi Direct peer; the higher layers use the peer information to decide which device to connect to.

---

### `haptics/`

#### `HapticPattern`

Parametric vibration waveform:

```kotlin
val timings: LongArray
val amplitudes: IntArray
```

Validation:

- `timings` must not be empty.
- `timings` and `amplitudes` must have the same length.
- Every amplitude must be within `0..255`.

The API intentionally exposes a waveform rather than a fixed semantic enum. This lets callers define the exact vibration sequence while keeping Android-specific `VibrationEffect` creation inside `platform-android`.

#### `HapticEngine`

Executes a vibration pattern.

```kotlin
suspend fun perform(pattern: HapticPattern)
```

The interface does not define a cancellation method. A submitted pattern is expected to execute as a finite vibration operation.

---

### `power/`

#### `WakeLockManager`

Controls partial CPU wake locks for work that must continue while the device is otherwise allowed to sleep.

```kotlin
suspend fun acquire(tag: String)
suspend fun release(tag: String)
```

Current contract:

- Operations are keyed by `tag`.
- `acquire(tag)` is idempotent for an already-held tag.
- `release(tag)` is safe when that tag is not currently held.

#### `DozeModeHandler`

Requests exemption from Android battery optimization/Doze restrictions.

```kotlin
suspend fun requestIgnoreBatteryOptimizations()
```

This API only requests the system flow. It does not return a Boolean indicating whether the user ultimately granted the exemption.

---

### `alarm/`

#### `AlarmBypass`

Temporary emergency audio override.

```kotlin
suspend fun bypassDndAndMaxVolume()
suspend fun resetVolume()
```

**`bypassDndAndMaxVolume()`**
- Applies the platform-level emergency audio override.
- Ensures emergency audio can be heard despite normal volume/DND state.

**`resetVolume()`**
- Restores the state saved before the bypass.

The timeout/lifecycle policy is owned by `feature-emergency`; the API only exposes the override and restoration operations. The current architecture specifies a 30-second automatic reset after the last emergency alert, plus manual dismissal. fileciteturn0file1L209-L213

---

### `flashlight/`

#### `FlashlightController`

Controls the device camera flashlight for emergency signaling.

```kotlin
suspend fun strobe(intervalMs: Long)
suspend fun off()
```

**`strobe(intervalMs)`**
- Repeatedly toggles the flashlight using the requested interval.
- Intended to continue until `off()` is called.

**`off()`**
- Turns the flashlight off.
- Stops an active strobe.

The API does not define a named set of strobe patterns; callers supply the interval directly.

---

### `permissions/`

#### `Permission`

Logical, platform-independent permission identifiers:

```text
RECORD_AUDIO
LOCATION
BLUETOOTH_SCAN
BLUETOOTH_ADVERTISE
BLUETOOTH_CONNECT
NEARBY_WIFI_DEVICES
CAMERA
NOTIFICATIONS
```

The enum exists so higher layers never need to depend on Android permission-string constants.

#### `PermissionGateway`

```kotlin
suspend fun request(permission: Permission): Boolean
```

The implementation maps the logical permission to the appropriate platform behavior.

Return value:

- `true` — permission was granted.
- `false` — permission was not granted.

API-level-specific Android permission mapping belongs entirely in `platform-android`.

---

### `speech/`

This group exposes the contracts for fully local STT/TTS and model access. The architecture requires offline inference and uses shared multilingual models rather than a separate model file for every language. fileciteturn0file1L120-L127

#### `SpeechToText`

```kotlin
fun transcribe(audio: Flow<AudioFrame>): Flow<TranscriptionChunk>
```

The current STT contract emits a stream of `TranscriptionChunk` values rather than only final strings.

`TranscriptionChunk` contains:

```kotlin
val text: String
val isFinal: Boolean
val languageCode: String
```

Semantics:

- `isFinal = false` — partial transcription intended for live UI feedback.
- `isFinal = true` — finalized text after the pause/sentence boundary used by the speech pipeline.
- `languageCode` identifies the language detected for that transcription.
- No translation is performed by this API.

The feature layer is expected to display partial chunks but transmit only finalized text over the mesh. fileciteturn0file0L79-L91

#### `TextToSpeech`

```kotlin
suspend fun synthesize(text: String, langTag: LanguageTag): AudioFrame
```

Responsibilities:

- Convert text to speech locally.
- Use the supplied `LanguageTag` to select the requested language/voice at inference time.
- Return the synthesized result as an `AudioFrame`.
- Remain fully offline.

The receiving side passes the sender's packet language to TTS; there is no translation stage. fileciteturn0file0L125-L134

#### `ModelProvider`

Provides access to the locally available model bytes:

```kotlin
suspend fun getModelBuffer(id: ModelIdentifier): ByteBuffer
```

The provider deals in raw model data and remains format-agnostic. Actual model loading/inference logic belongs in `platform-android` and its speech backend.

#### `ModelDownloadManager`

Ensures a model is available locally:

```kotlin
fun ensureModelAvailable(id: ModelIdentifier): Flow<ModelStatus>
```

This is an **offline model provisioning** contract. It is not a network download API. In the current design, model assets are bundled locally and the implementation may copy/extract/verify them before reporting readiness.

Possible `ModelStatus` values are supplied by `core-domain`:

```text
Ready
Loading(progress)
Missing
Failed(reason)
```

The current architecture explicitly includes `Failed` for extraction/checksum or other model setup failures. fileciteturn0file1L153-L156

---

## Cross-cutting design rules

### Platform abstraction

Nothing in this module imports Android classes. Every Android-specific operation is expressed as an interface contract and implemented behind that boundary.

### Domain types come from `core-domain`

Examples include:

- `AudioConfig`
- `AudioFrame`
- `DeviceId`
- `LanguageTag`
- `ModelIdentifier`
- `ModelStatus`
- `TacticalResult`

This module defines platform-facing types only where the platform boundary itself needs a representation, such as `RawPacket`, `ScannedBleDevice`, `WifiDirectPeer`, `HapticPattern`, and `Permission`.

### Flows represent ongoing streams

`Flow` is used where the platform produces a sequence of values over time:

- microphone frames,
- VOX energy detections,
- incoming radio packets,
- BLE scan results,
- Wi-Fi Direct peer-list updates,
- STT transcription chunks,
- model provisioning status.

Operations that perform a discrete asynchronous action are generally `suspend` functions.

### Explicit failures where callers need them

Transmission and Wi-Fi Direct connection now return `TacticalResult<Unit>` so higher layers can distinguish success from failure without relying only on exceptions. fileciteturn0file1L193-L197

### No business policy

This module does not decide:

- when a user enters PTT/VOX,
- when a message should be sent,
- routing/TTL behavior,
- deduplication,
- emergency severity,
- when an alert expires,
- how a roster is maintained,
- which packets are accepted or rebroadcast.

Those responsibilities belong to the domain/engine/feature layers defined by the architecture.

## What is intentionally NOT here

- **Android implementations** — `platform-android`.
- **Packet serialization/CRC/wire format** — `core-protocol`.
- **Mesh routing, flooding, TTL, and deduplication** — `engine-mesh`.
- **BLE/Wi-Fi peer merging and roster management** — `engine-discovery`.
- **STT/TTS inference implementations** — platform speech backend.
- **PTT/VOX state machines** — `feature-ptt`.
- **Emergency orchestration, timing, and alert lifecycle** — `feature-emergency`.
- **UI and dependency injection wiring** — `app`.

The revised architecture keeps `core-platform-api` as the contract boundary and makes `platform-android` the implementation module. fileciteturn0file1L182-L196

## Current API surface at a glance

| Package | Type | Purpose |
|---|---|---|
| `audio` | `AudioRecorder` | Full recording + low-power VOX monitoring |
| `audio` | `AudioPlayer` | Audio playback + alert playback + interruption |
| `audio` | `AlertTone` | Predefined alert identifiers |
| `radio` | `RawPacket` | Raw received bytes + RSSI + timestamp |
| `radio` | `RadioTransport` | Bearer-agnostic receive/broadcast |
| `ble` | `ScannedBleDevice` | Raw BLE scan result |
| `ble` | `BleBeaconAdvertiser` | Advertise/stop local beacon |
| `ble` | `BleBeaconScanner` | Stream BLE scan results |
| `wifi` | `WifiDirectManager` | Peer discovery + connection |
| `wifi` | `WifiDirectPeer` | Wi-Fi Direct peer data |
| `haptics` | `HapticPattern` | Validated vibration waveform |
| `haptics` | `HapticEngine` | Execute vibration waveform |
| `power` | `WakeLockManager` | Acquire/release CPU wake lock |
| `power` | `DozeModeHandler` | Request battery-optimization exemption |
| `alarm` | `AlarmBypass` | Emergency DND/volume override |
| `flashlight` | `FlashlightController` | Emergency flashlight strobe/off |
| `permissions` | `Permission` | Platform-independent permission IDs |
| `permissions` | `PermissionGateway` | Request one logical permission |
| `speech` | `SpeechToText` | Streaming local STT |
| `speech` | `TranscriptionChunk` | Partial/final STT result + language |
| `speech` | `TextToSpeech` | Local multilingual TTS |
| `speech` | `ModelProvider` | Access local model bytes |
| `speech` | `ModelDownloadManager` | Ensure a model is locally ready |

## Build configuration

This module is a plain Kotlin/JVM library and depends on `core-domain` plus Kotlin coroutines for its `Flow`/suspending APIs.

```kotlin
plugins {
    kotlin("jvm")
}

dependencies {
    implementation(project(":core-domain"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    testImplementation(kotlin("test"))
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
}

kotlin {
    jvmToolchain(17)
}
```

The architecture requires core/engine/feature modules to remain plain Kotlin/JVM, with Android-specific implementation restricted to `platform-android` and `app`. fileciteturn0file1L287-L291
