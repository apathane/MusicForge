# Transfer benchmarks

## 2026-10-22: googlevideo transfer strategies (Crescendo 3b)

**Setup.** The stream was resolved by the app on the emulator (InnerTubeX client VISIONOS). The debug build dumped URL and headers to `cache/last_stream.txt`, and the host replayed it with `tools/transfer_bench.py` and a threshold probe. The emulator NATs through the host, so it's the same public IP the URL is bound to. Home connection, ~50 Mbit/s.

### 3.3 MB song ("Teardrop")
| Strategy | Run 1 | Run 2 |
|---|---|---|
| plain unbounded GET | 0.03 MB/s | 0.03 MB/s |
| whole-file `&range=0-N` in the URL (Metrolist downloads) | 5.79 MB/s | 1.91 MB/s |
| whole-file `Range:` header | 6.09 MB/s | 6.22 MB/s |
| 2 MiB `Range:` chunks (BitChord) | 5.50 MB/s | 5.00 MB/s |

### 70.1 MB stream (3-hour jazz piano video)
| Strategy | Throughput |
|---|---|
| whole-file `&range=0-N` | **0.03 MB/s** (0.9 MB in 32 s) |
| whole-file `Range:` header | **0.03 MB/s** |
| 2 MiB chunks | **5.16 MB/s** (63 MB in 12 s) |

### Single bounded range size → throughput (same 70 MB stream, from byte 1,000,000)
| Range | Throughput |
|---|---|
| 1 MiB | 5.06 MB/s |
| 2 MiB | 5.83 MB/s |
| 4 MiB | 6.27 MB/s |
| 6 MiB | 6.36 MB/s |
| 8 MiB | 6.09 MB/s |
| 12 MiB | **0.03 MB/s** |
| 16 MiB | 0.03 MB/s |
| 32 MiB | 0.03 MB/s |

### Conclusions
1. googlevideo throttles any request that asks for more than ~10 MiB (the threshold is between 8 and 12 MiB) to roughly playback speed. An unbounded GET counts as "more".
2. Metrolist's whole-file `&range=` is therefore fast only for songs under ~10 MB. Long tracks (mixes, live sets, podcasts, long videos) download ~170× slower than they could.
3. BitChord's chunking is the correct general fix. Chunk size matters little between 2 and 8 MiB. **Default is now 4 MiB** (2.5× under the cap, half the round trips of 2 MiB). InnerTubeX's per-client policy still takes precedence when it sets one (VISIONOS asked for 2 MiB).
4. End to end in the app: a 73.8 MB download, killed with `kill -9` at 8.2 MB, was restarted by the system ~10 s later and finished at ~3 MB/s through Media3 → chunked (the cache-write path on the emulator is slower than raw HTTP).

## 2026-10-23: rendering on the emulator (release build, R8)

The emulator was restarted with `-gpu host` (Mesa Iris Xe through the emulator's GLES translator). swiftshader software rendering gives meaningless GPU numbers (p50 GPU 4950 ms). The Iris Xe is several times faster than an Adreno 610, so treat GPU numbers as optimistic. UI-thread numbers transfer better.

| Scenario | Frames | Janky | p50 / p90 / p99 | Slow UI thread | Slow issue-draw |
|---|---|---|---|---|---|
| Search results list, 10 fast flings | 169 | 1.8 % | 16 / 18 / 20 ms | 0 | 3 |
| Player open + drag-close ×3 (before) | 285 | 13.3 % | 16 / 17 / 32 ms | 3 | 35 |
| … sheet on one graphicsLayer, artwork one layer | 276 | 15.9 % | 16 / 19 / 22 ms | 2 | 41 |
| … + no drawing under the fully open sheet | 274 | 17.2 % | 16 / 18 / 22 ms | 2 | 45 |
| A/B: no artwork shadow | 270 | 17.8 % | – / – / 24 ms | 2 | 46 |
| A/B: no blurred backdrop | 275 | 15.3 % | – / – / 24 ms | 2 | 40 |

Reading: the UI thread is clean (≤ 3 slow frames in every run); the tail improved (p99 32 → 22 ms). The remaining "janky" frames are all *issue draw commands* on the render thread while two full-screen layers animate, and neither the shadow nor the backdrop explains them. This is likely the emulator's GL-translation overhead per call. **It needs a check on the real Honor X7b** (`adb shell dumpsys gfxinfo dev.musicforge.app` after opening/closing the player a few times).

Cold start (release, first launch after install, no baseline profile): 2.7–3.6 s on the emulator. Next: a baseline profile.

## 2026-10-23 night: Aria Home on the GPU emulator (release, R8)

Fresh AVD `x7b` (API 33, 1080×2412) with `-gpu host` on the reinstalled laptop (Iris Xe, Mesa 26.2.5 through the emulator's GLES translator), the user's restored Metrolist library (8,446 songs, 209 playlists, 256 plays). `dumpsys gfxinfo` after warming images once.

| Scenario | Frames | Janky | p99 | Slow UI thread |
|---|---|---|---|---|
| Long flings top → bottom → top ×2 (crosses into new shelves and triggers YouTube paging) | 920 | 8.3 % | 97 ms | 32 |
| Local sections only (Jump back in → playlists), 20 short swipes | 836 | 3.0 % | 29 ms | 1 |
| YouTube feed only, 10 short swipes | 461 | 1.5 % | 27 ms | 0 |

Reading: in steady scrolling the UI thread is clean on both halves of Home; the slow UI frames in the long-fling run come from shelves composing for the first time and continuation pages arriving mid-fling. Emulator GPU numbers are directional only; the X7b numbers are still to be taken.
