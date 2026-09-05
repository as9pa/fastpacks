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
