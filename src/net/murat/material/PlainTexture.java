package net.murat.material;

import net.murat.math.Point3;
import net.murat.util.ColorUtil;

/**
 * PlainTexture is a simple texture that returns a single constant color regardless of the surface
 * position or UV coordinates.
 *
 * <p>This is the simplest implementation of RajaTexture, matching the Raja Ray Tracer's Plain
 * texture. It is fully garbage-free and uses packed ARGB integers for color representation.
 *
 * <p>Example usage:
 *
 * <pre>
 * RajaTexture redTexture = new PlainTexture(0xFFFF0000);
 * </pre>
 *
 * @author Murat Ray Tracer (Raja inspired)
 * @version 1.0
 * @see RajaTexture
 * @see DamierTexture
 */
public class PlainTexture implements RajaTexture {

  // =========================================================================
  // FIELDS
  // =========================================================================

  /** The constant color returned by this texture (packed ARGB). */
  private final int color;

  // =========================================================================
  // CONSTRUCTORS
  // =========================================================================

  /**
   * Creates a PlainTexture with the specified constant color.
   *
   * @param color The constant color as packed ARGB (0xAARRGGBB)
   */
  public PlainTexture(int color) {
    this.color = color | 0xFF000000; // Ensure alpha is fully opaque
  }

  // =========================================================================
  // RAJA TEXTURE INTERFACE METHODS
  // =========================================================================

  /**
   * Samples the texture at the given surface point.
   *
   * <p>For PlainTexture, this always returns the same constant color regardless of the input
   * parameters.
   *
   * @param hitPoint The surface point (ignored for plain texture)
   * @param u The U texture coordinate (ignored)
   * @param v The V texture coordinate (ignored)
   * @return The constant color as packed ARGB
   */
  @Override
  public int sample(Point3 hitPoint, double u, double v) {
    return color;
  }

  // =========================================================================
  // GETTERS
  // =========================================================================

  /**
   * Returns the constant color of this texture.
   *
   * @return The color as packed ARGB
   */
  public int getColor() {
    return color;
  }

  // =========================================================================
  // TO STRING
  // =========================================================================

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("textureType = plain|damier|photo;\n");
    sb.append("        textureColor = ").append(ColorUtil.toColorString(color)).append(";\n");
    sb.append("        checkerColor = ").append("0x00000000").append(";\n");
    sb.append("        textureScale = ").append("2.0").append(";\n");
    sb.append("        textureOffset = ").append("0.0").append(";\n");
    sb.append("        imagePath = \"textures/turkey.png\"");
    return sb.toString();
  }
}
