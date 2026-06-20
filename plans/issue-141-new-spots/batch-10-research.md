# Batch 10 — Research / TBD (needs scoping before creation)

These items from issue #141 are **not yet a concrete spot list**. This batch is
about turning them into named launches first, then feeding the results back as
new build batches (creating with `kite-spot-creator` as usual).

## A. Resolve-then-create (named but ambiguous)

- [ ] **Santa Veronica** — Colombia (`"Colombia"`, new). Caribbean coast near
      Barranquilla — strong Dec–Mar trade wind (E/NE), wave/flat mix. Likely
      ready to create directly; verify Windguru station. *(Could fold into a
      build batch once confirmed.)*
- [ ] **Boracay** — Philippines (`"Philippines"`, new). Research the actual kite
      launches: **Bulabog Beach** (main kite spot, Amihan NE wind Nov–Apr) is
      the obvious one; check if others (e.g. Lapuz Lapuz) are worth adding.
      Output: a short list of named launches → then create.

## B. Discover spot lists (expand into many spots)

- [ ] **More Netherlands spots** — from `https://kitesurfvereniging.nl/spotkaart/`.
      Extract named, kiteable launches not already covered in batch 5
      (e.g. Wijk aan Zee, Zandvoort, Hoek van Holland, Workum, Wijdenes,
      Schellinkhout, Strand Horst on Veluwemeer, etc.). Produce a deduped list,
      then split into build batches of ~5.
- [ ] **Inland spots Austria / Italy / Switzerland** — from
      `https://www.kitewetter.at/?page_id=1206`. Many Alpine lakes
      (e.g. Neusiedlersee already covered; Walchensee, Silvaplana already
      covered; Reschensee in batch 4). Extract the missing lakes, dedupe against
      existing `spots.json`, then split into build batches.
- [ ] **More USA spots** — research beyond Hatteras (e.g. Outer Banks other
      launches, San Francisco/Sherman Island, Corpus Christi/La Ventana-adjacent,
      Cape Cod, Florida). Produce a candidate list, prioritize, then build.

## Workflow for this batch

1. For each item, do **research only** — produce a checklist of concrete,
   named launches with country + a one-line reason.
2. Dedupe every candidate against current `src/main/resources/spots.json`
   (use the `check-spots` skill or grep by name/coords).
3. Append the resulting named lists as new `batch-11-*.md`, `batch-12-*.md`
   files in this folder, then execute them like batches 1–9.

## Done when

- [ ] Santa Veronica + Boracay launches created (or moved into a build batch).
- [ ] NL / Alpine / USA candidate lists produced, deduped, and written as new
      batch files.
- [ ] Issue #141 research checkboxes ticked.
