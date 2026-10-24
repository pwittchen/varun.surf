package surf.varun.core.network.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.Cache
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import surf.varun.core.network.HttpStack
import surf.varun.core.network.NetworkConfig
import surf.varun.core.network.VarunApi
import surf.varun.core.network.VarunPaidApi
import surf.varun.core.network.session.DataStoreSessionStore
import surf.varun.core.network.session.SessionManager
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun httpStack(config: NetworkConfig, @ApplicationContext context: Context): HttpStack =
        HttpStack(
            config = config,
            session = SessionManager(config.baseUrl.toHttpUrl(), DataStoreSessionStore(context)),
            cache = Cache(File(context.cacheDir, "http"), 50L * 1024 * 1024),
        )

    /** Cookies and client headers, no session logic: what the image loader uses. */
    @Provides
    fun okHttpClient(stack: HttpStack): OkHttpClient = stack.baseClient

    @Provides
    fun api(stack: HttpStack): VarunApi = stack.api

    @Provides
    fun paidApi(stack: HttpStack): VarunPaidApi = stack.paidApi

    @Provides
    fun sessionManager(stack: HttpStack): SessionManager = stack.session
}
