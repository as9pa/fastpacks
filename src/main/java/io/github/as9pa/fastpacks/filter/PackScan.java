package io.github.as9pa.fastpacks.filter;

import io.github.as9pa.fastpacks.Log;
import io.github.as9pa.fastpacks.icon.IconLoader;
import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Queues one resolution scan per pack on the icon pool: cache hit, or scan then write-through.
 * The cache file is flushed by a task queued behind the scans, so a cold start writes it a few times at most.
 */
public final class PackScan {
    private static final Object LOCK = new Object();
    private static PackCache cache;
    private static final AtomicBoolean FLUSH_QUEUED = new AtomicBoolean();

    private PackScan() {}

    /** Points the cache at {@code cacheDir/packs.json}. First call wins; later calls are ignored. */
    public static void configure(File cacheDir) {
        synchronized (LOCK) {
            if (cache == null) {
                cache = new PackCache(new File(cacheDir, "packs.json"));
            }
        }
    }

    public static void submit(final File pack, final ResolutionSink sink) {
        final PackCache c;
        synchronized (LOCK) {
            c = cache;
        }
        if (c == null) {
            throw new IllegalStateException("PackScan.configure was not called");
        }
        IconLoader.executor().execute(new Runnable() {
            @Override
            public void run() {
                PackResolution r;
                try {
                    String key = PackCache.key(pack);
                    r = c.lookup(key);
                    if (r == null) {
                        r = ResolutionScanner.scan(pack);
                        c.put(key, r);
                        scheduleFlush(c);
                    }
                } catch (RuntimeException e) {
                    Log.LOG.debug("fastpacks: scan failed for {}: {}", pack.getName(), e.toString());
                    r = PackResolution.UNKNOWN;
                }
                sink.acceptResolution(r);
            }
        });
    }

    private static void scheduleFlush(final PackCache c) {
        if (FLUSH_QUEUED.compareAndSet(false, true)) {
            IconLoader.executor().execute(new Runnable() {
                @Override
                public void run() {
                    FLUSH_QUEUED.set(false);
                    c.flush();
                }
            });
        }
    }

    /** Tests only. */
    static void reset() {
        synchronized (LOCK) {
            cache = null;
        }
        FLUSH_QUEUED.set(false);
    }
}
