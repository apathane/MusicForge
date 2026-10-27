# Reference Audit: What MusicForge Should Learn

> **Research snapshot: 2026-10-27.** This is a feature/architecture comparison, not a recommendation to fork or copy another application. ArchiveTune is treated more restrictively: only its public README, explicit AI policy and contribution-policy text were reviewed; no source files or contribution workflow were used.

[Vault index](README.md) · [Phase 15 proposal](../docs/PHASE-15-OVERTURE-ROADMAP.md) · [Quest Board](../docs/QUEST-BOARD.md)

## The conclusion

MusicForge should not become the union of every feature in Convx, ArchiveTune, BitChord and Metrolist. Each has different strengths and costs. MusicForge has an identity worth protecting: fast offline downloads, a persistent repairable cache, explainable local-first Home, compact monochrome design, backup compatibility and a defined target phone.

Near-term priorities: a distinctive Now Playing screen; dependable lyrics that can grow into word timing; a real Explore experience separate from Home and Search; audio/output labels that report facts; and performance/reliability/privacy as release gates. Local media, smart mixing, Listen Together, network storage and provider modules are later design decisions.

## Repository and policy matrix

| Project | Public strengths | What was examined | Caveat |
|---|---|---|---|
| [Convx](https://github.com/cosmictaserdev-creator/Convx) | Cupertino-style/Liquid Glass, word-synced lyrics, providers, Automix/DJ transitions, local/network storage, playlist imports, Listen Together | README, AI agent guide, contributor guide and selected lyric/transition source | README says stable is outdated/not working and recommends nightly 2.0.4, which may be unstable. Crossfade/Automix has analysis, dual-player and state complexity. |
| [BitChord](https://github.com/kushagrasinghx/BitChord) | Apple Music-influenced player, word/syllable lyrics, multiple sources, audio/source detail, playlist UX, Listen Together, Automix, Chromecast | README, release notes, lyric repository/clock, output negotiation and crossfade controller | The advanced audio/transition engine is large. Honest output reporting is useful; copying its audio-stack complexity is not a Phase 15 goal. |
| [Metrolist](https://github.com/MetrolistGroup/Metrolist) | YouTube Music playback/downloads, account/library sync, lyric providers, TTML, EQ and scrobbling | README, agent rules, provider registry/interface, TTML parser and EQ profile repository | README says maintenance mode: bug fixes/minor improvements only; no feature/major-change PRs requested. Preserve upstream lineage/license notices. |
| [ArchiveTune](https://github.com/rukamori/ArchiveTune) | README lists local playback, multi-account, player/background styles, lyric translation/romanization, recognition, stats, scheduled/cloud backup, Canvas, podcasts, Android Auto and .arch provider packages | **README, .NO_AI/README.md and contribution-policy text only. No code was inspected.** | Explicit NO AI / NO LLM policy. No AI contribution, fork, issue/PR/comment/review/discussion/support message or direct contact. Feature descriptions only for independent MusicForge inspiration. |

## Required ArchiveTune boundary for every agent

ArchiveTune's public policy forbids AI/LLM use for issues, bug reports, patches, pull requests, comments, code reviews and translations, and directs LLM agents to refuse repository interaction. The owner's instruction for MusicForge is to keep ArchiveTune out of upstream contribution/contact workflows.

**When working on MusicForge, AI agents must:**
- Never open, draft, submit or update an ArchiveTune issue or pull request.
- Never post an issue/review comment, commit, discussion, support request or message in ArchiveTune repositories or community spaces.
- Never contact ArchiveTune maintainers/community through GitHub, Discord, Telegram, email or any other channel.
- Never fork ArchiveTune or create an ArchiveTune-derived branch/worktree as a workaround.
- Never copy ArchiveTune source, assets, artwork, branding or product text into MusicForge.
- If asked to interact upstream, stop and point out the policy.
- If an ArchiveTune feature is relevant, record a feature-level idea in MusicForge planning docs and implement it independently in MusicForge. Never imply approval or affiliation.

This boundary is ArchiveTune-specific. It does not forbid independent MusicForge development. It is not permission to reuse source from other references without a separate license/provenance review.

## 1. Convx: motion, lyrics orchestration and ambitious transitions

**Observed in README/source inspected**
- Kotlin/Compose/Media3, dedicated HIG, Cupertino-style components and custom glass surfaces.
- The inspected LyricsProviderRepository has provider status, enablement, bounded concurrency, cached attempts and duplicate in-flight request reuse.
- The README describes crossfade, Automix and DJ transitions. The inspected CrossfadeController has outgoing/incoming player states, a preloaded incoming track, hand-off, abort/recovery and optional filter effects.
- Convx splits lyrics, artwork, Canvas, Spotify, integrations and SMB/WebDAV into modules, and has a Listen Together server.

**MusicForge lesson**
- Design provider boundaries and user-visible provider state rather than multiplying ad-hoc fallback calls.
- Separate transition planning from audio transition execution. Test cancellation and recovery.
- Motion should explain state/space, remain interruptible, respect reduced-motion and preserve listening/reading.

**Do not transplant wholesale**
Convx's architecture, components and Hilt wiring belong to its own project. Two players plus DJ DSP raise battery, decoder, queue-authority and lifecycle costs for the Snapdragon 680 target. Convx's current README warns that stable is outdated/not working and points to a nightly. Treat its features as direction, not a reliability ranking.

Sources: [Convx README](https://github.com/cosmictaserdev-creator/Convx/blob/main/README.md), [AI Agent Guide](https://github.com/cosmictaserdev-creator/Convx/blob/main/AGENT_CONTRIBUTION_GUIDE.md), [LyricsProviderRepository.kt](https://github.com/cosmictaserdev-creator/Convx/blob/main/app/src/main/kotlin/com/convx/music/lyrics/LyricsProviderRepository.kt), [CrossfadeController.kt](https://github.com/cosmictaserdev-creator/Convx/blob/main/app/src/main/kotlin/com/convx/music/playback/CrossfadeController.kt), [2.0.4 nightly](https://github.com/cosmictaserdev-creator/Convx/releases/tag/2.0.4-nightly).

## 2. BitChord: lyric clock and honest audio information

**Observed in README/source/release notes**
- Release v1.8.1 (19 October 2026) advertises a redesigned player, source-quality badge, artwork colors and an output pill that shows the device actually playing.
- Its lyric repository supports a user-selected provider order and word/syllable preference. Providers are run concurrently, but result choice respects user priority rather than simply the fastest response.
- Its lyric clock runs between player samples, timestamps readings, models playback rate and treats seek/skip/restart as explicit discontinuities.
- Its output negotiation model separates source, decoder, DSP, route and output and provides fallback reasons.
- Release notes describe lyric sharing, CJK matching, output route reporting and playlist batch actions.

**MusicForge lesson**
Get matching/fallback right, then add a pure/testable lyric clock. Show active route and source quality only when verified. Add lyric sharing and playlist multi-action after core reliability.

**Do not transplant wholesale**
BitChord's CrossfadeController is a large subsystem with two-player role swaps, queue reconciliation, DJ planning, analysis and error recovery. Direct USB/high-resolution output is not a casual upgrade and must not be marketed as bit-perfect without device measurements.

Sources: [BitChord README](https://github.com/kushagrasinghx/BitChord/blob/main/README.md), [v1.8.1 release](https://github.com/kushagrasinghx/BitChord/releases/tag/v1.8.1), [LyricsRepository.kt](https://github.com/kushagrasinghx/BitChord/blob/main/shared/src/jvmSharedMain/kotlin/com/music/bitchord/data/lyrics/LyricsRepository.kt), [LyricClock.kt](https://github.com/kushagrasinghx/BitChord/blob/main/sharedUi/src/jvmSharedMain/kotlin/com/music/bitchord/ui/player/LyricClock.kt), [OutputNegotiator.kt](https://github.com/kushagrasinghx/BitChord/blob/main/app/src/main/java/com/music/bitchord/playback/audio/OutputNegotiator.kt).

## 3. Metrolist: provider conventions and reliability

**Observed in README/source/release notes**
- Metrolist explicitly says maintenance mode and asks for no new-feature or major-change PRs.
- Its lyric provider interface/registry uses a simple contract and serializable provider ordering, restoring defaults when stored order lacks providers.
- Its TTML parser handles namespace variants, line/word spans and background lines and disables XML external entities/DTDs where supported.
- Releases highlight lower memory use on large libraries and fixes for stream fallback, crossfade and account sync.

**MusicForge lesson**
Keep provider identity/order stable; make fallback explicit; harden parsing. Test migrations, sync and failure recovery. Preserve Metrolist/ConvX DB and DataStore compatibility already required by MusicForge.

Sources: [Metrolist README](https://github.com/MetrolistGroup/Metrolist/blob/main/README.md), [AGENTS.md](https://github.com/MetrolistGroup/Metrolist/blob/main/AGENTS.md), [LyricsProvider.kt](https://github.com/MetrolistGroup/Metrolist/blob/main/app/src/main/kotlin/com/metrolist/music/lyrics/LyricsProvider.kt), [LyricsProviderRegistry.kt](https://github.com/MetrolistGroup/Metrolist/blob/main/app/src/main/kotlin/com/metrolist/music/lyrics/LyricsProviderRegistry.kt), [TTMLParser.kt](https://github.com/MetrolistGroup/Metrolist/blob/main/app/src/main/kotlin/com/metrolist/music/betterlyrics/TTMLParser.kt), [release notes](https://github.com/MetrolistGroup/Metrolist/releases).

## 4. ArchiveTune: feature ideas only, strict separation

**Public README/release descriptions only; no code inspection.**

Its public feature overview describes local audio playback, multiple accounts, player/background customization, lyric translation/romanization, music recognition, stats, scheduled/Google Drive backups, Canvas art, podcasts, Android Auto and .arch provider modules.

Questions for later MusicForge phases, not Overture commitments:
- Should local files be first-class? That affects item identity, permissions, metadata, playlists, queue, lyrics and backups.
- Do listeners need multiple account profiles? Switching touches cookies, sync snapshots and local-data isolation.
- Is animated artwork worth the network/battery cost on the target phone?
- Can provider-supplied translation/romanization meet needs before any AI service?
- Would encrypted backups add value before cloud storage?
- Would modules solve a real maintenance problem? They add code execution, trust, permissions, updates and revocation concerns.

ArchiveTune's explicit policy is a firm upstream boundary. These are independent feature questions for MusicForge, not a request to fork, copy or contribute upstream.

Sources: [ArchiveTune README](https://github.com/rukamori/ArchiveTune/blob/main/README.md), [NO AI / NO LLM Policy](https://github.com/rukamori/ArchiveTune/blob/main/.NO_AI/README.md), [15.1.0 release notes](https://github.com/rukamori/ArchiveTune/releases/tag/15.1.0).

## Feature gap matrix

| Idea | MusicForge today | Recommendation | Horizon |
|---|---|---|---|
| Line-synced lyrics | LRCLIB + YouTube plain fallback, cache, tap-to-seek | Parser/provider regression tests first | Overture |
| Multiple lyric sources/priority | Two-source strategy, no general registry verified | Small provider contract after tests | Overture |
| Word/syllable lyrics and stable clock | Current model uses timed lines | Enhanced LRC, then TTML and tested clock | Overture, staged |
| Lyrics offset/source picker/share card | Not confirmed complete | After pipeline is stable | Overture |
| Distinctive player/artwork ambience | Player and ambience already exist | Refine; don't replace wholesale | Overture |
| Active output/source info | Output picker and quality display exist | Verify actual route; label truthfully | Overture |
| Explore: moods, genres, releases, charts | README roadmap describes it; source tabs are Home/Search/Library/Downloads | Navigation gap audit, then real catalogue sections | Overture |
| Collection multi-select | Not verified complete | Implement only missing actions | Overture |
| Queue remove/undo/reorder | Much already exists | Test/polish gaps | Sonata/Overture |
| Local-file library | Not a first-class feature in verified map | Design ID/permissions/backup first | Phase 16 |
| SMB/WebDAV | Not in verified map | Only with a real use case | Later |
| Crossfade/Smart Automix | Deferred due two-decoder cost | Target-device evaluation after Overture | Phase 17 |
| Listen Together | Not in current architecture | Protocol/backend/privacy/clock design first | Later |
| Music recognition / Canvas | Not core features | Research privacy, battery and permissions | Later |
| Provider modules | Not present | Avoid until trust, signing and update rules exist | Later |
| Translation/romanization | Not verified complete | Start with provider-supplied data | Later |
| EQ profiles by headphones | Five-band EQ exists; external profiles not confirmed | Separate evaluation | Later |
| Cloud backup | Local backups exist; preferences ZIP is sensitive/unencrypted | Block until encryption/consent design | Security gate |

## Proposed order

1. Finish Sonata with a gap matrix and failure-path review.
2. Lyrics reliability and matching tests.
3. Player cohesion and target-device verification.
4. Lyrics v2: source/order, offset, richer timing and clock.
5. Explore with real catalogue sections and resilient states.
6. Library details where source audit proves a gap.
7. Measure, harden and release.
8. Decide separately on Pocket Library, Smart Transitions, social and provider modules.

## Using this audit

For each quest, read MusicForge source/tests on the active branch, confirm the feature still is missing, use this page for design trade-offs rather than copying code, obey the ArchiveTune boundary, and update the audit when a reference or product decision changes.
