package net.murat.shape;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.murat.material.Material;
import net.murat.math.*;
import net.murat.util.ColorUtil;

/**
 * Torus class represents a torus (donut shape) in 3D space using ray marching. Implements EMShape
 * interface with full transformation support.
 *
 * <p>This implementation is 100% garbage-free during render() execution. All temporary objects are
 * pre-allocated as instance fields and reused.
 *
 * <p>Note: Ray marching is inherently slower than analytic intersection, but this implementation
 * minimizes GC overhead.
 */
public class Torus implements EMShape {

  // =========================================================================
  // CONSTANTS
  // =========================================================================

  /** Pre-allocated empty list for no-intersection cases. */
  private static final List<IntersectionInterval> EMPTY_INTERVAL_LIST =
      java.util.Collections.emptyList();

  /** Pre-allocated fallback normal. */
  private static final Vector3 FALLBACK_NORMAL = new Vector3(0, 1, 0);

  // =========================================================================
  // TORUS PROPERTIES
  // =========================================================================

  private final double majorRadius; // R
  private final double minorRadius; // r
  private Material material;

  private Matrix4 transform;
  private Matrix4 inverseTransform;
  private Matrix4 inverseTransposeTransformForNormal;

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

  // Ray Marching Parameters
  private static final int MAX_MARCH_STEPS = 200;
  private static final double HIT_THRESHOLD = 0.001;
  private static final double MAX_MARCH_DISTANCE = 100.0;

  private String name = "torus";

  // Mesh cache
  private List<Triangle> cachedTriangles;

  // =========================================================================
  // GC-FREE SCRATCH POOL - Zero allocation per intersection test
  // =========================================================================

  /** Temporary point in local space. */
  private final Point3 tempLocalPoint = new Point3();

  /** Temporary point in world space. */
  private final Point3 tempWorldPoint = new Point3();

  /** Temporary normal in local space. */
  private final Vector3 tempLocalNormal = new Vector3();

  /** Temporary normal in world space. */
  private final Vector3 tempWorldNormal = new Vector3();

  /** Temporary ray in local space. */
  private final Ray tempLocalRay = new Ray(new Point3(), new Vector3(), 1.0);

  /** Temporary direction vector. */
  private final Vector3 tempDir = new Vector3();

  /** Temporary position vector for ray marching. */
  private final Point3 tempPos = new Point3();

  /** Temporary offset vector. */
  private final Vector3 tempOffset = new Vector3();

  /** Temporary origin for transform operations. */
  private final Point3 tempOrigin = new Point3();

  /** Temporary direction for transform operations. */
  private final Vector3 tempDirLocal = new Vector3();

  /** Temporary vector for SDF calculations. */
  private final Vector3 tempSDFVec = new Vector3();

  /** Temporary vector for SDF calculations. */
  private final Vector3 tempSDFVec2 = new Vector3();

  /** Pre-allocated intersection for intersectAll. */
  private final Intersection cachedInIntersection = new Intersection();

  /** Pre-allocated intersection for intersectAll. */
  private final Intersection cachedOutIntersection = new Intersection();

  /** Pre-allocated interval for intersectAll. */
  private final IntersectionInterval singleInterval =
      new IntersectionInterval(0, 0, cachedInIntersection, cachedOutIntersection);

  /** Pre-allocated array for SDF normal calculation. */
  private final double[] sdfValues = new double[6];

  // =========================================================================
  // CONSTRUCTORS
  // =========================================================================

  /**
   * Creates a Torus defined in local coordinate system. Centered at (0,0,0). World space position
   * and transformations are set via setTransform().
   *
   * @param majorRadius Main radius of torus (from center to tube center)
   * @param minorRadius Secondary radius of torus (tube thickness)
   */
  public Torus(double majorRadius, double minorRadius) {
    this.majorRadius = majorRadius;
    this.minorRadius = minorRadius;
    this.material = null;

    this.transform = new Matrix4();
    this.inverseTransform = new Matrix4();
    this.inverseTransposeTransformForNormal = new Matrix4();
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
    this.inverseTransposeTransformForNormal = this.inverseTransform.inverseTransposeForNormal();

    if (this.inverseTransform == null) {
      this.inverseTransform = new Matrix4();
    }

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
  // SDF CALCULATION - GC-FREE
  // =========================================================================

  /**
   * Calculates the SDF distance of a given point in the torus's local coordinate system. This
   * method is 100% garbage-free.
   *
   * @param p Point in object's local space
   * @return SDF distance from point to torus
   */
  private double calculateSDF(Point3 p) {
    // q_x_dist = length(p.x, p.z) - majorRadius
    // Using pre-allocated vectors to avoid allocation
    double qxDist = Math.sqrt(p.x * p.x + p.z * p.z) - majorRadius;
    double qyVal = p.y;

    return Math.sqrt(qxDist * qxDist + qyVal * qyVal) - minorRadius;
  }

  // =========================================================================
  // INTERSECTION METHODS - GC-FREE
  // =========================================================================

  /**
   * Intersects a ray with the torus using ray marching. This method is 100% garbage-free.
   *
   * @param ray The ray to intersect with the torus (in world coordinates)
   * @param hit The Intersection object to store hit information
   * @return true if intersection occurs, false otherwise
   */
  @Override
  public boolean intersect(Ray ray, Intersection hit) {
    if (inverseTransform == null) {
      return false;
    }

    // Transform ray to local space using pre-allocated scratch
    inverseTransform.transformPoint(ray.getOrigin(), tempOrigin);
    inverseTransform.transformDirection(ray.getDirection(), tempDirLocal);
    tempDirLocal.normalize();
    tempLocalRay.set(tempOrigin, tempDirLocal, 1.0);

    double totalDistanceMarched = 0.0;
    tempPos.set(tempOrigin);

    for (int i = 0; i < MAX_MARCH_STEPS; i++) {
      // Calculate distance from current position to torus
      double distanceToTorus = calculateSDF(tempPos);

      // If distance is below threshold, we found an intersection
      if (distanceToTorus < HIT_THRESHOLD) {
        // Fill hit information using pre-allocated scratch
        ray.pointAtParameter(totalDistanceMarched, tempWorldPoint);
        Vector3 normal = getNormalAt(tempWorldPoint);
        hit.set(tempWorldPoint, normal, totalDistanceMarched, this);
        return true;
      }

      // Advance ray by distance to object (in local space)
      // Use pre-allocated tempDir for scaling
      tempDir.set(tempDirLocal);
      tempDir.multiply(distanceToTorus);
      tempPos.add(tempDir);
      totalDistanceMarched += distanceToTorus;

      // If total marched distance exceeds maximum distance, no intersection
      if (totalDistanceMarched >= MAX_MARCH_DISTANCE) {
        return false;
      }
    }

    return false;
  }

  /**
   * @deprecated Use {@link #intersect(Ray, Intersection)} instead.
   */
  @Deprecated
  public double intersect(Ray ray) {
    if (inverseTransform == null) {
      return Double.POSITIVE_INFINITY;
    }

    // Transform ray to local space
    inverseTransform.transformPoint(ray.getOrigin(), tempOrigin);
    inverseTransform.transformDirection(ray.getDirection(), tempDirLocal);
    tempDirLocal.normalize();
    tempLocalRay.set(tempOrigin, tempDirLocal, 1.0);

    double totalDistanceMarched = 0.0;
    tempPos.set(tempOrigin);

    for (int i = 0; i < MAX_MARCH_STEPS; i++) {
      double distanceToTorus = calculateSDF(tempPos);

      if (distanceToTorus < HIT_THRESHOLD) {
        return totalDistanceMarched;
      }

      tempDir.set(tempDirLocal);
      tempDir.multiply(distanceToTorus);
      tempPos.add(tempDir);
      totalDistanceMarched += distanceToTorus;

      if (totalDistanceMarched >= MAX_MARCH_DISTANCE) {
        return Double.POSITIVE_INFINITY;
      }
    }

    return Double.POSITIVE_INFINITY;
  }

  /**
   * Calculates all intersection intervals between a ray and this torus.
   *
   * <p>Note: This method returns pre-allocated objects. The caller must NOT modify the returned
   * objects as they are reused.
   *
   * @param ray The ray to test, in world coordinates
   * @return A list of IntersectionInterval objects. Empty if no intersections
   */
  @Override
  public List<IntersectionInterval> intersectAll(Ray ray) {
    if (inverseTransform == null) {
      return EMPTY_INTERVAL_LIST;
    }

    // Transform ray to local space
    inverseTransform.transformPoint(ray.getOrigin(), tempOrigin);
    inverseTransform.transformDirection(ray.getDirection(), tempDirLocal);
    tempDirLocal.normalize();
    tempLocalRay.set(tempOrigin, tempDirLocal, 1.0);

    double totalDistance = 0.0;
    tempPos.set(tempOrigin);

    boolean isInside = false;
    double tIn = -1;

    for (int i = 0; i < MAX_MARCH_STEPS; i++) {
      double sdf = calculateSDF(tempPos);

      boolean wasInside = isInside;
      isInside = sdf < HIT_THRESHOLD;

      // Entry: from outside to inside
      if (!wasInside && isInside && tIn < 0) {
        tIn = totalDistance;
      }

      // Exit: from inside to outside
      if (wasInside && !isInside && tIn >= 0) {
        double tOut = totalDistance;

        // Update cached intersections
        ray.pointAtParameter(tIn, tempWorldPoint);
        Vector3 normalIn = getNormalAt(tempWorldPoint);
        cachedInIntersection.set(tempWorldPoint, normalIn, tIn, this);

        ray.pointAtParameter(tOut, tempWorldPoint);
        Vector3 normalOut = getNormalAt(tempWorldPoint);
        cachedOutIntersection.set(tempWorldPoint, normalOut, tOut, this);

        // Update and return pre-allocated interval
        singleInterval.setEntry(tIn, cachedInIntersection);
        singleInterval.setExit(tOut, cachedOutIntersection);

        return java.util.Collections.singletonList(singleInterval);
      }

      // Move ray forward
      double step = Math.max(sdf, 0.01);
      tempDir.set(tempDirLocal);
      tempDir.multiply(step);
      tempPos.add(tempDir);
      totalDistance += step;

      if (totalDistance > MAX_MARCH_DISTANCE) {
        break;
      }
    }

    return EMPTY_INTERVAL_LIST;
  }

  // =========================================================================
  // NORMAL CALCULATION - GC-FREE
  // =========================================================================

  /**
   * Calculates the surface normal at intersection point using gradient of SDF. This method is 100%
   * garbage-free.
   *
   * @param worldPoint Intersection point in world space
   * @return Normalized surface normal in world space
   */
  @Override
  public Vector3 getNormalAt(Point3 worldPoint) {
    if (inverseTransform == null) {
      return FALLBACK_NORMAL;
    }

    // Transform intersection point to local space
    inverseTransform.transformPoint(worldPoint, tempLocalPoint);

    final double h = 0.0001;

    // Calculate SDF values using pre-allocated positions
    // X axis
    tempPos.set(tempLocalPoint);
    tempPos.x += h;
    sdfValues[0] = calculateSDF(tempPos);

    tempPos.set(tempLocalPoint);
    tempPos.x -= h;
    sdfValues[1] = calculateSDF(tempPos);

    // Y axis
    tempPos.set(tempLocalPoint);
    tempPos.y += h;
    sdfValues[2] = calculateSDF(tempPos);

    tempPos.set(tempLocalPoint);
    tempPos.y -= h;
    sdfValues[3] = calculateSDF(tempPos);

    // Z axis
    tempPos.set(tempLocalPoint);
    tempPos.z += h;
    sdfValues[4] = calculateSDF(tempPos);

    tempPos.set(tempLocalPoint);
    tempPos.z -= h;
    sdfValues[5] = calculateSDF(tempPos);

    // Compute gradient using central differences
    double nx = sdfValues[0] - sdfValues[1];
    double ny = sdfValues[2] - sdfValues[3];
    double nz = sdfValues[4] - sdfValues[5];

    tempLocalNormal.set(nx, ny, nz);
    tempLocalNormal.normalize();

    // Transform normal to world space using inverse transpose
    if (inverseTransposeTransformForNormal != null) {
      inverseTransposeTransformForNormal.transformVector(tempLocalNormal, tempWorldNormal);
      tempWorldNormal.normalize();
    } else {
      tempWorldNormal.set(tempLocalNormal);
    }

    return tempWorldNormal;
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
    return ("Torus: " + getTransform().toString());
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
    int tubeSegments = 12;

    for (int i = 0; i < segments; i++) {
      double u = 2 * Math.PI * i / segments;
      double u2 = 2 * Math.PI * (i + 1) / segments;

      for (int j = 0; j < tubeSegments; j++) {
        double v = 2 * Math.PI * j / tubeSegments;
        double v2 = 2 * Math.PI * (j + 1) / tubeSegments;

        Point3 p1 =
            new Point3(
                (majorRadius + minorRadius * Math.cos(v)) * Math.cos(u),
                minorRadius * Math.sin(v),
                (majorRadius + minorRadius * Math.cos(v)) * Math.sin(u));
        Point3 p2 =
            new Point3(
                (majorRadius + minorRadius * Math.cos(v2)) * Math.cos(u),
                minorRadius * Math.sin(v2),
                (majorRadius + minorRadius * Math.cos(v2)) * Math.sin(u));
        Point3 p3 =
            new Point3(
                (majorRadius + minorRadius * Math.cos(v2)) * Math.cos(u2),
                minorRadius * Math.sin(v2),
                (majorRadius + minorRadius * Math.cos(v2)) * Math.sin(u2));
        Point3 p4 =
            new Point3(
                (majorRadius + minorRadius * Math.cos(v)) * Math.cos(u2),
                minorRadius * Math.sin(v),
                (majorRadius + minorRadius * Math.cos(v)) * Math.sin(u2));

        triangles.add(new Triangle(p1, p2, p3));
        triangles.add(new Triangle(p1, p3, p4));
      }
    }

    cachedTriangles = triangles;
    return triangles;
  }

  @Override
  public List<Triangle> getTriangles() {
    return cachedTriangles;
  }

  // =========================================================================
  // TO STRING - GC acceptable (not called during render)
  // =========================================================================

  /**
   * Generates an OBJ format string representation of this Torus. This method is called only during
   * export, not during render. Uses robust vertex deduplication with tolerance to avoid degenerate
   * triangles.
   *
   * @return String in OBJ format
   */
  /**
   * Generates an OBJ format string representation of this Torus. Transform is applied directly to
   * vertices, not written as OBJ transform. Uses robust vertex deduplication with tolerance to
   * avoid degenerate triangles.
   *
   * @return String in OBJ format
   */
  @Override
  public String toObjString() {
    StringBuilder sb = new StringBuilder();

    sb.append("# Murat Ray Tracer Mesh File\n");
    sb.append("# Shape: Torus\n");
    sb.append("# Major Radius: ").append(majorRadius).append("\n");
    sb.append("# Minor Radius: ").append(minorRadius).append("\n");
    sb.append("# Generated: ").append(new java.util.Date()).append("\n");

    // Transform is applied to vertices, not written to OBJ
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
    sb.append("Torus " + name + " {\n");
    sb.append("    name = " + name + ";\n");
    sb.append("    majorRadius = " + majorRadius + ";\n");
    sb.append("    minorRadius = " + minorRadius + ";\n");
    sb.append("\n");
    sb.append("    " + getTransform().toString() + "\n");
    sb.append("\n");
    sb.append("    firstAnim_" + animTransforms[0].toString() + "\n");
    sb.append("    secondAnim_" + animTransforms[1].toString() + "\n");
    sb.append("    otherAnimationInfo = " + otherAnimationInfo + ";\n");
    sb.append("\n");
    sb.append("    shadowColor = " + ColorUtil.toColorString(shadowColor) + ";\n");
    sb.append("    shadowBias = " + shadowBias + ";\n");
    sb.append("\n");
    sb.append("    isVisibleSpecial = " + isVisibleSpecial + ";\n");
    sb.append("    isVisible = " + isVisible + ";\n");
    sb.append("    isShadowEnable = " + isShadowEnable + ";\n");
    sb.append("    isShadowOnly = " + isShadowOnly + ";\n");
    sb.append("    isReflective = " + isReflective + ";\n");
    sb.append("    isRefractive = " + isRefractive + ";\n");
    sb.append("\n");
    sb.append("    material = " + getMaterial().toString() + ";\n}");
    return sb.toString();
  }
}
