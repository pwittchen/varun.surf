package surf.varun.core.network

import kotlinx.serialization.json.Json

/** The one Json configuration for the wire: lenient, forgiving, forward compatible. */
val NetworkJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
    isLenient = true
}
