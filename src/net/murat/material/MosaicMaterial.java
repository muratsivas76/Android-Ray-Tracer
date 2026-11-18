package net.murat.material;

import net.murat.light.Light;
import net.murat.light.LightProperties;
import net.murat.light.MuratAmbientLight;
import net.murat.math.*;
import net.murat.util.ColorUtil;

/**
 * MosaicMaterial - Procedural mosaic tile with grout pattern. Zero-allocation using packed ARGB
 * integers.
 */
public class MosaicMaterial implements Material {
  private int baseColorPacked;
  private int tileColorPacked;
  private double tileSize;
  private double groutWidth;
  private double randomness;
  private double transparency;

  // Pre-cached color components
  private int baseR, baseG, baseB;
  private int tileR, tileG, tileB;

  // ===== GC DOSTU: PRE-ALLOCATED TEMP OBJECTS =====
  private final Vector3 tempLightDir = new Vector3();
  private final LightProperties tempLightProps = new LightProperties();

  public MosaicMaterial(
      int baseColor, int tileColor, double tileSize, double groutWidth, double randomness) {
    this.baseColorPacked = baseColor;
    this.tileColorPacked = tileColor;
    this.tileSize = tileSize;
    this.groutWidth = groutWidth;
    this.randomness = randomness;
    this.transparency = 0.0;

    this.baseR = (baseColor >> 16) & 0xFF;
    this.baseG = (baseColor >> 8) & 0xFF;
    this.baseB = baseColor & 0xFF;

    this.tileR = (tileColor >> 16) & 0xFF;
    this.tileG = (tileColor >> 8) & 0xFF;
    this.tileB = tileColor & 0xFF;
  }

  public MosaicMaterial(int baseColor, int tileColor) {
    this(baseColor, tileColor, 0.3, 0.05, 0.2);
  }

  @Override
  public double getTransparency() {
    return transparency;
  }

  @Override
  public double getReflectivity() {
    return 0.1;
  }

  @Override
  public double getIndexOfRefraction() {
    return 1.3;
  }

  @Override
  public void setObjectTransform(Matrix4 tm) {}

  @Override
  public int getColorAt(Point3 point, Vector3 normal, Light light, Point3 viewerPoint) {
    // 1. Pattern
    double pattern = calculateMosaicPattern(point);

    int patternR = (int) (baseR + (tileR - baseR) * pattern);
    int patternG = (int) (baseG + (tileG - baseG) * pattern);
    int patternB = (int) (baseB + (tileB - baseB) * pattern);

    patternR = clamp(patternR);
    patternG = clamp(patternG);
    patternB = clamp(patternB);

    // 2. Light properties - ZERO ALLOCATION
    LightProperties.getLightProperties(light, point, tempLightProps);
    tempLightDir.set(tempLightProps.direction);

    // 3. Ambient check
    if (light instanceof MuratAmbientLight) {
      int r = (int) (patternR * tempLightProps.intensity);
      int g = (int) (patternG * tempLightProps.intensity);
      int b = (int) (patternB * tempLightProps.intensity);
      r = clamp(r);
      g = clamp(g);
      b = clamp(b);
      return (0xFF000000 | (r << 16) | (g << 8) | b);
    }

    // 4. Light color
    int lightColor = tempLightProps.color;
    int lightR = (lightColor >> 16) & 0xFF;
    int lightG = (lightColor >> 8) & 0xFF;
    int lightB = lightColor & 0xFF;

    // 5. Diffuse
    double NdotL = Math.max(0.0, normal.dot(tempLightDir));
    double diffFactor = NdotL * tempLightProps.intensity;

    int r = (int) (patternR * diffFactor * lightR / 255.0);
    int g = (int) (patternG * diffFactor * lightG / 255.0);
    int b = (int) (patternB * diffFactor * lightB / 255.0);

    r = clamp(r);
    g = clamp(g);
    b = clamp(b);

    return (0xFF000000 | (r << 16) | (g << 8) | b);
  }

  private double calculateMosaicPattern(Point3 point) {
    double randomOffset = ((point.hashCode() & 0x7FFFFFFF) % 1000) * 0.001 * randomness;

    double invTileSize = 1.0 / tileSize;
    double x = (point.x + randomOffset) * invTileSize;
    double y = (point.y + randomOffset) * invTileSize;
    double z = (point.z + randomOffset) * invTileSize;

    double fracX = x - Math.floor(x);
    double fracY = y - Math.floor(y);
    double fracZ = z - Math.floor(z);

    double inverseGrout = 1.0 - groutWidth;
    if (fracX < groutWidth
        || fracX > inverseGrout
        || fracY < groutWidth
        || fracY > inverseGrout
        || fracZ < groutWidth
        || fracZ > inverseGrout) {
      return 0.0;
    }

    double variation = ((int) (Math.floor(x) + Math.floor(y) + Math.floor(z)) % 3) * 0.1;
    return 0.9 + variation;
  }

  private int clamp(int value) {
    return Math.max(0, Math.min(255, value));
  }

  public int getBaseColorPacked() {
    return baseColorPacked;
  }

  public void setBaseColorPacked(int baseColor) {
    this.baseColorPacked = baseColor;
    this.baseR = (baseColor >> 16) & 0xFF;
    this.baseG = (baseColor >> 8) & 0xFF;
    this.baseB = baseColor & 0xFF;
    this.transparency = 0.0;
  }

  public int getTileColorPacked() {
    return tileColorPacked;
  }

  public void setTileColorPacked(int tileColor) {
    this.tileColorPacked = tileColor;
    this.tileR = (tileColor >> 16) & 0xFF;
    this.tileG = (tileColor >> 8) & 0xFF;
    this.tileB = tileColor & 0xFF;
  }

  public double getTileSize() {
    return tileSize;
  }

  public void setTileSize(double tileSize) {
    this.tileSize = tileSize;
  }

  public double getGroutWidth() {
    return groutWidth;
  }

  public void setGroutWidth(double groutWidth) {
    this.groutWidth = groutWidth;
  }

  public double getRandomness() {
    return randomness;
  }

  public void setRandomness(double randomness) {
    this.randomness = randomness;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("MosaicMaterial mosaicmaterial {\n");
    sb.append("        baseColor = ")
        .append(ColorUtil.toColorString(baseColorPacked))
        .append(";\n");
    sb.append("        tileColor = ")
        .append(ColorUtil.toColorString(tileColorPacked))
        .append(";\n");
    sb.append("        tileSize = ").append(tileSize).append(";\n");
    sb.append("        groutWidth = ").append(groutWidth).append(";\n");
    sb.append("        randomness = ").append(randomness).append(";\n");
    sb.append("    }");
    return sb.toString();
  }
}
