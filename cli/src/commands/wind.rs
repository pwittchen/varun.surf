//! `varun wind <SPOT>`: `get_wind_forecast`.

use serde::Serialize;

use crate::api::Fetched;
use crate::api::dto::{Forecast, Spot};
use crate::commands::{Ctx, spot_not_found};
use crate::config::Format;
use crate::domain::text::java_fixed;
use crate::domain::wind::{KiteableWindow, WindForecast, wind_forecast};
use crate::domain::wind_class::{arrow, speed, unit_label};
use crate::error::Result;
use crate::render::{self, Col, Grid, Out, SCHEMA_VERSION, Style, Val, md};

pub struct WindArgs<'a> {
    pub spot: &'a str,
    pub hours: Option<i64>,
    pub min_wind: Option<i64>,
    pub all: bool,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct WindJson<'a> {
    schema_version: u32,
    spot: SpotRef<'a>,
    model: &'static str,
    hours: usize,
    min_wind: i64,
    summary: Option<SummaryJson<'a>>,
    forecast: Vec<&'a Forecast>,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct SpotRef<'a> {
    name: &'a str,
    country: &'a str,
    wg_id: i64,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct SummaryJson<'a> {
    peak: &'a Forecast,
    kiteable_hours: usize,
    kiteable_windows: &'a [KiteableWindow],
}

pub fn run(ctx: &mut Ctx, args: WindArgs) -> Result<String> {
    if ctx.is_md() {
        let id = match ctx.resolve_id_hint(args.spot) {
            Some(id) => id,
            None => ctx.resolve(args.spot)?.wg_id,
        };
        let mut query = Vec::new();
        if let Some(h) = args.hours {
            query.push(format!("hours={h}"));
        }
        if let Some(w) = args.min_wind {
            query.push(format!("minWind={w}"));
        }
        let query = if query.is_empty() { String::new() } else { format!("?{}", query.join("&")) };
        return match ctx.client.markdown(&format!("/llms/spots/{id}/wind.md{query}"))? {
            Fetched::Found(text) => Ok(text),
            Fetched::NotFound => Err(spot_not_found(id)),
        };
    }

    let spot = ctx.resolve(args.spot)?;
    let hours = match ctx.client.forecast(spot.wg_id)? {
        Fetched::Found((forecast, _)) => forecast.hours,
        Fetched::NotFound => return Err(spot_not_found(spot.wg_id)),
    };
    let forecast = wind_forecast(&hours, args.hours, args.min_wind);

    Ok(match ctx.out.format {
        Format::Json => render::json(&WindJson {
            schema_version: SCHEMA_VERSION,
            spot: SpotRef { name: &spot.name, country: &spot.country, wg_id: spot.wg_id },
            model: "gfs",
            hours: forecast.as_ref().map_or(0, |f| f.span),
            min_wind: forecast.as_ref().map_or(0, |f| f.floor),
            summary: forecast.as_ref().and_then(|f| f.summary.as_ref()).map(|s| SummaryJson {
                peak: s.peak,
                kiteable_hours: s.kiteable,
                kiteable_windows: &s.windows,
            }),
            forecast: forecast.as_ref().map_or_else(Vec::new, |f| f.rows.clone()),
        }),
        Format::Md => md::wind_forecast(&spot, forecast.as_ref()),
        _ => table(&ctx.out, &spot, forecast.as_ref(), args.all),
    })
}

fn table(out: &Out, spot: &Spot, forecast: Option<&WindForecast>, all: bool) -> String {
    let Some(forecast) = forecast else {
        return if out.format == Format::Table {
            format!(
                "{}\n",
                out.tr(
                    &format!("No hourly wind forecast cached yet for {}, {}.", spot.name, spot.country),
                    &format!("Brak prognozy godzinowej dla {}, {}.", spot.name, spot.country),
                )
            )
        } else {
            String::new()
        };
    };

    let waves = all && forecast.rows.iter().any(|f| f.wave.is_some());
    let mut cols = vec![
        Col::left(out.tr("Hour", "Godzina")),
        Col::right(out.speed_header("Wind", "Wiatr")),
        Col::right(out.speed_header("Gusts", "Porywy")),
        Col::left(out.tr("Dir", "Kier.")),
    ];
    if all {
        cols.extend([
            Col::right(out.tr("Temp (°C)", "Temp. (°C)")),
            Col::right(out.tr("Rain (mm)", "Opad (mm)")),
            Col::right(out.tr("Cloud (%)", "Chmury (%)")),
            Col::right(out.tr("Pressure (hPa)", "Ciśn. (hPa)")),
        ]);
        if waves {
            cols.extend([
                Col::right(out.tr("Wave (m)", "Fala (m)")),
                Col::right(out.tr("Period (s)", "Okres (s)")),
                Col::left(out.tr("Wave dir", "Kier. fali")),
            ]);
        }
    }
    let mut grid = Grid::new(cols);
    for f in &forecast.rows {
        let direction = f.direction.clone().unwrap_or_default();
        let mut row = vec![
            Val::new(f.date.clone().unwrap_or_default()),
            Val::styled(speed(f.wind, out.units), Style::wind(f.wind)),
            Val::new(speed(f.gusts, out.units)),
            Val::fancy(format!("{} {direction}", arrow(&direction)), direction.clone(), Style::None),
        ];
        if all {
            row.extend([
                Val::new(java_fixed(f.temp, 0)),
                Val::new(java_fixed(f.precipitation, 1)),
                Val::new(java_fixed(f.cloud_cover_percent, 0)),
                Val::new(java_fixed(f.pressure_hpa, 0)),
            ]);
            if waves {
                row.extend([
                    Val::new(f.wave.map(|w| java_fixed(w, 1)).unwrap_or_default()),
                    Val::new(f.wave_period.map(|p| java_fixed(p, 0)).unwrap_or_default()),
                    Val::new(f.wave_direction.clone().unwrap_or_default()),
                ]);
            }
        }
        grid.push(row);
    }

    if out.format != Format::Table {
        return grid.plain();
    }

    let mut text = String::new();
    text.push_str(&out.heading(&format!("{}, {}", spot.name, spot.country)));
    text.push_str(&out.paint(
        &format!(" · GFS · {} {} {}\n", out.tr("next", "najbliższe"), forecast.span, out.tr("h", "h")),
        Style::Dim,
    ));
    if let Some(summary) = &forecast.summary {
        let peak = summary.peak;
        let units = unit_label(out.units);
        text.push_str(&format!(
            "{} {} — {} {units}, {} {} {units}, {} {}\n",
            out.tr("Windiest hour:", "Najwięcej wiatru:"),
            peak.date.clone().unwrap_or_default(),
            out.paint(&speed(peak.wind, out.units), Style::wind(peak.wind)),
            out.tr("gusting", "w porywach"),
            speed(peak.gusts, out.units),
            out.tr("from", "z"),
            peak.direction.clone().unwrap_or_default(),
        ));
        text.push_str(&format!(
            "{} {} / {}\n",
            out.tr("Hours at 12+ kts:", "Godziny z 12+ kts:"),
            summary.kiteable,
            forecast.window.len()
        ));
        if summary.windows.is_empty() {
            text.push_str(&out.paint(out.tr("No kiteable window.\n", "Brak okna do pływania.\n"), Style::Dim));
        } else {
            text.push_str(out.tr("Kiteable windows:\n", "Okna do pływania:\n"));
            for w in &summary.windows {
                text.push_str(&format!(
                    "  {} → {}  ({} h, {} {} {units})\n",
                    w.from,
                    w.to,
                    w.hours,
                    out.tr("peak", "max"),
                    out.paint(&speed(w.peak_wind as f64, out.units), Style::wind(w.peak_wind as f64)),
                ));
            }
        }
    }
    text.push('\n');
    if forecast.rows.is_empty() {
        text.push_str(&format!(
            "{} {} kts.\n",
            out.tr("No hour in this window reaches", "Żadna godzina w tym oknie nie osiąga"),
            forecast.floor
        ));
    } else {
        text.push_str(&grid.render(out));
    }
    text
}
