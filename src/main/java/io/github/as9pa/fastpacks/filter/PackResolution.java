package io.github.as9pa.fastpacks.filter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** What the scanner found in a pack: the texture resolution bucket, an overlay, or nothing usable. Immutable. */
public final class PackResolution {
    public enum Kind { TEXTURES, OVERLAY, UNKNOWN }

    public static final PackResolution OVERLAY = new PackResolution(Kind.OVERLAY, 0);
    public static final PackResolution UNKNOWN = new PackResolution(Kind.UNKNOWN, 0);

    public final Kind kind;
    /** 16, 32, 64 or 128 for TEXTURES; 0 otherwise. */
    public final int res;

    private PackResolution(Kind kind, int res) {
        this.kind = kind;
        this.res = res;
    }

    public static PackResolution textures(int bucket) {
        return new PackResolution(Kind.TEXTURES, bucket);
    }

    /** 16, 32, 64, or 128 for anything larger. */
    public static int bucket(int width) {
        if (width <= 16) {
            return 16;
        }
        if (width <= 32) {
            return 32;
        }
        if (width <= 64) {
            return 64;
        }
        return 128;
    }

    /** Most common width wins; a tie goes to the larger width; no widths means an overlay pack. */
    public static PackResolution fromWidths(List<Integer> widths) {
        if (widths.isEmpty()) {
            return OVERLAY;
        }
        Map<Integer, Integer> counts = new HashMap<>();
        int best = -1;
        int bestCount = 0;
        for (int w : widths) {
            Integer prev = counts.get(w);
            int c = prev == null ? 1 : prev + 1;
            counts.put(w, c);
            if (c > bestCount || (c == bestCount && w > best)) {
                best = w;
                bestCount = c;
            }
        }
        return textures(bucket(best));
    }

    /** Text for the row badge: "16x", "overlay", or "" when nothing is known. */
    public String badge() {
        switch (kind) {
            case TEXTURES:
                return res + "x";
            case OVERLAY:
                return "overlay";
            default:
                return "";
        }
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof PackResolution)) {
            return false;
        }
        PackResolution other = (PackResolution) o;
        return kind == other.kind && res == other.res;
    }

    @Override
    public int hashCode() {
        return kind.hashCode() * 31 + res;
    }

    @Override
    public String toString() {
        return kind == Kind.TEXTURES ? "TEXTURES(" + res + ")" : kind.name();
    }
}
