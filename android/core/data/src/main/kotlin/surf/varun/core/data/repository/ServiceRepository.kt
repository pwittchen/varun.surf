package surf.varun.core.data.repository

import surf.varun.core.data.util.catching
import surf.varun.core.model.ForecastProgress
import surf.varun.core.model.ServiceStatus
import surf.varun.core.model.Sponsor
import surf.varun.core.network.VarunNetworkDataSource
import javax.inject.Inject
import javax.inject.Singleton

/** Small, uncached-on-disk endpoints: status, sweep progress, sponsors. */
interface ServiceRepository {
    suspend fun status(): Result<ServiceStatus>

    suspend fun forecastProgress(): Result<ForecastProgress>

    /** Main sponsors; kept in memory after the first success (they change with releases). */
    suspend fun sponsors(): Result<List<Sponsor>>

    val baseUrl: String
}

@Singleton
class NetworkServiceRepository @Inject constructor(
    private val network: VarunNetworkDataSource,
) : ServiceRepository {
    @Volatile
    private var sponsorsCache: List<Sponsor>? = null

    override suspend fun status() = catching { network.status() }

    override suspend fun forecastProgress() = catching { network.forecastProgress() }

    override suspend fun sponsors(): Result<List<Sponsor>> {
        sponsorsCache?.let { return Result.success(it) }
        return catching { network.sponsors() }.onSuccess { sponsorsCache = it }
    }

    override val baseUrl: String get() = network.baseUrl
}
