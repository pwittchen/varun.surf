package surf.varun.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import dagger.hilt.android.HiltAndroidApp
import okhttp3.OkHttpClient
import surf.varun.feature.alerts.AlertsWork
import surf.varun.widget.WidgetWork
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Provider

@HiltAndroidApp
class VarunApplication : Application(), Configuration.Provider, SingletonImageLoader.Factory {
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    /** The app's base client: Coil shares its connection pool and HTTP cache. */
    @Inject
    lateinit var okHttpClient: Provider<OkHttpClient>

    @Inject
    lateinit var shortcuts: ShortcutsUpdater

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) Timber.plant(Timber.DebugTree())
        AlertsWork.schedule(this)
        WidgetWork.schedule(this)
        shortcuts.start()
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { okHttpClient.get() })) }
            .build()
}
