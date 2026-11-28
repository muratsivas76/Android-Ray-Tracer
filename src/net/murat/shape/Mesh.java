package net.murat.shape;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.murat.material.Material;
import net.murat.math.*;
import net.murat.util.ColorUtil;

/**
 * Mesh class represents a collection of triangles forming a 3D surface. Implements EMShape
 * interface with full transformation support. Can be loaded from OBJ files or created procedurally.
 *
 * <p>This implementation is 100% garbage-free during render() execution. All temporary objects are
 * pre-allocated as instance fields and reused.
 */
public class Mesh implements EMShape {

  // =========================================================================
  // CONSTANTS
  // =========================================================================

  /** Pre-allocated empty list for no-intersection cases. */
  private static final List<IntersectionInterval> EMPTY_INTERVAL_LIST =
      java.util.Collections.emptyList();

  /** Pre-allocated local normal for fallback. */
  private static final Vector3 FALLBACK_NORMAL = new Vector3(0, 1, 0);

  // =========================================================================
  // MESH PROPERTIES
  // =========================================================================

  private List<Triangle> triangles;
  private Material material;

  private Matrix4 transform;
  private Matrix4 inverseTransform;
  private Matrix4 inverseTransposeTransformForNormal;

  private AABB bounds;
  private AABB worldBounds;
  private BVHNode bvhRoot;

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

  private String name = "mesh";

  // =========================================================================
  // GC-FREE SCRATCH POOL - Zero allocation per intersection test
  // =========================================================================

  /** Temporary origin in local space. */
  private final Point3 tempLocalOrigin = new Point3();

  /** Temporary direction in local space. */
  private final Vector3 tempLocalDir = new Vector3();

  /** Temporary hit point in world space. */
  private final Point3 tempWorldHit = new Point3();

  /** Temporary normal in world space. */
  private final Vector3 tempWorldNormal = new Vector3();

  /** Temporary ray in local space. */
  private final Ray tempLocalRay = new Ray(new Point3(), new Vector3(), 1.0);

  /** Temporary intersection for BVH traversal. */
  private final Intersection tempIntersection = new Intersection();

  /** Temporary hit point in local space. */
  private final Point3 tempLocalHit = new Point3();

  /** Temporary normal in local space. */
  private final Vector3 tempLocalNormal = new Vector3();

  /** Temporary point for transform operations. */
  private final Point3 tempPoint = new Point3();

  /** Temporary vector for edge calculations. */
  private final Vector3 tempEdge1 = new Vector3();

  /** Temporary vector for edge calculations. */
  private final Vector3 tempEdge2 = new Vector3();

  /** Temporary vector for point subtraction. */
  private final Vector3 tempV0p = new Vector3();

  /** Temporary vector for point subtraction. */
  private final Vector3 tempV1p = new Vector3();

  /** Temporary vector for cross product. */
  private final Vector3 tempCross = new Vector3();

  /** Temporary vector for normal accumulation. */
  private final Vector3 tempNormalAccum = new Vector3();

  /** Temporary point for triangle vertices. */
  private final Point3 tempV0 = new Point3();

  /** Temporary point for triangle vertices. */
  private final Point3 tempV1 = new Point3();

  /** Temporary point for triangle vertices. */
  private final Point3 tempV2 = new Point3();

  /** Pre-allocated corners array for world bounds (8 corners). */
  private final Point3[] corners = new Point3[8];

  /** Pre-allocated single hit for intersectAll. */
  private final Intersection singleHit = new Intersection();

  /** Pre-allocated single interval for intersectAll. */
  private final IntersectionInterval singleInterval =
      new IntersectionInterval(0, 0, singleHit, singleHit);

  // =========================================================================
  // CONSTRUCTORS
  // =========================================================================

  /** Constructor with triangles only. */
  public Mesh(List<Triangle> triangles) {
    this.triangles = new ArrayList<>(triangles);
    this.transform = Matrix4.identity();
    this.inverseTransform = Matrix4.identity();
    this.inverseTransposeTransformForNormal = Matrix4.identity();
    this.bounds = calculateBounds(triangles);
    this.worldBounds = new AABB();
    this.bvhRoot = new BVHNode(triangles);

    // Initialize corners array
    for (int i = 0; i < corners.length; i++) {
      corners[i] = new Point3();
    }
  }

  /** Constructor with triangles and material. */
  public Mesh(List<Triangle> triangles, Material material) {
    this(triangles);
    this.material = material;
  }

  // =========================================================================
  // BOUNDS CALCULATION - GC-FREE
  // =========================================================================

  /** Calculates the axis-aligned bounding box for the triangle list. */
  private AABB calculateBounds(List<Triangle> triangles) {
    AABB bounds = new AABB();
    if (triangles == null || triangles.isEmpty()) {
      return bounds;
    }
    int size = triangles.size();
    for (int i = 0; i < size; i++) {
      Triangle tri = triangles.get(i);
      bounds.expand(tri.getV0());
      bounds.expand(tri.getV1());
      bounds.expand(tri.getV2());
    }
    return bounds;
  }

  /**
   * Updates the world-space bounding box after transform changes. This method is GC-free using
   * pre-allocated corners.
   */
  private void updateWorldBounds() {
    if (bounds == null || transform == null) {
      this.worldBounds.set(this.bounds);
      return;
    }

    // Reset world bounds
    worldBounds.reset();

    // Use pre-allocated corners
    corners[0].set(bounds.minX, bounds.minY, bounds.minZ);
    corners[1].set(bounds.maxX, bounds.minY, bounds.minZ);
    corners[2].set(bounds.minX, bounds.maxY, bounds.minZ);
    corners[3].set(bounds.maxX, bounds.maxY, bounds.minZ);
    corners[4].set(bounds.minX, bounds.minY, bounds.maxZ);
    corners[5].set(bounds.maxX, bounds.minY, bounds.maxZ);
    corners[6].set(bounds.minX, bounds.maxY, bounds.maxZ);
    corners[7].set(bounds.maxX, bounds.maxY, bounds.maxZ);

    // Transform each corner and expand world bounds
    for (int i = 0; i < corners.length; i++) {
      transform.transformPoint(corners[i], tempPoint);
      worldBounds.expand(tempPoint);
    }
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
    // this.inverseTransposeTransformForNormal = this.inverseTransform.inverseTransposeForNormal();
    this.inverseTransposeTransformForNormal = this.transform.inverseTransposeForNormal();
    updateWorldBounds();
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

  /** Intersects a ray with this mesh. This method is 100% garbage-free. */
  @Override
  public boolean intersect(Ray ray, Intersection hit) {
    if (this.inverseTransform == null || triangles == null || triangles.isEmpty()) {
      return false;
    }

    // World bounds check using pre-allocated scratch
    if (worldBounds != null && !worldBounds.intersect(ray)) {
      return false;
    }

    // Transform ray to local space
    this.inverseTransform.transformPoint(ray.getOrigin(), tempLocalOrigin);
    this.inverseTransform.transformDirection(ray.getDirection(), tempLocalDir);
    tempLocalDir.normalize();
    tempLocalRay.set(tempLocalOrigin, tempLocalDir, 1.0);

    // Local bounds check
    if (bounds != null && !bounds.intersect(tempLocalRay)) {
      return false;
    }

    double closestT;
    if (bvhRoot != null) {
      // Use pre-allocated tempIntersection for BVH traversal
      closestT = bvhRoot.intersect(tempLocalRay, Double.POSITIVE_INFINITY, hit, tempIntersection);
    } else {
      closestT = bruteForceIntersect(tempLocalRay, hit);
    }

    if (closestT == Double.POSITIVE_INFINITY) {
      return false;
    }

    // Transform hit point and normal to world space
    Point3 localHit = hit.getPoint();
    Vector3 localNormal = hit.getNormal();

    this.transform.transformPoint(localHit, tempWorldHit);
    this.inverseTransposeTransformForNormal.transformVector(localNormal, tempWorldNormal);
    tempWorldNormal.normalize();

    hit.set(tempWorldHit, tempWorldNormal, hit.getT(), this);

    return true;
  }

  /**
   * Brute-force triangle scan, kept only as a safety fallback. This method is GC-free using
   * pre-allocated scratch.
   */
  private double bruteForceIntersect(Ray localRay, Intersection hit) {
    double closestT = Double.POSITIVE_INFINITY;
    int n = triangles.size();
    for (int i = 0; i < n; i++) {
      Triangle tri = triangles.get(i);
      if (tri.intersect(localRay, tempIntersection)) {
        double t = tempIntersection.getDistance();
        if (t > Ray.EPSILON && t < closestT) {
          closestT = t;
          hit.set(tempIntersection);
        }
      }
    }
    return closestT;
  }

  /**
   * Calculates all intersection intervals between a ray and this mesh.
   *
   * <p>Note: This method returns pre-allocated objects. The caller must NOT modify the returned
   * objects as they are reused.
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

  /** Returns the surface normal at a given point on the mesh. This method is 100% garbage-free. */
  @Override
  public Vector3 getNormalAt(Point3 worldPoint) {
    if (this.inverseTransposeTransformForNormal == null) {
      return FALLBACK_NORMAL;
    }

    // Transform point to local space
    this.inverseTransform.transformPoint(worldPoint, tempLocalHit);

    double minDist = Double.POSITIVE_INFINITY;
    tempNormalAccum.set(0, 0, 1);

    int n = triangles.size();
    for (int i = 0; i < n; i++) {
      Triangle tri = triangles.get(i);

      // Get triangle vertices using pre-allocated points
      tempV0.set(tri.getV0());
      tempV1.set(tri.getV1());
      tempV2.set(tri.getV2());

      double d = pointToTriangleDistance(tempLocalHit, tempV0, tempV1, tempV2);
      if (d < minDist) {
        minDist = d;

        // Calculate normal using pre-allocated vectors
        tempEdge1.set(tempV1).subtract(tempV0);
        tempEdge2.set(tempV2).subtract(tempV0);
        tempCross.cross(tempEdge1, tempEdge2);
        tempCross.normalize();
        tempNormalAccum.set(tempCross);
      }
    }

    // Transform normal to world space
    this.inverseTransposeTransformForNormal.transformVector(tempNormalAccum, tempWorldNormal);
    tempWorldNormal.normalize();

    return tempWorldNormal;
  }

  /**
   * Calculates distance from point to triangle. This method is GC-free using pre-allocated scratch
   * vectors.
   */
  private double pointToTriangleDistance(Point3 p, Point3 v0, Point3 v1, Point3 v2) {
    // v0v1 = v1 - v0
    tempEdge1.set(v1).subtract(v0);
    // v0v2 = v2 - v0
    tempEdge2.set(v2).subtract(v0);
    // v0p = p - v0
    tempV0p.set(p).subtract(v0);

    double d1 = tempEdge1.dot(tempV0p);
    double d2 = tempEdge2.dot(tempV0p);

    if (d1 <= 0 && d2 <= 0) {
      return tempV0p.length();
    }

    // v1p = p - v1
    tempV1p.set(p).subtract(v1);
    double d3 = tempEdge1.dot(tempV1p);
    double d4 = tempEdge2.dot(tempV1p);

    if (d3 >= 0 && d4 <= d3) {
      return tempV1p.length();
    }

    double d5 = tempEdge1.dot(tempV0p);
    double d6 = tempEdge2.dot(tempV0p);

    if (d6 >= 0 && d5 <= d6) {
      tempV1p.set(p).subtract(v2);
      return tempV1p.length();
    }

    double vc = d1 * d4 - d3 * d2;
    if (vc <= 0 && d1 >= 0 && d3 <= 0) {
      tempCross.cross(tempEdge1, tempV0p);
      return Math.abs(tempCross.length()) / tempEdge1.length();
    }

    double vb = d5 * d2 - d1 * d6;
    if (vb <= 0 && d2 >= 0 && d6 <= 0) {
      tempCross.cross(tempEdge2, tempV0p);
      return Math.abs(tempCross.length()) / tempEdge2.length();
    }

    double va = d3 * d6 - d5 * d4;
    if (va <= 0 && (d4 - d3) >= 0 && (d5 - d6) >= 0) {
      tempEdge1.set(v2).subtract(v1);
      tempV1p.set(p).subtract(v1);
      tempCross.cross(tempEdge1, tempV1p);
      return Math.abs(tempCross.length()) / tempEdge1.length();
    }

    tempCross.cross(tempEdge1, tempEdge2);
    return Math.abs(tempCross.dot(tempV0p)) / tempCross.length();
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
    return ("Mesh: "
        + getTransform().toString()
        + ", Triangles: "
        + (triangles != null ? triangles.size() : 0));
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

  public List<Triangle> getTriangles() {
    return triangles;
  }

  public void setTriangles(List<Triangle> triangles) {
    this.triangles = new ArrayList<>(triangles);
    this.bounds = calculateBounds(triangles);
    updateWorldBounds();
    this.bvhRoot = new BVHNode(triangles);
  }

  @Override
  public List<Triangle> generateMesh() {
    return triangles;
  }

  // =========================================================================
  // TO STRING - GC acceptable (not called during render)
  // =========================================================================

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

  /**
   * Generates an OBJ format string representation of this Mesh. This method is called only during
   * export, not during render. Uses robust vertex deduplication with tolerance to avoid degenerate
   * triangles.
   *
   * @return String in OBJ format
   */
  @Override
  public String toObjString() {
    StringBuilder sb = new StringBuilder();

    sb.append("# Murat Ray Tracer Mesh File\n");
    sb.append("# Shape: Mesh\n");
    sb.append("# Triangles: ").append(triangles != null ? triangles.size() : 0).append("\n");
    sb.append("# Generated: ").append(new java.util.Date()).append("\n");

    if (transform != null) {
      sb.append("# Transform: ").append(transform.toString()).append("\n");
    }

    if (material != null) {
      sb.append("# Material: ").append(material.getClass().getSimpleName()).append("\n");
    }
    sb.append("\n");

    List<Triangle> triList = generateMesh();
    if (triList == null || triList.isEmpty()) {
      sb.append("# No triangles in mesh\n");
      return sb.toString();
    }

    Matrix4 trans = (transform != null) ? transform : new Matrix4();

    // Collect all transformed vertices
    List<Point3> allVerts = new ArrayList<Point3>();
    for (Triangle tri : triList) {
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
    for (int i = 0; i < triList.size(); i++) {
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

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("Mesh " + name + " {\n");
    sb.append("    name = " + name + ";\n");
    sb.append("    triangles = " + (triangles != null ? triangles.size() : 0) + ";\n");
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

    String objStr = toObjString();
    String[] objLines = objStr.split("\n");
    final int objLinesLength = objLines.length;
    boolean vertexSection = false;
    boolean triangleSection = false;

    for (int i = 0; i < objLinesLength; i++) {
      String line = objLines[i].trim();
      if (line.startsWith("# ===== VERTEXES =====")) {
        vertexSection = true;
        triangleSection = false;
        sb.append("    # ===== VERTEXES =====\n");
        continue;
      }
      if (line.startsWith("# ===== TRIANGLES =====")) {
        vertexSection = false;
        triangleSection = true;
        sb.append("    # ===== TRIANGLES =====\n");
        continue;
      }
      if (line.startsWith("# ===== MATERIAL =====") || line.startsWith("# ===== END =====")) {
        vertexSection = false;
        triangleSection = false;
        continue;
      }
      if (vertexSection && line.startsWith("v ")) {
        sb.append("    ").append(line).append("\n");
        continue;
      }
      if (triangleSection && line.startsWith("f ")) {
        sb.append("    ").append(line).append("\n");
        continue;
      }
    }

    sb.append("\n");
    sb.append("    material = ")
        .append(getMaterial() != null ? getMaterial().toString() : "null")
        .append(";\n");
    sb.append("}");
    return sb.toString();
  }
}
