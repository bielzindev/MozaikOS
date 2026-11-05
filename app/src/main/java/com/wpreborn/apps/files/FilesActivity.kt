package com.wpreborn.apps.files

import android.content.Context
import android.content.Intent
import android.net.Uri
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

    BackHandler(enabled = selected != null || filter != null || stack.isNotEmpty()) {
        when {
            selected != null -> selected = null
            filter != null -> filter = null
            else -> stack.removeAt(stack.lastIndex)
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

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(start = 20.dp, top = 14.dp, end = 20.dp)) {
            // The path as on Windows Phone: "\\ phone \ documents \", in the accent colour.
            val phone = stringResource(R.string.files_phone)
            val crumbs = remember(current) {
                val root = Environment.getExternalStorageDirectory().absolutePath
                current?.absolutePath?.let { p ->
                    val rel = if (p.startsWith(root)) listOf(phone) + p.removePrefix(root).split('/').filter { it.isNotEmpty() } else p.split('/').filter { it.isNotEmpty() }
                    rel.dropLast(1)
                }.orEmpty()
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                WpIcon(WpIcons.Phone, tint = Wp.accent, size = 18.dp)
                Spacer(Modifier.width(6.dp))
                WpText(
                    "\\\\ " + crumbs.joinToString("") { "$it \\ " },
                    style = WpType.Large.copy(fontSize = 19.sp), color = Wp.accent, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            WpText(
                (current?.name?.let { if (current.absolutePath == Environment.getExternalStorageDirectory().absolutePath) phone else it } ?: stringResource(R.string.files_root)).lowercase(),
                style = WpType.Header,
                maxLines = 1,
                softWrap = false,
            )
            filter?.let { q ->
                WpTextBox(q, { filter = it }, Modifier.fillMaxWidth().padding(top = 6.dp), placeholder = stringResource(R.string.files_search))
            }
            clipboard?.let { (files, move) ->
                Box(Modifier.fillMaxWidth().padding(top = 8.dp).background(Wp.accent).padding(10.dp)) {
                    WpText(
                        context.resources.getQuantityString(if (move) R.plurals.files_to_move else R.plurals.files_to_copy, files.size, files.size),
                        style = WpType.Normal,
                        color = Color.White,
                    )
                }
            }
            busy?.let { WpText(it, style = WpType.Normal, color = Wp.accent, modifier = Modifier.padding(top = 6.dp)) }
        }
        Box(Modifier.weight(1f)) {
            if (current == null) {
                Places(onOpen = { stack.add(it) })
            } else {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 12.dp, top = 6.dp, bottom = 16.dp)) {
                    if (shown.isEmpty()) item { WpText(stringResource(R.string.files_empty), style = WpType.Large, color = Wp.palette.subtle) }
                    items(shown, key = { it.absolutePath }) { f ->
                        val sel = selected
                        val checked = sel != null && f in sel
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .slideInStaggered(shown.indexOf(f).coerceAtMost(12))
                                .wpCombinedClickable(onLongClick = { if (sel == null) selected = setOf(f) }) {
                                    if (sel != null) {
                                        selected = if (checked) sel - f else sel + f
                                    } else {
                                        open(f)
                                    }
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (sel != null) {
                                Box(
                                    Modifier.size(26.dp).background(if (checked) Wp.accent else Color.Transparent).border(2.dp, Wp.palette.foreground),
                                    contentAlignment = Alignment.Center,
                                ) { if (checked) WpIcon(WpIcons.Check, tint = Color.White, size = 18.dp) }
                                Spacer(Modifier.width(12.dp))
                            }
                            // Folders: an accent square with the number of items; files: a grey
                            // square with the kind of file, like the Files app of Windows Phone.
                            Box(Modifier.size(84.dp).background(if (f.isDirectory) Wp.accent else Wp.palette.raised)) {
                                if (f.isDirectory) {
                                    val n = remember(f, reload) { f.list()?.size ?: 0 }
                                    WpText(n.toString(), style = WpType.Large, color = Color.White, modifier = Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = 4.dp))
                                } else {
                                    WpIcon(iconFor(f), Modifier.align(Alignment.Center), tint = Wp.palette.foreground, size = 36.dp)
                                }
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                WpText(f.name, style = WpType.Large.copy(fontSize = 24.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                val d = Date(f.lastModified())
                                WpText(DateFormat.getDateFormat(context).format(d) + " " + DateFormat.getTimeFormat(context).format(d), style = WpType.Normal, color = Wp.palette.subtle, maxLines = 1)
                                if (!f.isDirectory) WpText(Formatter.formatShortFileSize(context, f.length()), style = WpType.Normal, color = Wp.palette.subtle, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
        val sel = selected
        when {
            sel != null -> WpAppBar(
                // Delete, move, copy and share, as on Windows Phone.
                buttons = listOf(
                    AppBarButton(WpIcons.Delete, stringResource(R.string.delete), enabled = sel.isNotEmpty()) { confirmDelete = sel.toList() },
                    AppBarButton(WpIcons.Folder, stringResource(R.string.files_move), enabled = sel.isNotEmpty()) { clipboard = sel.toList() to true; selected = null },
                    AppBarButton(Glyphs.Document, stringResource(R.string.files_copy), enabled = sel.isNotEmpty()) { clipboard = sel.toList() to false; selected = null },
                    AppBarButton(WpIcons.Share, stringResource(R.string.share), enabled = sel.any { it.isFile }) { share(sel.toList()); selected = null },
                ),
                menuItems = listOf(
                    AppBarMenuItem(stringResource(R.string.photos_select_all)) { selected = if (sel.size == shown.size) emptySet() else shown.toSet() },
                    AppBarMenuItem(stringResource(R.string.files_rename), enabled = sel.size == 1) {
                        val f = sel.first()
                        naming = f.name to { name -> scope.launch { withContext(Dispatchers.IO) { f.renameTo(File(f.parentFile, name)) }; reload++ } }
                        selected = null
                    },
                    AppBarMenuItem(stringResource(R.string.files_properties), enabled = sel.size == 1) { info = sel.first(); selected = null },
                ),
            )
            clipboard != null -> WpAppBar(
                buttons = listOf(
                    AppBarButton(WpIcons.Check, stringResource(R.string.files_paste), enabled = current != null) { paste() },
                    AppBarButton(WpIcons.Close, stringResource(R.string.cancel)) { clipboard = null },
                ),
            )
            current != null -> WpAppBar(
                buttons = listOf(
                    AppBarButton(WpIcons.SelectAll, stringResource(R.string.photos_select), enabled = shown.isNotEmpty()) { selected = emptySet() },
                    AppBarButton(WpIcons.Add, stringResource(R.string.files_new_folder)) {
                        naming = context.getString(R.string.files_new_folder_default) to { name -> scope.launch { withContext(Dispatchers.IO) { File(current, name).mkdirs() }; reload++ } }
                        creating = true
                    },
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
            else -> {}
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
 * The first page, after Windows Phone's Files: the phone and the SD card as accent squares
 * with their number of items, used and free space and a bar; then the usual folders.
 */
@Composable
private fun Places(onOpen: (File) -> Unit) {
    val context = LocalContext.current
    val places = remember {
        val list = ArrayList<Place>()
        val internal = Environment.getExternalStorageDirectory()
        list += Place(context.getString(R.string.files_phone), internal, WpIcons.Storage, true)
        if (Build.VERSION.SDK_INT >= 30) {
            val sm = context.getSystemService(android.os.storage.StorageManager::class.java)
            sm?.storageVolumes?.filter { it.isRemovable }?.forEach { v -> v.directory?.let { list += Place(v.getDescription(context), it, WpIcons.Storage, true) } }
        }
        fun dir(type: String, label: Int, icon: ImageVector) {
            val d = Environment.getExternalStoragePublicDirectory(type)
            if (d.exists()) list += Place(context.getString(label), d, icon, false)
        }
        dir(Environment.DIRECTORY_DOWNLOADS, R.string.files_downloads, WpIcons.Download)
        dir(Environment.DIRECTORY_DOCUMENTS, R.string.files_documents, Glyphs.Document)
        dir(Environment.DIRECTORY_DCIM, R.string.files_camera, WpIcons.Camera)
        dir(Environment.DIRECTORY_PICTURES, R.string.files_pictures, WpIcons.Photos)
        dir(Environment.DIRECTORY_MUSIC, R.string.files_music, WpIcons.MusicNote)
        dir(Environment.DIRECTORY_MOVIES, R.string.files_videos, Glyphs.Video)
        list
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 16.dp)) {
        items(places, key = { it.dir.absolutePath + it.name }) { p ->
            val count = remember(p.dir) { p.dir.list()?.size ?: 0 }
            Row(Modifier.fillMaxWidth().wpClickable { onOpen(p.dir) }.padding(vertical = 6.dp), verticalAlignment = if (p.volume) Alignment.Top else Alignment.CenterVertically) {
                Box(Modifier.size(if (p.volume) 96.dp else 72.dp).background(Wp.accent)) {
                    if (!p.volume) WpIcon(p.icon, Modifier.align(Alignment.Center), tint = Color.White, size = 30.dp)
                    WpText(count.toString(), style = WpType.Large, color = Color.White, modifier = Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = 4.dp))
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    WpText(p.name, style = WpType.ExtraLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (p.volume) {
                        val stat = remember(p.dir) { runCatching { StatFs(p.dir.path) }.getOrNull() }
                        if (stat != null) {
                            val total = stat.totalBytes
                            val free = stat.availableBytes
                            Row(Modifier.fillMaxWidth().padding(top = 2.dp)) {
                                WpText(context.getString(R.string.files_used, Formatter.formatShortFileSize(context, total - free)), style = WpType.Normal, color = Wp.palette.subtle, modifier = Modifier.weight(1f), maxLines = 1)
                                WpText(context.getString(R.string.files_free, Formatter.formatShortFileSize(context, free)), style = WpType.Normal, color = Wp.palette.subtle, maxLines = 1)
                            }
                            Box(Modifier.fillMaxWidth().padding(top = 6.dp).height(8.dp).background(Wp.accent.copy(alpha = 0.18f))) {
                                Box(Modifier.fillMaxWidth(((total - free).toFloat() / total.coerceAtLeast(1)).coerceIn(0f, 1f)).height(8.dp).background(Wp.accent))
                            }
                        }
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
