package net.murat.shape;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.murat.material.Material;
import net.murat.math.*;

/**
 * Paraboloid class represents a canonical paraboloid in its local coordinate system, transformed
 * into 3D world space using a 4x4 model matrix. Fully optimized to run 100% garbage-free inside hot
 * loops.
 *
 * <p>Formula: axis^2/a² + other1^2/b² = 2 * direction * height
 *
 * <p>axis: 0=X, 1=Y, 2=Z (which axis points up)
 *
 * <p>direction: 1.0 = bowl (U shape), -1.0 = hill (∩ shape)
 *
 * <p>This implementation is 100% garbage-free during render() execution. All temporary objects are
 * pre-allocated as instance fields and reused.
 *
 * @author Murat
 * @version 1.0
 */
public class Paraboloid implements EMShape {

  // =========================================================================
  // CONSTANTS
  // =========================================================================

  /** Pre-allocated empty list for no-intersection cases. */
  private static final List<IntersectionInterval> EMPTY_INTERVAL_LIST =
      java.util.Collections.emptyList();

  /** Pre-allocated fallback normal. */
  private static final Vector3 FALLBACK_NORMAL = new Vector3(0, 1, 0);

  // =========================================================================
  // PARABOLOID PROPERTIES
  // =========================================================================

  private Material material;
  private final double a; // Scale for first horizontal axis
  private final double b; // Scale for second horizontal axis
  private final int axis; // 0=X, 1=Y, 2=Z (up axis)
  private final double direction; // 1.0 = bowl (U), -1.0 = hill (∩)
  private final double maxHeight; // Maximum height (0 = unbounded)
  private final boolean cap; // Cap at maxHeight

  private Matrix4 transform;
  private Matrix4 inverseTransform;

  private int shadowColor = 0xFF000000;
  private double shadowBias = 0.001;
  private Matrix4[] animTransforms = new Matrix4[] {new Matrix4(), new Matrix4()};
  private String otherAnimationInfo = "veofr,0e0fr,veofr";
  private boolean isVisibleSpecial = true;
  private boolean isVisible = true;
  private boolean isShadowEnable = true;
  private boolean isShadowOnly = false;
  private boolean isReflective = true;
  private boolean isRefractive = true;
  private String name = "paraboloid";

  // Mesh cache
  private List<Triangle> cachedTriangles;

  // =========================================================================
  // GC-FREE SCRATCH POOL - Zero allocation per intersection test
  // =========================================================================

  /** Temporary world hit point. */
  private final Point3 worldHitPoint = new Point3();

  /** Temporary world normal. */
  private final Vector3 worldNormal = new Vector3();

  /** Temporary local point for normal calculation. */
  private final Point3 localPoint = new Point3();

  /** Temporary local normal for normal calculation. */
  private final Vector3 localNormal = new Vector3();

  /** Temporary origin for transform operations. */
  private final Point3 tempOrigin = new Point3();

  /** Temporary direction for transform operations. */
  private final Vector3 tempDir = new Vector3();

  /** Temporary point for local hit. */
  private final Point3 localHitPoint = new Point3();

  /** Pre-allocated in intersection for intersectAll. */
  private final Intersection cachedInIntersection = new Intersection();

  /** Pre-allocated out intersection for intersectAll. */
  private final Intersection cachedOutIntersection = new Intersection();

  /** Pre-allocated single interval for intersectAll. */
  private final IntersectionInterval singleInterval =
      new IntersectionInterval(0, 0, cachedInIntersection, cachedOutIntersection);

  /** Axis names for formulas. */
  private static final int AXIS_X = 0;

  private static final int AXIS_Y = 1;
  private static final int AXIS_Z = 2;

  // =========================================================================
  // CONSTRUCTORS
  // =========================================================================

  /**
   * Creates a paraboloid with default material.
   *
   * @param a Scale for first horizontal axis
   * @param b Scale for second horizontal axis
   * @param axis Which axis points up (0=X, 1=Y, 2=Z)
   * @param direction 1.0 = bowl, -1.0 = hill
   * @param maxHeight Maximum height (0 = unbounded)
   * @param cap Cap at maxHeight
   */
  public Paraboloid(double a, double b, int axis, double direction, double maxHeight, boolean cap) {
    this(a, b, axis, direction, maxHeight, cap, null);
  }

  /**
   * Creates a paraboloid with specified material.
   *
   * @param a Scale for first horizontal axis
   * @param b Scale for second horizontal axis
   * @param axis Which axis points up (0=X, 1=Y, 2=Z)
   * @param direction 1.0 = bowl, -1.0 = hill
   * @param maxHeight Maximum height (0 = unbounded)
   * @param cap Cap at maxHeight
   * @param material The material for this paraboloid
   */
  public Paraboloid(
      double a,
      double b,
      int axis,
      double direction,
      double maxHeight,
      boolean cap,
      Material material) {
    this.a = a;
    this.b = b;
    this.axis = axis;
    this.direction = direction;
    this.maxHeight = maxHeight;
    this.cap = cap;
    this.material = material;
    this.transform = new Matrix4();
    this.inverseTransform = new Matrix4();
  }

  // =========================================================================
  // GETTERS
  // =========================================================================

  public double getA() {
    return a;
  }

  public double getB() {
    return b;
  }

  public int getAxis() {
    return axis;
  }

  public double getDirection() {
    return direction;
  }

  public double getMaxHeight() {
    return maxHeight;
  }

  public boolean getCap() {
    return cap;
  }

  // =========================================================================
  // EMShape INTERFACE METHODS
  // =========================================================================

  @Override
  public Material getMaterial() {
    return material;
  }

  @Override
  public void setMaterial(Material material) {
    this.material = material;
  }

  @Override
  public void setTransform(Matrix4 transform) {
    this.transform = transform;
    this.inverseTransform = transform.inverse();
    cachedTriangles = null;
  }

  @Override
  public Matrix4 getTransform() {
    return this.transform;
  }

  @Override
  public Matrix4 getInverseTransform() {
    return this.inverseTransform;
  }

  // =========================================================================
  // PRIVATE HELPER METHODS - GC-FREE
  // =========================================================================

  /** Gets the up axis value from a point. */
  private double getAxisValue(Point3 p) {
    if (axis == AXIS_X) return p.x;
    if (axis == AXIS_Y) return p.y;
    return p.z;
  }

  /** Sets the up axis value on a point. */
  private void setAxisValue(Point3 p, double value) {
    if (axis == AXIS_X) p.x = value;
    else if (axis == AXIS_Y) p.y = value;
    else p.z = value;
  }

  /** Gets the horizontal axis 1 value from a point. */
  private double getH1(Point3 p) {
    if (axis == AXIS_X) return p.y;
    if (axis == AXIS_Y) return p.x;
    return p.x;
  }

  /** Gets the horizontal axis 2 value from a point. */
  private double getH2(Point3 p) {
    if (axis == AXIS_X) return p.z;
    if (axis == AXIS_Y) return p.z;
    return p.y;
  }

  // =========================================================================
  // INTERSECTION METHODS - GC-FREE
  // =========================================================================

  /**
   * Intersects a ray with the paraboloid and stores hit information. This method is 100%
   * garbage-free.
   *
   * @param ray The ray to intersect with the paraboloid (in world coordinates)
   * @param target The Intersection object to store hit information
   * @return true if intersection occurs, false otherwise
   */
  @Override
  public boolean intersect(Ray ray, Intersection target) {
    if (this.transform == null) {
      return false;
    }

    // Transform ray to local space
    this.inverseTransform.transformPoint(ray.getOrigin(), tempOrigin);
    this.inverseTransform.transformDirection(ray.getDirection(), tempDir);

    double ox = tempOrigin.x;
    double oy = tempOrigin.y;
    double oz = tempOrigin.z;
    double dx = tempDir.x;
    double dy = tempDir.y;
    double dz = tempDir.z;

    // Get axis values
    double oAxis, dAxis, oH1, dH1, oH2, dH2;

    if (axis == AXIS_X) {
      oAxis = ox;
      dAxis = dx;
      oH1 = oy;
      dH1 = dy;
      oH2 = oz;
      dH2 = dz;
    } else if (axis == AXIS_Y) {
      oAxis = oy;
      dAxis = dy;
      oH1 = ox;
      dH1 = dx;
      oH2 = oz;
      dH2 = dz;
    } else { // AXIS_Z
      oAxis = oz;
      dAxis = dz;
      oH1 = ox;
      dH1 = dx;
      oH2 = oy;
      dH2 = dy;
    }

    // Paraboloid equation: H1²/a² + H2²/b² = 2 * direction * axis
    // Rearranged: direction * axis = (H1²/a² + H2²/b²) / 2
    // Or: direction * (oAxis + dAxis * t) = ((oH1 + dH1 * t)²/a² + (oH2 + dH2 * t)²/b²) / 2

    double invA2 = 1.0 / (a * a);
    double invB2 = 1.0 / (b * b);
    double dir = direction;

    // Quadratic: A*t² + B*t + C = 0
    // A = (dH1²/a² + dH2²/b²) / 2
    // B = (2*oH1*dH1/a² + 2*oH2*dH2/b²) / 2 - dir * dAxis
    // C = (oH1²/a² + oH2²/b²) / 2 - dir * oAxis

    double A = (dH1 * dH1 * invA2 + dH2 * dH2 * invB2) * 0.5;
    double B = (2.0 * oH1 * dH1 * invA2 + 2.0 * oH2 * dH2 * invB2) * 0.5 - dir * dAxis;
    double C = (oH1 * oH1 * invA2 + oH2 * oH2 * invB2) * 0.5 - dir * oAxis;

    double discriminant = B * B - 4.0 * A * C;
    if (discriminant < 0) {
      return false;
    }

    double sqrtDisc = Math.sqrt(discriminant);
    double t1 = (-B - sqrtDisc) / (2.0 * A);
    double t2 = (-B + sqrtDisc) / (2.0 * A);

    double t = Double.POSITIVE_INFINITY;
    if (t1 > Ray.EPSILON) t = t1;
    if (t2 > Ray.EPSILON && t2 < t) t = t2;

    if (t == Double.POSITIVE_INFINITY) {
      return false;
    }

    // Compute hit point in world space
    worldHitPoint.x = ray.getOrigin().x + ray.getDirection().x * t;
    worldHitPoint.y = ray.getOrigin().y + ray.getDirection().y * t;
    worldHitPoint.z = ray.getOrigin().z + ray.getDirection().z * t;

    // Transform to local space for bounds check
    this.inverseTransform.transformPoint(worldHitPoint, localPoint);

    // Check height bound
    double axisValue = getAxisValue(localPoint);
    if (maxHeight > 0 && axisValue > maxHeight) {
      return false;
    }

    // Check if inside (for direction = 1.0, must be above the surface)
    double h1 = getH1(localPoint);
    double h2 = getH2(localPoint);
    double surfaceAxis = (h1 * h1 * invA2 + h2 * h2 * invB2) * 0.5 * dir;

    // For bowl (direction=1): point must be on surface (we already are)
    // For hill (direction=-1): point must be on surface
    // No additional check needed since we solved the equation

    // Compute normal: gradient of paraboloid equation
    // f = h1²/a² + h2²/b² - 2*direction*axis = 0
    // ∇f = (2*h1/a², 2*h2/b², -2*direction)
    // But we need to map to correct axes

    double gradH1 = 2.0 * h1 * invA2;
    double gradH2 = 2.0 * h2 * invB2;
    double gradAxis = -2.0 * dir;

    // Map gradient to x,y,z
    if (axis == AXIS_X) {
      localNormal.x = gradAxis;
      localNormal.y = gradH1;
      localNormal.z = gradH2;
    } else if (axis == AXIS_Y) {
      localNormal.x = gradH1;
      localNormal.y = gradAxis;
      localNormal.z = gradH2;
    } else { // AXIS_Z
      localNormal.x = gradH1;
      localNormal.y = gradH2;
      localNormal.z = gradAxis;
    }

    localNormal.normalize();

    // Transform normal to world space
    Matrix4 inverseTranspose = this.inverseTransform.transpose();
    inverseTranspose.transformDirection(localNormal, worldNormal);
    worldNormal.normalize();

    target.set(worldHitPoint, worldNormal, t, this);
    return true;
  }

  /**
   * Calculates the normal at a given point and stores it in targetNormal. This method is 100%
   * garbage-free.
   *
   * @param worldPoint The point in world coordinates
   * @param targetNormal The Vector3 to store the normal in
   * @return The normalized normal vector (same as targetNormal)
   */
  public Vector3 getNormalAt(Point3 worldPoint, Vector3 targetNormal) {
    if (this.transform == null || this.inverseTransform == null) {
      return targetNormal.set(FALLBACK_NORMAL);
    }

    // Transform point to local space
    this.inverseTransform.transformPoint(worldPoint, localPoint);

    double h1 = getH1(localPoint);
    double h2 = getH2(localPoint);
    double axisValue = getAxisValue(localPoint);

    double invA2 = 1.0 / (a * a);
    double invB2 = 1.0 / (b * b);
    double dir = direction;

    // Gradient: ∇f = (2*h1/a², 2*h2/b², -2*direction)
    double gradH1 = 2.0 * h1 * invA2;
    double gradH2 = 2.0 * h2 * invB2;
    double gradAxis = -2.0 * dir;

    // Map gradient to x,y,z
    if (axis == AXIS_X) {
      localNormal.x = gradAxis;
      localNormal.y = gradH1;
      localNormal.z = gradH2;
    } else if (axis == AXIS_Y) {
      localNormal.x = gradH1;
      localNormal.y = gradAxis;
      localNormal.z = gradH2;
    } else { // AXIS_Z
      localNormal.x = gradH1;
      localNormal.y = gradH2;
      localNormal.z = gradAxis;
    }

    double lenSq =
        localNormal.x * localNormal.x
            + localNormal.y * localNormal.y
            + localNormal.z * localNormal.z;

    if (lenSq > Ray.EPSILON) {
      double invLen = 1.0 / Math.sqrt(lenSq);
      localNormal.x *= invLen;
      localNormal.y *= invLen;
      localNormal.z *= invLen;
    } else {
      localNormal.set(FALLBACK_NORMAL);
    }

    // Transform normal to world space
    Matrix4 inverseTranspose = this.inverseTransform.transpose();
    inverseTranspose.transformDirection(localNormal, targetNormal);
    targetNormal.normalize();

    return targetNormal;
  }

  /**
   * Calculates all intersection intervals between a ray and this paraboloid.
   *
   * <p>Note: This method returns pre-allocated objects. The caller must NOT modify the returned
   * objects as they are reused.
   *
   * @param ray The ray to test, in world coordinates
   * @return A list of IntersectionInterval objects. Empty if no intersections
   */
  @Override
  public List<IntersectionInterval> intersectAll(Ray ray) {
    if (this.inverseTransform == null) {
      return EMPTY_INTERVAL_LIST;
    }

    // Transform ray to local space
    this.inverseTransform.transformPoint(ray.getOrigin(), tempOrigin);
    this.inverseTransform.transformDirection(ray.getDirection(), tempDir);

    double ox = tempOrigin.x;
    double oy = tempOrigin.y;
    double oz = tempOrigin.z;
    double dx = tempDir.x;
    double dy = tempDir.y;
    double dz = tempDir.z;

    // Get axis values
    double oAxis, dAxis, oH1, dH1, oH2, dH2;

    if (axis == AXIS_X) {
      oAxis = ox;
      dAxis = dx;
      oH1 = oy;
      dH1 = dy;
      oH2 = oz;
      dH2 = dz;
    } else if (axis == AXIS_Y) {
      oAxis = oy;
      dAxis = dy;
      oH1 = ox;
      dH1 = dx;
      oH2 = oz;
      dH2 = dz;
    } else { // AXIS_Z
      oAxis = oz;
      dAxis = dz;
      oH1 = ox;
      dH1 = dx;
      oH2 = oy;
      dH2 = dy;
    }

    double invA2 = 1.0 / (a * a);
    double invB2 = 1.0 / (b * b);
    double dir = direction;

    double A = (dH1 * dH1 * invA2 + dH2 * dH2 * invB2) * 0.5;
    double B = (2.0 * oH1 * dH1 * invA2 + 2.0 * oH2 * dH2 * invB2) * 0.5 - dir * dAxis;
    double C = (oH1 * oH1 * invA2 + oH2 * oH2 * invB2) * 0.5 - dir * oAxis;

    double discriminant = B * B - 4.0 * A * C;
    if (discriminant < 0) {
      return EMPTY_INTERVAL_LIST;
    }

    double sqrtDisc = Math.sqrt(discriminant);
    double t1 = (-B - sqrtDisc) / (2.0 * A);
    double t2 = (-B + sqrtDisc) / (2.0 * A);

    if (t1 > t2) {
      double temp = t1;
      t1 = t2;
      t2 = temp;
    }

    boolean t1Valid = t1 > Ray.EPSILON;
    boolean t2Valid = t2 > Ray.EPSILON;

    if (!t1Valid && !t2Valid) {
      return EMPTY_INTERVAL_LIST;
    }

    // Compute in point
    localHitPoint.x = ray.getOrigin().x + ray.getDirection().x * t1;
    localHitPoint.y = ray.getOrigin().y + ray.getDirection().y * t1;
    localHitPoint.z = ray.getOrigin().z + ray.getDirection().z * t1;
    getNormalAt(localHitPoint, worldNormal);
    cachedInIntersection.set(localHitPoint, worldNormal, t1, this);

    // Compute out point
    localHitPoint.x = ray.getOrigin().x + ray.getDirection().x * t2;
    localHitPoint.y = ray.getOrigin().y + ray.getDirection().y * t2;
    localHitPoint.z = ray.getOrigin().z + ray.getDirection().z * t2;
    getNormalAt(localHitPoint, worldNormal);
    cachedOutIntersection.set(localHitPoint, worldNormal, t2, this);

    singleInterval.setEntry(t1, cachedInIntersection);
    singleInterval.setExit(t2, cachedOutIntersection);

    return java.util.Collections.singletonList(singleInterval);
  }

  /**
   * Returns the surface normal at a given point on the paraboloid. This method is 100%
   * garbage-free.
   *
   * @param worldPoint The point on the paraboloid's surface in world coordinates
   * @return The normalized normal vector at that point in world coordinates
   */
  @Override
  public Vector3 getNormalAt(Point3 worldPoint) {
    getNormalAt(worldPoint, worldNormal);
    return worldNormal;
  }

  // =========================================================================
  // GETTERS/SETTERS - GC-FREE
  // =========================================================================

  @Override
  public void setAnimationTransforms(Matrix4[] atm) {
    this.animTransforms = atm;
  }

  @Override
  public Matrix4[] getAnimationTransforms() {
    return this.animTransforms;
  }

  @Override
  public int getShadowColor() {
    return this.shadowColor;
  }

  @Override
  public void setShadowColor(int color) {
    this.shadowColor = color;
  }

  @Override
  public double getShadowBias() {
    return this.shadowBias;
  }

  @Override
  public void setShadowBias(double bias) {
    this.shadowBias = bias;
  }

  @Override
  public String getNameInfo() {
    return ("Paraboloid: " + getTransform().toString());
  }

  @Override
  public String getOtherAnimationInfo() {
    return this.otherAnimationInfo;
  }

  @Override
  public void setOtherAnimationInfo(String str) {
    this.otherAnimationInfo = str;
  }

  @Override
  public boolean isVisible() {
    return this.isVisible;
  }

  @Override
  public boolean isShadowEnable() {
    return this.isShadowEnable;
  }

  @Override
  public boolean isShadowOnly() {
    return this.isShadowOnly;
  }

  @Override
  public boolean isReflective() {
    return this.isReflective;
  }

  @Override
  public boolean isRefractive() {
    return this.isRefractive;
  }

  @Override
  public void setVisible(boolean visible) {
    this.isVisible = visible;
  }

  @Override
  public void setShadowEnable(boolean enable) {
    this.isShadowEnable = enable;
  }

  @Override
  public void setShadowOnly(boolean only) {
    this.isShadowOnly = only;
  }

  @Override
  public void setReflective(boolean rfl) {
    this.isReflective = rfl;
  }

  @Override
  public void setRefractive(boolean rfr) {
    this.isRefractive = rfr;
  }

  @Override
  public boolean isVisibleSpecial() {
    return this.isVisibleSpecial;
  }

  @Override
  public void setVisibleSpecial(boolean visible) {
    this.isVisibleSpecial = visible;
  }

  @Override
  public String getName() {
    return this.name;
  }

  @Override
  public void setName(String name) {
    this.name = name;
  }

  // =========================================================================
  // MESH METHODS - Called once, GC acceptable
  // =========================================================================

  @Override
  public List<Triangle> generateMesh() {
    if (cachedTriangles != null) {
      return cachedTriangles;
    }

    List<Triangle> triangles = new ArrayList<Triangle>();
    int segments = 32;
    int heightSegments = 20;

    double maxH = (maxHeight > 0) ? maxHeight : 10.0;

    for (int hIdx = 0; hIdx < heightSegments; hIdx++) {
      double h1 = maxH * hIdx / heightSegments;
      double h2 = maxH * (hIdx + 1) / heightSegments;

      double r1 = Math.sqrt(2.0 * direction * h1);
      double r2 = Math.sqrt(2.0 * direction * h2);

      if (r1 < 0 || r2 < 0) continue;

      double radius1 = a * r1;
      double radius2 = a * r2;

      for (int i = 0; i < segments; i++) {
        double theta1 = 2 * Math.PI * i / segments;
        double theta2 = 2 * Math.PI * (i + 1) / segments;

        double x1 = radius1 * Math.cos(theta1);
        double y1 = radius1 * Math.sin(theta1);
        double x2 = radius2 * Math.cos(theta1);
        double y2 = radius2 * Math.sin(theta1);
        double x3 = radius2 * Math.cos(theta2);
        double y3 = radius2 * Math.sin(theta2);
        double x4 = radius1 * Math.cos(theta2);
        double y4 = radius1 * Math.sin(theta2);

        Point3 v1, v2, v3, v4;

        // Map to correct axes based on 'axis' parameter
        if (axis == AXIS_X) {
          v1 = new Point3(h1, x1, y1);
          v2 = new Point3(h2, x2, y2);
          v3 = new Point3(h2, x3, y3);
          v4 = new Point3(h1, x4, y4);
        } else if (axis == AXIS_Y) {
          v1 = new Point3(x1, h1, y1);
          v2 = new Point3(x2, h2, y2);
          v3 = new Point3(x3, h2, y3);
          v4 = new Point3(x4, h1, y4);
        } else { // AXIS_Z
          v1 = new Point3(x1, y1, h1);
          v2 = new Point3(x2, y2, h2);
          v3 = new Point3(x3, y3, h2);
          v4 = new Point3(x4, y4, h1);
        }

        triangles.add(new Triangle(v1, v2, v3));
        triangles.add(new Triangle(v1, v3, v4));
      }
    }

    // Cap at maxHeight
    if (cap && maxHeight > 0) {
      generateCap(triangles, maxHeight, segments);
    }

    cachedTriangles = triangles;
    return triangles;
  }

  private void generateCap(List<Triangle> triangles, double height, int segments) {
    double r = Math.sqrt(2.0 * direction * height);
    if (r < 0) return;
    double radius = a * r;

    Point3 center;
    if (axis == AXIS_X) {
      center = new Point3(height, 0, 0);
    } else if (axis == AXIS_Y) {
      center = new Point3(0, height, 0);
    } else { // AXIS_Z
      center = new Point3(0, 0, height);
    }

    for (int i = 0; i < segments; i++) {
      double theta1 = 2 * Math.PI * i / segments;
      double theta2 = 2 * Math.PI * (i + 1) / segments;

      double x1 = radius * Math.cos(theta1);
      double y1 = radius * Math.sin(theta1);
      double x2 = radius * Math.cos(theta2);
      double y2 = radius * Math.sin(theta2);

      Point3 v1, v2;

      if (axis == AXIS_X) {
        v1 = new Point3(height, x1, y1);
        v2 = new Point3(height, x2, y2);
      } else if (axis == AXIS_Y) {
        v1 = new Point3(x1, height, y1);
        v2 = new Point3(x2, height, y2);
      } else { // AXIS_Z
        v1 = new Point3(x1, y1, height);
        v2 = new Point3(x2, y2, height);
      }

      triangles.add(new Triangle(center, v1, v2));
    }
  }

  @Override
  public List<Triangle> getTriangles() {
    return cachedTriangles;
  }

  // =========================================================================
  // TO STRING - GC acceptable (not called during render)
  // =========================================================================

  /**
   * Generates an OBJ format string representation of this Paraboloid. This method is called only
   * during export, not during render. Uses robust vertex deduplication with tolerance to avoid
   * degenerate triangles.
   *
   * @return String in OBJ format
   */
  @Override
  public String toObjString() {
    StringBuilder sb = new StringBuilder();

    sb.append("# Murat Ray Tracer Mesh File\n");
    sb.append("# Shape: Paraboloid\n");
    sb.append("# a: ").append(a).append("\n");
    sb.append("# b: ").append(b).append("\n");
    sb.append("# axis: ").append(axis).append("\n");
    sb.append("# direction: ").append(direction).append("\n");
    sb.append("# maxHeight: ").append(maxHeight).append("\n");
    sb.append("# cap: ").append(cap).append("\n");
    sb.append("# Generated: ").append(new java.util.Date()).append("\n");

    if (transform != null) {
      sb.append("# Transform: ").append(transform.toString()).append("\n");
    }

    if (material != null) {
      sb.append("# Material: ").append(material.getClass().getSimpleName()).append("\n");
    }
    sb.append("\n");

    List<Triangle> triangles = generateMesh();
    if (triangles == null || triangles.isEmpty()) {
      sb.append("# No triangles generated\n");
      return sb.toString();
    }

    Matrix4 trans = (transform != null) ? transform : new Matrix4();

    // Collect all transformed vertices
    List<Point3> allVerts = new ArrayList<Point3>();
    for (Triangle tri : triangles) {
      allVerts.add(trans.transformPoint(tri.getV0()));
      allVerts.add(trans.transformPoint(tri.getV1()));
      allVerts.add(trans.transformPoint(tri.getV2()));
    }

    // Deduplicate vertices with tolerance
    List<Point3> uniqueVerts = new ArrayList<Point3>();
    int[] indexMap = new int[allVerts.size()];
    final double TOLERANCE = 0.000001;

    for (int i = 0; i < allVerts.size(); i++) {
      Point3 v = allVerts.get(i);
      int found = -1;
      for (int j = 0; j < uniqueVerts.size(); j++) {
        Point3 u = uniqueVerts.get(j);
        if (Math.abs(u.x - v.x) < TOLERANCE
            && Math.abs(u.y - v.y) < TOLERANCE
            && Math.abs(u.z - v.z) < TOLERANCE) {
          found = j;
          break;
        }
      }
      if (found == -1) {
        found = uniqueVerts.size();
        uniqueVerts.add(new Point3(v.x, v.y, v.z));
      }
      indexMap[i] = found;
    }

    // Write vertices
    sb.append("# ===== VERTICES =====\n");
    for (Point3 v : uniqueVerts) {
      sb.append(String.format(java.util.Locale.US, "v %.6f %.6f %.6f\n", v.x, v.y, v.z));
    }
    sb.append("\n");

    // Write faces
    sb.append("# ===== TRIANGLES =====\n");
    for (int i = 0; i < triangles.size(); i++) {
      int base = i * 3;
      sb.append(
          String.format(
              java.util.Locale.US,
              "f %d %d %d\n",
              indexMap[base] + 1,
              indexMap[base + 1] + 1,
              indexMap[base + 2] + 1));
    }
    sb.append("\n");

    // Write material
    if (material != null) {
      sb.append("# ===== MATERIAL =====\n");
      sb.append("material = ").append(material.toString()).append("\n");
    }

    sb.append("\n# ===== END =====\n");

    return sb.toString();
  }

  private int getOrAddVertex(List<Point3> vertices, Map<String, Integer> map, Point3 v) {
    String key = String.format(java.util.Locale.US, "%.6f,%.6f,%.6f", v.x, v.y, v.z);
    if (map.containsKey(key)) {
      return map.get(key).intValue();
    }
    int index = vertices.size();
    vertices.add(new Point3(v.x, v.y, v.z));
    map.put(key, Integer.valueOf(index));
    return index;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("Paraboloid ").append(name).append(" {\n");
    sb.append("    name = ").append(name).append(";\n");
    sb.append("    a = ").append(a).append(";\n");
    sb.append("    b = ").append(b).append(";\n");
    sb.append("    axis = ").append(axis).append(";\n");
    sb.append("    direction = ").append(direction).append(";\n");
    sb.append("    maxHeight = ").append(maxHeight).append(";\n");
    sb.append("    cap = ").append(cap).append(";\n\n");
    sb.append("    ").append(getTransform().toString()).append("\n\n");
    sb.append("    firstAnim_").append(animTransforms[0].toString()).append("\n");
    sb.append("    secondAnim_").append(animTransforms[1].toString()).append("\n");
    sb.append("    otherAnimationInfo = ").append(otherAnimationInfo).append(";\n\n");
    sb.append("    shadowColor = ")
        .append(net.murat.util.ColorUtil.toColorString(shadowColor))
        .append(";\n");
    sb.append("    shadowBias = ").append(shadowBias).append(";\n\n");
    sb.append("    isVisibleSpecial = ").append(isVisibleSpecial).append(";\n");
    sb.append("    isVisible = ").append(isVisible).append(";\n");
    sb.append("    isShadowOnly = ").append(isShadowOnly).append(";\n");
    sb.append("    isShadowEnable = ").append(isShadowEnable).append(";\n");
    sb.append("    isReflective = ").append(isReflective).append(";\n");
    sb.append("    isRefractive = ").append(isRefractive).append(";\n\n");
    sb.append("    material = ")
        .append(getMaterial() != null ? getMaterial().toString() : "null")
        .append(";\n}");
    return sb.toString();
  }
}
