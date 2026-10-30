package com.wpreborn.launcher.allapps

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wpreborn.R
import com.wpreborn.core.icons.WpIcons
import com.wpreborn.core.motion.slideInStaggered
import com.wpreborn.core.theme.Wp
import com.wpreborn.core.theme.WpType
import com.wpreborn.core.ui.ContextMenuItem
import com.wpreborn.core.ui.JumpList
import com.wpreborn.core.ui.LetterTile
import com.wpreborn.core.ui.RoundButton
import com.wpreborn.core.ui.WpContextMenu
import com.wpreborn.core.ui.WpIcon
import com.wpreborn.core.ui.WpText
import com.wpreborn.core.ui.WpTextBox
import com.wpreborn.core.ui.wpCombinedClickable
import com.wpreborn.data.apps.AppEntry
import com.wpreborn.data.apps.AppRepository
import kotlinx.coroutines.launch

private val LETTERS = listOf("#") + ('a'..'z').map { it.toString() }
private const val NEW_WINDOW_MS = 3L * 24 * 60 * 60 * 1000

private sealed interface ListRow {
    data class Header(val letter: String) : ListRow
    data class App(val app: AppEntry, val isNew: Boolean = false) : ListRow
    data object NewHeader : ListRow
}

/**
 * The 8.1 app list: search button on top, newly installed apps first, then alphabetical
 * groups with letter tiles. Tapping a letter opens the jump list; press and hold an app for
 * "pin to start", "uninstall" and "app info".
 */
@Composable
fun AllAppsScreen(
    apps: List<AppEntry>,
    isPinned: (AppEntry) -> Boolean,
    onLaunch: (AppEntry) -> Unit,
    onPin: (AppEntry) -> Unit,
    visible: Boolean,
) {
    val context = LocalContext.current
    val repo = remember { AppRepository.get(context) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var jumpOpen by remember { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<AppEntry?>(null) }
    var menuY by remember { mutableFloatStateOf(0f) }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = remember { FocusRequester() }

    BackHandler(enabled = searching) {
        searching = false
        query = ""
    }

    val now = remember(apps) { System.currentTimeMillis() }
    val rows: List<ListRow> = remember(apps, query, searching) {
        if (searching && query.isNotBlank()) {
            apps.filter { it.label.contains(query.trim(), ignoreCase = true) }.map { ListRow.App(it) }
        } else {
            val fresh = apps.filter { it.installedAt > 0 && now - it.installedAt < NEW_WINDOW_MS }
                .sortedByDescending { it.installedAt }
            buildList {
                if (fresh.isNotEmpty()) {
                    add(ListRow.NewHeader)
                    fresh.forEach { add(ListRow.App(it, isNew = true)) }
                }
                apps.groupBy { it.group }.toSortedMap(compareBy { LETTERS.indexOf(it) }).forEach { (letter, list) ->
                    add(ListRow.Header(letter))
                    list.forEach { add(ListRow.App(it)) }
                }
            }
        }
    }
    val available = remember(apps) { apps.mapTo(HashSet()) { it.group } }

    var boxTop by remember { mutableFloatStateOf(0f) }
    Box(Modifier.fillMaxSize().onGloballyPositioned { boxTop = it.boundsInWindow().top }) {
        Column(Modifier.fillMaxSize()) {
            if (searching) {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp)) {
                    WpTextBox(
                        query,
                        { query = it },
                        Modifier.fillMaxWidth(),
                        placeholder = stringResource(R.string.search_apps),
                        imeAction = ImeAction.Search,
                        onImeAction = { keyboard?.hide() },
                        focusRequester = focus,
                    )
                }
                LaunchedEffect(Unit) {
                    focus.requestFocus()
                    keyboard?.show()
                }
            } else {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, bottom = 8.dp)) {
                    RoundButton(WpIcons.Search, size = 40.dp) { searching = true }
                }
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, bottom = 80.dp),
            ) {
                items(rows, key = {
                    when (it) {
                        is ListRow.Header -> "h:${it.letter}"
                        is ListRow.App -> (if (it.isNew) "n:" else "a:") + it.app.key
                        ListRow.NewHeader -> "new"
                    }
                }) { row ->
                    when (row) {
                        is ListRow.Header -> Box(Modifier.padding(vertical = 6.dp)) {
                            LetterTile(
                                row.letter,
                                size = 48.dp,
                                modifier = Modifier.wpCombinedClickable(onLongClick = null) { jumpOpen = true },
                            )
                        }
                        ListRow.NewHeader -> WpText(
                            stringResource(R.string.apps_new),
                            style = WpType.Large,
                            color = Wp.accent,
                            modifier = Modifier.padding(vertical = 6.dp),
                        )
                        is ListRow.App -> {
                            var bottom by remember { mutableFloatStateOf(0f) }
                            AppRow(
                                row.app,
                                repo,
                                Modifier
                                    .onGloballyPositioned { bottom = it.boundsInWindow().bottom }
                                    .slideInStaggered(rows.indexOf(row).coerceAtMost(12), key = visible),
                                onClick = { onLaunch(row.app) },
                                onLongClick = {
                                    menuY = bottom
                                    menuFor = row.app
                                },
                            )
                        }
                    }
                }
            }
        }

        menuFor?.let { app ->
            val items = buildList {
                if (!isPinned(app)) add(ContextMenuItem(context.getString(R.string.pin_to_start)) { onPin(app) })
                if (app.builtIn == null) {
                    if (!repo.isSystemApp(app)) add(ContextMenuItem(context.getString(R.string.uninstall)) { repo.uninstall(app) })
                    add(ContextMenuItem(context.getString(R.string.app_info)) { repo.openAppInfo(app) })
                }
            }
            if (items.isEmpty()) {
                menuFor = null
            } else {
                // Windows coordinates are relative to the activity window; the menu is drawn in this box.
                WpContextMenu(items, anchorY = menuY - boxTop, onDismiss = { menuFor = null })
            }
        }

        if (jumpOpen) {
            JumpList(
                letters = LETTERS,
                available = available,
                onPick = { letter ->
                    jumpOpen = false
                    val index = rows.indexOfFirst { it is ListRow.Header && it.letter == letter }
                    if (index >= 0) scope.launch { listState.scrollToItem(index) }
                },
                onDismiss = { jumpOpen = false },
            )
        }
    }
}

@Composable
private fun AppRow(app: AppEntry, repo: AppRepository, modifier: Modifier, onClick: () -> Unit, onLongClick: () -> Unit) {
    val metro = Wp.settings.metroIcons || app.builtIn != null
    val onAccent = metro || Wp.settings.iconsOnAccent
    Row(
        modifier
            .fillMaxWidth()
            .wpCombinedClickable(onLongClick = onLongClick, onClick = onClick)
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(48.dp)
                .then(if (onAccent) Modifier.background(Wp.accent) else Modifier),
            contentAlignment = Alignment.Center,
        ) {
            com.wpreborn.launcher.start.AppIcon(app, if (metro) 28.dp else if (onAccent) 36.dp else 46.dp)
        }
        Spacer(Modifier.width(14.dp))
        WpText(
            app.label,
            style = WpType.Large.copy(fontSize = 21.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    Spacer(Modifier.height(0.dp))
}
