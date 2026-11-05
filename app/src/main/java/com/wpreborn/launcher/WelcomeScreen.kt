package com.wpreborn.launcher

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wpreborn.R
import com.wpreborn.core.icons.WpIcons
import com.wpreborn.core.motion.slideInStaggered
import com.wpreborn.core.theme.AccentColor
import com.wpreborn.core.theme.Wp
import com.wpreborn.core.theme.WpType
import com.wpreborn.core.ui.WpButton
import com.wpreborn.core.ui.WpIcon
import com.wpreborn.core.ui.WpText
import com.wpreborn.core.ui.wpClickable
import com.wpreborn.data.notifications.NotificationCenter
import com.wpreborn.data.prefs.SettingsRepository
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin

private val Greetings = listOf(
    "hello", "olá", "hola", "bonjour", "hallo", "ciao", "привет", "merhaba", "こんにちは", "안녕하세요", "你好", "مرحبا", "namaste",
)

private enum class Step { Hello, Look, Home, Tiles, Finishing }

/**
 * First start, after the Windows Phone 8.1 out-of-box experience: "hello" in many languages
 * on black, then a few pages (look, Start screen, live tiles), each with "next" at the
 * bottom right, and finally "hi there / we're setting things up for you" with the flowing
 * dots before Start appears. Shown only once, on the first install. With [preview] (from
 * Settings) it only shows the pages: choices and buttons change nothing on the phone.
 */
@Composable
fun WelcomeScreen(onMakeDefault: () -> Unit, onDone: () -> Unit, preview: Boolean = false) {
    var index by remember { mutableIntStateOf(0) }
    val step = Step.entries[index]
    BackHandler(enabled = index in 1 until Step.Finishing.ordinal) { index-- }
    Box(
        Modifier
            .fillMaxSize()
            .background(if (step == Step.Hello) Color.Black else if (step == Step.Finishing) Wp.accent else Wp.palette.background)
            .clickable(remember { MutableInteractionSource() }, null) {},
    ) {
        AnimatedContent(
            targetState = step,
            transitionSpec = {
                val forward = targetState.ordinal > initialState.ordinal
                (slideInHorizontally(tween(320)) { if (forward) it / 3 else -it / 3 } + fadeIn(tween(320))) togetherWith
                    (slideOutHorizontally(tween(220)) { if (forward) -it / 4 else it / 4 } + fadeOut(tween(200)))
            },
            label = "oobe",
        ) { s ->
            when (s) {
                Step.Hello -> HelloPage { index = Step.Look.ordinal }
                Step.Look -> Page(R.string.welcome_look_title, onNext = { index++ }) { LookChooser(preview) }
                Step.Home -> Page(R.string.welcome_default_title, onNext = { index++ }) {
                    WpText(stringResource(R.string.welcome_default_body), style = WpType.Normal, color = Wp.palette.foreground)
                    Spacer(Modifier.height(20.dp))
                    WpButton(stringResource(R.string.welcome_default_button), onClick = { if (!preview) onMakeDefault() })
                }
                Step.Tiles -> Page(R.string.welcome_notifications_title, onNext = { index++ }) {
                    val context = LocalContext.current
                    WpText(stringResource(R.string.welcome_notifications_body), style = WpType.Normal, color = Wp.palette.foreground)
                    Spacer(Modifier.height(20.dp))
                    WpButton(stringResource(R.string.allow)) {
                        if (!preview) runCatching { context.startActivity(NotificationCenter.accessSettingsIntent(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    }
                }
                Step.Finishing -> FinishingPage(onDone)
            }
        }
    }
}

/** Black screen, the name, and "hello" changing language, like the phone's first boot. */
@Composable
private fun HelloPage(onStart: () -> Unit) {
    var i by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1600)
            i = (i + 1) % Greetings.size
        }
    }
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 28.dp),
    ) {
        WpText("Mozaik", style = WpType.AppTitle, color = Color.White, modifier = Modifier.slideInStaggered(0))
        Spacer(Modifier.weight(1f))
        AnimatedContent(
            targetState = Greetings[i],
            transitionSpec = { fadeIn(tween(600)) togetherWith fadeOut(tween(500)) },
            label = "hello",
        ) { word ->
            WpText(word, style = WpType.Header.copy(fontSize = 64.sp, lineHeight = 72.sp, fontWeight = FontWeight.Light), color = Color.White, maxLines = 1)
        }
        Spacer(Modifier.height(12.dp))
        WpText(stringResource(R.string.welcome_body), style = WpType.Normal, color = Color.White.copy(alpha = 0.75f), modifier = Modifier.slideInStaggered(1))
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            NextButton(stringResource(R.string.welcome_get_started), Color.White, onStart)
        }
    }
}

/** One setup page: lowercase title in the big header style, content, and "next". */
@Composable
private fun Page(title: Int, onNext: () -> Unit, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 24.dp),
        ) {
            WpText("MOZAIK", style = WpType.AppTitle, color = Wp.palette.foreground, modifier = Modifier.slideInStaggered(0))
            Spacer(Modifier.height(6.dp))
            WpText(stringResource(title).lowercase(), style = WpType.Header, color = Wp.palette.foreground, modifier = Modifier.slideInStaggered(1))
            Spacer(Modifier.height(18.dp))
            Column(Modifier.slideInStaggered(2)) { content() }
        }
        Row(
            Modifier.fillMaxWidth().background(Wp.palette.chrome).padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            NextButton(stringResource(R.string.welcome_next), Wp.palette.foreground, onNext)
        }
    }
}

@Composable
private fun NextButton(label: String, color: Color, onClick: () -> Unit) {
    Row(Modifier.wpClickable(onClick = onClick).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        WpText(label, style = WpType.Large, color = color)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.size(40.dp).border(2.dp, color, CircleShape), contentAlignment = Alignment.Center) {
            WpIcon(WpIcons.Forward, tint = color, size = 20.dp)
        }
    }
}

/** Background (dark or light) and accent colour, applied at once. */
@Composable
private fun LookChooser(preview: Boolean) {
    val context = LocalContext.current
    val repo = remember { SettingsRepository.get(context) }
    val real = Wp.settings
    // In the preview, the choices are only shown, not applied.
    var local by remember { androidx.compose.runtime.mutableStateOf(real.darkTheme to real.accent) }
    val settings = if (preview) real.copy(darkTheme = local.first, accent = local.second) else real
    WpText(stringResource(R.string.welcome_look_body), style = WpType.Normal, color = Wp.palette.foreground)
    Spacer(Modifier.height(16.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf(true to R.string.welcome_dark, false to R.string.welcome_light).forEach { (dark, label) ->
            val on = settings.darkTheme == dark
            Box(
                Modifier
                    .width(120.dp).height(48.dp)
                    .background(if (dark) Color.Black else Color.White)
                    .border(3.dp, if (on) Wp.accent else Wp.palette.subtle)
                    .wpClickable { if (preview) local = dark to local.second else repo.setDarkTheme(dark) },
                contentAlignment = Alignment.Center,
            ) { WpText(stringResource(label), style = WpType.Normal, color = if (dark) Color.White else Color.Black) }
        }
    }
    Spacer(Modifier.height(20.dp))
    AccentColor.entries.chunked(5).forEach { row ->
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { a ->
                Box(
                    Modifier
                        .weight(1f)
                        .aspectRatio(1f)
                        .background(a.color)
                        .then(if (settings.accent == a) Modifier.border(3.dp, Wp.palette.foreground) else Modifier)
                        .wpClickable { if (preview) local = local.first to a else repo.setAccent(a) },
                )
            }
            repeat(5 - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

/** "hi there", "we're setting things up for you" and the flowing dots, then Start. */
@Composable
private fun FinishingPage(onDone: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(3200)
        onDone()
    }
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 28.dp)) {
        Spacer(Modifier.weight(1f))
        WpText(stringResource(R.string.welcome_title), style = WpType.Header.copy(fontSize = 56.sp, lineHeight = 62.sp, fontWeight = FontWeight.Light), color = Color.White, modifier = Modifier.slideInStaggered(0))
        Spacer(Modifier.height(10.dp))
        WpText(stringResource(R.string.welcome_setting_up), style = WpType.Large, color = Color.White, modifier = Modifier.slideInStaggered(1))
        WpText(stringResource(R.string.welcome_wont_take_long), style = WpType.Normal, color = Color.White.copy(alpha = 0.8f), modifier = Modifier.slideInStaggered(2))
        Spacer(Modifier.weight(1f))
        FlowingDots(Color.White)
        Spacer(Modifier.height(24.dp))
    }
}

/** Windows Phone's indeterminate progress: five dots racing in, slowing, racing out. */
@Composable
private fun FlowingDots(color: Color) {
    val t by rememberInfiniteTransition(label = "dots").animateFloat(
        0f, 1f, infiniteRepeatable(tween(2600, easing = LinearEasing), RepeatMode.Restart), label = "t",
    )
    Canvas(Modifier.fillMaxWidth().height(8.dp)) {
        val r = 2.2.dp.toPx()
        for (i in 0 until 5) {
            val p = t * 1.3f - i * 0.07f
            if (p < 0f || p > 1f) continue
            // Fast at the edges, slow in the middle.
            val x = (p + 0.15f * sin(2f * PI.toFloat() * p)) * size.width
            drawCircle(color, r, Offset(x, size.height / 2))
        }
    }
}
