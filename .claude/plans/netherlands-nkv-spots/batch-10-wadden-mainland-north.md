# Batch 10 — Waddenzee mainland & northern lakes (6 spots)

Run in a fresh context. Use the **`kite-spot-creator`** agent per spot.
`country` = `"Netherlands"`.

Mainland Waddenzee launches plus Groningen inland lakes.

## Spots

- [x] **Harlingen – Waddenzee**
      source: https://kitesurfvereniging.nl/kitespot/harlingen-waddenzee/
- [x] **Lauwersoog Waddenzee**
      source: https://kitesurfvereniging.nl/kitespot/lauwersoog-waddenzee/
- [x] **Lauwersmeer** **[RESTRICTED]**
      source: https://kitesurfvereniging.nl/kitespot/lauwersmeer/
      NKV policy: *Beperkt* — read the restriction on the source page and put it
      in `hazards` / `season` in BOTH `spotInfo` and `spotInfoPL`.
- [x] **Delfzijl**
      source: https://kitesurfvereniging.nl/kitespot/delfzijl/
- [x] **Zuidlaardermeer**
      source: https://kitesurfvereniging.nl/kitespot/zuidlaardermeer/
- [x] **Midwolda Oldambtmeer** **[RESTRICTED]**
      source: https://kitesurfvereniging.nl/kitespot/midwolda-oldambtmeer/
      NKV policy: *Beperkt* — read the restriction on the source page and put it
      in `hazards` / `season` in BOTH `spotInfo` and `spotInfoPL`.

## Done when

- [x] 6 entries appended to `src/main/resources/spots.json`
- [x] `python3 -c "import json; json.load(open('src/main/resources/spots.json'))"` passes
- [x] `./gradlew test` green
- [x] boxes ticked here and in `README.md`
