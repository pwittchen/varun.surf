# CLAUDE.md - Command-Line Client

`varun`, the command-line client for the varun.surf backend: the MCP server's answers in a
terminal. This directory is a **separate Rust crate** (package `varun-cli`, binary `varun`)
from the Spring Boot backend in the repository root: run every `cargo` command from `cli/`.

- Product and technical spec: [SPEC.md](SPEC.md) (section numbers below refer to it)
- Usage, configuration and developer notes: [README.md](README.md)
- Release notes: [RELEASES.md](RELEASES.md)
- Backend context: [../CLAUDE.md](../CLAUDE.md)

## Tech Stack

- Rust, edition 2024, toolchain pinned in `rust-toolchain.toml` (`rust-version` in `Cargo.toml`)
- `clap` (derive) + `clap_complete` + `clap_mangen`, `reqwest` **blocking** with `rustls-tls`
  (no async runtime, no OpenSSL - the Linux binaries are static musl)
- `serde`/`serde_json`, `toml`, `directories`, `comfy-table`, `owo-colors`, `dialoguer`,
  `chrono`, `unicode-normalization`
- `thiserror` in the library (`CliError`), mapped to exit codes in `main.rs`
- Tests: `assert_cmd`, `predicates`, `tempfile`, and a hand-written mock HTTP server in `tests/cli.rs`
- Keep the dependency tree small: every new crate needs a reason, and `deny.toml` allows
  permissive licences only

## Build & Test

```bash
cargo test                                   # unit, end-to-end (mock backend) and parity tests
cargo clippy --all-targets -- -D warnings
cargo fmt                                    # rustfmt.toml: max_width 120
cargo llvm-cov                               # coverage, 80 % lines expected
cargo deny check                             # licences and advisories
cargo run -- --base-url http://localhost:8080 windy   # against a local ./gradlew bootRun
```

CI (`../.github/workflows/cli.yml`) runs `fmt --check`, `clippy`, `test --locked` and
`cargo deny` on changes under `cli/**` (`.md`-only changes are skipped), plus shellcheck on
the install script. Run the same before calling a change done. The backend `ci.yml` ignores
`cli/**`.

## Layout

```
src/main.rs       clap wiring, config/flag precedence, error → exit code
src/cli.rs        command and option definitions
src/config.rs     config file, platform dirs, Format/Lang/Units/Tz
src/error.rs      CliError and its exit codes (SPEC 4.12)
src/api/          HTTP client, SESSION cookie, 60 s spots cache, DTOs (dto.rs)
src/domain/       pure logic, no I/O: windy scan, wind summary, spot resolution,
                  wind classes and units, staleness, slug and diacritic folding
src/render/       table, plain, json (mod.rs) and the Markdown ported from LlmController (md.rs)
src/commands/     one module per command; each returns the text to print
schemas/          JSON schemas of the computed outputs (`"schemaVersion": 1`)
tests/cli.rs      the binary against a mock backend serving tests/fixtures
tests/parity.rs   client-side Markdown vs the server's /llms documents
tests/fixtures/   captured backend responses (capture.sh)
```

- Commands return a `String` rather than printing, so `--watch` can re-render and tests
  can read it. stdout carries only the answer; warnings and errors go to stderr
- New failure modes go through a `CliError` variant, never `process::exit` or a panic.
  Network failures must never panic

## Backend Contract

The CLI consumes the public API only: `/api/v1/session`, `/spots`, `/spots/{id}[/{model}]`,
`/forecast/{id}`, `/wind?hours=N`, `/status`, `/status/forecast`, `/api/v1/health` and
`/llms/*.md`.

- **Never call the paid AI analysis** (`POST /spots/{id}/analysis`), nor `/spots/{id}/icm`.
  If an analysis command is ever added: interactive confirmation, refused without a TTY
  unless `--yes`, never in `--watch` (SPEC 2.2)
- **Everything is optional on the client**: DTOs use `#[serde(default)]` and never
  `deny_unknown_fields`. The backend ships independently of CLI releases, so a renamed
  backend field silently becomes absent here. Empty-string URLs mean "absent"
- Session: the cookie is cached in the cache dir (mode 0600) and reused until its `<epoch>.`
  prefix is under 1 h away; a 401 refreshes it once and retries once. Never parse or verify
  the HMAC. `/llms/**` needs no cookie
- Every request sends `User-Agent: varun-cli/<version> (<os>; <arch>)` and
  `X-Client: varun-cli/<version>`
- Timeouts 5 s connect / 20 s total; one jittered retry on connect errors and 502/503/504,
  **never** on 429 (exit 6). The nginx rate limit protects the backend, and Windguru has
  blocked the production IP before - be a good citizen
- `/api/v1/spots/{id}` triggers server-side model discovery: only `varun spot` may call it.
  `wind`, `windy` and `now` read `/forecast/{id}`, `/wind` and the cached spots list
- `windy` asks `/api/v1/wind?hours=<N>` with exactly the span it needs
- `Forecast.date` is a zone-less wall-clock string in the spot's time zone; date parsing
  never fails a command, it falls back to the raw string

## MCP Parity (keep in sync)

The commands mirror the MCP tools in `../src/main/java/.../service/mcp/McpToolService.java`
and the renderers in `LlmController`: same defaults, limits, ordering and not-found wording
(SPEC 3). The constants live in `src/domain/wind.rs` and `src/domain/windy.rs`.

- `--format md` prints the `/llms` document verbatim where one exists; `search`, `status`,
  filtered lists and non-default models are rendered client-side by `src/render/md.rs`
- `tests/parity.rs` holds the client-side computation and Markdown to the server's output
  byte for byte. When `LlmController` changes, recapture the fixtures against a local backend
  (`./gradlew bootRun`, wait for the forecasts, then `tests/fixtures/capture.sh`) and change the
  CLI until the test passes. Refresh fixtures by capturing, never by hand-editing them into
  something the server never sent
- Wind classes (`src/domain/wind_class.rs`) and the 60 min staleness rule
  (`src/domain/time.rs`) must match `../src/frontend/js/common/{weather,date}.js` and
  `../android/core/domain`

## Output and Exit Codes

- Formats: `table` on a TTY, `plain` (tab-separated, no colour or glyphs) in a pipe, `json`,
  `md`. Piped output contains no ANSI codes; `NO_COLOR` is honoured
- `json` passes the backend payload through for `spot`, `spots`/`country` and `status`; the
  computed commands follow `schemas/*.json`. A change to a computed shape updates its schema;
  an incompatible one waits for a major version
- Exit codes are a contract (SPEC 4.12): 0 ok (an empty answer included), 1 unexpected,
  2 usage, 3 not found, 4 ambiguous without a TTY, 5 network, 6 rate limited

## Testing

- `domain/` is pure and tested exhaustively in unit tests next to the code
- `tests/cli.rs` runs the real binary against the mock backend, with config and cache dirs
  in a `TempDir` (`VARUN_CONFIG_DIR`, `VARUN_CACHE_DIR`). Assert stdout, stderr and exit code
- **No test touches the real varun.surf**
- Coverage target: ≥ 80 % lines

## Platforms and Distribution

- Linux x86_64 / arm64 (musl) and macOS on Apple Silicon only. No Windows or Intel Mac code
  paths, binaries or CI jobs (SPEC 1.1)
- The install script lives at `../src/frontend/assets/cli/install.sh`, not here: it ships with
  a backend deploy and must stay POSIX `sh` and version-agnostic. It builds download URLs from
  the archive names `varun-<ver>-<target>.tar.gz`, so renaming an archive in `cli.yml` breaks
  installs
- Releasing: bump `version` in `Cargo.toml`, add a `## X.Y.Z` section to `RELEASES.md` (the
  release body is taken from it), commit, then `git tag cli-vX.Y.Z && git push origin cli-vX.Y.Z`.
  The tag must equal `package.version`, or the release job fails
- `build.rs` bakes the git commit into `varun --version` (`VARUN_GIT_SHA` in CI)

## Conventions

- `cargo fmt` and `clippy -D warnings` clean; match the surrounding code's naming and
  comment density (module-level `//!` docs explain why, not what)
- `Cargo.lock` is committed; CI builds with `--locked`
- No telemetry and no update check
- When behaviour changes, update `README.md` (and `SPEC.md` where it deviates), and the CLI
  section of `../CLAUDE.md` if the backend contract moves
- Commit messages must NOT include "Co-Authored-By" or any AI attribution lines
