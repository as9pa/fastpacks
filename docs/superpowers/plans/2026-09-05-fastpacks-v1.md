# fastpacks v1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A Forge 1.8.9 client mod that makes the Resource Packs screen open in tens of milliseconds instead of seconds, moves pack-icon decoding off the main thread, and skips drawing off-screen rows.

**Architecture:** Three SpongePowered Mixins (`@Inject`/`@Redirect` only, never `@Overwrite`) into `ResourcePackRepository`, its inner `Entry`, and `GuiListExtended`, each delegating to small pure-Java helpers that carry the unit tests. A background executor decodes icons; a dev-only harness measures before/after on the owner's real 248-pack folder.

**Tech Stack:** Java 8 target, Gradle 8.8 (run on JDK 21) with Essential's architectury-loom 0.10 fork, Forge 1.8.9-11.15.1.2318, MCP `stable_22`, Mixin 0.7.11-SNAPSHOT runtime + 0.8.5-SNAPSHOT annotation processor, JUnit 4.13.2, Log4j 2 (ships with Minecraft).

**Spec:** `docs/superpowers/specs/2026-09-05-fastpacks-design.md`

## Global Constraints

- Minecraft **1.8.9** only; Forge **1.8.9-11.15.1.2318-1.8.9**; mappings **`de.oceanlabs.mcp:mcp_stable:22-1.8.9`**.
- Mixin runtime **`org.spongepowered:mixin:0.7.11-SNAPSHOT`** (bundled, not relocated); annotation processor **`org.spongepowered:mixin:0.8.5-SNAPSHOT`**. Mixin 0.7 shades ASM under `org.spongepowered.asm.lib` — import `ClassNode` from there.
- **`@Inject` and `@Redirect` only. No `@Overwrite`.** Never inject into a method OptiFine modifies (`GuiSlot.drawScreen`, anything in `TextureManager`/`TextureUtil`/`AbstractResourcePack`).
- **No lambdas or anonymous classes inside mixin classes** (Mixin 0.7 merge safety). Lambdas are fine in ordinary classes.
- Mod id and display name: **`fastpacks`** (lowercase). Base package **`io.github.as9pa.fastpacks`**. Version **`0.1.0`**.
- Mixin config file **`mixins.fastpacks.json`**, refmap **`mixins.fastpacks.refmap.json`**, `"defaultRequire": 1`, `"required": true`.
- Icon cap **128 px** on the longest side; executor threads **`clamp(cores - 1, 1, 4)`**, daemon, `Thread.MIN_PRIORITY`, named `fastpacks-icon-N`.
- Log lines: `fastpacks: rescanned {} packs in {} ms` (INFO, every scan) and `fastpacks dev: {} packs, avg frame {} ms over {} frames` (dev harness only).
- System properties (dev only): `fastpacks.baseline`, `fastpacks.devOpenPacksGui`. Forwarded to the dev client from Gradle project properties of the same name (`-Pfastpacks.baseline=true`).
- Gradle must run on **JDK 21**: prefix every Gradle command with `JAVA_HOME="/c/Program Files/Java/jdk-21.0.12.1"`. The compile/test/runClient toolchain is **JDK 8** at `C:\Program Files\Eclipse Adoptium\jdk-8.0.504.1-hotspot`.
- Work directly on `main` in `C:\Users\alexa\projects\packs` (fresh repo, no worktree). Commit after every task with the trailer lines below.
- Commit trailer (every commit):
  ```
  Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
  Claude-Session: https://claude.ai/code/session_016nrBSZRraXWQ2XcP2qa9AW
  ```
- The owner's real packs live at `%APPDATA%\.minecraft\resourcepacks` (248 items). Read-only use via a directory junction; never write into it.

---

## File structure

| Path | Responsibility |
|---|---|
| `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`, `log4j2.xml`, `gradlew*`, `gradle/wrapper/*` | Build. Adapted from `lineargraph/Forge1.8.9Template`. |
| `.gitignore` | Ignore `run/`, `build/`, `.gradle/`, IDE dirs. |
| `src/main/resources/mcmod.info` | Forge mod metadata (expanded by `processResources`). |
| `src/main/resources/mixins.fastpacks.json` | Mixin config: three client mixins + plugin. |
| `src/main/java/io/github/as9pa/fastpacks/FastPacks.java` | `@Mod` entry point; registers `DevHarness` when the property is set. |
| `src/main/java/io/github/as9pa/fastpacks/Log.java` | Shared Log4j logger `fastpacks`. |
| `src/main/java/io/github/as9pa/fastpacks/PackKey.java` | Pure: vanilla-format identity string for a `File`. |
| `src/main/java/io/github/as9pa/fastpacks/ListCulling.java` | Pure: `isOffscreen(y, height, top, bottom)`. |
| `src/main/java/io/github/as9pa/fastpacks/icon/IconImages.java` | Pure: `decode(InputStream)`, `downscale(BufferedImage, int)`. |
| `src/main/java/io/github/as9pa/fastpacks/icon/IconSink.java` | Callback interface `acceptIcon(BufferedImage)`. |
| `src/main/java/io/github/as9pa/fastpacks/icon/IconLoader.java` | Background executor, placeholder, `submit(File, IconSink)`, `load(File)`. |
| `src/main/java/io/github/as9pa/fastpacks/mixin/EntryExtension.java` | Duck interface: `fastpacks$refreshKey()` + `IconSink`. |
| `src/main/java/io/github/as9pa/fastpacks/mixin/MixinResourcePackRepositoryEntry.java` | Cached identity key; deferred icon; swap-in on bind. |
| `src/main/java/io/github/as9pa/fastpacks/mixin/MixinResourcePackRepository.java` | Key refresh at scan start; timing log at scan end. |
| `src/main/java/io/github/as9pa/fastpacks/mixin/MixinGuiListExtended.java` | Off-screen culling for `GuiResourcePackList` only. |
| `src/main/java/io/github/as9pa/fastpacks/mixin/FastPacksMixinPlugin.java` | `IMixinConfigPlugin`: baseline toggle. |
| `src/main/java/io/github/as9pa/fastpacks/dev/DevHarness.java` | Dev-only: auto-open packs screen, time frames, shut down. |
| `src/test/java/io/github/as9pa/fastpacks/PackKeyTest.java` | Unit tests. |
| `src/test/java/io/github/as9pa/fastpacks/ListCullingTest.java` | Unit tests. |
| `src/test/java/io/github/as9pa/fastpacks/icon/IconImagesTest.java` | Unit tests. |
| `src/test/java/io/github/as9pa/fastpacks/icon/IconLoaderTest.java` | Unit tests. |
| `README.md` | What it does, measured numbers, install, build. |

Verified MCP `stable_22` member names used below (from `mcp_stable-22-1.8.9` `fields.csv`/`methods.csv`): `repositoryEntriesAll`, `resourcePackFile`, `texturePackIcon`, `locationTexturePackIcon`, `updateRepositoryEntriesAll`, `updateResourcePack`, `bindTexturePackIcon`, `drawSlot`, `getPackImage`, `deleteTexture`, `getRepositoryEntriesAll`, `GuiSlot.top`, `GuiSlot.bottom`.

---

### Task 1: Project scaffold that builds a Forge jar

**Files:**
- Create: `build.gradle.kts`, `settings.gradle.kts`, `gradle.properties`, `log4j2.xml`, `.gitignore`, `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, `gradle/wrapper/gradle-wrapper.properties`
- Create: `src/main/resources/mcmod.info`, `src/main/resources/mixins.fastpacks.json`
- Create: `src/main/java/io/github/as9pa/fastpacks/FastPacks.java`

**Interfaces:**
- Produces: a working `./gradlew build` and `./gradlew test`, a `runClient` task pinned to JDK 8 that forwards `-Pfastpacks.baseline` / `-Pfastpacks.devOpenPacksGui` as system properties, and the `@Mod` class `io.github.as9pa.fastpacks.FastPacks` that later tasks extend.

- [ ] **Step 1: Download the Gradle wrapper and log4j config from the template**

```bash
cd /c/Users/alexa/projects/packs
B=https://raw.githubusercontent.com/lineargraph/Forge1.8.9Template/master
mkdir -p gradle/wrapper
curl -sfL "$B/gradlew" -o gradlew
curl -sfL "$B/gradlew.bat" -o gradlew.bat
curl -sfL "$B/gradle/wrapper/gradle-wrapper.jar" -o gradle/wrapper/gradle-wrapper.jar
curl -sfL "$B/gradle/wrapper/gradle-wrapper.properties" -o gradle/wrapper/gradle-wrapper.properties
curl -sfL "$B/log4j2.xml" -o log4j2.xml
chmod +x gradlew
grep distributionUrl gradle/wrapper/gradle-wrapper.properties
```
Expected: `distributionUrl=https\://services.gradle.org/distributions/gradle-8.8-bin.zip`; `gradle-wrapper.jar` is about 43 KB.

- [ ] **Step 2: Write `.gitignore`**

```gitignore
.idea/
.vscode/
run/
build/
.gradle/
*.log
```

- [ ] **Step 3: Write `settings.gradle.kts`**

```kotlin
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://oss.sonatype.org/content/repositories/snapshots")
        maven("https://maven.architectury.dev/")
        maven("https://maven.fabricmc.net")
        maven("https://maven.minecraftforge.net/")
        maven("https://repo.spongepowered.org/maven/")
        maven("https://repo.essential.gg/repository/maven-releases/")
    }
    resolutionStrategy {
        eachPlugin {
            when (requested.id.id) {
                "gg.essential.loom" -> useModule("gg.essential:architectury-loom:${requested.version}")
            }
        }
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version ("0.6.0")
}

rootProject.name = "fastpacks"
```

- [ ] **Step 4: Write `gradle.properties`**

```properties
loom.platform=forge
org.gradle.jvmargs=-Xmx2g
baseGroup = io.github.as9pa.fastpacks
mcVersion = 1.8.9
modid = fastpacks
version = 0.1.0
```

- [ ] **Step 5: Write `build.gradle.kts`**

```kotlin
import org.apache.commons.lang3.SystemUtils

plugins {
    idea
    java
    id("gg.essential.loom") version "0.10.0.+"
    id("dev.architectury.architectury-pack200") version "0.1.3"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

val baseGroup: String by project
val mcVersion: String by project
val version: String by project
val modid: String by project

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(8))
}

loom {
    log4jConfigs.from(file("log4j2.xml"))
    launchConfigs {
        "client" {
            property("mixin.debug", "true")
            arg("--tweakClass", "org.spongepowered.asm.launch.MixinTweaker")
        }
    }
    runConfigs {
        "client" {
            if (SystemUtils.IS_OS_MAC_OSX) {
                vmArgs.remove("-XstartOnFirstThread")
            }
        }
        remove(getByName("server"))
    }
    forge {
        pack200Provider.set(dev.architectury.pack200.java.Pack200Adapter())
        mixinConfig("mixins.$modid.json")
    }
    mixin {
        defaultRefmapName.set("mixins.$modid.refmap.json")
    }
}

sourceSets.main {
    output.setResourcesDir(sourceSets.main.flatMap { it.java.classesDirectory })
}

repositories {
    mavenCentral()
    maven("https://repo.spongepowered.org/maven/")
}

val shadowImpl: Configuration by configurations.creating {
    configurations.implementation.get().extendsFrom(this)
}

dependencies {
    minecraft("com.mojang:minecraft:1.8.9")
    mappings("de.oceanlabs.mcp:mcp_stable:22-1.8.9")
    forge("net.minecraftforge:forge:1.8.9-11.15.1.2318-1.8.9")

    shadowImpl("org.spongepowered:mixin:0.7.11-SNAPSHOT") {
        isTransitive = false
    }
    annotationProcessor("org.spongepowered:mixin:0.8.5-SNAPSHOT")

    testImplementation("junit:junit:4.13.2")
}

tasks.withType(JavaCompile::class) {
    options.encoding = "UTF-8"
}

tasks.test {
    useJUnit()
    testLogging {
        events("passed", "failed", "skipped")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

tasks.withType(org.gradle.jvm.tasks.Jar::class) {
    archiveBaseName.set(modid)
    manifest.attributes.run {
        this["FMLCorePluginContainsFMLMod"] = "true"
        this["ForceLoadAsMod"] = "true"
        this["TweakClass"] = "org.spongepowered.asm.launch.MixinTweaker"
        this["MixinConfigs"] = "mixins.$modid.json"
    }
}

tasks.processResources {
    inputs.property("version", project.version)
    inputs.property("mcversion", mcVersion)
    inputs.property("modid", modid)

    filesMatching(listOf("mcmod.info")) {
        expand(inputs.properties)
    }
}

// The dev client must run on the Java 8 toolchain, not on the JVM running Gradle.
// Forward the two fastpacks.* dev switches from -P project properties to the client JVM.
tasks.named<JavaExec>("runClient") {
    javaLauncher.set(javaToolchains.launcherFor(java.toolchain))
    listOf("fastpacks.baseline", "fastpacks.devOpenPacksGui").forEach { key ->
        (project.findProperty(key) as String?)?.let { systemProperty(key, it) }
    }
}

val remapJar by tasks.named<net.fabricmc.loom.task.RemapJarTask>("remapJar") {
    archiveClassifier.set("")
    from(tasks.shadowJar)
    input.set(tasks.shadowJar.get().archiveFile)
}

tasks.jar {
    archiveClassifier.set("without-deps")
    destinationDirectory.set(layout.buildDirectory.dir("intermediates"))
}

tasks.shadowJar {
    destinationDirectory.set(layout.buildDirectory.dir("intermediates"))
    archiveClassifier.set("non-obfuscated-with-deps")
    configurations = listOf(shadowImpl)
}

tasks.assemble.get().dependsOn(tasks.remapJar)
```

- [ ] **Step 6: Write `src/main/resources/mcmod.info`**

```json
[
  {
    "modid": "${modid}",
    "name": "fastpacks",
    "description": "Makes the Resource Packs screen open instantly with hundreds of packs. Background icon loading, off-screen row culling.",
    "version": "${version}",
    "mcversion": "${mcversion}",
    "url": "",
    "updateUrl": "",
    "authorList": ["as9pa"],
    "credits": "",
    "logoFile": "",
    "screenshots": [],
    "dependencies": []
  }
]
```

- [ ] **Step 7: Write an empty-for-now `src/main/resources/mixins.fastpacks.json`**

```json
{
  "required": true,
  "minVersion": "0.7",
  "package": "io.github.as9pa.fastpacks.mixin",
  "refmap": "mixins.fastpacks.refmap.json",
  "compatibilityLevel": "JAVA_8",
  "client": [],
  "injectors": { "defaultRequire": 1 }
}
```

- [ ] **Step 8: Write `src/main/java/io/github/as9pa/fastpacks/FastPacks.java`**

```java
package io.github.as9pa.fastpacks;

import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;

@Mod(modid = FastPacks.MODID, useMetadata = true, clientSideOnly = true, acceptedMinecraftVersions = "[1.8.9]")
public class FastPacks {
    public static final String MODID = "fastpacks";

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        Log.LOG.info("fastpacks initialised");
    }
}
```

- [ ] **Step 9: Write `src/main/java/io/github/as9pa/fastpacks/Log.java`**

```java
package io.github.as9pa.fastpacks;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Single shared logger. Safe to touch from mixins and from the Mixin config plugin (no Minecraft classes). */
public final class Log {
    public static final Logger LOG = LogManager.getLogger("fastpacks");

    private Log() {}
}
```

- [ ] **Step 10: Make sure Gradle can find JDK 8 as a toolchain**

Append (create the file if missing) to `%USERPROFILE%\.gradle\gradle.properties`:

```bash
mkdir -p "$USERPROFILE/.gradle"
grep -q 'org.gradle.java.installations.paths' "$USERPROFILE/.gradle/gradle.properties" 2>/dev/null || \
  echo 'org.gradle.java.installations.paths=C:/Program Files/Eclipse Adoptium/jdk-8.0.504.1-hotspot' >> "$USERPROFILE/.gradle/gradle.properties"
cat "$USERPROFILE/.gradle/gradle.properties"
```

- [ ] **Step 11: Run the first build (downloads Minecraft, Forge, mappings; several minutes)**

```bash
cd /c/Users/alexa/projects/packs
JAVA_HOME="/c/Program Files/Java/jdk-21.0.12.1" ./gradlew build --no-daemon --console=plain 2>&1 | tail -40
```
Use a 600000 ms timeout; if it is still running, run it in the background and poll the output file. Expected: `BUILD SUCCESSFUL`. If Gradle complains it cannot run on this JVM, retry with `JAVA_HOME` pointed at a JDK 17 provisioned by foojay (`./gradlew -Porg.gradle.java.installations.auto-download=true ...`). If `runClient` configuration fails with "Cannot set both executable and javaLauncher", replace the `javaLauncher.set(...)` line with `executable("C:/Program Files/Eclipse Adoptium/jdk-8.0.504.1-hotspot/bin/java.exe")`.

- [ ] **Step 12: Verify the jar**

```bash
ls -la build/libs/
unzip -p build/libs/fastpacks-0.1.0.jar META-INF/MANIFEST.MF
unzip -l build/libs/fastpacks-0.1.0.jar | grep -E 'mixins.fastpacks.json|mcmod.info|org/spongepowered/asm/launch/MixinTweaker.class|fastpacks/FastPacks.class'
unzip -p build/libs/fastpacks-0.1.0.jar mcmod.info | grep -E '"version"|"modid"'
```
Expected: manifest has `TweakClass: org.spongepowered.asm.launch.MixinTweaker` and `MixinConfigs: mixins.fastpacks.json`; all four listed entries exist; mcmod.info shows `"version": "0.1.0"` and `"modid": "fastpacks"`.

- [ ] **Step 13: Commit**

```bash
git add .gitignore build.gradle.kts settings.gradle.kts gradle.properties log4j2.xml gradlew gradlew.bat gradle/ src/
git update-index --chmod=+x gradlew
git commit -m "build: Forge 1.8.9 + Mixin project scaffold for fastpacks

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_016nrBSZRraXWQ2XcP2qa9AW"
```

---

### Task 2: `PackKey` (vanilla-format identity string)

**Files:**
- Create: `src/main/java/io/github/as9pa/fastpacks/PackKey.java`
- Test: `src/test/java/io/github/as9pa/fastpacks/PackKeyTest.java`

**Interfaces:**
- Produces: `public static String PackKey.compute(java.io.File file)` returning `name:zip:lastModified` for files and `name:folder:lastModified` for directories, byte-identical to vanilla `ResourcePackRepository.Entry.toString()`.

- [ ] **Step 1: Write the failing test**

```java
package io.github.as9pa.fastpacks;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class PackKeyTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void zipFileUsesZipKind() throws IOException {
        File f = tmp.newFile("Cool Pack §b16x.zip");
        assertEquals("Cool Pack §b16x.zip:zip:" + f.lastModified(), PackKey.compute(f));
    }

    @Test
    public void directoryUsesFolderKind() throws IOException {
        File d = tmp.newFolder("Vaes Pack Folder!");
        assertEquals("Vaes Pack Folder!:folder:" + d.lastModified(), PackKey.compute(d));
    }

    @Test
    public void matchesVanillaFormatExpression() throws IOException {
        File f = tmp.newFile("a.zip");
        String vanilla = String.format("%s:%s:%d", f.getName(), f.isDirectory() ? "folder" : "zip", f.lastModified());
        assertEquals(vanilla, PackKey.compute(f));
    }

    @Test
    public void missingFileStillProducesAKey() {
        File f = new File(tmp.getRoot(), "does-not-exist.zip");
        assertEquals("does-not-exist.zip:zip:0", PackKey.compute(f));
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

```bash
JAVA_HOME="/c/Program Files/Java/jdk-21.0.12.1" ./gradlew test --tests 'io.github.as9pa.fastpacks.PackKeyTest' --console=plain 2>&1 | tail -20
```
Expected: compilation error, `PackKey` does not exist.

- [ ] **Step 3: Implement**

```java
package io.github.as9pa.fastpacks;

import java.io.File;

/**
 * Builds the same identity string vanilla {@code ResourcePackRepository.Entry.toString()} builds,
 * so cached keys compare exactly like vanilla's uncached ones.
 */
public final class PackKey {
    private PackKey() {}

    public static String compute(File file) {
        return String.format("%s:%s:%d", file.getName(), file.isDirectory() ? "folder" : "zip", file.lastModified());
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Same command as step 2. Expected: `BUILD SUCCESSFUL`, 4 tests passed.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/io/github/as9pa/fastpacks/PackKey.java src/test/java/io/github/as9pa/fastpacks/PackKeyTest.java
git commit -m "feat: PackKey computes vanilla-format pack identity

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_016nrBSZRraXWQ2XcP2qa9AW"
```

---

### Task 3: `ListCulling` (off-screen predicate)

**Files:**
- Create: `src/main/java/io/github/as9pa/fastpacks/ListCulling.java`
- Test: `src/test/java/io/github/as9pa/fastpacks/ListCullingTest.java`

**Interfaces:**
- Produces: `public static boolean ListCulling.isOffscreen(int y, int height, int top, int bottom)`; true iff `y > bottom || y + height < top`.

- [ ] **Step 1: Write the failing test**

```java
package io.github.as9pa.fastpacks;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ListCullingTest {
    private static final int TOP = 32;
    private static final int BOTTOM = 200;
    private static final int H = 32;

    @Test public void fullyAboveIsOffscreen()        { assertTrue(ListCulling.isOffscreen(-40, H, TOP, BOTTOM)); }
    @Test public void justAboveByOnePixelIsOffscreen(){ assertTrue(ListCulling.isOffscreen(-1, H, TOP, BOTTOM)); }
    @Test public void touchingTopIsVisible()         { assertFalse(ListCulling.isOffscreen(0, H, TOP, BOTTOM)); }
    @Test public void straddlingTopIsVisible()       { assertFalse(ListCulling.isOffscreen(20, H, TOP, BOTTOM)); }
    @Test public void middleIsVisible()              { assertFalse(ListCulling.isOffscreen(100, H, TOP, BOTTOM)); }
    @Test public void straddlingBottomIsVisible()    { assertFalse(ListCulling.isOffscreen(190, H, TOP, BOTTOM)); }
    @Test public void touchingBottomIsVisible()      { assertFalse(ListCulling.isOffscreen(200, H, TOP, BOTTOM)); }
    @Test public void justBelowIsOffscreen()         { assertTrue(ListCulling.isOffscreen(201, H, TOP, BOTTOM)); }
    @Test public void farBelowIsOffscreen()          { assertTrue(ListCulling.isOffscreen(5000, H, TOP, BOTTOM)); }
}
```

- [ ] **Step 2: Run it to verify it fails**

```bash
JAVA_HOME="/c/Program Files/Java/jdk-21.0.12.1" ./gradlew test --tests 'io.github.as9pa.fastpacks.ListCullingTest' --console=plain 2>&1 | tail -20
```
Expected: compilation error, `ListCulling` does not exist.

- [ ] **Step 3: Implement**

```java
package io.github.as9pa.fastpacks;

/** Off-screen test for list rows. Mirrors vanilla GuiSlot's own check so partially visible rows still draw. */
public final class ListCulling {
    private ListCulling() {}

    public static boolean isOffscreen(int y, int height, int top, int bottom) {
        return y > bottom || y + height < top;
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Same command. Expected: 9 tests passed.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/io/github/as9pa/fastpacks/ListCulling.java src/test/java/io/github/as9pa/fastpacks/ListCullingTest.java
git commit -m "feat: ListCulling off-screen predicate

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_016nrBSZRraXWQ2XcP2qa9AW"
```

---

### Task 4: `IconImages` (decode + downscale)

**Files:**
- Create: `src/main/java/io/github/as9pa/fastpacks/icon/IconImages.java`
- Test: `src/test/java/io/github/as9pa/fastpacks/icon/IconImagesTest.java`

**Interfaces:**
- Produces: `public static final int IconImages.MAX_ICON_SIZE = 128`; `public static BufferedImage IconImages.decode(InputStream in)` (closes the stream; returns `null` for unreadable data, never throws); `public static BufferedImage IconImages.downscale(BufferedImage img, int max)` (returns the same instance when already within bounds; otherwise a new `TYPE_INT_ARGB` image with the longest side `== max`, other side `side * max / longest` using integer division, minimum 1).

- [ ] **Step 1: Write the failing test**

```java
package io.github.as9pa.fastpacks.icon;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import javax.imageio.ImageIO;
import org.junit.Test;

public class IconImagesTest {

    private static byte[] png(int w, int h) throws IOException {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        img.setRGB(0, 0, 0xFFFF0000);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    @Test
    public void decodesSmallPngUnchanged() throws IOException {
        BufferedImage img = IconImages.decode(new ByteArrayInputStream(png(16, 16)));
        assertNotNull(img);
        assertEquals(16, img.getWidth());
        assertEquals(16, img.getHeight());
    }

    @Test
    public void decodeReturnsNullForGarbage() {
        assertNull(IconImages.decode(new ByteArrayInputStream("definitely not a png".getBytes())));
    }

    @Test
    public void decodeReturnsNullForTruncatedPng() throws IOException {
        byte[] whole = png(64, 64);
        byte[] cut = Arrays.copyOf(whole, whole.length / 3);
        assertNull(IconImages.decode(new ByteArrayInputStream(cut)));
    }

    @Test
    public void downscaleKeepsSmallImageInstance() {
        BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB);
        assertSame(img, IconImages.downscale(img, 128));
    }

    @Test
    public void downscaleKeepsExactlyMaxSizeInstance() {
        BufferedImage img = new BufferedImage(128, 128, BufferedImage.TYPE_INT_RGB);
        assertSame(img, IconImages.downscale(img, 128));
    }

    @Test
    public void downscalesWideImagePreservingAspect() {
        BufferedImage out = IconImages.downscale(new BufferedImage(512, 256, BufferedImage.TYPE_INT_RGB), 128);
        assertEquals(128, out.getWidth());
        assertEquals(64, out.getHeight());
        assertEquals(BufferedImage.TYPE_INT_ARGB, out.getType());
    }

    @Test
    public void downscalesTallImagePreservingAspect() {
        BufferedImage out = IconImages.downscale(new BufferedImage(100, 300, BufferedImage.TYPE_INT_RGB), 128);
        assertEquals(42, out.getWidth());
        assertEquals(128, out.getHeight());
    }

    @Test
    public void downscaleNeverProducesZeroSide() {
        BufferedImage out = IconImages.downscale(new BufferedImage(1, 4096, BufferedImage.TYPE_INT_RGB), 128);
        assertEquals(1, out.getWidth());
        assertEquals(128, out.getHeight());
    }

    @Test
    public void maxIconSizeIs128() {
        assertEquals(128, IconImages.MAX_ICON_SIZE);
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

```bash
JAVA_HOME="/c/Program Files/Java/jdk-21.0.12.1" ./gradlew test --tests 'io.github.as9pa.fastpacks.icon.IconImagesTest' --console=plain 2>&1 | tail -20
```
Expected: compilation error, `IconImages` does not exist.

- [ ] **Step 3: Implement**

```java
package io.github.as9pa.fastpacks.icon;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import javax.imageio.ImageIO;

/** Pure image helpers. No Minecraft imports; safe on any thread. */
public final class IconImages {
    /** Longest side an icon may have after loading. 32 GUI px at GUI scale 4 is 128 device px. */
    public static final int MAX_ICON_SIZE = 128;

    private IconImages() {}

    /** Decodes an image from the stream and closes it. Returns null when the data is not a readable image. */
    public static BufferedImage decode(InputStream in) {
        try {
            return ImageIO.read(in);
        } catch (IOException | RuntimeException e) {
            return null;
        } finally {
            try {
                in.close();
            } catch (IOException ignored) {
                // nothing useful to do
            }
        }
    }

    /** Scales so the longest side equals {@code max}. Returns the same instance if already within bounds. */
    public static BufferedImage downscale(BufferedImage img, int max) {
        int w = img.getWidth();
        int h = img.getHeight();
        if (w <= max && h <= max) {
            return img;
        }
        int longest = Math.max(w, h);
        int nw = Math.max(1, w * max / longest);
        int nh = Math.max(1, h * max / longest);
        BufferedImage out = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(img, 0, 0, nw, nh, null);
        } finally {
            g.dispose();
        }
        return out;
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Same command. Expected: 9 tests passed.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/io/github/as9pa/fastpacks/icon/IconImages.java src/test/java/io/github/as9pa/fastpacks/icon/IconImagesTest.java
git commit -m "feat: IconImages decode and aspect-preserving downscale

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_016nrBSZRraXWQ2XcP2qa9AW"
```

---

### Task 5: `IconLoader` (background executor + placeholder)

**Files:**
- Create: `src/main/java/io/github/as9pa/fastpacks/icon/IconSink.java`
- Create: `src/main/java/io/github/as9pa/fastpacks/icon/IconLoader.java`
- Test: `src/test/java/io/github/as9pa/fastpacks/icon/IconLoaderTest.java`

**Interfaces:**
- Consumes: `IconImages.decode`, `IconImages.downscale`, `IconImages.MAX_ICON_SIZE` (Task 4); `Log.LOG` (Task 1).
- Produces:
  - `public interface IconSink { void acceptIcon(BufferedImage image); }`
  - `public static BufferedImage IconLoader.placeholder()` never null, cached.
  - `public static void IconLoader.submit(File packFile, IconSink sink)`; sink is called on a background thread only when a decoded image exists.
  - `static BufferedImage IconLoader.load(File packFile)` (package-private, synchronous; null on any failure).
  - `static int IconLoader.threadCount(int cores)` returns `clamp(cores - 1, 1, 4)`.

- [ ] **Step 1: Write the failing test**

```java
package io.github.as9pa.fastpacks.icon;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.imageio.ImageIO;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class IconLoaderTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    private static void writePng(File target, int w, int h) throws IOException {
        ImageIO.write(new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB), "png", target);
    }

    private File zipWithIcon(String name, int w, int h) throws IOException {
        File png = tmp.newFile(name + "-icon.png");
        writePng(png, w, h);
        File zip = new File(tmp.getRoot(), name + ".zip");
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(zip))) {
            out.putNextEntry(new ZipEntry("pack.mcmeta"));
            out.write("{\"pack\":{\"pack_format\":1,\"description\":\"t\"}}".getBytes("UTF-8"));
            out.closeEntry();
            out.putNextEntry(new ZipEntry("pack.png"));
            java.nio.file.Files.copy(png.toPath(), out);
            out.closeEntry();
        }
        return zip;
    }

    @Test
    public void loadsIconFromZip() throws IOException {
        BufferedImage img = IconLoader.load(zipWithIcon("small", 64, 64));
        assertNotNull(img);
        assertEquals(64, img.getWidth());
    }

    @Test
    public void loadDownscalesLargeIcon() throws IOException {
        BufferedImage img = IconLoader.load(zipWithIcon("big", 1024, 1024));
        assertNotNull(img);
        assertEquals(128, img.getWidth());
        assertEquals(128, img.getHeight());
    }

    @Test
    public void loadsIconFromFolderPack() throws IOException {
        File folder = tmp.newFolder("FolderPack");
        writePng(new File(folder, "pack.png"), 32, 32);
        BufferedImage img = IconLoader.load(folder);
        assertNotNull(img);
        assertEquals(32, img.getWidth());
    }

    @Test
    public void zipWithoutIconGivesNull() throws IOException {
        File zip = new File(tmp.getRoot(), "noicon.zip");
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(zip))) {
            out.putNextEntry(new ZipEntry("pack.mcmeta"));
            out.write("{}".getBytes("UTF-8"));
            out.closeEntry();
        }
        assertNull(IconLoader.load(zip));
    }

    @Test
    public void missingFileGivesNull() {
        assertNull(IconLoader.load(new File(tmp.getRoot(), "gone.zip")));
    }

    @Test
    public void corruptZipGivesNull() throws IOException {
        File bad = tmp.newFile("bad.zip");
        try (FileOutputStream out = new FileOutputStream(bad)) {
            out.write("this is not a zip".getBytes("UTF-8"));
        }
        assertNull(IconLoader.load(bad));
    }

    @Test
    public void submitDeliversImageOnBackgroundThread() throws Exception {
        File zip = zipWithIcon("async", 16, 16);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<BufferedImage> got = new AtomicReference<>();
        AtomicReference<String> thread = new AtomicReference<>();
        IconLoader.submit(zip, image -> {
            got.set(image);
            thread.set(Thread.currentThread().getName());
            latch.countDown();
        });
        assertTrue("icon not delivered within 10 s", latch.await(10, TimeUnit.SECONDS));
        assertEquals(16, got.get().getWidth());
        assertTrue(thread.get(), thread.get().startsWith("fastpacks-icon-"));
    }

    @Test
    public void submitDoesNotCallSinkWhenThereIsNoIcon() throws Exception {
        File zip = new File(tmp.getRoot(), "none.zip");
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(zip))) {
            out.putNextEntry(new ZipEntry("pack.mcmeta"));
            out.write("{}".getBytes("UTF-8"));
            out.closeEntry();
        }
        CountDownLatch latch = new CountDownLatch(1);
        IconLoader.submit(zip, image -> latch.countDown());
        assertTrue("sink must not be called", !latch.await(1, TimeUnit.SECONDS));
    }

    @Test
    public void placeholderIsCachedAndNeverNull() {
        BufferedImage a = IconLoader.placeholder();
        assertNotNull(a);
        assertSame(a, IconLoader.placeholder());
        assertTrue(a.getWidth() >= 16);
    }

    @Test
    public void threadCountIsClamped() {
        assertEquals(1, IconLoader.threadCount(1));
        assertEquals(1, IconLoader.threadCount(2));
        assertEquals(3, IconLoader.threadCount(4));
        assertEquals(4, IconLoader.threadCount(8));
        assertEquals(4, IconLoader.threadCount(32));
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

```bash
JAVA_HOME="/c/Program Files/Java/jdk-21.0.12.1" ./gradlew test --tests 'io.github.as9pa.fastpacks.icon.IconLoaderTest' --console=plain 2>&1 | tail -20
```
Expected: compilation error, `IconLoader`/`IconSink` do not exist.

- [ ] **Step 3: Implement `IconSink`**

```java
package io.github.as9pa.fastpacks.icon;

import java.awt.image.BufferedImage;

/** Receives a decoded (and possibly downscaled) icon. Called on a background thread. */
public interface IconSink {
    void acceptIcon(BufferedImage image);
}
```

- [ ] **Step 4: Implement `IconLoader`**

```java
package io.github.as9pa.fastpacks.icon;

import io.github.as9pa.fastpacks.Log;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Decodes pack icons off the client thread.
 *
 * <p>Must not reference Minecraft, Forge, or GL: the resource pack repository (and therefore the
 * first {@link #submit}) is constructed during {@code Minecraft.startGame}, before any mod is initialised.
 */
public final class IconLoader {
    private static final Object LOCK = new Object();
    private static ExecutorService pool;
    private static BufferedImage placeholder;

    private IconLoader() {}

    /** clamp(cores - 1, 1, 4). */
    static int threadCount(int cores) {
        return Math.max(1, Math.min(4, cores - 1));
    }

    /** The image every entry shows until its own icon arrives. Vanilla's default pack.png if available. */
    public static BufferedImage placeholder() {
        synchronized (LOCK) {
            if (placeholder == null) {
                BufferedImage img = null;
                InputStream in = IconLoader.class.getResourceAsStream("/pack.png");
                if (in != null) {
                    img = IconImages.decode(in);
                }
                if (img == null) {
                    img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
                    Graphics2D g = img.createGraphics();
                    try {
                        g.setColor(new Color(0x40, 0x40, 0x40));
                        g.fillRect(0, 0, 32, 32);
                    } finally {
                        g.dispose();
                    }
                }
                placeholder = img;
            }
            return placeholder;
        }
    }

    /** Queues a background load; the sink is invoked only when an icon was decoded. */
    public static void submit(final File packFile, final IconSink sink) {
        pool().execute(() -> {
            BufferedImage img = load(packFile);
            if (img != null) {
                sink.acceptIcon(img);
            }
        });
    }

    /** Synchronous load + downscale. Null on any failure (missing, corrupt, not an image). */
    static BufferedImage load(File packFile) {
        try {
            BufferedImage img;
            if (packFile.isDirectory()) {
                File png = new File(packFile, "pack.png");
                if (!png.isFile()) {
                    return null;
                }
                img = IconImages.decode(new FileInputStream(png));
            } else {
                try (ZipFile zip = new ZipFile(packFile)) {
                    ZipEntry entry = zip.getEntry("pack.png");
                    if (entry == null) {
                        return null;
                    }
                    img = IconImages.decode(zip.getInputStream(entry));
                }
            }
            return img == null ? null : IconImages.downscale(img, IconImages.MAX_ICON_SIZE);
        } catch (IOException | RuntimeException e) {
            Log.LOG.debug("fastpacks: could not load icon for {}: {}", packFile.getName(), e.toString());
            return null;
        }
    }

    private static ExecutorService pool() {
        synchronized (LOCK) {
            if (pool == null) {
                int threads = threadCount(Runtime.getRuntime().availableProcessors());
                pool = Executors.newFixedThreadPool(threads, new ThreadFactory() {
                    private final AtomicInteger n = new AtomicInteger();

                    @Override
                    public Thread newThread(Runnable r) {
                        Thread t = new Thread(r, "fastpacks-icon-" + n.incrementAndGet());
                        t.setDaemon(true);
                        t.setPriority(Thread.MIN_PRIORITY);
                        return t;
                    }
                });
            }
            return pool;
        }
    }
}
```

- [ ] **Step 5: Run the test to verify it passes**

Same command as step 2. Expected: 10 tests passed. If `placeholderIsCachedAndNeverNull` reports a 32 px image rather than 128 px, that only means the Minecraft jar is not on the test runtime classpath; the test still passes and the fallback path is exercised.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/io/github/as9pa/fastpacks/icon/ src/test/java/io/github/as9pa/fastpacks/icon/IconLoaderTest.java
git commit -m "feat: IconLoader background icon decoding with placeholder

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_016nrBSZRraXWQ2XcP2qa9AW"
```

---

### Task 6: Identity mixins + config plugin (the 6-second fix)

**Files:**
- Create: `src/main/java/io/github/as9pa/fastpacks/mixin/EntryExtension.java`
- Create: `src/main/java/io/github/as9pa/fastpacks/mixin/MixinResourcePackRepositoryEntry.java`
- Create: `src/main/java/io/github/as9pa/fastpacks/mixin/MixinResourcePackRepository.java`
- Create: `src/main/java/io/github/as9pa/fastpacks/mixin/FastPacksMixinPlugin.java`
- Modify: `src/main/resources/mixins.fastpacks.json`

**Interfaces:**
- Consumes: `PackKey.compute(File)` (Task 2); `IconSink` (Task 5); `Log.LOG` (Task 1).
- Produces: `EntryExtension extends IconSink { void fastpacks$refreshKey(); }` implemented by every `ResourcePackRepository.Entry` at runtime. Task 7 adds icon fields/methods to `MixinResourcePackRepositoryEntry`.

There is no unit test for mixins; verification is compile + refmap content here, and the dev-client run in Task 10.

- [ ] **Step 1: Write `EntryExtension`**

```java
package io.github.as9pa.fastpacks.mixin;

import io.github.as9pa.fastpacks.icon.IconSink;

/** Implemented by {@code ResourcePackRepository.Entry} via mixin. */
public interface EntryExtension extends IconSink {
    /** Recompute the cached identity key from the file's current state. */
    void fastpacks$refreshKey();
}
```

- [ ] **Step 2: Write `MixinResourcePackRepositoryEntry` (identity part; Task 7 extends it)**

```java
package io.github.as9pa.fastpacks.mixin;

import io.github.as9pa.fastpacks.PackKey;
import java.awt.image.BufferedImage;
import java.io.File;
import net.minecraft.client.resources.ResourcePackRepository;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Vanilla Entry.toString() stats the file twice on every call, and equals()/hashCode() call toString().
 * updateRepositoryEntriesAll() compares entries O(N^2) times, so with hundreds of packs that is hundreds of
 * thousands of filesystem calls per screen open. We cache the string; the repository mixin refreshes it once per scan.
 */
@Mixin(ResourcePackRepository.Entry.class)
public abstract class MixinResourcePackRepositoryEntry implements EntryExtension {

    @Shadow @Final private File resourcePackFile;

    @Unique private String fastpacks$key;
    @Unique private volatile BufferedImage fastpacks$pendingIcon;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void fastpacks$onConstructed(CallbackInfo ci) {
        fastpacks$refreshKey();
    }

    @Override
    public void fastpacks$refreshKey() {
        fastpacks$key = PackKey.compute(resourcePackFile);
    }

    @Override
    public void acceptIcon(BufferedImage image) {
        fastpacks$pendingIcon = image;
    }

    @Inject(method = "toString", at = @At("HEAD"), cancellable = true)
    private void fastpacks$cachedToString(CallbackInfoReturnable<String> cir) {
        if (fastpacks$key == null) {
            fastpacks$refreshKey();
        }
        cir.setReturnValue(fastpacks$key);
    }
}
```

- [ ] **Step 3: Write `MixinResourcePackRepository`**

```java
package io.github.as9pa.fastpacks.mixin;

import io.github.as9pa.fastpacks.Log;
import java.util.List;
import net.minecraft.client.resources.ResourcePackRepository;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Refreshes every cached entry key once per scan (keeps vanilla semantics) and logs the scan time. */
@Mixin(ResourcePackRepository.class)
public abstract class MixinResourcePackRepository {

    @Shadow private List<ResourcePackRepository.Entry> repositoryEntriesAll;

    @Unique private long fastpacks$scanStartNanos;

    @Inject(method = "updateRepositoryEntriesAll", at = @At("HEAD"))
    private void fastpacks$beforeScan(CallbackInfo ci) {
        fastpacks$scanStartNanos = System.nanoTime();
        for (Object entry : repositoryEntriesAll) {
            if (entry instanceof EntryExtension) {
                ((EntryExtension) entry).fastpacks$refreshKey();
            }
        }
    }

    @Inject(method = "updateRepositoryEntriesAll", at = @At("RETURN"))
    private void fastpacks$afterScan(CallbackInfo ci) {
        long ms = (System.nanoTime() - fastpacks$scanStartNanos) / 1_000_000L;
        Log.LOG.info("fastpacks: rescanned {} packs in {} ms", repositoryEntriesAll.size(), ms);
    }
}
```

- [ ] **Step 4: Write `FastPacksMixinPlugin`**

```java
package io.github.as9pa.fastpacks.mixin;

import io.github.as9pa.fastpacks.Log;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.spongepowered.asm.lib.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Dev switch: -Dfastpacks.baseline=true keeps the timing mixin but disables the optimisations,
 * so before/after can be measured in the same environment. Must not reference Minecraft classes.
 */
public class FastPacksMixinPlugin implements IMixinConfigPlugin {
    private static final boolean BASELINE = Boolean.getBoolean("fastpacks.baseline");
    private static final Set<String> OPTIMISATIONS = new HashSet<>(Arrays.asList(
            "io.github.as9pa.fastpacks.mixin.MixinResourcePackRepositoryEntry",
            "io.github.as9pa.fastpacks.mixin.MixinGuiListExtended"));

    @Override
    public void onLoad(String mixinPackage) {
        if (BASELINE) {
            Log.LOG.warn("fastpacks: BASELINE mode - optimisations disabled, timing only");
        }
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return !(BASELINE && OPTIMISATIONS.contains(mixinClassName));
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
```

- [ ] **Step 5: Register the mixins and the plugin in `mixins.fastpacks.json`**

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
    "MixinResourcePackRepository"
  ],
  "injectors": { "defaultRequire": 1 }
}
```

- [ ] **Step 6: Build and inspect the refmap**

```bash
JAVA_HOME="/c/Program Files/Java/jdk-21.0.12.1" ./gradlew build --console=plain 2>&1 | grep -E 'warning|error|BUILD' | head -20
unzip -p build/libs/fastpacks-0.1.0.jar mixins.fastpacks.refmap.json
```
Expected: `BUILD SUCCESSFUL`; the refmap JSON has entries for both mixin classes mapping `resourcePackFile`, `repositoryEntriesAll`, `updateRepositoryEntriesAll` to `field_…`/`func_110611_a` names, and `toString`/`<init>` unmapped. Any `Unable to locate obfuscation mapping` warning means a member name is wrong: recheck against the names listed at the top of this plan.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/io/github/as9pa/fastpacks/mixin/ src/main/resources/mixins.fastpacks.json
git commit -m "feat: cache pack identity keys; log scan time (fixes O(n^2) stat storm)

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_016nrBSZRraXWQ2XcP2qa9AW"
```

---

### Task 7: Deferred icon loading in the Entry mixin

**Files:**
- Modify: `src/main/java/io/github/as9pa/fastpacks/mixin/MixinResourcePackRepositoryEntry.java`

**Interfaces:**
- Consumes: `IconLoader.placeholder()`, `IconLoader.submit(File, IconSink)` (Task 5); the `fastpacks$pendingIcon` field and `acceptIcon` from Task 6.
- Produces: nothing new for later tasks.

- [ ] **Step 1: Add the shadows, the redirect and the bind hook**

Add these imports:

```java
import io.github.as9pa.fastpacks.icon.IconLoader;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.resources.DefaultResourcePack;
import net.minecraft.client.resources.IResourcePack;
import net.minecraft.util.ResourceLocation;
import org.spongepowered.asm.mixin.injection.Redirect;
```

Add these members to the class body (after `fastpacks$pendingIcon`):

```java
    @Shadow private BufferedImage texturePackIcon;
    @Shadow private ResourceLocation locationTexturePackIcon;

    /**
     * Vanilla decodes pack.png synchronously here (for every pack, at startup). Both getPackImage() call sites in
     * updateResourcePack() hit this redirect: the pack's own icon and the default-pack fallback. We hand back the
     * shared placeholder at once and decode the real icon in the background.
     */
    @Redirect(
            method = "updateResourcePack",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/resources/IResourcePack;getPackImage()Ljava/awt/image/BufferedImage;"))
    private BufferedImage fastpacks$deferIcon(IResourcePack pack) {
        if (!(pack instanceof DefaultResourcePack)) {
            IconLoader.submit(resourcePackFile, this);
        }
        return IconLoader.placeholder();
    }

    /** Client thread: swap a finished background icon in before vanilla binds (and lazily uploads) the texture. */
    @Inject(method = "bindTexturePackIcon", at = @At("HEAD"))
    private void fastpacks$swapInLoadedIcon(TextureManager textureManager, CallbackInfo ci) {
        BufferedImage ready = fastpacks$pendingIcon;
        if (ready == null) {
            return;
        }
        fastpacks$pendingIcon = null;
        texturePackIcon = ready;
        if (locationTexturePackIcon != null) {
            textureManager.deleteTexture(locationTexturePackIcon);
            locationTexturePackIcon = null;
        }
    }
```

- [ ] **Step 2: Build and inspect the refmap**

```bash
JAVA_HOME="/c/Program Files/Java/jdk-21.0.12.1" ./gradlew build --console=plain 2>&1 | grep -E 'warning|error|BUILD' | head -20
unzip -p build/libs/fastpacks-0.1.0.jar mixins.fastpacks.refmap.json | tr ',' '\n' | grep -E 'getPackImage|texturePackIcon|locationTexturePackIcon|bindTexturePackIcon|updateResourcePack'
```
Expected: `BUILD SUCCESSFUL`; the grep shows mapped entries for all five names (`getPackImage` maps to a `func_…` descriptor on `IResourcePack`).

- [ ] **Step 3: Commit**

```bash
git add src/main/java/io/github/as9pa/fastpacks/mixin/MixinResourcePackRepositoryEntry.java
git commit -m "feat: decode pack icons in the background, placeholder first

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_016nrBSZRraXWQ2XcP2qa9AW"
```

---

### Task 8: Off-screen culling mixin

**Files:**
- Create: `src/main/java/io/github/as9pa/fastpacks/mixin/MixinGuiListExtended.java`
- Modify: `src/main/resources/mixins.fastpacks.json`

**Interfaces:**
- Consumes: `ListCulling.isOffscreen(int, int, int, int)` (Task 3).
- Produces: nothing new for later tasks.

- [ ] **Step 1: Write the mixin**

```java
package io.github.as9pa.fastpacks.mixin;

import io.github.as9pa.fastpacks.ListCulling;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiListExtended;
import net.minecraft.client.gui.GuiResourcePackList;
import net.minecraft.client.gui.GuiSlot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Vanilla GuiSlot.drawSelectionBox() calls drawSlot() for every row every frame, on-screen or not.
 * Skip rows entirely outside the list area, but only for the two resource pack lists so other
 * mods' lists keep vanilla behaviour. OptiFine already does this; with it present this is a no-op.
 */
@Mixin(GuiListExtended.class)
public abstract class MixinGuiListExtended extends GuiSlot {

    private MixinGuiListExtended(Minecraft mc, int width, int height, int top, int bottom, int slotHeight) {
        super(mc, width, height, top, bottom, slotHeight);
    }

    @Inject(method = "drawSlot", at = @At("HEAD"), cancellable = true)
    private void fastpacks$cullOffscreenRows(int entryId, int x, int y, int height, int mouseX, int mouseY, CallbackInfo ci) {
        if (this instanceof GuiResourcePackList && ListCulling.isOffscreen(y, height, this.top, this.bottom)) {
            ci.cancel();
        }
    }
}
```

- [ ] **Step 2: Register it**

In `mixins.fastpacks.json`, change the `client` array to:

```json
  "client": [
    "MixinResourcePackRepositoryEntry",
    "MixinResourcePackRepository",
    "MixinGuiListExtended"
  ],
```

- [ ] **Step 3: Build and inspect**

```bash
JAVA_HOME="/c/Program Files/Java/jdk-21.0.12.1" ./gradlew build --console=plain 2>&1 | grep -E 'warning|error|BUILD' | head -20
unzip -p build/libs/fastpacks-0.1.0.jar mixins.fastpacks.refmap.json | tr ',' '\n' | grep -E 'drawSlot'
unzip -p build/libs/fastpacks-0.1.0.jar mixins.fastpacks.json
```
Expected: `BUILD SUCCESSFUL`; `drawSlot` maps to `func_180791_a` (the 6-int `GuiListExtended.drawSlot`); the shipped config lists three mixins.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/io/github/as9pa/fastpacks/mixin/MixinGuiListExtended.java src/main/resources/mixins.fastpacks.json
git commit -m "feat: skip drawing off-screen rows in the resource pack lists

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_016nrBSZRraXWQ2XcP2qa9AW"
```

---

### Task 9: Dev harness (auto-open + frame timing) and mod wiring

**Files:**
- Create: `src/main/java/io/github/as9pa/fastpacks/dev/DevHarness.java`
- Modify: `src/main/java/io/github/as9pa/fastpacks/FastPacks.java`

**Interfaces:**
- Consumes: `Log.LOG`.
- Produces: with `-Dfastpacks.devOpenPacksGui=true` the client opens the Resource Packs screen at the main menu, logs `fastpacks dev: {} packs, avg frame {} ms over {} frames` after 120 frames, then shuts down.

- [ ] **Step 1: Write `DevHarness`**

```java
package io.github.as9pa.fastpacks.dev;

import io.github.as9pa.fastpacks.Log;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiScreenResourcePacks;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Dev-only. Opens the Resource Packs screen as soon as the main menu appears, measures whole-frame
 * render time (RenderTickEvent START to END, which excludes the frame-rate cap wait) while that screen
 * is showing, logs the average after FRAMES frames and exits the game.
 */
public class DevHarness {
    private static final int FRAMES = 120;

    private boolean opened;
    private long frameStartNanos;
    private long totalNanos;
    private int frames;
    private boolean reported;

    @SubscribeEvent
    public void onGuiOpen(GuiOpenEvent event) {
        if (opened || !(event.gui instanceof GuiMainMenu)) {
            return;
        }
        opened = true;
        final GuiScreen menu = event.gui;
        final Minecraft mc = Minecraft.getMinecraft();
        // Let the main menu finish showing, then replace it on the next tick.
        mc.addScheduledTask(() -> {
            Log.LOG.info("fastpacks dev: opening GuiScreenResourcePacks");
            mc.displayGuiScreen(new GuiScreenResourcePacks(menu));
        });
    }

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (reported || !(mc.currentScreen instanceof GuiScreenResourcePacks)) {
            return;
        }
        if (event.phase == TickEvent.Phase.START) {
            frameStartNanos = System.nanoTime();
            return;
        }
        if (frameStartNanos == 0L) {
            return;
        }
        totalNanos += System.nanoTime() - frameStartNanos;
        frames++;
        if (frames >= FRAMES) {
            reported = true;
            int packs = mc.getResourcePackRepository().getRepositoryEntriesAll().size();
            Log.LOG.info("fastpacks dev: {} packs, avg frame {} ms over {} frames",
                    packs, String.format("%.2f", totalNanos / 1_000_000.0 / frames), frames);
            mc.shutdown();
        }
    }
}
```

- [ ] **Step 2: Wire it in `FastPacks`**

Replace the class body with:

```java
package io.github.as9pa.fastpacks;

import io.github.as9pa.fastpacks.dev.DevHarness;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;

@Mod(modid = FastPacks.MODID, useMetadata = true, clientSideOnly = true, acceptedMinecraftVersions = "[1.8.9]")
public class FastPacks {
    public static final String MODID = "fastpacks";

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        Log.LOG.info("fastpacks initialised");
        if (Boolean.getBoolean("fastpacks.devOpenPacksGui")) {
            Log.LOG.warn("fastpacks: dev harness enabled - will auto-open the Resource Packs screen and exit");
            MinecraftForge.EVENT_BUS.register(new DevHarness());
        }
    }
}
```

- [ ] **Step 3: Build**

```bash
JAVA_HOME="/c/Program Files/Java/jdk-21.0.12.1" ./gradlew build --console=plain 2>&1 | grep -E 'warning|error|BUILD' | head -20
```
Expected: `BUILD SUCCESSFUL`, all unit tests still pass.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/io/github/as9pa/fastpacks/dev/DevHarness.java src/main/java/io/github/as9pa/fastpacks/FastPacks.java
git commit -m "feat: dev harness to auto-open the packs screen and time frames

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_016nrBSZRraXWQ2XcP2qa9AW"
```

---

### Task 10: Measure before/after in the dev client on the real pack folder

**Files:**
- Create: `run/resourcepacks` (junction, git-ignored)
- Create: `docs/measurements-2026-09-05.md`

**Interfaces:**
- Consumes: everything above.
- Produces: the numbers the README (Task 11) publishes.

The dev client opens a real game window on the owner's desktop and closes itself after the measurement. Each run takes one to three minutes; the first one also decompiles/remaps Minecraft.

- [ ] **Step 1: Junction the real pack folder into the run dir**

```powershell
New-Item -ItemType Directory -Force "C:\Users\alexa\projects\packs\run" | Out-Null
if (-not (Test-Path "C:\Users\alexa\projects\packs\run\resourcepacks")) {
  New-Item -ItemType Junction -Path "C:\Users\alexa\projects\packs\run\resourcepacks" -Target "$env:APPDATA\.minecraft\resourcepacks" | Out-Null
}
(Get-ChildItem "C:\Users\alexa\projects\packs\run\resourcepacks").Count
```
Expected: 248.

- [ ] **Step 2: Baseline run (optimisations off, timing on)**

```bash
cd /c/Users/alexa/projects/packs
JAVA_HOME="/c/Program Files/Java/jdk-21.0.12.1" ./gradlew runClient -Pfastpacks.baseline=true -Pfastpacks.devOpenPacksGui=true --console=plain > build/runclient-baseline.log 2>&1
grep -E 'fastpacks|Mixing|MIXIN|BASELINE|Exception in|Caused by' build/runclient-baseline.log | head -40
```
Use a 600000 ms timeout or run in the background and wait for the process to exit. Expected lines, in order: `SpongePowered MIXIN Subsystem Version=0.7.11`, `fastpacks: BASELINE mode`, one `fastpacks: rescanned 248 packs in N ms` at startup (N in the low thousands because icons are decoded inline in baseline), `fastpacks initialised`, `fastpacks dev: opening GuiScreenResourcePacks`, a second `rescanned 248 packs in N ms` with N roughly 4000 to 8000 (the freeze), then `fastpacks dev: 248 packs, avg frame X ms over 120 frames`. If the game fails to start, read the full log and fix before going on; do not proceed with a broken baseline.

- [ ] **Step 3: Optimised run**

```bash
JAVA_HOME="/c/Program Files/Java/jdk-21.0.12.1" ./gradlew runClient -Pfastpacks.devOpenPacksGui=true --console=plain > build/runclient-optimised.log 2>&1
grep -E 'fastpacks|Mixing io|Exception in|Caused by' build/runclient-optimised.log | head -40
```
Expected: three `Mixing …` lines (Entry, ResourcePackRepository, GuiListExtended); startup `rescanned 248 packs in N ms` with N at most 200; the screen-open `rescanned 248 packs in N ms` with N at most 100; `avg frame` at or below the baseline figure; no exceptions mentioning `fastpacks`.

- [ ] **Step 4: Record the numbers**

Write `docs/measurements-2026-09-05.md` with the actual values copied from the two logs:

```markdown
# Measurements, 2026-09-05

Machine: owner's Windows 11 PC, JDK 8, 248 packs (247 zips + 1 folder, 6.5 GB) via junction.
Dev client (Forge 1.8.9, no OptiFine), `-Dfastpacks.devOpenPacksGui=true`, 120 frames sampled.

| Metric | Baseline (`-Dfastpacks.baseline=true`) | fastpacks | Ratio |
|---|---|---|---|
| Startup scan (`rescanned` line #1) | <N1> ms | <M1> ms | <N1/M1>x |
| Screen open scan (`rescanned` line #2) | <N2> ms | <M2> ms | <N2/M2>x |
| Avg frame on the packs screen | <F1> ms | <F2> ms | <F1/F2>x |

Log excerpts:
- baseline: `build/runclient-baseline.log` lines: <paste the fastpacks lines>
- optimised: `build/runclient-optimised.log` lines: <paste the fastpacks lines>
```
Replace every `<...>` with the real numbers before committing; the file must contain no angle-bracket placeholders.

- [ ] **Step 5: Commit**

```bash
git add docs/measurements-2026-09-05.md
git commit -m "docs: before/after measurements on the owner's 248-pack folder

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_016nrBSZRraXWQ2XcP2qa9AW"
```

---

### Task 11: README, release jar, install into the owner's mods folder

**Files:**
- Create: `README.md`
- Copy: `build/libs/fastpacks-0.1.0.jar` to `%APPDATA%\.minecraft\mods\fastpacks-0.1.0.jar`

**Interfaces:**
- Consumes: `docs/measurements-2026-09-05.md` numbers.

- [ ] **Step 1: Write `README.md`** (fill the table from the measurements file; no placeholders)

```markdown
# fastpacks

Forge 1.8.9 client mod. Makes the Resource Packs screen open instantly when you have hundreds of packs.

## Why the vanilla screen freezes

Every time the screen opens, vanilla re-matches each pack on disk against its cached list using a
comparison that performs two filesystem calls per compare, inside three O(N^2) list scans. With
248 packs that is about 340,000 filesystem calls per open, roughly six seconds on Windows.
Pack size has nothing to do with it.

## What fastpacks does

- Caches each pack's identity string and refreshes it once per scan: 2N filesystem calls instead of ~6N^2. Behaviour is otherwise identical to vanilla.
- Decodes pack icons on background threads, showing the default icon until each real one arrives. Icons larger than 128 px are downscaled before upload.
- Skips drawing rows that are scrolled out of view in the two pack lists (OptiFine already does this; the mod works with or without OptiFine).
- Logs `fastpacks: rescanned N packs in M ms` so you can see it working in `latest.log`.

## Measured (owner's PC, 248 packs, 6.5 GB)

| Metric | Vanilla | fastpacks |
|---|---|---|
| Screen open scan | <N2> ms | <M2> ms |
| Startup scan (icons inline vs background) | <N1> ms | <M1> ms |
| Avg frame on the packs screen (no OptiFine) | <F1> ms | <F2> ms |

Details in `docs/measurements-2026-09-05.md`.

## Install

Drop `fastpacks-<version>.jar` into `.minecraft/mods` next to Forge 1.8.9. Compatible with OptiFine 1.8.9 HD U M5 as a mod jar and with other Mixin 0.7.11 mods. Uses `@Inject`/`@Redirect` only, no `@Overwrite`.

## Build

Needs JDK 21 (to run Gradle) and JDK 8 (toolchain, auto-detected). On Windows:

    set JAVA_HOME=C:\Program Files\Java\jdk-21.0.12.1
    gradlew build

Output: `build/libs/fastpacks-<version>.jar`. Tests: `gradlew test`.

Dev switches for `gradlew runClient`: `-Pfastpacks.baseline=true` (optimisations off, timing on),
`-Pfastpacks.devOpenPacksGui=true` (auto-open the screen, log frame time, exit).

## Roadmap

v2: resolution filters (16x/32x/64x), search box, persistent per-pack cache.
```

- [ ] **Step 2: Final build and unit tests**

```bash
JAVA_HOME="/c/Program Files/Java/jdk-21.0.12.1" ./gradlew clean build --console=plain 2>&1 | grep -E 'tests completed|passed|FAILED|BUILD' | head
ls -la build/libs/fastpacks-0.1.0.jar
```
Expected: `BUILD SUCCESSFUL`, jar present.

- [ ] **Step 3: Install into the owner's mods folder**

```bash
cp build/libs/fastpacks-0.1.0.jar "$APPDATA/.minecraft/mods/fastpacks-0.1.0.jar"
ls -la "$APPDATA/.minecraft/mods/"
```
Expected: `fastpacks-0.1.0.jar` listed alongside OptiFine, Meowtils, clearchat, KeystrokesMod.

- [ ] **Step 4: Commit**

```bash
git add README.md
git commit -m "docs: README with measured results and install instructions

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>
Claude-Session: https://claude.ai/code/session_016nrBSZRraXWQ2XcP2qa9AW"
git log --oneline
```

---

## Self-review

- Spec 4.1 identity cache: Task 2 + Task 6. Spec 4.2 background icons: Tasks 4, 5, 7. Spec 4.3 culling: Tasks 3, 8. Spec 4.4 timing log: Task 6. Spec 4.5 dev harness: Tasks 6 (plugin) + 9. Spec 5 mixin config: Tasks 1, 6, 8. Spec 6 toolchain: Task 1. Spec 7 error handling: Tasks 4, 5 (null on failure), 6 (`defaultRequire`). Spec 8 testing: Tasks 2 to 5 (unit), 10 (dev client). Spec 9 acceptance: Task 10 thresholds, Task 11 install. README metric: Task 11.
- Names are consistent across tasks: `PackKey.compute`, `ListCulling.isOffscreen`, `IconImages.decode/downscale/MAX_ICON_SIZE`, `IconSink.acceptIcon`, `IconLoader.placeholder/submit/load/threadCount`, `EntryExtension.fastpacks$refreshKey`, `Log.LOG`.
- Owner's manual OptiFine verification happens after Task 11 and is outside the plan.
