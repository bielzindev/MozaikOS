package com.wpreborn.keyboard

import android.inputmethodservice.InputMethodService
import android.media.AudioManager
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import com.wpreborn.R
import com.wpreborn.data.prefs.SettingsRepository

/**
 * The ZUNEX keyboard (Word Flow): an Android input method that can be made the default
 * keyboard. Letters, numbers and symbols, emoji, long-press accents, dozens of languages and
 * scripts (with Hangul and kana composition), suggestions, autocorrect, next-word prediction,
 * shape writing, cursor control on the space bar and word delete on the backspace key.
 */
class ZunexKeyboardService : InputMethodService(), KeyboardListener {

    private lateinit var prefs: KeyboardPrefs
    private lateinit var predictor: WordPredictor
    private var view: KeyboardView? = null

    private val word = StringBuilder()
    private var hangul = HangulComposer()
    private var kana = KanaComposer()
    private var language = KeyboardLayouts.languages.first()
    private var shift = 0
    private var lastShiftTap = 0L
    private var page = Page.Letters
    private var symbolsPage = 0
    private var info: EditorInfo? = null
    private var noSuggestions = false
    private var email = false
    private var lastCommitted: String? = null
    /** Word inserted by the last swipe (and whether it is followed by our space), for replacement. */
    private var swipedWord: String? = null
    private var swipeAlternatives: List<String> = emptyList()
    private var correctionShown: String? = null
    /** Word replaced by autocorrect, so backspace can undo it. */
    private var undoCorrection: Pair<String, String>? = null

    private enum class Page { Letters, Symbols, Emoji, Number, Phone, Clipboard, Edit }

    // Clipboard history (this keyboard is the default input method, so it may read the clipboard).
    private val clipListener = android.content.ClipboardManager.OnPrimaryClipChangedListener { rememberClip() }
    private var selecting = false
    private var recognizer: android.speech.SpeechRecognizer? = null

    override fun onCreate() {
        super.onCreate()
        prefs = KeyboardPrefs(this)
        predictor = WordPredictor(this)
        language = KeyboardLayouts.byCode(prefs.current)
        predictor.preload(language)
        runCatching { getSystemService(android.content.ClipboardManager::class.java)?.addPrimaryClipChangedListener(clipListener) }
    }

    override fun onDestroy() {
        runCatching { getSystemService(android.content.ClipboardManager::class.java)?.removePrimaryClipChangedListener(clipListener) }
        recognizer?.destroy()
        super.onDestroy()
    }

    private fun clips(): List<String> = (getSharedPreferences("zunex_keyboard", MODE_PRIVATE).getString("clips", "") ?: "")
        .split("\u0001").filter { it.isNotEmpty() }

    private fun rememberClip() {
        val text = runCatching {
            getSystemService(android.content.ClipboardManager::class.java)?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString()
        }.getOrNull()?.trim()
        if (text.isNullOrEmpty() || text.length > 2000) return
        val next = (listOf(text) + clips().filterNot { it == text }).take(12)
        getSharedPreferences("zunex_keyboard", MODE_PRIVATE).edit().putString("clips", next.joinToString("\u0001")).apply()
        if (page == Page.Clipboard) render()
    }

    private fun clipboardRows(): List<KeyRow> {
        val list = clips().take(4)
        val rows = ArrayList<KeyRow>()
        if (list.isEmpty()) rows += listOf(Key(getString(R.string.kb_clip_empty), KeyType.Action, 10f, text = "none"))
        list.forEachIndexed { i, c ->
            val one = c.replace('\n', ' ')
            rows += listOf(Key(if (one.length > 34) one.take(33) + "…" else one, KeyType.Action, 10f, text = "paste:$i"))
        }
        rows += listOf(
            Key("abc", KeyType.Letters, 2f),
            Key(getString(R.string.kb_clip_clear), KeyType.Action, 3f, text = "clipclear"),
            Key("space", KeyType.Space, 3f, text = " "),
            Key("del", KeyType.Backspace, 2f),
        )
        return rows
    }

    private fun editRows(): List<KeyRow> = listOf(
        listOf(Key("⇤", KeyType.Action, text = "home"), Key("▲", KeyType.Action, text = "up"), Key("⇥", KeyType.Action, text = "end")),
        listOf(Key("◀", KeyType.Action, text = "left"), Key(getString(if (selecting) R.string.kb_edit_selecting else R.string.kb_edit_select), KeyType.Action, text = "select"), Key("▶", KeyType.Action, text = "right")),
        listOf(
            Key(getString(R.string.kb_edit_all), KeyType.Action, text = "selall"),
            Key(getString(R.string.kb_edit_copy), KeyType.Action, text = "copy"),
            Key(getString(R.string.kb_edit_cut), KeyType.Action, text = "cut"),
            Key(getString(R.string.kb_edit_paste), KeyType.Action, text = "paste"),
        ),
        listOf(Key("abc", KeyType.Letters, 1f), Key("▼", KeyType.Action, text = "down"), Key("del", KeyType.Backspace, 1f)),
    )

    private fun arrow(code: Int) {
        val ic = ic() ?: return
        val meta = if (selecting) android.view.KeyEvent.META_SHIFT_ON or android.view.KeyEvent.META_SHIFT_LEFT_ON else 0
        val t = android.os.SystemClock.uptimeMillis()
        ic.sendKeyEvent(android.view.KeyEvent(t, t, android.view.KeyEvent.ACTION_DOWN, code, 0, meta))
        ic.sendKeyEvent(android.view.KeyEvent(t, t, android.view.KeyEvent.ACTION_UP, code, 0, meta))
    }

    private fun action(id: String) {
        val ic = ic()
        when {
            id.startsWith("paste:") -> {
                clips().getOrNull(id.removePrefix("paste:").toIntOrNull() ?: -1)?.let { commitComposing(); ic?.commitText(it, 1) }
                page = Page.Letters
                render()
            }
            id == "clipclear" -> { getSharedPreferences("zunex_keyboard", MODE_PRIVATE).edit().remove("clips").apply(); render() }
            id == "home" -> arrow(android.view.KeyEvent.KEYCODE_MOVE_HOME)
            id == "end" -> arrow(android.view.KeyEvent.KEYCODE_MOVE_END)
            id == "up" -> arrow(android.view.KeyEvent.KEYCODE_DPAD_UP)
            id == "down" -> arrow(android.view.KeyEvent.KEYCODE_DPAD_DOWN)
            id == "left" -> arrow(android.view.KeyEvent.KEYCODE_DPAD_LEFT)
            id == "right" -> arrow(android.view.KeyEvent.KEYCODE_DPAD_RIGHT)
            id == "select" -> { selecting = !selecting; render() }
            id == "selall" -> ic?.performContextMenuAction(android.R.id.selectAll)
            id == "copy" -> ic?.performContextMenuAction(android.R.id.copy)
            id == "cut" -> ic?.performContextMenuAction(android.R.id.cut)
            id == "paste" -> ic?.performContextMenuAction(android.R.id.paste)
        }
    }

    override fun onToolbar(id: String) {
        when (id) {
            "clip" -> { commitComposing(); rememberClip(); page = Page.Clipboard; render() }
            "edit" -> { commitComposing(); selecting = false; page = Page.Edit; render() }
            "voice" -> startVoice()
            "settings" -> runCatching {
                startActivity(android.content.Intent(this, KeyboardSettingsActivity::class.java).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }

    /** Voice typing with Android's speech recognition; partial words show in the strip. */
    private fun startVoice() {
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED ||
            !android.speech.SpeechRecognizer.isRecognitionAvailable(this)
        ) {
            // The keyboard can't ask for permissions itself: its settings screen does.
            runCatching {
                startActivity(
                    android.content.Intent(this, KeyboardSettingsActivity::class.java)
                        .putExtra(KeyboardSettingsActivity.EXTRA_ASK_MIC, true)
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
            return
        }
        commitComposing()
        recognizer?.destroy()
        val r = android.speech.SpeechRecognizer.createSpeechRecognizer(this)
        recognizer = r
        val v = view
        v?.suggestions = listOf(getString(R.string.kb_listening))
        r.setRecognitionListener(object : android.speech.RecognitionListener {
            override fun onReadyForSpeech(params: android.os.Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onEvent(eventType: Int, params: android.os.Bundle?) = Unit
            override fun onError(error: Int) { updateSuggestions() }
            override fun onPartialResults(partial: android.os.Bundle?) {
                partial?.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { view?.suggestions = listOf("$it…") }
            }
            override fun onResults(results: android.os.Bundle?) {
                val text = results?.getStringArrayList(android.speech.SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!text.isNullOrBlank()) ic()?.commitText(text + " ", 1)
                updateSuggestions()
            }
        })
        r.startListening(
            android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, java.util.Locale(language.code).toLanguageTag())
                .putExtra(android.speech.RecognizerIntent.EXTRA_PARTIAL_RESULTS, true),
        )
    }

    override fun onCreateInputView(): View {
        val v = KeyboardView(this, this)
        v.feedback = { feedback(it) }
        // Keep the bottom row above the navigation bar / gesture area.
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(v) { view, insets ->
            val nav = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.navigationBars()).bottom
            if (view.paddingBottom != nav) {
                view.setPadding(0, 0, 0, nav)
                view.requestLayout()
            }
            insets
        }
        view = v
        return v
    }

    override fun onStartInputView(editorInfo: EditorInfo, restarting: Boolean) {
        super.onStartInputView(editorInfo, restarting)
        info = editorInfo
        language = KeyboardLayouts.byCode(prefs.current)
        predictor.preload(language)
        resetComposing()
        val cls = editorInfo.inputType and InputType.TYPE_MASK_CLASS
        val variation = editorInfo.inputType and InputType.TYPE_MASK_VARIATION
        page = when (cls) {
            InputType.TYPE_CLASS_NUMBER, InputType.TYPE_CLASS_DATETIME -> Page.Number
            InputType.TYPE_CLASS_PHONE -> Page.Phone
            else -> Page.Letters
        }
        val password = variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
            variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
        email = variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS || variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS
        val uri = variation == InputType.TYPE_TEXT_VARIATION_URI
        noSuggestions = password || email || uri || (editorInfo.inputType and InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS != 0) || !prefs.suggestions
        shift = 0
        symbolsPage = 0
        updateAutoCaps()
        render()
    }

    /**
     * The user moved the cursor (tapped elsewhere in the text, or the app moved it): the word
     * being typed is left as it is and typing continues where the cursor now is, instead of
     * going on with the old word (which made the cursor jump back to it).
     */
    override fun onUpdateSelection(oldSelStart: Int, oldSelEnd: Int, newSelStart: Int, newSelEnd: Int, candidatesStart: Int, candidatesEnd: Int) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        val typing = composingText().isNotEmpty()
        // Our own updates always leave the cursor at the end of the word's composing region;
        // anything else is the user (late updates from our own commits have no region and are
        // ignored, so fast typing isn't cut).
        val awayFromWord = candidatesStart >= 0 && (newSelStart != newSelEnd || newSelEnd != candidatesEnd)
        if (typing && awayFromWord) {
            ic()?.finishComposingText()
            resetComposing()
            correctionShown = null
            updateSuggestions()
        } else if (!typing && newSelStart != oldSelStart && candidatesStart < 0) {
            // Cursor moved with nothing being typed: fresh suggestions for the new place, once
            // it settles (deleting moves it many times a second).
            undoCorrection = null
            mainHandler.removeCallbacks(afterDelete)
            mainHandler.postDelayed(afterDelete, 140)
        }
    }

    override fun onFinishInput() {
        super.onFinishInput()
        mainHandler.removeCallbacks(afterDelete)
        commitComposing()
        predictor.save(language.code)
    }

    // --- Rendering --------------------------------------------------------------------------
    private fun render() {
        view?.keyHeightDp = when (prefs.size) { 0 -> 46f; 2 -> 62f; else -> 54f }
        val v = view ?: return
        val settings = SettingsRepository.get(this).cached
        v.dark = settings.darkTheme
        v.accent = android.graphics.Color.argb(
            255,
            (settings.accent.color.red * 255).toInt(),
            (settings.accent.color.green * 255).toInt(),
            (settings.accent.color.blue * 255).toInt(),
        )
        v.language = language
        v.swipeEnabled = prefs.swipe && language.composer == Composer.None
        v.lettersPage = page == Page.Letters
        v.emojiMode = page == Page.Emoji
        v.shiftState = shift
        v.spaceLabel = if (prefs.languages.size > 1) language.name.lowercase() else ""
        v.enterLabel = enterLabel()
        val multi = prefs.languages.size > 1
        v.rows = when (page) {
            Page.Letters -> KeyboardLayouts.letters(language, shift > 0, prefs.numberRow, multi, email)
            Page.Symbols -> KeyboardLayouts.symbols(symbolsPage, multi)
            Page.Number -> KeyboardLayouts.numberPad(phone = false)
            Page.Phone -> KeyboardLayouts.numberPad(phone = true)
            Page.Emoji -> emptyList()
            Page.Clipboard -> clipboardRows()
            Page.Edit -> editRows()
        }
        v.toolbar = if (page == Page.Letters) listOf("clip" to "clip", "voice" to "voice", "edit" to "edit", "settings" to "settings") else emptyList()
        updateSuggestions()
    }

    private fun enterLabel(): String? {
        val i = info ?: return null
        if (i.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0) return null
        return when (i.imeOptions and EditorInfo.IME_MASK_ACTION) {
            EditorInfo.IME_ACTION_GO -> getString(R.string.kb_go)
            EditorInfo.IME_ACTION_SEARCH -> getString(R.string.search)
            EditorInfo.IME_ACTION_SEND -> getString(R.string.messages_send)
            EditorInfo.IME_ACTION_NEXT -> getString(R.string.kb_next)
            EditorInfo.IME_ACTION_DONE -> getString(R.string.kb_done)
            else -> null
        }
    }

    private fun updateSuggestions() {
        val v = view ?: return
        if (noSuggestions || page != Page.Letters) {
            v.suggestions = emptyList(); v.highlighted = -1
            return
        }
        if (swipeAlternatives.isNotEmpty()) {
            v.suggestions = swipeAlternatives; v.highlighted = -1
            return
        }
        if (word.isEmpty()) {
            correctionShown = null
            val before = ic()?.getTextBeforeCursor(1, 0)
            v.suggestions = if (before.isNullOrEmpty()) emptyList() else lastCommitted?.let { predictor.predictNext(language, it) }.orEmpty()
            v.highlighted = -1
            return
        }
        val s = predictor.suggest(language, word.toString())
        val typed = word.toString()
        correctionShown = if (prefs.autoCorrect) s.correction else null
        // WP showed what you typed plus the best guesses; the autocorrect word is highlighted.
        val list = (listOf(typed) + s.words).distinct().take(3)
        v.suggestions = list
        v.highlighted = correctionShown?.let { list.indexOf(it) } ?: -1
    }

    // --- Text helpers -----------------------------------------------------------------------
    private fun ic() = currentInputConnection

    private fun composingText(): String = when (language.composer) {
        Composer.Hangul -> hangul.composing
        Composer.Kana -> kana.composing
        Composer.None -> word.toString()
    }

    private fun showComposing() {
        val t = composingText()
        ic()?.setComposingText(t, 1)
    }

    private fun resetComposing() {
        word.setLength(0)
        hangul = HangulComposer()
        kana = KanaComposer()
        swipedWord = null
        swipeAlternatives = emptyList()
        undoCorrection = null
    }

    /** Commits the word being typed as is. */
    private fun commitComposing() {
        val text = when (language.composer) {
            Composer.Hangul -> hangul.reset()
            Composer.Kana -> kana.reset()
            Composer.None -> word.toString()
        }
        if (text.isNotEmpty()) {
            ic()?.commitText(text, 1)
            if (language.composer == Composer.None) predictor.learn(language, text, lastCommitted)
            lastCommitted = text
        } else {
            ic()?.finishComposingText()
        }
        word.setLength(0)
    }

    /** Commits the word, replacing it with the autocorrection when there is one. */
    private fun commitWithCorrection() {
        val typed = word.toString()
        val corr = correctionShown
        if (!noSuggestions && corr != null && corr != typed && typed.isNotEmpty() && language.composer == Composer.None) {
            ic()?.commitText(corr, 1)
            undoCorrection = typed to corr
            lastCommitted = corr
            word.setLength(0)
            correctionShown = null
        } else {
            undoCorrection = null
            commitComposing()
        }
    }

    private fun updateAutoCaps() {
        if (!prefs.autoCaps || !language.cased || page != Page.Letters || shift == 2) return
        val i = info ?: return
        val caps = ic()?.getCursorCapsMode(i.inputType) ?: 0
        val newShift = if (caps != 0 && (i.inputType and InputType.TYPE_TEXT_FLAG_CAP_SENTENCES != 0 || i.inputType and InputType.TYPE_TEXT_FLAG_CAP_WORDS != 0)) 1 else 0
        if (newShift != shift) {
            shift = newShift
            if (view?.rows?.isNotEmpty() == true) render()
        }
    }

    private fun feedback(v: View) {
        if (prefs.vibrate) view?.haptic()
        if (prefs.sound) (getSystemService(AUDIO_SERVICE) as AudioManager).playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD, -1f)
    }

    // --- KeyboardListener -------------------------------------------------------------------
    override fun onKey(key: Key) {
        when (key.type) {
            KeyType.Char -> typeChar(key.text)
            KeyType.Space -> space()
            KeyType.Backspace -> backspace()
            KeyType.Enter -> enter()
            KeyType.Shift -> {
                val now = System.currentTimeMillis()
                shift = when {
                    shift == 0 && now - lastShiftTap < 350 -> 2
                    shift == 0 -> 1
                    shift == 1 && now - lastShiftTap < 350 -> 2
                    else -> 0
                }
                lastShiftTap = now
                render()
            }
            KeyType.Symbols -> { commitComposing(); page = Page.Symbols; symbolsPage = 0; render() }
            KeyType.Letters -> { page = Page.Letters; render() }
            KeyType.SymbolsPage -> { symbolsPage = 1 - symbolsPage; render() }
            KeyType.Emoji -> { commitComposing(); page = Page.Emoji; render() }
            KeyType.Language -> switchLanguage()
            KeyType.Comma -> punctuation(",")
            KeyType.Period -> punctuation(".")
            KeyType.Action -> action(key.text)
        }
    }

    override fun onText(text: String) {
        if (page == Page.Emoji) {
            commitComposing()
            ic()?.commitText(text, 1)
            return
        }
        if (text.length == 1 && (text[0].isLetter() || text[0] == '\'')) typeChar(text) else punctuation(text)
    }

    private fun typeChar(t: String) {
        swipeAlternatives = emptyList()
        if (swipedWord != null) {
            // Typing right after a swiped word: separate it with a space first.
            swipedWord = null
        }
        undoCorrection = null
        when (language.composer) {
            Composer.Hangul -> {
                t.forEach { c ->
                    val done = hangul.input(c)
                    if (done.isNotEmpty()) ic()?.commitText(done, 1)
                }
                showComposing()
            }
            Composer.Kana -> {
                t.forEach { kana.input(it) }
                showComposing()
            }
            Composer.None -> {
                if (t.length == 1 && (t[0].isLetterOrDigit() || t[0] == '\'' || Character.getType(t[0]) == Character.NON_SPACING_MARK.toInt() || Character.getType(t[0]) == Character.COMBINING_SPACING_MARK.toInt())) {
                    word.append(t)
                    showComposing()
                } else {
                    commitComposing()
                    ic()?.commitText(t, 1)
                }
            }
        }
        if (shift == 1) {
            shift = 0
            render()
        } else {
            updateSuggestions()
        }
    }

    private fun space() {
        if (language.composer != Composer.None && composingText().isNotEmpty()) {
            commitComposing()
            ic()?.commitText(" ", 1)
            updateAutoCaps(); updateSuggestions()
            return
        }
        swipeAlternatives = emptyList()
        if (swipedWord != null) {
            // Word Flow already added the space.
            swipedWord = null
            updateSuggestions()
            return
        }
        // Double space -> ". "
        if (word.isEmpty() && prefs.doubleSpacePeriod) {
            val before = ic()?.getTextBeforeCursor(2, 0)?.toString().orEmpty()
            if (before.length == 2 && before[1] == ' ' && before[0].isLetterOrDigit()) {
                ic()?.deleteSurroundingText(1, 0)
                ic()?.commitText(". ", 1)
                updateAutoCaps(); updateSuggestions()
                return
            }
        }
        commitWithCorrection()
        ic()?.commitText(" ", 1)
        updateAutoCaps()
        updateSuggestions()
    }

    private fun punctuation(p: String) {
        if (swipedWord != null && p in listOf(".", ",", "?", "!", ";", ":")) {
            // Attach punctuation to the swiped word: drop the automatic space.
            val before = ic()?.getTextBeforeCursor(1, 0)
            if (before == " ") ic()?.deleteSurroundingText(1, 0)
        }
        swipedWord = null
        swipeAlternatives = emptyList()
        if (language.composer != Composer.None) commitComposing() else commitWithCorrection()
        ic()?.commitText(p, 1)
        if (p in listOf(".", "?", "!") && page == Page.Letters) {
            ic()?.commitText(" ", 1)
        }
        if (page == Page.Symbols && p != ",") { page = Page.Letters; render() }
        updateAutoCaps()
        updateSuggestions()
    }

    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    /**
     * Suggestions and auto-capitals after deleting, run once the deleting pauses: finding a
     * correction compares the word with thousands of dictionary words, and doing it for every
     * repeated backspace (about 18 a second) froze the keyboard and the app behind it.
     */
    private val afterDelete = Runnable {
        updateAutoCaps()
        updateSuggestions()
    }

    private fun backspace() {
        swipeAlternatives = emptyList()
        // The correction found for the longer word no longer applies.
        correctionShown = null
        // Undo the last autocorrection, like WP did when you tapped backspace right after.
        undoCorrection?.let { (typed, corr) ->
            val before = ic()?.getTextBeforeCursor(corr.length + 1, 0)?.toString()
            if (before == "$corr " || before?.endsWith(corr) == true) {
                ic()?.deleteSurroundingText(if (before.endsWith(" ")) corr.length + 1 else corr.length, 0)
                word.setLength(0); word.append(typed)
                showComposing()
                undoCorrection = null
                predictor.learn(language, typed, null)
                mainHandler.removeCallbacks(afterDelete)
                updateSuggestions()
                return
            }
        }
        undoCorrection = null
        val handled = when (language.composer) {
            Composer.Hangul -> hangul.backspace().also { if (it) showComposing() }
            Composer.Kana -> kana.backspace().also { if (it) showComposing() }
            Composer.None -> if (word.isNotEmpty()) {
                word.deleteCharAt(word.length - 1)
                showComposing()
                true
            } else {
                false
            }
        }
        if (!handled) {
            swipedWord = null
            val selected = ic()?.getSelectedText(0)
            if (!selected.isNullOrEmpty()) {
                ic()?.commitText("", 1)
            } else {
                sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
            }
        }
        mainHandler.removeCallbacks(afterDelete)
        mainHandler.postDelayed(afterDelete, 140)
    }

    override fun onDeleteWord() {
        commitComposing()
        val before = ic()?.getTextBeforeCursor(64, 0)?.toString().orEmpty()
        if (before.isEmpty()) return
        val trimmed = before.trimEnd()
        val start = trimmed.indexOfLast { it.isWhitespace() } + 1
        ic()?.deleteSurroundingText(before.length - start, 0)
        updateAutoCaps(); updateSuggestions()
    }

    private fun enter() {
        commitWithCorrection()
        swipedWord = null
        val i = info
        val action = (i?.imeOptions ?: 0) and EditorInfo.IME_MASK_ACTION
        val multiLine = (i?.inputType ?: 0) and InputType.TYPE_TEXT_FLAG_MULTI_LINE != 0
        val noAction = (i?.imeOptions ?: 0) and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0
        if (!multiLine && !noAction && action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
            ic()?.performEditorAction(action)
        } else {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
        }
        updateAutoCaps(); updateSuggestions()
    }

    override fun onSuggestion(index: Int, word: String) {
        val swiped = swipedWord
        if (swiped != null && swipeAlternatives.isNotEmpty()) {
            // Replace the swiped word with the chosen alternative.
            ic()?.deleteSurroundingText(swiped.length + 1, 0)
            ic()?.commitText("$word ", 1)
            swipedWord = word
            lastCommitted = word
            predictor.learn(language, word, null)
            swipeAlternatives = emptyList()
            updateSuggestions()
            return
        }
        if (this.word.isEmpty()) {
            // Next-word prediction.
            ic()?.commitText("$word ", 1)
        } else {
            this.word.setLength(0)
            ic()?.commitText("$word ", 1)
        }
        predictor.learn(language, word, lastCommitted)
        lastCommitted = word
        undoCorrection = null
        if (shift == 1) { shift = 0; render() }
        updateAutoCaps()
        updateSuggestions()
    }

    override fun onSwipe(keys: String) {
        commitComposing()
        val matches = predictor.matchSwipe(language, keys)
        if (matches.isEmpty()) {
            // Nothing recognised: type the keys that were passed, like a fast tap sequence.
            keys.firstOrNull()?.let { typeChar(it.toString()) }
            return
        }
        val before = ic()?.getTextBeforeCursor(1, 0)?.toString().orEmpty()
        val needsSpace = before.isNotEmpty() && !before[0].isWhitespace() && before[0] !in "([{\"'¿¡"
        var best = matches.first()
        if (shift > 0) best = if (shift == 2) best.uppercase() else best.replaceFirstChar { it.uppercase() }
        ic()?.commitText((if (needsSpace) " " else "") + best + " ", 1)
        predictor.learn(language, best, lastCommitted)
        lastCommitted = best
        swipedWord = best
        swipeAlternatives = matches.drop(1).take(3).map { if (shift > 0) it.replaceFirstChar { c -> c.uppercase() } else it }
        if (shift == 1) shift = 0
        render()
    }

    override fun onCursor(steps: Int) {
        commitComposing()
        val code = if (steps > 0) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT
        repeat(kotlin.math.abs(steps)) { sendDownUpKeyEvents(code) }
    }

    override fun onLongPressSpace() {
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
    }

    private fun switchLanguage() {
        commitComposing()
        val list = prefs.languages
        val next = list[(list.indexOf(language.code) + 1).mod(list.size)]
        prefs.current = next
        language = KeyboardLayouts.byCode(next)
        predictor.preload(language)
        resetComposing()
        render()
    }
}
