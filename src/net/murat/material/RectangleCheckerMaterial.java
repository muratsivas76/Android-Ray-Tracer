package net.murat.material;

import net.murat.light.*;
import net.murat.math.*;
import net.murat.util.ColorUtil;

/**
 * RectangleCheckerMaterial - Rectangular checkerboard pattern. Zero-allocation using packed ARGB
 * integers.
 */
public class RectangleCheckerMaterial implements Material {
  private final int color1Packed;
  private final int color2Packed;
  private final double rectWidth;
  private final double rectHeight;
  private Matrix4 objectInverseTransform;
  private Matrix4 inverseTransposeTransform;

  private final int color1R, color1G, color1B;
  private final int color2R, color2G, color2B;

  private final double ambientCoefficient;
  private final double diffuseCoefficient;
  private final double specularCoefficient;
  private final double shininess;
  private final double reflectivity;
  private final double ior;
  private final double transparency;
  private final int specularColorPacked;

  // ===== GC DOSTU: PRE-ALLOCATED TEMP OBJECTS =====
  private final Point3 tempLocalPoint = new Point3();
  private final Vector3 tempLocalNormal = new Vector3();
  private final Vector3 tempTransformedNormal = new Vector3();
  private final Vector3 tempViewDir = new Vector3();
  private final Vector3 tempLightDir = new Vector3();
  private final Vector3 tempReflectDir = new Vector3();
  private final Point3 tempViewerPoint = new Point3();
  private final LightProperties tempLightProps = new LightProperties();

  public RectangleCheckerMaterial(
      int color1,
      int color2,
      double rectWidth,
      double rectHeight,
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
    this.rectWidth = rectWidth;
    this.rectHeight = rectHeight;
    this.ambientCoefficient = ambient;
    this.diffuseCoefficient = diffuse;
    this.specularCoefficient = specular;
    this.shininess = shininess;
    this.reflectivity = reflectivity;
    this.ior = ior;
    this.transparency = transparency;
    this.objectInverseTransform =
        objectInverseTransform != null ? objectInverseTransform : Matrix4.identity();
    this.inverseTransposeTransform = this.objectInverseTransform.inverseTransposeForNormal();
    this.specularColorPacked = 0xFFFFFFFF;

    this.color1R = (color1 >> 16) & 0xFF;
    this.color1G = (color1 >> 8) & 0xFF;
    this.color1B = color1 & 0xFF;

    this.color2R = (color2 >> 16) & 0xFF;
    this.color2G = (color2 >> 8) & 0xFF;
    this.color2B = color2 & 0xFF;
  }

  public RectangleCheckerMaterial(
      int color1, int color2, double rectWidth, double rectHeight, Matrix4 objectInverseTransform) {
    this(
        color1,
        color2,
        rectWidth,
        rectHeight,
        0.1,
        0.7,
        0.2,
        10.0,
        0.0,
        1.0,
        0.0,
        objectInverseTransform);
  }

  @Override
  public void setObjectTransform(Matrix4 tm) {
    if (tm == null) tm = Matrix4.identity();
    this.objectInverseTransform = tm;
    this.inverseTransposeTransform = tm.inverseTransposeForNormal();
  }

  private int getPatternColor(double u, double v) {
    double uScaled = u / rectWidth;
    double vScaled = v / rectHeight;

    uScaled = (uScaled % 2.0 + 2.0) % 2.0;
    vScaled = (vScaled % 2.0 + 2.0) % 2.0;

    int xSeg = (int) Math.floor(uScaled);
    int ySeg = (int) Math.floor(vScaled);

    if (((xSeg + ySeg) & 1) == 0) {
      return color1Packed;
    } else {
      return color2Packed;
    }
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

    // 3. UV mapping
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
    int patternColor = getPatternColor(u, v);
    int patR = (patternColor >> 16) & 0xFF;
    int patG = (patternColor >> 8) & 0xFF;
    int patB = patternColor & 0xFF;

    // 5. Light properties - ZERO ALLOCATION
    LightProperties.getLightProperties(light, worldPoint, tempLightProps);
    tempLightDir.set(tempLightProps.direction);

    int lightColor = tempLightProps.color;
    int lightR = (lightColor >> 16) & 0xFF;
    int lightG = (lightColor >> 8) & 0xFF;
    int lightB = lightColor & 0xFF;
    double intensity = tempLightProps.intensity;

    // 6. Ambient check
    if (light instanceof MuratAmbientLight) {
      int r = (int) (patR * ambientCoefficient * lightR / 255.0 * intensity);
      int g = (int) (patG * ambientCoefficient * lightG / 255.0 * intensity);
      int b = (int) (patB * ambientCoefficient * lightB / 255.0 * intensity);
      r = clamp(r);
      g = clamp(g);
      b = clamp(b);
      return (0xFF000000 | (r << 16) | (g << 8) | b);
    }

    // 7. Diffuse
    double NdotL = Math.max(0.0, tempLocalNormal.dot(tempLightDir));
    double diffFactor = diffuseCoefficient * NdotL * intensity;
    int diffR = (int) (patR * diffFactor * lightR / 255.0);
    int diffG = (int) (patG * diffFactor * lightG / 255.0);
    int diffB = (int) (patB * diffFactor * lightB / 255.0);

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
    double specFactor = Math.pow(RdotV, shininess) * specularCoefficient * intensity;

    int specR = (int) (((specularColorPacked >> 16) & 0xFF) * specFactor * lightR / 255.0);
    int specG = (int) (((specularColorPacked >> 8) & 0xFF) * specFactor * lightG / 255.0);
    int specB = (int) ((specularColorPacked & 0xFF) * specFactor * lightB / 255.0);

    specR = clamp(specR);
    specG = clamp(specG);
    specB = clamp(specB);

    // 11. Combine
    int r = (int) (patR * ambientCoefficient * lightR / 255.0 * intensity) + diffR + specR;
    int g = (int) (patG * ambientCoefficient * lightG / 255.0 * intensity) + diffG + specG;
    int b = (int) (patB * ambientCoefficient * lightB / 255.0 * intensity) + diffB + specB;

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
    sb.append("RectangleCheckerMaterial rectanglecheckermaterial {\n");
    sb.append("        color1 = ").append(ColorUtil.toColorString(color1Packed)).append(";\n");
    sb.append("        color2 = ").append(ColorUtil.toColorString(color2Packed)).append(";\n");
    sb.append("        rectWidth = ").append(rectWidth).append(";\n");
    sb.append("        rectHeight = ").append(rectHeight).append(";\n");
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
