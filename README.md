# MusicForge

A YouTube Music player for Android. I wanted something that downloads fast, works offline, and doesn't fill Home with stuff I never asked for. So this got built.

It's tested on one cheap phone (an Honor X7b with a Snapdragon 680) because that's the phone I have. If it runs well there, it'll run well on yours.

<p align="center">
  <img src="docs/screenshots/home.jpg" width="200" alt="Home">
  <img src="docs/screenshots/player.jpg" width="200" alt="Player">
  <img src="docs/screenshots/queue.jpg" width="200" alt="Queue">
  <img src="docs/screenshots/artist.jpg" width="200" alt="Artist page">
</p>

## What it does

- **Home is built from what you actually play:** what you played last, what you play around this time of day, what's on repeat, songs you liked and forgot about. Every shelf says why it's there. Long-press anything for "not for me".
- **Downloads are fast.** Google throttles big downloads to about 30 kB/s, so this asks for 4 MB at a time and gets full speed instead (3 to 5 MB/s on home Wi-Fi). Downloads survive the app being killed.
- **Sign in with Google** and your liked songs, playlists, albums and artists come across and stay synced. Searches run as you too, unless you turn that off.
- **Offline first.** Downloaded songs start in about 0.3 s with no signal. Search still finds your own music.
- **Coming from Metrolist or ConvX?** It restores their backups, sign-in included, then offers to re-download what you had.
- **Your files are yours.** Downloads can be copied to `Music/MusicForge` as normal tagged Ogg files with covers and lyrics, so any other player or your PC can use them.
- **Plus:** synced lyrics, radio, a sleep timer, loudness levelling, a built-in equalizer, Android Auto, a widget, ListenBrainz, stats.
- **Small things:**
  - swipe the mini player to skip;
  - swipe songs out of the queue, with undo;
  - paste a YouTube link into search;
  - liquid glass on the nav bar and mini player, kept to those two so a slow phone stays smooth.

## Numbers (on the X7b)

| | |
|---|---|
| Cold start | ~0.5 s |
| Tap to sound, streaming | ~1.3 s |
| Scrolling 6,000+ songs | 0.16% late frames |
| APK | 5.4 MB |

## Things that broke and why

- **YouTube returns error 400 for languages it doesn't know.** My phone is set to `en-ZA`. On the emulator (`en-US`) everything worked. On the phone, nothing did. It took a while to work out why.
- **Requests over ~10 MB crawl.** That's why everything moves in 4 MB pieces. The measurements are in [`docs/BENCHMARKS.md`](docs/BENCHMARKS.md).
- **Old libraries have dead pictures.** About 3% of artist and playlist pictures from a restored library just 404 now. The app re-fetches them when they fail.
- **Overdraw.** Every cover on Home was being painted four times a frame. Fixing that mattered more than any animation.

## Install

Get the APK from [Releases](../../releases). It installs as its own app, next to whatever you use now. On Honor/Huawei/Xiaomi phones, let it run in the background when it asks, or the system kills playback.

## Build it

JDK 21 and the Android SDK (platform 37).

```sh
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleRelease
```

The build writes to `/dev/shm/musicforge-build` because my laptop's disk is slow. Delete the block at the end of `build.gradle.kts` if you don't want that. [`CLAUDE.md`](CLAUDE.md) explains how the app is put together.

## How it's made

I decide what it should be and test it on my phone. Claude (Anthropic's model, through Claude Code) writes the code. Free models review each phase. They're wrong about half the time, so every finding gets checked against the code first. Each phase gets a musical codename: the current one is **Rondo**, version 0.13. The app shows that name on your home screen until it gets a real one.

Not done yet: crossfade, multi-select, and the player still drops a frame or two when it opens.

## Credits

GPL-3.0. Built on other people's open-source work:

- [Metrolist](https://github.com/MetrolistGroup/Metrolist): the YouTube Music API module, the download design, account sync
- [InnerTubeX](https://github.com/MetrolistGroup/innertubex): stream extraction
- [ConvX](https://github.com/cosmictaserdev-creator/Convx): lots of the screens and features, and the backup format
- [Kyant0/backdrop](https://github.com/Kyant0/backdrop): the liquid glass (Apache-2.0)
- [BitChord](https://github.com/kushagrasinghx/BitChord): the chunked transfers and the black-and-white look
- [Inter](https://rsms.me/inter/) for the font, [LRCLIB](https://lrclib.net) for lyrics

Not affiliated with Google or YouTube.
