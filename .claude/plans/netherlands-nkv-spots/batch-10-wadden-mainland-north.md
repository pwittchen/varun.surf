# Batch 10 — Waddenzee mainland & northern lakes (6 spots)

Run in a fresh context. Use the **`kite-spot-creator`** agent per spot.
`country` = `"Netherlands"`.

Mainland Waddenzee launches plus Groningen inland lakes.

## Spots

- [ ] **Harlingen – Waddenzee**
      source: https://kitesurfvereniging.nl/kitespot/harlingen-waddenzee/
- [ ] **Lauwersoog Waddenzee**
      source: https://kitesurfvereniging.nl/kitespot/lauwersoog-waddenzee/
- [ ] **Lauwersmeer** **[RESTRICTED]**
      source: https://kitesurfvereniging.nl/kitespot/lauwersmeer/
      NKV policy: *Beperkt* — read the restriction on the source page and put it
      in `hazards` / `season` in BOTH `spotInfo` and `spotInfoPL`.
- [ ] **Delfzijl**
      source: https://kitesurfvereniging.nl/kitespot/delfzijl/
- [ ] **Zuidlaardermeer**
      source: https://kitesurfvereniging.nl/kitespot/zuidlaardermeer/
- [ ] **Midwolda Oldambtmeer** **[RESTRICTED]**
      source: https://kitesurfvereniging.nl/kitespot/midwolda-oldambtmeer/
      NKV policy: *Beperkt* — read the restriction on the source page and put it
      in `hazards` / `season` in BOTH `spotInfo` and `spotInfoPL`.

## Done when

- [ ] 6 entries appended to `src/main/resources/spots.json`
- [ ] `python3 -c "import json; json.load(open('src/main/resources/spots.json'))"` passes
- [ ] `./gradlew test` green
- [ ] boxes ticked here and in `README.md`
