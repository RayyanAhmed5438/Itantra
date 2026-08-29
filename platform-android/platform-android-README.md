# platform-android

The only module allowed to touch Android's SDK and hardware. Implements every interface defined in `core-platform-api`, plus the on-device STT/TTS inference backends. Nothing above this layer — `engine-*`, `feature-*`, `app` — is meant to reference a `platform-android` class directly by type; they depend on `core-platform-api`'s interfaces, and something (Hilt, wired in `app`) supplies a `platform-android` implementation underneath. Package root: `com.tactical.platform` (note: no `.android` — the module folder says android, the package doesn't).

## Module-level dependencies

**Depends on:** `core-domain`, `core-platform-api`, `core-protocol` (only `BlePayloadMapper` uses this directly — see its entry below), `kotlinx-coroutines-android`, Hilt (`hilt-android` + `hilt-compiler` via KSP), TensorFlow Lite (`tensorflow-lite`, `tensorflow-lite-support`), ONNX Runtime Android.

**Depended on by:** in principle, nothing above this module references it by type — only `app` should, and only to wire Hilt bindings. In practice, as built, only `speech/SpeechBackendModule.kt` actually does that wiring (for `SpeechToText`/`TextToSpeech`); see **Open items** below for what's still missing.

## `audio/`

- **`AudioRecordConfigMapper`** — maps `core-domain`'s `AudioConfig` to Android's `AudioFormat`/`AudioRecord` constants, and queries the platform's minimum buffer size. Pure utility, no state.
  - *Depends on:* `core-domain.AudioConfig`, `android.media.*`.
  - *Used by:* `AndroidAudioRecordRecorder` only.
- **`AndroidAudioRecordRecorder`** — implements `AudioRecorder`. Two independent capture modes: `start()` (full-fidelity, what actually gets transmitted) and `startMonitoring()` (cheap low-priority energy scan for VOX).
  - *Depends on:* `AudioRecordConfigMapper`, `core-domain.{AudioConfig, AudioFrame}`, `core-platform-api.audio.AudioRecorder`.
  - *Used by (via the interface):* `feature-ptt`'s PTT/VOX capture, feeding into `SpeechToText.transcribe()`.
- **`AndroidAudioTrackPlayer`** — implements `AudioPlayer`. One long-lived `AudioTrack`, assumes every `AudioFrame` it receives shares one global format (flagged in-code as unconfirmed end-to-end).
  - *Depends on:* `AlertToneGenerator`, `core-domain.{AudioConfig, AudioFrame}`, `core-platform-api.audio.{AudioPlayer, AlertTone}`.
  - *Used by (via the interface):* `feature-ptt` (incoming speech playback), `feature-emergency`'s `SystemSquelchBreaker` (alert tone + spoken alert).
- **`AlertToneGenerator`** — synthesizes alert tones as raw PCM from scratch (no model, no asset file), specifically so an alert can play even if the speech backend is broken.
  - *Depends on:* `core-domain.{AudioConfig, AudioFrame}`, `core-platform-api.audio.AlertTone`.
  - *Used by:* `AndroidAudioTrackPlayer` only.

## `radio/`

- **`BleFragmenter`** (object) **/ `BleFragmentReassembler`** (class, same file) — splits/reassembles packets across BLE's small MTU. Pure logic, no Android imports.
  - *Depends on:* nothing outside the JDK.
  - *Used by:* `BleRadioTransport` only.
- **`BleConnectionRegistry`** — tracks BLE connections in both roles (inbound/server, outbound/client), per-peer negotiated MTU, RSSI, and incoming-fragment listeners.
  - *Depends on:* `android.bluetooth.*`.
  - *Used by:* `BleRadioTransport` only.
- **`BleRadioTransport`** — implements `RadioTransport` over BLE GATT. Dual-role (this device is both a GATT server and maintains outbound GATT client connections) — a real design decision, not a documented requirement, flagged for `engine-mesh`'s owner to confirm.
  - *Depends on:* `BleFragmenter`, `BleConnectionRegistry`, `core-domain.result.TacticalResult`, `core-platform-api.radio.{RadioTransport, RawPacket}`, `android.bluetooth.*`.
  - *Used by:* `CompositeRadioTransport` only.
- **`WifiDirectRadioTransport`** — implements `RadioTransport` over raw TCP sockets on top of an already-formed Wi-Fi Direct group. Length-prefixed message framing. Independent of `AndroidWifiDirectManager` — the two coordinate only via Android's own `WIFI_P2P_CONNECTION_CHANGED_ACTION` broadcast, never by calling each other directly.
  - *Depends on:* `core-domain.result.TacticalResult`, `core-platform-api.radio.{RadioTransport, RawPacket}`, `android.net.wifi.p2p.*`, `java.net.*`.
  - *Used by:* `CompositeRadioTransport` only.
- **`CompositeRadioTransport`** — implements `RadioTransport` by multiplexing both bearers: merges both `incoming()` flows, broadcasts on both concurrently, succeeds if either bearer succeeds. **This is the actual `RadioTransport` implementation `engine-mesh` ends up talking to** (once wired via Hilt).
  - *Depends on:* `BleRadioTransport`, `WifiDirectRadioTransport` (both via the `RadioTransport` interface, constructor-injected), `core-domain.result.TacticalResult`.
  - *Used by:* `engine-mesh`'s `FloodMeshRouter` (via the `RadioTransport` interface, once Hilt binds this as the implementation).

## `ble/`

- **`AndroidBleAdvertiser`** — implements `BleBeaconAdvertiser`, non-connectable legacy advertising. `advertise()` takes raw bytes, no knowledge of `BeaconPacket`.
  - *Depends on:* `core-platform-api.ble.BleBeaconAdvertiser`, `android.bluetooth.le.*`.
  - *Used by (via the interface):* `engine-discovery`'s `PeriodicBeaconEmitter`.
- **`AndroidBleScanner`** — implements `BleBeaconScanner`. Currently scans with no `ScanFilter` (flagged — real but non-fatal battery cost, see Open items).
  - *Depends on:* `core-platform-api.ble.{BleBeaconScanner, ScannedBleDevice}`, `android.bluetooth.le.*`.
  - *Used by (via the interface):* `engine-discovery`.
- **`BlePayloadMapper`** — converts `BeaconPacket ↔ ByteArray` via `core-protocol`'s `PacketSerializer`. The only file in this module that imports `core-protocol` directly.
  - *Depends on:* `core-domain.packet.{BeaconPacket, Packet}`, `core-protocol.serialization.PacketSerializer`.
  - *Used by:* **currently nothing** — see Open items, this is a real gap.

## `wifi/`

- **`AndroidWifiDirectManager`** — implements `WifiDirectManager`. Peer discovery and connection *initiation* only; the resulting data path is `WifiDirectRadioTransport`'s job, coordinated only through Android's own broadcasts, not through a code dependency between the two classes.
  - *Depends on:* `core-domain.result.TacticalResult`, `core-platform-api.wifi.{WifiDirectManager, WifiDirectPeer}`, `android.net.wifi.p2p.*`.
  - *Used by (via the interface):* `engine-discovery`/`engine-mesh` for initiating connections to peers found via BLE.

## `haptics/`

- **`AndroidHapticEngine`** — implements `HapticEngine`. `VibratorManager` on API 31+, legacy `Vibrator` below that.
  - *Depends on:* `core-platform-api.haptics.{HapticEngine, HapticPattern}`, `android.os.*`.
  - *Used by (via the interface):* `feature-ptt` (recording feedback), `feature-emergency`, `feature-sentry`.

## `power/`

- **`AndroidWakeLockManager`** — implements `WakeLockManager`, one partial wake lock per tag, idempotent per tag (deliberately not using Android's own ref-counted wake lock stacking).
  - *Depends on:* `core-platform-api.power.WakeLockManager`, `android.os.PowerManager`.
  - *Used by (via the interface):* `engine-mesh` (always-on relay), `feature-sentry` (cold-wake listener).
- **`AndroidDozeModeHandler`** — implements `DozeModeHandler`, launches the ignore-battery-optimizations system dialog. No way to check the result — by design, matching the interface's own async nature.
  - *Depends on:* `core-platform-api.power.DozeModeHandler`, `android.os.PowerManager`, `android.provider.Settings`.
  - *Used by (via the interface):* `app`'s onboarding/settings screen, `feature-sentry`.

## `alarm/`

- **`AndroidAlarmBypass`** — implements `AlarmBypass`. Resolves the "what if two emergencies overlap" gap flagged in `core-platform-api` with a reference count: only the first `bypassDndAndMaxVolume()` call captures original state, only the matching `resetVolume()` call restores it.
  - *Depends on:* `core-platform-api.alarm.AlarmBypass`, `android.app.NotificationManager`, `android.media.AudioManager`.
  - *Used by (via the interface):* `feature-emergency`'s `SystemSquelchBreaker`.

## `flashlight/`

- **`CameraFlashlightController`** — implements `FlashlightController`. `strobe()` launches a background toggle loop and returns immediately; `off()` cancels it.
  - *Depends on:* `core-platform-api.flashlight.FlashlightController`, `android.hardware.camera2.*`.
  - *Used by (via the interface):* `feature-emergency`, `feature-sentry`.

## `permissions/`

- **`AndroidPermissionGateway`** — implements `PermissionGateway`. Not a pure DI singleton in practice — needs a hosting `Activity` to call `attachLauncher()`/`detachLauncher()` around its lifecycle, since an `ActivityResultLauncher` can only be registered by an Activity/Fragment. Fails closed (`false`) if `request()` is called with no launcher attached.
  - *Depends on:* `core-platform-api.permissions.{Permission, PermissionGateway}`, `androidx.activity.result.ActivityResultLauncher`.
  - *Used by (via the interface):* `app`'s onboarding flow, `feature-sentry` (before arming).

## `speech/`

- **`SpeechBackendModule`** — the one Hilt module meant to be edited to swap inference backends. `@Binds` `SpeechToText`/`TextToSpeech` to the TFLite implementations. Deliberately does not bind `ModelProvider`/`ModelDownloadManager` (backend-independent, belongs in a general module — see Open items).
- **`AssetModelProvider`** — implements `ModelProvider`. Memory-maps a model file already extracted to `filesDir/models/` — deliberately *not* reading straight from APK assets, since that requires an uncompressed-asset build config guarantee this class doesn't want to depend on. Only succeeds after `ModelDownloadManager.ensureModelAvailable()` has reached `Ready`.
  - *Depends on:* `core-domain.speech.ModelIdentifier`, `core-platform-api.speech.ModelProvider`.
- **`DynamicModelDownloadManager`** — implements `ModelDownloadManager`. Extracts bundled model assets to `filesDir/models/` on first run.
  - *Depends on:* `core-domain.speech.{ModelIdentifier, ModelStatus}`, `core-platform-api.speech.ModelDownloadManager`.
- **`speech/backend/tflite/`** (`TfliteSpeechToText`, `TfliteTextToSpeech`, `TfliteTensorMapper`) — the currently-bound backend. `TfliteSpeechToText.transcribe()` genuinely buffers audio, runs VAD-based silence detection, and invokes the model — but its final decode step (`decodeLogits()`) throws `NotImplementedError` deliberately, since fabricating transcribed text would be actively dangerous in this app rather than just low quality. `TfliteTextToSpeech` similarly has a placeholder tokenizer and an assumed (unconfirmed) language-conditioning tensor shape.
  - *Depends on:* `core-domain.{audio.AudioFrame, speech.{LanguageTag, ModelIdentifier}}`, `core-platform-api.speech.{ModelProvider, SpeechToText, TextToSpeech, TranscriptionChunk}`, `org.tensorflow.lite`.
- **`speech/backend/onnx/`** (`OnnxSpeechToText`, `OnnxTextToSpeech`, `OnnxTensorMapper`) — the alternate backend, same shape, not currently bound by `SpeechBackendModule`.
  - *Depends on:* same as the tflite backend, but `onnxruntime-android` instead of TFLite.
- *Used by (via the `SpeechToText`/`TextToSpeech` interfaces):* `engine-speech`'s pipeline, `feature-ptt` (transcribing outgoing speech, synthesizing incoming text).

## Open items — real gaps found during review, not yet resolved

1. **No general Hilt module for most `core-platform-api` interfaces.** `SpeechBackendModule` only works because `TfliteSpeechToText`/`TfliteTextToSpeech` have `@Inject`-annotated constructors with already-injectable dependencies. Every other implementation class here (`AndroidBleAdvertiser`, `AndroidWakeLockManager`, `AndroidAlarmBypass`, `CompositeRadioTransport`, etc.) takes a plain `Context`/framework object with no `@Inject constructor` — meaning nothing currently binds `BleBeaconAdvertiser → AndroidBleAdvertiser`, `RadioTransport → CompositeRadioTransport`, and so on. A `@Module` with `@Provides` methods for all of these needs to exist somewhere (this module or `app`) before Hilt can actually wire anything above `platform-android` to a real implementation.
2. **`BlePayloadMapper` has no caller anywhere in this module.** It exists (per `architecture.md`'s file tree) to convert `BeaconPacket ↔ ByteArray`, but neither `AndroidBleAdvertiser` nor `AndroidBleScanner` invokes it — both work in raw bytes only. Whatever is meant to call it (`engine-discovery`, decoding a `ScannedBleDevice.advertisementPayload` into a `BeaconPacket`) would have to depend on `platform-android` directly to reach a class in `com.tactical.platform.ble`, which breaks the "only `app` references `platform-android` by type" rule. Worth resolving: either `BlePayloadMapper` needs to be called from somewhere inside this module (e.g., `AndroidBleScanner` decoding before emitting), or it needs to move somewhere both `platform-android` and `engine-discovery` can legitimately depend on.
3. **The mesh-relay foreground `Service`** referenced in this module's own `AndroidManifest.xml` TODO doesn't exist in `platform-android`'s file list or (as far as reviewed) `engine-mesh`'s — open question for whoever owns either about where it actually lives.
4. **`AndroidBleScanner.scan()` has no `ScanFilter`** — picks up all nearby BLE traffic, not just this app's beacons. Real battery cost, not a correctness bug.
5. **Speech backend correctness is explicitly, deliberately incomplete** until the real model checkpoint is chosen: STT output decoding, TTS tokenization, and the TTS language-conditioning tensor shape are all placeholder/unimplemented, flagged clearly in-code rather than silently guessed.
6. **`AndroidBleAdvertiser`'s `MANUFACTURER_ID` (`0xFFFF`)** is the Bluetooth SIG's reserved testing value, not a real registered company ID — must be replaced before shipping.
7. **BLE GATT writes on Android 13+ (API 33)** should migrate to the newer `writeCharacteristic(characteristic, value, writeType)` overload to avoid a `.value`-mutation race the pre-33 API has; not addressed in `BleRadioTransport` yet.
