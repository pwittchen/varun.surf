package surf.varun.core.network

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.builtins.ListSerializer
import org.junit.Test
import surf.varun.core.model.DayLabel
import surf.varun.core.model.Direction
import surf.varun.core.network.dto.HourlyForecastDto
import surf.varun.core.network.dto.SpotDto
import surf.varun.core.network.dto.WindTimelineDto
import surf.varun.core.network.dto.toModel
import surf.varun.core.testing.Fixtures
import java.time.LocalDateTime

class DtoParsingTest {
    private val base = "https://varun.surf/"

    @Test
    fun parsesRealSpotsList() {
        val spots = NetworkJson.decodeFromString(ListSerializer(SpotDto.serializer()), Fixtures.spots)
            .mapNotNull { it.toModel(base) }
        assertThat(spots).hasSize(60)
        assertThat(spots.count { it.live != null }).isEqualTo(10)
        val first = spots.first { it.live == null }
        assertThat(first.hourly).isEmpty()
        assertThat(first.daily.first().day).isEqualTo(DayLabel.Today)
        // "icmUrl": null and "webcamUrl": "" both mean absent
        assertThat(spots.first { it.name == "Lignano Pineta" }.links.icm).isNull()
        assertThat(spots.first { it.name == "Lignano Pineta" }.links.webcam).isNull()
    }

    @Test
    fun parsesRealSingleSpot() {
        val spot = NetworkJson.decodeFromString(SpotDto.serializer(), Fixtures.spot).toModel(base)!!
        assertThat(spot.wgId).isEqualTo(Fixtures.SPOT_ID)
        assertThat(spot.name).isEqualTo("Jastarnia")
        assertThat(spot.photoUrl).startsWith("https://varun.surf/images/spots/500760.jpg?v=")
        assertThat(spot.live!!.observedAt).isEqualTo(LocalDateTime.of(2026, 10, 24, 20, 8, 52))
        assertThat(spot.live!!.direction).isEqualTo(Direction.S)
        assertThat(spot.liveHistory.size).isGreaterThan(100)
        assertThat(spot.hourly.first().time).isEqualTo(LocalDateTime.of(2026, 10, 24, 8, 0))
        assertThat(spot.hasWaveData).isTrue()
        assertThat(spot.availableModels.map { it.key }).containsAtLeast("gfs", "ifs")
        assertThat(spot.infoPl!!.type).isEqualTo("Laguna, płaska woda")
        assertThat(spot.coordinates!!.lat).isWithin(0.01).of(54.69)
    }

    @Test
    fun parsesHourlyForecastAndWindTimeline() {
        val hourly = NetworkJson.decodeFromString(HourlyForecastDto.serializer(), Fixtures.forecast).toModel()
        assertThat(hourly).isNotEmpty()
        assertThat(hourly.all { it.time != null }).isTrue()

        val wind = NetworkJson.decodeFromString(WindTimelineDto.serializer(), Fixtures.wind).toModel()
        assertThat(wind.size).isEqualTo(120)
        assertThat(wind.hours.first()).isEqualTo(LocalDateTime.of(2026, 10, 24, 18, 0))
        val series = wind.spots.getValue(49152)
        assertThat(series.wind).hasSize(120)
        assertThat(series.direction.first()).isNotNull()
    }

    @Test
    fun toleratesMissingExtraAndEmptyFields() {
        val json = """
            [
              {"wgId": 1, "name": "Minimal"},
              {"wgId": 2, "name": "Extra", "brandNewField": {"x": 1}, "windguruUrl": "", "windguruFallbackUrl": "https://wg/2",
               "currentConditions": {"date": "nonsense", "wind": 12.6, "gusts": 15, "direction": "XX"},
               "forecast": [{"date": "Day 4", "wind": 10}], "coordinates": {"lat": 1.0}},
              {"name": "No id"},
              {"wgId": 3, "name": ""}
            ]
        """.trimIndent()
        val spots = NetworkJson.decodeFromString(ListSerializer(SpotDto.serializer()), json).mapNotNull { it.toModel(base) }
        assertThat(spots.map { it.wgId }).containsExactly(1, 2)
        val extra = spots[1]
        assertThat(extra.links.windguru).isEqualTo("https://wg/2")
        assertThat(extra.live!!.wind).isEqualTo(13)
        assertThat(extra.live!!.observedAt).isNull()
        assertThat(extra.live!!.direction).isNull()
        assertThat(extra.daily.single().day).isEqualTo(DayLabel.Offset(3))
        assertThat(extra.daily.single().gusts).isEqualTo(10.0)
        assertThat(extra.coordinates).isNull()
    }
}
