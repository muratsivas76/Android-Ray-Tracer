package net.murat.material;

import net.murat.light.Light;
import net.murat.light.LightProperties;
import net.murat.math.*;
import net.murat.util.ColorUtil;

/**
 * PhongMaterial implements the Blinn-Phong reflection model with ambient, diffuse, and specular
 * components. Uses Blinn-Phong specular for sharper, more realistic highlights.
 *
 * <p>This material is fully GC-friendly with pre-allocated temp objects and uses double precision
 * color math matching Elena quality.
 */
public class PhongMaterial implements Material {

  // Packed ARGB colors (A-R-G-B)
  private final int diffuseColorPacked;
  private final int specularColorPacked;

  // Phong parameters
  private final double shininess;
  private final double ambientCoefficient;
  private final double diffuseCoefficient;
  private final double specularCoefficient;

  // Material properties for ray tracing
  private final double reflectivity;
  private final double ior;
  private final double transparency;

  // Decomposed ARGB components [0-255]
  private final int diffA, diffR, diffG, diffB;
  private final int specA, specR, specG, specB;

  // ===== GC FRIENDLY: PRE-ALLOCATED TEMP OBJECTS =====
  private final Vector3 tempViewDir = new Vector3();
  private final Vector3 tempLightDir = new Vector3();
  private final Vector3 tempHalfVec = new Vector3();
  private final Point3 tempViewerPoint = new Point3();
  private final LightProperties tempLightProps = new LightProperties();

  /**
   * Full constructor for PhongMaterial.
   *
   * @param diffuseColor Packed ARGB diffuse color (0xAARRGGBB)
   * @param specularColor Packed ARGB specular color (0xAARRGGBB)
   * @param shininess Shininess exponent for specular (higher = smaller highlight)
   * @param ambientCoefficient Ambient coefficient (0.0–1.0)
   * @param diffuseCoefficient Diffuse coefficient (0.0–1.0)
   * @param specularCoefficient Specular coefficient (0.0–1.0)
   * @param reflectivity Reflectivity (0.0–1.0)
   * @param ior Index of refraction (>= 1.0)
   * @param transparency Transparency (0.0–1.0)
   */
  public PhongMaterial(
      int diffuseColor,
      int specularColor,
      double shininess,
      double ambientCoefficient,
      double diffuseCoefficient,
      double specularCoefficient,
      double reflectivity,
      double ior,
      double transparency) {
    this.diffuseColorPacked = diffuseColor;
    this.specularColorPacked = specularColor;
    this.shininess = shininess;

    this.ambientCoefficient = clamp01(ambientCoefficient);
    this.diffuseCoefficient = clamp01(diffuseCoefficient);
    this.specularCoefficient = clamp01(specularCoefficient);
    this.reflectivity = clamp01(reflectivity);
    this.ior = Math.max(1.0, ior);
    this.transparency = clamp01(transparency);

    // Extract ARGB components from diffuse color
    this.diffA = (diffuseColor >> 24) & 0xFF;
    this.diffR = (diffuseColor >> 16) & 0xFF;
    this.diffG = (diffuseColor >> 8) & 0xFF;
    this.diffB = diffuseColor & 0xFF;

    // Extract ARGB components from specular color
    this.specA = (specularColor >> 24) & 0xFF;
    this.specR = (specularColor >> 16) & 0xFF;
    this.specG = (specularColor >> 8) & 0xFF;
    this.specB = specularColor & 0xFF;
  }

  /**
   * Simplified constructor with default parameters. White specular, shininess=32, default
   * coefficients. reflectivity=0.0, IOR=1.0, transparency=0.0
   *
   * @param diffuseColor Packed ARGB diffuse color (0xAARRGGBB)
   */
  public PhongMaterial(int diffuseColor) {
    this(diffuseColor, 0xFFFFFFFF, 32.0, 0.1, 0.7, 0.7, 0.0, 1.0, 0.0);
  }

  // ===== GETTERS =====

  public int getDiffuseColorPacked() {
    return diffuseColorPacked;
  }

  public int getSpecularColorPacked() {
    return specularColorPacked;
  }

  public double getShininess() {
    return shininess;
  }

  public double getAmbientCoefficient() {
    return ambientCoefficient;
  }

  public double getDiffuseCoefficient() {
    return diffuseCoefficient;
  }

  public double getSpecularCoefficient() {
    return specularCoefficient;
  }

  // ===== MATERIAL INTERFACE =====

  @Override
  public int getColorAt(Point3 point, Vector3 normal, Light light, Point3 viewerPos) {
    // Get light properties (GC-free)
    LightProperties.getLightProperties(light, point, tempLightProps);
    tempLightDir.set(tempLightProps.direction);

    int lightColor = tempLightProps.color;
    double intensity = tempLightProps.intensity;

    // Normalize light color to [0, 1]
    double lightR = ((lightColor >> 16) & 0xFF) / 255.0;
    double lightG = ((lightColor >> 8) & 0xFF) / 255.0;
    double lightB = (lightColor & 0xFF) / 255.0;

    // ===== AMBIENT LIGHT =====
    if (light instanceof net.murat.light.MuratAmbientLight) {
      double a = diffA / 255.0; // Kullanıcının diffuse alpha'sı
      double r = (diffR / 255.0) * ambientCoefficient * lightR * intensity;
      double g = (diffG / 255.0) * ambientCoefficient * lightG * intensity;
      double b = (diffB / 255.0) * ambientCoefficient * lightB * intensity;
      return ColorUtil.packDouble(a, r, g, b);
    }

    // ===== DIFFUSE COMPONENT =====
    double NdotL = Math.max(0, normal.dot(tempLightDir));
    double diffFactor = diffuseCoefficient * NdotL * intensity;

    double diffR_f = (diffR / 255.0) * diffFactor * lightR;
    double diffG_f = (diffG / 255.0) * diffFactor * lightG;
    double diffB_f = (diffB / 255.0) * diffFactor * lightB;

    // ===== VIEW DIRECTION =====
    tempViewerPoint.set(viewerPos);
    tempViewDir.set(tempViewerPoint);
    tempViewDir.subtract(point);
    tempViewDir.normalize();

    // ===== BLINN-PHONG SPECULAR (like AmberMaterial) =====
    tempHalfVec.set(tempLightDir);
    tempHalfVec.add(tempViewDir);
    tempHalfVec.normalize();

    double specAngle = Math.max(0.0, normal.dot(tempHalfVec));
    double specFactor = Math.pow(specAngle, shininess) * specularCoefficient * intensity;

    double specR_f = (specR / 255.0) * specFactor * lightR;
    double specG_f = (specG / 255.0) * specFactor * lightG;
    double specB_f = (specB / 255.0) * specFactor * lightB;

    // ===== AMBIENT COMPONENT =====
    double ambR_f = (diffR / 255.0) * ambientCoefficient * lightR * intensity;
    double ambG_f = (diffG / 255.0) * ambientCoefficient * lightG * intensity;
    double ambB_f = (diffB / 255.0) * ambientCoefficient * lightB * intensity;

    // ===== COMBINE ALL COMPONENTS =====
    double r = ambR_f + diffR_f + specR_f;
    double g = ambG_f + diffG_f + specG_f;
    double b = ambB_f + diffB_f + specB_f;

    // Kullanıcının diffuse alpha'sını koru
    double a = diffA / 255.0;

    return ColorUtil.packDouble(a, r, g, b);
  }

  // ===== MATERIAL PROPERTIES =====

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
  public void setObjectTransform(Matrix4 tm) {
    // No transform needed; works in world space
  }

  // ===== HELPER =====

  private double clamp01(double val) {
    return Math.min(1.0, Math.max(0.0, val));
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("PhongMaterial phongmaterial {\n");
    sb.append("        diffuseColor = ")
        .append(ColorUtil.toColorString(diffuseColorPacked))
        .append(";\n");
    sb.append("        specularColor = ")
        .append(ColorUtil.toColorString(specularColorPacked))
        .append(";\n");
    sb.append("        shininess = ").append(shininess).append(";\n");
    sb.append("        ambientCoefficient = ").append(ambientCoefficient).append(";\n");
    sb.append("        diffuseCoefficient = ").append(diffuseCoefficient).append(";\n");
    sb.append("        specularCoefficient = ").append(specularCoefficient).append(";\n");
    sb.append("        reflectivity = ").append(reflectivity).append(";\n");
    sb.append("        ior = ").append(ior).append(";\n");
    sb.append("        transparency = ").append(transparency).append(";\n");
    sb.append("    }");
    return sb.toString();
  }
}
