package com.wpreborn.launcher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.wpreborn.core.theme.WpTheme

/** Settings > about > "see the welcome screen": the first-start pages, to look at only. */
class WelcomePreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            WpTheme {
                WelcomeScreen(onMakeDefault = {}, onDone = { finish() }, preview = true)
            }
        }
    }
}
