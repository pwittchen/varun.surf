# Batch 05 — Noord-Holland — coast south (Zandvoort to Bergen) (6 spots)

Run in a fresh context. Use the **`kite-spot-creator`** agent per spot.
`country` = `"Netherlands"`.

North Sea beaches north of IJmuiden. Same caveat as batch 03: forecasts are near-identical, entries differ by launch and access.

## Spots

- [ ] **Zandvoort**
      source: https://kitesurfvereniging.nl/kitespot/zandvoort/
- [ ] **Bloemendaal aan Zee**
      source: https://kitesurfvereniging.nl/kitespot/bloemendaal-aan-zee/
- [ ] **Wijk aan Zee – Pier (Strand Noordpier Velsen-Noord)**
      source: https://kitesurfvereniging.nl/kitespot/wijk-aan-zee-pier/
      merge in: Wijk aan Zee – de HangOut (Strand Noordpier Velsen-Noord) — https://kitesurfvereniging.nl/kitespot/wijk-aan-zee-hangout/
      (same forecast point; describe both launches in one entry)
- [ ] **Castricum aan Zee**
      source: https://kitesurfvereniging.nl/kitespot/castricum-aan-zee/
- [ ] **Egmond Binnen/aan Zee**
      source: https://kitesurfvereniging.nl/kitespot/egmond-binnen-en-aan-zee/
- [ ] **Bergen aan Zee**
      source: https://kitesurfvereniging.nl/kitespot/bergen-aan-zee/

## Done when

- [ ] 6 entries appended to `src/main/resources/spots.json`
- [ ] `python3 -c "import json; json.load(open('src/main/resources/spots.json'))"` passes
- [ ] `./gradlew test` green
- [ ] boxes ticked here and in `README.md`
