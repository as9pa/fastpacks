package io.github.as9pa.fastpacks.filter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class PackScanTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();
    private File cacheDir;

    @Before
    public void setUp() {
        PackScan.reset();
        cacheDir = new File(tmp.getRoot(), "config/fastpacks");
        PackScan.configure(cacheDir);
    }

    @After
    public void tearDown() {
        PackScan.reset();
    }

    private File stonePack(String name, int width) throws IOException {
        Map<String, byte[]> e = new LinkedHashMap<>();
        e.put("pack.mcmeta", TestPacks.MCMETA);
        e.put("assets/minecraft/textures/blocks/stone.png", TestPacks.png(width, width));
        return TestPacks.zip(new File(tmp.getRoot(), name), e);
    }

    private static PackResolution await(File pack) throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<PackResolution> got = new AtomicReference<>();
        final AtomicReference<String> thread = new AtomicReference<>();
        PackScan.submit(pack, r -> {
            got.set(r);
            thread.set(Thread.currentThread().getName());
            latch.countDown();
        });
        assertTrue("result not delivered within 10 s", latch.await(10, TimeUnit.SECONDS));
        assertTrue(thread.get(), thread.get().startsWith("fastpacks-icon-"));
        return got.get();
    }

    @Test
    public void scansOnTheBackgroundPoolAndWritesTheCache() throws Exception {
        File pack = stonePack("s32.zip", 32);
        assertEquals(PackResolution.textures(32), await(pack));

        File json = new File(cacheDir, "packs.json");
        long deadline = System.currentTimeMillis() + 10000;
        while (!json.isFile() && System.currentTimeMillis() < deadline) {
            Thread.sleep(50);
        }
        assertTrue("packs.json not written within 10 s", json.isFile());
        assertEquals(PackResolution.textures(32), new PackCache(json).lookup(PackCache.key(pack)));
    }

    @Test
    public void cachedValueIsServedWithoutRescanning() throws Exception {
        File pack = stonePack("s16.zip", 16);
        PackCache pre = new PackCache(new File(cacheDir, "packs.json"));
        pre.put(PackCache.key(pack), PackResolution.textures(64));   // deliberately wrong: proves the cache is trusted
        pre.flush();
        PackScan.reset();
        PackScan.configure(cacheDir);
        assertEquals(PackResolution.textures(64), await(pack));
    }

    @Test
    public void missingPackReportsUnknown() throws Exception {
        assertEquals(PackResolution.UNKNOWN, await(new File(tmp.getRoot(), "gone.zip")));
    }
}
