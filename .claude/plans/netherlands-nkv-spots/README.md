# Netherlands spots from the NKV spotkaart

Adds Dutch kite spots sourced from the Nederlandse Kitesurf Vereniging spot map:
<https://kitesurfvereniging.nl/spotkaart/>

This completes the deferred item **"More Netherlands spots"** in
`.claude/plans/issue-141-new-spots/batch-10-research.md` (section B), and follows
the same execution model: self-contained batches, one fresh context each,
`kite-spot-creator` per spot.

## Source data

The map is a WordPress custom post type exposed over REST, so the list is
reproducible rather than hand-copied:

```
https://kitesurfvereniging.nl/wp-json/wp/v2/kitespot?per_page=100&page=1..2
https://kitesurfvereniging.nl/wp-json/wp/v2/provincie
https://kitesurfvereniging.nl/wp-json/wp/v2/beleid
```

ACF fields are not exposed over REST, but each spot's detail page
(`/kitespot/<slug>/`) carries what `spotInfo` needs: wind directions, season,
skill level, water depth, launch surface, facilities, embedded lat/lng and
sometimes a webcam.

## Scope

The map holds **106 spots**, classified by *beleid* (policy):

| Policy | Count | Decision |
|---|---|---|
| Toegestaan (allowed) | 68 | include |
| Beperkt (restricted) | 16 | include, restriction recorded in `hazards` / `season` |
| **Verboden (forbidden)** | **22** | **excluded — do not add** |

The 22 forbidden spots are deliberately left out. Publishing them as forecast
destinations would work against exactly the access rules the NKV and its spot
managers maintain.

From the 84 non-forbidden spots:

- **11** are already represented by existing `spots.json` entries → enrich, don't duplicate
- **5** are merged into a neighbouring entry sharing the same forecast point
- **68 new entries** remain → the batches below

Resulting catalogue: **159 → ~227 spots**, Netherlands going from 6 to ~74.

### Already covered (enrich the existing entry, do not create a new one)

| Existing `spots.json` entry | NKV entries it covers |
|---|---|
| Noordwijk | Noordwijk aan Zee, Noordwijk Langevelderslag |
| Scheveningen | Scheveningen – Zwarte Pad, Scheveningen – Noorderstrand |
| IJsselmeer - Workum | Workum |
| IJmuiden | IJmuiden Zone 1, IJmuiden Zone 2, IJmuiderslag |
| Slufterstrand (Maasvlakte) | Maasvlakte 2 – Slufter, Maasvlakte 2 – Spot P1 t/m P3 |
| Brouwersdam | Brouwersdam |

### Merged into one entry (same forecast point, two launches)

| Kept as | Merged in |
|---|---|
| Wijk aan Zee – Pier | Wijk aan Zee – de HangOut |
| Hoek van Holland (HWV) | Windsurfing Hoek van Holland |
| Rockanje Sportstrand | Rockanje Badstrand |
| Cadzand-bad – Vlamingpolder | Cadzand-bad – West |
| Grevelingendam – Zuid | Grevelingendam – Noord |

### Deliberately *not* merged

Pairs that look like duplicates but are different water and deserve separate
entries: each Wadden island's Noordzee vs Waddenzee side (Texel, Terschelling,
Ameland), Kijkduin vs Zandmotor, Oostvoorne Noordzee vs Oostvoornse meer,
Lelystad Bataviastrand vs Trintelhaven.

If a leaner catalogue is preferred, merging those six pairs would land at ~62
new entries instead of 68. That is a judgement call, not a correctness issue.

## The bottleneck: Windguru station IDs

`spots.json` requires a verified Windguru station, and this is where the time
goes — not the JSON writing.

- Windguru has **no public search API** (`qsearch.php`, `ajax_*`, `search.php`
  all 404).
- `micro.windguru.cz` accepts `lat`/`lon` **only for PRO accounts**; everyone
  else must pass a numeric `s=<spotId>`.

So each spot needs a manual/browser-assisted lookup, then verification that
`windguru.cz/<id>` really is that beach. Coverage is good in NL (existing
entries use both low IDs like `97`/`572` and newer `48xxx` ones), but expect a
few spots — small inland lakes especially — to have no dedicated station.

**If no station verifies: skip the spot and note it in the batch file.** Do not
guess a number, and do not silently reuse a neighbour's station without setting
it up as an explicit fallback.

## Execution

1. Open **one** batch file in a fresh session.
2. Per spot, run the **`kite-spot-creator`** agent (see
   `.claude/agents/kite-spot-creator.md`). Its Link Verification Protocol is
   mandatory — every URL must be fetched and confirmed.
3. Read the NKV source page linked in the batch file for conditions detail, but
   **write original EN + PL content**. The NKV text is their work and is Dutch;
   it is a research source, not copy to lift.
4. For *Beperkt* spots, put the restriction in `hazards` (and `season` when it
   is a seasonal window, e.g. Makkum: Oct–Apr only) in **both** `spotInfo` and
   `spotInfoPL`.
5. Append to `src/main/resources/spots.json`, then validate:
   - `python3 -c "import json; json.load(open('src/main/resources/spots.json'))"`
   - `./gradlew test` (`JsonSpotsStructureValidationTest` parses the whole file)
   - `check-spots` skill for duplicates / URL consistency
6. Tick the boxes here and in the batch file, then commit:
   `add <region> kite spots from NKV spotkaart` (no AI attribution).

## Batches

| # | File | Region | Spots |
|---|------|--------|-------|
| 01 | `batch-01-zeeland-west.md` | Zeeland — Westerschelde & Walcheren | 7 |
| 02 | `batch-02-zeeland-east.md` | Zeeland — Oosterschelde & delta dams | 6 |
| 03 | `batch-03-zuid-holland-coast.md` | Zuid-Holland — North Sea beaches | 6 |
| 04 | `batch-04-zuid-holland-delta.md` | Zuid-Holland — delta & inland water | 6 |
| 05 | `batch-05-noord-holland-south.md` | Noord-Holland — coast south | 6 |
| 06 | `batch-06-noord-holland-north.md` | Noord-Holland — coast north | 6 |
| 07 | `batch-07-markermeer-ijsselmeer-nh.md` | Noord-Holland — Markermeer & IJsselmeer | 6 |
| 08 | `batch-08-wadden-islands.md` | Wadden islands | 8 |
| 09 | `batch-09-friesland-ijsselmeer.md` | Friesland — IJsselmeer shore | 6 |
| 10 | `batch-10-wadden-mainland-north.md` | Waddenzee mainland & northern lakes | 6 |
| 11 | `batch-11-flevoland-gelderland.md` | Flevoland & Gelderland | 5 |

**Total: 68 new entries.** Every non-forbidden, non-duplicate spot from the map
is assigned to exactly one batch.

## Performance impact (measured, not estimated)

At 159 → ~227 spots (+43%):

| Area | Effect |
|---|---|
| Live-conditions loop (1 min) | **No change.** No NL spot matches any `FetchCurrentConditions` strategy, so `canProcess` filters them all out and no HTTP happens. No history queue is allocated either. |
| Forecast loop (3 h) | +43% requests to micro.windguru (318 → ~454 per cycle; 2 calls per spot — forecast + `ewam` wave). Peak concurrency is pinned by `FORECAST_SEMAPHORE_PERMITS = 32` regardless, so only cycle duration grows. Comfortably inside a 3 h budget; drop the semaphore to 16 if windguru starts throttling. |
| `/api/v1/spots` payload | 443 KB raw / 96.6 KB gzip → ~630 KB / ~138 KB gzip. The list endpoint ships only the 5-day daily forecast (no hourly, no history), so it scales linearly. |
| Frontend first paint | **Already handled** — commit `36ad2bd` made card rendering incremental, so first paint is ~2.4k DOM nodes regardless of spot count. |
| Memory | Modest. The 40-model hourly cache is per *visited* spot with a 3 h TTL, so it scales with traffic, not catalogue size. Note the `Dockerfile` sets no `-Xmx`, so the JVM takes 25% of container RAM — worth confirming VPS headroom before deploying all 11 batches. |
| AI analysis | Off by default. If enabled: ~$1.20 → ~$1.70/month. |

Ship batch by batch and watch `/api/v1/metrics` between them rather than
discovering a cliff at +68.

## Follow-up worth considering

The NKV pages pull live wind from **weather2kite.nl**, which sources
Rijkswaterstaat data. varun.surf currently has **zero** NL live stations, so a
single new `FetchCurrentConditions` strategy against RWS could light up live
wind for most of these spots at once.

This would invalidate the "no change" above: 68 spots × one call per minute is
~5× the current live load, plus 68 history queues. It needs a **batched** design
where one RWS call feeds many spots — not one strategy invocation per spot.
Scope it separately.

## Attribution

Worth crediting the NKV as the source in `README.md`. The spot-manager network
behind that map is who keeps this data accurate.

## Outcome

All 11 batches shipped. `spots.json` went **159 → 227 spots**, Netherlands **6 → 74**,
which is what the estimate above predicted. `./gradlew test` and `./gradlew testE2e`
are both green.

### Windguru station IDs — the bottleneck turned out to be solvable

The plan assumed each station needed a manual browser lookup. It doesn't. Windguru's
spot-search autocomplete is reachable without a PRO account, and it accepts a
coordinate:

```
https://www.windguru.cz/int/iapi.php?q=autocomplete_ss&type_info=true&latlon=1
  &spots=1&nearby_wg=50&lat=<lat>&lon=<lon>          # nearest forecast spots
  &query=<name>                                      # or search by name
```

It needs `Referer: https://www.windguru.cz/map/` (without it: `Unauthorized!`), and
returns `{data: <id>, value: <name>, lat, lon, type, id_user}`. `type` starting with
`W` and no `s` flag means a forecast spot rather than a live station; `id_user: 169`
marks the official Windguru spots, `id_user: 2` the ones auto-created from private
weather stations.

Sweeping that endpoint over a 0.07° × 0.10° grid across the Netherlands (1978 calls)
produced a local catalogue of **285 forecast spots**, 192 of them official. Every NKV
launch could then be matched by distance and confirmed by fetching
`https://www.windguru.cz/<id>` and reading the `<title>`. **No spot was skipped for
lack of a station.**

Launch coordinates came from the NKV pages themselves — each carries a Google Maps
"navigate" link with the precise lat/lng, which is more accurate than the map widget's
3-decimal attributes. Every `locationUrl` is a coordinate URL built from it.

### Spots sharing a forecast point

`Spot.wgId()` is derived from `windguruUrl` and is the app's public identifier — it
routes `/api/v1/spots/{id}`, `/llms/spots/{wgId}.md` and every in-memory cache — so two
spots may not carry the same Windguru URL. Ten entries genuinely share a forecast point
with a neighbour (the Ameland Noordzee/Waddenzee pair, the four Westerschelde spots
behind Borssele, and so on). They use the pattern the model already supports and the
existing Kadyny entry already uses: empty `windguruUrl` plus the shared point in
`windguruFallbackUrl`, so `forecastWgId()` still fetches the right forecast while
`wgId()` falls back to a deterministic per-spot id.

`JsonSpotsStructureValidationTest.shouldValidateNoDuplicateWindguruUrls` was comparing
raw strings, so several empty values would have tripped it. It now ignores empties, and
a new `shouldValidateNoDuplicateWgIds` asserts the invariant that actually matters.

### Known gaps

- **7 spots have no `windfinderUrl`** — Kamperland, Breskens Oost, Paulinapolder,
  Oostvoornse meer, Amstelmeer Lutjestrand Zuid, Warder and Mirns have no Windfinder
  page. Every other slug was fetched and its page title confirmed.
- **10 spots have a webcam.** The rest either have none or the NKV link turned out to
  be a photo gallery, a camera-network portal, or a cam pointed somewhere else.
- **Enkhuizen** is closed 1 Sep 2026 – 30 Apr 2027 for the *kustboog* coastal works.
  That is recorded in `hazards`, but it is a dated closure and will need removing.
- The **enrichment** of the 11 NKV spots already covered by existing entries
  (Noordwijk, Scheveningen, Workum, IJmuiden, Slufterstrand, Brouwersdam) was not part
  of any batch and has not been done.
- The **RWS live-wind strategy** in "Follow-up worth considering" remains unscoped.
