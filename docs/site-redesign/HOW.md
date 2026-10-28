# Implementation Notes

## What was done, step by step

1. **Reconnaissance (historical)**: the initial implementation started from `phase-13-rondo` at `1a00a81`. Since then, PR #2 merged, the site redesign landed on `main`, and GitHub Pages deployment passed in run 40757309328. Preview 4 is now tracked separately in PR #3.
2. **Extracted overlay zip**: `musicforge-redesign-overlay(4).zip` → site/index.html, site/styles.css, site/main.js, app-patch/liquid-glass.patch.
3. **Copied site files**: overlay's three site files replaced the existing v0.2 versions in `app/site/`.
4. **Applied liquid-glass patch** to `SettingsScreen.kt`: renamed `Artwork` → `Frosted Crayon`, `Liquid` → `Liquid Glass`, comment updated.
5. **Applied liquid-glass patch** to `Glass.kt`: ARTWORK doc → "Frosted Crayon", LIQUID doc expanded, all liquid() parameter values updated per patch (blur/lens/tint/rim/highlight/shadow/border/LiquidScale).
6. **Verified version naming**: `build.gradle.kts` phase=14/codename="Sonata" → `0.14.0-sonata`; site uses `SONATA / 0.14` consistently.
7. **JS syntax check**: `node --check site/main.js` passed.

## Verification performed

| Check | Result |
|---|---|
| `node --check site/main.js` | PASS (no syntax errors) |
| Site files parse as HTML/CSS | Visual inspection confirmed well-formed |
| Glass.kt patch applied cleanly | All 6 patch hunks applied, no conflicts |
| SettingsScreen.kt patch applied cleanly | All labels renamed correctly |
| Version naming consistent | build.gradle.kts + site + CHANGELOG all say 0.14/Sonata |
| Screenshot assets exist | 4 real JPEGs in site/img/ |

## Verification NOT yet performed (blockers)

| Check | Blocker |
|---|---|
| `scripts/check.sh` (Gradle build + tests + lint) | PASS via GitHub Actions CI — run 40757309443 (`:app:assembleDebug`, `:app:assembleRelease`, app/innertube unit tests and lint on ubuntu-24.04, JDK 21, API 37). Local execution was not verified during this hosted review; check the current SDK path before assuming it is unavailable. |
| Liquid Glass on real device | BLOCKED — no API 31+ device available in this environment. |
| GitHub Pages deployment | PASS — run 40757309328 completed successfully after Pages was configured for Actions. |
| HTML validator | Not run; visual inspection only. |

## Rollback

The site redesign and glass-mode naming are merged to `main`. Revert with a reviewed revert commit or PR; do not use `git checkout` to discard current work or reset/force-push shared `main`.

## Conventions followed

- Ponytail ultra: smallest change that fully solves the task; no drive-by refactors
- Anti-slop UI: no gradient blobs, no fake stats, no AI icons, no emoji, no monospace headings, no `transition: all`, no `scale(0)` entrances
- Animation: transform/opacity/color only, gated by `prefers-reduced-motion`, under 300ms where animated
- Version naming: `0.14.0-sonata` consistent across build config, site, changelog
- Preview 4 updater/release work is tracked separately in PR #3 and must pass CI before merging.
