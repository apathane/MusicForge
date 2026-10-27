# Project History and Rationale

[← Vault index](README.md) · [Current state](STATE.md) · [Changelog](../CHANGELOG.md)

This page summarizes context worth carrying into future work. The canonical item-by-item log is [CHANGELOG.md](../CHANGELOG.md), and Git commits remain the authority for precise implementation history.

## Product lineage

MusicForge is a personal player with a measured offline/download path rather than a reskinned streaming client. It deliberately borrows ideas from existing open-source apps while keeping a custom, local-first identity.

- **Metrolist** supplied/seeded much of the InnerTube API and account/library-sync lineage, download patterns, preference/table compatibility and history conventions.
- **ConvX** influenced screens/features and the backup format; selected UI patterns were adapted.
- **BitChord** informed bounded-range transfer, monochrome restraint and artwork-focused music player ideas. The current Quest Board tracks its lyrics source/parser/clock design as a reference for possible improvements.
- **InnerTubeX** handles stream extraction. Keep the extraction library and generic YouTube API client distinct.
- **Kyant0/backdrop** contributes the liquid-glass renderer (Apache-2.0, retained notice).

The [Personalized UI spec](../docs/PERSONALIZED-UI-SPEC.md) records a larger research effort around a private listening instrument: personal signals, explainable sections, feedback controls, adaptive UI, accessibility and device performance. It predates the current Sonata phase and should be treated as a design rationale that must be checked against current source, not as a live list of unfinished work.

## Durable milestones from the changelog and commits

### Crescendo (earlier phases)
- Benchmarked Google video transfer behavior: whole/open-ended/large range requests throttle on long streams, while bounded ranges transfer at line speed. Default 4 MiB chunks reflect the measured threshold and round-trip trade-off.
- Shared cache/download architecture and read-ahead are designed around Media3's single cache-writer constraint.
- Existing download index and metadata recovery make downloads survive process death and repair missing Room rows.

### Nocturne / phase 9
- Reduced overdraw in player/artwork rendering.
- Added widget, persistent queue resumption, in-app equalizer, sleep timer, Android Auto support, ListenBrainz, download/lyrics export and improvements to cache/restore safety.
- Built a single artwork ambience pipeline; effects should share tiny precomputed pixels rather than blur full images every frame.

### Aria / phase 10
- Home became a deterministic composition of real listening history and local signals. Recommendations explain themselves and user corrections (“not for me”, “less of an artist”) affect suggestion shelves.
- Personalization research and the current design spec describe the longer-term UI direction.

### Fugue / phase 11, Gigue / phase 12
- Added Home and playback correctness improvements, account/library and visual changes, liquid glass restricted to small surfaces, quality/download actions and further UI polish.
- Changelog documents release phase progression; consult it for exact per-phase details.

### Rondo / phase 13
- 2026-10-24 phase release and app polish: swipe mini-player to skip, queue swipe-to-remove with Undo, haptic/pressed states, short route transitions, skeleton loading rows, pull-to-refresh, pasted link search and artist-page affordances.
- Release `v0.13.0-rondo` was published 2026-10-24, according to GitHub release metadata at the time of writing.

### Sonata / phase 14
- The current development version is `0.14.0-sonata`, while the latest stable published release found on 2026-10-27 remained Rondo. Check releases before claiming otherwise.
- Source/changelog document two-way YouTube Music listening-history sync plus the first player redesign pass (codec pill, Up Next, volume, output-device picker).
- Commit `248c4fee9e2631ac48ac89eafbc8306405b16a6e` (the exact SHA is [here](https://github.com/apathane/MusicForge/commit/248c4fee9e2631ac48ac89eafbc8306405b16a6e)) says the first player pass passed the build gate on its worker/emulator but that the full-bleed artwork was still a card and there were icon/touching-disc follow-ups. Inspect current source before assuming each is still open.
- Commit `43194be9613df8152ad29c91474f53198c347b16` added remote history table/migration and local/remote recent-play merge; its message records five merge tests and a schema migration test.

## Research still worth keeping active

1. **Lyrics quality and tests:** LRCLIB matching and fallback exist, but dedicated lyrics parsing/provider tests were not found in the initial tree. Start with reliability, not word-animation.
2. **BitChord patterns:** multiple source abstraction, query cleanup, word-timed enhanced LRC, TTML, independent lyric clock, sync offsets and tests may guide a gradual upgrade. Don't copy wholesale.
3. **UI completion:** compare Sonata roadmap with current source/screenshots. Avoid repeating a design-spec migration list that already shipped in some form.
4. **Performance:** transfer measurements are reproducible host/emulator numbers; player/UI performance still needs the real target hardware for final claims.
5. **Tooling:** Hermes through FCC vs the current coding agent vs standalone Hermes remains an experiment. The user asked for web/Reddit-informed evaluation and this is tracked as Q13 on the Quest Board. Prefer a controlled coding task rather than a subjective one-day impression.

## Decisions to preserve

- Fast bounded ranges exist because of measured server behavior, not style.
- The local library and exported files are user assets; recovery and compatibility beat risky cleanup.
- Home is based on actual user signals and explicit remote content, not invented fillers.
- Liquid glass is a limited accent; more blur does not equal better design if the target GPU loses frames.
- The vault exists to avoid repeatedly rebuilding project context, but must remain explicitly grounded in current source.
