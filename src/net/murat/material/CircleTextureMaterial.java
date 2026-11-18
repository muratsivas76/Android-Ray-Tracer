package net.murat.material;

import net.murat.light.*;
import net.murat.math.Matrix4;
import net.murat.math.Point3;
import net.murat.math.Ray;
import net.murat.math.Vector3;
import net.murat.util.ColorUtil;

/**
 * A material that creates a repeating circular (dot) pattern on a surface. It applies a Phong-like
 * lighting model (Ambient + Diffuse + Specular) to the pattern. This material now fully implements
 * the extended Material interface.
 */
public class CircleTextureMaterial implements Material {

  private final int solidColorPacked;
  private final int patternColorPacked;
  private final double patternSize;
  private final double circleRadius;
  private Matrix4 objectInverseTransform;
  private Matrix4 inverseTransposeTransform;

  // Phong lighting model parameters
  private final double ambientCoefficient;
  private final double diffuseCoefficient;
  private final double specularCoefficient;
  private final double shininess;
  private final double reflectivity;
  private final double ior;
  private final double transparency;

  private final int specularColorPacked = ColorUtil.WHITE;

  // ===== GC DOSTU: PRE-ALLOCATED TEMP OBJECTS =====
  private final Point3 tempLocalPoint = new Point3();
  private final Vector3 tempLocalNormal = new Vector3();
  private final Vector3 tempTransformedNormal = new Vector3();
  private final Vector3 tempViewDir = new Vector3();
  private final Vector3 tempLightDir = new Vector3();
  private final Vector3 tempReflectDir = new Vector3();
  private final Point3 tempViewerPoint = new Point3();
  private final Vector3 tempResult = new Vector3();
  private final LightProperties tempLightProps = new LightProperties();

  /** Full constructor for CircleTextureMaterial. */
  public CircleTextureMaterial(
      int solidColor,
      int patternColor,
      double patternSize,
      double ambientCoefficient,
      double diffuseCoefficient,
      double specularCoefficient,
      double shininess,
      double reflectivity,
      double ior,
      double transparency,
      Matrix4 objectInverseTransform) {
    this.solidColorPacked = solidColor;
    this.patternColorPacked = patternColor;
    this.patternSize = patternSize;
    this.circleRadius = 0.35;
    this.objectInverseTransform =
        objectInverseTransform != null ? objectInverseTransform : new Matrix4();
    this.inverseTransposeTransform = this.objectInverseTransform.inverseTransposeForNormal();

    this.ambientCoefficient = ambientCoefficient;
    this.diffuseCoefficient = diffuseCoefficient;
    this.specularCoefficient = specularCoefficient;
    this.shininess = shininess;
    this.reflectivity = reflectivity;
    this.ior = ior;
    this.transparency = transparency;
  }

  /** Simplified constructor with default Phong-like coefficients. */
  public CircleTextureMaterial(
      int solidColor, int patternColor, double patternSize, Matrix4 objectInverseTransform) {
    this(
        solidColor,
        patternColor,
        patternSize,
        0.1,
        0.8,
        0.1,
        10.0,
        0.0,
        1.0,
        0.0,
        objectInverseTransform);
  }

  @Override
  public void setObjectTransform(Matrix4 tm) {
    if (tm == null) tm = new Matrix4();
    this.objectInverseTransform = tm;
    this.inverseTransposeTransform = tm.inverseTransposeForNormal();
  }

  /** Calculates the base pattern color (solid or circle) at a given local point. */
  private int getPatternColor(Point3 localPoint, Vector3 localNormal) {
    double u, v;
    double absNx = Math.abs(localNormal.x);
    double absNy = Math.abs(localNormal.y);
    double absNz = Math.abs(localNormal.z);

    if (absNx > absNy && absNx > absNz) {
      u = localPoint.y * this.patternSize;
      v = localPoint.z * this.patternSize;
    } else if (absNy > absNx && absNy > absNz) {
      u = localPoint.x * this.patternSize;
      v = localPoint.z * this.patternSize;
    } else {
      u = localPoint.x * this.patternSize;
      v = localPoint.y * this.patternSize;
    }

    double normalizedU = u - Math.floor(u + Ray.EPSILON);
    double normalizedV = v - Math.floor(v + Ray.EPSILON);

    double dx = normalizedU - 0.5;
    double dy = normalizedV - 0.5;
    double distance = Math.sqrt(dx * dx + dy * dy);

    if (distance <= circleRadius) {
      return patternColorPacked;
    } else {
      return solidColorPacked;
    }
  }

  @Override
  public int getColorAt(Point3 worldPoint, Vector3 worldNormal, Light light, Point3 viewerPos) {
    if (objectInverseTransform == null) {
      return ColorUtil.BLACK;
    }

    objectInverseTransform.transformPoint(worldPoint, tempLocalPoint);
    inverseTransposeTransform.transformDirection(worldNormal, tempTransformedNormal);
    tempLocalNormal.set(tempTransformedNormal);
    tempLocalNormal.normalize();

    int patternBaseColor = getPatternColor(tempLocalPoint, tempLocalNormal);

    LightProperties.getLightProperties(light, worldPoint, tempLightProps);
    tempLightDir.set(tempLightProps.direction);

    int lightColor = tempLightProps.color;
    double intensity = tempLightProps.intensity;

    int patternR = (patternBaseColor >> 16) & 0xFF;
    int patternG = (patternBaseColor >> 8) & 0xFF;
    int patternB = patternBaseColor & 0xFF;

    int lightR = (lightColor >> 16) & 0xFF;
    int lightG = (lightColor >> 8) & 0xFF;
    int lightB = lightColor & 0xFF;

    // Ambient - intensity EKLENDI
    double rAmbient = (patternR / 255.0) * ambientCoefficient * (lightR / 255.0) * intensity;
    double gAmbient = (patternG / 255.0) * ambientCoefficient * (lightG / 255.0) * intensity;
    double bAmbient = (patternB / 255.0) * ambientCoefficient * (lightB / 255.0) * intensity;

    if (light instanceof MuratAmbientLight) {
      int r = (int) Math.min(255, rAmbient * 255);
      int g = (int) Math.min(255, gAmbient * 255);
      int b = (int) Math.min(255, bAmbient * 255);
      return (0xFF000000 | (r << 16) | (g << 8) | b);
    }

    double NdotL = Math.max(0, tempLocalNormal.dot(tempLightDir));

    // Diffuse - intensity EKLENDI
    double rDiffuse =
        (patternR / 255.0) * diffuseCoefficient * (lightR / 255.0) * NdotL * intensity;
    double gDiffuse =
        (patternG / 255.0) * diffuseCoefficient * (lightG / 255.0) * NdotL * intensity;
    double bDiffuse =
        (patternB / 255.0) * diffuseCoefficient * (lightB / 255.0) * NdotL * intensity;

    tempViewerPoint.set(viewerPos);
    tempViewDir.set(tempViewerPoint);
    tempViewDir.subtract(worldPoint);
    tempViewDir.normalize();

    tempReflectDir.set(tempLightDir);
    tempReflectDir.negate();
    tempReflectDir.reflect(tempLocalNormal, tempReflectDir);

    double RdotV = Math.max(0, tempReflectDir.dot(tempViewDir));
    double specFactor = Math.pow(RdotV, shininess);

    int specR = (specularColorPacked >> 16) & 0xFF;
    int specG = (specularColorPacked >> 8) & 0xFF;
    int specB = specularColorPacked & 0xFF;

    // Specular - intensity EKLENDI
    double rSpecular =
        (specR / 255.0) * specularCoefficient * (lightR / 255.0) * specFactor * intensity;
    double gSpecular =
        (specG / 255.0) * specularCoefficient * (lightG / 255.0) * specFactor * intensity;
    double bSpecular =
        (specB / 255.0) * specularCoefficient * (lightB / 255.0) * specFactor * intensity;

    // 0-255 arasına çevir!
    int r = (int) Math.min(255, (rAmbient + rDiffuse + rSpecular) * 255);
    int g = (int) Math.min(255, (gAmbient + gDiffuse + gSpecular) * 255);
    int b = (int) Math.min(255, (bAmbient + bDiffuse + bSpecular) * 255);

    return (0xFF000000 | (r << 16) | (g << 8) | b);
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

  private double clamp01(double val) {
    return Math.min(1.0, Math.max(0.0, val));
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("CircleTextureMaterial circletexturematerial {\n");
    sb.append("        solidColor = ")
        .append(ColorUtil.toColorString(solidColorPacked))
        .append(";\n");
    sb.append("        patternColor = ")
        .append(ColorUtil.toColorString(patternColorPacked))
        .append(";\n");
    sb.append("        patternSize = ").append(patternSize).append(";\n");
    sb.append("        ambientCoefficient = ").append(ambientCoefficient).append(";\n");
    sb.append("        diffuseCoefficient = ").append(diffuseCoefficient).append(";\n");
    sb.append("        specularCoefficient = ").append(specularCoefficient).append(";\n");
    sb.append("        shininess = ").append(shininess).append(";\n");
    sb.append("        reflectivity = ").append(reflectivity).append(";\n");
    sb.append("        ior = ").append(ior).append(";\n");
    sb.append("        transparency = ").append(transparency).append(";\n");
    sb.append("    }");
    return sb.toString();
  }
}
