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
