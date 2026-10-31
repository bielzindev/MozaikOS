package com.wpreborn.apps.flashlight

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wpreborn.R
import com.wpreborn.apps.common.WpActivity
import com.wpreborn.core.icons.WpIcons
import com.wpreborn.core.theme.Wp
import com.wpreborn.core.theme.WpType
import com.wpreborn.core.ui.WpIcon
import com.wpreborn.core.ui.WpSlider
import com.wpreborn.core.ui.WpText
import com.wpreborn.core.ui.wpClickable
import com.wpreborn.launcher.actioncenter.QuickActions
import kotlinx.coroutines.delay

private enum class LightMode { Torch, Sos, Screen }

/**
 * Flashlight: one big round switch for the camera flash (with brightness where the phone
 * supports it), an SOS signal, and a white screen light for phones without a flash.
 */
class FlashlightActivity : WpActivity() {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val quick = remember { QuickActions(context) }
        DisposableEffect(Unit) { onDispose { quick.release() } }
        val torch by quick.torch.collectAsState()
        var mode by remember { mutableStateOf(if (quick.hasTorch) LightMode.Torch else LightMode.Screen) }
        var screenOn by remember { mutableStateOf(false) }
        var sos by remember { mutableStateOf(false) }
        val strength = remember { torchStrength(context) }
        var level by remember { mutableStateOf(1f) }

        // SOS: · · · — — — · · · with the flash, repeating.
        LaunchedEffect(sos) {
            if (!sos) return@LaunchedEffect
            val unit = 220L
            val pattern = listOf(1, 1, 1, 3, 3, 3, 1, 1, 1)
            while (sos) {
                for (p in pattern) {
                    setTorch(context, true)
                    delay(unit * p)
                    setTorch(context, false)
                    delay(unit)
                }
                delay(unit * 6)
            }
        }
        DisposableEffect(Unit) { onDispose { if (sos) setTorch(context, false) } }
        // Screen light: full brightness while it is on.
        DisposableEffect(screenOn) {
            val activity = context as? android.app.Activity
            val w = activity?.window
            if (screenOn) {
                w?.let { it.attributes = it.attributes.apply { screenBrightness = 1f } }
                w?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            onDispose {
                w?.let { it.attributes = it.attributes.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE } }
                w?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }

        if (screenOn) {
            Box(Modifier.fillMaxSize().background(Color.White).wpClickable(tilt = false) { screenOn = false }, contentAlignment = Alignment.BottomCenter) {
                WpText(stringResource(R.string.light_tap_to_turn_off), style = WpType.Normal, color = Color.Gray, modifier = Modifier.padding(32.dp))
            }
            return
        }

        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
            WpText(stringResource(R.string.app_flashlight).uppercase(), style = WpType.AppTitle, modifier = Modifier.padding(top = 14.dp))
            Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                listOfNotNull(
                    if (quick.hasTorch) LightMode.Torch else null,
                    if (quick.hasTorch) LightMode.Sos else null,
                    LightMode.Screen,
                ).forEach { m ->
                    WpText(
                        stringResource(when (m) { LightMode.Torch -> R.string.light_torch; LightMode.Sos -> R.string.light_sos; LightMode.Screen -> R.string.light_screen }),
                        style = WpType.PivotHeader,
                        color = if (mode == m) Wp.palette.foreground else Wp.palette.inactive,
                        modifier = Modifier.wpClickable {
                            if (sos) sos = false
                            if (torch) quick.toggleTorch()
                            mode = m
                        },
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            val on = when (mode) { LightMode.Torch -> torch; LightMode.Sos -> sos; LightMode.Screen -> false }
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(180.dp)
                    .then(if (on) Modifier.background(Wp.accent, CircleShape) else Modifier.border(4.dp, Wp.palette.foreground, CircleShape))
                    .wpClickable {
                        when (mode) {
                            LightMode.Torch -> quick.toggleTorch()
                            LightMode.Sos -> sos = !sos
                            LightMode.Screen -> screenOn = true
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                WpIcon(WpIcons.Flashlight, size = 80.dp, tint = if (on) Color.White else Wp.palette.foreground)
            }
            WpText(
                stringResource(if (on) R.string.light_on else R.string.light_off),
                style = WpType.ExtraLarge,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 16.dp),
            )
            if (mode == LightMode.Torch && strength > 1 && Build.VERSION.SDK_INT >= 33) {
                WpText(stringResource(R.string.light_brightness), style = WpType.Subtle, color = Wp.palette.subtle, modifier = Modifier.padding(top = 24.dp))
                WpSlider(level, { v ->
                    level = v
                    if (torch) setTorchLevel(context, (1 + v * (strength - 1)).toInt())
                })
            }
            Spacer(Modifier.weight(1f))
            if (!quick.hasTorch) {
                WpText(stringResource(R.string.light_no_flash), style = WpType.Normal, color = Wp.palette.subtle, modifier = Modifier.padding(bottom = 24.dp))
            }
            Spacer(Modifier.height(16.dp).fillMaxWidth())
        }
    }
}

private fun backCameraWithFlash(context: Context): String? {
    val cm = context.getSystemService(CameraManager::class.java) ?: return null
    return runCatching {
        cm.cameraIdList.firstOrNull { id ->
            val c = cm.getCameraCharacteristics(id)
            c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true && c.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        }
    }.getOrNull()
}

private fun setTorch(context: Context, on: Boolean) {
    val id = backCameraWithFlash(context) ?: return
    runCatching { context.getSystemService(CameraManager::class.java)?.setTorchMode(id, on) }
}

/** Levels of the flash brightness (1 when it can't be dimmed). */
private fun torchStrength(context: Context): Int {
    if (Build.VERSION.SDK_INT < 33) return 1
    val id = backCameraWithFlash(context) ?: return 1
    return runCatching {
        context.getSystemService(CameraManager::class.java)!!.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL) ?: 1
    }.getOrDefault(1)
}

private fun setTorchLevel(context: Context, level: Int) {
    if (Build.VERSION.SDK_INT < 33) return
    val id = backCameraWithFlash(context) ?: return
    runCatching { context.getSystemService(CameraManager::class.java)?.turnOnTorchWithStrengthLevel(id, level) }
}
