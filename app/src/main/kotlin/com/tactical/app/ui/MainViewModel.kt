package com.tactical.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tactical.app.di.DeviceIdentityStore
import com.tactical.domain.identity.DeviceId
import com.tactical.domain.identity.LinkType
import com.tactical.domain.packet.TextPacket
import com.tactical.domain.result.TacticalResult
import com.tactical.platform.api.audio.AudioRecorder
import com.tactical.platform.api.haptics.HapticEngine
import com.tactical.platform.api.speech.SpeechToText
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Locale
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
    val isVoice: Boolean = false
)

data class EmergencyAlertData(
    val sender: String = "COMMANDER",
    val timestampText: String = "10:32 AM",
    val hindiText: String = "कृपया तुरंत सुरक्षित स्थान पर जाएं!",
    val englishText: String = "Please move to a safe location immediately.",
    val durationSeconds: Int = 4
)

data class NetworkMetrics(
    val rttMs: Int = 0,
    val hopCount: Int = 0,
    val packetLossPercent: Int = 0,
    val transportName: String = "BLE / Wi-Fi Direct"
)

data class MainUiState(
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
    val pttLastTranscription: String? = null
)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val discoveryService: DiscoveryService,
    private val meshService: MeshService,
    private val identityStore: DeviceIdentityStore,
    private val bleConnectionManager: BleConnectionManager,
    private val audioRecorder: AudioRecorder,
    private val speechToText: SpeechToText,
    private val hapticEngine: HapticEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()
    private val estimator = RssiProximityEstimator()
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


    init {
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
                        isVoice = true
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
                        PeerNodeUi(
                            deviceAddress = id,
                            callsign = device.callsign.ifBlank { previous?.callsign ?: id },
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
                if (packet is TextPacket) {
                    val message = ChatMessageUi(
                        sender = packet.sender.value.take(12),
                        text = packet.text,
                        timestampText = "Just now",
                        statusText = "Received"
                    )
                    _uiState.update {
                        it.copy(
                            messages = listOf(message) + it.messages,
                            receivedMessages = listOf(message) + it.receivedMessages
                        )
                    }
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

    fun sendTextMessage(text: String) {
        val value = text.trim()
        if (value.isBlank()) return

        val pending = ChatMessageUi("YOU", value, "Just now", "Sending…")
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

            var connectedCount = 0
            pairedIds.forEach { peerId ->
                val connectionResult = bleConnectionManager.reconnectPaired(peerId)
                if (connectionResult is TacticalResult.Success) {
                    connectedCount++
                }
            }

            if (connectedCount == 0) {
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
