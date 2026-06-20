# Batch 9 — Long-haul / worldwide (6 spots)

Run in a fresh context. Use the **`kite-spot-creator`** agent per spot.
Six well-known destination spots across six countries. Each is its own
`country`. Warm water, thermal/trade-wind driven.

## Spots

- [ ] **Essaouira** — Morocco (`"Morocco"` already in dataset). Atlantic bay,
      strong N/NE thermal trade wind, choppy/wave. Best wind N/NE.
      Season spring–summer. Water cool (16–21°C) despite Africa.
- [ ] **Kalpitiya** — Sri Lanka (`"Sri Lanka"`, new). Lagoon (flat) + sea.
      SW monsoon (May–Sep) & NE (Dec–Feb). Best wind SW/W. Warm water.
- [ ] **Paje** — Zanzibar, Tanzania (`"Tanzania"`, new — name spot "Paje").
      Turquoise reef lagoon, flat at low tide. Best wind NE (Kaskazi Dec–Mar)
      & SE (Kusi Jun–Sep). Hazards: reef, tide, sea urchins.
- [ ] **Mui Ne** — Vietnam (`"Vietnam"`, new). Sea (wave) + nearby flat spots.
      NE monsoon (Nov–Apr). Best wind NE/E. Warm water.
- [ ] **Hatteras (Cape Hatteras)** — USA (`"USA"` already in dataset). OBX,
      North Carolina — Pamlico Sound flat water + ocean side. Best wind
      SW/NE/N. Major US spot. (More USA spots → batch 10 research.)
- [ ] **Turks and Caicos** — (`"Turks and Caicos"`, new — e.g. Long Bay Beach,
      Providenciales). Shallow flat turquoise water. Best wind E (trades).
      Beginner paradise. Season Nov–Jul.

## Research hints

- These are famous — Windguru stations almost certainly exist; still **fetch and
  confirm** the station matches (warm-water destinations are easy to mix up).
- Monsoon/trade seasonality is the key `season`/`bestWind` data — get it right.
- Reef/tide/urchin hazards for Paje & Turks; currents for Hatteras ocean side.

## Done when

- [ ] 6 entries appended, JSON parses, `./gradlew test` green.
- [ ] Checkboxes ticked here and in issue #141.
