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

@Composable
private fun CallScreen(onProximity: (Boolean) -> Unit, onFinish: () -> Unit) {
    val context = LocalContext.current
    val calls by CallManager.state.collectAsState()
    val audio by CallManager.audio.collectAsState()
    val call = calls.firstOrNull { it.ringing } ?: calls.firstOrNull { !it.holding && !it.ended } ?: calls.firstOrNull()
    // Gone: a moment of "call ended", then close.
    var lastNumber by remember { mutableStateOf("") }
    if (call != null) lastNumber = call.number
    LaunchedEffect(call == null || call.ended) {
        if (call == null || call.ended) {
            delay(1200)
            if (CallManager.state.value.none { !it.ended }) onFinish()
        }
    }
    val number = call?.number ?: lastNumber
    val contact by produceState<Pair<String, android.net.Uri?>?>(null, number) {
        value = withContext(Dispatchers.IO) { runCatching { ContactsRepository(context).lookupNumber(number) }.getOrNull() }
    }
    val speaker = audio?.route == android.telecom.CallAudioState.ROUTE_SPEAKER
    val muted = audio?.isMuted == true
    LaunchedEffect(call?.active == true, speaker) { onProximity(call?.active == true && !speaker) }
    var keypad by remember { mutableStateOf(false) }
    var typed by remember { mutableStateOf("") }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(call?.connectedAt) {
        while (true) {
            now = System.currentTimeMillis()
            delay(500)
        }
    }

    val bg = lerp(Wp.accent, Color.Black, 0.35f)
    Box(Modifier.fillMaxSize().background(bg)) {
        // The caller's picture fills the top, fading into the colour below.
        contact?.second?.let { photo ->
            Box(Modifier.fillMaxWidth().aspectRatio(1f)) {
                Thumbnail(photo, Modifier.fillMaxSize(), sizePx = 720, contentScale = ContentScale.Crop)
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to bg)))
            }
        }
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
            Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp)) {
                WpText(stringResource(R.string.app_phone).uppercase(), style = WpType.AppTitle, color = Color.White)
            }
            Spacer(Modifier.weight(1f))
            Column(Modifier.padding(horizontal = 20.dp)) {
                val status = when {
                    call == null || call.ended -> stringResource(R.string.call_ended)
                    call.ringing -> stringResource(R.string.call_incoming)
                    call.dialing -> stringResource(R.string.call_calling)
                    call.holding -> stringResource(R.string.call_on_hold)
                    call.connectedAt > 0 -> duration(now - call.connectedAt)
                    else -> ""
                }
                WpText(status, style = WpType.Large, color = Color.White.copy(alpha = 0.85f))
                WpText(
                    contact?.first ?: number.ifBlank { stringResource(R.string.call_unknown) },
                    style = WpType.Header.copy(fontSize = 46.sp, lineHeight = 52.sp, fontWeight = FontWeight.Light),
                    color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                if (contact != null && number.isNotBlank()) {
                    WpText(number, style = WpType.Large, color = Color.White.copy(alpha = 0.8f), maxLines = 1)
                }
                if (keypad && typed.isNotEmpty()) {
                    WpText(typed, style = WpType.Large, color = Color.White, maxLines = 1, modifier = Modifier.padding(top = 6.dp))
                }
            }
            Spacer(Modifier.height(20.dp))
            if (call != null && call.ringing) {
                Bar(stringResource(R.string.call_answer), Color(0xFF60A917)) { CallManager.answer(call.call) }
                Bar(stringResource(R.string.call_decline), Color(0xFFE51400)) { CallManager.reject(call.call) }
            } else {
                if (keypad && call != null) {
                    Keypad { d -> typed += d; CallManager.tone(call.call, d) }
                } else {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Square(WpIcons.Speaker, stringResource(R.string.call_speaker), speaker, Modifier.weight(1f)) { CallManager.toggleSpeaker() }
                        Square(WpIcons.Mute, stringResource(R.string.call_mute), muted, Modifier.weight(1f)) { CallManager.toggleMute() }
                        Square(WpIcons.Pause, stringResource(R.string.call_hold), call?.holding == true, Modifier.weight(1f), enabled = call?.canHold == true || call?.holding == true) {
                            call?.let { CallManager.toggleHold(it) }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Square(WpIcons.Keypad, stringResource(R.string.call_keypad), keypad, Modifier.weight(1f)) { keypad = !keypad }
                    Box(
                        Modifier.weight(2f).height(72.dp).background(Color(0xFFE51400)).wpClickable { call?.let { CallManager.hangUp(it.call) } ?: onFinish() },
                        contentAlignment = Alignment.Center,
                    ) { WpText(stringResource(R.string.call_end), style = WpType.Large, color = Color.White) }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

private fun duration(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}

@Composable
private fun Bar(label: String, color: Color, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 8.dp).height(64.dp).background(color).wpClickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { WpText(label, style = WpType.Large, color = Color.White) }
}

/** A square call button: glyph and label, filled with the accent while on. */
@Composable
private fun Square(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    on: Boolean,
    modifier: Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Column(
        modifier
            .height(72.dp)
            .background(if (on) Wp.accent else Color.White.copy(alpha = 0.12f))
            .wpClickable(enabled = enabled, onClick = onClick)
            .padding(8.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        WpIcon(icon, tint = if (enabled) Color.White else Color.White.copy(alpha = 0.4f), size = 24.dp)
        WpText(label, style = WpType.Small, color = if (enabled) Color.White else Color.White.copy(alpha = 0.4f), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Keypad tones during a call (menus, codes). */
@Composable
private fun Keypad(onDigit: (Char) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("123", "456", "789", "*0#").forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { d ->
                    Box(
                        Modifier.weight(1f).height(52.dp).background(Color.White.copy(alpha = 0.12f)).wpClickable { onDigit(d) },
                        contentAlignment = Alignment.Center,
                    ) { WpText(d.toString(), style = WpType.Large.copy(fontSize = 26.sp), color = Color.White) }
                }
            }
        }
    }
}
