# Batch 04 — Zuid-Holland — delta & inland water (6 spots)

Run in a fresh context. Use the **`kite-spot-creator`** agent per spot.
`country` = `"Netherlands"`.

Haringvliet / Voorne mix of sea beaches and freshwater lakes.

## Spots

- [ ] **Hoek van Holland, Hoekse Watersport Vereniging**
      source: https://kitesurfvereniging.nl/kitespot/hoek-van-holland/
      merge in: Windsurfing Hoek van Holland — https://kitesurfvereniging.nl/kitespot/hoek-van-holland-windsurfing/
      (same forecast point; describe both launches in one entry)
- [ ] **Oostvoorne Noordzee**
      source: https://kitesurfvereniging.nl/kitespot/oostvoorne-noordzee/
- [ ] **Oostvoornse meer**
      source: https://kitesurfvereniging.nl/kitespot/oostvoornse-meer/
- [ ] **Rockanje Sportstrand**
      source: https://kitesurfvereniging.nl/kitespot/rockanje-sportstrand/
      merge in: Rockanje Badstrand — https://kitesurfvereniging.nl/kitespot/rockanje/
      (same forecast point; describe both launches in one entry)
- [ ] **Ouddorp**
      source: https://kitesurfvereniging.nl/kitespot/ouddorp/
- [ ] **Hellevoetsluis – Haringvliet**
      source: https://kitesurfvereniging.nl/kitespot/hellevoetsluis-haringvliet/

## Done when

- [ ] 6 entries appended to `src/main/resources/spots.json`
- [ ] `python3 -c "import json; json.load(open('src/main/resources/spots.json'))"` passes
- [ ] `./gradlew test` green
- [ ] boxes ticked here and in `README.md`
