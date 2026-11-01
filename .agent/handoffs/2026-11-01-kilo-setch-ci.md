# 2026-11-01 kilo — The Set CI fix (branch agent/p15-set)

## Done
- Fixed app/src/main/kotlin/dev/musicforge/ui/forge/TheSet.kt so it compiles:
  - added `import androidx.compose.runtime.getValue` + `setValue` — `State<T>` only exposes a no-arg `getValue()`, so `by collectAsStateWithLifecycle()` delegates failed without them (lines 61-70, 162-163)
  - removed `import androidx.compose.foundation.layout.weight` — it resolved to the top-level `weight` that touches internal `RowColumnParentData.weight`; Row/Column member extensions cover the call sites
  - added missing `androidx.foundation.lazy.LazyColumn` and `androidx.foundation.layout.Box` imports
  - `private const val SeamHeight = 40.dp` → `private val` (const can't hold a Dp)
  - key assignment `spare[it.mediaId]?.removeFirstOrNull() ?: nextKey.longValue++` put a `Unit` expression where a `Long` was expected; rewritten with `run { nextKey.longValue++; nextKey.longValue }`
- No behavior change; design intact. One code joke added (Personality rule).
- Not compiled locally (local builds forbidden by the owner); CI verifies via scripts/ci-check.sh.

## Not done / risks
- Smart casts on delegated vals (e.g. `view != null -> Blend(view)`) compile in this repo (ForgeRoot does the same with `now`), but unverified locally until CI runs.
