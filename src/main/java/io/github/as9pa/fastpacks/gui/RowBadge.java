package io.github.as9pa.fastpacks.gui;

import io.github.as9pa.fastpacks.EntryExtension;
import io.github.as9pa.fastpacks.filter.PackResolution;
import net.minecraft.client.resources.ResourcePackListEntry;
import net.minecraft.client.resources.ResourcePackListEntryDefault;
import net.minecraft.client.resources.ResourcePackListEntryFound;
import net.minecraft.client.resources.ResourcePackRepository;

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
            ResourcePackRepository.Entry pack = ((ResourcePackListEntryFound) entry).func_148318_i();
            // Without the Entry mixin (baseline mode) nothing implements EntryExtension: show no badge.
            PackResolution r = pack instanceof EntryExtension ? ((EntryExtension) pack).fastpacks$resolution() : null;
            return r == null ? "" : r.badge();
        }
        return "";
    }
}
