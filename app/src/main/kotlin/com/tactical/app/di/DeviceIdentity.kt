package com.tactical.app.di

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class LocalDeviceIdValue

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class LocalCallsign

/**
 * Generates a stable, per-install-unique device identity on first launch
 * and persists it — fixes the bug where every device previously shared
 * the literal hardcoded id "SENTINEL-COMMANDER", which broke
 * FloodMeshRouter's self-origin loop check across real, distinct devices.
 *
 * Callsign is separate from the id (matches PeriodicBeaconEmitter's two
 * distinct constructor params) and currently defaults from the id's
 * suffix — there's no settings screen yet to let the user pick their own
 * callsign, which the mocked MainViewModel's "COMMANDER"/"TEAM-02" naming
 * implies should eventually be user-editable.
 */
@Singleton
class DeviceIdentityStore @Inject constructor(@ApplicationContext context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val deviceIdValue: String by lazy {
        prefs.getString(KEY_DEVICE_ID, null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString(KEY_DEVICE_ID, it).apply()
        }
    }

    val callsign: String by lazy {
        prefs.getString(KEY_CALLSIGN, null) ?: run {
            val default = "OP-${deviceIdValue.takeLast(4).uppercase()}"
            prefs.edit().putString(KEY_CALLSIGN, default).apply()
            default
        }
    }

    companion object {
        private const val PREFS_NAME = "tactical_device_identity"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_CALLSIGN = "callsign"
    }
}