package com.wpreborn.apps.recorder

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wpreborn.R
import com.wpreborn.apps.common.WpActivity
import com.wpreborn.core.icons.Glyphs
import com.wpreborn.core.icons.WpIcons
import com.wpreborn.core.motion.slideInStaggered
import com.wpreborn.core.theme.Wp
import com.wpreborn.core.theme.WpType
import com.wpreborn.core.ui.ContextMenuItem
import com.wpreborn.core.ui.WpContextMenu
import com.wpreborn.core.ui.WpFormat
import com.wpreborn.core.ui.WpIcon
import com.wpreborn.core.ui.WpPivot
import com.wpreborn.core.ui.WpText
import com.wpreborn.core.ui.wpClickable
import com.wpreborn.core.ui.wpCombinedClickable
import com.wpreborn.permissions.PermissionGate
import com.wpreborn.permissions.rememberPermissionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class Recording(val uri: Uri, val name: String, val durationMs: Long, val date: Long)

/**
 * Voice recorder: a big round record button with the time and a live level meter, and the
 * list of recordings to play, share, rename or delete. Recordings are AAC (.m4a) files in the
 * phone's Recordings folder, so other apps see them too.
 */
class RecorderActivity : WpActivity() {

    @Composable
    override fun Content() {
        val perm = rememberPermissionState(arrayOf(Manifest.permission.RECORD_AUDIO))
        var refresh by remember { mutableStateOf(0) }
        WpPivot(
            title = stringResource(R.string.app_recorder),
            headers = listOf(stringResource(R.string.rec_record), stringResource(R.string.rec_recordings)),
        ) { page ->
            if (page == 0) {
                PermissionGate(perm, stringResource(R.string.perm_microphone), Modifier.padding(start = 20.dp)) {
                    RecordPage { refresh++ }
                }
            } else {
                RecordingsPage(refresh)
            }
        }
    }
}

@Composable
private fun RecordPage(onSaved: () -> Unit) {
    val context = LocalContext.current
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var target by remember { mutableStateOf<Uri?>(null) }
    var started by remember { mutableLongStateOf(0L) }
    var now by remember { mutableLongStateOf(0L) }
    val levels = remember { mutableStateListOf<Float>() }
    LaunchedEffect(recorder) {
        while (recorder != null) {
            now = System.currentTimeMillis()
            val amp = runCatching { recorder?.maxAmplitude ?: 0 }.getOrDefault(0)
            levels.add((amp / 20000f).coerceIn(0.02f, 1f))
            if (levels.size > 60) levels.removeAt(0)
            delay(80)
        }
    }

    fun stop() {
        val r = recorder ?: return
        runCatching { r.stop() }
        r.release()
        recorder = null
        target?.let { uri ->
            if (Build.VERSION.SDK_INT >= 29) {
                context.contentResolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            }
        }
        levels.clear()
        onSaved()
    }

    fun start() {
        val name = "ZUNEX " + SimpleDateFormat("yyyy-MM-dd HH.mm.ss", Locale.US).format(Date())
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "$name.m4a")
            put(MediaStore.MediaColumns.MIME_TYPE, "audio/mp4")
            put(MediaStore.MediaColumns.RELATIVE_PATH, if (Build.VERSION.SDK_INT >= 31) "Recordings/ZUNEX" else "Music/ZUNEX Recordings")
            put(MediaStore.Audio.Media.IS_MUSIC, 0)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = context.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values) ?: return
        val fd = context.contentResolver.openFileDescriptor(uri, "w") ?: return
        val r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
        runCatching {
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioEncodingBitRate(128_000)
            r.setAudioSamplingRate(44_100)
            r.setOutputFile(fd.fileDescriptor)
            r.prepare()
            r.start()
        }.onFailure {
            r.release()
            context.contentResolver.delete(uri, null, null)
            return
        }
        fd.close()
        target = uri
        started = System.currentTimeMillis()
        now = started
        recorder = r
    }
    DisposableEffect(Unit) { onDispose { stop() } }

    val recording = recorder != null
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(24.dp))
        WpText(
            if (recording) WpFormat.duration(now - started) else "00:00",
            style = WpType.LockClock.copy(fontSize = 72.sp),
            color = if (recording) Wp.palette.foreground else Wp.palette.subtle,
        )
        // Level meter: recent loudness as bars, newest on the right.
        val accent = Wp.accent
        Canvas(Modifier.fillMaxWidth().height(90.dp).padding(vertical = 12.dp)) {
            val n = 60
            val w = size.width / n
            levels.forEachIndexed { i, v ->
                val h = v * size.height
                val x = (n - levels.size + i) * w
                drawLine(accent, Offset(x + w / 2, size.height / 2 - h / 2), Offset(x + w / 2, size.height / 2 + h / 2), w * 0.6f)
            }
        }
        Spacer(Modifier.weight(1f))
        Box(
            Modifier
                .size(120.dp)
                .background(if (recording) Color(0xFFE51400) else Wp.accent, CircleShape)
                .wpClickable { if (recording) stop() else start() },
            contentAlignment = Alignment.Center,
        ) {
            if (recording) Box(Modifier.size(36.dp).background(Color.White)) else WpIcon(Glyphs.Microphone, tint = Color.White, size = 56.dp)
        }
        WpText(stringResource(if (recording) R.string.rec_tap_to_stop else R.string.rec_tap_to_record), style = WpType.Normal, color = Wp.palette.subtle, modifier = Modifier.padding(top = 12.dp, bottom = 32.dp))
    }
}

@Composable
private fun RecordingsPage(refresh: Int) {
    val context = LocalContext.current
    var list by remember { mutableStateOf<List<Recording>>(emptyList()) }
    var reload by remember { mutableStateOf(0) }
    LaunchedEffect(refresh, reload) { list = loadRecordings(context) }
    var playing by remember { mutableStateOf<Uri?>(null) }
    val player = remember { arrayOfNulls<MediaPlayer>(1) }
    DisposableEffect(Unit) { onDispose { player[0]?.release() } }
    var menu by remember { mutableStateOf<Recording?>(null) }
    var menuY by remember { mutableFloatStateOf(0f) }
    var top by remember { mutableFloatStateOf(0f) }
    val actions = com.wpreborn.apps.music.rememberSongActions { reload++ }

    fun toggle(r: Recording) {
        player[0]?.release()
        player[0] = null
        if (playing == r.uri) {
            playing = null
            return
        }
        player[0] = runCatching {
            MediaPlayer().apply {
                setDataSource(context, r.uri)
                setOnCompletionListener { playing = null }
                prepare()
                start()
            }
        }.getOrNull()
        playing = if (player[0] != null) r.uri else null
    }

    Box(Modifier.fillMaxSize().onGloballyPositioned { top = it.boundsInWindow().top }) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 16.dp, bottom = 16.dp)) {
            if (list.isEmpty()) item { WpText(stringResource(R.string.rec_empty), style = WpType.Large, color = Wp.palette.subtle) }
            itemsIndexed(list, key = { _, r -> r.uri.toString() }) { i, r ->
                var bottom by remember { mutableFloatStateOf(0f) }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .slideInStaggered(i)
                        .onGloballyPositioned { bottom = it.boundsInWindow().bottom }
                        .wpCombinedClickable(onLongClick = { menuY = bottom - top; menu = r }) { toggle(r) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    WpIcon(if (playing == r.uri) WpIcons.Pause else WpIcons.Play, size = 28.dp, tint = if (playing == r.uri) Wp.accent else Wp.palette.foreground)
                    Spacer(Modifier.padding(start = 12.dp))
                    Column(Modifier.weight(1f)) {
                        WpText(r.name, style = WpType.Large, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        WpText(WpFormat.shortStamp(context, r.date) + "  ·  " + WpFormat.duration(r.durationMs), style = WpType.Subtle, color = Wp.palette.subtle)
                    }
                }
            }
        }
        menu?.let { r ->
            val asSong = com.wpreborn.data.media.Song(ContentUris.parseId(r.uri), r.name, "", "", 0, r.durationMs, 0)
            WpContextMenu(
                items = listOf(
                    ContextMenuItem(stringResource(R.string.share)) {
                        runCatching {
                            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("audio/mp4").putExtra(Intent.EXTRA_STREAM, r.uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                    },
                    ContextMenuItem(stringResource(R.string.music_rename)) { actions.rename(asSong) },
                    ContextMenuItem(stringResource(R.string.delete)) { if (playing == r.uri) toggle(r); actions.delete(asSong) },
                ),
                anchorY = menuY,
                onDismiss = { menu = null },
            )
        }
        actions.Dialogs()
    }
}

private suspend fun loadRecordings(context: Context): List<Recording> = withContext(Dispatchers.IO) {
    val out = ArrayList<Recording>()
    val path = if (Build.VERSION.SDK_INT >= 31) "Recordings/ZUNEX%" else "Music/ZUNEX Recordings%"
    runCatching {
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.DATE_ADDED),
            "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?",
            arrayOf(path),
            "${MediaStore.Audio.Media.DATE_ADDED} DESC",
        )?.use { c ->
            while (c.moveToNext()) {
                out += Recording(
                    ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, c.getLong(0)),
                    c.getString(1) ?: "",
                    c.getLong(2),
                    c.getLong(3) * 1000,
                )
            }
        }
    }
    out
}
