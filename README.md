# fastpacks

Forge 1.8.9 client mod. Makes the Resource Packs screen open instantly when you have hundreds of packs.

## Why the vanilla screen freezes

Every time the screen opens, vanilla re-matches each pack on disk against its cached list using a
comparison that performs two filesystem calls per compare, inside three O(N^2) list scans. With
248 files on disk and 175 loaded packs that is roughly 75,000 pack comparisons, each costing four
filesystem calls, on the order of 300,000 filesystem calls per open, roughly 7.6 seconds on the
owner's Windows PC. Pack size has nothing to do with it.

## What fastpacks does

- Caches each pack's identity string and refreshes it once per scan: 2N filesystem calls instead of ~6N^2. Behaviour is otherwise identical to vanilla.
- Decodes pack icons on background threads, showing the default icon until each real one arrives. Icons larger than 128 px are downscaled before upload.
- Skips drawing rows that are scrolled out of view in the two pack lists (OptiFine already does this; the mod works with or without OptiFine).
- Logs `fastpacks: rescanned N packs in M ms` so you can see it working in `latest.log`.

## Measured (owner's PC, 175 loadable packs, 6.4 GiB)

The pack folder has 248 files on disk (247 zips + 1 folder, 6.4 GiB); vanilla loads 175 of them
because 72 zips have `pack.mcmeta` nested inside a top-level folder, which the game ignores
(vanilla still opens each of them on every scan, fails to find `pack.mcmeta`, and drops it via an
exception).

| Metric | Vanilla | fastpacks | Ratio |
|---|---|---|---|
| Startup scan | 1977 ms | 275 ms | 7.2x |
| Screen open scan | 7644 ms | 94 ms | 81x |
| Avg frame on the packs screen (no OptiFine) | 10.70 ms | 0.61 ms | 17.5x |

Avg frame is render time between Forge's render-tick start and end events; it excludes the
frame-rate-cap wait, so it is not an FPS figure.

The optimised startup scan came in at 275 ms against a 200 ms design target (263 ms on a repeat
run). The remaining time is vanilla work this mod does not touch: reading 248 directory entries
and opening every zip for its `pack.mcmeta`, of which 72 fail with an exception, all before the
JIT has warmed up.

Details in `docs/measurements-2026-09-05.md`.

## Known limitation

Packs whose `pack.mcmeta` sits inside a folder in the zip are not loaded by vanilla and therefore
not by this mod either. Re-zip them so `pack.mcmeta` is at the root.

## Install

Drop `fastpacks-0.1.0.jar` into `.minecraft/mods` next to Forge 1.8.9. Compatible with OptiFine 1.8.9 HD U M5 as a mod jar and with other Mixin 0.7.11 mods. Uses `@Inject`/`@Redirect` only, no `@Overwrite`.

## Build

Needs JDK 21 (to run Gradle) and JDK 8 (toolchain, auto-detected). On Windows:

    set JAVA_HOME=C:\Program Files\Java\jdk-21.0.12.1
    gradlew build

Output: `build/libs/fastpacks-0.1.0.jar`. Tests: `gradlew test`.

Dev switches for `gradlew runDevClient`: `-Pfastpacks.baseline=true` (optimisations off, timing on),
`-Pfastpacks.devOpenPacksGui=true` (auto-open the screen, log frame time, exit).

## Roadmap

v2: resolution filters (16x/32x/64x), search box, persistent per-pack cache.
