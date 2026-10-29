//! `varun windy`: `find_windy_spots`.

use std::collections::HashMap;

use serde::Serialize;

use crate::api::Fetched;
use crate::api::dto::Spot;
use crate::commands::{Ctx, country_not_found};
use crate::config::Format;
use crate::domain::wind_class::{arrow, speed};
use crate::domain::windy::{WindyQuery, WindyResult, WindyWindow, scan, spots_by_wg_id};
use crate::error::{CliError, Result};
use crate::render::{self, Col, Grid, Out, SCHEMA_VERSION, Style, Val, md};

#[derive(Debug, Clone)]
pub struct WindyArgs {
    pub min_wind: Option<i64>,
    pub hours: Option<i64>,
    pub country: Option<String>,
    pub limit: Option<i64>,
    pub favorites: bool,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct WindyJson<'a> {
    schema_version: u32,
    min_wind: i64,
    hours: usize,
    country: Option<&'a str>,
    grid: Option<GridJson<'a>>,
    scanned: usize,
    matched: usize,
    spots: &'a [WindyWindow],
}

#[derive(Serialize)]
struct GridJson<'a> {
    from: &'a str,
    to: &'a str,
}

pub fn run(ctx: &mut Ctx, args: &WindyArgs) -> Result<String> {
    let query = WindyQuery::new(args.min_wind, args.hours, args.limit);
    let country = args.country.as_deref().map(str::trim).filter(|c| !c.is_empty());
    if args.favorites && ctx.config.favorites.is_empty() {
        return Err(CliError::Usage("No favourites yet: add one with `varun fav add <SPOT>`.".to_string()));
    }

    if ctx.is_md() && !args.favorites {
        let mut params = vec![format!("minWind={}", query.floor), format!("hours={}", query.hours)];
        if let Some(c) = country {
            params.push(format!("country={}", encode(c)));
        }
        params.push(format!("limit={}", query.limit));
        return match ctx.client.markdown(&format!("/llms/wind.md?{}", params.join("&")))? {
            Fetched::Found(text) => Ok(text),
            Fetched::NotFound => Err(country_not_found(country.unwrap_or_default())),
        };
    }

    let list = ctx.spot_list()?;
    let mut candidates: HashMap<i64, &Spot> = spots_by_wg_id(&list.spots, country);
    if country.is_some() && candidates.is_empty() {
        return Err(country_not_found(country.unwrap_or_default()));
    }
    if args.favorites {
        candidates.retain(|id, _| ctx.config.favorites.iter().any(|f| f.wg_id == *id));
    }
    // Exactly the span needed, not the 120 h the map asks for.
    let timeline = ctx.client.wind(query.hours)?;
    let result = scan(&timeline, &candidates, query);

    Ok(match ctx.out.format {
        Format::Md => md::windy_spots(result.as_ref(), query, country),
        Format::Json => {
            let shown = result.as_ref().map_or(&[][..], |r| &r.matches[..r.matches.len().min(query.limit)]);
            render::json(&WindyJson {
                schema_version: SCHEMA_VERSION,
                min_wind: query.floor,
                hours: result.as_ref().map_or(0, |r| r.span),
                country,
                grid: result.as_ref().map(|r| GridJson { from: &r.grid_from, to: &r.grid_to }),
                scanned: result.as_ref().map_or(0, |r| r.scanned),
                matched: result.as_ref().map_or(0, |r| r.matches.len()),
                spots: shown,
            })
        }
        _ => table(&ctx.out, result.as_ref(), query, country),
    })
}

fn table(out: &Out, result: Option<&WindyResult>, query: WindyQuery, country: Option<&str>) -> String {
    let Some(result) = result else {
        return if out.format == Format::Table {
            out.tr("No wind forecast is cached yet.\n", "Brak prognozy wiatru.\n").to_string()
        } else {
            String::new()
        };
    };
    let shown = &result.matches[..result.matches.len().min(query.limit)];
    let mut grid = Grid::new(vec![
        Col::left("Spot"),
        Col::left(out.tr("Country", "Kraj")).low(),
        Col::right("wgId").low(),
        Col::left(out.tr("From", "Od")),
        Col::left(out.tr("To", "Do")),
        Col::right(out.tr("Hours", "Godz.")),
        Col::right(out.speed_header("Peak", "Max")),
        Col::right(out.speed_header("Gusts", "Porywy")),
        Col::left(out.tr("Dir", "Kier.")),
    ]);
    for w in shown {
        grid.push(vec![
            Val::new(&w.name),
            Val::new(&w.country),
            Val::new(w.wg_id.to_string()),
            Val::new(&w.from),
            Val::new(&w.to),
            Val::new(w.windy_hours.to_string()),
            Val::styled(speed(w.peak_wind as f64, out.units), Style::wind(w.peak_wind as f64)),
            Val::new(speed(w.peak_gusts as f64, out.units)),
            Val::fancy(
                format!("{} {}", arrow(&w.peak_direction), w.peak_direction),
                w.peak_direction.clone(),
                Style::None,
            ),
        ]);
    }
    if out.format != Format::Table {
        return grid.plain();
    }
    let mut text = out.heading(&format!(
        "{} {} kts {} {} h",
        out.tr("Spots with at least", "Spoty z co najmniej"),
        query.floor,
        out.tr("in the next", "w ciągu"),
        result.span
    ));
    if let Some(c) = country {
        text.push_str(&format!(" · {c}"));
    }
    text.push('\n');
    text.push_str(&out.paint(
        &format!(
            "{} → {} · {} {} · {} {}\n\n",
            result.grid_from,
            result.grid_to,
            out.tr("scanned", "sprawdzono"),
            result.scanned,
            out.tr("matched", "pasuje"),
            result.matches.len()
        ),
        Style::Dim,
    ));
    if shown.is_empty() {
        text.push_str(&format!("{} {} kts.\n", out.tr("No spot reaches", "Żaden spot nie osiąga"), query.floor));
    } else {
        text.push_str(&grid.render(out));
    }
    text
}

/// Percent-encodes a query value (country names carry spaces).
fn encode(value: &str) -> String {
    value
        .bytes()
        .map(|b| match b {
            b'A'..=b'Z' | b'a'..=b'z' | b'0'..=b'9' | b'-' | b'_' | b'.' | b'~' => (b as char).to_string(),
            _ => format!("%{b:02X}"),
        })
        .collect()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn encodes_query_values() {
        assert_eq!(encode("czech-republic"), "czech-republic");
        assert_eq!(encode("Czech Republic"), "Czech%20Republic");
        assert_eq!(encode("Curaçao"), "Cura%C3%A7ao");
    }
}
