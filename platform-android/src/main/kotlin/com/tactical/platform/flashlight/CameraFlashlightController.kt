package com.tactical.platform.flashlight

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import com.tactical.platform.api.flashlight.FlashlightController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Implements FlashlightController wrapping CameraManager's torch API.
 * strobe() launches a background toggle loop and returns immediately —
 * per the interface's kdoc ("repeating until off() is called"), off() is
 * what stops it, not strobe() suspending for the whole duration.
 */
class CameraFlashlightController(private val context: Context) : FlashlightController {

    private val cameraManager: CameraManager by lazy {
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }
    private val scope = CoroutineScope(Dispatchers.Default)
    private var strobeJob: Job? = null

    override suspend fun strobe(intervalMs: Long) {
        require(intervalMs > 0) { "intervalMs must be positive" }

        strobeJob?.cancel()
        val cameraId = torchCameraIdOrNull() ?: return

        strobeJob = scope.launch {
            var torchOn = false
            try {
                while (true) {
                    torchOn = !torchOn
                    cameraManager.setTorchMode(cameraId, torchOn)
                    delay(intervalMs)
                }
            } finally {
                // Reached on cancellation (off(), or a new strobe() call
                // above) — always leave the torch physically off rather
                // than stopping mid-flash.
                runCatching { cameraManager.setTorchMode(cameraId, false) }
            }
        }
    }

    override suspend fun off() {
        strobeJob?.cancel()
        strobeJob = null
    }

    private fun torchCameraIdOrNull(): String? =
        cameraManager.cameraIdList.firstOrNull { id ->
            cameraManager.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
}