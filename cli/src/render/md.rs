//! Client-side Markdown with the layout of `LlmController` and `McpToolService`.
//!
//! `--format md` prints the server-rendered `/llms` document wherever one exists;
//! these renderers cover what has no `/llms` twin (search, status, filtered lists,
//! other models) and are held to the server's output by the parity tests, which is
//! also what checks the client-side computation behind the other formats.

use std::collections::BTreeMap;
use std::fmt::Write;

use crate::api::dto::{CurrentConditions, Forecast, Spot, SpotInfo, Status};
use crate::domain::text::{java_fixed, to_slug};
use crate::domain::wind::{HOURLY_FORECAST_LIMIT, WindForecast};
use crate::domain::windy::{WindyQuery, WindyResult};

fn plural<'a>(n: usize, one: &'a str, many: &'a str) -> &'a str {
    if n == 1 { one } else { many }
}

fn has_value(s: &Option<String>) -> bool {
    s.as_deref().is_some_and(|s| !s.trim().is_empty())
}

fn or_empty(s: &Option<String>) -> &str {
    s.as_deref().unwrap_or("")
}

/// Countries in Java's `sorted()` order with their spot counts.
pub fn countries_with_counts(spots: &[Spot]) -> BTreeMap<&str, usize> {
    let mut counts = BTreeMap::new();
    for spot in spots {
        *counts.entry(spot.country.as_str()).or_insert(0) += 1;
    }
    counts
}

fn country_lines(sb: &mut String, spots: &[Spot]) {
    for (country, count) in countries_with_counts(spots) {
        let _ = writeln!(
            sb,
            "- [{country}](/llms/countries/{}.md) — {count} {}",
            to_slug(country),
            plural(count, "spot", "spots")
        );
    }
}

/// `renderSpotsIndex`.
pub fn spots_index(spots: &[Spot]) -> String {
    let mut sb = String::new();
    sb.push_str("# Kite spots on VARUN.SURF\n\n");
    sb.push_str("Complete list of kite spots with live conditions and forecasts.\n");
    sb.push_str("Each spot has a dedicated markdown document with details, forecast and live conditions.\n\n");
    let _ = writeln!(sb, "Total: {} spots across {} countries.\n", spots.len(), countries_with_counts(spots).len());
    sb.push_str("## Spots\n\n");
    let mut sorted: Vec<&Spot> = spots.iter().collect();
    sorted.sort_by(|a, b| a.country.cmp(&b.country).then_with(|| a.name.cmp(&b.name)));
    for spot in sorted {
        let _ = writeln!(sb, "- [{}, {}](/llms/spots/{}.md)", spot.name, spot.country, spot.wg_id);
    }
    sb.push_str("\n## Countries\n\n");
    country_lines(&mut sb, spots);
    sb.push_str("\n## Wind\n\n");
    sb.push_str(
        "- [Where it will blow](/llms/wind.md) — every spot reaching a given wind speed \
         in the hours ahead. Query parameters: `minWind` (knots), `hours`, `country`, `limit`.\n",
    );
    sb.push_str(
        "- Hour-by-hour wind for one spot: /llms/spots/{wgId}/wind.md — \
         query parameters: `hours`, `minWind`.\n",
    );
    sb
}

/// `renderCountriesIndex`.
pub fn countries_index(spots: &[Spot]) -> String {
    let mut sb = String::new();
    sb.push_str("# Countries on VARUN.SURF\n\n");
    sb.push_str("Kite spots grouped by country. Each country has a dedicated markdown document.\n\n");
    let _ = writeln!(sb, "Total: {} countries.\n", countries_with_counts(spots).len());
    country_lines(&mut sb, spots);
    sb
}

/// `renderCountry`.
pub fn country(country: &str, all: &[Spot]) -> String {
    let mut sb = String::new();
    let _ = writeln!(sb, "# Kite spots in {country}\n");
    let mut spots: Vec<&Spot> = all.iter().filter(|s| s.country == country).collect();
    spots.sort_by(|a, b| a.name.cmp(&b.name));
    let _ = writeln!(sb, "Total: {} {}.\n", spots.len(), plural(spots.len(), "spot", "spots"));
    sb.push_str("## Spots\n\n");
    for spot in spots {
        let _ = writeln!(sb, "- [{}](/llms/spots/{}.md)", spot.name, spot.wg_id);
    }
    sb.push_str("\n## Related\n\n");
    sb.push_str("- [All spots](/llms/spots.md)\n");
    sb.push_str("- [All countries](/llms/countries.md)\n");
    sb
}

/// `renderSpot`. The server document is always English, so the info here is the
/// English `spotInfo` too.
pub fn spot(spot: &Spot) -> String {
    let mut sb = String::new();
    let _ = writeln!(sb, "# {}, {}\n", spot.name, spot.country);
    if let Some(updated) = spot.last_updated.as_deref().filter(|s| !s.is_empty()) {
        let _ = writeln!(sb, "Last updated: {updated}\n");
    }
    sb.push_str("## Overview\n\n");
    let _ = writeln!(sb, "- Spot ID: {}", spot.wg_id);
    if let Some(c) = spot.coordinates {
        let _ = writeln!(sb, "- Coordinates: {}, {}", java_fixed(c.lat, 5), java_fixed(c.lon, 5));
    }
    spot_info(&mut sb, spot.spot_info.as_ref());
    current_conditions(&mut sb, spot.live());
    if !spot.forecast.is_empty() {
        sb.push_str("\n## Forecast (daily)\n\n");
        forecast_table(&mut sb, &spot.forecast);
    }
    if !spot.forecast_hourly.is_empty() {
        let shown = spot.forecast_hourly.len().min(HOURLY_FORECAST_LIMIT);
        let _ = writeln!(sb, "\n## Forecast (hourly, next {shown} entries)\n");
        forecast_table(&mut sb, &spot.forecast_hourly[..shown]);
    }
    links(&mut sb, spot);
    sb.push_str("\n## Related\n\n");
    let _ = writeln!(sb, "- [Hour-by-hour wind forecast](/llms/spots/{}/wind.md)", spot.wg_id);
    let _ = writeln!(sb, "- [Kite spots in {}](/llms/countries/{}.md)", spot.country, to_slug(&spot.country));
    sb.push_str("- [All spots](/llms/spots.md)\n");
    sb.push_str("- [All countries](/llms/countries.md)\n");
    sb
}

fn spot_info(sb: &mut String, info: Option<&SpotInfo>) {
    let Some(info) = info else { return };
    for (label, value) in [
        ("Type", &info.kind),
        ("Best wind", &info.best_wind),
        ("Water temperature", &info.water_temp),
        ("Experience", &info.experience),
        ("Launch", &info.launch),
        ("Hazards", &info.hazards),
        ("Season", &info.season),
    ] {
        if has_value(value) {
            let _ = writeln!(sb, "- {label}: {}", or_empty(value).trim());
        }
    }
    if has_value(&info.description) {
        let _ = writeln!(sb, "\n### Description\n\n{}", or_empty(&info.description).trim());
    }
}

fn current_conditions(sb: &mut String, conditions: Option<&CurrentConditions>) {
    let Some(c) = conditions else { return };
    sb.push_str("\n## Current Conditions\n\n");
    if has_value(&c.date) {
        let _ = writeln!(sb, "- Observed at: {}", or_empty(&c.date));
    }
    let _ = writeln!(sb, "- Wind: {} kts", c.wind);
    let _ = writeln!(sb, "- Gusts: {} kts", c.gusts);
    if has_value(&c.direction) {
        let _ = writeln!(sb, "- Direction: {}", or_empty(&c.direction));
    }
    let _ = writeln!(sb, "- Temperature: {}°C", c.temp);
}

fn forecast_table(sb: &mut String, forecasts: &[Forecast]) {
    sb.push_str("| Date | Wind (kts) | Gusts (kts) | Direction | Temp (°C) | Precip (mm) |\n");
    sb.push_str("|------|------------|-------------|-----------|-----------|-------------|\n");
    for f in forecasts {
        let _ = writeln!(
            sb,
            "| {} | {} | {} | {} | {} | {} |",
            or_empty(&f.date),
            java_fixed(f.wind, 1),
            java_fixed(f.gusts, 1),
            or_empty(&f.direction),
            java_fixed(f.temp, 1),
            java_fixed(f.precipitation, 1)
        );
    }
}

fn links(sb: &mut String, spot: &Spot) {
    let lines = [
        ("Windguru", &spot.windguru_url),
        ("Windfinder", &spot.windfinder_url),
        ("ICM forecast", &spot.icm_url),
        ("Webcam", &spot.webcam_url),
        ("Location", &spot.location_url),
    ];
    if !lines.iter().any(|(_, url)| has_value(url)) {
        return;
    }
    sb.push_str("\n## Links\n\n");
    let _ = writeln!(sb, "- Spot page: https://varun.surf/spot/{}", spot.wg_id);
    for (label, url) in lines {
        if has_value(url) {
            let _ = writeln!(sb, "- {label}: {}", or_empty(url));
        }
    }
}

/// `renderWindForecast`.
pub fn wind_forecast(spot: &Spot, forecast: Option<&WindForecast>) -> String {
    let Some(forecast) = forecast else {
        return format!(
            "No hourly wind forecast cached yet for {}, {} (wgId={}).\n",
            spot.name, spot.country, spot.wg_id
        );
    };
    let mut sb = String::new();
    let _ = writeln!(sb, "# Wind forecast for {}, {} (wgId={})\n", spot.name, spot.country, spot.wg_id);
    let _ = writeln!(
        sb,
        "Model: GFS. Hourly, starting at the current hour, {} {} ahead.",
        forecast.span,
        plural(forecast.span, "hour", "hours")
    );
    if forecast.floor > 0 {
        let _ = writeln!(sb, "Only hours with wind of at least {} kts are listed.", forecast.floor);
    }
    if let Some(summary) = &forecast.summary {
        let peak = summary.peak;
        let _ = writeln!(
            sb,
            "Windiest hour: {} at {} kts, gusting {} kts from {}.",
            or_empty(&peak.date),
            java_fixed(peak.wind, 0),
            java_fixed(peak.gusts, 0),
            or_empty(&peak.direction)
        );
        let _ = writeln!(sb, "Hours at or above 12 kts: {} of {}.", summary.kiteable, forecast.window.len());
    }
    if forecast.rows.is_empty() {
        let _ = writeln!(sb, "\nNo hour in this window reaches {} kts.", forecast.floor);
    } else {
        sb.push_str("\n| Hour | Wind (kts) | Gusts (kts) | Direction |\n");
        sb.push_str("|------|------------|-------------|-----------|\n");
        for f in &forecast.rows {
            let _ = writeln!(
                sb,
                "| {} | {} | {} | {} |",
                or_empty(&f.date),
                java_fixed(f.wind, 0),
                java_fixed(f.gusts, 0),
                or_empty(&f.direction)
            );
        }
    }
    sb.push_str("\n## Related\n\n");
    let _ = writeln!(sb, "- [Spot details](/llms/spots/{}.md)", spot.wg_id);
    sb.push_str("- [Where it will blow](/llms/wind.md)\n");
    sb
}

/// `renderWindySpots`.
pub fn windy_spots(result: Option<&WindyResult>, query: WindyQuery, country: Option<&str>) -> String {
    let Some(result) = result else {
        return "No wind forecast is cached yet.\n".to_string();
    };
    let mut sb = String::new();
    let _ = writeln!(
        sb,
        "# Spots with at least {} kts in the next {} {}\n",
        query.floor,
        result.span,
        plural(result.span, "hour", "hours")
    );
    let _ = writeln!(sb, "Grid: {} to {} (GFS, hourly).", result.grid_from, result.grid_to);
    let country = country.map(str::trim).filter(|c| !c.is_empty());
    let _ = writeln!(
        sb,
        "Scanned {} {}{}.",
        result.scanned,
        plural(result.scanned, "spot", "spots"),
        country.map(|c| format!(" in {c}")).unwrap_or_default()
    );
    if result.matches.is_empty() {
        let _ = writeln!(sb, "\nNo spot reaches {} kts in this window.", query.floor);
        return sb;
    }
    let reported = &result.matches[..result.matches.len().min(query.limit)];
    let _ = writeln!(
        sb,
        "{} {} match, showing {}.\n",
        result.matches.len(),
        plural(result.matches.len(), "spot", "spots"),
        reported.len()
    );
    sb.push_str(
        "| Spot | Country | wgId | From | To | Windy hours | Peak wind (kts) | Peak gusts (kts) | Direction at peak |\n",
    );
    sb.push_str(
        "|------|---------|------|------|----|-------------|-----------------|------------------|-------------------|\n",
    );
    for w in reported {
        let _ = writeln!(
            sb,
            "| {} | {} | {} | {} | {} | {} | {} | {} | {} |",
            w.name, w.country, w.wg_id, w.from, w.to, w.windy_hours, w.peak_wind, w.peak_gusts, w.peak_direction
        );
    }
    sb.push_str("\n## Related\n\n");
    sb.push_str("- Hour-by-hour wind for one spot: /llms/spots/{wgId}/wind.md\n");
    sb.push_str("- [All spots](/llms/spots.md)\n");
    sb
}

/// `findSpotByName`.
pub fn search(query: &str, matches: &[&Spot]) -> String {
    let mut sb = String::new();
    let _ = writeln!(sb, "# Spots matching '{query}'\n");
    for spot in matches {
        let _ = writeln!(sb, "- {}, {} (wgId={})", spot.name, spot.country, spot.wg_id);
    }
    sb
}

/// `getStatus`.
pub fn status_sentence(status: &Status) -> String {
    format!(
        "varun.surf is tracking {} spots across {} countries. {} live weather {} currently reporting.",
        status.spots_count,
        status.countries_count,
        status.live_stations,
        if status.live_stations == 1 { "station is" } else { "stations are" }
    )
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn status_sentence_is_singular_for_one_station() {
        let mut status = Status { spots_count: 830, countries_count: 59, live_stations: 15, ..Default::default() };
        assert_eq!(
            status_sentence(&status),
            "varun.surf is tracking 830 spots across 59 countries. 15 live weather stations are currently reporting."
        );
        status.live_stations = 1;
        assert!(status_sentence(&status).ends_with("1 live weather station is currently reporting."));
    }

    #[test]
    fn search_lists_matches() {
        let spot = Spot { wg_id: 1, name: "Puck".into(), country: "Poland".into(), ..Default::default() };
        assert_eq!(search("puck", &[&spot]), "# Spots matching 'puck'\n\n- Puck, Poland (wgId=1)\n");
    }
}
