---
name: release-notes-android
description: Write or update the Google Play release notes of the Android app (android/distribution/whatsnew, EN + PL) from the changes since the last Android release. Use before tagging an android-vX.Y.Z release, or when the user asks for Android release notes / "what's new".
argument-hint: "[base ref, e.g. android-v1.0.1 or a commit; default: last release]"
---

# Android Release Notes Skill

Write the "What's new" text that Google Play shows for an Android release, from the commits
since the previous release. The CI release job (`.github/workflows/android.yml`) uploads both
files with the AAB (`whatsNewDirectory: android/distribution/whatsnew`), so they must be
ready before the `android-vX.Y.Z` tag is pushed.

Files (plain text, one paragraph each, Play language codes as suffixes):

- `android/distribution/whatsnew/whatsnew-en-US`
- `android/distribution/whatsnew/whatsnew-pl-PL`

## Instructions

### 1. Find the base of the release

Use the argument as the base when one is given. Otherwise, from the repository root:

```bash
# the last release tag, newest version first (check the remote too - tags may not be fetched)
git fetch --tags --quiet origin 2>/dev/null
git tag -l 'android-v*' --sort=-v:refname | head -1
```

If there is no `android-v*` tag yet (1.0.0 was uploaded by hand), use the last commit that
changed the release notes - that is when the previous release was prepared:

```bash
git log -1 --format=%H -- android/distribution/whatsnew
```

Tell the user which base was used, so a wrong one is easy to spot.

### 2. Collect the changes

```bash
git log --oneline <base>..HEAD -- android ':!android/**/*.md'
git diff --stat <base>..HEAD -- android ':!android/**/*.md'
```

Read the full diff of any commit whose subject is not clear enough to describe to a user
(`git show <sha> -- android`). The backend ships independently, but a backend change can be
visible in the app (a new field the app already shows, a new model in the dropdown); include
it only if the user would notice it in this release.

### 3. Pick what goes in

Write for someone reading the Play listing, not for a developer:

- **In**: new features, visible UI changes, removed features or links, bug fixes a user
  could have hit, noticeably faster or more reliable behaviour
- **Out**: CI, build, Gradle and dependency bumps, refactors, tests, docs, lint fixes,
  internal renames - unless they change something the user sees (then describe the effect)
- Lead with the most noticeable change. Merge related commits into one sentence
- No commit hashes, class names, module names, API paths or model ids
- If nothing user-visible changed, say so and propose a short generic line
  ("Stability improvements and minor fixes." / "Poprawki stabilności i drobne usprawnienia.")
  instead of inventing features

### 4. Write both languages

- **Limit: 500 characters per file** (Play rejects longer text; the upload step fails)
- One short paragraph, 1-3 sentences, no bullet points, no headings, no version number
  (Play shows the version itself)
- Use a plain hyphen ` - ` rather than an em dash, matching the existing notes
- English: plain, concrete, present tense ("Forecast models now show a loading indicator...")
- Polish: a natural rewrite, not a word-for-word translation; same facts as the English one.
  Use the app's own terms (spot, prognoza, model prognozy, alerty wiatrowe, widżety)
- Write the files **without a trailing newline**:

```bash
cd android/distribution/whatsnew
printf '%s' 'English text...' > whatsnew-en-US
printf '%s' 'Polski tekst...' > whatsnew-pl-PL
wc -m whatsnew-en-US whatsnew-pl-PL   # each must be <= 500
```

### 5. Report

Show the user:

- the base used and the commits the notes were written from (one line each)
- both texts and their character counts
- anything left out that they might want in

Do not commit, push or tag. The notes are part of a release the user triggers; leave them in
the working tree for review unless the user asks to commit.
