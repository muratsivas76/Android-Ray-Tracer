package net.murat.material;

import net.murat.light.Light;
import net.murat.light.LightProperties;
import net.murat.math.*;
import net.murat.util.ColorUtil;

/**
 * CeramicTileMaterial - High performance PBR-style tile rendering engine. Implements Material
 * interface with pure integer ARGB operations using ColorUtil. Zero GC overhead, fully optimized
 * for Java 6-8 environments.
 *
 * <p>Optimization notes: 1. Uses LightProperties for zero-allocation light calculations 2. All
 * lighting math is done in world space (no local-space mixing) 3. Pre-allocated temp objects for
 * GC-friendly operation 4. All color operations use ColorUtil for consistency and performance 5.
 * Preserves all original visual behavior while optimizing performance
 */
public class CeramicTileMaterial implements Material {
  // Packed ARGB colors (0xAARRGGBB)
  private final int tileColor;
  private final int groutColor;

  // Material properties
  private final double tileSize;
  private final double groutWidth;
  private final double tileRoughness;
  private final double groutRoughness;
  private final double tileSpecular;
  private final double groutSpecular;
  private final double fresnelIntensity;
  private final double normalMicroFacet;
  private final double reflectionSharpness;
  private final double energyConservation;

  // Default values
  private static final int DEFAULT_TILE_COLOR = 0xFFF5F5FF; // Ice white
  private static final int DEFAULT_GROUT_COLOR = 0xFF464650; // Dark gray
  private static final double DEFAULT_TILE_SIZE = 0.6;
  private static final double DEFAULT_GROUT_WIDTH = 0.03;
  private static final double DEFAULT_TILE_ROUGHNESS = 0.1;
  private static final double DEFAULT_GROUT_ROUGHNESS = 0.6;
  private static final double DEFAULT_TILE_SPECULAR = 0.5;
  private static final double DEFAULT_GROUT_SPECULAR = 0.3;
  private static final double DEFAULT_FRESNEL = 0.9;
  private static final double DEFAULT_MICRO_FACET = 0.02;
  private static final double DEFAULT_SHARPNESS = 0.95;
  private static final double DEFAULT_ENERGY = 1.0;

  // ===== GC-FRIENDLY: PRE-ALLOCATED TEMP OBJECTS =====
  private final Vector3 tempNormal = new Vector3();
  private final Vector3 tempViewDir = new Vector3();
  private final Vector3 tempLightDir = new Vector3();
  private final Vector3 tempReflectDir = new Vector3();
  private final LightProperties tempLightProps = new LightProperties();

  // Constructors
  public CeramicTileMaterial() {
    this(
        DEFAULT_TILE_COLOR,
        DEFAULT_GROUT_COLOR,
        DEFAULT_TILE_SIZE,
        DEFAULT_GROUT_WIDTH,
        DEFAULT_TILE_ROUGHNESS,
        DEFAULT_GROUT_ROUGHNESS,
        DEFAULT_TILE_SPECULAR,
        DEFAULT_GROUT_SPECULAR,
        DEFAULT_FRESNEL,
        DEFAULT_MICRO_FACET,
        DEFAULT_SHARPNESS,
        DEFAULT_ENERGY);
  }

  public CeramicTileMaterial(
      int tileColor,
      int groutColor,
      double tileSize,
      double groutWidth,
      double tileRoughness,
      double groutRoughness) {
    this(
        tileColor,
        groutColor,
        tileSize,
        groutWidth,
        tileRoughness,
        groutRoughness,
        DEFAULT_TILE_SPECULAR,
        DEFAULT_GROUT_SPECULAR,
        DEFAULT_FRESNEL,
        DEFAULT_MICRO_FACET,
        DEFAULT_SHARPNESS,
        DEFAULT_ENERGY);
  }

  public CeramicTileMaterial(
      int tileColor,
      int groutColor,
      double tileSize,
      double groutWidth,
      double tileRoughness,
      double groutRoughness,
      double tileSpecular,
      double groutSpecular) {
    this(
        tileColor,
        groutColor,
        tileSize,
        groutWidth,
        tileRoughness,
        groutRoughness,
        tileSpecular,
        groutSpecular,
        DEFAULT_FRESNEL,
        DEFAULT_MICRO_FACET,
        DEFAULT_SHARPNESS,
        DEFAULT_ENERGY);
  }

  // Full constructor
  public CeramicTileMaterial(
      int tileColor,
      int groutColor,
      double tileSize,
      double groutWidth,
      double tileRoughness,
      double groutRoughness,
      double tileSpecular,
      double groutSpecular,
      double fresnelIntensity,
      double normalMicroFacet,
      double reflectionSharpness,
      double energyConservation) {
    // Boost tile color saturation slightly (multiply by 1.2)
    this.tileColor = ColorUtil.enhanceColorSaturation(tileColor, 1.2f);
    this.groutColor = groutColor;
    this.tileSize = Math.max(0.1, tileSize);
    this.groutWidth = Math.max(0.01, Math.min(0.1, groutWidth));
    this.tileRoughness = clamp(tileRoughness, 0.01, 1.0);
    this.groutRoughness = clamp(groutRoughness, 0.01, 1.0);
    this.tileSpecular = clamp(tileSpecular, 0.0, 1.0);
    this.groutSpecular = clamp(groutSpecular, 0.0, 1.0);
    this.fresnelIntensity = clamp(fresnelIntensity, 0.0, 1.0);
    this.normalMicroFacet = clamp(normalMicroFacet, 0.0, 0.1);
    this.reflectionSharpness = clamp(reflectionSharpness, 0.5, 1.0);
    this.energyConservation = clamp(energyConservation, 0.8, 1.0);
  }

  @Override
  public int getColorAt(Point3 point, Vector3 normal, Light light, Point3 viewerPos) {
    // point, normal and viewerPos are already in world space, as supplied
    // by the ray tracer - no local-space transform is needed here.
    tempNormal.set(normal).normalize();

    // 1. UV mapping for tile pattern
    double invTileSize = 1.0 / tileSize;
    double u = ((point.x % tileSize) + tileSize) % tileSize * invTileSize;
    double v = ((point.z % tileSize) + tileSize) % tileSize * invTileSize;

    double groutRatio = groutWidth * invTileSize;
    double invGroutRatio = 1.0 - groutRatio;

    boolean isGrout = (u < groutRatio || u > invGroutRatio || v < groutRatio || v > invGroutRatio);

    // 2. Select material properties
    int baseColor = isGrout ? groutColor : tileColor;
    double roughness = isGrout ? groutRoughness : tileRoughness;
    double specularIntensity = isGrout ? groutSpecular : tileSpecular;

    // 3. Light properties - ZERO ALLOCATION
    LightProperties.getLightProperties(light, point, tempLightProps);
    tempLightDir.set(tempLightProps.direction);

    int lightColor = tempLightProps.color;
    int lightR = (lightColor >> 16) & 0xFF;
    int lightG = (lightColor >> 8) & 0xFF;
    int lightB = lightColor & 0xFF;
    double intensity = tempLightProps.intensity;

    // 4. View direction - world point to world viewer, correct subtraction.
    tempViewDir.set(viewerPos).subtract(point).normalize();

    // 5. Micro-facet perturbation (preserves original visual behavior)
    double nx = tempNormal.x;
    double ny = tempNormal.y;
    double nz = tempNormal.z;

    if (normalMicroFacet > 0.0) {
      double noise = Math.sin(point.x * 10.0) * Math.cos(point.z * 10.0) * 0.01;
      double px = noise * normalMicroFacet;
      double py = (1.0 - Math.abs(noise)) * normalMicroFacet;
      double pz = noise * normalMicroFacet * 0.5;

      nx = tempNormal.x + px;
      ny = tempNormal.y + py;
      nz = tempNormal.z + pz;
      double nLenInv = 1.0 / Math.sqrt(nx * nx + ny * ny + nz * nz);
      nx *= nLenInv;
      ny *= nLenInv;
      nz *= nLenInv;
    }

    // 6. Fresnel effect
    double dotNV = nx * tempViewDir.x + ny * tempViewDir.y + nz * tempViewDir.z;
    double cosTheta = (dotNV > 0.001) ? dotNV : 0.001;
    double fresnel = Math.pow(1.0 - cosTheta, 5.0);
    fresnel = fresnelIntensity * (0.04 + 0.96 * fresnel);

    // 7. Diffuse component (Lambertian)
    double dotNL = nx * tempLightDir.x + ny * tempLightDir.y + nz * tempLightDir.z;
    double NdotL = (dotNL > 0.0) ? dotNL : 0.0;
    double diffuseIntensity = NdotL * energyConservation * intensity;

    // 8. Specular component - using tempReflectDir
    double dotNLReflect = -(tempLightDir.x * nx + tempLightDir.y * ny + tempLightDir.z * nz);
    tempReflectDir.set(tempLightDir);
    tempReflectDir.x = -tempLightDir.x - 2.0 * dotNLReflect * nx;
    tempReflectDir.y = -tempLightDir.y - 2.0 * dotNLReflect * ny;
    tempReflectDir.z = -tempLightDir.z - 2.0 * dotNLReflect * nz;
    tempReflectDir.normalize();

    double dotRV =
        tempReflectDir.x * tempViewDir.x
            + tempReflectDir.y * tempViewDir.y
            + tempReflectDir.z * tempViewDir.z;
    double RdotV = (dotRV > 0.0) ? dotRV : 0.0;

    double specularPower = (1.0 - roughness * reflectionSharpness) * 256.0 + 1.0;
    double specular = specularIntensity * fresnel * Math.pow(RdotV, specularPower) * intensity;

    // 9. Ambient component (light color aware)
    double ambientIntensity = isGrout ? 0.3 : 0.2;

    // 10. Combine components using ColorUtil methods
    int finalColor =
        combineColorsWithColorUtil(
            baseColor, diffuseIntensity, specular, ambientIntensity, lightColor);

    // 11. Contrast and gamma correction using ColorUtil (preserves original behavior)
    finalColor = ColorUtil.adjustContrast(finalColor, 1.1f);
    return ColorUtil.gammaCorrect(finalColor, 0.8f);
  }

  /**
   * Combines diffuse, specular, and ambient components into final ARGB color. Uses ColorUtil
   * methods for consistent and GC-friendly operations.
   *
   * @param baseColor Base tile/grout color
   * @param diffuseIntensity Diffuse lighting intensity
   * @param specularIntensity Specular lighting intensity
   * @param ambientIntensity Ambient lighting intensity
   * @param lightColor Light color in ARGB format
   * @return Combined ARGB color
   */
  private int combineColorsWithColorUtil(
      int baseColor,
      double diffuseIntensity,
      double specularIntensity,
      double ambientIntensity,
      int lightColor) {
    // Create diffuse lighting
    int diffuseLight = ColorUtil.multiplyColor(lightColor, diffuseIntensity);
    int diffuseResult = ColorUtil.multiply(baseColor, diffuseLight);

    // Create ambient lighting
    int ambientLight = ColorUtil.multiplyColor(lightColor, ambientIntensity);
    int ambientResult = ColorUtil.multiply(baseColor, ambientLight);

    // Create specular (white light reflection with light color influence)
    int specularColor = ColorUtil.multiplyColor(lightColor, specularIntensity);

    // Combine all components using additive blending
    int combined = ColorUtil.add(ambientResult, diffuseResult);
    combined = ColorUtil.add(combined, specularColor);

    return combined;
  }

  /**
   * Legacy combine method for backward compatibility. Kept for reference but not used in optimized
   * version.
   */
  @Deprecated
  private int combineColors(
      int baseColor,
      double diffuseIntensity,
      double specularIntensity,
      double ambientIntensity,
      int lightR,
      int lightG,
      int lightB) {
    int lightColor = (0xFF << 24) | (lightR << 16) | (lightG << 8) | lightB;
    return combineColorsWithColorUtil(
        baseColor, diffuseIntensity, specularIntensity, ambientIntensity, lightColor);
  }

  // Helper methods
  private double clamp(double value, double min, double max) {
    return Math.max(min, Math.min(max, value));
  }

  // PBR properties exposed through Material interface
  @Override
  public double getReflectivity() {
    return 0.4 * fresnelIntensity;
  }

  @Override
  public double getIndexOfRefraction() {
    return 1.52;
  }

  @Override
  public double getTransparency() {
    return 0.0;
  }

  @Override
  public void setObjectTransform(Matrix4 tm) {
    // Transformation not used for tile grid alignment
  }

  // Getters
  public int getTileColor() {
    return tileColor;
  }

  public int getGroutColor() {
    return groutColor;
  }

  public double getTileSize() {
    return tileSize;
  }

  public double getGroutWidth() {
    return groutWidth;
  }

  public double getTileRoughness() {
    return tileRoughness;
  }

  public double getGroutRoughness() {
    return groutRoughness;
  }

  public double getTileSpecular() {
    return tileSpecular;
  }

  public double getGroutSpecular() {
    return groutSpecular;
  }

  public double getFresnelIntensity() {
    return fresnelIntensity;
  }

  public double getNormalMicroFacet() {
    return normalMicroFacet;
  }

  public double getReflectionSharpness() {
    return reflectionSharpness;
  }

  public double getEnergyConservation() {
    return energyConservation;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("CeramicTileMaterial ceramictilematerial {\n");
    sb.append("        tileColor = ").append(ColorUtil.toColorString(tileColor)).append(";\n");
    sb.append("        groutColor = ").append(ColorUtil.toColorString(groutColor)).append(";\n");
    sb.append("        tileSize = ").append(tileSize).append(";\n");
    sb.append("        groutWidth = ").append(groutWidth).append(";\n");
    sb.append("        tileRoughness = ").append(tileRoughness).append(";\n");
    sb.append("        groutRoughness = ").append(groutRoughness).append(";\n");
    sb.append("        tileSpecular = ").append(tileSpecular).append(";\n");
    sb.append("        groutSpecular = ").append(groutSpecular).append(";\n");
    sb.append("        fresnelIntensity = ").append(fresnelIntensity).append(";\n");
    sb.append("        normalMicroFacet = ").append(normalMicroFacet).append(";\n");
    sb.append("        reflectionSharpness = ").append(reflectionSharpness).append(";\n");
    sb.append("        energyConservation = ").append(energyConservation).append(";\n");
    sb.append("    }");
    return sb.toString();
  }
}
