package io.github.as9pa.fastpacks.filter;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ResolutionScannerTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    private static final String SWORD = "assets/minecraft/textures/items/diamond_sword.png";
    private static final String STONE = "assets/minecraft/textures/blocks/stone.png";
    private static final String DIRT = "assets/minecraft/textures/blocks/dirt.png";
    private static final String SKY = "assets/minecraft/mcpatcher/sky/world0/sky1.png";

    @Test
    public void zipPackVotesAcrossSampledTextures() throws IOException {
        Map<String, byte[]> e = new LinkedHashMap<>();
        e.put("pack.mcmeta", TestPacks.MCMETA);
        e.put(SWORD, TestPacks.png(32, 32));
        e.put(STONE, TestPacks.png(32, 32));
        e.put(DIRT, TestPacks.png(16, 16));
        assertEquals(PackResolution.textures(32), ResolutionScanner.scan(TestPacks.zip(new File(tmp.getRoot(), "a.zip"), e)));
    }

    @Test
    public void folderPackIsScannedToo() throws IOException {
        File folder = tmp.newFolder("FolderPack");
        TestPacks.write(new File(folder, "pack.mcmeta"), TestPacks.MCMETA);
        TestPacks.write(new File(folder, STONE), TestPacks.png(64, 64));
        assertEquals(PackResolution.textures(64), ResolutionScanner.scan(folder));
    }

    @Test
    public void packWithNoSampledTexturesIsOverlay() throws IOException {
        Map<String, byte[]> e = new LinkedHashMap<>();
        e.put("pack.mcmeta", TestPacks.MCMETA);
        e.put(SKY, TestPacks.png(512, 512));
        assertEquals(PackResolution.OVERLAY, ResolutionScanner.scan(TestPacks.zip(new File(tmp.getRoot(), "o.zip"), e)));
    }

    @Test
    public void unreadableSampledTextureIsIgnored() throws IOException {
        Map<String, byte[]> e = new LinkedHashMap<>();
        e.put(STONE, "not a png".getBytes());
        e.put(DIRT, TestPacks.png(16, 16));
        assertEquals(PackResolution.textures(16), ResolutionScanner.scan(TestPacks.zip(new File(tmp.getRoot(), "b.zip"), e)));
    }

    @Test
    public void missingFileIsUnknown() {
        assertEquals(PackResolution.UNKNOWN, ResolutionScanner.scan(new File(tmp.getRoot(), "gone.zip")));
    }

    @Test
    public void corruptZipIsUnknown() throws IOException {
        File bad = tmp.newFile("bad.zip");
        TestPacks.write(bad, "this is not a zip".getBytes());
        assertEquals(PackResolution.UNKNOWN, ResolutionScanner.scan(bad));
    }

    @Test
    public void nestedFolderZipDoesNotCrash() throws IOException {
        Map<String, byte[]> e = new LinkedHashMap<>();
        e.put("MyPack/pack.mcmeta", TestPacks.MCMETA);
        e.put("MyPack/" + STONE, TestPacks.png(32, 32));
        // Vanilla never loads such a zip, so the scanner is never asked; if it is, it must simply not throw.
        assertEquals(PackResolution.OVERLAY, ResolutionScanner.scan(TestPacks.zip(new File(tmp.getRoot(), "n.zip"), e)));
    }

    @Test
    public void sampleListIsTheSpecList() {
        assertEquals(12, ResolutionScanner.SAMPLES.length);
        assertEquals(SWORD, ResolutionScanner.SAMPLES[0]);
    }
}
