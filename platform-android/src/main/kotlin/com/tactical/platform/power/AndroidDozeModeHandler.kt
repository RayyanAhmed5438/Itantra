package com.tactical.platform.power

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.core.net.toUri
import com.tactical.platform.api.power.DozeModeHandler

/**
 * Implements DozeModeHandler by launching the
 * ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS system dialog. Per the
 * interface's kdoc, there's no synchronous result — the caller finds out
 * the user's choice later some other way (isIgnoringBatteryOptimizations()
 * isn't part of this interface as specified, so it isn't exposed here).
 *
 * FLAG_ACTIVITY_NEW_TASK is set because this class is constructed with a
 * plain Context — ASSUMPTION FLAGGED: if the DI graph actually injects an
 * Application context here (likely, for an app-scoped singleton), this
 * flag is required for startActivity() to work at all; if it instead
 * injects an Activity context, the flag is harmless but redundant. Worth
 * checking which once the DI module exists.
 */
class AndroidDozeModeHandler(private val context: Context) : DozeModeHandler {

    override suspend fun requestIgnoreBatteryOptimizations() {
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        if (powerManager.isIgnoringBatteryOptimizations(context.packageName)) return

        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = "package:${context.packageName}".toUri()
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}