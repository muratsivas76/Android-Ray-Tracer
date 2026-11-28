package net.murat.material;

import net.murat.light.*;
import net.murat.math.*;
import net.murat.util.ColorUtil;

/** OrbitalMaterial - Creates orbital ring patterns. Zero-allocation using packed ARGB integers. */
public class OrbitalMaterial implements Material {
  private int centerColorPacked;
  private int orbitColorPacked;
  private double ringWidth;
  private int ringCount;
  private double transparency;
  private Matrix4 objectInverseTransform;
  private Matrix4 inverseTransposeTransform;

  // Pre-cached color components
  private int centerR, centerG, centerB;
  private int orbitR, orbitG, orbitB;

  private final double ambientCoefficient = 0.3;
  private final double diffuseCoefficient = 0.7;
  private final double specularCoefficient = 0.4;
  private final double shininess = 50.0;
  private final double reflectivity = 0.2;
  private final double ior = 1.4;

  // ===== GC DOSTU: PRE-ALLOCATED TEMP OBJECTS =====
  private final Point3 tempLocalPoint = new Point3();
  private final Vector3 tempLocalNormal = new Vector3();
  private final Vector3 tempTransformedNormal = new Vector3();
  private final Vector3 tempViewDir = new Vector3();
  private final Vector3 tempLightDir = new Vector3();
  private final Vector3 tempHalfway = new Vector3();
  private final Point3 tempViewerPoint = new Point3();
  private final LightProperties tempLightProps = new LightProperties();

  public OrbitalMaterial(
      int centerColor,
      int orbitColor,
      double ringWidth,
      int ringCount,
      Matrix4 objectInverseTransform) {
    this.centerColorPacked = centerColor;
    this.orbitColorPacked = orbitColor;
    this.ringWidth = Math.max(0.01, ringWidth);
    this.ringCount = Math.max(1, ringCount);
    this.objectInverseTransform =
        objectInverseTransform != null ? objectInverseTransform : new Matrix4();
    this.inverseTransposeTransform = this.objectInverseTransform.inverseTransposeForNormal();
    this.transparency = 0.0;

    this.centerR = (centerColor >> 16) & 0xFF;
    this.centerG = (centerColor >> 8) & 0xFF;
    this.centerB = centerColor & 0xFF;

    this.orbitR = (orbitColor >> 16) & 0xFF;
    this.orbitG = (orbitColor >> 8) & 0xFF;
    this.orbitB = orbitColor & 0xFF;
  }

  public OrbitalMaterial(int centerColor, int orbitColor, Matrix4 objectInverseTransform) {
    this(centerColor, orbitColor, 0.1, 5, objectInverseTransform);
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
  public void setObjectTransform(Matrix4 tm) {
    this.objectInverseTransform = tm != null ? tm : new Matrix4();
    this.inverseTransposeTransform = this.objectInverseTransform.inverseTransposeForNormal();
  }

  @Override
  public int getColorAt(Point3 worldPoint, Vector3 worldNormal, Light light, Point3 viewerPos) {
    // 1. Transform to local space
    objectInverseTransform.transformPoint(worldPoint, tempLocalPoint);

    // 2. Transform normal
    inverseTransposeTransform.transformDirection(worldNormal, tempTransformedNormal);
    // tempLocalNormal.set(tempTransformedNormal);
    tempLocalNormal.set(worldNormal);
    tempLocalNormal.normalize();

    // 3. Orbital pattern
    double pattern = calculateOrbitalPattern(tempLocalPoint);

    int surfR = (int) (centerR + (orbitR - centerR) * pattern);
    int surfG = (int) (centerG + (orbitG - centerG) * pattern);
    int surfB = (int) (centerB + (orbitB - centerB) * pattern);
    surfR = clamp(surfR);
    surfG = clamp(surfG);
    surfB = clamp(surfB);

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
    // int ambient = ColorUtil.multiplyColors(surfR, surfG, surfB, tempLightProps.color,
    // ambientCoefficient);
    int ambient =
        ColorUtil.multiplyColors(
            (0xFF000000 | (surfR << 16) | (surfG << 8) | surfB),
            tempLightProps.color,
            ambientCoefficient);

    if (light instanceof MuratAmbientLight) {
      return ambient;
    }

    // 7. Diffuse
    double NdotL = Math.max(0.0, tempLocalNormal.dot(tempLightDir));
    double diffFactor = diffuseCoefficient * NdotL * intensity;
    int diffR = (int) (surfR * diffFactor * lightR / 255.0);
    int diffG = (int) (surfG * diffFactor * lightG / 255.0);
    int diffB = (int) (surfB * diffFactor * lightB / 255.0);

    // 8. View direction - ZERO ALLOCATION
    tempViewerPoint.set(viewerPos);
    tempViewDir.set(tempViewerPoint);
    tempViewDir.subtract(worldPoint);
    tempViewDir.normalize();

    // 9. Halfway vector - ZERO ALLOCATION
    tempHalfway.set(tempLightDir);
    tempHalfway.add(tempViewDir);
    tempHalfway.normalize();

    // 10. Specular
    double NdotH = Math.max(0.0, tempLocalNormal.dot(tempHalfway));
    double specIntensity = Math.pow(NdotH, shininess) * specularCoefficient * intensity;
    int specR = (int) (255 * specIntensity * lightR / 255.0);
    int specG = (int) (255 * specIntensity * lightG / 255.0);
    int specB = (int) (255 * specIntensity * lightB / 255.0);

    // 11. Combine
    int r = ((ambient >> 16) & 0xFF) + diffR + specR;
    int g = ((ambient >> 8) & 0xFF) + diffG + specG;
    int b = (ambient & 0xFF) + diffB + specB;

    r = clamp(r);
    g = clamp(g);
    b = clamp(b);

    return (0xFF000000 | (r << 16) | (g << 8) | b);
  }

  private double calculateOrbitalPattern(Point3 point) {
    double px = point.x;
    double py = point.y;
    double pz = point.z;

    double radialDist = Math.sqrt(px * px + pz * pz);
    double radius = Math.sqrt(px * px + py * py + pz * pz);
    double theta = Math.acos(py / radius);
    double normalizedAngle = theta / Math.PI;

    double ringPattern = Math.sin(normalizedAngle * ringCount * Math.PI * 2.0);

    // double ringPattern = Math.sin(radialDist * ringCount * Math.PI * 2.0);
    double x = (ringPattern + 1.0) * 0.5;

    double edge0 = 0.5 - ringWidth;
    double edge1 = 0.5 + ringWidth;
    double diff = edge1 - edge0;
    double invDiff = diff > 0.000001 ? 1.0 / diff : 1.0;
    double v = (x - edge0) * invDiff;
    v = v > 1.0 ? 1.0 : (v < 0.0 ? 0.0 : v);

    return v * v * (3.0 - 2.0 * v);
  }

  private int clamp(int value) {
    return Math.max(0, Math.min(255, value));
  }

  // Getters
  public int getCenterColorPacked() {
    return centerColorPacked;
  }

  public int getOrbitColorPacked() {
    return orbitColorPacked;
  }

  public double getRingWidth() {
    return ringWidth;
  }

  public int getRingCount() {
    return ringCount;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("OrbitalMaterial orbitalmaterial {\n");
    sb.append("        centerColor = ")
        .append(ColorUtil.toColorString(centerColorPacked))
        .append(";\n");
    sb.append("        orbitColor = ")
        .append(ColorUtil.toColorString(orbitColorPacked))
        .append(";\n");
    sb.append("        ringWidth = ").append(ringWidth).append(";\n");
    sb.append("        ringCount = ").append(ringCount).append(";\n");
    sb.append("    }");
    return sb.toString();
  }
}
