package com.wpreborn.apps.phone

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.provider.ContactsContract
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wpreborn.R
import com.wpreborn.apps.messages.MessagingActivity
import com.wpreborn.apps.people.PeopleActivity
import com.wpreborn.core.icons.WpIcons
import com.wpreborn.core.motion.slideInStaggered
import com.wpreborn.core.motion.tilt
import com.wpreborn.core.theme.Wp
import com.wpreborn.core.theme.WpType
import com.wpreborn.core.ui.AppBarButton
import com.wpreborn.core.ui.AppBarMenuItem
import com.wpreborn.core.ui.CenteredMessage
import com.wpreborn.core.ui.ContactAvatar
import com.wpreborn.core.ui.Thumbnail
import com.wpreborn.core.ui.WpAppBar
import com.wpreborn.core.ui.WpFormat
import com.wpreborn.core.ui.WpIcon
import com.wpreborn.core.ui.WpListItem
import com.wpreborn.core.ui.WpPageHeader
import com.wpreborn.core.ui.WpPivot
import com.wpreborn.core.ui.WpText
import com.wpreborn.core.ui.wpClickable
import com.wpreborn.apps.common.WpActivity
import com.wpreborn.data.calls.CallEntry
import com.wpreborn.data.calls.CallLogRepository
import com.wpreborn.data.calls.CallType
import com.wpreborn.data.contacts.Contact
import com.wpreborn.data.contacts.ContactsRepository
import com.wpreborn.navigation.WpNavHost
import com.wpreborn.navigation.rememberNavigator
import com.wpreborn.permissions.PermissionGate
import com.wpreborn.permissions.Perms
import com.wpreborn.permissions.hasPermissions
import com.wpreborn.permissions.rememberPermissionState
import kotlinx.coroutines.launch

private sealed interface PhonePage {
    data object Home : PhonePage
    data class Keypad(val number: String = "") : PhonePage
    data class Detail(val number: String, val name: String?) : PhonePage
}

/**
 * Phone: call history and speed dial, the dial pad and call details. Calls are placed by the
 * Android telecom stack (ACTION_CALL, or ACTION_DIAL when CALL_PHONE was not granted), so
 * the in-call experience, emergency calls and carrier features stay intact.
 */
class PhoneActivity : WpActivity() {

    @Composable
    override fun Content() {
        val initial = remember {
            val data = intent?.data
            if (intent?.action == Intent.ACTION_DIAL || intent?.action == Intent.ACTION_VIEW) {
                PhonePage.Keypad(data?.schemeSpecificPart.orEmpty())
            } else {
                PhonePage.Home
            }
        }
        val nav = rememberNavigator<PhonePage>(initial)
        WpNavHost(nav) { page ->
            when (page) {
                PhonePage.Home -> PhoneHome(
                    onKeypad = { nav.navigate(PhonePage.Keypad()) },
                    onDetail = { n, name -> nav.navigate(PhonePage.Detail(n, name)) },
                )
                is PhonePage.Keypad -> KeypadPage(page.number)
                is PhonePage.Detail -> CallDetailPage(page.number, page.name)
            }
        }
    }

    companion object {
        /** Places a call through Android; falls back to the system dialer without CALL_PHONE. */
        @SuppressLint("MissingPermission")
        fun call(context: Context, number: String) {
            if (number.isBlank()) return
            val uri = Uri.fromParts("tel", number, null)
            val intent = if (context.hasPermissions(Perms.CALL)) Intent(Intent.ACTION_CALL, uri) else Intent(Intent.ACTION_DIAL, uri)
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }

        fun text(context: Context, number: String) {
            if (com.wpreborn.BuildConfig.LITE) {
                MessagingActivity.openSystemMessaging(context, number)
                return
            }
            // ZUNEX Messaging may be turned off in the Store: then the phone's app writes.
            runCatching {
                context.startActivity(
                    Intent(context, MessagingActivity::class.java)
                        .putExtra(MessagingActivity.EXTRA_ADDRESS, number)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }.onFailure { MessagingActivity.openSystemMessaging(context, number) }
        }
    }
}

@Composable
private fun PhoneHome(onKeypad: () -> Unit, onDetail: (String, String?) -> Unit) {
    val context = LocalContext.current
    val logPerm = rememberPermissionState(Perms.CALL_LOG)
    val contactsPerm = rememberPermissionState(Perms.CONTACTS)
    val callPerm = rememberPermissionState(Perms.CALL)
    val repo = remember { CallLogRepository(context) }
    var calls by remember { mutableStateOf<List<CallEntry>?>(null) }
    LaunchedEffect(logPerm.granted) {
        if (logPerm.granted) {
            repo.changes().collect {
                calls = repo.recent()
            }
        }
    }
    LaunchedEffect(logPerm.granted) { if (logPerm.granted) repo.markMissedSeen() }
    val favorites by produceState<List<Contact>>(emptyList(), contactsPerm.granted) {
        if (contactsPerm.granted) value = ContactsRepository(context).all().filter { it.starred && it.hasPhone }
    }

    Column(Modifier.fillMaxSize()) {
        if (!com.wpreborn.BuildConfig.LITE) DefaultDialerBanner()
        WpPivot(
            title = stringResource(R.string.app_phone),
            headers = listOf(stringResource(R.string.phone_history), stringResource(R.string.phone_speed_dial)),
            modifier = Modifier.weight(1f),
        ) { index ->
            when (index) {
                0 -> if (com.wpreborn.BuildConfig.LITE) {
                    LiteHistory()
                } else PermissionGate(logPerm, stringResource(R.string.perm_call_log), Modifier.padding(start = 20.dp)) {
                    val list = calls
                    if (list != null && list.isEmpty()) {
                        CenteredMessage(stringResource(R.string.phone_no_history), Modifier.padding(start = 20.dp))
                    } else {
                        LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 12.dp, end = 12.dp, bottom = 16.dp)) {
                            itemsIndexed(list.orEmpty(), key = { _, c -> c.id }) { i, c ->
                                CallRow(c, Modifier.slideInStaggered(i)) { onDetail(c.number, c.name) }
                            }
                        }
                    }
                }
                else -> PermissionGate(contactsPerm, stringResource(R.string.perm_contacts), Modifier.padding(start = 20.dp)) {
                    if (favorites.isEmpty()) {
                        CenteredMessage(stringResource(R.string.phone_no_speed_dial), Modifier.padding(start = 20.dp))
                    } else {
                        SpeedDial(favorites)
                    }
                }
            }
        }
        WpAppBar(
            buttons = listOf(
                AppBarButton(WpIcons.Keypad, stringResource(R.string.phone_keypad), onClick = onKeypad),
                AppBarButton(WpIcons.People, stringResource(R.string.app_people)) {
                    runCatching { context.startActivity(Intent(context, PeopleActivity::class.java)) }
                },
            ),
            menuItems = buildList {
                if (!callPerm.granted && !com.wpreborn.BuildConfig.LITE) add(AppBarMenuItem(context.getString(R.string.phone_allow_direct_calls)) { callPerm.request() })
                add(AppBarMenuItem(context.getString(R.string.phone_call_settings)) {
                    context.startActivity(Intent(android.telecom.TelecomManager.ACTION_SHOW_CALL_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                })
            },
        )
    }
}

/**
 * Until Mozaik is the default Phone app, Android's own call screen shows the calls: offer to
 * make it the default (the role asks the user once), so calls use Mozaik's call screen.
 */
@Composable
private fun DefaultDialerBanner() {
    val context = LocalContext.current
    val rm = remember { context.getSystemService(android.app.role.RoleManager::class.java) }
    fun held() = rm == null || !rm.isRoleAvailable(android.app.role.RoleManager.ROLE_DIALER) || rm.isRoleHeld(android.app.role.RoleManager.ROLE_DIALER)
    var isDefault by remember { mutableStateOf(held()) }
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    LaunchedEffect(owner) {
        owner.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) { isDefault = held() }
    }
    if (isDefault) return
    Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 8.dp)) {
        com.wpreborn.core.ui.WpText(stringResource(R.string.phone_not_default), style = com.wpreborn.core.theme.WpType.Subtle, color = com.wpreborn.core.theme.Wp.palette.subtle)
        Spacer(Modifier.height(8.dp))
        com.wpreborn.core.ui.WpButton(stringResource(R.string.phone_make_default)) {
            runCatching {
                (context as? android.app.Activity)?.startActivityForResult(rm!!.createRequestRoleIntent(android.app.role.RoleManager.ROLE_DIALER), 8)
            }
        }
    }
}

/** Lite edition: no call log permission, so the history stays in the Android Phone app. */
@Composable
private fun LiteHistory() {
    val context = LocalContext.current
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp)) {
        WpText(stringResource(R.string.phone_lite_history), style = WpType.Large, color = Wp.palette.subtle)
        Spacer(Modifier.height(16.dp))
        com.wpreborn.core.ui.WpButton(stringResource(R.string.phone_open_history)) {
            try {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW).setType(android.provider.CallLog.Calls.CONTENT_TYPE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            } catch (_: Exception) {
                context.startActivity(Intent(Intent.ACTION_DIAL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }
}

@Composable
private fun CallRow(c: CallEntry, modifier: Modifier, onOpen: () -> Unit) {
    val context = LocalContext.current
    val missed = c.type == CallType.Missed
    val label = when (c.type) {
        CallType.Missed -> stringResource(R.string.call_missed)
        CallType.Outgoing -> stringResource(R.string.call_outgoing)
        CallType.Rejected -> stringResource(R.string.call_rejected)
        else -> stringResource(R.string.call_incoming)
    }
    Row(modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .padding(start = 8.dp, end = 12.dp)
                .wpClickable { PhoneActivity.call(context, c.number) },
        ) {
            WpIcon(WpIcons.Phone, size = 30.dp, tint = if (missed) Wp.accent else Wp.palette.foreground)
        }
        Column(Modifier.weight(1f).wpClickable(onClick = onOpen)) {
            WpText(c.name ?: com.wpreborn.core.PhoneFormat.br(c.number).ifBlank { stringResource(R.string.unknown_number) }, style = WpType.ExtraLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                WpIcon(
                    when (c.type) {
                        CallType.Outgoing -> WpIcons.CallOutgoing
                        CallType.Missed -> WpIcons.CallMissed
                        else -> WpIcons.CallIncoming
                    },
                    size = 14.dp,
                    tint = if (missed) Wp.accent else Wp.palette.subtle,
                )
                Spacer(Modifier.width(6.dp))
                WpText(
                    "$label, ${WpFormat.shortStamp(context, c.date)}",
                    style = WpType.Subtle,
                    color = if (missed) Wp.accent else Wp.palette.subtle,
                )
            }
        }
    }
}

@Composable
private fun SpeedDial(favorites: List<Contact>) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(favorites, key = { it.id }) { contact ->
            Box(
                Modifier
                    .aspectRatio(1f)
                    .tilt()
                    .background(Wp.accent)
                    .wpClickable(tilt = false) {
                        scope.launch {
                            val number = ContactsRepository(context).detail(contact).phones.firstOrNull()?.number
                            if (number != null) PhoneActivity.call(context, number)
                        }
                    },
            ) {
                Thumbnail(contact.photoUri ?: contact.thumbUri, Modifier.fillMaxSize(), sizePx = 384) {
                    WpIcon(WpIcons.Person, Modifier.align(Alignment.Center), tint = Color.White, size = 40.dp)
                }
                WpText(
                    contact.name,
                    style = WpType.TileLabel,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.35f))
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                )
            }
        }
    }
}

private val KEYS = listOf(
    "1" to "", "2" to "ABC", "3" to "DEF",
    "4" to "GHI", "5" to "JKL", "6" to "MNO",
    "7" to "PQRS", "8" to "TUV", "9" to "WXYZ",
    "*" to "", "0" to "+", "#" to "",
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun KeypadPage(initial: String) {
    val context = LocalContext.current
    var number by remember { mutableStateOf(initial) }
    val tone = remember {
        try {
            ToneGenerator(AudioManager.STREAM_DTMF, 70)
        } catch (_: Exception) {
            null
        }
    }
    DisposableEffect(Unit) { onDispose { tone?.release() } }
    val match by produceState<String?>(null, number) {
        value = if (number.length >= 3 && context.hasPermissions(Perms.CONTACTS)) ContactsRepository(context).lookupNumber(number)?.first else null
    }
    fun press(key: String) {
        number += key
        val t = when (key) {
            "*" -> ToneGenerator.TONE_DTMF_S
            "#" -> ToneGenerator.TONE_DTMF_P
            "+" -> ToneGenerator.TONE_DTMF_0
            else -> ToneGenerator.TONE_DTMF_0 + key.toInt()
        }
        tone?.startTone(t, 120)
    }

    Column(Modifier.fillMaxSize().navigationBarsPadding()) {
        WpPageHeader(stringResource(R.string.app_phone), null)
        Box(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 20.dp), contentAlignment = Alignment.BottomStart) {
            Column {
                WpText(match.orEmpty(), style = WpType.Normal, color = Wp.accent)
                WpText(
                    number,
                    style = WpType.Header.copy(fontSize = if (number.length > 12) 34.sp else 46.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            KEYS.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    row.forEach { (digit, letters) ->
                        Row(
                            Modifier
                                .weight(1f)
                                .height(62.dp)
                                .tilt()
                                .background(Wp.palette.raised)
                                .combinedClickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onLongClick = if (digit == "0") ({ press("+") }) else null,
                                    onClick = { press(digit) },
                                )
                                .padding(start = 18.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            WpText(digit, style = WpType.Keypad)
                            Spacer(Modifier.width(10.dp))
                            WpText(letters, style = WpType.Small, color = Wp.palette.subtle)
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    Modifier
                        .weight(2f)
                        .height(58.dp)
                        .tilt()
                        .background(Wp.accent)
                        .wpClickable(tilt = false) { PhoneActivity.call(context, number) },
                    contentAlignment = Alignment.Center,
                ) {
                    WpText(stringResource(R.string.phone_call), style = WpType.Large.copy(fontWeight = FontWeight.Normal), color = Color.White)
                }
                Box(
                    Modifier
                        .weight(1f)
                        .height(58.dp)
                        .tilt()
                        .background(Wp.palette.raised)
                        .combinedClickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onLongClick = { number = "" },
                            onClick = { number = number.dropLast(1) },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    WpIcon(WpIcons.Backspace, size = 30.dp)
                }
            }
            if (number.isNotEmpty() && match == null) {
                WpText(
                    stringResource(R.string.phone_save),
                    style = WpType.Normal,
                    color = Wp.accent,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .wpClickable {
                            // A new contact with this number, in People.
                            context.startActivity(
                                Intent(context, com.wpreborn.apps.people.PeopleActivity::class.java)
                                    .putExtra(com.wpreborn.apps.people.PeopleActivity.EXTRA_NEW_NUMBER, number),
                            )
                        }
                        .padding(vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun CallDetailPage(number: String, name: String?) {
    val context = LocalContext.current
    val calls by produceState<List<CallEntry>>(emptyList(), number) {
        val target = ContactsRepository.normalize(number)
        value = CallLogRepository(context).recent(500).filter { ContactsRepository.normalize(it.number) == target }
    }
    val photo = calls.firstOrNull()?.photoUri
    Column(Modifier.fillMaxSize()) {
        WpPageHeader(stringResource(R.string.app_phone), name ?: com.wpreborn.core.PhoneFormat.br(number))
        LazyColumn(Modifier.weight(1f).padding(horizontal = 20.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
                    ContactAvatar(photo, size = 96.dp)
                }
                WpListItem(stringResource(R.string.phone_call_number), subtitle = com.wpreborn.core.PhoneFormat.br(number), subtitleAccent = true) { PhoneActivity.call(context, number) }
                WpListItem(stringResource(R.string.phone_text_number), subtitle = com.wpreborn.core.PhoneFormat.br(number), subtitleAccent = true) { PhoneActivity.text(context, number) }
                Spacer(Modifier.height(16.dp))
                WpText(stringResource(R.string.phone_history), style = WpType.GroupHeader, color = Wp.palette.subtle)
            }
            itemsIndexed(calls, key = { _, c -> c.id }) { i, c ->
                val label = when (c.type) {
                    CallType.Missed -> stringResource(R.string.call_missed)
                    CallType.Outgoing -> stringResource(R.string.call_outgoing)
                    CallType.Rejected -> stringResource(R.string.call_rejected)
                    else -> stringResource(R.string.call_incoming)
                }
                Column(Modifier.padding(vertical = 6.dp).slideInStaggered(i)) {
                    WpText(label, style = WpType.Large)
                    WpText(
                        "${WpFormat.dayLabel(context, c.date)}, ${WpFormat.time(context, c.date)}" +
                            if (c.durationSec > 0) " · ${WpFormat.duration(c.durationSec * 1000)}" else "",
                        style = WpType.Subtle,
                        color = Wp.palette.subtle,
                    )
                }
            }
        }
    }
}
