# Handoff: B2 · Long-press menus for albums, artists and playlists

**Date:** 2026-10-29
**Agent:** nemotron-3-ultra-550b-a55b:free (OpenCode)
**Branch:** toccata/p3-collection-menus

## Done

- Added `CollectionActionsSheet` long-press handler to **SearchScreen.kt** for AlbumItem, ArtistItem, and PlaylistItem results
- The `CollectionActionsSheet` was already implemented in `CollectionActions.kt` with all required actions:
  - **Album**: Play, Shuffle, Play next, Add to queue, Start radio, Download all, Add to playlist, Share, Go to album, Go to artist, Save to/remove from library
  - **Artist**: Play, Shuffle, Start radio, Follow/Unfollow, Share, Go to artist
  - **Playlist**: Play, Shuffle, Play next, Add to queue, Download all, Share, Go to playlist; for local playlists also Rename and Delete (with confirmation)
- Verified existing wiring in:
  - HomeScreen.kt (Home cards, shelves, remote feed)
  - LibraryLists.kt (Library grids for albums, artists)
  - ArtistScreen.kt (Artist page items)
  - Shelf.kt (remote shelves in Home/Search)

## Open

- None

## Could not verify

- Real-device testing on Honor X7b (long-press haptics, sheet animation timing, download progress while menu open)
- Search results long-press on actual YouTube Music items (need signed-in account)

## Risk

- None identified. CI passes. The change is minimal and follows existing patterns.

## Commits

```
feat: add long-press menu to Search results for albums/artists/playlists

The CollectionActionsSheet was already implemented with all required actions.
This change wires it up in SearchScreen.kt for remote AlbumItem, ArtistItem, and PlaylistItem.

Skills: android-compose-ui, compose-component-design, compose-state-and-effects, android-data-layer, android-navigation, ui-ux-pro-max, ponytail
Ported-from: Metrolist YouTubeAlbumMenu.kt, YouTubeArtistMenu.kt, YouTubePlaylistMenu.kt; BitChord BrowseActionsSheet.kt
```