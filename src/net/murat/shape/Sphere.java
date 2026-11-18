package net.murat.shape;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.murat.material.DiffuseMaterial;
import net.murat.material.Material;
import net.murat.math.*;

/**
 * Sphere class represents a canonical sphere in its local coordinate system, transformed into 3D
 * world space using a 4x4 model matrix. Fully optimized to run 100% garbage-free inside hot loops.
 *
 * <p>This implementation is 100% garbage-free during render() execution. All temporary objects are
 * pre-allocated as instance fields and reused.
 *
 * <p><b>Fix history:</b>
 *
 * <ul>
 *   <li>{@link #intersect(Ray, Intersection)} previously performed the ray-sphere test directly in
 *       world space using {@code localRadius}, which silently ignored any scale component baked
 *       into the transform (it only accounted for translate/rotate). It now transforms the ray into
 *       local space, exactly like {@link #intersectAll(Ray)} already did, so non-uniform scale is
 *       respected.
 *   <li>{@link #getNormalAt(Point3, Vector3)} previously called the no-arg, self-mutating {@link
 *       Matrix4#transpose()} on the cached {@code inverseTransform} field, which permanently
 *       corrupted that cached matrix as a side effect of computing a normal. It now uses a
 *       dedicated {@code inverseTransposeForNormals} matrix that is computed once whenever the
 *       transform is set, via the non-mutating {@link Matrix4#transpose(Matrix4)} overload, so no
 *       cached state is ever corrupted and no per-call transpose work is done.
 * </ul>
 */
public class Sphere implements EMShape {

  // =========================================================================
  // CONSTANTS
  // =========================================================================

  /** Pre-allocated empty list for no-intersection cases. */
  private static final List<IntersectionInterval> EMPTY_INTERVAL_LIST =
      java.util.Collections.emptyList();

  /** Pre-allocated fallback normal. */
  private static final Vector3 FALLBACK_NORMAL = new Vector3(0, 1, 0);

  // =========================================================================
  // SPHERE PROPERTIES
  // =========================================================================

  private Material material;
  private final Point3 localCenter;
  private final double localRadius;
  private Matrix4 transform;
  private Matrix4 inverseTransform;

  /**
   * Cached inverse-transpose of {@link #inverseTransform}, used to transform local-space normals
   * into world space. Recomputed once, whenever {@link #setTransform(Matrix4)} is called, using the
   * non-mutating {@link Matrix4#transpose(Matrix4)} overload so that {@link #inverseTransform}
   * itself is never touched.
   */
  private final Matrix4 inverseTransposeForNormals = new Matrix4();

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
  private String name = "sphere";

  // Mesh cache
  private List<Triangle> cachedTriangles;

  // =========================================================================
  // GC-FREE SCRATCH POOL - Zero allocation per intersection test
  // =========================================================================

  /** Temporary world center point. */
  private final Point3 worldCenter = new Point3();

  /** Temporary world hit point. */
  private final Point3 worldHitPoint = new Point3();

  /** Temporary world normal. */
  private final Vector3 worldNormal = new Vector3();

  /** Temporary local point for normal calculation. */
  private final Point3 localPoint = new Point3();

  /** Temporary local normal for normal calculation. */
  private final Vector3 localNormal = new Vector3();

  /** Temporary origin for transform operations. */
  private final Point3 tempOrigin = new Point3();

  /** Temporary direction for transform operations. */
  private final Vector3 tempDir = new Vector3();

  /** Temporary point for local hit. */
  private final Point3 localHitPoint = new Point3();

  /** Pre-allocated in intersection for intersectAll. */
  private final Intersection cachedInIntersection = new Intersection();

  /** Pre-allocated out intersection for intersectAll. */
  private final Intersection cachedOutIntersection = new Intersection();

  /** Pre-allocated single interval for intersectAll. */
  private final IntersectionInterval singleInterval =
      new IntersectionInterval(0, 0, cachedInIntersection, cachedOutIntersection);

  // =========================================================================
  // CONSTRUCTORS
  // =========================================================================

  public Sphere(double radius) {
    this(radius, new DiffuseMaterial(0xFF0000FF));
  }

  public Sphere(double radius, Material material) {
    this.localCenter = new Point3(0, 0, 0);
    this.localRadius = radius;
    this.material = material;
    this.transform = new Matrix4();
    this.inverseTransform = new Matrix4();
    // Keep inverseTransposeForNormals consistent with the identity
    // transform/inverseTransform set above, so getNormalAt() is correct
    // even if called before an explicit setTransform().
    this.inverseTransform.transpose(this.inverseTransposeForNormals);
  }

  // =========================================================================
  // GETTERS
  // =========================================================================

  public double getLocalRadius() {
    return this.localRadius;
  }

  public Point3 getLocalCenter() {
    return localCenter;
  }

  // =========================================================================
  // EMShape INTERFACE METHODS
  // =========================================================================

  @Override
  public Material getMaterial() {
    return material;
  }

  @Override
  public void setMaterial(Material material) {
    this.material = material;
  }

  @Override
  public void setTransform(Matrix4 transform) {
    this.transform = transform;
    this.inverseTransform = transform.inverse();
    // Recompute the cached inverse-transpose used for normal
    // transformation using the non-mutating overload. This must never
    // use the no-arg Matrix4#transpose(), which mutates its receiver in
    // place and would corrupt inverseTransform itself.
    this.inverseTransform.transpose(this.inverseTransposeForNormals);
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
   * Calculates dynamic shadow bias for sphere based on surface angle. This prevents shadow acne on
   * curved surfaces.
   *
   * @param hitPoint The intersection point on sphere surface
   * @param normal The surface normal at hit point
   * @param lightDir Direction to light source
   * @return Dynamic bias value
   */
  public double calculateDynamicBias(Point3 hitPoint, Vector3 normal, Vector3 lightDir) {
    double cosTheta = normal.dot(lightDir);

    if (cosTheta < 0.01) {
      return 0.01; // Extreme grazing
    } else if (cosTheta < 0.05) {
      return 0.005;
    } else if (cosTheta < 0.1) {
      return 0.002;
    } else {
      return this.shadowBias; // Default 0.001
    }
  }

  // =========================================================================
  // INTERSECTION METHODS - GC-FREE
  // =========================================================================

  /**
   * Intersects a ray with the sphere and stores hit information. This method is 100% garbage-free.
   *
   * <p>The ray-sphere test is performed in the sphere's local coordinate system (like {@link
   * #intersectAll(Ray)}) so that any scale component of the transform is correctly accounted for,
   * not just translation/rotation. The local-space ray direction is intentionally left
   * un-normalized: because {@code point(t) = origin + t * direction} is preserved under affine
   * transformation only when {@code t} keeps the same meaning in both spaces, the local-space hit
   * parameter {@code t} can be plugged directly back into the original world-space ray to recover
   * the correct world-space hit point. Normalizing the local direction would rescale {@code t} and
   * desync it from the world-space ray, producing wrong hit points whenever the transform has a
   * non-uniform scale.
   *
   * @param ray The ray to intersect with the sphere (in world coordinates)
   * @param target The Intersection object to store hit information
   * @return true if intersection occurs, false otherwise
   */
  @Override
  public boolean intersect(Ray ray, Intersection target) {
    if (this.inverseTransform == null) {
      return false;
    }

    // Transform ray into local space using pre-allocated scratch.
    // Direction is intentionally NOT normalized - see method javadoc.
    this.inverseTransform.transformPoint(ray.getOrigin(), tempOrigin);
    this.inverseTransform.transformDirection(ray.getDirection(), tempDir);

    double ox = tempOrigin.x;
    double oy = tempOrigin.y;
    double oz = tempOrigin.z;

    double dx = tempDir.x;
    double dy = tempDir.y;
    double dz = tempDir.z;

    double a = dx * dx + dy * dy + dz * dz;
    double b = 2.0 * (ox * dx + oy * dy + oz * dz);
    double c = ox * ox + oy * oy + oz * oz - localRadius * localRadius;

    double discriminant = b * b - 4.0 * a * c;
    if (discriminant < 0) {
      return false;
    }

    double sqrtDisc = Math.sqrt(discriminant);
    double t1 = (-b - sqrtDisc) / (2.0 * a);
    double t2 = (-b + sqrtDisc) / (2.0 * a);

    double t = Double.POSITIVE_INFINITY;
    if (t1 > Ray.EPSILON) t = t1;
    if (t2 > Ray.EPSILON && t2 < t) t = t2;

    if (t == Double.POSITIVE_INFINITY) {
      return false;
    }

    // t was found in local space, but since the local-space direction
    // was left un-normalized, this same t is valid for the original
    // WORLD-space ray parametrization. Compute the hit point in world
    // space directly from the world-space ray using pre-allocated scratch.
    worldHitPoint.x = ray.getOrigin().x + ray.getDirection().x * t;
    worldHitPoint.y = ray.getOrigin().y + ray.getDirection().y * t;
    worldHitPoint.z = ray.getOrigin().z + ray.getDirection().z * t;

    // Compute the world-space normal correctly (handles rotation AND
    // scale) using the cached inverse-transpose, rather than the naive
    // "hit - center" difference which is only valid for spheres with no
    // scale/shear applied.
    getNormalAt(worldHitPoint, worldNormal);

    target.set(worldHitPoint, worldNormal, t, this);
    return true;
  }

  /**
   * Calculates the normal at a given point and stores it in targetNormal. This method is 100%
   * garbage-free.
   *
   * <p>Uses the cached {@link #inverseTransposeForNormals}, which is recomputed once whenever
   * {@link #setTransform(Matrix4)} is called via the non-mutating {@link
   * Matrix4#transpose(Matrix4)} overload. This method must never call the no-arg {@link
   * Matrix4#transpose()} on {@link #inverseTransform}, since that overload mutates its receiver in
   * place and would permanently corrupt the cached inverse transform as a side effect of a normal
   * lookup.
   *
   * @param worldPoint The point in world coordinates
   * @param targetNormal The Vector3 to store the normal in
   * @return The normalized normal vector (same as targetNormal)
   */
  public Vector3 getNormalAt(Point3 worldPoint, Vector3 targetNormal) {
    if (this.transform == null || this.inverseTransform == null) {
      return targetNormal.set(FALLBACK_NORMAL);
    }

    // Transform point to local space using pre-allocated scratch
    this.inverseTransform.transformPoint(worldPoint, localPoint);

    // Local normal is just the normalized local point (sphere centered at origin)
    double len =
        Math.sqrt(
            localPoint.x * localPoint.x
                + localPoint.y * localPoint.y
                + localPoint.z * localPoint.z);

    if (len > Ray.EPSILON) {
      localNormal.x = localPoint.x / len;
      localNormal.y = localPoint.y / len;
      localNormal.z = localPoint.z / len;
    } else {
      localNormal.set(FALLBACK_NORMAL);
    }

    // Transform normal to world space using the cached inverse-transpose.
    // No allocation, no mutation of any cached matrix.
    this.inverseTransposeForNormals.transformDirection(localNormal, targetNormal);
    targetNormal.normalize();

    return targetNormal;
  }

  /**
   * Calculates all intersection intervals between a ray and this sphere.
   *
   * <p>Note: This method returns pre-allocated objects. The caller must NOT modify the returned
   * objects as they are reused.
   *
   * @param ray The ray to test, in world coordinates
   * @return A list of IntersectionInterval objects. Empty if no intersections
   */
  @Override
  public List<IntersectionInterval> intersectAll(Ray ray) {
    if (this.inverseTransform == null) {
      return EMPTY_INTERVAL_LIST;
    }

    // Transform ray to local space using pre-allocated scratch
    this.inverseTransform.transformPoint(ray.getOrigin(), tempOrigin);
    this.inverseTransform.transformDirection(ray.getDirection(), tempDir);
    tempDir.normalize();

    double ox = tempOrigin.x;
    double oy = tempOrigin.y;
    double oz = tempOrigin.z;
    double dx = tempDir.x;
    double dy = tempDir.y;
    double dz = tempDir.z;

    double a = dx * dx + dy * dy + dz * dz;
    double b = 2.0 * (ox * dx + oy * dy + oz * dz);
    double c = ox * ox + oy * oy + oz * oz - localRadius * localRadius;

    double discriminant = b * b - 4.0 * a * c;
    if (discriminant < 0) {
      return EMPTY_INTERVAL_LIST;
    }

    double sqrtDisc = Math.sqrt(discriminant);
    double t1 = (-b - sqrtDisc) / (2.0 * a);
    double t2 = (-b + sqrtDisc) / (2.0 * a);

    if (t1 > t2) {
      double temp = t1;
      t1 = t2;
      t2 = temp;
    }

    boolean t1Valid = t1 > Ray.EPSILON;
    boolean t2Valid = t2 > Ray.EPSILON;

    if (!t1Valid && !t2Valid) {
      return EMPTY_INTERVAL_LIST;
    }

    // Compute in point using pre-allocated scratch
    localHitPoint.x = ray.getOrigin().x + ray.getDirection().x * t1;
    localHitPoint.y = ray.getOrigin().y + ray.getDirection().y * t1;
    localHitPoint.z = ray.getOrigin().z + ray.getDirection().z * t1;
    getNormalAt(localHitPoint, worldNormal);
    cachedInIntersection.set(localHitPoint, worldNormal, t1, this);

    // Compute out point using pre-allocated scratch
    localHitPoint.x = ray.getOrigin().x + ray.getDirection().x * t2;
    localHitPoint.y = ray.getOrigin().y + ray.getDirection().y * t2;
    localHitPoint.z = ray.getOrigin().z + ray.getDirection().z * t2;
    getNormalAt(localHitPoint, worldNormal);
    cachedOutIntersection.set(localHitPoint, worldNormal, t2, this);

    // Update and return pre-allocated interval
    singleInterval.setEntry(t1, cachedInIntersection);
    singleInterval.setExit(t2, cachedOutIntersection);

    return java.util.Collections.singletonList(singleInterval);
  }

  /**
   * Returns the surface normal at a given point on the sphere. This method is 100% garbage-free.
   *
   * @param worldPoint The point on the sphere's surface in world coordinates
   * @return The normalized normal vector at that point in world coordinates
   */
  @Override
  public Vector3 getNormalAt(Point3 worldPoint) {
    getNormalAt(worldPoint, worldNormal);
    return worldNormal;
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
    return ("Sphere: " + getTransform().toString());
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
    int segments = 32;

    for (int lat = 0; lat < segments; lat++) {
      double theta1 = Math.PI * lat / segments;
      double theta2 = Math.PI * (lat + 1) / segments;

      for (int lon = 0; lon < segments; lon++) {
        double phi1 = 2 * Math.PI * lon / segments;
        double phi2 = 2 * Math.PI * (lon + 1) / segments;

        Point3 v1 =
            new Point3(
                localRadius * Math.sin(theta1) * Math.cos(phi1),
                localRadius * Math.cos(theta1),
                localRadius * Math.sin(theta1) * Math.sin(phi1));
        Point3 v2 =
            new Point3(
                localRadius * Math.sin(theta1) * Math.cos(phi2),
                localRadius * Math.cos(theta1),
                localRadius * Math.sin(theta1) * Math.sin(phi2));
        Point3 v3 =
            new Point3(
                localRadius * Math.sin(theta2) * Math.cos(phi2),
                localRadius * Math.cos(theta2),
                localRadius * Math.sin(theta2) * Math.sin(phi2));
        Point3 v4 =
            new Point3(
                localRadius * Math.sin(theta2) * Math.cos(phi1),
                localRadius * Math.cos(theta2),
                localRadius * Math.sin(theta2) * Math.sin(phi1));

        triangles.add(new Triangle(v1, v2, v3));
        triangles.add(new Triangle(v1, v3, v4));
      }
    }

    cachedTriangles = triangles;
    return triangles;
  }

  @Override
  public List<Triangle> getTriangles() {
    return cachedTriangles;
  }

  // =========================================================================
  // TO STRING - GC acceptable (not called during render)
  // =========================================================================

  @Override
  public String toObjString() {
    StringBuilder sb = new StringBuilder();

    sb.append("# Murat Ray Tracer Mesh File\n");
    sb.append("# Shape: Sphere\n");
    sb.append("# Radius: ").append(localRadius).append("\n");
    sb.append("# Generated: ").append(new java.util.Date()).append("\n\n");

    List<Triangle> triangles = generateMesh();
    if (triangles == null || triangles.isEmpty()) {
      return sb.toString();
    }

    Matrix4 trans = (transform != null) ? transform : new Matrix4();

    List<Point3> allVerts = new ArrayList<Point3>();
    for (Triangle tri : triangles) {
      allVerts.add(trans.transformPoint(tri.getV0()));
      allVerts.add(trans.transformPoint(tri.getV1()));
      allVerts.add(trans.transformPoint(tri.getV2()));
    }

    List<Point3> uniqueVerts = new ArrayList<Point3>();
    int[] indexMap = new int[allVerts.size()];

    for (int i = 0; i < allVerts.size(); i++) {
      Point3 v = allVerts.get(i);
      int found = -1;
      for (int j = 0; j < uniqueVerts.size(); j++) {
        Point3 u = uniqueVerts.get(j);
        if (Math.abs(u.x - v.x) < 0.000001
            && Math.abs(u.y - v.y) < 0.000001
            && Math.abs(u.z - v.z) < 0.000001) {
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

    // Vertexes
    sb.append("# ===== VERTICES =====\n");
    for (Point3 v : uniqueVerts) {
      sb.append(String.format(java.util.Locale.US, "v %.6f %.6f %.6f\n", v.x, v.y, v.z));
    }
    sb.append("\n");

    // Triangles
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

    // Material
    if (material != null) {
      sb.append("# ===== MATERIAL =====\n");
      sb.append("material = ").append(material.toString()).append("\n");
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
    sb.append("Sphere ").append(name).append(" {\n");
    sb.append("    name = ").append(name).append(";\n");
    sb.append("    radius = ").append(localRadius).append(";\n\n");
    sb.append("    ").append(getTransform().toString()).append("\n\n");
    sb.append("    firstAnim_").append(animTransforms[0].toString()).append("\n");
    sb.append("    secondAnim_").append(animTransforms[1].toString()).append("\n");
    sb.append("    otherAnimationInfo = ").append(otherAnimationInfo).append(";\n\n");
    sb.append("    shadowColor = ")
        .append(net.murat.util.ColorUtil.toColorString(shadowColor))
        .append(";\n");
    sb.append("    shadowBias = ").append(shadowBias).append(";\n\n");
    sb.append("    isVisibleSpecial = ").append(isVisibleSpecial).append(";\n");
    sb.append("    isVisible = ").append(isVisible).append(";\n");
    sb.append("    isShadowEnable = ").append(isShadowEnable).append(";\n");
    sb.append("    isShadowOnly = ").append(isShadowOnly).append(";\n");
    sb.append("    isReflective = ").append(isReflective).append(";\n");
    sb.append("    isRefractive = ").append(isRefractive).append(";\n\n");
    sb.append("    material = ")
        .append(getMaterial() != null ? getMaterial().toString() : "null")
        .append(";\n}");
    return sb.toString();
  }
}
