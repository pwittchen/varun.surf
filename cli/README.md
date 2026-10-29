# varun CLI

Kite spot forecasts and live wind from [varun.surf](https://varun.surf) in a terminal.
It answers the same questions as the varun.surf MCP server, with the same defaults,
for a person at a prompt or a script in a pipe. The design is in [SPEC.md](SPEC.md).

```console
$ varun wind jastarnia
$ varun windy --country poland
$ varun now
```

## Install

Linux (x86_64, arm64) and macOS on Apple Silicon:

```sh
curl -fsSL https://varun.surf/cli/install.sh | sh
```

The script downloads the binary for your platform from the
[GitHub Release](https://github.com/pwittchen/varun.surf/releases), checks its SHA-256
and installs it into `~/.local/bin`. It never uses `sudo`, and it never edits your
shell rc files. If the directory is not on your `PATH`, the script prints the line to add.

```sh
# a given version, into another directory
curl -fsSL https://varun.surf/cli/install.sh | sh -s -- --version 0.1.0 --dir /usr/local/bin
```

Re-running the script upgrades in place. Windows and Intel Macs are not supported.
WSL users can install the Linux binary.

**By hand:** download `varun-<version>-<target>.tar.gz` from the release, check it
against its `.sha256`, and put `varun` on your `PATH`. On macOS, an archive downloaded
with a browser is quarantined, because the binary is ad-hoc signed and not notarised.
`curl` downloads are not quarantined. For a browser download, run
`xattr -d com.apple.quarantine varun` once.

**From source:** `cargo build --release` in `cli/`. The binary ends up at `target/release/varun`.

**Uninstall:** delete the binary, then the config and cache directories listed under
[Configuration](#configuration).

## Commands

`<SPOT>` can be any of these:
- a Windguru id (`500760`)
- a `varun.surf/spot/…` or Windguru URL
- a favourite's alias
- part of a spot name: case and diacritics don't matter, so `mrzezyno` finds Mrzeżyno

If a name matches several spots, a picker opens on a terminal. In a pipe, the command
exits with code 4 and lists the candidates.

| Command | What it answers | MCP tool |
|---|---|---|
| `varun wind <SPOT> [--hours N] [--min-wind K] [--all]` | Wind at one spot, hour by hour: the windiest hour and the kiteable windows (72 h by default) | `get_wind_forecast` |
| `varun windy [--min-wind K] [--hours N] [--country C] [--limit L] [--favorites]` | Spots that reach K kts (12 by default) in the next N hours (24 by default), strongest first | `find_windy_spots` |
| `varun now [SPOT...]` | Live conditions at the spots given; with none, your favourites or else the 10 windiest stations. Readings 60 min old or more are marked stale | – |
| `varun spot <SPOT> [--model KEY] [--models] [--hours N]` | Everything about one spot: overview, live conditions, daily and hourly forecast, links | `get_spot` |
| `varun search <QUERY>` | Spots whose name contains the query | `find_spot_by_name` |
| `varun spots [--country C] [--live]` | All spots, with live wind where a station reports it | `list_spots` |
| `varun countries` | Countries, with spot counts and slugs | `list_countries` |
| `varun country <C>` | Spots in one country (by name or slug) | `get_spots_by_country` |
| `varun status [--forecast]` | Spots, countries, live stations, backend version, forecast sweep progress | `get_status` |
| `varun fav add <SPOT> [--as ALIAS]`, `fav rm`, `fav ls` | Favourites, kept in the local config | – |
| `varun open <SPOT>` | Opens the spot page in your browser | – |
| `varun config [get\|set\|unset\|path\|edit]` | Reads and writes the config file | – |
| `varun completions <bash\|zsh\|fish>` | Prints a shell completion script | – |

`now` and `windy` take `--watch[=SECONDS]` to redraw every minute (the minimum, since
live data refreshes once a minute). `Ctrl-C` stops it.

### Output

| `--format` | When | What |
|---|---|---|
| `table` | default on a terminal | aligned columns, arrows, wind-class colours (same thresholds as the website) |
| `plain` | default in a pipe | tab-separated with a header row, no colour: ready for `cut`, `awk`, `sort` |
| `json` (or `--json`) | scripts | `spot` and `status` pass the backend payload through; the computed commands follow the versioned schemas in [`schemas/`](schemas) (`"schemaVersion": 1`) |
| `md` | notes, LLM pipelines | the server-rendered `/llms` document, byte for byte the MCP answer; `search`, `status` and filtered lists are rendered locally in the same layout |

Answers go to stdout. Warnings and errors go to stderr.

```sh
varun windy --country poland | cut -f1,7              # spot and peak wind
varun wind home --json | jq '.summary.kiteableWindows'
varun spot jastarnia --format md | llm "Should I go tomorrow?"
```

### Exit codes

| Code | Meaning |
|---|---|
| 0 | Success, including "no spot reaches K kts" |
| 1 | Unexpected error |
| 2 | Usage error: bad flag, empty query, unknown model |
| 3 | Not found: spot, country, or a search with no match |
| 4 | Ambiguous spot name, with no terminal to pick on |
| 5 | Network error, backend unreachable or 5xx |
| 6 | Rate limited (HTTP 429): wait a minute, it is not retried |

## Configuration

| Option | Environment | Default |
|---|---|---|
| `--base-url URL` | `VARUN_BASE_URL` | `https://varun.surf` (`http://localhost:8080` for a local backend) |
| `--format FORMAT` / `--json` | `VARUN_FORMAT` | `table` on a terminal, `plain` in a pipe |
| `--lang en\|pl` | `VARUN_LANG` | from `LC_ALL`/`LANG`, otherwise `en` |
| `--units kts\|ms\|kmh\|mph\|bft` | `VARUN_UNITS` | `kts` (display only: `--min-wind` is always in knots) |
| `--tz spot\|local\|ZONE` | `VARUN_TZ` | `spot`: forecast hours are shown as served. The API does not expose a spot's time zone yet, so only `spot` is honoured |
| `--no-color` | `NO_COLOR` | colour on a terminal |
| `--no-cache` | | the spots list is cached for 60 s |
| `-q` / `-v` | | `-v` logs every request and its timing to stderr |

Precedence: flag, then environment, then config file, then default. The config file
is TOML, and `varun config set` validates values before it writes them:

```toml
base_url = "https://varun.surf"
lang = "en"
units = "kts"
format = "table"

[[favorites]]
wg_id = 500760
alias = "home"
```

| | Linux | macOS |
|---|---|---|
| Config (`config.toml`) | `~/.config/varun/` | `~/Library/Application Support/surf.varun.cli/` |
| Cache (spots list, session cookie) | `~/.cache/varun/` | `~/Library/Caches/surf.varun.cli/` |

`VARUN_CONFIG_DIR` and `VARUN_CACHE_DIR` override both.

## How it talks to the backend

- The CLI uses the public API only: `/api/v1/spots`, `/spots/{id}[/{model}]`,
  `/forecast/{id}`, `/wind?hours=N`, `/status`, `/status/forecast` and `/llms/*.md`.
  It never calls the paid AI analysis.
- `/api/v1` needs the `SESSION` cookie. The CLI gets one from `/api/v1/session`, keeps
  it in the cache directory (mode 0600) until an hour before it expires, and on a 401
  refreshes it once. `--format md` reads `/llms`, which needs no cookie.
- Every request sends `User-Agent: varun-cli/<version> (<os>; <arch>)` and
  `X-Client: varun-cli/<version>`.
- Timeouts are 5 s to connect and 20 s in total. A connect error or a 502/503/504 is
  retried once, with jitter; a 429 never is. There is no telemetry and no update check.

## Development

```sh
cargo test                                   # unit, end-to-end (mock backend) and parity tests
cargo clippy --all-targets -- -D warnings
cargo fmt
cargo llvm-cov                               # coverage, 80 % lines expected
cargo deny check                             # licences and advisories
cargo run -- --base-url http://localhost:8080 windy
```

- `src/domain/`: the pure logic (windy-window scan, wind summary, spot resolution,
  wind classes, units, staleness), with no I/O
- `src/api/`: the HTTP client, session cookie, cache and DTOs
- `src/render/`: tables, plain text, JSON, and the Markdown renderers ported from
  `LlmController`
- `src/commands/`: one module per command

**Parity with the MCP server.** `tests/parity.rs` runs the client-side computation over
JSON fixtures and requires the result to equal, byte for byte, the `/llms` documents the
backend rendered from the same data. When `LlmController` changes, regenerate the fixtures
against a local backend (`./gradlew bootRun`, then wait for the forecasts), and the test
shows what the CLI has to follow:

```sh
cli/tests/fixtures/capture.sh                # or: capture.sh https://varun.surf
```

**Releasing.** Bump `version` in `Cargo.toml`, add a `## X.Y.Z` section to
[RELEASES.md](RELEASES.md), commit, then `git tag cli-vX.Y.Z && git push origin cli-vX.Y.Z`.
The `CLI` workflow builds the Linux (x86_64, arm64) and macOS (arm64) archives,
publishes the GitHub Release, and checks that the install script installs it.
