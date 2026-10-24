package surf.varun.core.data.util

import javax.inject.Qualifier

/** Scope that outlives screens: on-demand generation and fire-and-forget writes run here. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher
