# Batch 4 — Italy (6 spots)

Run in a fresh context. Use the **`kite-spot-creator`** agent per spot.
`country` = `"Italy"`. Mix of an Alpine lake, a northern lagoon-beach, and
southern/Sicilian wave spots. Two names need disambiguation (see notes).

## Spots

- [x] **Reschensee (Lago di Resia)** — South Tyrol Alpine lake (the famous
      submerged bell tower). Flat/choppy cold lake, thermal winds. Best wind
      N/S valley thermals. Inland — cold water, summer season.
      → added as `Reschensee (Lago di Resia)`, WG 274295 (fallback 71178).
- [x] **Grado – Pineta** — Friuli, northern Adriatic. Shallow flat lagoon/beach.
      Best wind E (Bora-influenced) / SW. Beginner-friendly shallows.
      → added as `Grado Pineta`, WG 49154.
- [x] **Lecce** — Puglia (likely San Cataldo / Frigole beaches near Lecce on the
      Adriatic). Sea, chop/wave. Best wind N/NW. Resolve exact launch beach.
      → added as `Frigole (Lecce)`, WG 196766 (fallback 120159).
- [x] **Puzziteddu** — Sicily south coast (Mazara del Vallo area). Wave spot.
      Best wind SW/W/S. Hazards: shore break, rocks. Verify Windguru station.
      → added as `Puzziteddu`, WG 49081 (fallback 889235).
- [x] **Capo Feto** — Sicily south coast (near Mazara del Vallo, by Puzziteddu).
      Flat-water lagoon behind the beach + wave outside. Best wind SW/W.
      → added as `Capo Feto`, WG 331569.
- [x] **Tranquinia** ⚠️ — likely a **misspelling**. Probably *Tonnara di
      Bonagia* or *Marsala / Stagnone (Tranquillo?)* in Sicily — **resolve the
      real spot name first**, then create. If unresolvable, flag and skip.
      → **SKIPPED**: unresolvable misspelling, zero matches in any kite source;
      no verifiable Windguru station. (Stagnone/Marsala already covered by
      existing `Lo Stagnone`.)

## Research hints

- Sicilian south-coast spots (Puzziteddu, Capo Feto) cluster near Mazara del
  Vallo — they may share a Windguru station; verify and use fallback if needed.
- Stagnone/Marsala is the big flat-water Sicily area — if `Tranquinia` resolves
  to that region, name it accurately.
- Reschensee is **inland Alpine** — cold water, summer thermals, different
  hazards from coastal spots.

## Done when

- [x] 5–6 entries appended (Tranquinia only if resolved), JSON parses,
      `./gradlew test` green. → 5 added (Tranquinia skipped).
- [x] Checkboxes ticked here and in issue #141.
