package net.murat.material;

import net.murat.light.*;
import net.murat.math.*;
import net.murat.util.ColorUtil;

/**
 * LavaFlowMaterial - Procedural animated lava with emissive glow. Zero-allocation using packed ARGB
 * integers.
 */
public class LavaFlowMaterial implements Material {
  private final int hotColorPacked;
  private final int coolColorPacked;
  private final double flowSpeed;
  private Matrix4 objectInverseTransform;
  private Matrix4 inverseTransposeTransform;
  private double time;
  private final double reflectivity = 0.1;

  // Pre-cached color components
  private final int hotR, hotG, hotB;
  private final int coolR, coolG, coolB;

  // ===== GC DOSTU: PRE-ALLOCATED TEMP OBJECTS =====
  private final Point3 tempLocalPoint = new Point3();
  private final Vector3 tempLocalNormal = new Vector3();
  private final Vector3 tempTransformedNormal = new Vector3();
  private final Vector3 tempLightDir = new Vector3();
  private final LightProperties tempLightProps = new LightProperties();

  public LavaFlowMaterial(int hotColor, int coolColor, double flowSpeed, Matrix4 invTransform) {
    this.hotColorPacked = hotColor;
    this.coolColorPacked = coolColor;
    this.flowSpeed = flowSpeed;
    this.objectInverseTransform = invTransform != null ? invTransform : new Matrix4();
    this.inverseTransposeTransform = this.objectInverseTransform.inverseTransposeForNormal();
    this.time = 0.0;

    this.hotR = (hotColor >> 16) & 0xFF;
    this.hotG = (hotColor >> 8) & 0xFF;
    this.hotB = hotColor & 0xFF;

    this.coolR = (coolColor >> 16) & 0xFF;
    this.coolG = (coolColor >> 8) & 0xFF;
    this.coolB = coolColor & 0xFF;
  }

  public void update(double deltaTime) {
    time += deltaTime * flowSpeed;
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

    // 3. Lava flow pattern
    double nx = tempLocalPoint.x * 2.0 + time;
    double ny = tempLocalPoint.y * 3.0;

    double noise1 = fract(Math.sin(nx) * 43758.5453);
    double noise2 = fract(Math.cos(ny) * 12578.1459);
    double pattern =
        clamp(Math.sin(noise1 * Math.PI * 3.0) * Math.cos(noise2 * Math.PI * 2.0), -1.0, 1.0) * 0.5
            + 0.5;

    // Temperature variation
    double temp = clamp(0.5 + 0.5 * Math.sin(tempLocalPoint.y * 5.0 - time * 0.3), 0.0, 1.0);
    double blendFactor = temp * (0.5 + 0.5 * pattern);

    // 4. Base color - blend cool to hot
    int baseR = (int) (coolR + (hotR - coolR) * blendFactor);
    int baseG = (int) (coolG + (hotG - coolG) * blendFactor);
    int baseB = (int) (coolB + (hotB - coolB) * blendFactor);
    baseR = baseR > 255 ? 255 : (baseR < 0 ? 0 : baseR);
    baseG = baseG > 255 ? 255 : (baseG < 0 ? 0 : baseG);
    baseB = baseB > 255 ? 255 : (baseB < 0 ? 0 : baseB);
    int baseColor = (0xFF000000 | (baseR << 16) | (baseG << 8) | baseB);

    // 5. Light properties - ZERO ALLOCATION
    LightProperties.getLightProperties(light, worldPoint, tempLightProps);
    tempLightDir.set(tempLightProps.direction);

    // 6. Ambient check
    if (light instanceof MuratAmbientLight) {
      int r = (int) (baseR * 0.5 * tempLightProps.intensity);
      int g = (int) (baseG * 0.5 * tempLightProps.intensity);
      int b = (int) (baseB * 0.5 * tempLightProps.intensity);
      r = r > 255 ? 255 : (r < 0 ? 0 : r);
      g = g > 255 ? 255 : (g < 0 ? 0 : g);
      b = b > 255 ? 255 : (b < 0 ? 0 : b);
      return (0xFF000000 | (r << 16) | (g << 8) | b);
    }

    // 7. Diffuse lighting
    double NdotL = clamp(tempLocalNormal.dot(tempLightDir), 0.0, 1.0);
    int lightColor = tempLightProps.color;
    double intensity = tempLightProps.intensity;

    int lightR = (lightColor >> 16) & 0xFF;
    int lightG = (lightColor >> 8) & 0xFF;
    int lightB = lightColor & 0xFF;

    int diffR = (int) (baseR * NdotL * intensity * lightR / 255.0);
    int diffG = (int) (baseG * NdotL * intensity * lightG / 255.0);
    int diffB = (int) (baseB * NdotL * intensity * lightB / 255.0);

    // 8. Emissive glow
    double glow = clamp(Math.pow(temp * 0.8 + 0.2, 3.0) * (0.7 + 0.3 * pattern), 0.0, 1.0);
    int emitR = (int) (hotR * glow * 0.5 * intensity * lightR / 255.0);
    int emitG = (int) (hotG * glow * 0.5 * intensity * lightG / 255.0);
    int emitB = (int) (hotB * glow * 0.5 * intensity * lightB / 255.0);

    // 9. Combine
    int r = diffR + emitR;
    int g = diffG + emitG;
    int b = diffB + emitB;

    r = r > 255 ? 255 : (r < 0 ? 0 : r);
    g = g > 255 ? 255 : (g < 0 ? 0 : g);
    b = b > 255 ? 255 : (b < 0 ? 0 : b);

    return (0xFF000000 | (r << 16) | (g << 8) | b);
  }

  private static double clamp(double value, double min, double max) {
    return Math.max(min, Math.min(max, value));
  }

  private static double fract(double value) {
    return value - Math.floor(value);
  }

  @Override
  public double getReflectivity() {
    return reflectivity;
  }

  @Override
  public double getIndexOfRefraction() {
    return 1.4;
  }

  @Override
  public double getTransparency() {
    return 0.0;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("LavaFlowMaterial lavaflowmaterial {\n");
    sb.append("        hotColor = ").append(ColorUtil.toColorString(hotColorPacked)).append(";\n");
    sb.append("        coolColor = ")
        .append(ColorUtil.toColorString(coolColorPacked))
        .append(";\n");
    sb.append("        flowSpeed = ").append(flowSpeed).append(";\n");
    sb.append("    }");
    return sb.toString();
  }
}
