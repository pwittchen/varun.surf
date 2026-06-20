# Batch 3 — France, Atlantic / North (5 spots)

Run in a fresh context. Use the **`kite-spot-creator`** agent per spot.
`country` = `"France"`. Tidal Atlantic/Channel spots — check tide-dependent
launch windows and note them in `hazards`/`description`.

## Spots

- [ ] **Île de Noirmoutier** — Vendée island (e.g. La Guérinière / Barbâtre).
      Sea + flat areas at low tide. Best wind W/SW/NW. Strong tides.
- [ ] **Quiberon** — Brittany peninsula (Penthièvre / isthmus). Ocean side wavy,
      bay side flatter. Best wind W/SW. Popular school spot.
- [ ] **La Baule / Pornichet** — Loire-Atlantique bay. Long sandy beach, sea.
      Best wind W/SW/S. Tide-dependent. Webcam likely exists.
- [ ] **Saint-Malo** — Brittany, big tidal range (one of Europe's largest).
      Sea, chop. Best wind W/SW/NW. Hazards: huge tides, rocks, swimmers.
- [ ] **Wissant** — Hauts-de-France, between Cap Gris-Nez & Blanc-Nez.
      Sea, wave/chop, strong winds. Best wind W/SW. Verify Windguru station;
      if none, nearest (e.g. Cap Gris-Nez) as fallback.

## Research hints

- All tide-sensitive — mention best tide state if known.
- Windfinder slugs: `quiberon`, `la_baule`, `saint_malo`, `wissant`, etc.
  Verify each resolves.
- Season is broadly spring–autumn; water cold (Atlantic/Channel ~10–19°C).

## Done when

- [ ] 5 entries appended, JSON parses, `./gradlew test` green.
- [ ] Checkboxes ticked here and in issue #141.
