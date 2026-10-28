# .agent: the shared vault every agent reads and writes

MusicForge is built by several agents (Claude Code, Codex, OpenCode, Kilo, Cline, Pi, Grok,
Antigravity, Muse, Hermes, Freebuff), most of them on free models through fcc. This folder is
their shared memory, so whatever one agent learns, the next one starts with. It's plain
markdown in git, reviewed like code.

**Read first:** `facts.md`, then `board.md`, then the newest file in `handoffs/`.

| File | What goes in it | Who writes |
|---|---|---|
| `facts.md` | Durable truths about the project: architecture, constraints, measured numbers | Anyone who learns one; keep it short, fix it when it's wrong |
| `gotchas.md` | Traps that cost someone time, with the fix | Anyone, right after it bites |
| `board.md` | The task board: open work, who claimed it, its branch | Claim before you start, update when you finish |
| `decisions/NNNN-title.md` | One file per non-trivial decision: context, choice, why, what it rules out | Whoever made the call |
| `handoffs/YYYY-MM-DD-agent.md` | A short recap at the end of a session: done, open, next | Every agent, every session |
| `models.md` | Which free model and harness is good for what, with evidence | Whoever measured it |
| `private/` | **Not pushed (gitignored).** Local paths, account notes, device ids | Local only |

## Rules (CI enforces the first one)

1. **This folder is public.** No emails, home-directory paths, usernames, device serials,
   tokens, cookies, keys, passwords or user data, ever. Those go in `private/`. The
   `vault-guard` CI step fails the build if one slips in.
2. Facts over chat: write what's true and how you know it, not what you did step by step.
3. One fact per line or bullet. Delete or fix stale entries; don't append contradictions.
4. Claim board items with your agent name and branch, so two agents don't do the same work
   (agents work in separate git worktrees; never two agents in one checkout).
5. Keep the voice of the repo: plain, short, a dry joke if it earned its place.
