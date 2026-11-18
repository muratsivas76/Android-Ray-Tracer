package net.murat.material;

import net.murat.math.Point3;

/**
 * RajaTexture interface defines the contract for procedural textures inspired by the Raja Ray
 * Tracer. A texture provides a color at a given surface point with UV coordinates.
 *
 * <p>This interface follows the Raja design where textures are separate from materials, enabling
 * flexible composition. All implementations must be 100% garbage-free.
 *
 * <p>Key differences from standard Texture:
 *
 * <ul>
 *   <li>Explicitly designed for RajaMaterial compatibility
 *   <li>Supports both Plain and Damier texture types
 *   <li>Follows Raja's procedural texture philosophy
 * </ul>
 *
 * @author Murat Ray Tracer (Raja inspired)
 * @version 1.0
 * @see PlainTexture
 * @see DamierTexture
 */
public interface RajaTexture {

  /**
   * Samples the texture at the given surface point with UV coordinates.
   *
   * <p>This method must be 100% garbage-free - no object allocation.
   *
   * @param hitPoint The surface point in world or local space
   * @param u The U texture coordinate (typically 0.0 - 1.0)
   * @param v The V texture coordinate (typically 0.0 - 1.0)
   * @return Packed ARGB color (0xAARRGGBB) at this texture coordinate
   */
  int sample(Point3 hitPoint, double u, double v);
}
