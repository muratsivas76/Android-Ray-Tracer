package net.murat.math;

import java.util.Random;

/**
 * Represents a point in 3D space with x, y, z coordinates.
 *
 * <p>Design goal: zero garbage in hot paths (ray tracing inner loops). Every method that can mutate
 * {@code this} and return {@code this} does so. Methods that are documented as {@code ALLOCATES}
 * are kept only because their signature has no pre-allocated target parameter to write into; they
 * are not meant to be called from per-pixel / per-ray hot paths. Prefer the GC-friendly overload
 * (the one that takes a {@code target} parameter) wherever one is available.
 */
public class Point3 {

  public double x, y, z;

  // Conceptually a constant, but the backing instance is mutable.
  // Do NOT modify this global constant's fields!
  public static final Point3 ORIGIN = new Point3(0, 0, 0);

  // =========================================================================
  // CONSTRUCTORS
  // =========================================================================

  /** No-arg constructor - initializes to (0, 0, 0). Used for pre-allocation in scratch pads. */
  public Point3() {
    this.x = 0.0;
    this.y = 0.0;
    this.z = 0.0;
  }

  public Point3(double x, double y, double z) {
    this.x = x;
    this.y = y;
    this.z = z;
  }

  /**
   * Copy constructor - creates a new point by copying another. ALLOCATES - this is a constructor,
   * allocation is unavoidable here. Prefer {@link #set(Point3)} on a pre-allocated instance in hot
   * paths.
   */
  public Point3(Point3 other) {
    if (other != null) {
      this.x = other.x;
      this.y = other.y;
      this.z = other.z;
    } else {
      this.x = 0.0;
      this.y = 0.0;
      this.z = 0.0;
    }
  }

  // =========================================================================
  // SETTERS (Fluent, GC-Friendly)
  // =========================================================================

  /**
   * Sets this point's components (mutates this).
   *
   * @return this for method chaining
   */
  public Point3 set(double x, double y, double z) {
    this.x = x;
    this.y = y;
    this.z = z;
    return this;
  }

  /**
   * Copies values from another Point3 into this (mutates this).
   *
   * @return this for method chaining
   */
  public Point3 set(Point3 other) {
    this.x = other.x;
    this.y = other.y;
    this.z = other.z;
    return this;
  }

  /**
   * Copies values from a Vector3 into this point (mutates this).
   *
   * @return this for method chaining
   */
  public Point3 set(Vector3 vector) {
    this.x = vector.x;
    this.y = vector.y;
    this.z = vector.z;
    return this;
  }

  // =========================================================================
  // GETTERS
  // =========================================================================

  public double get(int axis) {
    switch (axis) {
      case 0:
        return x;
      case 1:
        return y;
      case 2:
        return z;
      default:
        throw new IllegalArgumentException("Axis must be 0, 1 or 2");
    }
  }

  public double getX() {
    return this.x;
  }

  public double getY() {
    return this.y;
  }

  public double getZ() {
    return this.z;
  }

  // =========================================================================
  // CORE MATH - MUTATING / SCALAR-RETURNING (GC-FRIENDLY)
  // =========================================================================

  public double length() {
    return Math.sqrt(x * x + y * y + z * z);
  }

  public double lengthSquared() {
    return x * x + y * y + z * z;
  }

  /**
   * Calculates the Euclidean distance between this point and another. Scalar result - no
   * allocation. Does NOT mutate.
   */
  public double distance(Point3 other) {
    double dx = this.x - other.x;
    double dy = this.y - other.y;
    double dz = this.z - other.z;
    return Math.sqrt(dx * dx + dy * dy + dz * dz);
  }

  /**
   * Dot product between this point and another point. Scalar result - no allocation. Does NOT
   * mutate.
   */
  public double dot(Point3 other) {
    return this.x * other.x + this.y * other.y + this.z * other.z;
  }

  /**
   * Dot product between this point and a vector. Scalar result - no allocation. Does NOT mutate.
   */
  public double dot(Vector3 vector) {
    return this.x * vector.x + this.y * vector.y + this.z * vector.z;
  }

  /**
   * Adds a Vector3 to this point (mutates this).
   *
   * @return this for method chaining
   */
  public Point3 add(Vector3 v) {
    this.x += v.x;
    this.y += v.y;
    this.z += v.z;
    return this;
  }

  /**
   * Adds another Point3 to this point (mutates this).
   *
   * @return this for method chaining
   */
  public Point3 add(Point3 p) {
    this.x += p.x;
    this.y += p.y;
    this.z += p.z;
    return this;
  }

  /**
   * Subtracts a Vector3 from this point (mutates this).
   *
   * @return this for method chaining
   */
  public Point3 subtract(Vector3 v) {
    this.x -= v.x;
    this.y -= v.y;
    this.z -= v.z;
    return this;
  }

  /**
   * Subtracts another Point3 from this point (mutates this).
   *
   * @return this for method chaining
   */
  public Point3 subtract(Point3 p) {
    this.x -= p.x;
    this.y -= p.y;
    this.z -= p.z;
    return this;
  }

  /**
   * Returns the vector from this point to another point: {@code other - this}.
   *
   * <p><b>ALLOCATES</b> a new Vector3 - signature has no target parameter, so allocation cannot be
   * avoided here. Use {@link #subtractToVector(Point3, Vector3)} in any hot path.
   *
   * <p><b>Semantics note (kept identical to the original implementation to avoid breaking existing
   * call sites):</b> despite the "subtract" name, the result points from {@code this} toward {@code
   * other} (i.e. {@code other - this}), not {@code this - other}. Callers that need a "toward the
   * camera" / "toward this point" direction should call it as {@code
   * target.subtractToVector(origin)} to get {@code origin - target}, or negate the result.
   *
   * @param other The other point
   * @return A new Vector3 representing (other - this)
   */
  public Vector3 subtractToVector(Point3 other) {
    return new Vector3(other.x - this.x, other.y - this.y, other.z - this.z);
  }

  /**
   * GC-Friendly version: stores the difference (other - this) in the target vector. No allocation.
   *
   * @param other The other point
   * @param target The vector to store the result
   * @return target for method chaining
   */
  public Vector3 subtractToVector(Point3 other, Vector3 target) {
    target.x = other.x - this.x;
    target.y = other.y - this.y;
    target.z = other.z - this.z;
    return target;
  }

  /**
   * Converts this point to a Vector3.
   *
   * <p><b>ALLOCATES</b> a new Vector3 - signature has no target parameter. Use {@link
   * #toVector(Vector3)} in any hot path.
   *
   * @return A new Vector3 with the same x, y, z coordinates
   */
  public Vector3 toVector() {
    return new Vector3(this.x, this.y, this.z);
  }

  /**
   * Returns a new Point3 = this - other (component-wise). <b>ALLOCATES</b> - signature has no
   * target parameter.
   */
  public Point3 minus(Point3 other) {
    return new Point3(this.x - other.x, this.y - other.y, this.z - other.z);
  }

  /**
   * Returns a new Point3 = this + other (component-wise). <b>ALLOCATES</b> - signature has no
   * target parameter.
   */
  public Point3 plus(Point3 other) {
    return new Point3(this.x + other.x, this.y + other.y, this.z + other.z);
  }

  /** Returns a new Point3 = this * scalar. <b>ALLOCATES</b> - signature has no target parameter. */
  public Point3 times(double scalar) {
    return new Point3(this.x * scalar, this.y * scalar, this.z * scalar);
  }

  /**
   * Multiplies this point by a scalar (mutates this). No allocation.
   *
   * @return this for method chaining
   */
  public Point3 multiply(double scalar) {
    this.x *= scalar;
    this.y *= scalar;
    this.z *= scalar;
    return this;
  }

  /**
   * Normalizes this point (mutates this). No allocation. If length is zero, sets to (0, 0, 0).
   *
   * @return this for method chaining
   */
  public Point3 normalize() {
    double len = length();
    if (len == 0) {
      this.x = 0.0;
      this.y = 0.0;
      this.z = 0.0;
      return this;
    }
    this.x /= len;
    this.y /= len;
    this.z /= len;
    return this;
  }

  /**
   * Normalizes this point (mutates this). No allocation. If length is zero, sets to (0, 1, 0) as a
   * safe default.
   *
   * @return this for method chaining
   */
  public Point3 normalizeSafe() {
    double len = length();
    if (len == 0) {
      this.x = 0.0;
      this.y = 1.0;
      this.z = 0.0;
      return this;
    }
    this.x /= len;
    this.y /= len;
    this.z /= len;
    return this;
  }

  /**
   * Returns a new Point3 with each component multiplied by scalar. <b>ALLOCATES</b> - signature has
   * no target parameter. Prefer {@link #multiply(double)} in any hot path.
   *
   * @param scalar The scaling factor
   * @return A new Point3 = this * scalar
   */
  public Point3 scale(double scalar) {
    return new Point3(this.x * scalar, this.y * scalar, this.z * scalar);
  }

  /**
   * Linear interpolation between two points (mutates this). No allocation. this = a + (b - a) * t
   *
   * @return this for method chaining
   */
  public Point3 lerp(Point3 a, Point3 b, double t) {
    t = Math.max(0.0, Math.min(1.0, t));
    this.x = a.x + (b.x - a.x) * t;
    this.y = a.y + (b.y - a.y) * t;
    this.z = a.z + (b.z - a.z) * t;
    return this;
  }

  /**
   * Linear interpolation from a point in a direction (mutates this). No allocation. this = p + v *
   * t
   *
   * @return this for method chaining
   */
  public Point3 lerp(Point3 p, Vector3 v, double t) {
    t = Math.max(0.0, Math.min(1.0, t));
    this.x = p.x + v.x * t;
    this.y = p.y + v.y * t;
    this.z = p.z + v.z * t;
    return this;
  }

  /**
   * Sets this point as the difference between two points: a - b (mutates this). No allocation.
   *
   * @return this for method chaining
   */
  public Point3 difference(Point3 a, Point3 b) {
    this.x = a.x - b.x;
    this.y = a.y - b.y;
    this.z = a.z - b.z;
    return this;
  }

  /**
   * Sets this point as the sum of two points: a + b (mutates this). No allocation.
   *
   * @return this for method chaining
   */
  public Point3 sum(Point3 a, Point3 b) {
    this.x = a.x + b.x;
    this.y = a.y + b.y;
    this.z = a.z + b.z;
    return this;
  }

  /**
   * Generates a random point in a unit sphere (mutates this). No allocation.
   *
   * @return this for method chaining
   */
  public Point3 randomInUnitSphere(Random rand) {
    do {
      this.x = rand.nextDouble() * 2.0 - 1.0;
      this.y = rand.nextDouble() * 2.0 - 1.0;
      this.z = rand.nextDouble() * 2.0 - 1.0;
    } while (this.lengthSquared() >= 1.0);
    return this;
  }

  /**
   * Converts this point to a Vector3 (mutates the target). No allocation.
   *
   * @param target The vector instance that will store the result
   * @return The target vector for method chaining
   */
  public Vector3 toVector(Vector3 target) {
    target.x = this.x;
    target.y = this.y;
    target.z = this.z;
    return target;
  }

  // =========================================================================
  // COPY
  // =========================================================================

  /**
   * Copies this point into target (mutates target, not this). No allocation.
   *
   * @return target for method chaining
   */
  public Point3 copy(Point3 target) {
    target.x = this.x;
    target.y = this.y;
    target.z = this.z;
    return target;
  }

  /**
   * Returns a new Point3 (ALLOCATES). Kept only for compatibility with legacy code; avoid in hot
   * paths.
   */
  public Point3 clone() {
    return new Point3(this.x, this.y, this.z);
  }

  // =========================================================================
  // TO STRING
  // =========================================================================

  @Override
  public String toString() {
    return String.format(java.util.Locale.US, "P(%.2f, %.2f, %.2f)", x, y, z);
  }
}
