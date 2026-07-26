# Batch 01 — Zeeland — Westerschelde & Walcheren (7 spots)

Run in a fresh context. Use the **`kite-spot-creator`** agent per spot.
`country` = `"Netherlands"`.

North Sea beaches and delta flats on the Zeeland coast. Tide matters everywhere here; several are shallow flat-water spots at low tide.

## Spots

- [x] **Domburg**
      source: https://kitesurfvereniging.nl/kitespot/domburg/
- [x] **Vrouwenpolder**
      source: https://kitesurfvereniging.nl/kitespot/vrouwenpolder/
- [x] **Neeltje Jans**
      source: https://kitesurfvereniging.nl/kitespot/neeltje-jans/
- [x] **Kamperland – Roompot XBeach**
      source: https://kitesurfvereniging.nl/kitespot/kamperland/
- [x] **Cadzand-bad – Vlamingpolder**
      source: https://kitesurfvereniging.nl/kitespot/cadzand-bad-vlamingpolder/
      merge in: Cadzand-bad – West — https://kitesurfvereniging.nl/kitespot/cadzand-bad-west/
      (same forecast point; describe both launches in one entry)
- [x] **Breskens Oost**
      source: https://kitesurfvereniging.nl/kitespot/breskens-oost/
- [x] **Paulinapolder**
      source: https://kitesurfvereniging.nl/kitespot/paulinapolder/

## Done when

- [x] 7 entries appended to `src/main/resources/spots.json`
- [x] `python3 -c "import json; json.load(open('src/main/resources/spots.json'))"` passes
- [x] `./gradlew test` green
- [x] boxes ticked here and in `README.md`
