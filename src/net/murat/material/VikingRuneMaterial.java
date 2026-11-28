package net.murat.material;

import net.murat.light.*;
import net.murat.math.*;
import net.murat.util.ColorUtil;

/**
 * VikingRuneMaterial - Stone surface with Viking rune carvings. Zero-allocation using packed ARGB
 * integers.
 */
public class VikingRuneMaterial implements Material {
  private final int stoneColorPacked;
  private final int runeColorPacked;
  private final double runeDepth;
  private Matrix4 objectTransform;
  private Matrix4 inverseTransform;
  private Matrix4 inverseTransposeTransform;

  // Pre-cached color components
  private final int stoneR, stoneG, stoneB;
  private final int runeR, runeG, runeB;
  private final int specularR, specularG, specularB;

  private final double ambientCoeff = 0.4;
  private final double diffuseCoeff = 0.75;
  private final double specularCoeff = 0.1;
  private final double shininess = 12.0;
  private final double reflectivity = 0.05;
  private final double ior = 1.8;
  private final double transparency = 0.0;

  private static final int SPECULAR_COLOR = 0xFFCCCCCC;

  // ===== GC DOSTU: PRE-ALLOCATED TEMP OBJECTS =====
  private final Point3 tempLocalPoint = new Point3();
  private final Vector3 tempLocalNormal = new Vector3();
  private final Vector3 tempTransformedNormal = new Vector3();
  private final Vector3 tempViewDir = new Vector3();
  private final Vector3 tempLightDir = new Vector3();
  private final Vector3 tempReflectDir = new Vector3();
  private final Point3 tempViewerPoint = new Point3();
  private final LightProperties tempLightProps = new LightProperties();

  public VikingRuneMaterial() {
    this(0xFF606060, 0xFFE8D8C8, 0.35);
  }

  public VikingRuneMaterial(int stoneColor, int runeColor, double runeDepth) {
    this.stoneColorPacked = stoneColor;
    this.runeColorPacked = runeColor;
    this.runeDepth = Math.max(0, Math.min(1, runeDepth));

    this.stoneR = (stoneColor >> 16) & 0xFF;
    this.stoneG = (stoneColor >> 8) & 0xFF;
    this.stoneB = stoneColor & 0xFF;

    this.runeR = (runeColor >> 16) & 0xFF;
    this.runeG = (runeColor >> 8) & 0xFF;
    this.runeB = runeColor & 0xFF;

    this.specularR = 204;
    this.specularG = 204;
    this.specularB = 204;

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

    // 3. Calculate rune carving
    int surfaceColor = calculateRuneCarving(tempLocalPoint);
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
    int specR = (int) (specularR * specFactor * lightR / 255.0);
    int specG = (int) (specularG * specFactor * lightG / 255.0);
    int specB = (int) (specularB * specFactor * lightB / 255.0);

    // 10. Combine
    int r = ((ambient >> 16) & 0xFF) + diffR + specR;
    int g = ((ambient >> 8) & 0xFF) + diffG + specG;
    int b = (ambient & 0xFF) + diffB + specB;

    r = clamp(r);
    g = clamp(g);
    b = clamp(b);

    return (0xFF000000 | (r << 16) | (g << 8) | b);
  }

  private int calculateRuneCarving(Point3 point) {
    double x = point.x * 10.0;
    double y = point.y * 10.0;
    double z = point.z * 10.0;

    double rune1 = Math.abs(Math.sin(x * 2.5) * Math.cos(y * 2.0));
    double rune2 = Math.abs(Math.sin(x * 3.0 + y * 1.7) + Math.cos(y * 2.3 + z * 1.2));
    double rune3 = (Math.floor(x * 0.8) + Math.floor(y * 0.8)) % 2.5;
    double ancient = Math.sin(x * 1.2 + y * 0.8 + z * 0.5) * 0.3 + 0.7;

    double combinedPattern = (rune1 * 0.35 + rune2 * 0.3 + rune3 * 0.25 + ancient * 0.1);
    double normalizedPattern = combinedPattern % 1.0;

    int r, g, b;
    if (normalizedPattern < runeDepth) {
      double depth = normalizedPattern / runeDepth;
      double darkFactor = 1.0 - depth * 0.6;
      r = (int) (runeR * darkFactor);
      g = (int) (runeG * darkFactor);
      b = (int) (runeB * darkFactor);
    } else {
      double weathering = (normalizedPattern - runeDepth) / (1.0 - runeDepth);
      double noise = 0.9 + Math.sin(weathering * 15.0) * 0.1;
      double darkFactor2 = 1.0 - weathering * 0.2;
      r = (int) (stoneR * noise * darkFactor2);
      g = (int) (stoneG * noise * darkFactor2);
      b = (int) (stoneB * noise * darkFactor2);
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
    sb.append("VikingRuneMaterial vikingrunematerial {\n");
    sb.append("        stoneColor = ")
        .append(ColorUtil.toColorString(stoneColorPacked))
        .append(";\n");
    sb.append("        runeColor = ")
        .append(ColorUtil.toColorString(runeColorPacked))
        .append(";\n");
    sb.append("        runeDepth = ").append(runeDepth).append(";\n");
    sb.append("    }");
    return sb.toString();
  }
}
