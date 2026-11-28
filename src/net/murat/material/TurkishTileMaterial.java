package net.murat.material;

import net.murat.light.*;
import net.murat.math.*;
import net.murat.util.ColorUtil;

/**
 * TurkishTileMaterial - Traditional Turkish tile pattern with geometric motifs. Zero-allocation
 * using packed ARGB integers.
 */
public class TurkishTileMaterial implements Material {
  private final int baseColorPacked;
  private final int patternColorPacked;
  private final double tileSize;
  private Matrix4 objectTransform;
  private Matrix4 inverseTransform;
  private Matrix4 inverseTransposeTransform;

  // Pre-cached color components
  private final int baseR, baseG, baseB;
  private final int patternR, patternG, patternB;

  private final double ambientCoeff = 0.4;
  private final double diffuseCoeff = 0.6;
  private final double specularCoeff = 0.8;
  private final double shininess = 80.0;
  private final int specularColorPacked = 0xFFFFFFFF;
  private final double reflectivity = 0.3;
  private final double ior = 1.5;
  private final double transparency = 0.0;

  // ===== GC DOSTU: PRE-ALLOCATED TEMP OBJECTS =====
  private final Point3 tempLocalPoint = new Point3();
  private final Vector3 tempLocalNormal = new Vector3();
  private final Vector3 tempTransformedNormal = new Vector3();
  private final Vector3 tempViewDir = new Vector3();
  private final Vector3 tempLightDir = new Vector3();
  private final Vector3 tempReflectDir = new Vector3();
  private final Point3 tempViewerPoint = new Point3();
  private final LightProperties tempLightProps = new LightProperties();

  public TurkishTileMaterial() {
    this(0xFF0066CC, 0xFFFFFFFF, 2.0);
  }

  public TurkishTileMaterial(int baseColor, int patternColor, double tileSize) {
    this.baseColorPacked = baseColor;
    this.patternColorPacked = patternColor;
    this.tileSize = Math.max(0.5, tileSize);

    this.baseR = (baseColor >> 16) & 0xFF;
    this.baseG = (baseColor >> 8) & 0xFF;
    this.baseB = baseColor & 0xFF;

    this.patternR = (patternColor >> 16) & 0xFF;
    this.patternG = (patternColor >> 8) & 0xFF;
    this.patternB = patternColor & 0xFF;

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

  private int calculatePatternColor(Point3 localPoint, Vector3 normal) {
    // UV mapping based on dominant normal
    double u, v;
    double absX = Math.abs(normal.x);
    double absY = Math.abs(normal.y);
    double absZ = Math.abs(normal.z);

    if (absX > absY && absX > absZ) {
      u = localPoint.z * tileSize;
      v = localPoint.y * tileSize;
    } else if (absY > absX && absY > absZ) {
      u = localPoint.x * tileSize;
      v = localPoint.z * tileSize;
    } else {
      u = localPoint.x * tileSize;
      v = localPoint.y * tileSize;
    }

    double tileU = u - Math.floor(u);
    double tileV = v - Math.floor(v);

    return createCleanTurkishPattern(tileU, tileV);
  }

  private int createCleanTurkishPattern(double u, double v) {
    double border = 0.1;
    if (u < border || u > 1.0 - border || v < border || v > 1.0 - border) {
      return patternColorPacked;
    }

    double centerX = 0.5;
    double centerY = 0.5;
    double diffX = u - centerX;
    double diffY = v - centerY;
    double dist = Math.sqrt(diffX * diffX + diffY * diffY);

    if (dist < 0.15) {
      double angle = Math.atan2(diffY, diffX);
      double petal = Math.sin(4.0 * angle);
      petal = Math.abs(petal);

      if (petal > 0.9 && dist > 0.05) {
        return patternColorPacked;
      }

      if (dist < 0.05) {
        return patternColorPacked;
      }

      return baseColorPacked;
    }

    double diag1 = Math.abs(u - v);
    double diag2 = Math.abs(u + v - 1.0);
    if (diag1 < 0.02 || diag2 < 0.02) {
      return patternColorPacked;
    }

    double lineH = Math.abs(u - 0.5);
    double lineV = Math.abs(v - 0.5);
    if (lineH < 0.01 || lineV < 0.01) {
      return patternColorPacked;
    }

    // Corner 0: (0, 0)
    double cDist0 = Math.sqrt(u * u + v * v);
    if (cDist0 < 0.3) {
      double cornerAngle = Math.atan2(v, u);
      double star = Math.abs(Math.sin(5.0 * cornerAngle));
      if (star > 0.95 && cDist0 > 0.15) return patternColorPacked;
    }

    // Corner 1: (1, 0)
    double invU = 1.0 - u;
    double cDist1 = Math.sqrt(invU * invU + v * v);
    if (cDist1 < 0.3) {
      double cornerAngle = Math.atan2(v, u - 1.0);
      double star = Math.abs(Math.sin(5.0 * cornerAngle));
      if (star > 0.95 && cDist1 > 0.15) return patternColorPacked;
    }

    // Corner 2: (0, 1)
    double invV = 1.0 - v;
    double cDist2 = Math.sqrt(u * u + invV * invV);
    if (cDist2 < 0.3) {
      double cornerAngle = Math.atan2(v - 1.0, u);
      double star = Math.abs(Math.sin(5.0 * cornerAngle));
      if (star > 0.95 && cDist2 > 0.15) return patternColorPacked;
    }

    // Corner 3: (1, 1)
    double cDist3 = Math.sqrt(invU * invU + invV * invV);
    if (cDist3 < 0.3) {
      double cornerAngle = Math.atan2(v - 1.0, u - 1.0);
      double star = Math.abs(Math.sin(5.0 * cornerAngle));
      if (star > 0.95 && cDist3 > 0.15) return patternColorPacked;
    }

    return baseColorPacked;
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

    // 3. Calculate pattern
    int patternColor = calculatePatternColor(tempLocalPoint, tempLocalNormal);
    int surfR = (patternColor >> 16) & 0xFF;
    int surfG = (patternColor >> 8) & 0xFF;
    int surfB = patternColor & 0xFF;

    // 4. Light properties - ZERO ALLOCATION
    LightProperties.getLightProperties(light, worldPoint, tempLightProps);
    tempLightDir.set(tempLightProps.direction);

    int lightColor = tempLightProps.color;
    int lightR = (lightColor >> 16) & 0xFF;
    int lightG = (lightColor >> 8) & 0xFF;
    int lightB = lightColor & 0xFF;
    double intensity = tempLightProps.intensity;

    // 5. Ambient check
    int ambient = ColorUtil.multiplyColors(patternColor, tempLightProps.color, ambientCoeff);

    if (light instanceof MuratAmbientLight) {
      return ambient;
    }

    // 6. View direction - ZERO ALLOCATION
    tempViewerPoint.set(viewerPos);
    tempViewDir.set(tempViewerPoint);
    tempViewDir.subtract(worldPoint);
    tempViewDir.normalize();

    // 7. Diffuse
    double NdotL = Math.max(0.0, tempLocalNormal.dot(tempLightDir));
    double diffFactor = diffuseCoeff * NdotL * intensity;
    int diffR = (int) (surfR * diffFactor * lightR / 255.0);
    int diffG = (int) (surfG * diffFactor * lightG / 255.0);
    int diffB = (int) (surfB * diffFactor * lightB / 255.0);

    // 8. Reflection direction - ZERO ALLOCATION
    tempReflectDir.set(tempLightDir);
    tempReflectDir.negate();
    tempReflectDir.reflect(tempLocalNormal, tempReflectDir);

    // 9. Specular
    double RdotV = Math.max(0.0, tempReflectDir.dot(tempViewDir));
    double specFactor = Math.pow(RdotV, shininess) * specularCoeff * intensity;

    int specR = (int) (((specularColorPacked >> 16) & 0xFF) * specFactor * lightR / 255.0);
    int specG = (int) (((specularColorPacked >> 8) & 0xFF) * specFactor * lightG / 255.0);
    int specB = (int) ((specularColorPacked & 0xFF) * specFactor * lightB / 255.0);

    specR = clamp(specR);
    specG = clamp(specG);
    specB = clamp(specB);

    // 10. Combine
    int r = ((ambient >> 16) & 0xFF) + diffR + specR;
    int g = ((ambient >> 8) & 0xFF) + diffG + specG;
    int b = (ambient & 0xFF) + diffB + specB;

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
    sb.append("TurkishTileMaterial turkishtilematerial {\n");
    sb.append("        baseColor = ")
        .append(ColorUtil.toColorString(baseColorPacked))
        .append(";\n");
    sb.append("        patternColor = ")
        .append(ColorUtil.toColorString(patternColorPacked))
        .append(";\n");
    sb.append("        tileSize = ").append(tileSize).append(";\n");
    sb.append("    }");
    return sb.toString();
  }
}
