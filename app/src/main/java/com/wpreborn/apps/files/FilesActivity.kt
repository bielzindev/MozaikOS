package com.wpreborn.apps.files

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import kotlinx.coroutines.isActive
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import android.text.format.DateFormat
import android.text.format.Formatter
import android.webkit.MimeTypeMap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.unit.sp
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.wpreborn.R
import com.wpreborn.apps.common.WpActivity
import com.wpreborn.core.icons.Glyphs
import com.wpreborn.core.icons.WpIcons
import com.wpreborn.core.motion.slideInStaggered
import com.wpreborn.core.theme.Wp
import com.wpreborn.core.theme.WpType
import com.wpreborn.core.ui.AppBarButton
import com.wpreborn.core.ui.AppBarMenuItem
import com.wpreborn.core.ui.WpAppBar
import com.wpreborn.core.ui.WpButton
import com.wpreborn.core.ui.WpIcon
import com.wpreborn.core.ui.WpMessageBox
import com.wpreborn.core.ui.WpText
import com.wpreborn.core.ui.WpTextBox
import com.wpreborn.core.ui.wpClickable
import com.wpreborn.core.ui.wpCombinedClickable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Date

private enum class Sort { Name, Date, Size, Type }

/** A place on the first page: the phone's storage, an SD card, or a common folder. */
private data class Place(val name: String, val dir: File, val icon: ImageVector, val volume: Boolean)

/**
 * Files, after the Windows Phone 8.1 Files app: the phone and SD cards with their free space,
 * shortcuts to the usual folders, then folders and files. Open, share, select several, copy,
 * move, rename, delete, new folder, sort, hidden files, search in the folder, properties.
 */
class FilesActivity : WpActivity() {
    @Composable
    override fun Content() {
        val context = LocalContext.current
        var granted by remember { mutableStateOf(hasAccess()) }
        var resumes by remember { mutableIntStateOf(0) }
        LaunchedEffect(resumes) { granted = hasAccess() }
        androidx.compose.runtime.DisposableEffect(Unit) {
            val obs = androidx.lifecycle.LifecycleEventObserver { _, e -> if (e == androidx.lifecycle.Lifecycle.Event.ON_RESUME) resumes++ }
            lifecycle.addObserver(obs)
            onDispose { lifecycle.removeObserver(obs) }
        }
        if (!granted) {
            Column(Modifier.fillMaxSize().padding(20.dp)) {
                WpText(stringResource(R.string.app_files).uppercase(), style = WpType.AppTitle)
                WpText(stringResource(R.string.files_access_title), style = WpType.Large, modifier = Modifier.padding(top = 16.dp))
                WpText(stringResource(R.string.files_access), style = WpType.Normal, color = Wp.palette.subtle, modifier = Modifier.padding(top = 8.dp, bottom = 16.dp))
                WpButton(stringResource(R.string.files_allow)) { requestAccess(context) }
            }
            return
        }
        FilesScreen()
    }

    private fun hasAccess(): Boolean =
        if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
        else checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED

    private fun requestAccess(context: Context) {
        if (Build.VERSION.SDK_INT >= 30) {
            runCatching {
                context.startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}")))
            }.onFailure { runCatching { context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) } }
        } else {
            requestPermissions(arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE, android.Manifest.permission.WRITE_EXTERNAL_STORAGE), 1)
        }
    }
}

private fun iconFor(f: File): ImageVector {
    if (f.isDirectory) return WpIcons.Folder
    return when (f.extension.lowercase()) {
        "jpg", "jpeg", "png", "gif", "webp", "heic", "bmp" -> WpIcons.Photos
        "mp3", "m4a", "aac", "flac", "ogg", "wav", "opus" -> WpIcons.MusicNote
        "mp4", "mkv", "webm", "3gp", "mov", "avi" -> Glyphs.Video
        "zip", "rar", "7z", "tar", "gz" -> Glyphs.Bag
        "apk" -> WpIcons.Apps
        else -> Glyphs.Document
    }
}

private fun mimeOf(f: File): String =
    MimeTypeMap.getSingleton().getMimeTypeFromExtension(f.extension.lowercase()) ?: "*/*"

private fun uriOf(context: Context, f: File): Uri = FileProvider.getUriForFile(context, context.packageName + ".files", f)

private fun dirSize(f: File): Long = if (f.isFile) f.length() else f.listFiles()?.sumOf { dirSize(it) } ?: 0L

/** Copies [src] into [destDir] (recursively), choosing "name (2)" when the name is taken. */
private fun copyInto(src: File, destDir: File): File {
    var target = File(destDir, src.name)
    var n = 2
    while (target.exists()) {
        target = File(destDir, src.nameWithoutExtension + " ($n)" + if (src.extension.isNotEmpty() && src.isFile) "." + src.extension else "")
        n++
    }
    if (src.isDirectory) {
        target.mkdirs()
        src.listFiles()?.forEach { copyInto(it, target) }
    } else {
        src.copyTo(target)
    }
    return target
}

@Composable
private fun FilesScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("files", Context.MODE_PRIVATE) }
    val stack = remember { mutableStateListOf<File>() }
    val current = stack.lastOrNull()
    var sort by remember { mutableStateOf(runCatching { Sort.valueOf(prefs.getString("sort", "Name")!!) }.getOrDefault(Sort.Name)) }
    var showHidden by remember { mutableStateOf(prefs.getBoolean("hidden", false)) }
    var reload by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<Set<File>?>(null) }
    var clipboard by remember { mutableStateOf<Pair<List<File>, Boolean>?>(null) } // files, move?
    var busy by remember { mutableStateOf<String?>(null) }
    var naming by remember { mutableStateOf<Pair<String, (String) -> Unit>?>(null) }
    var confirmDelete by remember { mutableStateOf<List<File>?>(null) }
    var info by remember { mutableStateOf<File?>(null) }
    var filter by remember { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }

    BackHandler(enabled = selected != null || filter != null || stack.isNotEmpty() || clipboard != null) {
        when {
            selected != null -> selected = null
            filter != null -> filter = null
            stack.isNotEmpty() -> stack.removeAt(stack.lastIndex)
            else -> clipboard = null
        }
    }

    val entries by androidx.compose.runtime.produceState<List<File>>(emptyList(), current, sort, showHidden, reload) {
        value = withContext(Dispatchers.IO) {
            val list = current?.listFiles()?.toList().orEmpty().filter { showHidden || !it.name.startsWith(".") }
            val dirs = list.filter { it.isDirectory }
            val files = list.filter { !it.isDirectory }
            fun order(l: List<File>) = when (sort) {
                Sort.Name -> l.sortedBy { it.name.lowercase() }
                Sort.Date -> l.sortedByDescending { it.lastModified() }
                Sort.Size -> l.sortedByDescending { if (it.isDirectory) 0L else it.length() }
                Sort.Type -> l.sortedWith(compareBy({ it.extension.lowercase() }, { it.name.lowercase() }))
            }
            order(dirs) + order(files)
        }
    }
    val shown = filter?.let { q -> entries.filter { it.name.contains(q, ignoreCase = true) } } ?: entries

    fun open(f: File) {
        if (f.isDirectory) {
            stack.add(f)
            filter = null
        } else {
            runCatching {
                context.startActivity(
                    Intent.createChooser(
                        Intent(Intent.ACTION_VIEW).setDataAndType(uriOf(context, f), mimeOf(f)).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                        f.name,
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
    }

    fun share(files: List<File>) {
        val list = files.filter { it.isFile }
        if (list.isEmpty()) return
        runCatching {
            val i = if (list.size == 1) {
                Intent(Intent.ACTION_SEND).setType(mimeOf(list[0])).putExtra(Intent.EXTRA_STREAM, uriOf(context, list[0]))
            } else {
                Intent(Intent.ACTION_SEND_MULTIPLE).setType("*/*").putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(list.map { uriOf(context, it) }))
            }
            context.startActivity(Intent.createChooser(i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    fun paste() {
        val (files, move) = clipboard ?: return
        val dest = current ?: return
        clipboard = null
        busy = context.getString(if (move) R.string.files_moving else R.string.files_copying)
        scope.launch {
            withContext(Dispatchers.IO) {
                for (f in files) {
                    if (dest.absolutePath.startsWith(f.absolutePath + "/") || f.parentFile == dest && move) continue
                    runCatching {
                        if (move) {
                            val target = File(dest, f.name)
                            if (target.exists() || !f.renameTo(target)) {
                                copyInto(f, dest)
                                f.deleteRecursively()
                            }
                        } else {
                            copyInto(f, dest)
                        }
                    }
                }
            }
            busy = null
            reload++
        }
    }

    val phoneName = stringResource(R.string.files_phone)
    val sdName = stringResource(R.string.files_sd_card)
    // The phone's storage and the SD cards, named like on Windows Phone (not "08C3-4D92").
    val volumes = remember {
        val list = ArrayList<Pair<String, File>>()
        list += phoneName to Environment.getExternalStorageDirectory()
        if (Build.VERSION.SDK_INT >= 30) {
            context.getSystemService(android.os.storage.StorageManager::class.java)?.storageVolumes
                ?.filter { it.isRemovable }?.forEach { v -> v.directory?.let { list += sdName to it } }
        }
        list
    }
    fun titleOf(f: File) = volumes.firstOrNull { it.second.absolutePath == f.absolutePath }?.first ?: f.name
    fun volumeOf(f: File) = volumes.filter { f.absolutePath == it.second.absolutePath || f.absolutePath.startsWith(it.second.absolutePath + "/") }
        .maxByOrNull { it.second.absolutePath.length }
    val picking = clipboard
    val listed = if (picking != null) shown.filter { it.isDirectory } else shown

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(start = 20.dp, top = 14.dp, end = 20.dp)) {
            picking?.let { (files, move) ->
                WpText(
                    context.resources.getQuantityString(if (move) R.plurals.files_move_to else R.plurals.files_copy_to, files.size, files.size).uppercase(),
                    style = WpType.AppTitle,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            // The path: home, then each folder above this one; a tap goes back there.
            Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.wpClickable { stack.clear(); filter = null }.padding(end = 4.dp, top = 2.dp, bottom = 2.dp)) {
                    WpIcon(Glyphs.Home, tint = if (current == null) Wp.palette.foreground else Wp.accent, size = 20.dp)
                }
                stack.dropLast(1).forEachIndexed { i, dir ->
                    WpText(" › ", style = WpType.Normal, color = Wp.accent)
                    WpText(
                        titleOf(dir).lowercase(), style = WpType.Normal, color = Wp.accent, maxLines = 1,
                        modifier = Modifier.wpClickable { while (stack.size > i + 1) stack.removeAt(stack.lastIndex); filter = null },
                    )
                }
                if (current != null) WpText(" ›", style = WpType.Normal, color = Wp.accent)
            }
            WpText(
                (current?.let { titleOf(it) } ?: stringResource(R.string.files_root)).lowercase(),
                style = WpType.Header,
                maxLines = 1,
                softWrap = false,
            )
            filter?.let { q ->
                WpText(
                    stringResource(R.string.files_search_in, (current?.let { titleOf(it) } ?: phoneName).lowercase()),
                    style = WpType.Small, color = Wp.palette.subtle, modifier = Modifier.padding(top = 4.dp),
                )
                WpTextBox(q, { filter = it }, Modifier.fillMaxWidth().padding(top = 4.dp), placeholder = stringResource(R.string.files_search))
            }
            busy?.let { WpText(it, style = WpType.Normal, color = Wp.accent, modifier = Modifier.padding(top = 6.dp)) }
        }
        Box(Modifier.weight(1f)) {
            val q = filter?.trim().orEmpty()
            when {
                filter != null && q.isNotEmpty() -> SearchResults(current ?: volumes.first().second, q, showHidden, reload, onOpen = { open(it) }) { f ->
                    volumeOf(f)?.let { (name, root) ->
                        name.lowercase() + (f.parentFile?.absolutePath.orEmpty().removePrefix(root.absolutePath)).replace('/', '\\')
                    } ?: f.parent.orEmpty()
                }
                current == null -> Places(volumes, onOpen = { stack.add(it) })
                else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 12.dp, top = 8.dp, bottom = 16.dp)) {
                    if (listed.isEmpty()) item { WpText(stringResource(R.string.files_empty), style = WpType.Large, color = Wp.palette.subtle) }
                    items(listed, key = { it.absolutePath }) { f ->
        androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.animateItem()) {
                        if (picking != null) {
                            // Choosing where to move / copy: only the folders, as plain names.
                            WpText(
                                f.name, style = WpType.ExtraLarge.copy(fontSize = 26.sp), maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth().wpClickable { open(f) }.padding(vertical = 8.dp),
                            )
                            return@items
                        }
                        val sel = selected
                        val checked = sel != null && f in sel
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .slideInStaggered(listed.indexOf(f).coerceAtMost(12))
                                .wpCombinedClickable(onLongClick = { if (sel == null) selected = setOf(f) }) {
                                    if (sel != null) {
                                        selected = if (checked) sel - f else sel + f
                                    } else {
                                        open(f)
                                    }
                                }
                                .padding(vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (sel != null) {
                                Box(
                                    Modifier.size(26.dp).background(if (checked) Wp.accent else Color.Transparent).border(2.dp, Wp.palette.foreground),
                                    contentAlignment = Alignment.Center,
                                ) { if (checked) WpIcon(WpIcons.Check, tint = Color.White, size = 18.dp) }
                                Spacer(Modifier.width(14.dp))
                            }
                            FileSquare(f, reload)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                WpText(f.name, style = WpType.Large.copy(fontSize = 22.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                val d = Date(f.lastModified())
                                WpText(DateFormat.getDateFormat(context).format(d) + " " + DateFormat.getTimeFormat(context).format(d), style = WpType.Small, color = Wp.palette.subtle, maxLines = 1)
                                if (!f.isDirectory) WpText(Formatter.formatShortFileSize(context, f.length()), style = WpType.Small, color = Wp.palette.subtle, maxLines = 1)
                            }
                        }
                    
        }
    }
                }
            }
        }
        val sel = selected
        when {
            picking != null -> Row(
                Modifier.fillMaxWidth().background(Wp.palette.chrome).padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                WpButton(stringResource(if (picking.second) R.string.files_move_here else R.string.files_copy_here), Modifier.weight(1f), enabled = current != null) { paste() }
                WpButton(stringResource(R.string.cancel), Modifier.weight(1f)) { clipboard = null }
            }
            sel != null -> WpAppBar(
                // Delete, move, copy and share, as on Windows Phone.
                buttons = listOf(
                    AppBarButton(WpIcons.Delete, stringResource(R.string.delete), enabled = sel.isNotEmpty()) { confirmDelete = sel.toList() },
                    AppBarButton(WpIcons.Folder, stringResource(R.string.files_move), enabled = sel.isNotEmpty()) { clipboard = sel.toList() to true; selected = null },
                    AppBarButton(Glyphs.Document, stringResource(R.string.files_copy), enabled = sel.isNotEmpty()) { clipboard = sel.toList() to false; selected = null },
                    AppBarButton(WpIcons.Share, stringResource(R.string.share), enabled = sel.any { it.isFile }) { share(sel.toList()); selected = null },
                ),
                menuItems = listOf(
                    AppBarMenuItem(stringResource(R.string.photos_select_all)) { selected = if (sel.size == listed.size) emptySet() else listed.toSet() },
                    AppBarMenuItem(stringResource(R.string.files_rename), enabled = sel.size == 1) {
                        val f = sel.first()
                        naming = f.name to { name -> scope.launch { withContext(Dispatchers.IO) { f.renameTo(File(f.parentFile, name)) }; reload++ } }
                        selected = null
                    },
                    AppBarMenuItem(stringResource(R.string.files_properties), enabled = sel.size == 1) { info = sel.first(); selected = null },
                ),
            )
            current != null -> WpAppBar(
                buttons = listOf(
                    AppBarButton(WpIcons.Add, stringResource(R.string.files_new_folder)) {
                        naming = context.getString(R.string.files_new_folder_default) to { name -> scope.launch { withContext(Dispatchers.IO) { File(current, name).mkdirs() }; reload++ } }
                        creating = true
                    },
                    AppBarButton(WpIcons.SelectAll, stringResource(R.string.photos_select), enabled = listed.isNotEmpty()) { selected = emptySet() },
                    AppBarButton(WpIcons.Search, stringResource(R.string.files_search)) { filter = if (filter == null) "" else null },
                ),
                menuItems = Sort.entries.map { s ->
                    AppBarMenuItem(stringResource(R.string.files_sort_by, stringResource(when (s) { Sort.Name -> R.string.files_sort_name; Sort.Date -> R.string.files_sort_date; Sort.Size -> R.string.files_sort_size; Sort.Type -> R.string.files_sort_type })) + if (s == sort) " ✓" else "") {
                        sort = s
                        prefs.edit().putString("sort", s.name).apply()
                    }
                } + AppBarMenuItem(stringResource(if (showHidden) R.string.files_hide_hidden else R.string.files_show_hidden)) {
                    showHidden = !showHidden
                    prefs.edit().putBoolean("hidden", showHidden).apply()
                },
            )
            else -> WpAppBar(
                buttons = listOf(AppBarButton(WpIcons.Search, stringResource(R.string.files_search)) { filter = if (filter == null) "" else null }),
            )
        }
    }

    naming?.let { (initial, onDone) ->
        NameBox(initial, creating, onDismiss = { naming = null; creating = false }) { name -> naming = null; creating = false; onDone(name) }
    }
    confirmDelete?.let { list ->
        WpMessageBox(
            title = stringResource(R.string.files_delete_title),
            message = context.resources.getQuantityString(R.plurals.files_delete, list.size, list.size),
            confirm = stringResource(R.string.delete),
            onConfirm = {
                confirmDelete = null
                selected = null
                scope.launch {
                    withContext(Dispatchers.IO) { list.forEach { runCatching { it.deleteRecursively() } } }
                    reload++
                }
            },
            dismiss = stringResource(R.string.cancel),
            onDismiss = { confirmDelete = null },
        )
    }
    info?.let { f ->
        val size by androidx.compose.runtime.produceState<Long?>(null, f) { value = withContext(Dispatchers.IO) { dirSize(f) } }
        WpMessageBox(
            title = f.name,
            message = listOf(
                f.absolutePath,
                stringResource(R.string.files_size) + ": " + (size?.let { Formatter.formatFileSize(context, it) } ?: "…"),
                stringResource(R.string.files_modified) + ": " + DateFormat.getDateFormat(context).format(Date(f.lastModified())) + " " + DateFormat.getTimeFormat(context).format(Date(f.lastModified())),
                if (f.isFile) stringResource(R.string.files_type) + ": " + mimeOf(f) else "",
            ).filter { it.isNotBlank() }.joinToString("\n"),
            confirm = stringResource(R.string.ok),
            onConfirm = { info = null },
            onDismiss = { info = null },
        )
    }
}

/**
 * The first page, after Windows Phone's Files: the phone and each SD card by name, used and
 * free space at the two ends, and a bar.
 */
@Composable
private fun Places(volumes: List<Pair<String, File>>, onOpen: (File) -> Unit) {
    val context = LocalContext.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 16.dp)) {
        items(volumes, key = { it.second.absolutePath }) { (name, dir) ->
        androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.animateItem()) {
            Column(Modifier.fillMaxWidth().wpClickable { onOpen(dir) }.padding(vertical = 10.dp)) {
                WpText(name, style = WpType.ExtraLarge.copy(fontSize = 28.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                val stat = remember(dir) { runCatching { StatFs(dir.path) }.getOrNull() }
                if (stat != null) {
                    val total = stat.totalBytes
                    val free = stat.availableBytes
                    Row(Modifier.fillMaxWidth()) {
                        WpText(context.getString(R.string.files_used, Formatter.formatShortFileSize(context, total - free)), style = WpType.Small, color = Wp.palette.subtle, modifier = Modifier.weight(1f), maxLines = 1)
                        WpText(context.getString(R.string.files_free, Formatter.formatShortFileSize(context, free)), style = WpType.Small, color = Wp.palette.subtle, maxLines = 1)
                    }
                    Box(Modifier.fillMaxWidth().padding(top = 6.dp).height(10.dp).background(Wp.accent.copy(alpha = 0.2f))) {
                        Box(Modifier.fillMaxWidth(((total - free).toFloat() / total.coerceAtLeast(1)).coerceIn(0f, 1f)).height(10.dp).background(Wp.accent))
                    }
                }
            }
        
        }
    }
    }
}

/** The square of a row: a folder in the accent colour with its number of items; a file grey with its kind, or the picture itself. */
@Composable
private fun FileSquare(f: File, reload: Int) {
    Box(Modifier.size(72.dp).background(if (f.isDirectory) Wp.accent else Wp.palette.raised)) {
        if (f.isDirectory) {
            val n = remember(f, reload) { f.list()?.size ?: 0 }
            WpText(n.toString(), style = WpType.Large, color = Color.White, modifier = Modifier.align(Alignment.BottomEnd).padding(end = 7.dp, bottom = 3.dp))
        } else {
            WpIcon(iconFor(f), Modifier.align(Alignment.Center), tint = Wp.palette.foreground, size = 34.dp)
            if (f.extension.lowercase() in setOf("jpg", "jpeg", "png", "webp", "gif", "heic", "bmp")) {
                com.wpreborn.core.ui.Thumbnail(Uri.fromFile(f), Modifier.fillMaxSize(), sizePx = 192)
            }
        }
    }
}

/** Search in a folder and the folders inside it, after the phone's "search in documents". */
@Composable
private fun SearchResults(base: File, query: String, hidden: Boolean, reload: Int, onOpen: (File) -> Unit, where: (File) -> String) {
    val context = LocalContext.current
    val results by androidx.compose.runtime.produceState<List<File>?>(null, base, query, hidden, reload) {
        kotlinx.coroutines.delay(250)
        value = withContext(Dispatchers.IO) {
            base.walkTopDown().maxDepth(10).onEnter { it == base || hidden || !it.name.startsWith(".") }
                .takeWhile { isActive }
                .filter { it != base && (hidden || !it.name.startsWith(".")) && it.name.contains(query, ignoreCase = true) }
                .take(300).toList()
        }
    }
    val list = results
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 12.dp, top = 8.dp, bottom = 16.dp)) {
        if (list != null && list.isEmpty()) item { WpText(stringResource(R.string.files_no_results), style = WpType.Large, color = Wp.palette.subtle) }
        items(list.orEmpty(), key = { it.absolutePath }) { f ->
        androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.animateItem()) {
            Row(Modifier.fillMaxWidth().wpClickable { onOpen(f) }.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                FileSquare(f, reload)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    WpText(f.name, style = WpType.Large.copy(fontSize = 22.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val d = Date(f.lastModified())
                    WpText(DateFormat.getDateFormat(context).format(d) + " " + DateFormat.getTimeFormat(context).format(d), style = WpType.Small, color = Wp.palette.subtle, maxLines = 1)
                    WpText(where(f), style = WpType.Small, color = Wp.palette.subtle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        
        }
    }
    }
}

/** "Create a new folder" / "rename": a page with the name box and check / cancel, as on the phone. */
@Composable
private fun NameBox(initial: String, creating: Boolean, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(initial, androidx.compose.ui.text.TextRange(0, initial.length))) }
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    BackHandler(onBack = onDismiss)
    Column(Modifier.fillMaxSize().background(Wp.palette.background).wpClickable(tilt = false) { }) {
        Column(Modifier.weight(1f).padding(start = 20.dp, end = 20.dp, top = 20.dp)) {
            WpText(stringResource(if (creating) R.string.files_create_folder_title else R.string.files_rename).uppercase(), style = WpType.AppTitle)
            Spacer(Modifier.height(24.dp))
            WpText(stringResource(R.string.files_folder_name), style = WpType.Subtle, color = Wp.palette.subtle)
            androidx.compose.foundation.text.BasicTextField(
                value = name,
                onValueChange = { name = it.copy(text = it.text.replace("/", "")) },
                singleLine = true,
                textStyle = WpType.Normal.copy(color = Color.Black, fontSize = 18.sp),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(Wp.accent),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { if (name.text.isNotBlank()) onSave(name.text.trim()) }),
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp).background(Color.White).border(2.dp, Wp.accent).padding(10.dp).focusRequester(focus),
            )
        }
        WpAppBar(
            buttons = listOf(
                AppBarButton(WpIcons.Check, stringResource(R.string.save), enabled = name.text.isNotBlank()) { onSave(name.text.trim()) },
                AppBarButton(WpIcons.Close, stringResource(R.string.cancel), onClick = onDismiss),
            ),
        )
    }
}
