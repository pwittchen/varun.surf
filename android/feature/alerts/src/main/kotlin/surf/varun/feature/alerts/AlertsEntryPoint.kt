package surf.varun.feature.alerts

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import surf.varun.core.data.repository.AlertsRepository

/** For code Hilt can't inject: broadcast receivers started by the system, [AlertsWork]. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface AlertsEntryPoint {
    fun alertsRepository(): AlertsRepository
}
