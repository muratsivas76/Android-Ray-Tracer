package net.murat.material;

import net.murat.light.*;
import net.murat.math.*;
import net.murat.util.ColorUtil;

/**
 * NazarMaterial - Turkish protection shield! Keeps evil eyes away. Zero-allocation using packed
 * ARGB integers.
 */
public class NazarMaterial implements Material {
  private final int blueColorPacked;
  private final int whiteColorPacked;
  private final double glowIntensity;
  private final double transparency;
  private final double reflectivity;
  private Matrix4 objectInverseTransform;
  private Matrix4 inverseTransposeTransform;

  // Pre-cached color components
  private final int blueR, blueG, blueB;
  private final int whiteR, whiteG, whiteB;

  // ===== GC DOSTU: PRE-ALLOCATED TEMP OBJECTS =====
  private final Point3 tempLocalPoint = new Point3();
  private final Vector3 tempLocalNormal = new Vector3();
  private final Vector3 tempTransformedNormal = new Vector3();
  private final Vector3 tempViewDir = new Vector3();
  private final Vector3 tempLightDir = new Vector3();
  private final Vector3 tempReflectDir = new Vector3();
  private final Point3 tempViewerPoint = new Point3();
  private final LightProperties tempLightProps = new LightProperties();

  public NazarMaterial(
      int blueColor,
      int whiteColor,
      double glowIntensity,
      double transparency,
      double reflectivity,
      Matrix4 invTransform) {
    this.blueColorPacked = blueColor;
    this.whiteColorPacked = whiteColor;
    this.glowIntensity = Math.max(0.0, Math.min(1.0, glowIntensity));
    this.transparency = Math.max(0.0, Math.min(1.0, transparency));
    this.reflectivity = Math.max(0.0, Math.min(1.0, reflectivity));
    this.objectInverseTransform = invTransform != null ? invTransform : new Matrix4();
    this.inverseTransposeTransform = this.objectInverseTransform.inverseTransposeForNormal();

    this.blueR = (blueColor >> 16) & 0xFF;
    this.blueG = (blueColor >> 8) & 0xFF;
    this.blueB = blueColor & 0xFF;

    this.whiteR = (whiteColor >> 16) & 0xFF;
    this.whiteG = (whiteColor >> 8) & 0xFF;
    this.whiteB = whiteColor & 0xFF;
  }

  public NazarMaterial(Matrix4 invTransform) {
    this(0xFF1E90FF, 0xFFFFFFFF, 0.3, 0.25, 0.4, invTransform);
  }

  @Override
  public void setObjectTransform(Matrix4 tm) {
    if (tm == null) tm = new Matrix4();
    this.objectInverseTransform = tm;
    this.inverseTransposeTransform = tm.inverseTransposeForNormal();
  }

  @Override
  public int getColorAt(Point3 worldPoint, Vector3 worldNormal, Light light, Point3 viewPos) {
    if (objectInverseTransform == null) {
      return 0xFF000000;
    }

    // 1. Transform to local space - ZERO ALLOCATION
    objectInverseTransform.transformPoint(worldPoint, tempLocalPoint);

    // 2. Transform normal - ZERO ALLOCATION
    inverseTransposeTransform.transformDirection(worldNormal, tempTransformedNormal);
    tempLocalNormal.set(tempTransformedNormal);
    tempLocalNormal.normalize();

    // 3. Nazar bead circular pattern
    double distanceFromCenter =
        Math.sqrt(tempLocalPoint.x * tempLocalPoint.x + tempLocalPoint.y * tempLocalPoint.y);
    double ringPattern = Math.sin(distanceFromCenter * 20.0) * 0.5 + 0.5;

    // 4. Blue-white rings selection
    int baseR, baseG, baseB;
    if (ringPattern > 0.7) {
      baseR = blueR;
      baseG = blueG;
      baseB = blueB;
    } else {
      baseR = whiteR;
      baseG = whiteG;
      baseB = whiteB;
    }

    // 5. Light properties - ZERO ALLOCATION
    LightProperties.getLightProperties(light, worldPoint, tempLightProps);
    tempLightDir.set(tempLightProps.direction);

    // 6. Light color
    int lightColor = tempLightProps.color;
    int lightR = (lightColor >> 16) & 0xFF;
    int lightG = (lightColor >> 8) & 0xFF;
    int lightB = lightColor & 0xFF;
    double intensity = tempLightProps.intensity;

    // 7. Ambient check
    if (light instanceof MuratAmbientLight) {
      int r = (int) (baseR * 0.2 * intensity * lightR / 255.0);
      int g = (int) (baseG * 0.2 * intensity * lightG / 255.0);
      int b = (int) (baseB * 0.2 * intensity * lightB / 255.0);
      r = clamp(r);
      g = clamp(g);
      b = clamp(b);
      return (0xFF000000 | (r << 16) | (g << 8) | b);
    }

    // 8. View direction - ZERO ALLOCATION
    tempViewerPoint.set(viewPos);
    tempViewDir.set(tempViewerPoint);
    tempViewDir.subtract(worldPoint);
    tempViewDir.normalize();

    // 9. Diffuse
    double NdotL = Math.max(0.0, tempLocalNormal.dot(tempLightDir));
    double diffFactor = NdotL * intensity;
    int diffR = (int) (baseR * diffFactor * lightR / 255.0);
    int diffG = (int) (baseG * diffFactor * lightG / 255.0);
    int diffB = (int) (baseB * diffFactor * lightB / 255.0);

    // 10. Reflection direction - ZERO ALLOCATION
    tempReflectDir.set(tempLightDir);
    tempReflectDir.negate();
    tempReflectDir.reflect(tempLocalNormal, tempReflectDir);

    // 11. Specular (glass-like high shininess)
    double RdotV = Math.max(0.0, tempReflectDir.dot(tempViewDir));
    double specFactor = Math.pow(RdotV, 128.0) * 0.8 * intensity;
    int specR = (int) (255 * specFactor * lightR / 255.0);
    int specG = (int) (255 * specFactor * lightG / 255.0);
    int specB = (int) (255 * specFactor * lightB / 255.0);

    // 12. Ambient
    int ambR = (int) (baseR * 0.2 * intensity * lightR / 255.0);
    int ambG = (int) (baseG * 0.2 * intensity * lightG / 255.0);
    int ambB = (int) (baseB * 0.2 * intensity * lightB / 255.0);

    // 13. Glow (self-illumination)
    int glowR = (int) (blueR * glowIntensity);
    int glowG = (int) (blueG * glowIntensity);
    int glowB = (int) (blueB * glowIntensity);

    // 14. Combine
    int r = ambR + diffR + specR + glowR;
    int g = ambG + diffG + specG + glowG;
    int b = ambB + diffB + specB + glowB;

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
    return 1.5;
  }

  @Override
  public double getTransparency() {
    return transparency;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("NazarMaterial nazarmaterial {\n");
    sb.append("        blueColor = ")
        .append(ColorUtil.toColorString(blueColorPacked))
        .append(";\n");
    sb.append("        whiteColor = ")
        .append(ColorUtil.toColorString(whiteColorPacked))
        .append(";\n");
    sb.append("        glowIntensity = ").append(glowIntensity).append(";\n");
    sb.append("        transparency = ").append(transparency).append(";\n");
    sb.append("        reflectivity = ").append(reflectivity).append(";\n");
    sb.append("    }");
    return sb.toString();
  }
}
