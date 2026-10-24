package surf.varun.feature.map

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import surf.varun.core.model.Direction
import java.time.LocalDateTime

class MapLogicTest {
    @Test
    fun stepZeroIsNowWithTheLiveReading() {
        val live = conditionsAtStep(TestData.jastarnia, 0, TestData.timeline, TestData.now)!!
        assertThat(live.isLive).isTrue()
        assertThat(live.wind).isEqualTo(18.0)
        // Without a station, now is the forecast (here today's daily entry)
        val tarifa = conditionsAtStep(TestData.tarifa, 0, TestData.timeline, TestData.now)!!
        assertThat(tarifa.isLive).isFalse()
        assertThat(tarifa.wind).isEqualTo(25.0)
    }

    @Test
    fun laterStepsReadTheGrid() {
        val first = conditionsAtStep(TestData.jastarnia, 1, TestData.timeline, TestData.now)!!
        assertThat(first.wind).isEqualTo(20.0)
        assertThat(first.gusts).isEqualTo(26.0)
        assertThat(first.direction).isEqualTo(Direction.SW)
        assertThat(first.forecastTime).isEqualTo(LocalDateTime.of(2026, 10, 24, 22, 0))
        // An hole in the grid is no data, not calm
        assertThat(conditionsAtStep(TestData.jastarnia, 2, TestData.timeline, TestData.now)).isNull()
        // Wind without gusts is still drawable
        assertThat(conditionsAtStep(TestData.jastarnia, 3, TestData.timeline, TestData.now)!!.gusts.isNaN()).isTrue()
        assertThat(conditionsAtStep(TestData.lonely, 1, TestData.timeline, TestData.now)).isNull()
        assertThat(conditionsAtStep(TestData.jastarnia, 1, null, TestData.now)).isNull()
    }

    @Test
    fun featuresSkipSpotsWithoutCoordinatesAndKeepThoseWithoutWind() {
        val features = MapFeatures.build(TestData.spots, 2, TestData.timeline, TestData.now)
        assertThat(features.map { it.wgId }).containsExactly(500760, 1, 3)
        val jastarnia = features.first { it.wgId == 500760 }
        assertThat(jastarnia.wind).isNull()
        assertThat(jastarnia.windClass).isEqualTo(MapFeatures.NO_DATA)
        val tarifa = features.first { it.wgId == 1 }
        assertThat(tarifa.wind).isEqualTo(31)
        assertThat(tarifa.dirIndex).isEqualTo(Direction.NE.ordinal)
        assertThat(tarifa.windClass).isEqualTo("extreme")
    }

    @Test
    fun featuresAtNowCarryTheLiveFlag() {
        val features = MapFeatures.build(TestData.spots, 0, TestData.timeline, TestData.now)
        assertThat(features.first { it.wgId == 500760 }.isLive).isTrue()
        assertThat(features.first { it.wgId == 500760 }.windClass).isEqualTo("strong")
        assertThat(features.first { it.wgId == 3 }.wind).isNull()
    }

    @Test
    fun windClassKeysFollowTheMapScale() {
        assertThat(MapFeatures.windClassKey(3.0)).isEqualTo("calm")
        assertThat(MapFeatures.windClassKey(8.0)).isEqualTo("light")
        assertThat(MapFeatures.windClassKey(12.0)).isEqualTo("moderate")
        assertThat(MapFeatures.windClassKey(null)).isEqualTo("nodata")
        assertThat(MapFeatures.windClassKey(Double.NaN)).isEqualTo("nodata")
    }

    @Test
    fun filtersCombine() {
        val spots = TestData.spots
        assertThat(MapFilters().apply(spots, emptySet())).hasSize(4)
        assertThat(MapFilters(country = "Poland").apply(spots, emptySet()).map { it.wgId }).containsExactly(500760, 2)
        assertThat(MapFilters(liveOnly = true).apply(spots, emptySet()).map { it.wgId }).containsExactly(500760)
        assertThat(MapFilters(favouritesOnly = true).apply(spots, setOf(1, 3)).map { it.wgId }).containsExactly(1, 3)
        assertThat(MapFilters(country = "Spain", favouritesOnly = true).apply(spots, setOf(3))).isEmpty()
        assertThat(MapFilters().active).isFalse()
        assertThat(MapFilters(liveOnly = true).active).isTrue()
    }

    @Test
    fun timelineSteps() {
        val steps = TimelineSteps(TestData.timeline.hours)
        assertThat(steps.count).isEqualTo(4)
        assertThat(steps.lastStep).isEqualTo(3)
        assertThat(steps.timeAt(0)).isNull()
        assertThat(steps.timeAt(1)).isEqualTo(LocalDateTime.of(2026, 10, 24, 22, 0))
        assertThat(steps.isMidnight(3)).isTrue()
        assertThat(steps.isMidnight(2)).isFalse()
        assertThat(steps.dayStarts).containsExactly(3)
        assertThat(steps.clamp(99)).isEqualTo(3)
        assertThat(steps.clamp(-1)).isEqualTo(0)
        assertThat(TimelineSteps(emptyList()).clamp(5)).isEqualTo(0)
    }
}
