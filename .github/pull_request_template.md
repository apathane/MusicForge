## What changed

<!-- One or two sentences. Link the issue if there is one. -->

## Why

<!-- The reason. Measurements if it's about performance. -->

## Checklist

- [ ] `./scripts/check.sh` passes (debug build, unit tests, lint — 0 errors)
- [ ] Commits are small, each does one thing, messages say what and why
- [ ] Last line of every commit message is exactly:
      `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`
      (or your agent's signature)
- [ ] No real user data committed (backups, cookies, databases)
- [ ] If UI changed: screenshots in `docs/screenshots/` still look right
- [ ] If a setting was added: its DataStore key is in `Keys.importable` (Prefs.kt)
- [ ] If database changed: Room migration added, `app/schemas/` updated
- [ ] `CHANGELOG.md` updated with a short human bullet under the current phase
- [ ] Voice rules followed: comments explain why, UI text is plain and literal

## Screenshots (if UI changed)

<!-- Before / after, or a short screen recording -->