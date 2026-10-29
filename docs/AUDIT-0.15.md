# Feature Audit 0.15 — MusicForge vs Metrolist / ConvX / BitChord

**Audit version:** 0.15  
**Branch:** `toccata/audit`  
**Date:** 2026-10-29  
**Auditor:** Claude (t4) on ultracave  
**Scope:** read-only comparison of MusicForge against Metrolist (foundation), ConvX (advanced feel), and BitChord v1.8.2 (newest/advanced).

Reference copies live at:
- `/home/zanthee/MusicForge/upstream/Metrolist/`
- `/home/zanthee/MusicForge/upstream/Convx/`
- `/home/zanthee/MusicForge/upstream/BitChord-v1.8.2/`

---

## 1. Settings Audit

Settings are grouped by screen section in MusicForge (`ui/screens/SettingsScreen.kt`), by reference-app screen under `ui/screens/settings/*`, and by key in each app's preference store file.

Legend: ✓ present · ✗ missing · — N/A (concept doesn't map)  
Priority: **P0** critical/user-facing · **P1** comfort/quality-of-life · **P2** polish/edge-case

### 1.1 Account

| Feature | Metrolist | ConvX | BitChord | MusicForge | Gap / Priority |
|---|---|---|---|---|---|
| Sign in with Google | ✓ | ✓ | ✓ | ✓ | — |
| Show account name & email | ✓ | ✓ | ✓ | ✓ | — |
| Sync now (manual) | ✓ | ✓ | ✓ | ✓ | — |
| Sync status / last sync time | ✓ | ✓ | ✓ | ✓ | — |
| Pause search history | ✓ | ✓ | ✓ | ✓ | — |
| Pause listen history | ✓ | ✓ | ✓ | ✓ | — |
| Clear search history | ✓ | ✓ | ✓ | ✓ | — |
| Switch account | ✓ | ✓ | ✓ | ✓ | — |
| Sign out | ✓ | ✓ | ✓ | ✓ | — |
| Saved accounts (multi-account quick switch) | — | ✓ `savedAccounts` | — | ✗ | P1 |
| Account sync status detail (per-entity: songs, albums, artists, playlists) | ✓ | ✓ | ✓ | ✓ | — |
| Account channel handle display | ✓ | ✓ | ✓ | ✓ | — |

### 1.2 Backup & Restore

| Feature | Metrolist | ConvX | BitChord | MusicForge | Gap / Priority |
|---|---|---|---|---|---|
| Create backup (settings + library) | ✓ | ✓ | ✓ full pref export | ✓ | — |
| Restore from backup file | ✓ | ✓ | ✓ `importPrefs` | ✓ `BackupImporter` | — |
| Auto-backup (daily snapshots) | ✓ | ✓ | ✓ | ✓ `AutoBackup` | — |
| Snapshot before restore | ✓ | ✓ | ✓ | ✓ | — |
| List available automatic backups | ✓ | ✓ | ✓ | ✓ | — |
| Settings import (cross-app: Metrolist/ConvX → MusicForge) | ✓ | ✓ | ✓ SharedPreferences | ✓ DataStore importable map | — |
| Backup excludes secrets | ✓ | ✓ | ✓ `SECRETS` set | ✓ `importable` map filters them | — |
| Backup excludes device-local prefs | ✓ | — | ✓ `DEVICE_LOCAL` set | ✓ | — |
| Show backup preview (row counts) | ✓ | ✓ | ✓ | ✓ | — |
| Re-download prompt for previously-downloaded songs | — | — | ✓ | ✓ `Redownloads` | — |

### 1.3 General

| Feature | Metrolist | ConvX | BitChord | MusicForge | Gap / Priority |
|---|---|---|---|---|---|
| Start screen (tab choice) | ✓ `defaultOpenTab` | ✓ | ✓ | ✓ `Keys.StartScreen` | — |
| Keep screen on | ✓ `keepScreenOn` | ✓ | ✓ | ✓ | — |
| Haptics | ✓ | ✓ | ✓ | ✓ `forge.hapticsEnabled` | — |
| App language | ✓ `appLanguage` | ✓ | ✓ | ✗ | P2 |
| Content language | ✓ `contentLanguage` | ✓ | ✓ ✓ `contentLanguage` | ✓ | — |
| Content country | ✓ `contentCountry` | ✓ | ✓ ✓ `contentCountry` | ✓ | — |
| Hide explicit | ✓ | ✓ | ✗ | ✗ | P2 |
| Hide video songs | ✓ | ✓ | ✗ | ✗ | P2 |
| Hide YouTube Shorts | ✓ | ✓ | ✗ | ✗ | P2 |
| Show artist description | ✓ | ✓ | ✗ | ✗ | P2 |
| Show artist subscriber count | ✓ | ✓ | ✗ | ✗ | P2 |
| Show monthly listeners | ✓ | ✓ | ✗ | ✗ | P2 |
| Density scale (UI scaling) | ✓ `density_scale_factor` | ✓ | ✗ | ✗ | P2 |
| Force bottom nav bar | ✓ `forceBottomNavBar` | ✓ | ✗ | ✗ | P2 |
| Slim nav bar | ✓ `slimNavBar` | ✓ | ✗ | ✗ | P2 |
| Grid item size | ✓ `gridItemSize` | ✓ | ✗ | ✗ | P2 |
| Slider style | ✓ `sliderStyle` | ✓ | ✗ | ✗ | P2 |
| Squiggly slider | ✓ | ✓ | ✗ | ✗ | P2 |
| Swipe to song (gestures) | ✓ | ✓ | ✗ | ✗ | P2 |
| Swipe to remove song (queue) | ✓ | ✓ | ✗ | ✗ | P2 |
| Use new player design | ✓ | ✓ | ✗ | ✗ | P2 |
| Use new mini player design | ✓ | ✓ | ✗ | ✗ | P2 |
| Hide player thumbnail | ✓ | ✓ | ✗ | ✗ | P2 |
| Crop album art | ✓ | ✓ | ✗ | ✗ | P2 |
| Seek extra seconds | ✓ | ✓ | ✗ | ✗ | P2 |
| Pause on mute | ✓ | ✓ | ✗ | ✗ | P2 |
| Resume on Bluetooth connect | ✓ | ✓ | ✗ | ✗ | P2 |
| Check for updates | ✓ | ✓ | ✓ | ✓ | — |
| Dismissed update notifications | ✓ | ✓ | ✓ | ✗ | P2 |
| Update notifications toggle | ✓ | ✓ | ✓ | ✗ | P2 |
| Enable high refresh rate | ✓ | ✓ | ✗ | ✗ | P2 |
| Enable landscape scaling | ✓ | — | ✗ | ✗ | P2 |
| Dynamic theme | ✓ | ✓ | ✗ | ✗ | P2 |
| Selected theme color | ✓ | ✓ | ✗ | ✗ | P2 |
| App icon selection | ✓ | ✓ `appIcon` | ✗ | ✗ | P2 |
| Enable dynamic icon | ✓ | ✓ | ✗ | ✗ | P2 |

### 1.4 Playback

| Feature | Metrolist | ConvX | BitChord | MusicForge | Gap / Priority |
|---|---|---|---|---|---|
| Audio quality (Auto/High/Low) | ✓ `audioQuality` | ✓ | ✓ wi-fi + cellular | ✓ `audioQuality` | — |
| Enable audio offload | ✓ | ✓ | ✓ `enableOffload` | ✗ | P1 |
| Audio track playback params | ✓ | ✓ | ✓ | ✗ | P1 |
| Playback speed | ✓ | ✓ | ✓ `playback_speed` | ✗ | P1 |
| Skip silence | ✓ `skipSilence` | ✓ | ✓ | ✓ | — |
| Skip silence (instant) | ✓ `skipSilenceInstant` | ✓ | ✗ | ✗ | P2 |
| Loudness normalization / audio normalization | ✓ `audioNormalization` | ✓ | ✓ `loudness_normalization` | ✓ `audioNormalization` | — |
| Loudness level (target LUFS) | ✓ AGGRESSIVE/LOUD/BALANCED/QUIET | — | ✓ | ✗ | P1 |
| Persistent queue | ✓ `persistentQueue` | ✓ | ✓ | ✓ | — |
| Autoplay (radio when queue runs out) | ✓ `autoplay` | ✓ | ✓ | ✓ | — |
| Queue locked (disable swipe/drag in queue) | ✓ | ✓ `queueEditLock` | ✗ | ✓ `forge.queueLocked` | — |
| Prevent duplicate tracks in queue | ✓ | ✓ | ✗ | ✗ | P2 |
| Auto-load more | ✓ | ✓ | ✗ | ✗ | P2 |
| Auto-skip next on error | ✓ | ✓ | ✗ | ✗ | P2 |
| Stop music on task clear | ✓ | ✓ | ✓ | ✗ | P1 |
| Shuffle playlist first | ✓ | ✓ | ✗ | ✗ | P2 |
| Remember shuffle and repeat | ✓ | ✓ | ✗ | ✗ | P2 |
| Shuffle mode | ✓ `shuffleMode` | ✓ | ✓ | ✗ | P1 |
| Repeat mode | ✓ `repeatMode` | ✓ | ✓ | ✗ | P1 |
| Crossfade enabled | ✓ `crossfadeEnabled` | ✓ | ✓ `crossfade_seconds` | ✓ | — |
| Crossfade duration | ✓ `crossfadeDuration` | ✓ | ✓ | ✓ `crossfadeDuration` | — |
| Crossfade gapless | ✓ `crossfadeGapless` | ✓ | ✓ | ✓ | — |
| Smart/automix crossfade | — | ✓ `smartFadeEnabled` | ✓ `smart_fade_enabled` | ✗ | P1 |
| Volume normalization | ✓ `audioTrackPlaybackParams` | ✓ | ✓ | ✗ | P1 |
| Bit-perfect output | — | ✓ `bitPerfect` | ✓ USB/DAP/Android modes | ✗ | P2 |
| Spatial audio (stereo widening) | — | ✓ `spatialAudio` | ✓ | ✗ | P1 |
| Vocal suppression | — | ✓ `vocalSuppression` | ✗ | ✗ | P2 |
| Dolby Atmos toggle | — | ✓ `dolbyAtmos` | ✓ | ✗ | P1 |
| Direct source streaming | — | — | ✓ `direct_source_stream` | ✗ | P2 |
| Varispeed (independent speed/pitch) | ✓ | ✓ | ✗ | ✗ | P2 |
| Double-tap cover to seek | — | — | — | ✓ `forge.doubleTapSeek` | — |
| Swipe mini player to skip | — | — | — | ✓ `forge.swipeMiniToSkip` | — |
| Player volume control | — | — | ✓ `player_volume` | ✗ | P2 |
| Sleep timer | — | ✓ | ✓ `sleep_timer_*` | ✗ | P0 |
| Show nerd stats (codec/bitrate/sample rate) | ✓ | ✓ | ✓ `show_nerd_stats` | ✗ | P1 |
| Hide volume bar in player | — | — | ✓ | ✗ | P2 |
| Hide status bar in player | — | — | ✓ | ✗ | P1 |
| Hide "Playing from" status | — | — | ✓ | ✗ | P2 |
| Show queue history | — | — | ✓ `show_queue_history` | ✗ | P2 |
| Swipe to play next (queue) | — | — | ✓ | ✗ | P2 |
| High performance mode (refresh rate) | ✓ | ✓ | ✓ | ✗ | P2 |
| Reduce animation | — | — | ✓ | ✗ | P2 |
| Player layout order (customize) | — | ✓ `player_layout_order` | ✗ | ✗ | P2 |
| Player layout hidden slots | — | ✓ | ✗ | ✗ | P2 |
| Player buttons style | ✓ | ✓ `player_buttons_style` | ✗ | ✗ | P2 |
| Player background style | ✓ | ✓ `playerBackgroundStyle` | ✗ | ✗ | P2 |
| Player artwork style (card/vinyl/clover) | — | ✓ `playerArtworkStyle` | ✗ | ✗ | P2 |
| Show up next | — | ✓ `showUpNext` | ✗ | ✗ | P2 |
| Compact player in tab view | — | ✓ | ✗ | ✗ | P2 |
| Show video/audio switch | — | ✓ | ✗ | ✗ | P2 |
| Hide home favorite icon | — | ✓ | ✗ | ✗ | P2 |
| Thumbnail corner radius | — | ✓ | ✗ | ✗ | P2 |
| Auto DJ mixing | — | ✓ `autoDjMixingEnabled` | ✗ | ✗ | P2 |
| Creative transitions | — | ✓ `creativeTransitionsEnabled` | ✗ | ✗ | P2 |
| DJ reverb/delay type | — | ✓ | ✗ | ✗ | P2 |
| Stem quality | — | ✓ `stemQuality` | ✗ | ✗ | P2 |
| Demucs model URL | — | ✓ | ✗ | ✗ | P2 |
| Song transitions screen | — | ✓ | ✗ | ✗ | P2 |

### 1.5 Equalizer / Audio Processing

| Feature | Metrolist | ConvX | BitChord | MusicForge | Gap / Priority |
|---|---|---|---|---|---|
| EQ enabled (toggle) | ✓ `enableEq` | ✓ | ✓ `equalizer_enabled` | ✓ `forge.eqEnabled` | — |
| EQ presets | ✓ | ✓ axion | ✓ dynamic + manual | ✓ | — |
| EQ bands (manual sliders) | ✓ | ✓ | ✓ 7-band | ✓ 5-band | P1 |
| EQ tone pad (warm/bright, scoop/mid) | — | — | ✓ | ✗ | P1 |
| EQ band frequencies | ✓ | ✓ | ✓ | ✓ | — |
| EQ from system | ✓ | ✓ | ✗ | ✗ | P2 |
| System equalizer row | ✓ | ✓ | ✓ | ✗ | P1 |
| Playback speed | ✓ | ✓ | ✓ | ✗ | P1 |
| Output PCM mode (16-bit / 32-bit float) | — | — | ✓ | ✗ | P1 |
| Prefer USB DAC | — | — | ✓ | ✗ | P2 |

### 1.6 Lyrics

| Feature | Metrolist | ConvX | BitChord | MusicForge | Gap / Priority |
|---|---|---|---|---|---|
| Show lyrics in player | ✓ `showLyrics` | ✓ | ✓ | ✓ `showLyrics` | — |
| Lyrics text size | ✓ `lyricsTextSize` | ✓ | ✓ | ✓ `lyricsTextSize` | — |
| Synced lyrics (word-level) | ✓ | ✓ | ✓ `synced_lyrics` | ✗ | P1 |
| Line-synced lyrics | ✓ | ✓ | ✓ | ✗ | P1 |
| Lyrics source providers | ✓ `enableZemer`/`enableKugou`/`enableLrclib`/`enableBetterLyrics`/`enablePaxsenix` | ✓ + many more | ✓ configurable set | ✗ | P1 |
| Lyrics source order | — | ✓ `lyricsSourceOrder` | ✓ | ✗ | P2 |
| Prioritize syllable sync | — | — | ✓ `prioritize_syllable_sync` | ✗ | P2 |
| Paxsenix API key | ✓ | ✓ | ✓ | ✗ | P2 |
| Lyrics romanization (Japanese) | — | ✓ | ✗ | ✗ | P2 |
| Lyrics romanization (Korean/Chinese/etc) | — | ✓ | ✗ | ✗ | P2 |
| Lyrics romanize as main | ✓ `lyricsRomanizeAsMain` | ✓ | ✓ | ✗ | P2 |
| Translate lyrics | — | ✓ `translateLyrics` | ✓ | ✗ | P2 |
| AI translation (OpenRouter/Deepl) | ✓ | ✓ | ✓ | ✗ | P2 |
| Lyrics animation style | — | ✓ `lyricsAnimationStyle` (11 options) | — | ✗ | P1 |
| Lyrics click to toggle UI | ✓ | ✓ | ✗ | ✗ | P2 |
| Lyrics scroll | ✓ | ✓ | ✗ | ✗ | P2 |
| Lyrics glow effect | — | ✓ | — | ✗ | P2 |
| Lyrics line spacing | — | ✓ | — | ✗ | P2 |
| Hide status bar on fullscreen lyrics | ✓ | ✓ | ✗ | ✗ | P2 |
| Lyrics custom font | — | ✓ | ✗ | ✗ | P2 |
| One-tap fullscreen lyrics | — | ✓ | ✗ | ✗ | P2 |
| Fullscreen lyrics collapse top | — | ✓ | ✗ | ✗ | P2 |
| Respect agent positioning | — | ✓ | ✗ | ✗ | P2 |
| Show interval indicator | — | ✓ | ✗ | ✗ | P2 |
| Apple Music lyrics blur | — | ✓ | ✗ | ✗ | P2 |
| Standard lyrics blur | — | ✓ | ✗ | ✗ | P2 |
| Lyrics offset (manual ms) | ✓ | ✓ | ✓ `lyrics_offset_ms` | ✗ | P1 |

### 1.7 Network / Proxy

| Feature | Metrolist | ConvX | BitChord | MusicForge | Gap / Priority |
|---|---|---|---|---|---|
| Proxy enabled | ✓ `proxyEnabled` | ✓ | ✓ | ✓ | — |
| Proxy type (HTTP/SOCKS) | ✓ `proxyType` | ✓ | ✓ | ✓ | — |
| Proxy URL | ✓ `proxyUrl` | ✓ | ✓ | ✓ | — |
| Proxy username | ✓ `proxyUsername` | ✓ | ✓ | ✓ | — |
| Proxy password | ✓ `proxyPassword` | ✓ | ✓ | ✓ | — |
| Data saver (cellular) | ✓ | ✓ `dataSaverEnabled` | ✗ | ✗ | P2 |
| IP version | — | ✓ `ipVersion` | ✗ | ✗ | P2 |
| Canvas over cellular | — | — | ✓ `canvas_over_cellular` | ✗ | P2 |
| Animated canvas | ✓ | ✓ | ✓ | ✗ | P1 |
| Canvas source (Spotify/Apple/ConvX/Tidal) | — | ✓ | — | ✗ | P2 |

### 1.8 Storage / Cache

| Feature | Metrolist | ConvX | BitChord | MusicForge | Gap / Priority |
|---|---|---|---|---|---|
| Max song cache size | ✓ `maxSongCacheSize` | ✓ | ✓ `audio_cache_limit_bytes` | ✓ `maxSongCacheSize` | — |
| Max image cache size | ✓ `maxImageCacheSize` | ✓ | ✓ | ✓ `maxImageCacheSize` | — |
| Enable song cache | ✓ | ✓ | ✓ `enableSongCache` | ✗ | P2 |
| Clear image cache | ✓ | ✓ | ✓ | ✓ | — |
| Clear song cache | ✓ | ✓ | ✓ | ✓ | — |
| Song cache (show folder in Library) | ✓ `showCacheFolder` | ✓ | ✓ | ✗ | P2 |
| Show cached playlist in Library | ✓ `showCachedPlaylist` | ✓ | ✓ | ✗ | P2 |
| Save downloads to Music folder | ✓ `saveDownloadsToMusicFolder` | ✓ | ✓ `export_downloads` | ✓ `forge.exportToMusic` | — |
| Import local songs to library | ✓ | ✓ `localOnlyMode` | ✓ | ✗ | P1 |
| Local music folder selection (SAF) | ✓ | ✓ `localMusicFolderUri` | ✓ | ✗ | P1 |
| Local music sort | — | ✓ | ✓ | ✗ | P1 |
| Downloaded music sort | — | ✓ | ✓ | ✗ | P1 |
| Local music view type (list/grid) | — | ✓ | ✓ | ✗ | P2 |
| Filter non-music audio | — | ✓ | ✓ `filter_non_music_audio` | ✗ | P2 |
| Local excluded folders | — | ✓ `localExcludedFolders` | ✗ | ✗ | P2 |
| Filter non-music from local | — | ✓ | ✓ | ✗ | P2 |
| Library sort mode | — | ✓ | ✓ | ✗ | P2 |
| Detail song sort (per-album/per-playlist) | — | ✓ | ✓ | ✗ | P2 |

### 1.9 Scrobbling

| Feature | Metrolist | ConvX | BitChord | MusicForge | Gap / Priority |
|---|---|---|---|---|---|
| Last.fm scrobbling | ✓ `lastfmScrobblingEnable` | ✓ | ✓ | ✗ | P0 |
| Last.fm username | ✓ | ✓ | ✓ | ✗ | P0 |
| Last.fm session key | ✓ `lastfmSession` | ✓ | ✓ | ✗ | P0 |
| Last.fm now playing | ✓ `lastfmUseNowPlaying` | ✓ | ✓ | ✗ | P2 |
| Last.fm send likes | ✓ `lastfmUseSendLikes` | ✓ | ✓ | ✗ | P2 |
| Last.fm primary artist only | — | ✓ | ✓ | ✗ | P2 |
| Scrobble min duration | ✓ `scrobbleMinSongDuration` | ✓ | ✓ | ✗ | P2 |
| Scrobble delay percent | ✓ `scrobbleDelayPercent` | ✓ | ✓ | ✗ | P2 |
| Scrobble delay seconds | ✓ `scrobbleDelaySeconds` | ✓ | ✓ | ✗ | P2 |
| ListenBrainz enabled | ✓ `listenbrainz_enabled` | ✓ | ✓ | ✓ ✓ `listenbrainz_enabled` | — |
| ListenBrainz token | ✓ `listenbrainz_token` | ✓ | ✓ | ✓ ✓ `listenbrainz_token` | — |
| ListenBrainz user | — | ✓ | ✓ | ✓ `forge.listenBrainzUser` | — |
| ListenBrainz primary artist only | — | ✓ | ✓ | ✗ | P2 |
| Spotify scrobbling | — | ✓ `spotifySpdcToken` | ✓ | ✗ | P2 |
| Spotty/Spotify scrobble | — | ✓ | ✓ | ✗ | P2 |
| Scrobble to Spotify | — | ✓ | ✓ | ✗ | P2 |

### 1.10 Appearance

| Feature | Metrolist | ConvX | BitChord | MusicForge | Gap / Priority |
|---|---|---|---|---|---|
| Dark mode (Auto/Light/Dark) | ✓ `darkMode` | ✓ | ✓ | ✓ ✓ | — |
| Pure black | ✓ `pureBlack` | ✓ | ✓ | ✓ | — |
| Pure black mini player | ✓ `pureBlackMiniPlayer` | ✓ | ✓ | ✗ | P2 |
| Glass tier (Solid/Artwork/Live) | ✓ `forge.glassTier` | ✓ | ✓ `glass_effect_*` | ✓ ✓ | — |
| Mini player background style | ✓ | ✓ | ✓ | ✗ | P2 |
| Mini player outline (ConvX) | ✓ | ✓ | ✗ | ✗ | P2 |
| Use Apple Music player style | — | ✓ `useAppleMusicPlayer` | ✗ | ✗ | P2 |
| Show player thumbnail shadow | — | ✓ | ✓ | ✗ | P2 |
| Player thumbnail shadow elevation | — | ✓ | ✓ | ✗ | P2 |
| Reduce dynamic blur | ✓ `reduceDynamicBlur` | ✓ | ✓ | ✗ | P1 |
| Progressive blur | — | ✓ | ✓ | ✗ | P2 |
| Liquid glass (Android 12+) | — | ✓ | ✓ `liquid_glass` | ✗ | P1 |
| Liquid glass adaptive contrast | — | ✓ | ✓ | ✗ | P1 |
| Liquid glass surface tint | — | ✓ | ✓ | ✗ | P1 |
| Liquid glass surface opacity | — | ✓ | ✓ | ✗ | P1 |
| Liquid glass vibrancy | — | ✓ | ✓ | ✗ | P1 |
| Liquid glass blur radius | — | ✓ | ✓ | ✗ | P1 |
| Liquid glass lens height | — | ✓ | ✓ | ✗ | P1 |
| Liquid glass lens amount | — | ✓ | ✓ | ✗ | P1 |
| Liquid glass chromatic aberration | — | ✓ | ✓ | ✗ | P1 |
| Liquid glass depth effect | — | ✓ | ✓ | ✗ | P1 |
| Liquid glass on mini player | — | ✓ | ✓ | ✗ | P1 |
| Liquid glass on nav bar | — | ✓ | ✓ | ✗ | P1 |
| Liquid glass on side panel | — | ✓ | ✓ | ✗ | P1 |
| Menu backdrop resolution | — | ✓ | ✓ | ✗ | P2 |
| Glass effect settings screen | — | ✓ | ✓ | ✗ | P2 |
| Custom app background color | — | ✓ | ✓ | ✗ | P2 |
| Custom app text color | — | ✓ | ✓ | ✗ | P2 |
| Home background enable | — | ✓ | ✓ | ✗ | P2 |
| Home background image path | — | ✓ | ✓ | ✗ | P2 |
| Home background blur | — | ✓ | ✓ | ✗ | P2 |
| Home background dim | — | ✓ | ✓ | ✗ | P2 |
| Home background animate | — | ✓ | ✓ | ✗ | P2 |
| Home background quality | — | ✓ | ✓ | ✗ | P2 |
| Home background is video | — | ✓ | ✓ | ✗ | P2 |
| Selected font | — | ✓ | ✓ | ✗ | P2 |
| Custom font enabled | — | ✓ | ✓ | ✗ | P2 |
| Custom font path | — | ✓ | ✓ | ✗ | P2 |
| Custom font artist only | — | ✓ | ✓ | ✗ | P2 |
| iOS overscroll effect | — | ✓ | ✓ | ✗ | P2 |
| App icons / custom app icon | ✓ | ✓ | ✓ | ✗ | P1 |
| Show liked playlist | ✓ | ✓ | ✓ | ✗ | P2 |
| Show downloaded playlist | ✓ | ✓ | ✓ | ✗ | P2 |
| Show top playlist | ✓ | ✓ | ✓ | ✗ | P2 |
| Show cached playlist | ✓ | ✓ | ✓ | ✗ | P2 |
| Show uploaded playlist | ✓ | ✓ | ✓ | ✗ | P2 |
| Show local playlist | ✓ | ✓ | ✓ | ✗ | P2 |
| Show audio quality badge | — | ✓ | ✓ | ✗ | P2 |
| Show comment button | — | ✓ | ✓ | ✗ | P2 |
| Show history button | — | ✓ | ✓ | ✗ | P2 |
| Show stats button | — | ✓ | ✓ | ✗ | P2 |
| Mini player waveform | — | ✓ | ✓ | ✗ | P2 |
| Library icons only | — | ✓ | ✓ | ✗ | P2 |
| Brand font enabled | — | ✓ | ✓ | ✗ | P2 |
| Hide top bar (edge-to-edge canvas) | ✓ `forge.hideTopBar` | ✓ `hideStatusBarInFullscreen` | ✓ | ✓ ✓ | — |
| Glass tier | ✓ | ✓ | ✓ | ✓ | — |
| Theme screen | ✓ | ✓ | ✓ | ✗ | P2 |
| Player design sheet | — | ✓ | ✓ | ✗ | P2 |
| Player theme screen | — | ✓ | ✓ | ✗ | P2 |
| App icon screen | — | ✓ | ✓ | ✗ | P2 |
| Glass effect settings | — | ✓ | ✓ | ✗ | P2 |
| Home background settings | — | ✓ | ✓ | ✗ | P2 |
| Lyrics font settings | — | ✓ | ✓ | ✗ | P2 |
| Home feed order screen | — | ✓ `homeSectionOrder` | ✓ | ✗ | P1 |
| Hide/reorder Home sections | — | ✓ `homeSectionHidden` | ✓ | ✓ `forge.homeOrder` / `forge.homeHidden` | — |
| Home hero card | — | ✓ `homeHeroCardEnabled` | ✓ | ✗ | P2 |
| Home card corner radius | — | ✓ | ✓ | ✗ | P2 |
| Home hero card height | — | ✓ | ✓ | ✗ | P2 |
| Speed dial columns | — | ✓ | ✓ | ✗ | P2 |
| Grid columns override | — | ✓ | ✓ | ✗ | P2 |
| Grid spacing | — | ✓ | ✓ | ✗ | P2 |
| Grid card height override | — | ✓ | ✓ | ✗ | P2 |
| Tab view compact player | — | ✓ | ✓ | ✗ | P2 |
| Force tablet layout | — | ✓ | ✓ | ✗ | P2 |
| Sidebar collapsed | — | ✓ | ✓ | ✗ | P2 |
| Blur style (single-pass/haze) | — | ✓ | ✓ | ✗ | P2 |
| Home background enable (video) | — | ✓ | ✓ | ✗ | P2 |
| Show home Fab | — | ✓ | ✓ | ✗ | P2 |
| Home mood filters | — | ✓ | ✓ | ✗ | P2 |
| Home grid columns | — | ✓ | ✓ | ✗ | P2 |
| Randomize home order | ✓ | ✓ | ✓ | ✗ | P2 |

### 1.11 Integrations (Discord, Listen Together, etc.)

| Feature | Metrolist | ConvX | BitChord | MusicForge | Gap / Priority |
|---|---|---|---|---|---|
| Discord RPC enabled | ✓ `discordRPCEnable` | ✓ | ✓ | ✗ | P1 |
| Discord username/name/avatar | ✓ | ✓ | ✓ | ✗ | P1 |
| Discord use details | ✓ | ✓ | ✓ | ✗ | P2 |
| Discord show audio quality | ✓ | ✓ | ✓ | ✗ | P2 |
| Discord advanced mode | ✓ | ✓ | ✓ | ✗ | P2 |
| Discord status | ✓ | ✓ | ✓ | ✗ | P2 |
| Discord activity type/name | ✓ | ✓ | ✓ | ✗ | P2 |
| Discord custom buttons | ✓ | ✓ | ✓ | ✗ | P2 |
| Discord info dismissed | ✓ | ✓ | ✓ | ✗ | P2 |
| Listen Together enabled | ✓ | ✓ | ✓ | ✗ | P2 |
| Listen Together server URL | ✓ | ✓ | ✓ | ✗ | P2 |
| Listen Together username | ✓ | ✓ | ✓ | ✗ | P2 |
| Listen Together auto approval | ✓ | ✓ | ✓ | ✗ | P2 |
| Listen Together sync volume | ✓ | ✓ | ✓ | ✗ | P2 |
| Listen Together blocked users | ✓ | ✓ | ✓ | ✗ | P2 |
| Listen Together in top bar | ✓ | ✓ | ✓ | ✗ | P2 |
| Google Cast | ✓ | ✓ | ✓ | ✗ | P1 |
| Apple Music integration | — | ✓ | ✓ | ✗ | P2 |
| Spotify source/scrobbling | — | ✓ | ✓ | ✗ | P2 |
| JioSaavn streaming | — | ✓ `enableSaavnStreaming` | ✗ | ✗ | P2 |
| TIDAL streaming | — | ✓ `enableTidalStreaming` | ✗ | ✗ | P2 |
| SimpMusic migration | ✓ | ✓ | ✗ | ✗ | P2 |
| Video thumbnail migration | ✓ | ✓ | ✗ | ✗ | P2 |

### 1.12 Album / Artist / Playlist Views

| Feature | Metrolist | ConvX | BitChord | MusicForge | Gap / Priority |
|---|---|---|---|---|---|
| Album view type (list/grid) | ✓ `albumViewType` | ✓ | ✓ | ✗ | P2 |
| Artist view type | ✓ `artistViewType` | ✓ | ✓ | ✗ | P2 |
| Playlist view type | ✓ `playlistViewType` | ✓ | ✓ | ✗ | P2 |
| Song sort type | ✓ | ✓ | ✓ | ✗ | P1 |
| Song sort descending | ✓ | ✓ | ✓ | ✗ | P1 |
| Playlist song sort | ✓ | ✓ | ✓ | ✗ | P2 |
| Artist song sort | ✓ | ✓ | ✓ | ✗ | P2 |
| Album sort type | ✓ | ✓ | ✓ | ✗ | P2 |
| Playlist sort type | ✓ | ✓ | ✓ | ✗ | P2 |
| Add to playlist position | ✓ | ✓ | ✓ | ✗ | P2 |
| Mix sort type | ✓ | ✓ | ✓ | ✗ | P2 |
| Song filter | ✓ | ✓ | ✓ | ✗ | P2 |
| Artist filter | ✓ | ✓ | ✓ | ✗ | P2 |
| Album filter | ✓ | ✓ | ✓ | ✗ | P2 |
| Podcast filter | ✓ | — | ✗ | ✗ | P2 |
| Chip sort type | ✓ | ✓ | ✓ | ✗ | P2 |
| Playlist edit lock | ✓ | ✓ | ✓ | ✗ | P2 |
| Queue edit lock | ✓ | ✓ | ✓ | ✗ | P2 |
| Show most stats playlists | ✓ | ✓ | ✓ | ✗ | P2 |
| Album canvas enabled | ✓ | ✓ | ✓ | ✗ | P2 |
| Show wrapped card | ✓ | ✓ | ✓ | ✗ | P2 |
| Wrapped seen | ✓ | ✓ | ✓ | ✗ | P2 |
| Last seen version | ✓ | ✓ | ✓ | ✗ | P2 |

### 1.13 Content / Metadata Sources

| Feature | Metrolist | ConvX | BitChord | MusicForge | Gap / Priority |
|---|---|---|---|---|---|
| Zemer lyrics provider | ✓ `enableZemer` | ✓ | ✗ | ✗ | P2 |
| Kugou lyrics | ✓ `enableKugou` | ✓ | ✓ | ✗ | P2 |
| LrcLib lyrics | ✓ `enableLrcLib` | ✓ | ✓ | ✗ | P2 |
| Better Lyrics | ✓ `enableBetterLyrics` | ✓ | ✓ | ✗ | P2 |
| Paxsenix lyrics | ✓ `enablePaxsenix` | ✓ | ✓ | ✗ | P2 |
| Lyrics Plus | ✓ | — | ✗ | ✗ | P2 |
| Musixmatch | — | ✓ `enableMusixmatch` | ✓ | ✗ | P2 |
| Genius | — | ✓ | ✓ | ✗ | P2 |
| SimpMusic | — | ✓ `enableSimpMusic` | ✓ | ✗ | P2 |
| YouLyPlus | — | ✓ `enableYouLyPlus` | ✓ | ✗ | P2 |
| Unison | — | ✓ `enableUnison` | ✓ | ✗ | P2 |
| Preferred lyrics provider | ✓ `lyricsProvider` | ✓ | ✓ | ✗ | P2 |
| Lyrics provider order | — | ✓ `lyricsProviderOrder` | ✓ | ✗ | P2 |
| Suggestion region | — | ✓ `suggestionRegion` | ✓ | ✗ | P2 |
| YTM sync | ✓ `ytmSync` | ✓ | ✓ | ✗ | P2 |
| Android Auto YouTube playlists | ✓ | ✓ | ✓ | ✗ | P2 |
| Android Auto sections order | ✓ | ✓ | ✓ | ✗ | P2 |
| Android Auto target playlist | ✓ | ✓ | ✓ | ✗ | P2 |
| Android Auto search local limit | ✓ | ✓ | ✓ | ✗ | P2 |

### 1.14 Alarms / Sleep Timer

| Feature | Metrolist | ConvX | BitChord | MusicForge | Gap / Priority |
|---|---|---|---|---|---|
| Sleep timer | ✓ `sleepTimerEnabled` | ✓ | ✓ | ✗ | P0 |
| Sleep timer presets | ✓ | ✓ | ✓ | ✗ | P0 |
| Sleep timer custom | ✓ | ✓ | ✓ | ✗ | P1 |
| Sleep timer fade out | ✓ | ✓ | ✓ | ✗ | P1 |
| Sleep timer stop after current | ✓ | ✓ | ✓ | ✗ | P1 |
| Alarm (music wake-up) | ✓ `alarmEnabled` | ✓ | ✓ | ✗ | P1 |
| Alarm playlist | ✓ | ✓ | ✓ | ✗ | P2 |
| Alarm time | ✓ | ✓ | ✓ | ✗ | P2 |
| Alarm shuffle | ✓ | ✓ | ✓ | ✗ | P2 |

### 1.15 About / System

| Feature | Metrolist | ConvX | BitChord | MusicForge | Gap / Priority |
|---|---|---|---|---|---|
| Changelog screen | ✓ | ✓ | ✗ | ✓ `onAbout` → AboutScreen | — |
| Crash logs settings | ✓ | ✓ | ✗ | ✗ | P2 |
| App version / build | ✓ | ✓ | ✓ | ✓ | — |
| Developer mode | ✓ `developerMode` | ✓ | ✓ | ✗ | P2 |
| Settings popup (global) | ✓ `enableSettingsPopup` | ✓ | — | ✗ | P2 |
| Ringtone settings | ✓ | ✓ | ✓ | ✗ | P2 |
| Searchable settings | — | ✓ | ✓ | ✗ | P2 |
| Donate / support | ✓ `donationPrompt` | ✓ | ✓ | ✗ | P2 |

---

## 2. Screens & Flows Audit

### 2.1 MusicForge Screens (`ui/` packages)

| Screen / Flow | Present | Notes |
|---|---|---|
| Home (feed + recommendations) | ✓ `ui/home/` | MVI; HomeComposer; recommendation_feedback |
| Library (songs, albums, artists, playlists, podcasts) | ✓ `ui/library/` | |
| Search (online + local) | ✓ `ui/search/` | YouTube link parsing; voice search |
| Player (full-screen sheet) | ✓ `ui/player/` | Media3 MediaLibraryService; notification; lock screen |
| Mini player | ✓ `ui/player/` | overlay sheet; swipe-to-skip |
| Queue | ✓ `ui/queue/` | persistent via QueueStore; swipe-to-remove |
| Equalizer | ✓ `ui/equalizer/` | 5-band; on any phone |
| Stats | ✓ `ui/stats/` | play counts |
| Taste (Home preferences) | ✓ `ui/taste/` | Home signals |
| Customise Home | ✓ `ui/home/customise/` | reorder/hide shelves |
| Account / Sign in | ✓ `ui/account/` | WebView Google login |
| About | ✓ `ui/about/` | credits, licenses, version |
| Backup & Restore | ✓ `ui/backup/` | BackupImporter/Exporter |
| Downloads tab | ✓ `download/` | persistent index; reconcile |
| Redownloads (after restore) | ✓ `Redownloads` | offers previously-downloaded songs |
| Lyrics in player | ✓ | text size adjustable; synced when available |
| Android Auto | ✓ `AndroidAuto` / BrowseTree | browse tree, voice search |
| Widget | ✓ `widget/` | short-lived controller |
| History | ✓ `ui/history/` | play-time checkpoints every 30 s |
| Sleep timer | ✗ | Missing entirely |
| Alarm | ✗ | Missing |
| Local music | ✗ | No local file library |
| Canvas / visualiser | ✗ | No animated canvas behind player |
| Discord RPC | ✗ | Missing |
| Listen Together | ✗ | Missing |
| Google Cast | ✗ | Missing |
| Stats / Wrapped | ✗ | No yearly stats summary |
| Radio | ✓ | `Start radio` in song menu |
| Song info | ✗ | No "Song Info" screen in menu |

### 2.2 Metrolist Screens

| Screen / Flow | Present | Notes |
|---|---|---|
| Home | ✓ `HomeScreen.kt` | |
| Library | ✓ `library/LibraryScreen.kt` + sub-screens | songs, albums, artists, playlists, podcasts |
| Search | ✓ `search/SearchScreen.kt` | local + online |
| Player | ✓ | Media3 |
| Mini player | ✓ | |
| Queue | ✓ | |
| Equalizer | ✓ `equalizer/EqScreen.kt` | axion EQ |
| History | ✓ `HistoryScreen.kt` | |
| Stats | ✓ `StatsScreen.kt` | |
| Wrapped | ✓ `wrapped/WrappedScreen.kt` | yearly summary |
| Sleep timer | ✓ | alarm + sleep timer in settings |
| Alarm | ✓ `AlarmSettings.kt` | |
| Local music | ✓ | |
| Lyrics screen | ✓ | |
| Android Auto | ✓ | |
| Settings hub | ✓ `settings/SettingsScreen.kt` | ~30 sub-screens |
| Account | ✓ `AccountSettings.kt` | |
| Backup & Restore | ✓ `BackupAndRestore.kt` | |
| About | ✓ `AboutScreen.kt` | |
| Changelog | ✓ `ChangelogScreen.kt` | |
| Discord RPC | ✓ `integrations/DiscordSettings.kt` | |
| Listen Together | ✓ `ListenTogetherScreen.kt` | |
| Last.fm | ✓ `integrations/LastFMSettings.kt` | |
| ListenBrainz | ✓ `ListenBrainzManager.kt` | |
| Google Cast | ✓ | |
| Update checker | ✓ `UpdaterSettings.kt` | |
| Theme screen | ✓ `ThemeScreen.kt` | |
| Appearance | ✓ `AppearanceSettings.kt` | |
| Player design | ✓ `PlayerAdvancedSettings.kt` | |
| JioSaavn | ✓ `JioSettings.kt` | |
| Spotify import | ✓ `SpotifyScreen.kt` | |
| Apple Music | ✓ `AppleMusicPlaylistImport.kt` | |
| Crash logs | ✓ `CrashLogsSettingsScreen.kt` | |
| Romanization | ✓ `RomanizationSettings.kt` | |
| AI settings | ✓ `AiSettings.kt` | |
| DIY player layout | ✓ `diy/` | |
| Song transitions | ✓ `SongTransitionsScreen.kt` | |
| Home background | ✓ `HomeBackgroundSettings.kt` | |
| Lyrics font | ✓ `LyricsFontSettings.kt` | |
| Glyphs (custom icons) | ✓ | |
| Scan music | ✓ `ScanMusicScreen.kt` | |
| Local folders | ✓ `LocalFoldersSettingsScreen.kt` | |
| Network storage | ✓ `NetworkStorageSettingsScreen.kt` | |
| App icon | ✓ `AppIconScreen.kt` | |
| Module sources | ✓ `ModuleSourceScreen.kt` | |

### 2.3 ConvX Screens

| Screen / Flow | Present | Notes |
|---|---|---|
| Home | ✓ `HomeScreen.kt` | |
| Library | ✓ + extensive local music support | |
| Search | ✓ + Apple Music search scope | |
| Player | ✓ | |
| Mini player | ✓ | |
| Queue | ✓ | |
| Equalizer | ✓ `EqScreen.kt` | axion EQ |
| History | ✓ | |
| Stats | ✓ | |
| Wrapped | ✓ | |
| Sleep timer | ✓ | alarm + sleep timer |
| Local music | ✓ extensive | folder picker, local songs |
| Lyrics screen | ✓ | |
| Android Auto | ✓ | |
| Settings | ✓ `settings/SettingsScreen.kt` | ~50 sub-screens |
| Account | ✓ | |
| Backup & Restore | ✓ | |
| About | ✓ | |
| Changelog | ✓ | |
| Discord RPC | ✓ | |
| Listen Together | ✓ | |
| Last.fm | ✓ | |
| ListenBrainz | ✓ | |
| Google Cast | ✓ | |
| Apple Music | ✓ | |
| Spotify | ✓ | |
| TIDAL | ✓ `TidalQualityKey` | |
| JioSaavn | ✓ | |
| Canvas / Visualiser | ✓ `CanvasSourceKey` | animated canvas |
| Glass / Liquid Glass | ✓ extensive `liquidGlass*` keys | |
| DIY layout | ✓ | |
| Song transitions | ✓ | |
| Home background | ✓ | |
| Lyrics font | ✓ | |
| Glyphs | ✓ | |
| Font selection | ✓ `AppFont` | |
| Density scale | ✓ | |
| Grid customization | ✓ | |
| Tab view (tablets) | ✓ | |
| Side bar | ✓ | |
| Custom app background | ✓ | |
| Recognition (Shazam-like) | ✓ `RecognitionScreen.kt` | |
| Jam screen | ✓ `JamScreen.kt` | |
| Ambient mode | ✓ `AmbientModeScreen.kt` | |
| Comments | ✓ `CommentSheet.kt` | |
| Explore | ✓ `ExploreScreen.kt` | |
| Apple Music library | ✓ `AppleLibraryScreen.kt` | |
| Spotify library | ✓ `SpotifyLibraryScreen.kt` | |

### 2.4 BitChord Screens

| Screen / Flow | Present | Notes |
|---|---|---|
| Home | ✓ `HomeScreen.kt` | |
| Library | ✓ `LibraryScreen.kt` | |
| Search | ✓ `SearchScreen.kt` | |
| Player | ✓ | |
| Mini player | ✓ | |
| Queue | ✓ | |
| Equalizer | ✓ `EqualizerScreen.kt` | tone pad + 7-band |
| History | ✓ `HistoryScreen.kt` | |
| Stats / Replay | ✓ | genre charts |
| Listen Together | ✓ `ListenTogetherScreen.kt` | |
| Sleep timer | ✓ `SleepTimer` in `SongActionsMenu.kt` | |
| Android Auto | ✓ | |
| Settings | ✓ `SettingsSheet.kt` | single sheet, sections |
| Sources | ✓ `SourcesScreen.kt` | |
| Discord | ✓ `DiscordScreen.kt` | |
| Signature API | ✓ `SignatureApiEditor.kt` | |
| Spotify Canvas | ✓ `SpotifyCanvasAuthScreen.kt` | |
| Party server | ✓ `PartyServerEditor.kt` | Listen Together host |
| Wear OS | ✓ `WearOsSettings.kt` | |
| Local folders | ✓ `LocalFolder.kt` | |
| Channel picker | ✓ `ChannelPickerScreen.kt` | |
| Switch channel | ✓ `SwitchChannelScreen.kt` | |
| Top picks shelf | ✓ `TopPicksShelf.kt` | |
| Detail screen | ✓ `DetailScreen.kt` | album/artist/playlist pages |

---

## 3. Menu Actions (Long-Press / Overflow) Audit

### 3.1 Song Menu (Long-Press on a Track)

#### Metrolist (`ui/menu/SongMenu.kt`)

| Action | Label | Notes |
|---|---|---|
| Like | Like | |
| Dislike | Dislike | |
| Add to playlist | Add to playlist | |
| Add to queue | Add to queue | |
| Play next | Play next | |
| Start radio | Go to song station | |
| Download | Download | |
| Remove download | Remove download | |
| Cancel download | Cancel download | |
| Share | Share | |
| Open album | Go to album | |
| Open artist | Go to artist | |
| Song info | Song info | |
| Statistics | Statistics | play count etc. |
| Show in folder | Show in folder | local files only |
| Rename | Rename | local files only |
| Delete | Delete | local files only |
| Set as ringtone | Set as ringtone | local files only |
| Add to ignore list | Add to ignore list | |
| Report incorrect metadata | Report | |
| Copy link | Copy link | |
| Remove from playlist | Remove from playlist | when on playlist screen |
| Move to top | Move to top | playlists |
| Go to album | Go to album | |
| Go to artist | Go to artist | |
| Go to channel | Go to channel | uploads |

#### ConvX (`ui/menu/SongMenu.kt` — assumed same as Metrolist)

| Action | Label | Notes |
|---|---|---|
| Like | Like | |
| Dislike | Dislike | |
| Add to playlist | Add to playlist | |
| Add to queue | Add to queue | |
| Play next | Play next | |
| Start radio | Start radio | |
| Download | Download | |
| Remove download | Remove download | |
| Cancel download | Cancel download | |
| Share | Share | |
| Open album | Open album | |
| Open artist | Open artist | |
| Song info | Song info | |
| Statistics | Statistics | |
| Show in folder | Show in folder | local only |
| Rename | Rename | local only |
| Delete | Delete | local only |
| Set as ringtone | Set as ringtone | local only |
| Add to ignore list | Add to ignore list | |
| Report incorrect metadata | Report | |
| Copy link | Copy link | |
| Remove from playlist | Remove from playlist | |
| Move to top | Move to top | playlists |
| Go to album | Go to album | |
| Go to artist | Go to artist | |
| Go to channel | Go to channel | uploads |

#### BitChord (`ui/components/SongActionsMenu.kt`)

| Action | Label | Notes |
|---|---|---|
| Like | Like | heart icon |
| Dislike | Dislike | broken heart |
| Share | Share | |
| Revert to original | Revert to original | if playing substituted copy |
| Upgrade quality | Upgrade quality | if pinned to original |
| Convert to video / audio | Convert to video | toggle audio/video version |
| Convert to audio | Convert to audio | |
| Add to playlist | Add to playlist | |
| Remove from playlist | Remove from playlist | when on playlist page |
| Start radio | Start radio | |
| Play next | Play next | |
| Add to queue | Add to queue | |
| Download | Download | full download lifecycle state |
| Cancel download | Cancel (in-progress) | |
| Retry download | Retry (failed) | |
| Save to downloads | Saved to downloads | when completed |
| Delete download | Delete (when saved) | |
| Remove from cache | Remove from cache | |
| Upload to WebDAV | Upload to WebDAV | local files only |
| Remove from history | Remove from history | on History screen |
| Open album | Open album | resolves links |
| Open artist | Open artist | resolves links |
| Song info | Song info | dialog |
| Sleep timer | Sleep timer | player only |
| Lyrics offset | Lyrics offset | player only |
| Copy log | Copy log | player only |

#### MusicForge (`ui/components/TrackActions.kt`)

| Action | Label | Notes |
|---|---|---|
| Play next | Play next | |
| Start radio | Start radio | |
| Like | Like / Unlike | animated heart icon |
| Add to playlist | Add to playlist | inline picker |
| New playlist | New playlist | inline in picker |
| Download | Download | 3 states: normal/downloading/removing |
| Re-download in high quality | Re-download in high quality | low bitrate |
| Remove download | Remove download | when completed |
| Cancel download | Cancel download | when downloading |
| Extra actions | (caller-provided) | `extra` param |

**Song menu gaps for MusicForge:**
- ✗ Dislike / hate | P1
- ✗ Share | P0
- ✗ Open album / Go to album | P1
- ✗ Open artist / Go to artist | P1
- ✗ Song info | P2
- ✗ Statistics | P2
- ✗ Remove from playlist (contextual) | P1
- ✗ Sleep timer | P0
- ✗ Convert to video/audio | P2
- ✗ Revert to original / Upgrade quality | P1
| Lyrics offset | P2
- ✗ Copy log | P2

### 3.2 Album / Artist / Playlist Menu (Long-Press on a Release)

#### Metrolist (`ui/menu/AlbumMenu.kt`, `ArtistMenu.kt`, `PlaylistMenu.kt`)

| Action | Album | Artist | Playlist | Notes |
|---|---|---|---|---|
| Play | ✓ | ✓ | ✓ | |
| Shuffle play | ✓ | ✓ | ✓ | |
| Add to queue | ✓ | ✓ | ✓ | |
| Play next | ✓ | ✓ | ✓ | |
| Start radio | ✓ | ✓ | ✗ | albums/artists only |
| Download | ✓ | ✓ | ✓ | |
| Remove download | ✓ | ✓ | ✓ | |
| Share | ✓ | ✓ | ✓ | |
| Open album page | ✓ | | | |
| Open artist page | ✓ | | | from album |
| Open artist | | ✓ | | from playlist |
| Add to playlist | ✓ | | | |
| Rename playlist | | | ✓ | owned only |
| Delete playlist | | | ✓ | owned only |
| Edit playlist | | | ✓ | owner |
| Add to home | ✓ | ✓ | ✓ | |
| Statistics | ✓ | ✓ | ✓ | play counts |
| Song info | ✓ | | | |
| Report | ✓ | ✓ | ✓ | |
| Copy link | ✓ | ✓ | ✓ | |
| Set as ringtone | ✓ | | | local only |
| Delete | ✓ | | | local only |

#### ConvX (`ui/menu/AlbumMenu.kt`, `ArtistMenu.kt`, `PlaylistMenu.kt`)

(inherits Metrolist's menus, adds:)

| Action | Album | Artist | Playlist | Notes |
|---|---|---|---|---|
| Pin to library | ✓ | ✓ | ✓ | |
| Unpin | ✓ | ✓ | ✓ | |
| Hide | ✓ | ✓ | ✓ | |
| Show in search | ✓ | ✓ | ✓ | |
| Add to home screen | ✓ | ✓ | ✓ | |
| Sort by | | ✓ | ✓ | artists/playlists |
| Filter | | ✓ | ✓ | artists/playlists |
| View type | | ✓ | ✓ | list/grid |
| Export | ✓ | | | local files |
| Share cover | ✓ | ✓ | ✓ | |

#### BitChord (`ui/components/BrowseActionsMenu.kt`)

| Action | Album | Artist | Playlist | Notes |
|---|---|---|---|---|
| Play | ✓ | ✓ | ✓ | optional (null on own page) |
| Shuffle | ✓ | ✓ | ✓ | optional |
| Add to queue | ✓ | ✓ | ✓ | always |
| Play next | ✓ | ✓ | ✓ | always |
| Download all | ✓ | ✓ | ✓ | state-aware |
| Open | ✓ / Open album | ✓ / Open artist | ✓ / Open playlist | optional |
| Share | ✓ | ✓ | ✓ | when has link |
| Pin | | | ✓ | playlist only |
| Unpin | | | ✓ | playlist only |
| Rename | | | ✓ | owned playlist only |
| Reorder songs | | | ✓ | owned playlist only |
| Delete playlist | | | ✓ | owned, confirmation |
| Delete download | | | ✓ | optional, confirmation |

#### MusicForge Collection Menus

| Action | Album | Artist | Playlist | Notes |
|---|---|---|---|---|
| Play | ✗ | ✗ | ✗ | no long-press collection menu |
| Shuffle | ✗ | ✗ | ✗ | |
| Add to queue | ✗ | ✗ | ✗ | |
| Play next | ✗ | ✗ | ✗ | |
| Download | ✗ | ✗ | ✗ | |
| Share | ✗ | ✗ | ✗ | |
| Open album page | ✗ | ✗ | ✗ | |
| Open artist page | ✗ | ✗ | ✗ | |
| Rename playlist | ✗ | ✗ | ✗ | |
| Delete playlist | ✗ | ✗ | ✗ | |
| Add to playlist | ✗ | ✗ | ✗ | |
| Start radio | ✗ | ✗ | ✗ | |
| Statistics | ✗ | ✗ | ✗ | |
| Pin | ✗ | ✗ | ✗ | |

**Collection menu gap:** MusicForge has no long-press menu on album/artist/playlist cards. Only song actions exist via `TrackActionsSheet`. **P0**.

### 3.3 Queue Menu (Overflow on Queue Items)

#### Metrolist (`ui/menu/QueueMenu.kt`)

| Action | Notes |
|---|---|
| Play next | |
| Add to queue | |
| Start radio | |
| Go to album | |
| Go to artist | |
| Share | |
| Download | |
| Remove download | |
| Cancel download | |
| Song info | |
| Statistics | |
| Remove from queue | |
| Clear queue | |
| Move to top | |
| Add to playlist | |
| Like | |
| Dislike | |

#### ConvX

(same as Metrolist + pin/hide options)

#### BitChord

| Action | Notes |
|---|---|
| Go to album | |
| Go to artist | |
| Share | |
| Song info | |
| Remove from queue | |

#### MusicForge

Queue supports swipe-to-remove with undo. No overflow menu on queue items.

**Queue menu gap:** No overflow menu on individual queue items in MusicForge. **P1**.

### 3.4 Player Menu (Overflow on Now Playing)

#### Metrolist

| Action | Notes |
|---|---|
| Like | |
| Dislike | |
| Add to playlist | |
| Share | |
| Start radio | |
| Sleep timer | |
| Playback speed | |
| Equalizer | |
| Statistics | |
| Song info | |
| Copy link | |
| Go to album | |
| Go to artist | |

#### ConvX

(same + canvas toggle, lyrics translate, AI features)

#### BitChord

| Action | Notes |
|---|---|
| Like | |
| Dislike | |
| Share | |
| Add to playlist | |
| Remove from playlist | |
| Start radio | |
| Play next | |
| Add to queue | |
| Cancel download / Download | |
| Revert to original | |
| Upgrade quality | |
| Convert to video/audio | |
| Remove from cache | |
| Upload to WebDAV | |
| Remove from history | |
| Open album | |
| Open artist | |
| Song info | |
| Sleep timer | |
| Lyrics offset | |
| Copy log | |

#### MusicForge

Player uses the same `TrackActionsSheet` — Play next, Start radio, Like/Unlike, Add to playlist, Download/Cancel/Remove.

**Player menu gaps:**
- ✗ Share | P1
- ✗ Dislike | P2
- ✗ Sleep timer | P0
- ✗ Lyrics offset | P2
- ✗ Open album / artist | P1
- ✗ Song info | P2
- ✗ Copy log | P2

---

## 4. Top 40 Missing Gaps (Prioritized)

| # | Feature | Area | Reference | MusicForge file / Location | Priority |
|---|---|---|---|---|---|
| 1 | **Sleep timer** | Playback / Player menu | BitChord `SleepTimer`, Metrolist `sleepTimerEnabled` | ✗ no `SleepTimer` class | P0 |
| 2 | **Last.fm scrobbling** | Scrobbling | Metrolist `PreferenceKeys.kt:204-213`, ConvX `AppSettings.kt:1699-1713` | ✗ no scrobbler | P0 |
| 3 | **Collection long-press menus** (album/artist/playlist) | Menus | BitChord `BrowseActionsMenu.kt`, Metrolist `AlbumMenu.kt` | ✗ only song `TrackActionsSheet` | P0 |
| 4 | **Share track** | Song menu / Player menu | BitChord `SongActionsMenu.kt:230`, Metrolist `SongMenu.kt` | ✗ no share action | P0 |
| 5 | **Open album / Open artist** | Song menu | BitChord `SongActionsMenu.kt:348-368`, Metrolist | ✗ no navigation rows | P1 |
| 6 | **Dislike / hate** | Song menu | BitChord `SongActionsMenu.kt:224`, Metrolist | ✗ only Like/Unlike | P1 |
| 7 | **Local music library** | Library | ConvX `LocalMusicScreen.kt`, Metrolist | ✗ no on-device music | P0 |
| 8 | **Google Cast** | Integrations | Metrolist `EnableGoogleCastKey`, ConvX | ✗ no Cast | P1 |
| 9 | **Discord RPC** | Integrations | BitChord `AppSettings.kt:711-745` | ✗ no Discord | P1 |
| 10 | **Spotify source / scrobbling** | Integrations | ConvX `SpotifyScreen.kt` | ✗ no Spotify | P2 |
| 11 | **Animated Canvas** (Canvas/Artwork) | Player | ConvX `CanvasSourceKey`, BitChord | ✗ no canvas | P1 |
| 12 | **Liquid Glass / advanced blur effects** | Appearance | ConvX `liquidGlass*` keys (30+) | ✗ only Solid/Artwork/Liquid | P1 |
| 13 | **EQ tone pad** (warm/bright + scoop/mid) | Equalizer | BitChord `equalizerToneX/Y` | ✗ 5-band sliders only | P1 |
| 14 | **7-band EQ** | Equalizer | BitChord 7 sliders | ✗ only 5 bands | P1 |
| 15 | **Bit-perfect output** | Audio processing | BitChord `bitPerfectMode` | ✗ | P2 |
| 16 | **Spatial audio** | Audio processing | ConvX `spatialAudio`, BitChord | ✗ | P1 |
| 17 | **Vocal suppression** | Audio processing | ConvX `vocalSuppression` | ✗ | P2 |
| 18 | **Dolby Atmos toggle** | Playback | ConvX `dolbyAtmos` | ✗ | P1 |
| 19 | **Playback speed** | Playback | ConvX `PlaybackSettings.kt:8`, BitChord `KEY_SPEED` | ✗ | P1 |
| 20 | **Sleep timer presets + custom** | Sleep timer | BitChord `SleepTimer.PRESETS`, Metrolist | ✗ | P0 |
| 21 | **Alarm (music wake-up)** | Alarms | Metrolist `AlarmSettings.kt` | ✗ | P1 |
| 22 | **Remove from history** | Song menu | BitChord `SongActionsMenu.kt:337` | ✗ | P1 |
| 23 | **Remove from cache** | Song menu | BitChord `SongActionsMenu.kt:328` | ✗ | P2 |
| 24 | **Song info screen** | Song menu | BitChord `SongActionsMenu.kt:370`, Metrolist | ✗ | P2 |
| 25 | **Statistics (play count)** | Song menu / Collection | Metrolist `SongMenu.kt` | ✗ | P2 |
| 26 | **Queue item overflow menu** | Queue | Metrolist `QueueMenu.kt`, ConvX | ✗ no per-item menu | P1 |
| 27 | **Queue lock toggle** | Queue | Metrolist `preventDuplicateTracksInQueue`, ConvX `queueEditLock` | ✓ `forge.queueLocked` — present | — |
| 28 | **ListenBrainz user name** | Scrobbling | MusicForge `Keys.ListenBrainzUser` | ✓ present | — |
| 29 | **Scrobble min duration** | Scrobbling | Metrolist `scrobbleMinDuration`, ConvX | ✗ | P2 |
| 30 | **Scrobble delay percent** | Scrobbling | Metrolist `scrobbleDelayPercent` | ✗ | P2 |
| 31 | **App icon selection** | Appearance | Metrolist `appIcon`, ConvX | ✗ | P2 |
| 32 | **Font selection** | Appearance | ConvX `AppFont`, BitChord | ✗ | P2 |
| 33 | **Density scale (UI scaling)** | Appearance | Metrolist `density_scale_factor` | ✗ | P2 |
| 34 | **Hide explicit / video / Shorts** | Content filters | Metrolist `hideExplicit`, ConvX | ✗ | P2 |
| 35 | **Suggestion region override** | Content | ConvX `suggestionRegion` | ✗ | P2 |
| 36 | **Lyrics source providers** | Lyrics | ConvX `enableKugou`, `enableLrcLib`, etc. | ✗ | P1 |
| 37 | **Lyrics animation style** | Lyrics | ConvX `lyricsAnimationStyle` (11 styles) | ✗ | P1 |
| 38 | **Lyrics romanization** | Lyrics | ConvX `lyricsRomanizeJapanese` etc. | ✗ | P2 |
| 39 | **Lyrics translation (AI)** | Lyrics | ConvX `translateLyrics`, `openRouterApiKey` | ✗ | P2 |
| 40 | **Local music folder selection** | Storage | ConvX `localMusicFolderUri`, BitChord | ✗ | P1 |

---

## 5. Settings Key Mapping (Cross-App Import Compatibility)

MusicForge's `Keys.importable` map (in `data/prefs/Prefs.kt:117-127`) enables cross-app settings import via column-name intersection during backup restore. Keys that match Metrolist/ConvX by name carry across automatically.

| Key name | Metrolist | ConvX | BitChord (SharedPreferences) | MusicForge | Importable? |
|---|---|---|---|---|---|
| `innerTubeCookie` | ✓ | ✓ | ✓ KEY | ✓ `Keys.InnerTubeCookie` | ✓ |
| `visitorData` | ✓ | ✓ | ✓ | ✓ `Keys.VisitorData` | ✓ |
| `dataSyncId` | ✓ | ✓ | ✓ | ✓ `Keys.DataSyncId` | ✓ |
| `innerTubeAuthUser` | ✓ | ✓ | ✓ | ✓ `Keys.InnerTubeAuthUser` | ✓ |
| `accountName` | ✓ | ✓ | ✓ | ✓ `Keys.AccountName` | ✓ |
| `accountEmail` | ✓ | ✓ | ✓ | ✓ `Keys.AccountEmail` | ✓ |
| `accountChannelHandle` | ✓ | ✓ | ✓ | ✓ `Keys.AccountChannelHandle` | ✓ |
| `contentLanguage` | ✓ | ✓ | ✓ | ✓ `Keys.ContentLanguage` | ✓ |
| `contentCountry` | ✓ | ✓ | ✓ | ✓ `Keys.ContentCountry` | ✓ |
| `darkMode` | ✓ | ✓ | ✓ | ✓ `Keys.DarkMode` | ✓ |
| `pureBlack` | ✓ | ✓ | ✓ | ✓ `Keys.PureBlack` | ✓ |
| `audioQuality` | ✓ | ✓ | ✓ | ✓ `Keys.AudioQuality` | ✓ |
| `skipSilence` | ✓ | ✓ | ✓ | ✓ `Keys.SkipSilence` | ✓ |
| `audioNormalization` | ✓ | ✓ | ✓ | ✓ `Keys.AudioNormalization` | ✓ |
| `persistentQueue` | ✓ | ✓ | ✓ | ✓ `Keys.PersistentQueue` | ✓ |
| `crossfadeEnabled` | ✓ | ✓ | ✓ | ✓ `Keys.CrossfadeEnabled` | ✓ |
| `crossfadeDuration` | ✓ (float) | ✓ | ✓ `crossfade_seconds` (int!) | ✓ (float) | ✓ (Metrolist/ConvX) ✗ (BitChord int) |
| `crossfadeDurationFloat` | — | ✓ | — | ✓ `Keys.CrossfadeSeconds` (alias) | ✓ from ConvX |
| `crossfadeGapless` | ✓ | ✓ | ✓ | ✓ `Keys.CrossfadeGapless` | ✓ |
| `maxSongCacheSize` | ✓ | ✓ | ✓ `audio_cache_limit_bytes` | ✓ `Keys.MaxSongCacheSizeMb` | ✓ (Metrolist/ConvX) ✗ (BitChord) |
| `maxImageCacheSize` | ✓ | ✓ | ✓ | ✓ `Keys.MaxImageCacheSizeMb` | ✓ |
| `proxyEnabled` | ✓ | ✓ | ✓ | ✓ `Keys.ProxyEnabled` | ✓ |
| `proxyType` | ✓ | ✓ | ✓ | ✓ `Keys.ProxyType` | ✓ |
| `proxyUrl` | ✓ | ✓ | ✓ | ✓ `Keys.ProxyUrl` | ✓ |
| `proxyUsername` | ✓ | ✓ | ✓ | ✓ `Keys.ProxyUsername` | ✓ |
| `proxyPassword` | ✓ | ✓ | ✓ | ✓ `Keys.ProxyPassword` | ✓ |
| `listenbrainz_token` | ✓ | ✓ | ✓ | ✓ `Keys.ListenBrainzToken` | ✓ |
| `listenbrainz_enabled` | ✓ | ✓ | ✓ | ✓ `Keys.ListenBrainzEnabled` | ✓ |
| `autoDownloadOnLike` | ✓ | ✓ | ✓ | ✓ `Keys.AutoDownloadOnLike` | ✓ |
| `autoplay` | ✓ | ✓ | ✓ | ✓ `Keys.Autoplay` | ✓ |
| `useLoginForBrowse` | ✓ | ✓ | ✓ | ✓ `Keys.UseLoginForBrowse` | ✓ |
| `pauseSearchHistory` | ✓ | ✓ | ✓ | ✓ `Keys.PauseSearchHistory` | ✓ |
| `pauseListenHistory` | ✓ | ✓ | ✓ | ✓ `Keys.PauseListenHistory` | ✓ |
| `defaultOpenTab` | ✓ | ✓ | ✓ | ✓ `Keys.StartScreen` | ✓ |
| `keepScreenOn` | ✓ | ✓ | ✓ | ✓ `Keys.KeepScreenOn` | ✓ |
| `showLyrics` | ✓ | ✓ | ✓ | ✓ `Keys.ShowLyrics` | ✓ |
| `lyricsTextSize` | ✓ | ✓ | ✓ | ✓ `Keys.LyricsTextSize` | ✓ |

**Note:** BitChord uses SharedPreferences with different key names (`bitchord_settings` file) and its own naming convention. Direct cross-app import of BitChord settings is not supported — the key names diverge significantly (`audio_quality_wifi` vs `audioQuality`, etc.). MusicForge's backup importer targets Metrolist/ConvX format compatibility.

---

## 6. References

### MusicForge source files examined

- `data/prefs/Prefs.kt` — full `Keys` object, lines 1-128
- `ui/screens/SettingsScreen.kt` — full settings UI, lines 1-661
- `ui/components/TrackActions.kt` — song menu (`TrackActionsSheet`), lines 1-225
- `.agent/handoffs/2026-10-29-claude-t4-player-canvas.md` — T4 context

### Metrolist reference files

- `constants/PreferenceKeys.kt` — 193 preference keys (lines 1-722)
- `ui/screens/settings/*.kt` — 20+ settings sub-screens:
  - `settings/AccountSettings.kt`
  - `settings/AppearanceSettings.kt`
  - `settings/PlayerSettings.kt`
  - `settings/StorageSettings.kt`
  - `settings/BackupAndRestore.kt`
  - `settings/integrations/DiscordSettings.kt`
  - `settings/integrations/LastFMSettings.kt`
  - `settings/integrations/ListenTogetherSettings.kt`
  - `settings/AlarmSettings.kt`
  - `settings/AiSettings.kt`
  - `settings/RomanizationSettings.kt`
  - `settings/PrivacySettings.kt`
  - `settings/AboutScreen.kt`
  - `settings/ChangelogScreen.kt`
  - `settings/UpdaterSettings.kt`
  - `settings/ThemeScreen.kt`
  - `settings/AndroidAutoSettings.kt`
- `ui/menu/SongMenu.kt`, `AlbumMenu.kt`, `ArtistMenu.kt`, `PlaylistMenu.kt`, `QueueMenu.kt`, `YouTubeSongMenu.kt`, `YouTubePlaylistMenu.kt`

### ConvX reference files

- `constants/PreferenceKeys.kt` — extended preference keys (lines 1-1084)
- `data/settings/PlaybackSettings.kt` — playback state holder
- `data/settings/AppSettings.kt` — convenience re-export
- `ui/screens/settings/*.kt` — 50+ settings sub-screens:
  - `settings/AppearanceSettings.kt`
  - `settings/AppearanceAdvancedSettings.kt`
  - `settings/PlayerSettings.kt`
  - `settings/PlayerAdvancedSettings.kt`
  - `settings/PlayerDesignSheet.kt`
  - `settings/PlayerThemeScreen.kt`
  - `settings/StorageSettings.kt`
  - `settings/BackupAndRestore.kt`
  - `settings/LyricsSettingsScreen.kt`
  - `settings/LyricsFontSettings.kt`
  - `settings/JioSettings.kt`
  - `settings/SpotifyScreen.kt`
  - `settings/UpdateSettings.kt`
  - `settings/GlassEffectSettings.kt`
  - `settings/HomeBackgroundSettings.kt`
  - `settings/HomeFeedOrderScreen.kt`
  - `settings/DiyEditorScreen.kt`
  - `settings/ModuleSourceScreen.kt`
  - `settings/ScanMusicScreen.kt`
  - `settings/LocalFoldersSettingsScreen.kt`
  - `settings/NetworkStorageSettingsScreen.kt`
  - `settings/SongTransitionsScreen.kt`
  - `settings/RomanizationSettings.kt`
  - `settings/PrivacySettings.kt`
  - `settings/CrashLogsSettingsScreen.kt`
  - `settings/SearchableSettings.kt`
- `ui/screens/library/LocalMusicScreen.kt`
- `ui/screens/ExploreScreen.kt`
- `ui/screens/JamScreen.kt`
- `ui/screens/ambient/AmbientModeScreen.kt`
- `ui/screens/wrapped/WrappedScreen.kt`

### BitChord reference files

- `data/settings/AppSettings.kt` — full settings object with 80+ SharedPreferences keys (lines 1-2208)
- `ui/components/SongActionsMenu.kt` — song long-press menu (lines 1-980)
- `ui/components/BrowseActionsMenu.kt` — collection (album/artist/playlist) menu (lines 1-293)
- `ui/screens/*.kt` — 20 screens:
  - `AccountAndScrobblingScreen.kt`
  - `SourcesScreen.kt`
  - `DiscordScreen.kt`
  - `SpotifyCanvasAuthScreen.kt`
  - `SignatureApiEditor.kt`
  - `PartyServerEditor.kt`
  - `WearOsSettings.kt`
  - `SettingsSheet.kt`
  - `EqualizerScreen.kt`
  - `DetailScreen.kt`
  - `HistoryScreen.kt`
  - `StatsScreen.kt`
  - `ListenTogetherScreen.kt`
  - `ListenTogetherSheets.kt`

### Key count comparison

| App | Settings keys | Settings sub-screens | Menu files | Song menu actions |
|---|---|---|---|---|
| Metrolist | 193 | ~20 | 8+ | ~25 |
| ConvX | ~170 | ~50 | 8+ | ~25 |
| BitChord | 80+ | 1 sheet | 2 | ~25+ (player-only rows) |
| MusicForge | 47 | 1 (`SettingsScreen.kt`) | 1 (`TrackActions.kt`) | ~9 |

---

## 7. Summary of Key Findings

1. **P0 gaps (must address):**
   - No sleep timer — a fundamental playback feature
   - No Last.fm scrobbling — major integration missing
   - No collection-level menus (album/artist/playlist long-press) — only song actions exist
   - No Share action on tracks — basic expectation
   - No local music library — significant audience gap

2. **P1 gaps (should address):**
   - Missing audio processing features: spatial audio, Dolby Atmos, playback speed, bit-perfect
   - No Google Cast integration
   - No Discord RPC
   - No animated Canvas / visualiser
   - Limited EQ (5-band vs 7-band; no tone pad)
   - No Queue item overflow menus
   - No lyrics source selection or translation
   - No local music folder picker

3. **P2 gaps (nice to have):**
   - Extensive appearance customization (fonts, density, liquid glass, custom backgrounds)
   - Alarm (music wake-up)
   - Wrapped / yearly stats
   - Apple Music / Spotify / TIDAL / JioSaavn source integrations
   - Advanced grid/tablet layout customization
   - Recognition (Shazam-like)
   - Content filters (explicit, video songs, Shorts)
   - Romanization settings

4. **Strengths (areas where MusicForge is competitive):**
   - Backup & restore with cross-app import (Metrolist/ConvX column intersection)
   - Persistent downloads with Media3 DownloadManager
   - EQ on all phones (Metrolist's axion only works on some)
   - Customise Home (reorder/hide shelves)
   - SuggestionsPaused (Home preferences)
   - Hide top bar (edge-to-edge canvas)
   - Glass tier (Solid/Artwork/Liquid)
   - Redownloads after restore
   - Lyrics text size adjustment

5. **Design philosophy differences:**
   - Metrolist: maximalist — everything configurable, 193 keys, 20+ settings screens
   - ConvX: maximalist v2 — even more keys (~170), 50+ settings screens, extensive appearance/audio features
   - BitChord: balanced — 80+ keys, single settings sheet, rich player features (sleep timer, WebDAV, equaliser)
   - MusicForge: minimalist — 47 keys, single flat settings screen, focused on core playback + download experience

