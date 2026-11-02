package com.wpreborn.apps.translator

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.wpreborn.R
import com.wpreborn.apps.common.WpActivity
import com.wpreborn.core.icons.WpIcons
import com.wpreborn.core.theme.Wp
import com.wpreborn.core.theme.WpType
import com.wpreborn.core.ui.AppBarButton
import com.wpreborn.core.ui.AppBarMenuItem
import com.wpreborn.core.ui.WpAppBar
import com.wpreborn.core.ui.WpIcon
import com.wpreborn.core.ui.WpPivot
import com.wpreborn.core.ui.WpText
import com.wpreborn.core.ui.WpTextBox
import com.wpreborn.core.ui.wpClickable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import kotlin.coroutines.resume

private const val AUTO = "auto"

private fun languageName(code: String, ctx: Context): String =
    if (code == AUTO) ctx.getString(R.string.tr_detect) else Locale(code).getDisplayLanguage(Locale.getDefault()).lowercase()

private suspend fun <T> com.google.android.gms.tasks.Task<T>.await(): T? = suspendCancellableCoroutine { c ->
    addOnSuccessListener { c.resume(it) }
    addOnFailureListener { c.resume(null) }
}

private data class Entry(val from: String, val to: String, val text: String, val result: String)

/**
 * Translator, after Bing Translator on Windows Phone: type, speak or photograph text and get
 * it in another language; hear it, copy it, share it; history. Translation runs on the phone
 * (ML Kit): each language is downloaded once (about 30 MB) and then works offline.
 */
class TranslatorActivity : WpActivity() {
    @Composable
    override fun Content() {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val prefs = remember { context.getSharedPreferences("translator", Context.MODE_PRIVATE) }
        val languages = remember { TranslateLanguage.getAllLanguages().sortedBy { languageName(it, context) } }
        var from by remember { mutableStateOf(prefs.getString("from", AUTO) ?: AUTO) }
        var to by remember { mutableStateOf(prefs.getString("to", if (Locale.getDefault().language == "pt") TranslateLanguage.ENGLISH else TranslateLanguage.PORTUGUESE) ?: TranslateLanguage.ENGLISH) }
        var input by remember { mutableStateOf(intent?.getStringExtra(Intent.EXTRA_PROCESS_TEXT) ?: intent?.getStringExtra(Intent.EXTRA_TEXT) ?: "") }
        var output by remember { mutableStateOf("") }
        var detected by remember { mutableStateOf<String?>(null) }
        var status by remember { mutableStateOf<String?>(null) }
        var picking by remember { mutableStateOf<Boolean?>(null) } // true = from, false = to
        var packs by remember { mutableStateOf(false) }
        val history = remember { mutableStateListOf<Entry>().apply { addAll(readHistory(prefs)) } }

        val tts = remember { arrayOfNulls<TextToSpeech>(1) }
        DisposableEffect(Unit) {
            tts[0] = TextToSpeech(context) {}
            onDispose { tts[0]?.shutdown() }
        }
        val speech = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
            r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { input = it }
        }
        fun recognize(img: InputImage) {
            status = context.getString(R.string.tr_reading)
            scope.launch {
                val text = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).process(img).await()?.text
                status = null
                if (!text.isNullOrBlank()) input = text
            }
        }
        val photo = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { b: Bitmap? -> b?.let { recognize(InputImage.fromBitmap(it, 0)) } }
        val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { u: Uri? ->
            u?.let { runCatching { recognize(InputImage.fromFilePath(context, it)) } }
        }

        // Translates a moment after typing stops.
        LaunchedEffect(input, from, to) {
            prefs.edit().putString("from", from).putString("to", to).apply()
            val text = input.trim()
            if (text.isEmpty()) { output = ""; detected = null; return@LaunchedEffect }
            delay(500)
            val src = if (from == AUTO) {
                LanguageIdentification.getClient().identifyLanguage(text).await()?.takeIf { it != "und" }?.let { TranslateLanguage.fromLanguageTag(it) }
                    .also { detected = it }
            } else from
            if (src == null) { status = context.getString(R.string.tr_unknown); return@LaunchedEffect }
            if (src == to) { output = text; status = null; return@LaunchedEffect }
            val translator = Translation.getClient(TranslatorOptions.Builder().setSourceLanguage(src).setTargetLanguage(to).build())
            try {
                status = context.getString(R.string.tr_preparing)
                if (translator.downloadModelIfNeeded(DownloadConditions.Builder().build()).await() == null &&
                    !modelsReady(src, to)
                ) {
                    status = context.getString(R.string.tr_download_failed)
                    return@LaunchedEffect
                }
                status = null
                output = translator.translate(text).await().orEmpty()
            } finally {
                translator.close()
            }
        }
        // Keep finished translations in the history (after a pause, not on every letter).
        LaunchedEffect(output) {
            if (output.isBlank() || input.isBlank()) return@LaunchedEffect
            delay(2500)
            val e = Entry(detected ?: from, to, input.trim(), output)
            history.removeAll { it.text == e.text && it.to == e.to }
            history.add(0, e)
            while (history.size > 40) history.removeAt(history.lastIndex)
            writeHistory(prefs, history)
        }

        Column(Modifier.fillMaxSize()) {
            WpPivot(
                title = stringResource(R.string.app_translator),
                headers = listOf(stringResource(R.string.tr_translate), stringResource(R.string.tr_history)),
                modifier = Modifier.weight(1f),
            ) { page ->
                if (page == 0) {
                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
                        // Languages: from ⇄ to.
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 10.dp)) {
                            LangButton(languageName(from, context) + (if (from == AUTO) detected?.let { " (" + languageName(it, context) + ")" } ?: "" else ""), Modifier.weight(1f)) { picking = true }
                            Box(
                                Modifier.size(44.dp).wpClickable {
                                    if (from != AUTO) { val f = from; from = to; to = f; input = output }
                                    else detected?.let { d -> from = to; to = d; input = output }
                                },
                                contentAlignment = Alignment.Center,
                            ) { WpIcon(WpIcons.Sync, size = 24.dp) }
                            LangButton(languageName(to, context), Modifier.weight(1f)) { picking = false }
                        }
                        WpTextBox(input, { input = it }, Modifier.fillMaxWidth().heightIn(min = 120.dp), placeholder = stringResource(R.string.tr_hint), singleLine = false, maxLines = 8)
                        status?.let { WpText(it, style = WpType.Small, color = Wp.accent, modifier = Modifier.padding(top = 6.dp)) }
                        if (output.isNotEmpty()) {
                            Box(Modifier.fillMaxWidth().padding(top = 14.dp).background(Wp.accent).padding(14.dp)) {
                                WpText(output, style = WpType.Large.copy(fontSize = 22.sp, lineHeight = 28.sp), color = Color.White)
                            }
                            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                                Action(stringResource(R.string.tr_listen)) {
                                    tts[0]?.setLanguage(Locale(to))
                                    tts[0]?.speak(output, TextToSpeech.QUEUE_FLUSH, null, "tr")
                                }
                                Action(stringResource(R.string.tr_copy)) {
                                    context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("translation", output))
                                }
                                Action(stringResource(R.string.share)) {
                                    runCatching { context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, output), null)) }
                                }
                            }
                        }
                        WpText(stringResource(R.string.tr_note), style = WpType.Small, color = Wp.palette.subtle, modifier = Modifier.padding(top = 20.dp, bottom = 16.dp))
                    }
                } else {
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, end = 16.dp, bottom = 16.dp)) {
                        if (history.isEmpty()) item { WpText(stringResource(R.string.tr_no_history), style = WpType.Large, color = Wp.palette.subtle) }
                        items(history) { e ->
                            Column(Modifier.fillMaxWidth().wpClickable { from = e.from; to = e.to; input = e.text }.padding(vertical = 8.dp)) {
                                WpText(e.text, style = WpType.Large, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                WpText(e.result, style = WpType.Normal, color = Wp.accent, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                WpText(languageName(e.from, context) + " → " + languageName(e.to, context), style = WpType.Small, color = Wp.palette.subtle)
                            }
                        }
                    }
                }
            }
            WpAppBar(
                buttons = listOf(
                    AppBarButton(WpIcons.Camera, stringResource(R.string.tr_photo)) { runCatching { photo.launch(null) } },
                    AppBarButton(WpIcons.Photos, stringResource(R.string.tr_picture)) { runCatching { gallery.launch("image/*") } },
                    AppBarButton(com.wpreborn.core.icons.Glyphs.Microphone, stringResource(R.string.tr_speak)) {
                        runCatching {
                            speech.launch(
                                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                                    .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                    .apply { if (from != AUTO) putExtra(RecognizerIntent.EXTRA_LANGUAGE, from) },
                            )
                        }
                    },
                    AppBarButton(WpIcons.Close, stringResource(R.string.tr_clear)) { input = ""; output = "" },
                ),
                menuItems = listOf(AppBarMenuItem(stringResource(R.string.tr_packs)) { packs = true }),
            )
        }

        picking?.let { isFrom ->
            LanguageList(if (isFrom) listOf(AUTO) + languages else languages, onDismiss = { picking = null }) { code ->
                if (isFrom) from = code else to = code
                picking = null
            }
        }
        if (packs) Packs(languages) { packs = false }
    }

    private suspend fun modelsReady(a: String, b: String): Boolean {
        val m = RemoteModelManager.getInstance()
        val have = m.getDownloadedModels(TranslateRemoteModel::class.java).await().orEmpty().map { it.language }.toSet()
        return (a == TranslateLanguage.ENGLISH || a in have) && (b == TranslateLanguage.ENGLISH || b in have)
    }
}

private fun readHistory(p: android.content.SharedPreferences): List<Entry> = runCatching {
    val a = JSONArray(p.getString("history", "[]"))
    List(a.length()) { i -> a.getJSONObject(i).let { Entry(it.getString("f"), it.getString("t"), it.getString("x"), it.getString("r")) } }
}.getOrDefault(emptyList())

private fun writeHistory(p: android.content.SharedPreferences, list: List<Entry>) {
    val a = JSONArray()
    list.forEach { a.put(JSONObject().put("f", it.from).put("t", it.to).put("x", it.text).put("r", it.result)) }
    p.edit().putString("history", a.toString()).apply()
}

@Composable
private fun LangButton(label: String, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.border(2.dp, Wp.palette.foreground).wpClickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 9.dp)) {
        WpText(label, style = WpType.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun Action(label: String, onClick: () -> Unit) {
    WpText(label, style = WpType.Normal, color = Wp.accent, modifier = Modifier.wpClickable(onClick = onClick).padding(vertical = 6.dp))
}

@Composable
private fun LanguageList(codes: List<String>, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val context = LocalContext.current
    androidx.activity.compose.BackHandler(onBack = onDismiss)
    Column(Modifier.fillMaxSize().background(Wp.palette.background).padding(start = 20.dp, top = 14.dp, end = 16.dp)) {
        WpText(stringResource(R.string.app_translator).uppercase(), style = WpType.AppTitle)
        WpText(stringResource(R.string.tr_languages), style = WpType.Header, maxLines = 1, softWrap = false)
        LazyColumn(Modifier.weight(1f)) {
            items(codes) { c ->
                WpText(languageName(c, context), style = WpType.ExtraLarge, maxLines = 1, modifier = Modifier.fillMaxWidth().wpClickable { onPick(c) }.padding(vertical = 4.dp))
            }
        }
    }
}

/** Downloaded languages (for offline use), each removable. */
@Composable
private fun Packs(languages: List<String>, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var have by remember { mutableStateOf<Set<String>>(emptySet()) }
    var refresh by remember { mutableStateOf(0) }
    LaunchedEffect(refresh) {
        have = RemoteModelManager.getInstance().getDownloadedModels(TranslateRemoteModel::class.java).await().orEmpty().map { it.language }.toSet()
    }
    androidx.activity.compose.BackHandler(onBack = onDismiss)
    Column(Modifier.fillMaxSize().background(Wp.palette.background).padding(start = 20.dp, top = 14.dp, end = 16.dp)) {
        WpText(stringResource(R.string.app_translator).uppercase(), style = WpType.AppTitle)
        WpText(stringResource(R.string.tr_packs), style = WpType.Header, maxLines = 1, softWrap = false)
        WpText(stringResource(R.string.tr_packs_hint), style = WpType.Small, color = Wp.palette.subtle, modifier = Modifier.padding(bottom = 8.dp))
        LazyColumn(Modifier.weight(1f)) {
            items(languages) { c ->
                val on = c in have
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    WpText(languageName(c, context), style = WpType.Large, modifier = Modifier.weight(1f))
                    WpText(
                        stringResource(if (on) R.string.delete else R.string.tr_download),
                        style = WpType.Normal,
                        color = Wp.accent,
                        modifier = Modifier.wpClickable {
                            scope.launch {
                                val model = TranslateRemoteModel.Builder(c).build()
                                val m = RemoteModelManager.getInstance()
                                if (on) m.deleteDownloadedModel(model).await() else m.download(model, DownloadConditions.Builder().build()).await()
                                refresh++
                            }
                        }.padding(8.dp),
                    )
                }
            }
        }
    }
}
