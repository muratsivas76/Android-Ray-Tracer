package net.murat.shape;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import net.murat.material.Material;
import net.murat.math.*;

/**
 * Represents a Constructive Solid Geometry (CSG) Union operation. The resulting shape is the union
 * of two shapes: A ∪ B. A point is inside the union if it is inside shape A, shape B, or both.
 *
 * <p>All shared state (shadow settings, visibility flags, animation transforms, name, mesh cache)
 * lives in {@link CSGShape}. This class only implements the union-specific interval combination.
 */
public class UnionCSG extends CSGShape {

  // =========================================================================
  // CONSTANTS
  // =========================================================================

  /** Pre-allocated empty list for no-intersection cases. */
  private static final List<IntersectionInterval> EMPTY_INTERVAL_LIST =
      java.util.Collections.emptyList();

  /** Static comparator to avoid lambda allocation. */
  private static final Comparator<IntersectionInterval> COMPARATOR =
      new Comparator<IntersectionInterval>() {
        @Override
        public int compare(IntersectionInterval ia, IntersectionInterval ib) {
          return Double.compare(ia.tIn, ib.tIn);
        }
      };

  // =========================================================================
  // SCRATCH POOL
  // =========================================================================

  /** Reusable all-intervals list (single-threaded render loop only). */
  private final List<IntersectionInterval> allList = new ArrayList<IntersectionInterval>();

  // =========================================================================
  // CONSTRUCTOR
  // =========================================================================

  /**
   * Constructs a UnionCSG from two shapes.
   *
   * @param left The first shape (left operand).
   * @param right The second shape (right operand).
   */
  public UnionCSG(EMShape left, EMShape right) {
    super(left, right);
    this.name = "unioncsg";
  }

  // =========================================================================
  // COMBINE METHOD
  // =========================================================================

  /**
   * Combines intersection intervals from two shapes using the union operation. Intervals that
   * overlap or touch are merged into a single interval. A merged interval keeps the original entry
   * hit and takes the farther exit hit, so each boundary still knows which child shape (and
   * therefore which material) produced it.
   *
   * @param a List of intervals from the left shape
   * @param b List of intervals from the right shape
   * @return Merged non-overlapping intervals
   */
  @Override
  protected List<IntersectionInterval> combine(
      List<IntersectionInterval> a, List<IntersectionInterval> b) {

    // Fast path: both empty
    if (a.isEmpty() && b.isEmpty()) {
      return EMPTY_INTERVAL_LIST;
    }

    // Fast path: only one side has intervals
    if (a.isEmpty()) {
      return new ArrayList<IntersectionInterval>(b);
    }
    if (b.isEmpty()) {
      return new ArrayList<IntersectionInterval>(a);
    }

    // Combine all intervals into the reusable list and sort by entry t
    allList.clear();
    allList.addAll(a);
    allList.addAll(b);
    Collections.sort(allList, COMPARATOR);

    // Fresh result list: merged intervals must never alias each other
    List<IntersectionInterval> result = new ArrayList<IntersectionInterval>();

    IntersectionInterval current = allList.get(0);

    for (int i = 1; i < allList.size(); i++) {
      IntersectionInterval next = allList.get(i);

      if (next.tIn <= current.tOut + Ray.EPSILON) {
        // Overlapping or touching: extend the exit if next goes further
        if (next.tOut > current.tOut) {
          current = new IntersectionInterval(current.tIn, next.tOut, current.in, next.out);
        }
      } else {
        result.add(current);
        current = next;
      }
    }

    result.add(current);
    return result;
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
   * Generates an OBJ format string representation of this UnionCSG. Transform is applied directly
   * to vertices, not written as an OBJ transform. Uses robust vertex deduplication with tolerance
   * to avoid degenerate triangles.
   *
   * @return String in OBJ format
   */
  @Override
  public String toObjString() {
    StringBuilder sb = new StringBuilder();

    sb.append("# Murat Ray Tracer Mesh File\n");
    sb.append("# Shape: UnionCSG\n");
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

    sb.append("UnionCSG " + name + " {\n");
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
