package com.wpreborn.launcher

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.wpreborn.R
import com.wpreborn.core.motion.slideInStaggered
import com.wpreborn.core.theme.Wp
import com.wpreborn.core.theme.WpType
import com.wpreborn.core.ui.WpButton
import com.wpreborn.core.ui.WpText
import com.wpreborn.data.notifications.NotificationCenter

/** First-run page in the style of the Windows Phone OOBE ("let's set up a few things"). */
@Composable
fun WelcomeScreen(onMakeDefault: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    Column(
        Modifier
            .fillMaxSize()
            .background(Wp.accent)
            .clickable(remember { MutableInteractionSource() }, null) {}
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 24.dp),
    ) {
        WpText(stringResource(R.string.welcome_title), style = WpType.Header, color = androidx.compose.ui.graphics.Color.White, modifier = Modifier.slideInStaggered(0))
        Spacer(Modifier.height(16.dp))
        WpText(stringResource(R.string.welcome_body), style = WpType.Normal, color = androidx.compose.ui.graphics.Color.White, modifier = Modifier.slideInStaggered(1))
        Spacer(Modifier.height(28.dp))
        WelcomeStep(2, stringResource(R.string.welcome_default_title), stringResource(R.string.welcome_default_body), stringResource(R.string.welcome_default_button), onMakeDefault)
        WelcomeStep(3, stringResource(R.string.welcome_notifications_title), stringResource(R.string.welcome_notifications_body), stringResource(R.string.allow)) {
            context.startActivity(NotificationCenter.accessSettingsIntent(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        Spacer(Modifier.height(24.dp))
        WpButton(stringResource(R.string.welcome_done), Modifier.fillMaxWidth().slideInStaggered(4), onClick = onDone)
    }
}

@Composable
private fun WelcomeStep(index: Int, title: String, body: String, button: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = 24.dp).slideInStaggered(index)) {
        WpText(title, style = WpType.Large, color = androidx.compose.ui.graphics.Color.White)
        Spacer(Modifier.height(6.dp))
        WpText(body, style = WpType.Subtle, color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.85f))
        Spacer(Modifier.height(10.dp))
        WpButton(button, onClick = onClick)
    }
}
