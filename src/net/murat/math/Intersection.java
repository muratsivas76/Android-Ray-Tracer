package net.murat.math;

import net.murat.shape.EMShape;
import net.murat.material.Material;

/**
 * Represents the geometric hit data when a ray intersects an object. Fully optimized to be reused
 * in performance-critical loops without object allocation.
 */
public class Intersection {
  // Performance Update: Removed 'final' to allow reusing a single instance.
  // The Point3 and Vector3 fields are pre-allocated inside the constructor to avoid garbage
  // collection overhead.
  private final Point3 point; // Intersection point (internal structure is mutable)
  private final Vector3
      normal; // Normal vector at intersection point (internal structure is mutable)
  private double distance; // Distance from ray origin to intersection point (t parameter)
  private EMShape shape; // Intersected object reference
  private double u = 0.5; // UV coordinate U
  private double v = 0.5; // UV coordinate V
  private Material material;  // Per-hit material override (used by CSG). null = use shape's material.
  
  /**
   * No-arg constructor specifically designed to pre-allocate an empty intersection record inside
   * static context pools or material scopes.
   */
  public Intersection() {
    this.point = new Point3();
    this.normal = new Vector3();
    this.distance = Double.POSITIVE_INFINITY;
    this.shape = null;
  }

  /**
   * Constructs an Intersection with initial data. Allocates deep mutable structures for safe
   * internal tracking.
   */
  public Intersection(Point3 point, Vector3 normal, double distance, EMShape shape) {
    this.point = new Point3(point.x, point.y, point.z);
    this.normal = new Vector3(normal.x, normal.y, normal.z);
    this.distance = distance;
    this.shape = shape;
  }

  /**
   * PERFORMANCE METHOD: Updates all fields of this existing intersection instance in-place. This
   * eliminates the need for calling 'new Intersection(...)' in hot rendering loops.
   */
  public Intersection set(Point3 point, Vector3 normal, double distance, EMShape shape) {
    this.point.set(point);
    this.normal.set(normal);
    this.distance = distance;
    this.shape = shape;
    this.u = 0.5; // Reset default UV coordinates
    this.v = 0.5;
    this.material = null;
    return this;
  }

  /** PERFORMANCE METHOD: Copies the hit information from another intersection instance. */
  public Intersection set(Intersection other) {
    this.point.set(other.point);
    this.normal.set(other.normal);
    this.distance = other.distance;
    this.shape = other.shape;
    this.u = other.u;
    this.v = other.v;
    return this;
  }

  /** PERFORMANCE METHOD: Resets the intersection data back to an unhit/infinite state. */
  public void reset() {
    this.distance = Double.POSITIVE_INFINITY;
    this.shape = null;
    this.u = 0.5;
    this.v = 0.5;
    this.material = null;
  }

  public Point3 getPoint() {
    return point;
  }

  public Vector3 getNormal() {
    return normal;
  }

  public double getDistance() {
    return distance;
  }

  /**
   * Returns the distance of the intersection point from the ray origin ('t' parameter). Returns the
   * same value as getDistance().
   *
   * @return Intersection distance (t value).
   */
  public double getT() {
    return distance;
  }

  public EMShape getShape() {
    return shape;
  }

  // UV getter methods
  public double getU() {
    return u;
  }

  public double getV() {
    return v;
  }

  // UV setter method
  public void setUVCoordinates(double u, double v) {
    this.u = u;
    this.v = v;
  }
  
  public Material getMaterial() {
    return material;
  }

  public void setMaterial(Material material) {
    this.material = material;
  }
  
  @Override
  public String toString() {
    return "Intersection{"
        + "point="
        + point
        + ", normal="
        + normal
        + ", distance="
        + distance
        + ", shape="
        + (shape != null ? shape.getClass().getSimpleName() : "null")
        + '}';
  }
}
