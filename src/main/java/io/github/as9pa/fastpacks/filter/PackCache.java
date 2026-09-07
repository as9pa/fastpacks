package io.github.as9pa.fastpacks.filter;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.as9pa.fastpacks.Log;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;

/**
 * On-disk memory of scanned pack resolutions, keyed by file name, size and modification time.
 * Thread-safe; loaded lazily on first use; never throws into the caller.
 */
public final class PackCache {
    public static final int FORMAT_VERSION = 1;

    private final File file;
    private final Map<String, PackResolution> entries = new HashMap<>();
    private boolean loaded;
    private boolean dirty;

    public PackCache(File file) {
        this.file = file;
    }

    public static String key(File pack) {
        return pack.getName() + ":" + pack.length() + ":" + pack.lastModified();
    }

    public synchronized PackResolution lookup(String key) {
        ensureLoaded();
        return entries.get(key);
    }

    public synchronized void put(String key, PackResolution resolution) {
        ensureLoaded();
        PackResolution previous = entries.put(key, resolution);
        if (!resolution.equals(previous)) {
            dirty = true;
        }
    }

    public synchronized int size() {
        ensureLoaded();
        return entries.size();
    }

    public synchronized boolean isDirty() {
        return dirty;
    }

    /** Writes the file if anything changed, via a temp file and rename. True when a write happened. */
    public synchronized boolean flush() {
        if (!dirty) {
            return false;
        }
        JsonObject packs = new JsonObject();
        for (Map.Entry<String, PackResolution> e : entries.entrySet()) {
            JsonObject o = new JsonObject();
            o.addProperty("res", e.getValue().res);
            o.addProperty("kind", e.getValue().kind.name());
            packs.add(e.getKey(), o);
        }
        JsonObject root = new JsonObject();
        root.addProperty("version", FORMAT_VERSION);
        root.add("packs", packs);

        File tmp = new File(file.getPath() + ".tmp");
        try {
            File dir = file.getParentFile();
            if (dir != null) {
                dir.mkdirs();
            }
            try (Writer w = new OutputStreamWriter(new FileOutputStream(tmp), StandardCharsets.UTF_8)) {
                new GsonBuilder().setPrettyPrinting().create().toJson(root, w);
            }
            try {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            dirty = false;
            return true;
        } catch (IOException | RuntimeException e) {
            Log.LOG.warn("fastpacks: could not write {}: {}", file, e.toString());
            tmp.delete();
            return false;
        }
    }

    private void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        if (!file.isFile()) {
            return;
        }
        try (Reader r = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            JsonObject root = new JsonParser().parse(r).getAsJsonObject();
            JsonElement version = root.get("version");
            if (version == null || version.getAsInt() != FORMAT_VERSION) {
                Log.LOG.info("fastpacks: ignoring {} (unsupported format)", file);
                return;
            }
            JsonObject packs = root.getAsJsonObject("packs");
            if (packs == null) {
                return;
            }
            for (Map.Entry<String, JsonElement> e : packs.entrySet()) {
                JsonObject o = e.getValue().getAsJsonObject();
                PackResolution.Kind kind;
                try {
                    kind = PackResolution.Kind.valueOf(o.get("kind").getAsString());
                } catch (IllegalArgumentException | NullPointerException bad) {
                    continue;
                }
                PackResolution value;
                if (kind == PackResolution.Kind.TEXTURES) {
                    value = PackResolution.textures(o.get("res").getAsInt());
                } else if (kind == PackResolution.Kind.OVERLAY) {
                    value = PackResolution.OVERLAY;
                } else {
                    value = PackResolution.UNKNOWN;
                }
                entries.put(e.getKey(), value);
            }
            Log.LOG.info("fastpacks: loaded {} cached pack resolutions", entries.size());
        } catch (IOException | RuntimeException e) {
            entries.clear();
            Log.LOG.info("fastpacks: ignoring unreadable {}: {}", file, e.toString());
        }
    }
}
