package io.github.as9pa.fastpacks.icon;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import javax.imageio.ImageIO;
import org.junit.Test;

public class IconImagesTest {

    private static byte[] png(int w, int h) throws IOException {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        img.setRGB(0, 0, 0xFFFF0000);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    @Test
    public void decodesSmallPngUnchanged() throws IOException {
        BufferedImage img = IconImages.decode(new ByteArrayInputStream(png(16, 16)));
        assertNotNull(img);
        assertEquals(16, img.getWidth());
        assertEquals(16, img.getHeight());
    }

    @Test
    public void decodeReturnsNullForGarbage() {
        assertNull(IconImages.decode(new ByteArrayInputStream("definitely not a png".getBytes())));
    }

    @Test
    public void decodeReturnsNullForTruncatedPng() throws IOException {
        byte[] whole = png(64, 64);
        byte[] cut = Arrays.copyOf(whole, whole.length / 3);
        assertNull(IconImages.decode(new ByteArrayInputStream(cut)));
    }

    @Test
    public void decodeReturnsNullForNullStream() {
        assertNull(IconImages.decode(null));
    }

    @Test
    public void downscaleKeepsSmallImageInstance() {
        BufferedImage img = new BufferedImage(64, 64, BufferedImage.TYPE_INT_RGB);
        assertSame(img, IconImages.downscale(img, 128));
    }

    @Test
    public void downscaleKeepsExactlyMaxSizeInstance() {
        BufferedImage img = new BufferedImage(128, 128, BufferedImage.TYPE_INT_RGB);
        assertSame(img, IconImages.downscale(img, 128));
    }

    @Test
    public void downscalesWideImagePreservingAspect() {
        BufferedImage out = IconImages.downscale(new BufferedImage(512, 256, BufferedImage.TYPE_INT_RGB), 128);
        assertEquals(128, out.getWidth());
        assertEquals(64, out.getHeight());
        assertEquals(BufferedImage.TYPE_INT_ARGB, out.getType());
    }

    @Test
    public void downscalesTallImagePreservingAspect() {
        BufferedImage out = IconImages.downscale(new BufferedImage(100, 300, BufferedImage.TYPE_INT_RGB), 128);
        assertEquals(42, out.getWidth());
        assertEquals(128, out.getHeight());
    }

    @Test
    public void downscaleNeverProducesZeroSide() {
        BufferedImage out = IconImages.downscale(new BufferedImage(1, 4096, BufferedImage.TYPE_INT_RGB), 128);
        assertEquals(1, out.getWidth());
        assertEquals(128, out.getHeight());
    }

    @Test
    public void maxIconSizeIs128() {
        assertEquals(128, IconImages.MAX_ICON_SIZE);
    }
}
