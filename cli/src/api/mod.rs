//! The backend client: session cookie, the short on-disk cache of the spots list,
//! retries and the mapping of HTTP failures to exit codes (SPEC 6.2, 6.3).

pub mod dto;

use std::fs;
use std::path::PathBuf;
use std::time::{Duration, Instant, SystemTime, UNIX_EPOCH};

use reqwest::StatusCode;
use reqwest::blocking::{Client as Http, Response};
use reqwest::header::{ACCEPT, COOKIE, SET_COOKIE};
use serde::de::DeserializeOwned;

use crate::error::{CliError, Result};
use dto::{ForecastFetchProgress, HourlyForecast, Spot, Status, WindTimeline};

/// A cookie this close to its expiry is replaced rather than reused.
const COOKIE_REFRESH_MARGIN_SECS: i64 = 3600;

/// Live conditions ride in the spots payload and refresh once a minute, so a
/// longer TTL would show stale wind.
const SPOTS_CACHE_TTL: Duration = Duration::from_secs(60);

pub struct Client {
    http: Http,
    base_url: String,
    cache_dir: PathBuf,
    use_cache: bool,
    verbose: bool,
    session: Option<String>,
}

/// A 404, told apart from other failures so each command can word its own
/// "not found" message.
pub enum Fetched<T> {
    Found(T),
    NotFound,
}

impl Client {
    pub fn new(base_url: &str, cache_dir: PathBuf, use_cache: bool, verbose: bool) -> Result<Client> {
        let version = env!("CARGO_PKG_VERSION");
        let user_agent = format!("varun-cli/{version} ({}; {})", std::env::consts::OS, std::env::consts::ARCH);
        let mut headers = reqwest::header::HeaderMap::new();
        headers.insert("X-Client", format!("varun-cli/{version}").parse().expect("valid header value"));
        let http = Http::builder()
            .user_agent(user_agent)
            .default_headers(headers)
            .connect_timeout(Duration::from_secs(5))
            .timeout(Duration::from_secs(20))
            .build()
            .map_err(|e| CliError::Other(format!("cannot build the HTTP client: {e}")))?;
        Ok(Client {
            http,
            base_url: base_url.trim_end_matches('/').to_string(),
            cache_dir,
            use_cache,
            verbose,
            session: None,
        })
    }

    pub fn base_url(&self) -> &str {
        &self.base_url
    }

    // ---- endpoints ------------------------------------------------------------

    /// The spots list, parsed and as received.
    pub fn spots(&mut self) -> Result<(Vec<Spot>, String)> {
        let cache = self.cache_path("spots");
        if self.use_cache
            && let Some(cached) = read_fresh_cache::<Vec<Spot>>(&cache, SPOTS_CACHE_TTL)
        {
            self.log("cache hit: /api/v1/spots");
            return Ok(cached);
        }
        let body = self
            .api_text("/api/v1/spots")?
            .found_or(|| CliError::Network("the backend answered /api/v1/spots with 404".to_string()))?;
        let spots: Vec<Spot> = parse_json(&body, "/api/v1/spots")?;
        if self.use_cache {
            write_private(&cache, body.as_bytes());
        }
        Ok((spots, body))
    }

    /// The spot with its hourly forecast, in GFS or the model named.
    pub fn spot(&mut self, wg_id: i64, model: Option<&str>) -> Result<Fetched<(Spot, String)>> {
        let path = match model {
            Some(model) => format!("/api/v1/spots/{wg_id}/{model}"),
            None => format!("/api/v1/spots/{wg_id}"),
        };
        self.api_text(&path)?.map(|body| Ok((parse_json(&body, &path)?, body)))
    }

    pub fn forecast(&mut self, wg_id: i64) -> Result<Fetched<(HourlyForecast, String)>> {
        let path = format!("/api/v1/forecast/{wg_id}");
        self.api_text(&path)?.map(|body| Ok((parse_json(&body, &path)?, body)))
    }

    pub fn wind(&mut self, hours: i64) -> Result<WindTimeline> {
        let path = format!("/api/v1/wind?hours={hours}");
        let body =
            self.api_text(&path)?.found_or(|| CliError::Network(format!("the backend answered {path} with 404")))?;
        parse_json(&body, &path)
    }

    pub fn status(&mut self) -> Result<(Status, String)> {
        let body = self
            .api_text("/api/v1/status")?
            .found_or(|| CliError::Network("the backend answered /api/v1/status with 404".to_string()))?;
        Ok((parse_json(&body, "/api/v1/status")?, body))
    }

    pub fn forecast_progress(&mut self) -> Result<(ForecastFetchProgress, String)> {
        let path = "/api/v1/status/forecast";
        let body =
            self.api_text(path)?.found_or(|| CliError::Network(format!("the backend answered {path} with 404")))?;
        Ok((parse_json(&body, path)?, body))
    }

    /// `/api/v1/health`, which needs no session: what `status` falls back on.
    pub fn health(&mut self) -> Result<String> {
        let response = self.send("/api/v1/health", None)?;
        let status = response.status();
        let body = read_body(response)?;
        check_status(status, "/api/v1/health")?;
        let value: serde_json::Value = parse_json(&body, "/api/v1/health")?;
        Ok(value.get("status").and_then(|s| s.as_str()).unwrap_or("UNKNOWN").to_string())
    }

    /// A server-rendered `/llms` Markdown document. No session needed.
    pub fn markdown(&mut self, path_and_query: &str) -> Result<Fetched<String>> {
        let response = self.send(path_and_query, None)?;
        let status = response.status();
        if status == StatusCode::NOT_FOUND {
            return Ok(Fetched::NotFound);
        }
        let body = read_body(response)?;
        check_status(status, path_and_query)?;
        Ok(Fetched::Found(body))
    }

    // ---- session ----------------------------------------------------------------

    /// A `/api/v1` GET carrying the session cookie: minted when missing or close to
    /// expiry, refreshed once on a 401, and a second 401 is an error.
    fn api_text(&mut self, path: &str) -> Result<Fetched<String>> {
        let cookie = self.session_cookie(false)?;
        let mut response = self.send(path, Some(&cookie))?;
        if response.status() == StatusCode::UNAUTHORIZED {
            self.log("401: refreshing the session cookie");
            let cookie = self.session_cookie(true)?;
            response = self.send(path, Some(&cookie))?;
            if response.status() == StatusCode::UNAUTHORIZED {
                return Err(CliError::Network(format!("the backend refused the session cookie for {path} (HTTP 401)")));
            }
        }
        let status = response.status();
        if status == StatusCode::NOT_FOUND {
            return Ok(Fetched::NotFound);
        }
        let body = read_body(response)?;
        check_status(status, path)?;
        Ok(Fetched::Found(body))
    }

    fn session_cookie(&mut self, force_refresh: bool) -> Result<String> {
        let now = unix_now();
        if !force_refresh {
            if let Some(cookie) = &self.session
                && cookie_is_fresh(cookie, now)
            {
                return Ok(cookie.clone());
            }
            if let Ok(stored) = fs::read_to_string(self.cache_path("session"))
                && cookie_is_fresh(stored.trim(), now)
            {
                self.session = Some(stored.trim().to_string());
                return Ok(stored.trim().to_string());
            }
        }
        let response = self.send("/api/v1/session", None)?;
        let status = response.status();
        let cookie = response
            .headers()
            .get_all(SET_COOKIE)
            .iter()
            .filter_map(|v| v.to_str().ok())
            .find_map(session_from_set_cookie);
        check_status(status, "/api/v1/session")?;
        let cookie = cookie.ok_or_else(|| {
            CliError::Network("the backend handed out no SESSION cookie at /api/v1/session".to_string())
        })?;
        write_private(&self.cache_path("session"), cookie.as_bytes());
        self.session = Some(cookie.clone());
        Ok(cookie)
    }

    // ---- transport ------------------------------------------------------------

    /// One GET, retried once with jitter on a connect error or 502/503/504 -
    /// never on 429.
    fn send(&self, path: &str, session: Option<&str>) -> Result<Response> {
        let url = format!("{}{}", self.base_url, path);
        let accept = if path.starts_with("/llms") { "text/markdown" } else { "application/json" };
        for attempt in 0..2 {
            let started = Instant::now();
            let mut request = self.http.get(&url).header(ACCEPT, accept);
            if let Some(cookie) = session {
                request = request.header(COOKIE, format!("SESSION={cookie}"));
            }
            let result = request.send();
            match result {
                Ok(response) => {
                    let status = response.status();
                    self.log(&format!("GET {url} -> {} in {} ms", status.as_u16(), started.elapsed().as_millis()));
                    if attempt == 0
                        && matches!(
                            status,
                            StatusCode::BAD_GATEWAY | StatusCode::SERVICE_UNAVAILABLE | StatusCode::GATEWAY_TIMEOUT
                        )
                    {
                        std::thread::sleep(jitter());
                        continue;
                    }
                    return Ok(response);
                }
                Err(e) => {
                    self.log(&format!("GET {url} failed in {} ms: {e}", started.elapsed().as_millis()));
                    if attempt == 0 && e.is_connect() {
                        std::thread::sleep(jitter());
                        continue;
                    }
                    return Err(network_error(&self.base_url, e));
                }
            }
        }
        unreachable!("the second attempt always returns")
    }

    fn cache_path(&self, name: &str) -> PathBuf {
        // One cache per backend, so a local dev run never serves production data.
        self.cache_dir.join(format!("{name}-{:016x}.cache", fnv1a(self.base_url.as_bytes())))
    }

    fn log(&self, message: &str) {
        if self.verbose {
            eprintln!("varun: {message}");
        }
    }
}

impl<T> Fetched<T> {
    fn found_or(self, error: impl FnOnce() -> CliError) -> Result<T> {
        match self {
            Fetched::Found(value) => Ok(value),
            Fetched::NotFound => Err(error()),
        }
    }

    fn map<U>(self, f: impl FnOnce(T) -> Result<U>) -> Result<Fetched<U>> {
        match self {
            Fetched::Found(value) => Ok(Fetched::Found(f(value)?)),
            Fetched::NotFound => Ok(Fetched::NotFound),
        }
    }
}

fn check_status(status: StatusCode, path: &str) -> Result<()> {
    match status {
        s if s.is_success() => Ok(()),
        StatusCode::TOO_MANY_REQUESTS => Err(CliError::RateLimited),
        s => Err(CliError::Network(format!("the backend answered {path} with HTTP {}", s.as_u16()))),
    }
}

fn read_body(response: Response) -> Result<String> {
    response.text().map_err(|e| CliError::Network(format!("reading the response failed: {e}")))
}

fn parse_json<T: DeserializeOwned>(body: &str, path: &str) -> Result<T> {
    serde_json::from_str(body).map_err(|e| CliError::Network(format!("unexpected response from {path}: {e}")))
}

fn network_error(base_url: &str, e: reqwest::Error) -> CliError {
    if e.is_timeout() {
        CliError::Network(format!("{base_url} did not answer in time"))
    } else if e.is_connect() {
        CliError::Network(format!("cannot reach {base_url}: {}", root_cause(&e)))
    } else {
        CliError::Network(format!("request to {base_url} failed: {}", root_cause(&e)))
    }
}

fn root_cause(e: &(dyn std::error::Error + 'static)) -> String {
    let mut cause = e;
    while let Some(source) = cause.source() {
        cause = source;
    }
    cause.to_string()
}

/// The `SESSION` value out of a `Set-Cookie` header.
pub fn session_from_set_cookie(header: &str) -> Option<String> {
    let pair = header.split(';').next()?.trim();
    let (name, value) = pair.split_once('=')?;
    (name.trim() == "SESSION" && !value.trim().is_empty()).then(|| value.trim().to_string())
}

/// The token is `<expiry epoch second>.<signature>`: only the expiry is read, the
/// signature is the backend's business.
pub fn cookie_is_fresh(cookie: &str, now: i64) -> bool {
    cookie
        .split_once('.')
        .and_then(|(expiry, _)| expiry.parse::<i64>().ok())
        .is_some_and(|expiry| expiry - now > COOKIE_REFRESH_MARGIN_SECS)
}

fn unix_now() -> i64 {
    SystemTime::now().duration_since(UNIX_EPOCH).map_or(0, |d| d.as_secs() as i64)
}

fn jitter() -> Duration {
    let nanos = SystemTime::now().duration_since(UNIX_EPOCH).map_or(0, |d| d.subsec_nanos());
    Duration::from_millis(250 + u64::from(nanos % 500))
}

/// A cached body younger than `ttl`, parsed and as stored. A corrupt one is
/// deleted, never fatal.
fn read_fresh_cache<T: DeserializeOwned>(path: &PathBuf, ttl: Duration) -> Option<(T, String)> {
    let age = fs::metadata(path).ok()?.modified().ok()?.elapsed().ok()?;
    if age > ttl {
        return None;
    }
    let body = fs::read_to_string(path).ok()?;
    match serde_json::from_str(&body) {
        Ok(value) => Some((value, body)),
        Err(_) => {
            let _ = fs::remove_file(path);
            None
        }
    }
}

/// Best effort: a cache that cannot be written only costs a download next time.
/// Mode 0600, since the session cookie lives here too.
fn write_private(path: &PathBuf, bytes: &[u8]) {
    use std::io::Write;
    use std::os::unix::fs::OpenOptionsExt;
    if let Some(dir) = path.parent() {
        let _ = fs::create_dir_all(dir);
    }
    let tmp = path.with_extension("tmp");
    let written = fs::OpenOptions::new()
        .write(true)
        .create(true)
        .truncate(true)
        .mode(0o600)
        .open(&tmp)
        .and_then(|mut f| f.write_all(bytes));
    if written.is_ok() {
        let _ = fs::rename(&tmp, path);
    } else {
        let _ = fs::remove_file(&tmp);
    }
}

fn fnv1a(bytes: &[u8]) -> u64 {
    bytes.iter().fold(0xcbf2_9ce4_8422_2325, |hash, &b| (hash ^ u64::from(b)).wrapping_mul(0x0100_0000_01b3))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn reads_the_session_from_set_cookie() {
        assert_eq!(
            session_from_set_cookie("SESSION=1793394977.abc-_; Path=/; Max-Age=86400; HttpOnly; SameSite=Lax"),
            Some("1793394977.abc-_".to_string())
        );
        assert_eq!(session_from_set_cookie("OTHER=1; Path=/"), None);
        assert_eq!(session_from_set_cookie("SESSION=; Max-Age=0"), None);
    }

    #[test]
    fn cookie_is_reused_until_an_hour_before_expiry() {
        let now = 1_000_000;
        assert!(cookie_is_fresh("1003601.sig", now));
        assert!(!cookie_is_fresh("1003600.sig", now));
        assert!(!cookie_is_fresh("999999.sig", now));
        assert!(!cookie_is_fresh("garbage", now));
        assert!(!cookie_is_fresh("", now));
    }

    #[test]
    fn corrupt_cache_is_deleted() {
        let dir = tempfile::tempdir().unwrap();
        let path = dir.path().join("spots.cache");
        fs::write(&path, "{not json").unwrap();
        assert!(read_fresh_cache::<Vec<Spot>>(&path, SPOTS_CACHE_TTL).is_none());
        assert!(!path.exists());
    }

    #[test]
    fn private_files_are_0600() {
        use std::os::unix::fs::PermissionsExt;
        let dir = tempfile::tempdir().unwrap();
        let path = dir.path().join("nested/session.cache");
        write_private(&path, b"1.x");
        assert_eq!(fs::metadata(&path).unwrap().permissions().mode() & 0o777, 0o600);
        assert_eq!(fs::read_to_string(&path).unwrap(), "1.x");
    }

    #[test]
    fn cache_files_are_per_backend() {
        assert_ne!(fnv1a(b"https://varun.surf"), fnv1a(b"http://localhost:8080"));
    }
}
