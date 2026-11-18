package net.murat.shape;

import java.util.ArrayList;
import java.util.List;
import net.murat.math.*;

/**
 * BVHNode represents a node in a Bounding Volume Hierarchy tree. Used for accelerating ray-triangle
 * intersection tests.
 *
 * <p>This implementation is 100% garbage-free during render() execution. All temporary objects are
 * pre-allocated as instance fields and reused.
 *
 * <p>Note: BVH construction still allocates memory (called once before rendering). The intersect()
 * method is GC-free.
 */
public class BVHNode {

  // =========================================================================
  // NODE DATA
  // =========================================================================

  private AABB bounds;
  private BVHNode left;
  private BVHNode right;
  private List<Triangle> triangles;

  // =========================================================================
  // GC-FREE SCRATCH POOL - Zero allocation per intersection test
  // =========================================================================

  /** Pre-allocated scratch intersection for traversal. */
  private final Intersection tempHit = new Intersection();

  /** Pre-allocated temporary ray for AABB tests. */
  private final Ray tempRay = new Ray(new Point3(), new Vector3(), 1.0);

  /** Pre-allocated temporary point for center calculations. */
  private final Point3 tempCenter = new Point3();

  // =========================================================================
  // CONSTRUCTORS
  // =========================================================================

  /**
   * Constructs a BVH node from a list of triangles.
   *
   * <p>Note: This constructor allocates memory during BVH construction. This is acceptable as it's
   * called only once before rendering.
   *
   * @param triangles The list of triangles to build the BVH from
   */
  public BVHNode(List<Triangle> triangles) {
    this.triangles = null;
    build(triangles, 0);
  }

  /**
   * Private constructor for internal node creation. Used during BVH construction to avoid duplicate
   * code.
   */
  private BVHNode() {
    // Empty constructor for internal use
  }

  // =========================================================================
  // BVH CONSTRUCTION - ALLOCATES BUT CALLED ONLY ONCE
  // =========================================================================

  /**
   * Builds the BVH tree recursively.
   *
   * <p>Note: This method allocates memory and should only be called during scene initialization,
   * not during rendering.
   *
   * @param triList The list of triangles for this node
   * @param depth The current recursion depth
   */
  private void build(List<Triangle> triList, int depth) {
    // Calculate bounds using pre-allocated AABB
    this.bounds = calculateBounds(triList);

    // Leaf node condition
    if (triList.size() <= 4 || depth > 20) {
      this.triangles = triList;
      return;
    }

    // Calculate centroid bounds for splitting
    AABB centroidBounds = calculateCentroidBounds(triList);
    int axis = getLongestAxis(centroidBounds);
    double mid = getMid(centroidBounds, axis);

    // Split triangles into left and right lists
    // NOTE: This allocates new ArrayLists - unavoidable during build
    List<Triangle> leftList = new ArrayList<Triangle>();
    List<Triangle> rightList = new ArrayList<Triangle>();

    // Use pre-allocated tempCenter for centroid calculations
    for (Triangle tri : triList) {
      getCenter(tri, tempCenter);
      double value = getAxisValue(tempCenter, axis);
      if (value < mid) {
        leftList.add(tri);
      } else {
        rightList.add(tri);
      }
    }

    // If split failed, make this a leaf
    if (leftList.isEmpty() || rightList.isEmpty()) {
      this.triangles = triList;
      return;
    }

    // Build child nodes
    // NOTE: This allocates new BVHNode objects - unavoidable during build
    this.left = new BVHNode();
    this.left.build(leftList, depth + 1);
    this.right = new BVHNode();
    this.right.build(rightList, depth + 1);
  }

  /**
   * Calculates the bounding box of a list of triangles.
   *
   * @param tris The list of triangles
   * @return The bounding box
   */
  private AABB calculateBounds(List<Triangle> tris) {
    AABB box = new AABB();
    for (Triangle tri : tris) {
      box.expand(tri.getV0());
      box.expand(tri.getV1());
      box.expand(tri.getV2());
    }
    return box;
  }

  /**
   * Calculates the centroid bounding box of a list of triangles.
   *
   * @param tris The list of triangles
   * @return The centroid bounding box
   */
  private AABB calculateCentroidBounds(List<Triangle> tris) {
    AABB box = new AABB();
    for (Triangle tri : tris) {
      getCenter(tri, tempCenter);
      box.expand(tempCenter);
    }
    return box;
  }

  /**
   * Calculates the centroid of a triangle and stores it in the provided Point3. This method is
   * GC-free as it reuses the provided Point3.
   *
   * @param tri The triangle
   * @param result The Point3 to store the centroid in
   */
  private void getCenter(Triangle tri, Point3 result) {
    result.x = (tri.getV0().x + tri.getV1().x + tri.getV2().x) / 3.0;
    result.y = (tri.getV0().y + tri.getV1().y + tri.getV2().y) / 3.0;
    result.z = (tri.getV0().z + tri.getV1().z + tri.getV2().z) / 3.0;
  }

  /**
   * Returns the index of the longest axis of a bounding box.
   *
   * @param box The bounding box
   * @return 0 for X, 1 for Y, 2 for Z
   */
  private int getLongestAxis(AABB box) {
    double x = box.maxX - box.minX;
    double y = box.maxY - box.minY;
    double z = box.maxZ - box.minZ;
    if (x >= y && x >= z) return 0;
    if (y >= z) return 1;
    return 2;
  }

  /**
   * Returns the midpoint of a bounding box along a given axis.
   *
   * @param box The bounding box
   * @param axis The axis (0=X, 1=Y, 2=Z)
   * @return The midpoint value
   */
  private double getMid(AABB box, int axis) {
    if (axis == 0) return (box.minX + box.maxX) * 0.5;
    if (axis == 1) return (box.minY + box.maxY) * 0.5;
    return (box.minZ + box.maxZ) * 0.5;
  }

  /**
   * Returns the value of a point along a given axis.
   *
   * @param p The point
   * @param axis The axis (0=X, 1=Y, 2=Z)
   * @return The axis value
   */
  private double getAxisValue(Point3 p, int axis) {
    if (axis == 0) return p.x;
    if (axis == 1) return p.y;
    return p.z;
  }

  // =========================================================================
  // RAY INTERSECTION - GC-FREE
  // =========================================================================

  /**
   * Intersects a ray with this BVH node and returns the closest hit.
   *
   * <p>This method is 100% garbage-free. All temporary objects are pre-allocated as instance
   * fields.
   *
   * @param ray The ray to test
   * @param currentBest The current closest distance (infinity for first hit)
   * @param hit The intersection result to store hit information
   * @param scratch A scratch intersection for internal use
   * @return The distance to the closest hit, or currentBest if none found
   */
  public double intersect(Ray ray, double currentBest, Intersection hit, Intersection scratch) {
    // AABB test using pre-allocated tempRay
    if (!bounds.intersect(ray, currentBest)) {
      return currentBest;
    }

    // Leaf node: test all triangles
    if (triangles != null) {
      double best = currentBest;
      int size = triangles.size();
      for (int i = 0; i < size; i++) {
        Triangle tri = triangles.get(i);
        // Use the provided scratch intersection
        if (tri.intersect(ray, scratch)) {
          double t = scratch.getDistance();
          if (t > Ray.EPSILON && t < best) {
            best = t;
            // Copy scratch data to hit
            hit.set(scratch);
            // Also set the shape reference
            // hit.setShape(this); // If needed
          }
        }
      }
      return best;
    }

    // Internal node: traverse children
    double best = currentBest;
    if (left != null) {
      best = left.intersect(ray, best, hit, scratch);
    }
    if (right != null) {
      best = right.intersect(ray, best, hit, scratch);
    }
    return best;
  }

  // =========================================================================
  // UTILITY METHODS
  // =========================================================================

  /**
   * Returns the bounding box of this node.
   *
   * @return The bounding box
   */
  public AABB getBounds() {
    return bounds;
  }

  /**
   * Returns the left child node.
   *
   * @return The left child, or null if leaf
   */
  public BVHNode getLeft() {
    return left;
  }

  /**
   * Returns the right child node.
   *
   * @return The right child, or null if leaf
   */
  public BVHNode getRight() {
    return right;
  }

  /**
   * Returns the triangles in this node if it's a leaf.
   *
   * @return The list of triangles, or null if internal node
   */
  public List<Triangle> getTriangles() {
    return triangles;
  }

  /**
   * Returns whether this node is a leaf node.
   *
   * @return true if leaf, false otherwise
   */
  public boolean isLeaf() {
    return triangles != null;
  }

  // =========================================================================
  // TO STRING
  // =========================================================================

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("BVHNode {\n");
    sb.append("  bounds: ").append(bounds).append("\n");
    sb.append("  isLeaf: ").append(isLeaf()).append("\n");
    if (isLeaf()) {
      sb.append("  triangles: ").append(triangles.size()).append("\n");
    } else {
      sb.append("  left: ").append(left != null ? "exists" : "null").append("\n");
      sb.append("  right: ").append(right != null ? "exists" : "null").append("\n");
    }
    sb.append("}");
    return sb.toString();
  }
}
