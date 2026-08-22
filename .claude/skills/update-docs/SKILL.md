---
name: update-docs
description: Update project documentation (README.md, CLAUDE.md, AGENTS.md, docs/BACKEND.md, docs/FRONTEND.md) so it matches the current state of the code after a change
---

# Update Docs Skill

Bring the project documentation back in sync with the code. Documentation drifts
silently in this repo (counts, endpoint lists, file trees, feature checklists),
so this skill both applies updates for a specific change and sweeps for stale
content while it is in there.

## Documentation Inventory

| File | Audience | Owns |
|------|----------|------|
| `README.md` | humans, GitHub visitors | project pitch, tech stack, build/run/test/docker, CI/CD, deployment, cache busting, monitoring, feature list, MCP server, llms.txt endpoints, data sources, agent + skill tables |
| `CLAUDE.md` | Claude Code | condensed architecture, key components, data model, configuration, code organization tree, feature checklist, "Important Notes for AI Assistants" |
| `AGENTS.md` | other AI coding agents | same ground as CLAUDE.md but longer: per-service detail, code snippets, quick reference, debugging tips |
| `docs/BACKEND.md` | backend devs | ASCII system diagrams, request/update flow, data model, external integrations, caching strategy, concurrency, API endpoint summary, code organization, metrics, E2E architecture |
| `docs/FRONTEND.md` | frontend devs | project structure, pages, JS modules, components, state (localStorage/sessionStorage keys), routing, data flow, styling, i18n, features, performance |

`CLAUDE.md` and `AGENTS.md` describe the same system — whatever changes in one
must change in the other, at the depth each file uses. Never let them disagree.

## Instructions

### 1. Determine the Change Set

Unless the user names a topic, derive it from git (run in parallel):

- `git status` — uncommitted work
- `git diff` and `git diff --cached` — the actual change
- `git log --oneline -20` — recent commits
- `git log -5 --name-only -- README.md CLAUDE.md AGENTS.md docs/` — when the docs were last touched

If the user passed `--since <ref>`, use `git diff <ref>...HEAD --stat` instead.

Summarize in one or two sentences what changed in the code. If nothing changed
and no topic was given, fall back to a full staleness sweep (step 5 only) and
say so.

### 2. Map Changes to Docs

| Changed area | Docs to update |
|--------------|----------------|
| new/changed REST endpoint (`controller/`) | CLAUDE.md (flow list + component section), AGENTS.md (controller section), docs/BACKEND.md (API Endpoints Summary + request flow), README.md only if user-facing |
| new/changed service (`service/`) | CLAUDE.md Key Components, AGENTS.md Core Services, docs/BACKEND.md (diagrams, flow) |
| new weather station strategy (`service/live/strategy/`) | strategy count + list in CLAUDE.md, AGENTS.md, docs/BACKEND.md External Integrations, README.md data sources |
| new/changed model record (`model/`) | Data Model sections in CLAUDE.md, AGENTS.md, docs/BACKEND.md |
| caching / scheduling change | Caching Strategy in CLAUDE.md, AGENTS.md, docs/BACKEND.md (intervals, TTLs, cache names) |
| config or feature flag (`application.yml`, env vars) | Configuration in CLAUDE.md, AGENTS.md, README.md if it affects running the app |
| new dependency or version bump (`build.gradle`) | Tech Stack in README.md, CLAUDE.md, AGENTS.md |
| build/deploy change (`Dockerfile`, workflows, `deployment.sh`, `build.ts`) | README.md, docs/BACKEND.md Deployment/Build |
| frontend change (`src/frontend/`) | docs/FRONTEND.md (structure, components, state, features), README.md features if user-visible |
| new user-facing feature | feature lists in README.md **and** CLAUDE.md **and** AGENTS.md |
| `spots.json` change | spot count wherever it appears; country list if a new country was added |
| new `.claude/agents/*.md` | agent trigger table in README.md |
| new `.claude/skills/*/SKILL.md` | skill table in README.md |
| new package or moved file | Code Organization trees in CLAUDE.md, AGENTS.md, docs/BACKEND.md |
| MCP tools (`mcp/`) | MCP server section in README.md |

Anything not on this list: pick the doc that already covers the closest topic.
Do not invent new top-level sections when an existing one fits.

### 3. Read Before Writing

Read the sections you are about to change — never patch from memory. Verify
every claim you write against the source:

- endpoint paths: read the controller's mapping annotations
- intervals and TTLs: read the `@Scheduled` / constant declarations
- counts (spots, strategies, models, countries): compute them, do not guess
  - spots: `python3 -c "import json;print(len(json.load(open('src/main/resources/spots.json'))))"`
  - countries: same file, `len({s['country'] for s in ...})`
  - strategies: `ls src/main/java/com/github/pwittchen/varun/service/live/strategy/`
  - forecast models: count entries in the `ForecastModel` enum
- file trees: `git ls-files` — every path in a tree must exist

### 4. Apply the Updates

Use `Edit` for surgical changes. Rules:

- **Match the file's existing voice.** README.md uses lowercase headings and a
  terse style; CLAUDE.md and AGENTS.md use Title Case and structured lists;
  docs/*.md lean on ASCII diagrams. Do not restyle a file while updating it.
- **Edit, do not rewrite.** Touch only the sections the change affects. A docs
  update should read as a small diff.
- **Keep ASCII diagrams aligned.** If a box or arrow changes in
  docs/BACKEND.md, re-check the column alignment of the whole diagram.
- **Feature checklists**: CLAUDE.md and AGENTS.md use `- [x]`; README.md uses
  plain bullets. Add the feature in the same style at the end of the relevant
  group.
- **Remove what is gone.** Deleting stale lines is as much a part of this job as
  adding new ones.
- **Never edit generated assets.** `src/main/resources/static/*.{html,css,js}`
  are build outputs; frontend sources live in `src/frontend/`.
- **No AI attribution anywhere** (project rule).
- **Do not commit.** Leave the working tree dirty and let the user run
  `/commit`.

### 5. Consistency Sweep

After applying the change-driven edits, check the docs against reality. Report
findings even when out of scope for the current change (fix the cheap ones,
list the rest):

1. **Counts**: spot count, country count, strategy count, model count, log
   buffer size, health history points — compare each occurrence against the code.
2. **File trees**: every path in a Code Organization tree resolves in
   `git ls-files`; every top-level package appears in the tree.
3. **Endpoints**: every endpoint documented exists in a controller, and every
   controller mapping is documented in docs/BACKEND.md.
4. **Tables in README.md**: one row per file in `.claude/agents/` and
   `.claude/skills/`; no rows for deleted ones.
5. **CLAUDE.md ↔ AGENTS.md parity**: same components, same features, same
   configuration, same notes — no contradictions.
6. **Config**: every flag in `application.yml` that docs mention still exists
   with the documented default.

### 6. Report

Output:

```markdown
## Docs Updated

**Change**: [one-line summary of what changed in the code]

| File | Section | Update |
|------|---------|--------|
| CLAUDE.md | Key Components | added FooService |
| docs/BACKEND.md | API Endpoints Summary | added GET /api/v1/foo |

## Staleness Found

- `CLAUDE.md:NNN` — spot count says ~102, spots.json has 230 → fixed
- `AGENTS.md:NNN` — references deleted `OldService.java` → left for review

## Not Updated

- README.md — change is internal, no user-facing impact
```

Keep it short. If nothing needed changing, say so in one line.

## Arguments

- `/update-docs` — derive the change set from git and update everything affected
- `/update-docs <topic>` — document a specific change, e.g.
  `/update-docs new Holfuy strategy for Jastarnia`
- `/update-docs --since <ref>` — document everything since a commit/tag/branch
- `/update-docs --check` — report only, make no edits
- `/update-docs <file>` — restrict updates to one doc, e.g.
  `/update-docs docs/FRONTEND.md`

## Examples

```bash
/update-docs
/update-docs added /api/v1/tides endpoint
/update-docs --since v1.4.0
/update-docs --check
/update-docs README.md
```

## Notes

- Documentation is a description of the code, not a wish list — if a doc claims
  something the code does not do, the doc is wrong.
- Prefer approximate counts (`~230 spots`) over exact ones in prose that ages
  quickly; use exact numbers only where precision matters.
- When a change is large enough that a doc section needs restructuring rather
  than patching, say so in the report before doing it.
- If the change is ambiguous about user-facing impact, ask before adding it to
  the README feature list.
- Related skills: `/commit` to commit the result, `/explain` to understand a
  flow before documenting it, `/arch-check` when the structure itself changed.
