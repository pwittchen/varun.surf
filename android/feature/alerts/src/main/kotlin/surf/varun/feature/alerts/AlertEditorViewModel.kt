package surf.varun.feature.alerts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import surf.varun.core.data.repository.AlertsRepository
import surf.varun.core.data.repository.SettingsRepository
import surf.varun.core.data.repository.SpotsRepository
import surf.varun.core.domain.SpotSearch
import surf.varun.core.domain.Units
import surf.varun.core.model.AlertTrigger
import surf.varun.core.model.Direction
import surf.varun.core.model.Spot
import surf.varun.core.model.WindAlert
import surf.varun.core.model.WindUnit
import surf.varun.core.navigation.AlertEditorRoute
import kotlin.math.roundToInt

/** The spot an alert is for, as the editor shows it. */
data class SpotChoice(val wgId: Int, val name: String, val country: String, val hasLiveStation: Boolean)

enum class EditorError { NO_SPOT, MAX_BELOW_MIN }

data class AlertEditorState(
    val loading: Boolean = true,
    val isNew: Boolean = true,
    val spot: SpotChoice? = null,
    val query: String = "",
    val minWind: Int = DEFAULT_MIN_WIND,
    val maxWind: Int? = null,
    val directions: Set<Direction> = emptySet(),
    val trigger: AlertTrigger = AlertTrigger.FORECAST,
    val horizonDays: Int = 3,
    val minConsecutiveHours: Int = 2,
    val quietHours: Boolean = true,
    val quietStartHour: Int = 22,
    val quietEndHour: Int = 7,
    val unit: WindUnit = WindUnit.KNOTS,
    val error: EditorError? = null,
) {
    companion object {
        const val DEFAULT_MIN_WIND = 15
        const val MIN_KNOTS = 5
        const val MAX_KNOTS = 45
    }
}

sealed interface EditorEvent {
    /** Saved; [firstAlert] asks the screen to request notification permission. */
    data class Saved(val firstAlert: Boolean) : EditorEvent
}

@HiltViewModel(assistedFactory = AlertEditorViewModel.Factory::class)
class AlertEditorViewModel @AssistedInject constructor(
    @Assisted private val route: AlertEditorRoute,
    private val alerts: AlertsRepository,
    private val spots: SpotsRepository,
    settings: SettingsRepository,
) : ViewModel() {
    @AssistedFactory
    interface Factory {
        fun create(route: AlertEditorRoute): AlertEditorViewModel
    }

    private val _state = MutableStateFlow(AlertEditorState())
    val state: StateFlow<AlertEditorState> = _state.asStateFlow()

    private val events = Channel<EditorEvent>(Channel.BUFFERED)
    val eventFlow: Flow<EditorEvent> = events.receiveAsFlow()

    /** The alert being edited, kept so saving doesn't lose lastFiredAt / mutedUntil. */
    private var original: WindAlert? = null

    /** Directions are prefilled from the spot until the user touches them. */
    private var directionsTouched = false

    private val index = spots.spots.map { SpotSearch.index(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SpotSearch.Index(emptyList()))

    val results: StateFlow<List<Spot>> = combine(index, _state.map { it.query }) { idx, query ->
        if (query.isBlank()) emptyList() else SpotSearch.search(idx, query, limit = 20)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            settings.settings.collect { s -> _state.update { it.copy(unit = s.windUnit) } }
        }
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val existing = route.alertId?.let { alerts.get(it) }
        if (existing != null) {
            original = existing
            directionsTouched = true
            val spot = spots.spots(listOf(existing.wgId)).firstOrNull()
            _state.update {
                it.copy(
                    loading = false,
                    isNew = false,
                    spot = spot?.toChoice() ?: SpotChoice(existing.wgId, existing.spotName, "", false),
                    minWind = existing.minWind,
                    maxWind = existing.maxWind,
                    directions = existing.directions,
                    trigger = existing.trigger,
                    horizonDays = existing.horizonDays,
                    minConsecutiveHours = existing.minConsecutiveHours,
                    quietHours = existing.quietStartHour != null && existing.quietEndHour != null,
                    quietStartHour = existing.quietStartHour ?: 22,
                    quietEndHour = existing.quietEndHour ?: 7,
                )
            }
            return
        }
        val spot = route.wgId?.let { id -> spots.spots(listOf(id)).firstOrNull() }
        _state.update { it.copy(loading = false) }
        spot?.let(::selectSpot)
    }

    fun setQuery(query: String) = _state.update { it.copy(query = query) }

    fun selectSpot(spot: Spot) {
        _state.update { state ->
            state.copy(
                spot = spot.toChoice(),
                query = "",
                error = null,
                directions = if (directionsTouched) state.directions else BestWind.parse(spot.info?.bestWind ?: spot.infoPl?.bestWind),
                // A spot without a station can only have forecast alerts
                trigger = if (spot.live == null) AlertTrigger.FORECAST else state.trigger,
            )
        }
    }

    fun clearSpot() = _state.update { it.copy(spot = null) }

    /** [value] is in the user's unit; the alert stores knots. */
    fun setMinWind(value: Float) = _state.update { state ->
        val knots = toKnots(value, state.unit)
        state.copy(minWind = knots, error = null)
    }

    fun setMaxWind(value: Float) = _state.update { state ->
        state.copy(maxWind = toKnots(value, state.unit), error = null)
    }

    fun setMaxEnabled(enabled: Boolean) = _state.update { state ->
        state.copy(maxWind = if (enabled) (state.maxWind ?: (state.minWind + 10).coerceAtMost(AlertEditorState.MAX_KNOTS)) else null, error = null)
    }

    fun toggleDirection(direction: Direction) {
        directionsTouched = true
        _state.update { state ->
            state.copy(directions = if (direction in state.directions) state.directions - direction else state.directions + direction)
        }
    }

    fun clearDirections() {
        directionsTouched = true
        _state.update { it.copy(directions = emptySet()) }
    }

    fun setTrigger(trigger: AlertTrigger) = _state.update { it.copy(trigger = trigger) }

    fun setHorizonDays(days: Int) = _state.update { it.copy(horizonDays = days.coerceIn(1, MAX_HORIZON_DAYS)) }

    fun setMinConsecutiveHours(hours: Int) = _state.update { it.copy(minConsecutiveHours = hours.coerceIn(1, MAX_CONSECUTIVE_HOURS)) }

    fun setQuietHours(enabled: Boolean) = _state.update { it.copy(quietHours = enabled) }

    fun setQuietStart(hour: Int) = _state.update { it.copy(quietStartHour = Math.floorMod(hour, 24)) }

    fun setQuietEnd(hour: Int) = _state.update { it.copy(quietEndHour = Math.floorMod(hour, 24)) }

    fun save() {
        val state = _state.value
        val spot = state.spot ?: return _state.update { it.copy(error = EditorError.NO_SPOT) }
        if (state.maxWind != null && state.maxWind < state.minWind) {
            return _state.update { it.copy(error = EditorError.MAX_BELOW_MIN) }
        }
        viewModelScope.launch {
            val firstAlert = alerts.all().isEmpty()
            val base = original ?: WindAlert(wgId = spot.wgId, spotName = spot.name, minWind = state.minWind)
            alerts.save(
                base.copy(
                    wgId = spot.wgId,
                    spotName = spot.name,
                    minWind = state.minWind,
                    maxWind = state.maxWind,
                    directions = state.directions,
                    trigger = state.trigger,
                    horizonDays = state.horizonDays,
                    minConsecutiveHours = state.minConsecutiveHours,
                    quietStartHour = state.quietStartHour.takeIf { state.quietHours },
                    quietEndHour = state.quietEndHour.takeIf { state.quietHours },
                    // A changed rule deserves a fresh notification
                    lastFiredAt = if (original?.let { it.wgId == spot.wgId && it.minWind == state.minWind } == true) base.lastFiredAt else null,
                ),
            )
            events.send(EditorEvent.Saved(firstAlert))
        }
    }

    private fun Spot.toChoice() = SpotChoice(wgId, name, country, live != null)

    companion object {
        const val MAX_HORIZON_DAYS = 5
        const val MAX_CONSECUTIVE_HOURS = 6

        /** A slider value in [unit] to whole knots, kept within the editor's range. */
        fun toKnots(value: Float, unit: WindUnit): Int =
            Units.toKnots(value.toDouble(), unit).roundToInt()
                .coerceIn(AlertEditorState.MIN_KNOTS, AlertEditorState.MAX_KNOTS)
    }
}
