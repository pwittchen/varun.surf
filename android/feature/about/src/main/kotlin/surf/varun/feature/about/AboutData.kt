package surf.varun.feature.about

/** A link out: what the About page lists as sources and licences. */
data class Link(val name: String, val url: String, val detail: String = url.substringAfter("://").removePrefix("www.").trimEnd('/'))

/** The backend's data sources (StatusController on the server); listed, not pinged (spec 2.4). */
object DataSources {
    val forecasts = listOf(
        Link("Windguru", "https://www.windguru.cz"),
        Link("Windfinder", "https://www.windfinder.com"),
        Link("ICM Meteo", "https://www.meteo.pl"),
    )

    val liveStations = listOf(
        Link("Wiatr Kadyny (Kadyny, Kuźnica, Draga, Rewa, Puck)", "https://www.wiatrkadyny.pl"),
        Link("Kiteriders Podersdorf", "https://www.kiteriders.at/wind/weatherstat_kn.html"),
        Link("SC Podo Podersdorf", "https://scpodo.at/wind.php"),
        Link("Holfuy Góra Żar", "https://holfuy.com/en/weather/1612"),
        Link("Holfuy Svencele", "https://holfuy.com/en/weather/1515"),
        Link("Turawa Południe (Weather Underground)", "https://www.wunderground.com/dashboard/pws/ISZCZE187"),
        Link("Turawa Północ (Airmax)", "https://airmax.pl/kamery/turawa"),
        Link("Mietków (WeeWX)", "https://frog01-21064.wykr.es/weewx/inx.html"),
        Link("Tarifa (Spotfav)", "https://www.spotfav.com"),
        Link("El Médano (Bergfex)", "https://cabezo.bergfex.at/wetterstation/"),
        Link("Le Barcarès (Winds-Up)", "https://m.winds-up.com/spot/58"),
        Link("Prasonisi ProCenter", "https://www.prasonisi.com/index.php/wind.html"),
        Link("Silvaplana (kitesailing.ch)", "https://www.kitesailing.ch"),
    )

    val spots = listOf(
        Link("Kitewetter", "https://www.kitewetter.at/"),
        Link("Nederlandse Kitesurf Vereniging", "https://kitesurfvereniging.nl/spotkaart/"),
        Link("Dziobak", "http://www.dziobak.pl/"),
    )
}

/** Open-source libraries the app ships (spec 5.9: licences). */
object Licences {
    val all = listOf(
        Link("AndroidX & Jetpack Compose", "https://developer.android.com/jetpack/androidx", "Apache License 2.0"),
        Link("Kotlin, kotlinx.coroutines, kotlinx.serialization", "https://github.com/JetBrains/kotlin", "Apache License 2.0"),
        Link("Dagger Hilt", "https://github.com/google/dagger", "Apache License 2.0"),
        Link("OkHttp", "https://github.com/square/okhttp", "Apache License 2.0"),
        Link("Retrofit", "https://github.com/square/retrofit", "Apache License 2.0"),
        Link("Coil", "https://github.com/coil-kt/coil", "Apache License 2.0"),
        Link("MapLibre Native", "https://github.com/maplibre/maplibre-native", "BSD 2-Clause"),
        Link("OpenFreeMap", "https://openfreemap.org", "MIT / ODbL map data"),
        Link("OpenStreetMap data", "https://www.openstreetmap.org/copyright", "ODbL 1.0"),
        Link("Timber", "https://github.com/JakeWharton/timber", "Apache License 2.0"),
        Link("Material Symbols", "https://fonts.google.com/icons", "Apache License 2.0"),
    )
}
