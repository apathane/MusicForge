# MusicForge Site v0.2 — Design System

> "Lively" means the interface responds to you. Motion is reactive: cursor, scroll, hover, input. Never on a timer.

## Color

- **Canvas:** #09090B (dark) / #F4F3EF (light)
- **Ink:** #F4F3EF (dark) / #151518 (light)
- **Accent:** #B45349 dark / #D97A6E light — **always the same hue, always the brick**
- **Accent is dynamic via opacity only**, shifted per section using `--section-accent`:
  - Hero: `rgba(180, 83, 73, 0.90)`
  - Why: `rgba(180, 83, 73, 0.85)`
  - Features: full-strength `#B45349` (key moment — full strength on feature icons)
  - Numbers: `rgba(180, 83, 73, 0.95)`
  - Screenshots: `rgba(180, 83, 73, 0.80)`
  - Broke: `rgba(180, 83, 73, 0.75)`
  - FAQ: `rgba(180, 83, 73, 0.85)`
  - Footer: `rgba(180, 83, 73, 0.65)`

Elements that use `--section-accent` (content-level, follow the section): `.num`, `.dot`, `.za-flash`.

Elements that use full-strength `--accent` (brand/CTA, always full): `.mark-dot`, `.btn-accent`, `.scroll-progress`, focus rings, interactive hover states.

No hue rotation. No color cycling. The accent is the brick; only its opacity varies.

## Radius

Four tokens. Every element uses exactly one.

| Token             | Value | Use |
|-------------------|-------|-----|
| `--radius-small`  | 10px  | skip link, panic button |
| `--radius-row`    | 14px  | code, eyebrow elements |
| `--radius-surface`| 22px  | cards, buttons, screenshots, phone |
| `--radius-hero`   | 30px  | hero phone mockup |

Exception: circular elements (toggle thumb, icon buttons) use `50%` or `999px`.

## Shadows

Three tokens. Every surface uses at most one drop shadow + one inset highlight.

| Token                          | Value | Use |
|-------------------------------|-------|-----|
| `--shadow-floating`           | `0 4px 14px -4px rgba(0,0,0,0.32)` | hover lift on cards, buttons |
| `--shadow-lifted`             | `0 24px 48px -16px rgba(0,0,0,0.55)` | phone mockup |
| `--shadow-inset-highlight`    | `inset 0 0 0 1px rgba(255,255,255,0.035)` | phone inner highlight |

Most elements sit flat. Shadow is an elevation marker, not decoration.

## Borders

One color: `--line` (#2A2A2F dark / #D8D7D2 light). One weight: 1px.

- Surface on surface: `1px solid var(--line)`
- Card on raised: no border (shadow implies separation)
- Divider between sections: `1px solid var(--line)` at `.band` top

Never use `#333`, `#5A5A5A`, or any other color.

## Section Composition (RHYTHM 3)

Five families. Each family appears at least once. Variation comes from content density, not layout invention.

### Family A: Header + Body Grid
- **Hero** — two-column grid at 900px+, single stack below.

### Family B: Section Head + Stacked Content
- **Why** — numbered grid (3 columns)
- **Things that broke** — article grid (2+ columns)

Both share: `.section-head`, `.intro`, `border-top: 1px solid var(--line)` dividers.

### Family C: Section Head + Gallery List
- **Features** — stacked cards with SVG + h3 + p
- **Numbers** — grid with 1px outer border, `--canvas` cells
- **Screenshots** — horizontal scroll-snap rail

All share: `.section-head`, `--gap-row: 24px`, `--radius-surface: 22px` on items.

### Family D: Section Head + Disclosure Stack
- **FAQ** — `details`/`summary` pairs, `+` / `−` indicator.

### Family E: Grid Columns
- **Footer** — 3-column grid desktop, 1-column mobile.

### Universal rules
- Every section uses `.band` with `padding-block: 80px` / `56px` mobile
- Every section has `border-top: 1px solid var(--line)`
- Every section uses `.wrap` (max-width: 1120px)

## Motion (MOTION 2)

**Reactive only. No timers. No loops. No auto-play.**

| Motion | Trigger | Purpose | Duration |
|--------|---------|---------|----------|
| Section reveal | scroll into view | spatial orientation | 400ms |
| Scroll progress bar | scroll | feedback (how far) | instant RAF |
| Button scale | hover / press | affordance | 140ms / 100ms |
| Magnetic CTA | cursor on `.btn-accent` | micro-delight | 80ms spring |
| Text scramble | hover hero title | personality | 800ms |
| Mask reveal | cursor on hero title | personality | 1500ms JS |
| Feature tilt | cursor on `.feature` | depth | spring |
| Rail scroll | click nav buttons | spatial consistency | smooth |
| FAQ toggle | click summary | state indication | native |
| Theme toggle | click | feedback | 200ms |
| Room light follow | cursor move | atmosphere | lerp (RAF) |
| Room mood shift | idle / visibility | context | 400ms |
| Trail dots | mouse down | playfulness | 1200ms fade |
| Za-mode flash | Ctrl+Shift+Z | easter egg | 600ms |
| Panic flash | click panic | easter egg | 700ms |

### What does NOT animate
- Accent color (static per section)
- Background gradient (CSS-only, hero only)
- Font size (never animated)
- Layout flow (only transform + opacity)

### Easing
- Primary: `cubic-bezier(0.23, 1, 0.32, 1)` — strong ease-out, instant feedback
- Secondary: `cubic-bezier(0.77, 0, 0.175, 1)` — ease-in-out for reversible motion

### Reduced motion
Under `prefers-reduced-motion: reduce`:
- All transforms become `none`
- All transitions become `none`
- Scroll behavior becomes `auto`
- Room, trails, blur overlays are removed

## Typography

- **Font:** Inter (400, 500, 600, 700, 800) — loaded from Google Fonts
- **Body:** 16px / 1.6 line-height
- **Headings use tight leading** (1.05–1.3 depending on size) and **negative tracking** on large display text
- **Numbers:** `font-variant-numeric: tabular-nums` for consistent width
- **No custom fonts loaded inline.** Inter is the only font.

## Implementation notes

- No JavaScript framework. All motion is native CSS transitions or vanilla JS.
- The room light system updates CSS custom properties via `requestAnimationFrame`, never DOM state.
- Scroll listeners are throttled via `requestAnimationFrame`.
- All interactive elements have `:active` (scale 0.96) and `:focus-visible` (outline).
- `prefers-reduced-motion` is checked before applying any non-essential JS animation.
