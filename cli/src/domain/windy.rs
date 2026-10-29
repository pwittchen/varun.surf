//! The windy-spots scan of `LlmController.renderWindySpots` / `toWindyWindow`.

use std::collections::HashMap;

use serde::Serialize;

use crate::api::dto::{DIRECTIONS, Spot, SpotWind, WindTimeline};
use crate::domain::text::to_slug;

pub const DEFAULT_MIN_WIND: i64 = 12;
pub const DEFAULT_HOURS: i64 = 24;
pub const DEFAULT_LIMIT: i64 = 20;
pub const MAX_LIMIT: i64 = 100;

/// The parameters as the caller gave them, with the defaults and clamps applied.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct WindyQuery {
    pub floor: i64,
    pub hours: i64,
    pub limit: usize,
}

impl WindyQuery {
    pub fn new(min_wind: Option<i64>, hours: Option<i64>, limit: Option<i64>) -> WindyQuery {
        WindyQuery {
            floor: min_wind.map_or(DEFAULT_MIN_WIND, |w| w.max(1)),
            hours: hours.map_or(DEFAULT_HOURS, |h| h.max(1)),
            limit: limit.unwrap_or(DEFAULT_LIMIT).clamp(1, MAX_LIMIT) as usize,
        }
    }
}

/// One spot's answer to "will it blow": first and last hour reaching the floor
/// (bracketing every matching hour, not one unbroken run), how many hours do, and
/// the peak.
#[derive(Debug, Clone, PartialEq, Eq, Serialize)]
#[serde(rename_all = "camelCase")]
pub struct WindyWindow {
    pub name: String,
    pub country: String,
    pub wg_id: i64,
    #[serde(skip)]
    pub first_hour: usize,
    #[serde(skip)]
    pub last_hour: usize,
    pub from: String,
    pub to: String,
    pub windy_hours: i64,
    pub peak_wind: i64,
    pub peak_gusts: i64,
    pub peak_direction: String,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct WindyResult {
    /// Hours actually scanned: the requested span capped by the grid.
    pub span: usize,
    pub grid_from: String,
    pub grid_to: String,
    pub scanned: usize,
    /// Every spot reaching the floor, strongest first.
    pub matches: Vec<WindyWindow>,
}

/// `LlmController.spotsByWgId`: the spots to consider, optionally in one country
/// (by name or slug). With a country given, an empty map means no such country.
pub fn spots_by_wg_id<'a>(spots: &'a [Spot], country: Option<&str>) -> HashMap<i64, &'a Spot> {
    let slug = country.map(str::trim).filter(|c| !c.is_empty()).map(to_slug);
    spots
        .iter()
        .filter(|s| slug.as_ref().is_none_or(|slug| &to_slug(&s.country) == slug))
        .map(|s| (s.wg_id, s))
        .collect()
}

/// None when no forecast is on the grid yet.
pub fn scan(timeline: &WindTimeline, spots: &HashMap<i64, &Spot>, query: WindyQuery) -> Option<WindyResult> {
    if timeline.hours.is_empty() {
        return None;
    }
    let span = (query.hours as usize).min(timeline.hours.len());
    let mut scanned = 0;
    let mut matches = Vec::new();
    for series in &timeline.spots {
        let Some(spot) = spots.get(&series.wg_id) else { continue };
        scanned += 1;
        if let Some(window) = to_windy_window(spot, series, span, query.floor, &timeline.hours) {
            matches.push(window);
        }
    }
    matches.sort_by(|a, b| b.peak_wind.cmp(&a.peak_wind).then_with(|| a.name.cmp(&b.name)));
    Some(WindyResult {
        span,
        grid_from: timeline.hours[0].clone(),
        grid_to: timeline.hours[span - 1].clone(),
        scanned,
        matches,
    })
}

fn to_windy_window(spot: &Spot, series: &SpotWind, span: usize, floor: i64, hours: &[String]) -> Option<WindyWindow> {
    let mut first = None;
    let mut last = 0;
    let mut windy_hours = 0;
    let mut peak_wind = 0;
    let mut peak_gusts = 0;
    let mut peak_hour = None;

    for hour in 0..span.min(series.wind.len()) {
        let Some(speed) = series.wind[hour] else { continue };
        if speed < floor {
            continue;
        }
        first.get_or_insert(hour);
        last = hour;
        windy_hours += 1;
        if speed > peak_wind {
            peak_wind = speed;
            peak_hour = Some(hour);
            peak_gusts = series.gusts.get(hour).copied().flatten().unwrap_or(speed);
        }
    }

    let first = first?;
    let peak_direction = peak_hour
        .and_then(|h| series.direction.get(h).copied().flatten())
        .and_then(|i| usize::try_from(i).ok())
        .and_then(|i| DIRECTIONS.get(i))
        .map_or(String::new(), |d| d.to_string());
    Some(WindyWindow {
        name: spot.name.clone(),
        country: spot.country.clone(),
        wg_id: series.wg_id,
        first_hour: first,
        last_hour: last,
        from: hours.get(first).cloned().unwrap_or_default(),
        to: hours.get(last).cloned().unwrap_or_default(),
        windy_hours,
        peak_wind,
        peak_gusts,
        peak_direction,
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    fn spot(wg_id: i64, name: &str, country: &str) -> Spot {
        Spot { wg_id, name: name.into(), country: country.into(), ..Default::default() }
    }

    fn series(wg_id: i64, wind: &[Option<i64>], gusts: &[Option<i64>], direction: &[Option<i64>]) -> SpotWind {
        SpotWind { wg_id, wind: wind.to_vec(), gusts: gusts.to_vec(), direction: direction.to_vec() }
    }

    fn grid(n: usize) -> Vec<String> {
        (0..n).map(|h| format!("h{h}")).collect()
    }

    #[test]
    fn defaults_and_clamps() {
        assert_eq!(WindyQuery::new(None, None, None), WindyQuery { floor: 12, hours: 24, limit: 20 });
        assert_eq!(WindyQuery::new(Some(0), Some(-5), Some(500)), WindyQuery { floor: 1, hours: 1, limit: 100 });
        assert_eq!(WindyQuery::new(Some(20), Some(48), Some(0)).limit, 1);
    }

    #[test]
    fn window_brackets_gaps_and_skips_nulls() {
        let spots = [spot(1, "A", "Poland")];
        let timeline = WindTimeline {
            hours: grid(6),
            spots: vec![series(
                1,
                &[Some(14), Some(5), None, Some(20), Some(13), Some(2)],
                &[Some(18), Some(7), None, Some(26), None, Some(3)],
                &[Some(6), Some(6), None, Some(7), Some(0), Some(0)],
            )],
        };
        let result = scan(&timeline, &spots_by_wg_id(&spots, None), WindyQuery::new(None, None, None)).unwrap();
        assert_eq!(result.span, 6);
        let w = &result.matches[0];
        assert_eq!((w.from.as_str(), w.to.as_str()), ("h0", "h4"));
        assert_eq!(w.windy_hours, 3);
        assert_eq!((w.peak_wind, w.peak_gusts, w.peak_direction.as_str()), (20, 26, "NW"));
    }

    #[test]
    fn missing_gust_falls_back_to_wind_and_bad_direction_to_blank() {
        let spots = [spot(1, "A", "Poland")];
        let timeline =
            WindTimeline { hours: grid(2), spots: vec![series(1, &[Some(15), Some(3)], &[None], &[Some(9)])] };
        let result = scan(&timeline, &spots_by_wg_id(&spots, None), WindyQuery::new(None, None, None)).unwrap();
        assert_eq!(result.matches[0].peak_gusts, 15);
        assert_eq!(result.matches[0].peak_direction, "");
    }

    #[test]
    fn first_peak_wins_ties() {
        let spots = [spot(1, "A", "Poland")];
        let timeline = WindTimeline {
            hours: grid(3),
            spots: vec![series(1, &[Some(15), Some(15), Some(15)], &[Some(16), Some(19), Some(22)], &[])],
        };
        let result = scan(&timeline, &spots_by_wg_id(&spots, None), WindyQuery::new(None, None, None)).unwrap();
        assert_eq!(result.matches[0].peak_gusts, 16);
    }

    #[test]
    fn span_is_capped_by_the_grid_and_limits_the_scan() {
        let spots = [spot(1, "A", "Poland")];
        let timeline = WindTimeline { hours: grid(3), spots: vec![series(1, &[Some(1), Some(1), Some(30)], &[], &[])] };
        let by_id = spots_by_wg_id(&spots, None);
        let all = scan(&timeline, &by_id, WindyQuery::new(None, Some(100), None)).unwrap();
        assert_eq!((all.span, all.grid_to.as_str()), (3, "h2"));
        assert_eq!(all.matches.len(), 1);
        let short = scan(&timeline, &by_id, WindyQuery::new(None, Some(2), None)).unwrap();
        assert!(short.matches.is_empty());
        assert_eq!(short.scanned, 1);
    }

    #[test]
    fn strongest_first_then_by_name() {
        let spots = [spot(1, "Zeta", "Poland"), spot(2, "Alpha", "Poland"), spot(3, "Beta", "Spain")];
        let timeline = WindTimeline {
            hours: grid(1),
            spots: vec![
                series(1, &[Some(20)], &[], &[]),
                series(2, &[Some(20)], &[], &[]),
                series(3, &[Some(25)], &[], &[]),
                series(99, &[Some(40)], &[], &[]),
            ],
        };
        let result = scan(&timeline, &spots_by_wg_id(&spots, None), WindyQuery::new(None, None, None)).unwrap();
        let names: Vec<_> = result.matches.iter().map(|w| w.name.as_str()).collect();
        assert_eq!(names, ["Beta", "Alpha", "Zeta"]);
        assert_eq!(result.scanned, 3, "a series without a known spot is not scanned");
    }

    #[test]
    fn country_by_name_or_slug() {
        let spots = [spot(1, "A", "Czech Republic"), spot(2, "B", "Poland")];
        assert_eq!(spots_by_wg_id(&spots, Some("czech-republic")).len(), 1);
        assert_eq!(spots_by_wg_id(&spots, Some(" Czech Republic ")).len(), 1);
        assert!(spots_by_wg_id(&spots, Some("Atlantis")).is_empty());
        assert_eq!(spots_by_wg_id(&spots, Some("  ")).len(), 2);
    }

    #[test]
    fn empty_grid_is_no_result() {
        assert!(scan(&WindTimeline::default(), &HashMap::new(), WindyQuery::new(None, None, None)).is_none());
    }
}
