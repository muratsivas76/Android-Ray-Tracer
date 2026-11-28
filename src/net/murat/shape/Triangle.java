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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.murat.material.Material;
import net.murat.math.*;
import net.murat.util.ColorUtil;

/**
 * Triangle class represents a single triangle, defined by three vertices. It implements the EMShape
 * interface, supporting Matrix4 transformations. The vertices are defined in the triangle's local
 * coordinate system.
 *
 * <p>This implementation is 100% garbage-free during render() execution. All temporary objects are
 * pre-allocated as instance fields and reused.
 *
 * @author Murat iNAN
 */
public class Triangle implements EMShape {

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

  // =========================================================================
  // TRIANGLE PROPERTIES
  // =========================================================================

  /** Vertices defined in the triangle's LOCAL coordinate system. */
  private final Point3 localV0, localV1, localV2;

  /** The material applied to this triangle. */
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

  /** Precomputed local normal for optimization. */
  private Vector3 precomputedLocalNormal;

  /** Cached triangulated mesh (just the triangle itself). */
  private List<Triangle> cachedTriangles;

  /** Name of this triangle for identification. */
  private String name = "triangle";

  // =========================================================================
  // GC-FREE SCRATCH POOL - Zero allocation per intersection test
  // =========================================================================

  /** Temporary point for world hit. */
  private final Point3 tempHitPoint = new Point3();

  /** Temporary origin in local space. */
  private final Point3 localOrigin = new Point3();

  /** Temporary direction in local space. */
  private final Vector3 localDirection = new Vector3();

  /** Temporary edge1 vector. */
  private final Vector3 edge1 = new Vector3();

  /** Temporary edge2 vector. */
  private final Vector3 edge2 = new Vector3();

  /** Temporary pvec vector (localDirection × edge2). */
  private final Vector3 pvec = new Vector3();

  /** Temporary tvec vector (localOrigin - localV0). */
  private final Vector3 tvec = new Vector3();

  /** Temporary qvec vector (tvec × edge1). */
  private final Vector3 qvec = new Vector3();

  /** Temporary world normal. */
  private final Vector3 worldNormal = new Vector3();

  /** Pre-allocated single hit for intersectAll. */
  private final Intersection singleHit = new Intersection();

  /** Pre-allocated single interval for intersectAll. */
  private final IntersectionInterval singleInterval =
      new IntersectionInterval(0, 0, singleHit, singleHit);

  // =========================================================================
  // CONSTRUCTORS
  // =========================================================================

  /**
   * Constructs a triangle using its three vertices in its LOCAL coordinate system. The material
   * must be set separately using setMaterial(). The transformation matrix is initialized to
   * identity.
   *
   * @param v0 The first vertex in local space.
   * @param v1 The second vertex in local space.
   * @param v2 The third vertex in local space.
   */
  public Triangle(Point3 v0, Point3 v1, Point3 v2) {
    this.localV0 = new Point3(v0);
    this.localV1 = new Point3(v1);
    this.localV2 = new Point3(v2);
    this.material = null;

    // Initialize transforms to identity
    this.transform = new Matrix4();
    this.inverseTransform = new Matrix4();
    this.inverseTransposeTransformForNormal = new Matrix4();

    precomputeLocalNormal();
  }

  /**
   * Constructs a triangle with the specified vertices and material.
   *
   * @param v0 The first vertex in local space.
   * @param v1 The second vertex in local space.
   * @param v2 The third vertex in local space.
   * @param material The material to apply.
   */
  public Triangle(Point3 v0, Point3 v1, Point3 v2, Material material) {
    this(v0, v1, v2);
    this.setMaterial(material);
  }

  // =========================================================================
  // INTERNAL METHODS
  // =========================================================================

  /**
   * Precomputes the normal vector of the triangle in its LOCAL coordinate system. This method is
   * called during construction and when vertices change.
   */
  private void precomputeLocalNormal() {
    edge1.set(localV1).subtract(localV0);
    edge2.set(localV2).subtract(localV0);
    this.precomputedLocalNormal = edge1.cross(edge2).normalize();
  }

  /** Updates the inverse transformation matrices after transform changes. */
  /**
   * private void updateTransforms() { Matrix4 inv = this.transform.inverse(); this.inverseTransform
   * = (inv != null) ? inv : new Matrix4(); this.inverseTransposeTransformForNormal =
   * this.inverseTransform.transpose(); if (this.inverseTransposeTransformForNormal == null) {
   * this.inverseTransposeTransformForNormal = new Matrix4(); } }
   */

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
   * Calculates the normal vector at a given point on the Triangle's surface. This method is 100%
   * garbage-free.
   *
   * @param worldPoint The point on the Triangle's surface in world coordinates
   * @return The normalized normal vector at that point in world coordinates
   */
  @Override
  public Vector3 getNormalAt(Point3 worldPoint) {
    if (inverseTransposeTransformForNormal == null) {
      return FALLBACK_NORMAL;
    }

    inverseTransposeTransformForNormal.transformVector(precomputedLocalNormal, worldNormal);
    worldNormal.normalize();
    return worldNormal;
  }

  /**
   * Calculates the intersection of a ray with this triangle using the Möller-Trumbore algorithm.
   * This method is 100% garbage-free.
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

    // Transform ray to local space
    inverseTransform.transformPoint(ray.getOrigin(), localOrigin);
    inverseTransform.transformDirection(ray.getDirection(), localDirection);

    // Compute edges
    edge1.set(localV1).subtract(localV0);
    edge2.set(localV2).subtract(localV0);

    // pvec = localDirection × edge2
    pvec.setCross(localDirection, edge2);

    // det = edge1 · pvec
    double det = edge1.dot(pvec);

    // Ray is parallel to triangle plane
    if (Math.abs(det) < EPSILON) {
      return false;
    }

    double invDet = 1.0 / det;

    // tvec = localOrigin - localV0
    tvec.set(localOrigin).subtract(localV0);

    // u = tvec · pvec * invDet
    double u = tvec.dot(pvec) * invDet;

    // Check barycentric coordinate U
    if (u < 0.0 || u > 1.0) {
      return false;
    }

    // qvec = tvec × edge1
    qvec.setCross(tvec, edge1);

    // v = localDirection · qvec * invDet
    double v = localDirection.dot(qvec) * invDet;

    // Check barycentric coordinate V
    if (v < 0.0 || u + v > 1.0) {
      return false;
    }

    // t = edge2 · qvec * invDet
    double t = edge2.dot(qvec) * invDet;

    // Check if intersection is in front of ray
    if (t < EPSILON) {
      return false;
    }

    // Calculate world space hit point
    ray.pointAtParameter(t, tempHitPoint);
    Vector3 normal = getNormalAt(tempHitPoint);

    // Store intersection data
    hit.set(tempHitPoint, normal, t, this);

    return true;
  }

  /**
   * Calculates the intersection interval for this (zero-thickness) triangle.
   *
   * <p>A triangle is a single planar surface, so the ray either misses it or crosses it at exactly
   * one t value. The returned interval is therefore degenerate: tEnter == tExit. CSG operations
   * treat this as an instantaneous boundary crossing.
   *
   * <p>The Möller-Trumbore test is performed in the triangle's local space with the local-space
   * ray direction left un-normalized, so t stays in the parametrization of the incoming ray
   * (point = origin + direction * t) even when this triangle has a scale. This is required for
   * CSG, because the CSG combine step compares t values coming from different child shapes.
   *
   * <p>Note: This method returns pre-allocated objects. The caller must NOT modify the returned
   * objects as they are reused.
   *
   * @param ray The ray to test, in the caller's coordinates
   * @return A singleton list with one degenerate interval if hit, otherwise empty list.
   */
  @Override
  public List<IntersectionInterval> intersectAll(Ray ray) {
    if (inverseTransform == null) {
      return EMPTY_INTERVAL_LIST;
    }

    // Transform ray to local space using pre-allocated scratch.
    // Direction is intentionally NOT normalized: t must stay in the incoming ray's parametrization.
    inverseTransform.transformPoint(ray.getOrigin(), localOrigin);
    inverseTransform.transformDirection(ray.getDirection(), localDirection);

    // Möller-Trumbore, exactly as in intersect(Ray, Intersection).
    edge1.set(localV1).subtract(localV0);
    edge2.set(localV2).subtract(localV0);

    pvec.setCross(localDirection, edge2);
    double det = edge1.dot(pvec);

    // Ray parallel to the triangle plane.
    if (Math.abs(det) < EPSILON) {
      return EMPTY_INTERVAL_LIST;
    }

    double invDet = 1.0 / det;

    tvec.set(localOrigin).subtract(localV0);
    double u = tvec.dot(pvec) * invDet;
    if (u < 0.0 || u > 1.0) {
      return EMPTY_INTERVAL_LIST;
    }

    qvec.setCross(tvec, edge1);
    double v = localDirection.dot(qvec) * invDet;
    if (v < 0.0 || u + v > 1.0) {
      return EMPTY_INTERVAL_LIST;
    }

    double t = edge2.dot(qvec) * invDet;
    if (t < EPSILON) {
      return EMPTY_INTERVAL_LIST;
    }

    // Compute world-space hit point. t is valid in the original ray parametrization.
    ray.pointAtParameter(t, tempHitPoint);
    Vector3 normal = getNormalAt(tempHitPoint);

    // Reuse the pre-allocated singleHit for the (degenerate) entry/exit.
    singleHit.set(tempHitPoint, normal, t, this);
    singleInterval.setEntry(t, singleHit);
    singleInterval.setExit(t, singleHit);

    return java.util.Collections.singletonList(singleInterval);
  }

  // =========================================================================
  // GETTERS
  // =========================================================================

  public Point3 getLocalV0() {
    return localV0;
  }

  public Point3 getLocalV1() {
    return localV1;
  }

  public Point3 getLocalV2() {
    return localV2;
  }

  public Point3 getV0() {
    return localV0;
  }

  public Point3 getV1() {
    return localV1;
  }

  public Point3 getV2() {
    return localV2;
  }

  // =========================================================================
  // SETTERS
  // =========================================================================

  public void setV0(Point3 v0) {
    this.localV0.set(v0);
    precomputeLocalNormal();
    cachedTriangles = null;
  }

  public void setV1(Point3 v1) {
    this.localV1.set(v1);
    precomputeLocalNormal();
    cachedTriangles = null;
  }

  public void setV2(Point3 v2) {
    this.localV2.set(v2);
    precomputeLocalNormal();
    cachedTriangles = null;
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
    return "Triangle: " + getTransform().toString();
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
  // MESH METHODS
  // =========================================================================

  @Override
  public List<Triangle> generateMesh() {
    if (cachedTriangles != null) {
      return cachedTriangles;
    }

    List<Triangle> triangles = new ArrayList<Triangle>();
    triangles.add(new Triangle(localV0, localV1, localV2));

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

  @Override
  public String toObjString() {
    StringBuilder sb = new StringBuilder();

    sb.append("# Murat Ray Tracer Mesh File\n");
    sb.append("# Shape: Triangle\n");
    sb.append("# Name: ").append(name).append("\n");
    sb.append("# Generated: ").append(new java.util.Date()).append("\n");

    if (transform != null) {
      sb.append("# Transform: ").append(transform.toString()).append("\n");
    }

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

    List<Point3> vertices = new ArrayList<Point3>();
    Map<String, Integer> vertexMap = new HashMap<String, Integer>();
    List<int[]> faces = new ArrayList<int[]>();

    for (Triangle tri : triangles) {
      Point3 v0 = trans.transformPoint(tri.getLocalV0());
      Point3 v1 = trans.transformPoint(tri.getLocalV1());
      Point3 v2 = trans.transformPoint(tri.getLocalV2());

      int i0 = getOrAddVertex(vertices, vertexMap, v0);
      int i1 = getOrAddVertex(vertices, vertexMap, v1);
      int i2 = getOrAddVertex(vertices, vertexMap, v2);

      faces.add(new int[] {i0, i1, i2});
    }

    sb.append("# ===== VERTICES =====\n");
    for (Point3 v : vertices) {
      sb.append(String.format(java.util.Locale.US, "v %.6f %.6f %.6f\n", v.x, v.y, v.z));
    }
    sb.append("\n");

    sb.append("# ===== FACES =====\n");
    for (int[] tri : faces) {
      sb.append(
          String.format(java.util.Locale.US, "f %d %d %d\n", tri[0] + 1, tri[1] + 1, tri[2] + 1));
    }
    sb.append("\n");

    if (material != null) {
      sb.append("# ===== MATERIAL =====\n");
      sb.append("material = ").append(material.toString()).append("\n");
    }

    sb.append("\n# ===== END =====\n");

    return sb.toString();
  }

  /** Helper method for OBJ export - adds a vertex to the list if not already present. */
  private int getOrAddVertex(List<Point3> vertices, Map<String, Integer> map, Point3 v) {
    String key = String.format(java.util.Locale.US, "%.6f,%.6f,%.6f", v.x, v.y, v.z);
    Integer existing = map.get(key);
    if (existing != null) {
      return existing;
    }
    int index = vertices.size();
    vertices.add(new Point3(v.x, v.y, v.z));
    map.put(key, index);
    return index;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("Triangle ").append(name).append(" {\n");
    sb.append("    name = ").append(name).append(";\n");
    sb.append("    v0 = ").append(localV0).append(";\n");
    sb.append("    v1 = ").append(localV1).append(";\n");
    sb.append("    v2 = ").append(localV2).append(";\n");
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
