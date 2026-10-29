//! Parity with the MCP server (SPEC section 3): the client-side computation and
//! Markdown, run over the JSON fixtures, must equal the `/llms` documents the
//! backend rendered from the same data at the same moment (`fixtures/capture.sh`).

use std::fs;
use std::path::PathBuf;

use varun_cli::api::dto::{HourlyForecast, Spot, WindTimeline};
use varun_cli::domain::wind::wind_forecast;
use varun_cli::domain::windy::{WindyQuery, scan, spots_by_wg_id};
use varun_cli::render::md;

fn fixture(name: &str) -> String {
    let path = PathBuf::from(env!("CARGO_MANIFEST_DIR")).join("tests/fixtures").join(name);
    fs::read_to_string(&path).unwrap_or_else(|e| panic!("{}: {e}", path.display()))
}

fn json<T: serde::de::DeserializeOwned>(name: &str) -> T {
    serde_json::from_str(&fixture(name)).unwrap()
}

fn spots() -> Vec<Spot> {
    json("spots.json")
}

/// Line by line, so a failure names the first line that drifted.
fn assert_same(client: &str, server: &str, what: &str) {
    for (i, (c, s)) in client.lines().zip(server.lines()).enumerate() {
        assert_eq!(c, s, "{what}: line {} differs", i + 1);
    }
    assert_eq!(client, server, "{what}: documents differ in length or trailing newline");
}

fn wind_md(wg_id: i64, hours: Option<i64>, min_wind: Option<i64>) -> String {
    let spots = spots();
    let spot = spots.iter().find(|s| s.wg_id == wg_id).unwrap();
    let forecast: HourlyForecast = json(&format!("forecast-{wg_id}.json"));
    md::wind_forecast(spot, wind_forecast(&forecast.hours, hours, min_wind).as_ref())
}

fn windy_md(file: &str, country: &str, min_wind: Option<i64>, hours: Option<i64>, limit: Option<i64>) -> String {
    let spots = spots();
    let timeline: WindTimeline = json(file);
    let query = WindyQuery::new(min_wind, hours, limit);
    let result = scan(&timeline, &spots_by_wg_id(&spots, Some(country)), query);
    md::windy_spots(result.as_ref(), query, Some(country))
}

#[test]
fn wind_matches_get_wind_forecast() {
    assert_same(&wind_md(500760, None, None), &fixture("llms-wind-500760.md"), "wind 500760");
    assert_same(&wind_md(1584, None, None), &fixture("llms-wind-1584.md"), "wind 1584");
}

#[test]
fn wind_with_span_and_floor_matches() {
    assert_same(
        &wind_md(500760, Some(12), Some(12)),
        &fixture("llms-wind-500760-h12-m12.md"),
        "wind 500760 --hours 12 --min-wind 12",
    );
    assert_same(&wind_md(1584, None, Some(40)), &fixture("llms-wind-1584-m40.md"), "wind 1584 --min-wind 40");
}

#[test]
fn windy_matches_find_windy_spots() {
    assert_same(
        &windy_md("wind-24.json", "poland", None, None, None),
        &fixture("llms-windy-poland.md"),
        "windy --country poland",
    );
    assert_same(
        &windy_md("wind-24.json", "czech-republic", None, None, None),
        &fixture("llms-windy-czech-republic.md"),
        "windy --country czech-republic",
    );
    assert_same(
        &windy_md("wind-48.json", "Lithuania", Some(8), Some(48), Some(3)),
        &fixture("llms-windy-lithuania-m8-h48-l3.md"),
        "windy --country Lithuania --min-wind 8 --hours 48 --limit 3",
    );
}

#[test]
fn spot_matches_get_spot() {
    for wg_id in [500760, 1584] {
        let mut spot: Spot = json(&format!("spot-{wg_id}.json"));
        let server = fixture(&format!("llms-spot-{wg_id}.md"));
        // The two requests of the capture can straddle a live-conditions refresh,
        // which stamps the spot anew: the timestamp is the one thing taken over.
        spot.last_updated =
            server.lines().find_map(|l| l.strip_prefix("Last updated: ")).map(str::to_string).or(spot.last_updated);
        assert_same(&md::spot(&spot), &server, &format!("spot {wg_id}"));
    }
}

#[test]
fn country_matches_get_spots_by_country() {
    let spots = spots();
    assert_same(&md::country("Poland", &spots), &fixture("llms-country-poland.md"), "country poland");
    assert_same(
        &md::country("Czech Republic", &spots),
        &fixture("llms-country-czech-republic.md"),
        "country czech-republic",
    );
}

/// The indexes cover the whole spot list, which is too big to commit, so this one
/// runs against a full capture on demand:
///
///   dir=$(mktemp -d); curl -s -c $dir/jar -o /dev/null https://varun.surf/api/v1/session
///   curl -s -b $dir/jar https://varun.surf/api/v1/spots -o $dir/spots.json
///   curl -s https://varun.surf/llms/spots.md -o $dir/spots.md
///   curl -s https://varun.surf/llms/countries.md -o $dir/countries.md
///   VARUN_PARITY_FULL=$dir cargo test --test parity -- --ignored
#[test]
#[ignore = "needs a full capture in VARUN_PARITY_FULL"]
fn indexes_match_list_spots_and_list_countries() {
    let dir = PathBuf::from(std::env::var("VARUN_PARITY_FULL").expect("VARUN_PARITY_FULL"));
    let read = |name: &str| fs::read_to_string(dir.join(name)).unwrap();
    let spots: Vec<Spot> = serde_json::from_str(&read("spots.json")).unwrap();
    assert_same(&md::spots_index(&spots), &read("spots.md"), "spots");
    assert_same(&md::countries_index(&spots), &read("countries.md"), "countries");
}
