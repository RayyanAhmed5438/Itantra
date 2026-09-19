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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Locale
import javax.inject.Inject

data class PeerNodeUi(
    val callsign: String,
    val isConnected: Boolean,
    val distanceText: String,
    val signalBars: Int,
    val linkText: String
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
    private val identityStore: DeviceIdentityStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()
    private val estimator = RssiProximityEstimator()
    private var scanJob: Job? = null

    init {
        viewModelScope.launch {
            discoveryService.peers().collectLatest { devices ->
                _uiState.update { state ->
                    state.copy(
                        squadPeers = devices.map { device ->
                            PeerNodeUi(
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
     * Starts this installation's beacon as well as the active scan.
     * This makes the Scan button self-contained: it no longer depends on
     * the foreground service having been started successfully beforehand.
     *
     * Only iTantra beacons are accepted by CompositeBeaconScanner, so
     * ordinary nearby Bluetooth devices are never added to this list.
     */
    fun startDiscovery() {
        if (scanJob?.isActive == true) return

        scanJob = viewModelScope.launch {
            try {
                discoveryService.start()

                (discoveryService as? DefaultDiscoveryService)?.startDiscovery()

                _uiState.update { it.copy(isScanning = true) }

                delay(8000L)

                (discoveryService as? DefaultDiscoveryService)?.stopDiscovery()
                _uiState.update { it.copy(isScanning = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isScanning = false) }
            } finally {
                scanJob = null
            }
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
        viewModelScope.launch { discoveryService.stop() }
        scanJob?.cancel()
        super.onCleared()
    }
}
