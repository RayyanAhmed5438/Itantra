package com.tactical.app.ui.i18n

import androidx.compose.runtime.staticCompositionLocalOf

enum class UiTextKey {
    BACK, SETTINGS, BLUETOOTH_MIC_PERMISSIONS, BLUETOOTH_OFF,
    SQUAD_REQUEST, WANTS_TO_ADD, SENDING, APPROVE, REJECT,
    NETWORK_READY, DEVICE, DEVICES, SQUAD, MESSAGES,
    AVAILABLE_DEVICES, SCANNING, SCAN, SEND_ALERT, EMERGENCY_SOS, SOS,
    ACTIVE, HOLD_TWO_SECONDS, LOOKING_NEARBY, NO_DEVICES, TAP_SCAN,
    CONNECTED, CONNECTING, AVAILABLE, ADD_TO_SQUAD,
    STARTING, RECORDING, PUSH_TO_TALK, NO_CONNECTION, RELEASE_TO_SEND,
    TRANSCRIBING_SENDING, HOLD_TO_RECORD, CONNECT_SQUAD_MEMBER,
    PTT, CALL, CALL_MODE_VOICE, LIVE_PTT_TRANSCRIPTION, LAST_TRANSMISSION,
    LISTENING_CONTINUOUSLY, CALL_READY, LIVE, SPEAK_NORMALLY,
    LANGUAGE, LOADING, SELECT_LANGUAGE, INDIAN_LANGUAGES, OTHER, CONFIRM,
    NO_CONNECTED_DEVICES, NO_SQUAD_MEMBERS, ALL_SQUAD_CONNECTED,
    CONNECTED_DOT, IN_SQUAD_DOT, SIGNAL, REMOVE_FROM_SQUAD, UNKNOWN,
    SENT, SENDING_DOT,
    SELECTED, CLEAR, SELECT_ALL, DELETE_SELECTED, CANCEL_SELECTION, SELECT,
    CHAT_PPT_MODE, CALL_MODE, NO_MESSAGES, YOU, WRITE_MESSAGE, SEND,
    DELETE_MESSAGES, CANNOT_UNDO, DELETE, CANCEL, JUST_NOW, MIN_AGO,
    EMERGENCY_TAP_DETAILS,
    USERNAME_CALLSIGN, NAME_SHOWN, USERNAME, MAX_CALLSIGN, SAVE_USERNAME,
    UI_LANGUAGE, INCOMING_VOICE_PLAYBACK, ONE_BY_ONE, ONE_BY_ONE_DESC,
    OVERLAPPING_VOICES, OVERLAPPING_DESC,
    EMERGENCY, EMERGENCY_ALERT, CLOSE, FROM, SEVERITY, LANG, MESSAGE,
    LOCATION, LOCATION_ATTACHED, ACCURACY, NO_LOCATION,
    RECORDING_STARTED, LISTENING, READY_TO_SEND, SPEAK_EMERGENCY,
    ALERT_BROADCAST, TRANSCRIPT_READY
}

class UiStrings private constructor(
    private val languageCode: String
) {
    private val english = mapOf(
        UiTextKey.BACK to "Back",
        UiTextKey.SETTINGS to "Settings",
        UiTextKey.BLUETOOTH_MIC_PERMISSIONS to "Bluetooth / microphone permissions are required.",
        UiTextKey.BLUETOOTH_OFF to "Bluetooth is off. Turn it on.",
        UiTextKey.SQUAD_REQUEST to "SQUAD REQUEST",
        UiTextKey.WANTS_TO_ADD to "wants to add you to their squad.",
        UiTextKey.SENDING to "SENDING...",
        UiTextKey.APPROVE to "APPROVE",
        UiTextKey.REJECT to "REJECT",
        UiTextKey.NETWORK_READY to "NETWORK READY",
        UiTextKey.DEVICE to "Device",
        UiTextKey.DEVICES to "DEVICES",
        UiTextKey.SQUAD to "SQUAD",
        UiTextKey.MESSAGES to "MESSAGES",
        UiTextKey.AVAILABLE_DEVICES to "AVAILABLE DEVICES",
        UiTextKey.SCANNING to "SCANNING",
        UiTextKey.SCAN to "SCAN",
        UiTextKey.SEND_ALERT to "Send alert with your location and critical message",
        UiTextKey.EMERGENCY_SOS to "Emergency SOS",
        UiTextKey.SOS to "SOS",
        UiTextKey.ACTIVE to "ACTIVE",
        UiTextKey.HOLD_TWO_SECONDS to "Hold for 2 seconds",
        UiTextKey.LOOKING_NEARBY to "Looking for nearby iTantra devices",
        UiTextKey.NO_DEVICES to "No devices found yet",
        UiTextKey.TAP_SCAN to "Tap SCAN to search for nearby iTantra devices.",
        UiTextKey.CONNECTED to "CONNECTED",
        UiTextKey.CONNECTING to "CONNECTING",
        UiTextKey.AVAILABLE to "AVAILABLE",
        UiTextKey.ADD_TO_SQUAD to "ADD TO SQUAD",
        UiTextKey.STARTING to "STARTING",
        UiTextKey.RECORDING to "RECORDING",
        UiTextKey.PUSH_TO_TALK to "PUSH TO TALK",
        UiTextKey.NO_CONNECTION to "NO CONNECTION",
        UiTextKey.RELEASE_TO_SEND to "Release to send • swipe right to cancel",
        UiTextKey.TRANSCRIBING_SENDING to "Transcribing and sending…",
        UiTextKey.HOLD_TO_RECORD to "Hold to record • release to send • swipe right to cancel",
        UiTextKey.CONNECT_SQUAD_MEMBER to "Connect to a squad member to enable PTT",
        UiTextKey.PTT to "PTT",
        UiTextKey.CALL to "CALL",
        UiTextKey.CALL_MODE_VOICE to "CALL MODE • VOICE TRANSMISSIONS",
        UiTextKey.LIVE_PTT_TRANSCRIPTION to "LIVE PTT TRANSCRIPTION",
        UiTextKey.LAST_TRANSMISSION to "LAST TRANSMISSION",
        UiTextKey.LISTENING_CONTINUOUSLY to "Listening continuously",
        UiTextKey.CALL_READY to "Continuous voice mode ready",
        UiTextKey.LIVE to "LIVE",
        UiTextKey.SPEAK_NORMALLY to "Speak normally. Each finalized sentence is sent automatically.",
        UiTextKey.LANGUAGE to "LANGUAGE",
        UiTextKey.LOADING to "Loading…",
        UiTextKey.SELECT_LANGUAGE to "Select Language",
        UiTextKey.INDIAN_LANGUAGES to "Indian languages",
        UiTextKey.OTHER to "Other",
        UiTextKey.CONFIRM to "CONFIRM",
        UiTextKey.NO_CONNECTED_DEVICES to "No connected devices",
        UiTextKey.NO_SQUAD_MEMBERS to "No squad members",
        UiTextKey.ALL_SQUAD_CONNECTED to "All squad members are connected",
        UiTextKey.CONNECTED_DOT to "Connected • ",
        UiTextKey.IN_SQUAD_DOT to "In squad • ",
        UiTextKey.SIGNAL to "Signal",
        UiTextKey.REMOVE_FROM_SQUAD to "REMOVE FROM SQUAD",
        UiTextKey.UNKNOWN to "Unknown",
        UiTextKey.SENT to "Sent",
        UiTextKey.SENDING_DOT to "Sending…",
        UiTextKey.SELECTED to "SELECTED",
        UiTextKey.CLEAR to "CLEAR",
        UiTextKey.SELECT_ALL to "SELECT ALL",
        UiTextKey.DELETE_SELECTED to "Delete selected messages",
        UiTextKey.CANCEL_SELECTION to "Cancel selection",
        UiTextKey.SELECT to "SELECT",
        UiTextKey.CHAT_PPT_MODE to "CHAT / PTT MODE",
        UiTextKey.CALL_MODE to "CALL MODE",
        UiTextKey.NO_MESSAGES to "No messages",
        UiTextKey.YOU to "YOU",
        UiTextKey.WRITE_MESSAGE to "Write message…",
        UiTextKey.SEND to "Send",
        UiTextKey.DELETE_MESSAGES to "Delete messages?",
        UiTextKey.CANNOT_UNDO to "This cannot be undone.",
        UiTextKey.DELETE to "DELETE",
        UiTextKey.CANCEL to "CANCEL",
        UiTextKey.JUST_NOW to "Just now",
        UiTextKey.MIN_AGO to "min ago",
        UiTextKey.EMERGENCY_TAP_DETAILS to "EMERGENCY • TAP FOR DETAILS",
        UiTextKey.USERNAME_CALLSIGN to "USERNAME / CALLSIGN",
        UiTextKey.NAME_SHOWN to "This name is shown to other squad members.",
        UiTextKey.USERNAME to "Username",
        UiTextKey.MAX_CALLSIGN to "Maximum 5 UTF-8 bytes for the existing BLE callsign field.",
        UiTextKey.SAVE_USERNAME to "SAVE USERNAME",
        UiTextKey.UI_LANGUAGE to "UI LANGUAGE",
        UiTextKey.INCOMING_VOICE_PLAYBACK to "INCOMING VOICE PLAYBACK",
        UiTextKey.ONE_BY_ONE to "ONE BY ONE",
        UiTextKey.ONE_BY_ONE_DESC to "Play all received voice messages one by one, regardless of which device sent them.",
        UiTextKey.OVERLAPPING_VOICES to "OVERLAPPING VOICES",
        UiTextKey.OVERLAPPING_DESC to "Voices from different devices may play at the same time. Messages from the same device never overlap.",
        UiTextKey.EMERGENCY to "Emergency",
        UiTextKey.EMERGENCY_ALERT to "EMERGENCY ALERT",
        UiTextKey.CLOSE to "Close",
        UiTextKey.FROM to "FROM: ",
        UiTextKey.SEVERITY to "SEVERITY: ",
        UiTextKey.LANG to "LANG: ",
        UiTextKey.MESSAGE to "MESSAGE",
        UiTextKey.LOCATION to "LOCATION",
        UiTextKey.LOCATION_ATTACHED to "Location attached",
        UiTextKey.ACCURACY to "Accuracy: ",
        UiTextKey.NO_LOCATION to "No location attached to this alert.",
        UiTextKey.RECORDING_STARTED to "Recording started automatically.",
        UiTextKey.LISTENING to "LISTENING…",
        UiTextKey.READY_TO_SEND to "READY TO SEND",
        UiTextKey.SPEAK_EMERGENCY to "Speak your emergency message.",
        UiTextKey.ALERT_BROADCAST to "The alert will be broadcast to nearby connected devices.",
        UiTextKey.TRANSCRIPT_READY to "Transcript ready"
    )

    private val hindi = mapOf(
        UiTextKey.BACK to "वापस",
        UiTextKey.SETTINGS to "सेटिंग्स",
        UiTextKey.BLUETOOTH_MIC_PERMISSIONS to "ब्लूटूथ और माइक्रोफ़ोन की अनुमति ज़रूरी है।",
        UiTextKey.BLUETOOTH_OFF to "ब्लूटूथ बंद है। इसे चालू करें।",
        UiTextKey.SQUAD_REQUEST to "स्क्वाड अनुरोध",
        UiTextKey.WANTS_TO_ADD to "आपको अपने स्क्वाड में जोड़ना चाहता है।",
        UiTextKey.SENDING to "भेजा जा रहा है…",
        UiTextKey.APPROVE to "स्वीकार करें",
        UiTextKey.REJECT to "अस्वीकार करें",
        UiTextKey.NETWORK_READY to "नेटवर्क तैयार",
        UiTextKey.DEVICE to "डिवाइस",
        UiTextKey.DEVICES to "डिवाइस",
        UiTextKey.SQUAD to "स्क्वाड",
        UiTextKey.MESSAGES to "संदेश",
        UiTextKey.AVAILABLE_DEVICES to "उपलब्ध डिवाइस",
        UiTextKey.SCANNING to "स्कैन हो रहा है",
        UiTextKey.SCAN to "स्कैन",
        UiTextKey.SEND_ALERT to "अपना स्थान और ज़रूरी संदेश भेजें",
        UiTextKey.EMERGENCY_SOS to "आपातकालीन SOS",
        UiTextKey.SOS to "SOS",
        UiTextKey.ACTIVE to "सक्रिय",
        UiTextKey.HOLD_TWO_SECONDS to "2 सेकंड दबाकर रखें",
        UiTextKey.LOOKING_NEARBY to "पास के iTantra डिवाइस खोजे जा रहे हैं",
        UiTextKey.NO_DEVICES to "अभी कोई डिवाइस नहीं मिला",
        UiTextKey.TAP_SCAN to "पास के iTantra डिवाइस खोजने के लिए स्कैन दबाएँ।",
        UiTextKey.CONNECTED to "कनेक्टेड",
        UiTextKey.CONNECTING to "कनेक्ट हो रहा है",
        UiTextKey.AVAILABLE to "उपलब्ध",
        UiTextKey.ADD_TO_SQUAD to "स्क्वाड में जोड़ें",
        UiTextKey.STARTING to "शुरू हो रहा है",
        UiTextKey.RECORDING to "रिकॉर्डिंग",
        UiTextKey.PUSH_TO_TALK to "बोलने के लिए दबाकर रखें",
        UiTextKey.NO_CONNECTION to "कोई कनेक्शन नहीं",
        UiTextKey.RELEASE_TO_SEND to "भेजने के लिए छोड़ें • रद्द करने के लिए दाईं ओर स्वाइप करें",
        UiTextKey.TRANSCRIBING_SENDING to "ट्रांसक्रिप्शन और भेजने की प्रक्रिया चल रही है…",
        UiTextKey.HOLD_TO_RECORD to "रिकॉर्ड करने के लिए दबाकर रखें • भेजने के लिए छोड़ें • रद्द करने के लिए दाईं ओर स्वाइप करें",
        UiTextKey.CONNECT_SQUAD_MEMBER to "PTT चालू करने के लिए किसी स्क्वाड सदस्य से कनेक्ट करें",
        UiTextKey.PTT to "PTT",
        UiTextKey.CALL to "कॉल",
        UiTextKey.CALL_MODE_VOICE to "कॉल मोड • वॉइस ट्रांसमिशन",
        UiTextKey.LIVE_PTT_TRANSCRIPTION to "लाइव PTT ट्रांसक्रिप्शन",
        UiTextKey.LAST_TRANSMISSION to "अंतिम ट्रांसमिशन",
        UiTextKey.LISTENING_CONTINUOUSLY to "लगातार सुन रहा है",
        UiTextKey.CALL_READY to "कॉन्टिन्युअस वॉइस मोड तैयार है",
        UiTextKey.LIVE to "लाइव",
        UiTextKey.SPEAK_NORMALLY to "सामान्य रूप से बोलें। हर पूरा वाक्य अपने-आप भेजा जाएगा।",
        UiTextKey.LANGUAGE to "भाषा",
        UiTextKey.LOADING to "लोड हो रहा है…",
        UiTextKey.SELECT_LANGUAGE to "भाषा चुनें",
        UiTextKey.INDIAN_LANGUAGES to "भारतीय भाषाएँ",
        UiTextKey.OTHER to "अन्य",
        UiTextKey.CONFIRM to "पुष्टि करें",
        UiTextKey.NO_CONNECTED_DEVICES to "कोई कनेक्टेड डिवाइस नहीं",
        UiTextKey.NO_SQUAD_MEMBERS to "स्क्वाड में कोई सदस्य नहीं",
        UiTextKey.ALL_SQUAD_CONNECTED to "सभी स्क्वाड सदस्य कनेक्टेड हैं",
        UiTextKey.CONNECTED_DOT to "कनेक्टेड • ",
        UiTextKey.IN_SQUAD_DOT to "स्क्वाड में • ",
        UiTextKey.SIGNAL to "सिग्नल",
        UiTextKey.REMOVE_FROM_SQUAD to "स्क्वाड से हटाएँ",
        UiTextKey.UNKNOWN to "अज्ञात",
        UiTextKey.SENT to "भेजा गया",
        UiTextKey.SENDING_DOT to "भेजा जा रहा है…",
        UiTextKey.SELECTED to "चयनित",
        UiTextKey.CLEAR to "साफ़ करें",
        UiTextKey.SELECT_ALL to "सभी चुनें",
        UiTextKey.DELETE_SELECTED to "चयनित संदेश हटाएँ",
        UiTextKey.CANCEL_SELECTION to "चयन रद्द करें",
        UiTextKey.SELECT to "चुनें",
        UiTextKey.CHAT_PPT_MODE to "चैट / PTT मोड",
        UiTextKey.CALL_MODE to "कॉल मोड",
        UiTextKey.NO_MESSAGES to "कोई संदेश नहीं",
        UiTextKey.YOU to "आप",
        UiTextKey.WRITE_MESSAGE to "संदेश लिखें…",
        UiTextKey.SEND to "भेजें",
        UiTextKey.DELETE_MESSAGES to "संदेश हटाएँ?",
        UiTextKey.CANNOT_UNDO to "इसे हटाने के बाद वापस नहीं लाया जा सकता।",
        UiTextKey.DELETE to "हटाएँ",
        UiTextKey.CANCEL to "रद्द करें",
        UiTextKey.JUST_NOW to "अभी-अभी",
        UiTextKey.MIN_AGO to "मिनट पहले",
        UiTextKey.EMERGENCY_TAP_DETAILS to "आपातकाल • विवरण के लिए टैप करें",
        UiTextKey.USERNAME_CALLSIGN to "उपयोगकर्ता नाम / कॉलसाइन",
        UiTextKey.NAME_SHOWN to "यह नाम अन्य स्क्वाड सदस्यों को दिखाई देगा।",
        UiTextKey.USERNAME to "उपयोगकर्ता नाम",
        UiTextKey.MAX_CALLSIGN to "मौजूदा BLE कॉलसाइन फ़ील्ड में अधिकतम 5 UTF-8 बाइट।",
        UiTextKey.SAVE_USERNAME to "उपयोगकर्ता नाम सेव करें",
        UiTextKey.UI_LANGUAGE to "इंटरफ़ेस भाषा",
        UiTextKey.INCOMING_VOICE_PLAYBACK to "आने वाली वॉइस प्लेबैक",
        UiTextKey.ONE_BY_ONE to "एक-एक करके",
        UiTextKey.ONE_BY_ONE_DESC to "सभी प्राप्त वॉइस संदेश क्रम से चलाएँ, चाहे वे किसी भी डिवाइस से आए हों।",
        UiTextKey.OVERLAPPING_VOICES to "एक साथ वॉइस",
        UiTextKey.OVERLAPPING_DESC to "अलग-अलग डिवाइस की आवाज़ें एक साथ चल सकती हैं। एक ही डिवाइस की आवाज़ें कभी ओवरलैप नहीं होंगी।",
        UiTextKey.EMERGENCY to "आपातकाल",
        UiTextKey.EMERGENCY_ALERT to "आपातकालीन अलर्ट",
        UiTextKey.CLOSE to "बंद करें",
        UiTextKey.FROM to "प्रेषक: ",
        UiTextKey.SEVERITY to "गंभीरता: ",
        UiTextKey.LANG to "भाषा: ",
        UiTextKey.MESSAGE to "संदेश",
        UiTextKey.LOCATION to "स्थान",
        UiTextKey.LOCATION_ATTACHED to "स्थान संलग्न है",
        UiTextKey.ACCURACY to "सटीकता: ",
        UiTextKey.NO_LOCATION to "इस अलर्ट के साथ कोई स्थान संलग्न नहीं है।",
        UiTextKey.RECORDING_STARTED to "रिकॉर्डिंग अपने-आप शुरू हुई।",
        UiTextKey.LISTENING to "सुन रहा है…",
        UiTextKey.READY_TO_SEND to "भेजने के लिए तैयार",
        UiTextKey.SPEAK_EMERGENCY to "अपना आपातकालीन संदेश बोलें।",
        UiTextKey.ALERT_BROADCAST to "यह अलर्ट पास के कनेक्टेड डिवाइसों पर प्रसारित किया जाएगा।",
        UiTextKey.TRANSCRIPT_READY to "ट्रांसक्रिप्ट तैयार है"
    )

    private val values: Map<UiTextKey, String>
        get() = if (languageCode == "hi") hindi else english

    fun text(key: UiTextKey): String = values[key] ?: english[key].orEmpty()

    fun selected(count: Int): String =
        if (languageCode == "hi") "$count चयनित" else "$count SELECTED"

    fun connectedCount(count: Int): String =
        if (languageCode == "hi") "$count कनेक्टेड डिवाइस" else "$count CONNECTED DEVICES"

    fun offlineMembers(count: Int): String =
        if (languageCode == "hi") "ऑफ़लाइन सदस्य ($count)" else "OFFLINE MEMBERS ($count)"

    fun live(value: String): String =
        if (languageCode == "hi") "लाइव: $value" else "LIVE: $value"

    fun minutesAgo(minutes: Long): String =
        if (languageCode == "hi") "$minutes मिनट पहले" else "$minutes min ago"

    fun deleteConfirmation(count: Int): String =
        if (languageCode == "hi") {
            if (count == 1) "इस 1 संदेश को हटाएँ? इसे वापस नहीं लाया जा सकता।"
            else "इन $count संदेशों को हटाएँ? इन्हें वापस नहीं लाया जा सकता।"
        } else {
            "Delete $count selected message" + if (count == 1) "" else "s" +
                "? This cannot be undone."
        }

    fun squadRequestTitle(index: Int, total: Int): String =
        if (languageCode == "hi") {
            if (total > 1) "स्क्वाड अनुरोध $index/$total" else "स्क्वाड अनुरोध"
        } else {
            if (total > 1) "SQUAD REQUEST $index/$total" else "SQUAD REQUEST"
        }

    fun formatFrom(callsign: String): String =
        if (languageCode == "hi") {
            "$callsign ने आपको अपने स्क्वाड में जोड़ने का अनुरोध भेजा है।"
        } else {
            "$callsign ${text(UiTextKey.WANTS_TO_ADD)}"
        }

    companion object {
        fun forCode(code: String): UiStrings =
            UiStrings(if (code == "hi") "hi" else "en")
    }
}

val LocalUiStrings = staticCompositionLocalOf { UiStrings.forCode("en") }
