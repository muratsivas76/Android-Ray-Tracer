package net.murat.material;

import net.murat.light.Light;
import net.murat.light.LightProperties;
import net.murat.math.*;
import net.murat.util.ColorUtil;

/**
 * TranslucentMaterial - a frosted glass / translucent material with subsurface-like scattering.
 *
 * <p>This material simulates translucent surfaces where light enters the surface, scatters
 * internally, and exits at a different point. Unlike pure transparency (which preserves direction),
 * translucent materials blur and diffuse transmitted light.
 *
 * <p>This implementation is 100% garbage-free during render() execution. All temporary objects are
 * pre-allocated as instance fields and reused.
 *
 * @author Murat
 * @version 2.0
 */
public class TranslucentMaterial implements Material {

  // =========================================================================
  // MATERIAL PROPERTIES
  // =========================================================================

  private int baseColor;
  private int attenuation;
  private double opacity;
  private double roughness;
  private double indexOfRefraction;
  private double reflectivity;
  private double transparency;
  private double shininess = 32.0;
  private double specularStrength = 0.5;

  // =========================================================================
  // TRANSFORM CACHE
  // =========================================================================

  private Matrix4 objectTransform;
  private Matrix4 inverseObjectTransform;
  private Matrix4 inverseTransposeTransform;

  // =========================================================================
  // COLOR COMPONENTS
  // =========================================================================

  private int baseR;
  private int baseG;
  private int baseB;
  private int attenR;
  private int attenG;
  private int attenB;

  // =========================================================================
  // GC-FRIENDLY SCRATCH POOL
  // =========================================================================

  private final Vector3 tempNormal = new Vector3();
  private final Vector3 tempViewDir = new Vector3();
  private final Vector3 tempLightDir = new Vector3();
  private final Vector3 tempHalfVec = new Vector3();
  private final LightProperties tempLightProps = new LightProperties();

  // =========================================================================
  // CONSTRUCTORS
  // =========================================================================

  public TranslucentMaterial() {
    this(0xFFFFB038, 0xFFFFFF00, 0.3, 0.2, 1.52);
  }

  public TranslucentMaterial(
      int baseColor, int attenuation, double opacity, double roughness, double indexOfRefraction) {
    this.baseColor = baseColor;
    this.baseR = (baseColor >> 16) & 0xFF;
    this.baseG = (baseColor >> 8) & 0xFF;
    this.baseB = baseColor & 0xFF;

    this.attenuation = attenuation;
    this.attenR = (attenuation >> 16) & 0xFF;
    this.attenG = (attenuation >> 8) & 0xFF;
    this.attenB = attenuation & 0xFF;

    this.opacity = opacity;
    this.roughness = roughness;
    this.indexOfRefraction = indexOfRefraction;
    this.reflectivity = 0.2;
    this.transparency = 1.0 - opacity;

    this.objectTransform = new Matrix4();
    this.inverseObjectTransform = new Matrix4();
    this.inverseTransposeTransform = new Matrix4();
  }

  public TranslucentMaterial(
      int baseColor,
      int attenuation,
      double opacity,
      double roughness,
      double indexOfRefraction,
      double reflectivity,
      double shininess,
      double specularStrength) {
    this(baseColor, attenuation, opacity, roughness, indexOfRefraction);
    this.reflectivity = reflectivity;
    this.shininess = shininess;
    this.specularStrength = specularStrength;
  }

  // =========================================================================
  // MATERIAL INTERFACE METHODS
  // =========================================================================

  @Override
  public int getColorAt(Point3 point, Vector3 normal, Light light, Point3 viewerPoint) {
    tempNormal.set(normal).normalize();

    // Get light properties - ZERO ALLOCATION
    LightProperties.getLightProperties(light, point, tempLightProps);
    tempLightDir.set(tempLightProps.direction);

    int lightColor = tempLightProps.color;
    int lightR = (lightColor >> 16) & 0xFF;
    int lightG = (lightColor >> 8) & 0xFF;
    int lightB = lightColor & 0xFF;

    // 1. Diffuse term (Lambertian)
    double diffuse = Math.max(0.0, tempNormal.dot(tempLightDir));

    // 2. View direction
    tempViewDir.set(viewerPoint).subtract(point).normalize();

    // 3. Fresnel rim effect - STRONGER for translucent materials
    double nv = Math.max(0.0, tempNormal.dot(tempViewDir));
    double rim = 1.0 - nv;
    rim = rim * rim * (3.0 - 2.0 * rim);
    rim = Math.pow(rim, 1.5); // Boost rim effect

    // 4. Specular term (Blinn-Phong)
    tempHalfVec.set(tempLightDir).add(tempViewDir);
    double halfLen = tempHalfVec.length();
    if (halfLen > Ray.EPSILON) {
      tempHalfVec.scale(1.0 / halfLen);
    }
    double specAngle = Math.max(0.0, tempNormal.dot(tempHalfVec));
    double specular = Math.pow(specAngle, shininess);

    // 5. Subsurface scattering - MUCH STRONGER
    // Light penetrates and scatters through the material
    double scatter = diffuse * (0.3 + 0.7 * (1.0 - roughness));
    scatter = scatter + 0.4 * rim; // Rim light bleeds through

    // 6. Transmission - light passing through (translucent effect)
    // Less opacity = more light passes through
    double transmission = 1.0 - opacity;
    double transmitStrength = transmission * 0.8; // Strong transmission

    // 7. Combine lighting - MUCH BRIGHTER
    double ambient = 0.1;
    double intensity = tempLightProps.intensity;

    // Strong diffuse + strong scatter + rim + specular
    double totalLight =
        ambient
            + intensity * (0.8 * diffuse + 0.6 * scatter + 0.5 * rim)
            + intensity * specularStrength * specular;

    // 8. Apply color with strong attenuation
    // Base color dominates, attenuation adds color to transmitted light
    double attenWeight = transmission * 0.5;

    int r =
        (int)
            ((baseR * totalLight * lightR / 255.0) * (1.0 - attenWeight)
                + (attenR * totalLight * lightR / 255.0) * attenWeight);
    int g =
        (int)
            ((baseG * totalLight * lightG / 255.0) * (1.0 - attenWeight)
                + (attenG * totalLight * lightG / 255.0) * attenWeight);
    int b =
        (int)
            ((baseB * totalLight * lightB / 255.0) * (1.0 - attenWeight)
                + (attenB * totalLight * lightB / 255.0) * attenWeight);

    r = r > 255 ? 255 : (r < 0 ? 0 : r);
    g = g > 255 ? 255 : (g < 0 ? 0 : g);
    b = b > 255 ? 255 : (b < 0 ? 0 : b);

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
    return indexOfRefraction;
  }

  @Override
  public void setObjectTransform(Matrix4 tm) {
    if (tm == null) {
      this.objectTransform = new Matrix4();
    } else {
      this.objectTransform = tm;
    }
    this.inverseObjectTransform = this.objectTransform.inverse();
    this.inverseTransposeTransform = this.inverseObjectTransform.inverseTransposeForNormal();
  }

  // =========================================================================
  // GETTERS AND SETTERS
  // =========================================================================

  public int getBaseColor() {
    return baseColor;
  }

  public void setBaseColor(int baseColor) {
    this.baseColor = baseColor;
    this.baseR = (baseColor >> 16) & 0xFF;
    this.baseG = (baseColor >> 8) & 0xFF;
    this.baseB = baseColor & 0xFF;
  }

  public int getAttenuation() {
    return attenuation;
  }

  public void setAttenuation(int attenuation) {
    this.attenuation = attenuation;
    this.attenR = (attenuation >> 16) & 0xFF;
    this.attenG = (attenuation >> 8) & 0xFF;
    this.attenB = attenuation & 0xFF;
  }

  public double getOpacity() {
    return opacity;
  }

  public void setOpacity(double opacity) {
    this.opacity = opacity;
    this.transparency = 1.0 - opacity;
  }

  public double getRoughness() {
    return roughness;
  }

  public void setRoughness(double roughness) {
    this.roughness = roughness;
  }

  public void setReflectivity(double reflectivity) {
    this.reflectivity = reflectivity;
  }

  public double getShininess() {
    return shininess;
  }

  public void setShininess(double shininess) {
    this.shininess = shininess;
  }

  public double getSpecularStrength() {
    return specularStrength;
  }

  public void setSpecularStrength(double specularStrength) {
    this.specularStrength = specularStrength;
  }

  // =========================================================================
  // TO STRING
  // =========================================================================

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("TranslucentMaterial translucentmaterial {\n");
    sb.append("        baseColor = ").append(ColorUtil.toColorString(baseColor)).append(";\n");
    sb.append("        attenuation = ").append(ColorUtil.toColorString(attenuation)).append(";\n");
    sb.append("        opacity = ").append(opacity).append(";\n");
    sb.append("        roughness = ").append(roughness).append(";\n");
    sb.append("        ior = ").append(indexOfRefraction).append(";\n");
    sb.append("        reflectivity = ").append(reflectivity).append(";\n");
    sb.append("        transparency = ").append(transparency).append(";\n");
    sb.append("        shininess = ").append(shininess).append(";\n");
    sb.append("        specularStrength = ").append(specularStrength).append(";\n");
    sb.append("    }");
    return sb.toString();
  }
}
