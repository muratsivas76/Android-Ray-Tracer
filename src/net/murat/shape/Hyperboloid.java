package net.murat.shape;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.murat.material.Material;
import net.murat.math.*;

/**
 * Hyperboloid class represents a canonical hyperboloid in its local coordinate system, transformed
 * into 3D world space using a 4x4 model matrix. Fully optimized to run 100% garbage-free inside hot
 * loops.
 *
 * <p>Formula: x²/a² + y²/b² - z²/c² = type
 *
 * <p>type = 1.0 → One-sheeted hyperboloid (cooling tower)
 *
 * <p>type = -1.0 → Two-sheeted hyperboloid (two separate bowls)
 *
 * <p>This implementation is 100% garbage-free during render() execution. All temporary objects are
 * pre-allocated as instance fields and reused.
 *
 * @author Murat
 * @version 1.0
 */
public class Hyperboloid implements EMShape {

  // =========================================================================
  // CONSTANTS
  // =========================================================================

  /** Pre-allocated empty list for no-intersection cases. */
  private static final List<IntersectionInterval> EMPTY_INTERVAL_LIST =
      java.util.Collections.emptyList();

  /** Pre-allocated fallback normal. */
  private static final Vector3 FALLBACK_NORMAL = new Vector3(0, 1, 0);

  // =========================================================================
  // HYPERBOLOID PROPERTIES
  // =========================================================================

  private Material material;
  private final double a; // X-axis scale
  private final double b; // Y-axis scale
  private final double c; // Z-axis scale
  private final double type; // 1.0 = one-sheeted, -1.0 = two-sheeted
  private final double minZ; // Minimum Z bound
  private final double maxZ; // Maximum Z bound
  private final boolean capTop; // Cap at maxZ
  private final boolean capBottom; // Cap at minZ

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
  private String name = "hyperboloid";

  // Mesh cache
  private List<Triangle> cachedTriangles;

  // =========================================================================
  // GC-FREE SCRATCH POOL - Zero allocation per intersection test
  // =========================================================================

  /** Temporary world center point. */
  private final Point3 worldCenter = new Point3();

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

  /** Scratch vector for quadratic equation coefficients. */
  private final Vector3 scratchVec = new Vector3();

  /** Scratch point for quadratic equation. */
  private final Point3 scratchPoint = new Point3();

  // =========================================================================
  // CONSTRUCTORS
  // =========================================================================

  /**
   * Creates a hyperboloid with default material.
   *
   * @param a Scale along X axis
   * @param b Scale along Y axis
   * @param c Scale along Z axis
   * @param type 1.0 = one-sheeted, -1.0 = two-sheeted
   * @param minZ Minimum Z bound
   * @param maxZ Maximum Z bound
   * @param capTop Cap at maxZ
   * @param capBottom Cap at minZ
   */
  public Hyperboloid(
      double a,
      double b,
      double c,
      double type,
      double minZ,
      double maxZ,
      boolean capTop,
      boolean capBottom) {
    this(a, b, c, type, minZ, maxZ, capTop, capBottom, null);
  }

  /**
   * Creates a hyperboloid with specified material.
   *
   * @param a Scale along X axis
   * @param b Scale along Y axis
   * @param c Scale along Z axis
   * @param type 1.0 = one-sheeted, -1.0 = two-sheeted
   * @param minZ Minimum Z bound
   * @param maxZ Maximum Z bound
   * @param capTop Cap at maxZ
   * @param capBottom Cap at minZ
   * @param material The material for this hyperboloid
   */
  public Hyperboloid(
      double a,
      double b,
      double c,
      double type,
      double minZ,
      double maxZ,
      boolean capTop,
      boolean capBottom,
      Material material) {
    this.a = a;
    this.b = b;
    this.c = c;
    this.type = type;
    this.minZ = minZ;
    this.maxZ = maxZ;
    this.capTop = capTop;
    this.capBottom = capBottom;
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

  public double getC() {
    return c;
  }

  public double getType() {
    return type;
  }

  public double getMinZ() {
    return minZ;
  }

  public double getMaxZ() {
    return maxZ;
  }

  public boolean getCapTop() {
    return capTop;
  }

  public boolean getCapBottom() {
    return capBottom;
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
  // INTERSECTION METHODS - GC-FREE
  // =========================================================================

  /**
   * Intersects a ray with the hyperboloid and stores hit information. This method is 100%
   * garbage-free.
   *
   * @param ray The ray to intersect with the hyperboloid (in world coordinates)
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

    // Quadratic equation coefficients in local space
    // x²/a² + y²/b² - z²/c² = type
    double invA2 = 1.0 / (a * a);
    double invB2 = 1.0 / (b * b);
    double invC2 = 1.0 / (c * c);

    double A = dx * dx * invA2 + dy * dy * invB2 - dz * dz * invC2;
    double B = 2.0 * (ox * dx * invA2 + oy * dy * invB2 - oz * dz * invC2);
    double C = ox * ox * invA2 + oy * oy * invB2 - oz * oz * invC2 - type;

    double discriminant = B * B - 4.0 * A * C;
    if (discriminant < 0) {
      return false;
    }

    double sqrtDisc = Math.sqrt(discriminant);
    double t1 = (-B - sqrtDisc) / (2.0 * A);
    double t2 = (-B + sqrtDisc) / (2.0 * A);

    // Find closest valid hit
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

    // Check Z bounds
    if (localPoint.z < minZ || localPoint.z > maxZ) {
      return false;
    }

    // Compute normal: gradient of hyperboloid equation
    // ∇f = (2x/a², 2y/b², -2z/c²)
    localNormal.x = localPoint.x * invA2;
    localNormal.y = localPoint.y * invB2;
    localNormal.z = -localPoint.z * invC2;
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

    // Compute normal: gradient of hyperboloid equation
    // ∇f = (2x/a², 2y/b², -2z/c²)
    double invA2 = 1.0 / (a * a);
    double invB2 = 1.0 / (b * b);
    double invC2 = 1.0 / (c * c);

    localNormal.x = localPoint.x * invA2;
    localNormal.y = localPoint.y * invB2;
    localNormal.z = -localPoint.z * invC2;

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
   * Calculates all intersection intervals between a ray and this hyperboloid.
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

    double invA2 = 1.0 / (a * a);
    double invB2 = 1.0 / (b * b);
    double invC2 = 1.0 / (c * c);

    double A = dx * dx * invA2 + dy * dy * invB2 - dz * dz * invC2;
    double B = 2.0 * (ox * dx * invA2 + oy * dy * invB2 - oz * dz * invC2);
    double C = ox * ox * invA2 + oy * oy * invB2 - oz * oz * invC2 - type;

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
   * Returns the surface normal at a given point on the hyperboloid. This method is 100%
   * garbage-free.
   *
   * @param worldPoint The point on the hyperboloid's surface in world coordinates
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
    return ("Hyperboloid: " + getTransform().toString());
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
    int zSegments = 20;

    for (int zIdx = 0; zIdx < zSegments; zIdx++) {
      double z1 = minZ + (maxZ - minZ) * zIdx / zSegments;
      double z2 = minZ + (maxZ - minZ) * (zIdx + 1) / zSegments;

      double r1 = Math.sqrt(type + (z1 * z1) / (c * c));
      double r2 = Math.sqrt(type + (z2 * z2) / (c * c));

      // Skip if imaginary (for two-sheeted, between sheets)
      if (r1 < 0 || r2 < 0) continue;

      double radiusAtZ1 = a * r1;
      double radiusAtZ2 = a * r2;

      for (int i = 0; i < segments; i++) {
        double theta1 = 2 * Math.PI * i / segments;
        double theta2 = 2 * Math.PI * (i + 1) / segments;

        double x1 = radiusAtZ1 * Math.cos(theta1);
        double y1 = radiusAtZ1 * Math.sin(theta1);
        double x2 = radiusAtZ2 * Math.cos(theta1);
        double y2 = radiusAtZ2 * Math.sin(theta1);
        double x3 = radiusAtZ2 * Math.cos(theta2);
        double y3 = radiusAtZ2 * Math.sin(theta2);
        double x4 = radiusAtZ1 * Math.cos(theta2);
        double y4 = radiusAtZ1 * Math.sin(theta2);

        Point3 v1 = new Point3(x1, y1, z1);
        Point3 v2 = new Point3(x2, y2, z2);
        Point3 v3 = new Point3(x3, y3, z2);
        Point3 v4 = new Point3(x4, y4, z1);

        triangles.add(new Triangle(v1, v2, v3));
        triangles.add(new Triangle(v1, v3, v4));
      }
    }

    // Caps
    if (capTop) {
      generateCap(triangles, maxZ, segments, true);
    }
    if (capBottom) {
      generateCap(triangles, minZ, segments, false);
    }

    cachedTriangles = triangles;
    return triangles;
  }

  private void generateCap(List<Triangle> triangles, double z, int segments, boolean top) {
    double r = Math.sqrt(type + (z * z) / (c * c));
    if (r < 0) return;
    double radius = a * r;

    Point3 center = new Point3(0, 0, z);

    for (int i = 0; i < segments; i++) {
      double theta1 = 2 * Math.PI * i / segments;
      double theta2 = 2 * Math.PI * (i + 1) / segments;

      double x1 = radius * Math.cos(theta1);
      double y1 = radius * Math.sin(theta1);
      double x2 = radius * Math.cos(theta2);
      double y2 = radius * Math.sin(theta2);

      Point3 v1 = new Point3(x1, y1, z);
      Point3 v2 = new Point3(x2, y2, z);

      if (top) {
        triangles.add(new Triangle(center, v1, v2));
      } else {
        triangles.add(new Triangle(center, v2, v1));
      }
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
   * Generates an OBJ format string representation of this Hyperboloid. This method is called only
   * during export, not during render. Uses robust vertex deduplication with tolerance to avoid
   * degenerate triangles.
   *
   * @return String in OBJ format
   */
  /**
   * Generates an OBJ format string representation of this Hyperboloid. Transform is applied
   * directly to vertices, not written as OBJ transform. Uses robust vertex deduplication with
   * tolerance to avoid degenerate triangles.
   *
   * @return String in OBJ format
   */
  /**
   * Generates an OBJ format string representation of this Hyperboloid. Transform is applied
   * directly to vertices, not written as OBJ transform. Uses robust vertex deduplication with
   * tolerance to avoid degenerate triangles.
   *
   * @return String in OBJ format
   */
  @Override
  public String toObjString() {
    StringBuilder sb = new StringBuilder();

    sb.append("# Murat Ray Tracer Mesh File\n");
    sb.append("# Shape: Hyperboloid\n");
    sb.append("# a: ").append(a).append("\n");
    sb.append("# b: ").append(b).append("\n");
    sb.append("# c: ").append(c).append("\n");
    sb.append("# type: ").append(type).append("\n");
    sb.append("# minZ: ").append(minZ).append("\n");
    sb.append("# maxZ: ").append(maxZ).append("\n");
    sb.append("# capTop: ").append(capTop).append("\n");
    sb.append("# capBottom: ").append(capBottom).append("\n");
    sb.append("# Generated: ").append(new java.util.Date()).append("\n");

    // TRANSFORM YAZMA! Sadece material bilgisi
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

    // Deduplicate vertices with tolerance (0.0001 for better merging)
    List<Point3> uniqueVerts = new ArrayList<Point3>();
    int[] indexMap = new int[allVerts.size()];
    final double TOLERANCE = 0.0001;

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
    sb.append("Hyperboloid ").append(name).append(" {\n");
    sb.append("    name = ").append(name).append(";\n");
    sb.append("    a = ").append(a).append(";\n");
    sb.append("    b = ").append(b).append(";\n");
    sb.append("    c = ").append(c).append(";\n");
    sb.append("    type = ").append(type).append(";\n");
    sb.append("    minZ = ").append(minZ).append(";\n");
    sb.append("    maxZ = ").append(maxZ).append(";\n");
    sb.append("    capTop = ").append(capTop).append(";\n");
    sb.append("    capBottom = ").append(capBottom).append(";\n\n");
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
