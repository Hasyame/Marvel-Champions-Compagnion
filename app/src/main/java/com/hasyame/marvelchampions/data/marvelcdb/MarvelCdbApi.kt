package com.hasyame.marvelchampions.data.marvelcdb

import com.hasyame.marvelchampions.data.marvelcdb.dto.CardDto
import com.hasyame.marvelchampions.data.marvelcdb.dto.PackDto
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Url

interface MarvelCdbApi {

    /**
     * Every card.
     *
     * [encounter] is **not optional in practice**: without `encounter=1` the
     * endpoint silently returns only the 2086 player cards and omits every
     * villain, scheme, minion, treachery and modular set card. With it, all
     * 4375 come back.
     */
    @GET("api/public/cards/")
    suspend fun getAllCards(
        @Query("encounter") encounter: Int = 1,
    ): List<CardDto>

    @GET("api/public/cards/{packCode}")
    suspend fun getPackCards(
        @Path("packCode") packCode: String,
        @Query("encounter") encounter: Int = 1,
    ): List<CardDto>

    @GET("api/public/card/{code}")
    suspend fun getCard(
        @Path("code") code: String,
    ): CardDto

    @GET("api/public/packs/")
    suspend fun getPacks(): List<PackDto>

    /**
     * Absolute-URL variants. Translations live on a locale subdomain, so the
     * host has to vary per request rather than being fixed at client
     * construction.
     */
    @GET
    suspend fun getAllCardsAt(@Url url: String): List<CardDto>

    @GET
    suspend fun getPacksAt(@Url url: String): List<PackDto>

    /**
     * Fetches a deck as a raw response.
     *
     * Deliberately not typed as `DeckDto`: MarvelCDB signals both failure modes
     * without an error status, and a typed converter would turn them into
     * unhelpful parse exceptions.
     *
     * - A decklist that does not exist returns **200 with an empty body**.
     * - A deck that is private or missing **302s to /login**, so with redirects
     *   followed we get 200 and a page of HTML.
     *
     * `DeckRepository` inspects the response for both.
     */
    @GET
    suspend fun getDeckRaw(@Url url: String): Response<ResponseBody>
}
