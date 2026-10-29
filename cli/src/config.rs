//! The config file, the state/cache directories and how flags, environment, config
//! and defaults combine (flag > env > config > default, SPEC 6.1).

use std::fs;
use std::path::{Path, PathBuf};
use std::str::FromStr;

use serde::{Deserialize, Serialize};

use crate::error::{CliError, Result};

pub const DEFAULT_BASE_URL: &str = "https://varun.surf";

/// Keys `varun config set` accepts, in the order `config get` prints them.
pub const KEYS: [&str; 5] = ["base_url", "lang", "units", "tz", "format"];

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Format {
    Table,
    Plain,
    Json,
    Md,
}

impl FromStr for Format {
    type Err = String;
    fn from_str(s: &str) -> std::result::Result<Self, String> {
        match s.trim().to_ascii_lowercase().as_str() {
            "table" => Ok(Format::Table),
            "plain" => Ok(Format::Plain),
            "json" => Ok(Format::Json),
            "md" | "markdown" => Ok(Format::Md),
            other => Err(format!("unknown format '{other}' (expected table, plain, json or md)")),
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Default)]
pub enum Lang {
    #[default]
    En,
    Pl,
}

impl FromStr for Lang {
    type Err = String;
    fn from_str(s: &str) -> std::result::Result<Self, String> {
        match s.trim().to_ascii_lowercase().as_str() {
            "en" => Ok(Lang::En),
            "pl" => Ok(Lang::Pl),
            other => Err(format!("unknown language '{other}' (expected en or pl)")),
        }
    }
}

impl Lang {
    /// The language a POSIX locale names (`pl_PL.UTF-8` → Polish), English otherwise.
    pub fn from_locale(locale: &str) -> Lang {
        if locale.to_ascii_lowercase().starts_with("pl") { Lang::Pl } else { Lang::En }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Default)]
pub enum Units {
    #[default]
    Kts,
    Ms,
    Kmh,
    Mph,
    Bft,
}

impl FromStr for Units {
    type Err = String;
    fn from_str(s: &str) -> std::result::Result<Self, String> {
        match s.trim().to_ascii_lowercase().as_str() {
            "kts" | "kt" | "knots" => Ok(Units::Kts),
            "ms" | "m/s" => Ok(Units::Ms),
            "kmh" | "km/h" => Ok(Units::Kmh),
            "mph" => Ok(Units::Mph),
            "bft" | "beaufort" => Ok(Units::Bft),
            other => Err(format!("unknown units '{other}' (expected kts, ms, kmh, mph or bft)")),
        }
    }
}

/// How forecast hours are shown. Only `spot` (the hours as served) can be honoured
/// until the API exposes a spot's time zone - SPEC open question Q1.
#[derive(Debug, Clone, PartialEq, Eq, Default)]
pub enum Tz {
    #[default]
    Spot,
    Local,
    Named(String),
}

impl FromStr for Tz {
    type Err = String;
    fn from_str(s: &str) -> std::result::Result<Self, String> {
        let s = s.trim();
        match s.to_ascii_lowercase().as_str() {
            "spot" => Ok(Tz::Spot),
            "local" => Ok(Tz::Local),
            "" => Err("time zone must not be empty".to_string()),
            _ if s.contains('/') || s.eq_ignore_ascii_case("utc") => Ok(Tz::Named(s.to_string())),
            _ => Err(format!("unknown time zone '{s}' (expected spot, local or an IANA name)")),
        }
    }
}

#[derive(Debug, Clone, Default, Serialize, Deserialize, PartialEq)]
pub struct Favorite {
    pub wg_id: i64,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub alias: Option<String>,
}

/// `config.toml` as stored. Every key is optional.
#[derive(Debug, Clone, Default, Serialize, Deserialize, PartialEq)]
pub struct ConfigFile {
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub base_url: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub lang: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub units: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub tz: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub format: Option<String>,
    #[serde(default, skip_serializing_if = "Vec::is_empty")]
    pub favorites: Vec<Favorite>,
}

impl ConfigFile {
    /// Reads the config, warning about (not failing on) keys it does not know. A
    /// missing file is an empty config.
    pub fn load(path: &Path) -> Result<(ConfigFile, Vec<String>)> {
        let text = match fs::read_to_string(path) {
            Ok(text) => text,
            Err(e) if e.kind() == std::io::ErrorKind::NotFound => return Ok(Default::default()),
            Err(e) => return Err(CliError::Other(format!("cannot read {}: {e}", path.display()))),
        };
        Self::parse(&text).map_err(|e| CliError::Usage(format!("{}: {e}", path.display())))
    }

    pub fn parse(text: &str) -> std::result::Result<(ConfigFile, Vec<String>), String> {
        let table: toml::Table = text.parse().map_err(|e: toml::de::Error| e.message().to_string())?;
        let mut warnings = Vec::new();
        for key in table.keys() {
            if !KEYS.contains(&key.as_str()) && key != "favorites" {
                warnings.push(format!("unknown config key '{key}' ignored"));
            }
        }
        let config: ConfigFile =
            toml::Value::Table(table).try_into().map_err(|e: toml::de::Error| e.message().to_string())?;
        Ok((config, warnings))
    }

    pub fn save(&self, path: &Path) -> Result<()> {
        if let Some(dir) = path.parent() {
            fs::create_dir_all(dir).map_err(|e| CliError::Other(format!("cannot create {}: {e}", dir.display())))?;
        }
        let text =
            toml::to_string_pretty(self).map_err(|e| CliError::Other(format!("cannot serialise config: {e}")))?;
        fs::write(path, text).map_err(|e| CliError::Other(format!("cannot write {}: {e}", path.display())))
    }

    pub fn get(&self, key: &str) -> Result<Option<String>> {
        Ok(match key {
            "base_url" => self.base_url.clone(),
            "lang" => self.lang.clone(),
            "units" => self.units.clone(),
            "tz" => self.tz.clone(),
            "format" => self.format.clone(),
            _ => return Err(unknown_key(key)),
        })
    }

    /// Sets a key after checking its value parses, so a typo is caught here rather
    /// than on every later run.
    pub fn set(&mut self, key: &str, value: &str) -> Result<()> {
        let check = |r: std::result::Result<(), String>| r.map_err(CliError::Usage);
        let value = value.trim().to_string();
        match key {
            "base_url" => {
                check(validate_base_url(&value))?;
                self.base_url = Some(value);
            }
            "lang" => {
                check(value.parse::<Lang>().map(|_| ()))?;
                self.lang = Some(value);
            }
            "units" => {
                check(value.parse::<Units>().map(|_| ()))?;
                self.units = Some(value);
            }
            "tz" => {
                check(value.parse::<Tz>().map(|_| ()))?;
                self.tz = Some(value);
            }
            "format" => {
                check(value.parse::<Format>().map(|_| ()))?;
                self.format = Some(value);
            }
            _ => return Err(unknown_key(key)),
        }
        Ok(())
    }

    pub fn unset(&mut self, key: &str) -> Result<()> {
        match key {
            "base_url" => self.base_url = None,
            "lang" => self.lang = None,
            "units" => self.units = None,
            "tz" => self.tz = None,
            "format" => self.format = None,
            _ => return Err(unknown_key(key)),
        }
        Ok(())
    }

    pub fn favorite_by_alias(&self, alias: &str) -> Option<&Favorite> {
        self.favorites.iter().find(|f| f.alias.as_deref().is_some_and(|a| a.eq_ignore_ascii_case(alias.trim())))
    }
}

fn unknown_key(key: &str) -> CliError {
    CliError::Usage(format!("unknown config key '{key}' (expected one of: {})", KEYS.join(", ")))
}

pub fn validate_base_url(url: &str) -> std::result::Result<(), String> {
    if url.starts_with("http://") || url.starts_with("https://") {
        Ok(())
    } else {
        Err(format!("base URL '{url}' must start with http:// or https://"))
    }
}

/// Where the config and cache live. `VARUN_CONFIG_DIR` / `VARUN_CACHE_DIR` override
/// the platform defaults, which is what the tests use.
#[derive(Debug, Clone)]
pub struct Dirs {
    pub config: PathBuf,
    pub cache: PathBuf,
}

impl Dirs {
    pub fn resolve() -> Dirs {
        let base = directories::BaseDirs::new();
        // ~/.config/varun on Linux; ~/Library/Application Support/surf.varun.cli on macOS
        let app = if cfg!(target_os = "macos") { "surf.varun.cli" } else { "varun" };
        let fallback = || std::env::temp_dir().join("varun");
        let config = env_path("VARUN_CONFIG_DIR")
            .unwrap_or_else(|| base.as_ref().map(|b| b.config_dir().join(app)).unwrap_or_else(fallback));
        let cache = env_path("VARUN_CACHE_DIR")
            .unwrap_or_else(|| base.as_ref().map(|b| b.cache_dir().join(app)).unwrap_or_else(fallback));
        Dirs { config, cache }
    }

    pub fn config_file(&self) -> PathBuf {
        self.config.join("config.toml")
    }
}

fn env_path(name: &str) -> Option<PathBuf> {
    std::env::var_os(name).filter(|v| !v.is_empty()).map(PathBuf::from)
}

/// One setting from its sources, highest precedence first.
pub fn pick<T: FromStr<Err = String>>(
    name: &str,
    flag: Option<&str>,
    env: Option<String>,
    config: Option<&str>,
) -> Result<Option<T>> {
    let (source, value) = if let Some(v) = flag {
        ("flag", v.to_string())
    } else if let Some(v) = env.filter(|v| !v.trim().is_empty()) {
        ("environment", v)
    } else if let Some(v) = config {
        ("config file", v.to_string())
    } else {
        return Ok(None);
    };
    value.parse::<T>().map(Some).map_err(|e| CliError::Usage(format!("{name} from {source}: {e}")))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn flag_beats_env_beats_config() {
        let units: Option<Units> = pick("units", Some("ms"), Some("kmh".into()), Some("mph")).unwrap();
        assert_eq!(units, Some(Units::Ms));
        let units: Option<Units> = pick("units", None, Some("kmh".into()), Some("mph")).unwrap();
        assert_eq!(units, Some(Units::Kmh));
        let units: Option<Units> = pick("units", None, None, Some("mph")).unwrap();
        assert_eq!(units, Some(Units::Mph));
        let units: Option<Units> = pick("units", None, Some("  ".into()), None).unwrap();
        assert_eq!(units, None);
    }

    #[test]
    fn bad_value_names_its_source() {
        let err = pick::<Units>("units", None, Some("furlongs".into()), None).unwrap_err();
        assert!(err.to_string().contains("environment"));
        assert_eq!(err.exit_code(), 2);
    }

    #[test]
    fn unknown_keys_warn_but_load() {
        let (config, warnings) =
            ConfigFile::parse("lang = \"pl\"\ncolour = \"blue\"\n[[favorites]]\nwg_id = 500760\nalias = \"home\"\n")
                .unwrap();
        assert_eq!(config.lang.as_deref(), Some("pl"));
        assert_eq!(warnings, vec!["unknown config key 'colour' ignored"]);
        assert_eq!(config.favorite_by_alias("HOME").unwrap().wg_id, 500760);
    }

    #[test]
    fn set_validates_values() {
        let mut config = ConfigFile::default();
        assert!(config.set("units", "bft").is_ok());
        assert!(config.set("units", "parsecs").is_err());
        assert!(config.set("base_url", "localhost:8080").is_err());
        assert!(config.set("nope", "x").is_err());
        config.unset("units").unwrap();
        assert_eq!(config.get("units").unwrap(), None);
    }

    #[test]
    fn round_trips_through_toml() {
        let config = ConfigFile {
            lang: Some("pl".into()),
            favorites: vec![Favorite { wg_id: 1, alias: Some("a".into()) }, Favorite { wg_id: 2, alias: None }],
            ..Default::default()
        };
        let text = toml::to_string_pretty(&config).unwrap();
        assert_eq!(ConfigFile::parse(&text).unwrap().0, config);
    }

    #[test]
    fn language_from_locale() {
        assert_eq!(Lang::from_locale("pl_PL.UTF-8"), Lang::Pl);
        assert_eq!(Lang::from_locale("en_US.UTF-8"), Lang::En);
        assert_eq!(Lang::from_locale("C"), Lang::En);
    }

    #[test]
    fn time_zones() {
        assert_eq!("spot".parse::<Tz>().unwrap(), Tz::Spot);
        assert_eq!("Europe/Warsaw".parse::<Tz>().unwrap(), Tz::Named("Europe/Warsaw".into()));
        assert!("mars".parse::<Tz>().is_err());
    }
}
