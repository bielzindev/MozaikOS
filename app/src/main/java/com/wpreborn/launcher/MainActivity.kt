package com.wpreborn.launcher

import android.app.ActivityOptions
import android.app.role.RoleManager
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.AndroidUiDispatcher
import androidx.lifecycle.lifecycleScope
import com.wpreborn.R
import com.wpreborn.apps.common.ChromeState
import com.wpreborn.apps.common.WpActivity
import com.wpreborn.apps.common.WpChrome
import com.wpreborn.core.motion.TurnstileMode
import com.wpreborn.core.motion.TurnstileState
import com.wpreborn.core.motion.turnstile
import com.wpreborn.core.theme.Wp
import com.wpreborn.core.theme.WpTheme
import com.wpreborn.data.apps.AppEntry
import com.wpreborn.data.media.ExternalMediaWatcher
import com.wpreborn.data.notifications.NotificationCenter
import com.wpreborn.data.prefs.SettingsRepository
import com.wpreborn.data.tiles.TileSpec
import com.wpreborn.launcher.allapps.AllAppsScreen
import com.wpreborn.launcher.start.StartScreen
import com.wpreborn.launcher.start.StartScreenState
import com.wpreborn.launcher.start.TileInputs
import com.wpreborn.services.music.MusicController
import kotlinx.coroutines.launch

/**
 * The Home activity: Start screen and app list side by side, swiped horizontally like on
 * Windows Phone 8.1, with the status bar, Action Center and volume flyout on top.
 */
class MainActivity : ComponentActivity() {

    private val vm: LauncherViewModel by viewModels()
    private val chrome = ChromeState()
    private val start = StartScreenState()
    private val appsTurnstile = TurnstileState()
    private var pager: PagerState? = null
    private var returning = false
    private var stopped = false
    private var personalizing by mutableStateOf(false)
    private val personalizeTurnstile = TurnstileState()
    private var homePresses by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WpActivity.setupWindow(this)
        setContent {
            WpTheme {
                WpChrome(chrome) {
                    LauncherContent()
                    if (personalizing) PersonalizeScreen(personalizeTurnstile, onClose = ::closePersonalize)
                }
                if (!Wp.settings.firstRunDone) {
                    WelcomeScreen(
                        onMakeDefault = { requestHomeRole() },
                        onDone = { SettingsRepository.get(this).setFirstRunDone() },
                    )
                }
            }
        }
    }

    @Composable
    private fun LauncherContent() {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val tiles by vm.tiles.collectAsState()
        val apps by vm.apps.collectAsState()
        val live by vm.live.collectAsState()
        val notifications by NotificationCenter.items.collectAsState()
        val player by MusicController.get(context).ui.collectAsState()
        val external by ExternalMediaWatcher.state.collectAsState()
        val settings = Wp.settings
        val pagerState = rememberPagerState { 2 }
        pager = pagerState

        BackHandler(enabled = pagerState.currentPage == 1) { scope.launch { pagerState.animateScrollToPage(0) } }

        LaunchedEffect(homePresses) {
            if (homePresses == 0) return@LaunchedEffect
            when {
                personalizing -> closePersonalize()
                chrome.actionCenter.visible -> chrome.actionCenter.close()
                start.editId != null -> start.editId = null
                pagerState.currentPage != 0 -> pagerState.animateScrollToPage(0)
                else -> start.scroll.animateScrollTo(0)
            }
        }

        // System apps that duplicate ZUNEX apps are hidden from the list unless shown in settings.
        val duplicates = remember(apps) { vm.appRepo.duplicatePackages() }
        // Apps the user hid are left out of the list and of Start (Settings shows and restores them).
        val hidden by remember { com.wpreborn.data.apps.HiddenApps.get(context) }.keys.collectAsState()
        val listedApps = remember(apps, duplicates, settings.showDuplicates, hidden) {
            (if (settings.showDuplicates) apps else apps.filter { it.builtIn != null || it.packageName !in duplicates }).filter { it.key !in hidden }
        }
        val shownTiles = remember(tiles, hidden) { tiles.filter { it.appKey !in hidden } }
        // Collected state (not vm.apps.value) so tiles recompose once the app list has loaded.
        val appsByKey = remember(apps) { apps.associateBy { it.key } }
        val counts = remember(notifications) { notifications.groupBy { it.packageName }.mapValues { (_, l) -> l.sumOf { it.messageCount } } }
        val latest = remember(notifications) { notifications.groupBy { it.packageName }.mapValues { it.value.take(3) } }
        val nowPlaying = when {
            player.hasMedia -> com.wpreborn.data.media.NowPlaying(player.title, player.artist, player.album, null, player.playing, packageName)
            else -> null // the Music tile only follows ZUNEX Music
        }

        // Lite edition: Phone/Messaging counters come from the system apps' notifications.
        val liveData = if (com.wpreborn.BuildConfig.LITE) {
            val sms = android.provider.Telephony.Sms.getDefaultSmsPackage(context)
            val dialer = context.getSystemService(android.telecom.TelecomManager::class.java)?.defaultDialerPackage
            live.copy(unreadSms = counts[sms] ?: 0, missedCalls = counts[dialer] ?: 0)
        } else {
            live
        }

        fun inputsFor(tile: TileSpec, app: AppEntry?) = TileInputs(
            live = liveData,
            notificationCount = if (app?.builtIn == null) counts[app?.packageName] ?: 0 else 0,
            recent = if (app?.builtIn == null) latest[app?.packageName].orEmpty() else emptyList(),
            nowPlaying = nowPlaying,
            nowPlayingArt = if (player.hasMedia) player.artUri else null,
            liveEnabled = settings.liveTiles,
        )

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1,
            userScrollEnabled = start.editId == null,
        ) { page ->
            Box(Modifier.fillMaxSize()) {
                if (page == 0) {
                    StartScreen(
                        state = start,
                        tiles = shownTiles,
                        appFor = { appsByKey[it.appKey] },
                        inputsFor = ::inputsFor,
                        onLaunch = { tile, app -> launchFromTile(tile, app) },
                        onUnpin = { vm.unpin(it.id) },
                        onResize = { vm.resize(it.id, allowLarge = !settings.moreTiles) },
                        onColor = { t, c -> vm.setColor(t.id, c) },
                        onCustomColor = { t, c -> vm.setCustomColor(t.id, c) },
                        onMove = { id, index, persist -> vm.move(id, index, persist) },
                        onPersonalize = ::openPersonalize,
                    )
                } else {
                    Box(Modifier.fillMaxSize().turnstile(appsTurnstile)) {
                        AllAppsScreen(
                            apps = listedApps,
                            isPinned = vm::isPinned,
                            onLaunch = { launchFromList(it) },
                            onPin = { app ->
                                vm.pin(app)
                                scope.launch {
                                    pagerState.animateScrollToPage(0)
                                    start.scroll.animateScrollTo(start.scroll.maxValue)
                                }
                            },
                            visible = pagerState.currentPage == 1,
                        )
                    }
                }
            }
        }
    }

    private fun launchFromTile(tile: TileSpec, app: AppEntry) {
        lifecycleScope.launch(AndroidUiDispatcher.Main) {
            start.launchingId = tile.id
            start.turnstile.run(TurnstileMode.ForwardOut)
            if (!launch(app)) {
                start.turnstile.run(TurnstileMode.BackwardIn)
                start.launchingId = null
            }
        }
    }

    private fun launchFromList(app: AppEntry) {
        lifecycleScope.launch(AndroidUiDispatcher.Main) {
            appsTurnstile.run(TurnstileMode.ForwardOut)
            if (!launch(app)) appsTurnstile.run(TurnstileMode.BackwardIn)
        }
    }

    private fun launch(app: AppEntry): Boolean = try {
        val options = if (app.builtIn != null) {
            ActivityOptions.makeCustomAnimation(this, 0, 0)
        } else {
            ActivityOptions.makeCustomAnimation(this, R.anim.wp_app_open, R.anim.wp_stay)
        }
        vm.appRepo.launch(app, null, options.toBundle())
        returning = true
        true
    } catch (_: Exception) {
        false
    }

    /** The tiles fly away, then the personalisation page swings in. */
    private fun openPersonalize() {
        if (personalizing) return
        lifecycleScope.launch(AndroidUiDispatcher.Main) {
            start.turnstile.run(TurnstileMode.ForwardOut)
            personalizing = true
            personalizeTurnstile.run(TurnstileMode.ForwardIn)
        }
    }

    private fun closePersonalize() {
        lifecycleScope.launch(AndroidUiDispatcher.Main) {
            personalizeTurnstile.run(TurnstileMode.BackwardOut)
            personalizing = false
            start.turnstile.run(TurnstileMode.BackwardIn)
        }
    }

    override fun onStop() {
        super.onStop()
        stopped = true
    }

    override fun onStart() {
        super.onStart()
        // Coming back to Start (unlock, home from another app): tiles fly in like on 8.1.
        if (stopped && !returning && !personalizing) {
            lifecycleScope.launch(AndroidUiDispatcher.Main) { start.turnstile.run(TurnstileMode.ForwardIn) }
        }
        stopped = false
    }

    override fun onResume() {
        super.onResume()
        WpActivity.applySystemBars(this)
        vm.refreshLive()
        com.wpreborn.data.weather.WeatherRepository.get(this).refreshIfStale()
        if (returning) {
            returning = false
            lifecycleScope.launch(AndroidUiDispatcher.Main) {
                if (start.turnstile.mode == TurnstileMode.ForwardOut) {
                    start.turnstile.run(TurnstileMode.BackwardIn)
                    start.launchingId = null
                }
            }
            lifecycleScope.launch(AndroidUiDispatcher.Main) {
                if (appsTurnstile.mode == TurnstileMode.ForwardOut) appsTurnstile.run(TurnstileMode.BackwardIn)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN && hasWindowFocus()) homePresses++
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP || event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (event.action == KeyEvent.ACTION_DOWN) chrome.volume.onKey(this, event.keyCode == KeyEvent.KEYCODE_VOLUME_UP)
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun requestHomeRole() {
        val rm = getSystemService(RoleManager::class.java)
        if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME) && !rm.isRoleHeld(RoleManager.ROLE_HOME)) {
            @Suppress("DEPRECATION")
            startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_HOME), 1)
        } else {
            startActivity(Intent(android.provider.Settings.ACTION_HOME_SETTINGS))
        }
    }
}
