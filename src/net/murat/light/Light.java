package net.murat.light;

import net.murat.lovert.Scene;
import net.murat.math.Point3;
import net.murat.math.Vector3;

/**
 * Represents a light source within the ray tracer. Optimized to eliminate object allocation by
 * using packed integer ARGB colors. Compatible with Java 6-8 without lambdas.
 *
 * <p>Animation support: - Position animation via delta values (x, y, z) - Color animation via delta
 * values (alpha, red, green, blue) - Two-phase animation (increase then decrease)
 */
public interface Light {

  // ============================================================
  // POSITION
  // ============================================================

  /**
   * Returns the position of the light source.
   *
   * @return The 3D point of the light.
   */
  Point3 getPosition();

  /**
   * Sets the position of the light source. Used for animation updates.
   *
   * @param position New 3D position.
   */
  void setPosition(Point3 position);

  // ============================================================
  // COLOR
  // ============================================================

  /**
   * Returns the color of the light as a packed ARGB integer. Format: (A &lt;&lt; 24) | (R &lt;&lt;
   * 16) | (G &lt;&lt; 8) | B
   *
   * @return Packed ARGB color value.
   */
  int getColor();

  /**
   * Sets the color of the light as a packed ARGB integer. Used for animation updates.
   *
   * @param color Packed ARGB color value (0xAARRGGBB)
   */
  void setColor(int color);

  // ============================================================
  // INTENSITY
  // ============================================================

  /**
   * Sets the intensity of the light.
   *
   * @param intensity New intensity value.
   */
  void setIntensity(double intensity);

  /**
   * Returns the current intensity of the light.
   *
   * @return Intensity value.
   */
  double getIntensity();

  /**
   * Sets intensity animation delta values.
   *
   * @param delta Array of two doubles: {increase_per_frame, decrease_per_frame}
   */
  void setIncDecIntensity(double[] delta);

  /**
   * Returns intensity animation delta values.
   *
   * @return Array of two doubles: {increase_per_frame, decrease_per_frame}
   */
  double[] getIncDecIntensity();

  // ============================================================
  // DIRECTION & LIGHTING CALCULATIONS
  // ============================================================

  /**
   * Returns the light direction at a given surface point.
   *
   * @param point The surface point.
   * @return Direction vector from point to light.
   */
  Vector3 getDirectionAt(Point3 point);

  /**
   * Returns the attenuated intensity at a given surface point.
   *
   * @param point The surface point.
   * @return Attenuated intensity value.
   */
  double getAttenuatedIntensity(Point3 point);

  /**
   * Returns the raw intensity at a given surface point.
   *
   * @param point The surface point.
   * @return Intensity value.
   */
  double getIntensityAt(Point3 point);

  /**
   * Returns the direction to the light from a given point.
   *
   * @param point The surface point.
   * @return Direction vector.
   */
  Vector3 getDirectionTo(Point3 point);

  // ============================================================
  // VISIBILITY
  // ============================================================

  /**
   * Checks if the light is visible from a given point.
   *
   * @param point The surface point.
   * @param scene The scene containing objects.
   * @return true if visible, false otherwise.
   */
  boolean isVisibleFrom(Point3 point, Scene scene);

  // ============================================================
  // IDENTIFICATION
  // ============================================================

  /**
   * Returns the name of the light.
   *
   * @return Light name.
   */
  String getName();

  /**
   * Sets the name of the light.
   *
   * @param name Light name.
   */
  void setName(String name);

  // ============================================================
  // POSITION ANIMATION
  // ============================================================

  /**
   * Sets position animation delta values. First half: position increases by these values each
   * frame. Second half: position decreases by these values each frame.
   *
   * @param delta Array of three doubles: {x_delta, y_delta, z_delta}
   */
  void setPositionDelta(double[] delta);

  /**
   * Returns position animation delta values.
   *
   * @return Array of three doubles: {x_delta, y_delta, z_delta}
   */
  double[] getPositionDelta();

  // ============================================================
  // COLOR ANIMATION
  // ============================================================

  /**
   * Sets color animation delta values. First half: color channels increase by these values each
   * frame. Second half: color channels decrease by these values each frame. All delta values are
   * automatically clamped to 0-255 range during update.
   *
   * @param delta Array of four ints: {alpha_delta, red_delta, green_delta, blue_delta}
   */
  void setColorDelta(int[] delta);

  /**
   * Returns color animation delta values.
   *
   * @return Array of four ints: {alpha_delta, red_delta, green_delta, blue_delta}
   */
  int[] getColorDelta();
}
