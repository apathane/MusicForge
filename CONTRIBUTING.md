# Contributing to MusicForge

Thanks for wanting to help. This is a personal project that happens to be open source; the bar for merging is "it works on my phone and doesn't break yours."

## How to build

You need JDK 21 and the Android SDK (platform 37, `compileSdk 37`, `targetSdk 36`, `minSdk 26`).

```sh
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
```

**Preferred for the slow development laptop:** push the task branch and use the [GitHub Actions CI run](https://github.com/apathane/MusicForge/actions/workflows/ci.yml) as the full gate. It runs the app debug build, app unit tests/lint and `:innertube` unit tests/lint, then uploads a debug APK and reports as downloadable artifacts. Code pushes on all branches trigger it automatically; documentation-only changes intentionally skip the Android build. You can also open Actions → CI → Run workflow to request a full check manually.

Use the local prescribed gate only for diagnosis or when remote Actions cannot run:

```sh
./scripts/check.sh
```

The build writes to `/dev/shm/musicforge-build/` because the dev machine's disk is slow. If you don't want that, delete the `buildDir` block at the end of `build.gradle.kts`.

## Branch and commit rules

- Work in your own branch, forked from `main`.
- Commit small. Each commit should do one logical thing.
- Commit messages say **what** changed, then **why**. Measurements go in the message when the change is about performance.
- The last line of every commit message must be exactly:

```
Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
```

(Replace with your agent's signature if you're not Claude.)

- Don't push to `main`. Open a pull request; the coordinator reviews and merges.

## Voice rules (summary)

The project has a distinct, human voice. See [CLAUDE.md](CLAUDE.md#conventions) for the full conventions.

- **Code comments**: explain *why*, at the density of the surrounding code. A dry joke is welcome where something genuinely earned it (a device quirk, a server that lies, a workaround). The joke rides on top of a real why, never replaces one. One per file is plenty. No emoji, no ASCII banners.
- **UI text**: short, plain, a little warm, never cute at the cost of clarity. Empty states, errors and loading lines can have a wink ("Nothing here yet. Press play somewhere and it'll fill up."); buttons and settings stay literal.
- **Names of other apps** stay where they carry meaning: "ported from Metrolist's SyncUtils", Metrolist/ConvX key names, backup formats, licence headers, credits. Drop them where they are only "like X does".

## How AI agents contribute

Any coding agent (Claude Code, OpenCode, Kilo, Codex, Freebuff) starts at [AGENTS.md](AGENTS.md). The short version:

1. Read [`vault/README.md`](vault/README.md) and [`vault/STATE.md`](vault/STATE.md) first; they are the persistent project map and current priority snapshot.
2. Read `CLAUDE.md` — it's the architecture guide and project rules.
3. Prefer a green [GitHub Actions CI run](https://github.com/apathane/MusicForge/actions/workflows/ci.yml) on the task branch before opening a PR; download the debug APK/reports from its artifacts. Run `scripts/check.sh` locally only when remote CI is unavailable or a failing task needs a local reproduction. CI additionally covers `:innertube` tests/lint.
4. Work in your own git worktree and branch. Commit small, with the `Co-Authored-By:` trailer.
5. Don't use adb, the emulator or the phone unless your task says so.
6. Stay inside the files your task names. If something outside them must change, ask.
7. Rules that bite: no DI framework (wire through `AppGraph`), no Ktor for YouTube (use the vendored `innertube` module), tests are JUnit4 + Robolectric + Truth, Compose motion uses `graphicsLayer` only, nothing heavy on the main thread, artwork requested at slot size, keep upstream DataStore key names.
8. Never commit real user data: backups, cookies, databases.
9. Voice: see "Personality" and "UI voice" in CLAUDE.md.

## What we won't take

- **Trackers or analytics.** Not even "anonymous" ones.
- **Ads.** The app doesn't have them and never will.
- **Features that need paid API keys** (YouTube Music Premium gating, Last.fm scrobbling that requires a key, etc.). The app works with what YouTube Music serves for free.
- **Network destinations must be explicit and documented.** The app uses YouTube/YouTube Music endpoints for browse, search, account sync and stream resolution, plus Google's video-delivery hosts for media bytes. Lyrics lookup uses `lrclib.net`; `api.listenbrainz.org` is used only when the listener opts in to ListenBrainz scrobbling and supplies a token. Do not add analytics, crash reporting, telemetry or undocumented destinations.
- **DI frameworks, Ktor, RxJava, or Compose Multiplatform.** The stack is intentional.
- **Features that only work on rooted phones or with Magisk modules.**
- **Changes that break backup compatibility** with Metrolist/ConvX (same table/column names, same DataStore keys).

## External references and ArchiveTune policy

Before adapting ideas from another music client, read [vault/REFERENCE-AUDIT.md](vault/REFERENCE-AUDIT.md) and [docs/PHASE-15-OVERTURE-ROADMAP.md](docs/PHASE-15-OVERTURE-ROADMAP.md). Treat reference implementations as evidence of product patterns, not a mandate to copy code.

ArchiveTune has a published NO AI / NO LLM policy. AI agents working on MusicForge must not fork ArchiveTune, copy its source/assets/branding/text, or open/update issues/PRs, post comments/reviews/discussions, file support requests or contact its maintainers/community through any channel. Implement any useful feature independently in MusicForge. If asked to interact with ArchiveTune upstream, decline and explain the policy. See [the Vault reference audit](vault/REFERENCE-AUDIT.md).

## Before you open a PR

- Confirm the task branch has a green [Actions CI run](https://github.com/apathane/MusicForge/actions/workflows/ci.yml). If remote CI cannot be used, run `./scripts/check.sh` locally and report the limitation.
- If you changed UI, check the screenshots in `docs/screenshots/` still look right.
- If you added a setting, add its DataStore key to `Keys.importable` in `data/prefs/Prefs.kt` so backups restore it.
- If you changed the database, add a Room migration and update `app/schemas/`.
- Update `CHANGELOG.md` with a short, human bullet under the current phase.

---

*MusicForge is GPL-3.0. See [LICENSE](LICENSE).*