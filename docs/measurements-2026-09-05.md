# Measurements, 2026-09-05

Machine: owner's Windows 11 PC, JDK 8, resource pack folder junctioned into `run/resourcepacks`:
248 files on disk (247 zips + 1 folder, 6.4 GiB); vanilla loads 175 of them — 72 zips have
`pack.mcmeta` nested in a folder and are ignored.
Dev client (Forge 1.8.9, no OptiFine), `-Dfastpacks.devOpenPacksGui=true`, 120 frames sampled.
The dev launch task is `runDevClient`, not Loom's `runClient` (see `build.gradle.kts`).

```
JAVA_HOME="/c/Program Files/Java/jdk-21.0.12.1" ./gradlew runDevClient -Pfastpacks.baseline=true -Pfastpacks.devOpenPacksGui=true --console=plain
JAVA_HOME="/c/Program Files/Java/jdk-21.0.12.1" ./gradlew runDevClient -Pfastpacks.devOpenPacksGui=true --console=plain
```

| Metric | Baseline (`-Dfastpacks.baseline=true`) | fastpacks | Ratio |
|---|---|---|---|
| Startup scan (`rescanned` line #1) | 1977 ms | 275 ms | 7.2x |
| Screen open scan (`rescanned` line #2) | 7644 ms | 94 ms | 81x |
| Avg frame on the packs screen | 10.70 ms | 0.61 ms | 17.5x |

The screen-open scan is the freeze this mod exists to remove: opening Resource Packs stalls the
client for 7.6 seconds on this folder, and 94 ms afterwards.

Log excerpts:

- baseline: `build/runclient-baseline.log` lines:

```
[20:05:45] [main/WARN] (fastpacks) fastpacks: BASELINE mode - optimisations disabled, timing only
[20:05:50] [main/INFO] (fastpacks) fastpacks: rescanned 175 packs in 1977 ms
[20:05:56] [main/INFO] (fastpacks) fastpacks initialised
[20:05:56] [main/WARN] (fastpacks) fastpacks: dev harness enabled - will auto-open the Resource Packs screen and exit
[20:06:00] [main/INFO] (fastpacks) fastpacks dev: opening GuiScreenResourcePacks
[20:06:07] [main/INFO] (fastpacks) fastpacks: rescanned 175 packs in 7644 ms
[20:06:12] [main/INFO] (fastpacks) fastpacks dev: 175 packs, avg frame 10.70 ms over 120 frames
```

- optimised: `build/runclient-optimised.log` lines:

```
[20:07:32] [main/INFO] (fastpacks) fastpacks: rescanned 175 packs in 275 ms
[20:07:38] [main/INFO] (fastpacks) fastpacks initialised
[20:07:38] [main/WARN] (fastpacks) fastpacks: dev harness enabled - will auto-open the Resource Packs screen and exit
[20:07:42] [main/INFO] (fastpacks) fastpacks dev: opening GuiScreenResourcePacks
[20:07:42] [main/INFO] (fastpacks) fastpacks: rescanned 175 packs in 94 ms
[20:07:46] [main/INFO] (fastpacks) fastpacks dev: 175 packs, avg frame 0.61 ms over 120 frames
```

All three mixins applied in the optimised run, and no `fastpacks: BASELINE mode` line was present:

```
[20:07:32] [main/INFO] (mixin) Mixing MixinResourcePackRepository from mixins.fastpacks.json into net.minecraft.client.resources.ResourcePackRepository
[20:07:32] [main/INFO] (mixin) Mixing MixinResourcePackRepositoryEntry from mixins.fastpacks.json into net.minecraft.client.resources.ResourcePackRepository$Entry
[20:07:42] [main/INFO] (mixin) Mixing MixinGuiListExtended from mixins.fastpacks.json into net.minecraft.client.gui.GuiListExtended
```

Neither run logged an exception.

## Against the spec's thresholds

| Threshold | Measured | |
|---|---|---|
| Baseline screen-open scan at least 1000 ms | 7644 ms | pass |
| Optimised screen-open scan at most 100 ms | 94 ms | pass |
| Optimised startup scan at most 200 ms | 275 ms | **miss** |

The optimised startup scan misses its target by 75 ms. It is the one scan that cannot hit a warm
cache — it is the first time the process touches the folder, so it pays for 248 directory entries
plus a `pack.mcmeta` read on each of the 175 loadable packs. A repeat run measured 263 ms, so the
number is stable rather than noise. The screen-open scan, the figure the freeze is actually made of,
is 81x faster and inside its budget.
