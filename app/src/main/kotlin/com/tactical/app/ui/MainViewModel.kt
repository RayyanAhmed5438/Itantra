package com.tactical.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tactical.app.di.DeviceIdentityStore
import com.tactical.app.di.LocalAppDataStore
import com.tactical.app.di.StoredPairedDevice
import com.tactical.app.di.StoredReceivedMessage
import com.tactical.domain.identity.DeviceId
import com.tactical.domain.identity.LinkType
import com.tactical.domain.packet.EmergencyPacket
import com.tactical.domain.packet.TextPacket
import com.tactical.domain.packet.Severity
import com.tactical.domain.result.TacticalResult
import com.tactical.domain.audio.AudioConfig
import com.tactical.platform.api.audio.AudioRecorder
import com.tactical.platform.api.haptics.HapticEngine
import com.tactical.platform.api.speech.SpeechToText
import com.tactical.platform.speech.mms.MmsTtsEngine
import com.tactical.platform.speech.mms.MmsTtsLanguage
import com.tactical.platform.speech.mms.MmsTtsModelStore
import com.tactical.platform.speech.SpeechLanguagePreferences
import com.tactical.ptt.controller.DefaultPttController
import com.tactical.ptt.controller.PttController
import com.tactical.ptt.feedback.PatternedHapticFeedback
import com.tactical.ptt.relay.PttMeshDispatcher
import com.tactical.ptt.relay.PttPacketBuilder
import com.tactical.ptt.session.SessionState
import com.tactical.engine.discovery.proximity.RssiProximityEstimator
import com.tactical.engine.discovery.service.DefaultDiscoveryService
import com.tactical.engine.discovery.service.DiscoveryService
import com.tactical.engine.mesh.service.MeshService
import com.tactical.platform.api.ble.BleConnectionManager
import com.tactical.platform.api.ble.BleLinkState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.text.SimpleDateFormat
import java.util.Date
import javax.inject.Inject

data class PeerNodeUi(
    val deviceAddress: String,
    val callsign: String,
    val isConnected: Boolean,
    val distanceText: String,
    val signalBars: Int,
    val linkText: String,
    val bleState: BleLinkState = BleLinkState.NOT_PAIRED
)

data class ChatMessageUi(
    val sender: String,
    val text: String,
    val timestampText: String,
    val statusText: String,
    val isAlert: Boolean = false,
    val isVoice: Boolean = false,
    val emergencyData: EmergencyAlertData? = null,
    val timestampEpochMs: Long = 0L
)

data class EmergencyAlertData(
    val sender: String,
    val timestampText: String,
    val severity: String,
    val message: String,
    val languageCode: String,
    val locationLatitude: Double? = null,
    val locationLongitude: Double? = null,
    val locationAccuracyMeters: Float? = null
) {
    val hasLocation: Boolean
        get() = locationLatitude != null && locationLongitude != null
}

data class NetworkMetrics(
    val rttMs: Int = 0,
    val hopCount: Int = 0,
    val packetLossPercent: Int = 0,
    val transportName: String = "BLE / Wi-Fi Direct"
)

data class MainUiState(
    val username: String = "",
    val selectedLanguageCode: String = "hi",
    val selectedLanguage: String = "हिन्दी",
    val squadPeers: List<PeerNodeUi> = emptyList(),
    val availablePeers: List<PeerNodeUi> = emptyList(),
    val pairedPeers: List<PeerNodeUi> = emptyList(),
    val messages: List<ChatMessageUi> = emptyList(),
    val sentMessages: List<ChatMessageUi> = emptyList(),
    val receivedMessages: List<ChatMessageUi> = emptyList(),
    val networkMetrics: NetworkMetrics = NetworkMetrics(),
    val isScanning: Boolean = false,
    val pttSessionState: SessionState = SessionState.IDLE,
    val pttLastTranscription: String? = null,
    val unreadMessageCount: Int = 0,
    val emergencyComposerVisible: Boolean = false,
    val emergencyRecording: Boolean = false,
    val emergencySending: Boolean = false,
    val emergencyTranscription: String = "",
    val emergencyError: String? = null
)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val discoveryService: DiscoveryService,
    private val meshService: MeshService,
    private val identityStore: DeviceIdentityStore,
    private val bleConnectionManager: BleConnectionManager,
    private val audioRecorder: AudioRecorder,
    private val speechToText: SpeechToText,
    private val hapticEngine: HapticEngine,
    private val mmsTtsEngine: MmsTtsEngine,
    private val mmsTtsModelStore: MmsTtsModelStore,
    private val speechLanguagePreferences: SpeechLanguagePreferences,
    private val localAppDataStore: LocalAppDataStore,
    private val messageNotificationNotifier: com.tactical.app.service.MessageNotificationNotifier
) : ViewModel() {

    // Must be initialized before _uiState because storedPeerToUi() uses it
    // while the initial state is being constructed.
    private val estimator = RssiProximityEstimator()

    private val _uiState = MutableStateFlow(
        MainUiState(
            username = identityStore.callsign,
            selectedLanguageCode = speechLanguagePreferences.selectedLanguageCode,
            selectedLanguage = displayLanguageName(speechLanguagePreferences.selectedLanguageCode),
            pairedPeers = localAppDataStore.loadPairedDevices()
                .filter { it.deviceId in bleConnectionManager.pairedDeviceIds() }
                .map(::storedPeerToUi),
            squadPeers = localAppDataStore.loadPairedDevices()
                .filter { it.deviceId in bleConnectionManager.pairedDeviceIds() }
                .map(::storedPeerToUi),
            receivedMessages = localAppDataStore.loadReceivedMessages()
                .asReversed()
                .map(::storedMessageToUi),
            messages = localAppDataStore.loadReceivedMessages()
                .asReversed()
                .map(::storedMessageToUi),
            unreadMessageCount = localAppDataStore.unreadMessageCount()
        )
    )
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()
    private var scanJob: Job? = null
    private var scanLoopJob: Job? = null
    private var healthJob: Job? = null
    private val observedPeerIds = mutableSetOf<String>()
    private val reconnectJobs = mutableMapOf<String, Job>()
    private val pttController: PttController = DefaultPttController(
        deviceId = DeviceId(identityStore.deviceIdValue),
        audioRecorder = audioRecorder,
        speechToText = speechToText,
        packetBuilder = PttPacketBuilder(),
        meshDispatcher = PttMeshDispatcher(meshService),
        hapticFeedback = PatternedHapticFeedback(hapticEngine),
        scope = viewModelScope,
        releaseGraceMs = 5000L
    )

    private var lastHandledPttSessionId: String? = null

    private val emergencyTrigger =
        com.tactical.emergency.trigger.HoldPanicTrigger(viewModelScope)

    private var emergencyRecordingJob: Job? = null

    private val emergencyMessageBuilder =
        com.tactical.emergency.message.EmergencyMessageBuilder()

    private val emergencyBroadcaster =
        com.tactical.emergency.broadcast.RadiusEmergencyBroadcaster(meshService)

    private fun speakIncomingMessage(packet: TextPacket) {
        val language = MmsTtsLanguage.fromIsoCode(packet.languageCode) ?: return

        viewModelScope.launch {
            runCatching {
                mmsTtsModelStore.ensureBundledModelsAvailable()
                mmsTtsEngine.synthesizeAndPlay(language, packet.text)
            }.onFailure { error ->
                android.util.Log.w(
                    "MainViewModel",
                    "Automatic TTS playback failed: " + (error.message ?: error.javaClass.simpleName)
                )
            }
        }
    }


    init {
        viewModelScope.launch {
            emergencyTrigger.state().collect { triggerState ->
                if (triggerState == com.tactical.emergency.trigger.PanicTriggerState.TRIGGERED) {
                    startEmergencyRecording()
                }
            }
        }

        viewModelScope.launch {
            pttController.state().collect { ptt ->
                _uiState.update {
                    it.copy(
                        pttSessionState = ptt.sessionState,
                        pttLastTranscription = ptt.lastTranscription
                    )
                }

                val sessionId = ptt.sessionId
                val text = ptt.lastTranscription?.trim().orEmpty()
                val result = ptt.lastResult

                if (
                    ptt.sessionState == SessionState.IDLE &&
                    !sessionId.isNullOrBlank() &&
                    sessionId != lastHandledPttSessionId &&
                    text.isNotBlank() &&
                    result != null
                ) {
                    lastHandledPttSessionId = sessionId
                    val status = when (result) {
                        is TacticalResult.Success -> "Sent"
                        is TacticalResult.Failure -> "Queued"
                    }
                    val message = ChatMessageUi(
                        sender = "YOU",
                        text = text,
                        timestampText = "Just now",
                        statusText = status,
                        isVoice = true,
                        timestampEpochMs = System.currentTimeMillis()
                    )
                    _uiState.update {
                        it.copy(
                            messages = listOf(message) + it.messages,
                            sentMessages = listOf(message) + it.sentMessages
                        )
                    }
                }
            }
        }

        viewModelScope.launch {
            startDiscovery()
        }

        bleConnectionManager.pairedDeviceIds().forEach { pairedId ->
            if (observedPeerIds.add(pairedId)) {
                observePeerState(pairedId)
            }
        }
        viewModelScope.launch {
            bleConnectionManager.pairedDeviceIds().forEach { pairedId ->
                runCatching { bleConnectionManager.reconnectPaired(pairedId) }
            }
        }

        viewModelScope.launch {
            discoveryService.peers().collectLatest { devices ->
                _uiState.update { state ->
                    val existing = (state.availablePeers + state.pairedPeers)
                        .associateBy { it.deviceAddress }

                    val peers = devices.map { device ->
                        val id = device.id.value
                        val previous = existing[id]
                        val hasRssi = device.rssi != 0
                        val callsign = device.callsign.ifBlank {
                            previous?.callsign ?: id
                        }
                        if (id in bleConnectionManager.pairedDeviceIds()) {
                            localAppDataStore.savePairedDevice(
                                StoredPairedDevice(
                                    deviceId = id,
                                    callsign = callsign,
                                    lastSeenEpochMs = device.lastSeen.toEpochMilli(),
                                    rssi = device.rssi,
                                    linkText = device.link.name
                                )
                            )
                        }
                        PeerNodeUi(
                            deviceAddress = id,
                            callsign = localAppDataStore.callsignForPeer(id)
                                ?: callsign,
                            isConnected = previous?.isConnected ?: false,
                            distanceText = if (hasRssi) {
                                formatDistance(estimator.estimate(device.rssi))
                            } else {
                                previous?.distanceText ?: "Unknown"
                            },
                            signalBars = if (hasRssi) signalBars(device.rssi) else (previous?.signalBars ?: 0),
                            linkText = if (hasRssi) device.link.name else (previous?.linkText ?: device.link.name),
                            bleState = previous?.bleState ?: BleLinkState.NOT_PAIRED
                        )
                    }

                    peers.forEach { peer ->
                        if (observedPeerIds.add(peer.deviceAddress)) {
                            observePeerState(peer.deviceAddress)
                        }

                        // Discovery resolves the peer's current BLE address.
                        // Trigger at most one reconnect attempt at a time per
                        // peer; scan callbacks can fire many times per second.
                        if (bleConnectionManager.pairedDeviceIds().contains(peer.deviceAddress)) {
                            val existingReconnect = reconnectJobs[peer.deviceAddress]
                            if (existingReconnect?.isActive != true) {
                                val job = viewModelScope.launch {
                                    runCatching {
                                        bleConnectionManager.reconnectPaired(peer.deviceAddress)
                                    }
                                }
                                reconnectJobs[peer.deviceAddress] = job
                                job.invokeOnCompletion {
                                    if (reconnectJobs[peer.deviceAddress] === job) {
                                        reconnectJobs.remove(peer.deviceAddress)
                                    }
                                }
                            }
                        }
                    }

                    val pairedIds = bleConnectionManager.pairedDeviceIds()
                    val pairedById = state.pairedPeers.associateBy { it.deviceAddress }
                    val pairedPeers = pairedIds.mapNotNull { id ->
                        peers.firstOrNull { it.deviceAddress == id } ?: pairedById[id]
                    }

                    state.copy(
                        squadPeers = pairedPeers,
                        availablePeers = peers.filter { it.deviceAddress !in pairedIds },
                        pairedPeers = pairedPeers
                    )
                }

            }
        }

        healthJob = viewModelScope.launch {
            try {
                while (true) {
                    try {
                        delay(5000L)
                        if (scanLoopJob == null || scanLoopJob?.isCancelled == true) {
                            startDiscovery()
                        }
                        if (discoveryService is DefaultDiscoveryService && discoveryService.peers().value.isEmpty()) {
                            runCatching { discoveryService.start() }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        // Keep the health loop alive after transient errors.
                    }
                }
            } catch (_: CancellationException) {
                // Normal ViewModel cancellation.
            }
        }
        viewModelScope.launch {
            meshService.receive().collect { packet ->
                if (packet is EmergencyPacket) {
                    val senderName =
                        localAppDataStore.callsignForPeer(packet.sender.value)
                            ?: packet.sender.value.take(12)
                    val details = emergencyAlertData(packet, senderName)
                    val message = ChatMessageUi(
                        sender = senderName,
                        text = packet.description,
                        timestampText = formatTimestamp(packet.timestamp),
                        statusText = "Emergency",
                        isAlert = true,
                        emergencyData = details,
                        timestampEpochMs = packet.timestamp
                    )

                    localAppDataStore.saveReceivedMessage(
                        StoredReceivedMessage(
                            senderId = packet.sender.value,
                            senderName = senderName,
                            text = packet.description,
                            timestampEpochMs = packet.timestamp,
                            isVoice = false,
                            isAlert = true,
                            severity = packet.severity.name,
                            languageCode = packet.languageCode,
                            locationLatitude = packet.location?.latitude,
                            locationLongitude = packet.location?.longitude,
                            locationAccuracyMeters = packet.location?.accuracyMeters
                        )
                    )

                    _uiState.update {
                        it.copy(
                            messages = listOf(message) + it.messages,
                            receivedMessages = listOf(message) + it.receivedMessages,
                            unreadMessageCount = localAppDataStore.unreadMessageCount()
                        )
                    }
                } else if (packet is TextPacket) {
                    val senderName =
                        localAppDataStore.callsignForPeer(packet.sender.value)
                            ?: packet.sender.value.take(12)
                    val isVoiceMessage = packet.languageCode != "und"

                    val message = ChatMessageUi(
                        sender = senderName,
                        text = packet.text,
                        timestampText = formatTimestamp(packet.timestamp),
                        statusText = "Received",
                        isVoice = isVoiceMessage,
                        timestampEpochMs = packet.timestamp
                    )

                    localAppDataStore.saveReceivedMessage(
                        StoredReceivedMessage(
                            senderId = packet.sender.value,
                            senderName = senderName,
                            text = packet.text,
                            timestampEpochMs = packet.timestamp,
                            isVoice = isVoiceMessage
                        )
                    )

                    messageNotificationNotifier.show(
                        senderName = senderName,
                        message = packet.text,
                        isVoice = isVoiceMessage
                    )

                    _uiState.update {
                        it.copy(
                            messages = listOf(message) + it.messages,
                            receivedMessages = listOf(message) + it.receivedMessages,
                            unreadMessageCount = localAppDataStore.unreadMessageCount()
                        )
                    }
                    speakIncomingMessage(packet)
                }
            }
        }
    }

    /**
     * Keeps discovery alive independently from any established connection.
     * The 10-second timer schedules fresh discovery cycles; it never calls
     * disconnect on an already connected peer.
     */
    fun startDiscovery() {
        if (scanLoopJob?.isActive == true) return

        scanLoopJob = viewModelScope.launch {
            runCatching { discoveryService.start() }
            try {
                while (true) {
                    try {
                        runScanCycle()
                        delay(DISCOVERY_INTERVAL_MS)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        // Keep periodic discovery alive after transient errors.
                    }
                }
            } catch (_: CancellationException) {
                // Normal cancellation of the ViewModel scope.
            }
        }
    }

    private suspend fun runScanCycle() {
        if (scanJob?.isActive == true) return

        val job = viewModelScope.launch {
            try {
                (discoveryService as? DefaultDiscoveryService)?.startDiscovery()
                _uiState.update { it.copy(isScanning = true) }
                delay(SINGLE_SCAN_WINDOW_MS)
            } catch (_: Exception) {
                // Keep the 10-second loop alive after an individual radio failure.
            } finally {
                (discoveryService as? DefaultDiscoveryService)?.stopDiscovery()
                _uiState.update { it.copy(isScanning = false) }
            }
        }

        scanJob = job
        job.join()
        scanJob = null
    }

    /** Immediate scan requested by the user. Persistent discovery remains enabled. */
    fun forceDiscovery() {
        if (scanJob?.isActive == true) return
        viewModelScope.launch { runScanCycle() }
    }

    fun pairPeer(deviceAddress: String) {
        viewModelScope.launch {
            val result = bleConnectionManager.pair(deviceAddress)
            if (result is TacticalResult.Success) {
                refreshPairedPeers()
                // Pairing is followed by the deterministic BLE link strategy.
                // Only one phone becomes the outbound GATT initiator; the peer
                // remains passive and can send through server notifications.
                bleConnectionManager.reconnectPaired(deviceAddress)
                refreshPairedPeers()
            }
        }
    }

    private fun refreshPairedPeers() {
        val pairedIds = bleConnectionManager.pairedDeviceIds()
        _uiState.update { state ->
            val pairedById = state.availablePeers.associateBy { it.deviceAddress } + state.pairedPeers.associateBy { it.deviceAddress }
            val paired = pairedIds.mapNotNull { pairedById[it] }
            state.copy(
                pairedPeers = paired,
                squadPeers = paired,
                availablePeers = state.availablePeers.filter { it.deviceAddress !in pairedIds }
            )
        }
    }

    fun observePeerState(deviceAddress: String) {
        viewModelScope.launch {
            bleConnectionManager.state(deviceAddress).collect { linkState ->
                _uiState.update { state ->
                    fun updatePeer(peer: PeerNodeUi): PeerNodeUi {
                        return if (peer.deviceAddress == deviceAddress) {
                            peer.copy(
                                bleState = linkState,
                                isConnected = linkState == BleLinkState.CONNECTED
                            )
                        } else {
                            peer
                        }
                    }

                    state.copy(
                        squadPeers = state.squadPeers.map(::updatePeer),
                        availablePeers = state.availablePeers.map(::updatePeer),
                        pairedPeers = state.pairedPeers.map(::updatePeer)
                    )
                }
            }
        }

        // RSSI is read directly from the established GATT session, so the
        // distance/signal display keeps updating between discovery scans.
        viewModelScope.launch {
            bleConnectionManager.rssi(deviceAddress).collect { rssi ->
                if (rssi == null) return@collect

                _uiState.update { state ->
                    fun updatePeer(peer: PeerNodeUi): PeerNodeUi {
                        return if (peer.deviceAddress == deviceAddress) {
                            peer.copy(
                                distanceText = formatDistance(estimator.estimate(rssi)),
                                signalBars = signalBars(rssi)
                            )
                        } else {
                            peer
                        }
                    }

                    state.copy(
                        squadPeers = state.squadPeers.map(::updatePeer),
                        availablePeers = state.availablePeers.map(::updatePeer),
                        pairedPeers = state.pairedPeers.map(::updatePeer)
                    )
                }
            }
        }
    }

    fun connectPeer(deviceAddress: String) {
        viewModelScope.launch { bleConnectionManager.connect(deviceAddress) }
    }

    fun repairPeer(deviceAddress: String) {
        viewModelScope.launch { bleConnectionManager.repairAndReconnect(deviceAddress) }
    }

    fun setSelectedLanguage(languageCode: String) {
        if (languageCode !in setOf("hi", "en")) return

        speechLanguagePreferences.setSelectedLanguageCode(languageCode)
        _uiState.update {
            it.copy(
                selectedLanguageCode = languageCode,
                selectedLanguage = displayLanguageName(languageCode)
            )
        }
    }

    fun setUsername(username: String): String? {
        val cleaned = username.trim()
        if (cleaned.isBlank()) return "Username cannot be blank."

        return runCatching {
            identityStore.setCallsign(cleaned)
        }.fold(
            onSuccess = {
                _uiState.update { state ->
                    state.copy(username = cleaned)
                }
                // Discovery reads the callsign from DeviceIdentityStore when
                // advertising, so refresh the local presence after a save.
                viewModelScope.launch {
                    runCatching { discoveryService.stop() }
                    runCatching { discoveryService.start() }
                }
                null
            },
            onFailure = { it.message ?: "Could not save username." }
        )
    }



    fun startEmergencyHold() {
        if (_uiState.value.pttSessionState != SessionState.IDLE ||
            _uiState.value.emergencyComposerVisible
        ) return
        emergencyTrigger.startHold()
    }

    fun releaseEmergencyHold() {
        emergencyTrigger.releaseHold()
    }

    private fun startEmergencyRecording() {
        if (_uiState.value.emergencyComposerVisible) return

        _uiState.update {
            it.copy(
                emergencyComposerVisible = true,
                emergencyRecording = true,
                emergencySending = false,
                emergencyTranscription = "",
                emergencyError = null
            )
        }

        emergencyRecordingJob?.cancel()
        emergencyRecordingJob = viewModelScope.launch {
            try {
                var finalizedText = ""
                var latestPartial = ""

                val frames = audioRecorder.start(AudioConfig())
                speechToText.transcribe(frames).collect { chunk ->
                    if (chunk.isFinal) {
                        val finalText = chunk.text.trim()
                        if (finalText.isNotBlank()) {
                            // Vosk/Moonshine can finalize a sentence at a
                            // pause. Keep those segments instead of replacing
                            // the previously transcribed emergency text.
                            finalizedText = appendEmergencyTranscript(
                                finalizedText,
                                finalText
                            )
                        }
                        latestPartial = ""
                    } else {
                        latestPartial = chunk.text.trim()
                    }

                    val liveText = appendEmergencyTranscript(
                        finalizedText,
                        latestPartial
                    )
                    if (liveText.isNotBlank()) {
                        _uiState.update {
                            it.copy(emergencyTranscription = liveText)
                        }
                    }
                }
            } catch (t: Throwable) {
                _uiState.update {
                    it.copy(
                        emergencyRecording = false,
                        emergencyError = t.message ?: t.javaClass.simpleName
                    )
                }
            }
        }
    }

    private fun appendEmergencyTranscript(existing: String, next: String): String {
        if (next.isBlank()) return existing
        if (existing.isBlank()) return next.trim()
        if (existing == next.trim()) return existing
        return existing.trim() + " " + next.trim()
    }

    fun cancelEmergency() {
        audioRecorder.stop()
        emergencyRecordingJob?.cancel()
        emergencyRecordingJob = null
        emergencyTrigger.reset()
        _uiState.update {
            it.copy(
                emergencyComposerVisible = false,
                emergencyRecording = false,
                emergencySending = false,
                emergencyTranscription = "",
                emergencyError = null
            )
        }
    }

    fun sendEmergency() {
        if (_uiState.value.emergencySending) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(emergencySending = true, emergencyError = null)
            }

            audioRecorder.stop()
            withTimeoutOrNull(4000L) {
                emergencyRecordingJob?.join()
            }

            val text = _uiState.value.emergencyTranscription.trim()
            if (text.isBlank()) {
                _uiState.update {
                    it.copy(
                        emergencySending = false,
                        emergencyRecording = false,
                        emergencyError = "No message was transcribed."
                    )
                }
                return@launch
            }

            val packet = emergencyMessageBuilder.build(
                sender = DeviceId(identityStore.deviceIdValue),
                severity = Severity.CRITICAL,
                description = text,
                location = null,
                languageCode = _uiState.value.selectedLanguageCode
            )

            val result = emergencyBroadcaster.broadcastSos(packet)
            val status = when (result) {
                is TacticalResult.Success -> "Sent"
                is TacticalResult.Failure -> "Queued"
            }
            val details = emergencyAlertData(packet, "YOU")
            val message = ChatMessageUi(
                sender = "YOU",
                text = packet.description,
                timestampText = formatTimestamp(packet.timestamp),
                statusText = status,
                isAlert = true,
                emergencyData = details,
                timestampEpochMs = packet.timestamp
            )

            _uiState.update {
                it.copy(
                    messages = listOf(message) + it.messages,
                    sentMessages = listOf(message) + it.sentMessages,
                    emergencyComposerVisible = false,
                    emergencyRecording = false,
                    emergencySending = false,
                    emergencyTranscription = "",
                    emergencyError = null
                )
            }

            emergencyRecordingJob = null
            emergencyTrigger.reset()
        }
    }

    private fun emergencyAlertData(
        packet: EmergencyPacket,
        senderName: String
    ): EmergencyAlertData =
        EmergencyAlertData(
            sender = senderName,
            timestampText = formatTimestamp(packet.timestamp),
            severity = packet.severity.name,
            message = packet.description,
            languageCode = packet.languageCode,
            locationLatitude = packet.location?.latitude,
            locationLongitude = packet.location?.longitude,
            locationAccuracyMeters = packet.location?.accuracyMeters
        )

    fun pressPtt() {
        viewModelScope.launch {
            runCatching { pttController.press() }
        }
    }

    fun releasePtt() {
        viewModelScope.launch {
            runCatching { pttController.release() }
        }
    }

    fun markMessagesRead() {
        localAppDataStore.markMessagesRead()
        _uiState.update { it.copy(unreadMessageCount = 0) }
    }

    fun cancelPtt() {
        viewModelScope.launch {
            runCatching { pttController.cancel() }
        }
    }

    fun sendTextMessage(text: String) {
        val value = text.trim()
        if (value.isBlank()) return

        val pending = ChatMessageUi(
            sender = "YOU",
            text = value,
            timestampText = "Just now",
            statusText = "Sending…",
            timestampEpochMs = System.currentTimeMillis()
        )
        _uiState.update {
            it.copy(
                messages = listOf(pending) + it.messages,
                sentMessages = listOf(pending) + it.sentMessages
            )
        }

        viewModelScope.launch {
            val pairedIds = bleConnectionManager.pairedDeviceIds()
            if (pairedIds.isEmpty()) {
                _uiState.update { state ->
                    state.copy(
                        messages = state.messages.mapIndexed { index, msg ->
                            if (index == 0 && msg.sender == "YOU" && msg.text == value) {
                                msg.copy(statusText = "No paired devices")
                            } else msg
                        },
                        sentMessages = state.sentMessages.mapIndexed { index, msg ->
                            if (index == 0 && msg.text == value) {
                                msg.copy(statusText = "No paired devices")
                            } else msg
                        }
                    )
                }
                return@launch
            }

            // Send immediately when a GATT session is already ready.
            // Do not block the UI behind the BLE manager's 20-second reconnect
            // timeout just because a paired peer is temporarily disconnected.
            // The connection manager already performs background retries.
            val hasConnectedPeer = pairedIds.any { peerId ->
                bleConnectionManager.state(peerId).first() == BleLinkState.CONNECTED
            }

            val connectionReady = if (hasConnectedPeer) {
                true
            } else {
                // Give one short, parallel reconnect window rather than
                // reconnecting paired peers serially.
                coroutineScope {
                    pairedIds.map { peerId ->
                        async {
                            runCatching {
                                kotlinx.coroutines.withTimeoutOrNull(4000L) {
                                    bleConnectionManager.reconnectPaired(peerId)
                                }
                            }.getOrNull() is TacticalResult.Success
                        }
                    }.awaitAll().any { it }
                }
            }

            if (!connectionReady) {
                _uiState.update { state ->
                    state.copy(
                        messages = state.messages.mapIndexed { index, msg ->
                            if (index == 0 && msg.sender == "YOU" && msg.text == value) {
                                msg.copy(statusText = "No active connections")
                            } else msg
                        },
                        sentMessages = state.sentMessages.mapIndexed { index, msg ->
                            if (index == 0 && msg.text == value) {
                                msg.copy(statusText = "No active connections")
                            } else msg
                        }
                    )
                }
                return@launch
            }

            val result = meshService.send(
                TextPacket(
                    sender = DeviceId(identityStore.deviceIdValue),
                    text = value,
                    languageCode = "und",
                    timestamp = System.currentTimeMillis()
                )
            )

            val status = when (result) {
                is TacticalResult.Success -> "Sent"
                is TacticalResult.Failure -> "Queued"
            }

            _uiState.update { state ->
                state.copy(
                    messages = state.messages.mapIndexed { index, msg ->
                        if (index == 0 && msg.sender == "YOU" && msg.text == value) {
                            msg.copy(statusText = status)
                        } else msg
                    },
                    sentMessages = state.sentMessages.mapIndexed { index, msg ->
                        if (index == 0 && msg.text == value) {
                            msg.copy(statusText = status)
                        } else msg
                    }
                )
            }
        }
    }

    private fun displayLanguageName(languageCode: String): String =
        when (languageCode) {
            "hi" -> "हिन्दी"
            "en" -> "English"
            else -> languageCode.uppercase(Locale.US)
        }

    private fun storedPeerToUi(peer: StoredPairedDevice): PeerNodeUi =
        PeerNodeUi(
            deviceAddress = peer.deviceId,
            callsign = peer.callsign,
            isConnected = false,
            distanceText = if (peer.rssi != 0) {
                formatDistance(estimator.estimate(peer.rssi))
            } else {
                "Unknown"
            },
            signalBars = if (peer.rssi != 0) signalBars(peer.rssi) else 0,
            linkText = peer.linkText,
            bleState = BleLinkState.PAIRED
        )

    private fun storedMessageToUi(message: StoredReceivedMessage): ChatMessageUi {
        val emergencyData = if (message.isAlert) {
            EmergencyAlertData(
                sender = message.senderName,
                timestampText = formatTimestamp(message.timestampEpochMs),
                severity = message.severity ?: Severity.CRITICAL.name,
                message = message.text,
                languageCode = message.languageCode ?: "und",
                locationLatitude = message.locationLatitude,
                locationLongitude = message.locationLongitude,
                locationAccuracyMeters = message.locationAccuracyMeters
            )
        } else {
            null
        }

        return ChatMessageUi(
            sender = message.senderName,
            text = message.text,
            timestampText = formatTimestamp(message.timestampEpochMs),
            statusText = if (message.isAlert) "Emergency" else "Received",
            isVoice = message.isVoice,
            isAlert = message.isAlert,
            emergencyData = emergencyData,
            timestampEpochMs = message.timestampEpochMs
        )
    }

    private fun formatTimestamp(epochMs: Long): String {
        if (epochMs <= 0L) return "Unknown"
        return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(epochMs))
    }

    private fun formatDistance(distance: Double): String =
        if (distance < 0) "Unknown"
        else if (distance < 1000) "${distance.toInt()} m"
        else String.format(Locale.US, "%.1f km", distance / 1000.0)

    private fun signalBars(rssi: Int) = when {
        rssi >= -55 -> 4
        rssi >= -65 -> 3
        rssi >= -75 -> 2
        else -> 1
    }

    override fun onCleared() {
        scanLoopJob?.cancel()
        healthJob?.cancel()
        viewModelScope.launch { discoveryService.stop() }
        scanJob?.cancel()
        reconnectJobs.values.forEach { it.cancel() }
        reconnectJobs.clear()
        super.onCleared()
    }
    companion object {
        private const val SINGLE_SCAN_WINDOW_MS = 5000L
        private const val DISCOVERY_INTERVAL_MS = 10000L
    }
}
