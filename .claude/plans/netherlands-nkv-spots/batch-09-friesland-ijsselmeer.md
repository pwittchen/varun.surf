# Batch 09 — Friesland — IJsselmeer shore (6 spots)

Run in a fresh context. Use the **`kite-spot-creator`** agent per spot.
`country` = `"Netherlands"`.

Freshwater IJsselmeer launches south and east of the Afsluitdijk. Complements the existing IJsselmeer - Workum entry.

## Spots

- [x] **Makkum** **[RESTRICTED]**
      source: https://kitesurfvereniging.nl/kitespot/makkum/
      NKV policy: *Beperkt* — read the restriction on the source page and put it
      in `hazards` / `season` in BOTH `spotInfo` and `spotInfoPL`.
- [x] **Hindeloopen**
      source: https://kitesurfvereniging.nl/kitespot/hindeloopen/
- [x] **Stavoren – it Suderstrand**
      source: https://kitesurfvereniging.nl/kitespot/stavoren/
- [x] **Mirns**
      source: https://kitesurfvereniging.nl/kitespot/mirns/
- [x] **Lemmer** **[RESTRICTED]**
      source: https://kitesurfvereniging.nl/kitespot/lemmer/
      NKV policy: *Beperkt* — read the restriction on the source page and put it
      in `hazards` / `season` in BOTH `spotInfo` and `spotInfoPL`.
- [x] **Kornwerderzand** **[RESTRICTED]**
      source: https://kitesurfvereniging.nl/kitespot/kornwerderzand/
      NKV policy: *Beperkt* — read the restriction on the source page and put it
      in `hazards` / `season` in BOTH `spotInfo` and `spotInfoPL`.

## Done when

- [x] 6 entries appended to `src/main/resources/spots.json`
- [x] `python3 -c "import json; json.load(open('src/main/resources/spots.json'))"` passes
- [x] `./gradlew test` green
- [x] boxes ticked here and in `README.md`
