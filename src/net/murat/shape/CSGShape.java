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
 * <p>COORDINATE CONTRACT. {@link #intersectAll(Ray)} always returns intervals that are expressed in
 * the frame and the parametrization of the ray it was given:
 *
 * <ul>
 *   <li>every hit point and normal is already transformed by this CSG's own transform, so a CSG can
 *       be nested inside another CSG (or used as a top level shape) without any further conversion;
 *   <li>every t value is the parameter of the incoming ray (point = origin + direction * t), even
 *       when this CSG carries a scale. Internally the children are queried with a unit length local
 *       direction, and the resulting t values are converted back before they are returned.
 * </ul>
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

  /** CSG-level fallback material used only for children that have no material of their own. */
  private Material defaultMaterial;

  /** Material of the surface hit by the most recent successful intersect() call. */
  private Material lastHitMaterial;

  // =========================================================================
  // CSG PROPERTIES
  // =========================================================================

  protected final EMShape left;
  protected final EMShape right;

  private Matrix4 transform;
  private Matrix4 inverseTransform;
  private Matrix4 inverseTransposeTransformForNormal;

  protected int shadowColor = 0xFF000000;
  protected double shadowBias = 0.001;
  protected Matrix4[] animTransforms = new Matrix4[] {new Matrix4(), new Matrix4()};

  protected String otherAnimationInfo = "veofr,0e0fr,veofr";
  protected boolean isVisibleSpecial = true;
  protected boolean isVisible = true;
  protected boolean isShadowEnable = true;
  protected boolean isShadowOnly = false;
  protected boolean isReflective = true;
  protected boolean isRefractive = true;

  protected String name = "csgshape";

  // Mesh cache
  protected List<Triangle> cachedTriangles;

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
    refreshChildMaterials();
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
   * <p>The ray is moved into this shape's local space and handed to both children with a unit
   * length direction. The children's intervals are combined by {@link #combine}, and the result is
   * then converted back, so that the returned intervals follow the coordinate contract described in
   * the class documentation: hits are in the caller's frame and t values are in the caller's ray
   * parametrization.
   *
   * <p>Note: This method allocates (new ray, new result list, new hit copies) and may also allocate
   * if child shapes allocate in their intersectAll() implementations.
   *
   * @param ray The ray to test, in the caller's coordinates
   * @return A list of IntersectionInterval objects, never null
   */
  @Override
  public List<IntersectionInterval> intersectAll(Ray ray) {
    if (inverseTransform == null || left == null || right == null) {
      return EMPTY_INTERVAL_LIST;
    }

    // Transform ray to local space using pre-allocated scratch
    inverseTransform.transformPoint(ray.getOrigin(), localOrigin);
    inverseTransform.transformDirection(ray.getDirection(), localDirection);

    // dirLength = how many local units one unit of the caller's ray parameter covers.
    // It is 1.0 when the transform has no scale.
    double dirLength =
        Math.sqrt(
            localDirection.x * localDirection.x
                + localDirection.y * localDirection.y
                + localDirection.z * localDirection.z);
    if (dirLength < 1e-12) {
      return EMPTY_INTERVAL_LIST;
    }
    localDirection.normalize();

    // Fresh copies: the ray must not alias the scratch fields, because nested CSG shapes and
    // child shapes keep working while this ray is still in use.
    Ray localRay =
        new Ray(
            new Point3(localOrigin.x, localOrigin.y, localOrigin.z),
            new Vector3(localDirection.x, localDirection.y, localDirection.z));

    // Get intervals from child shapes (in this shape's local space, unit speed t)
    List<IntersectionInterval> a = left.intersectAll(localRay);
    List<IntersectionInterval> b = right.intersectAll(localRay);
    if (a == null) {
      a = EMPTY_INTERVAL_LIST;
    }
    if (b == null) {
      b = EMPTY_INTERVAL_LIST;
    }

    // Combine intervals (still local space)
    List<IntersectionInterval> local = combine(a, b);
    if (local == null || local.isEmpty()) {
      return EMPTY_INTERVAL_LIST;
    }

    // Convert back: local t (unit speed) -> caller's t, local hits -> caller's frame
    double toCallerT = 1.0 / dirLength;
    List<IntersectionInterval> result = new ArrayList<IntersectionInterval>(local.size());
    for (int i = 0; i < local.size(); i++) {
      IntersectionInterval iv = local.get(i);
      Intersection in = toCallerFrame(iv.in, toCallerT);
      Intersection out = toCallerFrame(iv.out, toCallerT);
      result.add(new IntersectionInterval(iv.tIn * toCallerT, iv.tOut * toCallerT, in, out));
    }
    return result;
  }

  /**
   * Converts one hit from this shape's local space to the caller's frame. Returns a new object, so
   * the children's reusable hit objects are never modified.
   *
   * @param src The hit in local space (point, outward normal, local t)
   * @param toCallerT Factor that converts a local t into the caller's ray parameter
   * @return A new hit in the caller's frame, with the same shape, UV and material
   */
  private Intersection toCallerFrame(Intersection src, double toCallerT) {
    if (src == null) {
      return null;
    }

    Point3 p = transform.transformPoint(src.getPoint());

    Vector3 n;
    if (inverseTransposeTransformForNormal != null) {
      n = inverseTransposeTransformForNormal.transformVector(src.getNormal()).normalize();
    } else {
      Vector3 sn = src.getNormal();
      n = new Vector3(sn.x, sn.y, sn.z);
    }

    Intersection r = new Intersection(p, n, src.getT() * toCallerT, src.getShape());
    r.setUVCoordinates(src.getU(), src.getV());
    r.setMaterial(src.getMaterial());
    return r;
  }

  /**
   * Combines two lists of intersection intervals according to the CSG operation. Subclasses
   * implement this for Union, Intersection, and Difference. Both lists are in this shape's local
   * space; the base class converts the result afterwards.
   *
   * @param a Intervals from left shape
   * @param b Intervals from right shape
   * @return Combined intervals
   */
  protected abstract List<IntersectionInterval> combine(
      List<IntersectionInterval> a, List<IntersectionInterval> b);

  /**
   * Intersects a ray with this CSG shape and reports the nearest boundary of the solid in front of
   * the ray origin. If the origin is inside the solid, the nearest boundary is the exit surface.
   *
   * @param ray The ray to intersect
   * @param hit The Intersection object to store hit information
   * @return true if intersection occurs, false otherwise
   */
  @Override
  public boolean intersect(Ray ray, Intersection hit) {
    // The intervals are already in the caller's frame and ray parametrization
    List<IntersectionInterval> intervals = intersectAll(ray);
    if (intervals.isEmpty()) {
      return false;
    }

    Intersection boundary = null;
    double bestT = Double.POSITIVE_INFINITY;
    for (int i = 0; i < intervals.size(); i++) {
      IntersectionInterval iv = intervals.get(i);
      if (iv.tIn > Ray.EPSILON && iv.tIn < bestT) {
        bestT = iv.tIn;
        boundary = iv.in;
      }
      if (iv.tOut > Ray.EPSILON && iv.tOut < bestT) {
        bestT = iv.tOut;
        boundary = iv.out;
      }
    }

    if (boundary == null) {
      return false;
    }

    hit.set(boundary.getPoint(), boundary.getNormal(), bestT, this);
    hit.setUVCoordinates(boundary.getU(), boundary.getV());

    Material m = boundary.getMaterial();
    if (m == null) {
      EMShape src = boundary.getShape();
      if (src != null && src != this) {
        m = src.getMaterial();
      }
    }

    hit.setMaterial(m);
    this.lastHitMaterial = m;

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
    if (lastHitMaterial != null) {
      return lastHitMaterial;
    }
    if (left != null && left.getMaterial() != null) {
      return left.getMaterial();
    }
    if (right != null && right.getMaterial() != null) {
      return right.getMaterial();
    }
    return defaultMaterial;
  }

  @Override
  public void setMaterial(Material material) {
    // CSG-level material is only a fallback. Do NOT overwrite child materials,
    // otherwise the left child's material would leak onto the right child.
    this.lastHitMaterial = null;
    this.defaultMaterial = material;
    if (left != null && left.getMaterial() == null) {
      left.setMaterial(material);
    }
    if (right != null && right.getMaterial() == null) {
      right.setMaterial(material);
    }
    refreshChildMaterials();
  }

  /**
   * Procedural materials evaluate in object space. For a child inside a CSG the object point is:
   * childInverse * csgInverse * worldPoint. Runs whenever the CSG transform or a child material
   * changes (including every animation frame, via setTransform).
   */
  public void refreshChildMaterials() {
    if (inverseTransform == null) {
      return;
    }

    // One shared Material instance on both children: use the CSG space only
    if (left != null
        && right != null
        && left.getMaterial() != null
        && left.getMaterial() == right.getMaterial()) {
      left.getMaterial().setObjectTransform(new Matrix4(inverseTransform));
      return;
    }

    applyChildMaterialTransform(left, inverseTransform);
    applyChildMaterialTransform(right, inverseTransform);
  }

  private static void applyChildMaterialTransform(EMShape child, Matrix4 parentInverse) {
    if (child == null) {
      return;
    }

    if (child instanceof CSGShape) {
      CSGShape c = (CSGShape) child;
      if (c.getInverseTransform() == null) {
        return;
      }
      Matrix4 acc = new Matrix4(c.getInverseTransform()).multiply(parentInverse);
      applyChildMaterialTransform(c.left, acc);
      applyChildMaterialTransform(c.right, acc);
      return;
    }

    Material m = child.getMaterial();
    Matrix4 childInv = child.getInverseTransform();
    if (m != null && childInv != null) {
      m.setObjectTransform(new Matrix4(childInv).multiply(parentInverse));
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
    return (this.getClass().getSimpleName() + ": " + getTransform().toString());
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
