//! `varun`: kite spot forecasts and live wind from varun.surf in a terminal.
//! See cli/SPEC.md.

use std::io::{IsTerminal, Write};

use clap::Parser;

use varun_cli::api::Client;
use varun_cli::cli::{Cli, Command, ConfigAction, Global};
use varun_cli::commands::Ctx;
use varun_cli::config::{ConfigFile, DEFAULT_BASE_URL, Dirs, Format, Lang, Tz, Units, pick, validate_base_url};
use varun_cli::error::{CliError, Result};
use varun_cli::render::Out;

fn main() {
    let cli = Cli::parse();
    let quiet = cli.global.quiet;
    let code = match run(cli) {
        Ok(()) => 0,
        Err(error) => {
            report(&error, quiet);
            error.exit_code()
        }
    };
    let _ = std::io::stdout().flush();
    std::process::exit(code);
}

fn report(error: &CliError, quiet: bool) {
    eprintln!("varun: {error}");
    if let CliError::Ambiguous { candidates, .. } = error
        && !quiet
    {
        for candidate in candidates {
            eprintln!("  {candidate}");
        }
    }
}

fn run(cli: Cli) -> Result<()> {
    // Nothing to fetch and nothing to configure.
    match &cli.command {
        Command::Completions { shell } => return print(&varun_cli::commands::completions::completions(*shell)?),
        Command::Man => return print(&varun_cli::commands::completions::man()?),
        _ => {}
    }

    let dirs = Dirs::resolve();
    let config_path = dirs.config_file();
    let tolerate_broken_config =
        matches!(cli.command, Command::Config { action: Some(ConfigAction::Path | ConfigAction::Edit) });
    let config = match ConfigFile::load(&config_path) {
        Ok((config, warnings)) => {
            if !cli.global.quiet {
                warnings.iter().for_each(|w| eprintln!("varun: {}: {w}", config_path.display()));
            }
            config
        }
        Err(e) if tolerate_broken_config => {
            eprintln!("varun: {e}");
            ConfigFile::default()
        }
        Err(e) => return Err(e),
    };

    let out = output_settings(&cli.global, &config)?;
    let base_url = pick_base_url(&cli.global, &config)?;
    let client = Client::new(&base_url, dirs.cache.clone(), !cli.global.no_cache, cli.global.verbose)?;
    let mut ctx = Ctx::new(client, out, config, config_path, cli.global.quiet);
    dispatch(&mut ctx, &cli.command)
}

fn dispatch(ctx: &mut Ctx, command: &Command) -> Result<()> {
    use varun_cli::commands::{self, *};
    let text = match command {
        Command::Spots { country, live } => spots::run(ctx, country.as_deref(), *live)?,
        Command::Country { country } => spots::country(ctx, country)?,
        Command::Spot { spot: arg, model, models, hours } => {
            spot::run(ctx, spot::SpotArgs { spot: arg, model: model.as_deref(), models: *models, hours: *hours })?
        }
        Command::Search { query } => search::run(ctx, query)?,
        Command::Countries => countries::run(ctx)?,
        Command::Wind { spot, hours, min_wind, all } => {
            wind::run(ctx, wind::WindArgs { spot, hours: *hours, min_wind: *min_wind, all: *all })?
        }
        Command::Windy { min_wind, hours, country, limit, favorites, watch } => {
            let args = windy::WindyArgs {
                min_wind: *min_wind,
                hours: *hours,
                country: country.clone(),
                limit: *limit,
                favorites: *favorites,
            };
            return commands::watch(ctx, watch.watch, |ctx| windy::run(ctx, &args));
        }
        Command::Now { spots, watch } => {
            return commands::watch(ctx, watch.watch, |ctx| now::run(ctx, spots));
        }
        Command::Status { forecast } => status::run(ctx, *forecast)?,
        Command::Fav { action } => fav::run(ctx, action)?,
        Command::Open { spot } => open::run(ctx, spot)?,
        Command::Config { action } => config::run(ctx, action.as_ref())?,
        Command::Completions { .. } | Command::Man => unreachable!("handled before the context exists"),
    };
    print(&text)
}

fn print(text: &str) -> Result<()> {
    let mut stdout = std::io::stdout().lock();
    match stdout.write_all(text.as_bytes()).and_then(|()| stdout.flush()) {
        // `varun spots | head` closes the pipe early: not an error worth a message.
        Err(e) if e.kind() == std::io::ErrorKind::BrokenPipe => Ok(()),
        Err(e) => Err(CliError::Other(format!("cannot write the output: {e}"))),
        Ok(()) => Ok(()),
    }
}

fn env(name: &str) -> Option<String> {
    std::env::var(name).ok()
}

fn pick_base_url(global: &Global, config: &ConfigFile) -> Result<String> {
    let url = global
        .base_url
        .clone()
        .or_else(|| env("VARUN_BASE_URL").filter(|v| !v.trim().is_empty()))
        .or_else(|| config.base_url.clone())
        .unwrap_or_else(|| DEFAULT_BASE_URL.to_string());
    validate_base_url(url.trim()).map_err(CliError::Usage)?;
    Ok(url.trim().to_string())
}

fn output_settings(global: &Global, config: &ConfigFile) -> Result<Out> {
    let stdout_tty = std::io::stdout().is_terminal();
    let format = if global.json {
        Format::Json
    } else {
        pick::<Format>("format", global.format.as_deref(), env("VARUN_FORMAT"), config.format.as_deref())?
            .unwrap_or(if stdout_tty { Format::Table } else { Format::Plain })
    };
    let lang =
        pick::<Lang>("lang", global.lang.as_deref(), env("VARUN_LANG"), config.lang.as_deref())?.unwrap_or_else(|| {
            ["LC_ALL", "LC_MESSAGES", "LANG"]
                .iter()
                .find_map(|v| env(v).filter(|l| !l.is_empty()))
                .map_or(Lang::En, |l| Lang::from_locale(&l))
        });
    let units = pick::<Units>("units", global.units.as_deref(), env("VARUN_UNITS"), config.units.as_deref())?
        .unwrap_or_default();
    let tz = pick::<Tz>("tz", global.tz.as_deref(), env("VARUN_TZ"), config.tz.as_deref())?.unwrap_or_default();
    if tz != Tz::Spot && !global.quiet {
        eprintln!("varun: the API does not say which time zone a spot is in yet, so hours are shown in spot time");
    }
    let no_color = global.no_color || env("NO_COLOR").is_some_and(|v| !v.is_empty());
    let color = !no_color && stdout_tty && format == Format::Table;
    let width = terminal_size::terminal_size_of(std::io::stdout())
        .map(|(w, _)| w.0)
        .or_else(|| env("COLUMNS").and_then(|c| c.parse().ok()))
        .unwrap_or(120);
    Ok(Out { format, color, width, lang, units })
}
