# T4 Handoff: Player canvas, insets and Hide top bar

## What was done

Branch `toccata/player-canvas` — commit `437dbc8`.

### Task 1: WindowInsets (safeDrawing)
- `PlayerSheet.kt` `PlayerContent`: replaced `statusBarsPadding().navigationBarsPadding()` with `WindowInsets.safeDrawing` via `windowInsetsPadding()`. This covers status bars + nav bars + display cutouts, ensuring nothing clips at 1080×2412 with gesture or 3-button nav.
- Made the controls `Column` scrollable (`verticalScroll(rememberScrollState())`) so overflow content doesn't clip. Cover retains its 45% height and scales down first (its own `graphicsLayer` transform), never the controls.

### Task 2: Canvas (ambient fade)
- `BigArtwork`: changed the bottom-edge gradient overlay to fade to the track's `ambient` colour (`ambience.colors.ambient`) instead of `Color.Black`. This removes the visible dark band where the cover met the controls background.
- `ambient` is threaded from `PlayerSheet` → `PlayerContent` → `BigArtwork`.
- Scrim still baked once per track via `withScrim`; the `playerBackground` backdrop is unchanged.

### Task 3: Hide top bar
- `Prefs.kt`: added `Keys.HideTopBar = bool("forge.hideTopBar")`.
- `MainActivity.kt`: added `hideTopBar` field to `Look`, read from DataStore, passed to `ForgeRoot`.
- `ForgeRoot.kt`: added `hideTopBar: Boolean = false` parameter; `LocalHideTopBar` composition local provided to children. The top status-bar scrim animates alpha via `animateFloatAsState` (0f→1f, 180 ms); when collapsed the scrim is skipped entirely (`alpha < 0.01f`).
- `Common.kt` (`ScreenHeader`): reads `LocalHideTopBar`, animates alpha, skips composition when fully collapsed.
- `Common.kt` (`BackRow`): new composable wrapping the back-button row pattern with the same alpha animation. Replaces inline `Row(…statusBarsPadding()…)` calls.
- Sub-screens updated to use `BackRow`: `EqualizerScreen`, `AboutScreen`, `TasteScreen`, `StatsScreen`, `ArtistScreen`, `SettingsScreen`, `LibraryLists`.
- `CollectionScreen`: the back+more row was custom — handled inline with the same pattern (fades out, skips when collapsed).
- `SettingsScreen`: added `ToggleRow("Hide top bar", …)` in the Appearance section with subtitle.
- NavHost transitions left untouched (per spec).

## Verification
- `./gradlew :app:compileDebugKotlin` — BUILD SUCCESSFUL
- `./gradlew :app:testDebugUnitTest` — BUILD SUCCESSFUL (all tests pass)
- `./gradlew :app:lintDebug` — BUILD SUCCESSFUL (0 errors)
- `scripts/ci-check.sh` — CI 40830482862: success

## Notes
- `local.properties` was created temporarily for local build (sdk.dir) and deleted. Not committed.
- The ConvX reference (NowPlayingBackground) uses `drawBehind` gradient read in the draw phase to avoid recomposition; MusicForge's approach of ambient colour + baked scrim is retained — only the fade target was changed from black to ambient.
- The BitChord reference (ArtworkPalette) uses `animateColorAsState` crossfade of sampled colours; the ambient colour from `ArtworkAmbience.colorsOf` already serves this role.
