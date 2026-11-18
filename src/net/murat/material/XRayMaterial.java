package net.murat.material;

import net.murat.light.*;
import net.murat.math.*;
import net.murat.util.ColorUtil;

/**
 * XRayMaterial - High performance volumetric X-Ray depth rendering. Zero-allocation using packed
 * ARGB integers.
 */
public class XRayMaterial implements Material {
  private Matrix4 objectTransform;
  private Matrix4 inverseObjectTransform;
  private Matrix4 inverseTransposeTransform;

  private int baseColorPacked;
  private double transparency;
  private double reflectivity;
  private final double ior = 1.1;

  // Pre-cached color components
  private int baseR, baseG, baseB;
  private float baseRF, baseGF, baseBF;
  private float baseAF;

  private static final float INV_255F = 0.003921569f;

  // ===== GC DOSTU: PRE-ALLOCATED TEMP OBJECTS =====
  private final Point3 tempLocalPoint = new Point3();
  private final Vector3 tempLocalNormal = new Vector3();
  private final Vector3 tempTransformedNormal = new Vector3();
  private final Vector3 tempViewDir = new Vector3();
  private final Vector3 tempLightDir = new Vector3();
  private final Vector3 tempReflectDir = new Vector3();
  private final Point3 tempViewerPoint = new Point3();
  private final LightProperties tempLightProps = new LightProperties();

  public XRayMaterial() {
    this(0xFF2666FF, 0.92, 0.05);
  }

  public XRayMaterial(int baseColor, double transparency, double reflectivity) {
    this.baseColorPacked = baseColor;
    this.transparency = transparency;
    this.reflectivity = reflectivity;

    this.baseR = (baseColor >> 16) & 0xFF;
    this.baseG = (baseColor >> 8) & 0xFF;
    this.baseB = baseColor & 0xFF;

    this.baseRF = this.baseR * INV_255F;
    this.baseGF = this.baseG * INV_255F;
    this.baseBF = this.baseB * INV_255F;
    this.baseAF = 0.6f;

    this.objectTransform = Matrix4.identity();
    this.inverseObjectTransform = Matrix4.identity();
    this.inverseTransposeTransform = Matrix4.identity();
  }

  @Override
  public void setObjectTransform(Matrix4 tm) {
    if (tm == null) tm = new Matrix4();
    this.objectTransform = tm;
    this.inverseObjectTransform = tm.inverse();
    this.inverseTransposeTransform = this.inverseObjectTransform.inverseTransposeForNormal();
  }

  @Override
  public int getColorAt(Point3 point, Vector3 normal, Light light, Point3 viewerPoint) {
    if (inverseObjectTransform == null) {
      return 0xFF000000;
    }

    // 1. Transform to local space
    inverseObjectTransform.transformPoint(point, tempLocalPoint);

    // 2. Transform normal
    inverseTransposeTransform.transformDirection(normal, tempTransformedNormal);
    tempLocalNormal.set(tempTransformedNormal);
    tempLocalNormal.normalize();

    // 3. Depth calculation - volumetric attenuation
    double px = tempLocalPoint.x;
    double py = tempLocalPoint.y;
    double pz = tempLocalPoint.z;
    double depth = Math.sqrt(px * px + py * py + pz * pz);
    float intensity = (float) Math.exp(-depth * 0.5);

    // 4. Light properties - ZERO ALLOCATION
    LightProperties.getLightProperties(light, point, tempLightProps);
    tempLightDir.set(tempLightProps.direction);

    int lightColor = tempLightProps.color;
    int lightR = (lightColor >> 16) & 0xFF;
    int lightG = (lightColor >> 8) & 0xFF;
    int lightB = lightColor & 0xFF;
    double lightIntensity = tempLightProps.intensity;

    // 5. Base color with volumetric attenuation
    int r = (int) (baseR * intensity * lightR / 255.0 * lightIntensity);
    int g = (int) (baseG * intensity * lightG / 255.0 * lightIntensity);
    int b = (int) (baseB * intensity * lightB / 255.0 * lightIntensity);

    // 6. View direction - ZERO ALLOCATION
    tempViewerPoint.set(viewerPoint);
    tempViewDir.set(tempViewerPoint);
    tempViewDir.subtract(point);
    tempViewDir.normalize();

    // 7. Add subtle edge glow for X-ray effect
    double fresnel = Math.pow(1.0 - Math.max(0.0, tempLocalNormal.dot(tempViewDir)), 3.0);
    int glowR = (int) (fresnel * 80 * lightR / 255.0 * lightIntensity);
    int glowG = (int) (fresnel * 80 * lightG / 255.0 * lightIntensity);
    int glowB = (int) (fresnel * 80 * lightB / 255.0 * lightIntensity);

    r = clamp(r + glowR);
    g = clamp(g + glowG);
    b = clamp(b + glowB);

    // 8. Apply transparency
    int alpha = (int) (0.6f * 255);

    return (alpha << 24) | (r << 16) | (g << 8) | b;
  }

  private int clamp(int value) {
    return Math.max(0, Math.min(255, value));
  }

  @Override
  public double getTransparency() {
    return transparency;
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
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("XRayMaterial xraymaterial {\n");
    sb.append("        baseColor = ")
        .append(ColorUtil.toColorString(baseColorPacked))
        .append(";\n");
    sb.append("        transparency = ").append(transparency).append(";\n");
    sb.append("        reflectivity = ").append(reflectivity).append(";\n");
    sb.append("    }");
    return sb.toString();
  }
}
