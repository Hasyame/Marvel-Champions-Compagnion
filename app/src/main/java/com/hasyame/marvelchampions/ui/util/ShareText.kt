package com.hasyame.marvelchampions.ui.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent

/**
 * Hands text to the system share sheet.
 *
 * The share sheet rather than the clipboard: the player usually knows where the
 * decklist is going — a message, a forum, a note — and copying makes them find
 * that app themselves.
 */
fun shareText(context: Context, subject: String, text: String): Boolean {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, text)
    }

    return try {
        context.startActivity(
            Intent.createChooser(intent, subject).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}
