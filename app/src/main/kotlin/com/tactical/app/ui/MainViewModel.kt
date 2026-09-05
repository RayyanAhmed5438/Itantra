package com.tactical.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.tactical.engine.discovery.proximity.RssiProximityEstimator
import com.tactical.engine.discovery.service.DiscoveryService
import com.tactical.domain.identity.LinkType
import kotlinx.coroutines.flow.collectLatest

enum class PttState {
    IDLE, RECORDING, PROCESSING, SENDING, SENT
}

data class PeerNodeUi(
    val callsign: String,
    val isConnected: Boolean,
    val distanceText: String,
    val signalBars: Int
)

data class ChatMessageUi(
    val sender: String,
    val text: String,
    val timestampText: String,
    val statusText: String,
    val isAlert: Boolean = false,
    val isVoice: Boolean = false
)

data class NetworkMetrics(
    val rttMs: Int = 42,
    val hopCount: Int = 2,
    val packetLossPercent: Int = 2,
    val transportName: String = "Wi-Fi Direct"
)

data class EmergencyAlertData(
    val sender: String = "COMMANDER",
    val timestampText: String = "10:32 AM",
    val hindiText: String = "कृपया तुरंत सुरक्षित स्थान पर जाएं!",
    val englishText: String = "Please move to a safe location immediately.",
    val durationSeconds: Int = 4
)

data class MainUiState(
    val pttState: PttState = PttState.IDLE,
    val recordingSeconds: Int = 0,
    val sendingSeconds: Int = 0,
    val selectedLanguage: String = "हिन्दी",
    val isWalkieTalkieOn: Boolean = true,
    val isVoxOn: Boolean = true,
    val isVoxListeningOverlayVisible: Boolean = false,
    val isWalkieTalkieScreenVisible: Boolean = false,
    val emergencyHoldProgress: Float = 0f,
    val activeEmergencyAlert: EmergencyAlertData? = null,
    val squadPeers: List<PeerNodeUi> = emptyList(),
    val messages: List<ChatMessageUi> = listOf(
        ChatMessageUi("YOU", "यह मदद चाहिए", "10:32 AM", "Delivered"),
        ChatMessageUi("TEAM-02", "सब लोग सुरक्षित हैं", "10:31 AM", "Played", isVoice = true),
        ChatMessageUi("COMMANDER", "सुरक्षित स्थान पर जाएं", "10:27 AM", "ALERT", isAlert = true),
        ChatMessageUi("TEAM-03", "लोकेशन भेज रहा हूं", "10:25 AM", "Delivered")
    ),
    val networkMetrics: NetworkMetrics = NetworkMetrics()
)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val discoveryService: DiscoveryService
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var recordingJob: Job? = null
    private var holdPanicJob: Job? = null

    fun selectLanguage(lang: String) {
        _uiState.update { it.copy(selectedLanguage = lang) }
    }

    fun toggleWalkieTalkie() {
        _uiState.update { it.copy(isWalkieTalkieOn = !it.isWalkieTalkieOn) }
    }

    fun toggleVox() {
        _uiState.update { it.copy(isVoxOn = !it.isVoxOn) }
    }

    fun showWalkieTalkieScreen(show: Boolean) {
        _uiState.update { it.copy(isWalkieTalkieScreenVisible = show) }
    }

    fun showVoxListeningOverlay(show: Boolean) {
        _uiState.update { it.copy(isVoxListeningOverlayVisible = show) }
    }

    fun startPtt() {
        if (_uiState.value.pttState != PttState.IDLE) return
        _uiState.update { it.copy(pttState = PttState.RECORDING, recordingSeconds = 0) }

        recordingJob = viewModelScope.launch {
            var secs = 0
            while (_uiState.value.pttState == PttState.RECORDING) {
                delay(1000)
                secs++
                _uiState.update { it.copy(recordingSeconds = secs) }
                if (secs >= 4) {
                    stopPttAndTransmit()
                    break
                }
            }
        }
    }

    fun stopPttAndTransmit() {
        recordingJob?.cancel()
        recordingJob = null

        viewModelScope.launch {
            _uiState.update { it.copy(pttState = PttState.PROCESSING) }
            delay(1200)

            _uiState.update { it.copy(pttState = PttState.SENDING, sendingSeconds = 1) }
            delay(1000)

            _uiState.update { it.copy(pttState = PttState.SENT) }
            
            // Append transcribed voice message to chat log
            val newMsg = ChatMessageUi(
                sender = "YOU",
                text = "यह मदद चाहिए",
                timestampText = "Just now",
                statusText = "Sent successfully",
                isVoice = true
            )
            _uiState.update { it.copy(messages = listOf(newMsg) + it.messages) }

            delay(1500)
            _uiState.update { it.copy(pttState = PttState.IDLE) }
        }
    }

    fun startEmergencyHold() {
        holdPanicJob?.cancel()
        holdPanicJob = viewModelScope.launch {
            var steps = 0
            val maxSteps = 20
            while (steps < maxSteps) {
                delay(100)
                steps++
                _uiState.update { it.copy(emergencyHoldProgress = steps.toFloat() / maxSteps) }
            }
            // Trigger emergency broadcast & show alert popup
            triggerEmergencyAlert()
        }
    }

    fun cancelEmergencyHold() {
        holdPanicJob?.cancel()
        holdPanicJob = null
        _uiState.update { it.copy(emergencyHoldProgress = 0f) }
    }

    fun triggerEmergencyAlert() {
        _uiState.update {
            it.copy(
                emergencyHoldProgress = 0f,
                activeEmergencyAlert = EmergencyAlertData()
            )
        }
    }

    fun dismissEmergencyAlert() {
        _uiState.update { it.copy(activeEmergencyAlert = null) }
    }

    fun sendTextMessage(text: String) {
        if (text.isBlank()) return
        val newMsg = ChatMessageUi(
            sender = "YOU",
            text = text,
            timestampText = "Just now",
            statusText = "Delivered"
        )
        _uiState.update { it.copy(messages = listOf(newMsg) + it.messages) }
    }

    private val proximityEstimator = RssiProximityEstimator()

    init {
        viewModelScope.launch {
            discoveryService.peers().collectLatest { devices ->
                _uiState.update { state ->
                    state.copy(
                        squadPeers = devices.map { device ->
                            val distance = proximityEstimator.estimate(device.rssi)

                            PeerNodeUi(
                                callsign = device.callsign.ifBlank {
                                    device.id.value
                                },
                                isConnected = device.link != LinkType.STALE,
                                distanceText = formatDistance(distance),
                                signalBars = signalBars(device.rssi)
                            )
                        }
                    )
                }
            }
        }
    }

    private fun formatDistance(distance: Double): String {
        if (distance < 0) return "Unknown"

        return if (distance < 100) {
            "${distance.toInt()} m"
        } else {
            "${(distance / 1000.0).formatOneDecimal()} km"
        }
    }

    private fun signalBars(rssi: Int): Int {
        return when {
            rssi >= -55 -> 4
            rssi >= -65 -> 3
            rssi >= -75 -> 2
            else -> 1
        }
    }

    private fun Double.formatOneDecimal(): String =
        String.format(java.util.Locale.US, "%.1f", this)

    override fun onCleared() {
        viewModelScope.launch {
            discoveryService.stop()
        }
        super.onCleared()
    }
}
