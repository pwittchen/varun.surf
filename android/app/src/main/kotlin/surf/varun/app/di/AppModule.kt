package surf.varun.app.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import surf.varun.app.BuildConfig
import surf.varun.core.network.NetworkConfig
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    /** `X-Client: varun-android/<version>` makes app traffic visible apart from the web (spec 12.8). */
    @Provides
    @Singleton
    fun networkConfig(): NetworkConfig = NetworkConfig(clientId = "varun-android/${BuildConfig.VERSION_NAME}")
}
