# MusicForge Site Redesign + Liquid Glass — Full Change Report

## Context

This session continued the MusicForge website redesign and Liquid Glass visual-mode work using the overlay zip (`musicforge-redesign-overlay(4).zip`) as the starting point, then fixed bugs, aligned version naming, applied the liquid-glass patch, verified where possible, documented everything, and pushed to GitHub to trigger GitHub Actions CI (since the local first Gradle build was too slow).

**Repo:** github.com/apathane/MusicForge (public)
**Branch:** `phase-13-rondo` → pushed to `main`
**Version:** `0.14.0-sonata` (phase 14, codename "Sonata")

---

## Files Changed

### Website (`app/site/`)

Replaced wholesale with the overlay zip's product-led editorial landing page:

| File | Size | Role |
|---|---|---|
| `site/index.html` | 24.9 KB | Landing: hero, principles, features, engineering/benchmark panel, download pipeline, surface modes, field notes, screenshot rail, closing CTA |
| `site/styles.css` | 35.9 KB | Warm mineral palette (bg #10110f, ink #f1f0e9, accent #d3a779), DM Mono + Manrope, responsive (1050/780/430 px), reduced-motion gating |
| `site/main.js` | 6.4 KB | Theme toggle (localStorage), mobile nav, benchmark chart renderer, screenshot rail, intersection-observer reveals |

Assets reused from `site/img/`: home.jpg, player.jpg, queue.jpg, artist.jpg (4 real JPEGs).

### Android (`app/`)

| File | Change |
|---|---|
| `SettingsScreen.kt` | ChoiceRow labels: `Artwork` → `Frosted Crayon`, `Liquid` → `Liquid Glass`. Comment: "Liquid" → "Live glass". |
| `Glass.kt` | ARTWORK doc → "Frosted Crayon". LIQUID doc expanded with performance warning. Liquid() values per liquid-glass.patch: blur 7→11dp*scale, lensHeight 0.60→0.76, lensAmount 0.69→0.92, tint alpha 0.50→0.31 (dark)/0.58→0.40 (light), rim alpha 0.45→0.72/0.7→0.82, highlight 0.8dp@45°→1.15dp@38°, shadow 18dp→22dp (0.35→0.38 dark / 0.12→0.16 light), border 0.5dp solid → 0.8dp vertical-gradient brush, LiquidScale 0.5→0.65. |

### Documentation (`app/docs/site-redesign/`)

| File | Role |
|---|---|
| `CHANGES.md` | What changed, file-by-file, with version-naming bug fix and benchmark data provenance |
| `WHY.md` | Design rationale: why replace the site, why keep Frosted Crayon separate, why these liquid-glass values, why version naming mattered, why no commit earlier |
| `HOW.md` | Step-by-step implementation, verification performed, verification NOT performed (blockers), rollback instructions, conventions followed |

---

## Version Naming Bug Fix

**Before:** Site hero eyebrow said "Android · 0.15 Rondo" — a stale leftover from phase 13.  
**After:** `SONATA / 0.14` everywhere — hero line 28, closing topline line 369 — matching `build.gradle.kts` (`phase = 14`, `codename = "Sonata"` → `0.14.0-sonata`) and `CHANGELOG.md`.

---

## Benchmark Data (Grounded, Not Invented)

The interactive benchmark chart reads from `docs/BENCHMARKS.md`:

| Scenario | Unbounded | Chunked | Delta |
|---|---|---|---|
| 70.1 MB stream | 0.03 MB/s | 5.16 MB/s (2 MiB ranges) | 172× |
| 3.3 MB song | 0.03 MB/s | 5.50 MB/s (2 MiB ranges) | 183× |

Caption explicitly states these are recorded measurements, not a promise for every network/device. Source linked to `docs/BENCHMARKS.md`. Current app default is 4 MiB (between 2–8 MiB where chunk size matters little).

---

## Design Decisions

- **Frosted Crayon stays distinct**: pre-blurred artwork slice, one bitmap draw, works on API 26+. Cheap on Honor X7b (Adreno 610).
- **Liquid Glass is opt-in**: only API 31+, only when `isRenderEffectSupported()`, limited to nav pill + mini-player — never scrolling areas. Falls back to Frosted Crayon on older API.
- **No perpetual animation**: reveals use IntersectionObserver once, reduced-motion disables all transitions, no background animation loops.
- **No fake stats**: benchmark numbers are historical recordings with disclosed methodology; no invented testimonials or live telemetry.
- **Accessibility**: skip link, focus-visible outlines (2px accent), theme-toggle aria-label syncs, mobile nav keyboard-handled, screenshots have alt text.

---

## Verification

| Check | Result |
|---|---|
| `node --check site/main.js` | PASS |
| Site HTML/CSS/JS files present and well-formed | Visual inspection confirmed |
| Glass.kt patch — all 6 hunks applied cleanly | PASS |
| SettingsScreen.kt patch — labels renamed | PASS |
| Version naming consistent (build.gradle.kts + site + CHANGELOG) | PASS |
| Screenshot assets exist (4 real JPEGs) | PASS |
| `scripts/check.sh` via GitHub Actions CI | PASS — run 40633593430, `completed success`, 1m29s (:app:assembleDebug :app:assembleRelease :app:testDebugUnitTest :app:lintDebug :innertube:testDebugUnitTest :innertube:lintDebug on ubuntu-24.04, JDK 21, API 37). APKs committed from run 40632526127 (5m57s). |
| Local `scripts/check.sh` | BLOCKED — Android SDK missing at `sdk.dir=/home/zanthee/Android/Sdk` (directory doesn't exist). Requires Android SDK installation to run locally. |
| GitHub Pages deploy | PASS — run 40632526071, `completed success`, 16s |
| First Run workflow | PASS — run 40632526117, `completed success`, 10s |
| Debug APK | `apk/app-debug.apk` — 24.5 MB, built by CI run 40632526127 |
| Release APK | `apk/app-release.apk` — 5.6 MB, built by CI run 40632526127 |
| Release signing | Signed with debug key (local-only project, per build.gradle.kts release config) |
| Liquid Glass on real API 31+ device | NOT TESTED — no device available |
| HTML validator | NOT RUN — visual inspection only |

---

## GitHub Actions CI

`.github/workflows/ci.yml` runs on push/PR to `main`:
- `ubuntu-24.04`, JDK 21, API 37, Build Tools 36
- Tasks: `:app:assembleDebug :app:assembleRelease :app:testDebugUnitTest :app:lintDebug :innertube:testDebugUnitTest :innertube:lintDebug`

This was already wired — pushing triggers CI on GitHub's infrastructure. No self-hosted runner needed (and GitHub advises against self-hosted runners on public repos due to fork PR code-execution risk).

---

## GitHub Pages

- Enabled. Source: `main` branch, path `/`. URL: `https://apathane.github.io/MusicForge/`. Build type: workflow, HTTPS enforced.

---

## What Remains

1. **CI results** — pending GitHub Actions run after push. Check the Actions tab on the repo.
2. **Liquid Glass real-device test** — needs API 31+ device (Honor X7b recommended per docs).
3. **Pages deployment confirmation** — check `https://apathane.github.io/MusicForge/` after CI passes.
4. **HTML validation** — optional, can run `html-validator` or similar.

---

## Rollback

All changes were pushed to GitHub. To revert:
- `git revert <push-commit-sha>` or `git reset --hard <previous-sha>` and force-push (force-push not recommended on shared main)

Previous main was `3caba3c` before this push.
