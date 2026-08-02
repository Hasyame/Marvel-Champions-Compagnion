package com.hasyame.marvelchampions.data.bgg

import com.hasyame.marvelchampions.domain.model.BggPlay
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

/** What came back from BoardGameGeek, in terms the UI can show a player. */
sealed interface BggResult {
    data object Success : BggResult

    /** Username or password rejected. */
    data object BadCredentials : BggResult

    /** Reached BGG, but it refused or returned something unexpected. */
    data class Rejected(val detail: String) : BggResult

    /** Never reached BGG at all. */
    data class Offline(val detail: String) : BggResult
}

/**
 * Logs plays to BoardGameGeek.
 *
 * **BGG publishes no write API.** Its XML API is read-only, so logging a play
 * goes through `geekplay.php`, the same endpoint the website's own form posts
 * to. That has consequences worth stating plainly rather than burying:
 *
 *  - It needs the account **password**, not a token. There is no OAuth and no
 *    app-specific credential to issue, so there is no version of this feature
 *    that avoids holding one.
 *  - It is undocumented and carries no compatibility promise. It can change
 *    without notice, and when it does this stops working rather than degrading.
 *  - Every failure is therefore reported to the player rather than swallowed. A
 *    play silently not appearing is worse than one that says why.
 *
 * The session is not persisted: each report logs in, posts, and forgets the
 * cookies. It is a handful of requests a week at most, and it means no session
 * token sits on disk alongside the password.
 */
@Singleton
class BggClient @Inject constructor(
    private val ioDispatcher: CoroutineDispatcher,
) {

    /** Checks a username and password without recording anything. */
    suspend fun verify(username: String, password: String): BggResult =
        withContext(ioDispatcher) {
            val client = newClient()
            when (val login = logIn(client, username, password)) {
                is BggResult.Success -> BggResult.Success
                else -> login
            }
        }

    /** Logs one play of Marvel Champions. */
    suspend fun reportPlay(
        username: String,
        password: String,
        play: BggPlay,
    ): BggResult = withContext(ioDispatcher) {
        val client = newClient()

        when (val login = logIn(client, username, password)) {
            is BggResult.Success -> Unit
            else -> return@withContext login
        }

        val form = FormBody.Builder()
            .add("ajax", "1")
            .add("action", "save")
            .add("version", "2")
            .add("objecttype", "thing")
            .add("objectid", MARVEL_CHAMPIONS_ID)
            .add("playdate", play.playedOn)
            .add("length", play.lengthMinutes.toString())
            .add("quantity", "1")
            .add("players", play.players.toString())
            .add("incomplete", "0")
            .add("nowinstats", "0")
            .add("comments", play.comment)
            .build()

        val request = Request.Builder()
            .url("$BASE_URL/geekplay.php")
            .post(form)
            .header("User-Agent", USER_AGENT)
            .header("X-Requested-With", "XMLHttpRequest")
            .build()

        runCatching { client.newCall(request).execute() }.fold(
            onSuccess = { response ->
                response.use {
                    val body = it.body?.string().orEmpty()
                    when {
                        !it.isSuccessful -> BggResult.Rejected("HTTP ${it.code}")
                        // A rejected save comes back 200 with an error in the
                        // body, so the status code alone cannot be trusted.
                        body.contains("\"error\"", ignoreCase = true) ->
                            BggResult.Rejected(body.take(ERROR_DETAIL_LIMIT))

                        else -> BggResult.Success
                    }
                }
            },
            onFailure = { BggResult.Offline(it.message ?: "no connection") },
        )
    }

    private fun logIn(
        client: OkHttpClient,
        username: String,
        password: String,
    ): BggResult {
        // Sent as JSON because that is what BGG's own login form posts; a form
        // body is rejected.
        val payload = buildJsonLogin(username, password)
        val request = Request.Builder()
            .url("$BASE_URL/login/api/v1")
            .post(payload.toRequestBody("application/json".toMediaType()))
            .header("User-Agent", USER_AGENT)
            .build()

        return runCatching { client.newCall(request).execute() }.fold(
            onSuccess = { response ->
                response.use {
                    when {
                        it.isSuccessful -> BggResult.Success
                        it.code == HTTP_UNAUTHORIZED -> BggResult.BadCredentials
                        else -> BggResult.Rejected("HTTP ${it.code}")
                    }
                }
            },
            onFailure = { BggResult.Offline(it.message ?: "no connection") },
        )
    }

    /**
     * Built by hand rather than through a serializer, so the password is never
     * copied into an intermediate object that might end up in a log or a crash
     * report.
     */
    private fun buildJsonLogin(username: String, password: String): String =
        """{"credentials":{"username":"${username.escapeJson()}",""" +
            """"password":"${password.escapeJson()}"}}"""

    private fun String.escapeJson(): String =
        replace("\\", "\\\\").replace("\"", "\\\"")

    /**
     * A client per report, with cookies held only in memory.
     *
     * Deliberately not the app's shared client: this one carries a session for
     * somebody's BGG account, and it should not outlive the request that needs
     * it.
     */
    private fun newClient(): OkHttpClient = OkHttpClient.Builder()
        .cookieJar(InMemoryCookieJar())
        .build()

    private class InMemoryCookieJar : CookieJar {
        private val cookies = mutableListOf<Cookie>()

        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            this.cookies.removeAll { existing -> cookies.any { it.name == existing.name } }
            this.cookies += cookies
        }

        override fun loadForRequest(url: HttpUrl): List<Cookie> = cookies
    }

    private companion object {
        const val BASE_URL = "https://boardgamegeek.com"

        /** Marvel Champions: The Card Game. */
        const val MARVEL_CHAMPIONS_ID = "285774"

        const val USER_AGENT = "MarvelChampionsCompanion/1.0 (github.com/Hasyame)"
        const val HTTP_UNAUTHORIZED = 401
        const val ERROR_DETAIL_LIMIT = 200
    }
}
