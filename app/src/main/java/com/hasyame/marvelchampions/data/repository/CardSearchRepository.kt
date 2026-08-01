package com.hasyame.marvelchampions.data.repository

import com.hasyame.marvelchampions.data.db.dao.CardDao
import com.hasyame.marvelchampions.data.db.entity.CardEntity
import com.hasyame.marvelchampions.domain.model.CardLocale
import com.hasyame.marvelchampions.domain.search.SearchNormalizer
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CardSearchRepository @Inject constructor(
    private val cardDao: CardDao,
    private val ioDispatcher: CoroutineDispatcher,
) {

    /**
     * Searches name and card text.
     *
     * An empty or punctuation-only query means "no filter", so it returns the
     * first page rather than nothing — otherwise the card list would appear
     * broken before the user types anything.
     */
    suspend fun search(
        query: String,
        locale: CardLocale,
        limit: Int = DEFAULT_LIMIT,
    ): List<CardEntity> = withContext(ioDispatcher) {
        val matchQuery = SearchNormalizer.toPrefixMatchQuery(query)
            ?: return@withContext cardDao.getPage(locale.code, limit, offset = 0)
        cardDao.search(matchQuery, locale.code, limit)
    }

    suspend fun getCard(code: String, locale: CardLocale): CardEntity? =
        withContext(ioDispatcher) { cardDao.getCard(code, locale.code) }

    fun observeCard(code: String, locale: CardLocale): Flow<CardEntity?> =
        cardDao.observeCard(code, locale.code)

    suspend fun countForLocale(locale: CardLocale): Int =
        withContext(ioDispatcher) { cardDao.countForLocale(locale.code) }

    private companion object {
        const val DEFAULT_LIMIT = 200
    }
}
