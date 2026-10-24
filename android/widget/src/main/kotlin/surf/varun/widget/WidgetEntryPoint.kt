package surf.varun.widget

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import surf.varun.core.data.repository.FavouritesRepository
import surf.varun.core.data.repository.SettingsRepository
import surf.varun.core.data.repository.SpotsRepository

/** Glance widgets and their callbacks can't be injected; they reach the graph through here. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun spotsRepository(): SpotsRepository

    fun favouritesRepository(): FavouritesRepository

    fun settingsRepository(): SettingsRepository
}

internal fun Context.widgetEntryPoint(): WidgetEntryPoint =
    EntryPointAccessors.fromApplication(applicationContext, WidgetEntryPoint::class.java)
