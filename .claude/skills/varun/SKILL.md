---
name: varun
description: Answer questions about kite spots, weather forecasts, and live wind conditions from varun.surf by fetching its public LLM-friendly Markdown endpoints (llms.txt). Use when the user asks about a specific kite spot, current wind conditions, hourly/daily forecasts, or wants to compare spots/countries covered by varun.surf.
---

# Varun Skill

Answer user questions about kite spots, forecasts, and live wind conditions by consuming the public, LLM-friendly Markdown endpoints exposed by varun.surf. These endpoints are free to fetch, do not require a session cookie, and are served with `text/markdown`.

## When to Use This Skill

Trigger this skill for questions such as:
- "What's the wind right now in Hel?"
- "Is it a good day to kite in Tarifa tomorrow?"
- "What kite spots does varun cover in Spain?"
- "Which countries are available on varun.surf?"
- "Show me the forecast for Podersdorf."
- "Compare conditions between Jastarnia and Chałupy."

Do NOT use this skill for questions about the varun.surf codebase, architecture, or deployment — use the `explain`, `arch-check`, or general research tools for that.

## Available Endpoints

Base URL: `https://varun.surf`

| Purpose | Endpoint | Notes |
|---------|----------|-------|
| Site overview & wind-speed guide | `/llms.txt` | Project summary, features, FAQ, SEO context |
| All spots index | `/llms/spots.md` | Names, countries, links to per-spot docs |
| Single spot (forecast + live conditions) | `/llms/spots/{wgId}.md` | `wgId` is the Windguru station ID |
| All countries index | `/llms/countries.md` | Countries with spot counts |
| Country detail | `/llms/countries/{slug}.md` | `slug` = lowercase country name, spaces → `-` (e.g. `poland`, `czech-republic`) |

All endpoints return pre-rendered Markdown optimized for LLMs. Per-spot documents include:
- Location & metadata (country, coordinates, water type, best wind directions, season, hazards)
- Current live conditions (wind speed, gust, direction, temperature, last update) when available
- Hourly forecast (up to 24 hours)
- Links back to Windguru, Windfinder, ICM, webcam

## How to Answer Questions

### Step 1 — Identify what the user is asking about

Classify the request:
- **Specific spot** (by name) → need the per-spot Markdown document
- **Country** (e.g. "spots in Poland") → fetch the country detail
- **List/catalog** ("what spots exist?") → fetch the spots or countries index
- **Project meta** ("what is varun.surf?") → fetch `/llms.txt`

### Step 2 — Resolve the spot identifier (when needed)

To fetch a per-spot document you need the numeric `wgId`. If the user names a spot but you do not already know its `wgId`:

1. Fetch `/llms/spots.md`
2. Find the matching spot name (case-insensitive, allow partial matches) and extract the `wgId` from the link `/llms/spots/{wgId}.md`
3. Then fetch the per-spot document

For country-scoped questions, use `/llms/countries/{slug}.md` first — it lists every spot in that country with its `wgId`.

### Step 3 — Fetch the relevant document(s)

Use `WebFetch` with the full `https://varun.surf/...` URL. Prefer fetching only what is needed — for a single spot, one document is enough. For comparisons, fetch each spot's document (in parallel where possible).

### Step 4 — Answer using the fetched data

Ground every claim in the fetched Markdown. Do NOT invent wind speeds, coordinates, or forecast values — if the document does not contain the data, say so.

When interpreting conditions for kitesurfing, apply the wind-speed guide from `/llms.txt`:

| Wind (knots) | Kiteability |
|--------------|-------------|
| 0–6 | Too light |
| 7–11 | Light; foil/very large kites, experienced riders |
| 12–18 | Beginner/intermediate friendly (10–14 m²) |
| 19–25 | Ideal (8–12 m²) |
| 26–33 | Strong; advanced (5–9 m²) |
| 34+ | Extreme/dangerous (3–5 m²) |

Always cite the source URL at the end of your answer so the user can verify.

## Output Format

Keep responses concise and structured. A typical answer shape:

```
**[Spot name], [Country]**

**Live conditions** (as of [timestamp from doc]):
- Wind: X kt, gusts Y kt, direction Z° ([cardinal])
- Temperature: T°C

**Forecast (next N hours):**
- HH:MM — X kt / Y kt gusts, [cardinal]
- ...

**Assessment:** [one or two sentences applying the wind-speed guide to the user's question]

Source: https://varun.surf/llms/spots/{wgId}.md
```

For list/catalog answers, a short bulleted list grouped by country is usually best. For country questions, include the spot count and a few notable spots.

## Guardrails

- **Do not fabricate data.** If the document is missing current conditions or forecast hours, state that explicitly.
- **Stale live data is possible.** Live conditions can lag. If the `updatedAt` timestamp is more than ~30 minutes old, flag it as potentially stale.
- **Respect the source.** Always include the source URL. These endpoints are free but the user should be able to verify.
- **Stay on-topic.** This skill is for spot/forecast/conditions questions; redirect codebase questions to other skills.
- **No session cookie needed.** The `/llms/*` and `/llms.txt` endpoints are public — do not attempt to authenticate or use `/api/v1/*`.

## Notes

- Spot names may contain diacritics (e.g. "Chałupy", "Władysławowo"). Match tolerantly.
- Country slugs are lowercase with hyphens: `poland`, `czech-republic`, `united-kingdom`.
- Forecasts are refreshed every 3 hours server-side; live conditions every minute. The Markdown snapshot reflects the state at fetch time.
