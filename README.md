# fastpacks

resource pack load-time optimizer for forge 1.8.9 client mod.

With hundreds of packs installed, the vanilla Resource Packs screen freezes for seconds every
time it opens. Vanilla re-matches every pack on disk against its cached list using a comparison
that makes filesystem calls on each compare, inside O(N^2) list scans, so a few hundred packs
means hundreds of thousands of filesystem calls per open. Pack size has nothing to do with it.
fastpacks caches each pack's identity and refreshes it once per scan, so the work drops to 2N
filesystem calls. In my own testing a 20 second freeze became well under a quarter of a second.

## What it does

- Caches pack identity strings so a rescan costs 2N filesystem calls instead of ~6N^2. Behaviour is otherwise identical to vanilla.
- Decodes pack icons on background threads, showing the default icon until each real one arrives. Icons larger than 128 px are downscaled before upload.
- Skips drawing rows that are scrolled out of view in both pack lists. Works with or without OptiFine.
- Adds a toolbar above the lists: a search box, resolution chips (All / 16x / 32x / 64x / 128x+ / Overlay) and a match counter. Only the Available list is filtered; selected packs never disappear.
- Shows each pack's resolution at the right end of its row, read from the PNG headers of a dozen common PvP textures once per pack and cached in `config/fastpacks/packs.json`. "Overlay" means the pack has none of those textures. Delete the cache file to rescan.
- Logs `fastpacks: rescanned N packs in M ms` to `latest.log`.

## Speedup

From my own testing with a few hundred packs installed:

| Metric | Speedup |
|---|---|
| Startup scan | ~3.5x to 7x |
| Screen open scan | ~80x to 155x |
| Avg frame on the packs screen | ~17x |

The remaining startup time is vanilla work this mod does not touch: reading the directory and
opening every zip for its `pack.mcmeta`.

## Install

Download `fastpacks-0.2.0.jar` from the [Releases](https://github.com/as9pa/fastpacks/releases)
page and drop it into `.minecraft/mods` next to Forge 1.8.9. Compatible with OptiFine 1.8.9 HD U M5
as a mod jar and with other Mixin 0.7.11 mods. Uses `@Inject`, `@Redirect` and `@ModifyConstant`
only, no `@Overwrite`. The mixin config is marked required: if a future OptiFine or Forge build
changes a patched method, the game fails at launch with a Mixin error naming fastpacks instead of
silently running slow. Remove the jar to launch again.

## Build

Needs JDK 21 (to run Gradle) and JDK 8 (toolchain, auto-detected). Point `JAVA_HOME` at a JDK 21
install, then:

    gradlew build

Output: `build/libs/fastpacks-0.2.0.jar`. Tests: `gradlew test`.

Dev switches for `gradlew runDevClient`: `-Pfastpacks.baseline=true` (optimisations off, timing on),
`-Pfastpacks.devOpenPacksGui=true` (auto-open the screen, log frame time, exit).
