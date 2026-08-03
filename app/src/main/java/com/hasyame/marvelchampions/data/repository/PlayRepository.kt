package com.hasyame.marvelchampions.data.repository

import com.hasyame.marvelchampions.data.bgg.BggAccount
import com.hasyame.marvelchampions.data.bgg.BggClient
import com.hasyame.marvelchampions.data.bgg.BggResult
import com.hasyame.marvelchampions.data.db.dao.AspectRow
import com.hasyame.marvelchampions.data.db.dao.PlayDao
import com.hasyame.marvelchampions.data.db.dao.WinRateRow
import com.hasyame.marvelchampions.data.db.entity.PlayEntity
import com.hasyame.marvelchampions.domain.model.BggPlay
import com.hasyame.marvelchampions.domain.model.BggReportingMode
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** What happened when a play was recorded, so the UI can say something true. */
sealed interface PlayRecorded {
    /** Saved, and BoardGameGeek was not involved. */
    data object SavedOnly : PlayRecorded

    /** Saved and sent. */
    data object SavedAndReported : PlayRecorded

    /** Saved, but the player should be asked whether to send it. */
    data object SavedAskToReport : PlayRecorded

    /** Saved; sending failed and the play is still here to send later. */
    data class SavedReportFailed(val detail: String) : PlayRecorded
}

@Singleton
class PlayRepository @Inject constructor(
    private val playDao: PlayDao,
    private val bggAccount: BggAccount,
    private val bggClient: BggClient,
    private val ioDispatcher: CoroutineDispatcher,
) {

    fun observePlays(): Flow<List<PlayEntity>> = playDao.observePlays()

    fun observeByHero(): Flow<List<WinRateRow>> = playDao.observeByHero()

    fun observeByScenario(): Flow<List<WinRateRow>> = playDao.observeByScenario()

    fun observeByDifficulty(): Flow<List<WinRateRow>> = playDao.observeByDifficulty()

    /** Aspects split out of their stored list and counted per aspect. */
    fun observeByAspect(): Flow<List<WinRateRow>> =
        playDao.observeAspectRows().map { rows -> countAspects(rows) }

    fun newPlayId(): String = UUID.randomUUID().toString()

    /**
     * Saves a play, and reports it if the player has asked for that.
     *
     * Saving always happens first and never depends on the network: a recorded
     * game is the player's own history, and losing it because BoardGameGeek was
     * unreachable would be indefensible.
     */
    suspend fun record(play: PlayEntity): PlayRecorded = withContext(ioDispatcher) {
        playDao.insert(play)

        when (bggAccount.currentMode()) {
            BggReportingMode.OFF -> PlayRecorded.SavedOnly
            BggReportingMode.ASK -> PlayRecorded.SavedAskToReport
            BggReportingMode.ALWAYS -> report(play.id)
        }
    }

    /**
     * Sends a saved play to BoardGameGeek.
     *
     * Only marked as reported on success, so a failure leaves it sendable
     * rather than silently dropped.
     */
    suspend fun report(playId: String): PlayRecorded = withContext(ioDispatcher) {
        val play = playDao.getPlay(playId) ?: return@withContext PlayRecorded.SavedOnly
        if (play.reportedToBgg) {
            return@withContext PlayRecorded.SavedAndReported
        }

        val credentials = bggAccount.credentialsForReporting()
            ?: return@withContext PlayRecorded.SavedOnly

        val result = bggClient.reportPlay(
            username = credentials.first,
            password = credentials.second,
            play = play.toBggPlay(),
        )

        when (result) {
            is BggResult.Success -> {
                playDao.markReported(play.id)
                PlayRecorded.SavedAndReported
            }

            is BggResult.BadCredentials ->
                PlayRecorded.SavedReportFailed("BoardGameGeek rejected the saved credentials")

            is BggResult.Rejected -> PlayRecorded.SavedReportFailed(result.detail)
            is BggResult.Offline -> PlayRecorded.SavedReportFailed(result.detail)
        }
    }

    suspend fun delete(playId: String) = withContext(ioDispatcher) { playDao.delete(playId) }

    /**
     * BoardGameGeek has no win flag on a play, so the outcome and the details
     * that make the entry worth reading go in the comment.
     */
    private fun PlayEntity.toBggPlay(): BggPlay {
        val heroes = listOfNotNull(
            heroName.takeIf { it.isNotBlank() },
            otherHeroes.takeIf { it.isNotBlank() },
        ).joinToString(", ")

        val comment = buildString {
            append(if (won) "Win" else "Loss")
            append(" — ").append(scenarioName)
            append(" (").append(difficulty).append(')')
            if (heroes.isNotBlank()) {
                append("\nHeroes: ").append(heroes)
            }
            if (aspects.isNotBlank()) {
                append("\nAspects: ").append(aspects)
            }
            if (notes.isNotBlank()) {
                append('\n').append(notes)
            }
        }

        return BggPlay(
            playedOn = DATE_FORMAT.format(Date(playedAt)),
            // BGG records length in minutes. A game shorter than a minute is
            // almost certainly a mistimed entry, so it reports as zero rather
            // than rounding up to something that looks deliberate.
            lengthMinutes = TimeUnit.MILLISECONDS.toMinutes(elapsedMillis).toInt(),
            players = players,
            won = won,
            comment = comment,
        )
    }

    private fun countAspects(rows: List<AspectRow>): List<WinRateRow> {
        val played = mutableMapOf<String, Int>()
        val won = mutableMapOf<String, Int>()

        val millis = mutableMapOf<String, Long>()

        for (row in rows) {
            row.aspects.split(',')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                // A hero played with two aspects counts once for each, so the
                // columns do not add up to the number of games. That is correct
                // for the question being asked: how does this aspect do.
                .distinct()
                .forEach { aspect ->
                    played[aspect] = (played[aspect] ?: 0) + 1
                    millis[aspect] = (millis[aspect] ?: 0) + row.elapsedMillis
                    if (row.won) {
                        won[aspect] = (won[aspect] ?: 0) + 1
                    }
                }
        }

        return played.map { (aspect, count) ->
            WinRateRow(aspect, count, won[aspect] ?: 0, millis[aspect] ?: 0)
        }.sortedWith(compareByDescending<WinRateRow> { it.played }.thenBy { it.key })
    }

    private companion object {
        // Fixed locale: this is a wire format for BoardGameGeek, not something
        // a person reads, so it must not follow the device language.
        val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    }
}
