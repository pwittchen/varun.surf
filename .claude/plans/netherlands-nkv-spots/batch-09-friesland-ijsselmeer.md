# Batch 09 — Friesland — IJsselmeer shore (6 spots)

Run in a fresh context. Use the **`kite-spot-creator`** agent per spot.
`country` = `"Netherlands"`.

Freshwater IJsselmeer launches south and east of the Afsluitdijk. Complements the existing IJsselmeer - Workum entry.

## Spots

- [ ] **Makkum** **[RESTRICTED]**
      source: https://kitesurfvereniging.nl/kitespot/makkum/
      NKV policy: *Beperkt* — read the restriction on the source page and put it
      in `hazards` / `season` in BOTH `spotInfo` and `spotInfoPL`.
- [ ] **Hindeloopen**
      source: https://kitesurfvereniging.nl/kitespot/hindeloopen/
- [ ] **Stavoren – it Suderstrand**
      source: https://kitesurfvereniging.nl/kitespot/stavoren/
- [ ] **Mirns**
      source: https://kitesurfvereniging.nl/kitespot/mirns/
- [ ] **Lemmer** **[RESTRICTED]**
      source: https://kitesurfvereniging.nl/kitespot/lemmer/
      NKV policy: *Beperkt* — read the restriction on the source page and put it
      in `hazards` / `season` in BOTH `spotInfo` and `spotInfoPL`.
- [ ] **Kornwerderzand** **[RESTRICTED]**
      source: https://kitesurfvereniging.nl/kitespot/kornwerderzand/
      NKV policy: *Beperkt* — read the restriction on the source page and put it
      in `hazards` / `season` in BOTH `spotInfo` and `spotInfoPL`.

## Done when

- [ ] 6 entries appended to `src/main/resources/spots.json`
- [ ] `python3 -c "import json; json.load(open('src/main/resources/spots.json'))"` passes
- [ ] `./gradlew test` green
- [ ] boxes ticked here and in `README.md`
