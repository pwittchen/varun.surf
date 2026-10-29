//! `varun spot <SPOT>`: `get_spot`, plus model selection.

use std::time::Duration;

use crate::api::Fetched;
use crate::api::dto::{Forecast, Spot};
use crate::commands::{Ctx, spot_not_found};
use crate::config::Format;
use crate::domain::text::java_fixed;
use crate::domain::time::{age_minutes, format_age, is_stale, now_local};
use crate::domain::wind::HOURLY_FORECAST_LIMIT;
use crate::domain::wind_class::{arrow, speed, unit_label};
use crate::error::{CliError, Result};
use crate::render::{self, Col, Grid, Out, Style, Val, md, pairs};

pub struct SpotArgs<'a> {
    pub spot: &'a str,
    pub model: Option<&'a str>,
    pub models: bool,
    pub hours: Option<usize>,
}

/// Model discovery runs on the server once a spot is opened, and until it ends
/// only GFS is listed: a model asked for and not (yet) listed is looked for again
/// this many times before it is called unknown.
const DISCOVERY_ATTEMPTS: u32 = 4;
const DISCOVERY_WAIT: Duration = Duration::from_millis(1500);

pub fn run(ctx: &mut Ctx, args: SpotArgs) -> Result<String> {
    let id = match ctx.resolve_id_hint(args.spot) {
        Some(id) => id,
        None => ctx.resolve(args.spot)?.wg_id,
    };

    if ctx.is_md() && args.model.is_none() && !args.models {
        return match ctx.client.markdown(&format!("/llms/spots/{id}.md"))? {
            Fetched::Found(text) => Ok(text),
            Fetched::NotFound => Err(spot_not_found(id)),
        };
    }

    let (mut spot, mut body) = fetch(ctx, id, None)?;

    if args.models {
        return Ok(models(&ctx.out, &spot, &body));
    }

    if let Some(model) = args.model.map(str::trim).map(str::to_ascii_lowercase) {
        let mut attempt = 1;
        while !spot.available_models.iter().any(|m| m.key.eq_ignore_ascii_case(&model)) {
            if spot.available_models.len() > 1 || attempt >= DISCOVERY_ATTEMPTS {
                let keys: Vec<&str> = spot.available_models.iter().map(|m| m.key.as_str()).collect();
                return Err(CliError::Usage(format!(
                    "Unknown model '{model}' for {}. Available: {}.",
                    spot.name,
                    if keys.is_empty() { "gfs".to_string() } else { keys.join(", ") }
                )));
            }
            std::thread::sleep(DISCOVERY_WAIT);
            attempt += 1;
            (spot, body) = fetch(ctx, id, None)?;
        }
        if model != "gfs" {
            (spot, body) = fetch(ctx, id, Some(&model))?;
        }
    }

    Ok(match ctx.out.format {
        Format::Json => body.trim_end().to_string() + "\n",
        Format::Md => md::spot(&spot),
        _ => details(ctx, &spot, args.model.unwrap_or("gfs"), args.hours.unwrap_or(HOURLY_FORECAST_LIMIT)),
    })
}

fn fetch(ctx: &mut Ctx, id: i64, model: Option<&str>) -> Result<(Spot, String)> {
    match ctx.client.spot(id, model)? {
        Fetched::Found(found) => Ok(found),
        Fetched::NotFound => Err(spot_not_found(id)),
    }
}

fn models(out: &Out, spot: &Spot, body: &str) -> String {
    match out.format {
        Format::Json => {
            let value: serde_json::Value = serde_json::from_str(body).unwrap_or_default();
            render::json(value.get("availableModels").unwrap_or(&serde_json::Value::Array(vec![])))
        }
        Format::Md => spot.available_models.iter().map(|m| format!("- {}: {}\n", m.key, m.name)).collect(),
        _ => {
            let mut grid = Grid::new(vec![Col::left(out.tr("Key", "Klucz")), Col::left(out.tr("Model", "Model"))]);
            for m in &spot.available_models {
                grid.push(vec![Val::new(&m.key), Val::new(&m.name)]);
            }
            grid.render(out)
        }
    }
}

fn details(ctx: &Ctx, spot: &Spot, model: &str, hourly_rows: usize) -> String {
    let out = &ctx.out;
    let table = out.format == Format::Table;
    let section = |title: &str| if table { format!("\n{}\n", out.heading(title)) } else { format!("\n# {title}\n") };
    let mut text = String::new();
    text.push_str(&out.heading(&format!("{}, {}", spot.name, spot.country)));
    text.push('\n');

    let mut overview = vec![("wgId".to_string(), spot.wg_id.to_string())];
    if let Some(c) = spot.coordinates {
        overview.push((
            out.tr("Coordinates", "Współrzędne").to_string(),
            format!("{}, {}", java_fixed(c.lat, 5), java_fixed(c.lon, 5)),
        ));
    }
    if let Some(info) = spot.info(out.lang) {
        for (label, value) in [
            (out.tr("Type", "Typ"), &info.kind),
            (out.tr("Best wind", "Najlepszy wiatr"), &info.best_wind),
            (out.tr("Water temperature", "Temperatura wody"), &info.water_temp),
            (out.tr("Experience", "Poziom"), &info.experience),
            (out.tr("Launch", "Start"), &info.launch),
            (out.tr("Hazards", "Zagrożenia"), &info.hazards),
            (out.tr("Season", "Sezon"), &info.season),
        ] {
            if let Some(v) = value.as_deref().map(str::trim).filter(|v| !v.is_empty()) {
                overview.push((label.to_string(), v.to_string()));
            }
        }
    }
    if let Some(updated) = spot.last_updated.as_deref().filter(|s| !s.is_empty()) {
        overview.push((out.tr("Last updated", "Aktualizacja").to_string(), updated.to_string()));
    }
    text.push_str(&pairs(out, &overview));
    if let Some(description) =
        spot.info(out.lang).and_then(|i| i.description.as_deref()).map(str::trim).filter(|d| !d.is_empty())
    {
        text.push('\n');
        text.push_str(&wrap(description, out.width.min(100) as usize));
    }

    if let Some(live) = spot.live() {
        text.push_str(&section(out.tr("Live conditions", "Warunki na żywo")));
        let now = now_local();
        let date = live.date.clone().unwrap_or_default();
        let age = age_minutes(&date, now).map(format_age);
        let stale = is_stale(live.date.as_deref(), now);
        let units = unit_label(out.units);
        let direction = live.direction.clone().unwrap_or_default();
        let observed = match age {
            Some(age) => format!("{date} ({age} {})", out.tr("ago", "temu")),
            None => date,
        };
        let mut lines = vec![
            (
                out.tr("Wind", "Wiatr").to_string(),
                format!("{} {units}", out.paint(&speed(live.wind as f64, out.units), Style::wind(live.wind as f64))),
            ),
            (out.tr("Gusts", "Porywy").to_string(), format!("{} {units}", speed(live.gusts as f64, out.units))),
            (
                out.tr("Direction", "Kierunek").to_string(),
                if table { format!("{} {direction}", arrow(&direction)) } else { direction },
            ),
            (out.tr("Temperature", "Temperatura").to_string(), format!("{}°C", live.temp)),
            (
                out.tr("Observed", "Odczyt").to_string(),
                if stale {
                    out.paint(&format!("{observed} · {}", out.tr("stale", "nieaktualny")), Style::Warn)
                } else {
                    observed
                },
            ),
        ];
        if !table {
            lines.iter_mut().for_each(|(_, v)| *v = strip_ansi(v));
        }
        text.push_str(&pairs(out, &lines));
    }

    if !spot.forecast.is_empty() {
        text.push_str(&section(out.tr("Forecast (daily)", "Prognoza (dzienna)")));
        text.push_str(&forecast_grid(out, &spot.forecast).render(out));
    }
    if !spot.forecast_hourly.is_empty() {
        let shown = spot.forecast_hourly.len().min(hourly_rows.max(1));
        let model_name = spot
            .available_models
            .iter()
            .find(|m| m.key.eq_ignore_ascii_case(model))
            .map_or_else(|| model.to_uppercase(), |m| m.name.clone());
        text.push_str(&section(&format!(
            "{} {model_name}, {} {shown})",
            out.tr("Forecast (hourly,", "Prognoza (godzinowa,"),
            out.tr("next", "najbliższe")
        )));
        text.push_str(&forecast_grid(out, &spot.forecast_hourly[..shown]).render(out));
    }

    let links: Vec<(String, String)> = [
        ("varun.surf", Some(format!("https://varun.surf/spot/{}", spot.wg_id))),
        ("Windguru", spot.windguru_url.clone()),
        ("Windfinder", spot.windfinder_url.clone()),
        ("ICM", spot.icm_url.clone()),
        (out.tr("Webcam", "Kamera"), spot.webcam_url.clone()),
        (out.tr("Location", "Lokalizacja"), spot.location_url.clone()),
    ]
    .into_iter()
    .filter_map(|(label, url)| url.filter(|u| !u.trim().is_empty()).map(|u| (label.to_string(), u)))
    .collect();
    text.push_str(&section(out.tr("Links", "Linki")));
    text.push_str(&pairs(out, &links));
    text
}

fn forecast_grid(out: &Out, forecasts: &[Forecast]) -> Grid {
    let mut grid = Grid::new(vec![
        Col::left(out.tr("Date", "Data")),
        Col::right(out.speed_header("Wind", "Wiatr")),
        Col::right(out.speed_header("Gusts", "Porywy")),
        Col::left(out.tr("Dir", "Kier.")),
        Col::right(out.tr("Temp (°C)", "Temp. (°C)")),
        Col::right(out.tr("Rain (mm)", "Opad (mm)")).low(),
    ]);
    for f in forecasts {
        let direction = f.direction.clone().unwrap_or_default();
        grid.push(vec![
            Val::new(f.date.clone().unwrap_or_default()),
            Val::styled(speed(f.wind, out.units), Style::wind(f.wind)),
            Val::new(speed(f.gusts, out.units)),
            Val::fancy(format!("{} {direction}", arrow(&direction)), direction.clone(), Style::None),
            Val::new(java_fixed(f.temp, 0)),
            Val::new(java_fixed(f.precipitation, 1)),
        ]);
    }
    grid
}

/// Greedy word wrap for the description paragraph.
fn wrap(text: &str, width: usize) -> String {
    let mut out = String::new();
    for paragraph in text.lines() {
        let mut line = 0;
        for word in paragraph.split_whitespace() {
            let len = word.chars().count();
            if line > 0 && line + 1 + len > width {
                out.push('\n');
                line = 0;
            } else if line > 0 {
                out.push(' ');
                line += 1;
            }
            out.push_str(word);
            line += len;
        }
        out.push('\n');
    }
    out
}

fn strip_ansi(text: &str) -> String {
    let mut out = String::with_capacity(text.len());
    let mut chars = text.chars();
    while let Some(c) = chars.next() {
        if c == '\u{1b}' {
            for c in chars.by_ref() {
                if c.is_ascii_alphabetic() {
                    break;
                }
            }
        } else {
            out.push(c);
        }
    }
    out
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn wraps_on_word_boundaries() {
        assert_eq!(wrap("aaa bbb ccc", 7), "aaa bbb\nccc\n");
        assert_eq!(wrap("one\ntwo", 80), "one\ntwo\n");
    }

    #[test]
    fn strips_ansi_sequences() {
        assert_eq!(strip_ansi("\u{1b}[32m15\u{1b}[0m kts"), "15 kts");
    }
}
