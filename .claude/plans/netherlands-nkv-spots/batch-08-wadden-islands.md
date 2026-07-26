# Batch 08 — Wadden islands (Texel, Vlieland, Terschelling, Ameland, Schiermonnikoog) (8 spots)

Run in a fresh context. Use the **`kite-spot-creator`** agent per spot.
`country` = `"Netherlands"`.

Each island has a North Sea (wave) side and a Waddenzee (flat, shallow) side — these are genuinely different spots, not duplicates. Strong nature-reserve restrictions apply.

## Spots

- [ ] **Texel Paal 17.6 – 18.99 en 28.2 – 32**
      source: https://kitesurfvereniging.nl/kitespot/texel/
- [ ] **Texel – Dijkmanshuizen (Wadzijde)** **[RESTRICTED]**
      source: https://kitesurfvereniging.nl/kitespot/texel-dijkmanshuizen/
      NKV policy: *Beperkt* — read the restriction on the source page and put it
      in `hazards` / `season` in BOTH `spotInfo` and `spotInfoPL`.
- [ ] **Vlieland Noordzee**
      source: https://kitesurfvereniging.nl/kitespot/vlieland-noordzee/
- [ ] **Terschelling – Noordzee**
      source: https://kitesurfvereniging.nl/kitespot/terschelling-noordzee/
- [ ] **Terschelling – Groene Strand**
      source: https://kitesurfvereniging.nl/kitespot/terschelling-groene-strand/
- [ ] **Ameland – Noordzee**
      source: https://kitesurfvereniging.nl/kitespot/ameland-noordzee/
- [ ] **Ameland – Waddenzee**
      source: https://kitesurfvereniging.nl/kitespot/ameland-waddenzee/
- [ ] **Schiermonnikoog (Noordzee: Paal 3-5)**
      source: https://kitesurfvereniging.nl/kitespot/schiermonnikoog-noordzee/

## Done when

- [ ] 8 entries appended to `src/main/resources/spots.json`
- [ ] `python3 -c "import json; json.load(open('src/main/resources/spots.json'))"` passes
- [ ] `./gradlew test` green
- [ ] boxes ticked here and in `README.md`
