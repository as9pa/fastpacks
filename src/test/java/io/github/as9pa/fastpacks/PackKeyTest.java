package io.github.as9pa.fastpacks;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class PackKeyTest {
    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void zipFileUsesZipKind() throws IOException {
        File f = tmp.newFile("Cool Pack §b16x.zip");
        assertEquals("Cool Pack §b16x.zip:zip:" + f.lastModified(), PackKey.compute(f));
    }

    @Test
    public void directoryUsesFolderKind() throws IOException {
        File d = tmp.newFolder("Vaes Pack Folder!");
        assertEquals("Vaes Pack Folder!:folder:" + d.lastModified(), PackKey.compute(d));
    }

    @Test
    public void matchesVanillaFormatExpression() throws IOException {
        File f = tmp.newFile("a.zip");
        String vanilla = String.format("%s:%s:%d", f.getName(), f.isDirectory() ? "folder" : "zip", f.lastModified());
        assertEquals(vanilla, PackKey.compute(f));
    }

    @Test
    public void missingFileStillProducesAKey() {
        File f = new File(tmp.getRoot(), "does-not-exist.zip");
        assertEquals("does-not-exist.zip:zip:0", PackKey.compute(f));
    }
}
