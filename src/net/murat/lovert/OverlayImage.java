package net.murat.lovert;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

/**
 * 2D image overlay (e.g. a logo or watermark).
 *
 * Sizing modes (in priority order):
 *   1. If {@link #drawRect} is non-null, the bitmap is drawn into that exact
 *      pixel rectangle; anchor/margin/width are ignored.
 *   2. If {@link #width} > 0, the width is a ratio of the image width and the
 *      height follows the bitmap's aspect ratio (anchor + margins apply).
 *   3. If {@link #width} equal/lessThan 0, the bitmap is drawn at its native pixel size
 *      (anchor + margins apply).
 *
 * The rotation pivot is the center of the destination rectangle. When
 * {@link #flipX} and/or {@link #flipY} is set, the bitmap is mirrored around
 * the same center before rotation is applied, so the visual result is a
 * rotated, mirrored image placed at the resolved anchor position.
 */
public class OverlayImage extends Overlay {

    /** The bitmap to draw. Must be non-null and not recycled at draw time. */
    public Bitmap image;

    /**
     * Width as a ratio of the image width. Height follows the aspect ratio.
     * Set to equal/lessThan 0 to draw at the bitmap's native pixel size.
     */
    public float width = 0.15f;

    /**
     * Optional explicit destination rectangle in pixels.
     * When set, overrides {@link #width} and the anchor/margin placement.
     */
    public RectF drawRect;

    /** Creates an empty image overlay. Fields can be filled afterwards. */
    public OverlayImage() {
    }

    /** Creates an image overlay with the given bitmap and defaults for the rest. */
    public OverlayImage(Bitmap image) {
        this.image = image;
    }

    /**
     * Convenience method equivalent to a desktop
     * drawImage(image, x, y, w, h): sets {@link #drawRect} and draws immediately.
     */
    public void draw(Canvas c, float x, float y, float w, float h) {
        RectF old = drawRect;
        drawRect = new RectF(x, y, x + w, y + h);
        try {
            draw(c, c.getWidth(), c.getHeight());
        } finally {
            drawRect = old;
        }
    }

    @Override
    public void draw(Canvas c, int W, int H) {
        if (image == null || image.isRecycled()) return;

        RectF dst;
        if (drawRect != null) {
            // Explicit rectangle: no anchor/margin/width math.
            dst = new RectF(drawRect);
        } else {
            float w, h;
            if (width > 0f) {
                w = W * width;
                h = w * image.getHeight() / image.getWidth();   // preserve aspect ratio
            } else {
                w = image.getWidth();                           // native pixel size
                h = image.getHeight();
            }
            float[] xy = resolve(W, H, w, h);
            dst = new RectF(xy[0], xy[1], xy[0] + w, xy[1] + h);
        }

        float cx = dst.centerX();
        float cy = dst.centerY();

        Paint p = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
        p.setAlpha((int) (alpha * 255));

        c.save();
        // Rotate around the destination rectangle's center.
        if (rotate != 0f) c.rotate(rotate, cx, cy);
        // Mirror around the same center so the placement and rotation pivot
        // are unaffected by the flip direction.
        if (flipX || flipY) {
            c.translate(cx, cy);
            c.scale(flipX ? -1f : 1f, flipY ? -1f : 1f);
            c.translate(-cx, -cy);
        }
        c.drawBitmap(image, null, dst, p);
        c.restore();
    }
    
}