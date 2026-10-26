# Google Play publishing status

A snapshot of where the first Google Play release of `surf.varun.app` stands. The full plan is in
[PUBLISHING.md](PUBLISHING.md) and the required minimum in [PUBLISHING_MINIMAL.md](PUBLISHING_MINIMAL.md).

**As of 2026-10-26: version 1.0.0 is in Google review on the production track.**

## Play Console setup

| Area | State |
|---|---|
| Developer account | Personal account `7269544076898428056`. Production isn't locked: the account predates the 12-tester / 14-day closed test rule, so no closed test is needed |
| App content | All policy declarations done. "Need attention" is empty |
| Default store listing (en-US) | Name, short description (78/80), full description, 512 px icon, feature graphic, 6 phone screenshots. Tablet, desktop and XR assets are optional and not added |
| Countries / regions | Selected for production and closed testing |
| Closed testing | Testers selected, no release. Not needed for launch |
| Managed publishing | **On**: an approved release doesn't go live until someone clicks **Publish** |

## The submitted release

| Field | Value |
|---|---|
| Track | Production, full rollout |
| Bundle | Version code 1, version name 1.0.0, uploaded 2026-10-25 22:20 |
| API levels | minSdk 28, targetSdk 36 |
| Download size | 8.55 MB for new installs |
| Release notes (en-US) | "First release of varun.surf: live wind and forecasts for 800+ kite spots." |
| Release notes (pl-PL) | None (optional) |
| Submitted | 2026-10-26. Google says reviews usually take up to 7 days, sometimes longer |

Review warning (not a blocker): *"This App Bundle contains native code, and you've not uploaded
debug symbols."* Crashes and ANRs in native code will be harder to read in Android vitals.

## Next steps

1. **Wait for the review.** Track it in Play Console → varun.surf → Publishing overview
   (or Submission activity). Google emails the account owner when it's done. To pull the
   submission back while it's in review, use "Remove changes".
2. **Publish.** Once approved, the release moves to "Changes ready to publish". Click **Publish**.
   The listing usually appears on Google Play within a few hours.
3. **If it's rejected**, fix whatever the email names (usually a declaration or the listing) and
   resubmit from Publishing overview.

## Follow-ups after launch

- [ ] Add native debug symbols to the next release: `ndk { debugSymbolLevel = "FULL" }` in the
      release build type
- [ ] Add pl-PL release notes in future updates
- [ ] Deploy `/.well-known/assetlinks.json`. The file (`../src/frontend/assets/assetlinks.json`)
      carries the 3 SHA-256 fingerprints Play Console lists under Grow users → Deep links. After
      the deploy, press **Recheck verification** there for `varun.surf`
- [ ] Fix or unpublish the old EEG Analyzer app (`com.pwittchen.eeganalyzer`). It has 2 policy
      issues on the same developer account
