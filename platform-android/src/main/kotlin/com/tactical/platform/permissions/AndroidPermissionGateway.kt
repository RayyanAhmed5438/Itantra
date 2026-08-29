package com.tactical.platform.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.ActivityResultLauncher
import com.tactical.platform.api.permissions.Permission
import com.tactical.platform.api.permissions.PermissionGateway
import kotlinx.coroutines.CompletableDeferred


/**
 * Implements PermissionGateway wrapping the Activity Result API.
 *
 * ASSUMPTION FLAGGED: PermissionGateway's lifetime/ownership isn't
 * specified in architecture.md, but an ActivityResultLauncher<String> can
 * only be registered by an Activity/Fragment before it reaches STARTED —
 * it can't be created by an app-scoped singleton on its own. Modeled here
 * as an app-scoped class that the hosting Activity attaches its launcher
 * to (attachLauncher(), from onCreate) and detaches on teardown
 * (detachLauncher(), from onDestroy). If request() is called with no
 * launcher attached, it fails closed (returns false) rather than
 * suspending forever — worth confirming that's the right failure mode
 * with whoever owns app's Activity lifecycle, versus e.g. queuing the
 * request until an Activity attaches.
 */


class AndroidPermissionGateway(private val context: Context) : PermissionGateway {

    private var launcher: ActivityResultLauncher<String>? = null
    private var pendingRequest: CompletableDeferred<Boolean>? = null

    /** Called by the hosting Activity, typically from onCreate. */
    fun attachLauncher(launcher: ActivityResultLauncher<String>) {
        this.launcher = launcher
    }

    /** Called by the hosting Activity's onDestroy, to avoid leaking a stale launcher. */
    fun detachLauncher() {
        launcher = null
    }

    /** Called from the Activity's ActivityResultCallback<Boolean> passed to registerForActivityResult. */
    fun onPermissionResult(granted: Boolean) {
        pendingRequest?.complete(granted)
        pendingRequest = null
    }

    override suspend fun request(permission: Permission): Boolean {
        val androidPermission = permission.toAndroidPermissionOrNull()
            ?: return true // no runtime permission needed on this API level for this logical permission

        if (context.checkSelfPermission(androidPermission) == PackageManager.PERMISSION_GRANTED) {
            return true
        }

        val currentLauncher = launcher ?: return false

        val deferred = CompletableDeferred<Boolean>()
        pendingRequest = deferred
        currentLauncher.launch(androidPermission)
        return deferred.await()
    }

    private fun Permission.toAndroidPermissionOrNull(): String? = when (this) {
        Permission.RECORD_AUDIO -> Manifest.permission.RECORD_AUDIO
        Permission.LOCATION -> Manifest.permission.ACCESS_FINE_LOCATION
        Permission.BLUETOOTH_SCAN ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Manifest.permission.BLUETOOTH_SCAN else null
        Permission.BLUETOOTH_ADVERTISE ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Manifest.permission.BLUETOOTH_ADVERTISE else null
        Permission.BLUETOOTH_CONNECT ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Manifest.permission.BLUETOOTH_CONNECT else null
        Permission.NEARBY_WIFI_DEVICES ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.NEARBY_WIFI_DEVICES else null
        Permission.CAMERA -> Manifest.permission.CAMERA
        Permission.NOTIFICATIONS ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.POST_NOTIFICATIONS else null
    }
}