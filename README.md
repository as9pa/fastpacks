# fastpacks

**The Resource Packs screen opens 155x faster.** With a few hundred packs installed, Forge 1.8.9 froze for 21 s every time it opened; with fastpacks it opens in 0.14 s.

**[Download](https://github.com/as9pa/fastpacks/releases/latest)** (Forge 1.8.9)

## What it does

- **Fast rescans:** caches each pack's identity, so a scan costs 2N filesystem calls instead of ~6N^2.
- **Icons off the main thread:** decoded in the background, icons over 128 px are downscaled.
- **Culled rows:** rows scrolled out of view are not drawn, with or without OptiFine.
- **Safe:** vanilla behaviour is otherwise unchanged, and selected packs never disappear.

Search, filter chips and resolution labels now live in the separate [packview](https://github.com/as9pa/packview) mod (formerly packorganizer).

Measured in the real game with OptiFine and a few hundred packs:

| | Vanilla | fastpacks | Faster |
|---|---|---|---|
| Opening the Resource Packs screen | 21.2 s | 0.14 s | 155x |
| Startup scan | 1.47 s | 0.41 s | 3.5x |

More benchmarks and how it works are in [docs/details.md](docs/details.md).

## Install

Download `fastpacks-0.3.0.jar` from [Releases](https://github.com/as9pa/fastpacks/releases) and drop it into `.minecraft/mods` next to Forge 1.8.9. Works with OptiFine 1.8.9 HD U M5 as a mod jar. Remove the jar to go back to vanilla.

## Build

Needs JDK 21 (to run Gradle) and JDK 8 (toolchain, auto-detected). Point `JAVA_HOME` at a JDK 21 install, then:

    gradlew build

Output: `build/libs/fastpacks-0.3.0.jar`. Tests: `gradlew test`.

## License

MIT. See [LICENSE](LICENSE).
