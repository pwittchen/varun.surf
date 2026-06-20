# Batch 8 — South Africa, Cape Town (5 spots)

Run in a fresh context. Use the **`kite-spot-creator`** agent per spot.
`country` = `"South Africa"` (new country). Driven by the SE "Cape Doctor"
wind (summer Nov–Mar). **Overlap risk** — see note before creating.

## Spots

- [x] **Bloubergstrand (Big Bay)** — Table View shoreline, Table Mountain view.
      Sea, wave/chop. Best wind SE/S. Iconic launch.
- [x] **Kite Beach** — Table View, the dedicated kite launch just south of
      Dolphin Beach. Sea. Best wind SE/S.
- [x] **Dolphin Beach** — Table View, between Big Bay and Kite Beach. Sea.
      Best wind SE/S.
- [x] **Langebaan** — West Coast lagoon (~1h north). Large flat shallow lagoon —
      beginner paradise. Best wind S/SW/SE. Distinct from Table View spots.
- [x] **Misty Cliffs** — Cape Peninsula (near Scarborough/Kommetjie). Wave spot,
      rocky, advanced. Best wind SE/S. Hazards: rocks, currents, sharks.

## ⚠️ Overlap check (do this first)

Bloubergstrand / Kite Beach / Dolphin Beach are three named launches on the
**same ~3 km Table View beach** and very likely share one Windguru station
(e.g. "Bloubergstrand" / "Table View"). Decide whether to:
- create all three as distinct entries (different `locationUrl`, shared
  `windguruUrl`), **or**
- consolidate if they're effectively the same forecast point.
Document the decision in the entries; differentiate by exact launch coords.

## Research hints

- "Cape Doctor" = strong SE, summer (Nov–Mar) season; water cold (12–18°C,
  Atlantic/Benguela) — wetsuit even in summer.
- Windfinder: `bloubergstrand`, `langebaan`, `misty_cliffs` — verify.
- Sharks/currents are real hazards on the peninsula — note for Misty Cliffs.

## Done when

- [x] Entries appended (5 spots — all three Table View launches kept distinct
      with their own Windguru stations), JSON parses, `./gradlew test` green.
- [x] Checkboxes ticked here and in issue #141.
