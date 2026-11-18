package net.murat.math;

/** Axis-aligned bounding box used for fast ray rejection and BVH acceleration. */
public class AABB {
  public double minX;
  public double minY;
  public double minZ;
  public double maxX;
  public double maxY;
  public double maxZ;

  public AABB() {
    this.minX = Double.POSITIVE_INFINITY;
    this.minY = Double.POSITIVE_INFINITY;
    this.minZ = Double.POSITIVE_INFINITY;
    this.maxX = Double.NEGATIVE_INFINITY;
    this.maxY = Double.NEGATIVE_INFINITY;
    this.maxZ = Double.NEGATIVE_INFINITY;
  }

  public AABB(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
    this.minX = minX;
    this.minY = minY;
    this.minZ = minZ;
    this.maxX = maxX;
    this.maxY = maxY;
    this.maxZ = maxZ;
  }

  /**
   * PERFORMANCE METHOD: Resets this AABB to an empty/initial state. Sets min values to +Infinity
   * and max values to -Infinity. Used for garbage-free reuse of AABB instances.
   *
   * @return This AABB instance for method chaining
   */
  public AABB reset() {
    this.minX = Double.POSITIVE_INFINITY;
    this.minY = Double.POSITIVE_INFINITY;
    this.minZ = Double.POSITIVE_INFINITY;
    this.maxX = Double.NEGATIVE_INFINITY;
    this.maxY = Double.NEGATIVE_INFINITY;
    this.maxZ = Double.NEGATIVE_INFINITY;
    return this;
  }

  /**
   * PERFORMANCE METHOD: Sets this AABB to the bounds of another AABB. Used for garbage-free
   * copying.
   *
   * @param other The AABB to copy from
   * @return This AABB instance for method chaining
   */
  public AABB set(AABB other) {
    this.minX = other.minX;
    this.minY = other.minY;
    this.minZ = other.minZ;
    this.maxX = other.maxX;
    this.maxY = other.maxY;
    this.maxZ = other.maxZ;
    return this;
  }

  /**
   * PERFORMANCE METHOD: Sets this AABB to specific bounds.
   *
   * @param minX Minimum X
   * @param minY Minimum Y
   * @param minZ Minimum Z
   * @param maxX Maximum X
   * @param maxY Maximum Y
   * @param maxZ Maximum Z
   * @return This AABB instance for method chaining
   */
  public AABB set(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
    this.minX = minX;
    this.minY = minY;
    this.minZ = minZ;
    this.maxX = maxX;
    this.maxY = maxY;
    this.maxZ = maxZ;
    return this;
  }

  public void expand(Point3 p) {
    if (p.x < minX) minX = p.x;
    if (p.y < minY) minY = p.y;
    if (p.z < minZ) minZ = p.z;
    if (p.x > maxX) maxX = p.x;
    if (p.y > maxY) maxY = p.y;
    if (p.z > maxZ) maxZ = p.z;
  }

  /**
   * Ray-box intersection test without a distance cutoff. Kept for backward compatibility; delegates
   * to the tMax-aware overload.
   */
  public boolean intersect(Ray ray) {
    return intersect(ray, Double.MAX_VALUE);
  }

  /**
   * Ray-box intersection test with a distance cutoff (tMax). BVH traversal must use this overload:
   * it allows a node to be rejected not only when the ray misses the box outright, but also when
   * the box is entirely farther away than the closest hit already found. Without this cutoff, BVH
   * traversal degenerates into visiting every node whose box the ray happens to cross, regardless
   * of whether a closer hit already makes that node irrelevant.
   */
  public boolean intersect(Ray ray, double tMax) {
    double tMin = 0.0;
    double localTMax = tMax;

    double ox = ray.getOrigin().x;
    double oy = ray.getOrigin().y;
    double oz = ray.getOrigin().z;
    double dx = ray.getDirection().x;
    double dy = ray.getDirection().y;
    double dz = ray.getDirection().z;

    if (dx != 0.0) {
      double t1 = (minX - ox) / dx;
      double t2 = (maxX - ox) / dx;
      if (t1 > t2) {
        double temp = t1;
        t1 = t2;
        t2 = temp;
      }
      if (t1 > tMin) tMin = t1;
      if (t2 < localTMax) localTMax = t2;
      if (tMin > localTMax) return false;
    } else {
      if (ox < minX || ox > maxX) return false;
    }

    if (dy != 0.0) {
      double t1 = (minY - oy) / dy;
      double t2 = (maxY - oy) / dy;
      if (t1 > t2) {
        double temp = t1;
        t1 = t2;
        t2 = temp;
      }
      if (t1 > tMin) tMin = t1;
      if (t2 < localTMax) localTMax = t2;
      if (tMin > localTMax) return false;
    } else {
      if (oy < minY || oy > maxY) return false;
    }

    if (dz != 0.0) {
      double t1 = (minZ - oz) / dz;
      double t2 = (maxZ - oz) / dz;
      if (t1 > t2) {
        double temp = t1;
        t1 = t2;
        t2 = temp;
      }
      if (t1 > tMin) tMin = t1;
      if (t2 < localTMax) localTMax = t2;
      if (tMin > localTMax) return false;
    } else {
      if (oz < minZ || oz > maxZ) return false;
    }

    return true;
  }

  @Override
  public String toString() {
    return String.format(
        "AABB[min=(%.2f,%.2f,%.2f), max=(%.2f,%.2f,%.2f)]", minX, minY, minZ, maxX, maxY, maxZ);
  }
}
