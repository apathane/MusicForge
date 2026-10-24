# Contributing to MusicForge

Thanks for wanting to help. This is a personal project that happens to be open source; the bar for merging is "it works on my phone and doesn't break yours."

## How to build

You need JDK 21 and the Android SDK (platform 37, `compileSdk 37`, `targetSdk 36`, `minSdk 26`).

```sh
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:lintDebug
```

Or run the one command the CI runs:

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

1. Read `CLAUDE.md` — it's the architecture guide and the project rules.
2. Run `scripts/check.sh` before opening a PR (debug build + unit tests + lint, 0 lint errors).
3. Work in your own git worktree and branch. Commit small, with the `Co-Authored-By:` trailer.
4. Don't use adb, the emulator or the phone unless your task says so.
5. Stay inside the files your task names. If something outside them must change, ask.
6. Rules that bite: no DI framework (wire through `AppGraph`), no Ktor (YouTube goes through the vendored innertube module), tests are JUnit4 + Robolectric + Truth, Compose motion uses `graphicsLayer` only, nothing heavy on the main thread, artwork requested at slot size, keep upstream DataStore key names.
7. Never commit real user data: backups, cookies, databases.
8. Voice: see "Personality" and "UI voice" in CLAUDE.md.

## What we won't take

- **Trackers or analytics.** Not even "anonymous" ones.
- **Ads.** The app doesn't have them and never will.
- **Features that need paid API keys** (YouTube Music Premium gating, Last.fm scrobbling that requires a key, etc.). The app works with what YouTube Music serves for free.
- **Code that sends data anywhere except `music.youtube.com` and `lrclib.net`** (for lyrics, opt-in).
- **DI frameworks, Ktor, RxJava, or Compose Multiplatform.** The stack is intentional.
- **Features that only work on rooted phones or with Magisk modules.**
- **Changes that break backup compatibility** with Metrolist/ConvX (same table/column names, same DataStore keys).

## Before you open a PR

- Run `./scripts/check.sh` and confirm it passes.
- If you changed UI, check the screenshots in `docs/screenshots/` still look right.
- If you added a setting, add its DataStore key to `Keys.importable` in `data/prefs/Prefs.kt` so backups restore it.
- If you changed the database, add a Room migration and update `app/schemas/`.
- Update `CHANGELOG.md` with a short, human bullet under the current phase.

---

*MusicForge is GPL-3.0. See [LICENSE](LICENSE).*