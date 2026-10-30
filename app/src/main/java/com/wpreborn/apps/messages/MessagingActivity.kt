package com.wpreborn.apps.messages

import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wpreborn.R
import com.wpreborn.apps.common.WpActivity
import com.wpreborn.apps.phone.PhoneActivity
import com.wpreborn.core.icons.WpIcons
import com.wpreborn.core.motion.LoadingDots
import com.wpreborn.core.motion.slideInStaggered
import com.wpreborn.core.motion.slideUpFadeIn
import com.wpreborn.core.theme.Wp
import com.wpreborn.core.theme.WpType
import com.wpreborn.core.theme.darker
import com.wpreborn.core.ui.AppBarButton
import com.wpreborn.core.ui.AppBarMenuItem
import com.wpreborn.core.ui.CenteredMessage
import com.wpreborn.core.ui.ContactAvatar
import com.wpreborn.core.ui.ContextMenuItem
import com.wpreborn.core.ui.WpAppBar
import com.wpreborn.core.ui.WpButton
import com.wpreborn.core.ui.WpContextMenu
import com.wpreborn.core.ui.WpFormat
import com.wpreborn.core.ui.WpPageHeader
import com.wpreborn.core.ui.WpText
import com.wpreborn.core.ui.WpTextBox
import com.wpreborn.core.ui.wpCombinedClickable
import com.wpreborn.data.contacts.Contact
import com.wpreborn.data.contacts.ContactsRepository
import com.wpreborn.data.sms.SmsMessage
import com.wpreborn.data.sms.SmsRepository
import com.wpreborn.data.sms.SmsStatus
import com.wpreborn.data.sms.SmsThread
import com.wpreborn.navigation.WpNavHost
import com.wpreborn.navigation.rememberNavigator
import com.wpreborn.permissions.PermissionGate
import com.wpreborn.permissions.Perms
import com.wpreborn.permissions.rememberPermissionState
import com.wpreborn.services.sms.SmsNotifications
import kotlinx.coroutines.launch
import androidx.lifecycle.repeatOnLifecycle

private sealed interface MsgPage {
    data object Threads : MsgPage
    data class Conversation(val threadId: Long, val address: String) : MsgPage
    data class New(val address: String = "", val body: String = "") : MsgPage
}

/**
 * Messaging: conversation list, conversation with WP speech bubbles, and new message.
 * Handles sms:/smsto: links, which is also what Android requires from a default SMS app.
 */
class MessagingActivity : WpActivity() {

    /** The lite edition has no SMS permissions: open the Android messaging app instead. */
    override fun redirect(): Boolean {
        if (!com.wpreborn.BuildConfig.LITE) return false
        openSystemMessaging(this, intent?.getStringExtra(EXTRA_ADDRESS))
        return true
    }

    @Composable
    override fun Content() {
        val start = remember { startPage(intent) }
        val nav = rememberNavigator(start)
        WpNavHost(nav) { page ->
            when (page) {
                MsgPage.Threads -> ThreadsPage(
                    onOpen = { nav.navigate(MsgPage.Conversation(it.threadId, it.address)) },
                    onNew = { nav.navigate(MsgPage.New()) },
                )
                is MsgPage.Conversation -> ConversationPage(page.threadId, page.address)
                is MsgPage.New -> NewMessagePage(page.address, page.body) { threadId, address ->
                    nav.replace(MsgPage.Conversation(threadId, address))
                }
            }
        }
    }

    private fun startPage(intent: Intent?): MsgPage {
        val repo = SmsRepository(this)
        val threadId = intent?.getLongExtra(EXTRA_THREAD_ID, -1L) ?: -1L
        val extraAddress = intent?.getStringExtra(EXTRA_ADDRESS)
        val uriAddress = intent?.data?.schemeSpecificPart?.substringBefore('?')?.takeIf { it.isNotBlank() }
        val body = intent?.getStringExtra("sms_body") ?: intent?.getStringExtra(Intent.EXTRA_TEXT) ?: ""
        val address = uriAddress ?: extraAddress
        return when {
            threadId > 0 -> MsgPage.Conversation(threadId, address ?: "")
            !address.isNullOrBlank() && body.isBlank() -> {
                val id = repo.threadIdFor(address)
                if (id > 0) MsgPage.Conversation(id, address) else MsgPage.New(address)
            }
            !address.isNullOrBlank() || body.isNotBlank() -> MsgPage.New(address.orEmpty(), body)
            else -> MsgPage.Threads
        }
    }

    companion object {
        fun openSystemMessaging(context: android.content.Context, address: String?) {
            val intent = if (!address.isNullOrBlank()) {
                Intent(Intent.ACTION_SENDTO, android.net.Uri.parse("smsto:" + android.net.Uri.encode(address)))
            } else {
                Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MESSAGING)
            }
            try {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (_: Exception) {
            }
        }

        const val EXTRA_THREAD_ID = "thread_id"
        const val EXTRA_ADDRESS = "address"
    }
}

@Composable
private fun DefaultAppBanner(repo: SmsRepository) {
    val context = LocalContext.current
    val activity = context as? android.app.Activity
    var isDefault by remember { mutableStateOf(repo.isDefaultSmsApp()) }
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    LaunchedEffect(owner) {
        owner.lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED) { isDefault = repo.isDefaultSmsApp() }
    }
    if (isDefault) return
    Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 12.dp).slideUpFadeIn()) {
        WpText(stringResource(R.string.messages_not_default), style = WpType.Subtle, color = Wp.palette.subtle)
        Spacer(Modifier.height(8.dp))
        WpButton(stringResource(R.string.messages_make_default)) {
            repo.requestDefaultIntent()?.let { activity?.startActivityForResult(it, 7) }
        }
    }
}

@Composable
private fun ThreadsPage(onOpen: (SmsThread) -> Unit, onNew: () -> Unit) {
    val context = LocalContext.current
    val perm = rememberPermissionState(Perms.SMS)
    val repo = remember { SmsRepository(context) }
    val scope = rememberCoroutineScope()
    var threads by remember { mutableStateOf<List<SmsThread>?>(null) }
    var menuFor by remember { mutableStateOf<SmsThread?>(null) }
    var menuY by remember { mutableFloatStateOf(0f) }
    var top by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(perm.granted) {
        if (perm.granted) repo.changes().collect { threads = repo.threads() }
    }
    Box(Modifier.fillMaxSize().onGloballyPositioned { top = it.boundsInWindow().top }) {
        Column(Modifier.fillMaxSize()) {
            WpPageHeader(null, stringResource(R.string.messages_header))
            Box(Modifier.weight(1f)) {
                PermissionGate(perm, stringResource(R.string.perm_sms), Modifier.padding(start = 20.dp)) {
                    Column {
                        DefaultAppBanner(repo)
                        val list = threads
                        when {
                            list == null -> LoadingDots(Modifier.padding(top = 20.dp))
                            list.isEmpty() -> CenteredMessage(stringResource(R.string.messages_empty), Modifier.padding(start = 20.dp))
                            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 16.dp)) {
                                itemsIndexed(list, key = { _, t -> t.threadId }) { i, t ->
                                    var bottom by remember { mutableFloatStateOf(0f) }
                                    ThreadRow(
                                        t,
                                        Modifier
                                            .onGloballyPositioned { bottom = it.boundsInWindow().bottom }
                                            .slideInStaggered(i),
                                        onClick = { onOpen(t) },
                                        onLongClick = {
                                            menuY = bottom - top
                                            menuFor = t
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
            WpAppBar(
                buttons = listOf(AppBarButton(WpIcons.Add, stringResource(R.string.messages_new), onClick = onNew)),
                menuItems = listOf(
                    AppBarMenuItem(stringResource(R.string.messages_settings)) {
                        repo.requestDefaultIntent()?.let { (context as? android.app.Activity)?.startActivityForResult(it, 7) }
                            ?: context.startActivity(Intent(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
                    },
                ),
            )
        }
        menuFor?.let { t ->
            WpContextMenu(
                items = listOf(
                    ContextMenuItem(stringResource(R.string.delete), enabled = repo.isDefaultSmsApp()) {
                        scope.launch { repo.deleteThread(t.threadId) }
                    },
                    ContextMenuItem(stringResource(R.string.phone_call)) { PhoneActivity.call(context, t.address) },
                ),
                anchorY = menuY,
                onDismiss = { menuFor = null },
            )
        }
    }
}

@Composable
private fun ThreadRow(t: SmsThread, modifier: Modifier, onClick: () -> Unit, onLongClick: () -> Unit) {
    val context = LocalContext.current
    Row(
        modifier
            .fillMaxWidth()
            .wpCombinedClickable(onLongClick = onLongClick, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        ContactAvatar(t.photoUri, size = 52.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Bottom) {
                WpText(
                    t.displayName,
                    style = WpType.Large,
                    color = if (t.unread > 0) Wp.accent else Wp.palette.foreground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                WpText(WpFormat.shortStamp(context, t.date), style = WpType.Small, color = Wp.palette.subtle, modifier = Modifier.padding(start = 8.dp, bottom = 3.dp))
            }
            WpText(t.snippet, style = WpType.Subtle, color = Wp.palette.subtle, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ConversationPage(threadId: Long, address: String) {
    val context = LocalContext.current
    val repo = remember { SmsRepository(context) }
    val scope = rememberCoroutineScope()
    val perm = rememberPermissionState(Perms.SMS)
    var messages by remember { mutableStateOf<List<SmsMessage>>(emptyList()) }
    var draft by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    var resolvedAddress by remember { mutableStateOf(address) }
    val name by produceState(address, resolvedAddress) { value = repo.displayNameFor(resolvedAddress) }

    LaunchedEffect(perm.granted, threadId) {
        if (!perm.granted) return@LaunchedEffect
        SmsNotifications.cancel(context, threadId)
        repo.changes().collect {
            val list = repo.messages(threadId)
            messages = list
            if (resolvedAddress.isBlank()) resolvedAddress = list.firstOrNull()?.address.orEmpty()
            repo.markThreadRead(threadId)
            if (list.isNotEmpty()) listState.scrollToItem(list.lastIndex)
        }
    }

    Column(Modifier.fillMaxSize()) {
        WpPageHeader(name, null)
        Spacer(Modifier.height(8.dp))
        PermissionGate(perm, stringResource(R.string.perm_sms), Modifier.padding(start = 20.dp)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                itemsIndexed(messages, key = { _, m -> m.id }) { _, m ->
                    Bubble(m)
                }
            }
        }
        if (perm.granted) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.Bottom) {
                WpTextBox(
                    draft,
                    { draft = it },
                    Modifier.weight(1f),
                    placeholder = stringResource(R.string.messages_type),
                    singleLine = false,
                    imeAction = ImeAction.Default,
                )
            }
            WpAppBar(
                buttons = listOf(
                    AppBarButton(WpIcons.Send, stringResource(R.string.messages_send), enabled = draft.isNotBlank() && !sending && resolvedAddress.isNotBlank()) {
                        val text = draft
                        sending = true
                        scope.launch {
                            if (repo.send(resolvedAddress, text)) draft = ""
                            sending = false
                        }
                    },
                    AppBarButton(WpIcons.Phone, stringResource(R.string.phone_call)) { PhoneActivity.call(context, resolvedAddress) },
                ),
            )
        }
    }
}

/**
 * Windows Phone speech bubble: a square-cornered block in the accent color with a small
 * triangular tail - top-left for received messages, bottom-right (darker shade) for sent ones.
 */
@Composable
private fun Bubble(m: SmsMessage) {
    val context = LocalContext.current
    val accent = Wp.accent
    val color = if (m.outgoing) accent.darker(0.35f) else accent
    val maxWidth = (LocalConfiguration.current.screenWidthDp * 0.8f).dp
    Box(Modifier.fillMaxWidth(), contentAlignment = if (m.outgoing) Alignment.CenterEnd else Alignment.CenterStart) {
        Column(horizontalAlignment = if (m.outgoing) Alignment.End else Alignment.Start) {
            if (!m.outgoing) Tail(color, up = true)
            Column(
                Modifier
                    .widthIn(max = maxWidth)
                    .background(color)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                WpText(m.body, style = WpType.Normal, color = Color.White)
                Spacer(Modifier.height(4.dp))
                val status = when (m.status) {
                    SmsStatus.Failed -> stringResource(R.string.messages_failed)
                    SmsStatus.Sending -> stringResource(R.string.messages_sending)
                    else -> WpFormat.shortStamp(context, m.date)
                }
                WpText(status, style = WpType.Small, color = Color.White.copy(alpha = 0.75f))
            }
            if (m.outgoing) Tail(color, up = false)
        }
    }
}

@Composable
private fun Tail(color: Color, up: Boolean) {
    Canvas(Modifier.padding(horizontal = 14.dp).size(width = 16.dp, height = 12.dp)) {
        val p = Path().apply {
            if (up) {
                // Received: tail above the bubble's left part, pointing up-left.
                moveTo(0f, 0f)
                lineTo(0f, size.height)
                lineTo(size.width, size.height)
            } else {
                // Sent: tail below the bubble's right part, pointing down-right.
                moveTo(0f, 0f)
                lineTo(size.width, 0f)
                lineTo(size.width, size.height)
            }
            close()
        }
        drawPath(p, color)
    }
}

@Composable
private fun NewMessagePage(initialAddress: String, initialBody: String, onSent: (Long, String) -> Unit) {
    val context = LocalContext.current
    val repo = remember { SmsRepository(context) }
    val scope = rememberCoroutineScope()
    var to by remember { mutableStateOf(initialAddress) }
    var body by remember { mutableStateOf(initialBody) }
    var sending by remember { mutableStateOf(false) }
    val contactsPerm = rememberPermissionState(Perms.CONTACTS)
    val contacts by produceState<List<Contact>>(emptyList(), contactsPerm.granted) {
        if (contactsPerm.granted) value = ContactsRepository(context).all().filter { it.hasPhone }
    }
    var picked by remember { mutableLongStateOf(-1L) }
    val suggestions = remember(to, contacts) {
        if (to.length < 2 || to.any { it.isDigit() }) emptyList() else contacts.filter { it.name.contains(to, true) }.take(6)
    }

    Column(Modifier.fillMaxSize()) {
        WpPageHeader(stringResource(R.string.app_messaging), stringResource(R.string.messages_new))
        Column(Modifier.weight(1f).padding(horizontal = 20.dp)) {
            WpText(stringResource(R.string.messages_to), style = WpType.Subtle, color = Wp.palette.subtle)
            WpTextBox(to, { to = it; picked = -1 }, Modifier.fillMaxWidth(), keyboardType = KeyboardType.Text, imeAction = ImeAction.Next)
            suggestions.forEach { c ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .wpCombinedClickable(onLongClick = null) {
                            scope.launch {
                                val number = ContactsRepository(context).detail(c).phones.firstOrNull()?.number
                                if (number != null) {
                                    to = number
                                    picked = c.id
                                }
                            }
                        }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ContactAvatar(c.thumbUri, size = 40.dp)
                    Spacer(Modifier.width(10.dp))
                    WpText(c.name, style = WpType.Large)
                }
            }
            Spacer(Modifier.height(16.dp))
            WpTextBox(body, { body = it }, Modifier.fillMaxWidth(), placeholder = stringResource(R.string.messages_type), singleLine = false)
        }
        WpAppBar(
            buttons = listOf(
                AppBarButton(WpIcons.Send, stringResource(R.string.messages_send), enabled = to.isNotBlank() && body.isNotBlank() && !sending) {
                    sending = true
                    scope.launch {
                        val address = to.trim()
                        if (repo.send(address, body)) {
                            onSent(repo.threadIdFor(address), address)
                        }
                        sending = false
                    }
                },
            ),
        )
    }
}
