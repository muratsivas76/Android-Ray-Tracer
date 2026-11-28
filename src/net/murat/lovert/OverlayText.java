package net.murat.lovert;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;

import java.io.File;

/**
 * 2D text overlay.
 *
 * Font resolution order:
 *   1. A file named {@link #fontFace} inside {@link #ttfDir} (if set).
 *   2. A system font family named {@link #fontFace} (e.g. "serif", "sans-serif").
 *
 * The rotation pivot is the center of the measured text bounds, so a rotated
 * overlay attached to a corner does not spill outwards from that corner.
 *
 * Text size is given as a ratio of the image height.
 */
public class OverlayText extends Overlay {

    /** The text to draw. */
    public String text = "";

    /** System font family name or a .ttf file name inside {@link #ttfDir}. */
    public String fontFace = "sans-serif";

    /** One of: NORMAL | BOLD | ITALIC | BOLD_ITALIC. */
    public String fontStyle = "NORMAL";

    /** Text size as a ratio of the image height. */
    public float size = 0.04f;

    /** ARGB color, e.g. 0xFFFFFFFF for opaque white. */
    public int color = 0xFFFFFFFF;

    /** Directory containing optional .ttf files; may be null. */
    public File ttfDir;

    /** Creates an empty text overlay. Fields can be filled afterwards. */
    public OverlayText() {
    }

    /** Creates a text overlay with the given text and defaults for the rest. */
    public OverlayText(String text) {
        this.text = text;
    }

    /**
     * Builds the Typeface from {@link #fontFace} and {@link #fontStyle}.
     * Tries the ttf directory first, then falls back to a system family.
     */
    private Typeface makeFont() {
        int s = Typeface.NORMAL;
        if ("BOLD".equals(fontStyle)) s = Typeface.BOLD;
        else if ("ITALIC".equals(fontStyle)) s = Typeface.ITALIC;
        else if ("BOLD_ITALIC".equals(fontStyle)) s = Typeface.BOLD_ITALIC;

        Typeface base;
        File ttf = null;
		if (ttfDir != null && fontFace != null && !fontFace.trim().isEmpty()) {
			String name = fontFace.trim();
			ttf = new File(ttfDir, name);
			if (!ttf.isFile() && !name.toLowerCase(java.util.Locale.US).endsWith(".ttf")) {
				ttf = new File(ttfDir, name + ".ttf");
			}
		}
		if (ttf != null && ttf.isFile()) {
			try {
				base = Typeface.createFromFile(ttf);
			} catch (Exception e) {
				base = Typeface.create(fontFace, Typeface.NORMAL);
			}
		} else {
			base = Typeface.create(fontFace, Typeface.NORMAL);
		}
        return Typeface.create(base, s);
    }

    @Override
    public void draw(Canvas c, int W, int H) {
        if (text == null || text.isEmpty()) return;

        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTypeface(makeFont());
        p.setTextSize(H * size);
        p.setColor(color);
        p.setAlpha((int) (alpha * 255));

        float w = p.measureText(text);
        float h = p.descent() - p.ascent();
        float[] xy = resolve(W, H, w, h);              // top-left corner
        float cx = xy[0] + w / 2f;
        float cy = xy[1] + h / 2f;                     // pivot = text center

        c.save();
        if (rotate != 0f) c.rotate(rotate, cx, cy);
        c.drawText(text, xy[0], xy[1] - p.ascent(), p);
        c.restore();
    }
    
}
