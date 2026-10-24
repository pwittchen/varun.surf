package surf.varun.app

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import surf.varun.core.data.repository.FavouritesRepository
import surf.varun.core.data.repository.RecentsRepository
import surf.varun.core.data.repository.SpotsRepository
import surf.varun.core.data.util.ApplicationScope
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the launcher's dynamic shortcuts (spec 2.5.7) on the most recently opened
 * spots, then the favourites, next to the static "Wind map" and "Search" ones.
 */
@Singleton
class ShortcutsUpdater @Inject constructor(
    @ApplicationContext private val context: Context,
    private val recents: RecentsRepository,
    private val favourites: FavouritesRepository,
    private val spots: SpotsRepository,
    @param:ApplicationScope private val scope: CoroutineScope,
) {
    @OptIn(kotlinx.coroutines.FlowPreview::class)
    fun start() {
        scope.launch {
            combine(recents.recentSpotIds, favourites.favouriteIds) { recent, favourite -> (recent + favourite).distinct() }
                .distinctUntilChanged()
                .debounce(1_000)
                .collect { ids -> publish(ids) }
        }
    }

    private suspend fun publish(ids: List<Int>) {
        val limit = (ShortcutManagerCompat.getMaxShortcutCountPerActivity(context) - STATIC_SHORTCUTS).coerceIn(0, MAX_DYNAMIC)
        val byId = spots.spots(ids.take(limit * 2)).associateBy { it.wgId }
        val shortcuts = ids.mapNotNull(byId::get).take(limit).mapIndexed { rank, spot ->
            ShortcutInfoCompat.Builder(context, "spot-${spot.wgId}")
                .setShortLabel(spot.name)
                .setLongLabel(spot.name)
                .setIcon(IconCompat.createWithResource(context, R.drawable.ic_shortcut_spot))
                .setIntent(Intent(Intent.ACTION_VIEW, "varun://spot/${spot.wgId}".toUri()).setPackage(context.packageName))
                .setRank(rank)
                .build()
        }
        runCatching { ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts) }
            .onFailure { Timber.w(it, "Could not publish shortcuts") }
    }

    private companion object {
        const val STATIC_SHORTCUTS = 2
        const val MAX_DYNAMIC = 4
    }
}
