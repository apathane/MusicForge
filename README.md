<p align="center">
  <img src="docs/brand/social-preview.png" alt="MusicForge: YouTube Music, forged for offline" width="100%">
</p>

<p align="center">
  <a href="https://github.com/apathane/MusicForge/releases"><img src="https://img.shields.io/github/v/release/apathane/MusicForge?color=000000&labelColor=222222&style=flat-square" alt="Latest Release"></a>
  <img src="https://img.shields.io/badge/License-GPL--3.0-blue?style=flat-square" alt="License: GPL-3.0">
  <img src="https://img.shields.io/badge/Android-8.0%2B%20%7C%20minSdk%2026-green?style=flat-square" alt="Android 8.0+ | minSdk 26">
  <img src="https://img.shields.io/badge/Kotlin-2.4-purple?style=flat-square" alt="Kotlin">
  <img src="https://img.shields.io/badge/Compose-1.12-brightgreen?style=flat-square" alt="Jetpack Compose">
  <a href="https://github.com/apathane/MusicForge/actions/workflows/ci.yml"><img src="https://github.com/apathane/MusicForge/actions/workflows/ci.yml/badge.svg?style=flat-square" alt="CI"></a>
</p>

<h1 align="center">YouTube Music, forged for offline</h1>

<p align="center">
  <a href="../../releases/latest">
    <img src="https://img.shields.io/badge/Download%20APK-000000?style=for-the-badge&labelColor=222222&label=%20" alt="Download APK" width="280">
  </a>
</p>

## Screenshots

<p align="center">
  <img src="docs/screenshots/home.jpg" width="240" alt="Home screen — shelves built from your own plays">
  <img src="docs/screenshots/player.jpg" width="240" alt="The player, tinted from the artwork">
  <img src="docs/screenshots/queue.jpg" width="240" alt="Queue — swipe to remove, drag to reorder">
  <img src="docs/screenshots/artist.jpg" width="240" alt="Artist page — related artists and discography">
</p>

## Why it exists

I wanted a YouTube Music player that downloads fast, works offline, and doesn't fill Home with stuff I never asked for. So this got built. It's tested on one cheap phone — an Honor X7b (Snapdragon 680) — because that's the one I have. If it runs well there, it runs well on yours.

Right now MusicForge is in the **Sonata** phase (0.14.0): a redesigned player, two-way history sync, and a name that still isn't real.

## Features

<details>
<summary>Home is built from what you actually play</summary>

What you played last, what you play around this time of day, what's stuck on repeat, songs you liked and forgot about — every shelf says why it's there. Long-press anything for "not for me". On a clean install you get only the welcome; YouTube's feed follows once you sign in.
</details>

<details>
<summary>Downloads run at full speed</summary>

Google throttles any single request over ~10 MB to dial-up speed (≈30 kB/s). MusicForge asks for 4 MB at a time and stays at line speed — 5.6× faster on a 73 MB stream. Downloads survive the app being killed, and re-open a partially-complete transfer in the same chunk.
</details>

<details>
<summary>Sign in with Google</summary>

Liked songs, playlists, albums and artists come across and stay synced. Searches run as you too, unless you turn that off in Settings.
</details>

<details>
<summary>Offline first</summary>

Downloaded songs start in about 0.3 s with no signal. Search still finds your own music. Home still works, built from local play history.
</details>

<details>
<summary>Bring your library with you</summary>

Restores backups from Metrolist and ConvX, sign-in included, then offers to re-download what you had. Settings and the cookie come across too — just point it at a backup file.
</details>

<details>
<summary>Your files are yours</summary>

Downloads copy to `Music/MusicForge` as tagged Ogg files with covers and lyrics, so any other player or your PC can use them. The format on disk is yours to keep.
</details>

<details>
<summary>Everything else</summary>

Synced lyrics (LRCLIB), radio from any song or album, a sleep timer, a 5-band equalizer, loudness levelling to −14 LUFS, Android Auto, a home-screen widget, ListenBrainz scrobbling, and stats.
</details>

<details>
<summary>Small things</summary>

Swipe the mini player to skip. Swipe songs out of the queue, with undo. Paste a YouTube link into search. Liquid glass on the nav bar and mini player — kept to those two so a slow phone stays smooth.
</details>

## Numbers (on the X7b)

| | |
|---|---|
| Cold start | ~0.5 s |
| Tap to sound, streaming | ~1.3 s |
| Scrolling 6,000+ songs | 0.16% late frames |
| APK | 5.4 MB |

## Things that broke and why

- **YouTube returns 400 for languages it doesn't know.** My phone is set to `en-ZA`. On the emulator (`en-US`) everything worked. On the phone, nothing did. `YouTubeSession.youTubeLanguage()` only sends `hl` values from YouTube's own list — any other tag gets HTTP 400 on every request.
- **Requests over ~10 MB crawl.** That's why everything moves in 4 MB pieces. The measurements are in [`docs/BENCHMARKS.md`](docs/BENCHMARKS.md).
- **Old libraries have dead pictures.** About 3% of artist and playlist pictures from a restored library just 404 now. `ArtRepair` re-fetches them when Coil gets the error.
- **Overdraw.** Every cover on Home was being painted four times a frame. Fixed by removing the activity's window background and letting `Artwork` crop past YouTube's letterboxing instead of layering a second pass.
- **Metrolist only writes a play event on transition.** So the last song before a pause or kill never counted. MusicForge writes the event when the threshold is crossed and checkpoints every 30 seconds.

## FAQ

<details>
<summary>Is this safe and legitimate?</summary>

Yes. It's a local Android app that talks to YouTube's own music API through InnerTubeX (GPL-3.0, MetrolistGroup). It doesn't proxy your traffic, store your password, or run servers. Your Google cookie stays on your phone.
</details>

<details>
<summary>Does it need an account?</summary>

No. You can browse, search, and play anonymously. Sign in only for your library, liked songs, playlists, and two-way history sync. You can skip Google entirely and still download and play offline.
</details>

<details>
<summary>Where do downloads go?</summary>

Into the app's private cache, managed by Media3's `DownloadManager` with a persistent index — so they survive a kill. Finished downloads can be exported to `Music/MusicForge` as tagged Ogg files from the Downloads screen.
</details>

<details>
<summary>Why is it called a codename?</summary>

Every phase ships under a musical codename (Sonata, Rondo, Aria…) until the owner picks a real name. It shows on your home screen and becomes the `versionName` suffix. The package id stays `dev.musicforge.app` so your data survives the renames.
</details>

<details>
<summary>Can I restore Metrolist or ConvX backups?</summary>

Yes. Pick a backup file and it imports by column intersection — songs, artists, playlists, liked status, settings, and your sign-in cookie. Downloads we already hold are kept; the rest you can re-fetch in the Downloads tab.
</details>

<details>
<summary>Why isn't it on the Play Store?</summary>

Google doesn't allow YouTube Music clients on the Play Store. APK only, from [Releases](../../releases/latest). On Huawei/Honor and Xiaomi phones, let it run in the background when it asks — the system kills playback otherwise.
</details>

<details>
<summary>Can I export my downloads?</summary>

Yes. Each finished download can be copied to `Music/MusicForge` as an Ogg file with Vorbis comments, a cover, and embedded lyrics. "Save all to Music" does the whole library as a background job with progress and a Cancel action.
</details>

## Roadmap

**Sonata (phase 14, now).** Two-way history sync (plays in MusicForge appear in YouTube Music and vice versa), a redesigned player (full-bleed artwork, codec pill, volume slider, output-device switcher, lyrics and queue as swipeable panes), mood/genre grids in Search, a new Explore tab, and collection pages with multi-select.

<details>
<summary>What Sonata brings (full spec)</summary>

The design direction follows the Sonata spec — every screen from the owner's screenshots, and the build order. Highlights:

- History that syncs: pull remote plays into `remote_history`, merge on Home, push local plays with `registerPlayback`.
- Player: full-bleed artwork, codec pill ("Opus 133 kbps"), Up Next row, volume slider, output-device switcher, lyrics/queue panes, crossfade-ready queue pills.
- Navigation: four tabs (Home, Explore, Library, Search) with a collapsing glass bar that carries the mini player.
- Home: "Top picks for you" carousel + speed dial, mixed with local shelves.
- Explore: featured albums, new releases, moods & genres, charts.
- Library: list-menu with accent-coloured icons, Downloads nested inside.
</details>

**Later.** Crossfade (deferred to a dedicated phase — it needs two decoders on a 680), a real app name + 1.0, and whatever the owner decides after Sonata ships.

## Contributing

Read [CONTRIBUTING.md](CONTRIBUTING.md) and [AGENTS.md](AGENTS.md). The build gate is `scripts/check.sh` (debug build + unit tests + lint, 0 lint errors). Work in your own git worktree and branch; commit small with a `Co-Authored-By:` trailer.

## Credits

GPL-3.0. Built on other people's open-source work:

- [Metrolist](https://github.com/MetrolistGroup/Metrolist): the YouTube Music API module, the download design, account sync
- [InnerTubeX](https://github.com/MetrolistGroup/innertubex): stream extraction
- [ConvX](https://github.com/cosmictaserdev-creator/Convx): lots of the screens and features, and the backup format
- [Kyant0/backdrop](https://github.com/Kyant0/backdrop): the liquid glass (Apache-2.0)
- [BitChord](https://github.com/kushagrasinghx/BitChord): the chunked transfers and the black-and-white look
- [Inter](https://rsms.me/inter/) for the font, [LRCLIB](https://lrclib.net) for lyrics

Not affiliated with Google or YouTube.
