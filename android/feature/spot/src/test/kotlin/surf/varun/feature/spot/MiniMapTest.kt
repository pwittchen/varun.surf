package surf.varun.feature.spot

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MiniMapTest {

    @Test
    fun `a snapshot at the size it is shown is decoded whole`() {
        assertThat(sampleSize(1080, 540, 1080, 540)).isEqualTo(1)
    }

    @Test
    fun `a snapshot left by a denser display is sampled down`() {
        // Rendered at 4x, read on a 2x display
        assertThat(sampleSize(1440, 720, 720, 360)).isEqualTo(2)
        assertThat(sampleSize(2880, 1440, 720, 360)).isEqualTo(4)
    }

    @Test
    fun `sampling never goes below the size shown`() {
        assertThat(sampleSize(1400, 700, 720, 360)).isEqualTo(1)
    }

    @Test
    fun `a smaller snapshot is never sampled`() {
        assertThat(sampleSize(360, 180, 1080, 540)).isEqualTo(1)
    }
}
