//! `varun now [SPOT...]`: live conditions at the spots named, the favourites, or
//! the ten windiest stations right now.

use serde::Serialize;

use crate::api::dto::Spot;
use crate::commands::Ctx;
use crate::commands::spots::live_cells;
use crate::config::Format;
use crate::domain::time::{age_minutes, format_age, is_stale, now_local};
use crate::error::Result;
use crate::render::{self, Col, Grid, SCHEMA_VERSION, Style, Val};

const TOP_STATIONS: usize = 10;

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct NowJson<'a> {
    schema_version: u32,
    spots: Vec<SpotNow<'a>>,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct SpotNow<'a> {
    name: &'a str,
    country: &'a str,
    wg_id: i64,
    live: Option<LiveJson<'a>>,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct LiveJson<'a> {
    date: Option<&'a str>,
    wind: i64,
    gusts: i64,
    direction: Option<&'a str>,
    temp: i64,
    age_minutes: Option<i64>,
    stale: bool,
}

pub fn run(ctx: &mut Ctx, args: &[String]) -> Result<String> {
    let spots: Vec<Spot> = if !args.is_empty() {
        args.iter().map(|a| ctx.resolve(a)).collect::<Result<_>>()?
    } else if !ctx.config.favorites.is_empty() {
        let list = ctx.spot_list()?;
        let favorites = ctx.config.favorites.clone();
        favorites.iter().filter_map(|f| list.spots.iter().find(|s| s.wg_id == f.wg_id).cloned()).collect()
    } else {
        let list = ctx.spot_list()?;
        let mut live: Vec<Spot> = list.spots.iter().filter(|s| s.live().is_some()).cloned().collect();
        live.sort_by(|a, b| {
            let wind = |s: &Spot| s.live().map_or(0, |c| c.wind);
            wind(b).cmp(&wind(a)).then_with(|| a.name.cmp(&b.name))
        });
        live.truncate(TOP_STATIONS);
        live
    };

    let now = now_local();
    let out = &ctx.out;
    Ok(match out.format {
        Format::Json => render::json(&NowJson {
            schema_version: SCHEMA_VERSION,
            spots: spots
                .iter()
                .map(|s| SpotNow {
                    name: &s.name,
                    country: &s.country,
                    wg_id: s.wg_id,
                    live: s.live().map(|c| LiveJson {
                        date: c.date.as_deref(),
                        wind: c.wind,
                        gusts: c.gusts,
                        direction: c.direction.as_deref(),
                        temp: c.temp,
                        age_minutes: c.date.as_deref().and_then(|d| age_minutes(d, now)),
                        stale: is_stale(c.date.as_deref(), now),
                    }),
                })
                .collect(),
        }),
        Format::Md => {
            let mut text = String::from("# Live conditions\n\n");
            for s in &spots {
                match s.live() {
                    Some(c) => text.push_str(&format!(
                        "- {}, {} (wgId={}): {} kts, gusts {} kts, {}, {}°C, observed at {}{}\n",
                        s.name,
                        s.country,
                        s.wg_id,
                        c.wind,
                        c.gusts,
                        c.direction.as_deref().unwrap_or(""),
                        c.temp,
                        c.date.as_deref().unwrap_or("unknown time"),
                        if is_stale(c.date.as_deref(), now) { " (stale)" } else { "" }
                    )),
                    None => text.push_str(&format!(
                        "- {}, {} (wgId={}): no live weather station\n",
                        s.name, s.country, s.wg_id
                    )),
                }
            }
            text
        }
        _ => {
            let mut grid = Grid::new(vec![
                Col::left("Spot"),
                Col::left(out.tr("Country", "Kraj")).low(),
                Col::right(out.speed_header("Wind", "Wiatr")),
                Col::right(out.speed_header("Gusts", "Porywy")),
                Col::left(out.tr("Dir", "Kier.")),
                Col::right("°C"),
                Col::right(out.tr("Age", "Wiek")),
            ]);
            for s in &spots {
                let mut row = vec![Val::new(&s.name), Val::new(&s.country)];
                row.extend(live_cells(ctx, s.live()));
                match s.live() {
                    Some(c) => {
                        let stale = is_stale(c.date.as_deref(), now);
                        let age = c.date.as_deref().and_then(|d| age_minutes(d, now));
                        row.push(Val::new(c.temp.to_string()));
                        row.push(Val::fancy(
                            age.map_or_else(|| "?".to_string(), format_age) + if stale { " ⚠" } else { "" },
                            age.map_or_else(String::new, |a| a.to_string()),
                            if stale { Style::Warn } else { Style::Dim },
                        ));
                    }
                    None => {
                        row[2] = Val::styled(out.tr("no station", "brak stacji"), Style::Dim);
                        row.push(Val::new(""));
                        row.push(Val::new(""));
                    }
                }
                grid.push(row);
            }
            if spots.is_empty() && out.format == Format::Table {
                return Ok(out.tr("No live weather station is reporting.\n", "Żadna stacja nie nadaje.\n").to_string());
            }
            grid.render(out)
        }
    })
}
