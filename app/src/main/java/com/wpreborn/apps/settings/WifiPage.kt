package com.wpreborn.apps.settings

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.ScanResult
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wpreborn.R
import com.wpreborn.core.icons.WpIcons
import com.wpreborn.core.theme.Wp
import com.wpreborn.core.theme.WpType
import com.wpreborn.core.ui.AppBarButton
import com.wpreborn.core.ui.WpAppBar
import com.wpreborn.core.ui.WpButton
import com.wpreborn.core.ui.WpIcon
import com.wpreborn.core.ui.WpPageHeader
import com.wpreborn.core.ui.WpText
import com.wpreborn.core.ui.WpTextBox
import com.wpreborn.core.ui.wpClickable
import com.wpreborn.permissions.rememberPermissionState
import kotlinx.coroutines.delay

/** A network around, as the list shows it. */
private data class WifiNet(val ssid: String, val level: Int, val secure: Boolean, val security: String)

/**
 * Wi-Fi, after Windows Phone: whether it is on, the network you are connected to, and the
 * networks around with their signal and a lock when they need a password. Tap one to join it
 * (Android asks to confirm a new network once, then joins it on its own); the networks you
 * saved earlier are joined by Android when they are in range.
 */
@Composable
fun WifiPage() {
    val context = LocalContext.current
    val wifi = remember { context.applicationContext.getSystemService(WifiManager::class.java) }
    val perms = remember {
        if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.NEARBY_WIFI_DEVICES, Manifest.permission.ACCESS_FINE_LOCATION)
        else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }
    val perm = rememberPermissionState(perms)
    var enabled by remember { mutableStateOf(wifi?.isWifiEnabled == true) }
    var nets by remember { mutableStateOf<List<WifiNet>>(emptyList()) }
    var current by remember { mutableStateOf<String?>(null) }
    var tick by remember { mutableIntStateOf(0) }
    var joining by remember { mutableStateOf<WifiNet?>(null) }

    fun read() {
        enabled = wifi?.isWifiEnabled == true
        current = currentSsid(context)
        nets = runCatching {
            @Suppress("DEPRECATION")
            wifi?.scanResults.orEmpty()
                .filter { ssidOf(it).isNotBlank() }
                .groupBy { ssidOf(it) }
                .map { (name, list) ->
                    val best = list.maxBy { it.level }
                    val caps = best.capabilities.orEmpty()
                    val sec = when {
                        "SAE" in caps -> "WPA3"
                        "WPA2" in caps || "RSN" in caps -> "WPA2"
                        "WPA" in caps -> "WPA"
                        "WEP" in caps -> "WEP"
                        else -> ""
                    }
                    WifiNet(name, WifiManager.calculateSignalLevel(best.level, 5), sec.isNotEmpty(), sec)
                }
                .sortedWith(compareByDescending<WifiNet> { it.ssid == current }.thenByDescending { it.level })
        }.getOrDefault(emptyList())
    }
    // New scan results arrive by broadcast; scans are asked for every 20 s (Android throttles them).
    DisposableEffect(Unit) {
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context, i: Intent) = read()
        }
        val f = IntentFilter().apply {
            addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
            addAction(WifiManager.WIFI_STATE_CHANGED_ACTION)
            addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)
        }
        androidx.core.content.ContextCompat.registerReceiver(context, r, f, androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { runCatching { context.unregisterReceiver(r) } }
    }
    LaunchedEffect(perm.granted, tick) {
        while (true) {
            read()
            @Suppress("DEPRECATION")
            if (perm.granted) runCatching { wifi?.startScan() }
            delay(20_000)
        }
    }

    Column(Modifier.fillMaxSize()) {
        WpPageHeader(stringResource(R.string.app_settings), stringResource(R.string.set_wifi))
        Column(Modifier.padding(horizontal = 20.dp)) {
            // Android lets only its own panel turn Wi-Fi on and off.
            Row(Modifier.fillMaxWidth().wpClickable { openPanel(context) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    WpText(stringResource(R.string.wifi_networking), style = WpType.Subtle, color = Wp.palette.subtle)
                    WpText(stringResource(if (enabled) R.string.on else R.string.off), style = WpType.Large)
                }
                WpIcon(WpIcons.Wifi, tint = if (enabled) Wp.accent else Wp.palette.subtle, size = 26.dp)
            }
            if (!perm.granted) {
                WpText(stringResource(R.string.wifi_needs_permission), style = WpType.Small, color = Wp.palette.subtle, modifier = Modifier.padding(top = 8.dp))
                WpButton(stringResource(R.string.allow), Modifier.padding(top = 8.dp)) { perm.request() }
            }
            Spacer(Modifier.height(8.dp))
        }
        LazyColumn(Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, bottom = 16.dp)) {
            if (enabled && perm.granted && nets.isEmpty()) item { WpText(stringResource(R.string.wifi_searching), style = WpType.Large, color = Wp.palette.subtle) }
            items(nets, key = { it.ssid }) { n ->
        androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.animateItem()) {
                val connected = n.ssid == current
                Row(
                    Modifier.fillMaxWidth().wpClickable { if (!connected) joining = n }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SignalBars(n.level)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        WpText(n.ssid, style = WpType.Large, color = if (connected) Wp.accent else Wp.palette.foreground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        WpText(
                            when {
                                connected -> stringResource(R.string.connected)
                                n.secure -> stringResource(R.string.wifi_secured, n.security)
                                else -> stringResource(R.string.wifi_open)
                            },
                            style = WpType.Small, color = Wp.palette.subtle,
                        )
                    }
                    if (n.secure) WpIcon(WpIcons.Lock, tint = Wp.palette.subtle, size = 16.dp)
                }
            
        }
    }
        }
        WpAppBar(
            buttons = listOf(
                AppBarButton(WpIcons.Refresh, stringResource(R.string.refresh), enabled = perm.granted) { tick++ },
                AppBarButton(WpIcons.Settings, stringResource(R.string.wifi_more)) { openPanel(context) },
            ),
        )
    }
    joining?.let { n -> JoinNetwork(n, onDismiss = { joining = null }) { pwd -> connect(context, n, pwd); joining = null } }
}

@Composable
private fun SignalBars(level: Int) {
    Row(Modifier.height(20.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        for (i in 0 until 4) {
            Box(Modifier.width(4.dp).height((5 + i * 5).dp).background(if (level > i) Wp.palette.foreground else Wp.palette.foreground.copy(alpha = 0.25f)))
        }
    }
}

@Composable
private fun JoinNetwork(n: WifiNet, onDismiss: () -> Unit, onJoin: (String?) -> Unit) {
    BackHandler(onBack = onDismiss)
    var pwd by remember { mutableStateOf("") }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)).clickable(remember { MutableInteractionSource() }, null, onClick = onDismiss)) {
        Column(
            Modifier.align(Alignment.TopCenter).fillMaxWidth().background(Wp.palette.chrome)
                .clickable(remember { MutableInteractionSource() }, null) {}.padding(start = 20.dp, end = 20.dp, top = 40.dp, bottom = 20.dp),
        ) {
            WpText(n.ssid, style = WpType.Large)
            if (n.secure) {
                WpText(stringResource(R.string.wifi_password), style = WpType.Subtle, color = Wp.palette.subtle, modifier = Modifier.padding(top = 12.dp))
                WpTextBox(pwd, { pwd = it }, Modifier.fillMaxWidth().padding(top = 4.dp), keyboardType = KeyboardType.Password)
            }
            Row(Modifier.padding(top = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                WpButton(stringResource(R.string.wifi_connect), Modifier.weight(1f), enabled = !n.secure || pwd.length >= 8 || n.security == "WEP") { onJoin(if (n.secure) pwd else null) }
                WpButton(stringResource(R.string.cancel), Modifier.weight(1f), onClick = onDismiss)
            }
        }
    }
}

@Suppress("DEPRECATION")
private fun ssidOf(r: ScanResult): String =
    if (Build.VERSION.SDK_INT >= 33) r.wifiSsid?.toString()?.removeSurrounding("\"").orEmpty() else r.SSID.orEmpty()

private fun currentSsid(context: Context): String? = runCatching {
    val cm = context.getSystemService(ConnectivityManager::class.java)
    val caps = cm?.getNetworkCapabilities(cm.activeNetwork)
    val info = if (Build.VERSION.SDK_INT >= 31) caps?.transportInfo as? WifiInfo else {
        @Suppress("DEPRECATION")
        context.applicationContext.getSystemService(WifiManager::class.java)?.connectionInfo
    }
    @Suppress("DEPRECATION")
    info?.ssid?.removeSurrounding("\"")?.takeIf { it.isNotBlank() && it != "<unknown ssid>" }
}.getOrNull()

private fun openPanel(context: Context) {
    runCatching {
        context.startActivity(Intent(if (Build.VERSION.SDK_INT >= 29) Settings.Panel.ACTION_WIFI else Settings.ACTION_WIFI_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

/**
 * Joins a network the way Android allows apps to: it is added as a saved network (Android
 * asks once to confirm), and the phone then connects to it.
 */
private fun connect(context: Context, n: WifiNet, password: String?) {
    val b = WifiNetworkSuggestion.Builder().setSsid(n.ssid)
    if (password != null) {
        if (n.security == "WPA3") b.setWpa3Passphrase(password) else b.setWpa2Passphrase(password)
    }
    val suggestion = runCatching { b.build() }.getOrNull() ?: return
    if (Build.VERSION.SDK_INT >= 30) {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_WIFI_ADD_NETWORKS)
                    .putParcelableArrayListExtra(Settings.EXTRA_WIFI_NETWORK_LIST, arrayListOf(suggestion))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    } else {
        runCatching { context.applicationContext.getSystemService(WifiManager::class.java)?.addNetworkSuggestions(listOf(suggestion)) }
    }
}
