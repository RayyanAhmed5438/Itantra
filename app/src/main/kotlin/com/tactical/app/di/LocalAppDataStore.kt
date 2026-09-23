package com.tactical.app.di

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

data class StoredPairedDevice(
    val deviceId: String,
    val callsign: String,
    val lastSeenEpochMs: Long,
    val rssi: Int,
    val linkText: String
)

data class StoredReceivedMessage(
    val senderId: String,
    val senderName: String,
    val text: String,
    val timestampEpochMs: Long,
    val isVoice: Boolean,
    val isCallMode: Boolean = false,
    val isAlert: Boolean = false,
    val severity: String? = null,
    val languageCode: String? = null,
    val locationLatitude: Double? = null,
    val locationLongitude: Double? = null,
    val locationAccuracyMeters: Float? = null,
    // Local device time when this message was actually received.
    // This is deliberately separate from timestampEpochMs, which comes
    // from the sender and can be skewed relative to this device's clock.
    val receivedAtEpochMs: Long = 0L
)

data class StoredSentMessage(
    val senderName: String,
    val text: String,
    val timestampEpochMs: Long,
    val statusText: String,
    val isVoice: Boolean = false,
    val isCallMode: Boolean = false,
    val isAlert: Boolean = false,
    val severity: String? = null,
    val languageCode: String? = null,
    val locationLatitude: Double? = null,
    val locationLongitude: Double? = null,
    val locationAccuracyMeters: Float? = null,
    // Local time used to keep conversation order independent of other
    // devices' clocks.
    val conversationOrderEpochMs: Long = timestampEpochMs
)

/**
 * Small local persistence layer for app-level squad data.
 *
 * BLE pairing/session state remains owned by AndroidBleConnectionManager.
 * This store only remembers displayable peer information and chat history
 * so the UI survives process/app restarts.
 */
@Singleton
class LocalAppDataStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val preferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    private val _receivedMessagesChanged = MutableStateFlow(0)
    val receivedMessagesChanged = _receivedMessagesChanged.asStateFlow()

    @Synchronized
    fun loadSentMessages(): List<StoredSentMessage> {
        val json = preferences.getString(KEY_SENT_MESSAGES, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(json)
            buildList(array.length()) {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    add(
                        StoredSentMessage(
                            senderName = item.optString("senderName", "YOU"),
                            text = item.getString("text"),
                            timestampEpochMs = item.optLong("timestampEpochMs", 0L),
                            statusText = item.optString("statusText", "Sent"),
                            isVoice = item.optBoolean("isVoice", false),
                            isCallMode = item.optBoolean("isCallMode", false),
                            isAlert = item.optBoolean("isAlert", false),
                            severity = item.optString("severity").takeIf { it.isNotBlank() },
                            languageCode = item.optString("languageCode").takeIf { it.isNotBlank() },
                            locationLatitude = if (item.has("locationLatitude") && !item.isNull("locationLatitude")) item.optDouble("locationLatitude") else null,
                            locationLongitude = if (item.has("locationLongitude") && !item.isNull("locationLongitude")) item.optDouble("locationLongitude") else null,
                            locationAccuracyMeters = if (item.has("locationAccuracyMeters") && !item.isNull("locationAccuracyMeters")) item.optDouble("locationAccuracyMeters").toFloat() else null,
                            conversationOrderEpochMs = item.optLong(
                                "conversationOrderEpochMs",
                                item.optLong("timestampEpochMs", 0L)
                            )
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    @Synchronized
    fun saveSentMessage(message: StoredSentMessage) {
        val messages = loadSentMessages()
            .filterNot { existing ->
                messageStorageKey(
                    senderName = existing.senderName,
                    timestampEpochMs = existing.timestampEpochMs,
                    text = existing.text,
                    isAlert = existing.isAlert,
                    isVoice = existing.isVoice
                ) == messageStorageKey(
                    senderName = message.senderName,
                    timestampEpochMs = message.timestampEpochMs,
                    text = message.text,
                    isAlert = message.isAlert,
                    isVoice = message.isVoice
                )
            }
            .toMutableList()

        messages.add(message)

        val array = JSONArray()
        messages.forEach { item ->
            array.put(
                JSONObject().apply {
                    put("senderName", item.senderName)
                    put("text", item.text)
                    put("timestampEpochMs", item.timestampEpochMs)
                    put("statusText", item.statusText)
                    put("isVoice", item.isVoice)
                    put("isCallMode", item.isCallMode)
                    put("isAlert", item.isAlert)
                    put("conversationOrderEpochMs", item.conversationOrderEpochMs)
                    item.severity?.let { put("severity", it) }
                    item.languageCode?.let { put("languageCode", it) }
                    item.locationLatitude?.let { put("locationLatitude", it) }
                    item.locationLongitude?.let { put("locationLongitude", it) }
                    item.locationAccuracyMeters?.let { put("locationAccuracyMeters", it) }
                }
            )
        }

        preferences.edit()
            .putString(KEY_SENT_MESSAGES, array.toString())
            .apply()
    }

    @Synchronized
    fun updateSentMessageStatus(messageKey: String, statusText: String) {
        val messages = loadSentMessages().toMutableList()
        var changed = false

        val updated = messages.map { item ->
            val key = messageStorageKey(
                senderName = item.senderName,
                timestampEpochMs = item.timestampEpochMs,
                text = item.text,
                isAlert = item.isAlert,
                isVoice = item.isVoice
            )

            if (key == messageKey) {
                changed = true
                item.copy(statusText = statusText)
            } else {
                item
            }
        }

        if (!changed) return

        val array = JSONArray()
        updated.forEach { item ->
            array.put(
                JSONObject().apply {
                    put("senderName", item.senderName)
                    put("text", item.text)
                    put("timestampEpochMs", item.timestampEpochMs)
                    put("statusText", item.statusText)
                    put("isVoice", item.isVoice)
                    put("isCallMode", item.isCallMode)
                    put("isAlert", item.isAlert)
                    put("conversationOrderEpochMs", item.conversationOrderEpochMs)
                    item.severity?.let { put("severity", it) }
                    item.languageCode?.let { put("languageCode", it) }
                    item.locationLatitude?.let { put("locationLatitude", it) }
                    item.locationLongitude?.let { put("locationLongitude", it) }
                    item.locationAccuracyMeters?.let { put("locationAccuracyMeters", it) }
                }
            )
        }

        preferences.edit()
            .putString(KEY_SENT_MESSAGES, array.toString())
            .apply()
    }

    @Synchronized
    fun deleteSentMessages(messageKeys: Set<String>) {
        if (messageKeys.isEmpty()) return

        val remaining = loadSentMessages().filterNot { message ->
            messageStorageKey(
                senderName = message.senderName,
                timestampEpochMs = message.timestampEpochMs,
                text = message.text,
                isAlert = message.isAlert,
                isVoice = message.isVoice
            ) in messageKeys
        }

        val array = JSONArray()
        remaining.forEach { item ->
            array.put(
                JSONObject().apply {
                    put("senderName", item.senderName)
                    put("text", item.text)
                    put("timestampEpochMs", item.timestampEpochMs)
                    put("statusText", item.statusText)
                    put("isVoice", item.isVoice)
                    put("isCallMode", item.isCallMode)
                    put("isAlert", item.isAlert)
                    put("conversationOrderEpochMs", item.conversationOrderEpochMs)
                    item.severity?.let { put("severity", it) }
                    item.languageCode?.let { put("languageCode", it) }
                    item.locationLatitude?.let { put("locationLatitude", it) }
                    item.locationLongitude?.let { put("locationLongitude", it) }
                    item.locationAccuracyMeters?.let { put("locationAccuracyMeters", it) }
                }
            )
        }

        preferences.edit()
            .putString(KEY_SENT_MESSAGES, array.toString())
            .apply()
    }

    @Synchronized
    fun loadPairedDevices(): List<StoredPairedDevice> {
        val json = preferences.getString(KEY_PAIRED_DEVICES, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(json)
            buildList(array.length()) {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    add(
                        StoredPairedDevice(
                            deviceId = item.getString("deviceId"),
                            callsign = item.getString("callsign"),
                            lastSeenEpochMs = item.optLong("lastSeenEpochMs", 0L),
                            rssi = item.optInt("rssi", 0),
                            linkText = item.optString("linkText", "PAIRED")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    @Synchronized
    fun savePairedDevice(device: StoredPairedDevice) {
        val existing = loadPairedDevices()
            .associateBy { it.deviceId }
            .toMutableMap()

        existing[device.deviceId] = device

        val array = JSONArray()
        existing.values
            .sortedBy { it.deviceId }
            .forEach { peer ->
                array.put(
                    JSONObject().apply {
                        put("deviceId", peer.deviceId)
                        put("callsign", peer.callsign)
                        put("lastSeenEpochMs", peer.lastSeenEpochMs)
                        put("rssi", peer.rssi)
                        put("linkText", peer.linkText)
                    }
                )
            }

        preferences.edit()
            .putString(KEY_PAIRED_DEVICES, array.toString())
            .apply()
    }

    @Synchronized
    fun removePairedDevice(deviceId: String) {
        val remaining = loadPairedDevices()
            .filterNot { it.deviceId == deviceId }

        val array = JSONArray()
        remaining.forEach { peer ->
            array.put(
                JSONObject().apply {
                    put("deviceId", peer.deviceId)
                    put("callsign", peer.callsign)
                    put("lastSeenEpochMs", peer.lastSeenEpochMs)
                    put("rssi", peer.rssi)
                    put("linkText", peer.linkText)
                }
            )
        }

        preferences.edit()
            .putString(KEY_PAIRED_DEVICES, array.toString())
            .apply()
    }

    @Synchronized
    fun callsignForPeer(deviceId: String): String? =
        loadPairedDevices()
            .firstOrNull { it.deviceId == deviceId }
            ?.callsign

    @Synchronized
    fun loadReceivedMessages(): List<StoredReceivedMessage> {
        val json = preferences.getString(KEY_RECEIVED_MESSAGES, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(json)
            buildList(array.length()) {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    add(
                        StoredReceivedMessage(
                            senderId = item.getString("senderId"),
                            senderName = item.getString("senderName"),
                            text = item.getString("text"),
                            timestampEpochMs = item.optLong("timestampEpochMs", 0L),
                            isVoice = item.optBoolean("isVoice", false),
                            isCallMode = item.optBoolean("isCallMode", false),
                            isAlert = item.optBoolean("isAlert", false),
                            severity = item.optString("severity").takeIf { it.isNotBlank() },
                            languageCode = item.optString("languageCode").takeIf { it.isNotBlank() },
                            locationLatitude = if (item.has("locationLatitude") && !item.isNull("locationLatitude")) item.optDouble("locationLatitude") else null,
                            locationLongitude = if (item.has("locationLongitude") && !item.isNull("locationLongitude")) item.optDouble("locationLongitude") else null,
                            locationAccuracyMeters = if (item.has("locationAccuracyMeters") && !item.isNull("locationAccuracyMeters")) item.optDouble("locationAccuracyMeters").toFloat() else null,
                            receivedAtEpochMs = item.optLong("receivedAtEpochMs", 0L)
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    @Synchronized
    fun saveReceivedMessage(message: StoredReceivedMessage): Boolean {
        val messages = loadReceivedMessages()
            .toMutableList()

        // The foreground service and MainViewModel can both observe the same
        // emergency packet. Treat an identical packet as one message so
        // background reception does not create duplicate chat entries.
        val alreadyStored = messages.any { existing ->
            existing.senderId == message.senderId &&
                existing.timestampEpochMs == message.timestampEpochMs &&
                existing.text == message.text &&
                existing.isAlert == message.isAlert
        }

        if (!alreadyStored) {
            // Do not use the sender's timestamp for unread tracking. Store a
            // local receive time so clock skew between phones cannot keep a
            // message permanently "unread".
            messages.add(
                message.copy(
                    receivedAtEpochMs = if (message.receivedAtEpochMs > 0L) {
                        message.receivedAtEpochMs
                    } else {
                        System.currentTimeMillis()
                    }
                )
            )
        }

        val array = JSONArray()
        messages.forEach { item ->
            array.put(
                JSONObject().apply {
                    put("senderId", item.senderId)
                    put("senderName", item.senderName)
                    put("text", item.text)
                    put("timestampEpochMs", item.timestampEpochMs)
                    put("isVoice", item.isVoice)
                    put("isCallMode", item.isCallMode)
                    put("isAlert", item.isAlert)
                    put("receivedAtEpochMs", item.receivedAtEpochMs)
                    item.severity?.let { put("severity", it) }
                    item.languageCode?.let { put("languageCode", it) }
                    item.locationLatitude?.let { put("locationLatitude", it) }
                    item.locationLongitude?.let { put("locationLongitude", it) }
                    item.locationAccuracyMeters?.let { put("locationAccuracyMeters", it) }
                }
            )
        }

        preferences.edit()
            .putString(KEY_RECEIVED_MESSAGES, array.toString())
            .apply()

        if (!alreadyStored) {
            _receivedMessagesChanged.value += 1
        }

        return !alreadyStored
    }

    @Synchronized
    fun deleteReceivedMessages(messageKeys: Set<String>) {
        if (messageKeys.isEmpty()) return

        val remaining = loadReceivedMessages().filterNot { message ->
            messageStorageKey(
                senderName = message.senderName,
                timestampEpochMs = message.timestampEpochMs,
                text = message.text,
                isAlert = message.isAlert,
                isVoice = message.isVoice
            ) in messageKeys
        }

        val array = JSONArray()
        remaining.forEach { item ->
            array.put(
                JSONObject().apply {
                    put("senderId", item.senderId)
                    put("senderName", item.senderName)
                    put("text", item.text)
                    put("timestampEpochMs", item.timestampEpochMs)
                    put("isVoice", item.isVoice)
                    put("isCallMode", item.isCallMode)
                    put("isAlert", item.isAlert)
                    put("receivedAtEpochMs", item.receivedAtEpochMs)
                    item.severity?.let { put("severity", it) }
                    item.languageCode?.let { put("languageCode", it) }
                    item.locationLatitude?.let { put("locationLatitude", it) }
                    item.locationLongitude?.let { put("locationLongitude", it) }
                    item.locationAccuracyMeters?.let { put("locationAccuracyMeters", it) }
                }
            )
        }

        preferences.edit()
            .putString(KEY_RECEIVED_MESSAGES, array.toString())
            .apply()

        _receivedMessagesChanged.value += 1
    }

    fun messageStorageKey(
        senderName: String,
        timestampEpochMs: Long,
        text: String,
        isAlert: Boolean,
        isVoice: Boolean,
        isCallMode: Boolean = false
    ): String =
        senderName + "|" +
            timestampEpochMs + "|" +
            text + "|" +
            isAlert + "|" +
            isVoice + "|" +
            isCallMode

    @Synchronized
    fun unreadMessageCount(): Int {
        val lastRead = if (preferences.contains(KEY_MESSAGES_LAST_READ)) {
            preferences.getLong(KEY_MESSAGES_LAST_READ, 0L)
        } else {
            val now = System.currentTimeMillis()
            preferences.edit().putLong(KEY_MESSAGES_LAST_READ, now).apply()
            now
        }
        // Compare local receive time, not the sender's packet timestamp.
        // Sender clocks may be ahead/behind this device.
        return loadReceivedMessages().count {
            it.receivedAtEpochMs > lastRead
        }
    }

    @Synchronized
    fun markMessagesRead() {
        preferences.edit()
            .putLong(KEY_MESSAGES_LAST_READ, System.currentTimeMillis())
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "tactical_local_app_data"
        private const val KEY_PAIRED_DEVICES = "paired_devices"
        private const val KEY_RECEIVED_MESSAGES = "received_messages"
        private const val KEY_SENT_MESSAGES = "sent_messages"
        private const val KEY_MESSAGES_LAST_READ = "messages_last_read_epoch_ms"
    }
}
