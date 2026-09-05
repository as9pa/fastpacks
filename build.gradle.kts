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
// Loom registers runClient in afterEvaluate, so configure it lazily rather than with tasks.named.
val devClientTasks = setOf("runClient", "runDevClient")
tasks.withType<JavaExec>().matching { it.name in devClientTasks }.configureEach {
    javaLauncher.set(javaToolchains.launcherFor(java.toolchain))
    listOf("fastpacks.baseline", "fastpacks.devOpenPacksGui").forEach { key ->
        (project.findProperty(key) as String?)?.let { systemProperty(key, it) }
    }
}

// Loom 0.10 targets Gradle 7: its AbstractRunTask overrides JavaExec.getMain(), which Gradle 8
// removed. Gradle 8.8 therefore fails runClient before it starts ("property 'main' is missing an
// input or output annotation") and would leave mainClass unset even if it did not. runDevClient
// performs the same launch from a plain JavaExec, taking every value from Loom's own run config.
tasks.register<JavaExec>("runDevClient") {
    group = "loom"
    description = "Starts the 1.8.9 dev client (Gradle 8 stand-in for Loom's runClient)."
    dependsOn("downloadAssets")
    workingDir = file("run")
    doFirst {
        workingDir.mkdirs()
    }
}

afterEvaluate {
    val loomRunClient = tasks.getByName("runClient") as net.fabricmc.loom.task.AbstractRunTask
    tasks.named<JavaExec>("runDevClient") {
        mainClass.set(loomRunClient.main)
        classpath = loomRunClient.classpath
        jvmArgs = loomRunClient.jvmArgs
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
