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
