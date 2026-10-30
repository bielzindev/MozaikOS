package com.wpreborn.apps.people

import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wpreborn.R
import com.wpreborn.apps.common.WpActivity
import com.wpreborn.apps.phone.PhoneActivity
import com.wpreborn.core.icons.WpIcons
import com.wpreborn.core.motion.LoadingDots
import com.wpreborn.core.motion.slideInStaggered
import com.wpreborn.core.theme.Wp
import com.wpreborn.core.theme.WpType
import com.wpreborn.core.ui.AppBarButton
import com.wpreborn.core.ui.CenteredMessage
import com.wpreborn.core.ui.ContactAvatar
import com.wpreborn.core.ui.JumpList
import com.wpreborn.core.ui.LetterTile
import com.wpreborn.core.ui.WpAppBar
import com.wpreborn.core.ui.WpFormat
import com.wpreborn.core.ui.WpListItem
import com.wpreborn.core.ui.WpPivot
import com.wpreborn.core.ui.WpText
import com.wpreborn.core.ui.WpTextBox
import com.wpreborn.core.ui.wpClickable
import com.wpreborn.data.calls.CallLogRepository
import com.wpreborn.data.contacts.Contact
import com.wpreborn.data.contacts.ContactDetail
import com.wpreborn.data.contacts.ContactsRepository
import com.wpreborn.navigation.WpNavHost
import com.wpreborn.navigation.rememberNavigator
import com.wpreborn.permissions.PermissionGate
import com.wpreborn.permissions.Perms
import com.wpreborn.permissions.hasPermissions
import com.wpreborn.permissions.rememberPermissionState
import kotlinx.coroutines.launch
import java.text.Normalizer

private sealed interface PeoplePage {
    data object Hub : PeoplePage
    data class Profile(val contactId: Long) : PeoplePage
    data object Search : PeoplePage
}

private val LETTERS = listOf("#") + ('a'..'z').map { it.toString() }

private fun groupOf(name: String): String {
    val c = Normalizer.normalize(name.take(1), Normalizer.Form.NFD).firstOrNull()?.lowercaseChar() ?: '#'
    return if (c in 'a'..'z') c.toString() else "#"
}

/** People: the contact list with letter groups and jump list, favorites, search and profiles. */
class PeopleActivity : WpActivity() {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val perm = rememberPermissionState(Perms.CONTACTS)
        var contacts by remember { mutableStateOf<List<Contact>?>(null) }
        var reload by remember { mutableStateOf(0) }
        LaunchedEffect(perm.granted, reload) { if (perm.granted) contacts = ContactsRepository(context).all() }
        val nav = rememberNavigator<PeoplePage>(PeoplePage.Hub)
        WpNavHost(nav) { page ->
            when (page) {
                PeoplePage.Hub -> Column(Modifier.fillMaxSize()) {
                    WpPivot(
                        title = stringResource(R.string.app_people),
                        headers = listOf(stringResource(R.string.people_all), stringResource(R.string.people_favorites)),
                        modifier = Modifier.weight(1f),
                    ) { index ->
                        PermissionGate(perm, stringResource(R.string.perm_contacts), Modifier.padding(start = 20.dp)) {
                            val list = contacts
                            when {
                                list == null -> LoadingDots(Modifier.padding(top = 24.dp))
                                index == 0 -> ContactList(list) { nav.navigate(PeoplePage.Profile(it.id)) }
                                else -> {
                                    val favs = list.filter { it.starred }
                                    if (favs.isEmpty()) CenteredMessage(stringResource(R.string.people_no_favorites), Modifier.padding(start = 20.dp))
                                    else ContactList(favs, grouped = false) { nav.navigate(PeoplePage.Profile(it.id)) }
                                }
                            }
                        }
                    }
                    WpAppBar(
                        buttons = listOf(
                            AppBarButton(WpIcons.Add, stringResource(R.string.people_new)) {
                                context.startActivity(Intent(ContactsContract.Intents.Insert.ACTION).setType(ContactsContract.RawContacts.CONTENT_TYPE))
                            },
                            AppBarButton(WpIcons.Search, stringResource(R.string.search), enabled = perm.granted) { nav.navigate(PeoplePage.Search) },
                            AppBarButton(WpIcons.Sync, stringResource(R.string.refresh), enabled = perm.granted) { reload++ },
                        ),
                    )
                }
                is PeoplePage.Profile -> contacts?.firstOrNull { it.id == page.contactId }?.let { ProfilePage(it) { reload++ } }
                PeoplePage.Search -> SearchPage(contacts.orEmpty()) { nav.navigate(PeoplePage.Profile(it.id)) }
            }
        }
    }
}

@Composable
private fun ContactList(contacts: List<Contact>, grouped: Boolean = true, onOpen: (Contact) -> Unit) {
    val listState: LazyListState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var jump by remember { mutableStateOf(false) }
    val groups = remember(contacts) { contacts.groupBy { groupOf(it.name) }.toSortedMap(compareBy { LETTERS.indexOf(it) }) }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 12.dp, bottom = 16.dp)) {
            if (grouped) {
                groups.forEach { (letter, list) ->
                    item(key = "h$letter") {
                        LetterTile(letter, size = 52.dp, filled = true, modifier = Modifier.padding(vertical = 8.dp).wpClickable { jump = true })
                    }
                    items(list, key = { it.id }) { c -> ContactRow(c, onOpen) }
                }
            } else {
                items(contacts, key = { it.id }) { c -> ContactRow(c, onOpen) }
            }
        }
        if (jump) {
            JumpList(LETTERS, groups.keys, onPick = { letter ->
                jump = false
                var index = 0
                for ((l, list) in groups) {
                    if (l == letter) break
                    index += 1 + list.size
                }
                scope.launch { listState.scrollToItem(index) }
            }, onDismiss = { jump = false })
        }
    }
}

@Composable
private fun ContactRow(c: Contact, onOpen: (Contact) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .slideInStaggered(0, key = c.id)
            .wpClickable { onOpen(c) }
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ContactAvatar(c.thumbUri, size = 56.dp)
        Spacer(Modifier.width(14.dp))
        WpText(c.name, style = WpType.Large, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun SearchPage(contacts: List<Contact>, onOpen: (Contact) -> Unit) {
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val results = remember(query, contacts) { if (query.isBlank()) emptyList() else contacts.filter { it.name.contains(query.trim(), true) } }
    Column(Modifier.fillMaxSize().padding(top = 14.dp)) {
        WpText(stringResource(R.string.people_search).uppercase(), style = WpType.AppTitle, modifier = Modifier.padding(start = 20.dp, bottom = 10.dp))
        WpTextBox(query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 20.dp), focusRequester = focus)
        LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 12.dp, top = 12.dp)) {
            items(results, key = { it.id }) { ContactRow(it, onOpen) }
        }
    }
}

@Composable
private fun ProfilePage(contact: Contact, onChanged: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { ContactsRepository(context) }
    val detail by produceState<ContactDetail?>(null, contact.id) { value = repo.detail(contact) }
    var starred by remember { mutableStateOf(contact.starred) }
    val history by produceState(emptyList<com.wpreborn.data.calls.CallEntry>(), detail) {
        val numbers = detail?.phones?.map { ContactsRepository.normalize(it.number) }.orEmpty().toSet()
        value = if (numbers.isNotEmpty() && context.hasPermissions(Perms.CALL_LOG)) {
            CallLogRepository(context).recent(300).filter { ContactsRepository.normalize(it.number) in numbers }.take(20)
        } else {
            emptyList()
        }
    }
    Column(Modifier.fillMaxSize()) {
        WpPivot(
            title = contact.name,
            headers = listOf(stringResource(R.string.people_profile), stringResource(R.string.phone_history)),
            modifier = Modifier.weight(1f),
        ) { index ->
            if (index == 0) {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, bottom = 20.dp)) {
                    item {
                        ContactAvatar(contact.photoUri ?: contact.thumbUri, size = 160.dp)
                        Spacer(Modifier.height(12.dp))
                    }
                    val d = detail
                    if (d != null) {
                        items(d.phones) { p ->
                            WpListItem(stringResource(R.string.people_call, p.type.lowercase()), subtitle = p.number, subtitleAccent = true, titleStyle = WpType.Large) {
                                PhoneActivity.call(context, p.number)
                            }
                            WpListItem(stringResource(R.string.people_text, p.type.lowercase()), subtitle = p.number, subtitleAccent = true, titleStyle = WpType.Large) {
                                PhoneActivity.text(context, p.number)
                            }
                        }
                        items(d.emails) { e ->
                            WpListItem(stringResource(R.string.people_email, e.type.lowercase()), subtitle = e.address, subtitleAccent = true, titleStyle = WpType.Large) {
                                context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:${e.address}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            }
                        }
                        items(d.addresses) { a ->
                            WpListItem(stringResource(R.string.people_map), subtitle = a, subtitleAccent = true, titleStyle = WpType.Large) {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=" + Uri.encode(a))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            }
                        }
                        d.organization?.let { item { WpListItem(stringResource(R.string.people_work), subtitle = it, titleStyle = WpType.Large) } }
                        d.birthday?.let { item { WpListItem(stringResource(R.string.people_birthday), subtitle = it, titleStyle = WpType.Large) } }
                    }
                }
            } else {
                if (history.isEmpty()) {
                    CenteredMessage(stringResource(R.string.phone_no_history), Modifier.padding(start = 20.dp))
                } else {
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp)) {
                        items(history, key = { it.id }) { c ->
                            WpListItem(
                                WpFormat.dayLabel(context, c.date),
                                subtitle = WpFormat.time(context, c.date) + " · " + c.number,
                                titleStyle = WpType.Large,
                            )
                        }
                    }
                }
            }
        }
        WpAppBar(
            buttons = listOf(
                AppBarButton(if (starred) WpIcons.Star else WpIcons.StarOutline, stringResource(if (starred) R.string.people_unfavorite else R.string.people_favorite)) {
                    scope.launch {
                        if (repo.setStarred(contact, !starred)) {
                            starred = !starred
                            onChanged()
                        } else {
                            context.startActivity(Intent(Intent.ACTION_EDIT, repo.contactUri(contact)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                    }
                },
                AppBarButton(WpIcons.Edit, stringResource(R.string.edit)) {
                    context.startActivity(Intent(Intent.ACTION_EDIT, repo.contactUri(contact)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                },
            ),
        )
    }
}
