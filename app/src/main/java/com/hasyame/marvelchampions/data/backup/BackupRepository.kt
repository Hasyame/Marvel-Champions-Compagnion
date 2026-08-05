package com.hasyame.marvelchampions.data.backup

import android.content.Context
import android.net.Uri
import com.hasyame.marvelchampions.data.db.MarvelChampionsDatabase
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import androidx.room.withTransaction
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Writes and reads a backup file.
 *
 * This app has no account and no server, so the device is the only copy of
 * everything a player has built. A lost phone loses a collection, every deck,
 * every campaign and years of play history with no recourse whatever. A file
 * the player can put somewhere else is the whole answer.
 *
 * Deliberately a plain, readable JSON document rather than a database copy: it
 * survives a schema change, can be inspected, and can be repaired by hand if it
 * ever comes to that.
 */
@Singleton
class BackupRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val database: MarvelChampionsDatabase,
    private val ioDispatcher: CoroutineDispatcher,
) {

    /**
     * Indented on purpose. A backup is read by people at least as often as by
     * the app — usually when something has already gone wrong.
     */
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun export(destination: Uri): BackupResult = withContext(ioDispatcher) {
        runCatching {
            // Read once and reused: the events are looked up per run, and
            // fetching the run list a second time to do it was pure waste.
            val runs = database.campaignDao().getRuns()

            val backup = Backup(
                createdAt = System.currentTimeMillis(),
                appVersion = appVersion(),
                ownedPacks = database.ownedPackDao().getOwned(),
                excludedModularSets = database.excludedModularSetDao().getExcluded(),
                decks = database.savedDeckDao().getDecks(),
                campaignRuns = runs,
                campaignEvents = runs.flatMap { database.campaignDao().getEvents(it.id) },
                plays = database.playDao().getAllPlays(),
                randomizerHistory = database.randomizerHistoryDao().getHistory(),
                favouriteCards = database.favouriteDao().getAll(),
            )

            val bytes = json.encodeToString(Backup.serializer(), backup).toByteArray()
            // "wt", not the default "w". Several document providers do not
            // truncate on plain write, so overwriting a longer backup with a
            // shorter one left the old tail behind and produced a file that no
            // longer parsed — a corrupt backup being much worse than none.
            context.contentResolver.openOutputStream(destination, "wt")?.use {
                it.write(bytes)
            } ?: error("could not open the file for writing")

            BackupResult.Exported(bytes.size.toLong())
        }.getOrElse { BackupResult.Failed(it.message ?: "unknown error") }
    }

    /** Reads a file without changing anything, so a restore can be confirmed first. */
    suspend fun peek(source: Uri): Result<Backup> = withContext(ioDispatcher) {
        runCatching {
            val text = context.contentResolver.openInputStream(source)?.use {
                it.readBytes().decodeToString()
            } ?: error("could not open the file")

            val backup = json.decodeFromString(Backup.serializer(), text)
            if (backup.formatVersion > Backup.CURRENT_FORMAT_VERSION) {
                error(
                    "this backup was written by a newer version of the app " +
                        "(format ${backup.formatVersion})",
                )
            }
            backup
        }
    }

    /**
     * Replaces the player's data with the contents of a backup.
     *
     * A restore replaces rather than merges. Merging two histories means
     * deciding what to do about a deck edited on both sides, and there is no
     * answer to that which is not a guess — replacing is at least a thing the
     * player can predict. The confirmation says so before this runs.
     *
     * In one transaction, so a failure halfway leaves the previous data intact
     * rather than half of each.
     */
    suspend fun restore(backup: Backup): BackupResult = withContext(ioDispatcher) {
        runCatching {
            database.withTransaction {
                database.playDao().deleteAll()
                database.campaignDao().deleteAllRuns()
                database.savedDeckDao().deleteAll()
                database.ownedPackDao().clearOwned()
                database.excludedModularSetDao().clear()
                database.randomizerHistoryDao().clear()
                database.favouriteDao().deleteAll()

                database.ownedPackDao().upsertAll(backup.ownedPacks)
                database.excludedModularSetDao().excludeAll(backup.excludedModularSets)
                database.savedDeckDao().upsertAll(backup.decks)
                backup.campaignRuns.forEach { database.campaignDao().insertRun(it) }
                // After the runs: an event references its run, and the foreign
                // key would reject it the other way round.
                database.campaignDao().appendEvents(backup.campaignEvents)
                backup.plays.forEach { database.playDao().insert(it) }
                database.randomizerHistoryDao().insertAll(backup.randomizerHistory)
                database.favouriteDao().addAll(backup.favouriteCards)
            }
            BackupResult.Restored(backup.summary())
        }.getOrElse { BackupResult.Failed(it.message ?: "unknown error") }
    }

    /** A filename that sorts by date and says what it is. */
    fun suggestedFileName(): String =
        "marvel-champions-companion-backup-${DATE.format(java.util.Date())}.json"

    private fun appVersion(): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull().orEmpty()

    private companion object {
        val DATE = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
    }
}
