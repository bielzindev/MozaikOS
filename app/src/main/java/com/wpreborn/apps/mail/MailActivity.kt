package com.wpreborn.apps.mail

import com.wpreborn.core.motion.listEntrance
import androidx.compose.foundation.lazy.itemsIndexed
import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import com.wpreborn.R
import com.wpreborn.apps.common.WpActivity
import com.wpreborn.core.icons.WpIcons
import com.wpreborn.core.motion.LoadingDots
import com.wpreborn.core.theme.Wp
import com.wpreborn.core.theme.WpType
import com.wpreborn.core.ui.AppBarButton
import com.wpreborn.core.ui.AppBarMenuItem
import com.wpreborn.core.ui.ContextMenuItem
import com.wpreborn.core.ui.WpAppBar
import com.wpreborn.core.ui.WpButton
import com.wpreborn.core.ui.WpContextMenu
import com.wpreborn.core.ui.WpIcon
import com.wpreborn.core.ui.WpPivot
import com.wpreborn.core.ui.WpText
import com.wpreborn.core.ui.WpTextBox
import com.wpreborn.core.ui.wpClickable
import com.wpreborn.core.ui.wpCombinedClickable
import com.wpreborn.data.mail.MailAccount
import com.wpreborn.data.mail.MailAccounts
import com.wpreborn.data.mail.MailCache
import com.wpreborn.data.mail.MailClient
import com.wpreborn.data.mail.MailFolder
import com.wpreborn.data.mail.MailHeader
import com.wpreborn.data.mail.MailMessage
import com.wpreborn.data.mail.MailPresets
import com.wpreborn.data.mail.MailStatus
import com.wpreborn.data.mail.MailSyncWorker
import com.wpreborn.navigation.WpNavHost
import com.wpreborn.navigation.rememberNavigator
import kotlinx.coroutines.launch
import java.io.File
import javax.mail.Flags

private sealed interface MailPage {
    data object Home : MailPage
    data object Setup : MailPage
    data object Folders : MailPage
    data object Accounts : MailPage
    data class Read(val uid: Long) : MailPage
    /** New message, or a reply / forward of [uid]. */
    data class Compose(val mode: Int = 0, val uid: Long? = null, val to: String = "") : MailPage

    companion object {
        const val NEW = 0
        const val REPLY = 1
        const val REPLY_ALL = 2
        const val FORWARD = 3
    }
}

/**
 * Mail, after Outlook Mail on Windows Phone 8.1: the account's inbox as a pivot (all, unread,
 * flagged), the message with its attachments, replies and forwards, folders, and several
 * accounts (Gmail, Outlook, Yahoo, iCloud, UOL, BOL, Terra or any IMAP server).
 */
class MailActivity : WpActivity() {
    @Composable
    override fun Content() {
        val context = LocalContext.current
        val store = remember { MailAccounts.get(context) }
        val accounts by store.accounts.collectAsState()
        val prefs = remember { context.getSharedPreferences("mail", android.content.Context.MODE_PRIVATE) }
        var accountId by remember { mutableStateOf(prefs.getString("account", null)) }
        val account = accounts.firstOrNull { it.id == accountId } ?: accounts.firstOrNull()
        var folder by remember { mutableStateOf("INBOX") }
        var folderName by remember { mutableStateOf("") }
        val cache = remember { MailCache(context) }
        val list = remember { mutableStateListOf<MailHeader>() }
        var loading by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }
        var refresh by remember { mutableIntStateOf(0) }
        val scope = rememberCoroutineScope()
        // To write to someone straight away: mailto: links and "share" from other apps.
        val incomingTo = remember {
            intent?.data?.takeIf { it.scheme == "mailto" }?.schemeSpecificPart?.substringBefore('?').orEmpty()
        }
        val nav = rememberNavigator<MailPage>(
            when {
                accounts.isEmpty() -> MailPage.Setup
                incomingTo.isNotBlank() -> MailPage.Compose(MailPage.NEW, null, incomingTo)
                else -> MailPage.Home
            },
        )

        LaunchedEffect(account?.id, folder, refresh) {
            val a = account ?: return@LaunchedEffect
            list.clear()
            list.addAll(cache.load(a.id, folder))
            val pwd = store.password(a.id) ?: return@LaunchedEffect
            loading = true
            error = null
            try {
                val fresh = MailClient.headers(a, pwd, folder)
                list.clear()
                list.addAll(fresh)
                cache.save(a.id, folder, fresh)
                if (folder == "INBOX") MailStatus.set(context, fresh.count { !it.seen })
            } catch (e: Exception) {
                error = e.message ?: e.javaClass.simpleName
            }
            loading = false
        }

        fun update(uid: Long, change: (MailHeader) -> MailHeader) {
            val i = list.indexOfFirst { it.uid == uid }
            if (i >= 0) list[i] = change(list[i])
            account?.let { cache.save(it.id, folder, list.toList()) }
        }
        fun act(block: suspend (MailAccount, String) -> Unit) {
            val a = account ?: return
            val pwd = store.password(a.id) ?: return
            scope.launch { runCatching { block(a, pwd) }.onFailure { error = it.message } }
        }

        WpNavHost(nav) { page ->
            when (page) {
                MailPage.Home -> if (account == null) {
                    SetupPage(onDone = { a -> accountId = a.id; prefs.edit().putString("account", a.id).apply(); nav.replace(MailPage.Home); refresh++ })
                } else {
                    Inbox(
                        account, if (folder == "INBOX") stringResource(R.string.mail_inbox) else folderName, list, loading, error,
                        onOpen = { nav.navigate(MailPage.Read(it.uid)) },
                        onCompose = { nav.navigate(MailPage.Compose()) },
                        onSync = { refresh++ },
                        onFolders = { nav.navigate(MailPage.Folders) },
                        onAccounts = { nav.navigate(MailPage.Accounts) },
                        onToggleRead = { h -> update(h.uid) { it.copy(seen = !h.seen) }; act { a, p -> MailClient.setFlag(a, p, folder, h.uid, Flags.Flag.SEEN, !h.seen) } },
                        onToggleFlag = { h -> update(h.uid) { it.copy(flagged = !h.flagged) }; act { a, p -> MailClient.setFlag(a, p, folder, h.uid, Flags.Flag.FLAGGED, !h.flagged) } },
                        onDelete = { h -> list.removeAll { it.uid == h.uid }; cache.save(account.id, folder, list.toList()); act { a, p -> MailClient.delete(a, p, folder, h.uid) } },
                    )
                }
                MailPage.Setup -> SetupPage(onDone = { a ->
                    accountId = a.id
                    prefs.edit().putString("account", a.id).apply()
                    folder = "INBOX"
                    refresh++
                    if (!nav.pop()) nav.replace(MailPage.Home)
                })
                MailPage.Folders -> account?.let { a ->
                    FoldersPage(a, store.password(a.id).orEmpty()) { f ->
                        folder = f.fullName
                        folderName = folderLabel(context, f)
                        nav.pop()
                    }
                }
                MailPage.Accounts -> AccountsPage(
                    accounts, account?.id,
                    onPick = { a -> accountId = a.id; prefs.edit().putString("account", a.id).apply(); folder = "INBOX"; nav.pop() },
                    onAdd = { nav.navigate(MailPage.Setup) },
                    onRemove = { a ->
                        MailClient.forget(a)
                        store.remove(a.id)
                        MailSyncWorker.schedule(context)
                        if (store.accounts.value.isEmpty()) nav.replace(MailPage.Setup)
                    },
                )
                is MailPage.Read -> account?.let { a ->
                    ReadPage(
                        a, store.password(a.id).orEmpty(), folder, page.uid,
                        onSeen = { update(page.uid) { it.copy(seen = true) } },
                        onReply = { mode -> nav.navigate(MailPage.Compose(mode, page.uid)) },
                        onDelete = {
                            list.removeAll { it.uid == page.uid }
                            cache.save(a.id, folder, list.toList())
                            act { ac, p -> MailClient.delete(ac, p, folder, page.uid) }
                            nav.pop()
                        },
                        onUnread = { update(page.uid) { it.copy(seen = false) }; act { ac, p -> MailClient.setFlag(ac, p, folder, page.uid, Flags.Flag.SEEN, false) }; nav.pop() },
                    )
                }
                is MailPage.Compose -> account?.let { a ->
                    ComposePage(a, store.password(a.id).orEmpty(), folder, page) { sent -> if (!nav.pop()) finish(); if (sent && folder != "INBOX") refresh++ }
                }
            }
        }
    }
}

private fun folderLabel(context: android.content.Context, f: MailFolder): String = when (f.special) {
    "\\inbox" -> context.getString(R.string.mail_inbox)
    "\\sent" -> context.getString(R.string.mail_sent)
    "\\drafts" -> context.getString(R.string.mail_drafts)
    "\\trash" -> context.getString(R.string.mail_trash)
    "\\junk" -> context.getString(R.string.mail_junk)
    else -> f.name
}

private fun whenText(context: android.content.Context, t: Long): String =
    if (DateUtils.isToday(t)) android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date(t))
    else android.text.format.DateFormat.format("dd/MM", t).toString()

@Composable
private fun Inbox(
    account: MailAccount,
    folderTitle: String,
    list: List<MailHeader>,
    loading: Boolean,
    error: String?,
    onOpen: (MailHeader) -> Unit,
    onCompose: () -> Unit,
    onSync: () -> Unit,
    onFolders: () -> Unit,
    onAccounts: () -> Unit,
    onToggleRead: (MailHeader) -> Unit,
    onToggleFlag: (MailHeader) -> Unit,
    onDelete: (MailHeader) -> Unit,
) {
    val context = LocalContext.current
    var menuFor by remember { mutableStateOf<MailHeader?>(null) }
    var menuY by remember { mutableFloatStateOf(0f) }
    Column(Modifier.fillMaxSize()) {
        WpPivot(
            title = (account.title + " · " + folderTitle).uppercase(),
            headers = listOf(stringResource(R.string.mail_all), stringResource(R.string.mail_unread), stringResource(R.string.mail_flagged)),
            modifier = Modifier.weight(1f),
        ) { index ->
            val shown = when (index) {
                1 -> list.filter { !it.seen }
                2 -> list.filter { it.flagged }
                else -> list
            }
            Column(Modifier.fillMaxSize()) {
                if (loading) LoadingDots(Modifier.padding(start = 20.dp, bottom = 4.dp))
                error?.let { WpText(stringResource(R.string.mail_error, it), style = WpType.Small, color = Wp.accent, maxLines = 2, modifier = Modifier.padding(horizontal = 20.dp)) }
                if (shown.isEmpty() && !loading) {
                    WpText(stringResource(R.string.mail_empty), style = WpType.Large, color = Wp.palette.subtle, modifier = Modifier.padding(20.dp))
                }
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 16.dp, bottom = 16.dp)) {
                    itemsIndexed(shown, key = { _, m -> m.uid }) { i, h ->
        androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.animateItem().listEntrance(i)) {
                        var y by remember { mutableFloatStateOf(0f) }
                        Row(
                            Modifier.fillMaxWidth()
                                .onGloballyPositioned { y = it.boundsInWindow().top }
                                .wpCombinedClickable(onLongClick = { menuFor = h; menuY = y }) { onOpen(h) }
                                .padding(vertical = 8.dp),
                        ) {
                            Column(Modifier.weight(1f)) {
                                // Unread: the sender in the accent colour, as on the phone.
                                WpText(h.from.ifBlank { h.fromAddress }, style = WpType.Large.copy(fontSize = 22.sp), color = if (h.seen) Wp.palette.foreground else Wp.accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                WpText(h.subject.ifBlank { stringResource(R.string.mail_no_subject) }, style = WpType.Normal.copy(fontWeight = if (h.seen) FontWeight.Normal else FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (h.preview.isNotBlank()) WpText(h.preview, style = WpType.Small, color = Wp.palette.subtle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = 8.dp)) {
                                WpText(whenText(context, h.date), style = WpType.Small, color = Wp.palette.subtle)
                                Row {
                                    if (h.attachments) WpIcon(WpIcons.Attach, tint = Wp.palette.subtle, size = 14.dp)
                                    if (h.flagged) WpIcon(WpIcons.Star, tint = Wp.accent, size = 14.dp)
                                }
                            }
                        }
                    
        }
    }
                }
            }
        }
        WpAppBar(
            buttons = listOf(
                AppBarButton(WpIcons.Add, stringResource(R.string.mail_new), onClick = onCompose),
                AppBarButton(WpIcons.Sync, stringResource(R.string.mail_sync), onClick = onSync),
                AppBarButton(WpIcons.Folder, stringResource(R.string.mail_folders), onClick = onFolders),
            ),
            menuItems = listOf(AppBarMenuItem(stringResource(R.string.mail_accounts), onClick = onAccounts)),
        )
    }
    menuFor?.let { h ->
        WpContextMenu(
            items = listOf(
                ContextMenuItem(stringResource(if (h.seen) R.string.mail_mark_unread else R.string.mail_mark_read)) { onToggleRead(h) },
                ContextMenuItem(stringResource(if (h.flagged) R.string.mail_unflag else R.string.mail_flag)) { onToggleFlag(h) },
                ContextMenuItem(stringResource(R.string.delete)) { onDelete(h) },
            ),
            anchorY = menuY,
            onDismiss = { menuFor = null },
        )
    }
}

/** Add an account: address and password; the servers are filled in for the known providers. */
@Composable
private fun SetupPage(onDone: (MailAccount) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var advanced by remember { mutableStateOf(false) }
    var imapHost by remember { mutableStateOf("") }
    var imapPort by remember { mutableStateOf("993") }
    var smtpHost by remember { mutableStateOf("") }
    var smtpPort by remember { mutableStateOf("465") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(email) {
        if (email.contains('@')) {
            val p = MailPresets.forEmail(email)
            imapHost = p.imapHost; imapPort = p.imapPort.toString(); smtpHost = p.smtpHost; smtpPort = p.smtpPort.toString()
        }
    }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 14.dp)) {
            WpText(stringResource(R.string.app_mail).uppercase(), style = WpType.AppTitle)
            WpText(stringResource(R.string.mail_add_account), style = WpType.Header)
            Spacer(Modifier.height(8.dp))
            WpText(stringResource(R.string.mail_setup_info), style = WpType.Normal, color = Wp.palette.subtle)
            Field(R.string.mail_your_name) { WpTextBox(name, { name = it }, Modifier.fillMaxWidth()) }
            Field(R.string.mail_address) { WpTextBox(email, { email = it.trim() }, Modifier.fillMaxWidth(), keyboardType = KeyboardType.Email) }
            Field(R.string.mail_password) { WpTextBox(password, { password = it }, Modifier.fillMaxWidth(), keyboardType = KeyboardType.Password) }
            if (MailPresets.needsAppPassword(email)) {
                WpText(stringResource(R.string.mail_app_password_info), style = WpType.Small, color = Wp.accent, modifier = Modifier.padding(top = 8.dp))
            }
            WpText(
                stringResource(if (advanced) R.string.mail_hide_advanced else R.string.mail_advanced), style = WpType.Normal, color = Wp.accent,
                modifier = Modifier.padding(top = 14.dp).wpClickable { advanced = !advanced },
            )
            if (advanced) {
                Field(R.string.mail_imap_server) { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { WpTextBox(imapHost, { imapHost = it.trim() }, Modifier.weight(1f)); WpTextBox(imapPort, { imapPort = it.filter(Char::isDigit) }, Modifier.width(80.dp), keyboardType = KeyboardType.Number) } }
                Field(R.string.mail_smtp_server) { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { WpTextBox(smtpHost, { smtpHost = it.trim() }, Modifier.weight(1f)); WpTextBox(smtpPort, { smtpPort = it.filter(Char::isDigit) }, Modifier.width(80.dp), keyboardType = KeyboardType.Number) } }
            }
            error?.let { WpText(it, style = WpType.Normal, color = Wp.accent, modifier = Modifier.padding(top = 12.dp)) }
            if (busy) LoadingDots(Modifier.padding(top = 16.dp))
            Spacer(Modifier.height(20.dp))
            WpButton(stringResource(R.string.mail_sign_in), enabled = !busy && email.contains('@') && password.isNotEmpty()) {
                busy = true
                error = null
                val base = MailPresets.forEmail(email, name)
                val port = smtpPort.toIntOrNull() ?: base.smtpPort
                val account = base.copy(
                    imapHost = imapHost.ifBlank { base.imapHost }, imapPort = imapPort.toIntOrNull() ?: 993,
                    smtpHost = smtpHost.ifBlank { base.smtpHost }, smtpPort = port, smtpSsl = port == 465,
                )
                scope.launch {
                    val e = MailClient.test(account, password)
                    busy = false
                    if (e == null) {
                        MailAccounts.get(context).add(account, password)
                        MailSyncWorker.schedule(context)
                        onDone(account)
                    } else {
                        val msg = e.message.orEmpty()
                        error = when {
                            // The server takes only OAuth sign-in (Outlook / Hotmail since 2024), no password.
                            msg.contains("No login methods", true) || msg.contains("XOAUTH2", true) -> context.getString(R.string.mail_oauth_only, account.email.substringAfter('@'))
                            e.auth -> context.getString(R.string.mail_login_failed, msg)
                            else -> context.getString(R.string.mail_connect_failed, msg)
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Field(label: Int, content: @Composable () -> Unit) {
    WpText(stringResource(label), style = WpType.Subtle, color = Wp.palette.subtle, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
    content()
}

@Composable
private fun FoldersPage(account: MailAccount, pwd: String, onPick: (MailFolder) -> Unit) {
    val context = LocalContext.current
    var folders by remember { mutableStateOf<List<MailFolder>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(account.id) {
        runCatching { MailClient.folders(account, pwd) }.onSuccess { folders = it }.onFailure { error = it.message; folders = emptyList() }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 14.dp)) {
        WpText(account.email.uppercase(), style = WpType.AppTitle, maxLines = 1)
        WpText(stringResource(R.string.mail_folders), style = WpType.Header)
        if (folders == null) LoadingDots(Modifier.padding(top = 16.dp))
        error?.let { WpText(it, style = WpType.Small, color = Wp.accent) }
        LazyColumn(Modifier.fillMaxSize().padding(top = 8.dp)) {
            itemsIndexed(folders.orEmpty(), key = { _, f -> f.fullName }) { i, f ->
        androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.animateItem().listEntrance(i)) {
                Row(Modifier.fillMaxWidth().wpClickable { onPick(f) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    WpText(folderLabel(context, f).lowercase(), style = WpType.ExtraLarge.copy(fontSize = 26.sp), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (f.unread > 0) WpText(f.unread.toString(), style = WpType.Large, color = Wp.accent)
                }
            
        }
    }
        }
    }
}

@Composable
private fun AccountsPage(accounts: List<MailAccount>, current: String?, onPick: (MailAccount) -> Unit, onAdd: () -> Unit, onRemove: (MailAccount) -> Unit) {
    var removing by remember { mutableStateOf<MailAccount?>(null) }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).padding(horizontal = 20.dp, vertical = 14.dp)) {
            WpText(stringResource(R.string.app_mail).uppercase(), style = WpType.AppTitle)
            WpText(stringResource(R.string.mail_accounts), style = WpType.Header)
            accounts.forEach { a ->
                Row(Modifier.fillMaxWidth().wpClickable { onPick(a) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        WpText(a.title, style = WpType.Large, color = if (a.id == current) Wp.accent else Wp.palette.foreground)
                        WpText(a.email, style = WpType.Small, color = Wp.palette.subtle)
                    }
                    Box(Modifier.size(40.dp).wpClickable { removing = a }, contentAlignment = Alignment.Center) { WpIcon(WpIcons.Delete, size = 20.dp) }
                }
            }
        }
        WpAppBar(buttons = listOf(AppBarButton(WpIcons.Add, stringResource(R.string.mail_add_account), onClick = onAdd)))
    }
    removing?.let { a ->
        com.wpreborn.core.ui.WpMessageBox(
            title = stringResource(R.string.mail_remove_account), message = a.email, confirm = stringResource(R.string.delete),
            onConfirm = { removing = null; onRemove(a) }, dismiss = stringResource(R.string.cancel), onDismiss = { removing = null },
        )
    }
}

/** A message: subject, sender, when, to / cc, attachments, and the text (HTML as sent). */
@Composable
private fun ReadPage(
    account: MailAccount,
    pwd: String,
    folder: String,
    uid: Long,
    onSeen: () -> Unit,
    onReply: (Int) -> Unit,
    onDelete: () -> Unit,
    onUnread: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var msg by remember { mutableStateOf<MailMessage?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var opening by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(uid) {
        runCatching { MailClient.message(account, pwd, folder, uid) }
            .onSuccess { msg = it; if (it != null) onSeen() }
            .onFailure { error = it.message }
    }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f)) {
            val m = msg
            Column(Modifier.padding(horizontal = 20.dp, vertical = 14.dp)) {
                WpText(account.title.uppercase(), style = WpType.AppTitle)
                if (m == null) {
                    if (error != null) WpText(stringResource(R.string.mail_error, error.orEmpty()), style = WpType.Normal, color = Wp.accent)
                    else LoadingDots(Modifier.padding(top = 16.dp))
                    return@Column
                }
                WpText(m.header.subject.ifBlank { stringResource(R.string.mail_no_subject) }, style = WpType.Large.copy(fontSize = 26.sp, lineHeight = 30.sp), maxLines = 3, overflow = TextOverflow.Ellipsis)
                WpText(m.header.from.ifBlank { m.header.fromAddress }, style = WpType.Normal, color = Wp.accent, modifier = Modifier.padding(top = 6.dp))
                WpText(
                    android.text.format.DateFormat.getDateFormat(context).format(java.util.Date(m.header.date)) + " " + android.text.format.DateFormat.getTimeFormat(context).format(java.util.Date(m.header.date)),
                    style = WpType.Small, color = Wp.palette.subtle,
                )
                if (m.to.isNotBlank()) WpText(stringResource(R.string.mail_to_line, m.to), style = WpType.Small, color = Wp.palette.subtle, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (m.cc.isNotBlank()) WpText(stringResource(R.string.mail_cc_line, m.cc), style = WpType.Small, color = Wp.palette.subtle, maxLines = 2, overflow = TextOverflow.Ellipsis)
                m.attachments.forEach { at ->
                    Row(
                        Modifier.fillMaxWidth().padding(top = 6.dp).background(Wp.palette.raised).wpClickable {
                            opening = at.index
                            scope.launch {
                                val f = runCatching { MailClient.attachment(account, pwd, folder, uid, at.index, File(context.cacheDir, "mail/$uid")) }.getOrNull()
                                opening = null
                                f?.let { file ->
                                    runCatching {
                                        val u = FileProvider.getUriForFile(context, context.packageName + ".files", file)
                                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW).setDataAndType(u, at.mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), file.name))
                                    }
                                }
                            }
                        }.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        WpIcon(WpIcons.Attach, size = 18.dp)
                        Spacer(Modifier.width(8.dp))
                        WpText(at.name, style = WpType.Normal, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (opening == at.index) LoadingDots() else if (at.size > 0) WpText(android.text.format.Formatter.formatShortFileSize(context, at.size.toLong()), style = WpType.Small, color = Wp.palette.subtle)
                    }
                }
            }
            val m2 = msg ?: return@Column
            val html = m2.html ?: ("<pre style='white-space:pre-wrap;font-family:sans-serif'>" + android.text.TextUtils.htmlEncode(m2.text.orEmpty()) + "</pre>")
            // The message as it was written, on white like a letter; links open in Explorer.
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                        settings.javaScriptEnabled = false
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false
                        setBackgroundColor(android.graphics.Color.WHITE)
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, request.url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                                return true
                            }
                        }
                    }
                },
                update = { w ->
                    if (w.tag != html) {
                        w.tag = html
                        w.loadDataWithBaseURL(null, "<meta name='viewport' content='width=device-width, initial-scale=1'>$html", "text/html", "utf-8", null)
                    }
                },
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
        }
        WpAppBar(
            buttons = listOf(
                AppBarButton(WpIcons.Mail, stringResource(R.string.mail_reply), enabled = msg != null) { onReply(MailPage.REPLY) },
                AppBarButton(WpIcons.Forward, stringResource(R.string.mail_forward), enabled = msg != null) { onReply(MailPage.FORWARD) },
                AppBarButton(WpIcons.Delete, stringResource(R.string.delete), onClick = onDelete),
            ),
            menuItems = listOf(
                AppBarMenuItem(stringResource(R.string.mail_reply_all), enabled = msg != null) { onReply(MailPage.REPLY_ALL) },
                AppBarMenuItem(stringResource(R.string.mail_mark_unread), onClick = onUnread),
            ),
        )
    }
}

/** Write a message (or a reply / forward): to, cc, subject, attachments and the text. */
@Composable
private fun ComposePage(account: MailAccount, pwd: String, folder: String, page: MailPage.Compose, onDone: (Boolean) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var to by remember { mutableStateOf(page.to) }
    var cc by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var replyId by remember { mutableStateOf<String?>(null) }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val files = remember { mutableStateListOf<File>() }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach { u -> copyToCache(context, u)?.let { files += it } }
    }
    LaunchedEffect(page) {
        val uid = page.uid ?: return@LaunchedEffect
        val m = runCatching { MailClient.message(account, pwd, folder, uid) }.getOrNull() ?: return@LaunchedEffect
        val quoted = "\n\n----- " + m.header.from + " -----\n" + (m.text ?: m.html?.let { org.jsoup.Jsoup.parse(it).text() }.orEmpty())
        when (page.mode) {
            MailPage.REPLY, MailPage.REPLY_ALL -> {
                to = m.replyTo
                if (page.mode == MailPage.REPLY_ALL) cc = listOf(m.to, m.cc).filter { it.isNotBlank() }.joinToString(", ")
                subject = if (m.header.subject.startsWith("Re:", true)) m.header.subject else "Re: " + m.header.subject
                replyId = m.messageId
            }
            MailPage.FORWARD -> subject = if (m.header.subject.startsWith("Enc:", true) || m.header.subject.startsWith("Fwd:", true)) m.header.subject else "Enc: " + m.header.subject
        }
        body = quoted
    }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 14.dp)) {
            WpText(account.email.uppercase(), style = WpType.AppTitle, maxLines = 1)
            WpText(stringResource(R.string.mail_new_message), style = WpType.Header)
            Field(R.string.mail_to) { WpTextBox(to, { to = it }, Modifier.fillMaxWidth(), keyboardType = KeyboardType.Email) }
            Field(R.string.mail_cc) { WpTextBox(cc, { cc = it }, Modifier.fillMaxWidth(), keyboardType = KeyboardType.Email) }
            Field(R.string.mail_subject) { WpTextBox(subject, { subject = it }, Modifier.fillMaxWidth()) }
            files.forEach { f ->
                Row(Modifier.fillMaxWidth().padding(top = 6.dp).background(Wp.palette.raised).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    WpIcon(WpIcons.Attach, size = 18.dp)
                    Spacer(Modifier.width(8.dp))
                    WpText(f.name, style = WpType.Normal, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Box(Modifier.size(32.dp).wpClickable { files.remove(f) }, contentAlignment = Alignment.Center) { WpIcon(WpIcons.Close, size = 16.dp) }
                }
            }
            Spacer(Modifier.height(10.dp))
            WpTextBox(body, { body = it }, Modifier.fillMaxWidth().heightIn(min = 220.dp), singleLine = false, maxLines = 400)
            error?.let { WpText(it, style = WpType.Normal, color = Wp.accent, modifier = Modifier.padding(top = 10.dp)) }
            if (sending) LoadingDots(Modifier.padding(top = 12.dp))
            Spacer(Modifier.height(24.dp))
        }
        WpAppBar(
            buttons = listOf(
                AppBarButton(WpIcons.Send, stringResource(R.string.mail_send), enabled = !sending && to.contains('@')) {
                    sending = true
                    error = null
                    scope.launch {
                        val r = runCatching { MailClient.send(account, pwd, to, cc, subject, body, replyId, files.toList()) }
                        sending = false
                        if (r.isSuccess) onDone(true) else error = context.getString(R.string.mail_send_failed, r.exceptionOrNull()?.message.orEmpty())
                    }
                },
                AppBarButton(WpIcons.Attach, stringResource(R.string.mail_attach)) { runCatching { pick.launch(arrayOf("*/*")) } },
                AppBarButton(WpIcons.Close, stringResource(R.string.cancel)) { onDone(false) },
            ),
        )
    }
}

/** A picked file copied next to the app (so it can be attached), with its own name. */
private fun copyToCache(context: android.content.Context, uri: Uri): File? = runCatching {
    val name = context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        ?: uri.lastPathSegment ?: "anexo"
    val dir = File(context.cacheDir, "mail_out").apply { mkdirs() }
    val out = File(dir, name.replace('/', '_'))
    context.contentResolver.openInputStream(uri)?.use { input -> out.outputStream().use { input.copyTo(it) } }
    out
}.getOrNull()
