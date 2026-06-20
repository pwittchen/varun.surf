# Batch 1 — Malta (3 spots)

Run in a fresh context. For each spot, use the **`kite-spot-creator`** agent,
then append the validated JSON to `src/main/resources/spots.json`.
`country` = `"Malta"` (new country for the dataset).

## Spots

- [x] **Ramla Bay** — Gozo, Malta. NW-facing red-sand bay (Ramla l-Ħamra).
      Sea, can be wavy. Best wind likely NW/W. Verify Windguru station for
      Gozo / Ramla; if none, nearest Gozo station as fallback.
- [x] **Għajn Tuffieħa** — NW Malta, near Golden Bay. Sea, wave/chop.
      Best wind likely NW. Note: bay shared with Golden Bay — pick exact launch.
- [x] **Gnejna Bay** — NW Malta, next bay south of Għajn Tuffieħa. Sea.
      Best wind likely NW/W. Verify it is actually kited (cliffs/rocks hazard).

## Research hints

- Maltese kiting is mostly NW (Majjistral) wind, autumn–spring season,
  warm water (16–26°C). Summer can be light. Confirm per spot.
- Windguru: search "windguru Malta", "windguru Mellieħa", "windguru Gozo".
- Hazards to check: rocky shorelines, swimmers in summer, limited launch space,
  cliffs around Gnejna/Għajn Tuffieħa.

## Done when

- [x] 3 entries appended, JSON parses, `./gradlew test` green.
- [x] Checkboxes ticked here and in issue #141.
