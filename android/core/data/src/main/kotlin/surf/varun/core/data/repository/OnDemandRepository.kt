package surf.varun.core.data.repository

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import surf.varun.core.data.mapper.analysisEntities
import surf.varun.core.data.mapper.modelEntities
import surf.varun.core.data.util.ApplicationScope
import surf.varun.core.data.util.catching
import surf.varun.core.database.dao.SpotDao
import surf.varun.core.model.Spot
import surf.varun.core.network.VarunNetworkDataSource
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

enum class OnDemandKind { AI_ANALYSIS, ICM_FORECAST }

sealed interface OnDemandState {
    data object Idle : OnDemandState
    data object Running : OnDemandState
    data object Succeeded : OnDemandState
    data class Failed(val error: Throwable) : OnDemandState
}

/**
 * The two paid operations (spec 6.5). Each press may cost an LLM call, so:
 * one request per spot and kind at a time (a second press joins the running one),
 * never retried automatically, run in the application scope so leaving the screen
 * or rotating doesn't cancel what may already be billed, and the result written to
 * the database so it shows up wherever the spot is on screen.
 *
 * Only ever call this from an explicit, confirmed user action - never from
 * widgets, workers or alerts.
 */
interface OnDemandRepository {
    fun state(wgId: Int, kind: OnDemandKind): Flow<OnDemandState>

    /** Generate the AI analysis; the backend writes both languages, [language] decides failure. */
    fun generateAnalysis(wgId: Int, language: String): Deferred<Result<Spot>>

    fun generateIcm(wgId: Int): Deferred<Result<Spot>>

    /** Forget a finished state (e.g. after the error was shown). */
    fun acknowledge(wgId: Int, kind: OnDemandKind)
}

@Singleton
class NetworkOnDemandRepository @Inject constructor(
    private val network: VarunNetworkDataSource,
    private val dao: SpotDao,
    @param:ApplicationScope private val scope: CoroutineScope,
) : OnDemandRepository {
    private data class Key(val wgId: Int, val kind: OnDemandKind)

    private val states = MutableStateFlow<Map<Key, OnDemandState>>(emptyMap())
    private val running = HashMap<Key, Deferred<Result<Spot>>>()

    override fun state(wgId: Int, kind: OnDemandKind): Flow<OnDemandState> =
        states.map { it[Key(wgId, kind)] ?: OnDemandState.Idle }.distinctUntilChanged()

    override fun generateAnalysis(wgId: Int, language: String) =
        launch(Key(wgId, OnDemandKind.AI_ANALYSIS)) { network.generateAnalysis(wgId, language) }

    override fun generateIcm(wgId: Int) = launch(Key(wgId, OnDemandKind.ICM_FORECAST)) { network.generateIcm(wgId) }

    override fun acknowledge(wgId: Int, kind: OnDemandKind) {
        val key = Key(wgId, kind)
        states.update { if (it[key] == OnDemandState.Running) it else it - key }
    }

    private fun launch(key: Key, call: suspend () -> Spot): Deferred<Result<Spot>> = synchronized(running) {
        running[key]?.takeIf { it.isActive }?.let { return it }
        states.update { it + (key to OnDemandState.Running) }
        val deferred = scope.async {
            val result = catching {
                val spot = call()
                dao.deleteAnalysis(spot.wgId)
                dao.insertAnalysis(spot.analysisEntities())
                if (spot.availableModels.isNotEmpty()) {
                    dao.deleteModels(spot.wgId)
                    dao.insertModels(spot.modelEntities())
                }
                spot
            }
            result.onFailure { Timber.w(it, "On-demand %s for %d failed", key.kind, key.wgId) }
            states.update {
                it + (key to result.fold({ OnDemandState.Succeeded }, { error -> OnDemandState.Failed(error) }))
            }
            synchronized(running) { running.remove(key) }
            result
        }
        running[key] = deferred
        deferred
    }
}
