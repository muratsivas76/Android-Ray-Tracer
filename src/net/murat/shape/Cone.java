package net.murat.shape;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.murat.material.Material;
import net.murat.math.*;
import net.murat.util.ColorUtil;

/**
 * Cone class represents a finite cone in 3D space, aligned along a local axis. Implements EMShape
 * interface, now supporting Matrix4 transformations.
 *
 * <p>This implementation is 100% garbage-free during render() execution. All temporary objects are
 * pre-allocated as instance fields and reused.
 */
public class Cone implements EMShape {

  // =========================================================================
  // CONSTANTS
  // =========================================================================

  /** Pre-allocated empty list for no-intersection cases. */
  private static final List<IntersectionInterval> EMPTY_INTERVAL_LIST =
      java.util.Collections.emptyList();

  // =========================================================================
  // CONE PROPERTIES
  // =========================================================================

  private Material material;

  private final Point3 localBaseCenter;
  private final double localRadius;
  private final double localHeight;
  private final Vector3 localAxis;
  private final Point3 localApex;

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

  private Matrix4 transform;
  private Matrix4 inverseTransform;
  private Matrix4 inverseTransposeTransformForNormal;

  private String name = "cone";

  // Mesh cache
  private List<Triangle> cachedTriangles;

  // =========================================================================
  // GC-FREE SCRATCH POOL - Zero allocation per intersection test
  // =========================================================================

  /** Temporary point in local space. */
  private final Point3 tempLocalPoint = new Point3();

  /** Temporary hit point. */
  private final Point3 tempHitPoint = new Point3();

  /** Temporary normal in local space. */
  private final Vector3 tempLocalNormal = new Vector3();

  /** Temporary normal in world space. */
  private final Vector3 tempWorldNormal = new Vector3();

  /** Temporary ray in local space. */
  private final Ray tempLocalRay = new Ray(new Point3(), new Vector3(), 1.0);

  /** Temporary hit point in world space. */
  private final Point3 tempWorldHit = new Point3();

  /** Temporary hit point in local space. */
  private final Point3 tempLocalHit = new Point3();

  /** Temporary direction in local space. */
  private final Vector3 tempLocalDir = new Vector3();

  /** Temporary origin for transform operations. */
  private final Point3 tempOrigin = new Point3();

  /** Temporary direction for transform operations. */
  private final Vector3 tempDir = new Vector3();

  /** Temporary vector for normal calculations. */
  private final Vector3 tempVec = new Vector3();

  /** Temporary point for projection. */
  private final Point3 tempProjection = new Point3();

  /** Pre-allocated base normal. */
  private final Vector3 baseNormal = new Vector3(0, -1, 0);

  /** Pre-allocated intersection buffer for intersectAll (max 4 hits). */
  private final Intersection[] hitBuffer = new Intersection[4];

  /** Pre-allocated distance array for sorting. */
  private final double[] tValues = new double[4];

  /** Pre-allocated single hit for intersectAll fallback. */
  private final Intersection singleHit = new Intersection();
  
  /** Pre-allocated entry hit for intersectAll. */
  private final Intersection inHit = new Intersection();

  /** Pre-allocated exit hit for intersectAll. */
  private final Intersection outHit = new Intersection();

  /** Pre-allocated single interval for intersectAll. */
  private final IntersectionInterval singleInterval =
      new IntersectionInterval(0, 0, singleHit, singleHit);

  /** Cached k_cone value (radius^2 / height^2). */
  private final double k_cone;

  // =========================================================================
  // CONSTRUCTORS
  // =========================================================================

  /**
   * Constructs a cone with a base center, radius, and height in its LOCAL coordinate system. By
   * default, the cone's base is at (0,0,0) and its axis extends along the Y-axis.
   *
   * @param radius The radius of the cone's base
   * @param height The height of the cone
   */
  public Cone(double radius, double height) {
    this.localBaseCenter = new Point3(0, 0, 0);
    this.localRadius = radius;
    this.localHeight = height;
    this.localAxis = new Vector3(0, 1, 0);
    this.localApex = new Point3(0, height, 0);
    this.k_cone = (radius * radius) / (height * height);

    this.transform = new Matrix4();
    this.inverseTransform = new Matrix4();
    this.inverseTransposeTransformForNormal = new Matrix4();

    // Initialize hit buffer
    for (int i = 0; i < hitBuffer.length; i++) {
      hitBuffer[i] = new Intersection();
    }
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
    this.inverseTransposeTransformForNormal = this.inverseTransform.inverseTransposeForNormal();
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

  // =========================================================================
  // NORMAL CALCULATION - GC-FREE
  // =========================================================================

  /**
   * Calculates the normal vector at a given point on the cone's surface in WORLD coordinates. This
   * method is 100% garbage-free.
   *
   * @param worldPoint The point on the cone's surface in world coordinates
   * @return The normalized normal vector at that point in world coordinates
   */
  @Override
  public Vector3 getNormalAt(Point3 worldPoint) {
    // Transform world point to local space using pre-allocated scratch
    inverseTransform.transformPoint(worldPoint, tempLocalPoint);

    // Check if hit point is on the base
    if (Math.abs(tempLocalPoint.y - localBaseCenter.y) < Ray.EPSILON) {
      // Base normal: negative Y axis
      tempLocalNormal.set(0, -1, 0);
    } else {
      // Lateral surface normal
      // Vector from apex to hit point
      tempVec.set(tempLocalPoint).subtract(localApex);

      // Projection onto axis
      double m = tempVec.dot(localAxis);

      // Calculate projection point on axis
      double denominator = 1.0 + k_cone;
      tempProjection.x = localApex.x + localAxis.x * (m / denominator);
      tempProjection.y = localApex.y + localAxis.y * (m / denominator);
      tempProjection.z = localApex.z + localAxis.z * (m / denominator);

      // Normal is hit point minus projection point
      tempLocalNormal.set(tempLocalPoint).subtract(tempProjection).normalize();

      // Ensure normal points outward (away from axis)
      if (tempLocalNormal.dot(localAxis) > 0) {
        tempLocalNormal.negate();
      }
    }

    // Transform normal to world space using inverse transpose
    if (inverseTransposeTransformForNormal != null) {
      inverseTransposeTransformForNormal.transformVector(tempLocalNormal, tempWorldNormal);
      tempWorldNormal.normalize();
    } else {
      tempWorldNormal.set(tempLocalNormal);
    }

    return tempWorldNormal;
  }

  // =========================================================================
  // INTERSECTION METHODS - GC-FREE
  // =========================================================================

  /**
   * @deprecated Use {@link #intersect(Ray, Intersection)} instead.
   */
  @Deprecated
  public double intersect(Ray ray) {
    // Transform ray to local space using pre-allocated scratch
    inverseTransform.transformPoint(ray.getOrigin(), tempOrigin);
    inverseTransform.transformDirection(ray.getDirection(), tempDir);
    tempDir.normalize();
    tempLocalRay.set(tempOrigin, tempDir, 1.0);

    double dx = tempLocalRay.getDirection().x;
    double dy = tempLocalRay.getDirection().y;
    double dz = tempLocalRay.getDirection().z;
    double ox = tempLocalRay.getOrigin().x;
    double oy = tempLocalRay.getOrigin().y;
    double oz = tempLocalRay.getOrigin().z;

    // Lateral surface - using cached k_cone
    double a = dx * dx + dz * dz - k_cone * dy * dy;
    double b = 2.0 * (ox * dx + oz * dz - k_cone * dy * (oy - localHeight));
    double c = ox * ox + oz * oz - k_cone * (oy - localHeight) * (oy - localHeight);

    double tLateral = Double.POSITIVE_INFINITY;

    if (Math.abs(a) > Ray.EPSILON) {
      double discriminant = b * b - 4.0 * a * c;
      if (discriminant >= Ray.EPSILON) {
        double sqrtD = Math.sqrt(discriminant);
        double t0 = (-b - sqrtD) / (2.0 * a);
        double t1 = (-b + sqrtD) / (2.0 * a);

        if (t0 > t1) {
          double temp = t0;
          t0 = t1;
          t1 = temp;
        }

        if (t0 > Ray.EPSILON) {
          tempLocalRay.pointAtParameter(t0, tempLocalHit);
          if (tempLocalHit.y <= localHeight + Ray.EPSILON && tempLocalHit.y >= -Ray.EPSILON) {
            tLateral = t0;
          }
        }

        if (t1 > Ray.EPSILON && t1 < tLateral) {
          tempLocalRay.pointAtParameter(t1, tempLocalHit);
          if (tempLocalHit.y <= localHeight + Ray.EPSILON && tempLocalHit.y >= -Ray.EPSILON) {
            tLateral = t1;
          }
        }
      }
    }

    // Base intersection
    double tBase = Double.POSITIVE_INFINITY;
    baseNormal.set(0, -1, 0);
    double denomBase = tempLocalRay.getDirection().dot(baseNormal);

    if (Math.abs(denomBase) > Ray.EPSILON) {
      tempVec.set(localBaseCenter).subtract(tempLocalRay.getOrigin());
      tBase = tempVec.dot(baseNormal) / denomBase;

      if (tBase > Ray.EPSILON) {
        tempLocalRay.pointAtParameter(tBase, tempLocalHit);
        double distSq = tempLocalHit.x * tempLocalHit.x + tempLocalHit.z * tempLocalHit.z;
        if (distSq > localRadius * localRadius + Ray.EPSILON) {
          tBase = Double.POSITIVE_INFINITY;
        }
      } else {
        tBase = Double.POSITIVE_INFINITY;
      }
    }

    double finalT = Math.min(tLateral, tBase);
    return (finalT == Double.POSITIVE_INFINITY) ? -1 : finalT;
  }

  /**
   * Calculates the intersection of a ray with the cone and stores the hit information in the
   * provided Intersection object. This method is 100% garbage-free.
   *
   * @param ray The ray to intersect with the cone (in world coordinates)
   * @param hit The Intersection object to store hit information
   * @return true if intersection occurs, false otherwise
   */
  @Override
  public boolean intersect(Ray ray, Intersection hit) {
    // Transform ray to local space using pre-allocated scratch
    inverseTransform.transformPoint(ray.getOrigin(), tempOrigin);
    inverseTransform.transformDirection(ray.getDirection(), tempDir);
    tempDir.normalize();
    tempLocalRay.set(tempOrigin, tempDir, 1.0);

    double dx = tempLocalRay.getDirection().x;
    double dy = tempLocalRay.getDirection().y;
    double dz = tempLocalRay.getDirection().z;
    double ox = tempLocalRay.getOrigin().x;
    double oy = tempLocalRay.getOrigin().y;
    double oz = tempLocalRay.getOrigin().z;

    // Lateral surface - using cached k_cone
    double a = dx * dx + dz * dz - k_cone * dy * dy;
    double b = 2.0 * (ox * dx + oz * dz - k_cone * dy * (oy - localHeight));
    double c = ox * ox + oz * oz - k_cone * (oy - localHeight) * (oy - localHeight);

    double tLateral = Double.POSITIVE_INFINITY;

    if (Math.abs(a) > Ray.EPSILON) {
      double discriminant = b * b - 4.0 * a * c;
      if (discriminant >= Ray.EPSILON) {
        double sqrtD = Math.sqrt(discriminant);
        double t0 = (-b - sqrtD) / (2.0 * a);
        double t1 = (-b + sqrtD) / (2.0 * a);

        if (t0 > t1) {
          double temp = t0;
          t0 = t1;
          t1 = temp;
        }

        if (t0 > Ray.EPSILON) {
          tempLocalRay.pointAtParameter(t0, tempLocalHit);
          if (tempLocalHit.y <= localHeight + Ray.EPSILON && tempLocalHit.y >= -Ray.EPSILON) {
            tLateral = t0;
          }
        }

        if (t1 > Ray.EPSILON && t1 < tLateral) {
          tempLocalRay.pointAtParameter(t1, tempLocalHit);
          if (tempLocalHit.y <= localHeight + Ray.EPSILON && tempLocalHit.y >= -Ray.EPSILON) {
            tLateral = t1;
          }
        }
      }
    }

    // Base intersection
    double tBase = Double.POSITIVE_INFINITY;
    baseNormal.set(0, -1, 0);
    double denomBase = tempLocalRay.getDirection().dot(baseNormal);

    if (Math.abs(denomBase) > Ray.EPSILON) {
      tempVec.set(localBaseCenter).subtract(tempLocalRay.getOrigin());
      tBase = tempVec.dot(baseNormal) / denomBase;

      if (tBase > Ray.EPSILON) {
        tempLocalRay.pointAtParameter(tBase, tempLocalHit);
        double distSq = tempLocalHit.x * tempLocalHit.x + tempLocalHit.z * tempLocalHit.z;
        if (distSq > localRadius * localRadius + Ray.EPSILON) {
          tBase = Double.POSITIVE_INFINITY;
        }
      } else {
        tBase = Double.POSITIVE_INFINITY;
      }
    }

    double finalT = Math.min(tLateral, tBase);
    if (finalT == Double.POSITIVE_INFINITY || finalT < 0) {
      return false;
    }

    // Fill hit information using pre-allocated scratch
    ray.pointAtParameter(finalT, tempWorldHit);
    Vector3 normal = getNormalAt(tempWorldHit);
    hit.set(tempWorldHit, normal, finalT, this);

    return true;
  }

  /**
   * Calculates the interval [tEnter, tExit] during which the ray is INSIDE this finite cone.
   * CSG operations need the real entry and exit surfaces, not just the first hit.
   *
   * <p>Like {@link #intersect(Ray, Intersection)}, the local-space ray direction is intentionally
   * left un-normalized, so every t value stays in the parametrization of the incoming ray
   * (point = origin + direction * t) even when this cone has a scale. This is required for CSG,
   * because the CSG combine step compares t values coming from different child shapes.
   *
   * <p>Note: This method returns pre-allocated objects. The caller must NOT modify the returned
   * objects as they are reused.
   *
   * @param ray The ray to test, in the caller's coordinates
   * @return A list containing a single interval if the ray passes through the cone, otherwise
   *     empty list.
   */
  @Override
  public List<IntersectionInterval> intersectAll(Ray ray) {
    if (inverseTransform == null) {
      return EMPTY_INTERVAL_LIST;
    }

    // Transform ray to local space using pre-allocated scratch.
    // Direction is intentionally NOT normalized: t must stay in the incoming ray's parametrization.
    inverseTransform.transformPoint(ray.getOrigin(), tempOrigin);
    inverseTransform.transformDirection(ray.getDirection(), tempDir);

    double dx = tempDir.x;
    double dy = tempDir.y;
    double dz = tempDir.z;
    double ox = tempOrigin.x;
    double oy = tempOrigin.y;
    double oz = tempOrigin.z;

    // Collect up to 2 valid lateral hits and 1 base hit into pre-allocated buffers.
    int hitCount = 0;

    // ---- Lateral surface ----
    double a = dx * dx + dz * dz - k_cone * dy * dy;
    if (Math.abs(a) > Ray.EPSILON) {
      double b = 2.0 * (ox * dx + oz * dz - k_cone * dy * (oy - localHeight));
      double c = ox * ox + oz * oz - k_cone * (oy - localHeight) * (oy - localHeight);
      double discriminant = b * b - 4.0 * a * c;

      if (discriminant >= 0) {
        double sqrtD = Math.sqrt(discriminant);
        double t0 = (-b - sqrtD) / (2.0 * a);
        double t1 = (-b + sqrtD) / (2.0 * a);
        if (t0 > t1) {
          double tempT = t0;
          t0 = t1;
          t1 = tempT;
        }

        // First lateral root
        if (t0 > Ray.EPSILON) {
          double ly = oy + dy * t0;
          if (ly >= -Ray.EPSILON && ly <= localHeight + Ray.EPSILON) {
            tValues[hitCount] = t0;
            hitCount++;
          }
        }
        // Second lateral root
        if (t1 > Ray.EPSILON) {
          double ly = oy + dy * t1;
          if (ly >= -Ray.EPSILON && ly <= localHeight + Ray.EPSILON) {
            tValues[hitCount] = t1;
            hitCount++;
          }
        }
      }
    }

    // ---- Base cap ----
    baseNormal.set(0, -1, 0);
    double denom = dy * baseNormal.y;
    if (Math.abs(denom) > Ray.EPSILON) {
      // Plane: y = 0 (local base center at origin)
      double t = (0.0 - oy) / dy;
      if (t > Ray.EPSILON) {
        double lx = ox + dx * t;
        double lz = oz + dz * t;
        if (lx * lx + lz * lz <= localRadius * localRadius + Ray.EPSILON) {
          tValues[hitCount] = t;
          hitCount++;
        }
      }
    }

    // Need at least two real crossings to form an inside interval.
    if (hitCount < 2) {
      return EMPTY_INTERVAL_LIST;
    }

    // Sort the (max 3) t values ascending.
    for (int i = 0; i < hitCount - 1; i++) {
      for (int j = 0; j < hitCount - 1 - i; j++) {
        if (tValues[j] > tValues[j + 1]) {
          double tempT = tValues[j];
          tValues[j] = tValues[j + 1];
          tValues[j + 1] = tempT;
        }
      }
    }

    double tEnter = tValues[0];
    double tExit = tValues[hitCount - 1];

    // Compute world-space entry point + normal (t is valid in the original ray parametrization).
    ray.pointAtParameter(tEnter, tempWorldHit);
    inHit.set(tempWorldHit, getNormalAt(tempWorldHit), tEnter, this);

    // Compute world-space exit point + normal.
    ray.pointAtParameter(tExit, tempWorldHit);
    outHit.set(tempWorldHit, getNormalAt(tempWorldHit), tExit, this);

    singleInterval.set(tEnter, tExit, inHit, outHit);
    return java.util.Collections.singletonList(singleInterval);
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
    return ("Cone: " + getTransform().toString());
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

    // Lateral surface
    for (int i = 0; i < segments; i++) {
      double theta1 = 2 * Math.PI * i / segments;
      double theta2 = 2 * Math.PI * (i + 1) / segments;

      double x1 = localRadius * Math.cos(theta1);
      double z1 = localRadius * Math.sin(theta1);
      double x2 = localRadius * Math.cos(theta2);
      double z2 = localRadius * Math.sin(theta2);

      Point3 v1 = new Point3(x1, 0, z1);
      Point3 v2 = new Point3(x2, 0, z2);
      Point3 apex = new Point3(0, localHeight, 0);

      triangles.add(new Triangle(v1, v2, apex));
    }

    // Base cap
    Point3 centerBottom = new Point3(0, 0, 0);
    for (int i = 0; i < segments; i++) {
      double theta1 = 2 * Math.PI * i / segments;
      double theta2 = 2 * Math.PI * (i + 1) / segments;

      Point3 v1 = new Point3(localRadius * Math.cos(theta1), 0, localRadius * Math.sin(theta1));
      Point3 v2 = new Point3(localRadius * Math.cos(theta2), 0, localRadius * Math.sin(theta2));

      triangles.add(new Triangle(centerBottom, v1, v2));
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

  /**
   * Generates an OBJ format string representation of this Cone. This method is called only during
   * export, not during render. Uses robust vertex deduplication with tolerance to avoid degenerate
   * triangles.
   *
   * @return String in OBJ format
   */
  /**
   * Generates an OBJ format string representation of this Cone. This method is called only during
   * export, not during render. Uses robust vertex deduplication with tolerance to avoid degenerate
   * triangles.
   *
   * @return String in OBJ format
   */
  /**
   * Generates an OBJ format string representation of this Cone. Cone has a special mesh structure:
   * all side triangles share the apex vertex, and base triangles share the center vertex. Uses
   * larger tolerance for proper vertex deduplication.
   *
   * @return String in OBJ format
   */
  /**
   * Generates an OBJ format string representation of this Cone. Transform is applied directly to
   * vertices, not written as OBJ transform.
   *
   * @return String in OBJ format
   */
  @Override
  public String toObjString() {
    StringBuilder sb = new StringBuilder();

    sb.append("# Murat Ray Tracer Mesh File\n");
    sb.append("# Shape: Cone\n");
    sb.append("# Radius: ").append(localRadius).append("\n");
    sb.append("# Height: ").append(localHeight).append("\n");
    sb.append("# Generated: ").append(new java.util.Date()).append("\n");

    // SADECE material bilgisi yaz, transform YAZMA!
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

    // Vertex'leri topla ve transform'u uygula
    List<Point3> allVerts = new ArrayList<Point3>();
    for (Triangle tri : triangles) {
      allVerts.add(trans.transformPoint(tri.getV0()));
      allVerts.add(trans.transformPoint(tri.getV1()));
      allVerts.add(trans.transformPoint(tri.getV2()));
    }

    // Deduplicate with tolerance
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
    sb.append("Cone " + name + " {\n");
    sb.append("    name = " + name + ";\n");
    sb.append("    radius = " + localRadius + ";\n");
    sb.append("    height = " + localHeight + ";\n");
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
    sb.append("    material = " + getMaterial().toString() + ";\n}");
    return sb.toString();
  }
}
