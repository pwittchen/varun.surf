# Batch 08 — Wadden islands (Texel, Vlieland, Terschelling, Ameland, Schiermonnikoog) (8 spots)

Run in a fresh context. Use the **`kite-spot-creator`** agent per spot.
`country` = `"Netherlands"`.

Each island has a North Sea (wave) side and a Waddenzee (flat, shallow) side — these are genuinely different spots, not duplicates. Strong nature-reserve restrictions apply.

## Spots

- [x] **Texel Paal 17.6 – 18.99 en 28.2 – 32**
      source: https://kitesurfvereniging.nl/kitespot/texel/
- [x] **Texel – Dijkmanshuizen (Wadzijde)** **[RESTRICTED]**
      source: https://kitesurfvereniging.nl/kitespot/texel-dijkmanshuizen/
      NKV policy: *Beperkt* — read the restriction on the source page and put it
      in `hazards` / `season` in BOTH `spotInfo` and `spotInfoPL`.
- [x] **Vlieland Noordzee**
      source: https://kitesurfvereniging.nl/kitespot/vlieland-noordzee/
- [x] **Terschelling – Noordzee**
      source: https://kitesurfvereniging.nl/kitespot/terschelling-noordzee/
- [x] **Terschelling – Groene Strand**
      source: https://kitesurfvereniging.nl/kitespot/terschelling-groene-strand/
- [x] **Ameland – Noordzee**
      source: https://kitesurfvereniging.nl/kitespot/ameland-noordzee/
- [x] **Ameland – Waddenzee**
      source: https://kitesurfvereniging.nl/kitespot/ameland-waddenzee/
- [x] **Schiermonnikoog (Noordzee: Paal 3-5)**
      source: https://kitesurfvereniging.nl/kitespot/schiermonnikoog-noordzee/

## Done when

- [x] 8 entries appended to `src/main/resources/spots.json`
- [x] `python3 -c "import json; json.load(open('src/main/resources/spots.json'))"` passes
- [x] `./gradlew test` green
- [x] boxes ticked here and in `README.md`
