package net.murat.material;

import net.murat.light.*;
import net.murat.math.*;
import net.murat.util.ColorUtil;

/**
 * KilimRosemalingMaterial - Cultural fusion of Turkish Kilim and Norwegian Rosemaling.
 * Zero-allocation using packed ARGB integers.
 */
public class KilimRosemalingMaterial implements Material {
  private final int kilimColorPacked;
  private final int rosemalingColorPacked;
  private final int accentColorPacked;
  private final double patternIntensity;

  private Matrix4 objectTransform;
  private Matrix4 inverseTransform;
  private Matrix4 inverseTransposeTransform;

  // Pre-cached color components
  private final int kilimR, kilimG, kilimB;
  private final int rosemalingR, rosemalingG, rosemalingB;
  private final int accentR, accentG, accentB;

  private final double ambientCoeff = 0.5;
  private final double diffuseCoeff = 0.85;
  private final double specularCoeff = 0.1;
  private final double shininess = 15.0;
  private final double reflectivity = 0.06;
  private final double ior = 1.5;
  private final double transparency = 0.0;

  // ===== GC DOSTU: PRE-ALLOCATED TEMP OBJECTS =====
  private final Point3 tempLocalPoint = new Point3();
  private final Vector3 tempLocalNormal = new Vector3();
  private final Vector3 tempTransformedNormal = new Vector3();
  private final Vector3 tempViewDir = new Vector3();
  private final Vector3 tempLightDir = new Vector3();
  private final Vector3 tempReflectDir = new Vector3();
  private final LightProperties tempLightProps = new LightProperties();

  public KilimRosemalingMaterial() {
    this(0xFFC40000, 0xFF006464, 0xFFFFD700, 0.7);
  }

  public KilimRosemalingMaterial(
      int kilimColor, int rosemalingColor, int accentColor, double patternIntensity) {
    this.kilimColorPacked = kilimColor;
    this.rosemalingColorPacked = rosemalingColor;
    this.accentColorPacked = accentColor;
    this.patternIntensity = Math.max(0, Math.min(1, patternIntensity));

    this.kilimR = (kilimColor >> 16) & 0xFF;
    this.kilimG = (kilimColor >> 8) & 0xFF;
    this.kilimB = kilimColor & 0xFF;

    this.rosemalingR = (rosemalingColor >> 16) & 0xFF;
    this.rosemalingG = (rosemalingColor >> 8) & 0xFF;
    this.rosemalingB = rosemalingColor & 0xFF;

    this.accentR = (accentColor >> 16) & 0xFF;
    this.accentG = (accentColor >> 8) & 0xFF;
    this.accentB = accentColor & 0xFF;

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

    // 3. Calculate fusion pattern
    int surfaceColor = calculateFusionPattern(tempLocalPoint);

    // 4. Light properties - ZERO ALLOCATION
    LightProperties.getLightProperties(light, worldPoint, tempLightProps);
    tempLightDir.set(tempLightProps.direction);

    // 5. Ambient check
    int ambient = ColorUtil.multiplyColors(surfaceColor, tempLightProps.color, ambientCoeff);

    if (light instanceof MuratAmbientLight) {
      return ambient;
    }

    // 6. Diffuse - inline dot product
    double NdotL =
        tempLocalNormal.x * tempLightDir.x
            + tempLocalNormal.y * tempLightDir.y
            + tempLocalNormal.z * tempLightDir.z;
    if (NdotL < 0.0) NdotL = 0.0;

    int diffuse =
        ColorUtil.multiplyColors(
            surfaceColor, tempLightProps.color, diffuseCoeff * NdotL * tempLightProps.intensity);

    // 7. View direction - inline
    double vX = viewerPos.x - worldPoint.x;
    double vY = viewerPos.y - worldPoint.y;
    double vZ = viewerPos.z - worldPoint.z;
    double vInvLen = 1.0 / Math.sqrt(vX * vX + vY * vY + vZ * vZ);
    tempViewDir.set(vX * vInvLen, vY * vInvLen, vZ * vInvLen);

    // 8. Reflection direction - ZERO ALLOCATION
    tempReflectDir.set(tempLightDir);
    tempReflectDir.negate();
    tempReflectDir.reflect(tempLocalNormal, tempReflectDir);

    // 9. Specular - inline dot product
    double RdotV =
        tempReflectDir.x * tempViewDir.x
            + tempReflectDir.y * tempViewDir.y
            + tempReflectDir.z * tempViewDir.z;
    if (RdotV < 0.0) RdotV = 0.0;

    double specFactor = Math.pow(RdotV, shininess) * tempLightProps.intensity;
    int specular =
        ColorUtil.multiplyColors(ColorUtil.WHITE, tempLightProps.color, specularCoeff * specFactor);

    // 10. Combine
    return ColorUtil.combineColors(ambient, diffuse, specular);
  }

  private int calculateFusionPattern(Point3 point) {
    double x = point.x * 12.0;
    double y = point.y * 12.0;
    double z = point.z * 12.0;

    // Kilim geometric patterns (Turkish)
    double kilimPattern1 = Math.abs(Math.sin(x * 2.0) + Math.cos(y * 2.0));
    double kilimPattern2 = (Math.floor(x * 1.2) + Math.floor(y * 1.2)) % 2.0;
    if (kilimPattern2 < 0.0) kilimPattern2 += 2.0;
    double kilimPattern3 = Math.abs(Math.sin(x * 3.0 + y * 2.0));

    // Rosemaling flower patterns (Norwegian)
    double rosePattern1 = Math.sin(x * 1.5) * Math.cos(y * 1.5 + Math.sin(z * 0.8));
    double rosePattern2 = Math.abs(Math.sin(x * 2.5 + y * 1.8) + Math.cos(y * 2.2));
    double rosePattern3 = Math.abs(Math.cos(x * 1.8 + y * 2.0 + z * 1.2));

    double weightFactor = 0.5 * patternIntensity;

    double combinedPattern =
        (kilimPattern1 * 0.2 + kilimPattern2 * 0.15 + kilimPattern3 * 0.15) * weightFactor
            + (rosePattern1 * 0.2 + rosePattern2 * 0.15 + rosePattern3 * 0.15) * weightFactor;

    double normalizedPattern = (combinedPattern + 1.0) * 0.5;

    int r, g, b;

    if (normalizedPattern < 0.3) {
      r = kilimR;
      g = kilimG;
      b = kilimB;
    } else if (normalizedPattern < 0.6) {
      double intensity = (normalizedPattern - 0.3) * 3.3333333333333335;
      int lightR = (int) (rosemalingR + (255 - rosemalingR) * 0.2);
      int lightG = (int) (rosemalingG + (255 - rosemalingG) * 0.2);
      int lightB = (int) (rosemalingB + (255 - rosemalingB) * 0.2);
      r = (int) (rosemalingR + (lightR - rosemalingR) * intensity);
      g = (int) (rosemalingG + (lightG - rosemalingG) * intensity);
      b = (int) (rosemalingB + (lightB - rosemalingB) * intensity);
    } else if (normalizedPattern < 0.8) {
      r = accentR;
      g = accentG;
      b = accentB;
    } else {
      double intensity = (normalizedPattern - 0.8) * 5.0;
      int borderR = (kilimR + rosemalingR) / 2;
      int borderG = (kilimG + rosemalingG) / 2;
      int borderB = (kilimB + rosemalingB) / 2;
      double darkFactor = 1.0 - intensity * 0.4;
      r = (int) (borderR * darkFactor);
      g = (int) (borderG * darkFactor);
      b = (int) (borderB * darkFactor);
    }

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
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("KilimRosemalingMaterial kilimrosemalingmaterial {\n");
    sb.append("        kilimColor = ")
        .append(ColorUtil.toColorString(kilimColorPacked))
        .append(";\n");
    sb.append("        rosemalingColor = ")
        .append(ColorUtil.toColorString(rosemalingColorPacked))
        .append(";\n");
    sb.append("        accentColor = ")
        .append(ColorUtil.toColorString(accentColorPacked))
        .append(";\n");
    sb.append("        patternIntensity = ").append(patternIntensity).append(";\n");
    sb.append("    }");
    return sb.toString();
  }
}
