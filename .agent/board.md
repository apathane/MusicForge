# Board

Claim an item by adding `claimed: <agent> · <branch>` under it; move it to Done with the commit
or PR when it lands. Agents work in separate worktrees, never in the same checkout.

## Now: preview 3 (owner bug list, 2026-10-28)

Plan: each agent owns its files only, at most 2 per provider at once, CI builds, coordinator reviews.

- [ ] **Sign-in loops** (LoginScreen.kt): after the cookie is captured, `recreate()` reopens the
      login route, which captures and recreates again. Leave the route first; add a "Use this
      account" confirm button (BitChord-style) instead of capturing on every page load. (fcc-claude · Laguna)
- [ ] **Tapping the headphones icon crashes** (PlayerSheet.kt): `MEDIA_OUTPUT` panel missing on
      MagicOS, unguarded `startActivity`. Catch it, fall back to sound settings, then a toast.
      Same file: volume slider semantics, 48 dp hit area and debounce; output-device lookup off
      the main thread. (Hermes · Nemotron 3.5 Super VL)
- [ ] **Artwork not loading** (Up Next and elsewhere): find why; candidates: queue items without
      artworkUri, the URL rewrite, repair not covering queue items. Files: MediaItems.kt,
      components/Common.kt (Artwork), data/ArtRepair.kt. (fcc-opencode · Laguna)
- [ ] **In-app updater**: check GitHub Releases (latest, prereleases opt-in), compare
      versionCode, download the APK, verify SHA256SUMS, hand it to the installer
      (REQUEST_INSTALL_PACKAGES + FileProvider). A Settings row + an "Update available" card.
      (fcc-codex · Gemma 4 31B)
- [ ] **Docs and versions**: CHANGELOG entries for previews 1–3; README status and version; site
      version stays SONATA / 0.14. (fcc-pi · Gemini 3.5 Flash)
- [ ] **Identity (owner)**: the app looks too much like BitChord. Needs the owner's call on a real
      name, a colour and a signature element before Sonata's redesign. Not delegated.

## Earlier: bug-fix release (0.14.0 previews 1–2)

- [x] PR #2 merged (verification fixes; network security config fixed to the real schema)
- [x] CI: drop setup-android's unsupported `cache` inputs (OpenCode via fcc)
- [x] **One permanent release key**: CI and local builds sign with it, so updates install over
      each other (they failed because every machine and CI runner had its own debug key)
- [x] Player: full-bleed artwork + button gap (OpenCode/Laguna), Material volume/headphone icons
- [x] **PlayerPanes accessibility** (48 dp targets: lock, row menu, drag handle, mini like/more, Undo, Clear; now-playing row glyph; TogglePill icon verified, no change) — fix/fix-player-panes, commit 0008a1d, not pushed
- [ ] **PlayerSheet accessibility (rest)**: volume slider ≥ 48 dp hit area + slider semantics (518-546), debounce `setStreamVolume` off drag frames (526, 535), output-device lookup off the main thread (554-577), MEDIA_OUTPUT intent guard for API 35+ (588), title clickable node (366)
- [x] Released v0.14.0-preview.1 and v0.14.0-preview.2 (CI-built, permanent key, SHA256SUMS)

## Next phase: Sonata (rest of docs/SONATA-SPEC in the workspace; summary here)

- [ ] **Update checker** (like ConvX/Metrolist): check GitHub Releases on start (at most daily),
      show "Update available" with notes, download the APK and hand it to the installer;
      a setting to turn it off
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
