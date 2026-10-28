# Board

Claim an item by adding `claimed: <agent> · <branch>` under it; move it to Done with the commit
or PR when it lands. Agents work in separate worktrees, never in the same checkout.

## Now: bug-fix release (0.14.0 preview)

- [x] PR #2 merged (verification fixes; network security config fixed to the real schema)
- [x] CI: drop setup-android's unsupported `cache` inputs (OpenCode via fcc)
- [ ] **One permanent release key**: CI and local builds sign with it, so updates install over
      each other (they failed because every machine and CI runner had its own debug key)
- [ ] Player fixes from the delegation test: full-bleed artwork + button gap; volume/headphone icons
- [ ] Player accessibility from the Hermes review: 48 dp targets (lock, row menu, drag handle,
      mini like/more, Undo, Clear, Play next), volume slider hit area + slider semantics,
      debounce `setStreamVolume`, output-device lookup off the main thread, now-playing row
      and "Next unavailable" not by colour alone
- [ ] Release: tag, notes, APK signed with the permanent key, SHA256SUMS

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
