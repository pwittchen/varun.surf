package surf.varun.core.ui

/**
 * Every basemap the app shows, in one place, so a provider can be swapped (or
 * pointed at tiles self-hosted on varun.surf) without touching the screens.
 *
 * Only free, keyless sources, so the maps keep working without an account or a
 * bill: Esri World Imagery for satellite (the web map uses the same tiles) and the
 * OpenFreeMap "liberty" vector style for the normal map. There is deliberately no
 * dark basemap.
 */
object MapStyles {
    /** Normal map: OpenFreeMap vector style, OSM data. */
    const val STREET = "https://tiles.openfreemap.org/styles/liberty"

    /** Glyphs for map labels; the raster satellite style has no basemap to borrow them from. */
    const val GLYPHS = "https://tiles.openfreemap.org/fonts/{fontstack}/{range}.pbf"

    /** A font OpenFreeMap serves for every style. */
    const val FONT = "Noto Sans Regular"

    /** Its bold cut, for the numbers on map markers. */
    const val FONT_BOLD = "Noto Sans Bold"

    private const val SATELLITE_TILES =
        "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}"

    const val SATELLITE_ATTRIBUTION =
        "Tiles © Esri — Source: Esri, i-cubed, USDA, USGS, AEX, GeoEye, Getmapping, Aerogrid, IGN, IGP, UPR-EGP, and the GIS User Community"

    /** Raster satellite style JSON for MapLibre. */
    val satelliteJson: String = """
        {
          "version": 8,
          "glyphs": "$GLYPHS",
          "sources": {
            "satellite": {
              "type": "raster",
              "tiles": ["$SATELLITE_TILES"],
              "tileSize": 256,
              "maxzoom": 19,
              "attribution": "$SATELLITE_ATTRIBUTION"
            }
          },
          "layers": [{ "id": "satellite", "type": "raster", "source": "satellite" }]
        }
    """.trimIndent()
}
