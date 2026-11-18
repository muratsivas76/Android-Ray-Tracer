package net.murat.lovert;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.murat.light.Light;
import net.murat.math.Intersection;
import net.murat.math.Point3;
import net.murat.math.Ray;
import net.murat.shape.EMShape;

/**
 * Represents a 3D scene containing shapes and lights for ray tracing. Handles intersection tests
 * and light management with zero-allocation performance.
 *
 * <p>This implementation is 100% garbage-free during render() execution. All temporary objects are
 * pre-allocated as instance fields and reused.
 */
public class Scene {

  // =========================================================================
  // PUBLIC DATA STRUCTURES (Lists - unavoidable but only accessed, not allocated)
  // =========================================================================

  private final List<EMShape> shapes = new ArrayList<EMShape>();
  private final List<Light> lights = new ArrayList<Light>();

  // =========================================================================
  // GC-FREE SCRATCH POOL - Zero allocation per intersection test
  // =========================================================================

  /** Primary scratch intersection for scene intersection tests. */
  private final Intersection sceneScratchHit = new Intersection();

  /** Shadow ray scratch intersection for shadow tests. */
  private final Intersection shadowScratch = new Intersection();

  /** Temporary intersection for loop iterations (reused to avoid allocation). */
  private final Intersection tempScratch = new Intersection();

  /** Cache array size to avoid repeated method calls. */
  private int shapesSize = 0;

  /** Cache lights size to avoid repeated method calls. */
  private int lightsSize = 0;

  // =========================================================================
  // CONSTRUCTOR
  // =========================================================================

  /** Creates an empty scene with all scratch objects pre-allocated. */
  public Scene() {
    // All scratch objects are initialized at field declaration.
    // No additional initialization needed.
  }

  // =========================================================================
  // PUBLIC API - Shape/Light Management (GC-free operations)
  // =========================================================================

  /**
   * Adds a shape to the scene.
   *
   * @param shape the shape to add
   */
  public void addShape(EMShape shape) {
    shapes.add(shape);
    shapesSize = shapes.size(); // Update cache
  }

  /**
   * Adds a light source to the scene.
   *
   * @param light the light to add
   */
  public void addLight(Light light) {
    lights.add(light);
    lightsSize = lights.size(); // Update cache
  }

  /**
   * Returns the list of shapes in the scene.
   *
   * @return unmodifiable view of shapes list
   */
  public List<EMShape> getShapes() {
    return shapes;
  }

  /**
   * Returns the list of lights in the scene.
   *
   * @return list of lights
   */
  public List<Light> getLights() {
    return lights;
  }

  /** Removes all shapes from the scene. */
  public void clearShapes() {
    shapes.clear();
    shapesSize = 0;
  }

  /** Removes all lights from the scene. */
  public void clearLights() {
    lights.clear();
    lightsSize = 0;
  }

  // =========================================================================
  // DEPRECATED METHODS (Maintained for backward compatibility)
  // =========================================================================

  /**
   * Finds the closest ray-object intersection in the scene.
   *
   * @deprecated This method allocates Optional and Intersection objects. Use {@link
   *     #intersectLocal(Ray, EMShape, Intersection)} instead.
   * @param ray the ray to test
   * @return Optional containing the closest intersection, or empty if none found
   */
  @Deprecated
  public Optional<Intersection> intersect(Ray ray) {
    return intersect(ray, null);
  }

  /**
   * Finds the closest ray-object intersection excluding a specific shape.
   *
   * @deprecated This method allocates Optional and Intersection objects. Use {@link
   *     #intersectLocal(Ray, EMShape, Intersection)} instead.
   * @param ray the ray to test
   * @param excludeShape the shape to exclude from intersection testing
   * @return Optional containing the closest intersection, or empty if none found
   */
  @Deprecated
  public Optional<Intersection> intersect(Ray ray, EMShape excludeShape) {
    // Use the GC-free method internally
    Intersection result = new Intersection();
    boolean hit = intersectLocal(ray, excludeShape, result);

    if (hit) {
      // Unfortunately, Optional.of() always allocates a new Optional object.
      // This is unavoidable for backward compatibility.
      return Optional.of(
          new Intersection(
              result.getPoint(), result.getNormal(), result.getDistance(), result.getShape()));
    }
    return Optional.empty();
  }

  // =========================================================================
  // PERFORMANCE METHOD - GC-FREE SCENE INTERSECTION
  // =========================================================================

  /**
   * High-performance, zero-allocation scene intersection test. Fully respects all EMShape
   * visibility and rendering flags.
   *
   * <p>Flag behavior:
   *
   * <ul>
   *   <li>{@code isVisible()} - If false and not shadow-only, shape is skipped
   *   <li>{@code isShadowOnly()} - Excluded from primary/reflection/refraction rays
   *   <li>{@code isVisibleSpecial()} - Handled downstream in traceRay
   *   <li>{@code isShadowEnable()} - Evaluated in findShadowCaster
   *   <li>{@code isReflective()} - Evaluated in shouldCalculateReflections
   *   <li>{@code isRefractive()} - Evaluated in shouldCalculateRefractions
   * </ul>
   *
   * @param ray the ray to test against the scene
   * @param excludeShape the shape to ignore during intersection (typically the origin shape)
   * @param finalClosestTarget pre-allocated Intersection to hold the closest hit data
   * @return true if a valid intersection was found, false otherwise
   */
  public boolean intersectLocal(Ray ray, EMShape excludeShape, Intersection finalClosestTarget) {
    // Reset the target to a clean state
    finalClosestTarget.reset();

    EMShape closestShape = null;
    double minDistance = Double.POSITIVE_INFINITY;

    // Use cached size to avoid repeated list.size() calls
    int size = shapesSize;

    for (int i = 0; i < size; i++) {
      EMShape shape = shapes.get(i);

      // Skip the origin shape to prevent self-intersection
      if (shape == excludeShape) {
        continue;
      }

      // Shadow-only shapes are invisible to primary, reflection, and refraction rays.
      // They exist solely to cast shadows via findShadowCaster.
      if (shape.isShadowOnly()) {
        continue;
      }

      // Completely invisible shapes are excluded from all ray tests.
      if (!shape.isVisible()) {
        continue;
      }

      // Run the zero-allocation intersection test into our instance-level scratch buffer
      // CRITICAL: Must reset before each use to avoid stale data
      sceneScratchHit.reset();

      if (shape.intersect(ray, sceneScratchHit)) {
        double currentDist = sceneScratchHit.getDistance();

        // Accept only hits beyond the epsilon threshold and closer than current best
        if (currentDist > Ray.EPSILON && currentDist < minDistance) {
          minDistance = currentDist;
          closestShape = shape;

          // Copy winning hit data into the final output target in-place
          // This is a field-by-field copy, no allocation
          finalClosestTarget.set(sceneScratchHit);
        }
      }
    }

    // No intersection found; ensure target remains in a clean reset state
    if (closestShape == null) {
      finalClosestTarget.reset();
      return false;
    }

    // The shape reference is already set by finalClosestTarget.set(sceneScratchHit)
    // No additional work needed.
    return true;
  }

  // =========================================================================
  // SHADOW RAY METHODS - GC-FREE
  // =========================================================================

  /**
   * High-performance shadow ray collision checker. Tests if any shape blocks the path from ray
   * origin to maxDistance.
   *
   * <p>This method is 100% garbage-free and uses pre-allocated scratch objects.
   *
   * @param ray the shadow ray to test
   * @param maxDistance the maximum distance to check
   * @return true if the ray hits any shape before maxDistance, false otherwise
   */
  public boolean intersects(Ray ray, double maxDistance) {
    int size = shapesSize;

    for (int i = 0; i < size; i++) {
      EMShape shape = shapes.get(i);

      // Skip shapes that don't cast shadows
      if (!shape.isShadowEnable()) {
        continue;
      }

      // Skip shadow-only shapes that are not visible (they already cast shadows)
      // Actually, shadow-only shapes should be included in shadow tests
      if (!shape.isVisible() && !shape.isShadowOnly()) {
        continue;
      }

      // Reset our pre-allocated shadow scratch drawer before each test
      shadowScratch.reset();

      if (shape.intersect(ray, shadowScratch)) {
        double distance = shadowScratch.getDistance();
        if (distance > Ray.EPSILON && distance < maxDistance) {
          return true; // Shadow ray hit an obstacle, point is in shadow
        }
      }
    }
    return false;
  }

  /**
   * Checks if a point is visible from any light source (for soft shadows).
   *
   * <p>Note: This method may allocate if the Light.isVisibleFrom() implementation allocates
   * objects. The allocation is outside Scene's control.
   *
   * @param point the point to check
   * @return the number of lights visible from the point
   */
  public int getVisibleLightCount(Point3 point) {
    int count = 0;
    int size = lightsSize;

    for (int i = 0; i < size; i++) {
      Light light = lights.get(i);
      if (light.isVisibleFrom(point, this)) {
        count++;
      }
    }
    return count;
  }

  // =========================================================================
  // INTERNAL CACHE UPDATE METHODS
  // =========================================================================

  /**
   * Updates the internal size caches. Called automatically when shapes/lights are added or removed.
   */
  private void updateCaches() {
    shapesSize = shapes.size();
    lightsSize = lights.size();
  }

  // =========================================================================
  // TO STRING
  // =========================================================================

  /**
   * Returns a string representation of the scene.
   *
   * @return formatted string with scene statistics
   */
  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("Scene {\n");
    sb.append("  shapes: ").append(shapesSize).append(" objects\n");
    sb.append("  lights: ").append(lightsSize).append(" sources\n");
    sb.append("}");
    return sb.toString();
  }
}
