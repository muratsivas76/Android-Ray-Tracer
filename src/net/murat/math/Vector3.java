package net.murat.math;

import java.util.Random;

/**
 * Represents a direction/displacement vector in 3D space with x, y, z components.
 *
 * <p>Design goal: zero garbage in hot paths (ray tracing inner loops). Every method that can mutate
 * {@code this} and return {@code this} does so. The only two methods that are documented as {@code
 * ALLOCATES} are {@link #times(double)} and {@link #clone()}: their signatures have no
 * pre-allocated target parameter to write into, so allocation cannot be avoided without changing
 * their signature. Prefer {@link #multiply(double)} over {@code times(double)} in any hot path.
 */
public class Vector3 {

  public double x, y, z;

  public static final Vector3 ZERO = new Vector3(0, 0, 0);
  public static final Vector3 ONE = new Vector3(1, 1, 1);

  // =========================================================================
  // CONSTRUCTORS
  // =========================================================================

  public Vector3() {
    this.x = 0.0;
    this.y = 0.0;
    this.z = 0.0;
  }

  public Vector3(double x, double y, double z) {
    this.x = x;
    this.y = y;
    this.z = z;
  }

  /**
   * Constructs the vector from point {@code from} to point {@code to}: {@code to - from}. This is a
   * constructor, so allocation is unavoidable here; prefer {@link Point3#subtractToVector(Point3,
   * Vector3)} or {@link #set(Point3, Point3)} on a pre-allocated instance in hot paths.
   */
  public Vector3(Point3 from, Point3 to) {
    this.x = to.x - from.x;
    this.y = to.y - from.y;
    this.z = to.z - from.z;
  }

  // =========================================================================
  // SETTERS (Fluent, GC-Friendly)
  // =========================================================================

  public Vector3 set(double x, double y, double z) {
    this.x = x;
    this.y = y;
    this.z = z;
    return this;
  }

  public Vector3 set(Vector3 other) {
    this.x = other.x;
    this.y = other.y;
    this.z = other.z;
    return this;
  }

  public Vector3 set(Point3 point) {
    this.x = point.x;
    this.y = point.y;
    this.z = point.z;
    return this;
  }

  /**
   * Sets this vector to the difference of two points: a - b.
   *
   * @return this for method chaining
   */
  public Vector3 set(Point3 a, Point3 b) {
    this.x = a.x - b.x;
    this.y = a.y - b.y;
    this.z = a.z - b.z;
    return this;
  }

  /**
   * Sets this vector to the difference of two vectors: a - b.
   *
   * @return this for method chaining
   */
  public Vector3 set(Vector3 a, Vector3 b) {
    this.x = a.x - b.x;
    this.y = a.y - b.y;
    this.z = a.z - b.z;
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
  // CORE MATH - ALL MUTATING (GC-FRIENDLY)
  // =========================================================================

  public double length() {
    return Math.sqrt(x * x + y * y + z * z);
  }

  public double lengthSquared() {
    return x * x + y * y + z * z;
  }

  public double dot(Vector3 other) {
    return x * other.x + y * other.y + z * other.z;
  }

  public Vector3 add(Vector3 other) {
    this.x += other.x;
    this.y += other.y;
    this.z += other.z;
    return this;
  }

  public Vector3 subtract(Vector3 other) {
    this.x -= other.x;
    this.y -= other.y;
    this.z -= other.z;
    return this;
  }

  public Vector3 subtract(Point3 other) {
    this.x -= other.x;
    this.y -= other.y;
    this.z -= other.z;
    return this;
  }

  /**
   * Multiplies this vector by a scalar (mutates this). No allocation. Semantically identical to
   * {@link #multiply(double)}; kept for API compatibility with existing call sites.
   *
   * @return this for method chaining
   */
  public Vector3 scale(double scalar) {
    this.x *= scalar;
    this.y *= scalar;
    this.z *= scalar;
    return this;
  }

  /**
   * Multiplies this vector by a scalar (mutates this). No allocation.
   *
   * @return this for method chaining
   */
  public Vector3 multiply(double t) {
    this.x *= t;
    this.y *= t;
    this.z *= t;
    return this;
  }

  /**
   * Component-wise multiplication (mutates this). No allocation.
   *
   * @return this for method chaining
   */
  public Vector3 times(Vector3 other) {
    this.x *= other.x;
    this.y *= other.y;
    this.z *= other.z;
    return this;
  }

  public Vector3 negate() {
    this.x = -this.x;
    this.y = -this.y;
    this.z = -this.z;
    return this;
  }

  public Vector3 normalize() {
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

  public Vector3 normalizeSafe() {
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
   * Cross product: this = this x other (mutates this). No allocation.
   *
   * @return this for method chaining
   */
  public Vector3 cross(Vector3 other) {
    double tx = this.y * other.z - this.z * other.y;
    double ty = this.z * other.x - this.x * other.z;
    double tz = this.x * other.y - this.y * other.x;
    this.x = tx;
    this.y = ty;
    this.z = tz;
    return this;
  }

  /**
   * Sets this vector to the cross product of a and b: this = a x b. Does not mutate a or b. No
   * allocation.
   *
   * @param a First vector
   * @param b Second vector
   * @return this for method chaining
   */
  public Vector3 setCross(Vector3 a, Vector3 b) {
    double tx = a.y * b.z - a.z * b.y;
    double ty = a.z * b.x - a.x * b.z;
    double tz = a.x * b.y - a.y * b.x;
    this.x = tx;
    this.y = ty;
    this.z = tz;
    return this;
  }

  /**
   * Cross product: computes this x other and stores in target (does not mutate this). No
   * allocation.
   *
   * @return target for method chaining
   */
  public Vector3 cross(Vector3 other, Vector3 target) {
    double tx = this.y * other.z - this.z * other.y;
    double ty = this.z * other.x - this.x * other.z;
    double tz = this.x * other.y - this.y * other.x;
    target.x = tx;
    target.y = ty;
    target.z = tz;
    return target;
  }

  /**
   * Returns a new Vector3 = this * scalar.
   *
   * <p><b>ALLOCATES</b> a new Vector3 - signature has no target parameter, so allocation cannot be
   * avoided here. Use {@link #multiply(double)} in any hot path.
   */
  public Vector3 times(double scalar) {
    return new Vector3(this.x * scalar, this.y * scalar, this.z * scalar);
  }

  /**
   * Linear interpolation toward another vector (mutates this). No allocation.
   *
   * @return this for method chaining
   */
  public Vector3 lerp(Vector3 other, double t) {
    t = Math.max(0.0, Math.min(1.0, t));
    this.x += (other.x - this.x) * t;
    this.y += (other.y - this.y) * t;
    this.z += (other.z - this.z) * t;
    return this;
  }

  /**
   * Angle between this vector and another, in radians. Scalar result - no allocation. Does NOT
   * mutate.
   */
  public double angleBetween(Vector3 other) {
    double dot = this.dot(other);
    double magProduct = this.length() * other.length();
    return Math.acos(dot / magProduct);
  }

  /**
   * Angle between this vector and another, in radians, clamped and null/zero-length safe. Scalar
   * result - no allocation. Does NOT mutate.
   */
  public double angleBetweenSafe(Vector3 other) {
    if (other == null) return Math.PI / 2;
    double dot = this.dot(other);
    double magProduct = this.length() * other.length();
    return magProduct > 0 ? Math.acos(Math.max(-1, Math.min(1, dot / magProduct))) : Math.PI / 2;
  }

  /**
   * Rotates this vector around an arbitrary axis by the given angle (Rodrigues' rotation formula,
   * mutates this). No allocation.
   *
   * @return this for method chaining
   */
  public Vector3 rotateAround(Vector3 axis, double angle) {
    double ax = axis.x, ay = axis.y, az = axis.z;
    double len = Math.sqrt(ax * ax + ay * ay + az * az);
    if (len > 0) {
      ax /= len;
      ay /= len;
      az /= len;
    }

    double cosTheta = Math.cos(angle);
    double sinTheta = Math.sin(angle);
    double oneMinusCos = 1.0 - cosTheta;

    double dot = this.x * ax + this.y * ay + this.z * az;
    double cx = ay * this.z - az * this.y;
    double cy = az * this.x - ax * this.z;
    double cz = ax * this.y - ay * this.x;

    this.x = this.x * cosTheta + cx * sinTheta + ax * dot * oneMinusCos;
    this.y = this.y * cosTheta + cy * sinTheta + ay * dot * oneMinusCos;
    this.z = this.z * cosTheta + cz * sinTheta + az * dot * oneMinusCos;

    return this;
  }

  /**
   * Transforms this vector as a surface normal using the inverse-transpose of the given matrix
   * (mutates this), then re-normalizes. No allocation beyond whatever {@link
   * Matrix4#inverseTransposeForNormal()} itself does.
   *
   * @return this for method chaining
   */
  public Vector3 transformNormal(Matrix4 matrix) {
    Matrix4 inverseTranspose = matrix.inverseTransposeForNormal();
    if (inverseTranspose == null) {
      this.x = 0.0;
      this.y = 0.0;
      this.z = 0.0;
      return this;
    }
    double tx =
        inverseTranspose.get(0, 0) * x
            + inverseTranspose.get(0, 1) * y
            + inverseTranspose.get(0, 2) * z;
    double ty =
        inverseTranspose.get(1, 0) * x
            + inverseTranspose.get(1, 1) * y
            + inverseTranspose.get(1, 2) * z;
    double tz =
        inverseTranspose.get(2, 0) * x
            + inverseTranspose.get(2, 1) * y
            + inverseTranspose.get(2, 2) * z;
    this.x = tx;
    this.y = ty;
    this.z = tz;
    return this.normalize();
  }

  // =========================================================================
  // DIELECTRIC/REFRACTION - SHIRLEY-EXACT (GC-FRIENDLY)
  // =========================================================================

  /**
   * Refracts this vector through a surface with the given relative index of refraction eta = n1/n2
   * (mutates this in place). No allocation.
   *
   * <p>Current sign convention is preserved exactly as-is (verified working / visually correct in
   * this engine); do not change without re-validating every call site and the resulting render
   * output.
   *
   * @return true if refraction succeeded, false on total internal reflection
   */
  public boolean refract(Vector3 normal, double eta) {
    double len = this.length();
    if (len == 0) return false;

    double ix = this.x / len;
    double iy = this.y / len;
    double iz = this.z / len;

    double cosI = -(ix * normal.x + iy * normal.y + iz * normal.z);
    double sinT2 = eta * eta * (1.0 - cosI * cosI);

    if (sinT2 > 1.0) {
      return false;
    }

    double cosT = Math.sqrt(1.0 - sinT2);

    double k = eta * cosI - cosT;

    this.x = eta * ix - k * normal.x;
    this.y = eta * iy - k * normal.y;
    this.z = eta * iz - k * normal.z;
    this.normalize();

    return true;
  }

  public boolean refract(Vector3 normal, double n1, double n2) {
    return this.refract(normal, n1 / n2);
  }

  /**
   * Refracts this vector with a single eta ratio and stores in target. target = refracted
   * direction, or unchanged if total internal reflection. No allocation.
   *
   * @param normal The surface normal (normalized)
   * @param eta The ratio of indices of refraction (n1/n2)
   * @param target The vector to store the result
   * @return true if refraction succeeded, false if total internal reflection
   */
  public boolean refract(Vector3 normal, double eta, Vector3 target) {
    target.set(this);
    return target.refract(normal, eta);
  }

  /**
   * Refracts this vector and stores result in target. target = refracted direction, or unchanged if
   * total internal reflection. No allocation.
   *
   * @param normal The surface normal (normalized)
   * @param n1 Index of refraction of current medium
   * @param n2 Index of refraction of entering medium
   * @param target The vector to store the result
   * @return true if refraction succeeded, false if total internal reflection
   */
  public boolean refract(Vector3 normal, double n1, double n2, Vector3 target) {
    target.set(this);
    return target.refract(normal, n1 / n2);
  }

  /**
   * Refracts this vector, or falls back to reflection on total internal reflection (mutates this in
   * place). No allocation.
   *
   * @return this for method chaining
   */
  public Vector3 refractOrReflect(Vector3 normal, double eta) {
    double len = this.length();
    if (len == 0) return this;

    double ix = this.x / len;
    double iy = this.y / len;
    double iz = this.z / len;

    double cosI = -(ix * normal.x + iy * normal.y + iz * normal.z);
    double sinT2 = eta * eta * (1.0 - cosI * cosI);

    if (sinT2 > 1.0) {
      return this.reflect(normal);
    }

    double cosT = Math.sqrt(1.0 - sinT2);

    double k = eta * cosI - cosT;

    this.x = eta * ix - k * normal.x;
    this.y = eta * iy - k * normal.y;
    this.z = eta * iz - k * normal.z;
    return this.normalize();
  }

  public Vector3 refractOrReflect(Vector3 normal, double n1, double n2) {
    return this.refractOrReflect(normal, n1 / n2);
  }

  // =========================================================================
  // STATIC FRESNEL METHODS (GC-FRIENDLY - scalar results only)
  // =========================================================================

  /**
   * Calculates the Fresnel reflection coefficient (alias for {@link #fresnel}). Prefer calling
   * {@link #fresnel} directly.
   */
  public static double calculateFresnel(Vector3 viewDir, Vector3 normal, double n1, double n2) {
    return fresnel(viewDir, normal, n1, n2);
  }

  /**
   * Schlick's approximation of the Fresnel reflection coefficient. Scalar result - no allocation.
   */
  public static double fresnel(Vector3 viewDir, Vector3 normal, double n1, double n2) {
    double cosTheta = Math.abs(viewDir.dot(normal));
    cosTheta = Math.min(1.0, Math.max(0.0, cosTheta));

    double r0 = (n1 - n2) / (n1 + n2);
    r0 = r0 * r0;

    double fresnel = r0 + (1.0 - r0) * Math.pow(1.0 - cosTheta, 5.0);

    if (fresnel < 0.0) return 0.0;
    if (fresnel > 1.0) return 1.0;
    return fresnel;
  }

  public static double calculateFresnel(Vector3 viewDir, Vector3 normal, double ior) {
    double cosTheta = Math.max(0.0, Math.min(1.0, Math.abs(viewDir.dot(normal))));
    double n1 = 1.0;
    double n2 = ior;
    double r0 = Math.pow((n1 - n2) / (n1 + n2), 2.0);
    return r0 + (1.0 - r0) * Math.pow(1.0 - cosTheta, 5.0);
  }

  public static double schlick(double cosine, double refIdx) {
    double r0 = (1.0 - refIdx) / (1.0 + refIdx);
    r0 = r0 * r0;
    return r0 + (1.0 - r0) * Math.pow(1.0 - cosine, 5.0);
  }

  public static double schlick(double cosine, double n1, double n2) {
    return schlick(cosine, n2 / n1);
  }

  public static double fresnelSchlick(double cosTheta, double f0) {
    double x = Math.max(0.0, Math.min(1.0, 1.0 - cosTheta));
    double x2 = x * x;
    return f0 + (1.0 - f0) * x2 * x2 * x; // (1-cosTheta)^5
  }

  // =========================================================================
  // STATIC HELPERS (GC-FRIENDLY)
  // =========================================================================

  /**
   * Generates a uniformly-distributed random point inside the unit sphere and stores it in target
   * (rejection sampling). No allocation.
   *
   * @return target for method chaining
   */
  public static Vector3 randomInUnitSphere(Random rand, Vector3 target) {
    do {
      target.set(
          rand.nextDouble() * 2.0 - 1.0,
          rand.nextDouble() * 2.0 - 1.0,
          rand.nextDouble() * 2.0 - 1.0);
    } while (target.lengthSquared() >= 1.0);
    return target;
  }

  /**
   * Reflects this vector around a normal (mutates this). No allocation. this = this - 2 * dot(this,
   * normal) * normal
   *
   * @return this for method chaining
   */
  public Vector3 reflect(Vector3 normal) {
    double factor = 2.0 * this.dot(normal);
    this.x -= normal.x * factor;
    this.y -= normal.y * factor;
    this.z -= normal.z * factor;
    return this;
  }

  /**
   * Reflects this vector around a normal and stores result in target. target = this - 2 * dot(this,
   * normal) * normal Does NOT mutate this vector. No allocation.
   *
   * @param normal The normal to reflect around
   * @param target The vector to store the result
   * @return target for method chaining
   */
  public Vector3 reflect(Vector3 normal, Vector3 target) {
    double factor = 2.0 * this.dot(normal);
    target.x = this.x - normal.x * factor;
    target.y = this.y - normal.y * factor;
    target.z = this.z - normal.z * factor;
    return target;
  }

  /**
   * Static reflect helper: reflects {@code incident} around {@code normal} and stores the result in
   * {@code target}. No allocation.
   *
   * @return target for method chaining
   */
  public static Vector3 reflect(Vector3 incident, Vector3 normal, Vector3 target) {
    double factor = 2.0 * incident.dot(normal);
    target.x = incident.x - normal.x * factor;
    target.y = incident.y - normal.y * factor;
    target.z = incident.z - normal.z * factor;
    return target;
  }

  // =========================================================================
  // COPY
  // =========================================================================

  /**
   * Copies this vector into target (mutates target, not this). No allocation.
   *
   * @return target for method chaining
   */
  public Vector3 copy(Vector3 target) {
    target.x = this.x;
    target.y = this.y;
    target.z = this.z;
    return target;
  }

  /**
   * Returns a new Vector3 (ALLOCATES). Kept only for compatibility with legacy code; avoid in hot
   * paths.
   */
  public Vector3 clone() {
    return new Vector3(this.x, this.y, this.z);
  }

  // =========================================================================
  // TO STRING
  // =========================================================================

  @Override
  public String toString() {
    return String.format(java.util.Locale.US, "V(%.2f, %.2f, %.2f)", x, y, z);
  }
}
