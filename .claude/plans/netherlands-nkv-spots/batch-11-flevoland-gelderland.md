# Batch 11 — Flevoland & Gelderland (5 spots)

Run in a fresh context. Use the **`kite-spot-creator`** agent per spot.
`country` = `"Netherlands"`.

Randmeren and Veluwemeer inland water — the classic Dutch flatwater/freestyle scene.

## Spots

- [ ] **Almere**
      source: https://kitesurfvereniging.nl/kitespot/almere/
- [ ] **Lelystad – Bataviastrand**
      source: https://kitesurfvereniging.nl/kitespot/lelystad-bataviastrand/
- [ ] **Lelystad – Trintelhaven** **[RESTRICTED]**
      source: https://kitesurfvereniging.nl/kitespot/lelystad-trintelhaven/
      NKV policy: *Beperkt* — read the restriction on the source page and put it
      in `hazards` / `season` in BOTH `spotInfo` and `spotInfoPL`.
- [ ] **Strand Horst**
      source: https://kitesurfvereniging.nl/kitespot/strand-horst/
- [ ] **Elburg** **[RESTRICTED]**
      source: https://kitesurfvereniging.nl/kitespot/elburg/
      NKV policy: *Beperkt* — read the restriction on the source page and put it
      in `hazards` / `season` in BOTH `spotInfo` and `spotInfoPL`.

## Done when

- [ ] 5 entries appended to `src/main/resources/spots.json`
- [ ] `python3 -c "import json; json.load(open('src/main/resources/spots.json'))"` passes
- [ ] `./gradlew test` green
- [ ] boxes ticked here and in `README.md`
