package com.wpreborn.core.ui

import android.content.Context
import android.net.Uri
import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wpreborn.R
import com.wpreborn.core.icons.WpIcons
import com.wpreborn.core.theme.Wp
import java.util.Calendar
import java.util.Date

object WpFormat {
    /** Message list / call history stamp: time today, weekday this week, short date otherwise. */
    fun shortStamp(context: Context, time: Long): String {
        val now = Calendar.getInstance()
        val then = Calendar.getInstance().apply { timeInMillis = time }
        return when {
            DateUtils.isToday(time) -> DateFormat.getTimeFormat(context).format(Date(time))
            now.timeInMillis - time < 6L * 24 * 3600 * 1000 && then.before(now) -> DateFormat.format("EEE", then).toString().lowercase()
            else -> DateFormat.getDateFormat(context).format(Date(time))
        }
    }

    fun dayLabel(context: Context, time: Long): String {
        val today = Calendar.getInstance()
        val c = Calendar.getInstance().apply { timeInMillis = time }
        val sameYear = today.get(Calendar.YEAR) == c.get(Calendar.YEAR)
        val dayDiff = if (sameYear) c.get(Calendar.DAY_OF_YEAR) - today.get(Calendar.DAY_OF_YEAR) else Int.MAX_VALUE
        return when (dayDiff) {
            0 -> context.getString(R.string.today)
            1 -> context.getString(R.string.tomorrow)
            -1 -> context.getString(R.string.yesterday)
            else -> DateFormat.format(if (sameYear) "EEEE, d MMMM" else "EEEE, d MMMM yyyy", c).toString().lowercase()
        }
    }

    fun time(context: Context, time: Long): String = DateFormat.getTimeFormat(context).format(Date(time))

    fun duration(ms: Long): String {
        val s = ms / 1000
        return "%d:%02d".format(s / 60, s % 60)
    }
}

/** Square contact picture, or the accent square with a person glyph (WP placeholder). */
@Composable
fun ContactAvatar(photo: Uri?, modifier: Modifier = Modifier, size: Dp = 56.dp) {
    Thumbnail(
        photo,
        modifier.size(size),
        sizePx = 256,
    ) {
        Box(Modifier.size(size).background(Wp.accent), contentAlignment = Alignment.Center) {
            WpIcon(WpIcons.Person, tint = Color.White, size = size * 0.6f)
        }
    }
}
