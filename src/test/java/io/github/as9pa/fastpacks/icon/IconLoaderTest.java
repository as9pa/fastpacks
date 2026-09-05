package io.github.as9pa.fastpacks.icon;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.imageio.ImageIO;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class IconLoaderTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    private static void writePng(File target, int w, int h) throws IOException {
        ImageIO.write(new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB), "png", target);
    }

    private File zipWithIcon(String name, int w, int h) throws IOException {
        File png = tmp.newFile(name + "-icon.png");
        writePng(png, w, h);
        File zip = new File(tmp.getRoot(), name + ".zip");
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(zip))) {
            out.putNextEntry(new ZipEntry("pack.mcmeta"));
            out.write("{\"pack\":{\"pack_format\":1,\"description\":\"t\"}}".getBytes("UTF-8"));
            out.closeEntry();
            out.putNextEntry(new ZipEntry("pack.png"));
            java.nio.file.Files.copy(png.toPath(), out);
            out.closeEntry();
        }
        return zip;
    }

    @Test
    public void loadsIconFromZip() throws IOException {
        BufferedImage img = IconLoader.load(zipWithIcon("small", 64, 64));
        assertNotNull(img);
        assertEquals(64, img.getWidth());
    }

    @Test
    public void loadDownscalesLargeIcon() throws IOException {
        BufferedImage img = IconLoader.load(zipWithIcon("big", 1024, 1024));
        assertNotNull(img);
        assertEquals(128, img.getWidth());
        assertEquals(128, img.getHeight());
    }

    @Test
    public void loadsIconFromFolderPack() throws IOException {
        File folder = tmp.newFolder("FolderPack");
        writePng(new File(folder, "pack.png"), 32, 32);
        BufferedImage img = IconLoader.load(folder);
        assertNotNull(img);
        assertEquals(32, img.getWidth());
    }

    @Test
    public void zipWithoutIconGivesNull() throws IOException {
        File zip = new File(tmp.getRoot(), "noicon.zip");
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(zip))) {
            out.putNextEntry(new ZipEntry("pack.mcmeta"));
            out.write("{}".getBytes("UTF-8"));
            out.closeEntry();
        }
        assertNull(IconLoader.load(zip));
    }

    @Test
    public void missingFileGivesNull() {
        assertNull(IconLoader.load(new File(tmp.getRoot(), "gone.zip")));
    }

    @Test
    public void corruptZipGivesNull() throws IOException {
        File bad = tmp.newFile("bad.zip");
        try (FileOutputStream out = new FileOutputStream(bad)) {
            out.write("this is not a zip".getBytes("UTF-8"));
        }
        assertNull(IconLoader.load(bad));
    }

    @Test
    public void submitDeliversImageOnBackgroundThread() throws Exception {
        File zip = zipWithIcon("async", 16, 16);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<BufferedImage> got = new AtomicReference<>();
        AtomicReference<String> thread = new AtomicReference<>();
        IconLoader.submit(zip, image -> {
            got.set(image);
            thread.set(Thread.currentThread().getName());
            latch.countDown();
        });
        assertTrue("icon not delivered within 10 s", latch.await(10, TimeUnit.SECONDS));
        assertEquals(16, got.get().getWidth());
        assertTrue(thread.get(), thread.get().startsWith("fastpacks-icon-"));
    }

    @Test
    public void submitDoesNotCallSinkWhenThereIsNoIcon() throws Exception {
        File zip = new File(tmp.getRoot(), "none.zip");
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(zip))) {
            out.putNextEntry(new ZipEntry("pack.mcmeta"));
            out.write("{}".getBytes("UTF-8"));
            out.closeEntry();
        }
        CountDownLatch latch = new CountDownLatch(1);
        IconLoader.submit(zip, image -> latch.countDown());
        assertTrue("sink must not be called", !latch.await(1, TimeUnit.SECONDS));
    }

    @Test
    public void placeholderIsCachedAndNeverNull() {
        BufferedImage a = IconLoader.placeholder();
        assertNotNull(a);
        assertSame(a, IconLoader.placeholder());
        assertTrue(a.getWidth() >= 16);
    }

    @Test
    public void threadCountIsClamped() {
        assertEquals(1, IconLoader.threadCount(1));
        assertEquals(1, IconLoader.threadCount(2));
        assertEquals(3, IconLoader.threadCount(4));
        assertEquals(4, IconLoader.threadCount(8));
        assertEquals(4, IconLoader.threadCount(32));
    }
}
