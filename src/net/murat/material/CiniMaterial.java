package net.murat.material;

import net.murat.light.*;
import net.murat.math.*;
import net.murat.util.ColorUtil;

public class CiniMaterial implements Material {
  private final int baseColorPacked;
  private final int patternColorPacked;
  private final double glossiness;
  private Matrix4 objectInverseTransform;

  private static final int WHITE_REFERENCE = ColorUtil.WHITE;

  public static final int IZNIK_BLUE = 0xFF0066CC;
  public static final int IZNIK_RED = 0xFFCC0033;
  public static final int IZNIK_TURQUOISE = 0xFF009999;
  public static final int IZNIK_WHITE = 0xFFFFFFFF;
  public static final int IZNIK_GREEN = 0xFF00994C;

  // Pre-allocated temp objects
  private final Point3 tempLocalPoint = new Point3();
  private final Vector3 tempViewDir = new Vector3();
  private final Vector3 tempLightDir = new Vector3();
  private final Vector3 tempHalfway = new Vector3();
  private final LightProperties tempLightProps = new LightProperties();

  public CiniMaterial(int baseColor, int patternColor, double glossiness, Matrix4 invTransform) {
    this.baseColorPacked = baseColor;
    this.patternColorPacked = patternColor;
    this.glossiness = Math.max(0.1, Math.min(1.0, glossiness));
    this.objectInverseTransform = invTransform != null ? invTransform : new Matrix4();
  }

  public CiniMaterial(Matrix4 invTransform) {
    this(IZNIK_WHITE, IZNIK_BLUE, 0.8, invTransform);
  }

  public static CiniMaterial createRedIznik(Matrix4 invTransform) {
    return new CiniMaterial(IZNIK_WHITE, IZNIK_RED, 0.85, invTransform);
  }

  public static CiniMaterial createTurquoiseIznik(Matrix4 invTransform) {
    return new CiniMaterial(IZNIK_WHITE, IZNIK_TURQUOISE, 0.8, invTransform);
  }

  @Override
  public void setObjectTransform(Matrix4 tm) {
    if (tm == null) tm = new Matrix4();
    this.objectInverseTransform = tm;
  }

  @Override
  public int getColorAt(Point3 worldPoint, Vector3 worldNormal, Light light, Point3 viewPos) {
    // Transform to local space
    objectInverseTransform.transformPoint(worldPoint, tempLocalPoint);

    // Generate pattern
    int finalColor = generateCiniPattern(tempLocalPoint, baseColorPacked, patternColorPacked);

    LightProperties.getLightProperties(light, worldPoint, tempLightProps);
    tempLightDir.set(tempLightProps.direction);

    // Diffuse - AFRICANKENTE ILE AYNI
    double NdotL = Math.max(0, worldNormal.dot(tempLightDir));
    double diffuseFactor = 0.7 * NdotL * tempLightProps.intensity;
    int diffuse = ColorUtil.multiply(finalColor, (float) diffuseFactor);
    diffuse = ColorUtil.multiplyColors(diffuse, tempLightProps.color, 1.0);

    // View direction
    tempViewDir.set(viewPos);
    tempViewDir.subtract(worldPoint);
    tempViewDir.normalize();

    // Halfway vector
    tempHalfway.set(tempLightDir);
    tempHalfway.add(tempViewDir);
    tempHalfway.normalize();

    // Specular
    double NdotH = Math.max(0, worldNormal.dot(tempHalfway));
    double specularIntensity = Math.pow(NdotH, 128.0);
    double specularFactor = glossiness * specularIntensity * tempLightProps.intensity;
    int specular = ColorUtil.multiply(WHITE_REFERENCE, (float) specularFactor);
    specular = ColorUtil.multiplyColors(specular, tempLightProps.color, 1.0);

    // Ambient
    double ambientFactor = 0.3 * tempLightProps.intensity;
    int ambient = ColorUtil.multiply(finalColor, (float) ambientFactor);
    ambient = ColorUtil.multiplyColors(ambient, tempLightProps.color, 1.0);

    // Combine
    int result = ColorUtil.add(ambient, diffuse);
    result = ColorUtil.add(result, specular);

    return ColorUtil.clampColor(result);
  }

  private int generateCiniPattern(Point3 localPoint, int base, int pattern) {
    double scale = 8.0;
    double x = localPoint.x * scale;
    double y = localPoint.y * scale;
    double z = localPoint.z * scale;

    double flower = Math.sin(x * 2.0) * Math.cos(y * 2.0) + Math.sin(x * 3.0 + y * 2.0) * 0.5;

    double border = Math.max(Math.abs(Math.sin(x * 4.0)), Math.abs(Math.sin(y * 4.0)));

    double distanceSq = x * x + y * y;
    double medallion =
        Math.sin(Math.sqrt(distanceSq) * 6.0) * 0.7 + Math.cos(x * 5.0) * Math.sin(y * 5.0) * 0.3;

    double patternMask = Math.max(flower, Math.max(border * 0.3, medallion));

    if (patternMask > 0.4) {
      return pattern;
    } else if (patternMask > 0.2) {
      return ColorUtil.blendColors(base, pattern, 0.5);
    }

    return base;
  }

  @Override
  public double getReflectivity() {
    return glossiness * 0.3;
  }

  @Override
  public double getIndexOfRefraction() {
    return 1.4;
  }

  @Override
  public double getTransparency() {
    return 0.0;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("CiniMaterial cinimaterial {\n");
    sb.append("        baseColor = ")
        .append(ColorUtil.toColorString(baseColorPacked))
        .append(";\n");
    sb.append("        patternColor = ")
        .append(ColorUtil.toColorString(patternColorPacked))
        .append(";\n");
    sb.append("        glossiness = ").append(glossiness).append(";\n");
    sb.append("    }");
    return sb.toString();
  }
}
