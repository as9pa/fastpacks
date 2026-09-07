package io.github.as9pa.fastpacks.icon;

import io.github.as9pa.fastpacks.Log;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Decodes pack icons off the client thread.
 *
 * <p>Must not reference Minecraft, Forge, or GL: the resource pack repository (and therefore the
 * first {@link #submit}) is constructed during {@code Minecraft.startGame}, before any mod is initialised.
 */
public final class IconLoader {
    private static final Object LOCK = new Object();
    private static ExecutorService pool;
    private static BufferedImage placeholder;

    private IconLoader() {}

    /** clamp(cores - 1, 1, 4). */
    static int threadCount(int cores) {
        return Math.max(1, Math.min(4, cores - 1));
    }

    /** The image every entry shows until its own icon arrives. Vanilla's default pack.png if available. */
    public static BufferedImage placeholder() {
        synchronized (LOCK) {
            if (placeholder == null) {
                BufferedImage img = null;
                InputStream in = IconLoader.class.getResourceAsStream("/pack.png");
                if (in != null) {
                    img = IconImages.decode(in);
                }
                if (img == null) {
                    img = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
                    Graphics2D g = img.createGraphics();
                    try {
                        g.setColor(new Color(0x40, 0x40, 0x40));
                        g.fillRect(0, 0, 32, 32);
                    } finally {
                        g.dispose();
                    }
                }
                placeholder = img;
            }
            return placeholder;
        }
    }

    /** The shared background pool; resolution scans queue here too so pack files are read on the same threads. */
    public static Executor executor() {
        return pool();
    }

    /** Queues a background load; the sink is invoked only when an icon was decoded. */
    public static void submit(final File packFile, final IconSink sink) {
        pool().execute(() -> {
            BufferedImage img = load(packFile);
            if (img != null) {
                sink.acceptIcon(img);
            }
        });
    }

    /** Synchronous load + downscale. Null on any failure (missing, corrupt, not an image). */
    static BufferedImage load(File packFile) {
        try {
            BufferedImage img;
            if (packFile.isDirectory()) {
                File png = new File(packFile, "pack.png");
                if (!png.isFile()) {
                    return null;
                }
                img = IconImages.decode(new FileInputStream(png));
            } else {
                try (ZipFile zip = new ZipFile(packFile)) {
                    ZipEntry entry = zip.getEntry("pack.png");
                    if (entry == null) {
                        return null;
                    }
                    img = IconImages.decode(zip.getInputStream(entry));
                }
            }
            return img == null ? null : IconImages.downscale(img, IconImages.MAX_ICON_SIZE);
        } catch (IOException | RuntimeException e) {
            Log.LOG.debug("fastpacks: could not load icon for {}: {}", packFile.getName(), e.toString());
            return null;
        }
    }

    private static ExecutorService pool() {
        synchronized (LOCK) {
            if (pool == null) {
                int threads = threadCount(Runtime.getRuntime().availableProcessors());
                pool = Executors.newFixedThreadPool(threads, new ThreadFactory() {
                    private final AtomicInteger n = new AtomicInteger();

                    @Override
                    public Thread newThread(Runnable r) {
                        Thread t = new Thread(r, "fastpacks-icon-" + n.incrementAndGet());
                        t.setDaemon(true);
                        t.setPriority(Thread.MIN_PRIORITY);
                        return t;
                    }
                });
            }
            return pool;
        }
    }
}
