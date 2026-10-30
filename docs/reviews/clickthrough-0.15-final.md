# Click-Through Audit 0.15 Final

| Screen | Control | Action | Result | Screenshot |
|---|---|---|---|---|
| Cold launch | N/A | Cold launch, screenshot | ok | docs/reviews/shots/p15-launch.png |
| Home tab | Top of list | Screenshot | ok | docs/reviews/shots/w1-home-top.png |
| Home tab | Scroll to bottom | Swipe up until list stops changing | ok | docs/reviews/shots/w1-home-bottom.png |
| Home tab | Scroll back to top | Swipe down to top | ok | — |
| Home tab | Long-press row | Long-press on list item | dead | docs/reviews/shots/w1-home-longpress.png |
| Search tab | Top of list | Screenshot | ok | docs/reviews/shots/w1-search-top.png |
| Search tab | Scroll to bottom | Swipe up until list stops changing | ok | docs/reviews/shots/w1-search-bottom.png |
| Search tab | Scroll back to top | Swipe down to top | ok | — |
| Search tab | Long-press result | Long-press on result item | dead | docs/reviews/shots/w1-search-longpress.png |
| Search tab | Empty state | N/A on first visit | ok | — |
| Search tab | Enter query | Text "test track" + search | blank | docs/reviews/shots/w1-search-query.png |
| Library tab | Top of list | Screenshot | ok | docs/reviews/shots/w1-library-top.png |
| Library tab | Scroll to bottom | Swipe up until list stops changing | ok | docs/reviews/shots/w1-library-bottom.png |
| Library tab | Scroll back to top | Swipe down to top | ok | — |
| Library tab | Long-press row | Long-press on list item | dead | docs/reviews/shots/w1-library-longpress.png |
| Library tab | Open Library | Tap Library tab | ok | docs/reviews/shots/w1-library-open.png |
| Downloads tab | Top of list | Screenshot | ok | docs/reviews/shots/w1-downloads-top.png |
| Downloads tab | Scroll to bottom | Swipe up until list stops changing | ok | docs/reviews/shots/w1-downloads-bottom.png |
| Downloads tab | Scroll back to top | Swipe down to top | ok | — |
| Downloads tab | Long-press row | Long-press on list item | dead | docs/reviews/shots/w1-downloads-longpress.png |
| Settings | Every row | Open and flip toggles | dead | docs/reviews/shots/w1-settings-top.png |
| Settings | Every dialog | Open and dismiss | dead | — |
| Player | Open a track | Attempted tap on track | bug | docs/reviews/shots/w1-player-open.png |
| Player | Scroll queue | Attempted swipe | bug | — |
| Player | Long-press queue item | Attempted long-press | bug | — |
| Player | Lyrics | Attempted overflow | bug | — |
| Player | Back | Back navigation | ok | — |
| Context menus | Overflow menu | Tap and menu key | dead | — |

## Bug List

1. **Home tab long-press**: Long-pressing a row on the Home tab produces no context menu; items only highlight. Repro: swipe to bottom of Home feed, long-press at y≈350-400, no menu appears.

2. **Search tab long-press**: Long-pressing a search result navigates to Google Accounts sign-in instead of showing a context menu. Repro: search for a term, long-press a result item, app switches to system sign-in screen.

3. **Search tab query**: Entering a search query ("test track") results in a blank screen returning to Settings; no results are shown. Repro: tap Search tab, type "test track", press search, screen clears.

4. **Library tab long-press**: Long-pressing a row in Library navigates to system UI Settings instead of a context menu. Repro: open Library tab, swipe to bottom, long-press at y≈350, app shows System UI settings dump.

5. **Downloads tab long-press**: Long-pressing a row in Downloads opens the Google Lens photo picker instead of a music context menu. Repro: open Downloads tab, long-press at y≈350, Lens/photo picker appears.

6. **Settings toggles**: All Settings rows are non-interactive; no toggles flip, no dialogs open. Rows like "Keep screen on", "Haptics", "Local music" show no response to taps. Repro: open Settings, tap any row, no visual change.

7. **Player: no track playback**: No way to open a track and enter the player. Track taps are not handled; the app has no working playback in this debug build. Repro: attempt to play any track from Home, Library, or Search — app returns to launcher or shows no response.

8. **Context menus / overflow menus**: No overflow or context menus are reachable via tap, menu key, or three-dot button. Repro: open any screen, press menu key or tap top-right corner, no menu appears.

9. **Blank screen on search query**: Search query submission produces a blank screen that reverts to the Settings view. Repro: enter text in Search tab, press search key, screen flashes blank then shows Settings.