package surf.varun.feature.map

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SpotGeoJsonTest {
    @Test
    fun featuresCarryTheLayerProperties() {
        val features = MapFeatures.build(TestData.spots, 1, TestData.timeline, TestData.now)
        val collection = features.toFeatureCollection()
        val jastarnia = collection.features()!!.first { it.id() == "500760" }
        assertThat(jastarnia.getNumberProperty(SpotProps.WIND).toInt()).isEqualTo(20)
        assertThat(jastarnia.getNumberProperty(SpotProps.DIR_INDEX).toInt()).isEqualTo(5)
        assertThat(jastarnia.getStringProperty(SpotProps.WIND_CLASS)).isEqualTo("strong")
        // A spot with nothing for the hour has no wind property, so it gets no number and no arrow
        val lonely = collection.features()!!.first { it.id() == "3" }
        assertThat(lonely.hasProperty(SpotProps.WIND)).isFalse()
        assertThat(lonely.hasProperty(SpotProps.DIR_INDEX)).isFalse()
        assertThat(collection.toJson()).contains("\"coordinates\":[18.66,54.69]")
    }
}
