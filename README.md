# MusicForge

A YouTube Music player for Android, written from scratch after reading three apps closely: the download engine from [Metrolist](https://github.com/MetrolistGroup/Metrolist), the screens and features from [ConvX](https://github.com/cosmictaserdev-creator/Convx), and the black-and-white look and fast transfers from [BitChord](https://github.com/kushagrasinghx/BitChord). It is tuned for, and tested on, an Honor X7b (Snapdragon 680, Adreno 610, Android 14, MagicOS).

The app doesn't have a name yet. Until it does, each development phase ships under its own codename, in order: Overture, Prelude, Crescendo, Interlude, Cadenza, Coda, Encore, Reprise, Nocturne. The current build shows up on the home screen as **Nocturne**. It is version 0.x. 1.0 comes once it has a name and has served as someone's daily player for a couple of weeks.

## What it does

| | |
|---|---|
| **Home** | Built from your own listening, never invented: Jump back in, Around this hour (what you tend to play at this time of day), Your rotation, Liked then forgotten (a different handful each day), your downloads, your playlists, the last 7 days. Every shelf says why it's there; long-press a song for "Not for me" or "Less of" an artist, undone in Settings. YouTube Music's own feed follows, labelled as such. A fresh install shows a welcome, not fake albums |
| **Listen** | YouTube Music's feed with mood chips and endless scroll, search with filters and suggestions, radio that keeps the queue going, synced lyrics (LRCLIB first, then YouTube Music), a sleep timer that fades out, loudness levelling to −14 LUFS, a 5-band equalizer that runs inside the app so it works even where the phone's own equalizer is locked |
| **Download** | Media3 downloads that survive the app being killed and resume from the last byte; songs you like can download themselves; two copies of the same recording under different video ids share one file |
| **Keep** | Liked songs, playlists you can reorder, albums, artists, history, listening stats by week, month and year; the queue comes back after a restart, paused |
| **Move in** | Restores real Metrolist and ConvX backups, including the sign-in. Backups don't carry music files, so it remembers what the old app had downloaded and offers it back in one tap with the size up front |
| **Take it out** | Copies downloads to `Music/MusicForge` as Ogg/Opus with title, artist, album, cover art and synced lyrics inside the file, plus a `.lrc` next to it, so other players and a PC can use them |
| **Elsewhere** | Android Auto, a home-screen widget that starts playback from cold, "Hey Google, play … on MusicForge", a like button in the notification, ListenBrainz scrobbling with an offline queue |

## Measured

| | | where |
|---|---|---|
| Cold start | 511 ms median (544 ms before the startup profile) | X7b |
| Tap to sound, streaming | about 1.3 s | X7b |
| Scrolling a 6,253-song library | 0.16% late frames, slowest frame 14 ms | X7b |
| Downloaded song in airplane mode, tap to sound | about 0.3 s | emulator |
| Skip to a song read-ahead already fetched | about 0.1 s | emulator |
| Download speed | 3 to 5 MB/s | emulator, home Wi-Fi |
| Release APK | 5.3 MB | |

## Things we found out the hard way

Google's video servers stream a request of 8 MiB or less at full speed and throttle anything above about 10 MiB to roughly 30 kB/s. Metrolist asks for the whole file in one go, which crawls on long tracks. Every byte here, downloads and playback alike, moves in 4 MiB pieces. The measurements are in [`docs/BENCHMARKS.md`](docs/BENCHMARKS.md).

YouTube answers HTTP 400 to any request whose language tag it doesn't recognise. The X7b is set to `en-ZA`, so the first test on a real phone failed on the home screen, then on search, then on every song. The emulator speaks `en-US` and never noticed. The app now sends only languages from YouTube's own list.

Android quietly switches off write-ahead logging for a database you `ATTACH`, so restore streams rows from a separate connection instead of attaching the backup.

On the phone's overdraw view every cover on Home came out red: the window background, the app background, a grey placeholder and the picture, each painted on every frame. Now each pixel gets drawn once or twice, and the player went from four full-screen layers to one.

## Build

You need JDK 21 and the Android SDK (platform 37, build-tools 37).

```sh
./gradlew :app:assembleDebug        # debug build, installs next to the release one (.debug)
./gradlew :app:testDebugUnitTest    # 50 tests: restore, transfers, export tags, equalizer, …
./gradlew :app:assembleRelease      # R8-minified; signed with the local debug key
```

Build outputs go to `/dev/shm/musicforge-build` when that exists. The machine this was built on writes to disk at about 40 MB/s, and a build writes over a gigabyte. Delete the block at the end of `build.gradle.kts` to build in the usual place.

The startup profile comes from a UI Automator journey in `:baselineprofile` (`./gradlew :app:generateReleaseBaselineProfile` with a device or emulator attached).

## Install

```sh
adb install app-release.apk
```

It uses its own package (`dev.musicforge.app`), so it sits next to Metrolist or ConvX without touching them. On MagicOS, allow it to run in the background when it asks; the system kills background apps otherwise.

## Not done yet

- Crossfade. BitChord's version is a 3,117-line system that hands playback between two players, and its own comments say a simple volume ramp never sounded right. On a Snapdragon 680 it means two decoders at once, so it waits for its own phase with measurements on the phone.
- The player still stutters for a frame or two as it opens (about 4% of frames late).
- Signing in through Google inside the app loads and should work, but nobody has finished it with a real account yet. Restoring a backup signs you in too, and that works.

## How it was made

apathane set the goal, the rules and the phone; Claude (Anthropic's model, through Claude Code) wrote the code, ran it on the emulator and then on the X7b over adb, and kept a journal of every decision and measurement. Free models through the Kilo CLI reviewed each phase, and roughly half of their findings turned out wrong, so each one was checked against the code before anything changed. Each commit message explains why the change was made.

## Credits and licence

GPL-3.0, like everything it learned from:

- [Metrolist](https://github.com/MetrolistGroup/Metrolist): the `innertube` module is vendored from it, and the download model follows its design
- [InnerTubeX](https://github.com/MetrolistGroup/innertubex): stream extraction
- [ConvX](https://github.com/cosmictaserdev-creator/Convx): screens, features and the backup format it restores
- [BitChord](https://github.com/kushagrasinghx/BitChord): bounded-range transfers, read-ahead and the monochrome style
- [Inter](https://rsms.me/inter/) (SIL Open Font License, `licenses/Inter-OFL.txt`), standing in for BitChord's SF Pro, which can't be redistributed

Not affiliated with Google or YouTube.
