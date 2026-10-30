package com.wpreborn.permissions

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.wpreborn.core.motion.slideInStaggered
import com.wpreborn.core.theme.Wp
import com.wpreborn.core.theme.WpType
import com.wpreborn.core.ui.WpButton
import com.wpreborn.core.ui.WpText

/** Runtime permissions grouped by the feature that needs them. Requested only when used. */
object Perms {
    val CONTACTS = arrayOf(Manifest.permission.READ_CONTACTS)
    val CALL_LOG = arrayOf(Manifest.permission.READ_CALL_LOG)
    val CALL = arrayOf(Manifest.permission.CALL_PHONE)
    val SMS = arrayOf(Manifest.permission.READ_SMS, Manifest.permission.SEND_SMS, Manifest.permission.READ_CONTACTS)
    val CALENDAR = arrayOf(Manifest.permission.READ_CALENDAR)
    val AUDIO = if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.READ_MEDIA_AUDIO) else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    val IMAGES = if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.READ_MEDIA_IMAGES) else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    val NOTIFICATIONS = if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.POST_NOTIFICATIONS) else emptyArray()
    val BLUETOOTH = if (Build.VERSION.SDK_INT >= 31) arrayOf(Manifest.permission.BLUETOOTH_CONNECT) else emptyArray()
}

fun Context.hasPermissions(perms: Array<String>): Boolean =
    perms.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }

fun Context.canWriteSettings(): Boolean = Settings.System.canWrite(this)

fun Context.writeSettingsIntent(): Intent =
    Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:$packageName")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

fun Context.canDrawOverlays(): Boolean = Settings.canDrawOverlays(this)

fun Context.overlayIntent(): Intent =
    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

fun Context.appDetailsIntent(): Intent =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

/**
 * Tracks whether [perms] are granted, re-checking every time the screen resumes (the user
 * may have changed them in Android settings).
 */
@Composable
fun rememberPermissionState(perms: Array<String>): PermissionState {
    val context = LocalContext.current
    val state = remember(perms.joinToString()) { PermissionState(perms, context.hasPermissions(perms)) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        state.granted = context.hasPermissions(perms)
        state.asked++
    }
    state.launcher = { launcher.launch(perms) }
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner, state) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            state.granted = context.hasPermissions(perms)
        }
    }
    return state
}

class PermissionState(val perms: Array<String>, initial: Boolean) {
    var granted by mutableStateOf(initial)
    var asked by mutableIntStateOf(0)
    internal var launcher: () -> Unit = {}
    fun request() = launcher()
}

/**
 * Shown in place of content that needs a permission: a short explanation in the Windows
 * Phone voice and an "allow" button. After a permanent denial it opens app settings.
 */
@Composable
fun PermissionGate(
    state: PermissionState,
    message: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (state.granted) {
        content()
        return
    }
    val context = LocalContext.current
    Column(modifier.fillMaxWidth().padding(top = 12.dp, end = 20.dp).slideInStaggered(0)) {
        WpText(message, style = WpType.Large, color = Wp.palette.subtle)
        Spacer(Modifier.height(20.dp))
        WpButton(text = context.getString(com.wpreborn.R.string.allow)) {
            if (state.asked >= 2) context.startActivity(context.appDetailsIntent()) else state.request()
        }
    }
}
