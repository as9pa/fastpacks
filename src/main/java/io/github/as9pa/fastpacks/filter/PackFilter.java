package io.github.as9pa.fastpacks.filter;

import java.util.Locale;

/** Which packs the Available list shows: one chip plus a free-text query. No Minecraft references. */
public final class PackFilter {
    public enum Chip {
        ALL("All"), R16("16x"), R32("32x"), R64("64x"), R128("128x+"), OVERLAY("Overlay");

        public final String label;

        Chip(String label) {
            this.label = label;
        }
    }

    private static volatile Chip activeChip = Chip.ALL;

    private PackFilter() {}

    /** Remembered for the life of the process, across screen opens. */
    public static Chip activeChip() {
        return activeChip;
    }

    public static void setActiveChip(Chip chip) {
        activeChip = chip == null ? Chip.ALL : chip;
    }

    public static String normalizeQuery(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
    }

    /** Drops Minecraft formatting codes: a section sign and the character after it. */
    public static String stripFormatting(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\u00a7') {
                i++;
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    /** A null resolution means the background scan has not finished: only ALL shows it. */
    public static boolean chipMatches(Chip chip, PackResolution r) {
        switch (chip) {
            case ALL:
                return true;
            case OVERLAY:
                return r != null && r.kind == PackResolution.Kind.OVERLAY;
            case R16:
                return isTextures(r, 16);
            case R32:
                return isTextures(r, 32);
            case R64:
                return isTextures(r, 64);
            case R128:
                return r != null && r.kind == PackResolution.Kind.TEXTURES && r.res >= 128;
            default:
                return false;
        }
    }

    private static boolean isTextures(PackResolution r, int res) {
        return r != null && r.kind == PackResolution.Kind.TEXTURES && r.res == res;
    }

    public static boolean queryMatches(String normalizedQuery, String name, String description) {
        if (normalizedQuery.isEmpty()) {
            return true;
        }
        String haystack = (stripFormatting(name) + " " + stripFormatting(description)).toLowerCase(Locale.ROOT);
        return haystack.contains(normalizedQuery);
    }

    public static boolean matches(Chip chip, String normalizedQuery, String name, String description, PackResolution r) {
        return chipMatches(chip, r) && queryMatches(normalizedQuery, name, description);
    }
}
