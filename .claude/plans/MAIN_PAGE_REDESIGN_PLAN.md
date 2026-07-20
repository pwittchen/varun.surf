# Main Page Redesign Plan — Glanceable Wind Status

> Goal: a kitesurfer opens **varun.surf** and, without reading a single number, knows
> *which spots are firing right now*. Every change below serves that one job.
>
> This plan **builds on** the existing token system, theming, and wind-strength
> semantics — it does not replace them.

## Constraints (confirmed from the codebase)

- **Vanilla CSS only** — `styles.css` is regex-minified by `build.ts`; no Sass/PostCSS/autoprefixer.
- **Cards are template strings** built in `js/page/index.js:1202` (`createSpotCard`); any
  DOM/class change must be coordinated between `styles.css` and that function.
- **"Inter" is referenced but never loaded** (`styles.css:272`), so most visitors currently
  see their OS system font.
- Colors live twice — each token change means editing `:root` **and** the
  `[data-theme="light"]` override block.

## Existing status system to reuse

Wind thresholds already exist (`weather.js:36-44`) and map cleanly to kite ridability:

| Live wind | Class (exists) | Status color | Meaning |
|---|---|---|---|
| < 8 kts | `wind-weak` | grey `--wind-weak` | Not rideable |
| 8–11 | `wind-weak` | grey/blue | Foil only |
| 12–17 | `wind-moderate` | green `--wind-moderate` | **Firing** (large/medium kite) |
| 18–25 | `wind-strong` | amber `--wind-strong` | Strong (small kite) |
| 25+ | `wind-extreme` | red `--wind-extreme` | Nuking |

> Suggested: split the current `< 12 = weak` bucket at 8 kts so "foil only" reads
> differently from "dead". One-line change in `getWindClassSimple`.

---

## Change 1 — Sort firing spots to the top (biggest UX win)

Spots currently render in JSON/favorite order (`index.js:244`). Add a **default sort by live
wind strength descending**, with a segmented control in the header:

```
[ ●  Firing now ]  [ ★ Favorites ]  [ A–Z ]  [ Country ]
```

- `Firing now` (new default): spots with fresh live conditions, sorted by current wind desc;
  spots without live data or stale (`isConditionsOutdated`, `index.js:1177`) sink to the bottom.
- Favorites still float via existing pin logic.
- Optional: a thin **"🔥 3 spots firing"** summary line above the grid, linking to them.

Implementation: a comparator added to the existing `sortSpots` path (`index.js:239-240`).
No DOM restructuring. Highest impact, lowest risk.

## Change 2 — Status-driven card accent (depth + signal)

Card DOM gains one class: `spot-card status-{weak|moderate|strong|extreme}` (added in
`createSpotCard`).

```css
.spot-card {
  background: var(--card-bg);
  border: 1px solid var(--border-primary);
  border-radius: var(--radius-lg);        /* new token, 14px */
  box-shadow: var(--shadow-card);          /* turn elevation back ON */
  border-left: 3px solid transparent;      /* status rail */
  transition: box-shadow .2s ease, border-color .2s ease, transform .2s ease;
}
.spot-card:hover { box-shadow: var(--shadow-hover); transform: translateY(-2px); }

.spot-card.status-moderate { border-left-color: var(--wind-moderate); }
.spot-card.status-strong   { border-left-color: var(--wind-strong); }
.spot-card.status-extreme  { border-left-color: var(--wind-extreme); }

/* firing cards get a faint tint so they read as a group */
.spot-card.status-moderate,
.spot-card.status-strong {
  background: linear-gradient(180deg, var(--wind-moderate-bg) 0%, var(--card-bg) 90px);
}
```

A colored left rail + subtle top tint + real shadow makes 100 grey rectangles resolve into a
scannable board — cheap, because `--wind-*-bg` tokens already exist.

## Change 3 — A "now" hero readout on each card

The most-wanted number should be the biggest thing on the card, not a table cell. Lead the
card body with live conditions:

```
┌─ Cabarete · DR ──────────────── ★ ─┐
│                                     │
│   22        ↗    gust 27    22°C    │   ← big number, arrow, gust, temp
│   kts       NE   ● LIVE 2m ago      │   ← live dot (green) + freshness
│                                     │
│  ┌─ 5-day forecast ──────────────┐  │   ← existing table, demoted, collapsible
```

- Wind number: ~`2.4rem`, `font-weight: 700`, colored by status token.
- Direction: large rotated arrow (rotation already computed, `weather.js:28`) + cardinal.
- `● LIVE · 2m ago` reuses the existing pulsing `.live-dot` / `.outdated` amber
  (`styles.css:1406-1449`) — relocated and enlarged.
- Spots with **no** live station show forecast-for-now, clearly labeled `forecast` (not `LIVE`),
  keeping the distinction honest.
- The forecast table stays as the *secondary* read; consider collapsing it behind a "5-day ▾"
  toggle in the dense 3-column view.

## Change 4 — Foundational token & polish additions

1. **Actually load Inter** — self-host `Inter var` woff2 in `assets/` with `font-display: swap`.
   Single cheapest real modernization on the page.
2. **Add missing scales as tokens** (color is tokenized; radius/spacing/shadow are hard-coded):
   ```css
   --radius-sm: 8px; --radius-md: 10px; --radius-lg: 14px; --radius-pill: 999px;
   --space-1: 4px; --space-2: 8px; --space-3: 12px; --space-4: 16px; --space-6: 24px;
   --shadow-card: 0 1px 2px rgba(0,0,0,.2), 0 2px 8px rgba(0,0,0,.15);
   --shadow-hover: 0 4px 20px rgba(0,0,0,.35);
   ```
3. **Tablet 2-column stage.** Grid jumps 3-col → 1-col at 929px (`styles.css:5072`). Add a
   2-column band (~600–930px).
4. **Slimmer header.** Drop header padding 20px→14px; let the `2.8rem/weight-100` wordmark
   shrink on scroll. Content should dominate over chrome.
5. **Status legend** — a small dismissible key (grey/green/amber/red = not
   rideable/firing/strong/nuking) so the color code is learnable on first visit.

---

## Change 5 — Accent & color updates

Principle: **the brand accent must stay visually distinct from the wind-status colors**
(grey/green/amber/red), or accent-colored chrome (links, active toggles, focus rings) gets
confused with "this spot is firing." That keeps the accent in the blue/cyan family — not green
or teal.

### Dark theme (default)

| Token | Now | Proposed | Why |
|---|---|---|---|
| `--bg-primary` | `#0f0f0f` | `#0a0e14` | Cool near-black "ocean night" — intentional, not flat |
| `--bg-secondary` (cards) | `#1a1a1a` | `#121821` | Cards separate from bg by hue + value, not just a border |
| `--bg-tertiary` | `#262626` | `#1c2530` | Same cool shift |
| `--border-primary` | `#262626` | `#1e2733` | Match cool neutrals |
| `--accent-primary` | `#4a9eff` | `#22c3e6` (cyan) | Wind/water identity; distinct from green "firing" |
| `--accent-secondary` | `#216ebb` | `#0e8aa8` | Deeper cyan for gradients/active |
| `--text-secondary` | `#9ca3af` | `#8b97a8` | Faintly cool to sit on new neutrals |

The shift is subtle on purpose — a considered dark theme, not "a blue website."

### Wind-status colors — tune, don't redefine

These carry meaning; keep intent, adjust only for the cooler background and the 8-kt split:

- **Weak** `#6b7280` → `#64748b` (cooler slate)
- **Moderate / firing** `#22c55e` → `#2ee66d` (brighter emerald so "firing" pops as the reward state)
- **Strong** `#f59e0b` → keep (amber is right)
- **Extreme** `#ef4444` → keep, or `#f5484a` for a hair more saturation on dark

### Light theme

- `--accent-primary` `#0d6efd` (Bootstrap default blue) → `#0891b2` — cyan matching the dark
  theme identity, darkened to hold contrast on white.
- `--bg-secondary` `#f5f7fa` → `#f2f6f9` for the same cool cast.

### Beyond color

1. **Turn elevation back on.** Cards are `box-shadow: none` (`styles.css:929`) — the single
   biggest contributor to the flat/dated feel. Soft shadow + status left-rail = depth.
2. **Load the accent font** (see Change 4.1) so the type scale renders as designed.

---

## Caveats

- **Contrast/accessibility:** the brighter "firing" green and cyan accents need a WCAG check
  against both card backgrounds — especially text over the `--wind-*-bg` tint overlays. Verify
  before shipping.
- **Every color lives twice** (`:root` + `[data-theme="light"]`). If tokens did all the theme
  work, these overrides could largely collapse — a separate refactor.
- **Open question:** does live wind data cover enough spots for "Firing now" sorting to be
  meaningful, or do most spots only have forecast data? Determines whether Change 1 sorts on
  real live wind or needs a forecast-now fallback as the primary signal.

## What NOT to do

- Don't touch the light/dark token architecture — only add to it.
- Don't add a framework or build step — stays vanilla per `build.ts`.
- Don't restructure the forecast table internals — it's demoted, not redesigned.

---

## Suggested sequence (each independently shippable)

1. **Sort by "Firing now"** + segmented control — pure JS, highest impact, lowest risk.
2. **Card accent + elevation + new scale tokens** — CSS-only + one class in `createSpotCard`.
3. **Accent & color updates** (Change 5) — token edits in both theme blocks; validate contrast.
4. **"Now" hero readout** — touches card template markup + CSS together.
5. **Polish:** load Inter, tablet breakpoint, header slimming, status legend.

Recommended starting point: **step 1** — the difference is felt immediately with the least code.
