# fastpacks

Resource pack load-time optimizer for Forge 1.8.9. With a few hundred packs installed, the Resource Packs screen opened in 21 s before and opens in 0.14 s now.

**[Download](https://github.com/as9pa/fastpacks/releases/latest)** (Forge 1.8.9)

## What it does

- **Fast rescans:** caches each pack's identity, so a scan costs 2N filesystem calls instead of ~6N^2.
- **Icons off the main thread:** decoded in the background, icons over 128 px are downscaled.
- **Culled rows:** rows scrolled out of view are not drawn, with or without OptiFine.
- **Search and chips:** a search box and All / 16x / 32x / 64x / 128x+ / Overlay filters above the Available list.
- **Resolution labels:** each pack shows its resolution at the right end of its row.
- **Safe:** vanilla behaviour is otherwise unchanged, and selected packs never disappear.

Benchmarks and how it works are in [docs/details.md](docs/details.md).

## Install

Download `fastpacks-0.2.0.jar` from [Releases](https://github.com/as9pa/fastpacks/releases) and drop it into `.minecraft/mods` next to Forge 1.8.9. Works with OptiFine 1.8.9 HD U M5 as a mod jar. Remove the jar to go back to vanilla.

## Build

Needs JDK 21 (to run Gradle) and JDK 8 (toolchain, auto-detected). Point `JAVA_HOME` at a JDK 21 install, then:

    gradlew build

Output: `build/libs/fastpacks-0.2.0.jar`. Tests: `gradlew test`.

## License

MIT. See [LICENSE](LICENSE).
