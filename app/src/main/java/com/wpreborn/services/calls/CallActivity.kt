package com.wpreborn.services.calls

import android.os.Bundle
import android.os.PowerManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.wpreborn.R
import com.wpreborn.core.icons.WpIcons
import com.wpreborn.core.theme.Wp
import com.wpreborn.core.theme.WpTheme
import com.wpreborn.core.theme.WpType
import com.wpreborn.core.ui.Thumbnail
import com.wpreborn.core.ui.WpIcon
import com.wpreborn.core.ui.WpText
import com.wpreborn.core.ui.wpClickable
import com.wpreborn.data.contacts.ContactsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * The call screen, after Windows Phone 8.1: the caller's name in large light type over their
 * picture (or the accent colour), the call time, square buttons for speaker, mute, keypad and
 * hold, and the red "end call" bar. An incoming call has "answer" and "ignore".
 */
class CallActivity : ComponentActivity() {
    private var proximity: PowerManager.WakeLock? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Over the lock screen, waking the phone, like the phone's own call screen.
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        proximity = runCatching {
            getSystemService(PowerManager::class.java)?.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "mozaik:call")
        }.getOrNull()
        setContent { WpTheme { CallScreen(onProximity = ::setProximity, onFinish = ::finish) } }
    }

    /** Screen off at the ear while talking without the speaker. */
    private fun setProximity(on: Boolean) {
        val w = proximity ?: return
        runCatching {
            if (on && !w.isHeld) w.acquire(3 * 60 * 60 * 1000L)
            if (!on && w.isHeld) w.release()
        }
    }

    override fun onDestroy() {
        setProximity(false)
        super.onDestroy()
    }
}

/** Who is calling: name, picture and the kind of number ("Mobile"), from the contacts. */
private data class Caller(val name: String, val photo: android.net.Uri?, val type: String)

private fun lookup(context: android.content.Context, number: String): Caller? {
    if (number.isBlank()) return null
    return runCatching {
        val uri = android.net.Uri.withAppendedPath(android.provider.ContactsContract.PhoneLookup.CONTENT_FILTER_URI, android.net.Uri.encode(number))
        context.contentResolver.query(
            uri,
            arrayOf(
                android.provider.ContactsContract.PhoneLookup.DISPLAY_NAME,
                android.provider.ContactsContract.PhoneLookup.PHOTO_URI,
                android.provider.ContactsContract.PhoneLookup.TYPE,
                android.provider.ContactsContract.PhoneLookup.LABEL,
            ),
            null, null, null,
        )?.use { c ->
            if (!c.moveToFirst()) return@use null
            val type = android.provider.ContactsContract.CommonDataKinds.Phone.getTypeLabel(context.resources, c.getInt(2), c.getString(3)).toString()
            Caller(c.getString(0).orEmpty(), c.getString(1)?.let(android.net.Uri::parse), type)
        }
    }.getOrNull()
}

/**
 * The Windows Phone 8.1 call screen: time (in the accent colour) and network at the top, the
 * name in very large light type and "Mobile: number" under it, over the caller's picture
 * when there is one; at the bottom a grid of dark squares (speaker, mute, Bluetooth / hold,
 * add call, message) and a row with "end call" in the accent colour and the keypad.
 */
@Composable
private fun CallScreen(onProximity: (Boolean) -> Unit, onFinish: () -> Unit) {
    val context = LocalContext.current
    val calls by CallManager.state.collectAsState()
    val audio by CallManager.audio.collectAsState()
    val call = calls.firstOrNull { it.ringing } ?: calls.firstOrNull { !it.holding && !it.ended } ?: calls.firstOrNull()
    var lastNumber by remember { mutableStateOf("") }
    if (call != null) lastNumber = call.number
    LaunchedEffect(call == null || call.ended) {
        if (call == null || call.ended) {
            delay(1200)
            if (CallManager.state.value.none { !it.ended }) onFinish()
        }
    }
    val number = call?.number ?: lastNumber
    val caller by produceState<Caller?>(null, number) { value = withContext(Dispatchers.IO) { lookup(context, number) } }
    val carrier = remember { runCatching { context.getSystemService(android.telephony.TelephonyManager::class.java)?.networkOperatorName }.getOrNull().orEmpty() }
    val speaker = audio?.route == android.telecom.CallAudioState.ROUTE_SPEAKER
    val bluetooth = audio?.route == android.telecom.CallAudioState.ROUTE_BLUETOOTH
    val btAvailable = ((audio?.supportedRouteMask ?: 0) and android.telecom.CallAudioState.ROUTE_BLUETOOTH) != 0
    val muted = audio?.isMuted == true
    LaunchedEffect(call?.active == true, speaker, bluetooth) { onProximity(call?.active == true && !speaker && !bluetooth) }
    var keypad by remember { mutableStateOf(false) }
    var typed by remember { mutableStateOf("") }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(500)
        }
    }
    val dark = Color(0xFF333333)

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        caller?.photo?.let { photo ->
            Thumbnail(photo, Modifier.fillMaxSize(), sizePx = 1080, contentScale = ContentScale.Crop)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.55f), 0.35f to Color.Transparent)))
        }
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                val status = when {
                    call == null || call.ended -> stringResource(R.string.call_ended)
                    call.ringing -> stringResource(R.string.call_incoming)
                    call.dialing -> stringResource(R.string.call_calling)
                    call.holding -> stringResource(R.string.call_on_hold)
                    call.connectedAt > 0 -> duration(now - call.connectedAt)
                    else -> ""
                }
                WpText(status, style = WpType.Normal, color = Wp.accent, modifier = Modifier.weight(1f))
                WpText(carrier, style = WpType.Normal, color = Color.White, maxLines = 1)
            }
            Column(Modifier.padding(start = 20.dp, end = 20.dp)) {
                val shownNumber = com.wpreborn.core.PhoneFormat.br(number)
                val name = caller?.name?.ifBlank { null }
                // A name wraps between words; a number stays on one line, smaller if long.
                val title = name ?: shownNumber.ifBlank { stringResource(R.string.call_unknown) }
                val big = when {
                    name != null -> 54
                    title.length <= 10 -> 54
                    title.length <= 14 -> 44
                    title.length <= 18 -> 36
                    else -> 30
                }
                WpText(
                    title,
                    style = WpType.Header.copy(fontSize = big.sp, lineHeight = (big + 6).sp, fontWeight = FontWeight.Light),
                    color = Color.White, maxLines = if (name != null) 2 else 1, softWrap = name != null, overflow = TextOverflow.Ellipsis,
                )
                // The kind of number and the number, only when the name is shown above it.
                if (name != null && number.isNotBlank()) {
                    WpText(
                        "${caller?.type}: $shownNumber",
                        style = WpType.Normal, color = Color.White, maxLines = 1,
                    )
                }
                if (keypad && typed.isNotEmpty()) {
                    WpText(typed, style = WpType.Large, color = Color.White, maxLines = 1, modifier = Modifier.padding(top = 6.dp))
                }
            }
            Spacer(Modifier.weight(1f))
            val gap = 3.dp
            if (call != null && call.ringing) {
                Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                    Bar(stringResource(R.string.call_text_reply), dark) {
                        com.wpreborn.apps.phone.PhoneActivity.text(context, call.number)
                        CallManager.reject(call.call)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        Bar(stringResource(R.string.call_answer), Wp.accent, Modifier.weight(1f)) { CallManager.answer(call.call) }
                        Bar(stringResource(R.string.call_decline), dark, Modifier.weight(1f)) { CallManager.reject(call.call) }
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                    if (keypad && call != null) {
                        Keypad(dark) { d -> typed += d; CallManager.tone(call.call, d) }
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                            Square(WpIcons.Speaker, stringResource(R.string.call_speaker), speaker, dark, Modifier.weight(1f)) { CallManager.toggleSpeaker() }
                            Square(WpIcons.Mute, stringResource(R.string.call_mute), muted, dark, Modifier.weight(1f)) { CallManager.toggleMute() }
                            Square(WpIcons.Bluetooth, "Bluetooth", bluetooth, dark, Modifier.weight(1f), enabled = btAvailable) { CallManager.toggleBluetooth() }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                            Square(WpIcons.Pause, stringResource(R.string.call_hold), call?.holding == true, dark, Modifier.weight(1f), enabled = call?.canHold == true || call?.holding == true) {
                                call?.let { CallManager.toggleHold(it) }
                            }
                            Square(WpIcons.Add, stringResource(R.string.call_add), false, dark, Modifier.weight(1f), enabled = call?.active == true) {
                                runCatching {
                                    context.startActivity(
                                        android.content.Intent(android.content.Intent.ACTION_DIAL).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                            .setClass(context, com.wpreborn.apps.phone.PhoneActivity::class.java),
                                    )
                                }
                            }
                            Square(WpIcons.Messaging, stringResource(R.string.call_message), false, dark, Modifier.weight(1f), enabled = number.isNotBlank()) {
                                com.wpreborn.apps.phone.PhoneActivity.text(context, number)
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                        Bar(stringResource(R.string.call_end), Wp.accent, Modifier.weight(2f)) { call?.let { CallManager.hangUp(it.call) } ?: onFinish() }
                        Box(
                            Modifier.weight(1f).height(72.dp).background(if (keypad) Wp.accent else dark).wpClickable { keypad = !keypad },
                            contentAlignment = Alignment.Center,
                        ) { WpIcon(WpIcons.Keypad, tint = Color.White, size = 26.dp) }
                    }
                }
            }
        }
    }
}

private fun duration(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%02d:%02d".format(s / 60, s % 60)
}

@Composable
private fun Bar(label: String, color: Color, modifier: Modifier = Modifier.fillMaxWidth(), onClick: () -> Unit) {
    Box(
        modifier.height(72.dp).background(color).wpClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { WpText(label, style = WpType.Large, color = Color.White) }
}

/** A call button: the glyph with its name under it, centred; filled with the accent while on. */
@Composable
private fun Square(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    on: Boolean,
    color: Color,
    modifier: Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val fg = if (enabled) Color.White else Color.White.copy(alpha = 0.35f)
    Column(
        modifier
            .height(76.dp)
            .background(if (on) Wp.accent else color)
            .wpClickable(enabled = enabled, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        WpIcon(icon, tint = fg, size = 22.dp)
        Spacer(Modifier.height(6.dp))
        WpText(label, style = WpType.Small, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Keypad tones during a call (menus, codes), in the same dark squares. */
@Composable
private fun Keypad(color: Color, onDigit: (Char) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        listOf("123", "456", "789", "*0#").forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                row.forEach { d ->
                    Box(
                        Modifier.weight(1f).height(58.dp).background(color).wpClickable { onDigit(d) },
                        contentAlignment = Alignment.Center,
                    ) { WpText(d.toString(), style = WpType.Large.copy(fontSize = 28.sp, fontWeight = FontWeight.Light), color = Color.White) }
                }
            }
        }
    }
}
