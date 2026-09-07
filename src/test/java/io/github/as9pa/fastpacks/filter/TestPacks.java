package io.github.as9pa.fastpacks.filter;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.imageio.ImageIO;

/** Builds throwaway packs for tests. */
final class TestPacks {
    static final byte[] MCMETA = "{\"pack\":{\"pack_format\":1,\"description\":\"t\"}}".getBytes();

    private TestPacks() {}

    static byte[] png(int w, int h) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB), "png", out);
        return out.toByteArray();
    }

    static File zip(File target, Map<String, byte[]> entries) throws IOException {
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(target))) {
            for (Map.Entry<String, byte[]> e : entries.entrySet()) {
                out.putNextEntry(new ZipEntry(e.getKey()));
                out.write(e.getValue());
                out.closeEntry();
            }
        }
        return target;
    }

    static void write(File file, byte[] bytes) throws IOException {
        File dir = file.getParentFile();
        if (dir != null) {
            dir.mkdirs();
        }
        Files.write(file.toPath(), bytes);
    }
}
