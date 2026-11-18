package net.murat.shape;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import net.murat.material.Material;
import net.murat.math.*;

/**
 * Represents a Constructive Solid Geometry (CSG) Difference operation. The resulting shape is the
 * difference of two shapes: A - B. A point is inside the difference if it is inside shape A and
 * outside shape B.
 *
 * <p>This implementation minimizes allocations but cannot be completely GC-free because interval
 * lists are inherently dynamic.
 *
 * <p>To make this truly GC-free, use pre-allocated interval pools and ensure child shapes are also
 * GC-free.
 */
public class DifferenceCSG extends CSGShape {

  // =========================================================================
  // CONSTANTS
  // =========================================================================

  /** Pre-allocated empty list for no-intersection cases. */
  private static final List<IntersectionInterval> EMPTY_INTERVAL_LIST =
      java.util.Collections.emptyList();

  // =========================================================================
  // PROPERTIES
  // =========================================================================

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

  private String name = "differencecsg";

  // Mesh cache
  private List<Triangle> cachedTriangles;

  // =========================================================================
  // GC-FREE SCRATCH POOL - Zero allocation per interval operation
  // =========================================================================

  /** Temporary point for interpolation. */
  private final Point3 tempPoint1 = new Point3();

  /** Temporary point for interpolation. */
  private final Point3 tempPoint2 = new Point3();

  /** Temporary vector for direction. */
  private final Vector3 tempVec = new Vector3();

  /** Temporary vector for flipped normal. */
  private final Vector3 flippedNormal = new Vector3();

  /** Pre-allocated intersection for flipNormal. */
  private final Intersection flippedIntersection = new Intersection();

  /** Pre-allocated interval for createInterval. */
  private final IntersectionInterval tempInterval =
      new IntersectionInterval(0, 0, new Intersection(), new Intersection());

  /** Pre-allocated interval for single result. */
  private final IntersectionInterval singleResult =
      new IntersectionInterval(0, 0, new Intersection(), new Intersection());

  /** Reusable list for result (cleared and reused). */
  private final List<IntersectionInterval> resultList = new ArrayList<IntersectionInterval>();

  /** Reusable list for sorted A. */
  private final List<IntersectionInterval> sortedA = new ArrayList<IntersectionInterval>();

  /** Reusable list for sorted B. */
  private final List<IntersectionInterval> sortedB = new ArrayList<IntersectionInterval>();

  // =========================================================================
  // CONSTRUCTOR
  // =========================================================================

  /**
   * Constructs a DifferenceCSG from two shapes.
   *
   * @param left The first shape (left operand, the one being subtracted from).
   * @param right The second shape (right operand, the one being subtracted).
   */
  public DifferenceCSG(EMShape left, EMShape right) {
    super(left, right);
  }

  // =========================================================================
  // EMShape OVERRIDES
  // =========================================================================

  @Override
  public void setTransform(Matrix4 transform) {
    super.setTransform(transform);
    cachedTriangles = null;
  }

  // =========================================================================
  // COMBINE METHOD - OPTIMIZED
  // =========================================================================

  /**
   * Combines intersection intervals using Difference operation (A - B). Result is parts of A that
   * are NOT inside B.
   *
   * <p>This method is optimized to minimize allocations but still allocates for interval lists. For
   * truly GC-free operation, use interval pooling.
   *
   * @param a List of intervals from left shape (A)
   * @param b List of intervals from right shape (B)
   * @return Resulting intervals for A - B
   */
  @Override
  protected List<IntersectionInterval> combine(
      List<IntersectionInterval> a, List<IntersectionInterval> b) {

    // Fast path: A empty -> empty result
    if (a.isEmpty()) {
      return EMPTY_INTERVAL_LIST;
    }

    // Fast path: B empty -> A
    if (b.isEmpty()) {
      // Return a copy (still allocates)
      return new ArrayList<IntersectionInterval>(a);
    }

    // Sort both by tIn (this modifies the lists!)
    // We need to sort copies to avoid modifying original lists
    // This unfortunately allocates
    sortedA.clear();
    sortedB.clear();
    sortedA.addAll(a);
    sortedB.addAll(b);

    Collections.sort(
        sortedA,
        new Comparator<IntersectionInterval>() {
          @Override
          public int compare(IntersectionInterval ia, IntersectionInterval ib) {
            return Double.compare(ia.tIn, ib.tIn);
          }
        });

    Collections.sort(
        sortedB,
        new Comparator<IntersectionInterval>() {
          @Override
          public int compare(IntersectionInterval ia, IntersectionInterval ib) {
            return Double.compare(ia.tIn, ib.tIn);
          }
        });

    // Clear and reuse result list
    resultList.clear();

    // Process each interval in A
    int sizeA = sortedA.size();
    int sizeB = sortedB.size();

    for (int i = 0; i < sizeA; i++) {
      IntersectionInterval ia = sortedA.get(i);
      double currentT = ia.tIn;

      // Use pre-allocated intersection for current in
      Intersection currentIn = ia.in;

      for (int j = 0; j < sizeB; j++) {
        IntersectionInterval ib = sortedB.get(j);

        // If B starts after A ends, no more overlap
        if (ib.tIn >= ia.tOut) {
          break;
        }

        // If B ends before current position, skip
        if (ib.tOut <= currentT) {
          continue;
        }

        // Part of A before B (if any)
        if (currentT < ib.tIn - Ray.EPSILON) {
          // Create interval using pre-allocated scratch
          IntersectionInterval interval = createIntervalGC(currentIn, ia.out, currentT, ib.tIn);
          if (interval != null) {
            resultList.add(interval);
          }
        }

        // Skip the part inside B
        currentT = ib.tOut;
        // Flip normal when exiting B
        currentIn = flipNormalGC(ib.out);
      }

      // Remaining part of A after all B intervals
      if (currentT < ia.tOut - Ray.EPSILON) {
        IntersectionInterval interval = createIntervalGC(currentIn, ia.out, currentT, ia.tOut);
        if (interval != null) {
          resultList.add(interval);
        }
      }
    }

    // Return result (may be empty list)
    return resultList.isEmpty() ? EMPTY_INTERVAL_LIST : new ArrayList<>(resultList);
  }

  // =========================================================================
  // GC-FREE HELPER METHODS
  // =========================================================================

  /**
   * Flips the normal of an intersection using pre-allocated scratch. This method is 100%
   * garbage-free.
   *
   * @param original The original intersection
   * @return The flipped intersection (pre-allocated)
   */
  private Intersection flipNormalGC(Intersection original) {
    // Flip normal using pre-allocated vector
    flippedNormal.set(-original.getNormal().x, -original.getNormal().y, -original.getNormal().z);

    // Update pre-allocated intersection
    flippedIntersection.set(original.getPoint(), flippedNormal, original.getT(), this);

    return flippedIntersection;
  }

  /**
   * Creates an IntersectionInterval using pre-allocated scratch. This method is 100% garbage-free.
   *
   * @param in The entry intersection
   * @param out The exit intersection
   * @param tIn The entry t value
   * @param tOut The exit t value
   * @return The interval (pre-allocated tempInterval)
   */
  private IntersectionInterval createIntervalGC(
      Intersection in, Intersection out, double tIn, double tOut) {

    // Get point and normal from in
    Point3 inPoint = in.getPoint();
    Vector3 inNormal = in.getNormal();

    // Get point and normal from out
    Point3 outPoint = out.getPoint();
    Vector3 outNormal = out.getNormal();

    // Calculate interpolated points using pre-allocated scratch
    double tInDouble = in.getT();
    double tOutDouble = out.getT();
    double range = tOutDouble - tInDouble + Ray.EPSILON;

    // Interpolate entry point
    double fracIn = (tIn - tInDouble) / range;
    tempPoint1.set(inPoint);
    tempVec.set(outPoint).subtract(inPoint);
    tempVec.multiply(fracIn);
    tempPoint1.add(tempVec);

    // Interpolate exit point
    double fracOut = (tOut - tInDouble) / range;
    tempPoint2.set(inPoint);
    tempVec.set(outPoint).subtract(inPoint);
    tempVec.multiply(fracOut);
    tempPoint2.add(tempVec);

    // Update pre-allocated intersections
    Intersection inInterp = tempInterval.in;
    Intersection outInterp = tempInterval.out;

    inInterp.set(tempPoint1, inNormal, tIn, this);
    outInterp.set(tempPoint2, outNormal, tOut, this);

    // Update pre-allocated interval
    tempInterval.setEntry(tIn, inInterp);
    tempInterval.setExit(tOut, outInterp);

    // Return the pre-allocated interval
    return tempInterval;
  }

  // =========================================================================
  // LEGACY METHODS - For backward compatibility
  // =========================================================================

  /**
   * @deprecated Use createIntervalGC instead.
   */
  @Deprecated
  private IntersectionInterval createInterval(
      IntersectionInterval original, double tIn, double tOut) {
    // This method still allocates - keep for backward compatibility
    Point3 pointIn =
        original
            .in
            .getPoint()
            .add(
                original
                    .out
                    .getPoint()
                    .subtract(original.in.getPoint())
                    .scale((tIn - original.tIn) / (original.tOut - original.tIn + Ray.EPSILON)));
    Point3 pointOut =
        original
            .in
            .getPoint()
            .add(
                original
                    .out
                    .getPoint()
                    .subtract(original.in.getPoint())
                    .scale((tOut - original.tIn) / (original.tOut - original.tIn + Ray.EPSILON)));

    Intersection in = new Intersection(pointIn, original.in.getNormal(), tIn, this);
    Intersection out = new Intersection(pointOut, original.out.getNormal(), tOut, this);

    return new IntersectionInterval(tIn, tOut, in, out);
  }

  /**
   * @deprecated Use flipNormalGC instead.
   */
  @Deprecated
  private Intersection flipNormal(Intersection original) {
    Vector3 flipped =
        new Vector3(-original.getNormal().x, -original.getNormal().y, -original.getNormal().z);
    return new Intersection(original.getPoint(), flipped, original.getT(), this);
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
    return ("DifferenceCSG: " + getTransform().toString());
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
   * Generates an OBJ format string representation of this DifferenceCSG. This method is called only
   * during export, not during render. Uses robust vertex deduplication with tolerance to avoid
   * degenerate triangles.
   *
   * @return String in OBJ format
   */
  /**
   * Generates an OBJ format string representation of this DifferenceCSG. Transform is applied
   * directly to vertices, not written as OBJ transform. Uses robust vertex deduplication with
   * tolerance to avoid degenerate triangles.
   *
   * @return String in OBJ format
   */
  @Override
  public String toObjString() {
    StringBuilder sb = new StringBuilder();

    sb.append("# Murat Ray Tracer Mesh File\n");
    sb.append("# Shape: DifferenceCSG\n");
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

    Matrix4 trans = (getTransform() != null) ? getTransform() : new Matrix4();

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
    String leftToString = left != null ? left.toString() : "null";
    String rightToString = right != null ? right.toString() : "null";

    int index1 = leftToString.indexOf("material =");
    int index2 = rightToString.indexOf("material =");

    if (index1 >= 0) {
      leftToString = leftToString.substring(0, index1) + "};///";
    }
    if (index2 >= 0) {
      rightToString = rightToString.substring(0, index2) + "};///";
    }

    sb.append("DifferenceCSG " + name + " {\n");
    sb.append("    name = " + name + ";\n");
    sb.append(
        "    left = "
            + ((leftToString.replaceAll("\n", "\n        ")).replace("    }\n        ", "}"))
            + "\n");
    sb.append(
        "    right = "
            + ((rightToString.replaceAll("\n", "\n        ")).replace("    }\n        ", "}"))
            + "\n");
    sb.append("\n");
    sb.append("    " + getTransform().toString() + "///\n");
    sb.append("\n");
    sb.append("    firstAnim_" + animTransforms[0].toString() + "///\n");
    sb.append("    secondAnim_" + animTransforms[1].toString() + "///\n");
    sb.append("\n");
    sb.append("    shadowColor = " + net.murat.util.ColorUtil.toColorString(shadowColor) + ";\n");
    sb.append("    shadowBias = " + shadowBias + ";\n");
    sb.append("    otherAnimationInfo = " + otherAnimationInfo + ";\n");
    sb.append("    isVisibleSpecial = " + isVisibleSpecial + ";\n");
    sb.append("    isVisible = " + isVisible + ";\n");
    sb.append("    isShadowEnable = " + isShadowEnable + ";\n");
    sb.append("    isShadowOnly = " + isShadowOnly + ";\n");
    sb.append("    isReflective = " + isReflective + ";\n");
    sb.append("    isRefractive = " + isRefractive + ";\n");
    sb.append("\n");
    sb.append("    material = " + getMaterial() + ";\n}");
    return sb.toString();
  }
}
