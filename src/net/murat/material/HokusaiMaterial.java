package net.murat.material;

import net.murat.light.Light;
import net.murat.light.LightProperties;
import net.murat.light.MuratAmbientLight;
import net.murat.math.*;
import net.murat.util.ColorUtil;

/** HokusaiMaterial - Procedural wave rendering with zero allocation. Using packed ARGB integers. */
public class HokusaiMaterial implements Material {
  public static final int PLANE_MODE = 0;
  public static final int SPHERE_MODE = 1;

  private final int waterColorPacked;
  private final int foamColorPacked;
  private final double scale;
  private final int mode;
  private final double radius;
  private final double reflectivity;
  private final double ambientCoefficient;

  private Matrix4 objectInverseTransform;
  private Matrix4 inverseTransposeTransform;

  private final double diffuseCoefficient = 0.8;
  private final double specularCoefficient = 0.9;
  private final double shininess = 300.0;
  private final double ior = 1.33;
  private final double transparency = 0.3;

  // Pre-cached color components
  private final int waterR, waterG, waterB;
  private final int foamR, foamG, foamB;

  // Fast performance constants
  private static final double INV_TWO_PI = 1.0 / (2.0 * Math.PI);
  private static final double INV_PI = 1.0 / Math.PI;

  // ===== GC-FRIENDLY: PRE-ALLOCATED TEMP OBJECTS =====
  private final Point3 tempLocalPoint = new Point3();
  private final Vector3 tempLocalNormal = new Vector3();
  private final Vector3 tempTransformedNormal = new Vector3();
  private final Vector3 tempViewDir = new Vector3();
  private final Vector3 tempLightDir = new Vector3();
  private final Vector3 tempReflectDir = new Vector3();
  private final LightProperties tempLightProps = new LightProperties();

  public HokusaiMaterial() {
    this(0xFF1A3380, 0xFFFFFFFF, 4.0, PLANE_MODE, 1.0, 0.6, 0.2, new Matrix4());
  }

  public HokusaiMaterial(
      int waterColor,
      int foamColor,
      double scale,
      int mode,
      double radius,
      Matrix4 objectInverseTransform) {
    this(waterColor, foamColor, scale, mode, radius, 0.6, 0.2, objectInverseTransform);
  }

  public HokusaiMaterial(
      int waterColor,
      int foamColor,
      double scale,
      int mode,
      double radius,
      double reflectivity,
      double ambientCoefficient,
      Matrix4 objectInverseTransform) {
    this.waterColorPacked = waterColor;
    this.foamColorPacked = foamColor;
    this.scale = Math.max(0.1, scale);
    this.mode = (mode == SPHERE_MODE) ? SPHERE_MODE : PLANE_MODE;
    this.radius = Math.max(0.1, radius);
    this.reflectivity = Math.max(0.0, Math.min(1.0, reflectivity));
    this.ambientCoefficient = Math.max(0.0, Math.min(1.0, ambientCoefficient));
    this.objectInverseTransform =
        objectInverseTransform != null ? objectInverseTransform : new Matrix4();
    this.inverseTransposeTransform = this.objectInverseTransform.inverseTransposeForNormal();

    this.waterR = (waterColor >> 16) & 0xFF;
    this.waterG = (waterColor >> 8) & 0xFF;
    this.waterB = waterColor & 0xFF;

    this.foamR = (foamColor >> 16) & 0xFF;
    this.foamG = (foamColor >> 8) & 0xFF;
    this.foamB = foamColor & 0xFF;
  }

  @Override
  public void setObjectTransform(Matrix4 tm) {
    if (tm == null) tm = new Matrix4();
    this.objectInverseTransform = tm;
    this.inverseTransposeTransform = tm.inverseTransposeForNormal();
  }

  @Override
  public int getColorAt(Point3 worldPoint, Vector3 worldNormal, Light light, Point3 viewerPos) {
    if (objectInverseTransform == null) {
      return 0xFF000000;
    }

    // 1. Transform to local space - ZERO ALLOCATION
    objectInverseTransform.transformPoint(worldPoint, tempLocalPoint);

    // 2. Transform normal
    inverseTransposeTransform.transformDirection(worldNormal, tempTransformedNormal);
    tempLocalNormal.set(tempTransformedNormal);
    tempLocalNormal.normalize();

    double u, v;

    if (mode == SPHERE_MODE) {
      // SPHERE MODE - Original
      u = tempLocalPoint.x * scale;
      v = tempLocalPoint.y * scale;

      u = u - Math.floor(u);
      v = v - Math.floor(v);
    } else {
      // PLANE MODE - Fixed for planes
      // Use x,y coordinates for plane UV mapping
      u = tempLocalPoint.x * scale;
      v = tempLocalPoint.z * scale; // FIX: Use z instead of y for plane

      u = u - Math.floor(u);
      v = v - Math.floor(v);
    }

    // Generate wave pattern
    int waveColor = createHokusaiWave(u, v);

    // LIGHTING
    LightProperties.getLightProperties(light, worldPoint, tempLightProps);
    tempLightDir.set(tempLightProps.direction);

    // Ambient light
    if (light instanceof MuratAmbientLight) {
      int r = (int) (((waveColor >> 16) & 0xFF) * ambientCoefficient * tempLightProps.intensity);
      int g = (int) (((waveColor >> 8) & 0xFF) * ambientCoefficient * tempLightProps.intensity);
      int b = (int) ((waveColor & 0xFF) * ambientCoefficient * tempLightProps.intensity);
      r = Math.max(0, Math.min(255, r));
      g = Math.max(0, Math.min(255, g));
      b = Math.max(0, Math.min(255, b));
      return (0xFF000000 | (r << 16) | (g << 8) | b);
    }

    // Diffuse
    double NdotL = Math.max(0, tempLocalNormal.dot(tempLightDir));
    int diffuse =
        ColorUtil.multiplyColors(
            waveColor, tempLightProps.color, diffuseCoefficient * NdotL * tempLightProps.intensity);

    // View direction
    double vx = viewerPos.x - worldPoint.x;
    double vy = viewerPos.y - worldPoint.y;
    double vz = viewerPos.z - worldPoint.z;
    double vLenInv = 1.0 / Math.sqrt(vx * vx + vy * vy + vz * vz);
    double viewX = vx * vLenInv;
    double viewY = vy * vLenInv;
    double viewZ = vz * vLenInv;

    // Half vector
    double hx = tempLightDir.x + viewX;
    double hy = tempLightDir.y + viewY;
    double hz = tempLightDir.z + viewZ;
    double hLenInv = 1.0 / Math.sqrt(hx * hx + hy * hy + hz * hz);
    double halfX = hx * hLenInv;
    double halfY = hy * hLenInv;
    double halfZ = hz * hLenInv;

    double NdotH =
        Math.max(
            0, tempLocalNormal.x * halfX + tempLocalNormal.y * halfY + tempLocalNormal.z * halfZ);
    double specIntensity = Math.pow(NdotH, shininess);

    int specular =
        ColorUtil.multiplyColors(
            ColorUtil.WHITE,
            tempLightProps.color,
            specularCoefficient * specIntensity * tempLightProps.intensity);

    int ambient =
        ColorUtil.multiplyColors(
            waveColor, tempLightProps.color, ambientCoefficient * tempLightProps.intensity);

    int result = ColorUtil.add(ambient, diffuse);
    result = ColorUtil.add(result, specular);

    return ColorUtil.clampColor(result);
  }

  private int createHokusaiWave(double u, double v) {
    double waveX = u * 8.0;
    double waveY = v * 2.0;

    double baseWave = Math.sin(waveX - waveY * 0.5) * 0.5 + 0.5;

    double foam = 0.0;
    if (baseWave > 0.85) {
      foam = (baseWave - 0.85) * 6.0;
      foam = Math.min(1.0, foam);
    }

    // FIX: Use symmetric depth fade for sphere mode
    double depthFade;
    if (mode == SPHERE_MODE) {
      // Sphere: symmetric - bright at both poles and equator
      double vCentered = Math.abs(v - 0.5) * 2.0; // 0 at equator, 1 at poles
      depthFade = 0.5 + 0.5 * (1.0 - vCentered * 0.5);
    } else {
      // Plane: original exponential fade
      depthFade = Math.exp(-v * 3.0);
    }

    int r = (int) (waterR * depthFade);
    int g = (int) (waterG * depthFade);
    int b = (int) (waterB * depthFade);
    r = Math.max(0, Math.min(255, r));
    g = Math.max(0, Math.min(255, g));
    b = Math.max(0, Math.min(255, b));
    int water = (0xFF000000 | (r << 16) | (g << 8) | b);

    return ColorUtil.blendColors(water, foamColorPacked, (float) foam);
  }

  @Override
  public double getReflectivity() {
    return reflectivity;
  }

  @Override
  public double getIndexOfRefraction() {
    return ior;
  }

  @Override
  public double getTransparency() {
    return transparency;
  }

  public int getWaterColorPacked() {
    return waterColorPacked;
  }

  public int getFoamColorPacked() {
    return foamColorPacked;
  }

  public double getScale() {
    return scale;
  }

  public int getMode() {
    return mode;
  }

  public double getRadius() {
    return radius;
  }

  public double getAmbientCoefficient() {
    return ambientCoefficient;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("HokusaiMaterial hokusaimaterial {\n");
    sb.append("        waterColor = ")
        .append(ColorUtil.toColorString(waterColorPacked))
        .append(";\n");
    sb.append("        foamColor = ")
        .append(ColorUtil.toColorString(foamColorPacked))
        .append(";\n");
    sb.append("        scale = ").append(scale).append(";\n");
    sb.append("        mode = ").append(mode).append("; // 0 planes and 1 spheres\n");
    sb.append("        radius = ").append(radius).append(";\n");
    sb.append("        reflectivity = ").append(reflectivity).append(";\n");
    sb.append("        ambientCoefficient = ").append(ambientCoefficient).append(";\n");
    sb.append("    }");
    return sb.toString();
  }
}
