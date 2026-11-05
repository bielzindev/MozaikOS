package com.wpreborn.core.diag

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import com.wpreborn.BuildConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

/**
 * A small on-phone error report: every crash (with its stack trace) and every time the main
 * thread stops answering for a few seconds (with what it was doing). Settings > About shows it
 * and shares it, so problems can be found without a computer.
 */
object CrashLog {
    private const val FILE = "crash_log.txt"
    private const val MAX = 150_000
    @Volatile private var installed = false

    fun install(context: Context) {
        if (installed) return
        installed = true
        val app = context.applicationContext
        val file = File(app.filesDir, FILE)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching { append(file, "ERRO (o app fechou)", "thread: ${t.name}\n" + e.stackTraceToString()) }
            previous?.uncaughtException(t, e)
        }
        // Watchdog: a ping to the main thread every 2.5 s; no answer means it is stuck.
        val main = Handler(Looper.getMainLooper())
        Thread {
            var lastReport = 0L
            while (true) {
                val answered = AtomicBoolean(false)
                main.post { answered.set(true) }
                try {
                    Thread.sleep(2500)
                } catch (_: InterruptedException) {
                    return@Thread
                }
                if (!answered.get()) {
                    val now = System.currentTimeMillis()
                    if (now - lastReport > 30_000) {
                        lastReport = now
                        val stack = Looper.getMainLooper().thread.stackTrace.take(40).joinToString("\n") { "  at $it" }
                        runCatching { append(file, "TRAVAMENTO (a tela não respondeu por mais de 2,5 s)", stack) }
                    }
                }
            }
        }.apply {
            isDaemon = true
            name = "mozaik-watchdog"
        }.start()
    }

    @Synchronized
    private fun append(file: File, kind: String, body: String) {
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        val entry = "=== $kind · $time · Mozaik ${BuildConfig.VERSION_NAME} · ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE}\n$body\n\n"
        val old = if (file.exists()) file.readText() else ""
        // Newest first, the file kept small.
        file.writeText((entry + old).take(MAX))
    }

    fun read(context: Context): String = runCatching { File(context.filesDir, FILE).readText() }.getOrDefault("")

    fun clear(context: Context) {
        runCatching { File(context.filesDir, FILE).delete() }
    }
}
