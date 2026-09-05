package com.rescuenet.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Hilt entry point. Implements Configuration.Provider so WorkManager's SyncWorker (Phase 3)
 * can receive Hilt-injected dependencies (DAOs, repositories) via @HiltWorker/@AssistedInject
 * instead of a manual factory — this is why AndroidManifest disables WorkManager's default
 * initializer (see the <provider> removal in AndroidManifest.xml).
 */
@HiltAndroidApp
class RescueNetApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
