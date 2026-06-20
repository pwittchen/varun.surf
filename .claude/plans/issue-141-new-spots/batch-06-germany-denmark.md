# Batch 6 — Germany + Denmark (5 spots)

Run in a fresh context. Use the **`kite-spot-creator`** agent per spot.
Two countries already in the dataset (`"Germany"`, `"Denmark"`). One German
name needs disambiguation (see notes).

## Germany (`country` = "Germany")

- [x] **Tatort Hawaii** — resolved to Stein (Kiel Fjord), Schleswig-Holstein.
      Windguru 500808. Best wind found W/NW/NE/E (fjord orientation; SW/S poor).
- [ ] ~~**Berzdorfer See**~~ — **SKIPPED**: kitesurfing is legally banned there
      (Saxon water law, fines up to €50,000); windsurf/wing/SUP only.
- [x] **Ummaii** → resolved to **Ummanz (Suhrendorf)**, island west of Rügen.
      Windguru 665105 (Suhrendorf, Ummaii), fallback 48115. Flat Bodden water.

## Denmark (`country` = "Denmark")

- [x] **Kitespot Farø** — causeway between Farø and Bogø, Storstrømmen, Zealand.
      Windguru 137343. Flat shallow water over sandbanks.
- [x] **Møn / Klintholm Strand** — Klintholm Havn, Møn island. Windguru 48088.
      Baltic bay, chop/small waves. Møns Klint cliffs flagged as no-go in hazards.

## Research hints

- Baltic spots: water 4–20°C, season spring–autumn, frequent SW/W wind.
- Berzdorfer See is **inland** — freshwater, thermal, summer; adjust hazards.
- For `Ummaii`/`Ummanz`: Suhrendorf on Ummanz is the classic launch — name it
  precisely if that's the match.

## Done when

- [x] 4 entries appended (Tatort Hawaii, Ummanz, Kitespot Farø, Møn/Klintholm);
      Berzdorfer See skipped (kite ban). JSON parses, `./gradlew test` green.
- [x] Checkboxes ticked here and in issue #141.
