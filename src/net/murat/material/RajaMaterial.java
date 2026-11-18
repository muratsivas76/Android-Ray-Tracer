package net.murat.material;

import net.murat.light.Light;
import net.murat.light.LightProperties;
import net.murat.light.MuratAmbientLight;
import net.murat.math.*;
import net.murat.util.ColorUtil;

/**
 * RajaMaterial - A material inspired by the classic Raja Ray Tracer.
 *
 * <p>This material combines the best of both worlds:
 *
 * <ul>
 *   <li><b>Texture:</b> Plain (solid color) or Damier (checkerboard) patterns
 *   <li><b>Volume:</b> Beer-Lambert absorption and scattering for realistic glass
 *   <li><b>Surface:</b> Diffuse + Specular + Fresnel effects
 * </ul>
 *
 * <p>All operations are 100% garbage-free using packed ARGB integers.
 *
 * @author Murat Ray Tracer (Raja inspired)
 * @version 1.1
 * @see RajaTexture
 * @see PlainTexture
 * @see DamierTexture
 * @see Volume
 */
public class RajaMaterial implements Material {

  // =========================================================================
  // FIELDS
  // =========================================================================

  private final RajaTexture texture;
  private final double diffuse;
  private final double specular;
  private final double ior;
  private final double shininess;
  private final double roughness;
  private final double metalness;
  private final double reflectivity;
  private final double transparency;
  private final Volume volume;
  private Matrix4 objectTransform;

  private static final double AMBIENT_CONTRIBUTION = 0.15;

  // =========================================================================
  // GC-FREE SCRATCH OBJECTS
  // =========================================================================

  private final Vector3 tempNormal = new Vector3();
  private final Vector3 tempViewDir = new Vector3();
  private final Vector3 tempLightDir = new Vector3();
  private final Vector3 tempHalfVec = new Vector3();
  private final LightProperties tempLightProps = new LightProperties();

  // =========================================================================
  // CONSTRUCTORS
  // =========================================================================

  /** Full constructor with roughness and metalness. */
  public RajaMaterial(
      RajaTexture texture,
      double diffuse,
      double specular,
      double ior,
      double shininess,
      double roughness,
      double metalness,
      double reflectivity,
      double transparency,
      Volume volume) {
    this.texture = texture;
    this.diffuse = ColorUtil.clampDouble(diffuse, 0.0, 1.0);
    this.specular = ColorUtil.clampDouble(specular, 0.0, 1.0);
    this.ior = ColorUtil.clampDouble(ior, 0.0, 10.0);
    this.shininess = Math.max(1.0, shininess);
    this.roughness = ColorUtil.clampDouble(roughness, 0.0, 1.0);
    this.metalness = ColorUtil.clampDouble(metalness, 0.0, 1.0);
    this.reflectivity = ColorUtil.clampDouble(reflectivity, 0.0, 1.0);
    this.transparency = ColorUtil.clampDouble(transparency, 0.0, 1.0);
    this.volume = volume;
    this.objectTransform = new Matrix4();
  }

  /** v1.0 compatible constructor (roughness=0, metalness=0). */
  public RajaMaterial(
      RajaTexture texture,
      double diffuse,
      double specular,
      double ior,
      double shininess,
      double reflectivity,
      double transparency,
      Volume volume) {
    this(texture, diffuse, specular, ior, shininess, 0.0, 0.0, reflectivity, transparency, volume);
  }

  public RajaMaterial(
      RajaTexture texture,
      double diffuse,
      double specular,
      double ior,
      double shininess,
      double reflectivity,
      double transparency) {
    this(texture, diffuse, specular, ior, shininess, reflectivity, transparency, null);
  }

  public RajaMaterial(
      RajaTexture texture,
      double diffuse,
      double specular,
      double ior,
      double reflectivity,
      double transparency) {
    this(texture, diffuse, specular, ior, 64.0, reflectivity, transparency, null);
  }

  // =========================================================================
  // MATERIAL INTERFACE METHODS
  // =========================================================================

  @Override
  public int getColorAt(Point3 point, Vector3 normal, Light light, Point3 viewerPoint) {
    tempNormal.set(normal).normalize();

    // Calculate UV coordinates from surface point and normal
    // Use world space coordinates for texture mapping
    double u = 0.0;
    double v = 0.0;

    // Simple planar mapping based on dominant axis of normal
    double nx = Math.abs(tempNormal.x);
    double ny = Math.abs(tempNormal.y);
    double nz = Math.abs(tempNormal.z);

    if (nx >= ny && nx >= nz) {
      // YZ plane
      u = point.y % 1.0;
      v = point.z % 1.0;
    } else if (ny >= nx && ny >= nz) {
      // XZ plane
      u = point.x % 1.0;
      v = point.z % 1.0;
    } else {
      // XY plane
      u = point.x % 1.0;
      v = point.y % 1.0;
    }

    // Ensure positive UVs
    if (u < 0.0) u += 1.0;
    if (v < 0.0) v += 1.0;

    int textureColor = texture.sample(point, u, v);
    int texR = (textureColor >> 16) & 0xFF;
    int texG = (textureColor >> 8) & 0xFF;
    int texB = textureColor & 0xFF;

    LightProperties.getLightProperties(light, point, tempLightProps);
    int lightColor = tempLightProps.color;
    int lightR = (lightColor >> 16) & 0xFF;
    int lightG = (lightColor >> 8) & 0xFF;
    int lightB = lightColor & 0xFF;
    double intensity = tempLightProps.intensity;

    if (light instanceof MuratAmbientLight) {
      int r = ColorUtil.clamp((int) (texR * intensity * AMBIENT_CONTRIBUTION * lightR / 255.0));
      int g = ColorUtil.clamp((int) (texG * intensity * AMBIENT_CONTRIBUTION * lightG / 255.0));
      int b = ColorUtil.clamp((int) (texB * intensity * AMBIENT_CONTRIBUTION * lightB / 255.0));
      return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    tempLightDir.set(tempLightProps.direction);
    double NdotL = Math.max(0.0, tempNormal.dot(tempLightDir));

    // Metalness suppresses diffuse
    double diffuseTerm = diffuse * NdotL * (1.0 - metalness);

    tempViewDir.set(viewerPoint).subtract(point).normalize();
    tempHalfVec.set(tempLightDir).add(tempViewDir).normalize();
    double HdotN = Math.max(0.0, tempHalfVec.dot(tempNormal));

    // Roughness bridge: convert roughness to equivalent shininess
    double effectiveShininess = shininess;
    if (roughness > 0.0) {
      double r = ColorUtil.clampDouble(roughness, 0.001, 1.0);
      effectiveShininess = Math.max(1.0, (2.0 / (r * r)) - 2.0);
    }
    double specularTerm = specular * Math.pow(HdotN, effectiveShininess);

    // Metalness tints specular highlight
    double specR = metalness > 0.0 ? lerp(255.0, texR, metalness) : 255.0;
    double specG = metalness > 0.0 ? lerp(255.0, texG, metalness) : 255.0;
    double specB = metalness > 0.0 ? lerp(255.0, texB, metalness) : 255.0;

    int r = (int) ((texR * diffuseTerm + specR * specularTerm) * intensity * lightR / 255.0);
    int g = (int) ((texG * diffuseTerm + specG * specularTerm) * intensity * lightG / 255.0);
    int b = (int) ((texB * diffuseTerm + specB * specularTerm) * intensity * lightB / 255.0);

    return 0xFF000000 | (ColorUtil.clamp(r) << 16) | (ColorUtil.clamp(g) << 8) | ColorUtil.clamp(b);
  }

  @Override
  public double getReflectivity() {
    return reflectivity;
  }

  @Override
  public double getTransparency() {
    return transparency;
  }

  @Override
  public double getIndexOfRefraction() {
    return ior;
  }

  @Override
  public void setObjectTransform(Matrix4 tm) {
    if (tm == null) {
      this.objectTransform = new Matrix4();
    } else {
      this.objectTransform = tm;
    }
  }

  // =========================================================================
  // ADDITIONAL METHODS
  // =========================================================================

  public RajaTexture getTexture() {
    return texture;
  }

  public double getDiffuse() {
    return diffuse;
  }

  public double getSpecular() {
    return specular;
  }

  public double getShininess() {
    return shininess;
  }

  public double getRoughness() {
    return roughness;
  }

  public double getMetalness() {
    return metalness;
  }

  public Volume getVolume() {
    return volume;
  }

  public boolean hasVolume() {
    return volume != null && volume.hasEffect();
  }

  public int applyVolume(int incomingColor, double distance) {
    if (volume == null || distance <= 0.0) {
      return incomingColor;
    }
    return volume.apply(incomingColor, distance);
  }

  private static double lerp(double a, double b, double t) {
    return a + (b - a) * t;
  }

  // =========================================================================
  // TO STRING
  // =========================================================================

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("RajaMaterial rajamaterial {\n");
    sb.append("        ").append(texture.toString()).append(";\n");
    sb.append("        diffuse = ").append(diffuse).append(";\n");
    sb.append("        specular = ").append(specular).append(";\n");
    sb.append("        ior = ").append(ior).append(";\n");
    sb.append("        shininess = ").append(shininess).append(";\n");
    sb.append("        roughness = ").append(roughness).append(";\n");
    sb.append("        metalness = ").append(metalness).append(";\n");
    sb.append("        reflectivity = ").append(reflectivity).append(";\n");
    sb.append("        transparency = ").append(transparency).append(";\n");
    if (volume != null) {
      sb.append("        ").append(volume.toString()).append(";\n");
    }
    sb.append("    }");
    return sb.toString();
  }
}
