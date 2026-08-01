package com.hasyame.marvelchampions

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class MarvelChampionsApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    /**
     * WorkManager is configured on demand rather than by its default
     * initializer, so [com.hasyame.marvelchampions.data.sync.CardSyncWorker]
     * can be constructed by Hilt. The default initializer is removed in the
     * manifest.
     */
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
