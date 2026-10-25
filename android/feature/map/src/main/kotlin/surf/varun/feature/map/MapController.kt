package surf.varun.feature.map

import surf.varun.core.ui.MapStyles
import android.content.Context
import android.graphics.PointF
import android.graphics.RectF
import androidx.compose.ui.graphics.toArgb
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.expressions.Expression.accumulated
import org.maplibre.android.style.expressions.Expression.all
import org.maplibre.android.style.expressions.Expression.coalesce
import org.maplibre.android.style.expressions.Expression.color
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.expressions.Expression.has
import org.maplibre.android.style.expressions.Expression.literal
import org.maplibre.android.style.expressions.Expression.match
import org.maplibre.android.style.expressions.Expression.not
import org.maplibre.android.style.expressions.Expression.step
import org.maplibre.android.style.expressions.Expression.stop
import org.maplibre.android.style.expressions.Expression.switchCase
import org.maplibre.android.style.expressions.Expression.toString as exprToString
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.circleBlur
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleOpacity
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.circleTranslate
import org.maplibre.android.style.layers.PropertyFactory.textAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.textColor
import org.maplibre.android.style.layers.PropertyFactory.textField
import org.maplibre.android.style.layers.PropertyFactory.textFont
import org.maplibre.android.style.layers.PropertyFactory.textIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.textSize
import org.maplibre.android.style.layers.PropertyFactory.visibility
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonOptions
import org.maplibre.android.style.sources.GeoJsonSource
import surf.varun.core.designsystem.theme.WindColors
import surf.varun.core.domain.WindClass
import surf.varun.core.model.Coordinates

/**
 * Everything the screen asks of the MapLibre map: the spots source and its layers,
 * camera moves, projection for the wind field, taps.
 *
 * Spots are one clustered GeoJSON source drawn with style layers - not a view per
 * marker - so ~820 spots stay cheap and a timeline step only swaps the data.
 */
class MapController(val map: MapLibreMap, private val view: MapView, private val context: Context) {
    private var style: Style? = null
    private var pendingFeatures: List<MapSpotFeature>? = null
    private var markersVisible = true

    /** Apply a basemap and re-add the spot layers on top once it has loaded. */
    fun setStyle(builder: Style.Builder, colors: WindColors, onLoaded: () -> Unit = {}) {
        style = null
        map.setStyle(builder) { loaded ->
            style = loaded
            addSpotLayers(loaded, colors)
            pendingFeatures?.let { setFeatures(it) }
            setMarkersVisible(markersVisible)
            onLoaded()
        }
    }

    fun setFeatures(features: List<MapSpotFeature>) {
        pendingFeatures = features
        style?.getSourceAs<GeoJsonSource>(SOURCE)?.setGeoJson(features.toFeatureCollection())
    }

    fun setMarkersVisible(visible: Boolean) {
        markersVisible = visible
        val value = if (visible) Property.VISIBLE else Property.NONE
        SPOT_LAYERS.forEach { id -> style?.getLayer(id)?.setProperties(visibility(value)) }
    }

    /**
     * Call [listener] each time the map has drawn everything it was given (tiles,
     * re-clustered spots, a layer shown or hidden). Returns the unsubscribe.
     */
    fun onRendered(listener: () -> Unit): () -> Unit {
        val idle = MapView.OnDidBecomeIdleListener { listener() }
        view.addOnDidBecomeIdleListener(idle)
        return { view.removeOnDidBecomeIdleListener(idle) }
    }

    val zoom: Double get() = map.cameraPosition.zoom

    fun project(lat: Double, lon: Double): PointF = map.projection.toScreenLocation(LatLng(lat, lon))

    /** What was tapped: a cluster zooms in, a spot is returned. */
    fun handleTap(point: PointF): Int? {
        val density = context.resources.displayMetrics.density
        val slop = 12 * density
        val box = RectF(point.x - slop, point.y - slop, point.x + slop, point.y + slop)
        val clusters = map.queryRenderedFeatures(box, LAYER_CLUSTERS)
        clusters.firstOrNull()?.let { cluster ->
            val source = style?.getSourceAs<GeoJsonSource>(SOURCE) ?: return null
            val zoomTo = runCatching { source.getClusterExpansionZoom(cluster) }.getOrNull()?.toDouble() ?: (zoom + 2)
            val geometry = cluster.geometry() as? org.maplibre.geojson.Point
            if (geometry != null) {
                map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(geometry.latitude(), geometry.longitude()), zoomTo), 400)
            }
            return null
        }
        val spots = map.queryRenderedFeatures(box, LAYER_DOTS)
        return spots.firstOrNull()?.getNumberProperty(SpotProps.WG_ID)?.toInt()
    }

    fun centreOn(coordinates: Coordinates, zoom: Double = FOCUS_ZOOM) {
        map.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(coordinates.lat, coordinates.lon), zoom), 600)
    }

    /** Fit the camera to [points]; a single point is centred instead. */
    fun zoomBy(delta: Double) {
        map.animateCamera(CameraUpdateFactory.zoomBy(delta), 250)
    }

    fun fit(points: List<Coordinates>, paddingPx: Int) {
        when (points.size) {
            0 -> return
            1 -> centreOn(points.first())
            else -> {
                val bounds = LatLngBounds.Builder().apply { points.forEach { include(LatLng(it.lat, it.lon)) } }.build()
                runCatching { map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, paddingPx), 600) }
            }
        }
    }

    private fun addSpotLayers(style: Style, colors: WindColors) {
        style.addSource(
            GeoJsonSource(
                SOURCE,
                GeoJsonOptions()
                    .withCluster(true)
                    // Same merge distance as the web (map.js CLUSTER_RADIUS_PX); MapLibre zoom z
                    // is Leaflet zoom z+1, so the web's CLUSTER_MAX_ZOOM 10 becomes 9 here
                    .withClusterRadius(CLUSTER_RADIUS)
                    .withClusterMaxZoom(CLUSTER_MAX_ZOOM)
                    .withClusterProperty(
                        SpotProps.MAX_WIND,
                        Expression.max(accumulated(), get(SpotProps.MAX_WIND)),
                        coalesce(get(SpotProps.WIND), literal(-1)),
                    ),
            ),
        )

        // Fill by wind class, with dark text on the bright classes and white on the dark ones
        val clusterFill = step(
            get(SpotProps.MAX_WIND),
            color(colors.calm.toArgb()),
            stop(5, color(colors.light.toArgb())),
            stop(12, color(colors.moderate.toArgb())),
            stop(18, color(colors.strong.toArgb())),
            stop(25.01, color(colors.extreme.toArgb())),
        )
        val clusterText = step(
            get(SpotProps.MAX_WIND),
            color(LIGHT_TEXT),
            stop(5, color(DARK_TEXT)),
            stop(25.01, color(LIGHT_TEXT)),
        )
        val dotFill = match(
            get(SpotProps.WIND_CLASS),
            color(colors.calm.toArgb()),
            stop(WindClass.LIGHT.name.lowercase(), color(colors.light.toArgb())),
            stop(WindClass.MODERATE.name.lowercase(), color(colors.moderate.toArgb())),
            stop(WindClass.STRONG.name.lowercase(), color(colors.strong.toArgb())),
            stop(WindClass.EXTREME.name.lowercase(), color(colors.extreme.toArgb())),
        )
        val dotText = match(
            get(SpotProps.WIND_CLASS),
            color(LIGHT_TEXT),
            stop(WindClass.LIGHT.name.lowercase(), color(DARK_TEXT)),
            stop(WindClass.MODERATE.name.lowercase(), color(DARK_TEXT)),
            stop(WindClass.STRONG.name.lowercase(), color(DARK_TEXT)),
        )
        val isCluster = has("point_count")
        val isSpot = not(isCluster)
        val hasWind = has(SpotProps.WIND)
        val dotRadius = switchCase(hasWind, literal(DOT_RADIUS), literal(EMPTY_DOT_RADIUS))
        // Grows with the number of spots like the web bubble (map.js clusterBubbleSize)
        val clusterRadius = step(
            get("point_count"),
            literal(clusterRadiusDp(0)),
            *CLUSTER_SIZE_STEPS.map { (count, radius) -> stop(count, radius) }.toTypedArray(),
        )

        // A soft shadow under every marker lifts it off the basemap and the wind wash
        style.addLayer(
            CircleLayer(LAYER_SHADOWS, SOURCE).withProperties(
                circleColor(0xFF000000.toInt()),
                circleOpacity(0.35f),
                circleBlur(0.6f),
                circleRadius(switchCase(isCluster, Expression.sum(clusterRadius, literal(3f)), switchCase(hasWind, literal(DOT_RADIUS + 3f), literal(EMPTY_DOT_RADIUS + 2f)))),
                circleTranslate(arrayOf(0f, 1.5f)),
            ),
        )
        style.addLayer(
            CircleLayer(LAYER_CLUSTERS, SOURCE)
                .withFilter(isCluster)
                .withProperties(
                    circleColor(clusterFill),
                    circleRadius(clusterRadius),
                    circleStrokeColor(RIM),
                    circleStrokeWidth(2f),
                ),
        )
        // One bubble with the number of spots inside, as on the web; the fill keeps the wind
        style.addLayer(
            SymbolLayer(LAYER_CLUSTER_LABELS, SOURCE)
                .withFilter(isCluster)
                .withProperties(
                    textField(exprToString(get("point_count"))),
                    textFont(arrayOf(MapStyles.FONT_BOLD)),
                    textSize(13f),
                    textColor(clusterText),
                    textAllowOverlap(true),
                    textIgnorePlacement(true),
                ),
        )

        style.addLayer(
            CircleLayer(LAYER_DOTS, SOURCE)
                .withFilter(isSpot)
                .withProperties(
                    circleColor(dotFill),
                    circleRadius(dotRadius),
                    circleStrokeColor(RIM),
                    circleStrokeWidth(switchCase(hasWind, literal(2f), literal(1.5f))),
                ),
        )
        style.addLayer(
            SymbolLayer(LAYER_LABELS, SOURCE)
                .withFilter(all(isSpot, hasWind))
                .withProperties(
                    textField(exprToString(get(SpotProps.WIND))),
                    textFont(arrayOf(MapStyles.FONT_BOLD)),
                    textSize(12f),
                    textColor(dotText),
                    textAllowOverlap(true),
                    textIgnorePlacement(true),
                ),
        )
    }

    /**
     * Where the markers are on screen right now, as discs in pixels, so the wind
     * field can leave them clear (it is drawn above the map, so it cannot go under
     * them). Main thread only.
     */
    fun markerHoles(width: Float, height: Float): List<MarkerHole> {
        if (!markersVisible) return emptyList()
        val density = context.resources.displayMetrics.density
        val features = map.queryRenderedFeatures(RectF(0f, 0f, width, height), LAYER_CLUSTERS, LAYER_DOTS)
        return features.mapNotNull { feature ->
            val point = feature.geometry() as? org.maplibre.geojson.Point ?: return@mapNotNull null
            val centre = project(point.latitude(), point.longitude())
            val count = feature.getNumberProperty("point_count")?.toInt()
            val wind = feature.hasProperty(SpotProps.WIND)
            val radius = when {
                count != null -> clusterRadiusDp(count) + 3f
                wind -> DOT_RADIUS + 3f
                else -> EMPTY_DOT_RADIUS + 2f
            } * density
            MarkerHole(centre.x, centre.y, radius)
        }
    }

    companion object {
        const val SOURCE = "spots"
        const val LAYER_SHADOWS = "spot-shadows"
        const val LAYER_CLUSTERS = "spot-clusters"
        const val LAYER_CLUSTER_LABELS = "spot-cluster-labels"
        const val LAYER_DOTS = "spot-dots"
        const val LAYER_LABELS = "spot-labels"
        val SPOT_LAYERS = listOf(
            LAYER_SHADOWS, LAYER_CLUSTERS, LAYER_CLUSTER_LABELS, LAYER_DOTS, LAYER_LABELS,
        )

        /** Marker geometry in dp (MapLibre circle radii and translations are in dp). */
        const val DOT_RADIUS = 13f
        const val EMPTY_DOT_RADIUS = 5f

        /** Bubble radius from this many spots on: the web's 32/38/44/50 px diameters. */
        private val CLUSTER_SIZE_STEPS = listOf(5 to 19f, 10 to 22f, 25 to 25f)

        internal fun clusterRadiusDp(count: Int): Float =
            CLUSTER_SIZE_STEPS.lastOrNull { (from, _) -> count >= from }?.second ?: 16f

        private const val RIM = 0xFFFFFFFF.toInt()
        private const val DARK_TEXT = 0xFF0B0F14.toInt()
        private const val LIGHT_TEXT = 0xFFFFFFFF.toInt()

        const val CLUSTER_RADIUS = 60
        const val CLUSTER_MAX_ZOOM = 9
        const val FOCUS_ZOOM = 10.0
    }
}
