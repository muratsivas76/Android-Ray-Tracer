package net.murat.material;

import net.murat.light.*;
import net.murat.math.*;
import net.murat.util.ColorUtil;

/**
 * HotCopperMaterial - Procedural patinated copper with zero allocation. Using packed ARGB integers.
 */
public class HotCopperMaterial implements Material {
  private final int copperColorPacked;
  private final int patinaColorPacked;
  private final double patinaAmount;

  private Matrix4 objectTransform;
  private Matrix4 inverseTransform;
  private Matrix4 inverseTransposeTransform;

  // Pre-cached color components
  private final int copperR, copperG, copperB;
  private final int patinaR, patinaG, patinaB;

  private final double ambientCoeff = 0.35;
  private final double diffuseCoeff = 0.65;
  private final double specularCoeff = 0.4;
  private final double shininess = 45.0;
  private final double reflectivity = 0.25;
  private final double ior = 2.6;
  private final double transparency = 0.0;

  private static final int SPECULAR_HIGHLIGHT = 0xFFFFE6C9;

  // ===== GC DOSTU: PRE-ALLOCATED TEMP OBJECTS =====
  private final Point3 tempLocalPoint = new Point3();
  private final Vector3 tempLocalNormal = new Vector3();
  private final Vector3 tempTransformedNormal = new Vector3();
  private final Vector3 tempViewDir = new Vector3();
  private final Vector3 tempLightDir = new Vector3();
  private final Vector3 tempReflectDir = new Vector3();
  private final LightProperties tempLightProps = new LightProperties();

  public HotCopperMaterial() {
    this(0xFFB87333, 0xFF339977, 0.25);
  }

  public HotCopperMaterial(int copperColor, int patinaColor, double patinaAmount) {
    this.copperColorPacked = copperColor;
    this.patinaColorPacked = patinaColor;
    this.patinaAmount = Math.max(0, Math.min(1, patinaAmount));

    this.copperR = (copperColor >> 16) & 0xFF;
    this.copperG = (copperColor >> 8) & 0xFF;
    this.copperB = copperColor & 0xFF;

    this.patinaR = (patinaColor >> 16) & 0xFF;
    this.patinaG = (patinaColor >> 8) & 0xFF;
    this.patinaB = patinaColor & 0xFF;

    this.objectTransform = Matrix4.identity();
    this.inverseTransform = Matrix4.identity();
    this.inverseTransposeTransform = Matrix4.identity();
  }

  @Override
  public void setObjectTransform(Matrix4 tm) {
    if (tm == null) tm = new Matrix4();
    this.objectTransform = tm;
    this.inverseTransform = tm.inverse();
    this.inverseTransposeTransform = this.inverseTransform.inverseTransposeForNormal();
  }

  @Override
  public int getColorAt(Point3 worldPoint, Vector3 worldNormal, Light light, Point3 viewerPos) {
    if (inverseTransform == null) {
      return 0xFF000000;
    }

    // 1. Transform to local space - ZERO ALLOCATION
    inverseTransform.transformPoint(worldPoint, tempLocalPoint);

    // 2. Transform normal - ZERO ALLOCATION
    inverseTransposeTransform.transformDirection(worldNormal, tempTransformedNormal);
    tempLocalNormal.set(tempTransformedNormal);
    tempLocalNormal.normalize();

    // 3. Calculate patina pattern
    int surfaceColor = calculatePatinaPattern(tempLocalPoint);

    // 4. Light properties - ZERO ALLOCATION
    LightProperties.getLightProperties(light, worldPoint, tempLightProps);
    tempLightDir.set(tempLightProps.direction);

    // 5. Ambient check
    int ambient = ColorUtil.multiplyColors(surfaceColor, tempLightProps.color, ambientCoeff);

    if (light instanceof MuratAmbientLight) {
      return ambient;
    }

    // 6. Diffuse
    double NdotL = Math.max(0, tempLocalNormal.dot(tempLightDir));
    int diffuse =
        ColorUtil.multiplyColors(
            surfaceColor, tempLightProps.color, diffuseCoeff * NdotL * tempLightProps.intensity);

    // 7. View direction - ZERO ALLOCATION (inline)
    tempViewDir.set(viewerPos);
    tempViewDir.subtract(worldPoint);
    tempViewDir.normalize();

    // 8. Reflection direction - ZERO ALLOCATION
    tempReflectDir.set(tempLightDir);
    tempReflectDir.negate();
    tempReflectDir.reflect(tempLocalNormal, tempReflectDir);

    // 9. Specular
    double RdotV = Math.max(0, tempReflectDir.dot(tempViewDir));
    double specFactor = Math.pow(RdotV, shininess) * tempLightProps.intensity;
    int specular =
        ColorUtil.multiplyColors(
            SPECULAR_HIGHLIGHT, tempLightProps.color, specularCoeff * specFactor);

    // 10. Combine
    return ColorUtil.combineColors(ambient, diffuse, specular);
  }

  private int calculatePatinaPattern(Point3 point) {
    double noise1 = simplexNoise3D(point.x * 8, point.y * 8, point.z * 8);
    double noise2 = simplexNoise3D(point.x * 3 + 5.3, point.y * 3 + 2.7, point.z * 3 + 1.9);

    double pattern = (noise1 * 0.7 + noise2 * 0.3 + 1) * 0.5;

    if (pattern < patinaAmount) {
      double intensity = pattern / patinaAmount;
      int targetR = Math.min(255, (int) (patinaR * 1.2));
      int targetG = Math.min(255, (int) (patinaG * 0.9));
      int targetB = Math.min(255, (int) (patinaB * 1.1));

      int r = (int) (patinaR + (targetR - patinaR) * intensity);
      int g = (int) (patinaG + (targetG - patinaG) * intensity);
      int b = (int) (patinaB + (targetB - patinaB) * intensity);

      r = r > 255 ? 255 : (r < 0 ? 0 : r);
      g = g > 255 ? 255 : (g < 0 ? 0 : g);
      b = b > 255 ? 255 : (b < 0 ? 0 : b);

      return (0xFF000000 | (r << 16) | (g << 8) | b);
    } else {
      double intensity = (pattern - patinaAmount) / (1 - patinaAmount);
      int targetR = Math.min(255, (int) (copperR * 1.1));
      int targetG = Math.min(255, (int) (copperG * 0.95));
      int targetB = Math.min(255, (int) (copperB * 0.9));

      int r = (int) (copperR + (targetR - copperR) * intensity);
      int g = (int) (copperG + (targetG - copperG) * intensity);
      int b = (int) (copperB + (targetB - copperB) * intensity);

      r = r > 255 ? 255 : (r < 0 ? 0 : r);
      g = g > 255 ? 255 : (g < 0 ? 0 : g);
      b = b > 255 ? 255 : (b < 0 ? 0 : b);

      return (0xFF000000 | (r << 16) | (g << 8) | b);
    }
  }

  private double simplexNoise3D(double x, double y, double z) {
    double value = Math.sin(x * 0.472) + Math.cos(y * 0.683) + Math.sin(z * 0.291);
    value += Math.cos(x * 1.732 + y * 0.846) * 0.6;
    value += Math.sin(y * 1.357 + z * 2.173) * 0.3;
    return value % 1.0;
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
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("HotCopperMaterial hotcoppermaterial {\n");
    sb.append("        copperColor = ")
        .append(ColorUtil.toColorString(copperColorPacked))
        .append(";\n");
    sb.append("        patinaColor = ")
        .append(ColorUtil.toColorString(patinaColorPacked))
        .append(";\n");
    sb.append("        patinaAmount = ").append(patinaAmount).append(";\n");
    sb.append("    }");
    return sb.toString();
  }
}
