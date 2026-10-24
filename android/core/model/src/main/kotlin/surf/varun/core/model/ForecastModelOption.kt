package surf.varun.core.model

/** A forecast model the spot has data for (`AvailableModel` on the server). */
data class ForecastModelOption(val key: String, val name: String) {
    companion object {
        const val DEFAULT_KEY = "gfs"
        const val ICM_KEY = "icm"
    }
}
