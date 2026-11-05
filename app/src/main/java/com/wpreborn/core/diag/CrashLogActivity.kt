package com.wpreborn.core.diag

import android.content.Intent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.wpreborn.R
import com.wpreborn.apps.common.WpActivity
import com.wpreborn.core.icons.WpIcons
import com.wpreborn.core.theme.Wp
import com.wpreborn.core.theme.WpType
import com.wpreborn.core.ui.AppBarButton
import com.wpreborn.core.ui.WpAppBar
import com.wpreborn.core.ui.WpText

/** The error report: what crashed or froze, to share with the developer. */
class CrashLogActivity : WpActivity() {
    @Composable
    override fun Content() {
        val context = LocalContext.current
        var text by remember { mutableStateOf(CrashLog.read(context)) }
        Column(Modifier.fillMaxSize()) {
            Column(Modifier.weight(1f).padding(start = 20.dp, end = 12.dp, top = 14.dp)) {
                WpText(stringResource(R.string.app_settings).uppercase(), style = WpType.AppTitle)
                WpText(stringResource(R.string.crash_title), style = WpType.Header)
                if (text.isBlank()) {
                    WpText(stringResource(R.string.crash_none), style = WpType.Normal, color = Wp.palette.subtle, modifier = Modifier.padding(top = 12.dp))
                } else {
                    Box(Modifier.fillMaxWidth().padding(top = 8.dp).verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState())) {
                        WpText(text, style = WpType.Small.copy(fontSize = 11.sp, lineHeight = 14.sp), softWrap = false)
                    }
                }
            }
            WpAppBar(
                buttons = listOf(
                    AppBarButton(WpIcons.Share, stringResource(R.string.share), enabled = text.isNotBlank()) {
                        runCatching {
                            context.startActivity(
                                Intent.createChooser(
                                    Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT, "Mozaik – relatório de erros").putExtra(Intent.EXTRA_TEXT, text.take(90_000)),
                                    null,
                                ),
                            )
                        }
                    },
                    AppBarButton(WpIcons.Delete, stringResource(R.string.delete), enabled = text.isNotBlank()) {
                        CrashLog.clear(context)
                        text = ""
                    },
                ),
            )
        }
    }
}
