package com.wpreborn.launcher.start

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.wpreborn.R
import com.wpreborn.core.icons.WpIcons
import com.wpreborn.core.theme.Wp
import com.wpreborn.core.theme.WpType
import com.wpreborn.core.ui.WpIcon
import com.wpreborn.core.ui.WpText
import com.wpreborn.core.ui.WpTextBox
import com.wpreborn.core.ui.wpClickable
import com.wpreborn.data.apps.AppEntry

/** Which folder to put the app in: one of the existing ones, or a new one with a name. */
@Composable
fun FolderPicker(folders: List<Pair<String, String>>, onFolder: (String) -> Unit, onNew: (String) -> Unit, onDismiss: () -> Unit) {
    BackHandler(onBack = onDismiss)
    var name by remember { mutableStateOf("") }
    val defaultName = stringResource(R.string.folder_default_name)
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)).clickable(remember { MutableInteractionSource() }, null, onClick = onDismiss),
    ) {
        Column(
            Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .background(Wp.palette.chrome)
                .clickable(remember { MutableInteractionSource() }, null) {}
                .padding(20.dp),
        ) {
            WpText(stringResource(R.string.folder_add).uppercase(), style = WpType.AppTitle)
            Spacer(Modifier.height(12.dp))
            Column(Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState())) {
                folders.forEach { (id, n) ->
                    WpText(
                        n, style = WpType.Large,
                        modifier = Modifier.fillMaxWidth().wpClickable { onFolder(id) }.padding(vertical = 10.dp),
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            WpText(stringResource(R.string.folder_new), style = WpType.Subtle, color = Wp.palette.subtle)
            WpTextBox(
                name, { name = it }, Modifier.fillMaxWidth().padding(top = 6.dp),
                placeholder = defaultName,
                onImeAction = { onNew(name.trim().ifBlank { defaultName }) },
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                WpText(
                    stringResource(R.string.folder_create).uppercase(), style = WpType.AppTitle,
                    modifier = Modifier.wpClickable { onNew(name.trim().ifBlank { defaultName }) }.padding(8.dp),
                )
            }
        }
    }
}

/**
 * An open folder: its name and its apps as tiles over the dimmed Start screen. Tap launches;
 * "edit" shows a button on each app to take it out of the folder; the name can be changed.
 */
@Composable
fun FolderOverlay(
    name: String,
    apps: List<AppEntry>,
    onLaunch: (AppEntry) -> Unit,
    onRemove: (AppEntry) -> Unit,
    onRename: (String) -> Unit,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    var editing by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var draft by remember(name) { mutableStateOf(name) }
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.82f)).clickable(remember { MutableInteractionSource() }, null, onClick = onClose),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .clickable(remember { MutableInteractionSource() }, null) {}
                .padding(horizontal = 16.dp, vertical = 16.dp),
        ) {
            if (renaming) {
                WpTextBox(
                    draft, { draft = it }, Modifier.fillMaxWidth(),
                    onImeAction = { onRename(draft); renaming = false },
                )
            } else {
                WpText(
                    name, style = WpType.Header, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.wpClickable { renaming = true },
                )
            }
            Spacer(Modifier.height(14.dp))
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(apps, key = { it.key }) { app ->
                    Box(Modifier.fillMaxWidth().aspectRatio(1f).background(Wp.accent).wpClickable { if (!editing) onLaunch(app) }) {
                        Box(Modifier.align(Alignment.Center)) { AppIcon(app, 44.dp) }
                        WpText(
                            app.label, style = WpType.TileLabel, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.align(Alignment.BottomStart).padding(start = 6.dp, end = 6.dp, bottom = 4.dp),
                        )
                        if (editing) {
                            Box(
                                Modifier.align(Alignment.TopEnd).padding(4.dp).size(30.dp).background(Color.White, androidx.compose.foundation.shape.CircleShape)
                                    .wpClickable { onRemove(app) },
                                contentAlignment = Alignment.Center,
                            ) { WpIcon(WpIcons.Close, tint = Color.Black, size = 18.dp) }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                WpText(
                    stringResource(if (editing) R.string.ok else R.string.folder_edit).uppercase(), style = WpType.AppTitle, color = Color.White,
                    modifier = Modifier.wpClickable { editing = !editing }.padding(8.dp),
                )
                WpText(
                    stringResource(R.string.folder_close).uppercase(), style = WpType.AppTitle, color = Color.White,
                    modifier = Modifier.wpClickable(onClick = onClose).padding(8.dp),
                )
            }
        }
    }
}
