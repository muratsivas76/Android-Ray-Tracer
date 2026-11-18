package net.murat.shape;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.murat.material.Material;
import net.murat.math.*;

/**
 * Plane class represents an infinite plane in 3D space. Implements EMShape interface with full
 * transformation support.
 *
 * <p>This implementation is 100% garbage-free during render() execution. All temporary objects are
 * pre-allocated as instance fields and reused.
 */
public class Plane implements EMShape {

  // =========================================================================
  // CONSTANTS
  // =========================================================================

  /** Pre-allocated empty list for no-intersection cases. */
  private static final List<IntersectionInterval> EMPTY_INTERVAL_LIST =
      java.util.Collections.emptyList();

  // =========================================================================
  // PLANE PROPERTIES
  // =========================================================================

  private Material material;
  private Point3 pointOnPlane;
  private Vector3 normal;
  private Matrix4 transform;
  private Matrix4 inverseTransform;
  private Matrix4 inverseTransposeTransform;

  private int shadowColor = 0xFF000000;
  private double shadowBias = 0.001;
  private Matrix4[] animTransforms = new Matrix4[] {new Matrix4(), new Matrix4()};
  private String otherAnimationInfo = "veofr,0e0fr,veofr";
  private boolean isVisibleSpecial = true;
  private boolean isVisible = true;
  private boolean isShadowEnable = false;
  private boolean isShadowOnly = false;
  private boolean isReflective = true;
  private boolean isRefractive = true;

  private String name = "plane";

  // Mesh cache
  private List<Triangle> cachedTriangles;

  // =========================================================================
  // GC-FREE SCRATCH POOL - Zero allocation per intersection test
  // =========================================================================

  /** Temporary point for world space plane point. */
  private final Point3 worldPointOnPlane = new Point3();

  /** Temporary normal in world space. */
  private final Vector3 worldNormal = new Vector3();

  /** Temporary vector for direction operations. */
  private final Vector3 tempDir = new Vector3();

  /** Temporary vector for subtraction operations. */
  private final Vector3 tempVec = new Vector3();

  /** Temporary point for hit calculation. */
  private final Point3 tempHitPoint = new Point3();

  /** Temporary vector for normal calculation. */
  private final Vector3 tempNormal = new Vector3();

  /** Pre-allocated single hit for intersectAll. */
  private final Intersection singleHit = new Intersection();

  /** Pre-allocated single interval for intersectAll. */
  private final IntersectionInterval singleInterval =
      new IntersectionInterval(0, 0, singleHit, singleHit);

  // =========================================================================
  // CONSTRUCTORS
  // =========================================================================

  public Plane(Point3 pointOnPlane, Vector3 normal) {
    this.pointOnPlane = new Point3();
    this.pointOnPlane.set(pointOnPlane);
    this.normal = new Vector3();
    this.normal.set(normal).normalize();
    this.transform = new Matrix4();
    this.inverseTransform = new Matrix4();
    this.inverseTransposeTransform = new Matrix4();
  }

  // =========================================================================
  // EMShape INTERFACE METHODS
  // =========================================================================

  @Override
  public void setMaterial(Material material) {
    this.material = material;
  }

  @Override
  public Material getMaterial() {
    return this.material;
  }

  @Override
  public void setTransform(Matrix4 transform) {
    this.transform = new Matrix4(transform);
    this.inverseTransform = this.transform.inverse();
    this.inverseTransposeTransform = this.inverseTransform.inverseTransposeForNormal();
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
   * Intersects a ray with the plane and stores hit information. This method is 100% garbage-free.
   *
   * @param ray The ray to intersect with the plane (in world coordinates)
   * @param target The Intersection object to store hit information
   * @return true if intersection occurs, false otherwise
   */
  @Override
  public boolean intersect(Ray ray, Intersection target) {
    // Transform pointOnPlane to world space using pre-allocated scratch
    this.transform.transformPoint(this.pointOnPlane, worldPointOnPlane);

    // Transform normal to world space using pre-allocated scratch
    this.inverseTransposeTransform.transformVector(this.normal, worldNormal);
    worldNormal.normalize();

    // World space ray-plane intersection
    tempDir.set(ray.getDirection()).normalize();
    double denom = tempDir.dot(worldNormal);
    if (Math.abs(denom) < Ray.EPSILON) {
      return false;
    }

    tempVec.set(worldPointOnPlane).subtract(ray.getOrigin());
    double t = tempVec.dot(worldNormal) / denom;

    if (t <= Ray.EPSILON) {
      return false;
    }

    // Calculate hit point using pre-allocated scratch
    tempVec.set(ray.getDirection()).multiply(t);
    tempHitPoint.set(ray.getOrigin()).add(tempVec);

    target.set(tempHitPoint, worldNormal, t, this);
    return true;
  }

  /**
   * Calculates all intersection intervals between a ray and this plane.
   *
   * <p>Note: This method returns pre-allocated objects. The caller must NOT modify the returned
   * objects as they are reused.
   *
   * @param ray The ray to test, in world coordinates
   * @return A list of IntersectionInterval objects. Empty if no intersections
   */
  @Override
  public List<IntersectionInterval> intersectAll(Ray ray) {
    // Use pre-allocated singleHit
    if (intersect(ray, singleHit)) {
      // Update the pre-allocated interval with current hit data
      double t = singleHit.getT();
      singleInterval.setEntry(t, singleHit);
      singleInterval.setExit(t, singleHit);
      return java.util.Collections.singletonList(singleInterval);
    }
    return EMPTY_INTERVAL_LIST;
  }

  // =========================================================================
  // NORMAL CALCULATION - GC-FREE
  // =========================================================================

  /**
   * Returns the surface normal at a given point on the plane. This method is 100% garbage-free.
   *
   * @param worldPoint The point on the plane's surface in world coordinates
   * @return The normalized normal vector at that point in world coordinates
   */
  @Override
  public Vector3 getNormalAt(Point3 worldPoint) {
    // Transform normal to world space using pre-allocated scratch
    this.inverseTransposeTransform.transformVector(this.normal, tempNormal);
    tempNormal.normalize();
    return tempNormal;
  }

  /**
   * Returns the surface normal at a given point on the plane. This version stores the result in the
   * provided Vector3 to avoid allocation.
   *
   * @param worldPoint The point on the plane's surface in world coordinates
   * @param targetNormal The Vector3 to store the normal in
   * @return The normalized normal vector (same as targetNormal)
   */
  public Vector3 getNormalAt(Point3 worldPoint, Vector3 targetNormal) {
    this.inverseTransposeTransform.transformVector(this.normal, targetNormal);
    targetNormal.normalize();
    return targetNormal;
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
    return ("Plane: " + getTransform().toString());
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

    double size = 10.0; // Larger size for better visibility
    Point3 v1 = new Point3(-size / 2, 0, -size / 2);
    Point3 v2 = new Point3(size / 2, 0, -size / 2);
    Point3 v3 = new Point3(size / 2, 0, size / 2);
    Point3 v4 = new Point3(-size / 2, 0, size / 2);

    triangles.add(new Triangle(v1, v2, v3));
    triangles.add(new Triangle(v1, v3, v4));

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
   * Generates an OBJ format string representation of this Plane. This method is called only during
   * export, not during render. Uses robust vertex deduplication with tolerance to avoid degenerate
   * triangles.
   *
   * @return String in OBJ format
   */
  @Override
  public String toObjString() {
    StringBuilder sb = new StringBuilder();

    sb.append("# Murat Ray Tracer Mesh File\n");
    sb.append("# Shape: Plane\n");
    sb.append("# pointOnPlane: ").append(pointOnPlane).append("\n");
    sb.append("# normal: ").append(normal).append("\n");
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
    sb.append("Plane ").append(name).append(" {\n");
    sb.append("    name = ").append(name).append(";\n");
    sb.append("    pointOnPlane = ").append(pointOnPlane).append(";\n");
    sb.append("    normal = ").append(normal).append(";\n\n");
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
    sb.append("    isShadowEnable = ").append(isShadowEnable).append(";\n");
    sb.append("    isShadowOnly = ").append(isShadowOnly).append(";\n");
    sb.append("    isReflective = ").append(isReflective).append(";\n");
    sb.append("    isRefractive = ").append(isRefractive).append(";\n\n");
    sb.append("    material = ")
        .append(getMaterial() != null ? getMaterial().toString() : "null")
        .append(";\n}");
    return sb.toString();
  }
}
