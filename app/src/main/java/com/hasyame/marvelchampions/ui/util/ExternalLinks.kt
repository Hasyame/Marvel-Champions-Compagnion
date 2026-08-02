package com.hasyame.marvelchampions.ui.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri

/**
 * Hands a web link to whichever app owns it — Spotify for a Spotify link, the
 * browser otherwise.
 *
 * **Only `http` and `https`.** The playlist URL is typed into Settings and
 * stored, so whatever it holds eventually reaches `startActivity`. Without a
 * scheme check, a value like `intent://…#Intent;…;end` would launch an
 * arbitrary component instead of opening a page — a small hole, but the kind
 * worth closing before the source is public.
 *
 * Returns false when the scheme is not allowed or nothing can open the link, so
 * the caller can say so rather than appearing to do nothing.
 */
fun openExternalUrl(context: Context, url: String): Boolean {
    if (url.isBlank()) {
        return false
    }

    val uri = runCatching { url.toUri() }.getOrNull() ?: return false
    if (uri.scheme?.lowercase() !in ALLOWED_SCHEMES) {
        return false
    }

    return try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}

private val ALLOWED_SCHEMES = setOf("http", "https")
