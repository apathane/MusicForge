# Models and harnesses: what works for what

Measured on 2026-10-28 through fcc (Free Claude Code, a local proxy that routes coding CLIs to
other providers). Raw results: the fcc delegation test. Free tiers change weekly: re-measure
before trusting this (`tools/model-scout.sh` checks who answers; a coding probe checks who's good).

## Free models that wrote correct code (Kotlin probe, run one at a time)

| Model (fcc id) | Time | Use it for |
|---|---|---|
| `gemini/models/gemini-3.5-flash` | 15 s | Fast default, quick edits, triage |
| `gemini/models/gemma-4-31b-it` | 43 s | Longer careful answers (wrote the most complete code) |
| `kilo/cohere/north-mini-code:free` | 66 s | Second-opinion review, small edits |
| `kilo/nvidia/nemotron-3-ultra-550b-a55b:free` | 79 s | Large-context reading, reviews |
| `kilo/dots-studio/dots-3-note-preview:free` | 93 s | Backup |
| `kilo/xingchen-agi/xing4.0-29b-a4b:free` | 101 s | Backup |
| `kilo/nvidia/nemotron-3.5-super-vl:free` | 115 s | Vision (screenshots); the plain-Hermes player review on it was the best review of the day |
| `kilo/poolside/laguna-s-2.1:free` | 120 s | Best free agentic coder (Kilo leaderboard); default for write tasks |
| `kilo/poolside/laguna-xs-2.1:free` | 135 s | Lighter Laguna |
| `kilo/recursive/flint-v1.0:free` | 140 s | Backup |
| `kilo/nvidia/nemotron-3.5-lightning:free` | 175 s | Mechanical edits, renames |

Didn't work that day:
- Gemini 3.1 Pro, 3.7 Flash and Pro Latest timed out.
- OpenCode Zen's free models answered 403.
- Cline Pass and OpenCode Go answered 403 (no subscription on this account).

## Harnesses through fcc (headless, permission prompts off)

| Harness | Command shape | Works |
|---|---|---|
| Claude Code | `fcc-claude -p "…" --dangerously-skip-permissions --model <fcc id>` | yes |
| Codex | `fcc-codex exec --dangerously-bypass-approvals-and-sandbox -m <fcc id> "…"`; native config already routes Codex through fcc | yes |
| OpenCode | `fcc-opencode run -m free-claude-code/<fcc id> "…"` | yes |
| Pi | `fcc-pi -p "…" --model free-claude-code/<fcc id>` | yes |
| Cline | `fcc-cline -y -m <fcc id> "…"` | yes (once failed a version check when run concurrently) |
| Grok | `fcc-grok -p "…" -m <fcc id> --always-approve` | yes (died once when fcc made no progress for 600 s under load) |
| Antigravity | `fcc-agy -p "…" --model <fcc id> --dangerously-skip-permissions` | yes |
| Muse | `fcc-muse exec --model <fcc id> "…"` | yes on Laguna; Flint returned HTTP 400 |
| Hermes | plain `hermes chat -q "…" --oneshot --yolo` (its own Kilo setup) | yes; fcc-hermes needs fcc's default model fixed |
| dsh | `fcc-dsh headless "…"` (no model flag) | needs fcc's default model fixed |

## Rules of thumb

- **fcc's limits:** it allows 2 requests at a time per provider, 1 per 2 s. Running nine agents at once makes them queue and time out. Run at most 3–4 at a time, spread across providers (Kilo, Gemini).
- **Write tasks:** Laguna S 2.1 (Claude Code or OpenCode harness), Gemma 4 31B, Nemotron 3 Ultra.
- **Reviews:** Nemotron 3.5 Super VL (Hermes), North Mini Code, Nemotron 3 Ultra.
- **Never merge on a worker's word:** CI builds it, a stronger model reviews it.
