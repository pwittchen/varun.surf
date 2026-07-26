# Batch 06 — Noord-Holland — coast north (Camperduin to Den Helder) (6 spots)

Run in a fresh context. Use the **`kite-spot-creator`** agent per spot.
`country` = `"Netherlands"`.

Northern North Sea coast plus the Amstelmeer inland lake.

## Spots

- [ ] **Camperduin**
      source: https://kitesurfvereniging.nl/kitespot/camperduin/
- [ ] **Petten (Pettemer Zeewering)**
      source: https://kitesurfvereniging.nl/kitespot/petten/
- [ ] **Callantsoog**
      source: https://kitesurfvereniging.nl/kitespot/callantsoog/
- [ ] **Julianadorp**
      source: https://kitesurfvereniging.nl/kitespot/julianadorp/
- [ ] **Den Helder – Huisduinen**
      source: https://kitesurfvereniging.nl/kitespot/den-helder-huisduinen/
- [ ] **Amstelmeer Lutjestrand zuid**
      source: https://kitesurfvereniging.nl/kitespot/amstelmeer-lutje-strand-zuid/

## Done when

- [ ] 6 entries appended to `src/main/resources/spots.json`
- [ ] `python3 -c "import json; json.load(open('src/main/resources/spots.json'))"` passes
- [ ] `./gradlew test` green
- [ ] boxes ticked here and in `README.md`
