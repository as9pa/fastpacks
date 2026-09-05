package io.github.as9pa.fastpacks;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Single shared logger. Safe to touch from mixins and from the Mixin config plugin (no Minecraft classes). */
public final class Log {
    public static final Logger LOG = LogManager.getLogger("fastpacks");

    private Log() {}
}
