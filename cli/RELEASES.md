# varun CLI releases

The `publish` job of `.github/workflows/cli.yml` takes a release's notes from its
`## X.Y.Z` section below, so add one before tagging `cli-vX.Y.Z`.

## 0.1.0

First release of `varun`, the varun.surf command-line client.

- `wind`, `windy`, `spot`, `search`, `spots`, `countries`, `country` and `status`: the
  questions the varun.surf MCP server answers, with the same defaults and limits
- `now` for live conditions, `fav` for favourites with aliases, `open`, `config`,
  shell completions, and `--watch` on `now` and `windy`
- Spot names resolve without diacritics (`mrzezyno` finds Mrzeżyno), with a picker when
  a name is ambiguous
- Output as tables, tab-separated text, JSON (versioned schemas) or the server's own
  Markdown; units in kts, m/s, km/h, mph or Beaufort; English and Polish
- Linux (x86_64, arm64) and macOS on Apple Silicon, installed with
  `curl -fsSL https://varun.surf/cli/install.sh | sh`
