/*
 * Copyright (C) 2026 Murat iNAN
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */

package net.murat.shape;

import java.util.ArrayList;
import java.util.List;
import net.murat.material.Material;
import net.murat.math.*;
import net.murat.util.ColorUtil;

/**
 * Box class represents an axis-aligned rectangular prism (cuboid) in 3D space. It implements the
 * EMShape interface, supporting Matrix4 transformations. The box is centered at the origin (0,0,0)
 * in its local coordinate system.
 *
 * <p>This implementation uses the slab method for ray-box intersection and is 100% garbage-free
 * during render() execution. All temporary objects are pre-allocated as instance fields and reused.
 *
 * @author Murat iNAN
 */
public class Box implements EMShape {

  // =========================================================================
  // CONSTANTS
  // =========================================================================

  /** Pre-allocated empty list for no-intersection cases. */
  private static final List<IntersectionInterval> EMPTY_INTERVAL_LIST =
      java.util.Collections.emptyList();

  /** Pre-allocated fallback normal. */
  private static final Vector3 FALLBACK_NORMAL = new Vector3(0, 1, 0);

  /** Epsilon for intersection tests. */
  private static final double EPSILON = 1e-8;

  /** Pre-computed face normals for the box (local space). */
  private static final Vector3[] FACE_NORMALS = {
    new Vector3(1, 0, 0), // +X face
    new Vector3(-1, 0, 0), // -X face
    new Vector3(0, 1, 0), // +Y face
    new Vector3(0, -1, 0), // -Y face
    new Vector3(0, 0, 1), // +Z face
    new Vector3(0, 0, -1) // -Z face
  };

  // =========================================================================
  // BOX PROPERTIES
  // =========================================================================

  /** Dimensions of the box in local space. */
  private final double width;

  private final double height;
  private final double depth;

  /** Half-dimensions for faster intersection tests. */
  private final double halfWidth;

  private final double halfHeight;
  private final double halfDepth;

  /** The material applied to this box. */
  private Material material;

  /** Shadow color for shadow rays (ARGB format). */
  private int shadowColor = 0xFF000000;

  /** Shadow bias to avoid self-shadowing artifacts. */
  private double shadowBias = 0.001;

  /** Animation transformation matrices for keyframe animation. */
  private Matrix4[] animTransforms = new Matrix4[] {new Matrix4(), new Matrix4()};

  /** Animation info string for per-frame visibility/shadow/reflection flags. */
  private String otherAnimationInfo = "veofr,0e0fr,veofr";

  /** Visibility flags. */
  private boolean isVisibleSpecial = true;

  private boolean isVisible = true;
  private boolean isShadowEnable = true;
  private boolean isShadowOnly = false;
  private boolean isReflective = true;
  private boolean isRefractive = true;

  /** Transformation matrices. */
  private Matrix4 transform;

  private Matrix4 inverseTransform;
  private Matrix4 inverseTransposeTransformForNormal;

  /** Cached triangulated mesh. */
  private List<Triangle> cachedTriangles;

  /** Name of this box for identification. */
  private String name = "box";

  // =========================================================================
  // GC-FREE SCRATCH POOL - Zero allocation per intersection test
  // =========================================================================

  /** Temporary origin in local space. */
  private final Point3 localOrigin = new Point3();

  /** Temporary direction in local space. */
  private final Vector3 localDirection = new Vector3();

  /** Temporary hit point in world space. */
  private final Point3 tempHitPoint = new Point3();

  /** Temporary normal in world space. */
  private final Vector3 tempWorldNormal = new Vector3();

  /** Temporary origin for transform operations. */
  private final Point3 tempOrigin = new Point3();

  /** Temporary direction for transform operations. */
  private final Vector3 tempDir = new Vector3();

  /** Temporary point for normal calculation. */
  private final Point3 tempLocalPoint = new Point3();

  /** Temporary normal for normal calculation. */
  private final Vector3 tempLocalNormal = new Vector3();

  /** Pre-allocated single hit for intersectAll. */
  private final Intersection singleHit = new Intersection();

  /** Pre-allocated single interval for intersectAll. */
  private final IntersectionInterval singleInterval =
      new IntersectionInterval(0, 0, singleHit, singleHit);

  /** Pre-allocated array for t values. */
  private final double[] tValues = new double[6];

  // =========================================================================
  // CONSTRUCTORS
  // =========================================================================

  /**
   * Constructs a Box with specified dimensions. The box is centered at the origin (0,0,0) in its
   * local space.
   *
   * @param width The width of the box along the local X-axis.
   * @param height The height of the box along the local Y-axis.
   * @param depth The depth of the box along the local Z-axis.
   */
  public Box(double width, double height, double depth) {
    this.width = width;
    this.height = height;
    this.depth = depth;
    this.halfWidth = width / 2.0;
    this.halfHeight = height / 2.0;
    this.halfDepth = depth / 2.0;
    this.material = null;

    // Initialize transforms to identity
    this.transform = new Matrix4();
    this.inverseTransform = new Matrix4();
    this.inverseTransposeTransformForNormal = new Matrix4();

    updateTransforms();
  }

  /**
   * Constructs a Box with specified dimensions and material.
   *
   * @param width The width of the box along the local X-axis.
   * @param height The height of the box along the local Y-axis.
   * @param depth The depth of the box along the local Z-axis.
   * @param material The material applied to the box.
   */
  public Box(double width, double height, double depth, Material material) {
    this(width, height, depth);
    this.setMaterial(material);
  }

  // =========================================================================
  // INTERNAL METHODS
  // =========================================================================

  /**
   * Updates the inverse transformation matrices after transform changes. Uses non-mutating
   * transpose to avoid corrupting inverseTransform.
   */
  private void updateTransforms() {
    Matrix4 inv = this.transform.inverse();
    if (inv != null) {
      this.inverseTransform = inv;
      // Non-mutating transpose - inverseTransform remains unchanged
      this.inverseTransform.transpose(this.inverseTransposeTransformForNormal);
    } else {
      this.inverseTransform = new Matrix4();
      this.inverseTransposeTransformForNormal = new Matrix4();
    }
  }

  /**
   * Determines which face of the box a point is on and returns the corresponding local normal. This
   * is a GC-free helper method.
   *
   * @param localPoint The point in local space
   * @param result The vector to store the normal in
   */
  private void getLocalNormalAt(Point3 localPoint, Vector3 result) {
    // Calculate distance to each face
    double dxPos = Math.abs(localPoint.x - halfWidth);
    double dxNeg = Math.abs(localPoint.x + halfWidth);
    double dyPos = Math.abs(localPoint.y - halfHeight);
    double dyNeg = Math.abs(localPoint.y + halfHeight);
    double dzPos = Math.abs(localPoint.z - halfDepth);
    double dzNeg = Math.abs(localPoint.z + halfDepth);

    // Find the nearest face
    double minDist = dxPos;
    int faceIndex = 0;

    if (dxNeg < minDist) {
      minDist = dxNeg;
      faceIndex = 1;
    }
    if (dyPos < minDist) {
      minDist = dyPos;
      faceIndex = 2;
    }
    if (dyNeg < minDist) {
      minDist = dyNeg;
      faceIndex = 3;
    }
    if (dzPos < minDist) {
      minDist = dzPos;
      faceIndex = 4;
    }
    if (dzNeg < minDist) {
      minDist = dzNeg;
      faceIndex = 5;
    }

    // Set the normal from pre-computed face normals
    result.set(FACE_NORMALS[faceIndex]);
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
    updateTransforms();
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

  /**
   * Calculates the normal vector at a given point on the Box's surface. This method is 100%
   * garbage-free.
   *
   * @param worldPoint The point on the Box's surface in world coordinates
   * @return The normalized normal vector at that point in world coordinates
   */
  @Override
  public Vector3 getNormalAt(Point3 worldPoint) {
    if (inverseTransform == null || inverseTransposeTransformForNormal == null) {
      return FALLBACK_NORMAL;
    }

    // Transform point to local space
    inverseTransform.transformPoint(worldPoint, tempLocalPoint);

    // Get local normal
    getLocalNormalAt(tempLocalPoint, tempLocalNormal);

    // Transform normal to world space
    inverseTransposeTransformForNormal.transformVector(tempLocalNormal, tempWorldNormal);
    tempWorldNormal.normalize();

    return tempWorldNormal;
  }

  /**
   * Calculates the intersection of a ray with this box using the slab method. This method is 100%
   * garbage-free.
   *
   * @param ray The ray to test intersection, in world coordinates.
   * @param hit The Intersection object to store the hit result.
   * @return True if intersection occurred, false otherwise.
   */
  @Override
  public boolean intersect(Ray ray, Intersection hit) {
    // Guard against null inverse transform
    if (inverseTransform == null) {
      return false;
    }

    // Transform ray to local space using pre-allocated scratch
    inverseTransform.transformPoint(ray.getOrigin(), localOrigin);
    inverseTransform.transformDirection(ray.getDirection(), localDirection);

    // Slab method for axis-aligned box intersection
    double tMin = Double.NEGATIVE_INFINITY;
    double tMax = Double.POSITIVE_INFINITY;

    // X-axis slabs
    if (Math.abs(localDirection.x) < EPSILON) {
      if (localOrigin.x < -halfWidth || localOrigin.x > halfWidth) {
        return false;
      }
    } else {
      double t1 = (-halfWidth - localOrigin.x) / localDirection.x;
      double t2 = (halfWidth - localOrigin.x) / localDirection.x;
      if (t1 > t2) {
        double temp = t1;
        t1 = t2;
        t2 = temp;
      }
      tMin = Math.max(tMin, t1);
      tMax = Math.min(tMax, t2);
      if (tMin > tMax) return false;
    }

    // Y-axis slabs
    if (Math.abs(localDirection.y) < EPSILON) {
      if (localOrigin.y < -halfHeight || localOrigin.y > halfHeight) {
        return false;
      }
    } else {
      double t1 = (-halfHeight - localOrigin.y) / localDirection.y;
      double t2 = (halfHeight - localOrigin.y) / localDirection.y;
      if (t1 > t2) {
        double temp = t1;
        t1 = t2;
        t2 = temp;
      }
      tMin = Math.max(tMin, t1);
      tMax = Math.min(tMax, t2);
      if (tMin > tMax) return false;
    }

    // Z-axis slabs
    if (Math.abs(localDirection.z) < EPSILON) {
      if (localOrigin.z < -halfDepth || localOrigin.z > halfDepth) {
        return false;
      }
    } else {
      double t1 = (-halfDepth - localOrigin.z) / localDirection.z;
      double t2 = (halfDepth - localOrigin.z) / localDirection.z;
      if (t1 > t2) {
        double temp = t1;
        t1 = t2;
        t2 = temp;
      }
      tMin = Math.max(tMin, t1);
      tMax = Math.min(tMax, t2);
      if (tMin > tMax) return false;
    }

    // Determine the intersection point
    double t = tMin;
    if (t < EPSILON) {
      t = tMax;
      if (t < EPSILON) {
        return false;
      }
    }

    // Calculate world space hit point
    ray.pointAtParameter(t, tempHitPoint);
    Vector3 normal = getNormalAt(tempHitPoint);

    // Store intersection data
    hit.set(tempHitPoint, normal, t, this);

    return true;
  }

  /**
   * Calculates all intersection intervals between a ray and this box.
   *
   * <p>Note: This method returns pre-allocated objects. The caller must NOT modify the returned
   * objects as they are reused.
   *
   * @param ray The ray to test, in world coordinates.
   * @return A list containing a single interval if intersected, otherwise empty list.
   */
  @Override
  public List<IntersectionInterval> intersectAll(Ray ray) {
    // Use pre-allocated singleHit
    if (intersect(ray, singleHit)) {
      double t = singleHit.getT();
      singleInterval.setEntry(t, singleHit);
      singleInterval.setExit(t, singleHit);
      return java.util.Collections.singletonList(singleInterval);
    }
    return EMPTY_INTERVAL_LIST;
  }

  // =========================================================================
  // GETTERS
  // =========================================================================

  public double getWidth() {
    return width;
  }

  public double getHeight() {
    return height;
  }

  public double getDepth() {
    return depth;
  }

  // =========================================================================
  // EMShape GETTERS/SETTERS - Flags and Animation
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
    return "Box: " + getTransform().toString();
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

    double hw = halfWidth;
    double hh = halfHeight;
    double hd = halfDepth;

    // 8 vertices
    Point3[] verts = new Point3[8];
    verts[0] = new Point3(-hw, -hh, -hd);
    verts[1] = new Point3(hw, -hh, -hd);
    verts[2] = new Point3(hw, hh, -hd);
    verts[3] = new Point3(-hw, hh, -hd);
    verts[4] = new Point3(-hw, -hh, hd);
    verts[5] = new Point3(hw, -hh, hd);
    verts[6] = new Point3(hw, hh, hd);
    verts[7] = new Point3(-hw, hh, hd);

    // 6 faces, each 2 triangles (12 triangles total)
    // Back face (-Z)
    triangles.add(new Triangle(verts[0], verts[1], verts[2]));
    triangles.add(new Triangle(verts[0], verts[2], verts[3]));

    // Front face (+Z)
    triangles.add(new Triangle(verts[4], verts[6], verts[5]));
    triangles.add(new Triangle(verts[4], verts[7], verts[6]));

    // Bottom face (-Y)
    triangles.add(new Triangle(verts[0], verts[4], verts[5]));
    triangles.add(new Triangle(verts[0], verts[5], verts[1]));

    // Top face (+Y)
    triangles.add(new Triangle(verts[3], verts[2], verts[6]));
    triangles.add(new Triangle(verts[3], verts[6], verts[7]));

    // Left face (-X)
    triangles.add(new Triangle(verts[0], verts[3], verts[7]));
    triangles.add(new Triangle(verts[0], verts[7], verts[4]));

    // Right face (+X)
    triangles.add(new Triangle(verts[1], verts[5], verts[6]));
    triangles.add(new Triangle(verts[1], verts[6], verts[2]));

    cachedTriangles = triangles;
    return triangles;
  }

  @Override
  public List<Triangle> getTriangles() {
    return cachedTriangles;
  }

  // =========================================================================
  // TO STRING METHODS
  // =========================================================================

  /**
   * Generates an OBJ format string representation of this Box. This method is called only during
   * export, not during render. Uses robust vertex deduplication with tolerance to avoid degenerate
   * triangles.
   *
   * @return String in OBJ format
   */
  /**
   * Generates an OBJ format string representation of this Box. Box has exactly 8 vertices, so we
   * write them directly without deduplication. This is faster and more reliable than generic vertex
   * deduplication.
   *
   * @return String in OBJ format
   */
  /**
   * Generates an OBJ format string representation of this Box. Uses the same robust vertex
   * deduplication as Sphere, with appropriate tolerance.
   *
   * @return String in OBJ format
   */
  /**
   * Generates an OBJ format string representation of this Box. Box has exactly 8 vertices, so we
   * write them directly. Transform is applied to vertices, not stored as OBJ transform.
   *
   * @return String in OBJ format
   */
  @Override
  public String toObjString() {
    StringBuilder sb = new StringBuilder();

    sb.append("# Murat Ray Tracer Mesh File\n");
    sb.append("# Shape: Box\n");
    sb.append("# Width: ").append(width).append("\n");
    sb.append("# Height: ").append(height).append("\n");
    sb.append("# Depth: ").append(depth).append("\n");
    sb.append("# Generated: ").append(new java.util.Date()).append("\n");

    // DO NOT write transform info - apply it directly to vertices
    if (material != null) {
      sb.append("# Material: ").append(material.getClass().getSimpleName()).append("\n");
    }
    sb.append("\n");

    // Get the transform matrix
    Matrix4 trans = (transform != null) ? transform : new Matrix4();

    // 8 vertices of the box (local space)
    double hw = halfWidth;
    double hh = halfHeight;
    double hd = halfDepth;

    // Transform each vertex to world space
    Point3 v0 = trans.transformPoint(new Point3(-hw, -hh, -hd));
    Point3 v1 = trans.transformPoint(new Point3(hw, -hh, -hd));
    Point3 v2 = trans.transformPoint(new Point3(hw, hh, -hd));
    Point3 v3 = trans.transformPoint(new Point3(-hw, hh, -hd));
    Point3 v4 = trans.transformPoint(new Point3(-hw, -hh, hd));
    Point3 v5 = trans.transformPoint(new Point3(hw, -hh, hd));
    Point3 v6 = trans.transformPoint(new Point3(hw, hh, hd));
    Point3 v7 = trans.transformPoint(new Point3(-hw, hh, hd));

    // Write vertices (OBJ uses 1-based indexing)
    sb.append("# ===== VERTICES =====\n");
    sb.append("v ").append(formatVertex(v0)).append("\n");
    sb.append("v ").append(formatVertex(v1)).append("\n");
    sb.append("v ").append(formatVertex(v2)).append("\n");
    sb.append("v ").append(formatVertex(v3)).append("\n");
    sb.append("v ").append(formatVertex(v4)).append("\n");
    sb.append("v ").append(formatVertex(v5)).append("\n");
    sb.append("v ").append(formatVertex(v6)).append("\n");
    sb.append("v ").append(formatVertex(v7)).append("\n");
    sb.append("\n");

    // Write faces (12 triangles)
    sb.append("# ===== TRIANGLES =====\n");

    // Back face (-Z): vertices 0,1,2,3
    sb.append("f 1 2 3\n");
    sb.append("f 1 3 4\n");

    // Front face (+Z): vertices 4,5,6,7
    sb.append("f 5 7 6\n");
    sb.append("f 5 8 7\n");

    // Bottom face (-Y): vertices 0,1,4,5
    sb.append("f 1 5 6\n");
    sb.append("f 1 6 2\n");

    // Top face (+Y): vertices 2,3,6,7
    sb.append("f 3 7 8\n");
    sb.append("f 3 8 4\n");

    // Left face (-X): vertices 0,3,4,7
    sb.append("f 1 4 8\n");
    sb.append("f 1 8 5\n");

    // Right face (+X): vertices 1,2,5,6
    sb.append("f 2 6 7\n");
    sb.append("f 2 7 3\n");

    sb.append("\n");

    // Write material
    if (material != null) {
      sb.append("# ===== MATERIAL =====\n");
      sb.append("material = ").append(material.toString()).append("\n");
    }

    sb.append("\n# ===== END =====\n");

    return sb.toString();
  }

  /** Helper method to format a vertex for OBJ output. */
  private String formatVertex(Point3 v) {
    return String.format(java.util.Locale.US, "%.6f %.6f %.6f", v.x, v.y, v.z);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("Box ").append(name).append(" {\n");
    sb.append("    name = ").append(name).append(";\n");
    sb.append("    width = ").append(width).append(";\n");
    sb.append("    height = ").append(height).append(";\n");
    sb.append("    depth = ").append(depth).append(";\n");
    sb.append("\n");
    sb.append("    ").append(getTransform().toString()).append("\n");
    sb.append("\n");
    sb.append("    firstAnim_").append(animTransforms[0].toString()).append("\n");
    sb.append("    secondAnim_").append(animTransforms[1].toString()).append("\n");
    sb.append("    otherAnimationInfo = ").append(otherAnimationInfo).append(";\n");
    sb.append("\n");
    sb.append("    shadowColor = ").append(ColorUtil.toColorString(shadowColor)).append(";\n");
    sb.append("    shadowBias = ").append(shadowBias).append(";\n");
    sb.append("\n");
    sb.append("    isVisibleSpecial = ").append(isVisibleSpecial).append(";\n");
    sb.append("    isVisible = ").append(isVisible).append(";\n");
    sb.append("    isShadowEnable = ").append(isShadowEnable).append(";\n");
    sb.append("    isShadowOnly = ").append(isShadowOnly).append(";\n");
    sb.append("    isReflective = ").append(isReflective).append(";\n");
    sb.append("    isRefractive = ").append(isRefractive).append(";\n");
    sb.append("\n");
    sb.append("    material = ")
        .append(getMaterial() != null ? getMaterial().toString() : "null")
        .append(";\n");
    sb.append("}");
    return sb.toString();
  }
}
