package net.murat.material;

import android.graphics.Bitmap;
import net.murat.math.*;

/**
 * Image-based texture with spherical mapping. Android adaptation: Uses Bitmap + int[] for fast,
 * GC-free sampling.
 */
public class ImageTexture {
  private final Bitmap image;

  // Cached pixel array for fast, GC-free sampling during render
  private final int[] imagePixels;
  private final int imageWidth;
  private final int imageHeight;

  private final double scaleU;
  private final double scaleV;
  private final double offsetU;
  private final double offsetV;

  // Pre-allocated temp vector
  private final Vector3 tempNormal = new Vector3();

  public ImageTexture(Bitmap image, double scale) {
    this(image, scale, scale, 0.0, 0.0);
  }

  public ImageTexture(Bitmap image, double scaleU, double scaleV, double offsetU, double offsetV) {
    this.image = image;
    this.scaleU = scaleU;
    this.scaleV = scaleV;
    this.offsetU = offsetU;
    this.offsetV = offsetV;

    if (image != null) {
      this.imageWidth = image.getWidth();
      this.imageHeight = image.getHeight();
      this.imagePixels = new int[imageWidth * imageHeight];
      image.getPixels(this.imagePixels, 0, imageWidth, 0, 0, imageWidth, imageHeight);
    } else {
      this.imageWidth = 0;
      this.imageHeight = 0;
      this.imagePixels = null;
    }
  }

  /** Gets color from UV coordinates - ZERO ALLOCATION Returns packed ARGB int (0xAARRGGBB) */
  public int getColorFromUV(double u, double v) {
    if (imagePixels == null) {
      return 0xFF000000;
    }

    u = u * scaleU + offsetU;
    v = v * scaleV + offsetV;

    int imgX = floorMod((int) (u * imageWidth), imageWidth);
    int imgY = floorMod((int) (v * imageHeight), imageHeight);

    imgX = Math.max(0, Math.min(imageWidth - 1, imgX));
    imgY = Math.max(0, Math.min(imageHeight - 1, imgY));

    return imagePixels[imgY * imageWidth + imgX];
  }

  /**
   * Gets color from 3D point with spherical mapping - ZERO ALLOCATION Returns packed ARGB int
   * (0xAARRGGBB)
   */
  public int getColorFrom3DPoint(Point3 point, Vector3 normal) {
    if (imagePixels == null) {
      return 0xFF000000;
    }

    tempNormal.set(normal);
    tempNormal.normalize();

    double theta = Math.acos(Math.max(-1.0, Math.min(1.0, tempNormal.y)));
    double phi = Math.atan2(tempNormal.z, tempNormal.x);
    if (phi < 0) phi += 2 * Math.PI;

    double u = phi / (2 * Math.PI);
    double v = theta / Math.PI;

    return getColorFromUV(u, v);
  }

  /** Java 8 compatible floorMod */
  private static int floorMod(int x, int y) {
    int mod = x % y;
    if (mod < 0) {
      mod += y;
    }
    return mod;
  }

  public int getWidth() {
    return imageWidth;
  }

  public int getHeight() {
    return imageHeight;
  }

  public boolean isValid() {
    return imagePixels != null;
  }

  public Bitmap getBitmap() {
    return image;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("ImageTexture imagetexture {\n");
    sb.append("        scaleU = ").append(scaleU).append(";\n");
    sb.append("        scaleV = ").append(scaleV).append(";\n");
    sb.append("        offsetU = ").append(offsetU).append(";\n");
    sb.append("        offsetV = ").append(offsetV).append(";\n");
    sb.append("    }");
    return sb.toString();
  }
}
