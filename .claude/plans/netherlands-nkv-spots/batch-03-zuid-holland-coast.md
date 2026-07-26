# Batch 03 — Zuid-Holland — North Sea beaches (6 spots)

Run in a fresh context. Use the **`kite-spot-creator`** agent per spot.
`country` = `"Netherlands"`.

Straight North Sea beach between Hoek van Holland and Katwijk. All share a near-identical forecast; the value is the launch detail and local rules.

## Spots

- [x] **Den Haag – Zuiderstrand**
      source: https://kitesurfvereniging.nl/kitespot/den-haag-zuiderstrand/
- [x] **Kijkduin**
      source: https://kitesurfvereniging.nl/kitespot/kijkduin/
- [x] **Kijkduin – Zandmotor**
      source: https://kitesurfvereniging.nl/kitespot/zandmotor/
- [x] **s-Gravenzande (slag Beukel)**
      source: https://kitesurfvereniging.nl/kitespot/s-gravenzande-slag-beukel/
- [x] **Wassenaarse slag**
      source: https://kitesurfvereniging.nl/kitespot/wassenaarse-slag/
- [x] **Katwijk**
      source: https://kitesurfvereniging.nl/kitespot/katwijk/

## Done when

- [x] 6 entries appended to `src/main/resources/spots.json`
- [x] `python3 -c "import json; json.load(open('src/main/resources/spots.json'))"` passes
- [x] `./gradlew test` green
- [x] boxes ticked here and in `README.md`
