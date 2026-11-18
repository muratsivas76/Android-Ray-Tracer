package net.murat.material;

import net.murat.light.*;
import net.murat.math.*;
import net.murat.util.ColorUtil;

/**
 * NorwegianRoseMaterial - Rosemaling pattern on wood grain. Zero-allocation using packed ARGB
 * integers.
 */
public class NorwegianRoseMaterial implements Material {
  private final int woodColorPacked;
  private final int roseColorPacked;
  private Matrix4 objectTransform;
  private Matrix4 inverseTransform;
  private Matrix4 inverseTransposeTransform;

  // Pre-cached color components
  private final int woodR, woodG, woodB;
  private final int roseR, roseG, roseB;

  private final double patternScale;

  private final double ambientCoeff = 0.4;
  private final double diffuseCoeff = 0.7;
  private final double specularCoeff = 0.2;
  private final double shininess = 30.0;
  private final int specularColorPacked = 0xFFDCDCDC;
  private final double reflectivity = 0.1;
  private final double ior = 1.4;
  private final double transparency = 0.0;

  private static final double TWO_PI = Math.PI * 2;
  private static final double THREE_PI = Math.PI * 3;

  // ===== GC DOSTU: PRE-ALLOCATED TEMP OBJECTS =====
  private final Point3 tempLocalPoint = new Point3();
  private final Vector3 tempLocalNormal = new Vector3();
  private final Vector3 tempTransformedNormal = new Vector3();
  private final Vector3 tempViewDir = new Vector3();
  private final Vector3 tempLightDir = new Vector3();
  private final Vector3 tempReflectDir = new Vector3();
  private final Point3 tempViewerPoint = new Point3();
  private final LightProperties tempLightProps = new LightProperties();

  public NorwegianRoseMaterial() {
    this(0xFF654321, 0xFFC83232, 4.0);
  }

  public NorwegianRoseMaterial(int woodColor, int roseColor) {
    this(woodColor, roseColor, 4.0);
  }

  public NorwegianRoseMaterial(int woodColor, int roseColor, double patternScale) {
    this.woodColorPacked = woodColor;
    this.roseColorPacked = roseColor;
    this.patternScale = patternScale;

    this.woodR = (woodColor >> 16) & 0xFF;
    this.woodG = (woodColor >> 8) & 0xFF;
    this.woodB = woodColor & 0xFF;

    this.roseR = (roseColor >> 16) & 0xFF;
    this.roseG = (roseColor >> 8) & 0xFF;
    this.roseB = roseColor & 0xFF;

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

    // 1. Transform to local space
    inverseTransform.transformPoint(worldPoint, tempLocalPoint);

    // 2. Transform normal
    inverseTransposeTransform.transformDirection(worldNormal, tempTransformedNormal);
    tempLocalNormal.set(tempTransformedNormal);
    tempLocalNormal.normalize();

    // 3. Calculate rosemal pattern
    int surfaceColor = evaluateRosemalPattern(tempLocalPoint, tempLocalNormal);
    int surfR = (surfaceColor >> 16) & 0xFF;
    int surfG = (surfaceColor >> 8) & 0xFF;
    int surfB = surfaceColor & 0xFF;

    // 4. Light properties - ZERO ALLOCATION
    LightProperties.getLightProperties(light, worldPoint, tempLightProps);
    tempLightDir.set(tempLightProps.direction);

    // 5. Light color
    int lightColor = tempLightProps.color;
    int lightR = (lightColor >> 16) & 0xFF;
    int lightG = (lightColor >> 8) & 0xFF;
    int lightB = lightColor & 0xFF;
    double intensity = tempLightProps.intensity;

    // 6. Ambient check
    int ambient = ColorUtil.multiplyColors(surfaceColor, tempLightProps.color, ambientCoeff);

    if (light instanceof MuratAmbientLight) {
      return ambient;
    }

    // 7. Diffuse
    double NdotL = Math.max(0.0, tempLocalNormal.dot(tempLightDir));
    double diffFactor = diffuseCoeff * NdotL * intensity;
    int diffR = (int) (surfR * diffFactor * lightR / 255.0);
    int diffG = (int) (surfG * diffFactor * lightG / 255.0);
    int diffB = (int) (surfB * diffFactor * lightB / 255.0);

    // 8. View direction - ZERO ALLOCATION
    tempViewerPoint.set(viewerPos);
    tempViewDir.set(tempViewerPoint);
    tempViewDir.subtract(worldPoint);
    tempViewDir.normalize();

    // 9. Reflection direction - ZERO ALLOCATION
    tempReflectDir.set(tempLightDir);
    tempReflectDir.negate();
    tempReflectDir.reflect(tempLocalNormal, tempReflectDir);

    // 10. Specular
    double RdotV = Math.max(0.0, tempReflectDir.dot(tempViewDir));
    double specFactor = Math.pow(RdotV, shininess) * specularCoeff * intensity;

    int specR = (int) (((specularColorPacked >> 16) & 0xFF) * specFactor * lightR / 255.0);
    int specG = (int) (((specularColorPacked >> 8) & 0xFF) * specFactor * lightG / 255.0);
    int specB = (int) ((specularColorPacked & 0xFF) * specFactor * lightB / 255.0);

    specR = specR > 255 ? 255 : (specR < 0 ? 0 : specR);
    specG = specG > 255 ? 255 : (specG < 0 ? 0 : specG);
    specB = specB > 255 ? 255 : (specB < 0 ? 0 : specB);

    // 11. Combine
    int r = ((ambient >> 16) & 0xFF) + diffR + specR;
    int g = ((ambient >> 8) & 0xFF) + diffG + specG;
    int b = (ambient & 0xFF) + diffB + specB;

    r = clamp(r);
    g = clamp(g);
    b = clamp(b);

    return (0xFF000000 | (r << 16) | (g << 8) | b);
  }

  private int evaluateRosemalPattern(Point3 localPoint, Vector3 localNormal) {
    // Calculate UV coordinates based on dominant normal axis
    double u, v;
    double absNx = Math.abs(localNormal.x);
    double absNy = Math.abs(localNormal.y);
    double absNz = Math.abs(localNormal.z);

    if (absNx > absNy && absNx > absNz) {
      u = localPoint.y * patternScale;
      v = localPoint.z * patternScale;
    } else if (absNy > absNx && absNy > absNz) {
      u = localPoint.x * patternScale;
      v = localPoint.z * patternScale;
    } else {
      u = localPoint.x * patternScale;
      v = localPoint.y * patternScale;
    }

    double tileU = (u % 1.0 + 1.0) % 1.0;
    double tileV = (v % 1.0 + 1.0) % 1.0;

    boolean useRoseColor = false;

    // 1. Border layout check
    if (tileU < 0.1 || tileU > 0.9 || tileV < 0.1 || tileV > 0.9) {
      useRoseColor = true;
    }

    // 2. Central flower/medallion
    if (!useRoseColor) {
      double dx = tileU - 0.5;
      double dy = tileV - 0.5;
      double distToCenterSq = dx * dx + dy * dy;

      if (distToCenterSq < 0.04) {
        double angle = Math.atan2(dy, dx);
        double petal = Math.sin(angle * 6.0) * 0.5 + 0.5;
        if (petal > 0.7 && distToCenterSq > 0.01) {
          useRoseColor = true;
        }
      }
    }

    // 3. Scrollwork patterns
    if (!useRoseColor) {
      double diffUV = Math.abs(tileU - tileV);
      double sumUV = Math.abs(tileU + tileV - 1.0);

      if (diffUV < 0.03
          || sumUV < 0.03
          || Math.sin(tileU * THREE_PI) > 0.8
          || Math.sin(tileV * THREE_PI) > 0.8
          || Math.cos(tileU * TWO_PI) > 0.7
          || Math.cos(tileV * TWO_PI) > 0.7) {
        useRoseColor = true;
      }
    }

    // 4. Corner elements
    if (!useRoseColor) {
      double tileU25 = Math.abs(tileU - 0.25);
      double tileU75 = Math.abs(tileU - 0.75);
      double tileV25 = Math.abs(tileV - 0.25);
      double tileV75 = Math.abs(tileV - 0.75);

      if ((tileU25 < 0.04 && tileV25 < 0.04)
          || (tileU75 < 0.04 && tileV75 < 0.04)
          || (tileU25 < 0.04 && tileV75 < 0.04)
          || (tileU75 < 0.04 && tileV25 < 0.04)) {
        useRoseColor = true;
      }
    }

    if (useRoseColor) {
      return (0xFF000000 | (roseR << 16) | (roseG << 8) | roseB);
    }

    // 5. Wood grain
    double grain = Math.sin(u * 3.0) * 0.15 + Math.sin(v * 1.5) * 0.1;
    double grainFactor = 0.85 + grain;

    int r = (int) (woodR * grainFactor);
    int g = (int) (woodG * grainFactor);
    int b = (int) (woodB * grainFactor);

    r = clamp(r);
    g = clamp(g);
    b = clamp(b);

    return (0xFF000000 | (r << 16) | (g << 8) | b);
  }

  private int clamp(int value) {
    return Math.max(0, Math.min(255, value));
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
    sb.append("NorwegianRoseMaterial norwegianrosematerial {\n");
    sb.append("        woodColor = ")
        .append(ColorUtil.toColorString(woodColorPacked))
        .append(";\n");
    sb.append("        roseColor = ")
        .append(ColorUtil.toColorString(roseColorPacked))
        .append(";\n");
    sb.append("        patternScale = ").append(patternScale).append(";\n");
    sb.append("    }");
    return sb.toString();
  }
}
