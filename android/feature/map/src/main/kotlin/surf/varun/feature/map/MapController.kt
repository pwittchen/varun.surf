package surf.varun.feature.map

import surf.varun.core.ui.MapStyles
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import androidx.compose.ui.graphics.toArgb
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.expressions.Expression.accumulated
import org.maplibre.android.style.expressions.Expression.all
import org.maplibre.android.style.expressions.Expression.coalesce
import org.maplibre.android.style.expressions.Expression.color
import org.maplibre.android.style.expressions.Expression.concat
import org.maplibre.android.style.expressions.Expression.get
import org.maplibre.android.style.expressions.Expression.gte
import org.maplibre.android.style.expressions.Expression.has
import org.maplibre.android.style.expressions.Expression.literal
import org.maplibre.android.style.expressions.Expression.match
import org.maplibre.android.style.expressions.Expression.not
import org.maplibre.android.style.expressions.Expression.product
import org.maplibre.android.style.expressions.Expression.step
import org.maplibre.android.style.expressions.Expression.stop
import org.maplibre.android.style.expressions.Expression.switchCase
import org.maplibre.android.style.expressions.Expression.toString as exprToString
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.iconIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.iconImage
import org.maplibre.android.style.layers.PropertyFactory.iconOffset
import org.maplibre.android.style.layers.PropertyFactory.iconRotate
import org.maplibre.android.style.layers.PropertyFactory.iconRotationAlignment
import org.maplibre.android.style.layers.PropertyFactory.textAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.textColor
import org.maplibre.android.style.layers.PropertyFactory.textField
import org.maplibre.android.style.layers.PropertyFactory.textFont
import org.maplibre.android.style.layers.PropertyFactory.textHaloColor
import org.maplibre.android.style.layers.PropertyFactory.textHaloWidth
import org.maplibre.android.style.layers.PropertyFactory.textIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.textLineHeight
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
class MapController(val map: MapLibreMap, private val context: Context) {
    private var style: Style? = null
    private var pendingFeatures: List<MapSpotFeature>? = null
    private var markersVisible = true

    /** Apply a basemap and re-add the spot layers on top once it has loaded. */
    fun setStyle(builder: Style.Builder, colors: WindColors, darkBasemap: Boolean, onLoaded: () -> Unit = {}) {
        style = null
        map.setStyle(builder) { loaded ->
            style = loaded
            addSpotLayers(loaded, colors, darkBasemap)
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

    private fun addSpotLayers(style: Style, colors: WindColors, darkBasemap: Boolean) {
        style.addImage(ARROW_IMAGE, arrowBitmap(context, darkBasemap))
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

        val outline = if (darkBasemap) 0xFF08090A.toInt() else 0xFFFFFFFF.toInt()
        val clusterFill = step(
            get(SpotProps.MAX_WIND),
            color(colors.calm.toArgb()),
            stop(5, color(colors.light.toArgb())),
            stop(12, color(colors.moderate.toArgb())),
            stop(18, color(colors.strong.toArgb())),
            stop(25.01, color(colors.extreme.toArgb())),
        )
        style.addLayer(
            CircleLayer(LAYER_CLUSTERS, SOURCE)
                .withFilter(has("point_count"))
                .withProperties(
                    circleColor(clusterFill),
                    circleRadius(step(get("point_count"), literal(16f), stop(5, 19f), stop(10, 22f), stop(25, 25f))),
                    circleStrokeColor(outline),
                    circleStrokeWidth(2f),
                ),
        )
        style.addLayer(
            SymbolLayer(LAYER_CLUSTER_LABELS, SOURCE)
                .withFilter(has("point_count"))
                .withProperties(
                    // Count, with the strongest wind of the cluster underneath
                    textField(
                        switchCase(
                            gte(get(SpotProps.MAX_WIND), literal(0)),
                            concat(exprToString(get("point_count")), literal("\n▲"), exprToString(get(SpotProps.MAX_WIND))),
                            exprToString(get("point_count")),
                        ),
                    ),
                    textFont(arrayOf(MapStyles.FONT)),
                    textSize(11f),
                    textLineHeight(1.0f),
                    // White with a dark halo reads on every class colour and on both basemaps
                    textColor(0xFFFFFFFF.toInt()),
                    textHaloColor(0xCC000000.toInt()),
                    textHaloWidth(1.2f),
                    textAllowOverlap(true),
                    textIgnorePlacement(true),
                ),
        )

        val isSpot = not(has("point_count"))
        style.addLayer(
            SymbolLayer(LAYER_ARROWS, SOURCE)
                .withFilter(all(isSpot, has(SpotProps.DIR_INDEX)))
                .withProperties(
                    iconImage(ARROW_IMAGE),
                    // The arrow sits on the downwind side of the dot and points where the wind
                    // blows to (a north wind points down), like the app's WindArrow
                    iconRotate(product(get(SpotProps.DIR_INDEX), literal(45f))),
                    iconRotationAlignment(Property.ICON_ROTATION_ALIGNMENT_MAP),
                    iconOffset(arrayOf(0f, ARROW_OFFSET)),
                    iconAllowOverlap(true),
                    iconIgnorePlacement(true),
                ),
        )
        style.addLayer(
            CircleLayer(LAYER_DOTS, SOURCE)
                .withFilter(isSpot)
                .withProperties(
                    circleColor(
                        match(
                            get(SpotProps.WIND_CLASS),
                            color(colors.calm.copy(alpha = 0.6f).toArgb()),
                            stop(MapFeatures.windClassKey(0.0), color(colors.calm.toArgb())),
                            stop(WindClass.LIGHT.name.lowercase(), color(colors.light.toArgb())),
                            stop(WindClass.MODERATE.name.lowercase(), color(colors.moderate.toArgb())),
                            stop(WindClass.STRONG.name.lowercase(), color(colors.strong.toArgb())),
                            stop(WindClass.EXTREME.name.lowercase(), color(colors.extreme.toArgb())),
                        ),
                    ),
                    circleRadius(switchCase(has(SpotProps.WIND), literal(12f), literal(6f))),
                    circleStrokeColor(outline),
                    circleStrokeWidth(1.5f),
                ),
        )
        style.addLayer(
            SymbolLayer(LAYER_LABELS, SOURCE)
                .withFilter(all(isSpot, has(SpotProps.WIND)))
                .withProperties(
                    textField(exprToString(get(SpotProps.WIND))),
                    textFont(arrayOf(MapStyles.FONT)),
                    textSize(12f),
                    textColor(0xFFFFFFFF.toInt()),
                    textHaloColor(0xCC000000.toInt()),
                    textHaloWidth(1.2f),
                    textAllowOverlap(true),
                    textIgnorePlacement(true),
                ),
        )
    }

    companion object {
        const val SOURCE = "spots"
        const val LAYER_CLUSTERS = "spot-clusters"
        const val LAYER_CLUSTER_LABELS = "spot-cluster-labels"
        const val LAYER_ARROWS = "spot-arrows"
        const val LAYER_DOTS = "spot-dots"
        const val LAYER_LABELS = "spot-labels"
        val SPOT_LAYERS = listOf(LAYER_CLUSTERS, LAYER_CLUSTER_LABELS, LAYER_ARROWS, LAYER_DOTS, LAYER_LABELS)

        const val ARROW_IMAGE = "wind-arrow"
        const val CLUSTER_RADIUS = 60
        const val CLUSTER_MAX_ZOOM = 9
        const val FOCUS_ZOOM = 10.0

        /** Icon offset is in icon pixels and rotates with the icon: below the dot at 0° (a north wind). */
        private const val ARROW_OFFSET = 20f

        /** A downward arrow, white with a dark rim so it reads on any basemap. */
        private fun arrowBitmap(context: Context, darkBasemap: Boolean): Bitmap {
            val density = context.resources.displayMetrics.density
            val size = (14 * density).toInt().coerceAtLeast(8)
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val w = size.toFloat()
            val path = Path().apply {
                moveTo(w / 2, w * 0.94f)
                lineTo(w * 0.86f, w * 0.46f)
                lineTo(w * 0.60f, w * 0.52f)
                lineTo(w * 0.60f, w * 0.06f)
                lineTo(w * 0.40f, w * 0.06f)
                lineTo(w * 0.40f, w * 0.52f)
                lineTo(w * 0.14f, w * 0.46f)
                close()
            }
            canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = if (darkBasemap) 0xFFF2F3F4.toInt() else 0xFF1F2933.toInt() })
            canvas.drawPath(
                path,
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = density
                    color = if (darkBasemap) 0xCC000000.toInt() else 0xCCFFFFFF.toInt()
                },
            )
            return bitmap
        }
    }
}
