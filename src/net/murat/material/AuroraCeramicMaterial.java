package net.murat.material;

import net.murat.light.*;
import net.murat.math.*;
import net.murat.util.ColorUtil;

/**
 * AuroraCeramicMaterial - Ceramic material with aurora-like flowing patterns. Fully garbage-free
 * using 32-bit packed ARGB integers. Compatible with Java 6-8 without lambdas.
 */
public class AuroraCeramicMaterial implements Material {
  private int baseColor;
  private int auroraColor;
  private double auroraIntensity;
  private Matrix4 objectTransform;
  private Matrix4 inverseTransform;
  private Matrix4 inverseTransposeTransform;

  // Cached primitive color components
  private int baseR, baseG, baseB;
  private int auroraR, auroraG, auroraB;

  private final double ambientCoeff = 0.5;
  private final double diffuseCoeff = 0.7;
  private final double specularCoeff = 0.35;
  private final double shininess = 55.0;
  private final double reflectivity = 0.22;
  private final double ior = 1.7;
  private final double transparency = 0.0;

  // ===== GC DOSTU: PRE-ALLOCATED TEMP OBJECTS =====
  private final Point3 tempLocalPoint = new Point3();
  private final Vector3 tempLocalNormal = new Vector3();
  private final Vector3 tempTransformedNormal = new Vector3();
  private final Point3 tempViewerPoint = new Point3();
  private final Vector3 tempViewDir = new Vector3();
  private final Vector3 tempReflectDir = new Vector3();
  private final Vector3 tempLightDir = new Vector3();
  private final Vector3 tempNormal = new Vector3();
  private final LightProperties tempLightProps = new LightProperties();

  public AuroraCeramicMaterial() {
    this(0xFFF5F5DC, 0xFF00FF7F, 0.45);
  }

  public AuroraCeramicMaterial(int baseColor, int auroraColor, double auroraIntensity) {
    this.baseColor = baseColor;
    this.auroraColor = auroraColor;
    this.auroraIntensity = Math.max(0.0, Math.min(1.0, auroraIntensity));

    this.baseR = (baseColor >> 16) & 0xFF;
    this.baseG = (baseColor >> 8) & 0xFF;
    this.baseB = baseColor & 0xFF;

    this.auroraR = (auroraColor >> 16) & 0xFF;
    this.auroraG = (auroraColor >> 8) & 0xFF;
    this.auroraB = auroraColor & 0xFF;

    this.objectTransform = new Matrix4();
    this.inverseTransform = new Matrix4();
    this.inverseTransposeTransform = new Matrix4();
  }

  @Override
  public void setObjectTransform(Matrix4 tm) {
    if (tm == null) {
      tm = new Matrix4();
    }
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

    // 3. Calculate aurora effect
    int surfaceColor = calculateAuroraEffect(tempLocalPoint, tempLocalNormal, viewerPos);

    // 4. Get light properties - ZERO ALLOCATION (FIXED!)
    LightProperties.getLightProperties(light, worldPoint, tempLightProps);

    // 5. Ambient
    int ambient = ColorUtil.multiply(surfaceColor, (float) ambientCoeff);
    ambient = ColorUtil.multiplyColors(ambient, tempLightProps.color, 1.0);

    if (light instanceof MuratAmbientLight) {
      return ambient;
    }

    // 6. Light direction
    tempLightDir.set(tempLightProps.direction);

    // 7. Diffuse
    double NdotL = Math.max(0.0, tempLocalNormal.dot(tempLightDir));
    double diffuseFactor = diffuseCoeff * NdotL * tempLightProps.intensity;
    int diffuse = ColorUtil.multiply(surfaceColor, (float) diffuseFactor);
    diffuse = ColorUtil.multiplyColors(diffuse, tempLightProps.color, 1.0);

    // 8. View direction - ZERO ALLOCATION
    tempViewerPoint.set(viewerPos);
    tempViewDir.set(tempViewerPoint);
    tempViewDir.subtract(worldPoint);
    tempViewDir.normalize();

    // 9. Reflection direction - ZERO ALLOCATION
    tempReflectDir.set(tempLightDir);
    tempReflectDir.negate();
    tempReflectDir.reflect(tempLocalNormal, tempReflectDir);

    // 10. Specular
    double RdotV = Math.max(0.0, tempReflectDir.dot(tempViewDir));
    double specFactor = Math.pow(RdotV, shininess) * tempLightProps.intensity;
    int specular = ColorUtil.multiply(0xFFFFFFFF, (float) (specularCoeff * specFactor));
    specular = ColorUtil.multiplyColors(specular, tempLightProps.color, 1.0);

    // 11. Combine
    int result = ColorUtil.add(ambient, diffuse);
    result = ColorUtil.add(result, specular);

    return ColorUtil.clamp(result);
  }

  private int calculateAuroraEffect(Point3 point, Vector3 normal, Point3 viewerPos) {
    double x = point.x * 7.0;
    double y = point.y * 7.0;
    double z = point.z * 7.0;

    double flow1 = Math.sin(x * 1.2 + Math.cos(y * 0.8) + Math.sin(z * 1.5));
    double flow2 = Math.cos(x * 0.7 + Math.sin(y * 1.3) + Math.cos(z * 0.9));
    double flow3 = Math.sin(x * 2.1 + y * 1.7 + z * 0.5);

    double auroraPattern = (flow1 * 0.4 + flow2 * 0.3 + flow3 * 0.3);
    double normalizedPattern = (auroraPattern + 1.0) * 0.5;

    // View-dependent effect - ZERO ALLOCATION
    tempViewerPoint.set(viewerPos);
    tempViewDir.set(tempViewerPoint);
    tempViewDir.subtract(point);
    tempViewDir.normalize();

    double viewAngle = Math.max(0.0, tempViewDir.dot(normal));
    double viewEffect = Math.pow(viewAngle, 0.7);

    int r, g, b;

    if (normalizedPattern < auroraIntensity) {
      double intensity = (normalizedPattern / auroraIntensity) * viewEffect;

      r = (int) (baseR * (1.0 - intensity) + auroraR * intensity);
      g = (int) (baseG * (1.0 - intensity) + auroraG * intensity);
      b = (int) (baseB * (1.0 - intensity) + auroraB * intensity);

      double glowBoost = intensity * 0.5;
      r = Math.min(255, r + (int) (auroraR * glowBoost));
      g = Math.min(255, g + (int) (auroraG * glowBoost));
      b = Math.min(255, b + (int) (auroraB * glowBoost));
    } else {
      double intensity =
          ((normalizedPattern - auroraIntensity) / (1.0 - auroraIntensity)) * 0.3 * viewEffect;

      r = (int) (baseR + auroraR * intensity * 0.3);
      g = (int) (baseG + auroraG * intensity * 0.4);
      b = (int) (baseB + auroraB * intensity * 0.2);
    }

    r = Math.min(255, Math.max(0, r));
    g = Math.min(255, Math.max(0, g));
    b = Math.min(255, Math.max(0, b));

    return (0xFF000000 | (r << 16) | (g << 8) | b);
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

  public int getBaseColor() {
    return baseColor;
  }

  public int getAuroraColor() {
    return auroraColor;
  }

  public double getAuroraIntensity() {
    return auroraIntensity;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("AuroraCeramicMaterial auroraceramicmaterial {\n");
    sb.append("        baseColor = ").append(ColorUtil.toColorString(baseColor)).append(";\n");
    sb.append("        auroraColor = ").append(ColorUtil.toColorString(auroraColor)).append(";\n");
    sb.append("        auroraIntensity = ").append(auroraIntensity).append(";\n");
    sb.append("    }");
    return sb.toString();
  }
}
