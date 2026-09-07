# fastpacks v2 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a search box, resolution filter chips, a match counter and per-row resolution badges to the 1.8.9 Resource Packs screen, with resolution detected once per pack in the background and cached on disk.

**Architecture:** Plain, MC-free classes under `filter/` do detection, caching and matching (unit-tested). A `PacksToolbar` (under `gui/`) owns the text field, chip buttons and a filtered view list; three new mixins wire it into `GuiScreenResourcePacks`, `GuiResourcePackList` and `ResourcePackListEntry`. Detection piggybacks on the v1 background pool and the v1 `Entry` mixin, which already runs once per new pack.

**Tech Stack:** Minecraft 1.8.9, Forge 11.15.1.2318, MCP stable_22, SpongePowered Mixin 0.7.11 (runtime) / 0.8.5 AP, Gson 2.2.4 (bundled with MC), JUnit 4.13.2, Gradle 8.8 on JDK 21 with a Java 8 toolchain.

**Spec:** `docs/superpowers/specs/2026-09-06-fastpacks-v2-design.md` (wireframe: https://claude.ai/code/artifact/e051ce8b-3211-4bcb-953b-a5ad29d5357e)

## Global Constraints

- Java 8 source level. No `var`, no `List.of`, no `String.isBlank`.
- Mixin classes: `@Inject` / `@Redirect` / `@ModifyConstant` only, never `@Overwrite`; **no lambdas or method references inside mixin classes** (use anonymous classes). Plain classes may use lambdas.
- Plain classes and interfaces implemented by mixins must NOT live in `io.github.as9pa.fastpacks.mixin` (Mixin refuses to load them as normal classes).
- Classes under `filter/` and `icon/` must not reference Minecraft, Forge, LWJGL or GL: the first pack scan happens during `Minecraft.startGame`, before mods initialise.
- `mixins.fastpacks.json` keeps `"required": true` and `"injectors": { "defaultRequire": 1 }`.
- Every commit message ends with the two trailer lines below. Before committing, `git config user.name` must print `as9pa`.

```
Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_016nrBSZRraXWQ2XcP2qa9AW
```

- Build/test command (Git Bash), always with JDK 21 driving Gradle:

```bash
cd /c/Users/alexa/projects/packs && JAVA_HOME="/c/Program Files/Java/jdk-21.0.12.1" ./gradlew test --tests 'io.github.as9pa.fastpacks.filter.*' 2>&1 | tail -30
```

- Full build: same prefix with `./gradlew build`. Output jar: `build/libs/fastpacks-<version>.jar`.
- Dev client: same prefix with `./gradlew runDevClient -Pfastpacks.devOpenPacksGui=true`; it opens the screen, logs `fastpacks dev: ...` lines and exits. Its game dir is `run/`.
- Work on branch `fastpacks-v2` (created in Task 1). Never push from a task; the orchestrator pushes.

## Vanilla facts the tasks rely on (MCP stable_22 names, verified with javap)

- `GuiScreenResourcePacks` (extends `GuiScreen`): private fields `availableResourcePacks` (`List<ResourcePackListEntry>`), `availableResourcePacksList` (`GuiResourcePackAvailable`), `selectedResourcePacksList` (`GuiResourcePackSelected`). Overrides `initGui`, `handleMouseInput`, `actionPerformed(GuiButton)`, `mouseClicked(int,int,int)`, `mouseReleased`, `drawScreen(int,int,float)`. Does NOT override `keyTyped(char,int)` or `updateScreen()`.
- `GuiScreen`: `public Minecraft mc`, `public int width, height`, `protected List<GuiButton> buttonList`, `protected FontRenderer fontRendererObj`, `protected void keyTyped(char, int) throws IOException` (Escape = keyCode 1 closes the screen), `public void updateScreen()`.
- `GuiSlot`: `public void setDimensions(int width, int height, int top, int bottom)` (also resets `left = 0`, `right = width`, so call `setSlotXBoundsFromLeft` again afterwards). Lists are built in `initGui` with top 32 and bottom `height - 51`.
- `GuiResourcePackList` (extends `GuiListExtended`): `protected int getSize()`, `public ResourcePackListEntry getListEntry(int)` (plus a synthetic bridge returning `IGuiListEntry`), `public List<ResourcePackListEntry> getList()`. All `GuiSlot` drawing and mouse code goes through `getSize()`/`getListEntry()`.
- `ResourcePackListEntry.drawEntry(int slotIndex, int x, int y, int listWidth, int slotHeight, int mouseX, int mouseY, boolean isSelected)`: draws the icon at `(x, y)` 32x32, the name at `(x + 34, y + 1)` trimmed with the literal `157` (twice: `if (i1 > 157)` and `trimStringToWidth(s, 157 - ...)`), then the description with a third `157` in `listFormattedStringToWidth(s1, 157)`. Field `protected final Minecraft mc`.
- `ResourcePackListEntryFound.func_148318_i()` returns the `ResourcePackRepository.Entry`; `Entry.getResourcePackName()`, `Entry.getTexturePackDescription()` (may contain `§` formatting codes). `ResourcePackListEntryDefault` is the vanilla "Default" row (always in Selected, never movable).
- `GuiTextField(int id, FontRenderer font, int x, int y, int width, int height)`: `drawTextBox()` draws a 1 px `0xA0A0A0` border OUTSIDE the box and text at `(x + 4, y + (height - 8) / 2)`; `textboxKeyTyped(char,int)`, `mouseClicked(int,int,int)`, `updateCursorCounter()`, `isFocused()`, `getText()`, `setText`, `setMaxStringLength`.
- `GuiButton(int id, int x, int y, int width, int height, String text)`: fields `xPosition, yPosition, width, height, displayString, visible, enabled`, `protected boolean hovered`, `protected static final ResourceLocation buttonTextures`; `protected int getHoverState(boolean mouseOver)` (0 disabled, 1 normal, 2 hovered); `public void drawButton(Minecraft, int, int)`; `protected void mouseDragged(Minecraft, int, int)`.
- `FontRenderer`: `int drawStringWithShadow(String, float x, float y, int colour)`, `int getStringWidth(String)`.
- Text colours: white 0xFFFFFF, button text 0xE0E0E0, hovered button text 0xFFFFA0, grey 0x808080, badge/counter grey 0xA0A0A0, placeholder 0x707070.

## File map

Create:
- `src/main/java/io/github/as9pa/fastpacks/filter/PngHeader.java` — PNG width from the IHDR chunk.
- `src/main/java/io/github/as9pa/fastpacks/filter/PackResolution.java` — value `{kind, res}`, bucketing, mode-of-widths.
- `src/main/java/io/github/as9pa/fastpacks/filter/ResolutionScanner.java` — samples 12 texture paths in a zip or folder pack.
- `src/main/java/io/github/as9pa/fastpacks/filter/PackCache.java` — `config/fastpacks/packs.json` load/lookup/put/flush.
- `src/main/java/io/github/as9pa/fastpacks/filter/PackFilter.java` — `Chip` enum, active chip, query normalising, matching.
- `src/main/java/io/github/as9pa/fastpacks/filter/ResolutionSink.java` — callback interface.
- `src/main/java/io/github/as9pa/fastpacks/filter/PackScan.java` — background task: cache lookup, scan, write-through, flush scheduling.
- `src/main/java/io/github/as9pa/fastpacks/gui/FilteredList.java` — interface the list mixin implements.
- `src/main/java/io/github/as9pa/fastpacks/gui/ChipButton.java` — `GuiButton` subclass with an active state.
- `src/main/java/io/github/as9pa/fastpacks/gui/PacksToolbar.java` — text field, chips, counter, filtered view.
- `src/main/java/io/github/as9pa/fastpacks/gui/RowBadge.java` — badge text and layout constants.
- `src/main/java/io/github/as9pa/fastpacks/mixin/MixinGuiScreenResourcePacks.java`
- `src/main/java/io/github/as9pa/fastpacks/mixin/MixinGuiResourcePackList.java`
- `src/main/java/io/github/as9pa/fastpacks/mixin/MixinResourcePackListEntry.java`
- Tests under `src/test/java/io/github/as9pa/fastpacks/filter/`: `PngHeaderTest`, `PackResolutionTest`, `TestPacks` (helper), `ResolutionScannerTest`, `PackCacheTest`, `PackFilterTest`, `PackScanTest`.

Modify:
- `src/main/java/io/github/as9pa/fastpacks/EntryExtension.java` — extends `ResolutionSink`, adds `fastpacks$resolution()`.
- `src/main/java/io/github/as9pa/fastpacks/icon/IconLoader.java` — expose `executor()`.
- `src/main/java/io/github/as9pa/fastpacks/mixin/MixinResourcePackRepositoryEntry.java` — submit the scan, hold the result.
- `src/main/java/io/github/as9pa/fastpacks/mixin/FastPacksMixinPlugin.java` — baseline mode also disables the three GUI mixins.
- `src/main/java/io/github/as9pa/fastpacks/dev/DevHarness.java` — log a resolution tally.
- `src/main/java/io/github/as9pa/fastpacks/FastPacks.java` — `VERSION` constant in `@Mod`.
- `src/main/resources/mixins.fastpacks.json` — three new mixins.
- `build.gradle.kts` — `testImplementation` Gson.
- `gradle.properties` — `version = 0.2.0`.
- `README.md`, `docs/superpowers/specs/2026-09-06-fastpacks-v2-design.md` (status), memory notes.

---

### Task 1: PngHeader and PackResolution (pure logic)

**Files:**
- Create: `src/main/java/io/github/as9pa/fastpacks/filter/PngHeader.java`
- Create: `src/main/java/io/github/as9pa/fastpacks/filter/PackResolution.java`
- Test: `src/test/java/io/github/as9pa/fastpacks/filter/PngHeaderTest.java`
- Test: `src/test/java/io/github/as9pa/fastpacks/filter/PackResolutionTest.java`

**Interfaces:**
- Produces: `PngHeader.width(InputStream) -> int` (-1 on failure, does not close the stream); `PackResolution` with `enum Kind { TEXTURES, OVERLAY, UNKNOWN }`, constants `OVERLAY`, `UNKNOWN`, `static textures(int bucket)`, `static bucket(int width)`, `static fromWidths(List<Integer>)`, fields `kind`, `res`, `badge()`, `equals/hashCode`.

- [ ] **Step 1: Create the branch**

```bash
cd /c/Users/alexa/projects/packs && git checkout -b fastpacks-v2 && git config user.name
```
Expected: `as9pa`.

- [ ] **Step 2: Write the failing tests**

`src/test/java/io/github/as9pa/fastpacks/filter/PngHeaderTest.java`:

```java
package io.github.as9pa.fastpacks.filter;

import static org.junit.Assert.assertEquals;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import javax.imageio.ImageIO;
import org.junit.Test;

public class PngHeaderTest {
    static byte[] png(int w, int h) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB), "png", out);
        return out.toByteArray();
    }

    @Test
    public void readsWidthOfRealPngs() throws IOException {
        assertEquals(16, PngHeader.width(new ByteArrayInputStream(png(16, 16))));
        assertEquals(32, PngHeader.width(new ByteArrayInputStream(png(32, 64))));
        assertEquals(128, PngHeader.width(new ByteArrayInputStream(png(128, 128))));
        assertEquals(1000, PngHeader.width(new ByteArrayInputStream(png(1000, 3))));
    }

    @Test
    public void truncatedStreamGivesMinusOne() throws IOException {
        byte[] full = png(64, 64);
        assertEquals(-1, PngHeader.width(new ByteArrayInputStream(Arrays.copyOf(full, 20))));
        assertEquals(-1, PngHeader.width(new ByteArrayInputStream(new byte[0])));
    }

    @Test
    public void nonPngGivesMinusOne() {
        byte[] junk = new byte[40];
        Arrays.fill(junk, (byte) 'x');
        assertEquals(-1, PngHeader.width(new ByteArrayInputStream(junk)));
    }

    @Test
    public void badChunkNameGivesMinusOne() throws IOException {
        byte[] bytes = png(16, 16);
        bytes[12] = 'X';
        assertEquals(-1, PngHeader.width(new ByteArrayInputStream(bytes)));
    }
}
```

`src/test/java/io/github/as9pa/fastpacks/filter/PackResolutionTest.java`:

```java
package io.github.as9pa.fastpacks.filter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

public class PackResolutionTest {
    @Test
    public void bucketsAtBoundaries() {
        assertEquals(16, PackResolution.bucket(8));
        assertEquals(16, PackResolution.bucket(16));
        assertEquals(32, PackResolution.bucket(17));
        assertEquals(32, PackResolution.bucket(32));
        assertEquals(64, PackResolution.bucket(33));
        assertEquals(64, PackResolution.bucket(64));
        assertEquals(128, PackResolution.bucket(65));
        assertEquals(128, PackResolution.bucket(128));
        assertEquals(128, PackResolution.bucket(512));
    }

    @Test
    public void emptyWidthsIsOverlay() {
        assertEquals(PackResolution.OVERLAY, PackResolution.fromWidths(Collections.<Integer>emptyList()));
    }

    @Test
    public void mostCommonWidthWins() {
        assertEquals(PackResolution.textures(16), PackResolution.fromWidths(Arrays.asList(16, 16, 32)));
        assertEquals(PackResolution.textures(64), PackResolution.fromWidths(Arrays.asList(64, 16, 64, 64, 32)));
    }

    @Test
    public void tieGoesToTheLargerWidth() {
        assertEquals(PackResolution.textures(32), PackResolution.fromWidths(Arrays.asList(16, 32)));
        assertEquals(PackResolution.textures(32), PackResolution.fromWidths(Arrays.asList(32, 32, 16, 16)));
        assertEquals(PackResolution.textures(32), PackResolution.fromWidths(Arrays.asList(16, 16, 32, 32)));
    }

    @Test
    public void widthsAreBucketedAfterVoting() {
        assertEquals(PackResolution.textures(128), PackResolution.fromWidths(Arrays.asList(512, 256)));
        assertEquals(PackResolution.textures(32), PackResolution.fromWidths(Arrays.asList(20, 20, 64)));
    }

    @Test
    public void badgeText() {
        assertEquals("16x", PackResolution.textures(16).badge());
        assertEquals("128x", PackResolution.textures(128).badge());
        assertEquals("overlay", PackResolution.OVERLAY.badge());
        assertEquals("", PackResolution.UNKNOWN.badge());
    }

    @Test
    public void equalityIsByValue() {
        assertEquals(PackResolution.textures(32), PackResolution.textures(32));
        assertEquals(PackResolution.textures(32).hashCode(), PackResolution.textures(32).hashCode());
        assertNotEquals(PackResolution.textures(32), PackResolution.textures(16));
        assertNotEquals(PackResolution.OVERLAY, PackResolution.UNKNOWN);
    }
}
```

- [ ] **Step 3: Run the tests to verify they fail**

Run the build/test command with `--tests 'io.github.as9pa.fastpacks.filter.*'`.
Expected: compilation FAILS with "package io.github.as9pa.fastpacks.filter does not exist" / cannot find symbol `PngHeader`.

- [ ] **Step 4: Implement PngHeader**

```java
package io.github.as9pa.fastpacks.filter;

import java.io.IOException;
import java.io.InputStream;

/** Reads a PNG's pixel width from its IHDR chunk without decoding the image. */
public final class PngHeader {
    private static final byte[] SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'};
    static final int HEADER_BYTES = 24;

    private PngHeader() {}

    /** Width in pixels, or -1 if the stream is too short or not a PNG. Does not close the stream. */
    public static int width(InputStream in) {
        byte[] head = new byte[HEADER_BYTES];
        int read = 0;
        try {
            while (read < HEADER_BYTES) {
                int n = in.read(head, read, HEADER_BYTES - read);
                if (n < 0) {
                    return -1;
                }
                read += n;
            }
        } catch (IOException e) {
            return -1;
        }
        for (int i = 0; i < SIGNATURE.length; i++) {
            if (head[i] != SIGNATURE[i]) {
                return -1;
            }
        }
        if (head[12] != 'I' || head[13] != 'H' || head[14] != 'D' || head[15] != 'R') {
            return -1;
        }
        int w = ((head[16] & 0xff) << 24) | ((head[17] & 0xff) << 16) | ((head[18] & 0xff) << 8) | (head[19] & 0xff);
        return w > 0 ? w : -1;
    }
}
```

- [ ] **Step 5: Implement PackResolution**

```java
package io.github.as9pa.fastpacks.filter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** What the scanner found in a pack: the texture resolution bucket, an overlay, or nothing usable. Immutable. */
public final class PackResolution {
    public enum Kind { TEXTURES, OVERLAY, UNKNOWN }

    public static final PackResolution OVERLAY = new PackResolution(Kind.OVERLAY, 0);
    public static final PackResolution UNKNOWN = new PackResolution(Kind.UNKNOWN, 0);

    public final Kind kind;
    /** 16, 32, 64 or 128 for TEXTURES; 0 otherwise. */
    public final int res;

    private PackResolution(Kind kind, int res) {
        this.kind = kind;
        this.res = res;
    }

    public static PackResolution textures(int bucket) {
        return new PackResolution(Kind.TEXTURES, bucket);
    }

    /** 16, 32, 64, or 128 for anything larger. */
    public static int bucket(int width) {
        if (width <= 16) {
            return 16;
        }
        if (width <= 32) {
            return 32;
        }
        if (width <= 64) {
            return 64;
        }
        return 128;
    }

    /** Most common width wins; a tie goes to the larger width; no widths means an overlay pack. */
    public static PackResolution fromWidths(List<Integer> widths) {
        if (widths.isEmpty()) {
            return OVERLAY;
        }
        Map<Integer, Integer> counts = new HashMap<>();
        int best = -1;
        int bestCount = 0;
        for (int w : widths) {
            Integer prev = counts.get(w);
            int c = prev == null ? 1 : prev + 1;
            counts.put(w, c);
            if (c > bestCount || (c == bestCount && w > best)) {
                best = w;
                bestCount = c;
            }
        }
        return textures(bucket(best));
    }

    /** Text for the row badge: "16x", "overlay", or "" when nothing is known. */
    public String badge() {
        switch (kind) {
            case TEXTURES:
                return res + "x";
            case OVERLAY:
                return "overlay";
            default:
                return "";
        }
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof PackResolution)) {
            return false;
        }
        PackResolution other = (PackResolution) o;
        return kind == other.kind && res == other.res;
    }

    @Override
    public int hashCode() {
        return kind.hashCode() * 31 + res;
    }

    @Override
    public String toString() {
        return kind == Kind.TEXTURES ? "TEXTURES(" + res + ")" : kind.name();
    }
}
```

- [ ] **Step 6: Run the tests to verify they pass**

Same command. Expected: `PngHeaderTest` 4 passed, `PackResolutionTest` 7 passed, BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
cd /c/Users/alexa/projects/packs && git add src/main/java/io/github/as9pa/fastpacks/filter src/test/java/io/github/as9pa/fastpacks/filter && git commit -q -F - <<'EOF'
feat(filter): PNG header width reader and PackResolution value

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_016nrBSZRraXWQ2XcP2qa9AW
EOF
```

---

### Task 2: ResolutionScanner

**Files:**
- Create: `src/main/java/io/github/as9pa/fastpacks/filter/ResolutionScanner.java`
- Test: `src/test/java/io/github/as9pa/fastpacks/filter/TestPacks.java` (helper)
- Test: `src/test/java/io/github/as9pa/fastpacks/filter/ResolutionScannerTest.java`

**Interfaces:**
- Consumes: `PngHeader.width`, `PackResolution.fromWidths`, `PackResolution.UNKNOWN`, `io.github.as9pa.fastpacks.Log.LOG`.
- Produces: `ResolutionScanner.scan(File pack) -> PackResolution` (never throws); `ResolutionScanner.SAMPLES` (package-private `String[]`); test helper `TestPacks.png(int,int) -> byte[]`, `TestPacks.zip(File target, Map<String,byte[]> entries) -> File`, `TestPacks.write(File, byte[])`.

- [ ] **Step 1: Write the test helper**

`src/test/java/io/github/as9pa/fastpacks/filter/TestPacks.java`:

```java
package io.github.as9pa.fastpacks.filter;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.imageio.ImageIO;

/** Builds throwaway packs for tests. */
final class TestPacks {
    static final byte[] MCMETA = "{\"pack\":{\"pack_format\":1,\"description\":\"t\"}}".getBytes();

    private TestPacks() {}

    static byte[] png(int w, int h) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB), "png", out);
        return out.toByteArray();
    }

    static File zip(File target, Map<String, byte[]> entries) throws IOException {
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(target))) {
            for (Map.Entry<String, byte[]> e : entries.entrySet()) {
                out.putNextEntry(new ZipEntry(e.getKey()));
                out.write(e.getValue());
                out.closeEntry();
            }
        }
        return target;
    }

    static void write(File file, byte[] bytes) throws IOException {
        File dir = file.getParentFile();
        if (dir != null) {
            dir.mkdirs();
        }
        Files.write(file.toPath(), bytes);
    }
}
```

- [ ] **Step 2: Write the failing tests**

`src/test/java/io/github/as9pa/fastpacks/filter/ResolutionScannerTest.java`:

```java
package io.github.as9pa.fastpacks.filter;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ResolutionScannerTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    private static final String SWORD = "assets/minecraft/textures/items/diamond_sword.png";
    private static final String STONE = "assets/minecraft/textures/blocks/stone.png";
    private static final String DIRT = "assets/minecraft/textures/blocks/dirt.png";
    private static final String SKY = "assets/minecraft/mcpatcher/sky/world0/sky1.png";

    @Test
    public void zipPackVotesAcrossSampledTextures() throws IOException {
        Map<String, byte[]> e = new LinkedHashMap<>();
        e.put("pack.mcmeta", TestPacks.MCMETA);
        e.put(SWORD, TestPacks.png(32, 32));
        e.put(STONE, TestPacks.png(32, 32));
        e.put(DIRT, TestPacks.png(16, 16));
        assertEquals(PackResolution.textures(32), ResolutionScanner.scan(TestPacks.zip(new File(tmp.getRoot(), "a.zip"), e)));
    }

    @Test
    public void folderPackIsScannedToo() throws IOException {
        File folder = tmp.newFolder("FolderPack");
        TestPacks.write(new File(folder, "pack.mcmeta"), TestPacks.MCMETA);
        TestPacks.write(new File(folder, STONE), TestPacks.png(64, 64));
        assertEquals(PackResolution.textures(64), ResolutionScanner.scan(folder));
    }

    @Test
    public void packWithNoSampledTexturesIsOverlay() throws IOException {
        Map<String, byte[]> e = new LinkedHashMap<>();
        e.put("pack.mcmeta", TestPacks.MCMETA);
        e.put(SKY, TestPacks.png(512, 512));
        assertEquals(PackResolution.OVERLAY, ResolutionScanner.scan(TestPacks.zip(new File(tmp.getRoot(), "o.zip"), e)));
    }

    @Test
    public void unreadableSampledTextureIsIgnored() throws IOException {
        Map<String, byte[]> e = new LinkedHashMap<>();
        e.put(STONE, "not a png".getBytes());
        e.put(DIRT, TestPacks.png(16, 16));
        assertEquals(PackResolution.textures(16), ResolutionScanner.scan(TestPacks.zip(new File(tmp.getRoot(), "b.zip"), e)));
    }

    @Test
    public void missingFileIsUnknown() {
        assertEquals(PackResolution.UNKNOWN, ResolutionScanner.scan(new File(tmp.getRoot(), "gone.zip")));
    }

    @Test
    public void corruptZipIsUnknown() throws IOException {
        File bad = tmp.newFile("bad.zip");
        TestPacks.write(bad, "this is not a zip".getBytes());
        assertEquals(PackResolution.UNKNOWN, ResolutionScanner.scan(bad));
    }

    @Test
    public void nestedFolderZipDoesNotCrash() throws IOException {
        Map<String, byte[]> e = new LinkedHashMap<>();
        e.put("MyPack/pack.mcmeta", TestPacks.MCMETA);
        e.put("MyPack/" + STONE, TestPacks.png(32, 32));
        // Vanilla never loads such a zip, so the scanner is never asked; if it is, it must simply not throw.
        assertEquals(PackResolution.OVERLAY, ResolutionScanner.scan(TestPacks.zip(new File(tmp.getRoot(), "n.zip"), e)));
    }

    @Test
    public void sampleListIsTheSpecList() {
        assertEquals(12, ResolutionScanner.SAMPLES.length);
        assertEquals(SWORD, ResolutionScanner.SAMPLES[0]);
    }
}
```

- [ ] **Step 3: Run the tests to verify they fail**

Expected: compilation FAILS, cannot find symbol `ResolutionScanner`.

- [ ] **Step 4: Implement ResolutionScanner**

```java
package io.github.as9pa.fastpacks.filter;

import io.github.as9pa.fastpacks.Log;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Works out a pack's resolution by reading the PNG width of a handful of PvP-relevant textures.
 * Runs on a background thread; must not touch Minecraft classes.
 */
public final class ResolutionScanner {
    private static final String ITEMS = "assets/minecraft/textures/items/";
    private static final String BLOCKS = "assets/minecraft/textures/blocks/";

    static final String[] SAMPLES = {
        ITEMS + "diamond_sword.png", ITEMS + "iron_sword.png", ITEMS + "bow_standing.png",
        ITEMS + "fishing_rod_uncast.png", ITEMS + "apple_golden.png", ITEMS + "ender_pearl.png",
        BLOCKS + "stone.png", BLOCKS + "planks_oak.png", BLOCKS + "sandstone_normal.png",
        BLOCKS + "wool_colored_white.png", BLOCKS + "dirt.png", BLOCKS + "cobblestone.png",
    };

    private ResolutionScanner() {}

    /** Never throws. UNKNOWN when the pack cannot be opened. */
    public static PackResolution scan(File pack) {
        try {
            List<Integer> widths = new ArrayList<>();
            if (pack.isDirectory()) {
                for (String sample : SAMPLES) {
                    File f = new File(pack, sample);
                    if (f.isFile()) {
                        try (InputStream in = new FileInputStream(f)) {
                            addWidth(widths, in);
                        }
                    }
                }
            } else {
                try (ZipFile zip = new ZipFile(pack)) {
                    for (String sample : SAMPLES) {
                        ZipEntry entry = zip.getEntry(sample);
                        if (entry != null) {
                            try (InputStream in = zip.getInputStream(entry)) {
                                addWidth(widths, in);
                            }
                        }
                    }
                }
            }
            return PackResolution.fromWidths(widths);
        } catch (IOException | RuntimeException e) {
            Log.LOG.debug("fastpacks: could not scan {}: {}", pack.getName(), e.toString());
            return PackResolution.UNKNOWN;
        }
    }

    private static void addWidth(List<Integer> widths, InputStream in) {
        int w = PngHeader.width(in);
        if (w > 0) {
            widths.add(w);
        }
    }
}
```

- [ ] **Step 5: Run the tests to verify they pass**

Expected: `ResolutionScannerTest` 8 passed.

- [ ] **Step 6: Commit**

```bash
cd /c/Users/alexa/projects/packs && git add src/main/java/io/github/as9pa/fastpacks/filter src/test/java/io/github/as9pa/fastpacks/filter && git commit -q -F - <<'EOF'
feat(filter): ResolutionScanner samples PvP textures in zip and folder packs

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_016nrBSZRraXWQ2XcP2qa9AW
EOF
```

---

### Task 3: PackCache (packs.json)

**Files:**
- Modify: `build.gradle.kts` (dependencies block, after the JUnit line)
- Create: `src/main/java/io/github/as9pa/fastpacks/filter/PackCache.java`
- Test: `src/test/java/io/github/as9pa/fastpacks/filter/PackCacheTest.java`

**Interfaces:**
- Consumes: `PackResolution`, `Log.LOG`, Gson 2.2.4 (`com.google.gson.JsonParser`, `JsonObject`, `GsonBuilder`).
- Produces: `new PackCache(File jsonFile)`; `static String key(File pack)` = `name:length:lastModified`; `synchronized PackResolution lookup(String key)` (null on miss); `synchronized void put(String key, PackResolution)`; `synchronized boolean flush()` (true when a file was written; never throws); `synchronized int size()`; `synchronized boolean isDirty()`; `static final int FORMAT_VERSION = 1`.

- [ ] **Step 1: Add Gson to the test classpath**

In `build.gradle.kts`, inside `dependencies { ... }`, directly after `testImplementation("junit:junit:4.13.2")` add:

```kotlin
    // Same version Minecraft 1.8.9 bundles; makes the cache tests independent of Loom's runtime classpath.
    testImplementation("com.google.code.gson:gson:2.2.4")
```

- [ ] **Step 2: Write the failing tests**

`src/test/java/io/github/as9pa/fastpacks/filter/PackCacheTest.java`:

```java
package io.github.as9pa.fastpacks.filter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class PackCacheTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    private File json() {
        return new File(tmp.getRoot(), "config/fastpacks/packs.json");
    }

    @Test
    public void keyIsNameSizeAndModifiedTime() throws IOException {
        File f = tmp.newFile("Aether 16x.zip");
        TestPacks.write(f, new byte[123]);
        assertEquals("Aether 16x.zip:123:" + f.lastModified(), PackCache.key(f));
    }

    @Test
    public void missingFileMeansEmptyCache() {
        PackCache cache = new PackCache(json());
        assertNull(cache.lookup("anything"));
        assertEquals(0, cache.size());
        assertFalse(cache.isDirty());
        assertFalse("nothing to flush", cache.flush());
        assertFalse(json().exists());
    }

    @Test
    public void roundTripsThroughTheFile() {
        PackCache cache = new PackCache(json());
        cache.put("a.zip:1:1", PackResolution.textures(16));
        cache.put("b.zip:2:2", PackResolution.OVERLAY);
        cache.put("c.zip:3:3", PackResolution.UNKNOWN);
        assertTrue(cache.isDirty());
        assertTrue(cache.flush());
        assertFalse(cache.isDirty());
        assertTrue(json().isFile());
        assertFalse(new File(json().getPath() + ".tmp").exists());

        PackCache reloaded = new PackCache(json());
        assertEquals(PackResolution.textures(16), reloaded.lookup("a.zip:1:1"));
        assertEquals(PackResolution.OVERLAY, reloaded.lookup("b.zip:2:2"));
        assertEquals(PackResolution.UNKNOWN, reloaded.lookup("c.zip:3:3"));
        assertNull(reloaded.lookup("d.zip:4:4"));
        assertEquals(3, reloaded.size());
    }

    @Test
    public void puttingTheSameValueDoesNotDirty() {
        PackCache cache = new PackCache(json());
        cache.put("a.zip:1:1", PackResolution.textures(16));
        cache.flush();
        cache.put("a.zip:1:1", PackResolution.textures(16));
        assertFalse(cache.isDirty());
        cache.put("a.zip:1:1", PackResolution.textures(32));
        assertTrue(cache.isDirty());
    }

    @Test
    public void wrongFormatVersionIsIgnored() throws IOException {
        TestPacks.write(json(), "{\"version\":99,\"packs\":{\"a.zip:1:1\":{\"res\":16,\"kind\":\"TEXTURES\"}}}".getBytes("UTF-8"));
        PackCache cache = new PackCache(json());
        assertNull(cache.lookup("a.zip:1:1"));
        assertEquals(0, cache.size());
    }

    @Test
    public void garbageFileIsIgnoredWithoutThrowing() throws IOException {
        TestPacks.write(json(), "{{{ not json".getBytes("UTF-8"));
        PackCache cache = new PackCache(json());
        assertNull(cache.lookup("a.zip:1:1"));
        cache.put("a.zip:1:1", PackResolution.textures(64));
        assertTrue("can overwrite a garbage file", cache.flush());
        assertEquals(PackResolution.textures(64), new PackCache(json()).lookup("a.zip:1:1"));
    }

    @Test
    public void unknownKindInFileFallsBackToUnknown() throws IOException {
        TestPacks.write(json(), "{\"version\":1,\"packs\":{\"a.zip:1:1\":{\"res\":0,\"kind\":\"WEIRD\"}}}".getBytes("UTF-8"));
        PackCache cache = new PackCache(json());
        assertEquals(0, cache.size());
    }
}
```

- [ ] **Step 3: Run the tests to verify they fail**

Expected: compilation FAILS, cannot find symbol `PackCache`.

- [ ] **Step 4: Implement PackCache**

```java
package io.github.as9pa.fastpacks.filter;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.as9pa.fastpacks.Log;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;

/**
 * On-disk memory of scanned pack resolutions, keyed by file name, size and modification time.
 * Thread-safe; loaded lazily on first use; never throws into the caller.
 */
public final class PackCache {
    public static final int FORMAT_VERSION = 1;

    private final File file;
    private final Map<String, PackResolution> entries = new HashMap<>();
    private boolean loaded;
    private boolean dirty;

    public PackCache(File file) {
        this.file = file;
    }

    public static String key(File pack) {
        return pack.getName() + ":" + pack.length() + ":" + pack.lastModified();
    }

    public synchronized PackResolution lookup(String key) {
        ensureLoaded();
        return entries.get(key);
    }

    public synchronized void put(String key, PackResolution resolution) {
        ensureLoaded();
        PackResolution previous = entries.put(key, resolution);
        if (!resolution.equals(previous)) {
            dirty = true;
        }
    }

    public synchronized int size() {
        ensureLoaded();
        return entries.size();
    }

    public synchronized boolean isDirty() {
        return dirty;
    }

    /** Writes the file if anything changed, via a temp file and rename. True when a write happened. */
    public synchronized boolean flush() {
        if (!dirty) {
            return false;
        }
        JsonObject packs = new JsonObject();
        for (Map.Entry<String, PackResolution> e : entries.entrySet()) {
            JsonObject o = new JsonObject();
            o.addProperty("res", e.getValue().res);
            o.addProperty("kind", e.getValue().kind.name());
            packs.add(e.getKey(), o);
        }
        JsonObject root = new JsonObject();
        root.addProperty("version", FORMAT_VERSION);
        root.add("packs", packs);

        File tmp = new File(file.getPath() + ".tmp");
        try {
            File dir = file.getParentFile();
            if (dir != null) {
                dir.mkdirs();
            }
            try (Writer w = new OutputStreamWriter(new FileOutputStream(tmp), StandardCharsets.UTF_8)) {
                new GsonBuilder().setPrettyPrinting().create().toJson(root, w);
            }
            try {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            dirty = false;
            return true;
        } catch (IOException | RuntimeException e) {
            Log.LOG.warn("fastpacks: could not write {}: {}", file, e.toString());
            tmp.delete();
            return false;
        }
    }

    private void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        if (!file.isFile()) {
            return;
        }
        try (Reader r = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            JsonObject root = new JsonParser().parse(r).getAsJsonObject();
            JsonElement version = root.get("version");
            if (version == null || version.getAsInt() != FORMAT_VERSION) {
                Log.LOG.info("fastpacks: ignoring {} (unsupported format)", file);
                return;
            }
            JsonObject packs = root.getAsJsonObject("packs");
            if (packs == null) {
                return;
            }
            for (Map.Entry<String, JsonElement> e : packs.entrySet()) {
                JsonObject o = e.getValue().getAsJsonObject();
                PackResolution.Kind kind;
                try {
                    kind = PackResolution.Kind.valueOf(o.get("kind").getAsString());
                } catch (IllegalArgumentException | NullPointerException bad) {
                    continue;
                }
                PackResolution value;
                if (kind == PackResolution.Kind.TEXTURES) {
                    value = PackResolution.textures(o.get("res").getAsInt());
                } else if (kind == PackResolution.Kind.OVERLAY) {
                    value = PackResolution.OVERLAY;
                } else {
                    value = PackResolution.UNKNOWN;
                }
                entries.put(e.getKey(), value);
            }
            Log.LOG.info("fastpacks: loaded {} cached pack resolutions", entries.size());
        } catch (IOException | RuntimeException e) {
            entries.clear();
            Log.LOG.info("fastpacks: ignoring unreadable {}: {}", file, e.toString());
        }
    }
}
```

- [ ] **Step 5: Run the tests to verify they pass**

Expected: `PackCacheTest` 7 passed. If the compiler cannot find `com.google.gson`, the Gson test dependency from Step 1 is missing or Gradle needs `--refresh-dependencies`.

- [ ] **Step 6: Commit**

```bash
cd /c/Users/alexa/projects/packs && git add build.gradle.kts src/main/java/io/github/as9pa/fastpacks/filter src/test/java/io/github/as9pa/fastpacks/filter && git commit -q -F - <<'EOF'
feat(filter): PackCache persists scanned resolutions in config/fastpacks/packs.json

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_016nrBSZRraXWQ2XcP2qa9AW
EOF
```

---

### Task 4: PackFilter (chips, query, matching)

**Files:**
- Create: `src/main/java/io/github/as9pa/fastpacks/filter/PackFilter.java`
- Test: `src/test/java/io/github/as9pa/fastpacks/filter/PackFilterTest.java`

**Interfaces:**
- Consumes: `PackResolution`.
- Produces: `PackFilter.Chip { ALL("All"), R16("16x"), R32("32x"), R64("64x"), R128("128x+"), OVERLAY("Overlay") }` with `public final String label`; `static Chip activeChip()` / `static void setActiveChip(Chip)` (process-wide, defaults to ALL); `static String normalizeQuery(String)`; `static String stripFormatting(String)`; `static boolean chipMatches(Chip, PackResolution)` (null resolution = pending, only ALL); `static boolean queryMatches(String normalizedQuery, String name, String description)`; `static boolean matches(Chip, String normalizedQuery, String name, String description, PackResolution)`.

- [ ] **Step 1: Write the failing tests**

`src/test/java/io/github/as9pa/fastpacks/filter/PackFilterTest.java`:

```java
package io.github.as9pa.fastpacks.filter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import io.github.as9pa.fastpacks.filter.PackFilter.Chip;
import org.junit.After;
import org.junit.Test;

public class PackFilterTest {
    private static final PackResolution R16 = PackResolution.textures(16);
    private static final PackResolution R32 = PackResolution.textures(32);
    private static final PackResolution R64 = PackResolution.textures(64);
    private static final PackResolution R128 = PackResolution.textures(128);

    @After
    public void resetChip() {
        PackFilter.setActiveChip(Chip.ALL);
    }

    @Test
    public void chipLabelsMatchTheWireframe() {
        assertEquals("All", Chip.ALL.label);
        assertEquals("16x", Chip.R16.label);
        assertEquals("32x", Chip.R32.label);
        assertEquals("64x", Chip.R64.label);
        assertEquals("128x+", Chip.R128.label);
        assertEquals("Overlay", Chip.OVERLAY.label);
    }

    @Test
    public void allShowsEverythingIncludingPendingAndUnknown() {
        assertTrue(PackFilter.chipMatches(Chip.ALL, null));
        assertTrue(PackFilter.chipMatches(Chip.ALL, PackResolution.UNKNOWN));
        assertTrue(PackFilter.chipMatches(Chip.ALL, PackResolution.OVERLAY));
        assertTrue(PackFilter.chipMatches(Chip.ALL, R32));
    }

    @Test
    public void exactChipsMatchOnlyTheirBucket() {
        assertTrue(PackFilter.chipMatches(Chip.R16, R16));
        assertFalse(PackFilter.chipMatches(Chip.R16, R32));
        assertTrue(PackFilter.chipMatches(Chip.R32, R32));
        assertFalse(PackFilter.chipMatches(Chip.R32, R64));
        assertTrue(PackFilter.chipMatches(Chip.R64, R64));
        assertFalse(PackFilter.chipMatches(Chip.R64, R128));
        assertFalse(PackFilter.chipMatches(Chip.R16, PackResolution.OVERLAY));
        assertFalse(PackFilter.chipMatches(Chip.R16, PackResolution.UNKNOWN));
        assertFalse(PackFilter.chipMatches(Chip.R16, null));
    }

    @Test
    public void r128ChipIs128AndAbove() {
        assertTrue(PackFilter.chipMatches(Chip.R128, R128));
        assertFalse(PackFilter.chipMatches(Chip.R128, R64));
        assertFalse(PackFilter.chipMatches(Chip.R128, PackResolution.OVERLAY));
    }

    @Test
    public void overlayChipMatchesOnlyOverlays() {
        assertTrue(PackFilter.chipMatches(Chip.OVERLAY, PackResolution.OVERLAY));
        assertFalse(PackFilter.chipMatches(Chip.OVERLAY, R16));
        assertFalse(PackFilter.chipMatches(Chip.OVERLAY, PackResolution.UNKNOWN));
        assertFalse(PackFilter.chipMatches(Chip.OVERLAY, null));
    }

    @Test
    public void queryNormalisation() {
        assertEquals("", PackFilter.normalizeQuery(null));
        assertEquals("", PackFilter.normalizeQuery("   "));
        assertEquals("faithful", PackFilter.normalizeQuery("  FaithFul "));
    }

    @Test
    public void formattingCodesAreStripped() {
        assertEquals("Aether 16x", PackFilter.stripFormatting("\u00a7bAether \u00a7f16x"));
        assertEquals("", PackFilter.stripFormatting(null));
        assertEquals("a", PackFilter.stripFormatting("a\u00a7b"));
        assertEquals("a", PackFilter.stripFormatting("a\u00a7"));
    }

    @Test
    public void queryMatchesNameOrDescriptionCaseInsensitively() {
        assertTrue(PackFilter.queryMatches("", "Aether 16x", "by Nova"));
        assertTrue(PackFilter.queryMatches("aether", "Aether 16x", "by Nova"));
        assertTrue(PackFilter.queryMatches("nova", "Aether 16x", "\u00a7dby Nova"));
        assertTrue(PackFilter.queryMatches("aether 16x", "\u00a7bAether \u00a7f16x", "by Nova"));
        assertFalse(PackFilter.queryMatches("zephyr", "Aether 16x", "by Nova"));
        assertTrue(PackFilter.queryMatches("16x", "Aether 16x", null));
    }

    @Test
    public void matchesIsChipAndQuery() {
        assertTrue(PackFilter.matches(Chip.R16, "aether", "Aether 16x", "by Nova", R16));
        assertFalse(PackFilter.matches(Chip.R32, "aether", "Aether 16x", "by Nova", R16));
        assertFalse(PackFilter.matches(Chip.R16, "zephyr", "Aether 16x", "by Nova", R16));
        assertTrue(PackFilter.matches(Chip.ALL, "", "Anything", "", null));
    }

    @Test
    public void activeChipIsRememberedProcessWide() {
        assertEquals(Chip.ALL, PackFilter.activeChip());
        PackFilter.setActiveChip(Chip.R32);
        assertEquals(Chip.R32, PackFilter.activeChip());
        PackFilter.setActiveChip(null);
        assertEquals(Chip.ALL, PackFilter.activeChip());
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Expected: compilation FAILS, cannot find symbol `PackFilter`.

- [ ] **Step 3: Implement PackFilter**

```java
package io.github.as9pa.fastpacks.filter;

import java.util.Locale;

/** Which packs the Available list shows: one chip plus a free-text query. No Minecraft references. */
public final class PackFilter {
    public enum Chip {
        ALL("All"), R16("16x"), R32("32x"), R64("64x"), R128("128x+"), OVERLAY("Overlay");

        public final String label;

        Chip(String label) {
            this.label = label;
        }
    }

    private static volatile Chip activeChip = Chip.ALL;

    private PackFilter() {}

    /** Remembered for the life of the process, across screen opens. */
    public static Chip activeChip() {
        return activeChip;
    }

    public static void setActiveChip(Chip chip) {
        activeChip = chip == null ? Chip.ALL : chip;
    }

    public static String normalizeQuery(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
    }

    /** Drops Minecraft formatting codes: a section sign and the character after it. */
    public static String stripFormatting(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\u00a7') {
                i++;
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    /** A null resolution means the background scan has not finished: only ALL shows it. */
    public static boolean chipMatches(Chip chip, PackResolution r) {
        switch (chip) {
            case ALL:
                return true;
            case OVERLAY:
                return r != null && r.kind == PackResolution.Kind.OVERLAY;
            case R16:
                return isTextures(r, 16);
            case R32:
                return isTextures(r, 32);
            case R64:
                return isTextures(r, 64);
            case R128:
                return r != null && r.kind == PackResolution.Kind.TEXTURES && r.res >= 128;
            default:
                return false;
        }
    }

    private static boolean isTextures(PackResolution r, int res) {
        return r != null && r.kind == PackResolution.Kind.TEXTURES && r.res == res;
    }

    public static boolean queryMatches(String normalizedQuery, String name, String description) {
        if (normalizedQuery.isEmpty()) {
            return true;
        }
        String haystack = (stripFormatting(name) + " " + stripFormatting(description)).toLowerCase(Locale.ROOT);
        return haystack.contains(normalizedQuery);
    }

    public static boolean matches(Chip chip, String normalizedQuery, String name, String description, PackResolution r) {
        return chipMatches(chip, r) && queryMatches(normalizedQuery, name, description);
    }
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Expected: `PackFilterTest` 10 passed.

- [ ] **Step 5: Commit**

```bash
cd /c/Users/alexa/projects/packs && git add src/main/java/io/github/as9pa/fastpacks/filter src/test/java/io/github/as9pa/fastpacks/filter && git commit -q -F - <<'EOF'
feat(filter): PackFilter chips, query normalising and matching

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_016nrBSZRraXWQ2XcP2qa9AW
EOF
```

---

### Task 5: Background scan wiring (PackScan, Entry mixin, dev tally)

**Files:**
- Create: `src/main/java/io/github/as9pa/fastpacks/filter/ResolutionSink.java`
- Create: `src/main/java/io/github/as9pa/fastpacks/filter/PackScan.java`
- Modify: `src/main/java/io/github/as9pa/fastpacks/icon/IconLoader.java` (add `executor()`)
- Modify: `src/main/java/io/github/as9pa/fastpacks/EntryExtension.java`
- Modify: `src/main/java/io/github/as9pa/fastpacks/mixin/MixinResourcePackRepositoryEntry.java`
- Modify: `src/main/java/io/github/as9pa/fastpacks/dev/DevHarness.java`
- Test: `src/test/java/io/github/as9pa/fastpacks/filter/PackScanTest.java`

**Interfaces:**
- Consumes: `PackCache`, `ResolutionScanner.scan`, `PackResolution`, `IconLoader` pool.
- Produces: `ResolutionSink.acceptResolution(PackResolution)`; `PackScan.configure(File cacheDir)` (idempotent, cacheDir holds `packs.json`), `PackScan.submit(File pack, ResolutionSink)`, package-private `PackScan.reset()` for tests; `IconLoader.executor() -> java.util.concurrent.Executor`; `EntryExtension.fastpacks$resolution() -> PackResolution` (null while pending).

- [ ] **Step 1: Write the failing test**

`src/test/java/io/github/as9pa/fastpacks/filter/PackScanTest.java`:

```java
package io.github.as9pa.fastpacks.filter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class PackScanTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();
    private File cacheDir;

    @Before
    public void setUp() {
        PackScan.reset();
        cacheDir = new File(tmp.getRoot(), "config/fastpacks");
        PackScan.configure(cacheDir);
    }

    @After
    public void tearDown() {
        PackScan.reset();
    }

    private File stonePack(String name, int width) throws IOException {
        Map<String, byte[]> e = new LinkedHashMap<>();
        e.put("pack.mcmeta", TestPacks.MCMETA);
        e.put("assets/minecraft/textures/blocks/stone.png", TestPacks.png(width, width));
        return TestPacks.zip(new File(tmp.getRoot(), name), e);
    }

    private static PackResolution await(File pack) throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<PackResolution> got = new AtomicReference<>();
        final AtomicReference<String> thread = new AtomicReference<>();
        PackScan.submit(pack, r -> {
            got.set(r);
            thread.set(Thread.currentThread().getName());
            latch.countDown();
        });
        assertTrue("result not delivered within 10 s", latch.await(10, TimeUnit.SECONDS));
        assertTrue(thread.get(), thread.get().startsWith("fastpacks-icon-"));
        return got.get();
    }

    @Test
    public void scansOnTheBackgroundPoolAndWritesTheCache() throws Exception {
        File pack = stonePack("s32.zip", 32);
        assertEquals(PackResolution.textures(32), await(pack));

        File json = new File(cacheDir, "packs.json");
        long deadline = System.currentTimeMillis() + 10000;
        while (!json.isFile() && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        assertTrue("packs.json not written within 10 s", json.isFile());
        assertEquals(PackResolution.textures(32), new PackCache(json).lookup(PackCache.key(pack)));
    }

    @Test
    public void cachedValueIsServedWithoutRescanning() throws Exception {
        File pack = stonePack("s16.zip", 16);
        PackCache pre = new PackCache(new File(cacheDir, "packs.json"));
        pre.put(PackCache.key(pack), PackResolution.textures(64));   // deliberately wrong: proves the cache is trusted
        pre.flush();
        PackScan.reset();
        PackScan.configure(cacheDir);
        assertEquals(PackResolution.textures(64), await(pack));
    }

    @Test
    public void missingPackReportsUnknown() throws Exception {
        assertEquals(PackResolution.UNKNOWN, await(new File(tmp.getRoot(), "gone.zip")));
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Expected: compilation FAILS, cannot find symbol `PackScan` / `ResolutionSink`.

- [ ] **Step 3: Add the sink interface and expose the pool**

`src/main/java/io/github/as9pa/fastpacks/filter/ResolutionSink.java`:

```java
package io.github.as9pa.fastpacks.filter;

/** Receives a pack's scanned or cached resolution. Called on a background thread. */
public interface ResolutionSink {
    void acceptResolution(PackResolution resolution);
}
```

In `IconLoader.java` add `import java.util.concurrent.Executor;` and, directly after the `placeholder()` method:

```java
    /** The shared background pool; resolution scans queue here too so pack files are read on the same threads. */
    public static Executor executor() {
        return pool();
    }
```

- [ ] **Step 4: Implement PackScan**

```java
package io.github.as9pa.fastpacks.filter;

import io.github.as9pa.fastpacks.Log;
import io.github.as9pa.fastpacks.icon.IconLoader;
import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Queues one resolution scan per pack on the icon pool: cache hit, or scan then write-through.
 * The cache file is flushed by a task queued behind the scans, so a cold start writes it a few times at most.
 */
public final class PackScan {
    private static final Object LOCK = new Object();
    private static PackCache cache;
    private static final AtomicBoolean FLUSH_QUEUED = new AtomicBoolean();

    private PackScan() {}

    /** Points the cache at {@code cacheDir/packs.json}. First call wins; later calls are ignored. */
    public static void configure(File cacheDir) {
        synchronized (LOCK) {
            if (cache == null) {
                cache = new PackCache(new File(cacheDir, "packs.json"));
            }
        }
    }

    public static void submit(final File pack, final ResolutionSink sink) {
        final PackCache c;
        synchronized (LOCK) {
            c = cache;
        }
        if (c == null) {
            throw new IllegalStateException("PackScan.configure was not called");
        }
        IconLoader.executor().execute(new Runnable() {
            @Override
            public void run() {
                PackResolution r;
                try {
                    String key = PackCache.key(pack);
                    r = c.lookup(key);
                    if (r == null) {
                        r = ResolutionScanner.scan(pack);
                        c.put(key, r);
                        scheduleFlush(c);
                    }
                } catch (RuntimeException e) {
                    Log.LOG.debug("fastpacks: scan failed for {}: {}", pack.getName(), e.toString());
                    r = PackResolution.UNKNOWN;
                }
                sink.acceptResolution(r);
            }
        });
    }

    private static void scheduleFlush(final PackCache c) {
        if (FLUSH_QUEUED.compareAndSet(false, true)) {
            IconLoader.executor().execute(new Runnable() {
                @Override
                public void run() {
                    FLUSH_QUEUED.set(false);
                    c.flush();
                }
            });
        }
    }

    /** Tests only. */
    static void reset() {
        synchronized (LOCK) {
            cache = null;
        }
        FLUSH_QUEUED.set(false);
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

Expected: `PackScanTest` 3 passed.

- [ ] **Step 6: Extend EntryExtension and the Entry mixin**

Replace `src/main/java/io/github/as9pa/fastpacks/EntryExtension.java` with:

```java
package io.github.as9pa.fastpacks;

import io.github.as9pa.fastpacks.filter.PackResolution;
import io.github.as9pa.fastpacks.filter.ResolutionSink;
import io.github.as9pa.fastpacks.icon.IconSink;

/** Implemented by {@code ResourcePackRepository.Entry} via mixin. */
public interface EntryExtension extends IconSink, ResolutionSink {
    /** Recompute the cached identity key from the file's current state. */
    void fastpacks$refreshKey();

    /** Detected resolution, or null while the background scan has not finished. */
    PackResolution fastpacks$resolution();
}
```

In `MixinResourcePackRepositoryEntry.java`:

1. Add imports:

```java
import io.github.as9pa.fastpacks.filter.PackResolution;
import io.github.as9pa.fastpacks.filter.PackScan;
import net.minecraft.client.Minecraft;
```

2. Add a field next to `fastpacks$pendingIcon`:

```java
    @Unique private volatile PackResolution fastpacks$resolution;
```

3. Change the body of `fastpacks$deferIcon` to:

```java
        if (!(pack instanceof DefaultResourcePack)) {
            IconLoader.submit(resourcePackFile, this);
            // mcDataDir is set in Minecraft's constructor, so it is valid even during startGame.
            PackScan.configure(new File(Minecraft.getMinecraft().mcDataDir, "config" + File.separator + "fastpacks"));
            PackScan.submit(resourcePackFile, this);
        }
        return IconLoader.placeholder();
```

4. Add the two interface methods after `acceptIcon`:

```java
    @Override
    public void acceptResolution(PackResolution resolution) {
        fastpacks$resolution = resolution;
    }

    @Override
    public PackResolution fastpacks$resolution() {
        return fastpacks$resolution;
    }
```

- [ ] **Step 7: Add a resolution tally to the dev harness**

Open `src/main/java/io/github/as9pa/fastpacks/dev/DevHarness.java`. Find where it logs `fastpacks dev: {} packs, avg frame {} ms over {} frames` (just before `mc.shutdown()`), and add directly after that log line:

```java
            int r16 = 0, r32 = 0, r64 = 0, r128 = 0, overlay = 0, unknown = 0, pending = 0;
            for (net.minecraft.client.resources.ResourcePackRepository.Entry entry : mc.getResourcePackRepository().getRepositoryEntriesAll()) {
                io.github.as9pa.fastpacks.filter.PackResolution r = ((io.github.as9pa.fastpacks.EntryExtension) entry).fastpacks$resolution();
                if (r == null) { pending++; }
                else if (r.kind == io.github.as9pa.fastpacks.filter.PackResolution.Kind.OVERLAY) { overlay++; }
                else if (r.kind == io.github.as9pa.fastpacks.filter.PackResolution.Kind.UNKNOWN) { unknown++; }
                else if (r.res == 16) { r16++; } else if (r.res == 32) { r32++; } else if (r.res == 64) { r64++; } else { r128++; }
            }
            Log.LOG.info("fastpacks dev: resolutions 16x={} 32x={} 64x={} 128x+={} overlay={} unknown={} pending={}",
                    r16, r32, r64, r128, overlay, unknown, pending);
```

(Use proper imports instead of fully qualified names if the file's import block is easy to extend; either compiles.)

- [ ] **Step 8: Full build and dev-client check**

```bash
cd /c/Users/alexa/projects/packs && JAVA_HOME="/c/Program Files/Java/jdk-21.0.12.1" ./gradlew build 2>&1 | tail -15
```
Expected: BUILD SUCCESSFUL, all tests pass (v1's 33 plus the new ones).

```bash
cd /c/Users/alexa/projects/packs && JAVA_HOME="/c/Program Files/Java/jdk-21.0.12.1" ./gradlew runDevClient -Pfastpacks.devOpenPacksGui=true 2>&1 | grep -E "fastpacks|Exception|ERROR" | head -30
```
Expected: `fastpacks: rescanned N packs in ...`, `fastpacks dev: N packs, avg frame ...`, and a `fastpacks dev: resolutions ...` line where `pending` is small (ideally 0) and 16x/32x dominate. `run/config/fastpacks/packs.json` exists afterwards:

```bash
ls -la /c/Users/alexa/projects/packs/run/config/fastpacks/ && head -c 400 /c/Users/alexa/projects/packs/run/config/fastpacks/packs.json
```

Run the dev client a second time: the log should show `fastpacks: loaded N cached pack resolutions` and `pending=0`.

- [ ] **Step 9: Commit**

```bash
cd /c/Users/alexa/projects/packs && git add -A src && git commit -q -F - <<'EOF'
feat: scan pack resolution in the background and cache it

Entry mixin queues a PackScan next to the icon load; results land in a
volatile field read by the GUI. Dev harness logs a resolution tally.

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_016nrBSZRraXWQ2XcP2qa9AW
EOF
```

---

### Task 6: Toolbar, chips, filtered view and the screen mixins

**Files:**
- Create: `src/main/java/io/github/as9pa/fastpacks/gui/FilteredList.java`
- Create: `src/main/java/io/github/as9pa/fastpacks/gui/ChipButton.java`
- Create: `src/main/java/io/github/as9pa/fastpacks/gui/PacksToolbar.java`
- Create: `src/main/java/io/github/as9pa/fastpacks/mixin/MixinGuiResourcePackList.java`
- Create: `src/main/java/io/github/as9pa/fastpacks/mixin/MixinGuiScreenResourcePacks.java`
- Modify: `src/main/resources/mixins.fastpacks.json`
- Modify: `src/main/java/io/github/as9pa/fastpacks/mixin/FastPacksMixinPlugin.java`

**Interfaces:**
- Consumes: `PackFilter`, `PackResolution`, `EntryExtension.fastpacks$resolution()`.
- Produces: `FilteredList.fastpacks$setView(List<ResourcePackListEntry>)`; `ChipButton(int id, int x, int y, int width, PackFilter.Chip chip)`, `chip`, `isActive()`, `setActive(boolean)`; `PacksToolbar(FontRenderer, int left, int top, List<ResourcePackListEntry> source, String initialQuery)` with `chips()`, `visible()`, `query()`, `refresh()`, `draw()`, `activate(GuiButton) -> boolean`, `keyTyped(char,int) -> boolean`, `mouseClicked(int,int,int)`, `tick()`; constants `PacksToolbar.TOOLBAR_Y = 28`, `LIST_TOP = 52`, `WIDTH = 408`.

No unit tests: these classes touch Minecraft GUI types. Verification is the dev client (Step 7) and the owner's game (Task 8).

- [ ] **Step 1: FilteredList**

```java
package io.github.as9pa.fastpacks.gui;

import java.util.List;
import net.minecraft.client.resources.ResourcePackListEntry;

/** Implemented by {@code GuiResourcePackList} via mixin: when a view is set, the widget reads rows from it. */
public interface FilteredList {
    void fastpacks$setView(List<ResourcePackListEntry> view);
}
```

- [ ] **Step 2: ChipButton**

```java
package io.github.as9pa.fastpacks.gui;

import io.github.as9pa.fastpacks.filter.PackFilter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;

/** A vanilla-looking button that keeps the hovered look while its chip is the active filter. */
public final class ChipButton extends GuiButton {
    public static final int HEIGHT = 20;
    private static final int TEXT_NORMAL = 0xE0E0E0;
    private static final int TEXT_HOVER = 0xFFFFA0;

    public final PackFilter.Chip chip;
    private boolean active;

    public ChipButton(int id, int x, int y, int width, PackFilter.Chip chip) {
        super(id, x, y, width, HEIGHT, chip.label);
        this.chip = chip;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    @Override
    protected int getHoverState(boolean mouseOver) {
        return active || mouseOver ? 2 : 1;
    }

    /** Vanilla GuiButton.drawButton (1.8.9), except the text colour also follows {@link #active}. */
    @Override
    public void drawButton(Minecraft mc, int mouseX, int mouseY) {
        if (!visible) {
            return;
        }
        FontRenderer font = mc.fontRendererObj;
        mc.getTextureManager().bindTexture(buttonTextures);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        hovered = mouseX >= xPosition && mouseY >= yPosition && mouseX < xPosition + width && mouseY < yPosition + height;
        int state = getHoverState(hovered);
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
        GlStateManager.blendFunc(770, 771);
        drawTexturedModalRect(xPosition, yPosition, 0, 46 + state * 20, width / 2, height);
        drawTexturedModalRect(xPosition + width / 2, yPosition, 200 - width / 2, 46 + state * 20, width / 2, height);
        mouseDragged(mc, mouseX, mouseY);
        int colour = active || hovered ? TEXT_HOVER : TEXT_NORMAL;
        drawCenteredString(font, displayString, xPosition + width / 2, yPosition + (height - 8) / 2, colour);
    }
}
```

- [ ] **Step 3: PacksToolbar**

```java
package io.github.as9pa.fastpacks.gui;

import io.github.as9pa.fastpacks.EntryExtension;
import io.github.as9pa.fastpacks.filter.PackFilter;
import io.github.as9pa.fastpacks.filter.PackResolution;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.resources.ResourcePackListEntry;
import net.minecraft.client.resources.ResourcePackListEntryFound;
import net.minecraft.client.resources.ResourcePackRepository;

/**
 * The row above the two lists: search box, resolution chips, match counter. Owns the filtered view
 * that the Available list widget reads through. One instance per initGui.
 */
public final class PacksToolbar {
    public static final int TOOLBAR_Y = 28;
    public static final int LIST_TOP = 52;
    public static final int WIDTH = 408;
    public static final int HEIGHT = 20;
    public static final int SEARCH_WIDTH = 140;
    public static final int CHIP_GAP = 2;
    public static final int SEARCH_ID = 199;
    public static final int CHIP_ID_BASE = 100;
    private static final int MAX_QUERY_LENGTH = 40;
    private static final String PLACEHOLDER = "Search packs";
    private static final int PLACEHOLDER_COLOUR = 0x707070;
    private static final int COUNTER_COLOUR = 0xA0A0A0;

    private final FontRenderer font;
    private final int left;
    private final int top;
    private final List<ResourcePackListEntry> source;
    private final List<ResourcePackListEntry> visible = new ArrayList<>();
    private final List<ResourcePackListEntry> snapshot = new ArrayList<>();
    private final List<ChipButton> chips = new ArrayList<>();
    private final GuiTextField search;
    private String query;
    private boolean dirty = true;

    public PacksToolbar(FontRenderer font, int left, int top, List<ResourcePackListEntry> source, String initialQuery) {
        this.font = font;
        this.left = left;
        this.top = top;
        this.source = source;
        // GuiTextField draws its 1 px border outside the box, so inset by 1 to occupy exactly 140x20.
        search = new GuiTextField(SEARCH_ID, font, left + 1, top + 1, SEARCH_WIDTH - 2, HEIGHT - 2);
        search.setMaxStringLength(MAX_QUERY_LENGTH);
        search.setText(initialQuery == null ? "" : initialQuery);
        query = PackFilter.normalizeQuery(search.getText());

        int x = left + SEARCH_WIDTH + 4;
        int id = CHIP_ID_BASE;
        PackFilter.Chip active = PackFilter.activeChip();
        for (PackFilter.Chip chip : PackFilter.Chip.values()) {
            int width = font.getStringWidth(chip.label) + 10;
            ChipButton button = new ChipButton(id++, x, top, width, chip);
            button.setActive(chip == active);
            chips.add(button);
            x += width + CHIP_GAP;
        }
    }

    /** Add these to the screen's buttonList; the screen draws and clicks them. */
    public List<ChipButton> chips() {
        return chips;
    }

    /** The live filtered view. Same instance for the toolbar's lifetime; contents change on refresh(). */
    public List<ResourcePackListEntry> visible() {
        return visible;
    }

    public String query() {
        return search.getText();
    }

    /** Recomputes the view when the filter or the source list changed. Cheap when neither did. */
    public void refresh() {
        if (!dirty && sameAsSnapshot()) {
            return;
        }
        dirty = false;
        snapshot.clear();
        snapshot.addAll(source);
        visible.clear();
        PackFilter.Chip chip = PackFilter.activeChip();
        for (ResourcePackListEntry entry : source) {
            if (matches(chip, entry)) {
                visible.add(entry);
            }
        }
    }

    private boolean sameAsSnapshot() {
        if (snapshot.size() != source.size()) {
            return false;
        }
        for (int i = 0; i < snapshot.size(); i++) {
            if (snapshot.get(i) != source.get(i)) {
                return false;
            }
        }
        return true;
    }

    private boolean matches(PackFilter.Chip chip, ResourcePackListEntry entry) {
        if (!(entry instanceof ResourcePackListEntryFound)) {
            return chip == PackFilter.Chip.ALL && query.isEmpty();
        }
        ResourcePackRepository.Entry pack = ((ResourcePackListEntryFound) entry).func_148318_i();
        PackResolution resolution = ((EntryExtension) pack).fastpacks$resolution();
        return PackFilter.matches(chip, query, pack.getResourcePackName(), pack.getTexturePackDescription(), resolution);
    }

    /** True (and the filter updated) when the button is one of our chips. */
    public boolean activate(GuiButton button) {
        if (!(button instanceof ChipButton)) {
            return false;
        }
        PackFilter.Chip chip = ((ChipButton) button).chip;
        PackFilter.setActiveChip(chip);
        for (ChipButton b : chips) {
            b.setActive(b.chip == chip);
        }
        dirty = true;
        return true;
    }

    /** True when the search box consumed the key. */
    public boolean keyTyped(char typedChar, int keyCode) {
        if (!search.isFocused()) {
            return false;
        }
        search.textboxKeyTyped(typedChar, keyCode);
        String normalized = PackFilter.normalizeQuery(search.getText());
        if (!normalized.equals(query)) {
            query = normalized;
            dirty = true;
        }
        return true;
    }

    public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
        search.mouseClicked(mouseX, mouseY, mouseButton);
    }

    public void tick() {
        search.updateCursorCounter();
    }

    /** Call after the vanilla screen has drawn (buttons included). */
    public void draw() {
        search.drawTextBox();
        if (search.getText().isEmpty() && !search.isFocused()) {
            font.drawStringWithShadow(PLACEHOLDER, left + 5, top + 6, PLACEHOLDER_COLOUR);
        }
        String counter = visible.size() + " of " + source.size();
        font.drawStringWithShadow(counter, left + WIDTH - font.getStringWidth(counter), top + 6, COUNTER_COLOUR);
    }
}
```

- [ ] **Step 4: MixinGuiResourcePackList**

```java
package io.github.as9pa.fastpacks.mixin;

import io.github.as9pa.fastpacks.gui.FilteredList;
import java.util.List;
import net.minecraft.client.gui.GuiResourcePackList;
import net.minecraft.client.resources.ResourcePackListEntry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Every GuiSlot draw and mouse path goes through getSize()/getListEntry(). When the toolbar attaches a
 * view, answer from it; the vanilla list itself is never mutated. Only the Available list gets a view.
 */
@Mixin(GuiResourcePackList.class)
public abstract class MixinGuiResourcePackList implements FilteredList {
    @Unique private List<ResourcePackListEntry> fastpacks$view;

    @Override
    public void fastpacks$setView(List<ResourcePackListEntry> view) {
        fastpacks$view = view;
    }

    @Inject(method = "getSize", at = @At("HEAD"), cancellable = true)
    private void fastpacks$filteredSize(CallbackInfoReturnable<Integer> cir) {
        if (fastpacks$view != null) {
            cir.setReturnValue(fastpacks$view.size());
        }
    }

    @Inject(method = "getListEntry(I)Lnet/minecraft/client/resources/ResourcePackListEntry;", at = @At("HEAD"), cancellable = true)
    private void fastpacks$filteredEntry(int index, CallbackInfoReturnable<ResourcePackListEntry> cir) {
        if (fastpacks$view != null) {
            cir.setReturnValue(fastpacks$view.get(index));
        }
    }
}
```

The explicit descriptor on `getListEntry` matters: the class also has a synthetic bridge `getListEntry(I)Lnet/minecraft/client/gui/GuiListExtended$IGuiListEntry;` and we only want the real method.

- [ ] **Step 5: MixinGuiScreenResourcePacks**

```java
package io.github.as9pa.fastpacks.mixin;

import io.github.as9pa.fastpacks.gui.FilteredList;
import io.github.as9pa.fastpacks.gui.PacksToolbar;
import java.io.IOException;
import java.util.List;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiResourcePackAvailable;
import net.minecraft.client.gui.GuiResourcePackSelected;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiScreenResourcePacks;
import net.minecraft.client.resources.ResourcePackListEntry;
import org.lwjgl.input.Keyboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds the fastpacks toolbar (search, chips, counter) and pushes both lists down to make room. */
@Mixin(GuiScreenResourcePacks.class)
public abstract class MixinGuiScreenResourcePacks extends GuiScreen {
    private static final int LIST_WIDTH = 200;
    private static final int LIST_BOTTOM_INSET = 51;

    @Shadow private List<ResourcePackListEntry> availableResourcePacks;
    @Shadow private GuiResourcePackAvailable availableResourcePacksList;
    @Shadow private GuiResourcePackSelected selectedResourcePacksList;

    @Unique private PacksToolbar fastpacks$toolbar;

    @Inject(method = "initGui", at = @At("RETURN"))
    private void fastpacks$addToolbar(CallbackInfo ci) {
        String keptQuery = fastpacks$toolbar == null ? "" : fastpacks$toolbar.query();   // survives a window resize
        int left = width / 2 - 204;
        fastpacks$toolbar = new PacksToolbar(fontRendererObj, left, PacksToolbar.TOOLBAR_Y, availableResourcePacks, keptQuery);
        buttonList.addAll(fastpacks$toolbar.chips());

        // setDimensions resets left/right, so re-apply the x bounds vanilla set in initGui.
        availableResourcePacksList.setDimensions(LIST_WIDTH, height, PacksToolbar.LIST_TOP, height - LIST_BOTTOM_INSET);
        availableResourcePacksList.setSlotXBoundsFromLeft(left);
        selectedResourcePacksList.setDimensions(LIST_WIDTH, height, PacksToolbar.LIST_TOP, height - LIST_BOTTOM_INSET);
        selectedResourcePacksList.setSlotXBoundsFromLeft(width / 2 + 4);

        ((FilteredList) availableResourcePacksList).fastpacks$setView(fastpacks$toolbar.visible());
        fastpacks$toolbar.refresh();
    }

    @Inject(method = "drawScreen", at = @At("HEAD"))
    private void fastpacks$refreshView(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        if (fastpacks$toolbar != null) {
            fastpacks$toolbar.refresh();
        }
    }

    @Inject(method = "drawScreen", at = @At("RETURN"))
    private void fastpacks$drawToolbar(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        if (fastpacks$toolbar != null) {
            fastpacks$toolbar.draw();
        }
    }

    @Inject(method = "actionPerformed", at = @At("HEAD"), cancellable = true)
    private void fastpacks$onChipPressed(GuiButton button, CallbackInfo ci) {
        if (fastpacks$toolbar != null && fastpacks$toolbar.activate(button)) {
            ci.cancel();
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"))
    private void fastpacks$focusSearch(int mouseX, int mouseY, int mouseButton, CallbackInfo ci) {
        if (fastpacks$toolbar != null) {
            fastpacks$toolbar.mouseClicked(mouseX, mouseY, mouseButton);
        }
    }

    // GuiScreenResourcePacks does not override these two, so nothing vanilla is replaced: the mixin
    // merges them as ordinary overrides of GuiScreen and defers to super.

    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (fastpacks$toolbar != null && keyCode != Keyboard.KEY_ESCAPE && fastpacks$toolbar.keyTyped(typedChar, keyCode)) {
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        if (fastpacks$toolbar != null) {
            fastpacks$toolbar.tick();
        }
    }
}
```

- [ ] **Step 6: Register the mixins and extend baseline mode**

`src/main/resources/mixins.fastpacks.json` `client` array becomes:

```json
  "client": [
    "MixinResourcePackRepositoryEntry",
    "MixinResourcePackRepository",
    "MixinGuiListExtended",
    "MixinGuiResourcePackList",
    "MixinGuiScreenResourcePacks"
  ],
```

In `FastPacksMixinPlugin.java`, the `OPTIMISATIONS` set becomes (baseline must be vanilla behaviour, toolbar included):

```java
    private static final Set<String> OPTIMISATIONS = new HashSet<>(Arrays.asList(
            "io.github.as9pa.fastpacks.mixin.MixinResourcePackRepositoryEntry",
            "io.github.as9pa.fastpacks.mixin.MixinGuiListExtended",
            "io.github.as9pa.fastpacks.mixin.MixinGuiResourcePackList",
            "io.github.as9pa.fastpacks.mixin.MixinGuiScreenResourcePacks",
            "io.github.as9pa.fastpacks.mixin.MixinResourcePackListEntry"));
```

(`MixinResourcePackListEntry` arrives in Task 7; listing it now is harmless.)

- [ ] **Step 7: Build and dev-client check**

```bash
cd /c/Users/alexa/projects/packs && JAVA_HOME="/c/Program Files/Java/jdk-21.0.12.1" ./gradlew build 2>&1 | tail -15
```
Expected: BUILD SUCCESSFUL. If the Mixin annotation processor reports it cannot resolve `getListEntry(I)Lnet/minecraft/client/resources/ResourcePackListEntry;`, check the descriptor spelling; the refmap must contain a mapping for it (`grep getListEntry build/resources/main/mixins.fastpacks.refmap.json`, or inside the jar).

```bash
cd /c/Users/alexa/projects/packs && JAVA_HOME="/c/Program Files/Java/jdk-21.0.12.1" ./gradlew runDevClient -Pfastpacks.devOpenPacksGui=true 2>&1 | grep -E "fastpacks|Mixin|Exception|ERROR|Caused" | head -40
```
Expected: no `Exception`/`Caused by` lines; the `fastpacks dev:` lines appear; avg frame stays within a few tenths of a ms of v1's 0.61 ms.

Also run the baseline switch to prove the toolbar mixins are off there:

```bash
cd /c/Users/alexa/projects/packs && JAVA_HOME="/c/Program Files/Java/jdk-21.0.12.1" ./gradlew runDevClient -Pfastpacks.devOpenPacksGui=true -Pfastpacks.baseline=true 2>&1 | grep -E "fastpacks|Exception" | head -20
```
Expected: `BASELINE mode` warning, no exceptions.

- [ ] **Step 8: Commit**

```bash
cd /c/Users/alexa/projects/packs && git add -A src && git commit -q -F - <<'EOF'
feat(gui): search box, resolution chips and match counter on the packs screen

Toolbar at y=28, lists pushed to y=52. The Available list reads rows through a
filtered view; the vanilla list is never mutated.

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_016nrBSZRraXWQ2XcP2qa9AW
EOF
```

---

### Task 7: Row badge

**Files:**
- Create: `src/main/java/io/github/as9pa/fastpacks/gui/RowBadge.java`
- Create: `src/main/java/io/github/as9pa/fastpacks/mixin/MixinResourcePackListEntry.java`
- Modify: `src/main/resources/mixins.fastpacks.json`

**Interfaces:**
- Consumes: `EntryExtension.fastpacks$resolution()`, `PackResolution.badge()`.
- Produces: `RowBadge.text(ResourcePackListEntry) -> String` ("" when nothing to show), `RowBadge.NAME_WIDTH = 110`, `RowBadge.RIGHT_EDGE = 188`.

Layout: a row draws at `x = list.left + 2`; the scroll bar starts at `x + 192`; so the badge's right edge sits at `x + 188`. The widest badge, "overlay", is 39 px in the vanilla font, so it starts at `x + 149`; the name, starting at `x + 34`, is trimmed to 110 px and ends by `x + 144`.

- [ ] **Step 1: RowBadge**

```java
package io.github.as9pa.fastpacks.gui;

import io.github.as9pa.fastpacks.EntryExtension;
import io.github.as9pa.fastpacks.filter.PackResolution;
import net.minecraft.client.resources.ResourcePackListEntry;
import net.minecraft.client.resources.ResourcePackListEntryDefault;
import net.minecraft.client.resources.ResourcePackListEntryFound;

/** The grey resolution text at the right end of a pack row. */
public final class RowBadge {
    /** Vanilla trims the name to 157 px; with a badge on the same line it gets 110. */
    public static final int NAME_WIDTH = 110;
    /** Right edge of the badge, relative to the row's x. 4 px clear of the scroll bar. */
    public static final int RIGHT_EDGE = 188;
    public static final int COLOUR = 0xA0A0A0;

    private RowBadge() {}

    public static String text(ResourcePackListEntry entry) {
        if (entry instanceof ResourcePackListEntryDefault) {
            return "16x";
        }
        if (entry instanceof ResourcePackListEntryFound) {
            PackResolution r = ((EntryExtension) ((ResourcePackListEntryFound) entry).func_148318_i()).fastpacks$resolution();
            return r == null ? "" : r.badge();
        }
        return "";
    }
}
```

- [ ] **Step 2: MixinResourcePackListEntry**

```java
package io.github.as9pa.fastpacks.mixin;

import io.github.as9pa.fastpacks.gui.RowBadge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.resources.ResourcePackListEntry;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Draws the resolution badge on every row and shortens the name trim so the two never overlap. */
@Mixin(ResourcePackListEntry.class)
public abstract class MixinResourcePackListEntry {
    @Shadow @Final protected Minecraft mc;

    /**
     * drawEntry uses the literal 157 three times: twice for the name ("if (width > 157)" and
     * "trimStringToWidth(s, 157 - ...)"), once for the description. Only the first two change.
     */
    @ModifyConstant(method = "drawEntry",
            constant = {@Constant(intValue = 157, ordinal = 0), @Constant(intValue = 157, ordinal = 1)},
            require = 2)
    private int fastpacks$nameWidthWithBadge(int vanillaWidth) {
        return RowBadge.NAME_WIDTH;
    }

    @Inject(method = "drawEntry", at = @At("RETURN"))
    private void fastpacks$drawBadge(int slotIndex, int x, int y, int listWidth, int slotHeight,
                                     int mouseX, int mouseY, boolean isSelected, CallbackInfo ci) {
        String badge = RowBadge.text((ResourcePackListEntry) (Object) this);
        if (badge.isEmpty()) {
            return;
        }
        FontRenderer font = mc.fontRendererObj;
        font.drawStringWithShadow(badge, x + RowBadge.RIGHT_EDGE - font.getStringWidth(badge), y + 1, RowBadge.COLOUR);
    }
}
```

- [ ] **Step 3: Register the mixin**

Add `"MixinResourcePackListEntry"` to the `client` array in `mixins.fastpacks.json` (after `MixinGuiScreenResourcePacks`).

- [ ] **Step 4: Build and dev-client check**

```bash
cd /c/Users/alexa/projects/packs && JAVA_HOME="/c/Program Files/Java/jdk-21.0.12.1" ./gradlew build 2>&1 | tail -15
```
Expected: BUILD SUCCESSFUL.

```bash
cd /c/Users/alexa/projects/packs && JAVA_HOME="/c/Program Files/Java/jdk-21.0.12.1" ./gradlew runDevClient -Pfastpacks.devOpenPacksGui=true 2>&1 | grep -E "fastpacks|Exception|ERROR|Caused|ModifyConstant|Critical" | head -40
```
Expected: no exceptions and no Mixin "Critical injection failure" line. If Mixin reports that `@ModifyConstant` found fewer than 2 targets, inspect the bytecode: `javap -c -p` on `net/minecraft/client/resources/ResourcePackListEntry.class` from the mapped jar (`C:\Users\alexa\.gradle\caches\essential-loom\1.8.9\de.oceanlabs.mcp.mcp_stable.1_8_9.22-1.8.9-forge-1.8.9-11.15.1.2318-1.8.9\minecraft-mapped.jar`) and count the `sipush 157` instructions in `drawEntry`; adjust the ordinals to the first two.

- [ ] **Step 5: Commit**

```bash
cd /c/Users/alexa/projects/packs && git add -A src && git commit -q -F - <<'EOF'
feat(gui): resolution badge on every pack row

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_016nrBSZRraXWQ2XcP2qa9AW
EOF
```

---

### Task 8: Version 0.2.0, @Mod version, docs, install

**Files:**
- Modify: `gradle.properties` (`version = 0.2.0`)
- Modify: `src/main/java/io/github/as9pa/fastpacks/FastPacks.java`
- Modify: `README.md`
- Modify: `docs/superpowers/specs/2026-09-06-fastpacks-v2-design.md` (status line)

- [ ] **Step 1: Version bump and @Mod version**

`gradle.properties`: change `version = 0.1.0` to `version = 0.2.0`.

`FastPacks.java`: change the annotation and add the constant so Forge stops warning about a missing version:

```java
@Mod(modid = FastPacks.MODID, version = FastPacks.VERSION, useMetadata = true, clientSideOnly = true,
        acceptedMinecraftVersions = "[1.8.9]")
public class FastPacks {
    public static final String MODID = "fastpacks";
    /** Keep in step with gradle.properties. */
    public static final String VERSION = "0.2.0";
```

- [ ] **Step 2: README**

In `README.md`:

1. Under `## What fastpacks does`, append two bullets after the `Logs ...` bullet:

```markdown
- Adds a toolbar above the lists: a search box, resolution chips (All / 16x / 32x / 64x / 128x+ / Overlay) and a match counter. Only the Available list is filtered; selected packs never disappear.
- Shows each pack's resolution at the right end of its row. Resolution is read from the PNG headers of a dozen PvP textures (swords, bow, stone, planks...) on the background threads, once per pack, and cached in `config/fastpacks/packs.json`. "Overlay" means the pack has none of those textures. Delete the cache file to rescan.
```

2. In `## Install`, change `fastpacks-0.1.0.jar` to `fastpacks-0.2.0.jar` and change "the three patched methods" to "one of the patched vanilla methods".

3. Replace the `## Roadmap` section body with:

```markdown
Done in 0.2.0: resolution filters, search, per-pack cache. Ideas: sorting, folders, a settings screen.
```

4. In `## Build`, change `build/libs/fastpacks-0.1.0.jar` to `build/libs/fastpacks-0.2.0.jar`.

- [ ] **Step 3: Spec status**

In `docs/superpowers/specs/2026-09-06-fastpacks-v2-design.md` change the `Status:` line to:

```
Status: implemented in 0.2.0 (branch fastpacks-v2). Deviations: resolution results are published through a volatile field rather than a client-thread handoff; the cache loads on the background thread; the name trim is 110 px, not 118.
```

- [ ] **Step 4: Build, full test run, install for the owner**

```bash
cd /c/Users/alexa/projects/packs && JAVA_HOME="/c/Program Files/Java/jdk-21.0.12.1" ./gradlew clean build 2>&1 | tail -15 && ls -la build/libs/
```
Expected: BUILD SUCCESSFUL; `build/libs/fastpacks-0.2.0.jar` present.

```bash
cd /c/Users/alexa/projects/packs && JAVA_HOME="/c/Program Files/Java/jdk-21.0.12.1" ./gradlew runDevClient -Pfastpacks.devOpenPacksGui=true 2>&1 | grep -E "fastpacks|missing the required element|Exception" | head -20
```
Expected: no "missing the required element 'version'" warning, no exceptions.

Install (replace the 0.1.0 jar; the old one is removed so Forge does not load two copies):

```bash
rm -f "$APPDATA/.minecraft/mods/fastpacks-0.1.0.jar" && cp /c/Users/alexa/projects/packs/build/libs/fastpacks-0.2.0.jar "$APPDATA/.minecraft/mods/" && ls -la "$APPDATA/.minecraft/mods/" | grep fastpacks
```

- [ ] **Step 5: Commit**

```bash
cd /c/Users/alexa/projects/packs && git add -A && git commit -q -F - <<'EOF'
chore: release 0.2.0 - filters, search, badges, @Mod version

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_016nrBSZRraXWQ2XcP2qa9AW
EOF
```

---

## Self-review notes

- Spec coverage: toolbar layout (Task 6), behaviour and filtered view (Task 6), detection (Tasks 1-2), cache (Task 3, 5), chip memory (Task 4), badge (Task 7), `@Mod` version (Task 8), baseline switch (Task 6 Step 6), OptiFine compatibility (verified before planning: none of `GuiScreenResourcePacks`, `GuiResourcePackList`, `ResourcePackListEntry`, `GuiButton`, `GuiTextField` are patched by HD U M5; see the orchestrator's note in the spec).
- Deviations from the spec, all recorded in Task 8 Step 3: volatile publish instead of client-thread handoff; cache loaded on the background thread (avoids client-thread stat calls); name trim 110 px.
- Type consistency: `PackResolution.textures(int)`, `fromWidths(List<Integer>)`, `badge()`; `PackFilter.Chip.label`; `PackScan.configure(File)`/`submit(File, ResolutionSink)`; `PacksToolbar.TOOLBAR_Y/LIST_TOP/WIDTH`; `RowBadge.NAME_WIDTH/RIGHT_EDGE/COLOUR`; `FilteredList.fastpacks$setView`; `EntryExtension.fastpacks$resolution()` are used with the same names in every task.
