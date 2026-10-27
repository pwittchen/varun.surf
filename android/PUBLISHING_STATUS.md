# Google Play publishing status

A snapshot of where the first Google Play release of `surf.varun.app` stands. The full plan is in
[PUBLISHING.md](PUBLISHING.md) and the required minimum in [PUBLISHING_MINIMAL.md](PUBLISHING_MINIMAL.md).

**As of 2026-10-27: version 1.0.0 is live on Google Play:
<https://play.google.com/store/apps/details?id=surf.varun.app>**

## Play Console setup

| Area | State |
|---|---|
| Developer account | Personal account `7269544076898428056`. Production isn't locked: the account predates the 12-tester / 14-day closed test rule, so no closed test is needed |
| App content | All policy declarations done. "Need attention" is empty |
| Default store listing (en-US) | Name, short description (78/80), full description, 512 px icon, feature graphic, 6 phone screenshots. Tablet, desktop and XR assets are optional and not added |
| Countries / regions | Selected for production and closed testing |
| Closed testing | Testers selected, no release. Not needed for launch |
| Managed publishing | **On**: an approved release doesn't go live until someone clicks **Publish** |

## The released version

| Field | Value |
|---|---|
| Track | Production, full rollout |
| Bundle | Version code 1, version name 1.0.0, uploaded 2026-10-25 22:20 |
| API levels | minSdk 28, targetSdk 36 |
| Download size | 8.55 MB for new installs |
| Release notes (en-US) | "First release of varun.surf: live wind and forecasts for 800+ kite spots." |
| Release notes (pl-PL) | None (optional) |
| Submitted | 2026-10-26 |
| Released | 2026-10-27 |
| Store listing | <https://play.google.com/store/apps/details?id=surf.varun.app> |

Review warning (not a blocker): *"This App Bundle contains native code, and you've not uploaded
debug symbols."* Crashes and ANRs in native code will be harder to read in Android vitals.

## Releasing an update

Managed publishing is still on, so every future release follows the same path: push an
`android-v<x.y.z>` tag (CI uploads the signed AAB straight to the production track, for
all users), wait for the review, then click **Publish** under
"Changes ready to publish".

## Follow-ups after launch

- [ ] Add native debug symbols to the next release: `ndk { debugSymbolLevel = "FULL" }` in the
      release build type
- [ ] Add pl-PL release notes in future updates
- [x] Deploy `/.well-known/assetlinks.json`. The file (`../src/frontend/assets/assetlinks.json`)
      carries the 3 SHA-256 fingerprints Play Console lists under Grow users → Deep links. After
      the deploy, press **Recheck verification** there for `varun.surf`
- [x] Fix or unpublish the old EEG Analyzer app (`com.pwittchen.eeganalyzer`). It had 2 policy
      issues on the same developer account; it is now unpublished
