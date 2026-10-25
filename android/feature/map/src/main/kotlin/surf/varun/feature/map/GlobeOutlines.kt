package surf.varun.feature.map

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.DataInputStream
import java.io.InputStream
import kotlin.math.cos
import kotlin.math.sin

/**
 * Land and country borders for the globe, as Earth-fixed unit vectors (xyz
 * interleaved) ready to be rotated into view.
 *
 * The web loads world-atlas from unpkg on first use; the app ships the same
 * outlines in its assets instead (converted by `tools/globe_outlines.py`), so the
 * globe works offline. The 1:50m set is only read once the globe is zoomed in.
 */
internal class GlobeOutlines(val land: List<FloatArray>, val borders: List<FloatArray>) {
    /** Bounding caps of every ring and line ([HorizonClipper.caps]), worked out once on load. */
    val landCaps: FloatArray = HorizonClipper.caps(land)
    val borderCaps: FloatArray = HorizonClipper.caps(borders)

    companion object {
        const val COARSE = "globe/outlines-110m.bin"
        const val DETAILED = "globe/outlines-50m.bin"

        private val cache = HashMap<String, GlobeOutlines>()
        private val lock = Mutex()

        /** Read once per process; null when the asset can't be read (the globe still works without coastlines). */
        suspend fun load(context: Context, asset: String): GlobeOutlines? = lock.withLock {
            cache[asset] ?: withContext(Dispatchers.IO) {
                runCatching { context.assets.open(asset).use(::read) }
                    .onFailure { Timber.w(it, "Globe outlines %s not loaded", asset) }
                    .getOrNull()
            }?.also { cache[asset] = it }
        }

        fun read(input: InputStream): GlobeOutlines {
            val data = DataInputStream(input.buffered())
            return GlobeOutlines(land = readLines(data), borders = readLines(data))
        }

        private fun readLines(data: DataInputStream): List<FloatArray> = List(data.readInt()) {
            val count = data.readInt()
            FloatArray(count * 3).also { out ->
                for (i in 0 until count) {
                    val lon = data.readShort() * LON_SCALE * DEG
                    val lat = data.readShort() * LAT_SCALE * DEG
                    out[i * 3] = (cos(lat) * cos(lon)).toFloat()
                    out[i * 3 + 1] = (cos(lat) * sin(lon)).toFloat()
                    out[i * 3 + 2] = sin(lat).toFloat()
                }
            }
        }

        private const val LON_SCALE = 180.0 / 32767
        private const val LAT_SCALE = 90.0 / 32767
    }
}
