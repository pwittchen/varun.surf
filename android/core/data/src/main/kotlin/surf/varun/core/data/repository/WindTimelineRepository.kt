package surf.varun.core.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import surf.varun.core.data.util.IoDispatcher
import surf.varun.core.data.util.catching
import surf.varun.core.model.WindTimeline
import surf.varun.core.network.VarunNetworkDataSource
import timber.log.Timber
import java.io.File
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.inject.Inject
import javax.inject.Singleton

data class CachedTimeline(val timeline: WindTimeline, val hours: Int, val fetchedAt: Instant)

/**
 * The map's hourly wind grid (`/api/v1/wind`). Kept as one gzipped blob in the
 * cache directory rather than rows (spec 7.4), and refetched when older than an
 * hour or when the server's forecast sweep finished after it was fetched.
 */
interface WindTimelineRepository {
    val timeline: StateFlow<CachedTimeline?>

    suspend fun ensureFresh(hours: Int, force: Boolean = false): Result<CachedTimeline>
}

@Singleton
class CachedWindTimelineRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val network: VarunNetworkDataSource,
    private val service: ServiceRepository,
    private val clock: Clock,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) : WindTimelineRepository {
    private val mutex = Mutex()
    private val _timeline = MutableStateFlow<CachedTimeline?>(null)
    override val timeline: StateFlow<CachedTimeline?> = _timeline.asStateFlow()

    private fun file(hours: Int) = File(context.cacheDir, "wind-timeline-$hours.json.gz")

    override suspend fun ensureFresh(hours: Int, force: Boolean): Result<CachedTimeline> = mutex.withLock {
        val cached = _timeline.value?.takeIf { it.hours == hours } ?: readCache(hours)?.also { _timeline.value = it }
        if (cached != null && !force && isFresh(cached)) return Result.success(cached)

        val fetched = catching {
            val json = network.windTimelineJson(hours)
            val parsed = network.parseWindTimeline(json)
            withContext(io) { writeCache(hours, json) }
            CachedTimeline(parsed, hours, clock.instant())
        }
        fetched.onSuccess { _timeline.value = it }
        // Offline with a cached copy: keep showing it
        if (fetched.isFailure && cached != null) Result.success(cached) else fetched
    }

    private suspend fun isFresh(cached: CachedTimeline): Boolean {
        val age = Duration.between(cached.fetchedAt, clock.instant())
        if (age >= MAX_AGE) return false
        // Asking for the sweep's progress costs a request; skip it for a just-fetched grid
        if (age < CHECK_SWEEP_AFTER) return true
        val finishedAt = service.forecastProgress().getOrNull()?.finishedAt ?: return true
        return !finishedAt.isAfter(cached.fetchedAt)
    }

    private suspend fun readCache(hours: Int): CachedTimeline? = withContext(io) {
        val file = file(hours)
        if (!file.exists()) return@withContext null
        runCatching {
            val json = GZIPInputStream(file.inputStream()).bufferedReader().use { it.readText() }
            CachedTimeline(network.parseWindTimeline(json), hours, Instant.ofEpochMilli(file.lastModified()))
        }.onFailure { Timber.w(it, "Unreadable wind timeline cache") }.getOrNull()
    }

    private fun writeCache(hours: Int, json: String) {
        val file = file(hours)
        val tmp = File(file.parentFile, file.name + ".tmp")
        GZIPOutputStream(tmp.outputStream()).bufferedWriter().use { it.write(json) }
        tmp.renameTo(file)
        file.setLastModified(clock.millis())
    }

    private companion object {
        val MAX_AGE: Duration = Duration.ofHours(1)
        val CHECK_SWEEP_AFTER: Duration = Duration.ofMinutes(10)
    }
}
