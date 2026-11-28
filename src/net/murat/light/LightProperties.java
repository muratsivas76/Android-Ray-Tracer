package net.murat.light;

import net.murat.math.Point3;
import net.murat.math.Vector3;
import net.murat.util.ColorUtil;

/**
 * Holds calculated light properties for shading. ZERO-ALLOCATION: Uses pre-allocated temp objects.
 * Compatible with Java 6-8 without lambdas.
 *
 * <p>THREAD-SAFETY FIX: the original version used `static final` scratch fields (TEMP_DIR,
 * TEMP_POS, TEMP_RESULT) shared by ALL callers across ALL threads. If the ray tracer renders with
 * more than one thread (e.g. tile-based or row-based parallel rendering), two threads calling
 * getLightProperties(...) concurrently would stomp on each other's scratch data, producing
 * flickering / randomly wrong lighting that is very hard to reproduce or debug.
 *
 * <p>Fix: scratch vectors are now per-thread via ThreadLocal instead of per-class. This keeps the
 * same "zero steady-state allocation" property (each thread allocates its own scratch Vector3
 * exactly ONCE, on first use, then reuses it forever) while making concurrent calls from different
 * threads safe. Single-threaded callers see no behavioral or performance difference at all; only
 * the multi-threaded case changes (from "silently broken" to "correct").
 *
 * <p>TEMP_PROPS (a shared LightProperties instance) was removed because it was declared but never
 * actually used anywhere in this class - dead field, not a behavioral change.
 */
public class LightProperties {

  // ===== PER-THREAD PRE-ALLOCATED TEMP OBJECTS =====
  // Each thread gets its own Vector3, allocated lazily on first access
  // and reused for the lifetime of that thread. This preserves the
  // "GC-friendly, no per-call allocation" intent of the original static
  // fields, but without the cross-thread data race.
  private static final ThreadLocal<Vector3> TEMP_DIR =
      new ThreadLocal<Vector3>() {
        @Override
        protected Vector3 initialValue() {
          return new Vector3();
        }
      };

  private static final ThreadLocal<Vector3> TEMP_POS =
      new ThreadLocal<Vector3>() {
        @Override
        protected Vector3 initialValue() {
          return new Vector3();
        }
      };

  private static final ThreadLocal<Vector3> TEMP_RESULT =
      new ThreadLocal<Vector3>() {
        @Override
        protected Vector3 initialValue() {
          return new Vector3();
        }
      };

  // ===== FIELDS =====
  public final Vector3 direction;
  public int color;
  public double intensity;

  public LightProperties() {
    this.direction = new Vector3();
    this.color = 0xFFFFFFFF;
    this.intensity = 1.0;
  }

  public LightProperties(Vector3 direction, int color, double intensity) {
    this.direction = direction;
    this.color = color;
    this.intensity = intensity;
  }

  /** ZERO-ALLOCATION: Fills target LightProperties with ambient data. */
  public static void fillAmbient(LightProperties target, int color, double intensity) {
    target.direction.set(0, 0, 0);
    target.color = color;
    target.intensity = intensity;
  }

  /** ZERO-ALLOCATION: Fills target LightProperties with directional light data. */
  public static void fillDirectional(
      LightProperties target, Vector3 direction, int color, double intensity) {
    target.direction.set(direction);
    target.direction.negate();
    target.direction.normalize();
    target.color = color;
    target.intensity = intensity;
  }

  /** ZERO-ALLOCATION: Fills target LightProperties with point light data. */
  public static void fillPointLight(
      LightProperties target, Vector3 lightPos, Point3 surfacePoint, int color, double intensity) {
    target.direction.set(lightPos);
    target.direction.subtract(surfacePoint);
    target.direction.normalize();
    target.color = color;
    target.intensity = intensity;
  }

  /** ZERO-ALLOCATION: Fills target LightProperties with null/black data. */
  public static void fillNull(LightProperties target) {
    target.direction.set(0, 1, 0);
    target.color = 0xFF000000;
    target.intensity = 0.0;
  }

  /**
   * ZERO-ALLOCATION: Gets light properties into the provided target. Thread-safe: TEMP_DIR/TEMP_POS
   * below are now per-thread (see class javadoc), so concurrent calls from different rendering
   * threads no longer corrupt each other's scratch data.
   *
   * @param light The light source
   * @param point The surface point
   * @param target The LightProperties instance to fill (MUST NOT BE NULL)
   */
  public static final void getLightProperties(Light light, Point3 point, LightProperties target) {
    if (light == null) {
      fillNull(target);
      return;
    }

    // Ambient: special case, no direction.
    if (light instanceof MuratAmbientLight) {
      fillAmbient(target, light.getColor(), light.getIntensity());
      return;
    }

    // Projector: special case, uses texture color.
    if (light instanceof ProjectorLight) {
      ProjectorLight proj = (ProjectorLight) light;
      target.color = proj.getColorAt(point);
      target.intensity = proj.getAttenuatedIntensity(point);

      Vector3 tempPos = TEMP_POS.get();
      tempPos.set(proj.getPosition());
      target.direction.set(tempPos);
      target.direction.subtract(point);
      target.direction.normalize();
      return;
    }

    try {
      if (light instanceof MuratPointLight) {
        Vector3 tempPos = TEMP_POS.get();
        tempPos.set(((MuratPointLight) light).getPosition());
        fillPointLight(target, tempPos, point, light.getColor(), light.getAttenuatedIntensity(point));
        return;
      }

      if (light instanceof MuratDirectionalLight) {
        Vector3 tempDir = TEMP_DIR.get();
        tempDir.set(((MuratDirectionalLight) light).getDirection());
        fillDirectional(target, tempDir, light.getColor(), light.getIntensity());
        return;
      }

      // Generic fallback for all other light types:
      // rely on each light's own getDirectionAt() and getAttenuatedIntensity().
      Vector3 dir = light.getDirectionAt(point);
      if (dir != null && dir.lengthSquared() > 0) {
        target.direction.set(dir);
      } else {
        target.direction.set(0, 1, 0);
      }
      target.color = light.getColor();
      target.intensity = light.getAttenuatedIntensity(point);
      return;
    } catch (Throwable e) {
      fillNull(target);
    }
  }

  /** ZERO-ALLOCATION: Sets this instance's values from another. */
  public void set(LightProperties other) {
    this.direction.set(other.direction);
    this.color = other.color;
    this.intensity = other.intensity;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("LightProperties lightproperties {\n");
    sb.append("    direction = ").append(direction).append(";\n");
    sb.append("    color = ").append(ColorUtil.toColorString(color)).append(";\n");
    sb.append("    intensity = ").append(intensity).append(";\n}");
    return sb.toString();
  }
}
