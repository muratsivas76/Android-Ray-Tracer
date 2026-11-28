package net.murat.material;

import net.murat.light.*;
import net.murat.math.*;
import net.murat.util.ColorUtil;

/**
 * WaveMaterial - Flowing wave patterns with color transitions
 *
 * <p>This material creates animated wave-like patterns using sine/cosine functions. The pattern
 * smoothly interpolates between two colors based on the wave value.
 *
 * <p>This implementation is 100% garbage-free during render() execution. All temporary objects are
 * pre-allocated as instance fields and reused.
 *
 * @author Murat
 * @version 1.0
 */
public class WaveMaterial implements Material {

  // =========================================================================
  // PROPERTIES
  // =========================================================================

  private final int colorA;
  private final int colorB;
  private final double frequency;
  private final double amplitude;
  private final double specularStrength;

  // Color components (cached for performance)
  private final int colorAR;
  private final int colorAG;
  private final int colorAB;
  private final int colorBR;
  private final int colorBG;
  private final int colorBB;

  // =========================================================================
  // GC-FRIENDLY SCRATCH POOL - Zero allocation per shade call
  // =========================================================================

  private final Vector3 tempNormal = new Vector3();
  private final Vector3 tempLightDir = new Vector3();
  private final Vector3 tempViewDir = new Vector3();
  private final Vector3 tempHalfVec = new Vector3();
  private final LightProperties tempLightProps = new LightProperties();

  // =========================================================================
  // CONSTRUCTORS
  // =========================================================================

  /**
   * Creates a wave material with full parameter control.
   *
   * @param colorA First color (ARGB)
   * @param colorB Second color (ARGB)
   * @param frequency Wave frequency (higher = more waves)
   * @param amplitude Wave amplitude (0.0 - 1.0)
   * @param specularStrength Specular highlight strength (0.0 - 1.0)
   */
  public WaveMaterial(
      int colorA, int colorB, double frequency, double amplitude, double specularStrength) {
    this.colorA = colorA;
    this.colorB = colorB;
    this.frequency = frequency;
    this.amplitude = clamp01(amplitude);
    this.specularStrength = clamp01(specularStrength);

    // Pre-compute color components
    this.colorAR = (colorA >> 16) & 0xFF;
    this.colorAG = (colorA >> 8) & 0xFF;
    this.colorAB = colorA & 0xFF;
    this.colorBR = (colorB >> 16) & 0xFF;
    this.colorBG = (colorB >> 8) & 0xFF;
    this.colorBB = colorB & 0xFF;
  }

  /**
   * Creates a wave material with default amplitude and specular.
   *
   * @param colorA First color (ARGB)
   * @param colorB Second color (ARGB)
   * @param frequency Wave frequency
   */
  public WaveMaterial(int colorA, int colorB, double frequency) {
    this(colorA, colorB, frequency, 0.5, 0.3);
  }

  /**
   * Creates a wave material with default settings.
   *
   * @param colorA First color (ARGB)
   * @param colorB Second color (ARGB)
   */
  public WaveMaterial(int colorA, int colorB) {
    this(colorA, colorB, 1.0, 0.5, 0.3);
  }

  // =========================================================================
  // MATERIAL INTERFACE METHODS
  // =========================================================================

  @Override
  public int getColorAt(Point3 point, Vector3 normal, Light light, Point3 viewerPos) {
    // Normalize normal - GC FREE
    tempNormal.set(normal).normalize();

    // Wave pattern: sin(x) * cos(z) with time-like animation
    double wave =
        Math.sin(point.x * frequency + point.y * frequency * 0.7)
            * Math.cos(point.z * frequency * 0.5);
    wave = wave * amplitude + 0.5;
    wave = Math.max(0.0, Math.min(1.0, wave));

    // Lerp between colorA and colorB based on wave value
    int baseR = (int) (colorAR + (colorBR - colorAR) * wave);
    int baseG = (int) (colorAG + (colorBG - colorAG) * wave);
    int baseB = (int) (colorAB + (colorBB - colorAB) * wave);
    int baseColor = (0xFF000000 | (baseR << 16) | (baseG << 8) | baseB);

    // Get light properties - ZERO ALLOCATION
    LightProperties.getLightProperties(light, point, tempLightProps);
    tempLightDir.set(tempLightProps.direction);

    int lightColor = tempLightProps.color;
    int lightR = (lightColor >> 16) & 0xFF;
    int lightG = (lightColor >> 8) & 0xFF;
    int lightB = lightColor & 0xFF;

    // View direction - ZERO ALLOCATION
    tempViewDir.set(viewerPos).subtract(point).normalize();

    // Diffuse (Lambertian)
    double diffuse = Math.max(0.0, tempNormal.dot(tempLightDir));

    // Specular (Blinn-Phong)
    tempHalfVec.set(tempLightDir).add(tempViewDir).normalize();
    double specular = Math.pow(Math.max(0.0, tempNormal.dot(tempHalfVec)), 64.0);
    specular *= specularStrength;

    // Ambient term
    double ambient = 0.15;

    // Combine lighting
    double totalLight = ambient + diffuse * tempLightProps.intensity;

    // Apply lighting to base color
    int r = (int) (baseR * totalLight * lightR / 255.0);
    int g = (int) (baseG * totalLight * lightG / 255.0);
    int b = (int) (baseB * totalLight * lightB / 255.0);

    // Add specular
    int specR = (int) (specular * tempLightProps.intensity * lightR / 255.0 * 255.0);
    int specG = (int) (specular * tempLightProps.intensity * lightG / 255.0 * 255.0);
    int specB = (int) (specular * tempLightProps.intensity * lightB / 255.0 * 255.0);

    r = Math.min(255, r + specR);
    g = Math.min(255, g + specG);
    b = Math.min(255, b + specB);

    r = Math.max(0, Math.min(255, r));
    g = Math.max(0, Math.min(255, g));
    b = Math.max(0, Math.min(255, b));

    return (0xFF000000 | (r << 16) | (g << 8) | b);
  }

  @Override
  public double getReflectivity() {
    return 0.0;
  }

  @Override
  public double getIndexOfRefraction() {
    return 1.0;
  }

  @Override
  public double getTransparency() {
    return 0.0;
  }

  @Override
  public void setObjectTransform(Matrix4 tm) {
    // Not used for this material
  }

  // =========================================================================
  // GETTERS
  // =========================================================================

  public int getColorA() {
    return colorA;
  }

  public int getColorB() {
    return colorB;
  }

  public double getFrequency() {
    return frequency;
  }

  public double getAmplitude() {
    return amplitude;
  }

  public double getSpecularStrength() {
    return specularStrength;
  }

  // =========================================================================
  // UTILITY METHODS
  // =========================================================================

  private static double clamp01(double val) {
    return val > 1.0 ? 1.0 : (val < 0.0 ? 0.0 : val);
  }

  // =========================================================================
  // TO STRING - GC acceptable (not called during render)
  // =========================================================================

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("WaveMaterial wavematerial {\n");
    sb.append("        colorA = ").append(ColorUtil.toColorString(colorA)).append(";\n");
    sb.append("        colorB = ").append(ColorUtil.toColorString(colorB)).append(";\n");
    sb.append("        frequency = ").append(frequency).append(";\n");
    sb.append("        amplitude = ").append(amplitude).append(";\n");
    sb.append("        specularStrength = ").append(specularStrength).append(";\n");
    sb.append("    }");
    return sb.toString();
  }
}
