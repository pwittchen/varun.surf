# Batch 07 — Noord-Holland — Markermeer & IJsselmeer shore (6 spots)

Run in a fresh context. Use the **`kite-spot-creator`** agent per spot.
`country` = `"Netherlands"`.

Freshwater, flat-to-choppy, shallow. Distinct forecast from the coastal spots.

## Spots

- [ ] **Muiderberg**
      source: https://kitesurfvereniging.nl/kitespot/muiderberg/
- [ ] **Edam Noord – Galgenveld**
      source: https://kitesurfvereniging.nl/kitespot/edam-noord-galgenveld/
- [ ] **Warder** **[RESTRICTED]**
      source: https://kitesurfvereniging.nl/kitespot/warder/
      NKV policy: *Beperkt* — read the restriction on the source page and put it
      in `hazards` / `season` in BOTH `spotInfo` and `spotInfoPL`.
- [ ] **Schellinkhout**
      source: https://kitesurfvereniging.nl/kitespot/schellinkhout/
- [ ] **Enkhuizen** **[RESTRICTED]**
      source: https://kitesurfvereniging.nl/kitespot/enkhuizen/
      NKV policy: *Beperkt* — read the restriction on the source page and put it
      in `hazards` / `season` in BOTH `spotInfo` and `spotInfoPL`.
- [ ] **Medemblik** **[RESTRICTED]**
      source: https://kitesurfvereniging.nl/kitespot/medemblik/
      NKV policy: *Beperkt* — read the restriction on the source page and put it
      in `hazards` / `season` in BOTH `spotInfo` and `spotInfoPL`.

## Done when

- [ ] 6 entries appended to `src/main/resources/spots.json`
- [ ] `python3 -c "import json; json.load(open('src/main/resources/spots.json'))"` passes
- [ ] `./gradlew test` green
- [ ] boxes ticked here and in `README.md`
