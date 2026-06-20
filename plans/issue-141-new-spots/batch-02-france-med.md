# Batch 2 — France, Mediterranean (5 spots)

Run in a fresh context. Use the **`kite-spot-creator`** agent per spot.
`country` = `"France"`. These are strong Tramontane/Mistral spots — mirror the
existing `Le Barcarès` entry's tone (offshore-wind warnings where relevant).

## Spots

- [ ] **Hyères – Almanarre** — La Capte / Almanarre beach, Giens peninsula.
      Flat-to-choppy sea, both sides of the tombolo. Best wind E (Levant) &
      W (Mistral). Major French spot — webcam likely exists.
- [ ] **Gruissan** — Occitanie, near Narbonne. Beach + nearby flat-water étangs.
      Best wind NW (Tramontane), strong. Verify Windguru station.
- [ ] **Port Camargue / Espiguette** — L'Espiguette beach, Le Grau-du-Roi.
      Large sandy beach, sea. Best wind W/SE. Big, popular spot.
- [ ] **Beauduc** — Camargue, remote flat-water lagoon/beach. Best wind W/NW
      (Mistral) & SE. Hazards: very remote (4x4 access), shallow. Verify station.
- [ ] **Serre-Ponçon** — Alpine reservoir lake (Hautes-Alpes). Flat/choppy lake,
      thermal + valley winds. Best wind variable (thermal). Cold mountain water.

## Research hints

- Tramontane = NW, Mistral = NW/N, Levant = E. Confirm dominant direction
  per spot — get it right (matters for `bestWind`).
- Windfinder slugs for FR Med beaches usually resolve; verify each.
- Serre-Ponçon is an **inland lake** — different profile (cold water, thermals,
  summer season). Don't copy coastal hazards.

## Done when

- [ ] 5 entries appended, JSON parses, `./gradlew test` green.
- [ ] Checkboxes ticked here and in issue #141.
