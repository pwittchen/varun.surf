# Batch 02 — Zeeland — Oosterschelde & delta dams (6 spots)

Run in a fresh context. Use the **`kite-spot-creator`** agent per spot.
`country` = `"Netherlands"`.

Sheltered delta water behind the storm barriers. Mostly flat, strong tidal currents in the channels.

## Spots

- [x] **Grevelingendam – Zuid**
      source: https://kitesurfvereniging.nl/kitespot/grevelingendam-zuid/
      merge in: Grevelingendam – Noord — https://kitesurfvereniging.nl/kitespot/grevelingendam-noord/
      (same forecast point; describe both launches in one entry)
- [x] **Ouwerkerk** **[RESTRICTED]**
      source: https://kitesurfvereniging.nl/kitespot/ouwerkerk/
      NKV policy: *Beperkt* — read the restriction on the source page and put it
      in `hazards` / `season` in BOTH `spotInfo` and `spotInfoPL`.
- [x] **Tholen Oesterdam**
      source: https://kitesurfvereniging.nl/kitespot/tholen-oesterdam/
- [x] **Baarland**
      source: https://kitesurfvereniging.nl/kitespot/baarland/
- [x] **Borssele (de Kaloot)**
      source: https://kitesurfvereniging.nl/kitespot/borssele-de-kaloot/
- [x] **Terneuzen (put van)**
      source: https://kitesurfvereniging.nl/kitespot/terneuzen/

## Done when

- [x] 6 entries appended to `src/main/resources/spots.json`
- [x] `python3 -c "import json; json.load(open('src/main/resources/spots.json'))"` passes
- [x] `./gradlew test` green
- [x] boxes ticked here and in `README.md`
