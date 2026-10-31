package com.wpreborn.keyboard

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.wpreborn.R
import com.wpreborn.apps.common.WpActivity
import com.wpreborn.core.theme.Wp
import com.wpreborn.core.theme.WpType
import com.wpreborn.core.ui.WpButton
import com.wpreborn.core.ui.WpCheckBox
import com.wpreborn.core.ui.WpPivot
import com.wpreborn.core.ui.WpText
import com.wpreborn.core.ui.WpTextBox
import com.wpreborn.core.ui.WpToggle

/** Keyboard app: turn the ZUNEX keyboard on, pick languages and typing options, try it out. */
class KeyboardSettingsActivity : WpActivity() {

    @Composable
    override fun Content() {
        val context = LocalContext.current
        val prefs = remember { KeyboardPrefs(context) }
        var enabled by remember { mutableStateOf(isEnabled(context)) }
        var selected by remember { mutableStateOf(isSelected(context)) }
        val owner = LocalLifecycleOwner.current
        LaunchedEffect(owner) {
            owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                enabled = isEnabled(context)
                selected = isSelected(context)
            }
        }
        // Re-check after the IME picker closes (the activity does not pause for it).
        LaunchedEffect(Unit) {
            while (true) {
                kotlinx.coroutines.delay(1500)
                selected = isSelected(context)
            }
        }
        WpPivot(
            title = stringResource(R.string.app_keyboard),
            headers = listOf(stringResource(R.string.kb_setup), stringResource(R.string.kb_languages), stringResource(R.string.kb_typing)),
        ) { index ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 32.dp)) {
                when (index) {
                    0 -> Setup(enabled, selected)
                    1 -> Languages(prefs)
                    else -> Typing(prefs)
                }
            }
        }
    }

    @Composable
    private fun Setup(enabled: Boolean, selected: Boolean) {
        val context = LocalContext.current
        WpText(stringResource(R.string.kb_intro), style = WpType.Normal, color = Wp.palette.subtle)
        Spacer(Modifier.height(16.dp))
        WpText(stringResource(R.string.kb_step1), style = WpType.Large)
        WpText(
            stringResource(if (enabled) R.string.kb_done_state else R.string.kb_todo_state),
            style = WpType.Subtle,
            color = if (enabled) Wp.accent else Wp.palette.subtle,
        )
        WpButton(stringResource(R.string.kb_enable), Modifier.padding(vertical = 8.dp)) {
            context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        Spacer(Modifier.height(12.dp))
        WpText(stringResource(R.string.kb_step2), style = WpType.Large)
        WpText(
            stringResource(if (selected) R.string.kb_done_state else R.string.kb_todo_state),
            style = WpType.Subtle,
            color = if (selected) Wp.accent else Wp.palette.subtle,
        )
        WpButton(stringResource(R.string.kb_choose), Modifier.padding(vertical = 8.dp), enabled = enabled) {
            (context.getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        }
        Spacer(Modifier.height(20.dp))
        WpText(stringResource(R.string.kb_try), style = WpType.Subtle, color = Wp.palette.subtle)
        var text by remember { mutableStateOf("") }
        WpTextBox(text, { text = it }, Modifier.fillMaxWidth(), singleLine = false)
        Spacer(Modifier.height(12.dp))
        WpText(stringResource(R.string.kb_tips), style = WpType.Small, color = Wp.palette.subtle)
    }

    @Composable
    private fun Languages(prefs: KeyboardPrefs) {
        var chosen by remember { mutableStateOf(prefs.languages) }
        WpText(stringResource(R.string.kb_languages_hint), style = WpType.Small, color = Wp.palette.subtle, modifier = Modifier.padding(bottom = 8.dp))
        KeyboardLayouts.languages.forEach { lang ->
            WpCheckBox(
                lang.name + if (lang.dictionary != null) "  ·  " + stringResource(R.string.kb_with_dictionary) else "",
                checked = lang.code in chosen,
            ) { on ->
                val next = if (on) chosen + lang.code else chosen - lang.code
                if (next.isNotEmpty()) {
                    chosen = next
                    prefs.languages = next
                }
            }
        }
    }

    @Composable
    private fun Typing(prefs: KeyboardPrefs) {
        val on = stringResource(R.string.on)
        val off = stringResource(R.string.off)
        var s by remember { mutableStateOf(prefs.suggestions) }
        var a by remember { mutableStateOf(prefs.autoCorrect) }
        var c by remember { mutableStateOf(prefs.autoCaps) }
        var d by remember { mutableStateOf(prefs.doubleSpacePeriod) }
        var w by remember { mutableStateOf(prefs.swipe) }
        var n by remember { mutableStateOf(prefs.numberRow) }
        var snd by remember { mutableStateOf(prefs.sound) }
        var v by remember { mutableStateOf(prefs.vibrate) }
        WpToggle(stringResource(R.string.kb_wordflow), w, onText = on, offText = off) { w = it; prefs.swipe = it }
        WpToggle(stringResource(R.string.kb_suggestions), s, onText = on, offText = off) { s = it; prefs.suggestions = it }
        WpToggle(stringResource(R.string.kb_autocorrect), a, onText = on, offText = off) { a = it; prefs.autoCorrect = it }
        WpToggle(stringResource(R.string.kb_autocaps), c, onText = on, offText = off) { c = it; prefs.autoCaps = it }
        WpToggle(stringResource(R.string.kb_double_space), d, onText = on, offText = off) { d = it; prefs.doubleSpacePeriod = it }
        WpToggle(stringResource(R.string.kb_number_row), n, onText = on, offText = off) { n = it; prefs.numberRow = it }
        WpToggle(stringResource(R.string.kb_sound), snd, onText = on, offText = off) { snd = it; prefs.sound = it }
        WpToggle(stringResource(R.string.kb_vibrate), v, onText = on, offText = off) { v = it; prefs.vibrate = it }
        // Key size: three choices in the 8.1 button style.
        var size by remember { mutableStateOf(prefs.size) }
        com.wpreborn.core.ui.WpText(stringResource(R.string.kb_size), style = com.wpreborn.core.theme.WpType.Subtle, color = com.wpreborn.core.theme.Wp.palette.subtle, modifier = androidx.compose.ui.Modifier.padding(top = 12.dp, bottom = 6.dp))
        androidx.compose.foundation.layout.Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
            listOf(R.string.kb_size_small, R.string.kb_size_medium, R.string.kb_size_large).forEachIndexed { i, label ->
                com.wpreborn.core.ui.WpButton(stringResource(label), filled = size == i) { size = i; prefs.size = i }
            }
        }
    }

    companion object {
        private fun imm(context: Context) = context.getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager

        fun isEnabled(context: Context): Boolean =
            imm(context).enabledInputMethodList.any { it.packageName == context.packageName }

        fun isSelected(context: Context): Boolean =
            Settings.Secure.getString(context.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)?.startsWith(context.packageName + "/") == true
    }
}
