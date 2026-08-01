package com.hasyame.marvelchampions.data.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import com.hasyame.marvelchampions.data.repository.CardDataRepository
import com.hasyame.marvelchampions.data.repository.CardSyncProgress
import com.hasyame.marvelchampions.data.settings.AppPreferences
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import java.io.IOException

/**
 * Refreshes the card database from MarvelCDB.
 *
 * WorkManager rather than a plain coroutine so the download survives the
 * Settings screen being closed. Progress is published as worker progress data,
 * which the UI observes.
 */
@HiltWorker
class CardSyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val repository: CardDataRepository,
    private val preferences: AppPreferences,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = try {
        repository.refreshFromNetwork { progress -> setProgressAsync(progress.toData()) }
        preferences.setLastCardSync(System.currentTimeMillis())
        Result.success()
    } catch (cancellation: CancellationException) {
        // Cancellation is the user pressing cancel, not a failure. The
        // repository writes each locale in one transaction, so nothing is left
        // half applied.
        throw cancellation
    } catch (io: IOException) {
        // Network trouble is worth another go on a better connection.
        if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else failure(io)
    } catch (error: Exception) {
        // A parse error or an unexpected API change will not fix itself.
        failure(error)
    }

    private fun failure(error: Throwable) = Result.failure(
        Data.Builder().putString(KEY_ERROR, error.message ?: error::class.java.simpleName).build(),
    )

    private fun CardSyncProgress.toData(): Data = Data.Builder()
        .putString(KEY_STEP, step.name)
        .putString(KEY_LOCALE, locale?.code)
        .build()

    companion object {
        const val NAME: String = "card-sync"
        const val KEY_STEP: String = "step"
        const val KEY_LOCALE: String = "locale"
        const val KEY_ERROR: String = "error"
        private const val MAX_ATTEMPTS = 3
    }
}
