package net.murat.material;

import net.murat.light.*;
import net.murat.math.*;
import net.murat.util.ColorUtil;

/**
 * NordicWeaveMaterial - High performance cultural weave texture rendering. Zero-allocation using
 * packed ARGB integers.
 */
public class NordicWeaveMaterial implements Material {
  private final int primaryColorPacked;
  private final int secondaryColorPacked;
  private final int accentColorPacked;
  private final double patternScale;

  private Matrix4 objectTransform;
  private Matrix4 inverseTransform;
  private Matrix4 inverseTransposeTransform;

  // Pre-cached color components
  private final int primaryR, primaryG, primaryB;
  private final int secondaryR, secondaryG, secondaryB;
  private final int accentR, accentG, accentB;

  private final double ambientCoeff = 0.5;
  private final double diffuseCoeff = 0.9;
  private final double specularCoeff = 0.1;
  private final double shininess = 10.0;
  private final double reflectivity = 0.05;
  private final double ior = 1.5;
  private final double transparency = 0.0;

  // ===== GC DOSTU: PRE-ALLOCATED TEMP OBJECTS =====
  private final Point3 tempLocalPoint = new Point3();
  private final Vector3 tempLocalNormal = new Vector3();
  private final Vector3 tempTransformedNormal = new Vector3();
  private final Vector3 tempViewDir = new Vector3();
  private final Vector3 tempLightDir = new Vector3();
  private final Vector3 tempReflectDir = new Vector3();
  private final Point3 tempViewerPoint = new Point3();
  private final LightProperties tempLightProps = new LightProperties();

  public NordicWeaveMaterial() {
    this(0xFF8B4513, 0xFF006464, 0xFFDCDCDC, 4.0);
  }

  public NordicWeaveMaterial(
      int primaryColor, int secondaryColor, int accentColor, double patternScale) {
    this.primaryColorPacked = primaryColor;
    this.secondaryColorPacked = secondaryColor;
    this.accentColorPacked = accentColor;
    this.patternScale = patternScale;

    this.primaryR = (primaryColor >> 16) & 0xFF;
    this.primaryG = (primaryColor >> 8) & 0xFF;
    this.primaryB = primaryColor & 0xFF;

    this.secondaryR = (secondaryColor >> 16) & 0xFF;
    this.secondaryG = (secondaryColor >> 8) & 0xFF;
    this.secondaryB = secondaryColor & 0xFF;

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

    // 1. Transform to local space - ZERO ALLOCATION
    inverseTransform.transformPoint(worldPoint, tempLocalPoint);

    // 2. Transform normal - ZERO ALLOCATION
    inverseTransposeTransform.transformDirection(worldNormal, tempTransformedNormal);
    tempLocalNormal.set(tempTransformedNormal);
    tempLocalNormal.normalize();

    // 3. Calculate pattern
    int surfaceColor = calculateWeavePattern(tempLocalPoint);
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
    int specR = (int) (accentR * specFactor * lightR / 255.0);
    int specG = (int) (accentG * specFactor * lightG / 255.0);
    int specB = (int) (accentB * specFactor * lightB / 255.0);

    // 11. Combine
    int r = ((ambient >> 16) & 0xFF) + diffR + specR;
    int g = ((ambient >> 8) & 0xFF) + diffG + specG;
    int b = (ambient & 0xFF) + diffB + specB;

    r = clamp(r);
    g = clamp(g);
    b = clamp(b);

    return (0xFF000000 | (r << 16) | (g << 8) | b);
  }

  private int calculateWeavePattern(Point3 point) {
    double x = point.x * patternScale;
    double y = point.y * patternScale;

    double pattern1 = Math.sin(x * 2.0) * Math.cos(y * 2.0);
    double pattern2 = Math.abs(Math.sin(x * 3.0 + y * 3.0));
    double pattern3 = Math.floor(x * 0.5) + Math.floor(y * 0.5);

    double combinedPattern = (pattern1 + pattern2 + pattern3 % 2.0) % 3.0;
    if (combinedPattern < 0.0) {
      combinedPattern += 3.0;
    }

    int r, g, b;
    if (combinedPattern < 1.0) {
      r = primaryR;
      g = primaryG;
      b = primaryB;
    } else if (combinedPattern < 2.0) {
      r = secondaryR;
      g = secondaryG;
      b = secondaryB;
    } else {
      r = accentR;
      g = accentG;
      b = accentB;
    }

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
    sb.append("NordicWeaveMaterial nordicweavematerial {\n");
    sb.append("        primaryColor = ")
        .append(ColorUtil.toColorString(primaryColorPacked))
        .append(";\n");
    sb.append("        secondaryColor = ")
        .append(ColorUtil.toColorString(secondaryColorPacked))
        .append(";\n");
    sb.append("        accentColor = ")
        .append(ColorUtil.toColorString(accentColorPacked))
        .append(";\n");
    sb.append("        patternScale = ").append(patternScale).append(";\n");
    sb.append("    }");
    return sb.toString();
  }
}
