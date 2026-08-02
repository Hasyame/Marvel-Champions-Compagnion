package com.hasyame.marvelchampions.ui.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri

/**
 * Hands a URL to whichever app owns it — Spotify for a Spotify link, the
 * browser otherwise.
 *
 * Returns false when nothing can open it, so the caller can say so rather than
 * appearing to do nothing.
 */
fun openExternalUrl(context: Context, url: String): Boolean {
    if (url.isBlank()) {
        return false
    }
    return try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, url.toUri())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}
