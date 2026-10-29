//! The backend's response shapes. Every field is optional on the client and unknown
//! fields are ignored, so the backend can evolve without a CLI release (SPEC 6.4).

use serde::{Deserialize, Deserializer, Serialize};

/// `null` read as the type's default: the backend serialises some empty lists as null.
fn nullable<'de, D, T>(deserializer: D) -> Result<T, D::Error>
where
    D: Deserializer<'de>,
    T: Default + Deserialize<'de>,
{
    Ok(Option::<T>::deserialize(deserializer)?.unwrap_or_default())
}

#[derive(Debug, Clone, Default, Deserialize, Serialize)]
#[serde(default, rename_all = "camelCase")]
pub struct Spot {
    pub wg_id: i64,
    pub name: String,
    pub country: String,
    pub windguru_url: Option<String>,
    pub windfinder_url: Option<String>,
    pub icm_url: Option<String>,
    pub webcam_url: Option<String>,
    pub location_url: Option<String>,
    pub current_conditions: Option<CurrentConditions>,
    #[serde(deserialize_with = "nullable")]
    pub forecast: Vec<Forecast>,
    #[serde(deserialize_with = "nullable")]
    pub forecast_hourly: Vec<Forecast>,
    pub coordinates: Option<Coordinates>,
    pub spot_info: Option<SpotInfo>,
    #[serde(rename = "spotInfoPL")]
    pub spot_info_pl: Option<SpotInfo>,
    #[serde(deserialize_with = "nullable")]
    pub available_models: Vec<AvailableModel>,
    pub last_updated: Option<String>,
}

impl Spot {
    /// Live conditions worth showing: the backend sends `null` for a spot without a
    /// station, but an all-zero reading means the same thing.
    pub fn live(&self) -> Option<&CurrentConditions> {
        self.current_conditions.as_ref().filter(|c| !c.is_empty())
    }

    /// The `SpotInfo` in the reader's language, falling back to English when the
    /// translation is missing.
    pub fn info(&self, lang: crate::config::Lang) -> Option<&SpotInfo> {
        match lang {
            crate::config::Lang::Pl => self.spot_info_pl.as_ref().or(self.spot_info.as_ref()),
            crate::config::Lang::En => self.spot_info.as_ref(),
        }
    }
}

#[derive(Debug, Clone, Default, Deserialize, Serialize, PartialEq)]
#[serde(default, rename_all = "camelCase")]
pub struct CurrentConditions {
    pub date: Option<String>,
    pub wind: i64,
    pub gusts: i64,
    pub direction: Option<String>,
    pub temp: i64,
}

impl CurrentConditions {
    /// Same rule as the backend's `CurrentConditionsEmptyFilter`.
    pub fn is_empty(&self) -> bool {
        self.date.is_none() && self.direction.is_none() && self.wind == 0 && self.gusts == 0 && self.temp == 0
    }
}

#[derive(Debug, Clone, Default, Deserialize, Serialize, PartialEq)]
#[serde(default, rename_all = "camelCase")]
pub struct Forecast {
    pub date: Option<String>,
    pub wind: f64,
    pub gusts: f64,
    pub direction: Option<String>,
    pub temp: f64,
    pub precipitation: f64,
    pub cloud_cover_percent: f64,
    pub pressure_hpa: f64,
    pub wave: Option<f64>,
    pub wave_period: Option<f64>,
    pub wave_direction: Option<String>,
}

#[derive(Debug, Clone, Default, Deserialize, Serialize)]
#[serde(default, rename_all = "camelCase")]
pub struct SpotInfo {
    #[serde(rename = "type")]
    pub kind: Option<String>,
    pub best_wind: Option<String>,
    pub water_temp: Option<String>,
    pub experience: Option<String>,
    pub launch: Option<String>,
    pub hazards: Option<String>,
    pub season: Option<String>,
    pub description: Option<String>,
}

#[derive(Debug, Clone, Copy, Default, Deserialize, Serialize)]
#[serde(default)]
pub struct Coordinates {
    pub lat: f64,
    pub lon: f64,
}

#[derive(Debug, Clone, Default, Deserialize, Serialize)]
#[serde(default)]
pub struct AvailableModel {
    pub key: String,
    pub name: String,
}

/// `GET /api/v1/forecast/{wgId}`: one spot's hourly forecast on the shared grid.
#[derive(Debug, Clone, Default, Deserialize, Serialize)]
#[serde(default, rename_all = "camelCase")]
pub struct HourlyForecast {
    pub wg_id: i64,
    #[serde(deserialize_with = "nullable")]
    pub hours: Vec<Forecast>,
}

/// `GET /api/v1/wind`: every spot's wind on one shared grid.
#[derive(Debug, Clone, Default, Deserialize, Serialize)]
#[serde(default)]
pub struct WindTimeline {
    #[serde(deserialize_with = "nullable")]
    pub hours: Vec<String>,
    #[serde(deserialize_with = "nullable")]
    pub spots: Vec<SpotWind>,
}

#[derive(Debug, Clone, Default, Deserialize, Serialize)]
#[serde(default, rename_all = "camelCase")]
pub struct SpotWind {
    pub wg_id: i64,
    #[serde(deserialize_with = "nullable")]
    pub wind: Vec<Option<i64>>,
    #[serde(deserialize_with = "nullable")]
    pub gusts: Vec<Option<i64>>,
    #[serde(deserialize_with = "nullable")]
    pub direction: Vec<Option<i64>>,
}

/// Cardinal directions in the order `SpotWind.direction` indexes them.
pub const DIRECTIONS: [&str; 8] = ["N", "NE", "E", "SE", "S", "SW", "W", "NW"];

/// `GET /api/v1/status`.
#[derive(Debug, Clone, Default, Deserialize, Serialize)]
#[serde(default, rename_all = "camelCase")]
pub struct Status {
    pub status: Option<String>,
    pub version: Option<String>,
    pub uptime: Option<String>,
    pub uptime_seconds: Option<i64>,
    pub start_time: Option<String>,
    pub spots_count: i64,
    pub countries_count: i64,
    pub live_stations: i64,
}

/// `GET /api/v1/status/forecast`: how far the forecast sweep got.
#[derive(Debug, Clone, Default, Deserialize, Serialize)]
#[serde(default, rename_all = "camelCase")]
pub struct ForecastFetchProgress {
    pub in_progress: bool,
    pub total: i64,
    pub completed: i64,
    pub fetched: i64,
    pub empty: i64,
    pub failed: i64,
    pub cached: i64,
    pub started_at: i64,
    pub finished_at: i64,
    pub elapsed_ms: i64,
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn tolerates_missing_and_unknown_fields() {
        let spot: Spot = serde_json::from_str(r#"{"name":"Jastarnia","wgId":500760,"brandNew":[1,2]}"#).unwrap();
        assert_eq!(spot.wg_id, 500760);
        assert!(spot.forecast.is_empty());
        assert!(spot.live().is_none());
    }

    #[test]
    fn tolerates_nulls_in_lists_and_numbers() {
        let spot: Spot =
            serde_json::from_str(r#"{"name":"X","forecast":null,"availableModels":null,"currentConditions":null}"#)
                .unwrap();
        assert_eq!(spot.name, "X");
        assert!(spot.forecast.is_empty());
        assert!(spot.available_models.is_empty());
    }

    #[test]
    fn all_zero_conditions_are_empty() {
        let c = CurrentConditions::default();
        assert!(c.is_empty());
        let c = CurrentConditions { wind: 3, ..Default::default() };
        assert!(!c.is_empty());
    }
}
