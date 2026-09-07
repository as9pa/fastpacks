package io.github.as9pa.fastpacks.filter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import io.github.as9pa.fastpacks.filter.PackFilter.Chip;
import org.junit.After;
import org.junit.Test;

public class PackFilterTest {
    private static final PackResolution R16 = PackResolution.textures(16);
    private static final PackResolution R32 = PackResolution.textures(32);
    private static final PackResolution R64 = PackResolution.textures(64);
    private static final PackResolution R128 = PackResolution.textures(128);

    @After
    public void resetChip() {
        PackFilter.setActiveChip(Chip.ALL);
    }

    @Test
    public void chipLabelsMatchTheWireframe() {
        assertEquals("All", Chip.ALL.label);
        assertEquals("16x", Chip.R16.label);
        assertEquals("32x", Chip.R32.label);
        assertEquals("64x", Chip.R64.label);
        assertEquals("128x+", Chip.R128.label);
        assertEquals("Overlay", Chip.OVERLAY.label);
    }

    @Test
    public void allShowsEverythingIncludingPendingAndUnknown() {
        assertTrue(PackFilter.chipMatches(Chip.ALL, null));
        assertTrue(PackFilter.chipMatches(Chip.ALL, PackResolution.UNKNOWN));
        assertTrue(PackFilter.chipMatches(Chip.ALL, PackResolution.OVERLAY));
        assertTrue(PackFilter.chipMatches(Chip.ALL, R32));
    }

    @Test
    public void exactChipsMatchOnlyTheirBucket() {
        assertTrue(PackFilter.chipMatches(Chip.R16, R16));
        assertFalse(PackFilter.chipMatches(Chip.R16, R32));
        assertTrue(PackFilter.chipMatches(Chip.R32, R32));
        assertFalse(PackFilter.chipMatches(Chip.R32, R64));
        assertTrue(PackFilter.chipMatches(Chip.R64, R64));
        assertFalse(PackFilter.chipMatches(Chip.R64, R128));
        assertFalse(PackFilter.chipMatches(Chip.R16, PackResolution.OVERLAY));
        assertFalse(PackFilter.chipMatches(Chip.R16, PackResolution.UNKNOWN));
        assertFalse(PackFilter.chipMatches(Chip.R16, null));
    }

    @Test
    public void r128ChipIs128AndAbove() {
        assertTrue(PackFilter.chipMatches(Chip.R128, R128));
        assertFalse(PackFilter.chipMatches(Chip.R128, R64));
        assertFalse(PackFilter.chipMatches(Chip.R128, PackResolution.OVERLAY));
    }

    @Test
    public void overlayChipMatchesOnlyOverlays() {
        assertTrue(PackFilter.chipMatches(Chip.OVERLAY, PackResolution.OVERLAY));
        assertFalse(PackFilter.chipMatches(Chip.OVERLAY, R16));
        assertFalse(PackFilter.chipMatches(Chip.OVERLAY, PackResolution.UNKNOWN));
        assertFalse(PackFilter.chipMatches(Chip.OVERLAY, null));
    }

    @Test
    public void queryNormalisation() {
        assertEquals("", PackFilter.normalizeQuery(null));
        assertEquals("", PackFilter.normalizeQuery("   "));
        assertEquals("faithful", PackFilter.normalizeQuery("  FaithFul "));
    }

    @Test
    public void formattingCodesAreStripped() {
        assertEquals("Aether 16x", PackFilter.stripFormatting("\u00a7bAether \u00a7f16x"));
        assertEquals("", PackFilter.stripFormatting(null));
        assertEquals("a", PackFilter.stripFormatting("a\u00a7b"));
        assertEquals("a", PackFilter.stripFormatting("a\u00a7"));
    }

    @Test
    public void queryMatchesNameOrDescriptionCaseInsensitively() {
        assertTrue(PackFilter.queryMatches("", "Aether 16x", "by Nova"));
        assertTrue(PackFilter.queryMatches("aether", "Aether 16x", "by Nova"));
        assertTrue(PackFilter.queryMatches("nova", "Aether 16x", "\u00a7dby Nova"));
        assertTrue(PackFilter.queryMatches("aether 16x", "\u00a7bAether \u00a7f16x", "by Nova"));
        assertFalse(PackFilter.queryMatches("zephyr", "Aether 16x", "by Nova"));
        assertTrue(PackFilter.queryMatches("16x", "Aether 16x", null));
    }

    @Test
    public void matchesIsChipAndQuery() {
        assertTrue(PackFilter.matches(Chip.R16, "aether", "Aether 16x", "by Nova", R16));
        assertFalse(PackFilter.matches(Chip.R32, "aether", "Aether 16x", "by Nova", R16));
        assertFalse(PackFilter.matches(Chip.R16, "zephyr", "Aether 16x", "by Nova", R16));
        assertTrue(PackFilter.matches(Chip.ALL, "", "Anything", "", null));
    }

    @Test
    public void activeChipIsRememberedProcessWide() {
        assertEquals(Chip.ALL, PackFilter.activeChip());
        PackFilter.setActiveChip(Chip.R32);
        assertEquals(Chip.R32, PackFilter.activeChip());
        PackFilter.setActiveChip(null);
        assertEquals(Chip.ALL, PackFilter.activeChip());
    }
}
