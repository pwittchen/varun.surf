//! One module per command. Each returns the text to print, so `--watch` can
//! re-render it and the tests can read it.

pub mod completions;
pub mod config;
pub mod countries;
pub mod fav;
pub mod now;
pub mod open;
pub mod search;
pub mod spot;
pub mod spots;
pub mod status;
pub mod wind;
pub mod windy;

use std::io::IsTerminal;
use std::path::PathBuf;
use std::rc::Rc;
use std::time::Duration;

use crate::api::Client;
use crate::api::dto::Spot;
use crate::config::{ConfigFile, Format};
use crate::domain::resolve::{NameMatch, SpotArg, match_name, parse_arg};
use crate::domain::text::to_slug;
use crate::error::{CliError, Result};
use crate::render::Out;

/// Live data refreshes once a minute; re-rendering faster only re-reads the cache.
pub const MIN_WATCH_SECONDS: u64 = 60;

pub struct Ctx {
    pub client: Client,
    pub out: Out,
    pub config: ConfigFile,
    pub config_path: PathBuf,
    /// stdin and stdout are terminals: the spot picker may be shown.
    pub interactive: bool,
    pub quiet: bool,
    spots: Option<Rc<SpotList>>,
}

/// The spots list, parsed, plus each spot's JSON exactly as the backend sent it
/// (in the same order), for `--json` to pass through.
pub struct SpotList {
    pub spots: Vec<Spot>,
    pub raw: Vec<serde_json::Value>,
}

impl Ctx {
    pub fn new(client: Client, out: Out, config: ConfigFile, config_path: PathBuf, quiet: bool) -> Ctx {
        let interactive = std::io::stdin().is_terminal() && std::io::stdout().is_terminal();
        Ctx { client, out, config, config_path, interactive, quiet, spots: None }
    }

    pub fn warn(&self, message: &str) {
        if !self.quiet {
            eprintln!("varun: {message}");
        }
    }

    /// `/api/v1/spots`, fetched at most once per run (and cached on disk for 60 s).
    pub fn spot_list(&mut self) -> Result<Rc<SpotList>> {
        if let Some(list) = &self.spots {
            return Ok(Rc::clone(list));
        }
        let (spots, body) = self.client.spots()?;
        let raw = serde_json::from_str::<Vec<serde_json::Value>>(&body).unwrap_or_default();
        let list = Rc::new(SpotList { spots, raw });
        self.spots = Some(Rc::clone(&list));
        Ok(list)
    }

    /// Drops the in-memory list, so the next render of `--watch` fetches anew.
    pub fn forget_spots(&mut self) {
        self.spots = None;
    }

    /// A `<SPOT>` argument to a spot of the list (SPEC 4.1).
    pub fn resolve(&mut self, arg: &str) -> Result<Spot> {
        let id = self.resolve_id_hint(arg);
        let list = self.spot_list()?;
        match id {
            Some(id) => list.spots.iter().find(|s| s.wg_id == id).cloned().ok_or_else(|| spot_not_found(id)),
            None => self.pick(&list.spots, arg).cloned(),
        }
    }

    /// The wgId an argument names without looking at the list: an id, a URL or a
    /// favourite alias. None for a name.
    pub fn resolve_id_hint(&self, arg: &str) -> Option<i64> {
        match parse_arg(arg) {
            SpotArg::Id(id) => Some(id),
            SpotArg::Name(name) => self.config.favorite_by_alias(&name).map(|f| f.wg_id),
        }
    }

    fn pick<'a>(&self, spots: &'a [Spot], query: &str) -> Result<&'a Spot> {
        match match_name(spots, query) {
            NameMatch::One(spot) => Ok(spot),
            NameMatch::None => Err(CliError::NotFound(format!(
                "No spot found matching '{}'. Use `varun search` or `varun spots` to discover available spots.",
                query.trim()
            ))),
            NameMatch::Many(candidates) => {
                let labels: Vec<String> =
                    candidates.iter().map(|s| format!("{}, {} ({})", s.name, s.country, s.wg_id)).collect();
                if !self.interactive {
                    return Err(CliError::Ambiguous {
                        message: format!(
                            "'{}' matches {} spots; name one more precisely or use its wgId:",
                            query.trim(),
                            candidates.len()
                        ),
                        candidates: labels,
                    });
                }
                let choice = dialoguer::Select::new()
                    .with_prompt(format!("'{}' matches {} spots (Esc cancels)", query.trim(), candidates.len()))
                    .items(&labels)
                    .default(0)
                    .interact_opt()
                    .map_err(|e| CliError::Other(format!("spot picker failed: {e}")))?;
                choice
                    .map(|i| candidates[i])
                    .ok_or_else(|| CliError::Ambiguous { message: "No spot picked.".to_string(), candidates: vec![] })
            }
        }
    }

    /// The country a name or slug refers to, as spelled in the spots list.
    pub fn find_country(&mut self, input: &str) -> Result<String> {
        let slug = to_slug(input.trim());
        let list = self.spot_list()?;
        list.spots
            .iter()
            .map(|s| &s.country)
            .find(|c| to_slug(c) == slug)
            .cloned()
            .ok_or_else(|| country_not_found(input))
    }

    pub fn is_md(&self) -> bool {
        self.out.format == Format::Md
    }

    pub fn is_json(&self) -> bool {
        self.out.format == Format::Json
    }
}

pub fn spot_not_found(wg_id: i64) -> CliError {
    CliError::NotFound(format!(
        "No spot found for wgId={wg_id}. Use `varun spots` or `varun search` to discover available spots."
    ))
}

pub fn country_not_found(input: &str) -> CliError {
    CliError::NotFound(format!(
        "No country found for '{}'. Use `varun countries` to see available countries.",
        input.trim()
    ))
}

pub fn empty_check(value: &str, message: &str) -> Result<()> {
    if value.trim().is_empty() { Err(CliError::Usage(message.to_string())) } else { Ok(()) }
}

/// Renders once, or with `--watch` clears the screen and renders again every
/// `seconds` until Ctrl-C. A failure on the first render ends the command; later
/// ones are shown and the next tick tries again.
pub fn watch(ctx: &mut Ctx, seconds: Option<u64>, mut render: impl FnMut(&mut Ctx) -> Result<String>) -> Result<()> {
    let Some(seconds) = seconds else {
        print!("{}", render(ctx)?);
        return Ok(());
    };
    if ctx.is_json() || ctx.is_md() {
        return Err(CliError::Usage("--watch works with the table and plain formats only".to_string()));
    }
    let seconds = if seconds < MIN_WATCH_SECONDS {
        ctx.warn(&format!("--watch is at least {MIN_WATCH_SECONDS} s: live data refreshes once a minute"));
        MIN_WATCH_SECONDS
    } else {
        seconds
    };
    let clear = std::io::stdout().is_terminal();
    let mut first = true;
    loop {
        ctx.forget_spots();
        match render(ctx) {
            Ok(text) => {
                if clear {
                    print!("\x1b[2J\x1b[H");
                }
                print!("{text}");
                let stamp = chrono::Local::now().format("%H:%M:%S");
                eprintln!(
                    "{}",
                    ctx.out.paint(
                        &format!("updated {stamp}, every {seconds} s - Ctrl-C exits"),
                        crate::render::Style::Dim
                    )
                );
            }
            Err(e) if first => return Err(e),
            Err(e) => eprintln!("varun: {e}"),
        }
        first = false;
        std::thread::sleep(Duration::from_secs(seconds));
    }
}
