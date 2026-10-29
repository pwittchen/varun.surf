//! `varun config [get|set|unset|path|edit]`.

use std::process::Command;

use crate::cli::ConfigAction;
use crate::commands::Ctx;
use crate::config::{ConfigFile, KEYS};
use crate::error::{CliError, Result};

pub fn run(ctx: &mut Ctx, action: Option<&ConfigAction>) -> Result<String> {
    match action {
        None | Some(ConfigAction::Get { key: None }) => {
            let mut text = String::new();
            for key in KEYS {
                if let Some(value) = ctx.config.get(key)? {
                    text.push_str(&format!("{key} = {value}\n"));
                }
            }
            if !ctx.config.favorites.is_empty() {
                text.push_str(&format!("favorites = {} (see `varun fav ls`)\n", ctx.config.favorites.len()));
            }
            if text.is_empty() {
                ctx.warn(&format!("nothing set in {}", ctx.config_path.display()));
            }
            Ok(text)
        }
        Some(ConfigAction::Get { key: Some(key) }) => match ctx.config.get(key)? {
            Some(value) => Ok(format!("{value}\n")),
            None => Err(CliError::NotFound(format!("{key} is not set"))),
        },
        Some(ConfigAction::Set { key, value }) => {
            ctx.config.set(key, value)?;
            ctx.config.save(&ctx.config_path)?;
            Ok(String::new())
        }
        Some(ConfigAction::Unset { key }) => {
            ctx.config.unset(key)?;
            ctx.config.save(&ctx.config_path)?;
            Ok(String::new())
        }
        Some(ConfigAction::Path) => Ok(format!("{}\n", ctx.config_path.display())),
        Some(ConfigAction::Edit) => edit(ctx),
    }
}

fn edit(ctx: &mut Ctx) -> Result<String> {
    if !ctx.config_path.exists() {
        ctx.config.save(&ctx.config_path)?;
    }
    let editor = ["VISUAL", "EDITOR"]
        .iter()
        .find_map(|v| std::env::var(v).ok().filter(|e| !e.trim().is_empty()))
        .unwrap_or_else(|| "vi".to_string());
    // $EDITOR may carry arguments ("code --wait"), so it goes through the shell.
    let status = Command::new("sh")
        .arg("-c")
        .arg(format!("{editor} \"$1\""))
        .arg("sh")
        .arg(&ctx.config_path)
        .status()
        .map_err(|e| CliError::Other(format!("cannot start {editor}: {e}")))?;
    if !status.success() {
        return Err(CliError::Other(format!("{editor} exited with {status}")));
    }
    // Catch a broken edit now rather than on the next run.
    ConfigFile::load(&ctx.config_path)?;
    Ok(String::new())
}
