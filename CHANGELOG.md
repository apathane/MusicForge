# Changelog

## Little Bug Boy — maintenance release

Version 0.13.1, tag `v0.13.1-little-bug-boy`.

- Reject malformed LRC timestamps whose seconds field is outside 00–59.
- When LRCLIB search has no known track duration, preserve provider relevance order instead of accidentally preferring the shortest or zero-duration result.
- When duration is known, compare only candidates with valid positive durations, falling back safely if none have one.
- Add regression tests for malformed and fractional timestamps, multiple timestamps per line, and lyric-result selection.
- No database schema changes.

## Rondo — phase 13

Version 0.13.0, tag `v0.13.0-rondo`.

This maintenance release is based on Rondo and intentionally excludes the later Sonata development work.
