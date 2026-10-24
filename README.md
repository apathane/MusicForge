# MusicForge

**A YouTube Music player for Android that keeps your music close: it downloads fast, plays offline, and builds Home from what you actually listen to.** Small (5.4 MB), quick (cold start about half a second on a budget phone), and open source.

<p align="center">
  <img src="docs/screenshots/home.jpg" width="200" alt="Home: a hero card for the song you were on, then shelves built from your own listening">
  <img src="docs/screenshots/player.jpg" width="200" alt="The player, tinted from the artwork">
  <img src="docs/screenshots/queue.jpg" width="200" alt="The queue: drag to reorder, swipe a song away, undo">
  <img src="docs/screenshots/artist.jpg" width="200" alt="An artist page with play, shuffle and follow">
</p>

## Why it's different

- **Home is yours, not an algorithm's guess.** Jump back in, Around this hour (what you tend to play at this time of day), Your rotation, Liked then forgotten, your downloads, the last 7 days. Every shelf says why it's there, and "Not for me" or "Less of this artist" is one long-press away. Signed in, your YouTube Music feed follows, labelled as such.
- **Downloads that don't crawl.** Google's servers throttle big requests to about 30 kB/s, so every byte moves in 4 MiB pieces at full speed, 3 to 5 MB/s at home. Downloads survive the app being killed and pick up where they stopped. A song you already streamed downloads from the cache instead of the network, and low-quality copies can be upgraded in place.
- **Sign in with Google and everything comes with you.** Liked songs, saved songs, albums, artists and playlists sync from your account and stay in sync. Search runs as you, unless you pause search history.
- **Liquid glass, done carefully.** The nav bar and mini player blur, saturate and bend what's behind them, with a specular rim. Only those two small surfaces get it, so it stays smooth on a Snapdragon 680. On older phones it falls back to a tinted glass drawn from the artwork.
- **It feels right in the hand.** Swipe the mini player to skip, swipe a queue song away and undo it, re-tap a tab to jump to the top, pull to refresh. Long-presses give a haptic tick, and pages load with skeletons instead of spinners.
- **Paste a link, get the music.** Paste any YouTube or YouTube Music link into search and the song plays, or the playlist, album or artist opens.
- **Move in without starting over.** Restores Metrolist and ConvX backups, sign-in included. Backups don't carry music files, so it remembers what you had downloaded and offers it back in one tap, with the size up front.
- **Take it out.** Copies downloads to `Music/MusicForge` as Ogg/Opus with tags, cover art and synced lyrics inside the file, so any player or PC can use them.
- **The rest you'd expect.** Synced lyrics, radio that keeps the queue going, a sleep timer that fades out, loudness levelling to −14 LUFS, an in-app 5-band equalizer, Android Auto, a home-screen widget, "Hey Google, play … on MusicForge", ListenBrainz scrobbling, listening stats.

## Measured on a budget phone

All numbers come from an Honor X7b (Snapdragon 680, Adreno 610, Android 14) unless noted.

| | |
|---|---|
| Cold start | 511 ms median |
| Tap to sound, streaming | about 1.3 s |
| Scrolling a 6,253-song library | 0.16% late frames, slowest frame 14 ms |
| Downloaded song in airplane mode, tap to sound | about 0.3 s (emulator) |
| Download speed | 3 to 5 MB/s (home Wi-Fi) |
| Release APK | 5.4 MB |

## Things we found out the hard way

- **Google's video servers throttle any request above about 10 MiB to roughly 30 kB/s.** A request of 8 MiB or less streams at full speed. Every transfer here, downloads and playback alike, moves in 4 MiB pieces. The measurements are in [`docs/BENCHMARKS.md`](docs/BENCHMARKS.md).
- **YouTube answers HTTP 400 to a language tag it doesn't know.** The test phone is set to `en-ZA`, so the first run on real hardware failed everywhere, while the `en-US` emulator never noticed. The app now sends only languages from YouTube's own list.
- **Android quietly turns off write-ahead logging for a database you `ATTACH`.** So restore streams rows from a separate connection instead.
- **Picture links in old libraries rot.** About 3% of artist avatars and playlist covers in a real restored library now return 403 or 404. When a picture fails, the app looks the item up again and saves the new link.
- **Overdraw is the budget-phone killer.** Every cover on Home used to be painted four times per frame. Now each pixel is drawn once or twice, and the player went from four full-screen layers to one.

## Install

Grab the latest APK from [Releases](../../releases) and open it on your phone, or:

```sh
adb install MusicForge-*.apk
```

It uses its own package (`dev.musicforge.app`), so it sits next to any other player without touching it. On MagicOS, EMUI and MIUI, allow it to run in the background when it asks, or the system kills playback.

## Build

You need JDK 21 and the Android SDK (platform 37, build-tools 37).

```sh
./gradlew :app:assembleDebug        # debug build, installs next to the release one (.debug)
./gradlew :app:testDebugUnitTest    # 74 tests: restore, transfers, sync, links, export tags, equalizer, …
./gradlew :app:lintDebug            # kept at 0 errors
./gradlew :app:assembleRelease      # R8-minified; signed with the local debug key
```

Build outputs go to `/dev/shm/musicforge-build` when that exists, because the machine it was built on has a slow disk. Delete the block at the end of `build.gradle.kts` to build in the usual place. [`CLAUDE.md`](CLAUDE.md) is the architecture guide: the byte path, the two caches, downloads, playback, the data model and the rules that came from measuring on the phone.

## Status

Version 0.x, built in named phases (Overture, Prelude, Crescendo, Interlude, Cadenza, Coda, Encore, Reprise, Nocturne, Aria, Fugue, Gigue, and now **Rondo**). Each phase's codename shows as the app's name on the home screen until the app gets a final one. Each phase is tagged `phase-N-<codename>`.

Not done yet:

- Crossfade. A real one means two decoders at once on a budget chip, so it waits for its own phase with measurements.
- The player can still drop a frame or two as it opens.
- Selecting several songs at once, and a morph between the mini and full player.

## How it was made

apathane set the goal, the rules and the phone. Claude (Anthropic's model, through Claude Code) wrote the code, drove it on an emulator and on the phone over adb, and measured every performance claim before making it. Free models (through the Kilo and OpenCode CLIs) reviewed each phase. Every finding was checked against the code before anything changed, and many turned out wrong. Commit messages explain why each change was made.

## Credits and licence

GPL-3.0. MusicForge stands on open-source work, with thanks:

- [Metrolist](https://github.com/MetrolistGroup/Metrolist): the `innertube` module is vendored from it, and the download model and account sync follow its design
- [InnerTubeX](https://github.com/MetrolistGroup/innertubex): stream extraction
- [ConvX](https://github.com/cosmictaserdev-creator/Convx): screens, features, the backup format it restores, and the route by which the glass renderer arrived
- [Kyant0/backdrop](https://github.com/Kyant0/backdrop): the liquid glass renderer, vendored (Apache-2.0, `licenses/backdrop-Apache-2.0.txt`)
- [BitChord](https://github.com/kushagrasinghx/BitChord): bounded-range transfers, read-ahead and the monochrome style
- [Inter](https://rsms.me/inter/) (SIL Open Font License, `licenses/Inter-OFL.txt`)
- [LRCLIB](https://lrclib.net): synced lyrics

Not affiliated with Google or YouTube.
