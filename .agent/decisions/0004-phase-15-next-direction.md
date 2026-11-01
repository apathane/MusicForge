# 0004 · Phase 15 NEXT: workers, Automix direction, what MusicForge adds

Status: active (2026-11-01). Owner: "release this next as phase 15 NEXT" (P9). Brief:
the owner's writing-block (new selectable UI + local-music reliability), with these additions.

## 1. Free worker models: evidence, constraints, workarounds

Reddit itself blocks the coordinator's search tool, so evidence comes from pages that aggregate or
quote it, provider docs, and this project's own runs (the most reliable signal). Re-check
`kilo models | grep free` and `opencode models | grep free` before each wave: the pools rotate.

| Model (route) | Evidence | Use for |
|---|---|---|
| DeepSeek V4.1 Flash (`opencode/deepseek-v4.1-flash-free`) | Ours: W7 scanner fix followed a precise design exactly, with tests, clean commits and a vault handoff. Independent test: DeepSeek V4 Flash Free 5/5 on hard tasks. Vendor: leads DeepSWE / Terminal-Bench among the flash models; fastest TTFT. | **First choice for code.** |
| GLM 5.3 Flash (`opencode/glm-5.3-flash-free`) | Tops the r/LocalLLaMA community coding aggregate (aexol.ai: 74 vs DeepSeek 62); zero retention on OpenCode Zen; slower (TTFT ~6 s). | Second choice for code; research. |
| Nemotron 3 Ultra (`kilo/nvidia/nemotron-3-ultra-550b-a55b:free`) | Ours: accurate read-only scans with file:line; its Compose code did not compile (invented APIs) and broke design rules; upstream idle timeouts on long runs. Independent test: 2/5 on hard tasks. Zen lists it as trial use. | **Scans and reviews only.** |
| Nemotron 3.5 Super VL (kilo / opencode) | Ours: good MusicForge scan. No first-hand coding reports found. | Scans, second reviewer. |
| North Mini Code (`kilo/cohere/north-mini-code:free`) | Ours: vague scan with invented citations. | Avoid for anything that matters. |
| MiMo V2.6 Flash (`opencode/mimo-v2.6-flash-free`) | No first-hand reports; Zen says free-period data may train the model. | Untested; public code only. |
| Nemotron 3.5 Lightning | Owner: "prone to failure". | Never. |
| kilo-auto/free, openrouter/free | Router picks a free model per request (may log prompts). | Fallback only. |

Constraints and workarounds:
- **Limits.** Kilo free: 200 requests/hour per IP (Kilo docs). OpenCode Zen free: one server-side
  limit per account/network across all free models (opencode issue #56523), so switching models
  does not help. Owner: Kilo's limits are better than OpenCode's. Workaround: at most two workers
  at a time, one per route; `tools/queue.sh` falls through to the next route after a rate limit
  and waits 5 minutes.
- **Unattended runs.** `kilo run` stops at the first permission prompt unless `--auto`.
- **Timeouts.** Long Nemotron runs die with "Upstream idle timeout exceeded": keep briefs to one
  file set and under an hour.
- **Quality.** Every worker gets `tools/briefs/common.md` (compile-correctness first, only APIs seen
  in the repo, tokens from `ui/designsystem`, one joke, no pushing). Precise designs in the brief
  (what to build, the exact signatures, the rules) turned DeepSeek's output from rewrite-worthy to
  merge-ready. The coordinator writes the contract (signatures, call site) first; workers fill it.
- **Privacy.** MusicForge is public AGPL code, so free-tier data use is acceptable; never send
  keys, backups or user data.
- **When the coordinator pauses** (usage guard at 90 %): the queue keeps running ready briefs;
  workers commit to their own branches only; the coordinator reviews and merges on resume.
- **References stay fresh**: `tools/pull-refs.sh` pulls every repo under `upstream/` every 6 h.

## 2. Automix: closer to BitChord (and ConvX), visually and functionally

The engine is BitChord's (ported, AGPL); the presentation must follow it too, not a MusicForge
invention bolted on. From BitChord (verified in its source): the planned mix window drawn on the
seek bar before it arrives, the fill breathing on the blend's beat, a "Mixing" label that takes
the quality badge's place, analysis states (waiting / analysing / analysed / failed) shown
honestly, the two-deck mix view with three-band waveforms aligned at the mix (MusicForge already
receives `AutomixState.smartMixView` and the deck waveforms from the port), and Automix settings
worded as BitChord words them. ConvX: restraint in glass and how its sheets present the mix.
Never call a fade through silence beat-matched; no control for anything the engine doesn't do.

## 3. What MusicForge adds once the music is playing: the Set

Every music app plays music. MusicForge's answer to "now what?" is that it keeps working on what
comes next: **it forges your queue into a set.** The Forge interface shows the queue as a chain
of songs joined by seams, and each seam is the transition Automix has planned there: its style
(beat-matched blend, filter sweep, loop mix, plain crossfade), tempo and key fit, and how far
analysis has got. The seam being played glows and pulses on the beat; a tap opens the two-deck
view for that join. Everything shown comes from the engine's real plan, so the Set is honest by
construction. It turns Automix from a hidden setting into the thing you watch, and it is
Phase 15 work (Automix presentation + queue integration), not a new phase.

## 4. Release

P9 ships when the integration branch passes CI, the R8 check APK is driven on the emulator
(analysis runs, no crash), and the local-music safety fix is in. Old UI stays the default.
