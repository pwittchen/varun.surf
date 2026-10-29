//! `varun spots [--country C] [--live]` and `varun country <C>`: `list_spots` and
//! `get_spots_by_country`.

use crate::api::Fetched;
use crate::api::dto::{CurrentConditions, Spot};
use crate::commands::{Ctx, country_not_found, empty_check};
use crate::domain::text::to_slug;
use crate::domain::wind_class::{arrow, speed};
use crate::error::{CliError, Result};
use crate::render::{self, Col, Grid, Style, Val, md};

pub fn run(ctx: &mut Ctx, country: Option<&str>, live: bool) -> Result<String> {
    if let Some(country) = country {
        empty_check(country, "Country slug must not be empty.")?;
    }

    // The server renders these documents itself; only a live-only list has no twin.
    if ctx.is_md() && !live {
        let path = match country {
            Some(c) => format!("/llms/countries/{}.md", to_slug(c.trim())),
            None => "/llms/spots.md".to_string(),
        };
        return match ctx.client.markdown(&path)? {
            Fetched::Found(text) => Ok(text),
            Fetched::NotFound => Err(country_not_found(country.unwrap_or_default())),
        };
    }

    let country = country.map(|c| ctx.find_country(c)).transpose()?;
    let list = ctx.spot_list()?;
    let picked: Vec<usize> = (0..list.spots.len())
        .filter(|&i| country.as_ref().is_none_or(|c| &list.spots[i].country == c))
        .filter(|&i| !live || list.spots[i].live().is_some())
        .collect();
    let spots: Vec<Spot> = picked.iter().map(|&i| list.spots[i].clone()).collect();

    Ok(match ctx.out.format {
        crate::config::Format::Json => {
            let raw: Vec<&serde_json::Value> = picked.iter().filter_map(|&i| list.raw.get(i)).collect();
            render::json(&raw)
        }
        crate::config::Format::Md => match &country {
            Some(c) => md::country(c, &spots),
            None => md::spots_index(&spots),
        },
        _ => table(ctx, &spots),
    })
}

fn table(ctx: &Ctx, spots: &[Spot]) -> String {
    let out = &ctx.out;
    let mut sorted: Vec<&Spot> = spots.iter().collect();
    sorted.sort_by(|a, b| a.country.cmp(&b.country).then_with(|| a.name.cmp(&b.name)));
    let mut grid = Grid::new(vec![
        Col::left(out.tr("Spot", "Spot")),
        Col::left(out.tr("Country", "Kraj")).low(),
        Col::right("wgId").low(),
        Col::right(out.speed_header("Live wind", "Wiatr teraz")),
        Col::right(out.speed_header("Gusts", "Porywy")),
        Col::left(out.tr("Dir", "Kier.")),
    ]);
    for spot in sorted {
        let mut row = vec![Val::new(&spot.name), Val::new(&spot.country), Val::new(spot.wg_id.to_string())];
        row.extend(live_cells(ctx, spot.live()));
        grid.push(row);
    }
    let mut text = grid.render(out);
    if out.format == crate::config::Format::Table {
        let countries = spots.iter().map(|s| &s.country).collect::<std::collections::HashSet<_>>().len();
        text.push_str(&out.paint(
            &format!(
                "{} {} · {} {}\n",
                spots.len(),
                out.tr("spots", "spotów"),
                countries,
                out.tr("countries", "krajów")
            ),
            Style::Dim,
        ));
    }
    text
}

/// Wind, gusts and direction cells of a live reading, blank without one.
pub fn live_cells(ctx: &Ctx, live: Option<&CurrentConditions>) -> Vec<Val> {
    let out = &ctx.out;
    let Some(c) = live else {
        return vec![Val::new(""), Val::new(""), Val::new("")];
    };
    let direction = c.direction.clone().unwrap_or_default();
    vec![
        Val::styled(speed(c.wind as f64, out.units), Style::wind(c.wind as f64)),
        Val::new(speed(c.gusts as f64, out.units)),
        Val::fancy(format!("{} {direction}", arrow(&direction)), direction.clone(), Style::None),
    ]
}

pub fn country(ctx: &mut Ctx, country: &str) -> Result<String> {
    if country.trim().is_empty() {
        return Err(CliError::Usage("Country slug must not be empty.".to_string()));
    }
    run(ctx, Some(country), false)
}
