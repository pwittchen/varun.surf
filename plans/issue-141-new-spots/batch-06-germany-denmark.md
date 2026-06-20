# Batch 6 — Germany + Denmark (5 spots)

Run in a fresh context. Use the **`kite-spot-creator`** agent per spot.
Two countries already in the dataset (`"Germany"`, `"Denmark"`). One German
name needs disambiguation (see notes).

## Germany (`country` = "Germany")

- [ ] **Tatort Hawaii** — kite spot near Heiligenhafen / Großenbrode, Baltic
      coast (a named local kite beach / school). Sea, flat-ish bay.
      Best wind SW/W. Resolve exact beach + Windguru station.
- [ ] **Berzdorfer See** — lake near Görlitz (Saxony), former open-cast mine.
      Flat freshwater lake, thermal/gradient wind. Best wind SW/W. Inland.
- [ ] **Ummaii** ⚠️ — likely a **misspelling**. Probably *Ummanz* (island west
      of Rügen, Baltic — well-known flat-water kite area). **Resolve to Ummanz**
      (or correct name) before creating; if unresolvable, flag and skip.

## Denmark (`country` = "Denmark")

- [ ] **Kitespot Farø** — Farø islands area (by the Farø bridges, Storstrømmen),
      Zealand. Flat shallow water. Best wind W/SW. Verify launch + station.
- [ ] **Møn / Klintholm Strand** — Møn island, near Klintholm Havn. Sea/bay.
      Best wind W/SW/S. Note Møns Klint cliffs nearby (scenery, not launch).

## Research hints

- Baltic spots: water 4–20°C, season spring–autumn, frequent SW/W wind.
- Berzdorfer See is **inland** — freshwater, thermal, summer; adjust hazards.
- For `Ummaii`/`Ummanz`: Suhrendorf on Ummanz is the classic launch — name it
  precisely if that's the match.

## Done when

- [ ] 4–5 entries appended (Ummaii only if resolved), JSON parses,
      `./gradlew test` green.
- [ ] Checkboxes ticked here and in issue #141.
