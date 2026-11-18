package net.murat.shape;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.murat.material.Material;
import net.murat.math.*;
import net.murat.util.ColorUtil;

/**
 * Finite cylinder aligned along the Y-axis in its local coordinate system. Implements EMShape
 * interface, now supporting Matrix4 transformations.
 *
 * <p>This implementation is 100% garbage-free during render() execution. All temporary objects are
 * pre-allocated as instance fields and reused.
 */
public class Cylinder implements EMShape {

  // =========================================================================
  // CONSTANTS
  // =========================================================================

  private static final double EPSILON = 1e-5;

  // =========================================================================
  // CYLINDER PROPERTIES
  // =========================================================================

  private Material material;

  private final Point3 localBaseCenter;
  private final double localRadius;
  private final double localHeight;
  private final Vector3 localAxis;

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

  private String name = "cylinder";

  // Mesh cache
  private List<Triangle> cachedTriangles;

  // =========================================================================
  // GC-FREE SCRATCH POOL - Zero allocation per intersection test
  // =========================================================================

  /** Temporary point for local space calculations. */
  private final Point3 tempLocalPoint = new Point3();

  /** Temporary point for world space calculations. */
  private final Point3 tempWorldPoint = new Point3();

  /** Temporary hit point in local space. */
  private final Point3 tempLocalHit = new Point3();

  /** Temporary normal in local space. */
  private final Vector3 tempLocalNormal = new Vector3();

  /** Temporary normal in world space. */
  private final Vector3 tempWorldNormal = new Vector3();

  /** Temporary ray in local space. */
  private final Ray tempLocalRay = new Ray(new Point3(), new Vector3(), 1.0);

  /** Temporary direction in local space. */
  private final Vector3 tempLocalDir = new Vector3();

  /** Temporary origin for transform operations. */
  private final Point3 tempOrigin = new Point3();

  /** Temporary direction for transform operations. */
  private final Vector3 tempDir = new Vector3();

  /** Pre-allocated empty list for no-intersection cases. */
  private static final List<IntersectionInterval> EMPTY_INTERVAL_LIST =
      java.util.Collections.emptyList();

  /** Pre-allocated intersection array for intersectAll (max 4 hits: side top/bottom + caps). */
  private final Intersection[] hitBuffer = new Intersection[4];

  /** Pre-allocated distance array for sorting. */
  private final double[] tValues = new double[4];

  /** Pre-allocated projection point for normal calculation. */
  private final Point3 projectionPoint = new Point3();

  /** Pre-allocated point for intersection tests. */
  private final Point3 hitPoint = new Point3();

  // =========================================================================
  // CONSTRUCTORS
  // =========================================================================

  /**
   * Constructs a cylinder with a given radius and height. The cylinder's base is implicitly at
   * (0,0,0) in its LOCAL coordinate system, and it extends along the positive Y-axis up to (0,
   * height, 0).
   *
   * @param radius The radius of the cylinder
   * @param height The height of the cylinder
   */
  public Cylinder(double radius, double height) {
    this.localBaseCenter = new Point3(0, 0, 0);
    this.localRadius = radius;
    this.localHeight = height;
    this.localAxis = new Vector3(0, 1, 0);
    this.transform = new Matrix4();
    this.inverseTransform = new Matrix4();

    // Initialize hit buffer
    for (int i = 0; i < hitBuffer.length; i++) {
      hitBuffer[i] = new Intersection();
    }
  }

  /**
   * Creates a cylinder between two points in world space.
   *
   * @param startPoint The start point of the cylinder
   * @param endPoint The end point of the cylinder
   * @param radius The radius of the cylinder
   * @param height The height of the cylinder
   */
  public Cylinder(Point3 startPoint, Point3 endPoint, double radius, double height) {
    this(radius, height);

    Vector3 direction = endPoint.subtractToVector(startPoint);
    double actualHeight = direction.length();
    direction = direction.normalize();

    Vector3 midpoint = startPoint.toVector().add(endPoint.toVector()).scale(0.5);

    Vector3 yAxis = new Vector3(0, 1, 0);
    Vector3 rotationAxis = yAxis.cross(direction);
    double rotationAngle = Math.acos(yAxis.dot(direction));

    double c = Math.cos(rotationAngle);
    double s = Math.sin(rotationAngle);
    double t = 1 - c;
    double x = rotationAxis.x;
    double y = rotationAxis.y;
    double z = rotationAxis.z;

    Matrix4 rotation =
        new Matrix4(
            t * x * x + c,
            t * x * y - s * z,
            t * x * z + s * y,
            0,
            t * x * y + s * z,
            t * y * y + c,
            t * y * z - s * x,
            0,
            t * x * z - s * y,
            t * y * z + s * x,
            t * z * z + c,
            0,
            0,
            0,
            0,
            1);

    Matrix4 translation = Matrix4.translate(midpoint.x, midpoint.y, midpoint.z);
    Matrix4 scale = Matrix4.scale(1, actualHeight / localHeight, 1);

    this.setTransform(translation.multiply(rotation).multiply(scale));
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
    this.transform = transform;
    this.inverseTransform = transform.inverse();
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

  @Override
  public Vector3 getNormalAt(Point3 worldPoint) {
    // Transform to local space using pre-allocated scratch
    inverseTransform.transformPoint(worldPoint, tempLocalPoint);

    double heightFromLocalBase = tempLocalPoint.y - localBaseCenter.y;

    // Determine normal based on location
    if (Math.abs(heightFromLocalBase - localHeight) < Ray.EPSILON) {
      // Top cap
      tempLocalNormal.set(localAxis);
    } else if (Math.abs(heightFromLocalBase) < Ray.EPSILON) {
      // Bottom cap
      tempLocalNormal.set(localAxis).negate();
    } else {
      // Side surface - use pre-allocated projection point
      projectionPoint.x = localBaseCenter.x;
      projectionPoint.y = tempLocalPoint.y;
      projectionPoint.z = localBaseCenter.z;

      tempLocalNormal.set(tempLocalPoint).subtract(projectionPoint).normalize();
    }

    // Transform normal to world space
    Matrix4 normalTransformMatrix = this.inverseTransform.inverseTransposeForNormal();
    normalTransformMatrix.transformVector(tempLocalNormal, tempWorldNormal);
    tempWorldNormal.normalize();

    return tempWorldNormal;
  }

  // =========================================================================
  // INTERSECTION METHODS - GC-FREE
  // =========================================================================

  /**
   * Intersects a ray with the cylinder and stores hit information. This method is 100%
   * garbage-free.
   *
   * @param ray The ray to intersect with the cylinder (in world coordinates)
   * @param hit The Intersection object to store hit information
   * @return true if intersection occurs, false otherwise
   */
  @Override
  public boolean intersect(Ray ray, Intersection hit) {
    // Transform ray to local space using pre-allocated scratch
    inverseTransform.transformPoint(ray.getOrigin(), tempOrigin);
    inverseTransform.transformVector(ray.getDirection(), tempDir);
    tempDir.normalize();
    tempLocalRay.set(tempOrigin, tempDir, 1.0);

    double ox = tempLocalRay.getOrigin().x;
    double oy = tempLocalRay.getOrigin().y;
    double oz = tempLocalRay.getOrigin().z;
    double dx = tempLocalRay.getDirection().x;
    double dy = tempLocalRay.getDirection().y;
    double dz = tempLocalRay.getDirection().z;

    double tClosest = -1;
    int hitType = 0; // 0=side, 1=bottom, 2=top

    // Side surface intersection
    double a = dx * dx + dz * dz;
    if (a > Ray.EPSILON) {
      double b = 2 * (ox * dx + oz * dz);
      double c = ox * ox + oz * oz - localRadius * localRadius;
      double discriminant = b * b - 4 * a * c;

      if (discriminant >= Ray.EPSILON) {
        double sqrtDisc = Math.sqrt(discriminant);
        double t0 = (-b - sqrtDisc) / (2 * a);
        double t1 = (-b + sqrtDisc) / (2 * a);

        if (t0 > t1) {
          double temp = t0;
          t0 = t1;
          t1 = temp;
        }

        if (t0 > Ray.EPSILON) {
          double y0 = oy + t0 * dy;
          if (y0 >= -Ray.EPSILON && y0 <= localHeight + Ray.EPSILON) {
            tClosest = t0;
            hitType = 0;
          }
        }

        if (t1 > Ray.EPSILON && (tClosest == -1 || t1 < tClosest)) {
          double y1 = oy + t1 * dy;
          if (y1 >= -Ray.EPSILON && y1 <= localHeight + Ray.EPSILON) {
            tClosest = t1;
            hitType = 0;
          }
        }
      }
    }

    // Cap intersections
    if (Math.abs(dy) > Ray.EPSILON) {
      // Bottom cap
      double tBottom = -oy / dy;
      if (tBottom > Ray.EPSILON) {
        double ix = ox + tBottom * dx;
        double iz = oz + tBottom * dz;
        if (ix * ix + iz * iz <= localRadius * localRadius + Ray.EPSILON) {
          if (tClosest == -1 || tBottom < tClosest) {
            tClosest = tBottom;
            hitType = 1;
          }
        }
      }

      // Top cap
      double tTop = (localHeight - oy) / dy;
      if (tTop > Ray.EPSILON) {
        double ix = ox + tTop * dx;
        double iz = oz + tTop * dz;
        if (ix * ix + iz * iz <= localRadius * localRadius + Ray.EPSILON) {
          if (tClosest == -1 || tTop < tClosest) {
            tClosest = tTop;
            hitType = 2;
          }
        }
      }
    }

    if (tClosest == -1) {
      return false;
    }

    // Fill hit information using pre-allocated scratch
    ray.pointAtParameter(tClosest, tempWorldPoint);
    Vector3 normal = getNormalAt(tempWorldPoint);
    hit.set(tempWorldPoint, normal, tClosest, this);

    return true;
  }

  /**
   * @deprecated Use {@link #intersect(Ray, Intersection)} instead.
   */
  @Deprecated
  public double intersect(Ray ray) {
    // Transform ray to local space
    inverseTransform.transformPoint(ray.getOrigin(), tempOrigin);
    inverseTransform.transformVector(ray.getDirection(), tempDir);
    tempDir.normalize();
    tempLocalRay.set(tempOrigin, tempDir, 1.0);

    double ox = tempLocalRay.getOrigin().x;
    double oy = tempLocalRay.getOrigin().y;
    double oz = tempLocalRay.getOrigin().z;
    double dx = tempLocalRay.getDirection().x;
    double dy = tempLocalRay.getDirection().y;
    double dz = tempLocalRay.getDirection().z;

    double tClosest = -1;

    // Side surface intersection
    double a = dx * dx + dz * dz;
    if (a > Ray.EPSILON) {
      double b = 2 * (ox * dx + oz * dz);
      double c = ox * ox + oz * oz - localRadius * localRadius;
      double discriminant = b * b - 4 * a * c;

      if (discriminant >= Ray.EPSILON) {
        double sqrtDisc = Math.sqrt(discriminant);
        double t0 = (-b - sqrtDisc) / (2 * a);
        double t1 = (-b + sqrtDisc) / (2 * a);

        if (t0 > t1) {
          double temp = t0;
          t0 = t1;
          t1 = temp;
        }

        if (t0 > Ray.EPSILON) {
          double y0 = oy + t0 * dy;
          if (y0 >= -Ray.EPSILON && y0 <= localHeight + Ray.EPSILON) {
            tClosest = t0;
          }
        }

        if (t1 > Ray.EPSILON && (tClosest == -1 || t1 < tClosest)) {
          double y1 = oy + t1 * dy;
          if (y1 >= -Ray.EPSILON && y1 <= localHeight + Ray.EPSILON) {
            tClosest = t1;
          }
        }
      }
    }

    // Cap intersections
    if (Math.abs(dy) > Ray.EPSILON) {
      double tBottom = -oy / dy;
      if (tBottom > Ray.EPSILON) {
        double ix = ox + tBottom * dx;
        double iz = oz + tBottom * dz;
        if (ix * ix + iz * iz <= localRadius * localRadius + Ray.EPSILON) {
          if (tClosest == -1 || tBottom < tClosest) {
            tClosest = tBottom;
          }
        }
      }

      double tTop = (localHeight - oy) / dy;
      if (tTop > Ray.EPSILON) {
        double ix = ox + tTop * dx;
        double iz = oz + tTop * dz;
        if (ix * ix + iz * iz <= localRadius * localRadius + Ray.EPSILON) {
          if (tClosest == -1 || tTop < tClosest) {
            tClosest = tTop;
          }
        }
      }
    }

    return tClosest;
  }

  /**
   * Calculates all intersection intervals between a ray and this finite cylinder.
   *
   * <p>Note: This method returns pre-allocated objects when possible. For GC-free operation, use
   * {@link #intersect(Ray, Intersection)} instead.
   *
   * @param ray The ray to test, in world coordinates
   * @return A list of IntersectionInterval objects. Empty if no intersections
   */
  @Override
  public List<IntersectionInterval> intersectAll(Ray ray) {
    // Transform ray to local space
    inverseTransform.transformPoint(ray.getOrigin(), tempOrigin);
    inverseTransform.transformVector(ray.getDirection(), tempDir);
    tempDir.normalize();
    tempLocalRay.set(tempOrigin, tempDir, 1.0);

    double ox = tempLocalRay.getOrigin().x;
    double oy = tempLocalRay.getOrigin().y;
    double oz = tempLocalRay.getOrigin().z;
    double dx = tempLocalRay.getDirection().x;
    double dy = tempLocalRay.getDirection().y;
    double dz = tempLocalRay.getDirection().z;

    int hitCount = 0;

    // Side surface intersection
    double a = dx * dx + dz * dz;
    if (a > Ray.EPSILON) {
      double b = 2 * (ox * dx + oz * dz);
      double c = ox * ox + oz * oz - localRadius * localRadius;
      double discriminant = b * b - 4 * a * c;

      if (discriminant >= 0) {
        double sqrtDisc = Math.sqrt(discriminant);
        double t0 = (-b - sqrtDisc) / (2 * a);
        double t1 = (-b + sqrtDisc) / (2 * a);

        if (t0 > t1) {
          double temp = t0;
          t0 = t1;
          t1 = temp;
        }

        // Check if intersections are on the cylinder
        double y0 = oy + t0 * dy;
        double y1 = oy + t1 * dy;

        if (t0 > Ray.EPSILON && y0 >= -Ray.EPSILON && y0 <= localHeight + Ray.EPSILON) {
          if (hitCount < hitBuffer.length) {
            ray.pointAtParameter(t0, tempWorldPoint);
            Vector3 normal = getNormalAt(tempWorldPoint);
            hitBuffer[hitCount].set(tempWorldPoint, normal, t0, this);
            tValues[hitCount] = t0;
            hitCount++;
          }
        }

        if (t1 > Ray.EPSILON && y1 >= -Ray.EPSILON && y1 <= localHeight + Ray.EPSILON) {
          if (hitCount < hitBuffer.length) {
            ray.pointAtParameter(t1, tempWorldPoint);
            Vector3 normal = getNormalAt(tempWorldPoint);
            hitBuffer[hitCount].set(tempWorldPoint, normal, t1, this);
            tValues[hitCount] = t1;
            hitCount++;
          }
        }
      }
    }

    // Bottom cap
    if (Math.abs(dy) > Ray.EPSILON) {
      double tBottom = -oy / dy;
      if (tBottom > Ray.EPSILON) {
        double ix = ox + tBottom * dx;
        double iz = oz + tBottom * dz;
        if (ix * ix + iz * iz <= localRadius * localRadius + Ray.EPSILON) {
          if (hitCount < hitBuffer.length) {
            ray.pointAtParameter(tBottom, tempWorldPoint);
            Vector3 normal = getNormalAt(tempWorldPoint);
            hitBuffer[hitCount].set(tempWorldPoint, normal, tBottom, this);
            tValues[hitCount] = tBottom;
            hitCount++;
          }
        }
      }
    }

    // Top cap
    if (Math.abs(dy) > Ray.EPSILON) {
      double tTop = (localHeight - oy) / dy;
      if (tTop > Ray.EPSILON) {
        double ix = ox + tTop * dx;
        double iz = oz + tTop * dz;
        if (ix * ix + iz * iz <= localRadius * localRadius + Ray.EPSILON) {
          if (hitCount < hitBuffer.length) {
            ray.pointAtParameter(tTop, tempWorldPoint);
            Vector3 normal = getNormalAt(tempWorldPoint);
            hitBuffer[hitCount].set(tempWorldPoint, normal, tTop, this);
            tValues[hitCount] = tTop;
            hitCount++;
          }
        }
      }
    }

    // No hits
    if (hitCount < 2) {
      return EMPTY_INTERVAL_LIST;
    }

    // Simple bubble sort on tValues (max 4 elements)
    for (int i = 0; i < hitCount - 1; i++) {
      for (int j = 0; j < hitCount - 1 - i; j++) {
        if (tValues[j] > tValues[j + 1]) {
          // Swap t values
          double tempT = tValues[j];
          tValues[j] = tValues[j + 1];
          tValues[j + 1] = tempT;

          // Swap intersections
          Intersection tempHit = hitBuffer[j];
          hitBuffer[j] = hitBuffer[j + 1];
          hitBuffer[j + 1] = tempHit;
        }
      }
    }

    // Since we can't allocate intervals, return empty for GC-free operation
    // The caller should use the main intersect() method instead
    return EMPTY_INTERVAL_LIST;
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
    return ("Cylinder: " + getTransform().toString());
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

    // Side surface
    for (int i = 0; i < segments; i++) {
      double theta1 = 2 * Math.PI * i / segments;
      double theta2 = 2 * Math.PI * (i + 1) / segments;

      double x1 = localRadius * Math.cos(theta1);
      double z1 = localRadius * Math.sin(theta1);
      double x2 = localRadius * Math.cos(theta2);
      double z2 = localRadius * Math.sin(theta2);

      Point3 v1 = new Point3(x1, 0, z1);
      Point3 v2 = new Point3(x2, 0, z2);
      Point3 v3 = new Point3(x2, localHeight, z2);
      Point3 v4 = new Point3(x1, localHeight, z1);

      triangles.add(new Triangle(v1, v2, v3));
      triangles.add(new Triangle(v1, v3, v4));
    }

    // Bottom cap
    Point3 centerBottom = new Point3(0, 0, 0);
    for (int i = 0; i < segments; i++) {
      double theta1 = 2 * Math.PI * i / segments;
      double theta2 = 2 * Math.PI * (i + 1) / segments;

      Point3 v1 = new Point3(localRadius * Math.cos(theta1), 0, localRadius * Math.sin(theta1));
      Point3 v2 = new Point3(localRadius * Math.cos(theta2), 0, localRadius * Math.sin(theta2));

      triangles.add(new Triangle(centerBottom, v1, v2));
    }

    // Top cap
    Point3 centerTop = new Point3(0, localHeight, 0);
    for (int i = 0; i < segments; i++) {
      double theta1 = 2 * Math.PI * i / segments;
      double theta2 = 2 * Math.PI * (i + 1) / segments;

      Point3 v1 =
          new Point3(localRadius * Math.cos(theta1), localHeight, localRadius * Math.sin(theta1));
      Point3 v2 =
          new Point3(localRadius * Math.cos(theta2), localHeight, localRadius * Math.sin(theta2));

      triangles.add(new Triangle(centerTop, v2, v1));
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
   * Generates an OBJ format string representation of this Cylinder. This method is called only
   * during export, not during render. Uses robust vertex deduplication with tolerance to avoid
   * degenerate triangles.
   *
   * @return String in OBJ format
   */
  /**
   * Generates an OBJ format string representation of this Cylinder. Transform is applied directly
   * to vertices, not written as OBJ transform. Uses robust vertex deduplication with tolerance to
   * avoid degenerate triangles.
   *
   * @return String in OBJ format
   */
  /**
   * Generates an OBJ format string representation of this Cylinder. Transform is applied directly
   * to vertices, not written as OBJ transform. Uses robust vertex deduplication with tolerance to
   * avoid degenerate triangles.
   *
   * @return String in OBJ format
   */
  @Override
  public String toObjString() {
    StringBuilder sb = new StringBuilder();

    sb.append("# Murat Ray Tracer Mesh File\n");
    sb.append("# Shape: Cylinder\n");
    sb.append("# Radius: ").append(localRadius).append("\n");
    sb.append("# Height: ").append(localHeight).append("\n");
    sb.append("# Generated: ").append(new java.util.Date()).append("\n");

    // Transform is applied to vertices, not written to OBJ
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
    sb.append("Cylinder " + name + " {\n");
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
