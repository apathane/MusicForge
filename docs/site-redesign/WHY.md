# Design & Rationale

## Why replace the site wholesale

The previous v0.2 site was a dark-humor single page with a "room light" mood machine, caption heckling, and Ctrl+Shift+Z easter egg. It read like a personal experiment, not a product. The overlay zip's site is an editorial landing page:

- Product story led by actual app screenshots (home, player, queue, artist), not README paraphrase
- Feature explanations tied to verified implementation (local-first history, bounded range requests, backup/export)
- Interactive benchmark chart grounded in `docs/BENCHMARKS.md` with disclosed conditions
- Two distinct surface modes (Frosted Crayon vs Liquid Glass) explained with an illustrated stage
- Honest caveats instead of fake telemetry or invented stats

## Why keep Frosted Crayon separate

The Honor X7b test target (Snapdragon 680, Adreno 610) cannot afford a live backdrop capture per frame. Frosted Crayon is a single pre-blurred artwork bitmap draw — cheap, works on API 26+. Liquid Glass requires RenderEffect (API 31+) and live layer capture, restricted to two small surfaces (nav pill + mini player). Merging them would regress performance on the project's actual test device.

## Why these liquid-glass values

Adopted from `app-patch/liquid-glass.patch` in the overlay zip:

| Parameter | Before | After | Reason |
|---|---|---|---|
| blur | 7 dp | 11 dp*scale | More glass-like, hides half-res sampling loss |
| lensHeight | 0.60*48 | 0.76*48 | Stronger refractive edge |
| lensAmount | 0.69*48 | 0.92*48 | More pronounced bend at edges |
| tint alpha (dark) | 0.50 | 0.31 | More transparent, reveals live backdrop |
| tint alpha (light) | 0.58 | 0.40 | Same, light theme |
| rim alpha (dark) | 0.45 | 0.72 | Brighter specular edge |
| rim alpha (light) | 0.70 | 0.82 | Same, light theme |
| highlight | 0.8dp @ 45° | 1.15dp @ 38° | Thinner, sharper edge reflection |
| shadow | 18dp | 22dp | Softer, more dimensional lift |
| LiquidScale | 0.5 | 0.65 | Slightly larger live-capture area |

Text contrast preserved: tint stays light enough (0.31-0.40 alpha) that labels remain legible over busy artwork.

## Why version naming mattered

The site hero eyebrow said "Android · 0.15 Rondo" — a leftover from phase 13. The actual build is `0.14.0-sonata` (phase 14, codename Sonata). Fixed to `SONATA / 0.14` on hero line 28 and closing topline line 369, matching `build.gradle.kts` and `CHANGELOG.md`.

## Why no commit/push yet

User instruction: "im cleaning up the repo, ill tell you pull it all when im done." All changes remain uncommitted in the worktree for review.
