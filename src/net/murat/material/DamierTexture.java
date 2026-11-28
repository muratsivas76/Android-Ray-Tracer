package net.murat.material;

import net.murat.math.Point3;
import net.murat.util.ColorUtil;

/**
 * DamierTexture is a procedural checkerboard/damier texture that alternates between two colors
 * based on UV coordinates and a scale factor.
 *
 * <p>This follows the Raja Ray Tracer's Damier texture implementation where the pattern is
 * determined by: (floor(u * scale + offset) + floor(v * scale + offset)) % 2 == 0
 *
 * <p>This implementation is 100% garbage-free using packed ARGB integers.
 *
 * <p>Example usage:
 *
 * <pre>
 * // Black and white checkerboard with scale 2.0
 * RajaTexture checker = new DamierTexture(0xFFFFFFFF, 0xFF000000, 2.0, 0.0);
 * </pre>
 *
 * @author Murat Ray Tracer (Raja inspired)
 * @version 1.0
 * @see RajaTexture
 * @see PlainTexture
 */
public class DamierTexture implements RajaTexture {

  // =========================================================================
  // FIELDS
  // =========================================================================

  /** First color of the checkerboard pattern (packed ARGB). */
  private final int textureColor;

  /** Second color of the checkerboard pattern (packed ARGB). */
  private final int checkerColor;

  /** Scale factor controlling the size of checkerboard squares. */
  private final double textureScale;

  /** Offset value shifting the pattern along both axes. */
  private final double textureOffset;

  // =========================================================================
  // CONSTRUCTORS
  // =========================================================================

  /**
   * Creates a DamierTexture with the specified colors, scale, and offset.
   *
   * @param color1 First color as packed ARGB (0xAARRGGBB)
   * @param color2 Second color as packed ARGB (0xAARRGGBB)
   * @param scale Scale factor (larger = smaller squares, must be > 0)
   * @param offset Offset shifting the pattern (0.0 = no shift)
   */
  public DamierTexture(int color1, int color2, double scale, double offset) {
    this.textureColor = color1 | 0xFF000000; // Ensure alpha is fully opaque
    this.checkerColor = color2 | 0xFF000000;
    this.textureScale = Math.max(0.001, scale);
    this.textureOffset = offset;
  }

  /**
   * Creates a DamierTexture with default offset (0.0).
   *
   * @param color1 First color as packed ARGB
   * @param color2 Second color as packed ARGB
   * @param scale Scale factor (larger = smaller squares)
   */
  public DamierTexture(int color1, int color2, double scale) {
    this(color1, color2, scale, 0.0);
  }

  // =========================================================================
  // RAJA TEXTURE INTERFACE METHODS
  // =========================================================================

  /**
   * Samples the damier texture at the given surface point with UV coordinates.
   *
   * <p>Uses the formula:
   *
   * <pre>
   * if ((floor(u * scale + offset) + floor(v * scale + offset)) % 2 == 0)
   *     return color1;
   * else
   *     return color2;
   * </pre>
   *
   * @param hitPoint The surface point (world coordinates, used for 3D mapping)
   * @param u The U texture coordinate (0.0 - 1.0 or any range)
   * @param v The V texture coordinate (0.0 - 1.0 or any range)
   * @return Packed ARGB color based on checkerboard pattern
   */
  @Override
  public int sample(Point3 hitPoint, double u, double v) {
    // Scale and offset the UV coordinates
    double uScaled = u * textureScale + textureOffset;
    double vScaled = v * textureScale + textureOffset;

    // Floor both values and sum them
    int uInt = (int) Math.floor(uScaled);
    int vInt = (int) Math.floor(vScaled);
    int sum = uInt + vInt;

    // Check if sum is even
    if ((sum & 1) == 0) {
      return textureColor;
    } else {
      return checkerColor;
    }
  }

  // =========================================================================
  // GETTERS
  // =========================================================================

  /**
   * Returns the first color of the checkerboard pattern.
   *
   * @return First color as packed ARGB
   */
  public int getColor1() {
    return textureColor;
  }

  /**
   * Returns the second color of the checkerboard pattern.
   *
   * @return Second color as packed ARGB
   */
  public int getColor2() {
    return checkerColor;
  }

  /**
   * Returns the scale factor of the checkerboard pattern.
   *
   * @return Scale factor
   */
  public double getScale() {
    return textureScale;
  }

  /**
   * Returns the offset of the checkerboard pattern.
   *
   * @return Offset value
   */
  public double getOffset() {
    return textureOffset;
  }

  // =========================================================================
  // TO STRING
  // =========================================================================

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("textureType = damier|plain|photo;\n");
    sb.append("        textureColor = ")
        .append(ColorUtil.toColorString(textureColor))
        .append(";\n");
    sb.append("        checkerColor = ")
        .append(ColorUtil.toColorString(checkerColor))
        .append(";\n");
    sb.append("        textureScale = ").append(textureScale).append(";\n");
    sb.append("        textureOffset = ").append(textureOffset).append(";\n");
    sb.append("        imagePath = \"textures/turkey.png\"");
    return sb.toString();
  }
}
