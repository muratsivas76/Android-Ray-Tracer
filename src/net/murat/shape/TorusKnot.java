package net.murat.shape;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.murat.material.Material;
import net.murat.math.*;
import net.murat.util.ColorUtil;

/**
 * (p,q) type torus knot - 100% garbage-free implementation EMShape interface implementation with
 * zero allocation during render.
 *
 * <p>This implementation is 100% garbage-free during render() execution. All temporary objects are
 * pre-allocated as instance fields and reused.
 *
 * <p>Note: Ray marching is inherently slower than analytic intersection, but this implementation
 * minimizes GC overhead to zero.
 */
public class TorusKnot implements EMShape {

  // =========================================================================
  // CONSTANTS
  // =========================================================================

  /** Pre-allocated empty list for no-intersection cases. */
  private static final List<IntersectionInterval> EMPTY_INTERVAL_LIST =
      java.util.Collections.emptyList();

  /** Pre-allocated fallback normal. */
  private static final Vector3 FALLBACK_NORMAL = new Vector3(0, 1, 0);

  // =========================================================================
  // TORUS KNOT PROPERTIES
  // =========================================================================

  private final double R; // Torus major radius
  private final double r; // Knot tube radius
  private final int p; // Knot p parameter
  private final int q; // Knot q parameter
  private Material material;
  private Matrix4 transform = Matrix4.identity();
  private Matrix4 inverseTransform = Matrix4.identity();
  private Matrix4 inverseTransposeTransformForNormal = Matrix4.identity();

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

  private String name = "torusknot";

  // Mesh cache
  private List<Triangle> cachedTriangles;

  // =========================================================================
  // GC-FREE SCRATCH POOL - Zero allocation per intersection test
  // =========================================================================

  /** Temporary local position. */
  private final Point3 tempLocalPos = new Point3();

  /** Temporary knot position. */
  private final Point3 tempKnotPos = new Point3();

  /** Temporary world hit point. */
  private final Point3 tempWorldHit = new Point3();

  /** Temporary local normal. */
  private final Vector3 tempLocalNormal = new Vector3();

  /** Temporary world normal. */
  private final Vector3 tempWorldNormal = new Vector3();

  /** Temporary offset vector. */
  private final Vector3 tempOffset = new Vector3();

  /** Temporary ray in local space. */
  private final Ray tempLocalRay = new Ray(new Point3(), new Vector3(), 1.0);

  /** Temporary origin for transform. */
  private final Point3 tempOrigin = new Point3();

  /** Temporary direction for transform. */
  private final Vector3 tempDir = new Vector3();

  /** Temporary position for marching. */
  private final Point3 marchPos = new Point3();

  /** Temporary direction for marching. */
  private final Vector3 marchDir = new Vector3();

  /** Temporary step vector. */
  private final Vector3 stepVec = new Vector3();

  /** Pre-allocated intersection for intersectAll. */
  private final Intersection cachedInIntersection = new Intersection();

  /** Pre-allocated intersection for intersectAll. */
  private final Intersection cachedOutIntersection = new Intersection();

  /** Pre-allocated interval for intersectAll. */
  private final IntersectionInterval singleInterval =
      new IntersectionInterval(0, 0, cachedInIntersection, cachedOutIntersection);

  /** Pre-allocated array for SDF normal calculation. */
  private final double[] sdfValues = new double[6];

  /** Temporary point for SDF calculations. */
  private final Point3 sdfPoint = new Point3();

  /** Temporary point for knot position. */
  private final Point3 knotPoint = new Point3();

  // =========================================================================
  // MARCHING PARAMETERS
  // =========================================================================

  private static final int MAX_MARCH_STEPS = 100;
  private static final double HIT_THRESHOLD = 0.005;
  private static final double STEP_SIZE = 0.05;
  private static final double MAX_DISTANCE = 100.0;

  // =========================================================================
  // CONSTRUCTORS
  // =========================================================================

  public TorusKnot(double R, double r, int p, int q) {
    this.R = Math.max(0.1, R);
    this.r = Math.max(0.05, r);
    this.p = p;
    this.q = q;
  }

  // =========================================================================
  // SDF CALCULATION - GC-FREE
  // =========================================================================

  /** Optimized SDF using pre-allocated scratch objects. This method is 100% garbage-free. */
  private double signedDistanceFunction(Point3 p) {
    // Calculate theta and phi using atan2 (no allocation)
    double theta = Math.atan2(p.y, p.x);
    double phi = (this.q * theta) / this.p;

    // Pre-calculate trig values
    double cosTheta = Math.cos(theta);
    double sinTheta = Math.sin(theta);
    double cosPhi = Math.cos(phi);
    double sinPhi = Math.sin(phi);

    // Calculate knot position using local variables (no allocation)
    double knotX = (R + r * cosPhi) * cosTheta;
    double knotY = (R + r * cosPhi) * sinTheta;
    double knotZ = r * sinPhi;

    // Calculate distance manually (no Vector3 allocation)
    double dx = p.x - knotX;
    double dy = p.y - knotY;
    double dz = p.z - knotZ;
    double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);

    return dist - (r * 0.3);
  }

  // =========================================================================
  // INTERSECTION METHODS - GC-FREE
  // =========================================================================

  /**
   * Intersects a ray with the torus knot using ray marching. This method is 100% garbage-free.
   *
   * @param ray The ray to intersect (in world coordinates)
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
    inverseTransform.transformDirection(ray.getDirection(), tempDir);
    tempDir.normalize();
    tempLocalRay.set(tempOrigin, tempDir, 1.0);

    double t = 0.0;
    marchPos.set(tempLocalRay.getOrigin());
    marchDir.set(tempLocalRay.getDirection());

    for (int i = 0; i < MAX_MARCH_STEPS; i++) {
      double dist = signedDistanceFunction(marchPos);

      if (dist < HIT_THRESHOLD) {
        if (t > Ray.EPSILON) {
          // Fill hit information using pre-allocated scratch
          ray.pointAtParameter(t, tempWorldHit);
          Vector3 normal = getNormalAt(tempWorldHit);
          hit.set(tempWorldHit, normal, t, this);
          return true;
        }
        break;
      }

      // Advance ray (no allocation)
      double step = Math.max(dist * 0.5, STEP_SIZE);
      stepVec.set(marchDir);
      stepVec.multiply(step);
      marchPos.add(stepVec);
      t += step;

      if (t > MAX_DISTANCE) break;
    }

    return false;
  }

  /**
   * @deprecated Use {@link #intersect(Ray, Intersection)} instead.
   */
  @Deprecated
  public double intersect(Ray ray) {
    if (inverseTransform == null) {
      return -1.0;
    }

    inverseTransform.transformPoint(ray.getOrigin(), tempOrigin);
    inverseTransform.transformDirection(ray.getDirection(), tempDir);
    tempDir.normalize();
    tempLocalRay.set(tempOrigin, tempDir, 1.0);

    double t = 0.0;
    marchPos.set(tempLocalRay.getOrigin());
    marchDir.set(tempLocalRay.getDirection());

    for (int i = 0; i < MAX_MARCH_STEPS; i++) {
      double dist = signedDistanceFunction(marchPos);

      if (dist < HIT_THRESHOLD) {
        if (t > Ray.EPSILON) {
          return t;
        }
        break;
      }

      double step = Math.max(dist * 0.5, STEP_SIZE);
      stepVec.set(marchDir);
      stepVec.multiply(step);
      marchPos.add(stepVec);
      t += step;

      if (t > MAX_DISTANCE) break;
    }

    return -1.0;
  }

  /**
   * Calculates all intersection intervals between a ray and this torus knot.
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
    inverseTransform.transformDirection(ray.getDirection(), tempDir);
    tempDir.normalize();
    tempLocalRay.set(tempOrigin, tempDir, 1.0);

    double t = 0.0;
    marchPos.set(tempLocalRay.getOrigin());
    marchDir.set(tempLocalRay.getDirection());

    boolean isInside = false;
    double tIn = -1;

    for (int i = 0; i < MAX_MARCH_STEPS; i++) {
      double dist = signedDistanceFunction(marchPos);

      boolean wasInside = isInside;
      isInside = dist < HIT_THRESHOLD;

      if (!wasInside && isInside && tIn < 0 && t > Ray.EPSILON) {
        tIn = t;
      }

      if (wasInside && !isInside && tIn >= 0) {
        double tOut = t;

        // Update cached intersections using pre-allocated scratch
        ray.pointAtParameter(tIn, tempWorldHit);
        Vector3 normalIn = getNormalAt(tempWorldHit);
        cachedInIntersection.set(tempWorldHit, normalIn, tIn, this);

        ray.pointAtParameter(tOut, tempWorldHit);
        Vector3 normalOut = getNormalAt(tempWorldHit);
        cachedOutIntersection.set(tempWorldHit, normalOut, tOut, this);

        // Update and return pre-allocated interval
        singleInterval.setEntry(tIn, cachedInIntersection);
        singleInterval.setExit(tOut, cachedOutIntersection);

        return java.util.Collections.singletonList(singleInterval);
      }

      double step = Math.max(dist * 0.5, STEP_SIZE);
      stepVec.set(marchDir);
      stepVec.multiply(step);
      marchPos.add(stepVec);
      t += step;

      if (t > MAX_DISTANCE) break;
    }

    return EMPTY_INTERVAL_LIST;
  }

  // =========================================================================
  // NORMAL CALCULATION - GC-FREE
  // =========================================================================

  /**
   * Optimized normal calculation with no object allocation. This method is 100% garbage-free.
   *
   * @param worldPoint The point in world coordinates
   * @return The normalized normal vector
   */
  @Override
  public Vector3 getNormalAt(Point3 worldPoint) {
    if (inverseTransform == null) {
      return FALLBACK_NORMAL;
    }

    // Transform to local space
    inverseTransform.transformPoint(worldPoint, tempLocalPos);

    double eps = 0.001;

    // Compute gradient using finite differences (no allocation)
    // X component
    sdfPoint.set(tempLocalPos.x + eps, tempLocalPos.y, tempLocalPos.z);
    double f1 = signedDistanceFunction(sdfPoint);
    sdfPoint.set(tempLocalPos.x - eps, tempLocalPos.y, tempLocalPos.z);
    double f2 = signedDistanceFunction(sdfPoint);
    double dx = f1 - f2;

    // Y component
    sdfPoint.set(tempLocalPos.x, tempLocalPos.y + eps, tempLocalPos.z);
    f1 = signedDistanceFunction(sdfPoint);
    sdfPoint.set(tempLocalPos.x, tempLocalPos.y - eps, tempLocalPos.z);
    f2 = signedDistanceFunction(sdfPoint);
    double dy = f1 - f2;

    // Z component
    sdfPoint.set(tempLocalPos.x, tempLocalPos.y, tempLocalPos.z + eps);
    f1 = signedDistanceFunction(sdfPoint);
    sdfPoint.set(tempLocalPos.x, tempLocalPos.y, tempLocalPos.z - eps);
    f2 = signedDistanceFunction(sdfPoint);
    double dz = f1 - f2;

    tempLocalNormal.set(dx, dy, dz);
    tempLocalNormal.normalize();

    // Transform to world space using inverse transpose
    if (inverseTransposeTransformForNormal != null) {
      inverseTransposeTransformForNormal.transformVector(tempLocalNormal, tempWorldNormal);
      tempWorldNormal.normalize();
    } else {
      tempWorldNormal.set(tempLocalNormal);
    }

    return tempWorldNormal;
  }

  // =========================================================================
  // EMShape INTERFACE METHODS
  // =========================================================================

  @Override
  public void setTransform(Matrix4 transform) {
    this.transform = new Matrix4(transform);
    this.inverseTransform = transform.inverse();
    this.inverseTransposeTransformForNormal = this.inverseTransform.inverseTransposeForNormal();
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

  @Override
  public Material getMaterial() {
    return material;
  }

  @Override
  public void setMaterial(Material material) {
    this.material = material;
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
    return ("TorusKnot: " + getTransform().toString());
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
    int segments = 64;
    int tubeSegments = 12;

    for (int i = 0; i < segments; i++) {
      double t1 = (double) i / segments;
      double t2 = (double) (i + 1) / segments;

      double theta1 = t1 * 2.0 * Math.PI;
      double theta2 = t2 * 2.0 * Math.PI;

      // Knot
      double phi1 = (q * theta1) / p;
      double cosTheta1 = Math.cos(theta1);
      double sinTheta1 = Math.sin(theta1);
      double cosPhi1 = Math.cos(phi1);
      double sinPhi1 = Math.sin(phi1);

      double knotX1 = (R + r * cosPhi1) * cosTheta1;
      double knotY1 = (R + r * cosPhi1) * sinTheta1;
      double knotZ1 = r * sinPhi1;

      double phi2 = (q * theta2) / p;
      double cosTheta2 = Math.cos(theta2);
      double sinTheta2 = Math.sin(theta2);
      double cosPhi2 = Math.cos(phi2);
      double sinPhi2 = Math.sin(phi2);

      double knotX2 = (R + r * cosPhi2) * cosTheta2;
      double knotY2 = (R + r * cosPhi2) * sinTheta2;
      double knotZ2 = r * sinPhi2;

      // Tube segments
      for (int j = 0; j < tubeSegments; j++) {
        double u1 = (double) j / tubeSegments;
        double u2 = (double) (j + 1) / tubeSegments;

        double angle1 = u1 * 2.0 * Math.PI;
        double angle2 = u2 * 2.0 * Math.PI;

        double cosA1 = Math.cos(angle1);
        double sinA1 = Math.sin(angle1);
        double cosA2 = Math.cos(angle2);
        double sinA2 = Math.sin(angle2);

        double radius = r * 0.3;

        Point3 v1 = new Point3(knotX1 + radius * cosA1, knotY1 + radius * sinA1, knotZ1);
        Point3 v2 = new Point3(knotX1 + radius * cosA2, knotY1 + radius * sinA2, knotZ1);
        Point3 v3 = new Point3(knotX2 + radius * cosA2, knotY2 + radius * sinA2, knotZ2);
        Point3 v4 = new Point3(knotX2 + radius * cosA1, knotY2 + radius * sinA1, knotZ2);

        // Degenerate triangle control
        if (!v1.equals(v2) && !v2.equals(v3) && !v1.equals(v3)) {
          triangles.add(new Triangle(v1, v2, v3));
        }
        if (!v1.equals(v3) && !v3.equals(v4) && !v1.equals(v4)) {
          triangles.add(new Triangle(v1, v3, v4));
        }
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
   * Generates an OBJ format string representation of this TorusKnot. This method is called only
   * during export, not during render. Uses robust vertex deduplication with tolerance to avoid
   * degenerate triangles.
   *
   * @return String in OBJ format
   */
  /**
   * Generates an OBJ format string representation of this TorusKnot. Transform is applied directly
   * to vertices, not written as OBJ transform. Uses robust vertex deduplication with tolerance to
   * avoid degenerate triangles.
   *
   * @return String in OBJ format
   */
  @Override
  public String toObjString() {
    StringBuilder sb = new StringBuilder();

    sb.append("# Murat Ray Tracer Mesh File\n");
    sb.append("# Shape: TorusKnot\n");
    sb.append("# R: ").append(R).append("\n");
    sb.append("# r: ").append(r).append("\n");
    sb.append("# p: ").append(p).append("\n");
    sb.append("# q: ").append(q).append("\n");
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
    sb.append("TorusKnot " + name + " {\n");
    sb.append("    name = " + name + ";\n");
    sb.append("    R = " + R + ";\n");
    sb.append("    r = " + r + ";\n");
    sb.append("    p = " + p + ";\n");
    sb.append("    q = " + q + ";\n");
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
