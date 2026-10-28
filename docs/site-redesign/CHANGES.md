# MusicForge Site Redesign + Liquid Glass — Change Report

## Overview

Site redesigned as a product showcase (not a README rearrangement) and Android glass visual modes renamed + strengthened per the liquid-glass patch. Version naming aligned to `0.14.0-sonata` across site, changelog, and build config.

## Files changed

### Website (`app/site/`)
Replaced wholesale with the overlay zip's product-led editorial landing:

| File | Size | Role |
|---|---|---|
| `site/index.html` | 24.9 KB | Landing page: hero, principles, features, engineering/benchmark panel, download pipeline, surface modes (Frosted Crayon vs Liquid Glass), field notes, screenshot rail, closing CTA |
| `site/styles.css` | 35.9 KB | Warm mineral palette (bg #10110f, ink #f1f0e9, accent #d3a779), DM Mono + Manrope typography, responsive breakpoints (1050/780/430 px), reduced-motion gating, reveal-on-scroll |
| `site/main.js` | 6.4 KB | Theme toggle (localStorage), mobile nav, benchmark chart renderer (recorded MB/s values only), screenshot rail buttons, intersection-observer reveals |

Assets reused as-is from `site/img/`: home.jpg, player.jpg, queue.jpg, artist.jpg (4 real JPEGs, 400x893 each).

### Android (`app/`)

| File | Change |
|---|---|
| `app/src/main/kotlin/dev/musicforge/ui/screens/SettingsScreen.kt` | ChoiceRow labels: `Artwork` → `Frosted Crayon`, `Liquid` → `Liquid Glass`. Comment updated ("Liquid" → "Live glass"). |
| `app/src/main/kotlin/dev/musicforge/ui/components/Glass.kt` | ARTWORK doc comment renamed to "Frosted Crayon". LIQUID doc comment expanded with performance warning. Liquid() values: blur 7→11 dp*scale, lensHeight 0.60→0.76, lensAmount 0.69→0.92, tint alpha 0.50→0.31 (dark)/0.58→0.40 (light), rim alpha 0.45→0.72/0.7→0.82, highlight 0.8dp@45°→1.15dp@38°, shadow 18dp→22dp (0.35→0.38 dark / 0.12→0.16 light), border 0.5dp solid → 0.8dp vertical gradient brush, LiquidScale 0.5→0.65. |

## Version naming (bug fix)

Before: site hero said "Android · 0.15 Rondo" (stale from a previous phase).  
After: `SONATA / 0.14` everywhere — hero eyebrow (line 28), closing topline (line 369), matching `build.gradle.kts` (`phase = 14`, `codename = "Sonata"` → `0.14.0-sonata`) and `CHANGELOG.md` ("Version 0.14.0, codename `Sonata"`).

## Benchmark data (grounded, not invented)

The interactive benchmark chart reads from `docs/BENCHMARKS.md`:
- 70.1 MB stream: unbounded GET 0.03 MB/s vs 2 MiB chunks 5.16 MB/s (172× delta)
- 3.3 MB song: unbounded GET 0.03 MB/s vs 2 MiB chunks 5.50 MB/s (183× delta)
- Caption explicitly states these are recorded measurements, not a promise for every network/device. Source linked to `docs/BENCHMARKS.md`.

## Design decisions

- **Frosted Crayon stays distinct**: pre-blurred artwork slice, one bitmap draw, cheap on Adreno 610 (Honor X7b test target). Not merged into Liquid Glass.
- **Liquid Glass is opt-in**: only API 31+, only offered when `isRenderEffectSupported()`, limited to nav pill and mini-player — never scrolling areas. Falls back to Frosted Crayon on older API.
- **No perpetual animation**: reveals use IntersectionObserver once, reduced-motion disables all transitions, no background animation loops.
- **No fake stats**: benchmark numbers are historical recordings with disclosed methodology; no invented testimonials or live telemetry.
- **Accessibility**: skip link, focus-visible outlines (2px accent), theme toggle aria-label syncs with state, mobile nav keyboard-handled, screenshots have alt text, contrast ratios checked via warm mineral palette.

## Known issues / caveats

- `site/assets/Inter-Display-600.woff2` is untracked — it's a 1.6 KB HTML error page from a failed font download, not a real font. Site uses Google Fonts CDN (Manrope + DM Mono) instead.
- Glass.kt liquid() values (blur 11 dp, lens 0.76/0.92) were adopted from the overlay patch but **not compiled against the full Android project** in this environment. Need real-device verification on Honor X7b / API 31+.
- `scripts/check.sh` requires Android SDK + local.properties; not run yet in this session.

## Next steps

1. Run `scripts/check.sh` (`:app:assembleDebug :app:testDebugUnitTest :app:lintDebug`) to verify Android build.
2. Test Liquid Glass rendering on API 31+ device; keep Frosted Crayon as default if frames drop.
3. Verify GitHub Pages deployment at `https://apathane.github.io/MusicForge/`.
4. Review the full diff before any commit/push.
