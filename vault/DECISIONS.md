# Engineering Invariants and Decisions

[← Vault index](README.md) · [Architecture](ARCHITECTURE.md) · [Workflow](WORKFLOW.md)

This is the “things an agent must not accidentally break” page. Some entries are explicit project policy; others are source-derived invariants inferred from code and documented rationale. If new evidence changes one, update this page with the reason.

## Product and privacy

1. **No ads, analytics, crash reporting or telemetry.** This is explicit project policy in CONTRIBUTING/SECURITY. Do not quietly add analytics or a tracker.
2. **Local-first, no invented recommendations.** Home should use real user signals and provider data; every personal shelf needs a reason. Remote feed failures should not make local content disappear.
3. **Network destinations must be explicit.** YouTube/YouTube Music APIs and Google's video-delivery hosts support browse, account sync, stream resolution and playback; LRCLIB supports lyric lookup; ListenBrainz is a third-party service used only when the listener explicitly enables it and supplies a token. Update `CONTRIBUTING.md` and `SECURITY.md` if integration behavior changes. The YouTube account cookie is for YouTube Music requests and must not be sent to lyric/scrobbling providers.
4. **Secrets stay out of Git/issues.** Never commit cookies, token values, PoTokens, signing keys, real backups, Room DBs, preference files, session IDs or logs that reveal them.
5. **No paid API key dependence, root-only requirement, or hidden network behavior.**

## Architecture constraints

- No DI framework. Add shared wiring in `AppGraph`.
- YouTube Music API work goes through the vendored `:innertube` module. Stream extraction is through `InnerTubeXPlayer`; it is not the same layer as general browse/search API.
- Do not add Ktor-based alternative YouTube networking contrary to AGENTS/CONTRIBUTING instructions. The dependency catalogue may contain libraries for transitive or existing paths; that is not permission to bypass the architecture.
- Tests are JUnit4, Robolectric where Android APIs are needed, and Truth assertions.
- Don't do disk/network, heavy image decoding, extraction or recommendation ranking on the main thread.
- Keep Compose motion compositing-focused: transforms/alpha through `graphicsLayer` per agent rules. Preserve stable keys/content types in lazy lists where appropriate.
- Ask for artwork close to its rendered slot size. Reuse the precomputed ambience and avoid per-frame blur or extra full-screen draws.

## Cache and transfer invariants

- Player LRU and permanent download cache stay separate.
- Playback is read-only against download cache, then uses player cache/network. Download writer is associated with download cache.
- Use the media/video ID consistently for source metadata and cache identity; quality upgrades intentionally use `#hq`.
- A duplicate song may alias its bytes to another ID only when the normalized title/first artist and duration rule matches. Keep shared bytes while any dependent download still points to them.
- Cached URLs expire. On refusal (403/410/416), invalidate URL and mark the client failed to avoid blind repeats.
- Mid-file re-resolution must match known content length. Never splice partial bytes from a new rendition into old bytes.
- Bounded range transfer is a measured requirement, not premature cleverness. The 4MiB range default reflects a measured threshold around 10MiB; remeasure if provider behavior or network changes.

## User-data and database invariants

- Keep the DB filename `song.db` and upstream table/column names where backup interoperability depends on them.
- Current Room version is 3. Schema changes require migrations, exported schema updates and migration test coverage.
- Settings imported across apps depend on compatible DataStore names and keys. Add a new cross-compatible key to `Keys.importable` when appropriate.
- Sync deletion is allowed only when an item was in the previous successful remote snapshot and is absent from the new complete snapshot. Partial requests must not erase local data.
- Sign-out clears identity/sync snapshots, not the user's local library.
- Restore must validate input, reject empty/damaged backups and snapshot the nonempty local library first. Do not add permissive archive extraction.
- **Backups are sensitive and not encrypted by the app.** `BackupExporter` copies `settings.preferences_pb` into the user-requested ZIP as-is; it can contain the YouTube Music cookie/session state, ListenBrainz token, proxy credentials and other saved preferences. Never commit backup archives, say they are safe to share, or log those values. The restore UI has an explicit choice about importing account/session state, but that does not encrypt the exported file.
- App-specific `recommendation_feedback` and `remote_history` tables are outside the foreign backup table list. Inspect restore semantics before changing what imported backup data replaces.
- Export only after enough bytes are actually in cache; don't trigger hidden network downloads while “saving files”. Do not delete files that were deliberately exported to the user's Music folder merely because an in-app download was removed.

## UI and performance

- Live glass is restricted to the nav pill and mini player; SOLID / ARTWORK fallback tiers are important.
- Artwork ambience decodes a tiny 54×120 bitmap once per current art URL, derives palette, and reuses the blurred result.
- Avoid new perpetual animations. Paused/off-screen components should stop unnecessary polling and recomposition.
- On weak hardware, measurable behavior beats aesthetics-on-paper. Emulator GPU-rendering measurements are directional; the X7b is the reference device.
- Keep accessible names and 48dp targets. Use explicit loading/empty/error states.
- A player redesign must preserve gesture boundaries: sheet drag handles, lyrics reading/auto-follow, queue reorder/remove and track actions should not fight each other.

## Upstream code and licensing

- MusicForge is GPL-3.0. Preserve the license and any upstream headers/attributions.
- Metrolist and ConvX influence schemas/settings/flows, but their source should not be transplanted blindly.
- BitChord is an inspiration for bounded-range transfers, monochrome style and lyrics/clock design. Reimplement/adapt with review of code provenance, license compatibility, APIs and tests.
- Kyant0/backdrop has Apache-2.0 attribution and notice in `licenses/backdrop-Apache-2.0.txt`; retain it.
- InnerTubeX version/commit is pinned via dependency config to reduce surprise API drift. Changing the pin should be intentional and tested.

## Documentation consistency risks

- `CLAUDE.md` contained a stale “Room schema version 2” statement in the reviewed main branch; code in `MusicDatabase.kt` is version 3.
- The security/contributor docs described YouTube and LRCLIB as the only network destinations, while opt-in ListenBrainz uses `api.listenbrainz.org`. The documentation should say this plainly.
- The spec's proposed phase is older than the current Sonata implementation. Re-check source and changelog before copying a proposed UI component or migration order.
- `scripts/check.sh` covers app build/test/lint only; CI also covers `:innertube` test/lint. Be clear whether “check passed” means local gate or full CI.

