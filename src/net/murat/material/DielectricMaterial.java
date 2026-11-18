package net.murat.material;

import net.murat.light.Light;
import net.murat.light.LightProperties;
import net.murat.math.*;
import net.murat.util.ColorUtil;

/**
 * Represents a physically-inspired dielectric (glass-like) material. Fully self-contained shading
 * model with specular color control.
 *
 * <p>Alpha-channel support: Both baseColor and specularColor are expected in 0xAARRGGBB format. The
 * alpha channel of each color is now extracted and used as an independent opacity/strength
 * multiplier for that color's contribution to the final shaded result: - baseColor's alpha ->
 * scales the non-specular (base) contribution - specularColor's alpha -> scales the specular
 * highlight contribution An alpha of 0xFF means "full contribution" (same behavior as before), an
 * alpha of 0x00 means "no contribution from this color at all". This does NOT affect the final
 * pixel's own alpha channel, which is still forced to 0xFF (fully opaque) since surface
 * transparency is handled elsewhere in the ray tracer's recursive refraction logic.
 */
public class DielectricMaterial implements Material {

  private int baseColor;
  private int specularColor;
  private double transparency;
  private double reflectivity;
  private double ior;

  // ===== TRANSFORM MATRICES =====
  private Matrix4 objectTransform;
  private Matrix4 inverseObjectTransform;
  private Matrix4 inverseTransposeTransform;
  private boolean hasTransform = false;

  private int baseR, baseG, baseB, baseA;
  private int specR, specG, specB, specA;

  // Normalized [0.0, 1.0] alpha factors, cached to avoid repeated division in getColorAt().
  private double baseAlphaFactor;
  private double specAlphaFactor;

  private double shininess = 300.0;
  private double specularStrength = 3.0;

  // ===== GC-FRIENDLY TEMP OBJECTS =====
  private final Vector3 tempNormal = new Vector3();
  private final Vector3 tempViewDir = new Vector3();
  private final Vector3 tempLightDir = new Vector3();
  private final Vector3 tempHalfVec = new Vector3();
  private final Point3 tempPoint = new Point3();
  private final LightProperties tempLightProps = new LightProperties();

  /**
   * Default constructor. Produces a generic clear glass-like dielectric, fully opaque base and
   * specular colors (alpha = 0xFF for both).
   */
  public DielectricMaterial() {
    this(0xFFFFFFFF, 0xFFFFFFFF, 0.5, 0.25, 1.52, 300.0, 3.0);
  }

  /**
   * Constructs a DielectricMaterial with explicit optical properties.
   *
   * @param baseColor the base tint color (0xAARRGGBB)
   * @param specularColor the specular highlight color (0xAARRGGBB)
   * @param transparency the transmission weight in [0,1]
   * @param reflectivity the reflection weight in [0,1]
   * @param ior the index of refraction of the medium
   * @param shininess specular shininess exponent
   * @param specularStrength specular highlight strength
   */
  public DielectricMaterial(
      int baseColor,
      int specularColor,
      double transparency,
      double reflectivity,
      double ior,
      double shininess,
      double specularStrength) {
    this.baseColor = baseColor;
    this.specularColor = specularColor;
    this.transparency = transparency;
    this.reflectivity = reflectivity;
    this.ior = ior;
    this.shininess = shininess;
    this.specularStrength = specularStrength;

    setBaseColor(baseColor);
    setSpecularColor(specularColor);

    this.objectTransform = new Matrix4();
    this.inverseObjectTransform = new Matrix4();
    this.inverseTransposeTransform = new Matrix4();
  }

  /** Simplified constructor (specularColor defaults to opaque white, alpha = 0xFF). */
  public DielectricMaterial(
      int baseColor,
      double transparency,
      double reflectivity,
      double ior,
      double shininess,
      double specularStrength) {
    this(baseColor, 0xFFFFFFFF, transparency, reflectivity, ior, shininess, specularStrength);
  }

  @Override
  public int getColorAt(Point3 point, Vector3 normal, Light light, Point3 viewerPoint) {
    // ===== TRANSFORM =====
    Point3 worldPoint = tempPoint;
    Vector3 worldNormal = tempNormal;

    if (hasTransform) {
      worldPoint.set(point);
      objectTransform.transformPoint(point, worldPoint);

      worldNormal.set(normal);
      objectTransform.transformNormal(normal, worldNormal);
      worldNormal.normalize();
    } else {
      worldPoint.set(point);
      worldNormal.set(normal).normalize();
    }

    // Light properties (world space)
    LightProperties.getLightProperties(light, worldPoint, tempLightProps);
    tempLightDir.set(tempLightProps.direction).normalize();

    int lightColor = tempLightProps.color;
    int lightR = (lightColor >> 16) & 0xFF;
    int lightG = (lightColor >> 8) & 0xFF;
    int lightB = lightColor & 0xFF;

    // View direction (world space)
    tempViewDir.set(viewerPoint).subtract(worldPoint).normalize();

    // ===== SPECULAR (with specular color and specular alpha) =====
    tempHalfVec.set(tempLightDir).add(tempViewDir).normalize();
    double specAngle = Math.max(0.0, worldNormal.dot(tempHalfVec));
    double specular = Math.pow(specAngle, shininess) * specularStrength * tempLightProps.intensity;

    double specScale = 180.0;
    // Multiply by specular color channels AND by the specular color's alpha factor,
    // so a low-alpha specularColor (e.g. 0x44FFFFFF) produces a proportionally
    // weaker highlight even though the RGB channels are unchanged.
    double specR_f = specScale * specular * lightR / 255.0 * specR / 255.0 * specAlphaFactor;
    double specG_f = specScale * specular * lightG / 255.0 * specG / 255.0 * specAlphaFactor;
    double specB_f = specScale * specular * lightB / 255.0 * specB / 255.0 * specAlphaFactor;

    // ===== FRESNEL / RIM (smoothstep-based, avoids sharp ring artifacts) =====
    double nv = Math.max(0.0, worldNormal.dot(tempViewDir));
    double fresnel = Vector3.calculateFresnel(tempViewDir, worldNormal, ior);
    // double rt = 1.0 - nv;
    // double rim = rt * rt * (3.0 - 2.0 * rt);
    double rt = 1.0 - nv;
    double rim = Math.pow(rt, 1.5) * (3.0 - 2.0 * rt);
    double internalGlow = Math.pow(nv, 2.0);

    // ===== BASE COLOR =====
    double ambient = 0.05;
    double totalLight =
        ambient
            + (transparency * internalGlow * 0.05)
            + (rim * reflectivity * 0.2)
            + (fresnel * 0.1);

    // Base color contribution is scaled by the base color's own alpha factor,
    // so a low-alpha baseColor (e.g. 0x44FFFFFF) fades the tint toward "no
    // base contribution" while specular highlights remain independently controlled.
    int r = (int) (baseR * totalLight * lightR / 255.0 * baseAlphaFactor + specR_f);
    int g = (int) (baseG * totalLight * lightG / 255.0 * baseAlphaFactor + specG_f);
    int b = (int) (baseB * totalLight * lightB / 255.0 * baseAlphaFactor + specB_f);

    // getColorAt
    // double ditherSeed = (worldPoint.x * 12.9898 + worldPoint.y * 78.233 + worldPoint.z * 37.719);
    // double dither = (Math.sin(ditherSeed) * 43758.5453);
    // dither = dither - Math.floor(dither); // [0,1) aralığına indir
    // dither = (dither - 0.5) * (1.0 / 255.0); // yarım-bit genişliğinde gürültü

    // int r = (int) Math.round((baseR * totalLight * lightR / 255.0 * baseAlphaFactor + specR_f) +
    // dither * 255.0);
    // int g = (int) Math.round((baseG * totalLight * lightG / 255.0 * baseAlphaFactor + specG_f) +
    // dither * 255.0);
    // int b = (int) Math.round((baseB * totalLight * lightB / 255.0 * baseAlphaFactor + specB_f) +
    // dither * 255.0);

    r = Math.min(255, Math.max(0, r));
    g = Math.min(255, Math.max(0, g));
    b = Math.min(255, Math.max(0, b));

    // Output pixel alpha stays fully opaque; surface-level transparency is
    // handled separately via the transparency/refraction recursion in the tracer.
    return (0xFF000000 | (r << 16) | (g << 8) | b);
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
    if (tm == null) {
      this.objectTransform = new Matrix4();
      this.hasTransform = false;
    } else {
      this.objectTransform = tm;
      this.hasTransform = true;
    }
    this.inverseObjectTransform = this.objectTransform.inverse();
    this.inverseTransposeTransform = this.inverseObjectTransform.inverseTransposeForNormal();
  }

  // ===== SETTERS/GETTERS =====

  public int getBaseColor() {
    return baseColor;
  }

  /**
   * Sets the base tint color, expected in 0xAARRGGBB format. Extracts and caches RGB channels plus
   * the alpha-derived opacity factor.
   */
  public void setBaseColor(int baseColor) {
    this.baseColor = baseColor;
    this.baseA = (baseColor >>> 24) & 0xFF;
    this.baseR = (baseColor >> 16) & 0xFF;
    this.baseG = (baseColor >> 8) & 0xFF;
    this.baseB = baseColor & 0xFF;
    this.baseAlphaFactor = baseA / 255.0;
  }

  public int getSpecularColor() {
    return specularColor;
  }

  /**
   * Sets the specular highlight color, expected in 0xAARRGGBB format. Extracts and caches RGB
   * channels plus the alpha-derived strength factor.
   */
  public void setSpecularColor(int specularColor) {
    this.specularColor = specularColor;
    this.specA = (specularColor >>> 24) & 0xFF;
    this.specR = (specularColor >> 16) & 0xFF;
    this.specG = (specularColor >> 8) & 0xFF;
    this.specB = specularColor & 0xFF;
    this.specAlphaFactor = specA / 255.0;
  }

  /** Returns the base color's alpha channel value in [0, 255]. */
  public int getBaseColorAlpha() {
    return baseA;
  }

  /** Returns the specular color's alpha channel value in [0, 255]. */
  public int getSpecularColorAlpha() {
    return specA;
  }

  public void setTransparency(double transparency) {
    this.transparency = transparency;
  }

  public void setReflectivity(double reflectivity) {
    this.reflectivity = reflectivity;
  }

  public void setIndexOfRefraction(double ior) {
    this.ior = ior;
  }

  public void setShininess(double shininess) {
    this.shininess = shininess;
  }

  public double getShininess() {
    return shininess;
  }

  public void setSpecularStrength(double specularStrength) {
    this.specularStrength = specularStrength;
  }

  public double getSpecularStrength() {
    return specularStrength;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("DielectricMaterial dielectricmaterial {\n");
    sb.append("        baseColor = ").append(ColorUtil.toColorString(baseColor)).append(";\n");
    sb.append("        specularColor = ")
        .append(ColorUtil.toColorString(specularColor))
        .append(";\n");
    sb.append("        transparency = ").append(transparency).append(";\n");
    sb.append("        reflectivity = ").append(reflectivity).append(";\n");
    sb.append("        ior = ").append(ior).append(";\n");
    sb.append("        shininess = ").append(shininess).append(";\n");
    sb.append("        specularStrength = ").append(specularStrength).append(";\n");
    sb.append("    }");
    return sb.toString();
  }
}
