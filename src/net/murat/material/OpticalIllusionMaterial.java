package net.murat.material;

import net.murat.light.*;
import net.murat.math.*;
import net.murat.util.ColorUtil;

/**
 * Optical Illusion Material - Creates optical illusion patterns. Zero-allocation using packed ARGB
 * integers.
 */
public class OpticalIllusionMaterial implements Material {
  private final int color1Packed;
  private final int color2Packed;
  private final double frequency;
  private final double smoothness;
  private Matrix4 objectInverseTransform;
  private Matrix4 inverseTransposeTransform;

  // Pre-cached color components
  private final int color1R, color1G, color1B;
  private final int color2R, color2G, color2B;

  private final double ambientCoefficient;
  private final double diffuseCoefficient;
  private final double specularCoefficient;
  private final double shininess;
  private final double reflectivity;
  private final double ior;
  private final double transparency;

  private static final double INV_2PI = 2.0 * Math.PI;

  // ===== GC DOSTU: PRE-ALLOCATED TEMP OBJECTS =====
  private final Point3 tempLocalPoint = new Point3();
  private final Vector3 tempLocalNormal = new Vector3();
  private final Vector3 tempTransformedNormal = new Vector3();
  private final Vector3 tempViewDir = new Vector3();
  private final Vector3 tempLightDir = new Vector3();
  private final Vector3 tempReflectDir = new Vector3();
  private final Point3 tempViewerPoint = new Point3();
  private final LightProperties tempLightProps = new LightProperties();

  public OpticalIllusionMaterial(
      int color1,
      int color2,
      double frequency,
      double smoothness,
      double ambient,
      double diffuse,
      double specular,
      double shininess,
      double reflectivity,
      double ior,
      double transparency,
      Matrix4 objectInverseTransform) {
    this.color1Packed = color1;
    this.color2Packed = color2;
    this.frequency = Math.max(0.1, frequency);
    this.smoothness = Math.max(0, Math.min(1, smoothness));
    this.ambientCoefficient = Math.max(0, Math.min(1, ambient));
    this.diffuseCoefficient = Math.max(0, Math.min(1, diffuse));
    this.specularCoefficient = Math.max(0, Math.min(1, specular));
    this.shininess = Math.max(1, shininess);
    this.reflectivity = Math.max(0, Math.min(1, reflectivity));
    this.ior = ior;
    this.transparency = Math.max(0, Math.min(1, transparency));
    this.objectInverseTransform =
        objectInverseTransform != null ? objectInverseTransform : new Matrix4();
    this.inverseTransposeTransform = this.objectInverseTransform.inverseTransposeForNormal();

    this.color1R = (color1 >> 16) & 0xFF;
    this.color1G = (color1 >> 8) & 0xFF;
    this.color1B = color1 & 0xFF;

    this.color2R = (color2 >> 16) & 0xFF;
    this.color2G = (color2 >> 8) & 0xFF;
    this.color2B = color2 & 0xFF;
  }

  public OpticalIllusionMaterial(
      int color1, int color2, double frequency, double smoothness, Matrix4 objectInverseTransform) {
    this(
        color1,
        color2,
        frequency,
        smoothness,
        0.15,
        0.6,
        0.25,
        30.0,
        0.05,
        1.2,
        0.02,
        objectInverseTransform);
  }

  @Override
  public void setObjectTransform(Matrix4 tm) {
    if (tm == null) tm = new Matrix4();
    this.objectInverseTransform = tm;
    this.inverseTransposeTransform = tm.inverseTransposeForNormal();
  }

  private int getPatternColor(double u, double v) {
    double dist = Math.sqrt(u * u + v * v);
    double pattern = 0.5 * (1.0 + Math.sin(dist * frequency * INV_2PI));

    if (smoothness > 0.0) {
      double edge0 = 0.5 - smoothness;
      double edge1 = 0.5 + smoothness;
      double diff = edge1 - edge0;
      double invDiff = diff > 0.000001 ? 1.0 / diff : 1.0;
      double x = (pattern - edge0) * invDiff;
      x = x > 1.0 ? 1.0 : (x < 0.0 ? 0.0 : x);
      pattern = x * x * (3.0 - 2.0 * x);
    }

    double ratio = pattern > 1.0 ? 1.0 : (pattern < 0.0 ? 0.0 : pattern);
    double invRatio = 1.0 - ratio;

    int r = (int) (color1R * invRatio + color2R * ratio);
    int g = (int) (color1G * invRatio + color2G * ratio);
    int b = (int) (color1B * invRatio + color2B * ratio);

    r = clamp(r);
    g = clamp(g);
    b = clamp(b);

    return (0xFF000000 | (r << 16) | (g << 8) | b);
  }

  @Override
  public int getColorAt(Point3 worldPoint, Vector3 worldNormal, Light light, Point3 viewerPos) {
    if (objectInverseTransform == null) {
      return 0xFF000000;
    }

    // 1. Transform to local space
    objectInverseTransform.transformPoint(worldPoint, tempLocalPoint);

    // 2. Transform normal
    inverseTransposeTransform.transformDirection(worldNormal, tempTransformedNormal);
    tempLocalNormal.set(tempTransformedNormal);
    tempLocalNormal.normalize();

    // 3. UV mapping based on dominant normal axis
    double absX = Math.abs(tempLocalNormal.x);
    double absY = Math.abs(tempLocalNormal.y);
    double absZ = Math.abs(tempLocalNormal.z);

    double u, v;
    if (absX > absY && absX > absZ) {
      u = tempLocalPoint.y;
      v = tempLocalPoint.z;
    } else if (absY > absX && absY > absZ) {
      u = tempLocalPoint.x;
      v = tempLocalPoint.z;
    } else {
      u = tempLocalPoint.x;
      v = tempLocalPoint.y;
    }

    // 4. Pattern color
    int baseColor = getPatternColor(u, v);
    int baseR = (baseColor >> 16) & 0xFF;
    int baseG = (baseColor >> 8) & 0xFF;
    int baseB = baseColor & 0xFF;

    // 5. Light properties - ZERO ALLOCATION
    LightProperties.getLightProperties(light, worldPoint, tempLightProps);
    tempLightDir.set(tempLightProps.direction);

    // 6. Light color
    int lightColor = tempLightProps.color;
    int lightR = (lightColor >> 16) & 0xFF;
    int lightG = (lightColor >> 8) & 0xFF;
    int lightB = lightColor & 0xFF;
    double intensity = tempLightProps.intensity;

    // 7. Ambient
    int r = (int) (baseR * ambientCoefficient * lightR / 255.0);
    int g = (int) (baseG * ambientCoefficient * lightG / 255.0);
    int b = (int) (baseB * ambientCoefficient * lightB / 255.0);

    // 8. Diffuse
    double NdotL = Math.max(0.0, worldNormal.dot(tempLightDir));
    double diffFactor = diffuseCoefficient * NdotL * intensity;

    r += (int) (baseR * diffFactor * lightR / 255.0);
    g += (int) (baseG * diffFactor * lightG / 255.0);
    b += (int) (baseB * diffFactor * lightB / 255.0);

    // 9. View direction - ZERO ALLOCATION
    tempViewerPoint.set(viewerPos);
    tempViewDir.set(tempViewerPoint);
    tempViewDir.subtract(worldPoint);
    tempViewDir.normalize();

    // 10. Reflection direction - ZERO ALLOCATION
    tempReflectDir.set(tempLightDir);
    tempReflectDir.negate();
    tempReflectDir.reflect(worldNormal, tempReflectDir);

    // 11. Specular
    double RdotV = Math.max(0.0, tempReflectDir.dot(tempViewDir));
    double specFactor = Math.pow(RdotV, shininess) * specularCoefficient * intensity;

    r += (int) (255 * specFactor * lightR / 255.0);
    g += (int) (255 * specFactor * lightG / 255.0);
    b += (int) (255 * specFactor * lightB / 255.0);

    // 12. Clamp
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
    sb.append("OpticalIllusionMaterial opticalillusionmaterial {\n");
    sb.append("        color1 = ").append(ColorUtil.toColorString(color1Packed)).append(";\n");
    sb.append("        color2 = ").append(ColorUtil.toColorString(color2Packed)).append(";\n");
    sb.append("        frequency = ").append(frequency).append(";\n");
    sb.append("        smoothness = ").append(smoothness).append(";\n");
    sb.append("        ambient = ").append(ambientCoefficient).append(";\n");
    sb.append("        diffuse = ").append(diffuseCoefficient).append(";\n");
    sb.append("        specular = ").append(specularCoefficient).append(";\n");
    sb.append("        shininess = ").append(shininess).append(";\n");
    sb.append("        reflectivity = ").append(reflectivity).append(";\n");
    sb.append("        ior = ").append(ior).append(";\n");
    sb.append("        transparency = ").append(transparency).append(";\n");
    sb.append("    }");
    return sb.toString();
  }
}
