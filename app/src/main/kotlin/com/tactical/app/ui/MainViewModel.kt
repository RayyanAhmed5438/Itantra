package com.tactical.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tactical.app.di.DeviceIdentityStore
import com.tactical.domain.identity.DeviceId
import com.tactical.domain.identity.LinkType
import com.tactical.domain.packet.TextPacket
import com.tactical.domain.result.TacticalResult
import com.tactical.engine.discovery.proximity.RssiProximityEstimator
import com.tactical.engine.discovery.service.DefaultDiscoveryService
import com.tactical.engine.discovery.service.DiscoveryService
import com.tactical.engine.mesh.service.MeshService
import com.tactical.platform.api.ble.BleConnectionManager
import com.tactical.platform.api.ble.BleLinkState
import dagger.hilt.android.lifecycle.HiltViewModel
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
    val messages: List<ChatMessageUi> = emptyList(),
    val sentMessages: List<ChatMessageUi> = emptyList(),
    val receivedMessages: List<ChatMessageUi> = emptyList(),
    val networkMetrics: NetworkMetrics = NetworkMetrics(),
    val isScanning: Boolean = false
)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val discoveryService: DiscoveryService,
    private val meshService: MeshService,
    private val identityStore: DeviceIdentityStore,
    private val bleConnectionManager: BleConnectionManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()
    private val estimator = RssiProximityEstimator()
    private var scanJob: Job? = null
    private var scanLoopJob: Job? = null
    private var healthJob: Job? = null

    init {
        viewModelScope.launch {
            startDiscovery()
        }

        viewModelScope.launch {
            discoveryService.peers().collectLatest { devices ->
                _uiState.update { state ->
                    state.copy(
                        squadPeers = devices.map { device ->
                            PeerNodeUi(
                                deviceAddress = device.id.value,
                                callsign = device.callsign.ifBlank { device.id.value },
                                isConnected = device.link != LinkType.STALE,
                                distanceText = formatDistance(estimator.estimate(device.rssi)),
                                signalBars = signalBars(device.rssi),
                                linkText = device.link.name
                            )
                        }
                    )
                }
            }
        }

        healthJob = viewModelScope.launch {
            while (isActive) {
                delay(5000L)
                if (scanLoopJob?.isActive != true) startDiscovery()
                // Recovery guard: restart discovery when it has repeatedly stopped making progress.
                if (discoveryService is DefaultDiscoveryService) {
                    val peers = discoveryService.peers().value
                    if (peers.isEmpty()) {
                        runCatching { discoveryService.start() }
                    }
                }
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
            while (isActive) {
                runScanCycle()
                delay(DISCOVERY_INTERVAL_MS)
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
        viewModelScope.launch { bleConnectionManager.pair(deviceAddress) }
    }

    fun observePeerState(deviceAddress: String) {
        viewModelScope.launch {
            bleConnectionManager.state(deviceAddress).collect { linkState ->
                _uiState.update { state ->
                    state.copy(squadPeers = state.squadPeers.map { peer ->
                        if (peer.deviceAddress == deviceAddress) peer.copy(bleState = linkState, isConnected = linkState == BleLinkState.CONNECTED) else peer
                    })
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
        super.onCleared()
    }
    companion object {
        private const val SINGLE_SCAN_WINDOW_MS = 5000L
        private const val DISCOVERY_INTERVAL_MS = 10000L
    }
}
