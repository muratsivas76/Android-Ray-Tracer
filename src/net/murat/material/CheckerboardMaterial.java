package net.murat.material;

import net.murat.light.Light;
import net.murat.light.LightProperties;
import net.murat.light.MuratAmbientLight;
import net.murat.math.*;
import net.murat.util.ColorUtil;

/**
 * CheckerboardMaterial - Procedural checkerboard pattern with Phong shading. Fully adapted to the
 * zero-allocation architecture using packed ARGB integers.
 */
public class CheckerboardMaterial implements Material {

  private int color1;
  private int color2;
  private final double size;

  private Matrix4 objectInverseTransform;
  private Matrix4 inverseTransposeTransform;

  private final double ambientCoeff;
  private final double diffuseCoeff;
  private final double specularCoeff;
  private final double shininess;
  private final int specularColor;
  private final double reflectivity;
  private final double ior;
  private final double transparency;

  private final int specR;
  private final int specG;
  private final int specB;

  // ===== GC DOSTU: PRE-ALLOCATED TEMP OBJECTS =====
  private final Point3 tempLocalPoint = new Point3();
  private final Vector3 tempLocalNormal = new Vector3();
  private final Vector3 tempTransformedNormal = new Vector3();
  private final Vector3 tempViewDir = new Vector3();
  private final Vector3 tempLightDir = new Vector3();
  private final Vector3 tempReflectDir = new Vector3();
  private final Point3 tempViewerPoint = new Point3();
  private final LightProperties tempLightProps = new LightProperties();

  public CheckerboardMaterial(int color1, int color2, double size, Matrix4 objectInverseTransform) {
    this(
        color1,
        color2,
        size,
        0.1,
        0.7,
        0.8,
        50.0,
        0xFFFFFFFF,
        0.0,
        1.0,
        0.0,
        objectInverseTransform);
  }

  public CheckerboardMaterial(
      int color1,
      int color2,
      double size,
      double ambientCoeff,
      double diffuseCoeff,
      double specularCoeff,
      double shininess,
      int specularColor,
      double reflectivity,
      double ior,
      double transparency,
      Matrix4 objectInverseTransform) {
    this.color1 = color1;
    this.color2 = color2;
    this.size = size;
    this.ambientCoeff = ambientCoeff;
    this.diffuseCoeff = diffuseCoeff;
    this.specularCoeff = specularCoeff;
    this.shininess = shininess;
    this.specularColor = specularColor;
    this.reflectivity = reflectivity;
    this.ior = ior;
    this.transparency = transparency;

    this.specR = (specularColor >> 16) & 0xFF;
    this.specG = (specularColor >> 8) & 0xFF;
    this.specB = specularColor & 0xFF;

    if (objectInverseTransform != null) {
      this.objectInverseTransform = objectInverseTransform;
      this.inverseTransposeTransform = objectInverseTransform.inverseTransposeForNormal();
    } else {
      this.objectInverseTransform = Matrix4.identity();
      this.inverseTransposeTransform = Matrix4.identity();
    }
  }

  @Override
  public void setObjectTransform(Matrix4 tm) {
    if (tm == null) tm = Matrix4.identity();
    this.objectInverseTransform = tm;
    this.inverseTransposeTransform = tm.inverseTransposeForNormal();
  }

  @Override
  public int getColorAt(Point3 worldPoint, Vector3 worldNormal, Light light, Point3 viewerPos) {
    // 1. Get checkerboard pattern color - ZERO ALLOCATION
    int patternColor = calculatePatternColor(worldPoint, worldNormal);
    int pR = (patternColor >> 16) & 0xFF;
    int pG = (patternColor >> 8) & 0xFF;
    int pB = patternColor & 0xFF;

    // 2. Get light properties - ZERO ALLOCATION
    LightProperties.getLightProperties(light, worldPoint, tempLightProps);
    tempLightDir.set(tempLightProps.direction);

    // 3. Light color
    int lightColor = tempLightProps.color;
    int lightR = (lightColor >> 16) & 0xFF;
    int lightG = (lightColor >> 8) & 0xFF;
    int lightB = lightColor & 0xFF;

    // 4. Ambient-only path
    if (light instanceof MuratAmbientLight) {
      double intensity = tempLightProps.intensity;
      int r = (int) Math.min(255, pR * ambientCoeff * intensity * lightR / 255.0);
      int g = (int) Math.min(255, pG * ambientCoeff * intensity * lightG / 255.0);
      int b = (int) Math.min(255, pB * ambientCoeff * intensity * lightB / 255.0);
      r = r < 0 ? 0 : r;
      g = g < 0 ? 0 : g;
      b = b < 0 ? 0 : b;
      return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    // 5. View direction - ZERO ALLOCATION
    tempViewerPoint.set(viewerPos);
    tempViewDir.set(tempViewerPoint);
    tempViewDir.subtract(worldPoint);
    tempViewDir.normalize();

    // 6. Diffuse
    double NdotL = Math.max(0.0, worldNormal.dot(tempLightDir));
    double diffuseFactor = ambientCoeff + diffuseCoeff * NdotL * tempLightProps.intensity;

    // 7. Reflection direction - ZERO ALLOCATION
    tempReflectDir.set(tempLightDir);
    tempReflectDir.negate();
    tempReflectDir.reflect(worldNormal, tempReflectDir);

    // 8. Specular
    double RdotV = Math.max(0.0, tempReflectDir.dot(tempViewDir));
    double specFactor = 0;

    // 9. Combine
    int r = (int) Math.min(255, pR * diffuseFactor * lightR / 255.0 + specR * specFactor);
    int g = (int) Math.min(255, pG * diffuseFactor * lightG / 255.0 + specG * specFactor);
    int b = (int) Math.min(255, pB * diffuseFactor * lightB / 255.0 + specB * specFactor);

    r = r < 0 ? 0 : r;
    g = g < 0 ? 0 : g;
    b = b < 0 ? 0 : b;

    return 0xFF000000 | (r << 16) | (g << 8) | b;
  }

  private int calculatePatternColor(Point3 worldPoint, Vector3 worldNormal) {
    // Transform to local space - ZERO ALLOCATION
    objectInverseTransform.transformPoint(worldPoint, tempLocalPoint);
    inverseTransposeTransform.transformDirection(worldNormal, tempTransformedNormal);
    tempLocalNormal.set(tempTransformedNormal);
    tempLocalNormal.normalize();

    double absNx = Math.abs(tempLocalNormal.x);
    double absNy = Math.abs(tempLocalNormal.y);
    double absNz = Math.abs(tempLocalNormal.z);

    double u, v;
    if (absNx > absNy && absNx > absNz) {
      u = tempLocalPoint.y * size + 1e-10;
      v = tempLocalPoint.z * size + 1e-10;
    } else if (absNy > absNx && absNy > absNz) {
      u = tempLocalPoint.x * size + 1e-10;
      v = tempLocalPoint.z * size + 1e-10;
    } else {
      u = tempLocalPoint.x * size + 1e-10;
      v = tempLocalPoint.y * size + 1e-10;
    }

    return ((int) Math.floor(u) + (int) Math.floor(v)) % 2 == 0 ? color1 : color2;
  }

  public void setColor1(int color) {
    this.color1 = color;
  }

  public void setColor2(int color) {
    this.color2 = color;
  }

  public int getColor1() {
    return color1;
  }

  public int getColor2() {
    return color2;
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
    sb.append("CheckerboardMaterial checkerboardmaterial {\n");
    sb.append("        color1 = ").append(ColorUtil.toColorString(color1)).append(";\n");
    sb.append("        color2 = ").append(ColorUtil.toColorString(color2)).append(";\n");
    sb.append("        size = ").append(size).append(";\n");
    sb.append("        ambientCoeff = ").append(ambientCoeff).append(";\n");
    sb.append("        diffuseCoeff = ").append(diffuseCoeff).append(";\n");
    sb.append("        specularCoeff = ").append(specularCoeff).append(";\n");
    sb.append("        shininess = ").append(shininess).append(";\n");
    sb.append("        specularColor = ")
        .append(ColorUtil.toColorString(specularColor))
        .append(";\n");
    sb.append("        reflectivity = ").append(reflectivity).append(";\n");
    sb.append("        ior = ").append(ior).append(";\n");
    sb.append("        transparency = ").append(transparency).append(";\n");
    sb.append("    }");
    return sb.toString();
  }
}
