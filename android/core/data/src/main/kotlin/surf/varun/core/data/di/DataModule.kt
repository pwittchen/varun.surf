package surf.varun.core.data.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import surf.varun.core.data.repository.AlertsRepository
import surf.varun.core.data.repository.CachedWindTimelineRepository
import surf.varun.core.data.repository.DataStoreSettingsRepository
import surf.varun.core.data.repository.FavouritesRepository
import surf.varun.core.data.repository.LocationRepository
import surf.varun.core.data.repository.NetworkOnDemandRepository
import surf.varun.core.data.repository.NetworkServiceRepository
import surf.varun.core.data.repository.OfflineFirstSpotsRepository
import surf.varun.core.data.repository.OnDemandRepository
import surf.varun.core.data.repository.PlatformLocationRepository
import surf.varun.core.data.repository.RecentsRepository
import surf.varun.core.data.repository.RoomAlertsRepository
import surf.varun.core.data.repository.RoomFavouritesRepository
import surf.varun.core.data.repository.RoomRecentsRepository
import surf.varun.core.data.repository.ServiceRepository
import surf.varun.core.data.repository.SettingsRepository
import surf.varun.core.data.repository.SpotsRepository
import surf.varun.core.data.repository.WindTimelineRepository
import surf.varun.core.data.util.AndroidConnectivityMonitor
import surf.varun.core.data.util.ApplicationScope
import surf.varun.core.data.util.ConnectivityMonitor
import surf.varun.core.data.util.IoDispatcher
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {
    @Binds abstract fun spots(impl: OfflineFirstSpotsRepository): SpotsRepository

    @Binds abstract fun favourites(impl: RoomFavouritesRepository): FavouritesRepository

    @Binds abstract fun alerts(impl: RoomAlertsRepository): AlertsRepository

    @Binds abstract fun recents(impl: RoomRecentsRepository): RecentsRepository

    @Binds abstract fun settings(impl: DataStoreSettingsRepository): SettingsRepository

    @Binds abstract fun service(impl: NetworkServiceRepository): ServiceRepository

    @Binds abstract fun onDemand(impl: NetworkOnDemandRepository): OnDemandRepository

    @Binds abstract fun windTimeline(impl: CachedWindTimelineRepository): WindTimelineRepository

    @Binds abstract fun location(impl: PlatformLocationRepository): LocationRepository

    @Binds abstract fun connectivity(impl: AndroidConnectivityMonitor): ConnectivityMonitor

    companion object {
        @Provides
        @Singleton
        fun clock(): Clock = Clock.systemDefaultZone()

        @Provides
        @IoDispatcher
        fun io(): CoroutineDispatcher = Dispatchers.IO

        @Provides
        @Singleton
        @ApplicationScope
        fun applicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
