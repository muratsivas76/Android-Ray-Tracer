package net.murat.shape;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import net.murat.material.Material;
import net.murat.math.*;

/**
 * Represents a Constructive Solid Geometry (CSG) Intersection operation. The resulting shape is the
 * intersection of two shapes: A ∩ B. A point is inside the intersection only if it is inside both
 * shape A and shape B.
 *
 * <p>All shared state lives in {@link CSGShape}. This class only implements the
 * intersection-specific interval combination.
 */
public class IntersectionCSG extends CSGShape {

  // =========================================================================
  // CONSTANTS
  // =========================================================================

  /** Pre-allocated empty list for no-intersection cases. */
  private static final List<IntersectionInterval> EMPTY_INTERVAL_LIST =
      java.util.Collections.emptyList();

  /** Static comparator (no lambda, Java 8 / Android friendly). */
  private static final Comparator<IntersectionInterval> BY_T_IN =
      new Comparator<IntersectionInterval>() {
        @Override
        public int compare(IntersectionInterval x, IntersectionInterval y) {
          return Double.compare(x.tIn, y.tIn);
        }
      };

  // =========================================================================
  // SCRATCH POOL
  // =========================================================================

  /** Reusable sorted A list (single-threaded render loop only). */
  private final List<IntersectionInterval> sortedA = new ArrayList<IntersectionInterval>();

  /** Reusable sorted B list (single-threaded render loop only). */
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
    this.name = "intersectioncsg";
  }

  // =========================================================================
  // COMBINE METHOD
  // =========================================================================

  /**
   * Combines the intersection intervals of two shapes using the intersection operation. The overlap
   * of each pair of intervals is computed as [max(tIn), min(tOut)]. The entry surface belongs to
   * the shape entered last, the exit surface to the shape left first, so each hit keeps its own
   * normal, shape and material.
   *
   * @param a List of intervals from the left shape
   * @param b List of intervals from the right shape
   * @return The resulting list of intervals for the intersection
   */
  @Override
  protected List<IntersectionInterval> combine(
      List<IntersectionInterval> a, List<IntersectionInterval> b) {

    // If either side is empty, the intersection is empty
    if (a.isEmpty() || b.isEmpty()) {
      return EMPTY_INTERVAL_LIST;
    }

    sortedA.clear();
    sortedB.clear();
    sortedA.addAll(a);
    sortedB.addAll(b);
    Collections.sort(sortedA, BY_T_IN);
    Collections.sort(sortedB, BY_T_IN);

    // Fresh list and fresh objects: results must never alias each other
    List<IntersectionInterval> result = new ArrayList<IntersectionInterval>();

    int i = 0;
    int j = 0;

    while (i < sortedA.size() && j < sortedB.size()) {
      IntersectionInterval ia = sortedA.get(i);
      IntersectionInterval ib = sortedB.get(j);

      double tIn = ia.tIn > ib.tIn ? ia.tIn : ib.tIn;
      double tOut = ia.tOut < ib.tOut ? ia.tOut : ib.tOut;

      if (tIn < tOut - Ray.EPSILON) {
        // The entry surface belongs to the shape entered LAST,
        // the exit surface to the shape left FIRST.
        // Each hit keeps its own normal, shape and material.
        Intersection hin = (ia.tIn >= ib.tIn) ? ia.in : ib.in;
        Intersection hout = (ia.tOut <= ib.tOut) ? ia.out : ib.out;
        result.add(new IntersectionInterval(tIn, tOut, hin, hout));
      }

      // Advance the interval that ends first
      if (ia.tOut < ib.tOut) {
        i++;
      } else {
        j++;
      }
    }

    return result.isEmpty() ? EMPTY_INTERVAL_LIST : result;
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

  // =========================================================================
  // OBJ EXPORT - Called only during export, GC acceptable
  // =========================================================================

  /**
   * Generates an OBJ format string representation of this IntersectionCSG. Transform is applied
   * directly to vertices, not written as an OBJ transform. Uses robust vertex deduplication with
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

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();

    String leftToString = left != null ? left.toString().trim() : "null";
    String rightToString = right != null ? right.toString().trim() : "null";

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
    sb.append("}");
    return sb.toString();
  }
}
