package net.murat.math;

/**
 * License GPLv3
 *
 * <p>Global scratchpad for zero-allocation mathematics in the ray tracing loop.
 *
 * <p>This class provides pre-allocated, mutable vector and point instances to prevent massive heap
 * allocation and GC overhead during the single-threaded rendering process.
 *
 * @author Murat
 */
public final class MathContext {

  // Suppress default constructor for non-instantiability
  private MathContext() {}

  // =========================================================================
  // RAY & INTERSECTION SCRATCHPAD (Primary Rendering Loop)
  // =========================================================================

  /**
   * Temporary vector used to compute or store the current ray's direction. NOTE: Keeping it as a
   * Vector3 instance to preserve your original architecture.
   */
  public static final Vector3 RAY_DIR = new Vector3();

  /** Stores the computed intersection point where a ray hits a surface (P = O + tD). */
  public static final Point3 HIT_POINT = new Point3();

  /** Stores the surface normal vector at the exact point of intersection. */
  public static final Vector3 SURFACE_NORMAL = new Vector3();

  // =========================================================================
  // SHADING, LIGHTING & MATERIAL SCRATCHPAD
  // =========================================================================

  /** Direction vector pointing from the hit point towards the light source. */
  public static final Vector3 LIGHT_DIR = new Vector3();

  /** Direction vector of the reflected ray, used for specular and mirror reflections. */
  public static final Vector3 REFLECT_DIR = new Vector3();

  /** Direction vector of the refracted ray, used for transparent/glass materials. */
  public static final Vector3 REFRACT_DIR = new Vector3();

  // =========================================================================
  // GENERAL PURPOSE MATH SCRATCHPAD (Reusable Temporary Variables)
  // =========================================================================

  /** General-purpose mutable vector for intermediate arithmetic equations. */
  public static final Vector3 V_TEMP1 = new Vector3();

  /** General-purpose mutable vector for secondary or nested intermediate math operations. */
  public static final Vector3 V_TEMP2 = new Vector3();

  /** General-purpose mutable point for intermediate spatial coordinate equations. */
  public static final Point3 P_TEMP1 = new Point3();

  /** General-purpose mutable point for secondary or nested coordinate operations. */
  public static final Point3 P_TEMP2 = new Point3();

  // =========================================================================
  // AUTOMATIC EXTENSIONS (Added to safely compile high-performance light utilities)
  // =========================================================================

  public static final Vector3 V_TEMP3 = new Vector3();
  public static final Vector3 V_TEMP4 = new Vector3();
  public static final Intersection TEMP_HIT = new Intersection();

  public static final Ray SHADOW_TEST_RAY = new Ray();

  /** The actual pre-allocated mutable Ray instance required for allocation-free shadow testing. */
  public static final Ray SHADOW_RAY = new Ray();
}
