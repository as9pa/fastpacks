# FastPacks design spec (v1)

**Date:** 2026-09-05
**Status:** approved by owner 2026-09-05 (chat + written spec)
**Target:** Minecraft 1.8.9, Forge 11.15.1.2318, client side only
**Name:** display name and mod id are both lowercase `fastpacks` for now. The owner will pick the final name later; renaming touches the mod id, base package, and two resource file names only.

## 1. Problem

Opening the Resource Packs screen freezes the client for several seconds when the
`resourcepacks` folder holds a few hundred packs. Measured on the owner's machine
(248 items, 236 loadable, 6.5 GB, Windows 11, JDK 8) by re-running the exact vanilla
algorithm against the real folder:

| Vanilla step | Cost | When |
|---|---|---|
| `File.listFiles` with the pack filter | 5 ms | every screen open |
| Open every zip's central directory | 73 ms | startup only |
| Read every `pack.mcmeta` | 5 ms | startup only |
| Decode every `pack.png` with ImageIO (154 icons) | 1,675 ms | startup only |
| Re-match on-disk packs against the cached list | **~6,000 ms** | **every screen open** |
| One `File.isDirectory()` + `File.lastModified()` pair | 35 us | unit cost |

Root cause (vanilla `ResourcePackRepository.updateRepositoryEntriesAll`, verbatim
1.8.9 source verified): for each file on disk it builds a new `Entry` and calls
`List.contains`, then `List.indexOf`, then finally `List.removeAll`. All three are
O(N^2) scans over a `List`, and `Entry.equals` compares `toString()` values, where
`toString()` performs two filesystem calls every time it is invoked and is never
cached. Total is about 3N^2 + N `toString()` calls, i.e. about 6N^2 + 2N filesystem
calls per screen open. With N = 236 that is roughly 340,000 filesystem calls at
35 us each.

Secondary costs: every `pack.png` is decoded on the main thread during startup, and
vanilla `GuiSlot.drawSelectionBox` calls `drawSlot` for every row every frame,
including rows scrolled out of view.

Zip size and icon size are **not** the cause for this owner (median pack 20 MB,
all icons at most 256 px).

## 2. Goals and non-goals

Goals (v1):

1. Opening the Resource Packs screen with hundreds of packs takes tens of
   milliseconds, not seconds, with behaviour otherwise identical to vanilla.
2. Startup no longer decodes pack icons on the main thread.
3. Off-screen rows in the two pack lists are not drawn.
4. Works alongside OptiFine 1.8.9 HD U M5 loaded as a mod jar, and alongside other
   Mixin 0.7.11 mods.
5. Measurable: the mod logs its own scan time so the owner can verify it, and the
   README publishes before/after numbers from the owner's machine.

Non-goals (v1):

- Speeding up applying a pack (`Minecraft.refreshResources`). Reloading all textures
  is inherent.
- Resolution filters (16x/32x/64x), search, folders/categories. These are v2 and
  are designed for, not built (see section 10).
- Loading `.7z` archives. Vanilla only loads `.zip` files and folders.
- Any config file or GUI settings.

## 3. Constraints verified against the owner's instance

- Forge 1.8.9 patches: none of `ResourcePackRepository`, `FileResourcePack`,
  `ResourcePackListEntry`, `GuiScreenResourcePacks`, `GuiListExtended` are patched.
  `GuiSlot` is patched only to extract a `drawContainerBackground` hook.
- OptiFine HD U M5 (checked by applying its xdelta patches to the vanilla 1.8.9 jar
  with OptiFine's own `Patcher` and diffing normalized bytecode):
  - `ResourcePackRepository`: one field made public; trivial recompile
    differences; **`updateRepositoryEntriesAll` unchanged.**
  - `ResourcePackRepository$Entry`: synthetic outer field renamed; `equals`
    recompiled with identical semantics; **`toString`, `hashCode`,
    `updateResourcePack`, `bindTexturePackIcon` unchanged.**
  - `GuiSlot`: OptiFine adds `drawContainerBackground` and **already skips
    `drawSlot` for off-screen rows when the list is a `GuiResourcePackList`**
    (the shipped jar contains the `instanceof GuiResourcePackList` check).
  - `GuiListExtended`, `GuiResourcePackList`, `GuiScreenResourcePacks`,
    `ResourcePackListEntry*`, `FileResourcePack`, `DynamicTexture`: untouched.
  - `TextureManager`, `TextureUtil`, `AbstractResourcePack`: patched; we call their
    public API only and never inject into them.
- Mixin 0.7.11 is already bootstrapped in the owner's instance by Meowtils and
  Clear Chat via `MixinTweaker`; our jar declares the same tweaker.
- Rule that follows from the above: **`@Inject` and `@Redirect` only. No
  `@Overwrite` anywhere.** Never inject into a method OptiFine modifies.

## 4. Architecture

Mod id `fastpacks`, display name `fastpacks`, base package `io.github.as9pa.fastpacks`.
One jar, no dependencies beyond Forge and the bundled Mixin runtime.

```
io.github.as9pa.fastpacks
|- FastPacks.java                      @Mod entry; registers DevHarness when enabled
|- PackKey.java                        pure: vanilla-format identity string for a File
|- ListCulling.java                    pure: isOffscreen(y, height, top, bottom)
|- icon/IconImages.java                pure: decode(InputStream) and downscale(img, max)
|- icon/IconLoader.java                background executor, placeholder, submit(File, sink)
|- EntryExtension.java                 duck interface implemented by the Entry mixin (must live outside the mixin package)
|- mixin/MixinResourcePackRepositoryEntry.java
|- mixin/MixinResourcePackRepository.java
|- mixin/MixinGuiListExtended.java
|- mixin/FastPacksMixinPlugin.java     IMixinConfigPlugin: baseline toggle
|- dev/DevHarness.java                 dev-only auto-open + frame timing
```

Pure classes have no Minecraft imports and are unit-tested. Mixins are thin and
delegate to them.

### 4.1 Cheap pack identity

Target: `net.minecraft.client.resources.ResourcePackRepository$Entry` via
`@Mixin(ResourcePackRepository.Entry.class)`; `Entry` is a public inner class in
the mapped jar, so the direct class reference works and no `targets =` string is
needed. The mixin implements `EntryExtension`, which lives in the root package
because Mixin refuses to load any class from the declared mixin package when it
is referenced directly.

- `@Shadow @Final private File resourcePackFile;`
- `@Unique private String fastpacks$key;`
- `fastpacks$refreshKey()`: `fastpacks$key = PackKey.compute(resourcePackFile)` where
  `PackKey.compute` returns exactly vanilla's
  `String.format("%s:%s:%d", name, isDirectory ? "folder" : "zip", lastModified)`.
- `@Inject(method = "<init>", at = @At("RETURN"))` calls `fastpacks$refreshKey()`.
- `@Inject(method = "toString", at = @At("HEAD"), cancellable = true)` returns
  the cached key (computing it if somehow null). Because vanilla `equals` and
  `hashCode` both call `toString()` virtually, they become string compares with no
  filesystem access.

Target: `net.minecraft.client.resources.ResourcePackRepository`.

- `@Shadow private List<ResourcePackRepository.Entry> repositoryEntriesAll;`
  (OptiFine may widen fields to public; `@Shadow` matches by name so either works.)
- `@Inject(method = "updateRepositoryEntriesAll", at = @At("HEAD"))`: for every
  existing entry that is an `EntryExtension`, call `fastpacks$refreshKey()`; record
  `System.nanoTime()` in a `@Unique` field.
- `@Inject(method = "updateRepositoryEntriesAll", at = @At("RETURN"))`: log one
  INFO line: `FastPacks: rescanned {} packs in {} ms`.

Semantics: refreshing every cached key at the start of each scan makes the
comparison results identical to vanilla, which re-reads the filesystem on every
compare; we simply read each file's state once per scan (2N filesystem calls
instead of about 6N^2). A pack replaced on disk between scans is treated exactly
as vanilla treats it.

### 4.2 Background icon loading

Target: the same Entry mixin.

- `@Shadow private BufferedImage texturePackIcon;`
- `@Shadow private ResourceLocation locationTexturePackIcon;`
- `@Unique private volatile BufferedImage fastpacks$pendingIcon;`
- `@Redirect(method = "updateResourcePack", at = @At(value = "INVOKE",
  target = "Lnet/minecraft/client/resources/IResourcePack;getPackImage()Ljava/awt/image/BufferedImage;"))`
  handler `fastpacks$deferIcon(IResourcePack pack)`. This matches both call sites
  in the method (the pack's own icon and the default-pack fallback). Behaviour:
  - if `pack instanceof DefaultResourcePack`: return `IconLoader.placeholder()`.
  - otherwise: `IconLoader.submit(resourcePackFile, image -> fastpacks$pendingIcon = image)`
    and return `IconLoader.placeholder()`.
  Because the first call returns non-null, vanilla's `if (texturePackIcon == null)`
  fallback branch never executes; it is handled anyway for safety.
- `@Inject(method = "bindTexturePackIcon", at = @At("HEAD"))`: on the client
  thread, if `fastpacks$pendingIcon != null`: move it into `texturePackIcon`, clear
  the pending field, and if `locationTexturePackIcon != null` call
  `textureManager.deleteTexture(location)` and set the field to null. Vanilla then
  creates the `DynamicTexture` from the new image and binds it as usual.

`IconLoader`:

- Lazily-created fixed pool of `clamp(cores - 1, 1, 4)` daemon threads named
  `FastPacks-IconLoader-N`, priority `Thread.MIN_PRIORITY`. Static; must not touch
  `Minecraft`, Forge, or GL, because the repository is constructed before mod
  init.
- `placeholder()`: decoded once on first use from the vanilla jar's root
  `pack.png` via `IconLoader.class.getResourceAsStream("/pack.png")` (the same
  image `DefaultResourcePack.getPackImage()` returns). If unavailable, a generated
  32x32 opaque dark-grey image. The result is cached for the session.
- `submit(File packFile, Consumer<BufferedImage> sink)`: enqueue a task that opens
  the pack (folder: `pack.png` child; zip: `ZipFile` entry `pack.png`), decodes
  via `IconImages.decode`, downscales via `IconImages.downscale(img, 128)` if either
  side exceeds 128, closes the zip, and calls the sink. Any exception is logged at
  DEBUG and the sink is not called, leaving the placeholder in place (vanilla shows
  the default icon in the same cases).
- `IconImages.downscale` uses `Graphics2D` with bilinear interpolation into a
  `TYPE_INT_ARGB` image preserving aspect ratio.

Downscaling is a guard, not a speed-up for this owner: with all current icons at or
below 256 px it changes nothing measurable. It exists because a single 2048 px or
4096 px `pack.png`, common in downloaded PvP packs, costs 16 to 64 MB of texture
upload on the render thread the first time it scrolls into view.

Threading: background threads write only `fastpacks$pendingIcon` (volatile). All
reads of `texturePackIcon`/`locationTexturePackIcon` and all GL calls remain on
the client thread inside `bindTexturePackIcon`. Orphaned entries whose load
completes after they were discarded simply hold an image until GC.

### 4.3 Off-screen row culling

Target: `net.minecraft.client.gui.GuiListExtended` (declares `drawSlot`; untouched
by Forge and OptiFine). The mixin class `extends GuiSlot` so it can read the
protected `top` and `bottom` fields.

- `@Inject(method = "drawSlot", at = @At("HEAD"), cancellable = true)`: if
  `this instanceof GuiResourcePackList` and
  `ListCulling.isOffscreen(y, height, top, bottom)`, cancel.
- `ListCulling.isOffscreen(y, height, top, bottom)` returns
  `y > bottom || y + height < top`, the same test vanilla already uses to decide
  the off-screen `setSelected` branch. Rows straddling either edge are still drawn.

Scoped to the two pack lists on purpose: other `GuiListExtended` subclasses from
other mods are left alone. With OptiFine present this injection is redundant and
harmless.

### 4.4 Timing log

Covered by the RETURN injection in 4.1. Always on in release builds; one line per
scan.

### 4.5 Dev-only harness (off unless a system property is set)

- `-Dfastpacks.baseline=true`: `FastPacksMixinPlugin.shouldApplyMixin` returns false
  for `MixinResourcePackRepositoryEntry` and `MixinGuiListExtended`, so the timing
  line measures vanilla behaviour in the same environment. The repository mixin
  still applies; its key-refresh loop is a no-op because no entry implements
  `EntryExtension`.
- `-Dfastpacks.devOpenPacksGui=true`: `FastPacks` registers `DevHarness` on the
  Forge event bus. On the first `GuiOpenEvent` for `GuiMainMenu`, it schedules
  `mc.displayGuiScreen(new GuiScreenResourcePacks(mainMenu))`. While that screen
  is current it times whole frames using `TickEvent.RenderTickEvent` START/END
  pairs; after 120 frames it logs
  `FastPacks dev: {} available packs, avg frame {} ms over {} frames` and calls
  `mc.shutdown()`. Nothing in `DevHarness` is referenced unless the property is set.

## 5. Mixin configuration

`src/main/resources/mixins.fastpacks.json`:

```json
{
  "required": true,
  "minVersion": "0.7",
  "package": "io.github.as9pa.fastpacks.mixin",
  "plugin": "io.github.as9pa.fastpacks.mixin.FastPacksMixinPlugin",
  "refmap": "mixins.fastpacks.refmap.json",
  "compatibilityLevel": "JAVA_8",
  "client": [
    "MixinResourcePackRepositoryEntry",
    "MixinResourcePackRepository",
    "MixinGuiListExtended"
  ],
  "injectors": { "defaultRequire": 1 }
}
```

`defaultRequire = 1` on purpose: if a target method is missing the game fails
loudly at startup instead of silently running slow.

Jar manifest: `TweakClass: org.spongepowered.asm.launch.MixinTweaker`,
`MixinConfigs: mixins.fastpacks.json`, `ForceLoadAsMod: true`,
`FMLCorePluginContainsFMLMod: true`.

`mcmod.info`: modid `fastpacks`, name `fastpacks`, version from Gradle, mcversion
1.8.9, author as9pa.

## 6. Build and toolchain

Based on the community template `lineargraph/Forge1.8.9Template` (formerly
nea89o), adapted:

| Item | Value |
|---|---|
| Gradle | 8.8 wrapper, run on the installed JDK 21 (`C:\Program Files\Java\jdk-21.0.12.1`) |
| Plugins | `gg.essential.loom` 0.10.0.+, `dev.architectury.architectury-pack200` 0.1.3, `com.github.johnrengelman.shadow` 8.1.1 |
| Compile toolchain | Java 8, auto-detected from `C:\Program Files\Eclipse Adoptium\jdk-8.0.504.1-hotspot` |
| Minecraft / mappings / Forge | `com.mojang:minecraft:1.8.9`, `de.oceanlabs.mcp:mcp_stable:22-1.8.9`, `net.minecraftforge:forge:1.8.9-11.15.1.2318-1.8.9` |
| Mixin | runtime `org.spongepowered:mixin:0.7.11-SNAPSHOT` bundled via shadow (not relocated); annotation processor `org.spongepowered:mixin:0.8.5-SNAPSHOT` for the refmap |
| Tests | JUnit 4.13.2; the `test` task runs on the Java 8 toolchain |
| `runDevClient` (dev launches) | plain `JavaExec` task copying Loom's run config (main class, classpath, JVM args) because Loom 0.10's `runClient` fails Gradle 8.8 task validation; **explicitly pinned** to the Java 8 toolchain launcher; forwards `-Pfastpacks.baseline` and `-Pfastpacks.devOpenPacksGui` as system properties; `mixin.debug=true` in dev |
| Output | `remapJar` produces `build/libs/fastpacks-<version>.jar`; intermediates go to `build/intermediates` |
| DevAuth | not used; the offline dev client is sufficient |

Dev run directory `run/` is git-ignored. For measurements, `run/resourcepacks` is a
Windows directory junction to `%APPDATA%\.minecraft\resourcepacks` (read-only use;
vanilla never writes into that folder).

Repository: local git in `C:\Users\alexa\projects\packs` on `main`, identity
`as9pa` (verified), no remote in v1. The README carries the measured before/after
table so the numbers can be published with the mod.

## 7. Error handling

| Situation | Behaviour |
|---|---|
| `pack.png` missing, corrupt, or zip unreadable in background | DEBUG log; placeholder stays (vanilla shows the default icon in the same cases) |
| Pack deleted while its icon is loading | task fails as above; entry disappears on next scan |
| Pack replaced on disk between scans | key refresh sees the new timestamp; identical to vanilla |
| Mixin target missing (wrong MC version, unexpected patch) | `required` and `defaultRequire = 1` produce a hard failure at startup with a clear Mixin error |
| Placeholder resource missing | generated 32x32 grey image; never null |
| Game exits with loads pending | daemon threads; no shutdown hook needed |

## 8. Testing

Unit tests (JUnit, pure Java, no Minecraft classes, run with `gradlew test`):

- `PackKeyTest`: zip file gives `name:zip:mtime`; folder gives `name:folder:mtime`;
  output equals the vanilla `String.format` expression for the same `File`.
- `IconImagesTest`: 16x16 PNG decodes unchanged; 512x256 downscales to 128x64 with
  ARGB type; 100x300 downscales to 42x128 (aspect preserved, long side capped);
  corrupt bytes produce `null`; `downscale` returns the same instance when already
  within bounds.
- `ListCullingTest`: fully above, fully below, straddling top, straddling bottom,
  fully visible; boundary equality cases match `y > bottom || y + height < top`.

Dev-client verification (owner's real folder via junction, JDK 8, no OptiFine):

1. `gradlew runDevClient` with `-Dfastpacks.baseline=true -Dfastpacks.devOpenPacksGui=true`:
   capture the `rescanned ... ms` and `avg frame` lines.
2. Same with baseline off: capture again.
3. Confirm the log shows `Mixing MixinResourcePackRepositoryEntry ... into
   net.minecraft.client.resources.ResourcePackRepository$Entry` and the two other
   mixins, with no Mixin errors from `mixins.fastpacks.json`.
4. Confirm the startup rescan line reports well under 200 ms (icon decoding no
   longer on the client thread).

Manual verification by the owner (with OptiFine): drop the jar in
`.minecraft/mods`, launch, open Options, then Resource Packs, observe no freeze, and
read the `FastPacks: rescanned` line in `latest.log`.

## 9. Acceptance criteria

- All unit tests pass.
- Baseline scan on the owner's folder at least 1,000 ms; optimized scan at most
  100 ms, logged by the same code path in the same session.
- Startup scan at most 200 ms. Measured 275 ms (263 ms on a repeat run); accepted
  because the remaining time is vanilla directory and `pack.mcmeta` work on 248
  files, 72 of which throw, before JIT warm-up.
- Three mixins apply with no errors; the game reaches the main menu and the
  Resource Packs screen renders icons that swap from placeholder to real icons.
- Built jar loads under Forge 1.8.9 with OptiFine M5 present (owner-confirmed).
- README contains the before/after table from the dev-client run.

## 10. v2 direction (informative, not built now)

- Persistent per-pack cache in `config/fastpacks/packs.json` keyed by
  `name:size:mtime`, populated by the same background executor as 4.2.
- Resolution detection by reading only the PNG header (IHDR width) of a few
  canonical textures inside each pack, e.g. `assets/minecraft/textures/blocks/stone.png`
  and `assets/minecraft/textures/items/diamond_sword.png`, without decoding pixels.
- Filter chips (16x, 32x, 64x, 128x+, unknown) and a search box added to the
  vanilla screen via `GuiScreenResourcePacks` injections, filtering the
  `availableResourcePacks` list.

## 11. Risks

- The KeystrokesMod jar in the owner's instance is an obfuscated coremod whose
  transforms cannot be inspected. Conflict is unlikely and would show as a Mixin
  error naming our config.
- Gradle 8.8 on JDK 21 is documented as supported (up to Java 22) but the
  template's CI only tests JDK 17. Fallback: let Foojay provision a JDK 17 for the
  Gradle daemon.
- Windows long paths are disabled on this machine (`LongPathsEnabled` absent). The
  project path is short; if Loom's cache paths overflow, enabling the setting is
  an admin registry change the owner must make.
- The 1.8.9 Mixin runtime is 2017 code. Everything used here (`@Inject`, `@Redirect`,
  `@Shadow`, `@Unique`, `targets`, `IMixinConfigPlugin`) is verified present in the
  0.7 branch source.
