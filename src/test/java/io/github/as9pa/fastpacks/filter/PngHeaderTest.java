package io.github.as9pa.fastpacks.filter;

import static org.junit.Assert.assertEquals;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import javax.imageio.ImageIO;
import org.junit.Test;

public class PngHeaderTest {
    static byte[] png(int w, int h) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB), "png", out);
        return out.toByteArray();
    }

    @Test
    public void readsWidthOfRealPngs() throws IOException {
        assertEquals(16, PngHeader.width(new ByteArrayInputStream(png(16, 16))));
        assertEquals(32, PngHeader.width(new ByteArrayInputStream(png(32, 64))));
        assertEquals(128, PngHeader.width(new ByteArrayInputStream(png(128, 128))));
        assertEquals(1000, PngHeader.width(new ByteArrayInputStream(png(1000, 3))));
    }

    @Test
    public void truncatedStreamGivesMinusOne() throws IOException {
        byte[] full = png(64, 64);
        assertEquals(-1, PngHeader.width(new ByteArrayInputStream(Arrays.copyOf(full, 20))));
        assertEquals(-1, PngHeader.width(new ByteArrayInputStream(new byte[0])));
    }

    @Test
    public void nonPngGivesMinusOne() {
        byte[] junk = new byte[40];
        Arrays.fill(junk, (byte) 'x');
        assertEquals(-1, PngHeader.width(new ByteArrayInputStream(junk)));
    }

    @Test
    public void badChunkNameGivesMinusOne() throws IOException {
        byte[] bytes = png(16, 16);
        bytes[12] = 'X';
        assertEquals(-1, PngHeader.width(new ByteArrayInputStream(bytes)));
    }
}
