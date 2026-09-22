package com.tactical.app.di

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
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
    val isAlert: Boolean = false,
    val severity: String? = null,
    val languageCode: String? = null,
    val locationLatitude: Double? = null,
    val locationLongitude: Double? = null,
    val locationAccuracyMeters: Float? = null
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
                            isAlert = item.optBoolean("isAlert", false),
                            severity = item.optString("severity").takeIf { it.isNotBlank() },
                            languageCode = item.optString("languageCode").takeIf { it.isNotBlank() },
                            locationLatitude = if (item.has("locationLatitude") && !item.isNull("locationLatitude")) item.optDouble("locationLatitude") else null,
                            locationLongitude = if (item.has("locationLongitude") && !item.isNull("locationLongitude")) item.optDouble("locationLongitude") else null,
                            locationAccuracyMeters = if (item.has("locationAccuracyMeters") && !item.isNull("locationAccuracyMeters")) item.optDouble("locationAccuracyMeters").toFloat() else null
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    @Synchronized
    fun saveReceivedMessage(message: StoredReceivedMessage) {
        val messages = loadReceivedMessages()
            .toMutableList()
            .apply { add(message) }

        val array = JSONArray()
        messages.forEach { item ->
            array.put(
                JSONObject().apply {
                    put("senderId", item.senderId)
                    put("senderName", item.senderName)
                    put("text", item.text)
                    put("timestampEpochMs", item.timestampEpochMs)
                    put("isVoice", item.isVoice)
                }
            )
        }

        preferences.edit()
            .putString(KEY_RECEIVED_MESSAGES, array.toString())
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "tactical_local_app_data"
        private const val KEY_PAIRED_DEVICES = "paired_devices"
        private const val KEY_RECEIVED_MESSAGES = "received_messages"
    }
}
