package com.wpreborn.apps.music

import android.app.Activity
import android.app.RecoverableSecurityException
import android.content.ContentValues
import android.content.Context
import android.content.IntentSender
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.wpreborn.R
import com.wpreborn.core.theme.Wp
import com.wpreborn.core.theme.WpType
import com.wpreborn.core.ui.WpButton
import com.wpreborn.core.ui.WpMessageBox
import com.wpreborn.core.ui.WpText
import com.wpreborn.core.ui.WpTextBox
import com.wpreborn.data.media.Favorites
import com.wpreborn.data.media.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Renaming and deleting songs. The files belong to the user, so Android asks them to confirm
 * each change (a system dialog on Android 11+, or a one-time grant on Android 10).
 */
class SongActions internal constructor(
    val rename: (Song) -> Unit,
    val delete: (Song) -> Unit,
    /** Deletes several songs after one confirmation (one system dialog on Android 11+). */
    val deleteMany: (List<Song>, () -> Unit) -> Unit,
    /** The rename / delete / error dialogs: place it last in the screen so it draws on top. */
    val Dialogs: @Composable () -> Unit,
)

/** Remembers the dialogs and permission flow for rename / delete; [onChanged] reloads the library. */
@Composable
fun rememberSongActions(onChanged: () -> Unit): SongActions {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var renaming by remember { mutableStateOf<Song?>(null) }
    var deleting by remember { mutableStateOf<Song?>(null) }
    var deletingMany by remember { mutableStateOf<Pair<List<Song>, () -> Unit>?>(null) }
    // What to do once Android grants access to the file.
    var pending by remember { mutableStateOf<(suspend () -> Unit)?>(null) }
    var failed by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val next = pending
        pending = null
        if (result.resultCode == Activity.RESULT_OK && next != null) scope.launch { next() }
    }
    fun ask(sender: IntentSender, then: suspend () -> Unit) {
        pending = then
        launcher.launch(IntentSenderRequest.Builder(sender).build())
    }

    fun doRename(song: Song, title: String) {
        scope.launch {
            when (val r = SongFiles.rename(context, song, title)) {
                is SongFiles.Result.Done -> onChanged()
                is SongFiles.Result.NeedsConsent -> ask(r.sender) {
                    if (SongFiles.rename(context, song, title) is SongFiles.Result.Done) onChanged() else failed = true
                }
                SongFiles.Result.Failed -> failed = true
            }
        }
    }

    fun doDelete(song: Song) {
        scope.launch {
            when (val r = SongFiles.delete(context, song)) {
                is SongFiles.Result.Done -> { Favorites.get(context).remove(song.id.toString()); onChanged() }
                // On Android 11+ the system dialog performs the deletion itself.
                is SongFiles.Result.NeedsConsent -> ask(r.sender) {
                    if (Build.VERSION.SDK_INT >= 30 || SongFiles.delete(context, song) is SongFiles.Result.Done) {
                        Favorites.get(context).remove(song.id.toString())
                        onChanged()
                    } else {
                        failed = true
                    }
                }
                SongFiles.Result.Failed -> failed = true
            }
        }
    }

    fun doDeleteMany(songs: List<Song>, done: () -> Unit) {
        fun finish() {
            val fav = Favorites.get(context)
            songs.forEach { fav.remove(it.id.toString()) }
            done()
            onChanged()
        }
        scope.launch {
            when (val r = SongFiles.deleteMany(context, songs)) {
                is SongFiles.Result.Done -> finish()
                // Android 11+: the system dialog deletes them all; Android 10: one grant per file.
                is SongFiles.Result.NeedsConsent -> ask(r.sender) {
                    if (Build.VERSION.SDK_INT >= 30) finish() else doDeleteMany(songs, done)
                }
                SongFiles.Result.Failed -> { failed = true; onChanged() }
            }
        }
    }

    val dialogs: @Composable () -> Unit = {
        deletingMany?.let { (list, done) ->
            WpMessageBox(
                title = stringResource(R.string.music_delete_many_title),
                message = context.resources.getQuantityString(R.plurals.music_delete_many_message, list.size, list.size),
                confirm = stringResource(R.string.delete),
                onConfirm = { deletingMany = null; doDeleteMany(list, done) },
                dismiss = stringResource(R.string.cancel),
                onDismiss = { deletingMany = null },
            )
        }
        renaming?.let { s -> RenameDialog(s, onDismiss = { renaming = null }) { title -> renaming = null; doRename(s, title) } }
        deleting?.let { s ->
            WpMessageBox(
                title = stringResource(R.string.music_delete_title),
                message = stringResource(R.string.music_delete_message, s.title),
                confirm = stringResource(R.string.delete),
                onConfirm = { deleting = null; doDelete(s) },
                dismiss = stringResource(R.string.cancel),
                onDismiss = { deleting = null },
            )
        }
        if (failed) {
            WpMessageBox(
                title = stringResource(R.string.music_edit_failed_title),
                message = stringResource(R.string.music_edit_failed),
                confirm = stringResource(R.string.ok),
                onConfirm = { failed = false },
                onDismiss = { failed = false },
            )
        }
    }
    val dialogsState = androidx.compose.runtime.rememberUpdatedState(dialogs)
    return remember {
        SongActions(
            rename = { renaming = it },
            delete = { deleting = it },
            deleteMany = { list, done -> if (list.isNotEmpty()) deletingMany = list to done },
            Dialogs = { dialogsState.value() },
        )
    }
}

@Composable
private fun RenameDialog(song: Song, onDismiss: () -> Unit, onRename: (String) -> Unit) {
    var text by remember { mutableStateOf(song.title) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    BackHandler(onBack = onDismiss)
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)).clickable(remember { MutableInteractionSource() }, null, onClick = onDismiss),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(Wp.palette.chrome)
                .clickable(remember { MutableInteractionSource() }, null) {}
                .statusBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 20.dp),
        ) {
            WpText(stringResource(R.string.music_rename_title), style = WpType.Large.copy(fontWeight = FontWeight.SemiBold))
            Spacer(Modifier.height(12.dp))
            WpTextBox(text, { text = it }, Modifier.fillMaxWidth(), focusRequester = focus, onImeAction = { if (text.isNotBlank()) onRename(text.trim()) })
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                WpButton(stringResource(R.string.save), Modifier.weight(1f)) { if (text.isNotBlank()) onRename(text.trim()) }
                WpButton(stringResource(R.string.cancel), Modifier.weight(1f), onClick = onDismiss)
            }
        }
    }
}

/** MediaStore operations behind the dialogs. */
object SongFiles {
    sealed interface Result {
        data object Done : Result
        data class NeedsConsent(val sender: IntentSender) : Result
        data object Failed : Result
    }

    /** New title in the library, and the file renamed to match (keeping its extension). */
    suspend fun rename(context: Context, song: Song, title: String): Result = withContext(Dispatchers.IO) {
        val cr = context.contentResolver
        try {
            val ext = cr.query(song.uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0)?.substringAfterLast('.', "") else null
            }.orEmpty()
            val safe = title.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(120)
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.TITLE, title)
                put(MediaStore.MediaColumns.DISPLAY_NAME, if (ext.isNotEmpty()) "$safe.$ext" else safe)
            }
            if (cr.update(song.uri, values, null, null) > 0) Result.Done else Result.Failed
        } catch (e: SecurityException) {
            consent(context, e, write = true, song)
        } catch (_: Exception) {
            Result.Failed
        }
    }

    suspend fun delete(context: Context, song: Song): Result = withContext(Dispatchers.IO) {
        try {
            if (context.contentResolver.delete(song.uri, null, null) > 0) Result.Done else Result.Failed
        } catch (e: SecurityException) {
            consent(context, e, write = false, song)
        } catch (_: Exception) {
            Result.Failed
        }
    }

    /**
     * Deletes [songs]; files Android protects are gathered into one delete request
     * (Android 11+), or asked for one at a time (Android 10). Already gone counts as deleted.
     */
    suspend fun deleteMany(context: Context, songs: List<Song>): Result = withContext(Dispatchers.IO) {
        val protected = ArrayList<android.net.Uri>()
        for (s in songs) {
            try {
                context.contentResolver.delete(s.uri, null, null)
            } catch (e: SecurityException) {
                if (Build.VERSION.SDK_INT >= 30) {
                    protected += s.uri
                } else {
                    return@withContext consent(context, e, write = false, s)
                }
            } catch (_: Exception) {
            }
        }
        if (protected.isEmpty()) Result.Done
        else Result.NeedsConsent(MediaStore.createDeleteRequest(context.contentResolver, protected).intentSender)
    }

    private fun consent(context: Context, e: SecurityException, write: Boolean, song: Song): Result = when {
        Build.VERSION.SDK_INT >= 30 -> {
            val pi = if (write) MediaStore.createWriteRequest(context.contentResolver, listOf(song.uri)) else MediaStore.createDeleteRequest(context.contentResolver, listOf(song.uri))
            Result.NeedsConsent(pi.intentSender)
        }
        e is RecoverableSecurityException -> Result.NeedsConsent(e.userAction.actionIntent.intentSender)
        else -> Result.Failed
    }
}
