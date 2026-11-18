package net.murat.shape;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import net.murat.material.Material;
import net.murat.math.*;

/**
 * Represents a Constructive Solid Geometry (CSG) Intersection operation. The resulting shape is the
 * intersection of two shapes: A ∩ B. A point is inside the intersection only if it is inside both
 * shape A and shape B.
 *
 * <p>This implementation minimizes allocations but cannot be completely GC-free because interval
 * lists are inherently dynamic.
 *
 * <p>To make this truly GC-free, use pre-allocated interval pools and ensure child shapes are also
 * GC-free.
 */
public class IntersectionCSG extends CSGShape {

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

  private String name = "intersectioncsg";

  // Mesh cache
  private List<Triangle> cachedTriangles;

  // =========================================================================
  // GC-FREE SCRATCH POOL - Zero allocation per interval operation
  // =========================================================================

  /** Temporary point for interpolation. */
  private final Point3 tempPointIn = new Point3();

  /** Temporary point for interpolation. */
  private final Point3 tempPointOut = new Point3();

  /** Temporary vector for normal averaging. */
  private final Vector3 tempNormalIn = new Vector3();

  /** Temporary vector for normal averaging. */
  private final Vector3 tempNormalOut = new Vector3();

  /** Temporary vector for addition. */
  private final Vector3 tempVec = new Vector3();

  /** Pre-allocated intersection for entry. */
  private final Intersection tempIn = new Intersection();

  /** Pre-allocated intersection for exit. */
  private final Intersection tempOut = new Intersection();

  /** Pre-allocated interval for result. */
  private final IntersectionInterval tempInterval = new IntersectionInterval(0, 0, tempIn, tempOut);

  /** Reusable result list. */
  private final List<IntersectionInterval> resultList = new ArrayList<IntersectionInterval>();

  /** Reusable sorted A list. */
  private final List<IntersectionInterval> sortedA = new ArrayList<IntersectionInterval>();

  /** Reusable sorted B list. */
  private final List<IntersectionInterval> sortedB = new ArrayList<IntersectionInterval>();

  // =========================================================================
  // CONSTRUCTOR
  // =========================================================================

  /**
   * Constructs an IntersectionCSG from two shapes.
   *
   * @param left The first shape (left operand).
   * @param right The second shape (right operand).
   */
  public IntersectionCSG(EMShape left, EMShape right) {
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
   * Combines the intersection intervals of two shapes using the Intersection operation. The
   * intersection is formed by finding intervals where the ray is inside both shapes.
   *
   * <p>This method is optimized to minimize allocations but still allocates for interval lists. For
   * truly GC-free operation, use interval pooling.
   *
   * @param a List of intervals from the left shape
   * @param b List of intervals from the right shape
   * @return The resulting list of intervals for the intersection
   */
  @Override
  protected List<IntersectionInterval> combine(
      List<IntersectionInterval> a, List<IntersectionInterval> b) {

    // Fast path: if either is empty, intersection is empty
    if (a.isEmpty() || b.isEmpty()) {
      return EMPTY_INTERVAL_LIST;
    }

    // Sort both interval lists by tIn using reusable lists
    sortedA.clear();
    sortedB.clear();
    sortedA.addAll(a);
    sortedB.addAll(b);

    // Sort using comparator (still allocates? No, comparator is stateless)
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

    int i = 0, j = 0;
    int sizeA = sortedA.size();
    int sizeB = sortedB.size();

    while (i < sizeA && j < sizeB) {
      IntersectionInterval intervalA = sortedA.get(i);
      IntersectionInterval intervalB = sortedB.get(j);

      // Find overlap: max(tIn) to min(tOut)
      double overlapTIn = intervalA.tIn > intervalB.tIn ? intervalA.tIn : intervalB.tIn;
      double overlapTOut = intervalA.tOut < intervalB.tOut ? intervalA.tOut : intervalB.tOut;

      // If there is a valid overlap
      if (overlapTIn < overlapTOut - Ray.EPSILON) {
        // Create interval using GC-free method
        IntersectionInterval interval =
            createIntervalGC(intervalA, intervalB, overlapTIn, overlapTOut);
        if (interval != null) {
          resultList.add(interval);
        }
      }

      // Advance the interval with the smaller tOut
      if (intervalA.tOut < intervalB.tOut) {
        i++;
      } else {
        j++;
      }
    }

    // Return result
    return resultList.isEmpty() ? EMPTY_INTERVAL_LIST : new ArrayList<>(resultList);
  }

  // =========================================================================
  // GC-FREE HELPER METHODS
  // =========================================================================

  /**
   * Creates an IntersectionInterval using pre-allocated scratch. This method is 100% garbage-free.
   *
   * @param intervalA First interval
   * @param intervalB Second interval
   * @param overlapTIn Entry t value
   * @param overlapTOut Exit t value
   * @return The interval (pre-allocated tempInterval)
   */
  private IntersectionInterval createIntervalGC(
      IntersectionInterval intervalA,
      IntersectionInterval intervalB,
      double overlapTIn,
      double overlapTOut) {

    // Get points from both intervals
    Point3 aInPoint = intervalA.in.getPoint();
    Point3 bInPoint = intervalB.in.getPoint();
    Point3 aOutPoint = intervalA.out.getPoint();
    Point3 bOutPoint = intervalB.out.getPoint();

    // Calculate midpoint of entry points using pre-allocated scratch
    tempPointIn.x = (aInPoint.x + bInPoint.x) * 0.5;
    tempPointIn.y = (aInPoint.y + bInPoint.y) * 0.5;
    tempPointIn.z = (aInPoint.z + bInPoint.z) * 0.5;

    // Calculate midpoint of exit points
    tempPointOut.x = (aOutPoint.x + bOutPoint.x) * 0.5;
    tempPointOut.y = (aOutPoint.y + bOutPoint.y) * 0.5;
    tempPointOut.z = (aOutPoint.z + bOutPoint.z) * 0.5;

    // Average the entry normals
    tempNormalIn.set(intervalA.in.getNormal());
    tempNormalIn.add(intervalB.in.getNormal());
    tempNormalIn.normalize();

    // Average the exit normals
    tempNormalOut.set(intervalA.out.getNormal());
    tempNormalOut.add(intervalB.out.getNormal());
    tempNormalOut.normalize();

    // Update pre-allocated intersections
    tempIn.set(tempPointIn, tempNormalIn, overlapTIn, this);
    tempOut.set(tempPointOut, tempNormalOut, overlapTOut, this);

    // Update pre-allocated interval
    tempInterval.setEntry(overlapTIn, tempIn);
    tempInterval.setExit(overlapTOut, tempOut);

    return tempInterval;
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
    return ("IntersectionCSG: " + getTransform().toString());
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
   * Generates an OBJ format string representation of this IntersectionCSG. This method is called
   * only during export, not during render. Uses robust vertex deduplication with tolerance to avoid
   * degenerate triangles.
   *
   * @return String in OBJ format
   */
  /**
   * Generates an OBJ format string representation of this IntersectionCSG. Transform is applied
   * directly to vertices, not written as OBJ transform. Uses robust vertex deduplication with
   * tolerance to avoid degenerate triangles.
   *
   * @return String in OBJ format
   */
  @Override
  public String toObjString() {
    StringBuilder sb = new StringBuilder();

    sb.append("# Murat Ray Tracer Mesh File\n");
    sb.append("# Shape: IntersectionCSG\n");
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

    sb.append("IntersectionCSG " + name + " {\n");
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
