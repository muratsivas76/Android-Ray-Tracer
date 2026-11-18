package net.murat.material;

import android.graphics.Typeface;
import android.util.Log;

import java.io.File;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Centralized font factory for material classes.
 *
 * Font face resolution rules:
 *   - null / empty / "default"  -> Typeface.DEFAULT (no TTF lookup)
 *   - "arial"                   -> <baseDir>/ttf/arial.ttf
 *   - "arial.ttf"               -> <baseDir>/ttf/arial.ttf
 *   - missing / unreadable TTF  -> Typeface.DEFAULT (with warning log)
 *
 * Thread-safe and cached: each unique font face string is resolved once and
 * then reused for the lifetime of the process.
 */
public final class OurFontFactory {

    private static final String TAG = "OurFontFactory";

    /** Base directory for TTF lookup (e.g. Download/carets/raytracer). */
    private static String baseDir = null;

    /** Cache: face name -> resolved Typeface. */
    private static final Map<String, Typeface> cache = new HashMap<String, Typeface>();

    private OurFontFactory() {
        // Utility class, no instances allowed.
    }

    /**
     * Sets the base directory used for TTF lookup. Must be called once at
     * app startup (RayTracerAndroid.onCreate).
     */
    public static void setBaseDir(String dir) {
        baseDir = dir;
        // Base directory changed, invalidate any previously cached entries.
        cache.clear();
    }

    /**
     * Returns the current base directory for TTF lookup.
     */
    public static String getBaseDir() {
        return baseDir;
    }

    /**
     * Resolves a font face name to a Typeface.
     *
     * @param face Font face name ("default", "arial", "arial.ttf", ...)
     * @return A Typeface instance. Never null.
     */
    public static Typeface getFont(String face) {
        if (face == null) {
            return Typeface.DEFAULT;
        }

        String trimmed = face.trim();
        if (trimmed.isEmpty() || "default".equalsIgnoreCase(trimmed)) {
            return Typeface.DEFAULT;
        }

        // Cache key is the lowercased trimmed name, so "Arial" and "arial"
        // resolve to the same entry.
        String cacheKey = trimmed.toLowerCase(Locale.US);
        synchronized (cache) {
            Typeface cached = cache.get(cacheKey);
            if (cached != null) {
                return cached;
            }
        }

        Typeface resolved = loadFromDisk(trimmed);

        synchronized (cache) {
            cache.put(cacheKey, resolved);
        }
        return resolved;
    }

    /**
     * Loads a Typeface from <baseDir>/ttf/<name>[.ttf]. Falls back to
     * Typeface.DEFAULT when anything goes wrong.
     */
    private static Typeface loadFromDisk(String face) {
        if (baseDir == null || baseDir.trim().isEmpty()) {
            Log.w(TAG, "baseDir not set, falling back to Typeface.DEFAULT for: " + face);
            return Typeface.DEFAULT;
        }

        try {
            String fileName = face;
            if (!fileName.toLowerCase(Locale.US).endsWith(".ttf")) {
                fileName = fileName + ".ttf";
            }

            File ttfFile = new File(baseDir + "/ttf/" + fileName);
            if (ttfFile.exists()) {
                return Typeface.createFromFile(ttfFile);
            } else {
                Log.w(TAG, "Font not found: " + ttfFile.getAbsolutePath()
                        + " — falling back to Typeface.DEFAULT.");
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to load font: " + face
                    + " — falling back to Typeface.DEFAULT.", e);
        }
        return Typeface.DEFAULT;
    }
    
}
