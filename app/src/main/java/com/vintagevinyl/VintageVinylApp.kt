package com.vintagevinyl

import android.app.Application

/**
 * Application class. Owns the [AppContainer], the app's hand-written dependency graph.
 *
 * Hilt was deliberately not used: the graph is a dozen singletons, and an annotation-processor
 * (KSP/KAPT) plugin would add build complexity and a failure mode for no benefit at this size.
 * The container is also reachable from the playback service, which lives in the same process.
 */
class VintageVinylApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
