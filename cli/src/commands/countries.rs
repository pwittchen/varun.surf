//! `varun countries`: `list_countries`.

use serde::Serialize;

use crate::api::Fetched;
use crate::commands::Ctx;
use crate::config::Format;
use crate::domain::text::to_slug;
use crate::error::{CliError, Result};
use crate::render::{self, Col, Grid, SCHEMA_VERSION, Val, md};

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct CountriesJson<'a> {
    schema_version: u32,
    total: usize,
    countries: Vec<CountryJson<'a>>,
}

#[derive(Serialize)]
struct CountryJson<'a> {
    name: &'a str,
    slug: String,
    spots: usize,
}

pub fn run(ctx: &mut Ctx) -> Result<String> {
    if ctx.is_md() {
        return match ctx.client.markdown("/llms/countries.md")? {
            Fetched::Found(text) => Ok(text),
            Fetched::NotFound => Err(CliError::Network("the backend has no /llms/countries.md".to_string())),
        };
    }
    let list = ctx.spot_list()?;
    let counts = md::countries_with_counts(&list.spots);
    Ok(match ctx.out.format {
        Format::Json => render::json(&CountriesJson {
            schema_version: SCHEMA_VERSION,
            total: counts.len(),
            countries: counts.iter().map(|(name, &spots)| CountryJson { name, slug: to_slug(name), spots }).collect(),
        }),
        _ => {
            let out = &ctx.out;
            let mut grid = Grid::new(vec![
                Col::left(out.tr("Country", "Kraj")),
                Col::right(out.tr("Spots", "Spoty")),
                Col::left("Slug").low(),
            ]);
            for (name, count) in &counts {
                grid.push(vec![Val::new(*name), Val::new(count.to_string()), Val::new(to_slug(name))]);
            }
            grid.render(out)
        }
    })
}
