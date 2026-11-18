package net.murat.material;

import net.murat.light.*;
import net.murat.math.*;
import net.murat.util.ColorUtil;

public class CalligraphyRuneMaterial implements Material {
  private final int parchmentColorPacked;
  private final int inkColorPacked;
  private final int goldLeafColorPacked;
  private final double writingIntensity;
  private Matrix4 objectTransform;

  // Pre-computed inverse matrix to save billions of dynamic CPU iterations inside the loop
  private Matrix4 inverseTransform;
  private Matrix4 inverseTransposeTransform;

  // Pre-allocated static global constants to achieve clean zero-allocation pipeline paths
  private static final int WHITE_REFERENCE = ColorUtil.WHITE;

  private final double ambientCoeff = 0.45;
  private final double diffuseCoeff = 0.8;
  private final double specularCoeff = 0.18;
  private final double shininess = 25.0;
  private final double reflectivity = 0.1;
  private final double ior = 1.6;
  private final double transparency = 0.0;

  // ===== GC DOSTU: PRE-ALLOCATED TEMP OBJECTS =====
  private final Point3 tempLocalPoint = new Point3();
  private final Vector3 tempLocalNormal = new Vector3();
  private final Vector3 tempTransformedNormal = new Vector3();
  private final Vector3 tempViewDir = new Vector3();
  private final Vector3 tempLightDir = new Vector3();
  private final Vector3 tempReflectDir = new Vector3();
  private final Point3 tempViewerPoint = new Point3();
  private final Vector3 tempViewDirPattern = new Vector3();
  private final LightProperties tempLightProps = new LightProperties();

  public CalligraphyRuneMaterial() {
    this(0xFFF5DEB3, 0xFF2F4F4F, 0xFFFFD700, 0.65);
  }

  public CalligraphyRuneMaterial(
      int parchmentColor, int inkColor, int goldLeafColor, double writingIntensity) {
    this.parchmentColorPacked = parchmentColor;
    this.inkColorPacked = inkColor;
    this.goldLeafColorPacked = goldLeafColor;
    this.writingIntensity = Math.max(0.0, Math.min(1.0, writingIntensity));
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
    // 1. Transform to local space - ZERO ALLOCATION (FIXED!)
    inverseTransform.transformPoint(worldPoint, tempLocalPoint);

    // 2. Transform normal - ZERO ALLOCATION (FIXED!)
    inverseTransposeTransform.transformDirection(worldNormal, tempTransformedNormal);
    tempLocalNormal.set(tempTransformedNormal);
    tempLocalNormal.normalize();

    // 3. Calculate writing pattern - ZERO ALLOCATION (FIXED!)
    int surfaceColor = calculateWritingPattern(tempLocalPoint, tempLocalNormal);

    // 4. Get light properties - ZERO ALLOCATION (FIXED!)
    LightProperties.getLightProperties(light, worldPoint, tempLightProps);

    // 5. Ambient
    int ambient = ColorUtil.multiplyColors(surfaceColor, tempLightProps.color, ambientCoeff);

    if (light instanceof MuratAmbientLight) {
      return ambient;
    }

    // 6. Light direction
    tempLightDir.set(tempLightProps.direction);

    // 7. Diffuse
    double NdotL = Math.max(0.0, tempLocalNormal.dot(tempLightDir));
    int diffuse =
        ColorUtil.multiplyColors(
            surfaceColor, tempLightProps.color, diffuseCoeff * NdotL * tempLightProps.intensity);

    // 8. View direction - ZERO ALLOCATION (FIXED!)
    tempViewerPoint.set(viewerPos);
    tempViewDir.set(tempViewerPoint);
    tempViewDir.subtract(worldPoint);
    tempViewDir.normalize();

    // 9. Reflection direction - ZERO ALLOCATION (FIXED!)
    tempReflectDir.set(tempLightDir);
    tempReflectDir.negate();
    tempReflectDir.reflect(tempLocalNormal, tempReflectDir);

    // 10. Specular
    double RdotV = Math.max(0.0, tempReflectDir.dot(tempViewDir));
    double specFactor = Math.pow(RdotV, shininess) * tempLightProps.intensity;

    int specular =
        ColorUtil.multiplyColors(
            goldLeafColorPacked, tempLightProps.color, specularCoeff * specFactor);

    return ColorUtil.combineColors(ambient, diffuse, specular);
  }

  private int calculateWritingPattern(Point3 point, Vector3 normal) {
    double x = point.x * 15.0;
    double y = point.y * 15.0;
    double z = point.z * 15.0;

    // Islamic calligraphy flowing patterns
    double calligraphy1 = Math.sin(x * 1.2 + Math.cos(y * 0.8) * 2.0);
    double calligraphy2 = Math.abs(Math.cos(x * 1.5 + y * 1.0) + Math.sin(y * 1.2 + z * 0.7));
    double calligraphy3 = Math.sin(x * 2.0 + y * 1.5) * Math.cos(y * 1.0 + z * 0.5);

    // Viking rune angular patterns
    double rune1 = Math.abs(Math.sin(x * 2.5) * Math.cos(y * 2.0));
    double rune2 = (Math.floor(x * 0.8) + Math.floor(y * 0.8)) % 2.5;
    double rune3 = Math.abs(Math.sin(x * 3.0 + y * 1.7) + Math.cos(y * 2.3));

    // Cultural fusion writing pattern weighting ratios
    double calligraphyWeight = 0.5 * writingIntensity;
    double runeWeight = 0.5 * writingIntensity;

    double combinedPattern =
        (calligraphy1 * 0.2 + calligraphy2 * 0.15 + calligraphy3 * 0.15) * calligraphyWeight
            + (rune1 * 0.2 + rune2 * 0.15 + rune3 * 0.15) * runeWeight;

    double normalizedPattern = (combinedPattern + 1.0) * 0.5;

    // View-dependent effect for gold leaf - ZERO ALLOCATION (FIXED!)
    tempViewDirPattern.set(0, 0, 1);
    double viewEffect = Math.abs(tempViewDirPattern.dot(normal)) * 0.5 + 0.5;

    if (normalizedPattern < 0.4) {
      double ageEffect = normalizedPattern / 0.4;
      return ColorUtil.darkenColor(parchmentColorPacked, ageEffect * 0.2);
    } else if (normalizedPattern < 0.7) {
      double intensity = (normalizedPattern - 0.4) / 0.3;
      int writingInk = ColorUtil.darkenColor(inkColorPacked, intensity * 0.3);
      return ColorUtil.addColorVariation(writingInk, intensity);
    } else {
      double intensity = (normalizedPattern - 0.7) / 0.3;
      int gold =
          ColorUtil.blendColors(
              goldLeafColorPacked, ColorUtil.lightenColor(goldLeafColorPacked, 0.3), intensity);
      return ColorUtil.multiplyColors(gold, WHITE_REFERENCE, viewEffect);
    }
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
    sb.append("CalligraphyRuneMaterial calligraphyrunematerial {\n");
    sb.append("        parchmentColor = ")
        .append(ColorUtil.toColorString(parchmentColorPacked))
        .append(";\n");
    sb.append("        inkColor = ").append(ColorUtil.toColorString(inkColorPacked)).append(";\n");
    sb.append("        goldLeafColor = ")
        .append(ColorUtil.toColorString(goldLeafColorPacked))
        .append(";\n");
    sb.append("        writingIntensity = ").append(writingIntensity).append(";\n");
    sb.append("    }");
    return sb.toString();
  }
}
