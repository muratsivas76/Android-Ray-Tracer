package net.murat.util;

import java.util.Random;
import net.murat.math.MathContext;
import net.murat.math.Point3;
import net.murat.math.Vector3;

/**
 * Mathematical helper functions for 3D graphics and ray tracing. All methods are thread-safe and
 * deterministic. FULLY GC-FRIENDLY - uses MathContext scratch variables.
 */
public final class MathUtil {

  // Mathematical constants
  public static final double PI = Math.PI;
  public static final double TWO_PI = 2.0 * PI;
  public static final double INV_PI = 1.0 / PI;
  public static final double EPSILON = 1e-8;
  public static final double GOLDEN_RATIO = 1.618033988749895;

  private static final Random RAND = new Random();
  private static final int[] PRIMES = {2, 3, 5, 7, 11, 13, 17, 19, 23, 29};

  private MathUtil() {} // Prevent instantiation

  // =========================================================================
  // BASIC MATH FUNCTIONS
  // =========================================================================

  public static double clamp(double value, double min, double max) {
    return Math.max(min, Math.min(max, value));
  }

  public static double radians(double degrees) {
    return degrees * PI / 180.0;
  }

  public static double degrees(double radians) {
    return radians * 180.0 / PI;
  }

  public static double lerp(double a, double b, double t) {
    return a + t * (b - a);
  }

  // =========================================================================
  // RANDOM NUMBER GENERATION
  // =========================================================================

  public static double random(double seed) {
    double x = Math.sin(seed * 12.9898 + 78.233) * 43758.5453;
    return x - Math.floor(x);
  }

  public static Vector3 randomInUnitSphere(double seed, Vector3 target) {
    double phi = TWO_PI * random(seed + PRIMES[0]);
    double costheta = 2.0 * random(seed + PRIMES[1]) - 1.0;
    double theta = Math.acos(costheta);
    double r = Math.cbrt(random(seed + PRIMES[2]));

    double rx = r * Math.sin(theta) * Math.cos(phi);
    double ry = r * Math.sin(theta) * Math.sin(phi);
    double rz = r * Math.cos(theta);

    return target.set(rx, ry, rz);
  }

  // =========================================================================
  // RAY-PLANE INTERSECTION (GC-FRIENDLY)
  // =========================================================================

  /**
   * Calculates ray-plane intersection. Uses MathContext scratch variables - NO ALLOCATION.
   *
   * @return distance along ray, or Double.POSITIVE_INFINITY if no intersection
   */
  public static double rayPlaneIntersect(
      Point3 rayOrigin, Vector3 rayDir, Point3 planePoint, Vector3 planeNormal) {

    double denom = planeNormal.dot(rayDir);
    if (Math.abs(denom) > EPSILON) {
      // diff = planePoint - rayOrigin
      MathContext.V_TEMP1.set(planePoint);
      MathContext.V_TEMP1.x -= rayOrigin.x;
      MathContext.V_TEMP1.y -= rayOrigin.y;
      MathContext.V_TEMP1.z -= rayOrigin.z;
      return MathContext.V_TEMP1.dot(planeNormal) / denom;
    }
    return Double.POSITIVE_INFINITY;
  }

  // =========================================================================
  // RAY-TRIANGLE INTERSECTION (GC-FRIENDLY)
  // =========================================================================

  /**
   * Ray-triangle intersection (Möller-Trumbore algorithm). Uses MathContext scratch variables - NO
   * ALLOCATION.
   *
   * @return distance along ray, or -1.0 if no intersection
   */
  public static double rayTriangleIntersect(
      Point3 rayOrigin, Vector3 rayDir, Point3 v0, Point3 v1, Point3 v2) {

    // edge1 = v1 - v0
    MathContext.V_TEMP1.set(v1);
    MathContext.V_TEMP1.x -= v0.x;
    MathContext.V_TEMP1.y -= v0.y;
    MathContext.V_TEMP1.z -= v0.z;

    // edge2 = v2 - v0
    MathContext.V_TEMP2.set(v2);
    MathContext.V_TEMP2.x -= v0.x;
    MathContext.V_TEMP2.y -= v0.y;
    MathContext.V_TEMP2.z -= v0.z;

    // h = rayDir x edge2
    MathContext.V_TEMP3.set(
        rayDir.y * MathContext.V_TEMP2.z - rayDir.z * MathContext.V_TEMP2.y,
        rayDir.z * MathContext.V_TEMP2.x - rayDir.x * MathContext.V_TEMP2.z,
        rayDir.x * MathContext.V_TEMP2.y - rayDir.y * MathContext.V_TEMP2.x);

    double a = MathContext.V_TEMP1.dot(MathContext.V_TEMP3);

    if (a > -EPSILON && a < EPSILON) {
      return -1.0;
    }

    double f = 1.0 / a;

    // s = rayOrigin - v0
    MathContext.V_TEMP4.set(rayOrigin);
    MathContext.V_TEMP4.x -= v0.x;
    MathContext.V_TEMP4.y -= v0.y;
    MathContext.V_TEMP4.z -= v0.z;

    double u = f * MathContext.V_TEMP4.dot(MathContext.V_TEMP3);

    if (u < 0.0 || u > 1.0) {
      return -1.0;
    }

    // q = s x edge1
    MathContext.V_TEMP3.set(
        MathContext.V_TEMP4.y * MathContext.V_TEMP1.z
            - MathContext.V_TEMP4.z * MathContext.V_TEMP1.y,
        MathContext.V_TEMP4.z * MathContext.V_TEMP1.x
            - MathContext.V_TEMP4.x * MathContext.V_TEMP1.z,
        MathContext.V_TEMP4.x * MathContext.V_TEMP1.y
            - MathContext.V_TEMP4.y * MathContext.V_TEMP1.x);

    double v = f * rayDir.dot(MathContext.V_TEMP3);

    if (v < 0.0 || u + v > 1.0) {
      return -1.0;
    }

    double t = f * MathContext.V_TEMP2.dot(MathContext.V_TEMP3);
    return t > EPSILON ? t : -1.0;
  }

  // =========================================================================
  // NOISE FUNCTIONS
  // =========================================================================

  public static int noiseHash(int x, int y, int z) {
    final int X_NOISE = 1619;
    final int Y_NOISE = 31337;
    final int Z_NOISE = 6971;
    final int SEED = 1013;

    int hash = (x * X_NOISE) ^ (y * Y_NOISE) ^ (z * Z_NOISE);
    hash = hash * hash * hash * SEED;
    return (hash >> 13) ^ hash;
  }

  public static double perlinNoise(double x, double y, double z) {
    int xi = (int) Math.floor(x) & 255;
    int yi = (int) Math.floor(y) & 255;
    int zi = (int) Math.floor(z) & 255;
    return random(xi + yi * 256 + zi * 65536) * 2.0 - 1.0;
  }

  // =========================================================================
  // PBR LIGHTING FUNCTIONS
  // =========================================================================

  public static double nearZero(double value) {
    return Math.abs(value) < EPSILON ? 0.0 : value;
  }

  public static double fresnelSchlick(double cosTheta, double refIdx) {
    double r0 = (1.0 - refIdx) / (1.0 + refIdx);
    r0 = r0 * r0;
    return r0 + (1.0 - r0) * Math.pow(1.0 - cosTheta, 5.0);
  }

  public static double ggxDistribution(double NdotH, double roughness) {
    double a = roughness * roughness;
    double a2 = a * a;
    double denom = (NdotH * NdotH * (a2 - 1.0) + 1.0);
    return a2 / (PI * denom * denom);
  }

  public static double smithG1(double NdotV, double roughness) {
    double k = (roughness + 1.0) * (roughness + 1.0) / 8.0;
    return NdotV / (NdotV * (1.0 - k) + k);
  }

  // =========================================================================
  // COORDINATE TRANSFORMATIONS (GC-FRIENDLY)
  // =========================================================================

  public static Vector3 sphericalToCartesian(double r, double theta, double phi, Vector3 target) {
    double sinTheta = Math.sin(theta);
    double cx = r * sinTheta * Math.cos(phi);
    double cy = r * sinTheta * Math.sin(phi);
    double cz = r * Math.cos(theta);
    return target.set(cx, cy, cz);
  }

  public static void cartesianToSpherical(Vector3 v, double[] targetArray) {
    double r = v.length();
    targetArray[0] = r;
    targetArray[1] = Math.acos(v.z / r);
    targetArray[2] = Math.atan2(v.y, v.x);
  }
}
