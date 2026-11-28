package net.murat.lovert;

import android.graphics.Canvas;

/**
 * Base class for 2D overlays drawn on top of a rendered image.
 *
 * Subclasses:
 *   - {@link OverlayText}  : draws a text string
 *   - {@link OverlayImage} : draws a bitmap
 *
 * All fields are public so a parser (e.g. SceneParser) can fill them directly.
 * Anchor names follow the pattern TOP_LEFT, TOP_CENTER, ..., BOTTOM_RIGHT, CENTER.
 * Margins and sizes are expressed as ratios of the image width/height (0..1).
 */
public abstract class Overlay {

    /** Corner/anchor the overlay is attached to. Default: BOTTOM_RIGHT. */
    public String anchor = "BOTTOM_RIGHT";

    /** Horizontal margin as a ratio of image width. */
    public float marginX = 0.02f;

    /** Vertical margin as a ratio of image height. */
    public float marginY = 0.02f;

    /** Rotation in degrees around the object's center. Positive = clockwise. */
    public float rotate = 0f;

    /** Global opacity, 0..1. Applied on top of any per-object alpha. */
    public float alpha = 1f;

    /** Horizontal mirror (flip around the vertical axis). Default: false. */
    public boolean flipX = false;

    /** Vertical mirror (flip around the horizontal axis). Default: false. */
    public boolean flipY = false;

    /**
     * Draws this overlay onto the given canvas.
     *
     * @param c target canvas
     * @param W image width in pixels
     * @param H image height in pixels
     */
    public abstract void draw(Canvas c, int W, int H);

    /**
     * Converts the anchor + margins into a top-left pixel coordinate
     * for an object of size (w, h).
     *
     * @return float[]{x, y} top-left corner in pixels
     */
	protected float[] resolve(int W, int H, float w, float h) {
		float mx = W * marginX;
		float my = H * marginY;
		float x, y;
		switch (anchor) {
			case "TOP_LEFT":      x = mx;                y = my;                 break;
			case "TOP_CENTER":    x = (W - w) / 2f + mx; y = my;                 break;
			case "TOP_RIGHT":     x = W - w - mx;        y = my;                 break;
			case "CENTER_LEFT":   x = mx;                y = (H - h) / 2f + my;  break;
			case "CENTER":        x = (W - w) / 2f + mx; y = (H - h) / 2f + my;  break;
			case "CENTER_RIGHT":  x = W - w - mx;        y = (H - h) / 2f + my;  break;
			case "BOTTOM_LEFT":   x = mx;                y = H - h - my;         break;
			case "BOTTOM_CENTER": x = (W - w) / 2f + mx; y = H - h - my;         break;
			case "BOTTOM_RIGHT":
			default:              x = W - w - mx;        y = H - h - my;         break;
		}
		return new float[]{x, y};
	}
    
}
