package io.github.as9pa.fastpacks;

import io.github.as9pa.fastpacks.icon.IconSink;

/** Implemented by {@code ResourcePackRepository.Entry} via mixin. */
public interface EntryExtension extends IconSink {
    /** Recompute the cached identity key from the file's current state. */
    void fastpacks$refreshKey();
}
