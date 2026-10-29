//! End to end: the `varun` binary against a mock backend serving `tests/fixtures`,
//! asserting stdout, stderr and the exit code. No test touches the real varun.surf.

use std::collections::HashMap;
use std::fs;
use std::io::{BufRead, BufReader, Read, Write};
use std::net::{TcpListener, TcpStream};
use std::path::{Path, PathBuf};
use std::sync::{Arc, Mutex};

use assert_cmd::Command;
use predicates::prelude::*;
use tempfile::TempDir;

/// A cookie valid for decades: the CLI only reads the expiry before the dot.
const COOKIE: &str = "4102444800.test-signature";

#[derive(Debug, Clone)]
struct Request {
    path: String,
    cookie: Option<String>,
    headers: HashMap<String, String>,
}

type Override = Box<dyn Fn(&Request, usize) -> Option<(u16, String)> + Send>;

/// A tiny HTTP/1.1 server: one thread, one request per connection.
struct Backend {
    url: String,
    requests: Arc<Mutex<Vec<Request>>>,
    overrides: Arc<Mutex<Vec<(String, Override)>>>,
}

impl Backend {
    fn start() -> Backend {
        let listener = TcpListener::bind("127.0.0.1:0").unwrap();
        let url = format!("http://{}", listener.local_addr().unwrap());
        let requests = Arc::new(Mutex::new(Vec::new()));
        let overrides: Arc<Mutex<Vec<(String, Override)>>> = Arc::new(Mutex::new(Vec::new()));
        let (log, rules) = (Arc::clone(&requests), Arc::clone(&overrides));
        std::thread::spawn(move || {
            for stream in listener.incoming().flatten() {
                serve(stream, &log, &rules);
            }
        });
        Backend { url, requests, overrides }
    }

    /// Answers `path` with what `rule` returns (given the request and how many
    /// times this path was asked before), falling back to the fixtures on None.
    fn route(&self, path: &str, rule: impl Fn(&Request, usize) -> Option<(u16, String)> + Send + 'static) {
        self.overrides.lock().unwrap().push((path.to_string(), Box::new(rule)));
    }

    fn hits(&self, path: &str) -> usize {
        self.requests.lock().unwrap().iter().filter(|r| r.path == path).count()
    }

    fn paths(&self) -> Vec<String> {
        self.requests.lock().unwrap().iter().map(|r| r.path.clone()).collect()
    }

    fn last(&self, path: &str) -> Request {
        self.requests.lock().unwrap().iter().rev().find(|r| r.path == path).cloned().unwrap()
    }
}

fn serve(mut stream: TcpStream, log: &Mutex<Vec<Request>>, rules: &Mutex<Vec<(String, Override)>>) {
    let mut reader = BufReader::new(stream.try_clone().unwrap());
    let mut line = String::new();
    if reader.read_line(&mut line).is_err() || line.is_empty() {
        return;
    }
    let path = line.split_whitespace().nth(1).unwrap_or("/").to_string();
    let mut headers = HashMap::new();
    loop {
        let mut header = String::new();
        if reader.read_line(&mut header).is_err() || header.trim().is_empty() {
            break;
        }
        if let Some((k, v)) = header.split_once(':') {
            headers.insert(k.trim().to_ascii_lowercase(), v.trim().to_string());
        }
    }
    let cookie = headers
        .get("cookie")
        .and_then(|c| c.split(';').find_map(|p| p.trim().strip_prefix("SESSION=").map(str::to_string)));
    let request = Request { path: path.clone(), cookie, headers };
    let seen = log.lock().unwrap().iter().filter(|r| r.path == path).count();
    log.lock().unwrap().push(request.clone());

    let custom = rules.lock().unwrap().iter().find(|(p, _)| *p == path).and_then(|(_, rule)| rule(&request, seen));
    let (status, extra, body) = match custom {
        Some((status, body)) => (status, String::new(), body),
        None => default_response(&request),
    };
    let reason = match status {
        200 => "OK",
        204 => "No Content",
        401 => "Unauthorized",
        404 => "Not Found",
        429 => "Too Many Requests",
        503 => "Service Unavailable",
        _ => "Status",
    };
    let response = format!(
        "HTTP/1.1 {status} {reason}\r\nContent-Length: {}\r\nConnection: close\r\n{extra}\r\n{body}",
        body.len()
    );
    let _ = stream.write_all(response.as_bytes());
    let _ = stream.flush();
    let mut sink = [0u8; 1];
    let _ = stream.set_read_timeout(Some(std::time::Duration::from_millis(5)));
    let _ = stream.read(&mut sink);
}

fn fixture(name: &str) -> String {
    fs::read_to_string(fixtures().join(name)).unwrap()
}

fn fixtures() -> PathBuf {
    PathBuf::from(env!("CARGO_MANIFEST_DIR")).join("tests/fixtures")
}

/// The backend as the fixtures captured it, with the session cookie enforced on
/// `/api/v1` like `SessionAuthenticationFilter` does.
fn default_response(request: &Request) -> (u16, String, String) {
    let path = request.path.as_str();
    if path == "/api/v1/session" {
        return (204, format!("Set-Cookie: SESSION={COOKIE}; Path=/; HttpOnly; SameSite=Lax\r\n"), String::new());
    }
    if path == "/api/v1/health" {
        return (200, String::new(), r#"{"status":"UP"}"#.to_string());
    }
    if path.starts_with("/api/v1/") && request.cookie.as_deref() != Some(COOKIE) {
        return (401, String::new(), String::new());
    }
    let (route, query) = path.split_once('?').unwrap_or((path, ""));
    let file = match route {
        "/api/v1/spots" => "spots.json".to_string(),
        "/api/v1/wind" if query.contains("hours=48") => "wind-48.json".to_string(),
        "/api/v1/wind" => "wind-24.json".to_string(),
        "/api/v1/status" => "status.json".to_string(),
        "/api/v1/status/forecast" => "status-forecast.json".to_string(),
        "/llms/countries/poland.md" => "llms-country-poland.md".to_string(),
        "/llms/countries/czech-republic.md" => "llms-country-czech-republic.md".to_string(),
        "/llms/wind.md" if query.contains("country=poland") => "llms-windy-poland.md".to_string(),
        r if r.starts_with("/api/v1/forecast/") => format!("forecast-{}.json", &r[17..]),
        r if r.starts_with("/api/v1/spots/") => {
            format!("spot-{}.json", r[14..].split('/').next().unwrap())
        }
        r if r.starts_with("/llms/spots/") && r.ends_with("/wind.md") => {
            let id = &r[12..r.len() - 8];
            match query {
                "" => format!("llms-wind-{id}.md"),
                "hours=12&minWind=12" => format!("llms-wind-{id}-h12-m12.md"),
                _ => String::new(),
            }
        }
        r if r.starts_with("/llms/spots/") => format!("llms-spot-{}", &r[12..]),
        _ => String::new(),
    };
    match fs::read_to_string(fixtures().join(&file)) {
        Ok(body) if !file.is_empty() => (200, String::new(), body),
        _ => (404, String::new(), String::new()),
    }
}

/// The binary, pointed at the backend, with its own config and cache dirs.
struct Env {
    backend: Backend,
    dirs: TempDir,
}

impl Env {
    fn new() -> Env {
        Env { backend: Backend::start(), dirs: TempDir::new().unwrap() }
    }

    fn cmd(&self) -> Command {
        let mut cmd = Command::cargo_bin("varun").unwrap();
        cmd.env_clear()
            .env("PATH", std::env::var("PATH").unwrap_or_default())
            .env("HOME", self.dirs.path())
            .env("VARUN_BASE_URL", &self.backend.url)
            .env("VARUN_CONFIG_DIR", self.dirs.path().join("config"))
            .env("VARUN_CACHE_DIR", self.dirs.path().join("cache"))
            .env("LANG", "en_US.UTF-8");
        // `cargo llvm-cov` measures the binary through these
        for (key, value) in std::env::vars().filter(|(k, _)| k.starts_with("LLVM_") || k.starts_with("CARGO_LLVM_COV"))
        {
            cmd.env(key, value);
        }
        cmd
    }

    fn config_dir(&self) -> &Path {
        self.dirs.path()
    }
}

fn stdout_of(cmd: &mut Command) -> String {
    let output = cmd.output().unwrap();
    assert!(output.status.success(), "failed: {}", String::from_utf8_lossy(&output.stderr));
    String::from_utf8(output.stdout).unwrap()
}

// ---- formats ----------------------------------------------------------------

#[test]
fn md_is_the_server_document_verbatim_and_needs_no_session() {
    let env = Env::new();
    let text = stdout_of(env.cmd().args(["wind", "500760", "--format", "md"]));
    assert_eq!(text, fixture("llms-wind-500760.md"));
    let text = stdout_of(env.cmd().args(["wind", "500760", "--hours", "12", "--min-wind", "12", "--format", "md"]));
    assert_eq!(text, fixture("llms-wind-500760-h12-m12.md"));
    assert_eq!(env.backend.hits("/api/v1/session"), 0);
}

#[test]
fn piped_output_is_plain_tab_separated_text() {
    let env = Env::new();
    let text = stdout_of(env.cmd().args(["wind", "jastarnia"]));
    let mut lines = text.lines();
    assert_eq!(lines.next(), Some("Hour\tWind (kts)\tGusts (kts)\tDir"));
    assert_eq!(lines.count(), 72, "72 hours by default");
    assert!(!text.contains('\u{1b}'));
}

#[test]
fn table_without_a_terminal_carries_no_ansi() {
    let env = Env::new();
    let text = stdout_of(env.cmd().args(["windy", "--country", "poland", "--format", "table"]));
    assert!(text.contains("Spots with at least 12 kts in the next 24 h · poland"));
    assert!(text.contains('│'));
    assert!(!text.contains('\u{1b}'));
}

#[test]
fn units_change_the_display_only() {
    let env = Env::new();
    let kts = stdout_of(env.cmd().args(["wind", "500760", "--hours", "1"]));
    let kmh = stdout_of(env.cmd().args(["wind", "500760", "--hours", "1", "--units", "kmh"]));
    assert!(kmh.starts_with("Hour\tWind (km/h)\tGusts (km/h)\tDir\n"));
    let knots: f64 = kts.lines().nth(1).unwrap().split('\t').nth(1).unwrap().parse().unwrap();
    let kilometres: f64 = kmh.lines().nth(1).unwrap().split('\t').nth(1).unwrap().parse().unwrap();
    assert!((kilometres - knots * 1.852).abs() <= 1.0);
}

#[test]
fn wind_json_follows_its_schema() {
    let env = Env::new();
    let text = stdout_of(env.cmd().args(["wind", "500760", "--hours", "12", "--min-wind", "12", "--json"]));
    let json: serde_json::Value = serde_json::from_str(&text).unwrap();
    assert_eq!(json["schemaVersion"], 1);
    assert_eq!(json["spot"]["wgId"], 500760);
    assert_eq!(json["hours"], 12);
    assert_eq!(json["minWind"], 12);
    assert!(json["summary"]["peak"]["wind"].is_number());
    assert!(json["forecast"].as_array().unwrap().iter().all(|f| f["wind"].as_f64().unwrap() >= 11.5));
}

#[test]
fn windy_asks_for_exactly_the_span_it_scans() {
    let env = Env::new();
    let text = stdout_of(env.cmd().args([
        "windy",
        "--country",
        "Lithuania",
        "--min-wind",
        "8",
        "--hours",
        "48",
        "--limit",
        "3",
        "--json",
    ]));
    assert_eq!(env.backend.hits("/api/v1/wind?hours=48"), 1);
    let json: serde_json::Value = serde_json::from_str(&text).unwrap();
    assert_eq!(json["schemaVersion"], 1);
    assert_eq!(json["scanned"], 10);
    assert_eq!(json["spots"].as_array().unwrap().len(), 3);
    assert_eq!(json["spots"][0]["name"], "Nida");
    assert_eq!(json["spots"][0]["peakWind"], 20);
}

#[test]
fn windy_md_is_the_server_document() {
    let env = Env::new();
    let text = stdout_of(env.cmd().args(["windy", "--country", "poland", "--format", "md"]));
    assert_eq!(text, fixture("llms-windy-poland.md"));
}

#[test]
fn spot_json_passes_the_backend_payload_through() {
    let env = Env::new();
    let text = stdout_of(env.cmd().args(["spot", "500760", "--json"]));
    let json: serde_json::Value = serde_json::from_str(&text).unwrap();
    let fixture: serde_json::Value = serde_json::from_str(&fixture("spot-500760.json")).unwrap();
    assert_eq!(json, fixture);
}

#[test]
fn spot_table_shows_overview_live_forecast_and_links() {
    let env = Env::new();
    let text = stdout_of(env.cmd().args(["spot", "silvaplana", "--format", "table", "--hours", "3"]));
    assert!(text.starts_with("Silvaplana, Switzerland\n"));
    assert!(text.contains("Live conditions"));
    assert!(text.contains("Forecast (hourly, GFS 13 km, next 3)"));
    assert!(text.contains("https://varun.surf/spot/1584"));
}

#[test]
fn spot_in_polish_reads_the_polish_info() {
    let env = Env::new();
    let text = stdout_of(env.cmd().args(["spot", "1584", "--lang", "pl", "--format", "table"]));
    assert!(text.contains("Najlepszy wiatr"));
    assert!(text.contains("Linki"));
}

#[test]
fn spot_models_and_model_selection() {
    let env = Env::new();
    let text = stdout_of(env.cmd().args(["spot", "500760", "--models"]));
    assert!(text.contains("ifs\tIFS 9 km"));
    stdout_of(env.cmd().args(["spot", "500760", "--model", "ifs"]));
    assert_eq!(env.backend.hits("/api/v1/spots/500760/ifs"), 1);
    env.cmd()
        .args(["spot", "500760", "--model", "nope"])
        .assert()
        .code(2)
        .stderr(predicate::str::contains("Unknown model 'nope' for Jastarnia. Available: gfs, ifs,"));
}

#[test]
fn countries_and_country() {
    let env = Env::new();
    let text = stdout_of(env.cmd().args(["countries", "--json"]));
    let json: serde_json::Value = serde_json::from_str(&text).unwrap();
    assert_eq!(json["total"], 4);
    assert_eq!(json["countries"][0]["slug"], "czech-republic");
    let text = stdout_of(env.cmd().args(["country", "Czech Republic"]));
    assert_eq!(text.lines().count(), 4, "header and three spots");
    assert_eq!(stdout_of(env.cmd().args(["country", "poland", "--format", "md"])), fixture("llms-country-poland.md"));
    let live = stdout_of(env.cmd().args(["spots", "--live"]));
    assert!(live.lines().skip(1).all(|l| !l.split('\t').nth(3).unwrap().is_empty()));
}

#[test]
fn status_sentence_and_progress() {
    let env = Env::new();
    let text = stdout_of(env.cmd().args(["status", "--format", "md"]));
    assert!(text.starts_with("varun.surf is tracking "));
    assert!(text.contains("live weather stations are currently reporting."));
    let text = stdout_of(env.cmd().args(["status", "--forecast"]));
    assert!(text.contains("forecast_completed\t"));
}

#[test]
fn status_falls_back_to_health_when_the_session_fails() {
    let env = Env::new();
    env.backend.route("/api/v1/session", |_, _| Some((503, String::new())));
    env.cmd()
        .args(["status"])
        .assert()
        .success()
        .stdout("status\tUP\n")
        .stderr(predicate::str::contains("detailed status unavailable"));
}

#[test]
fn now_lists_live_stations_strongest_first() {
    let env = Env::new();
    let text = stdout_of(env.cmd().args(["now", "--json"]));
    let json: serde_json::Value = serde_json::from_str(&text).unwrap();
    let spots = json["spots"].as_array().unwrap();
    assert!(!spots.is_empty() && spots.len() <= 10);
    let winds: Vec<i64> = spots.iter().map(|s| s["live"]["wind"].as_i64().unwrap()).collect();
    assert!(winds.windows(2).all(|w| w[0] >= w[1]));
    assert!(spots.iter().all(|s| s["live"]["stale"].is_boolean()));
}

// ---- spot resolution and not-found --------------------------------------------

#[test]
fn ambiguous_name_without_a_terminal_exits_4_with_candidates() {
    let env = Env::new();
    env.cmd().args(["wind", "turawa"]).assert().code(4).stdout("").stderr(
        predicate::str::contains("Turawa Północ, Poland (726)").and(predicate::str::contains("Turawa Południe")),
    );
}

#[test]
fn names_fold_diacritics_and_urls_carry_ids() {
    let env = Env::new();
    // No forecast fixture for it: the request it makes is what shows it resolved.
    env.cmd().args(["wind", "kolobrzeg"]).assert().code(3);
    assert!(env.backend.paths().iter().any(|p| p == "/api/v1/forecast/1106370"));
    stdout_of(env.cmd().args(["wind", "https://varun.surf/spot/1584", "--hours", "1"]));
    assert_eq!(env.backend.hits("/api/v1/forecast/1584"), 1);
}

#[test]
fn not_found_exits_3_with_the_mcp_wording() {
    let env = Env::new();
    env.cmd().args(["wind", "123"]).assert().code(3).stderr(predicate::str::contains(
        "No spot found for wgId=123. Use `varun spots` or `varun search` to discover available spots.",
    ));
    env.cmd().args(["windy", "--country", "atlantis"]).assert().code(3).stderr(predicate::str::contains(
        "No country found for 'atlantis'. Use `varun countries` to see available countries.",
    ));
    env.cmd()
        .args(["search", "zzz"])
        .assert()
        .code(3)
        .stderr(predicate::str::contains("No spots found matching 'zzz'."));
    env.cmd().args(["country", "atlantis", "--format", "md"]).assert().code(3);
}

#[test]
fn usage_errors_exit_2() {
    let env = Env::new();
    env.cmd().args(["search", " "]).assert().code(2).stderr(predicate::str::contains("Query must not be empty."));
    env.cmd().args(["wind"]).assert().code(2);
    env.cmd().args(["--units", "furlongs", "status"]).assert().code(2);
    env.cmd().args(["status"]).env("VARUN_FORMAT", "yaml").assert().code(2);
}

#[test]
fn search_lists_matches() {
    let env = Env::new();
    let text = stdout_of(env.cmd().args(["search", "rewa"]));
    assert!(text.contains("Rewa\tPoland\t"));
    assert!(text.contains("Mewia Rewa\tPoland\t"));
    let md = stdout_of(env.cmd().args(["search", "rewa", "--format", "md"]));
    assert!(md.starts_with("# Spots matching 'rewa'\n\n- "));
}

// ---- session, cache and network failures ---------------------------------------

#[test]
fn session_cookie_is_stored_private_and_reused() {
    let env = Env::new();
    stdout_of(env.cmd().args(["status"]));
    stdout_of(env.cmd().args(["countries"]));
    assert_eq!(env.backend.hits("/api/v1/session"), 1);
    assert_eq!(env.backend.last("/api/v1/status").cookie.as_deref(), Some(COOKIE));
    let session = fs::read_dir(env.config_dir().join("cache"))
        .unwrap()
        .flatten()
        .find(|e| e.file_name().to_string_lossy().starts_with("session-"))
        .unwrap();
    use std::os::unix::fs::PermissionsExt;
    assert_eq!(session.metadata().unwrap().permissions().mode() & 0o777, 0o600);
}

#[test]
fn identifies_itself() {
    let env = Env::new();
    stdout_of(env.cmd().args(["status"]));
    let request = env.backend.last("/api/v1/status");
    assert!(request.headers["x-client"].starts_with("varun-cli/"));
    assert!(request.headers["user-agent"].starts_with("varun-cli/"));
}

#[test]
fn a_401_refreshes_the_session_once_and_retries() {
    let env = Env::new();
    env.backend.route("/api/v1/status", |_, seen| (seen == 0).then(|| (401, String::new())));
    stdout_of(env.cmd().args(["status"]));
    assert_eq!(env.backend.hits("/api/v1/session"), 2);
    assert_eq!(env.backend.hits("/api/v1/status"), 2);
}

#[test]
fn a_second_401_is_an_error() {
    let env = Env::new();
    env.backend.route("/api/v1/spots", |_, _| Some((401, String::new())));
    env.cmd().args(["countries"]).assert().code(5).stderr(predicate::str::contains("HTTP 401"));
    assert_eq!(env.backend.hits("/api/v1/spots"), 2);
}

#[test]
fn rate_limit_exits_6_without_retrying() {
    let env = Env::new();
    env.backend.route("/api/v1/spots", |_, _| Some((429, String::new())));
    env.cmd().args(["countries"]).assert().code(6).stderr(predicate::str::contains("Wait a minute"));
    assert_eq!(env.backend.hits("/api/v1/spots"), 1);
}

#[test]
fn a_503_is_retried_once_then_exits_5() {
    let env = Env::new();
    env.backend.route("/api/v1/spots", |_, _| Some((503, String::new())));
    env.cmd().args(["countries"]).assert().code(5).stderr(predicate::str::contains("HTTP 503"));
    assert_eq!(env.backend.hits("/api/v1/spots"), 2);
}

#[test]
fn a_transient_503_recovers() {
    let env = Env::new();
    env.backend.route("/api/v1/spots", |_, seen| (seen == 0).then(|| (503, String::new())));
    stdout_of(env.cmd().args(["countries"]));
}

#[test]
fn unreachable_backend_exits_5() {
    let env = Env::new();
    let closed = {
        let listener = TcpListener::bind("127.0.0.1:0").unwrap();
        format!("http://{}", listener.local_addr().unwrap())
    };
    env.cmd()
        .env("VARUN_BASE_URL", closed)
        .args(["countries"])
        .assert()
        .code(5)
        .stderr(predicate::str::contains("cannot reach"));
}

#[test]
fn spots_list_is_cached_briefly_unless_told_not_to() {
    let env = Env::new();
    stdout_of(env.cmd().args(["countries"]));
    stdout_of(env.cmd().args(["search", "puck"]));
    assert_eq!(env.backend.hits("/api/v1/spots"), 1);
    stdout_of(env.cmd().args(["search", "puck", "--no-cache"]));
    assert_eq!(env.backend.hits("/api/v1/spots"), 2);
}

#[test]
fn corrupt_cache_is_refetched() {
    let env = Env::new();
    stdout_of(env.cmd().args(["countries"]));
    for entry in fs::read_dir(env.config_dir().join("cache")).unwrap().flatten() {
        if entry.file_name().to_string_lossy().starts_with("spots-") {
            fs::write(entry.path(), "{broken").unwrap();
        }
    }
    stdout_of(env.cmd().args(["countries"]));
    assert_eq!(env.backend.hits("/api/v1/spots"), 2);
}

// ---- favourites and config -------------------------------------------------------

#[test]
fn favourites_with_aliases() {
    let env = Env::new();
    env.cmd().args(["fav", "add", "jastarnia", "--as", "home"]).assert().success();
    env.cmd().args(["fav", "add", "1584"]).assert().success();
    let list = stdout_of(env.cmd().args(["fav", "ls"]));
    assert!(list.contains("home\tJastarnia\tPoland\t500760"));
    assert!(list.contains("\tSilvaplana\tSwitzerland\t1584"));

    assert_eq!(stdout_of(env.cmd().args(["wind", "home", "--format", "md"])), fixture("llms-wind-500760.md"));

    let now = stdout_of(env.cmd().args(["now", "--json"]));
    let json: serde_json::Value = serde_json::from_str(&now).unwrap();
    let ids: Vec<i64> = json["spots"].as_array().unwrap().iter().map(|s| s["wgId"].as_i64().unwrap()).collect();
    assert_eq!(ids, vec![500760, 1584]);

    let windy = stdout_of(env.cmd().args(["windy", "--favorites", "--min-wind", "1", "--json"]));
    let json: serde_json::Value = serde_json::from_str(&windy).unwrap();
    assert!(json["scanned"].as_u64().unwrap() <= 2);

    env.cmd().args(["fav", "rm", "home"]).assert().success();
    env.cmd().args(["fav", "rm", "home"]).assert().code(3);
    let config = fs::read_to_string(env.config_dir().join("config/config.toml")).unwrap();
    assert!(config.contains("wg_id = 1584"));
    assert!(!config.contains("500760"));
}

#[test]
fn config_set_get_and_precedence() {
    let env = Env::new();
    env.cmd().args(["config", "set", "units", "ms"]).assert().success();
    assert_eq!(stdout_of(env.cmd().args(["config", "get", "units"])), "ms\n");
    let text = stdout_of(env.cmd().args(["wind", "500760", "--hours", "1"]));
    assert!(text.starts_with("Hour\tWind (m/s)"), "config applies");
    let text = stdout_of(env.cmd().args(["wind", "500760", "--hours", "1"]).env("VARUN_UNITS", "mph"));
    assert!(text.starts_with("Hour\tWind (mph)"), "env beats config");
    let text =
        stdout_of(env.cmd().args(["wind", "500760", "--hours", "1", "--units", "kts"]).env("VARUN_UNITS", "mph"));
    assert!(text.starts_with("Hour\tWind (kts)"), "flag beats env");
    env.cmd().args(["config", "set", "units", "parsecs"]).assert().code(2);
    assert!(stdout_of(env.cmd().args(["config", "path"])).trim_end().ends_with("config/config.toml"));
}

#[test]
fn unknown_config_keys_warn() {
    let env = Env::new();
    fs::create_dir_all(env.config_dir().join("config")).unwrap();
    fs::write(env.config_dir().join("config/config.toml"), "colour = \"blue\"\n").unwrap();
    env.cmd()
        .args(["config", "get"])
        .assert()
        .success()
        .stderr(predicate::str::contains("unknown config key 'colour' ignored"));
}

#[test]
fn completions_and_man_page() {
    let env = Env::new();
    for shell in ["bash", "zsh", "fish"] {
        let text = stdout_of(env.cmd().args(["completions", shell]));
        assert!(text.contains("windy"), "{shell} completion");
    }
    assert!(stdout_of(env.cmd().args(["man"])).contains(".TH varun"));
    assert!(env.backend.paths().is_empty(), "no network for either");
}

#[test]
fn version_names_the_commit() {
    let env = Env::new();
    let text = stdout_of(env.cmd().args(["--version"]));
    assert!(text.starts_with(&format!("varun {} (", env!("CARGO_PKG_VERSION"))));
}

/// Every computed `--json` output carries the top-level keys its schema in
/// `cli/schemas` requires.
#[test]
fn json_outputs_carry_their_schema_keys() {
    let env = Env::new();
    env.cmd().args(["fav", "add", "500760"]).assert().success();
    for (schema, args) in [
        ("windy.json", vec!["windy", "--json"]),
        ("wind.json", vec!["wind", "500760", "--json"]),
        ("search.json", vec!["search", "puck", "--json"]),
        ("countries.json", vec!["countries", "--json"]),
        ("now.json", vec!["now", "--json"]),
        ("favorites.json", vec!["fav", "ls", "--json"]),
    ] {
        let path = PathBuf::from(env!("CARGO_MANIFEST_DIR")).join("schemas").join(schema);
        let schema_json: serde_json::Value = serde_json::from_str(&fs::read_to_string(path).unwrap()).unwrap();
        let output: serde_json::Value = serde_json::from_str(&stdout_of(env.cmd().args(&args))).unwrap();
        for key in schema_json["required"].as_array().unwrap() {
            assert!(output.get(key.as_str().unwrap()).is_some(), "{schema}: {key} missing");
        }
        for key in output.as_object().unwrap().keys() {
            assert!(schema_json["properties"].get(key).is_some(), "{schema}: {key} undocumented");
        }
    }
}

#[test]
fn wind_table_with_every_column_and_summary() {
    let env = Env::new();
    let text = stdout_of(
        env.cmd().args(["wind", "500760", "--all", "--format", "table", "--hours", "24"]).env("COLUMNS", "200"),
    );
    assert!(text.starts_with("Jastarnia, Poland · GFS · next 24 h\n"));
    assert!(text.contains("Windiest hour:"));
    assert!(text.contains("Hours at 12+ kts:"));
    assert!(text.contains("Pressure (hPa)"));
    assert!(text.contains("Wave (m)"), "Jastarnia has wave data");
    let lake = stdout_of(env.cmd().args(["wind", "1584", "--all", "--hours", "3"]));
    assert!(lake.starts_with("Hour\tWind (kts)\tGusts (kts)\tDir\tTemp (°C)\tRain (mm)\tCloud (%)\tPressure (hPa)\n"));
    assert!(!lake.contains("Wave"), "no wave columns on a lake");
    let calm = stdout_of(env.cmd().args(["wind", "1584", "--min-wind", "60", "--format", "table", "--lang", "pl"]));
    assert!(calm.contains("Żadna godzina w tym oknie nie osiąga 60 kts."));
}

#[test]
fn now_for_named_spots_in_every_format() {
    let env = Env::new();
    let table = stdout_of(env.cmd().args(["now", "silvaplana", "kolobrzeg", "--format", "table"]));
    assert!(table.contains("Silvaplana"));
    assert!(table.contains("no station"), "Kołobrzeg has no live station");
    let plain = stdout_of(env.cmd().args(["now", "1584"]));
    assert!(plain.starts_with("Spot\tCountry\tWind (kts)\tGusts (kts)\tDir\t°C\tAge\n"));
    let md = stdout_of(env.cmd().args(["now", "1584", "kolobrzeg", "--format", "md"]));
    assert!(md.starts_with("# Live conditions\n\n- Silvaplana, Switzerland (wgId=1584): "));
    assert!(md.contains("- Kołobrzeg, Poland (wgId=1106370): no live weather station\n"));
}

#[test]
fn config_get_all_and_unset() {
    let env = Env::new();
    env.cmd().args(["config", "set", "lang", "pl"]).assert().success();
    env.cmd().args(["config", "set", "base_url", "http://localhost:1"]).assert().success();
    let all = stdout_of(env.cmd().args(["config"]).env("VARUN_BASE_URL", &env.backend.url));
    assert_eq!(all, "base_url = http://localhost:1\nlang = pl\n");
    env.cmd().args(["config", "unset", "base_url"]).assert().success();
    env.cmd().args(["config", "get", "base_url"]).assert().code(3);
    env.cmd().args(["config", "get", "colour"]).assert().code(2);
}

#[test]
fn watch_refuses_machine_formats() {
    let env = Env::new();
    env.cmd()
        .args(["now", "--watch", "--json"])
        .assert()
        .code(2)
        .stderr(predicate::str::contains("--watch works with the table and plain formats only"));
}
