package com.hasyame.marvelchampions.data.diagnostics

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The last crash, written to a file on this device and nowhere else.
 *
 * This app has no crash reporting and is not getting any: there is no backend,
 * nothing is collected, and that is a promise worth keeping. But a crash the
 * author cannot see is a crash that cannot be fixed, and asking somebody to
 * reproduce it under `adb logcat` is asking a lot.
 *
 * So the stack trace is kept here, in the app's private storage, and shown in
 * Settings with a button to send it. Nothing leaves the device unless the
 * person holding it decides to send it.
 */
object CrashLog {

    private const val FILE_NAME = "last-crash.txt"

    /** Long enough for any stack trace, short enough never to be a problem. */
    private const val MAX_BYTES = 128 * 1024

    fun install(context: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            // Writing must never itself throw, or the app dies without the
            // system handler ever running and the process hangs instead.
            runCatching { write(context, thread, error) }
            previous?.uncaughtException(thread, error)
        }
    }

    private fun write(context: Context, thread: Thread, error: Throwable) {
        val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
        val text = buildString {
            appendLine(TIMESTAMP.format(Date()))
            appendLine("thread: ${thread.name}")
            appendLine("android: ${android.os.Build.VERSION.SDK_INT} on ${android.os.Build.MODEL}")
            appendLine()
            append(trace)
        }
        file(context).writeText(text.take(MAX_BYTES))
    }

    fun read(context: Context): String? =
        file(context).takeIf { it.exists() }?.readText()?.takeIf { it.isNotBlank() }

    fun clear(context: Context) {
        file(context).delete()
    }

    private fun file(context: Context) = File(context.filesDir, FILE_NAME)

    private val TIMESTAMP = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
}
