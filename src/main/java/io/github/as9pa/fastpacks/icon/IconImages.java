package io.github.as9pa.fastpacks.icon;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import javax.imageio.ImageIO;

/** Pure image helpers. No Minecraft imports; safe on any thread. */
public final class IconImages {
    /** Longest side an icon may have after loading. 32 GUI px at GUI scale 4 is 128 device px. */
    public static final int MAX_ICON_SIZE = 128;

    private IconImages() {}

    /** Decodes an image from the stream and closes it. Returns null when the data is not a readable image. */
    public static BufferedImage decode(InputStream in) {
        try {
            return ImageIO.read(in);
        } catch (IOException | RuntimeException e) {
            return null;
        } finally {
            try {
                in.close();
            } catch (IOException ignored) {
                // nothing useful to do
            }
        }
    }

    /** Scales so the longest side equals {@code max}. Returns the same instance if already within bounds. */
    public static BufferedImage downscale(BufferedImage img, int max) {
        int w = img.getWidth();
        int h = img.getHeight();
        if (w <= max && h <= max) {
            return img;
        }
        int longest = Math.max(w, h);
        int nw = Math.max(1, w * max / longest);
        int nh = Math.max(1, h * max / longest);
        BufferedImage out = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(img, 0, 0, nw, nh, null);
        } finally {
            g.dispose();
        }
        return out;
    }
}
