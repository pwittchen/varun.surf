//! `varun search <QUERY>`: `find_spot_by_name`.

use serde::Serialize;

use crate::commands::Ctx;
use crate::config::Format;
use crate::domain::resolve;
use crate::error::{CliError, Result};
use crate::render::{self, Col, Grid, SCHEMA_VERSION, Val, md};

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct SearchJson<'a> {
    schema_version: u32,
    query: &'a str,
    spots: Vec<SpotRef<'a>>,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct SpotRef<'a> {
    name: &'a str,
    country: &'a str,
    wg_id: i64,
}

pub fn run(ctx: &mut Ctx, query: &str) -> Result<String> {
    if query.trim().is_empty() {
        return Err(CliError::Usage("Query must not be empty.".to_string()));
    }
    let list = ctx.spot_list()?;
    let matches = resolve::search(&list.spots, query);
    if matches.is_empty() {
        return Err(CliError::NotFound(format!("No spots found matching '{query}'.")));
    }
    Ok(match ctx.out.format {
        Format::Md => md::search(query, &matches),
        Format::Json => render::json(&SearchJson {
            schema_version: SCHEMA_VERSION,
            query,
            spots: matches.iter().map(|s| SpotRef { name: &s.name, country: &s.country, wg_id: s.wg_id }).collect(),
        }),
        _ => {
            let out = &ctx.out;
            let mut grid = Grid::new(vec![Col::left("Spot"), Col::left(out.tr("Country", "Kraj")), Col::right("wgId")]);
            for spot in matches {
                grid.push(vec![Val::new(&spot.name), Val::new(&spot.country), Val::new(spot.wg_id.to_string())]);
            }
            grid.render(out)
        }
    })
}
