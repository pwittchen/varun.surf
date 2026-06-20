# Issue #141 — Add new set of kite spots around the world

Master plan for [issue #141](https://github.com/pwittchen/varun.surf/issues/141).
The work is split into **self-contained batches** so each can be executed in a
**fresh Claude context** without filling the window. Each batch file lists its
spots, per-spot research hints, and the exact run instructions.

## How to run a batch

1. Open **one** batch file (e.g. `batch-01-malta.md`) in a fresh session.
2. For each spot, invoke the **`kite-spot-creator`** agent (one spot at a time,
   or a few in parallel). The agent handles research, URL verification, and
   EN + PL content. See `.claude/agents/kite-spot-creator.md`.
3. Append each validated entry to `src/main/resources/spots.json`.
4. Validate + test:
   - `python3 -c "import json; json.load(open('src/main/resources/spots.json'))"`
   - `./gradlew test` (spots.json validation tests)
   - optionally `check-spots` skill for data consistency.
5. Tick the checkboxes in the batch file and in issue #141.
6. Commit (no AI attribution per project convention). Suggested message:
   `add <region> kite spots (#141)`.

## Schema reminder (mirror an existing entry in spots.json)

Fields: `name`, `country`, `windguruUrl`, `windguruFallbackUrl` (optional),
`windfinderUrl`, `webcamUrl`, `locationUrl`, `spotInfo`, `spotInfoPL`.
There is **no** `id` / `wgId` / `icmUrl`. Use `""` for unknown optional URLs —
**a wrong URL is worse than an empty one**. Windguru station numbers and
`maps.app.goo.gl` short codes must be verified, never guessed (use a
`https://www.google.com/maps?q=<lat>,<lng>` coordinate URL when no verified
short link exists).

## Batches

| # | File | Region | Spots |
|---|------|--------|-------|
| 1 | `batch-01-malta.md` | Malta | 3 |
| 2 | `batch-02-france-med.md` | France (Mediterranean) | 5 |
| 3 | `batch-03-france-atlantic.md` | France (Atlantic / North) | 5 |
| 4 | `batch-04-italy.md` | Italy | 6 |
| 5 | `batch-05-netherlands.md` | Netherlands | 4 |
| 6 | `batch-06-germany-denmark.md` | Germany + Denmark | 5 |
| 7 | `batch-07-iberia.md` | Portugal + Spain | 5 |
| 8 | `batch-08-cape-town.md` | South Africa (Cape Town) | 5 |
| 9 | `batch-09-long-haul.md` | Morocco, Sri Lanka, Zanzibar, Vietnam, USA, Turks & Caicos | 6 |
| 10 | `batch-10-research.md` | Research / TBD (Colombia, Boracay, more NL / USA / inland AT-IT-CH) | n/a |

**Total concrete spots: 44** across batches 1–9, plus a research batch (10).

## Notes / risks

- Some spots may **not have a dedicated Windguru station** (e.g. small/inland
  beaches). If none verifies, pick the nearest station and set the exact spot as
  `windguruFallbackUrl` only if it resolves — otherwise flag the spot in the
  batch file and skip rather than ship a wrong link.
- Several Cape Town spots (Bloubergstrand, Kite Beach, Dolphin Beach) sit on the
  same Table View shoreline and may share a Windguru station — verify overlap
  before creating near-duplicate entries (see batch 8).
- Issue lists `Tranquinia` and `Ummaii` — likely misspellings; resolve the real
  spot name during research (see batch notes).
