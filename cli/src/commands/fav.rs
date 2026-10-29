//! `varun fav add|rm|ls`: favourites, kept in the local config only.

use serde::Serialize;

use crate::cli::FavAction;
use crate::commands::Ctx;
use crate::config::{Favorite, Format};
use crate::domain::resolve::{SpotArg, parse_arg};
use crate::error::{CliError, Result};
use crate::render::{self, Col, Grid, SCHEMA_VERSION, Val};

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct FavoritesJson<'a> {
    schema_version: u32,
    favorites: Vec<FavoriteJson<'a>>,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct FavoriteJson<'a> {
    wg_id: i64,
    alias: Option<&'a str>,
    name: Option<String>,
    country: Option<String>,
}

pub fn run(ctx: &mut Ctx, action: &FavAction) -> Result<String> {
    match action {
        FavAction::Add { spot, alias } => add(ctx, spot, alias.as_deref()),
        FavAction::Rm { spot } => remove(ctx, spot),
        FavAction::Ls => list(ctx),
    }
}

fn add(ctx: &mut Ctx, arg: &str, alias: Option<&str>) -> Result<String> {
    let alias = alias.map(str::trim).filter(|a| !a.is_empty());
    if let Some(alias) = alias
        && matches!(parse_arg(alias), SpotArg::Id(_))
    {
        return Err(CliError::Usage(format!("Alias '{alias}' would read as a spot id; pick a name.")));
    }
    let spot = ctx.resolve(arg)?;
    if let Some(alias) = alias
        && let Some(other) = ctx.config.favorite_by_alias(alias).filter(|f| f.wg_id != spot.wg_id)
    {
        return Err(CliError::Usage(format!("Alias '{alias}' is already used by wgId={}.", other.wg_id)));
    }
    match ctx.config.favorites.iter_mut().find(|f| f.wg_id == spot.wg_id) {
        Some(existing) => {
            if alias.is_some() {
                existing.alias = alias.map(str::to_string);
            }
        }
        None => ctx.config.favorites.push(Favorite { wg_id: spot.wg_id, alias: alias.map(str::to_string) }),
    }
    ctx.config.save(&ctx.config_path)?;
    let named = alias.map(|a| format!(" as '{a}'")).unwrap_or_default();
    ctx.warn(&format!("added {}, {} (wgId={}){named} to favourites", spot.name, spot.country, spot.wg_id));
    Ok(String::new())
}

fn remove(ctx: &mut Ctx, arg: &str) -> Result<String> {
    let id = match ctx.resolve_id_hint(arg) {
        Some(id) => id,
        None => ctx.resolve(arg)?.wg_id,
    };
    let before = ctx.config.favorites.len();
    ctx.config.favorites.retain(|f| f.wg_id != id);
    if ctx.config.favorites.len() == before {
        return Err(CliError::NotFound(format!("wgId={id} is not a favourite. See `varun fav ls`.")));
    }
    ctx.config.save(&ctx.config_path)?;
    ctx.warn(&format!("removed wgId={id} from favourites"));
    Ok(String::new())
}

fn list(ctx: &mut Ctx) -> Result<String> {
    let favorites = ctx.config.favorites.clone();
    if favorites.is_empty() {
        if ctx.out.format == Format::Json {
            return Ok(render::json(&FavoritesJson { schema_version: SCHEMA_VERSION, favorites: vec![] }));
        }
        ctx.warn("no favourites yet: add one with `varun fav add <SPOT> [--as ALIAS]`");
        return Ok(String::new());
    }
    // Names are a nicety: the favourites themselves need no network.
    let list = match ctx.spot_list() {
        Ok(list) => Some(list),
        Err(e) => {
            ctx.warn(&format!("spot names unavailable: {e}"));
            None
        }
    };
    let lookup = |id: i64| list.as_ref().and_then(|l| l.spots.iter().find(|s| s.wg_id == id));
    let out = &ctx.out;
    Ok(match out.format {
        Format::Json => render::json(&FavoritesJson {
            schema_version: SCHEMA_VERSION,
            favorites: favorites
                .iter()
                .map(|f| FavoriteJson {
                    wg_id: f.wg_id,
                    alias: f.alias.as_deref(),
                    name: lookup(f.wg_id).map(|s| s.name.clone()),
                    country: lookup(f.wg_id).map(|s| s.country.clone()),
                })
                .collect(),
        }),
        Format::Md => favorites
            .iter()
            .map(|f| {
                let spot = lookup(f.wg_id).map_or_else(String::new, |s| format!("{}, {} ", s.name, s.country));
                let alias = f.alias.as_deref().map(|a| format!(" — alias `{a}`")).unwrap_or_default();
                format!("- {spot}(wgId={}){alias}\n", f.wg_id)
            })
            .collect(),
        _ => {
            let mut grid = Grid::new(vec![
                Col::left("Alias"),
                Col::left("Spot"),
                Col::left(out.tr("Country", "Kraj")).low(),
                Col::right("wgId"),
            ]);
            for f in &favorites {
                let spot = lookup(f.wg_id);
                grid.push(vec![
                    Val::new(f.alias.clone().unwrap_or_default()),
                    Val::new(spot.map_or("?", |s| s.name.as_str())),
                    Val::new(spot.map_or("", |s| s.country.as_str())),
                    Val::new(f.wg_id.to_string()),
                ]);
            }
            grid.render(out)
        }
    })
}
