//! One spot's wind hour by hour: `LlmController.renderWindForecast` and its summary.

use serde::Serialize;

use crate::api::dto::Forecast;
use crate::domain::text::java_round;
use crate::domain::windy::DEFAULT_MIN_WIND;

pub const DEFAULT_HOURS: i64 = 72;

/// Rows of the `spot` command's hourly table (`HOURLY_FORECAST_LIMIT`).
pub const HOURLY_FORECAST_LIMIT: usize = 24;

#[derive(Debug, Clone, PartialEq)]
pub struct WindForecast<'a> {
    /// Hours in the window: the requested span clamped to `1..forecast length`.
    pub span: usize,
    /// The floor rows are filtered by, 0 for every hour.
    pub floor: i64,
    pub window: &'a [Forecast],
    /// Hours of the window reaching the floor.
    pub rows: Vec<&'a Forecast>,
    pub summary: Option<WindSummary<'a>>,
}

#[derive(Debug, Clone, PartialEq)]
pub struct WindSummary<'a> {
    /// The windiest hour; the first of them on a tie, as Java's `Stream.max` keeps.
    pub peak: &'a Forecast,
    /// Hours at or above 12 kts.
    pub kiteable: usize,
    /// Unbroken runs of hours at or above 12 kts.
    pub windows: Vec<KiteableWindow>,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct KiteableWindow {
    pub from: String,
    pub to: String,
    pub hours: usize,
    pub peak_wind: i64,
}

/// None when there is nothing to forecast from.
pub fn wind_forecast(hours: &[Forecast], span: Option<i64>, min_wind: Option<i64>) -> Option<WindForecast<'_>> {
    if hours.is_empty() {
        return None;
    }
    let span = span.unwrap_or(DEFAULT_HOURS).clamp(1, hours.len() as i64) as usize;
    let floor = min_wind.map_or(0, |w| w.max(0));
    let window = &hours[..span];
    let rows = window.iter().filter(|f| java_round(f.wind) >= floor).collect();
    Some(WindForecast { span, floor, window, rows, summary: summarize(window) })
}

pub fn summarize(window: &[Forecast]) -> Option<WindSummary<'_>> {
    let mut peak = window.first()?;
    for f in window {
        if f.wind > peak.wind {
            peak = f;
        }
    }
    let kiteable = window.iter().filter(|f| java_round(f.wind) >= DEFAULT_MIN_WIND).count();
    Some(WindSummary { peak, kiteable, windows: kiteable_windows(window) })
}

fn kiteable_windows(window: &[Forecast]) -> Vec<KiteableWindow> {
    let mut windows = Vec::new();
    let mut run: Vec<&Forecast> = Vec::new();
    let flush = |run: &mut Vec<&Forecast>, windows: &mut Vec<KiteableWindow>| {
        if let (Some(first), Some(last)) = (run.first(), run.last()) {
            windows.push(KiteableWindow {
                from: first.date.clone().unwrap_or_default(),
                to: last.date.clone().unwrap_or_default(),
                hours: run.len(),
                peak_wind: run.iter().map(|f| java_round(f.wind)).max().unwrap_or(0),
            });
        }
        run.clear();
    };
    for f in window {
        if java_round(f.wind) >= DEFAULT_MIN_WIND {
            run.push(f);
        } else {
            flush(&mut run, &mut windows);
        }
    }
    flush(&mut run, &mut windows);
    windows
}

#[cfg(test)]
mod tests {
    use super::*;

    fn hour(date: &str, wind: f64, gusts: f64) -> Forecast {
        Forecast { date: Some(date.into()), wind, gusts, direction: Some("W".into()), ..Default::default() }
    }

    fn hours(winds: &[f64]) -> Vec<Forecast> {
        winds.iter().enumerate().map(|(i, &w)| hour(&format!("h{i}"), w, w + 3.0)).collect()
    }

    #[test]
    fn default_span_is_72_hours_capped_by_the_forecast() {
        let all = hours(&[5.0; 120]);
        assert_eq!(wind_forecast(&all, None, None).unwrap().span, 72);
        assert_eq!(wind_forecast(&all, Some(500), None).unwrap().span, 120);
        assert_eq!(wind_forecast(&all, Some(0), None).unwrap().span, 1);
        assert!(wind_forecast(&[], None, None).is_none());
    }

    #[test]
    fn floor_filters_rows_by_rounded_wind() {
        let all = hours(&[11.4, 11.5, 20.0, 3.0]);
        let forecast = wind_forecast(&all, None, Some(12)).unwrap();
        let dates: Vec<_> = forecast.rows.iter().map(|f| f.date.as_deref().unwrap()).collect();
        assert_eq!(dates, ["h1", "h2"]);
        assert_eq!(wind_forecast(&all, None, Some(-3)).unwrap().floor, 0);
        assert_eq!(wind_forecast(&all, None, None).unwrap().rows.len(), 4);
    }

    #[test]
    fn summary_names_the_first_windiest_hour_and_counts_kiteable_hours() {
        let all = hours(&[8.0, 15.0, 15.0, 12.0, 4.0, 13.0]);
        let summary = summarize(&all).unwrap();
        assert_eq!(summary.peak.date.as_deref(), Some("h1"));
        assert_eq!(summary.kiteable, 4);
        assert_eq!(
            summary.windows,
            vec![
                KiteableWindow { from: "h1".into(), to: "h3".into(), hours: 3, peak_wind: 15 },
                KiteableWindow { from: "h5".into(), to: "h5".into(), hours: 1, peak_wind: 13 },
            ]
        );
    }

    #[test]
    fn summary_is_computed_over_the_window_not_the_rows() {
        let all = hours(&[20.0, 5.0]);
        let forecast = wind_forecast(&all, None, Some(30)).unwrap();
        assert!(forecast.rows.is_empty());
        assert_eq!(forecast.summary.unwrap().kiteable, 1);
    }
}
