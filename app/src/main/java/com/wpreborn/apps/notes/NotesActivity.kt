package com.wpreborn.apps.notes

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wpreborn.R
import com.wpreborn.apps.common.WpActivity
import com.wpreborn.core.icons.WpIcons
import com.wpreborn.core.motion.LoadingDots
import com.wpreborn.core.motion.slideInStaggered
import com.wpreborn.core.theme.Wp
import com.wpreborn.core.theme.WpType
import com.wpreborn.core.ui.AppBarButton
import com.wpreborn.core.ui.AppBarMenuItem
import com.wpreborn.core.ui.CenteredMessage
import com.wpreborn.core.ui.ContextMenuItem
import com.wpreborn.core.ui.WpAppBar
import com.wpreborn.core.ui.WpContextMenu
import com.wpreborn.core.ui.WpFormat
import com.wpreborn.core.ui.WpIcon
import com.wpreborn.core.ui.WpPivot
import com.wpreborn.core.ui.WpText
import com.wpreborn.core.ui.WpTextBox
import com.wpreborn.core.ui.wpCombinedClickable
import com.wpreborn.data.notes.Note
import com.wpreborn.data.notes.NotesRepository
import com.wpreborn.navigation.WpNavHost
import com.wpreborn.navigation.rememberNavigator
import kotlinx.coroutines.launch
import java.util.UUID

private sealed interface NotesPage {
    data object List : NotesPage
    data class Edit(val id: String) : NotesPage
}

/**
 * Notes, in the spirit of OneNote on Windows Phone 8.1: a pivot with recent and starred
 * notes, a plain full-page editor that saves as you type, and checklists.
 */
class NotesActivity : WpActivity() {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val repo = remember { NotesRepository.get(context) }
        LaunchedEffect(Unit) { repo.load() }
        val notes by repo.notes.collectAsState()
        val shared = remember { intent?.takeIf { it.action == Intent.ACTION_SEND }?.getStringExtra(Intent.EXTRA_TEXT) }
        val nav = rememberNavigator<NotesPage>(NotesPage.List)
        LaunchedEffect(shared, notes != null) {
            if (shared != null && notes != null && nav.current.page == NotesPage.List) {
                val id = UUID.randomUUID().toString()
                repo.save(Note(id, "", shared, System.currentTimeMillis()))
                nav.navigate(NotesPage.Edit(id))
            }
        }
        WpNavHost(nav) { page ->
            when (page) {
                NotesPage.List -> NotesList(notes, onOpen = { nav.navigate(NotesPage.Edit(it)) }, onNew = { nav.navigate(NotesPage.Edit(UUID.randomUUID().toString())) })
                is NotesPage.Edit -> NoteEditor(repo, page.id, onDeleted = { nav.pop() })
            }
        }
    }
}

@Composable
private fun NotesList(notes: List<Note>?, onOpen: (String) -> Unit, onNew: () -> Unit) {
    val context = LocalContext.current
    val repo = remember { NotesRepository.get(context) }
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf<Note?>(null) }
    var menuY by remember { mutableFloatStateOf(0f) }
    var search by remember { mutableStateOf<String?>(null) }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            if (search != null) {
                WpTextBox(search!!, { search = it }, Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 14.dp), placeholder = stringResource(R.string.notes_search))
            }
            WpPivot(
                title = stringResource(R.string.app_notes),
                headers = listOf(stringResource(R.string.notes_recent), stringResource(R.string.notes_starred)),
                modifier = Modifier.weight(1f),
            ) { index ->
                val all = notes
                when {
                    all == null -> LoadingDots(Modifier.padding(top = 24.dp))
                    else -> {
                        val q = search?.trim().orEmpty()
                        val list = all.filter { index == 0 || it.starred }
                            .filter { q.isEmpty() || it.title.contains(q, true) || it.body.contains(q, true) }
                        if (list.isEmpty()) {
                            CenteredMessage(stringResource(if (index == 0 && q.isEmpty()) R.string.notes_empty else R.string.notes_none), Modifier.padding(start = 20.dp))
                        } else {
                            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 16.dp, bottom = 16.dp)) {
                                itemsIndexed(list, key = { _, n -> n.id }) { i, n ->
        androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.animateItem()) {
                                    var bottom by remember { mutableFloatStateOf(0f) }
                                    NoteRow(
                                        n,
                                        Modifier
                                            .slideInStaggered(i)
                                            .onGloballyPositioned { bottom = it.boundsInWindow().bottom }
                                            .wpCombinedClickable(onLongClick = { menuY = bottom; menu = n }) { onOpen(n.id) },
                                    )
                                
        }
    }
                            }
                        }
                    }
                }
            }
            WpAppBar(
                buttons = listOf(
                    AppBarButton(WpIcons.Add, stringResource(R.string.notes_new), onClick = onNew),
                    AppBarButton(WpIcons.Search, stringResource(R.string.search)) { search = if (search == null) "" else null },
                ),
            )
        }
        menu?.let { n ->
            WpContextMenu(
                items = listOf(
                    ContextMenuItem(stringResource(if (n.starred) R.string.notes_unstar else R.string.notes_star)) { scope.launch { repo.toggleStar(n.id) } },
                    ContextMenuItem(stringResource(R.string.share)) { shareNote(context, n) },
                    ContextMenuItem(stringResource(R.string.delete)) { scope.launch { repo.delete(n.id) } },
                ),
                anchorY = menuY,
                onDismiss = { menu = null },
            )
        }
    }
}

@Composable
private fun NoteRow(n: Note, modifier: Modifier) {
    Row(modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
        // OneNote's purple page edge.
        Box(Modifier.width(6.dp).height(54.dp).background(NotesPurple))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            val firstLine = n.body.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()
            WpText(n.title.ifBlank { firstLine.ifBlank { " " } }, style = WpType.Large, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val preview = if (n.title.isBlank()) n.body.lineSequence().filter { it.isNotBlank() }.drop(1).firstOrNull().orEmpty() else firstLine
            if (preview.isNotEmpty()) WpText(preview, style = WpType.Subtle, color = Wp.palette.subtle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                WpText(WpFormat.shortStamp(LocalContext.current, n.updated), style = WpType.Small, color = Wp.accent)
                n.checklist?.let { (done, total) ->
                    WpText("   $done/$total", style = WpType.Small, color = Wp.palette.subtle)
                }
                if (n.starred) {
                    Spacer(Modifier.width(8.dp))
                    WpIcon(WpIcons.Star, size = 14.dp, tint = Wp.palette.subtle)
                }
            }
        }
    }
}

@Composable
private fun NoteEditor(repo: NotesRepository, id: String, onDeleted: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val original = remember { repo.get(id) }
    var title by remember { mutableStateOf(original?.title.orEmpty()) }
    var body by remember { mutableStateOf(TextFieldValue(original?.body.orEmpty())) }
    var starred by remember { mutableStateOf(original?.starred ?: false) }
    var deleted by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (original == null) runCatching { focus.requestFocus() } }

    fun current() = Note(id, title.trim(), body.text.trimEnd(), System.currentTimeMillis(), starred)
    val save = rememberUpdatedState<() -> Unit> {
        if (!deleted) {
            val n = current()
            if (original == null || n.title != original.title || n.body != original.body || n.starred != original.starred) {
                repo.saveAsync(n)
            }
        }
    }
    // Saves as you go, like OneNote: on every change (debounced) and when leaving.
    LaunchedEffect(title, body.text, starred) {
        kotlinx.coroutines.delay(800)
        save.value()
    }
    DisposableEffect(Unit) { onDispose { save.value() } }

    fun toggleChecklistLine() {
        val text = body.text
        val cursor = body.selection.start.coerceIn(0, text.length)
        val lineStart = text.lastIndexOf('\n', cursor - 1) + 1
        val line = text.substring(lineStart).substringBefore('\n')
        val (newText, delta) = when {
            line.startsWith(Note.OPEN) -> text.replaceRange(lineStart, lineStart + 2, Note.DONE) to 0
            line.startsWith(Note.DONE) -> text.replaceRange(lineStart, lineStart + 2, "") to -2
            else -> text.replaceRange(lineStart, lineStart, Note.OPEN) to 2
        }
        body = TextFieldValue(newText, TextRange((cursor + delta).coerceIn(0, newText.length)))
    }

    Column(Modifier.fillMaxSize()) {
        WpText(stringResource(R.string.app_notes).uppercase(), style = WpType.AppTitle, color = NotesPurple, modifier = Modifier.padding(start = 20.dp, top = 14.dp))
        Column(Modifier.weight(1f).padding(horizontal = 20.dp)) {
            val fg = Wp.palette.foreground
            BasicTextField(
                value = title,
                onValueChange = { title = it.replace("\n", "") },
                textStyle = WpType.Header.copy(color = fg),
                cursorBrush = SolidColor(Wp.accent),
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp),
                decorationBox = { inner ->
                    Box {
                        if (title.isEmpty()) WpText(stringResource(R.string.notes_title_hint), style = WpType.Header, color = Wp.palette.disabled, maxLines = 1)
                        inner()
                    }
                },
            )
            WpText(WpFormat.shortStamp(context, original?.updated ?: System.currentTimeMillis()), style = WpType.Small, color = Wp.palette.subtle)
            Spacer(Modifier.height(10.dp))
            BasicTextField(
                value = body,
                onValueChange = { v ->
                    // Enter on a checklist line starts a new checklist item.
                    val old = body.text
                    var nv = v
                    if (v.text.length == old.length + 1 && v.selection.start > 0 && v.text[v.selection.start - 1] == '\n') {
                        val c = v.selection.start - 1
                        val ls = v.text.lastIndexOf('\n', c - 1) + 1
                        val prev = v.text.substring(ls, c)
                        if (prev.startsWith(Note.OPEN) || prev.startsWith(Note.DONE)) {
                            nv = if (prev.length <= 2) {
                                // Empty item: Enter ends the list.
                                val t = v.text.removeRange(ls, c + 1)
                                TextFieldValue(t, TextRange(ls))
                            } else {
                                val t = v.text.substring(0, c + 1) + Note.OPEN + v.text.substring(c + 1)
                                TextFieldValue(t, TextRange(c + 3))
                            }
                        }
                    }
                    body = nv
                },
                textStyle = WpType.Large.copy(color = fg),
                cursorBrush = SolidColor(Wp.accent),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth().weight(1f).focusRequester(focus),
                decorationBox = { inner ->
                    Box(Modifier.fillMaxSize()) {
                        if (body.text.isEmpty()) WpText(stringResource(R.string.notes_body_hint), style = WpType.Large, color = Wp.palette.disabled)
                        inner()
                    }
                },
            )
        }
        WpAppBar(
            buttons = listOf(
                AppBarButton(WpIcons.Check, stringResource(R.string.notes_checklist)) { toggleChecklistLine() },
                AppBarButton(if (starred) WpIcons.Star else WpIcons.StarOutline, stringResource(if (starred) R.string.notes_unstar else R.string.notes_star)) { starred = !starred },
                AppBarButton(WpIcons.Share, stringResource(R.string.share)) { shareNote(context, current()) },
                AppBarButton(WpIcons.Delete, stringResource(R.string.delete)) {
                    deleted = true
                    repo.deleteAsync(id)
                    onDeleted()
                },
            ),
            menuItems = listOf(
                AppBarMenuItem(stringResource(R.string.notes_clear_done)) {
                    body = TextFieldValue(body.text.lines().filterNot { it.startsWith(Note.DONE) }.joinToString("\n"))
                },
            ),
        )
    }
}

private fun shareNote(context: android.content.Context, n: Note) {
    val text = listOf(n.title, n.body).filter { it.isNotBlank() }.joinToString("\n\n")
    runCatching {
        context.startActivity(
            Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), null)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

private val NotesPurple = androidx.compose.ui.graphics.Color(0xFF80397B)
