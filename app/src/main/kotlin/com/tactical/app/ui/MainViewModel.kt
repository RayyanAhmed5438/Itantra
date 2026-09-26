package com.tactical.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tactical.app.di.DeviceIdentityStore
import com.tactical.app.di.LocalAppDataStore
import com.tactical.app.di.PttModePreferences
import com.tactical.app.di.StoredPairedDevice
import com.tactical.app.di.StoredReceivedMessage
import com.tactical.app.di.StoredSentMessage
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
import com.tactical.platform.speech.SpeechLanguagePreferences
import com.tactical.platform.speech.RoutingSpeechToText
import com.tactical.ptt.controller.DefaultPttController
import com.tactical.ptt.controller.PttTransmission
import com.tactical.ptt.controller.PttTransmissionStatus
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
import com.tactical.platform.api.ble.SquadRequest
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
    val bleState: BleLinkState = BleLinkState.AVAILABLE
)

data class ChatMessageUi(
    val sender: String,
    val text: String,
    val timestampText: String,
    val statusText: String,
    val isAlert: Boolean = false,
    val isVoice: Boolean = false,
    val isCallMode: Boolean = false,
    val emergencyData: EmergencyAlertData? = null,
    val timestampEpochMs: Long = 0L,
    // Local time used only for ordering messages in the conversation.
    // Unlike timestampEpochMs, this never comes from another device.
    val conversationOrderEpochMs: Long = 0L
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

data class PttTransmissionUi(
    val id: String,
    val text: String,
    val timestampEpochMs: Long,
    val statusText: String
)

data class MainUiState(
    val username: String = "",
    val selectedLanguageCode: String = "hi",
    val selectedLanguage: String = "हिन्दी",
    val squadPeers: List<PeerNodeUi> = emptyList(),
    val availablePeers: List<PeerNodeUi> = emptyList(),
    val messages: List<ChatMessageUi> = emptyList(),
    val sentMessages: List<ChatMessageUi> = emptyList(),
    val receivedMessages: List<ChatMessageUi> = emptyList(),
    val networkMetrics: NetworkMetrics = NetworkMetrics(),
    val isScanning: Boolean = false,
    val pttSessionState: SessionState = SessionState.IDLE,
    val pttLastTranscription: String? = null,
    val pttEnabled: Boolean = true,
    val pttContinuousSession: Boolean = false,
    val pttTransmissionHistory: List<PttTransmissionUi> = emptyList(),
    val unreadMessageCount: Int = 0,
    val emergencyComposerVisible: Boolean = false,
    val emergencyRecording: Boolean = false,
    val emergencySending: Boolean = false,
    val emergencyTranscription: String = "",
    val emergencyError: String? = null,
    val pendingSquadRequest: SquadRequest? = null
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
    private val speechLanguagePreferences: SpeechLanguagePreferences,
    private val routingSpeechToText: RoutingSpeechToText,
    private val localAppDataStore: LocalAppDataStore,
    private val pttModePreferences: PttModePreferences,
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
            squadPeers = localAppDataStore.loadPairedDevices()
                .filter { it.deviceId in bleConnectionManager.squadDeviceIds() }
                .map(::storedPeerToUi),
            receivedMessages = localAppDataStore.loadReceivedMessages()
                .asReversed()
                .map(::storedMessageToUi),
            messages = localAppDataStore.loadReceivedMessages()
                .asReversed()
                .map(::storedMessageToUi),
            sentMessages = localAppDataStore.loadSentMessages()
                .map(::storedSentMessageToUi),
            pttEnabled = pttModePreferences.isPttEnabled,
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
    private val continuousTransmissionMessages = mutableMapOf<String, ChatMessageUi>()
    private var resumeContinuousAfterEmergency = false

    private val emergencyTrigger =
        com.tactical.emergency.trigger.HoldPanicTrigger(viewModelScope)

    private var emergencyRecordingJob: Job? = null

    private val emergencyMessageBuilder =
        com.tactical.emergency.message.EmergencyMessageBuilder()

    private val emergencyBroadcaster =
        com.tactical.emergency.broadcast.RadiusEmergencyBroadcaster(meshService)

    init {
        // Keep the unread badge synchronized with messages persisted by either
        // the Activity/ViewModel or the foreground mesh service. This avoids
        // losing the badge when a SharedFlow packet is consumed before the
        // Activity's collector is ready.
        viewModelScope.launch {
            localAppDataStore.receivedMessagesChanged.collect {
                // The foreground service persists incoming messages even when
                // the Messages screen is already visible. Reload the list and
                // unread count immediately so navigation is not required.
                refreshReceivedMessages()
            }
        }

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
                        pttLastTranscription = ptt.lastTranscription,
                        pttContinuousSession = ptt.continuousSession,
                        pttTransmissionHistory = if (ptt.continuousSession) {
                            ptt.transmissions.map(::pttTransmissionToUi)
                        } else {
                            it.pttTransmissionHistory
                        }
                    )
                }

                if (ptt.continuousSession) {
                    syncContinuousTransmissions(ptt.transmissions)
                    return@collect
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
                    val localSendTime = System.currentTimeMillis()
                    val message = ChatMessageUi(
                        sender = "YOU",
                        text = text,
                        timestampText = "Just now",
                        statusText = status,
                        isVoice = true,
                        timestampEpochMs = localSendTime,
                        conversationOrderEpochMs = localSendTime
                    )
                    localAppDataStore.saveSentMessage(
                        storedSentMessage(message)
                    )
                    _uiState.update {
                        it.copy(
                            messages = listOf(message) + it.messages,
                            sentMessages = listOf(message) + it.sentMessages,
                            pttTransmissionHistory = listOf(
                                PttTransmissionUi(
                                    id = sessionId,
                                    text = text,
                                    timestampEpochMs = localSendTime,
                                    statusText = status
                                )
                            ) + it.pttTransmissionHistory.take(19)
                        )
                    }
                }
            }
        }

        viewModelScope.launch {
            startDiscovery()
        }

        bleConnectionManager.squadDeviceIds().forEach { pairedId ->
            if (observedPeerIds.add(pairedId)) {
                observePeerState(pairedId)
            }
        }
        viewModelScope.launch {
            bleConnectionManager.squadDeviceIds().forEach { pairedId ->
                runCatching { bleConnectionManager.reconnectSquadMember(pairedId) }
            }
        }

        viewModelScope.launch {
            bleConnectionManager.pendingSquadRequests().collect { requests ->
                _uiState.update { it.copy(pendingSquadRequest = requests.firstOrNull()) }
            }
        }

        viewModelScope.launch {
            discoveryService.peers().collectLatest { devices ->
                _uiState.update { state ->
                    val existing = (state.availablePeers + state.squadPeers)
                        .associateBy { it.deviceAddress }

                    val peers = devices.map { device ->
                        val id = device.id.value
                        val previous = existing[id]
                        val hasRssi = device.rssi != 0
                        val callsign = device.callsign.ifBlank {
                            previous?.callsign ?: id
                        }

                        if (id in bleConnectionManager.squadDeviceIds()) {
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
                            callsign = localAppDataStore.callsignForPeer(id) ?: callsign,
                            isConnected = previous?.isConnected ?: false,
                            distanceText = if (hasRssi) {
                                formatDistance(estimator.estimate(device.rssi))
                            } else {
                                previous?.distanceText ?: "Unknown"
                            },
                            signalBars = if (hasRssi) {
                                signalBars(device.rssi)
                            } else {
                                previous?.signalBars ?: 0
                            },
                            linkText = if (hasRssi) {
                                device.link.name
                            } else {
                                previous?.linkText ?: device.link.name
                            },
                            bleState = previous?.bleState ?: BleLinkState.AVAILABLE
                        )
                    }

                    peers.forEach { peer ->
                        if (observedPeerIds.add(peer.deviceAddress)) {
                            observePeerState(peer.deviceAddress)
                        }
                    }

                    val squadIds = bleConnectionManager.squadDeviceIds()
                    val squadById = state.squadPeers.associateBy { it.deviceAddress }
                    val currentSquad = squadIds.mapNotNull { id ->
                        peers.firstOrNull { it.deviceAddress == id } ?: squadById[id]
                    }
                    state.copy(
                        squadPeers = currentSquad,
                        availablePeers = peers.filter { it.deviceAddress !in squadIds }
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

    fun addPeerToSquad(deviceAddress: String) {
        viewModelScope.launch {
            runCatching { bleConnectionManager.addToSquad(deviceAddress) }
        }
    }
    fun respondToSquadRequest(deviceId: String, approve: Boolean) {
        viewModelScope.launch {
            runCatching { bleConnectionManager.respondToSquadRequest(deviceId, approve) }
        }
    }
    fun removePeerFromSquad(deviceAddress: String) {
        viewModelScope.launch {
            bleConnectionManager.removeFromSquad(deviceAddress)
            localAppDataStore.removePairedDevice(deviceAddress)
            refreshSquadPeers()
        }
    }

    private fun refreshSquadPeers() {
        val squadIds = bleConnectionManager.squadDeviceIds()
        _uiState.update { state ->
            val knownById = (state.availablePeers + state.squadPeers)
                .associateBy { it.deviceAddress }
            val squad = squadIds.mapNotNull { id -> knownById[id] }
            state.copy(
                squadPeers = squad,
                availablePeers = knownById.values
                    .filter { it.deviceAddress !in squadIds }
            )
        }
    }
    fun observePeerState(deviceAddress: String) {
        viewModelScope.launch {
            bleConnectionManager.state(deviceAddress).collect { linkState ->
                _uiState.update { state ->
                    fun updatePeer(peer: PeerNodeUi): PeerNodeUi =
                        if (peer.deviceAddress == deviceAddress) {
                            peer.copy(
                                bleState = linkState,
                                isConnected = linkState == BleLinkState.CONNECTED
                            )
                        } else {
                            peer
                        }

                    val updatedPeers = (state.squadPeers + state.availablePeers)
                        .map(::updatePeer)
                        .distinctBy { it.deviceAddress }
                    val squadIds = bleConnectionManager.squadDeviceIds()

                    state.copy(
                        squadPeers = updatedPeers.filter { it.deviceAddress in squadIds },
                        availablePeers = updatedPeers.filter { it.deviceAddress !in squadIds }
                    )
                }
            }
        }

        // Keep RSSI/distance/signal information fresh between discovery scans.
        viewModelScope.launch {
            bleConnectionManager.rssi(deviceAddress).collect { rssi ->
                if (rssi == null) return@collect

                _uiState.update { state ->
                    fun updatePeer(peer: PeerNodeUi): PeerNodeUi =
                        if (peer.deviceAddress == deviceAddress) {
                            peer.copy(
                                distanceText = formatDistance(estimator.estimate(rssi)),
                                signalBars = signalBars(rssi)
                            )
                        } else {
                            peer
                        }

                    state.copy(
                        squadPeers = state.squadPeers.map(::updatePeer),
                        availablePeers = state.availablePeers.map(::updatePeer)
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

        val wasContinuousCallMode =
            !_uiState.value.pttEnabled && _uiState.value.pttContinuousSession

        // A running call-mode STT flow must be stopped before swapping the
        // selected backend. Otherwise the old backend can keep owning the
        // microphone/model while the preference changes underneath it.
        viewModelScope.launch {
            if (wasContinuousCallMode) {
                runCatching { pttController.stopContinuous() }
            }

            speechLanguagePreferences.setSelectedLanguageCode(languageCode)
            routingSpeechToText.onSelectedLanguageChanged(languageCode)
            // Warm the newly selected STT backend immediately so the next PTT
            // press does not pay the native model-load cost.
            runCatching { routingSpeechToText.preloadSelectedLanguage() }

            _uiState.update {
                it.copy(
                    selectedLanguageCode = languageCode,
                    selectedLanguage = displayLanguageName(languageCode)
                )
            }

            MmsTtsLanguage.fromIsoCode(languageCode)?.let { language ->
                runCatching { mmsTtsEngine.preload(language) }
            }

            if (wasContinuousCallMode && !_uiState.value.pttEnabled) {
                runCatching { pttController.startContinuous() }
            }
        }
    }

    fun setPttEnabled(enabled: Boolean) {
        val state = _uiState.value

        if (state.pttEnabled == enabled) return
        if (state.emergencyComposerVisible) return

        // Do not switch voice mode underneath an active manual PTT session.
        // The microphone must remain owned by exactly one capture session.
        if (state.pttSessionState != SessionState.IDLE && !state.pttContinuousSession) {
            return
        }

        pttModePreferences.setPttEnabled(enabled)
        _uiState.update { it.copy(pttEnabled = enabled) }

        viewModelScope.launch {
            if (enabled) {
                runCatching { pttController.stopContinuous() }
            } else {
                runCatching { pttController.startContinuous() }
            }
        }
    }

    /**
     * Starts continuous voice mode after microphone permission is available.
     */
    fun ensureVoiceMode() {
        if (!_uiState.value.pttEnabled) {
            viewModelScope.launch {
                runCatching { pttController.startContinuous() }
            }
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
        val state = _uiState.value
        if (state.emergencyComposerVisible) return

        if (state.pttContinuousSession) {
            // Emergency recording shares the microphone with continuous mode.
            // Stop continuous capture immediately while the existing 2-second
            // emergency hold timer continues from the user's initial press.
            resumeContinuousAfterEmergency = !state.pttEnabled
            audioRecorder.stop()
            viewModelScope.launch {
                runCatching { pttController.stopContinuous() }
            }
        } else if (state.pttSessionState != SessionState.IDLE) {
            return
        }

        emergencyTrigger.startHold()
    }

    fun releaseEmergencyHold() {
        val shouldResume = resumeContinuousAfterEmergency &&
            !_uiState.value.emergencyComposerVisible

        emergencyTrigger.releaseHold()

        if (shouldResume) {
            resumeContinuousVoiceIfNeeded()
        }
    }

    private fun startEmergencyRecording() {
        if (_uiState.value.emergencyComposerVisible) return

        // The emergency UI becomes active first so releasing the emergency
        // button after the verified hold cannot accidentally restart call mode.
        _uiState.update {
            it.copy(
                emergencyComposerVisible = true,
                emergencyRecording = false,
                emergencySending = false,
                emergencyTranscription = "",
                emergencyError = null
            )
        }

        emergencyRecordingJob?.cancel()
        emergencyRecordingJob = viewModelScope.launch {
            try {
                // Continuous voice mode shares the same microphone. Wait for it
                // to finish releasing the recorder before starting emergency STT.
                runCatching { pttController.stopContinuous() }

                _uiState.update {
                    it.copy(emergencyRecording = true)
                }

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
                resumeContinuousVoiceIfNeeded()
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
        resumeContinuousVoiceIfNeeded()
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
            val localSendTime = System.currentTimeMillis()
            val message = ChatMessageUi(
                sender = "YOU",
                text = packet.description,
                timestampText = formatTimestamp(packet.timestamp),
                statusText = status,
                isAlert = true,
                emergencyData = details,
                timestampEpochMs = packet.timestamp,
                conversationOrderEpochMs = localSendTime
            )
            localAppDataStore.saveSentMessage(
                storedSentMessage(message)
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
            resumeContinuousVoiceIfNeeded()
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
        if (!_uiState.value.pttEnabled) return
        viewModelScope.launch {
            runCatching { pttController.press() }
        }
    }

    fun releasePtt() {
        if (!_uiState.value.pttEnabled) return
        viewModelScope.launch {
            runCatching { pttController.release() }
        }
    }

    fun deleteMessages(messages: Set<ChatMessageUi>, isSent: Boolean) {
        if (messages.isEmpty()) return

        val keys = messages.mapTo(mutableSetOf()) { messageStorageKey(it) }

        if (isSent) {
            localAppDataStore.deleteSentMessages(keys)
            _uiState.update { state ->
                state.copy(
                    messages = state.messages.filterNot { messageStorageKey(it) in keys },
                    sentMessages = state.sentMessages.filterNot { messageStorageKey(it) in keys }
                )
            }
        } else {
            localAppDataStore.deleteReceivedMessages(keys)
            refreshReceivedMessages()
        }
    }

    private fun messageStorageKey(message: ChatMessageUi): String =
        localAppDataStore.messageStorageKey(
            senderName = message.sender,
            timestampEpochMs = message.timestampEpochMs,
            text = message.text,
            isAlert = message.isAlert,
            isVoice = message.isVoice,
            isCallMode = message.isCallMode
        )

    fun refreshReceivedMessages() {
        val received = localAppDataStore.loadReceivedMessages()
            .asReversed()
            .map(::storedMessageToUi)

        _uiState.update {
            it.copy(
                receivedMessages = received,
                messages = (it.sentMessages + received)
                    .distinctBy(::messageStorageKey)
                    .sortedBy {
                        it.conversationOrderEpochMs.takeIf { time -> time > 0L }
                            ?: it.timestampEpochMs
                    },
                unreadMessageCount = localAppDataStore.unreadMessageCount()
            )
        }
    }

    fun markMessagesRead() {
        // Refresh from persistent storage first so messages received while the
        // Activity was backgrounded are present as soon as Messages is opened.
        refreshReceivedMessages()
        localAppDataStore.markMessagesRead()
        messageNotificationNotifier.clearMessageNotifications()
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

        val localSendTime = System.currentTimeMillis()
        val pending = ChatMessageUi(
            sender = "YOU",
            text = value,
            timestampText = "Just now",
            statusText = "Sending…",
            timestampEpochMs = localSendTime,
            conversationOrderEpochMs = localSendTime
        )
        localAppDataStore.saveSentMessage(
            storedSentMessage(pending)
        )

        _uiState.update {
            it.copy(
                messages = listOf(pending) + it.messages,
                sentMessages = listOf(pending) + it.sentMessages
            )
        }

        viewModelScope.launch {
            val squadIds = bleConnectionManager.squadDeviceIds()
            if (squadIds.isEmpty()) {
                updateSentMessageStatus(pending, "No squad members")
                return@launch
            }

            // Send immediately when a GATT session is already ready.
            // Do not block the UI behind the BLE manager's 20-second reconnect
            // timeout just because a squad member is temporarily disconnected.
            // The connection manager already performs background retries.
            val hasConnectedPeer = squadIds.any { peerId ->
                bleConnectionManager.state(peerId).first() == BleLinkState.CONNECTED
            }

            val connectionReady = if (hasConnectedPeer) {
                true
            } else {
                // Give one short, parallel reconnect window rather than
                // reconnecting squad members serially.
                coroutineScope {
                    squadIds.map { peerId ->
                        async {
                            runCatching {
                                kotlinx.coroutines.withTimeoutOrNull(4000L) {
                                    bleConnectionManager.reconnectSquadMember(peerId)
                                }
                            }.getOrNull() is TacticalResult.Success
                        }
                    }.awaitAll().any { it }
                }
            }

            if (!connectionReady) {
                updateSentMessageStatus(pending, "No active connections")
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

            updateSentMessageStatus(pending, status)
        }
    }

    private fun updateSentMessageStatus(
        message: ChatMessageUi,
        statusText: String
    ) {
        val key = messageStorageKey(message)

        localAppDataStore.updateSentMessageStatus(key, statusText)

        _uiState.update { state ->
            state.copy(
                messages = state.messages.map {
                    if (messageStorageKey(it) == key) {
                        it.copy(statusText = statusText)
                    } else {
                        it
                    }
                },
                sentMessages = state.sentMessages.map {
                    if (messageStorageKey(it) == key) {
                        it.copy(statusText = statusText)
                    } else {
                        it
                    }
                }
            )
        }
    }

    private fun pttTransmissionToUi(transmission: PttTransmission): PttTransmissionUi =
        PttTransmissionUi(
            id = transmission.id,
            text = transmission.text,
            timestampEpochMs = transmission.timestampEpochMs,
            statusText = when (transmission.status) {
                PttTransmissionStatus.SENDING -> "Sending…"
                PttTransmissionStatus.SENT -> "Sent"
                PttTransmissionStatus.QUEUED -> "Queued"
            }
        )

    private suspend fun syncContinuousTransmissions(
        transmissions: List<PttTransmission>
    ) {
        transmissions.forEach { transmission ->
            val statusText = when (transmission.status) {
                PttTransmissionStatus.SENDING -> "Sending…"
                PttTransmissionStatus.SENT -> "Sent"
                PttTransmissionStatus.QUEUED -> "Queued"
            }

            val existingMessage = continuousTransmissionMessages[transmission.id]

            if (existingMessage == null) {
                val message = ChatMessageUi(
                    sender = "YOU",
                    text = transmission.text,
                    timestampText = formatTimestamp(transmission.timestampEpochMs),
                    statusText = statusText,
                    isVoice = true,
                    isCallMode = true,
                    timestampEpochMs = transmission.timestampEpochMs,
                    conversationOrderEpochMs = transmission.timestampEpochMs
                )

                continuousTransmissionMessages[transmission.id] = message
                localAppDataStore.saveSentMessage(storedSentMessage(message))

                _uiState.update { state ->
                    if (state.sentMessages.any {
                        messageStorageKey(it) == messageStorageKey(message)
                    }) {
                        state
                    } else {
                        state.copy(
                            messages = listOf(message) + state.messages,
                            sentMessages = listOf(message) + state.sentMessages
                        )
                    }
                }
            } else if (existingMessage.statusText != statusText) {
                val updated = existingMessage.copy(statusText = statusText)
                continuousTransmissionMessages[transmission.id] = updated
                updateSentMessageStatus(existingMessage, statusText)
            }
        }
    }

    private fun resumeContinuousVoiceIfNeeded() {
        if (!resumeContinuousAfterEmergency) return
        resumeContinuousAfterEmergency = false

        if (!_uiState.value.pttEnabled) {
            viewModelScope.launch {
                runCatching { pttController.startContinuous() }
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
            bleState = BleLinkState.AVAILABLE
        )

    private fun storedSentMessageToUi(message: StoredSentMessage): ChatMessageUi =
        ChatMessageUi(
            sender = message.senderName,
            text = message.text,
            timestampText = if (message.timestampEpochMs > 0L) {
                formatTimestamp(message.timestampEpochMs)
            } else {
                "Unknown"
            },
            statusText = message.statusText,
            isVoice = message.isVoice,
            isCallMode = message.isCallMode,
            isAlert = message.isAlert,
            emergencyData = if (message.isAlert) {
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
            },
            timestampEpochMs = message.timestampEpochMs,
            conversationOrderEpochMs = message.conversationOrderEpochMs
        )

    private fun storedSentMessage(message: ChatMessageUi): StoredSentMessage =
        StoredSentMessage(
            senderName = message.sender,
            text = message.text,
            timestampEpochMs = message.timestampEpochMs,
            statusText = message.statusText,
            isVoice = message.isVoice,
            isCallMode = message.isCallMode,
            isAlert = message.isAlert,
            severity = message.emergencyData?.severity,
            languageCode = message.emergencyData?.languageCode,
            locationLatitude = message.emergencyData?.locationLatitude,
            locationLongitude = message.emergencyData?.locationLongitude,
            locationAccuracyMeters = message.emergencyData?.locationAccuracyMeters,
            conversationOrderEpochMs = message.conversationOrderEpochMs
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
            isCallMode = message.isCallMode,
            isAlert = message.isAlert,
            emergencyData = emergencyData,
            timestampEpochMs = message.timestampEpochMs,
            conversationOrderEpochMs = message.receivedAtEpochMs
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
        audioRecorder.stop()
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
