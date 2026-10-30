package com.wpreborn.apps.settings

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.wpreborn.BuildConfig
import com.wpreborn.R
import com.wpreborn.apps.common.WpActivity
import com.wpreborn.core.icons.WpIcons
import com.wpreborn.core.motion.slideInStaggered
import com.wpreborn.core.theme.AccentColor
import com.wpreborn.core.theme.Wp
import com.wpreborn.core.theme.WpType
import com.wpreborn.core.ui.Thumbnail
import com.wpreborn.core.ui.WpButton
import com.wpreborn.core.ui.WpIcon
import com.wpreborn.core.ui.WpListItem
import com.wpreborn.core.ui.WpPageHeader
import com.wpreborn.core.ui.WpPivot
import com.wpreborn.core.ui.WpSlider
import com.wpreborn.core.ui.WpText
import com.wpreborn.core.ui.WpToggle
import com.wpreborn.core.ui.wpClickable
import com.wpreborn.data.notifications.NotificationCenter
import com.wpreborn.data.prefs.SettingsRepository
import com.wpreborn.data.sms.SmsRepository
import com.wpreborn.data.system.SystemStatus
import com.wpreborn.launcher.actioncenter.QuickActions
import com.wpreborn.navigation.WpNavHost
import com.wpreborn.navigation.rememberNavigator
import com.wpreborn.permissions.canDrawOverlays
import com.wpreborn.permissions.canWriteSettings
import com.wpreborn.permissions.overlayIntent
import com.wpreborn.permissions.writeSettingsIntent

private enum class SettingsPage { Home, StartTheme, Accent, LockScreen, Notifications, Display, Storage, About, Launcher }

/**
 * Settings, laid out like Windows Phone 8.1 ("system" and "applications" pivots). Launcher
 * and theme options are handled here; anything protected by Android opens the matching
 * system screen through its official intent.
 */
class SettingsActivity : WpActivity() {

    @Composable
    override fun Content() {
        val nav = rememberNavigator(SettingsPage.Home)
        WpNavHost(nav) { page ->
            when (page) {
                SettingsPage.Home -> SettingsHome { nav.navigate(it) }
                SettingsPage.StartTheme -> StartThemePage { nav.navigate(SettingsPage.Accent) }
                SettingsPage.Accent -> AccentPage { nav.pop() }
                SettingsPage.LockScreen -> LockScreenPage()
                SettingsPage.Notifications -> NotificationsPage()
                SettingsPage.Display -> DisplayPage()
                SettingsPage.Storage -> StoragePage()
                SettingsPage.About -> AboutPage()
                SettingsPage.Launcher -> LauncherPage()
            }
        }
    }
}

private fun Context.open(action: String) {
    try {
        startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: Exception) {
        startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

/** Re-evaluates [block] every time the screen resumes (state may change in Android settings). */
@Composable
private fun <T> rememberOnResume(block: () -> T): T {
    var value by remember { mutableStateOf(block()) }
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(owner) {
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { value = block() }
    }
    return value
}

private data class Entry(val title: String, val subtitle: String?, val onClick: () -> Unit)

@Composable
private fun SettingsHome(go: (SettingsPage) -> Unit) {
    val context = LocalContext.current
    val settings = Wp.settings
    val status = remember { SystemStatus.get(context) }
    val net by status.network.collectAsState()
    val radios by status.radios.collectAsState()
    val battery by status.battery.collectAsState()
    val onOff = { b: Boolean -> context.getString(if (b) R.string.on else R.string.off) }
    val isHome = rememberOnResume { isDefaultHome(context) }
    val notifAccess = rememberOnResume { NotificationCenter.hasAccess(context) }
    val smsDefault = rememberOnResume { SmsRepository(context).isDefaultSmsApp() }

    val system = listOf(
        Entry(
            stringResource(R.string.set_start_theme),
            "${stringResource(if (settings.darkTheme) R.string.theme_dark else R.string.theme_light)}, ${stringResource(settings.accent.label)}",
        ) { go(SettingsPage.StartTheme) },
        Entry(stringResource(R.string.set_launcher), onOff(isHome)) { go(SettingsPage.Launcher) },
        Entry(stringResource(R.string.set_lock_screen), onOff(settings.lockEnabled)) { go(SettingsPage.LockScreen) },
        Entry(stringResource(R.string.set_notifications), onOff(notifAccess)) { go(SettingsPage.Notifications) },
        Entry(stringResource(R.string.set_wifi), if (net.wifiConnected) stringResource(R.string.connected) else stringResource(R.string.not_connected)) {
            context.open(Settings.ACTION_WIFI_SETTINGS)
        },
        Entry(stringResource(R.string.set_bluetooth), onOff(radios.bluetooth)) { context.open(Settings.ACTION_BLUETOOTH_SETTINGS) },
        Entry(stringResource(R.string.set_airplane), onOff(radios.airplane)) { context.open(Settings.ACTION_AIRPLANE_MODE_SETTINGS) },
        Entry(stringResource(R.string.set_cellular), net.carrier.ifBlank { null }) { context.open(Settings.ACTION_WIRELESS_SETTINGS) },
        Entry(stringResource(R.string.set_sounds), null) { context.open(Settings.ACTION_SOUND_SETTINGS) },
        Entry(stringResource(R.string.set_display), null) { go(SettingsPage.Display) },
        Entry(stringResource(R.string.set_battery), "${battery.level}%") { context.open(Settings.ACTION_BATTERY_SAVER_SETTINGS) },
        Entry(stringResource(R.string.set_storage), null) { go(SettingsPage.Storage) },
        Entry(stringResource(R.string.set_location), null) { context.open(Settings.ACTION_LOCATION_SOURCE_SETTINGS) },
        Entry(stringResource(R.string.set_date_time), null) { context.open(Settings.ACTION_DATE_SETTINGS) },
        Entry(stringResource(R.string.set_language), null) { context.open(Settings.ACTION_LOCALE_SETTINGS) },
        Entry(stringResource(R.string.set_keyboard), null) { context.open(Settings.ACTION_INPUT_METHOD_SETTINGS) },
        Entry(stringResource(R.string.set_ease_of_access), null) { context.open(Settings.ACTION_ACCESSIBILITY_SETTINGS) },
        Entry(stringResource(R.string.set_security), null) { context.open(Settings.ACTION_SECURITY_SETTINGS) },
        Entry(stringResource(R.string.set_about), Build.MODEL) { go(SettingsPage.About) },
    )
    val apps = listOf(
        Entry(stringResource(R.string.app_messaging), if (smsDefault) stringResource(R.string.set_default_app) else stringResource(R.string.set_not_default_app)) {
            SmsRepository(context).requestDefaultIntent()?.let { (context as? android.app.Activity)?.startActivityForResult(it, 3) }
                ?: context.open(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
        },
        Entry(stringResource(R.string.app_phone), null) {
            context.startActivity(Intent(android.telecom.TelecomManager.ACTION_SHOW_CALL_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        },
        Entry(stringResource(R.string.set_default_apps), null) { context.open(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS) },
        Entry(stringResource(R.string.set_all_apps), null) { context.open(Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS) },
        Entry(stringResource(R.string.set_app_permissions), null) {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        },
        Entry(stringResource(R.string.set_android_settings), null) { context.open(Settings.ACTION_SETTINGS) },
    )

    WpPivot(
        title = stringResource(R.string.app_settings),
        headers = listOf(stringResource(R.string.set_system), stringResource(R.string.set_applications)),
    ) { index ->
        val list = if (index == 0) system else apps
        LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp)) {
            itemsIndexed(list) { i, e ->
                WpListItem(e.title, Modifier.slideInStaggered(i), subtitle = e.subtitle, onClick = e.onClick)
            }
        }
    }
}

private fun isDefaultHome(context: Context): Boolean {
    val rm = context.getSystemService(RoleManager::class.java)
    return rm?.isRoleHeld(RoleManager.ROLE_HOME) == true
}

@Composable
private fun SettingsScaffold(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        WpPageHeader(stringResource(R.string.app_settings), title)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
        ) { content() }
    }
}

@Composable
private fun StartThemePage(onAccent: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { SettingsRepository.get(context) }
    val settings = Wp.settings
    val pickStart = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {
            }
            repo.setStartBackground(uri.toString())
        }
    }
    SettingsScaffold(stringResource(R.string.set_start_theme)) {
        WpText(stringResource(R.string.set_background), style = WpType.Subtle, color = Wp.palette.subtle)
        Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ChoiceBox(stringResource(R.string.theme_dark), settings.darkTheme) { repo.setDarkTheme(true) }
            ChoiceBox(stringResource(R.string.theme_light), !settings.darkTheme) { repo.setDarkTheme(false) }
        }
        Spacer(Modifier.height(12.dp))
        WpText(stringResource(R.string.set_accent), style = WpType.Subtle, color = Wp.palette.subtle)
        Row(
            Modifier.fillMaxWidth().padding(vertical = 8.dp).wpClickable(onClick = onAccent),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(40.dp).background(Wp.accent))
            Spacer(Modifier.width(12.dp))
            WpText(stringResource(settings.accent.label), style = WpType.Large)
        }
        WpToggle(stringResource(R.string.set_more_tiles), settings.moreTiles, onText = stringResource(R.string.on), offText = stringResource(R.string.off)) {
            repo.setMoreTiles(it)
        }
        WpToggle(stringResource(R.string.set_live_tiles), settings.liveTiles, onText = stringResource(R.string.on), offText = stringResource(R.string.off)) {
            repo.setLiveTiles(it)
        }
        WpToggle(stringResource(R.string.set_icons_accent), settings.iconsOnAccent, onText = stringResource(R.string.on), offText = stringResource(R.string.off)) {
            repo.setIconsOnAccent(it)
        }
        WpToggle(stringResource(R.string.set_wp_status_bar), settings.wpStatusBar, onText = stringResource(R.string.on), offText = stringResource(R.string.off)) {
            repo.setWpStatusBar(it)
        }
        Spacer(Modifier.height(16.dp))
        WpText(stringResource(R.string.set_start_background), style = WpType.Subtle, color = Wp.palette.subtle)
        WpText(stringResource(R.string.set_start_background_hint), style = WpType.Small, color = Wp.palette.subtle, modifier = Modifier.padding(vertical = 4.dp))
        settings.startBackground?.let {
            Thumbnail(android.net.Uri.parse(it), Modifier.padding(vertical = 8.dp).size(120.dp), sizePx = 360)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            WpButton(stringResource(R.string.choose_photo)) {
                pickStart.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
            if (settings.startBackground != null) WpButton(stringResource(R.string.remove)) { repo.setStartBackground(null) }
        }
    }
}

@Composable
private fun ChoiceBox(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .width(130.dp)
            .height(44.dp)
            .border(2.dp, if (selected) Wp.accent else Wp.palette.foreground)
            .background(if (selected) Wp.accent else Color.Transparent)
            .wpClickable(onClick = onClick),
        contentAlignment = Alignment.CenterStart,
    ) {
        WpText(label, style = WpType.Normal, color = if (selected) Color.White else Wp.palette.foreground, modifier = Modifier.padding(start = 12.dp))
    }
}

/** The full-screen accent color picker: a 4-column grid of squares, like 8.1. */
@Composable
private fun AccentPage(onPicked: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { SettingsRepository.get(context) }
    Column(Modifier.fillMaxSize()) {
        WpText(
            stringResource(R.string.set_accent).uppercase(),
            style = WpType.AppTitle,
            modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 14.dp),
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            itemsIndexed(AccentColor.entries) { i, c ->
                Box(
                    Modifier
                        .aspectRatio(1f)
                        .slideInStaggered(i / 4)
                        .background(c.color)
                        .then(if (Wp.settings.accent == c) Modifier.border(3.dp, Wp.palette.foreground) else Modifier)
                        .wpClickable {
                            repo.setAccent(c)
                            onPicked()
                        },
                )
            }
        }
    }
}

@Composable
private fun LockScreenPage() {
    val context = LocalContext.current
    val repo = remember { SettingsRepository.get(context) }
    val settings = Wp.settings
    val overlay = rememberOnResume { context.canDrawOverlays() }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {
            }
            repo.setLockWallpaper(uri.toString())
        }
    }
    SettingsScaffold(stringResource(R.string.set_lock_screen)) {
        WpText(stringResource(R.string.lock_explain), style = WpType.Small, color = Wp.palette.subtle, modifier = Modifier.padding(bottom = 8.dp))
        WpToggle(stringResource(R.string.lock_enable), settings.lockEnabled && overlay, onText = stringResource(R.string.on), offText = stringResource(R.string.off)) { on ->
            if (on && !context.canDrawOverlays()) {
                context.startActivity(context.overlayIntent())
            }
            repo.setLockEnabled(on)
        }
        if (settings.lockEnabled && !overlay) {
            WpText(stringResource(R.string.lock_needs_overlay), style = WpType.Small, color = Wp.accent)
        }
        WpToggle(stringResource(R.string.lock_show_notifications), settings.lockShowNotifications, onText = stringResource(R.string.on), offText = stringResource(R.string.off)) {
            repo.setLockShowNotifications(it)
        }
        Spacer(Modifier.height(12.dp))
        WpText(stringResource(R.string.lock_background), style = WpType.Subtle, color = Wp.palette.subtle)
        settings.lockWallpaper?.let {
            Thumbnail(android.net.Uri.parse(it), Modifier.padding(vertical = 8.dp).size(width = 100.dp, height = 170.dp), sizePx = 400)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 8.dp)) {
            WpButton(stringResource(R.string.choose_photo)) {
                pick.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
            if (settings.lockWallpaper != null) WpButton(stringResource(R.string.remove)) { repo.setLockWallpaper(null) }
        }
        Spacer(Modifier.height(16.dp))
        WpButton(stringResource(R.string.lock_preview)) {
            context.startActivity(Intent(context, com.wpreborn.launcher.lock.LockActivity::class.java))
        }
        Spacer(Modifier.height(16.dp))
        WpListItem(stringResource(R.string.lock_system), subtitle = stringResource(R.string.lock_system_hint), titleStyle = WpType.Large) {
            context.open(Settings.ACTION_SECURITY_SETTINGS)
        }
    }
}

@Composable
private fun NotificationsPage() {
    val context = LocalContext.current
    val access = rememberOnResume { NotificationCenter.hasAccess(context) }
    SettingsScaffold(stringResource(R.string.set_notifications)) {
        WpText(stringResource(R.string.notif_explain), style = WpType.Normal, color = Wp.palette.subtle)
        Spacer(Modifier.height(12.dp))
        WpToggle(stringResource(R.string.notif_access), access, onText = stringResource(R.string.on), offText = stringResource(R.string.off)) {
            context.startActivity(NotificationCenter.accessSettingsIntent(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        Spacer(Modifier.height(8.dp))
        WpListItem(stringResource(R.string.notif_android), titleStyle = WpType.Large) {
            context.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
        WpListItem(stringResource(R.string.notif_dnd), titleStyle = WpType.Large) {
            context.open(Settings.ACTION_ZEN_MODE_PRIORITY_SETTINGS)
        }
    }
}

@Composable
private fun DisplayPage() {
    val context = LocalContext.current
    val quick = remember { QuickActions(context) }
    val canWrite = rememberOnResume { context.canWriteSettings() }
    var auto by remember { mutableStateOf(quick.brightness() == QuickActions.BrightnessLevel.Auto) }
    var level by remember { mutableFloatStateOf(quick.brightnessValue()) }
    val window = (context as? android.app.Activity)?.window
    SettingsScaffold(stringResource(R.string.set_display)) {
        if (!canWrite) {
            WpText(stringResource(R.string.display_needs_permission), style = WpType.Small, color = Wp.palette.subtle)
            WpButton(stringResource(R.string.allow), Modifier.padding(vertical = 8.dp)) { context.startActivity(context.writeSettingsIntent()) }
        }
        WpToggle(stringResource(R.string.display_auto), auto, onText = stringResource(R.string.on), offText = stringResource(R.string.off), enabled = canWrite) {
            auto = it
            if (it) quick.setBrightnessRaw(-1f, window) else quick.setBrightnessRaw(level, window)
        }
        WpText(stringResource(R.string.display_level), style = WpType.Subtle, color = Wp.palette.subtle, modifier = Modifier.padding(top = 8.dp))
        WpSlider(level, {
            level = it
            auto = false
            quick.setBrightnessRaw(it, window)
        })
        Spacer(Modifier.height(16.dp))
        WpListItem(stringResource(R.string.display_more), titleStyle = WpType.Large) { context.open(Settings.ACTION_DISPLAY_SETTINGS) }
    }
}

@Composable
private fun StoragePage() {
    val context = LocalContext.current
    val stat = remember { StatFs(Environment.getDataDirectory().path) }
    val total = stat.totalBytes
    val free = stat.availableBytes
    val used = total - free
    SettingsScaffold(stringResource(R.string.set_storage)) {
        WpText(stringResource(R.string.storage_phone), style = WpType.ExtraLarge)
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(22.dp).background(Wp.palette.raised)) {
            Box(Modifier.fillMaxWidth(used.toFloat() / total.coerceAtLeast(1)).height(22.dp).background(Wp.accent))
        }
        Spacer(Modifier.height(6.dp))
        WpText(
            stringResource(R.string.storage_free, Formatter.formatShortFileSize(context, free), Formatter.formatShortFileSize(context, total)),
            style = WpType.Subtle,
            color = Wp.palette.subtle,
        )
        Spacer(Modifier.height(16.dp))
        WpListItem(stringResource(R.string.storage_manage), titleStyle = WpType.Large) { context.open(Settings.ACTION_INTERNAL_STORAGE_SETTINGS) }
    }
}

@Composable
private fun AboutPage() {
    val context = LocalContext.current
    SettingsScaffold(stringResource(R.string.set_about)) {
        val rows = listOf(
            stringResource(R.string.about_name) to Build.MODEL,
            stringResource(R.string.about_manufacturer) to Build.MANUFACTURER.replaceFirstChar { it.uppercase() },
            stringResource(R.string.about_android) to "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            stringResource(R.string.about_launcher) to "Windows Phone Reborn ${BuildConfig.VERSION_NAME}",
            stringResource(R.string.about_ram) to Formatter.formatShortFileSize(
                context,
                android.app.ActivityManager.MemoryInfo().also { context.getSystemService(android.app.ActivityManager::class.java).getMemoryInfo(it) }.totalMem,
            ),
        )
        rows.forEachIndexed { i, (k, v) ->
            Column(Modifier.padding(vertical = 6.dp).slideInStaggered(i)) {
                WpText(k, style = WpType.Subtle, color = Wp.palette.subtle)
                WpText(v, style = WpType.Large)
            }
        }
        Spacer(Modifier.height(16.dp))
        WpText(stringResource(R.string.about_disclaimer), style = WpType.Small, color = Wp.palette.subtle)
        Spacer(Modifier.height(12.dp))
        WpListItem(stringResource(R.string.about_more), titleStyle = WpType.Large) { context.open(Settings.ACTION_DEVICE_INFO_SETTINGS) }
    }
}

@Composable
private fun LauncherPage() {
    val context = LocalContext.current
    val isHome = rememberOnResume { isDefaultHome(context) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {}
    SettingsScaffold(stringResource(R.string.set_launcher)) {
        WpText(stringResource(R.string.launcher_explain), style = WpType.Normal, color = Wp.palette.subtle)
        Spacer(Modifier.height(12.dp))
        WpText(
            stringResource(if (isHome) R.string.launcher_is_default else R.string.launcher_not_default),
            style = WpType.Large,
            color = if (isHome) Wp.accent else Wp.palette.foreground,
        )
        Spacer(Modifier.height(12.dp))
        WpButton(stringResource(R.string.welcome_default_button)) {
            val rm = context.getSystemService(RoleManager::class.java)
            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME) && !rm.isRoleHeld(RoleManager.ROLE_HOME)) {
                launcher.launch(rm.createRequestRoleIntent(RoleManager.ROLE_HOME))
            } else {
                context.open(Settings.ACTION_HOME_SETTINGS)
            }
        }
        Spacer(Modifier.height(20.dp))
        WpIcon(WpIcons.Info, size = 28.dp, tint = Wp.palette.subtle)
        WpText(stringResource(R.string.launcher_reboot_note), style = WpType.Small, color = Wp.palette.subtle)
    }
}
