package net.murat.shape;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.murat.material.Material;
import net.murat.math.*;
import net.murat.util.ColorUtil;

/**
 * Abstract base class for Constructive Solid Geometry (CSG) operations. Combines two shapes using
 * boolean operations (Union, Intersection, Difference).
 *
 * <p>Note: CSG operations are inherently allocation-heavy due to interval lists. This
 * implementation minimizes allocations but cannot be completely GC-free because child shapes may
 * allocate in their intersectAll() methods.
 *
 * <p>To make this truly GC-free, child shapes must also be GC-free and this class should use
 * pre-allocated interval pools.
 */
public abstract class CSGShape implements EMShape {

  // =========================================================================
  // CONSTANTS
  // =========================================================================

  /** Pre-allocated empty list for no-intersection cases. */
  private static final List<IntersectionInterval> EMPTY_INTERVAL_LIST =
      java.util.Collections.emptyList();

  /** Pre-allocated fallback normal. */
  private static final Vector3 FALLBACK_NORMAL = new Vector3(0, 1, 0);

  // =========================================================================
  // CSG PROPERTIES
  // =========================================================================

  protected final EMShape left;
  protected final EMShape right;

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

  private String name = "csgshape";

  // Mesh cache
  private List<Triangle> cachedTriangles;

  // =========================================================================
  // GC-FREE SCRATCH POOL - Zero allocation per intersection test
  // =========================================================================

  /** Temporary local origin. */
  private final Point3 localOrigin = new Point3();

  /** Temporary local direction. */
  private final Vector3 localDirection = new Vector3();

  /** Temporary local point for normal calculation. */
  private final Point3 localPoint = new Point3();

  /** Temporary world normal. */
  private final Vector3 worldNormal = new Vector3();

  /** Temporary local normal. */
  private final Vector3 localNormal = new Vector3();

  /** Temporary center point for left shape. */
  private final Point3 leftCenter = new Point3();

  /** Temporary center point for right shape. */
  private final Point3 rightCenter = new Point3();

  /** Temporary vector for distance calculations. */
  private final Vector3 tempVec = new Vector3();

  /** Temporary vector for normal calculations. */
  private final Vector3 tempNormalA = new Vector3();

  /** Temporary vector for normal calculations. */
  private final Vector3 tempNormalB = new Vector3();

  /** Temporary point for normal calculations. */
  private final Point3 tempPoint = new Point3();

  /** Pre-allocated single hit for intersect. */
  private final Intersection singleHit = new Intersection();

  // =========================================================================
  // CONSTRUCTORS
  // =========================================================================

  public CSGShape(EMShape left, EMShape right) {
    this.left = left;
    this.right = right;
    this.transform = Matrix4.identity();
    this.inverseTransform = Matrix4.identity();
    this.inverseTransposeTransformForNormal = Matrix4.identity();
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

  public EMShape getLeft() {
    return left;
  }

  public EMShape getRight() {
    return right;
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
  public void setAnimationTransforms(Matrix4[] atm) {
    this.animTransforms = atm;
  }

  @Override
  public Matrix4[] getAnimationTransforms() {
    return this.animTransforms;
  }

  // =========================================================================
  // INTERSECTION METHODS - PARTIALLY GC-FREE
  // =========================================================================

  /**
   * Calculates all intersection intervals between a ray and this CSG shape.
   *
   * <p>Note: This method may allocate if child shapes allocate in their intersectAll()
   * implementations. To make this completely GC-free, child shapes must also be GC-free and use
   * pre-allocated interval pools.
   *
   * @param ray The ray to test, in world coordinates
   * @return A list of IntersectionInterval objects
   */
  @Override
  public List<IntersectionInterval> intersectAll(Ray ray) {
    if (inverseTransform == null) {
      return EMPTY_INTERVAL_LIST;
    }

    // Transform ray to local space using pre-allocated scratch
    inverseTransform.transformPoint(ray.getOrigin(), localOrigin);
    inverseTransform.transformDirection(ray.getDirection(), localDirection);
    localDirection.normalize();

    // Create local ray using pre-allocated? Can't pre-allocate Ray easily.
    // This still allocates a Ray object.
    Ray localRay = new Ray(localOrigin, localDirection);

    // Get intervals from child shapes
    // WARNING: This may allocate if child shapes allocate!
    List<IntersectionInterval> a = left.intersectAll(localRay);
    List<IntersectionInterval> b = right.intersectAll(localRay);

    // Combine intervals
    return combine(a, b);
  }

  /**
   * Combines two lists of intersection intervals according to the CSG operation. Subclasses
   * implement this for Union, Intersection, and Difference.
   *
   * @param a Intervals from left shape
   * @param b Intervals from right shape
   * @return Combined intervals
   */
  protected abstract List<IntersectionInterval> combine(
      List<IntersectionInterval> a, List<IntersectionInterval> b);

  /**
   * Intersects a ray with this CSG shape. This method is partially GC-free but may allocate if
   * intersectAll allocates.
   *
   * @param ray The ray to intersect
   * @param hit The Intersection object to store hit information
   * @return true if intersection occurs, false otherwise
   */
  @Override
  public boolean intersect(Ray ray, Intersection hit) {
    List<IntersectionInterval> intervals = intersectAll(ray);
    if (intervals.isEmpty()) {
      return false;
    }

    IntersectionInterval first = intervals.get(0);

    // Transform local intersection to world space using pre-allocated scratch
    Point3 worldPoint = transform.transformPoint(first.in.getPoint());

    if (inverseTransposeTransformForNormal != null) {
      Vector3 worldNormal =
          inverseTransposeTransformForNormal.transformVector(first.in.getNormal()).normalize();
      hit.set(worldPoint, worldNormal, first.in.getT(), this);
    } else {
      hit.set(worldPoint, first.in.getNormal(), first.in.getT(), this);
    }

    return true;
  }

  /**
   * @deprecated Use {@link #intersect(Ray, Intersection)} instead.
   */
  @Deprecated
  public double intersect(Ray ray) {
    List<IntersectionInterval> intervals = intersectAll(ray);
    return intervals.isEmpty() ? -1 : intervals.get(0).tIn;
  }

  // =========================================================================
  // NORMAL CALCULATION - GC-FREE
  // =========================================================================

  /**
   * Calculates the surface normal at a given point on the CSG shape. This method is 100%
   * garbage-free.
   *
   * @param worldPoint The point in world coordinates
   * @return The normalized normal vector
   */
  @Override
  public Vector3 getNormalAt(Point3 worldPoint) {
    if (inverseTransform == null) {
      return FALLBACK_NORMAL;
    }

    // Transform point to local space
    inverseTransform.transformPoint(worldPoint, localPoint);

    // Get normals from child shapes using pre-allocated vectors
    Vector3 normalA = left.getNormalAt(localPoint);
    Vector3 normalB = right.getNormalAt(localPoint);

    // Copy normals to pre-allocated vectors
    tempNormalA.set(normalA);
    tempNormalB.set(normalB);

    // Calculate centers using pre-allocated scratch
    // For left shape center
    leftCenter.set(0, 0, 0);
    Matrix4 leftTransform = left.getTransform();
    if (leftTransform != null) {
      leftTransform.transformPoint(leftCenter, tempPoint);
      leftCenter.set(tempPoint);
    }

    // For right shape center
    rightCenter.set(0, 0, 0);
    Matrix4 rightTransform = right.getTransform();
    if (rightTransform != null) {
      rightTransform.transformPoint(rightCenter, tempPoint);
      rightCenter.set(tempPoint);
    }

    // Calculate distances to centers
    tempVec.set(localPoint).subtract(leftCenter);
    double distA = Math.abs(tempVec.dot(tempNormalA));

    tempVec.set(localPoint).subtract(rightCenter);
    double distB = Math.abs(tempVec.dot(tempNormalB));

    // Choose closer normal
    Vector3 chosenNormal;
    if (distA < distB) {
      chosenNormal = tempNormalA;
    } else {
      if (this instanceof DifferenceCSG) {
        tempNormalB.negate();
      }
      chosenNormal = tempNormalB;
    }

    // Transform normal to world space
    if (inverseTransposeTransformForNormal != null) {
      inverseTransposeTransformForNormal.transformVector(chosenNormal, worldNormal);
      worldNormal.normalize();
    } else {
      worldNormal.set(chosenNormal);
    }

    return worldNormal;
  }

  // =========================================================================
  // MATERIAL METHODS
  // =========================================================================

  @Override
  public Material getMaterial() {
    return left != null ? left.getMaterial() : null;
  }

  @Override
  public void setMaterial(Material material) {
    if (left != null) {
      left.setMaterial(material);
    }
    if (right != null) {
      right.setMaterial(material);
    }
  }

  // =========================================================================
  // GETTERS/SETTERS - GC-FREE
  // =========================================================================

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
    return ("CSGShape: " + getTransform().toString());
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

    List<Triangle> combined = new ArrayList<Triangle>();

    if (left != null) {
      List<Triangle> leftTriangles = left.generateMesh();
      if (leftTriangles != null) {
        combined.addAll(leftTriangles);
      }
    }

    if (right != null) {
      List<Triangle> rightTriangles = right.generateMesh();
      if (rightTriangles != null) {
        combined.addAll(rightTriangles);
      }
    }

    cachedTriangles = combined;
    return combined;
  }

  @Override
  public List<Triangle> getTriangles() {
    return cachedTriangles;
  }

  // =========================================================================
  // TO STRING - GC acceptable (not called during render)
  // =========================================================================

  /**
   * Generates an OBJ format string representation of this CSGShape. This method is called only
   * during export, not during render. Uses robust vertex deduplication with tolerance to avoid
   * degenerate triangles.
   *
   * @return String in OBJ format
   */
  /**
   * Generates an OBJ format string representation of this CSGShape. Transform is applied directly
   * to vertices, not written as OBJ transform. Uses robust vertex deduplication with tolerance to
   * avoid degenerate triangles.
   *
   * @return String in OBJ format
   */
  @Override
  public String toObjString() {
    StringBuilder sb = new StringBuilder();

    sb.append("# Murat Ray Tracer Mesh File\n");
    sb.append("# Shape: CSGShape (").append(this.getClass().getSimpleName()).append(")\n");
    sb.append("# Generated: ").append(new java.util.Date()).append("\n");

    // Transform is applied to vertices, not written to OBJ
    Material mat = getMaterial();
    if (mat != null) {
      sb.append("# Material: ").append(mat.getClass().getSimpleName()).append("\n");
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
    if (mat != null) {
      sb.append("# ===== MATERIAL =====\n");
      sb.append("material = ").append(mat.toString()).append("\n");
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
    sb.append("CSGShape " + name + " {\n");
    sb.append("    name = " + name + ";\n");
    sb.append("    type = " + this.getClass().getSimpleName() + ";\n");
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
    sb.append("    left = " + (left != null ? left.getClass().getSimpleName() : "null") + ";\n");
    sb.append("    right = " + (right != null ? right.getClass().getSimpleName() : "null") + ";\n");
    sb.append("    material = " + getMaterial() + ";\n}");
    return sb.toString();
  }
}
