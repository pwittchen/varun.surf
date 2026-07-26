# Batch 11 — Flevoland & Gelderland (5 spots)

Run in a fresh context. Use the **`kite-spot-creator`** agent per spot.
`country` = `"Netherlands"`.

Randmeren and Veluwemeer inland water — the classic Dutch flatwater/freestyle scene.

## Spots

- [x] **Almere**
      source: https://kitesurfvereniging.nl/kitespot/almere/
- [x] **Lelystad – Bataviastrand**
      source: https://kitesurfvereniging.nl/kitespot/lelystad-bataviastrand/
- [x] **Lelystad – Trintelhaven** **[RESTRICTED]**
      source: https://kitesurfvereniging.nl/kitespot/lelystad-trintelhaven/
      NKV policy: *Beperkt* — read the restriction on the source page and put it
      in `hazards` / `season` in BOTH `spotInfo` and `spotInfoPL`.
- [x] **Strand Horst**
      source: https://kitesurfvereniging.nl/kitespot/strand-horst/
- [x] **Elburg** **[RESTRICTED]**
      source: https://kitesurfvereniging.nl/kitespot/elburg/
      NKV policy: *Beperkt* — read the restriction on the source page and put it
      in `hazards` / `season` in BOTH `spotInfo` and `spotInfoPL`.

## Done when

- [x] 5 entries appended to `src/main/resources/spots.json`
- [x] `python3 -c "import json; json.load(open('src/main/resources/spots.json'))"` passes
- [x] `./gradlew test` green
- [x] boxes ticked here and in `README.md`
