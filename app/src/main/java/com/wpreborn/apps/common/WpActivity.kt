package com.wpreborn.apps.common

import android.app.Activity
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.ui.platform.AndroidUiDispatcher
import androidx.lifecycle.lifecycleScope
import com.wpreborn.core.motion.LocalTurnstile
import com.wpreborn.core.motion.TurnstileMode
import com.wpreborn.core.motion.TurnstileState
import com.wpreborn.core.motion.turnstile
import com.wpreborn.core.theme.WpTheme
import com.wpreborn.data.prefs.SettingsRepository
import kotlinx.coroutines.launch

/**
 * Base class of every built-in Windows Phone app. Provides the edge-to-edge black/white
 * window, the WP status bar, Action Center, volume flyout and the turnstile animation that
 * plays when the app opens, closes, or is returned to.
 */
abstract class WpActivity : ComponentActivity() {

    protected val chrome = ChromeState()
    private val turnstile = TurnstileState(TurnstileMode.ForwardIn)
    private var pendingMode by mutableStateOf(TurnstileMode.ForwardIn)
    private var trigger by mutableIntStateOf(0)
    private var stopped = false
    private var closing = false

    @Composable
    protected abstract fun Content()

    /** Some apps (the photo viewer) want the plain page without the status bar. */
    protected open val showStatusBar: Boolean get() = true

    /** Return true to hand the request over to another app and finish immediately. */
    protected open fun redirect(): Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (redirect()) {
            finish()
            return
        }
        setupWindow(this)
        disableSystemTransition(opening = true)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = closeWithTurnstile()
        })
        setContent {
            WpTheme {
                LaunchedEffect(trigger) {
                    turnstile.run(pendingMode)
                }
                WpChrome(chrome, showStatusBar = showStatusBar) {
                    CompositionLocalProvider(LocalTurnstile provides turnstile) {
                        Box(Modifier.fillMaxSize().turnstile(turnstile)) { Content() }
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (stopped) {
            stopped = false
            closing = false
            pendingMode = TurnstileMode.BackwardIn
            trigger++
        }
    }

    override fun onStop() {
        super.onStop()
        stopped = true
    }

    override fun onResume() {
        super.onResume()
        applySystemBars(this)
    }

    /** Plays the backward-out turnstile, then finishes. */
    fun closeWithTurnstile() {
        if (closing) return
        closing = true
        lifecycleScope.launch(AndroidUiDispatcher.Main) {
            turnstile.run(TurnstileMode.BackwardOut)
            finish()
        }
    }

    override fun finish() {
        super.finish()
        disableSystemTransition(opening = false)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP || event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (event.action == KeyEvent.ACTION_DOWN) chrome.volume.onKey(this, event.keyCode == KeyEvent.KEYCODE_VOLUME_UP)
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    @Suppress("DEPRECATION")
    private fun disableSystemTransition(opening: Boolean) {
        if (Build.VERSION.SDK_INT >= 34) {
            overrideActivityTransition(if (opening) OVERRIDE_TRANSITION_OPEN else OVERRIDE_TRANSITION_CLOSE, 0, 0)
        } else {
            overridePendingTransition(0, 0)
        }
    }

    companion object {
        /** Edge-to-edge window with transparent bars; the WP status bar replaces Android's. */
        fun setupWindow(activity: Activity) {
            val window = activity.window
            WindowCompat.setDecorFitsSystemWindows(window, false)
            @Suppress("DEPRECATION")
            window.statusBarColor = Color.TRANSPARENT
            @Suppress("DEPRECATION")
            window.navigationBarColor = Color.TRANSPARENT
            if (Build.VERSION.SDK_INT >= 29) window.isNavigationBarContrastEnforced = false
            window.attributes = window.attributes.apply {
                if (Build.VERSION.SDK_INT >= 30) {
                    layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                } else {
                    layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                }
            }
            applySystemBars(activity)
        }

        fun applySystemBars(activity: Activity) {
            val settings = SettingsRepository.get(activity).cached
            val controller = WindowInsetsControllerCompat(activity.window, activity.window.decorView)
            controller.isAppearanceLightStatusBars = !settings.darkTheme
            controller.isAppearanceLightNavigationBars = !settings.darkTheme
            if (settings.wpStatusBar) {
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsetsCompat.Type.statusBars())
            } else {
                controller.show(WindowInsetsCompat.Type.statusBars())
            }
        }
    }
}
