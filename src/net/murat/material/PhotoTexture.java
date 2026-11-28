package net.murat.material;

import android.graphics.Bitmap;
import net.murat.math.Point3;

/**
 * PhotoTexture is a texture that samples from a Bitmap with tiling support. Android adaptation:
 * Uses Bitmap + int[] for fast, GC-free sampling.
 *
 * @author Murat Ray Tracer (Raja inspired)
 * @version 1.0
 */
public class PhotoTexture implements RajaTexture {

  // =========================================================================
  // FIELDS
  // =========================================================================

  /** The Bitmap used for texture sampling. */
  private final Bitmap image;

  /** Cached pixel array for fast sampling. */
  private final int[] imagePixels;

  /** Width of the image in pixels. */
  private final int width;

  /** Height of the image in pixels. */
  private final int height;

  /** Image path string (can be set/get). */
  private String imagePath;

  // =========================================================================
  // CONSTRUCTORS
  // =========================================================================

  public PhotoTexture(Bitmap image) {
    if (image == null) {
      throw new IllegalArgumentException("Image cannot be null");
    }
    this.image = image;
    this.width = image.getWidth();
    this.height = image.getHeight();
    this.imagePixels = new int[width * height];
    image.getPixels(this.imagePixels, 0, width, 0, 0, width, height);
    this.imagePath = "";
  }

  public PhotoTexture(Bitmap image, String imagePath) {
    this(image);
    this.imagePath = imagePath;
  }

  // =========================================================================
  // RAJA TEXTURE INTERFACE METHODS
  // =========================================================================

  @Override
  public int sample(Point3 hitPoint, double u, double v) {
    // Tiling: wrap UV coordinates to [0, 1) range
    double uWrapped = u - Math.floor(u);
    double vWrapped = v - Math.floor(v);

    if (uWrapped >= 1.0) uWrapped = 0.999999;
    if (vWrapped >= 1.0) vWrapped = 0.999999;
    if (uWrapped < 0.0) uWrapped = 0.0;
    if (vWrapped < 0.0) vWrapped = 0.0;

    int x = (int) (uWrapped * width);
    int y = (int) (vWrapped * height);

    x = Math.min(x, width - 1);
    y = Math.min(y, height - 1);

    // Fast array lookup instead of image.getRGB()
    return imagePixels[y * width + x] | 0xFF000000;
  }

  // =========================================================================
  // IMAGE PATH GETTER/SETTER
  // =========================================================================

  public void setImagePath(String imagePath) {
    this.imagePath = imagePath;
  }

  public String getImagePath() {
    return imagePath;
  }

  // =========================================================================
  // GETTERS
  // =========================================================================

  public Bitmap getImage() {
    return image;
  }

  public int getWidth() {
    return width;
  }

  public int getHeight() {
    return height;
  }

  // =========================================================================
  // TO STRING
  // =========================================================================

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("textureType = photo|plain|damier;\n");
    sb.append("        textureColor = ").append("0xFFFFFFFF").append(";\n");
    sb.append("        checkerColor = ").append("0x00000000").append(";\n");
    sb.append("        textureScale = ").append("2.0").append(";\n");
    sb.append("        textureOffset = ").append("0.0").append(";\n");
    sb.append("        imagePath = \"").append(imagePath).append("\"");
    return sb.toString();
  }
}
