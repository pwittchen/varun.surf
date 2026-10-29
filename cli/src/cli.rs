//! Commands and options (SPEC section 4).

use clap::{Args, Parser, Subcommand, ValueEnum};

pub const VERSION: &str = concat!(env!("CARGO_PKG_VERSION"), " (", env!("VARUN_GIT_SHA"), ")");

#[derive(Debug, Parser)]
#[command(
    name = "varun",
    version = VERSION,
    about = "Kite spot forecasts and live wind from varun.surf",
    long_about = "Kite spot forecasts and live wind from varun.surf.\n\n\
        <SPOT> is a Windguru id (500760), a varun.surf or Windguru URL, a favourite alias, \
        or part of a spot name (jastarnia, mrzezyno).",
    after_help = "Examples:\n  varun wind jastarnia\n  varun windy --country poland\n  varun now\n  varun spot tarifa --model ifs",
    propagate_version = true
)]
pub struct Cli {
    #[command(flatten)]
    pub global: Global,

    #[command(subcommand)]
    pub command: Command,
}

#[derive(Debug, Args)]
pub struct Global {
    /// Backend to talk to, e.g. http://localhost:8080 for local development
    #[arg(long, global = true, value_name = "URL")]
    pub base_url: Option<String>,

    /// Output format [default: table on a terminal, plain when piped]
    #[arg(long, global = true, value_name = "FORMAT", value_parser = ["table", "plain", "json", "md"])]
    pub format: Option<String>,

    /// Shorthand for --format json
    #[arg(long, global = true, conflicts_with = "format")]
    pub json: bool,

    /// Language of spot descriptions and labels [default: from LANG, else en]
    #[arg(long, global = true, value_name = "LANG", value_parser = ["en", "pl"])]
    pub lang: Option<String>,

    /// Display units; --min-wind is always in knots [default: kts]
    #[arg(long, global = true, value_name = "UNITS", value_parser = ["kts", "ms", "kmh", "mph", "bft"])]
    pub units: Option<String>,

    /// How forecast hours are shown: spot, local or an IANA zone [default: spot]
    #[arg(long, global = true, value_name = "TZ")]
    pub tz: Option<String>,

    /// No colours (also honoured: the NO_COLOR environment variable)
    #[arg(long, global = true)]
    pub no_color: bool,

    /// Bypass the on-disk cache of the spots list
    #[arg(long, global = true)]
    pub no_cache: bool,

    /// Only print the answer, no warnings
    #[arg(short, long, global = true, conflicts_with = "verbose")]
    pub quiet: bool,

    /// Log requests and timings to stderr
    #[arg(short, long, global = true)]
    pub verbose: bool,
}

#[derive(Debug, Subcommand)]
pub enum Command {
    /// All spots grouped by country, with live wind where a station reports it
    Spots {
        /// Only spots in this country (name or slug)
        #[arg(long, value_name = "COUNTRY")]
        country: Option<String>,
        /// Only spots with live conditions
        #[arg(long)]
        live: bool,
    },
    /// Full details of one spot: overview, live conditions, forecasts, links
    Spot {
        spot: String,
        /// Forecast model key, e.g. ifs or icon (see --models)
        #[arg(long, value_name = "KEY")]
        model: Option<String>,
        /// List the forecast models this spot has
        #[arg(long, conflicts_with = "model")]
        models: bool,
        /// Hourly rows to show [default: 24]
        #[arg(long, value_name = "N")]
        hours: Option<usize>,
    },
    /// Spots whose name contains the query
    Search { query: String },
    /// Countries with their number of spots and slugs
    Countries,
    /// Spots in one country (same as `spots --country`)
    Country { country: String },
    /// Hour-by-hour wind at one spot, with the windiest hour and kiteable windows
    Wind {
        spot: String,
        /// Hours ahead [default: 72, capped by the forecast]
        #[arg(long, value_name = "N", allow_negative_numbers = true)]
        hours: Option<i64>,
        /// Only hours with at least this wind, in knots
        #[arg(long, value_name = "KTS", allow_negative_numbers = true)]
        min_wind: Option<i64>,
        /// Add temperature, rain, cloud, pressure and waves
        #[arg(long)]
        all: bool,
    },
    /// Where it will blow: spots reaching a wind speed in the hours ahead, strongest first
    Windy {
        /// Wind a spot must reach, in knots [default: 12]
        #[arg(long, value_name = "KTS", allow_negative_numbers = true)]
        min_wind: Option<i64>,
        /// Hours ahead to scan [default: 24]
        #[arg(long, value_name = "N", allow_negative_numbers = true)]
        hours: Option<i64>,
        /// Only spots in this country (name or slug)
        #[arg(long, value_name = "COUNTRY")]
        country: Option<String>,
        /// How many spots to show [default: 20, at most 100]
        #[arg(long, value_name = "N", allow_negative_numbers = true)]
        limit: Option<i64>,
        /// Only scan favourite spots
        #[arg(long, visible_alias = "favourites")]
        favorites: bool,
        #[command(flatten)]
        watch: Watch,
    },
    /// Live conditions at the spots named, the favourites, or the windiest stations
    Now {
        spots: Vec<String>,
        #[command(flatten)]
        watch: Watch,
    },
    /// Backend status: spots, countries, live stations, forecast sweep progress
    Status {
        /// Always show the forecast sweep, not only while it runs
        #[arg(long)]
        forecast: bool,
    },
    /// Favourite spots, kept in the local config
    #[command(visible_alias = "favourites", alias = "favorites")]
    Fav {
        #[command(subcommand)]
        action: FavAction,
    },
    /// Open the spot page in the default browser
    Open { spot: String },
    /// Read and write the config file
    Config {
        #[command(subcommand)]
        action: Option<ConfigAction>,
    },
    /// Print a shell completion script
    Completions { shell: Shell },
    /// Print the man page (roff)
    #[command(hide = true)]
    Man,
}

#[derive(Debug, Args)]
pub struct Watch {
    /// Re-render every SECONDS (at least 60); Ctrl-C exits
    #[arg(long, value_name = "SECONDS", num_args = 0..=1, default_missing_value = "60", require_equals = true)]
    pub watch: Option<u64>,
}

#[derive(Debug, Subcommand)]
pub enum FavAction {
    /// Add a favourite, optionally under an alias usable as <SPOT>
    Add {
        spot: String,
        #[arg(long = "as", value_name = "ALIAS")]
        alias: Option<String>,
    },
    /// Remove a favourite by spot or alias
    #[command(visible_alias = "remove")]
    Rm { spot: String },
    /// List favourites
    #[command(visible_alias = "list")]
    Ls,
}

#[derive(Debug, Subcommand)]
pub enum ConfigAction {
    /// Print one setting, or all of them
    Get { key: Option<String> },
    /// Set a setting (base_url, lang, units, tz, format)
    Set { key: String, value: String },
    /// Remove a setting, falling back to its default
    Unset { key: String },
    /// Print the config file path
    Path,
    /// Open the config file in $VISUAL / $EDITOR
    Edit,
}

#[derive(Debug, Clone, Copy, ValueEnum)]
pub enum Shell {
    Bash,
    Zsh,
    Fish,
}

#[cfg(test)]
mod tests {
    use super::*;
    use clap::CommandFactory;

    #[test]
    fn command_tree_is_valid() {
        Cli::command().debug_assert();
    }

    #[test]
    fn watch_takes_an_optional_value() {
        let cli = Cli::try_parse_from(["varun", "now", "--watch"]).unwrap();
        let Command::Now { watch, .. } = cli.command else { panic!() };
        assert_eq!(watch.watch, Some(60));
        let cli = Cli::try_parse_from(["varun", "now", "--watch=120", "puck"]).unwrap();
        let Command::Now { watch, spots } = cli.command else { panic!() };
        assert_eq!((watch.watch, spots), (Some(120), vec!["puck".to_string()]));
    }

    #[test]
    fn global_options_go_anywhere() {
        let cli = Cli::try_parse_from(["varun", "wind", "puck", "--json", "--units", "ms"]).unwrap();
        assert!(cli.global.json);
        assert_eq!(cli.global.units.as_deref(), Some("ms"));
    }
}
