# W7 · local scan safety

## Done

`LocalAudioScanner` no longer decides deletion from the upsert query (which only sees changed
rows, and with a folder picked only that folder). Deletion now has its own presence pass per
collection: `_ID` projection, base selection only (no cutoff, no folder filter), ids rebuilt as
content-URI strings. Any null cursor or throw (SecurityException included) sets
`listingComplete = false` and nothing is removed.

- `idsToRemove` (pure, internal, same file): incomplete listing → nothing; empty listing over a
  non-empty library → nothing (card gone / permission revoked); otherwise only existing ids in a
  queried collection that are absent from `present`. Unqueried volumes are never touched.
- `MusicDao.deleteLocalSongs(ids)` replaces `deleteLocalSongsNotIn` (no other caller), called in
  500-row chunks inside one `withTransaction`.
- `FolderMode.selection` now returns `(RELATIVE_PATH LIKE ? OR DATA LIKE ?)` with args
  `"$rel%"` / `"%/$rel/%"`, so an apostrophe in a folder name can't break the SQL.
- `ScanResult.listingComplete`; Settings toast appends "Couldn't check for removed files (no
  access)." when false.

## Tests

`LocalScanRulesTest` (plain JVM, Truth) covers the five deletion rules; `LocalFolderSelectionTest`
(Robolectric, sdk 33) checks a `Guns N' Roses` tree keeps the apostrophe out of the SQL and in the
args.

## Open / risk

- Not built locally (no build here). CI is the gate.
- Folder-scoped scans still upsert only that folder but the presence pass lists every volume, so a
  deleted file anywhere on a readable volume is removed on any scan. That matches "deletion is a
  full listing" and is the intended fix.
- Presence query reuses the base selection, so files excluded by it (short clips, exported
  `Music/MusicForge` copies) are never candidates — same set the scanner indexes.

## Files

- `app/src/main/kotlin/dev/musicforge/data/local/LocalAudioScanner.kt`
- `app/src/main/kotlin/dev/musicforge/data/db/MusicDao.kt` (local-music block)
- `app/src/main/kotlin/dev/musicforge/ui/screens/SettingsScreen.kt` (`scanLocalMusic` only)
- `app/src/test/kotlin/dev/musicforge/data/local/LocalScanRulesTest.kt` (new)
