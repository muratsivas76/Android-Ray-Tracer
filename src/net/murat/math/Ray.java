package net.murat.math;

/**
 * Represents a ray in 3D space, defined by an origin point and a direction vector. Includes energy
 * tracking for ray tracing optimizations. FULLY GC-FRIENDLY - ZERO ALLOCATION in hot paths. All
 * methods mutate 'this'.
 */
public class Ray {

  private Point3 origin;
  private Vector3 direction;
  private double energy;

  public static final double EPSILON = 1e-4;
  public static final double BIAS = 1e-3;
  public static final double IOR_EPSILON = 1e-6;
  public static final double MIN_ATTENUATION = 1e-30;
  public static final double MIN_SHADOW_DISTANCE = 0.08;

  // Scratch variables for garbage-free operations
  public final Point3 P_TEMP1 = new Point3();
  public final Vector3 V_TEMP1 = new Vector3();

  // =========================================================================
  // CONSTRUCTORS
  // =========================================================================

  public Ray() {
    this.origin = new Point3(0, 0, 0);
    this.direction = new Vector3(0, 0, 0);
    this.energy = 1.0;
  }

  public Ray(Point3 origin, Vector3 direction) {
    this(origin, direction, 1.0);
  }

  public Ray(Point3 origin, Vector3 direction, double energy) {
    this.origin = new Point3(origin);
    this.direction = new Vector3();
    this.direction.set(direction);
    this.direction.normalize();
    this.energy = Math.max(0.0, Math.min(1.0, energy));
  }

  // =========================================================================
  // SETTERS (Fluent, GC-Friendly)
  // =========================================================================

  public Ray set(Point3 origin, Vector3 direction, double energy) {
    this.origin.set(origin);
    this.direction.set(direction).normalize();
    this.energy = Math.max(0.0, Math.min(1.0, energy));
    return this;
  }

  public Ray set(Ray other) {
    this.origin.set(other.origin);
    this.direction.set(other.direction);
    this.energy = other.energy;
    return this;
  }

  public Ray setOrigin(Point3 origin) {
    this.origin.set(origin);
    return this;
  }

  public Ray setDirection(Vector3 direction) {
    this.direction.set(direction).normalize();
    return this;
  }

  public Ray setEnergy(double energy) {
    this.energy = Math.max(0.0, Math.min(1.0, energy));
    return this;
  }

  // =========================================================================
  // GETTERS
  // =========================================================================

  public Point3 getOrigin() {
    return origin;
  }

  public Vector3 getDirection() {
    return direction;
  }

  public double getEnergy() {
    return energy;
  }

  // =========================================================================
  // CORE MATH - ALL MUTATING (GC-FRIENDLY)
  // =========================================================================

  /**
   * Calculates the point at parameter t: origin + direction * t. Writes result to target (no
   * allocation).
   *
   * @deprecated Use pointAt(double, Point3) instead
   */
  @Deprecated
  public Point3 pointAtParameter(double t) {
    return origin.add(direction.scale(t));
  }

  /**
   * Calculates the point at parameter t: origin + direction * t. Writes result to target (no
   * allocation).
   */
  public Point3 pointAt(double t, Point3 target) {
    target.set(this.origin);
    target.x += this.direction.x * t;
    target.y += this.direction.y * t;
    target.z += this.direction.z * t;
    return target;
  }

  /**
   * Calculates the point at parameter t: origin + direction * t. Allocates a new Point3 - use
   * pointAt(double, Point3) for GC-friendly code.
   */
  public Point3 pointAtParameter(double t, Point3 target) {
    return pointAt(t, target);
  }

  public Ray scaleEnergy(double factor) {
    this.energy *= Math.max(0.0, Math.min(1.0, factor));
    return this;
  }

  public Ray transform(Matrix4 matrix) {
    matrix.transformPoint(this.origin);
    matrix.transformDirection(this.direction);
    this.direction.normalize();
    return this;
  }

  public Ray transform(Matrix4 matrix, Ray target) {
    matrix.transformPoint(this.origin, target.origin);
    matrix.transformDirection(this.direction, target.direction);
    target.direction.normalize();
    target.energy = this.energy;
    return target;
  }

  public Ray reflect(Vector3 normal) {
    this.direction.reflect(normal);
    return this;
  }

  public boolean refract(Vector3 normal, double eta) {
    return this.direction.refract(normal, eta);
  }

  public Ray refractOrReflect(Vector3 normal, double eta) {
    this.direction.refractOrReflect(normal, eta);
    return this;
  }

  public Ray offset(double epsilon) {
    this.origin.x += this.direction.x * epsilon;
    this.origin.y += this.direction.y * epsilon;
    this.origin.z += this.direction.z * epsilon;
    return this;
  }

  public Ray offsetAlongNormal(Vector3 normal, double epsilon) {
    this.origin.x += normal.x * epsilon;
    this.origin.y += normal.y * epsilon;
    this.origin.z += normal.z * epsilon;
    return this;
  }

  // =========================================================================
  // COPY
  // =========================================================================

  public Ray copy(Ray target) {
    target.origin.set(this.origin);
    target.direction.set(this.direction);
    target.energy = this.energy;
    return target;
  }

  public Ray clone() {
    return new Ray(this.origin, this.direction, this.energy);
  }

  // =========================================================================
  // TO STRING
  // =========================================================================

  @Override
  public String toString() {
    return String.format("Ray(origin=%s, direction=%s, energy=%.3f)", origin, direction, energy);
  }
}
