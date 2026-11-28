package net.murat.material;

import net.murat.light.*;
import net.murat.math.*;
import net.murat.util.ColorUtil;

/**
 * TelemarkPatternMaterial - Traditional Telemark geometric patterns. Zero-allocation using packed
 * ARGB integers.
 */
public class TelemarkPatternMaterial implements Material {
  private final int baseColorPacked;
  private final int patternColorPacked;
  private final int accentColorPacked;
  private final double patternScale;
  private Matrix4 objectTransform;
  private Matrix4 inverseTransform;
  private Matrix4 inverseTransposeTransform;

  // Pre-cached color components
  private final int baseR, baseG, baseB;
  private final int patternR, patternG, patternB;
  private final int accentR, accentG, accentB;

  private final double ambientCoeff = 0.45;
  private final double diffuseCoeff = 0.8;
  private final double specularCoeff = 0.12;
  private final double shininess = 18.0;
  private final double reflectivity = 0.07;
  private final double ior = 1.6;
  private final double transparency = 0.0;
  private final int specularColorPacked = 0xFFFFFFFF;

  // ===== GC DOSTU: PRE-ALLOCATED TEMP OBJECTS =====
  private final Point3 tempLocalPoint = new Point3();
  private final Vector3 tempLocalNormal = new Vector3();
  private final Vector3 tempTransformedNormal = new Vector3();
  private final Vector3 tempViewDir = new Vector3();
  private final Vector3 tempLightDir = new Vector3();
  private final Vector3 tempReflectDir = new Vector3();
  private final Point3 tempViewerPoint = new Point3();
  private final LightProperties tempLightProps = new LightProperties();

  public TelemarkPatternMaterial() {
    this(0xFF8B0000, 0xFFFFD700, 0xFF006400, 5.0);
  }

  public TelemarkPatternMaterial(
      int baseColor, int patternColor, int accentColor, double patternScale) {
    this.baseColorPacked = baseColor;
    this.patternColorPacked = patternColor;
    this.accentColorPacked = accentColor;
    this.patternScale = patternScale;

    this.baseR = (baseColor >> 16) & 0xFF;
    this.baseG = (baseColor >> 8) & 0xFF;
    this.baseB = baseColor & 0xFF;

    this.patternR = (patternColor >> 16) & 0xFF;
    this.patternG = (patternColor >> 8) & 0xFF;
    this.patternB = patternColor & 0xFF;

    this.accentR = (accentColor >> 16) & 0xFF;
    this.accentG = (accentColor >> 8) & 0xFF;
    this.accentB = accentColor & 0xFF;

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
    if (inverseTransform == null) {
      return 0xFF000000;
    }

    // 1. Transform to local space
    inverseTransform.transformPoint(worldPoint, tempLocalPoint);

    // 2. Transform normal
    inverseTransposeTransform.transformDirection(worldNormal, tempTransformedNormal);
    tempLocalNormal.set(tempTransformedNormal);
    tempLocalNormal.normalize();

    // 3. Calculate Telemark pattern
    int surfaceColor = calculateTelemarkPattern(tempLocalPoint);
    int surfR = (surfaceColor >> 16) & 0xFF;
    int surfG = (surfaceColor >> 8) & 0xFF;
    int surfB = surfaceColor & 0xFF;

    // 4. Light properties - ZERO ALLOCATION
    LightProperties.getLightProperties(light, worldPoint, tempLightProps);
    tempLightDir.set(tempLightProps.direction);

    int lightColor = tempLightProps.color;
    int lightR = (lightColor >> 16) & 0xFF;
    int lightG = (lightColor >> 8) & 0xFF;
    int lightB = lightColor & 0xFF;
    double intensity = tempLightProps.intensity;

    // 5. Ambient check
    int ambient = ColorUtil.multiplyColors(surfaceColor, tempLightProps.color, ambientCoeff);

    if (light instanceof MuratAmbientLight) {
      return ambient;
    }

    // 6. View direction - ZERO ALLOCATION
    tempViewerPoint.set(viewerPos);
    tempViewDir.set(tempViewerPoint);
    tempViewDir.subtract(worldPoint);
    tempViewDir.normalize();

    // 7. Diffuse
    double NdotL = Math.max(0.0, tempLocalNormal.dot(tempLightDir));
    double diffFactor = diffuseCoeff * NdotL * intensity;
    int diffR = (int) (surfR * diffFactor * lightR / 255.0);
    int diffG = (int) (surfG * diffFactor * lightG / 255.0);
    int diffB = (int) (surfB * diffFactor * lightB / 255.0);

    // 8. Reflection direction - ZERO ALLOCATION
    tempReflectDir.set(tempLightDir);
    tempReflectDir.negate();
    tempReflectDir.reflect(tempLocalNormal, tempReflectDir);

    // 9. Specular
    double RdotV = Math.max(0.0, tempReflectDir.dot(tempViewDir));
    double specFactor = Math.pow(RdotV, shininess) * specularCoeff * intensity;

    int specR = (int) (((specularColorPacked >> 16) & 0xFF) * specFactor * lightR / 255.0);
    int specG = (int) (((specularColorPacked >> 8) & 0xFF) * specFactor * lightG / 255.0);
    int specB = (int) ((specularColorPacked & 0xFF) * specFactor * lightB / 255.0);

    specR = clamp(specR);
    specG = clamp(specG);
    specB = clamp(specB);

    // 10. Combine
    int r = ((ambient >> 16) & 0xFF) + diffR + specR;
    int g = ((ambient >> 8) & 0xFF) + diffG + specG;
    int b = (ambient & 0xFF) + diffB + specB;

    r = clamp(r);
    g = clamp(g);
    b = clamp(b);

    return (0xFF000000 | (r << 16) | (g << 8) | b);
  }

  private int calculateTelemarkPattern(Point3 point) {
    double x = point.x * patternScale;
    double y = point.y * patternScale;
    double z = point.z * patternScale;

    double diamond1 = Math.abs(Math.sin(x * 2.0) + Math.cos(y * 2.0));
    double diamond2 = Math.abs(Math.sin(x * 3.0 + y * 1.5) + Math.cos(y * 2.0 + x * 1.2));
    double cross = (Math.floor(x * 1.5) + Math.floor(y * 1.5)) % 3.0;
    double border = Math.abs(Math.sin(x * 4.0) * Math.cos(y * 4.0));

    double combinedPattern = (diamond1 * 0.3 + diamond2 * 0.25 + cross * 0.25 + border * 0.2);
    double normalizedPattern = combinedPattern % 1.0;
    if (normalizedPattern < 0) normalizedPattern += 1.0;

    int r, g, b;
    if (normalizedPattern < 0.3) {
      r = baseR;
      g = baseG;
      b = baseB;
    } else if (normalizedPattern < 0.6) {
      r = patternR;
      g = patternG;
      b = patternB;
    } else if (normalizedPattern < 0.8) {
      r = accentR;
      g = accentG;
      b = accentB;
    } else {
      double darkFactor = 0.6;
      r = (int) (patternR * darkFactor);
      g = (int) (patternG * darkFactor);
      b = (int) (patternB * darkFactor);
    }

    r = clamp(r);
    g = clamp(g);
    b = clamp(b);

    return (0xFF000000 | (r << 16) | (g << 8) | b);
  }

  private int clamp(int value) {
    return Math.max(0, Math.min(255, value));
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
    sb.append("TelemarkPatternMaterial telemarkpatternmaterial {\n");
    sb.append("        baseColor = ")
        .append(ColorUtil.toColorString(baseColorPacked))
        .append(";\n");
    sb.append("        patternColor = ")
        .append(ColorUtil.toColorString(patternColorPacked))
        .append(";\n");
    sb.append("        accentColor = ")
        .append(ColorUtil.toColorString(accentColorPacked))
        .append(";\n");
    sb.append("        patternScale = ").append(patternScale).append(";\n");
    sb.append("    }");
    return sb.toString();
  }
}
