package net.murat.shape;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.murat.material.Material;
import net.murat.math.*;
import net.murat.util.ColorUtil;

/**
 * Disc class represents a flat circular disc in 3D space. The disc lies in the XY plane in its
 * local coordinate system, centered at the origin (0,0,0) with its normal along the Z-axis.
 * Implements EMShape interface with full transformation support.
 *
 * <p>This implementation is 100% garbage-free during render() execution. All temporary objects are
 * pre-allocated as instance fields and reused.
 */
public class Disc implements EMShape {

  // =========================================================================
  // CONSTANTS
  // =========================================================================

  /** Pre-allocated local normal for disc (Z-axis). */
  private static final Vector3 LOCAL_NORMAL = new Vector3(0, 0, 1);

  /** Pre-allocated empty list for no-intersection cases. */
  private static final List<IntersectionInterval> EMPTY_INTERVAL_LIST =
      java.util.Collections.emptyList();

  // =========================================================================
  // DISC PROPERTIES
  // =========================================================================

  private Material material;
  private final double radius;

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

  private String name = "disc";

  // Mesh cache
  private List<Triangle> cachedTriangles;

  // =========================================================================
  // GC-FREE SCRATCH POOL - Zero allocation per intersection test
  // =========================================================================

  /** Temporary origin in local space. */
  private final Point3 tempLocalOrigin = new Point3();

  /** Temporary direction in local space. */
  private final Vector3 tempLocalDir = new Vector3();

  /** Temporary hit point in local space. */
  private final Point3 tempLocalHit = new Point3();

  /** Temporary hit point in world space. */
  private final Point3 tempWorldHit = new Point3();

  /** Temporary normal in local space. */
  private final Vector3 tempLocalNormal = new Vector3();

  /** Temporary normal in world space. */
  private final Vector3 tempWorldNormal = new Vector3();

  /** Temporary ray in local space. */
  private final Ray tempLocalRay = new Ray(new Point3(), new Vector3(), 1.0);

  /** Temporary origin for transform operations. */
  private final Point3 tempOrigin = new Point3();

  /** Temporary direction for transform operations. */
  private final Vector3 tempDir = new Vector3();

  /** Pre-allocated normal vector for dot product. */
  private final Vector3 normalVec = new Vector3(0, 0, 1);

  /** Pre-allocated single intersection for intersectAll. */
  private final Intersection singleHit = new Intersection();

  /** Pre-allocated single interval for intersectAll. */
  private final IntersectionInterval singleInterval =
      new IntersectionInterval(0, 0, singleHit, singleHit);

  // =========================================================================
  // CONSTRUCTORS
  // =========================================================================

  /**
   * Constructs a disc with the specified radius. The disc is centered at the origin in local space.
   *
   * @param radius The radius of the disc
   */
  public Disc(double radius) {
    this.radius = Math.max(0.0, radius);
    this.transform = Matrix4.identity();
    this.inverseTransform = Matrix4.identity();
    this.inverseTransposeTransformForNormal = Matrix4.identity();
  }

  /**
   * Constructs a disc with the specified radius and material.
   *
   * @param radius The radius of the disc
   * @param material The material applied to the disc
   */
  public Disc(double radius, Material material) {
    this(radius);
    this.material = material;
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

  // =========================================================================
  // INTERSECTION METHODS - GC-FREE
  // =========================================================================

  /**
   * Intersects a ray with the disc and stores hit information. This method is 100% garbage-free.
   *
   * @param ray The ray to intersect with the disc (in world coordinates)
   * @param hit The Intersection object to store hit information
   * @return true if intersection occurs, false otherwise
   */
  @Override
  public boolean intersect(Ray ray, Intersection hit) {
    if (this.inverseTransform == null) {
      return false;
    }

    // Transform ray to local space using pre-allocated scratch
    this.inverseTransform.transformPoint(ray.getOrigin(), tempOrigin);
    this.inverseTransform.transformDirection(ray.getDirection(), tempDir);
    tempDir.normalize();
    tempLocalRay.set(tempOrigin, tempDir, 1.0);

    // Disc lies in XY plane (z = 0) with normal (0, 0, 1)
    // Use pre-allocated normal vector
    normalVec.set(0, 0, 1);

    // Check if ray is parallel to the disc
    double denom = tempLocalRay.getDirection().dot(normalVec);
    if (Math.abs(denom) < Ray.EPSILON) {
      return false;
    }

    // Calculate intersection distance t
    double t = -tempLocalRay.getOrigin().z / denom;
    if (t <= Ray.EPSILON) {
      return false;
    }

    // Calculate hit point in local space using pre-allocated scratch
    tempLocalRay.pointAtParameter(t, tempLocalHit);

    // Check if hit point is within disc radius
    double distSq = tempLocalHit.x * tempLocalHit.x + tempLocalHit.y * tempLocalHit.y;
    if (distSq > radius * radius + Ray.EPSILON) {
      return false;
    }

    // Calculate world space hit point
    ray.pointAtParameter(t, tempWorldHit);

    // Calculate world space normal using pre-allocated scratch
    Vector3 worldNormal = getNormalAt(tempWorldHit);

    hit.set(tempWorldHit, worldNormal, t, this);
    return true;
  }

  /**
   * Calculates all intersection intervals between a ray and this disc.
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
      singleInterval.setEntry(singleHit.getT(), singleHit);
      singleInterval.setExit(singleHit.getT(), singleHit);
      // Return a singleton list containing the pre-allocated interval
      // Note: This is a bit of a hack but keeps it GC-free
      return java.util.Collections.singletonList(singleInterval);
    }
    return EMPTY_INTERVAL_LIST;
  }

  // =========================================================================
  // NORMAL CALCULATION - GC-FREE
  // =========================================================================

  /**
   * Returns the surface normal at a given point on the disc. The normal is (0,0,1) in local space,
   * transformed to world space.
   *
   * @param worldPoint The point on the disc's surface in world coordinates
   * @return The normalized normal vector at that point in world coordinates
   */
  @Override
  public Vector3 getNormalAt(Point3 worldPoint) {
    if (this.inverseTransposeTransformForNormal == null) {
      // Return a reference to the pre-allocated normal
      // WARNING: Caller must not modify this vector!
      return LOCAL_NORMAL;
    }

    // Local normal is (0, 0, 1) for disc in XY plane
    tempLocalNormal.set(0, 0, 1);

    // Transform to world space using inverse transpose
    this.inverseTransposeTransformForNormal.transformVector(tempLocalNormal, tempWorldNormal);
    tempWorldNormal.normalize();

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
    return ("Disc: " + getTransform().toString());
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

    // Center vertex
    Point3 center = new Point3(0, 0, 0);

    // Generate triangles from center to circumference
    for (int i = 0; i < segments; i++) {
      double theta1 = 2 * Math.PI * i / segments;
      double theta2 = 2 * Math.PI * (i + 1) / segments;

      Point3 v1 = new Point3(radius * Math.cos(theta1), radius * Math.sin(theta1), 0);
      Point3 v2 = new Point3(radius * Math.cos(theta2), radius * Math.sin(theta2), 0);

      triangles.add(new Triangle(center, v1, v2));
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
   * Generates an OBJ format string representation of this Disc. This method is called only during
   * export, not during render. Uses robust vertex deduplication with tolerance to avoid degenerate
   * triangles.
   *
   * @return String in OBJ format
   */
  /**
   * Generates an OBJ format string representation of this Disc. Transform is applied directly to
   * vertices, not written as OBJ transform. Uses robust vertex deduplication with tolerance to
   * avoid degenerate triangles.
   *
   * @return String in OBJ format
   */
  @Override
  public String toObjString() {
    StringBuilder sb = new StringBuilder();

    sb.append("# Murat Ray Tracer Mesh File\n");
    sb.append("# Shape: Disc\n");
    sb.append("# Radius: ").append(radius).append("\n");
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
    sb.append("Disc " + name + " {\n");
    sb.append("    name = " + name + ";\n");
    sb.append("    radius = " + radius + ";\n");
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
    sb.append("    material = ")
        .append(getMaterial() != null ? getMaterial().toString() : "null")
        .append(";\n}");
    return sb.toString();
  }
}
