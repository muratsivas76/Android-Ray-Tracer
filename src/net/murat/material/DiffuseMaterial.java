package net.murat.material;

import net.murat.light.*;
import net.murat.math.*;
import net.murat.util.ColorUtil;

/**
 * DiffuseMaterial - A physically-based diffuse (Lambertian) material.
 *
 * <p>This material implements a full diffuse reflection model with:
 *
 * <ul>
 *   <li>Lambertian diffuse (N·L) with energy conservation
 *   <li>Ambient light support (all light types, not just ambient lights)
 *   <li>Fresnel rim effect for realistic edge darkening/lighting
 *   <li>100% garbage-free during render
 * </ul>
 *
 * @author Murat
 * @version 2.0
 */
public class DiffuseMaterial implements Material {

  // =========================================================================
  // PROPERTIES
  // =========================================================================

  private final int colorPacked;
  private final double diffuseCoefficient;
  private final double reflectivity;
  private final double ior;
  private final double transparency;

  // Internal parameters (not exposed via constructor)
  private double roughness = 0.5;
  private double ambientStrength = 0.1;
  private double rimStrength = 0.2;

  // Pre-allocated temp objects - GC FREE
  private final LightProperties tempLightProps = new LightProperties();
  private final Vector3 tempNormal = new Vector3();
  private final Vector3 tempViewDir = new Vector3();

  // Color components (cached)
  private final int baseR;
  private final int baseG;
  private final int baseB;

  // =========================================================================
  // CONSTRUCTORS (UNCHANGED)
  // =========================================================================

  public DiffuseMaterial(
      int colorPacked,
      double diffuseCoefficient,
      double reflectivity,
      double ior,
      double transparency) {
    this.colorPacked = colorPacked;
    this.baseR = (colorPacked >> 16) & 0xFF;
    this.baseG = (colorPacked >> 8) & 0xFF;
    this.baseB = colorPacked & 0xFF;

    this.diffuseCoefficient = clamp01(diffuseCoefficient);
    this.reflectivity = clamp01(reflectivity);
    this.ior = Math.max(1.0, ior);
    this.transparency = clamp01(transparency);
  }

  public DiffuseMaterial(int colorPacked) {
    this(colorPacked, 0.8, 0.0, 1.0, 0.0);
  }

  // =========================================================================
  // MATERIAL INTERFACE
  // =========================================================================

  @Override
  public int getColorAt(Point3 point, Vector3 normal, Light light, Point3 viewerPos) {
    // Normalize normal - GC FREE
    tempNormal.set(normal).normalize();

    // Get light properties - ZERO ALLOCATION
    LightProperties.getLightProperties(light, point, tempLightProps);

    int lightColor = tempLightProps.color;
    double intensity = tempLightProps.intensity;

    // Extract light color components
    int lightR = (lightColor >> 16) & 0xFF;
    int lightG = (lightColor >> 8) & 0xFF;
    int lightB = lightColor & 0xFF;

    // === AMBIENT LIGHT (works for ALL light types) ===
    double ambient = ambientStrength * intensity;

    // === DIFFUSE (Lambertian) ===
    double NdotL = Math.max(0.0, tempNormal.dot(tempLightProps.direction));
    double diffuse = diffuseCoefficient * NdotL * intensity;

    // === RIM/FRESNEL EFFECT ===
    tempViewDir.set(viewerPos).subtract(point).normalize();
    double NdotV = Math.max(0.0, tempNormal.dot(tempViewDir));
    double rim = 1.0 - NdotV;
    rim = rim * rim * (3.0 - 2.0 * rim); // Smoothstep
    rim *= rimStrength * intensity;

    // === OCCLUSION SIMULATION (roughness based) ===
    // Rougher surfaces absorb more light at grazing angles
    double occlusion = 1.0 - roughness * (1.0 - NdotL) * 0.3;

    // === COMBINE ALL TERMS ===
    double totalLight = ambient + diffuse + rim;
    totalLight *= occlusion;

    // Special handling for ambient lights (pure color, no direction)
    if (light instanceof MuratAmbientLight) {
      totalLight = ambient;
    }

    // Apply color
    int r = (int) (baseR * totalLight * lightR / 255.0);
    int g = (int) (baseG * totalLight * lightG / 255.0);
    int b = (int) (baseB * totalLight * lightB / 255.0);

    r = r > 255 ? 255 : (r < 0 ? 0 : r);
    g = g > 255 ? 255 : (g < 0 ? 0 : g);
    b = b > 255 ? 255 : (b < 0 ? 0 : b);

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

  @Override
  public void setObjectTransform(Matrix4 tm) {
    // Not used for diffuse material
  }

  // =========================================================================
  // SETTERS (Internal parameters - called via reflection or direct)
  // =========================================================================

  public void setRoughness(double roughness) {
    this.roughness = clamp01(roughness);
  }

  public void setAmbientStrength(double ambientStrength) {
    this.ambientStrength = clamp01(ambientStrength);
  }

  public void setRimStrength(double rimStrength) {
    this.rimStrength = clamp01(rimStrength);
  }

  // =========================================================================
  // UTILITY METHODS
  // =========================================================================

  private static double clamp01(double val) {
    return val > 1.0 ? 1.0 : (val < 0.0 ? 0.0 : val);
  }

  // =========================================================================
  // TO STRING
  // =========================================================================

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("DiffuseMaterial diffusematerial {\n");
    sb.append("        color = ").append(ColorUtil.toColorString(colorPacked)).append(";\n");
    sb.append("        diffuseCoefficient = ").append(diffuseCoefficient).append(";\n");
    sb.append("        reflectivity = ").append(reflectivity).append(";\n");
    sb.append("        ior = ").append(ior).append(";\n");
    sb.append("        transparency = ").append(transparency).append(";\n");
    sb.append("        roughness = ").append(roughness).append(";\n");
    sb.append("        ambientStrength = ").append(ambientStrength).append(";\n");
    sb.append("        rimStrength = ").append(rimStrength).append(";\n");
    sb.append("    }");
    return sb.toString();
  }
}
