package io.github.as9pa.fastpacks.filter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class PackCacheTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    private File json() {
        return new File(tmp.getRoot(), "config/fastpacks/packs.json");
    }

    @Test
    public void keyIsNameSizeAndModifiedTime() throws IOException {
        File f = tmp.newFile("Aether 16x.zip");
        TestPacks.write(f, new byte[123]);
        assertEquals("Aether 16x.zip:123:" + f.lastModified(), PackCache.key(f));
    }

    @Test
    public void missingFileMeansEmptyCache() {
        PackCache cache = new PackCache(json());
        assertNull(cache.lookup("anything"));
        assertEquals(0, cache.size());
        assertFalse(cache.isDirty());
        assertFalse("nothing to flush", cache.flush());
        assertFalse(json().exists());
    }

    @Test
    public void roundTripsThroughTheFile() {
        PackCache cache = new PackCache(json());
        cache.put("a.zip:1:1", PackResolution.textures(16));
        cache.put("b.zip:2:2", PackResolution.OVERLAY);
        cache.put("c.zip:3:3", PackResolution.UNKNOWN);
        assertTrue(cache.isDirty());
        assertTrue(cache.flush());
        assertFalse(cache.isDirty());
        assertTrue(json().isFile());
        assertFalse(new File(json().getPath() + ".tmp").exists());

        PackCache reloaded = new PackCache(json());
        assertEquals(PackResolution.textures(16), reloaded.lookup("a.zip:1:1"));
        assertEquals(PackResolution.OVERLAY, reloaded.lookup("b.zip:2:2"));
        assertEquals(PackResolution.UNKNOWN, reloaded.lookup("c.zip:3:3"));
        assertNull(reloaded.lookup("d.zip:4:4"));
        assertEquals(3, reloaded.size());
    }

    @Test
    public void puttingTheSameValueDoesNotDirty() {
        PackCache cache = new PackCache(json());
        cache.put("a.zip:1:1", PackResolution.textures(16));
        cache.flush();
        cache.put("a.zip:1:1", PackResolution.textures(16));
        assertFalse(cache.isDirty());
        cache.put("a.zip:1:1", PackResolution.textures(32));
        assertTrue(cache.isDirty());
    }

    @Test
    public void wrongFormatVersionIsIgnored() throws IOException {
        TestPacks.write(json(), "{\"version\":99,\"packs\":{\"a.zip:1:1\":{\"res\":16,\"kind\":\"TEXTURES\"}}}".getBytes("UTF-8"));
        PackCache cache = new PackCache(json());
        assertNull(cache.lookup("a.zip:1:1"));
        assertEquals(0, cache.size());
    }

    @Test
    public void garbageFileIsIgnoredWithoutThrowing() throws IOException {
        TestPacks.write(json(), "{{{ not json".getBytes("UTF-8"));
        PackCache cache = new PackCache(json());
        assertNull(cache.lookup("a.zip:1:1"));
        cache.put("a.zip:1:1", PackResolution.textures(64));
        assertTrue("can overwrite a garbage file", cache.flush());
        assertEquals(PackResolution.textures(64), new PackCache(json()).lookup("a.zip:1:1"));
    }

    @Test
    public void unknownKindInFileIsSkipped() throws IOException {
        TestPacks.write(json(), "{\"version\":1,\"packs\":{\"a.zip:1:1\":{\"res\":0,\"kind\":\"WEIRD\"}}}".getBytes("UTF-8"));
        PackCache cache = new PackCache(json());
        assertEquals(0, cache.size());
    }

    @Test
    public void malformedEntryIsSkippedButOthersLoad() throws IOException {
        TestPacks.write(json(), ("{\"version\":1,\"packs\":{\"a.zip:1:1\":{\"res\":16,\"kind\":\"TEXTURES\"},"
                + "\"b.zip:2:2\":{\"kind\":\"TEXTURES\"}}}").getBytes("UTF-8"));
        PackCache cache = new PackCache(json());
        assertEquals(PackResolution.textures(16), cache.lookup("a.zip:1:1"));
        assertNull(cache.lookup("b.zip:2:2"));
        assertEquals(1, cache.size());
    }

    @Test
    public void nonObjectEntryIsSkippedButOthersLoad() throws IOException {
        TestPacks.write(json(), ("{\"version\":1,\"packs\":{\"a.zip:1:1\":{\"res\":16,\"kind\":\"TEXTURES\"},"
                + "\"c.zip:3:3\":5}}").getBytes("UTF-8"));
        PackCache cache = new PackCache(json());
        assertEquals(PackResolution.textures(16), cache.lookup("a.zip:1:1"));
        assertNull(cache.lookup("c.zip:3:3"));
        assertEquals(1, cache.size());
    }
}
