//! `varun open <SPOT>`: the spot page in the default browser.

use std::process::{Command, Stdio};

use crate::commands::Ctx;
use crate::error::{CliError, Result};

pub fn run(ctx: &mut Ctx, arg: &str) -> Result<String> {
    let id = match ctx.resolve_id_hint(arg) {
        Some(id) => id,
        None => ctx.resolve(arg)?.wg_id,
    };
    let url = format!("{}/spot/{id}", ctx.client.base_url());
    let opener = if cfg!(target_os = "macos") { "open" } else { "xdg-open" };
    let opened =
        Command::new(opener).arg(&url).stdin(Stdio::null()).stdout(Stdio::null()).stderr(Stdio::null()).status();
    match opened {
        Ok(status) if status.success() => Ok(format!("{url}\n")),
        _ => Err(CliError::Other(format!("could not start `{opener}`; open {url} by hand"))),
    }
}
