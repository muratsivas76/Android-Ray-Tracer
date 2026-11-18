package net.murat.material;

import net.murat.light.*;
import net.murat.math.*;
import net.murat.util.ColorUtil;

/**
 * HamamSaunaMaterial - Cultural fusion of Turkish Hamam and Norwegian Sauna. Zero-allocation using
 * packed ARGB integers.
 */
public class HamamSaunaMaterial implements Material {
  private final int marbleColorPacked;
  private final int woodColorPacked;
  private final int steamColorPacked;
  private final double steamIntensity;

  private Matrix4 objectTransform;
  private Matrix4 inverseTransform;
  private Matrix4 inverseTransposeTransform;

  // Pre-cached color components
  private final int marbleR, marbleG, marbleB;
  private final int woodR, woodG, woodB;
  private final int steamR, steamG, steamB;

  private final double ambientCoeff = 0.5;
  private final double diffuseCoeff = 0.8;
  private final double specularCoeff = 0.25;
  private final double shininess = 40.0;
  private final double reflectivity = 0.15;
  private final double ior = 1.55;
  private final double transparency = 0.12;

  private static final int SPECULAR_HIGHLIGHT = 0xFFFFFAF0;

  // ===== GC DOSTU: PRE-ALLOCATED TEMP OBJECTS =====
  private final Point3 tempLocalPoint = new Point3();
  private final Vector3 tempLocalNormal = new Vector3();
  private final Vector3 tempTransformedNormal = new Vector3();
  private final Vector3 tempViewDir = new Vector3();
  private final Vector3 tempLightDir = new Vector3();
  private final Vector3 tempReflectDir = new Vector3();
  private final Point3 tempViewerPoint = new Point3();
  private final LightProperties tempLightProps = new LightProperties();

  public HamamSaunaMaterial() {
    this(0xFFE6E6FA, 0xFF8B4513, 0xFFF5F5F5, 0.55);
  }

  public HamamSaunaMaterial(int marbleColor, int woodColor, int steamColor, double steamIntensity) {
    this.marbleColorPacked = marbleColor;
    this.woodColorPacked = woodColor;
    this.steamColorPacked = steamColor;
    this.steamIntensity = Math.max(0, Math.min(1, steamIntensity));

    this.marbleR = (marbleColor >> 16) & 0xFF;
    this.marbleG = (marbleColor >> 8) & 0xFF;
    this.marbleB = marbleColor & 0xFF;

    this.woodR = (woodColor >> 16) & 0xFF;
    this.woodG = (woodColor >> 8) & 0xFF;
    this.woodB = woodColor & 0xFF;

    this.steamR = (steamColor >> 16) & 0xFF;
    this.steamG = (steamColor >> 8) & 0xFF;
    this.steamB = steamColor & 0xFF;

    this.objectTransform = Matrix4.identity();
    this.inverseTransform = Matrix4.identity();
    this.inverseTransposeTransform = Matrix4.identity();
  }

  @Override
  public void setObjectTransform(Matrix4 tm) {
    if (tm == null) tm = new Matrix4();
    this.objectTransform = tm;
    this.inverseTransform = tm.inverse();
    this.inverseTransposeTransform = this.inverseTransform.inverseTransposeForNormal();
  }

  @Override
  public int getColorAt(Point3 worldPoint, Vector3 worldNormal, Light light, Point3 viewerPos) {
    // 1. Transform to local space - ZERO ALLOCATION
    inverseTransform.transformPoint(worldPoint, tempLocalPoint);

    // 2. Transform normal - ZERO ALLOCATION
    inverseTransposeTransform.transformDirection(worldNormal, tempTransformedNormal);
    tempLocalNormal.set(tempTransformedNormal);
    tempLocalNormal.normalize();

    // 3. Calculate pattern
    int surfaceColor = calculateHamamSaunaPattern(tempLocalPoint, tempLocalNormal, viewerPos);

    // 4. Light properties - ZERO ALLOCATION
    LightProperties.getLightProperties(light, worldPoint, tempLightProps);
    tempLightDir.set(tempLightProps.direction);

    // 5. Ambient check
    int ambient = ColorUtil.multiplyColors(surfaceColor, tempLightProps.color, ambientCoeff);

    if (light instanceof MuratAmbientLight) {
      return ambient;
    }

    // 6. Diffuse
    double NdotL = Math.max(0, tempLocalNormal.dot(tempLightDir));
    int diffuse =
        ColorUtil.multiplyColors(
            surfaceColor, tempLightProps.color, diffuseCoeff * NdotL * tempLightProps.intensity);

    // 7. View direction - ZERO ALLOCATION
    tempViewerPoint.set(viewerPos);
    tempViewDir.set(tempViewerPoint);
    tempViewDir.subtract(worldPoint);
    tempViewDir.normalize();

    // 8. Reflection direction - ZERO ALLOCATION
    tempReflectDir.set(tempLightDir);
    tempReflectDir.negate();
    tempReflectDir.reflect(tempLocalNormal, tempReflectDir);

    // 9. Specular
    double RdotV = Math.max(0, tempReflectDir.dot(tempViewDir));
    double specFactor = Math.pow(RdotV, shininess) * tempLightProps.intensity;
    int specular =
        ColorUtil.multiplyColors(
            SPECULAR_HIGHLIGHT, tempLightProps.color, specularCoeff * specFactor);

    // 10. Combine
    return ColorUtil.combineColors(ambient, diffuse, specular);
  }

  private int calculateHamamSaunaPattern(Point3 point, Vector3 normal, Point3 viewerPos) {
    double x = point.x * 8.0;
    double y = point.y * 8.0;
    double z = point.z * 8.0;

    // Marble veining
    double marbleVein1 = Math.sin(x * 2.5 + Math.cos(y * 1.8) * 3.0);
    double marbleVein2 = Math.abs(Math.cos(x * 3.0 + y * 2.2) + Math.sin(y * 2.5 + z * 1.5));
    double marbleVein3 = Math.sin(x * 4.0 + y * 3.0) * Math.cos(y * 2.0 + z * 1.8);

    // Wood grain
    double woodGrain1 = Math.sin(x * 1.2 + Math.sin(y * 0.5) * 2.0);
    double woodGrain2 = (Math.floor(x * 0.6) + Math.floor(y * 0.6)) % 1.8;
    double woodGrain3 = Math.abs(Math.sin(x * 2.8 + y * 1.4 + z * 1.0));

    // Steam effect
    double steamEffect = Math.sin(x * 0.7 + y * 0.9 + z * 1.2) * Math.cos(y * 1.1 + z * 0.8);

    double marbleWeight = 0.6;
    double woodWeight = 0.4;

    double materialPattern =
        (marbleVein1 * 0.3 + marbleVein2 * 0.2 + marbleVein3 * 0.2) * marbleWeight
            + (woodGrain1 * 0.25 + woodGrain2 * 0.15 + woodGrain3 * 0.1) * woodWeight;

    double normalizedPattern = (materialPattern + 1.0) * 0.5;
    double steamNormalized = (steamEffect + 1.0) * 0.5;

    // View-dependent effects - ZERO ALLOCATION
    tempViewerPoint.set(viewerPos);
    tempViewDir.set(tempViewerPoint);
    tempViewDir.subtract(point);
    tempViewDir.normalize();

    double viewEffect = Math.pow(Math.abs(tempViewDir.dot(normal)), 0.6);

    // Base material selection - inline color ops
    int baseR, baseG, baseB;

    if (normalizedPattern < 0.4) {
      double veinIntensity = normalizedPattern / 0.4;
      int darkR = (int) (marbleR * 0.85);
      int darkG = (int) (marbleG * 0.85);
      int darkB = (int) (marbleB * 0.85);
      baseR = (int) (marbleR + (darkR - marbleR) * veinIntensity);
      baseG = (int) (marbleG + (darkG - marbleG) * veinIntensity);
      baseB = (int) (marbleB + (darkB - marbleB) * veinIntensity);
    } else if (normalizedPattern < 0.7) {
      double woodIntensity = (normalizedPattern - 0.4) / 0.3;
      int lightR = (int) (woodR + (255 - woodR) * 0.25);
      int lightG = (int) (woodG + (255 - woodG) * 0.25);
      int lightB = (int) (woodB + (255 - woodB) * 0.25);
      baseR = (int) (woodR + (lightR - woodR) * woodIntensity);
      baseG = (int) (woodG + (lightG - woodG) * woodIntensity);
      baseB = (int) (woodB + (lightB - woodB) * woodIntensity);
    } else {
      double blendIntensity = (normalizedPattern - 0.7) / 0.3;
      baseR = (int) (marbleR + (woodR - marbleR) * blendIntensity);
      baseG = (int) (marbleG + (woodG - marbleG) * blendIntensity);
      baseB = (int) (marbleB + (woodB - marbleB) * blendIntensity);
    }

    baseR = baseR > 255 ? 255 : (baseR < 0 ? 0 : baseR);
    baseG = baseG > 255 ? 255 : (baseG < 0 ? 0 : baseG);
    baseB = baseB > 255 ? 255 : (baseB < 0 ? 0 : baseB);
    int baseColor = (0xFF000000 | (baseR << 16) | (baseG << 8) | baseB);

    // Apply steam effect
    double steamThreshold = 0.7 - steamIntensity * 0.3;
    if (steamNormalized > steamThreshold) {
      double steamAlpha = (steamNormalized - steamThreshold) / (steamIntensity * 0.3 + 0.3);
      steamAlpha = Math.min(1.0, steamAlpha * 1.5) * viewEffect;
      return ColorUtil.blendColors(baseColor, steamColorPacked, (float) steamAlpha);
    }

    return baseColor;
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

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("HamamSaunaMaterial hamamsaunamaterial {\n");
    sb.append("        marbleColor = ")
        .append(ColorUtil.toColorString(marbleColorPacked))
        .append(";\n");
    sb.append("        woodColor = ")
        .append(ColorUtil.toColorString(woodColorPacked))
        .append(";\n");
    sb.append("        steamColor = ")
        .append(ColorUtil.toColorString(steamColorPacked))
        .append(";\n");
    sb.append("        steamIntensity = ").append(steamIntensity).append(";\n");
    sb.append("    }");
    return sb.toString();
  }
}
