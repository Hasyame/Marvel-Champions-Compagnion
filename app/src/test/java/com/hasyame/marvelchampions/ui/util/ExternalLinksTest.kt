package com.hasyame.marvelchampions.ui.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * The playlist URL is typed in Settings and stored, so whatever it holds
 * eventually reaches `startActivity`. Only web links may.
 */
@RunWith(RobolectricTestRunner::class)
class ExternalLinksTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `schemes other than http and https are refused`() {
        // The intent: scheme is the one that matters — it names a component to
        // launch rather than a page to open.
        assertFalse(
            openExternalUrl(
                context,
                "intent://evil#Intent;scheme=http;package=com.example;end",
            ),
        )
        assertFalse(openExternalUrl(context, "file:///data/data/com.hasyame.marvelchampions"))
        assertFalse(openExternalUrl(context, "content://settings/secure"))
        assertFalse(openExternalUrl(context, "javascript:alert(1)"))
    }

    @Test
    fun `blank and scheme-less values are refused`() {
        assertFalse(openExternalUrl(context, ""))
        assertFalse(openExternalUrl(context, "   "))
        assertFalse(openExternalUrl(context, "melodice.org/playlist"))
    }
}
