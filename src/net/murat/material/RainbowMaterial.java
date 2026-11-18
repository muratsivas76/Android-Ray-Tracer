package net.murat.material;

import java.util.Locale;
import net.murat.light.Light;
import net.murat.light.LightProperties;
import net.murat.math.*;
import net.murat.util.ColorUtil;

/**
 * RainbowMaterial - creates a true rainbow effect where colors smoothly transition across the
 * surface based on surface position or angle, similar to a CD/DVD reflection or actual rainbow.
 *
 * <p>The rainbow follows the visible spectrum order: Red -> Orange -> Yellow -> Green -> Blue ->
 * Indigo -> Violet
 *
 * @author Murat
 * @version 1.0
 */
public class RainbowMaterial implements Material {

  // ===== CORE MATERIAL PROPERTIES =====
  private int baseColor;
  private double transparency;
  private double reflectivity;
  private double indexOfRefraction;

  // ===== TRANSFORM MATRICES =====
  private Matrix4 objectTransform;
  private Matrix4 inverseObjectTransform;
  private Matrix4 inverseTransposeTransform;

  // ===== RAINBOW PARAMETERS =====
  /** Rainbow intensity [0.0 - 1.0] */
  private double rainbowIntensity;

  /** Rainbow mode: "position" (surface position) or "angle" (view angle) */
  private String rainbowMode;

  /** Rainbow scale - controls how many rainbow cycles appear */
  private double rainbowScale;

  /** Rainbow offset - shifts the rainbow pattern */
  private double rainbowOffset;

  /** Rainbow saturation [0.0 - 1.0] - higher = more vivid colors */
  private double rainbowSaturation;

  /** Base color components */
  private int baseR, baseG, baseB;

  // ===== LIGHTING PARAMETERS =====
  private double shininess = 32.0;
  private double specularStrength = 0.3;
  private double ambientIntensity = 0.1;
  private double diffuseStrength = 0.6;

  // ===== GC-FRIENDLY TEMP OBJECTS =====
  private final Vector3 tempNormal = new Vector3();
  private final Vector3 tempViewDir = new Vector3();
  private final Vector3 tempLightDir = new Vector3();
  private final Vector3 tempHalfVec = new Vector3();
  private final LightProperties tempLightProps = new LightProperties();

  // ===== CONSTRUCTORS =====

  public RainbowMaterial() {
    this.baseColor = 0xFFFFFFFF;
    this.baseR = 0xFF;
    this.baseG = 0xFF;
    this.baseB = 0xFF;
    this.transparency = 0.0;
    this.reflectivity = 0.3;
    this.indexOfRefraction = 1.0;
    this.rainbowIntensity = 1.0;
    this.rainbowMode = "position";
    this.rainbowScale = 1.0;
    this.rainbowOffset = 0.0;
    this.rainbowSaturation = 1.0;
    this.objectTransform = new Matrix4();
    this.inverseObjectTransform = new Matrix4();
    this.inverseTransposeTransform = new Matrix4();
  }

  public RainbowMaterial(
      int baseColor,
      double rainbowIntensity,
      String rainbowMode,
      double rainbowScale,
      double rainbowOffset,
      double rainbowSaturation,
      double reflectivity,
      double transparency) {
    this.baseColor = baseColor;
    this.baseR = (baseColor >> 16) & 0xFF;
    this.baseG = (baseColor >> 8) & 0xFF;
    this.baseB = baseColor & 0xFF;
    this.rainbowIntensity = Math.max(0.0, Math.min(1.0, rainbowIntensity));
    this.rainbowMode = (rainbowMode != null) ? rainbowMode : "position";
    this.rainbowScale = rainbowScale;
    this.rainbowOffset = rainbowOffset;
    this.rainbowSaturation = Math.max(0.0, Math.min(1.0, rainbowSaturation));
    this.reflectivity = reflectivity;
    this.transparency = transparency;
    this.indexOfRefraction = 1.0;
    this.objectTransform = new Matrix4();
    this.inverseObjectTransform = new Matrix4();
    this.inverseTransposeTransform = new Matrix4();
  }

  // ===== MATERIAL INTERFACE =====

  @Override
  public int getColorAt(Point3 point, Vector3 normal, Light light, Point3 viewerPoint) {
    tempNormal.set(normal).normalize();

    LightProperties.getLightProperties(light, point, tempLightProps);
    tempLightDir.set(tempLightProps.direction);

    int lightColor = tempLightProps.color;
    double lightR = ((lightColor >> 16) & 0xFF) / 255.0;
    double lightG = ((lightColor >> 8) & 0xFF) / 255.0;
    double lightB = (lightColor & 0xFF) / 255.0;

    tempViewDir.set(viewerPoint).subtract(point).normalize();

    // ===== COMPUTE RAINBOW COLOR =====
    double t = 0.0;

    if ("angle".equalsIgnoreCase(rainbowMode)) {
      // Rainbow based on viewing angle (like CD/DVD reflection)
      double cosAngle = Math.max(-1.0, Math.min(1.0, tempNormal.dot(tempViewDir)));
      double angle = Math.acos(cosAngle);
      t = (angle / Math.PI) * rainbowScale + rainbowOffset;
    } else {
      // Rainbow based on surface position (default)
      // Using a combination of x, y, z for more interesting patterns
      double px = point.getX() * 0.3;
      double py = point.getY() * 0.3;
      double pz = point.getZ() * 0.3;
      t = (px + py + pz) * rainbowScale + rainbowOffset;
    }

    // Normalize t to [0, 1] range
    t = t - Math.floor(t);

    // Get rainbow color at position t
    int rainbowColor = getRainbowColor(t);

    double rainbowR = ((rainbowColor >> 16) & 0xFF) / 255.0;
    double rainbowG = ((rainbowColor >> 8) & 0xFF) / 255.0;
    double rainbowB = (rainbowColor & 0xFF) / 255.0;

    // Apply saturation
    double gray = 0.299 * rainbowR + 0.587 * rainbowG + 0.114 * rainbowB;
    rainbowR = gray + (rainbowR - gray) * rainbowSaturation;
    rainbowG = gray + (rainbowG - gray) * rainbowSaturation;
    rainbowB = gray + (rainbowB - gray) * rainbowSaturation;

    // Blend with base color
    double baseR = this.baseR / 255.0;
    double baseG = this.baseG / 255.0;
    double baseB = this.baseB / 255.0;

    double finalR = baseR * (1.0 - rainbowIntensity) + rainbowR * rainbowIntensity;
    double finalG = baseG * (1.0 - rainbowIntensity) + rainbowG * rainbowIntensity;
    double finalB = baseB * (1.0 - rainbowIntensity) + rainbowB * rainbowIntensity;

    // ===== LIGHTING =====
    double diffuse = Math.max(0.0, tempNormal.dot(tempLightDir));

    tempHalfVec.set(tempLightDir).add(tempViewDir).normalize();
    double specAngle = Math.max(0.0, tempNormal.dot(tempHalfVec));
    double specular = Math.pow(specAngle, shininess);

    double intensity = tempLightProps.intensity;
    double totalLight =
        ambientIntensity
            + intensity * (diffuseStrength * diffuse)
            + intensity * specularStrength * specular;

    int r = (int) (Math.max(0.0, Math.min(1.0, finalR * totalLight * lightR)) * 255.0);
    int g = (int) (Math.max(0.0, Math.min(1.0, finalG * totalLight * lightG)) * 255.0);
    int b = (int) (Math.max(0.0, Math.min(1.0, finalB * totalLight * lightB)) * 255.0);

    return (0xFF000000 | (r << 16) | (g << 8) | b);
  }

  /**
   * Returns a color from the rainbow spectrum at position t [0, 1] Maps to: Red -> Orange -> Yellow
   * -> Green -> Blue -> Violet
   */
  private int getRainbowColor(double t) {
    // 6 segments for 6 rainbow colors
    double segment = t * 6.0;
    int index = (int) Math.floor(segment);
    double frac = segment - index;

    double r = 0.0, g = 0.0, b = 0.0;

    switch (index) {
      case 0: // Red -> Orange
        r = 1.0;
        g = frac * 0.5; // 0.0 -> 0.5
        b = 0.0;
        break;
      case 1: // Orange -> Yellow
        r = 1.0;
        g = 0.5 + frac * 0.5; // 0.5 -> 1.0
        b = 0.0;
        break;
      case 2: // Yellow -> Green
        r = 1.0 - frac;
        g = 1.0;
        b = 0.0;
        break;
      case 3: // Green -> Cyan
        r = 0.0;
        g = 1.0;
        b = frac;
        break;
      case 4: // Cyan -> Blue
        r = 0.0;
        g = 1.0 - frac;
        b = 1.0;
        break;
      case 5: // Blue -> Violet
        r = frac * 0.5;
        g = 0.0;
        b = 1.0;
        break;
      default:
        r = 1.0;
        g = 0.0;
        b = 0.0;
        break;
    }

    int rInt = (int) (Math.max(0.0, Math.min(1.0, r)) * 255.0);
    int gInt = (int) (Math.max(0.0, Math.min(1.0, g)) * 255.0);
    int bInt = (int) (Math.max(0.0, Math.min(1.0, b)) * 255.0);

    return (0xFF000000 | (rInt << 16) | (gInt << 8) | bInt);
  }

  // ===== GETTERS & SETTERS =====

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

  public void setBaseColor(int baseColor) {
    this.baseColor = baseColor;
    this.baseR = (baseColor >> 16) & 0xFF;
    this.baseG = (baseColor >> 8) & 0xFF;
    this.baseB = baseColor & 0xFF;
  }

  public int getBaseColor() {
    return baseColor;
  }

  public void setRainbowIntensity(double rainbowIntensity) {
    this.rainbowIntensity = Math.max(0.0, Math.min(1.0, rainbowIntensity));
  }

  public double getRainbowIntensity() {
    return rainbowIntensity;
  }

  public void setRainbowMode(String rainbowMode) {
    this.rainbowMode = rainbowMode;
  }

  public String getRainbowMode() {
    return rainbowMode;
  }

  public void setRainbowScale(double rainbowScale) {
    this.rainbowScale = rainbowScale;
  }

  public double getRainbowScale() {
    return rainbowScale;
  }

  public void setRainbowOffset(double rainbowOffset) {
    this.rainbowOffset = rainbowOffset;
  }

  public double getRainbowOffset() {
    return rainbowOffset;
  }

  public void setRainbowSaturation(double rainbowSaturation) {
    this.rainbowSaturation = Math.max(0.0, Math.min(1.0, rainbowSaturation));
  }

  public double getRainbowSaturation() {
    return rainbowSaturation;
  }

  // ===== TO STRING =====

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("RainbowMaterial rainbowmaterial {\n");
    sb.append("        baseColor = ").append(ColorUtil.toColorString(baseColor)).append(";\n");
    sb.append("        rainbowIntensity = ")
        .append(String.format(Locale.US, "%.2f", rainbowIntensity))
        .append(";\n");
    sb.append("        rainbowMode = \"").append(rainbowMode).append("\";\n");
    sb.append("        rainbowScale = ")
        .append(String.format(Locale.US, "%.2f", rainbowScale))
        .append(";\n");
    sb.append("        rainbowOffset = ")
        .append(String.format(Locale.US, "%.2f", rainbowOffset))
        .append(";\n");
    sb.append("        rainbowSaturation = ")
        .append(String.format(Locale.US, "%.2f", rainbowSaturation))
        .append(";\n");
    sb.append("        reflectivity = ")
        .append(String.format(Locale.US, "%.2f", reflectivity))
        .append(";\n");
    sb.append("        transparency = ")
        .append(String.format(Locale.US, "%.2f", transparency))
        .append(";\n");
    sb.append("    }");
    return sb.toString();
  }
}
