package net.murat.material;

import net.murat.light.*;
import net.murat.math.*;
import net.murat.util.ColorUtil;

/**
 * HexagonalHoneycombMaterial - Procedural honeycomb grid pattern. Zero-allocation using packed ARGB
 * integers.
 */
public class HexagonalHoneycombMaterial implements Material {
  private final int primaryColorPacked;
  private final int secondaryColorPacked;
  private final int borderColorPacked;
  private final double cellSize;
  private final double borderWidth;
  private final double ambientStrength;
  private final double specularStrength;
  private final double shininess;
  private final double reflectivity;

  // Pre-cached color components
  private final int primaryR, primaryG, primaryB;
  private final int secondaryR, secondaryG, secondaryB;
  private final int borderR, borderG, borderB;

  // Fast performance constants
  private static final double SQRT3_DIV3 = 0.5773502691896257;
  private static final double TWO_DIV3 = 0.6666666666666666;
  private static final double INV_255 = 0.00392156862745098;

  // ===== GC DOSTU: PRE-ALLOCATED TEMP OBJECTS =====
  private final Vector3 tempLightDir = new Vector3();
  private final LightProperties tempLightProps = new LightProperties();

  public HexagonalHoneycombMaterial(
      int primary, int secondary, double cellSize, double borderWidth) {
    this(primary, secondary, 0xFF000000, cellSize, borderWidth, 0.2, 0.5, 32.0, 0.05);
  }

  public HexagonalHoneycombMaterial(
      int primary,
      int secondary,
      int borderColor,
      double cellSize,
      double borderWidth,
      double ambientStrength,
      double specularStrength,
      double shininess,
      double reflectivity) {
    this.primaryColorPacked = primary;
    this.secondaryColorPacked = secondary;
    this.borderColorPacked = borderColor;
    this.cellSize = Math.max(0.1, cellSize);
    this.borderWidth = Math.max(0, Math.min(0.5, borderWidth));
    this.ambientStrength = Math.max(0, Math.min(1, ambientStrength));
    this.specularStrength = Math.max(0, Math.min(1, specularStrength));
    this.shininess = Math.max(1, shininess);

    if (reflectivity <= 0.01) reflectivity = 0.05;
    if (reflectivity >= 1) reflectivity = 0.95;

    this.reflectivity = reflectivity;

    this.primaryR = (primary >> 16) & 0xFF;
    this.primaryG = (primary >> 8) & 0xFF;
    this.primaryB = primary & 0xFF;

    this.secondaryR = (secondary >> 16) & 0xFF;
    this.secondaryG = (secondary >> 8) & 0xFF;
    this.secondaryB = secondary & 0xFF;

    this.borderR = (borderColor >> 16) & 0xFF;
    this.borderG = (borderColor >> 8) & 0xFF;
    this.borderB = borderColor & 0xFF;
  }

  @Override
  public void setObjectTransform(Matrix4 tm) {
    // Procedural coordinate space logic directly scales via cellSize
  }

  @Override
  public int getColorAt(Point3 worldPoint, Vector3 normal, Light light, Point3 viewPos) {
    // 1. Hexagonal coordinate calculation
    double size = cellSize * 2.0;
    double x = (worldPoint.x + 1000) / size;
    double z = (worldPoint.z + 1000) / size;

    double q = (x * SQRT3_DIV3 - z * 0.3333333333333333);
    double r = z * TWO_DIV3;

    int q1 = (int) Math.round(q);
    int r1 = (int) Math.round(r);
    int s1 = (int) Math.round(-q - r);

    double dq = Math.abs(q - q1);
    double dr = Math.abs(r - r1);
    double ds = Math.abs(-q - r - s1);

    double borderLimit = 0.5 - borderWidth;
    boolean isBorder = (dq > borderLimit) || (dr > borderLimit) || (ds > borderLimit);

    int baseR, baseG, baseB;
    if (isBorder) {
      baseR = borderR;
      baseG = borderG;
      baseB = borderB;
    } else {
      boolean isPrimary = (q1 + r1 + s1) % 2 == 0;
      if (isPrimary) {
        baseR = primaryR;
        baseG = primaryG;
        baseB = primaryB;
      } else {
        baseR = secondaryR;
        baseG = secondaryG;
        baseB = secondaryB;
      }
    }

    // 2. Light properties - ZERO ALLOCATION
    LightProperties.getLightProperties(light, worldPoint, tempLightProps);
    tempLightDir.set(tempLightProps.direction);

    // 3. Ambient check
    if (light instanceof MuratAmbientLight) {
      int red = (int) (baseR * ambientStrength * tempLightProps.intensity);
      int grn = (int) (baseG * ambientStrength * tempLightProps.intensity);
      int blu = (int) (baseB * ambientStrength * tempLightProps.intensity);
      red = red > 255 ? 255 : (red < 0 ? 0 : red);
      grn = grn > 255 ? 255 : (grn < 0 ? 0 : grn);
      blu = blu > 255 ? 255 : (blu < 0 ? 0 : blu);
      return (0xFF000000 | (red << 16) | (grn << 8) | blu);
    }

    // 4. Lighting
    double NdotL = Math.max(0.1, normal.dot(tempLightDir));
    double intensity = tempLightProps.intensity;
    int lightColor = tempLightProps.color;

    int lightR = (lightColor >> 16) & 0xFF;
    int lightG = (lightColor >> 8) & 0xFF;
    int lightB = lightColor & 0xFF;

    double lighting = ambientStrength + NdotL * intensity;

    int rx = (int) (baseR * lighting * lightR * INV_255);
    int gx = (int) (baseG * lighting * lightG * INV_255);
    int bx = (int) (baseB * lighting * lightB * INV_255);

    rx = rx > 255 ? 255 : (rx < 0 ? 0 : rx);
    gx = gx > 255 ? 255 : (gx < 0 ? 0 : gx);
    bx = bx > 255 ? 255 : (bx < 0 ? 0 : bx);

    return (0xFF000000 | (rx << 16) | (gx << 8) | bx);
  }

  @Override
  public double getReflectivity() {
    return reflectivity;
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
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("HexagonalHoneycombMaterial hexagonalhoneycombmaterial {\n");
    sb.append("        primary = ")
        .append(ColorUtil.toColorString(primaryColorPacked))
        .append(";\n");
    sb.append("        secondary = ")
        .append(ColorUtil.toColorString(secondaryColorPacked))
        .append(";\n");
    sb.append("        borderColor = ")
        .append(ColorUtil.toColorString(borderColorPacked))
        .append(";\n");
    sb.append("        cellSize = ").append(cellSize).append(";\n");
    sb.append("        borderWidth = ").append(borderWidth).append(";\n");
    sb.append("        ambientStrength = ").append(ambientStrength).append(";\n");
    sb.append("        specularStrength = ").append(specularStrength).append(";\n");
    sb.append("        shininess = ").append(shininess).append(";\n");
    sb.append("        reflectivity = ").append(reflectivity).append(";\n");
    sb.append("    }");
    return sb.toString();
  }
}
