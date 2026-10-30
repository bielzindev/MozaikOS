package com.wpreborn.launcher.actioncenter

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.provider.Settings
import android.view.Window
import com.wpreborn.permissions.canWriteSettings
import com.wpreborn.permissions.writeSettingsIntent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Quick actions of the Action Center. Android 10+ no longer lets regular apps toggle Wi-Fi,
 * Bluetooth or airplane mode, so those open the official settings panels; brightness,
 * rotation lock, flashlight and vibrate are changed directly when permitted.
 */
class QuickActions(private val context: Context) {

    private val camera = context.getSystemService(CameraManager::class.java)
    private val _torch = MutableStateFlow(false)
    val torch: StateFlow<Boolean> = _torch.asStateFlow()
    private var torchId: String? = null

    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (cameraId == torchId) _torch.value = enabled
        }
    }

    init {
        torchId = try {
            camera?.cameraIdList?.firstOrNull { id ->
                camera.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        } catch (_: Exception) {
            null
        }
        try {
            camera?.registerTorchCallback(torchCallback, android.os.Handler(android.os.Looper.getMainLooper()))
        } catch (_: Exception) {
        }
    }

    fun release() {
        try {
            camera?.unregisterTorchCallback(torchCallback)
        } catch (_: Exception) {
        }
    }

    val hasTorch get() = torchId != null

    fun toggleTorch() {
        val id = torchId ?: return
        try {
            camera?.setTorchMode(id, !_torch.value)
        } catch (_: Exception) {
        }
    }

    fun openWifi() = start(Intent(Settings.Panel.ACTION_WIFI))
    fun openBluetooth() = start(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
    fun openAirplane() = start(Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS))
    fun openLocation() = start(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
    fun openAllSettings() = start(Intent(context, com.wpreborn.apps.settings.SettingsActivity::class.java))
    fun openInternet() = start(Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY))

    fun rotationLocked(): Boolean =
        Settings.System.getInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 1) == 0

    /** Returns false when WRITE_SETTINGS still has to be granted (the settings page is opened). */
    fun toggleRotationLock(): Boolean {
        if (!context.canWriteSettings()) {
            start(context.writeSettingsIntent())
            return false
        }
        Settings.System.putInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, if (rotationLocked()) 1 else 0)
        return true
    }

    /** Brightness levels of the 8.1 quick action: low, medium, high, auto. */
    enum class BrightnessLevel { Low, Medium, High, Auto }

    fun brightness(): BrightnessLevel {
        val mode = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE, 0)
        if (mode == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC) return BrightnessLevel.Auto
        val v = Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128)
        return when {
            v < 80 -> BrightnessLevel.Low
            v < 190 -> BrightnessLevel.Medium
            else -> BrightnessLevel.High
        }
    }

    fun brightnessValue(): Float =
        Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128) / 255f

    fun cycleBrightness(window: Window?): Boolean {
        val next = BrightnessLevel.entries[(brightness().ordinal + 1) % BrightnessLevel.entries.size]
        return setBrightness(next, window)
    }

    fun setBrightness(level: BrightnessLevel, window: Window?): Boolean = when (level) {
        BrightnessLevel.Auto -> setBrightnessRaw(-1f, window)
        BrightnessLevel.Low -> setBrightnessRaw(0.15f, window)
        BrightnessLevel.Medium -> setBrightnessRaw(0.5f, window)
        BrightnessLevel.High -> setBrightnessRaw(1f, window)
    }

    /** [value] in 0..1, or negative for automatic. */
    fun setBrightnessRaw(value: Float, window: Window?): Boolean {
        if (!context.canWriteSettings()) {
            // Fall back to this window only, and ask for the permission.
            window?.let { w -> w.attributes = w.attributes.apply { screenBrightness = if (value < 0) -1f else value.coerceIn(0.02f, 1f) } }
            start(context.writeSettingsIntent())
            return false
        }
        val cr = context.contentResolver
        if (value < 0) {
            Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC)
        } else {
            Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
            Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, (value.coerceIn(0.02f, 1f) * 255).toInt())
        }
        return true
    }

    fun vibrateOn(): Boolean = context.getSystemService(AudioManager::class.java).ringerMode == AudioManager.RINGER_MODE_VIBRATE

    fun toggleVibrate() {
        val am = context.getSystemService(AudioManager::class.java)
        try {
            am.ringerMode = if (am.ringerMode == AudioManager.RINGER_MODE_VIBRATE) AudioManager.RINGER_MODE_NORMAL else AudioManager.RINGER_MODE_VIBRATE
        } catch (_: SecurityException) {
            start(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
        }
    }

    private fun start(intent: Intent) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
