package com.wpreborn.apps.people

import android.Manifest
import android.provider.ContactsContract.CommonDataKinds.Phone
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.wpreborn.R
import com.wpreborn.core.icons.WpIcons
import com.wpreborn.core.theme.Wp
import com.wpreborn.core.theme.WpType
import com.wpreborn.core.ui.AppBarButton
import com.wpreborn.core.ui.AppBarMenuItem
import com.wpreborn.core.ui.WpAppBar
import com.wpreborn.core.ui.WpIcon
import com.wpreborn.core.ui.WpMessageBox
import com.wpreborn.core.ui.WpText
import com.wpreborn.core.ui.WpTextBox
import com.wpreborn.core.ui.wpClickable
import com.wpreborn.data.contacts.Contact
import com.wpreborn.data.contacts.ContactWriter
import com.wpreborn.data.contacts.EditableContact
import com.wpreborn.permissions.rememberPermissionState
import kotlinx.coroutines.launch

private val PHONE_TYPES = listOf(Phone.TYPE_MOBILE, Phone.TYPE_HOME, Phone.TYPE_WORK, Phone.TYPE_OTHER)

/**
 * New / edit contact, in the People app itself (Windows Phone's "new contact" page): first and
 * last name, phones with their kind, e-mails, company; save, cancel and delete.
 */
@Composable
fun ContactEditorPage(contact: Contact?, initialNumber: String? = null, onDeleted: () -> Unit = {}, onDone: (saved: Boolean) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val writer = remember { ContactWriter(context) }
    val perm = rememberPermissionState(arrayOf(Manifest.permission.WRITE_CONTACTS))
    var first by remember { mutableStateOf("") }
    var last by remember { mutableStateOf("") }
    var company by remember { mutableStateOf("") }
    val phones = remember { mutableStateListOf<Pair<String, Int>>() }
    val emails = remember { mutableStateListOf<String>() }
    var loaded by remember { mutableStateOf(contact == null) }
    var failed by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(contact?.id) {
        if (contact != null) {
            val e = writer.load(contact.id)
            first = e.first; last = e.last; company = e.company
            phones.clear(); phones.addAll(e.phones)
            emails.clear(); emails.addAll(e.emails)
        }
        if (phones.isEmpty()) phones += (initialNumber ?: "") to Phone.TYPE_MOBILE
        loaded = true
    }
    fun save() {
        if (!perm.granted) { perm.request(); return }
        scope.launch {
            val ok = writer.save(contact?.id, EditableContact(first, last, phones.toList(), emails.toList(), company))
            if (ok) onDone(true) else failed = true
        }
    }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 14.dp)) {
            WpText(stringResource(R.string.app_people).uppercase(), style = WpType.AppTitle)
            WpText(stringResource(if (contact == null) R.string.people_new_contact else R.string.people_edit_contact), style = WpType.Header)
            Spacer(Modifier.height(12.dp))
            if (!loaded) return@Column
            Label(R.string.people_first_name)
            WpTextBox(first, { first = it }, Modifier.fillMaxWidth())
            Label(R.string.people_last_name)
            WpTextBox(last, { last = it }, Modifier.fillMaxWidth())
            Label(R.string.people_phones)
            phones.forEachIndexed { i, (n, t) ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 6.dp)) {
                    WpTextBox(n, { phones[i] = it to t }, Modifier.weight(1f), keyboardType = KeyboardType.Phone)
                    // Tap the kind to change it (mobile, home, work, other).
                    WpText(
                        Phone.getTypeLabel(context.resources, t, "").toString().lowercase(),
                        style = WpType.Normal, color = Wp.accent,
                        modifier = Modifier.width(84.dp).wpClickable { phones[i] = n to PHONE_TYPES[(PHONE_TYPES.indexOf(t) + 1).mod(PHONE_TYPES.size)] }.padding(start = 10.dp),
                    )
                    Box(Modifier.size(36.dp).wpClickable { phones.removeAt(i) }, contentAlignment = Alignment.Center) { WpIcon(WpIcons.Close, size = 18.dp) }
                }
            }
            AddLine(R.string.people_add_phone) { phones += "" to Phone.TYPE_MOBILE }
            Label(R.string.people_emails)
            emails.forEachIndexed { i, e ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 6.dp)) {
                    WpTextBox(e, { emails[i] = it }, Modifier.weight(1f), keyboardType = KeyboardType.Email)
                    Box(Modifier.size(36.dp).wpClickable { emails.removeAt(i) }, contentAlignment = Alignment.Center) { WpIcon(WpIcons.Close, size = 18.dp) }
                }
            }
            AddLine(R.string.people_add_email) { emails += "" }
            Label(R.string.people_company)
            WpTextBox(company, { company = it }, Modifier.fillMaxWidth())
            Spacer(Modifier.height(24.dp))
        }
        WpAppBar(
            buttons = listOf(
                AppBarButton(WpIcons.Check, stringResource(R.string.save), enabled = first.isNotBlank() || last.isNotBlank() || phones.any { it.first.isNotBlank() }) { save() },
                AppBarButton(WpIcons.Close, stringResource(R.string.cancel)) { onDone(false) },
            ),
            menuItems = if (contact != null) listOf(AppBarMenuItem(stringResource(R.string.delete)) { confirmDelete = true }) else emptyList(),
        )
    }
    if (failed) {
        WpMessageBox(stringResource(R.string.people_save_failed_title), stringResource(R.string.people_save_failed), stringResource(R.string.ok), onConfirm = { failed = false }, onDismiss = { failed = false })
    }
    if (confirmDelete && contact != null) {
        WpMessageBox(
            title = stringResource(R.string.people_delete_title),
            message = contact.name,
            confirm = stringResource(R.string.delete),
            onConfirm = {
                confirmDelete = false
                if (!perm.granted) perm.request() else scope.launch { if (writer.delete(contact)) onDeleted() else failed = true }
            },
            dismiss = stringResource(R.string.cancel),
            onDismiss = { confirmDelete = false },
        )
    }
}

@Composable
private fun Label(res: Int) {
    WpText(stringResource(res), style = WpType.Subtle, color = Wp.palette.subtle, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
}

@Composable
private fun AddLine(res: Int, onClick: () -> Unit) {
    Row(Modifier.wpClickable(onClick = onClick).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        WpIcon(WpIcons.Add, tint = Wp.accent, size = 20.dp)
        WpText(stringResource(res), style = WpType.Normal, color = Wp.accent)
    }
}
