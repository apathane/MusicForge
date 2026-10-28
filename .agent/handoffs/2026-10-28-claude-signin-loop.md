# Sign-in loop fix (LoginScreen.kt)

## Done

Replaced auto-capture-on-page-load with a confirm-button flow. `probeSession()`
detects the SAPISID cookie + ytcfg when `onPageFinished` lands on
`music.youtube.com`, reads the account info, and stores it in Compose state
without persisting. The UI shows a card with the account name/email/avatar and
a "Use this account" button. Tapping it calls `useAccount()`, which saves all
settings, flushes the cookie, forgets sync snapshots, launches `accountSync.sync()`,
then calls `onDone()` (pops the login route) before anything observes the new
settings. No `recreate()` — Home and Library already watch the settings flow
for `InnerTubeCookie`, so the signed-in state propagates reactively.

## Guards

- `capture?.isActive == true` — blocks concurrent probes from multiple
  `onPageFinished` calls.
- `session != null || committing` — blocks re-probe after a session is found
  or after the user taps "Use this account".
- `committing` — blocks double-taps on the confirm button.

## Risks / open

- If the user signs out via Settings and immediately navigates back to login,
  `removeAllCookies` (async) may not have cleared the old SAPISID cookie yet,
  so the card may briefly show the previous account. The user can still tap
  "Use this account" to re-confirm, or navigate back and retry once cookies
  are flushed. Pre-existing behavior.
- The WebView remains visible behind the card. A dim effect was skipped to
  keep the change minimal (ponytail ultra).
- No `recreate()` means `YouTubeSession.start()` is not re-invoked. This is
  fine — `observe()` runs continuously and picks up the new cookie from the
  settings DataStore, same as the original `recreate()` would have triggered.

## Files

- `app/src/main/kotlin/dev/musicforge/ui/screens/LoginScreen.kt` (modified)
- `.agent/board.md` (marked item done)
