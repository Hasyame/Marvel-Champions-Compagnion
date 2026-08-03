package com.hasyame.marvelchampions.data.sync

import android.content.Context
import coil3.ImageLoader
import coil3.request.ImageRequest
import com.hasyame.marvelchampions.data.db.dao.CardDao
import com.hasyame.marvelchampions.data.marvelcdb.MarvelCdbUrls
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fills the image cache ahead of time, for the cards the player owns.
 *
 * The app is offline-first everywhere except pictures: card text lives in the
 * local database, but an image was fetched the first time it was looked at. At
 * a table with no signal that means blanks for every card not opened before —
 * which is most of them, and exactly when the app is meant to be useful.
 *
 * Owned packs only, and deliberately so. The full catalogue is several thousand
 * images; a collection is a few hundred, and the cards a player might need to
 * look up are the ones in their own boxes.
 */
@Singleton
class CardImagePrefetcher @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val cardDao: CardDao,
    private val imageLoader: ImageLoader,
    private val ioDispatcher: CoroutineDispatcher,
) {

    /**
     * Downloads what is missing, reporting how far along it is.
     *
     * A failure on one image is skipped rather than raised: a single missing
     * picture is not a reason to abandon the other four hundred, and the
     * fetcher will try again next time.
     */
    suspend fun prefetchOwned(
        ownedPackCodes: Set<String>,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ) = withContext(ioDispatcher) {
        if (ownedPackCodes.isEmpty()) {
            return@withContext
        }

        val urls = cardDao.getImageSources(ownedPackCodes.toList())
            .mapNotNull { MarvelCdbUrls.cardImage(it) }
            // One image serves every language of a card, so the same URL comes
            // back once per locale. Fetching it twice would double the work.
            .distinct()

        urls.forEachIndexed { index, url ->
            runCatching {
                imageLoader.execute(
                    ImageRequest.Builder(context)
                        .data(url)
                        .build(),
                )
            }
            onProgress(index + 1, urls.size)
        }
    }
}
