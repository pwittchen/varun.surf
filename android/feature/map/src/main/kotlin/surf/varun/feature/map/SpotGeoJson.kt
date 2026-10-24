package surf.varun.feature.map

import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

/** GeoJSON properties of a spot feature; the style layers read them by these names. */
object SpotProps {
    const val WG_ID = "wgId"
    const val WIND = "wind"
    const val GUSTS = "gusts"
    const val DIR_INDEX = "dirIndex"
    const val WIND_CLASS = "windClass"
    const val LIVE = "live"

    /** Cluster aggregate: strongest wind among the cluster's spots (-1 = none has data). */
    const val MAX_WIND = "maxWind"
}

/**
 * Spots as one GeoJSON collection for the clustered source. A timeline step swaps
 * the whole collection; the layers never change.
 */
fun List<MapSpotFeature>.toFeatureCollection(): FeatureCollection = FeatureCollection.fromFeatures(
    map { spot ->
        Feature.fromGeometry(Point.fromLngLat(spot.lon, spot.lat), null, spot.wgId.toString()).apply {
            addNumberProperty(SpotProps.WG_ID, spot.wgId)
            spot.wind?.let { addNumberProperty(SpotProps.WIND, it) }
            spot.gusts?.let { addNumberProperty(SpotProps.GUSTS, it) }
            spot.dirIndex?.let { addNumberProperty(SpotProps.DIR_INDEX, it) }
            addStringProperty(SpotProps.WIND_CLASS, spot.windClass)
            addBooleanProperty(SpotProps.LIVE, spot.isLive)
        }
    },
)
