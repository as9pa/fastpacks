# fastpacks v2: resolution filter, search, per-pack cache

Status: approved by the owner on 2026-09-06 (layout as drawn, row badge included).
Wireframe: https://claude.ai/code/artifact/e051ce8b-3211-4bcb-953b-a5ad29d5357e
Builds on v1 (`2026-09-05-fastpacks-design.md`). Version bumps to 0.2.0.

## Goal

With 246 packs, finding the one you want on the Resource Packs screen means scrolling through
seven screens of rows. v2 adds a toolbar above the lists: a search box, one row of resolution
chips and a match counter. Resolution is detected once per pack on the v1 background threads
and cached on disk, so the screen never waits on it.

## Scope

In:

- Toolbar on `GuiScreenResourcePacks`: search box, chips All / 16x / 32x / 64x / 128x+ / Overlay, match counter.
- Filtering applies to the Available list only. Selected packs never disappear.
- Resolution detection by sampling PvP-relevant textures, with an on-disk cache.
- Active chip remembered while the game runs.
- Resolution badge at the right end of every row in both lists.
- Fix the Forge warning about `@Mod` lacking `version`.

Out (unchanged from the v1 roadmap): folders, sorting, a settings GUI, changing how packs load or apply.

## Screen layout (game pixels, GUI-scale independent)

Vanilla positions that stay: title at y = 16; lists 200 wide at x = W/2 - 204 and W/2 + 4 with
bottom = H - 51; buttons "Open resource pack folder" (W/2 - 154, H - 48, 150x20) and "Done"
(W/2 + 4, H - 48, 150x20); caption "(Place resource pack files here)" at (W/2 - 77, H - 26).

New:

| Element | Position | Size |
|---|---|---|
| Toolbar row | x = W/2 - 204, y = 28 | 408 x 20 |
| Search box (`GuiTextField`) | x = W/2 - 204, y = 28 | 140 x 20 |
| Chips (`GuiButton` subclass) | start x = W/2 - 60, y = 28, 2 px gaps | 20 tall, width = label width + 10 |
| Match counter | right-aligned to x = W/2 + 204, vertically centred in the row | text, colour 0xA0A0A0 |
| Both lists | top moves from 32 to 52 | header and rows follow the list top as in vanilla |
| Row badge | right edge at row x + 188, y + 1, in both lists | text, colour 0xA0A0A0: `16x`, `32x`, `64x`, `128x`, `overlay`; nothing for unknown or pending |

The lists lose 20 px of height (about half a row). Header text, row pitch (36), icon (32x32),
name/description offsets, hover arrows and the scroll bar are vanilla.

Search box: placeholder "Search packs" in 0x707070 when empty and unfocused; max 40 chars.
Chips: vanilla button look; the active chip permanently shows the hovered look (blue-grey face,
text 0xFFFFA0). Exactly one chip is active; All is the default.
Counter: `<shown> of <total>` where total is the Available list size before filtering.
Row badge: the pack name is trimmed to 118 px instead of vanilla's 157 so the two never overlap.
The Default pack shows `16x`.

## Behaviour

- Search matches case-insensitively against the pack name and the description text shown on the
  row (both description lines). Substring match, trimmed. Cleared when the screen closes.
- Chip semantics: 16x, 32x, 64x match the bucketed resolution exactly; 128x+ matches 128 and
  above; Overlay matches packs whose scan found none of the sampled textures; All shows
  everything, including packs whose resolution is unknown or still being scanned. Unknown or
  pending packs appear under All only.
- Search and chip combine with AND.
- Moving a pack Selected -> Available (vanilla inserts at index 0 of the available list) works
  as in vanilla; if the pack fails the current filter it is hidden but still present. The counter
  reflects that.
- Active chip persists in a static field for the life of the process. Not written to disk.
- Filtering never mutates the vanilla `availableResourcePacks` list. The Available list widget
  reads through a filtered view (see Mixins).
- Scrolling: when the filter changes, vanilla's `bindAmountScrolled` clamps the scroll offset on
  the next frame; nothing extra needed.

## Resolution detection

Runs inside the same background task that already loads the icon (v1 `IconLoader`), so each
pack file is opened once. Order: cache lookup first; on a miss, scan and then write through.

Sampled textures, all under `assets/minecraft/textures/`:

- items: `diamond_sword.png`, `iron_sword.png`, `bow_standing.png`, `fishing_rod_uncast.png`, `apple_golden.png`, `ender_pearl.png`
- blocks: `stone.png`, `planks_oak.png`, `sandstone_normal.png`, `wool_colored_white.png`, `dirt.png`, `cobblestone.png`

For each one present, read the PNG width from the IHDR chunk (big-endian int at byte offset 16)
without decoding the image. Result:

- No sampled texture present, but the pack could be opened: `OVERLAY`.
- One or more present: width = the most common width; on a tie, the larger. Bucket: <= 16 -> 16,
  <= 32 -> 32, <= 64 -> 64, otherwise 128 (shown under 128x+).
- Pack could not be opened, or the file vanished: `UNKNOWN`.

Zip packs: `ZipFile.getEntry` per path (central directory lookups, no decompression beyond the
first 24 bytes of a hit). Folder packs: `new File(dir, path)`. The default pack is never scanned
(vanilla, 16x).

## Cache

File: `config/fastpacks/packs.json`, Gson (bundled with 1.8.9). Shape:

```json
{ "version": 1,
  "packs": { "Aether 16x.zip:123456:1725580000000": { "res": 16, "kind": "TEXTURES" } } }
```

Key = `fileName:sizeBytes:lastModifiedMillis`. `kind` is `TEXTURES`, `OVERLAY` or `UNKNOWN`; `res`
is the bucket for `TEXTURES`, 0 otherwise. Unknown results are cached too, so a broken zip is not
re-opened every launch; deleting the file resets everything.

Loaded once, lazily, on first use (client thread, a few hundred entries, well under a
millisecond). Written by the background pool when the scan queue drains (a "dirty" flag plus a
single writer task), to a temp file then renamed. Missing, unreadable or wrong-version file ->
start empty, log once at INFO. Never throws into the game.

## Code layout

New, plain classes (never in the mixin package):

- `filter/PackResolution.java`: immutable value `{ kind, res }` plus `bucket(int width)` and
  `fromWidths(List<Integer>)`.
- `filter/PngHeader.java`: `static int width(InputStream)` returning -1 on anything short or
  non-PNG.
- `filter/ResolutionScanner.java`: `scan(File)` -> `PackResolution`; owns the sampled path list.
- `filter/PackCache.java`: load/get/put/flush; key builder.
- `filter/PackFilter.java`: `enum Chip { ALL, R16, R32, R64, R128, OVERLAY }`,
  `matches(Chip, String query, String name, String description, PackResolution)`; the static
  `activeChip` lives here.
- `gui/ChipButton.java`: `GuiButton` subclass with an `active` flag; overrides `drawButton` so the
  active chip draws with hover state and hover text colour.
- `gui/PacksToolbar.java`: owns the `GuiTextField`, chips, counter drawing and the filtered view
  (`List<ResourcePackListEntry> visible`, recomputed at most once per frame and on every filter
  change). One instance per open screen, held by the screen mixin.
- `EntryExtension` gains `PackResolution fastpacks$resolution()` (and a setter used by the
  scanner callback, delivered on the client thread the same way icons are).

Changed:

- `icon/IconLoader` becomes the shared scanner entry point: the task loads the icon and, on a
  cache miss, the resolution; both results are handed back through the existing sink pattern.
- `FastPacks`: `version = "0.2.0"` constant in the `@Mod` annotation; `gradle.properties` and
  `mcmod.info` bumped to match.

## Mixins

All `@Inject` / `@Redirect` / `@ModifyConstant`, no `@Overwrite`, no lambdas, `defaultRequire = 1`.

- `MixinGuiScreenResourcePacks`
  - `initGui` RETURN: create the toolbar, call `setDimensions(200, height, 52, height - 51)` on
    both lists, add the chip buttons to `buttonList`, restore the remembered chip and the empty query.
  - `drawScreen` RETURN: draw text box, placeholder and counter (buttons draw themselves).
  - `actionPerformed` HEAD: if the button is a `ChipButton`, activate it and cancel.
  - `keyTyped` HEAD cancellable: forward to the text field when focused; Escape still closes.
  - `mouseClicked` HEAD: forward to the text field (focus / caret).
  - `updateScreen` RETURN: `updateCursorCounter`.
- `MixinGuiResourcePackList`
  - `getSize` and `getListEntry` HEAD cancellable, only when `(Object) this instanceof
    GuiResourcePackAvailable` and a toolbar is attached: answer from the filtered view.
- `MixinResourcePackRepositoryEntry` (v1): add the resolution field and accessor.
- `MixinResourcePackListEntry`
  - `drawEntry` RETURN: draw the badge right-aligned at x + 188, y + 1 from the entry's resolution.
  - `@ModifyConstant` 157 -> 118 on the name trim in `drawEntry` (the only 157 literal in that method).

Compatibility (verified 2026-09-06 against OptiFine HD U M5's `patch/*.xdelta` list): none of
`GuiScreenResourcePacks`, `GuiResourcePackList`, `GuiResourcePackAvailable`, `GuiResourcePackSelected`,
`ResourcePackListEntry`, `ResourcePackListEntryFound`, `ResourcePackListEntryDefault`, `GuiScreen`,
`GuiButton` or `GuiTextField` is patched. `GuiSlot` is patched (drawScreen, handleMouseInput,
drawSelectionBox, getSlotIndexFromScreenCoords, actionPerformed) but v2 never injects into it; it only
calls the unchanged `setDimensions` / `setSlotXBoundsFromLeft`, and the patched methods still reach
rows through the virtual `getSize()` / `getListEntry()` that the list mixin answers.

## Tests (JUnit 4, plain classes only)

- `PngHeaderTest`: real 16/32/64/128-px PNGs from `ImageIO`, truncated stream, non-PNG bytes, empty stream.
- `PackResolutionTest`: bucketing at the boundaries (16, 17, 32, 33, 64, 65, 128, 512), mode with
  tie to larger, empty list -> OVERLAY.
- `ResolutionScannerTest`: temp zip with sampled textures at mixed widths; temp folder pack;
  zip with none (OVERLAY); unreadable file (UNKNOWN); a zip with `pack.mcmeta` nested in a folder
  is UNKNOWN because vanilla never loads it (assert nothing crashes).
- `PackCacheTest`: round trip through a temp file; missing file -> empty; wrong version -> empty;
  key format.
- `PackFilterTest`: each chip against each kind/res; query on name, on description, case
  insensitivity, whitespace trimming; AND of both.

The GUI classes and mixins are checked by hand in the dev client (`gradlew runDevClient`) and by
the owner in the real game with OptiFine.

## Performance budget

- Screen open with 246 packs: no measurable change from v1 (the filtered view is one pass over
  246 entries per frame; well under 0.1 ms).
- Scanning all 246 packs from a cold cache on the background pool: under 5 s total, invisible to
  the player; from a warm cache: zero pack opens beyond v1's icon load.

## Decisions taken

- 2026-09-06: layout as drawn in the wireframe (toolbar y = 28, lists at 52).
- 2026-09-06: row badge included in both lists.
