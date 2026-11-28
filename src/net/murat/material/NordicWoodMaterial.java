package net.murat.material;

import net.murat.light.*;
import net.murat.math.*;
import net.murat.util.ColorUtil;

/**
 * NordicWoodMaterial - High performance procedural wood grain texture rendering. Zero-allocation
 * using packed ARGB integers.
 */
public class NordicWoodMaterial implements Material {
  private final int woodColorPacked;
  private final int grainColorPacked;
  private final double grainIntensity;

  private Matrix4 objectTransform;
  private Matrix4 inverseTransform;
  private Matrix4 inverseTransposeTransform;

  // Pre-cached color components
  private final int woodR, woodG, woodB;
  private final int grainR, grainG, grainB;

  private final double ambientCoeff = 0.45;
  private final double diffuseCoeff = 0.85;
  private final double specularCoeff = 0.18;
  private final double shininess = 25.0;
  private final double reflectivity = 0.09;
  private final double ior = 1.6;
  private final double transparency = 0.0;

  private static final int SPECULAR_HIGHLIGHT = 0xFFFFECCB;

  // ===== GC DOSTU: PRE-ALLOCATED TEMP OBJECTS =====
  private final Point3 tempLocalPoint = new Point3();
  private final Vector3 tempLocalNormal = new Vector3();
  private final Vector3 tempTransformedNormal = new Vector3();
  private final Vector3 tempViewDir = new Vector3();
  private final Vector3 tempLightDir = new Vector3();
  private final Vector3 tempReflectDir = new Vector3();
  private final Point3 tempViewerPoint = new Point3();
  private final LightProperties tempLightProps = new LightProperties();

  public NordicWoodMaterial() {
    this(0xFF8B5A2B, 0xFF5D4035, 0.5);
  }

  public NordicWoodMaterial(int woodColor, int grainColor, double grainIntensity) {
    this.woodColorPacked = woodColor;
    this.grainColorPacked = grainColor;
    this.grainIntensity = Math.max(0, Math.min(1, grainIntensity));

    this.woodR = (woodColor >> 16) & 0xFF;
    this.woodG = (woodColor >> 8) & 0xFF;
    this.woodB = woodColor & 0xFF;

    this.grainR = (grainColor >> 16) & 0xFF;
    this.grainG = (grainColor >> 8) & 0xFF;
    this.grainB = grainColor & 0xFF;

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

    // 1. Transform to local space - ZERO ALLOCATION
    inverseTransform.transformPoint(worldPoint, tempLocalPoint);

    // 2. Transform normal - ZERO ALLOCATION
    inverseTransposeTransform.transformDirection(worldNormal, tempTransformedNormal);
    tempLocalNormal.set(tempTransformedNormal);
    tempLocalNormal.normalize();

    // 3. Calculate wood grain
    int surfaceColor = calculateWoodGrain(tempLocalPoint);
    int surfR = (surfaceColor >> 16) & 0xFF;
    int surfG = (surfaceColor >> 8) & 0xFF;
    int surfB = surfaceColor & 0xFF;

    // 4. Light properties - ZERO ALLOCATION
    LightProperties.getLightProperties(light, worldPoint, tempLightProps);
    tempLightDir.set(tempLightProps.direction);

    // 5. Light color
    int lightColor = tempLightProps.color;
    int lightR = (lightColor >> 16) & 0xFF;
    int lightG = (lightColor >> 8) & 0xFF;
    int lightB = lightColor & 0xFF;
    double intensity = tempLightProps.intensity;

    // 6. Ambient check
    int ambient = ColorUtil.multiplyColors(surfaceColor, tempLightProps.color, ambientCoeff);

    if (light instanceof MuratAmbientLight) {
      return ambient;
    }

    // 7. Diffuse
    double NdotL = Math.max(0.0, tempLocalNormal.dot(tempLightDir));
    double diffFactor = diffuseCoeff * NdotL * intensity;
    int diffR = (int) (surfR * diffFactor * lightR / 255.0);
    int diffG = (int) (surfG * diffFactor * lightG / 255.0);
    int diffB = (int) (surfB * diffFactor * lightB / 255.0);

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
    double specFactor = Math.pow(RdotV, shininess) * specularCoeff * intensity;

    int specR = (int) (((SPECULAR_HIGHLIGHT >> 16) & 0xFF) * specFactor * lightR / 255.0);
    int specG = (int) (((SPECULAR_HIGHLIGHT >> 8) & 0xFF) * specFactor * lightG / 255.0);
    int specB = (int) ((SPECULAR_HIGHLIGHT & 0xFF) * specFactor * lightB / 255.0);

    // Clamp
    specR = specR > 255 ? 255 : (specR < 0 ? 0 : specR);
    specG = specG > 255 ? 255 : (specG < 0 ? 0 : specG);
    specB = specB > 255 ? 255 : (specB < 0 ? 0 : specB);

    // 11. Combine
    int r = ((ambient >> 16) & 0xFF) + diffR + specR;
    int g = ((ambient >> 8) & 0xFF) + diffG + specG;
    int b = (ambient & 0xFF) + diffB + specB;

    r = clamp(r);
    g = clamp(g);
    b = clamp(b);

    return (0xFF000000 | (r << 16) | (g << 8) | b);
  }

  private int calculateWoodGrain(Point3 point) {
    double x = point.x * 12.0;
    double y = point.y * 12.0;
    double z = point.z * 12.0;

    double ringPattern = Math.sin(x * 0.8 + Math.sin(y * 0.3) * 2.0);
    double linePattern = Math.abs(Math.sin(y * 4.0 + Math.cos(x * 1.2) * 0.5));
    double noisePattern = Math.sin(x * 5.0 + y * 3.0 + z * 2.0) * 0.3;

    double combinedPattern = (ringPattern * 0.5 + linePattern * 0.3 + noisePattern * 0.2);
    double normalizedPattern = (combinedPattern + 1.0) * 0.5;

    int r, g, b;
    if (normalizedPattern < grainIntensity) {
      double intensity = normalizedPattern / grainIntensity;
      double blend = intensity * 0.7;
      r = (int) (woodR + (grainR - woodR) * blend);
      g = (int) (woodG + (grainG - woodG) * blend);
      b = (int) (woodB + (grainB - woodB) * blend);
    } else {
      double intensity = (normalizedPattern - grainIntensity) / (1.0 - grainIntensity);
      double warmth = 0.9 + Math.sin(intensity * 15.0) * 0.1;
      double darkness = 0.85 + Math.cos(intensity * 8.0) * 0.15;

      r = (int) (woodR * warmth * darkness);
      g = (int) (woodG * warmth * darkness * 0.95);
      b = (int) (woodB * darkness * 0.9);
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
    sb.append("NordicWoodMaterial nordicwoodmaterial {\n");
    sb.append("        woodColor = ")
        .append(ColorUtil.toColorString(woodColorPacked))
        .append(";\n");
    sb.append("        grainColor = ")
        .append(ColorUtil.toColorString(grainColorPacked))
        .append(";\n");
    sb.append("        grainIntensity = ").append(grainIntensity).append(";\n");
    sb.append("    }");
    return sb.toString();
  }
}
