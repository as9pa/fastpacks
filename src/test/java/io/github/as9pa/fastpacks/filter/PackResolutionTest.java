package io.github.as9pa.fastpacks.filter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

public class PackResolutionTest {
    @Test
    public void bucketsAtBoundaries() {
        assertEquals(16, PackResolution.bucket(8));
        assertEquals(16, PackResolution.bucket(16));
        assertEquals(32, PackResolution.bucket(17));
        assertEquals(32, PackResolution.bucket(32));
        assertEquals(64, PackResolution.bucket(33));
        assertEquals(64, PackResolution.bucket(64));
        assertEquals(128, PackResolution.bucket(65));
        assertEquals(128, PackResolution.bucket(128));
        assertEquals(128, PackResolution.bucket(512));
    }

    @Test
    public void emptyWidthsIsOverlay() {
        assertEquals(PackResolution.OVERLAY, PackResolution.fromWidths(Collections.<Integer>emptyList()));
    }

    @Test
    public void mostCommonWidthWins() {
        assertEquals(PackResolution.textures(16), PackResolution.fromWidths(Arrays.asList(16, 16, 32)));
        assertEquals(PackResolution.textures(64), PackResolution.fromWidths(Arrays.asList(64, 16, 64, 64, 32)));
    }

    @Test
    public void tieGoesToTheLargerWidth() {
        assertEquals(PackResolution.textures(32), PackResolution.fromWidths(Arrays.asList(16, 32)));
        assertEquals(PackResolution.textures(32), PackResolution.fromWidths(Arrays.asList(32, 32, 16, 16)));
        assertEquals(PackResolution.textures(32), PackResolution.fromWidths(Arrays.asList(16, 16, 32, 32)));
    }

    @Test
    public void widthsAreBucketedAfterVoting() {
        assertEquals(PackResolution.textures(128), PackResolution.fromWidths(Arrays.asList(512, 256)));
        assertEquals(PackResolution.textures(32), PackResolution.fromWidths(Arrays.asList(20, 20, 64)));
    }

    @Test
    public void badgeText() {
        assertEquals("16x", PackResolution.textures(16).badge());
        assertEquals("128x", PackResolution.textures(128).badge());
        assertEquals("overlay", PackResolution.OVERLAY.badge());
        assertEquals("", PackResolution.UNKNOWN.badge());
    }

    @Test
    public void equalityIsByValue() {
        assertEquals(PackResolution.textures(32), PackResolution.textures(32));
        assertEquals(PackResolution.textures(32).hashCode(), PackResolution.textures(32).hashCode());
        assertNotEquals(PackResolution.textures(32), PackResolution.textures(16));
        assertNotEquals(PackResolution.OVERLAY, PackResolution.UNKNOWN);
    }
}
