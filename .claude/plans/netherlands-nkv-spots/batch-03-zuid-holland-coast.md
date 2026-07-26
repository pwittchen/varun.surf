# Batch 03 — Zuid-Holland — North Sea beaches (6 spots)

Run in a fresh context. Use the **`kite-spot-creator`** agent per spot.
`country` = `"Netherlands"`.

Straight North Sea beach between Hoek van Holland and Katwijk. All share a near-identical forecast; the value is the launch detail and local rules.

## Spots

- [ ] **Den Haag – Zuiderstrand**
      source: https://kitesurfvereniging.nl/kitespot/den-haag-zuiderstrand/
- [ ] **Kijkduin**
      source: https://kitesurfvereniging.nl/kitespot/kijkduin/
- [ ] **Kijkduin – Zandmotor**
      source: https://kitesurfvereniging.nl/kitespot/zandmotor/
- [ ] **s-Gravenzande (slag Beukel)**
      source: https://kitesurfvereniging.nl/kitespot/s-gravenzande-slag-beukel/
- [ ] **Wassenaarse slag**
      source: https://kitesurfvereniging.nl/kitespot/wassenaarse-slag/
- [ ] **Katwijk**
      source: https://kitesurfvereniging.nl/kitespot/katwijk/

## Done when

- [ ] 6 entries appended to `src/main/resources/spots.json`
- [ ] `python3 -c "import json; json.load(open('src/main/resources/spots.json'))"` passes
- [ ] `./gradlew test` green
- [ ] boxes ticked here and in `README.md`
