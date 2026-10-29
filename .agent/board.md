# Board

Claim an item by adding `claimed: <agent> · <branch>` under it; move it to Done with the commit
or PR when it lands. Agents work in separate worktrees, never in the same checkout.

## Now: P4 · 0.14.0 (published preview, 2026-10-28)

Plan: changes land through reviewable PRs, CI must pass, and release signing must match preview 3.

- [x] **Sign-in loop**: explicit account confirmation; merged to main.
- [x] **Player output picker**: guard missing MEDIA_OUTPUT panel on MagicOS, improve volume semantics/debounce, move device lookup off the main thread; merged to main.
- [x] **Artwork loading**: preserve signed thumbnail URLs and fall back when artwork is missing; merged to main.
- [x] **Website redesign and visual modes**: product-led landing page, benchmark caveats, Frosted Crayon and Liquid Glass labels; deployed from main.
- [x] **Updater implementation**: GitHub Releases discovery, version comparison, preview preference, progress, checksum validation, installer handoff.
- [x] **P4 release gate**: CI passed; permanent signing certificate verified; APK + SHA-256 manifest published as `p4-0.14.0`.
- [ ] **Real-device updater test**: verify update discovery, checksum rejection, permission flow, and install over preview 3 on hardware.
- [ ] **Identity (owner)**: the app still needs the owner's decision on its long-term name, colour, and signature element before a larger identity redesign.

## Now: Toccata · phase 15 (see decisions/0001-phase-15-toccata.md)

Wave 1
- [x] **T1 updater reuse**: don't re-download an APK that is already in cache and verified · `toccata/updater-reuse`
  claimed: kilo · toccata/updater-reuse
- [ ] **T2 motion**: route transitions with predictive back, sheets/menus/dialogs enter and exit, press and list motion · `toccata/motion`
- [ ] **T3 radio & streaming basics**: endless play, audio quality, skip silence, crossfade groundwork · `toccata/radio`

Wave 2
- [ ] **T4 player canvas**: controls clear of the nav bar, full-bleed cover blends into the ambience canvas, "Hide top bar" option · `toccata/player-canvas`
- [ ] **T5 loading**: a shared loading bar for every screen and menu that loads, faster menus · `toccata/loading`
- [ ] **T6 awareness**: offline / metered / battery saver / low storage / failing stream, shown in one line and adapted to · `toccata/awareness`

Wave 3
- [ ] **T7 Home**: customisable shelves, creative artist portraits, listening tracking · `toccata/home`
- [ ] **T8 settings**: the basic and quality-of-life settings every player has · `toccata/settings`
- [ ] **T9 performance**: GPU-friendly drawing, fewer recompositions, measured · `toccata/perf`

## Night shift (maintenance, branch `night/maintenance`)

Calm, small, reviewable: cleanup, bug fixes, tidy docs. Merged to main after CI.

- [x] Updater: strip paths from asset names, clear old APKs, whole-percent progress
- [x] Redundancy scan (Hermes, read-only): 15 claims, 4 held (committed APKs, an unused
      backup-rules file); the rest were used code or intentional (Metrolist import keys, baseline profiles)
- [x] Pre-release from-scratch review: 9 claims; fixed seek-bar semantics, queue key churn, cover
      query strings, account read off Main. Not fixed (small): `runBlocking` DataStore read in
      `AppGraph.mediaEngine`, `AutoBackup.list()` and `forgetSnapshots()` file I/O on Main
- [x] **0.14.0 · S0nata** published as the regular release (code 1409), via the generalized
      `publish-release.yml` (version from build.gradle.kts, notes from `.github/release-notes.md`)
- [ ] Undo in the queue re-inserts at the captured index; harmless today (Media3 clamps), wrong if
      the queue is reordered within the 6 s window

## Playground (branch `playground`, may never ship)

Ideas agents think would be fun. Each one small, behind a setting or easy to drop. If one turns
out good, it gets its own reviewed PR to main. Add yours with your agent name.

- [x] Hidden Playground section: 7 taps on the version line in Settings (Laguna)
- [x] Listening streak on Stats, off by default (Laguna; API-26 fix by Claude)
- [ ] (open) your idea here

## Earlier: bug-fix release (0.14.0 previews 1–2)

- [x] PR #2 merged (verification fixes; network security config fixed to the real schema)
- [x] CI: drop setup-android's unsupported `cache` inputs (OpenCode via fcc)
- [x] **One permanent release key**: CI and local builds sign with it, so updates install over
      each other (they failed because every machine and CI runner had its own debug key)
- [x] Player: full-bleed artwork + button gap (OpenCode/Laguna), Material volume/headphone icons
- [x] **PlayerPanes accessibility** (48 dp targets: lock, row menu, drag handle, mini like/more, Undo, Clear; now-playing row glyph); merged with preview 3
- [x] **PlayerSheet accessibility (rest)** (merged with preview 3, commit 98d139f): volume slider ≥ 48 dp hit area + slider semantics (518-546), debounce `setStreamVolume` off drag frames (526, 535), output-device lookup off the main thread (554-577), MEDIA_OUTPUT intent guard for API 35+ (588), title clickable node (366)
- [x] Released v0.14.0-preview.1 and v0.14.0-preview.2 (CI-built, permanent key, SHA256SUMS)

## Sonata roadmap (remaining work from docs/SONATA-SPEC)

- [x] **Update checker**: GitHub Releases updater, daily/manual checks, preview opt-in, download progress, SHA-256 validation, and Android installer handoff; shipped in P4.
- [ ] **Heavy inspiration from ConvX and BitChord, premium feel:** player screens, lyrics,
      mixing and crossfade, Home, Settings, Downloads, theme settings, look and feel
- [ ] Transfers sheet: progress bars and controls for every download, upgrade and export
- [ ] Max quality options for playback, downloads and export; study ConvX's extra sources
      (JioSaavn) behind a setting
- [ ] Collection pages + multi-select; Library menu; tabs + Explore + Search moods;
      collapsing nav; Home top picks; history by day
- [ ] Name phase-out: rename the vendored innertube package; keep credits and key names

## Process

- [ ] fcc: set a working default model + fallbacks (fixes fcc-hermes and fcc-dsh; stops hangs
      when one free model dies)
- [ ] fcc agents native in Orca: Codex already routes through fcc; OpenCode provider block added,
      verify once fcc is idle
- [ ] Agents must not hang: watchdog (`tools/worker-guard.sh` in the workspace) nudges and
      restarts; fcc fallbacks switch models on failure

## Done

- Shared vault `.agent/` with CI privacy guard
- Repo tidy: stale branches removed, site design note tracked, no local paths in docs
