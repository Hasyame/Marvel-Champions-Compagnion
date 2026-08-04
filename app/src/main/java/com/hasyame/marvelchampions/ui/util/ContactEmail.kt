package com.hasyame.marvelchampions.ui.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.net.toUri
import java.text.DateFormat
import java.util.Date

/**
 * Opens a bug report addressed to the project, prefilled with the details that
 * would otherwise have to be asked for.
 *
 * `ACTION_SENDTO` with a `mailto:` URI so only mail apps offer to handle it.
 * Returns false when none is installed, so the caller can say so instead of
 * appearing to do nothing.
 */
fun sendContactEmail(context: Context, lastSyncEpochMillis: Long?): Boolean {
    val versionName = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull() ?: "unknown"

    val lastSync = lastSyncEpochMillis
        ?.let { DateFormat.getDateTimeInstance().format(Date(it)) }
        ?: "never"

    val body = buildString {
        appendLine()
        appendLine()
        appendLine("---")
        appendLine("App version: $versionName")
        appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("Last card sync: $lastSync")
    }

    val intent = Intent(Intent.ACTION_SENDTO).apply {
        data = "mailto:$CONTACT_ADDRESS".toUri()
        putExtra(Intent.EXTRA_SUBJECT, "Marvel Champions Companion")
        putExtra(Intent.EXTRA_TEXT, body)
    }

    return try {
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}

/**
 * Opens a mail draft holding the last crash.
 *
 * The trace goes in the body rather than an attachment: the person sending it
 * can read exactly what they are sending, which is the whole point of keeping
 * it on the device until they choose to.
 */
fun sendCrashEmail(context: Context, trace: String): Boolean {
    val versionName = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull() ?: "unknown"

    val intent = Intent(Intent.ACTION_SENDTO).apply {
        data = "mailto:$CONTACT_ADDRESS".toUri()
        putExtra(Intent.EXTRA_SUBJECT, "Marvel Champions Companion crash ($versionName)")
        putExtra(Intent.EXTRA_TEXT, trace)
    }

    return try {
        context.startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}

const val CONTACT_ADDRESS: String = "marvelchampcompanion@proton.me"
