package net.murat.material;

import net.murat.light.Light;
import net.murat.light.LightProperties;
import net.murat.light.MuratAmbientLight;
import net.murat.math.*;
import net.murat.util.ColorUtil;

/**
 * MoonSurfaceMaterial - Procedural lunar surface with crater pattern. Zero-allocation using packed
 * ARGB integers.
 */
public class MoonSurfaceMaterial implements Material {
  private final int moonColorPacked;
  private Matrix4 objectInverseTransform;
  private Matrix4 inverseTransposeTransform;

  // Pre-cached color components
  private final int moonR, moonG, moonB;

  private final double ambientCoefficient = 0.3;
  private final double diffuseCoefficient = 0.9;
  private final double specularCoefficient = 0.05;
  private final double shininess = 10.0;
  private final double reflectivity = 0.02;
  private final double ior = 1.0;
  private final double transparency = 0.0;

  // ===== GC DOSTU: PRE-ALLOCATED TEMP OBJECTS =====
  private final Point3 tempLocalPoint = new Point3();
  private final Vector3 tempLocalNormal = new Vector3();
  private final Vector3 tempTransformedNormal = new Vector3();
  private final Vector3 tempLightDir = new Vector3();
  private final LightProperties tempLightProps = new LightProperties();

  public MoonSurfaceMaterial() {
    this(0xFFA9A9A9);
  }

  public MoonSurfaceMaterial(int moonColor) {
    this.moonColorPacked = moonColor;
    this.moonR = (moonColor >> 16) & 0xFF;
    this.moonG = (moonColor >> 8) & 0xFF;
    this.moonB = moonColor & 0xFF;
    this.objectInverseTransform = new Matrix4();
    this.inverseTransposeTransform = new Matrix4();
  }

  @Override
  public void setObjectTransform(Matrix4 tm) {
    if (tm == null) tm = new Matrix4();
    this.objectInverseTransform = tm;
    this.inverseTransposeTransform = tm.inverseTransposeForNormal();
  }

  @Override
  public int getColorAt(Point3 worldPoint, Vector3 worldNormal, Light light, Point3 viewerPos) {
    if (objectInverseTransform == null) {
      return 0xFF000000;
    }

    // 1. Transform to local space - ZERO ALLOCATION
    objectInverseTransform.transformPoint(worldPoint, tempLocalPoint);

    // 2. Transform normal - ZERO ALLOCATION
    inverseTransposeTransform.transformDirection(worldNormal, tempTransformedNormal);
    tempLocalNormal.set(tempTransformedNormal);
    tempLocalNormal.normalize();

    // 3. Procedural crater pattern
    double craterPattern =
        Math.sin(tempLocalPoint.x * 50.0)
            * Math.cos(tempLocalPoint.y * 50.0)
            * Math.sin(tempLocalPoint.z * 50.0);
    double surfaceVariation = 0.7 + Math.abs(craterPattern) * 0.3;

    // 4. Surface color variation
    int surfR = (int) (moonR * surfaceVariation);
    int surfG = (int) (moonG * surfaceVariation);
    int surfB = (int) (moonB * surfaceVariation);
    surfR = surfR > 255 ? 255 : (surfR < 0 ? 0 : surfR);
    surfG = surfG > 255 ? 255 : (surfG < 0 ? 0 : surfG);
    surfB = surfB > 255 ? 255 : (surfB < 0 ? 0 : surfB);
    int surfaceColor = (0xFF000000 | (surfR << 16) | (surfG << 8) | surfB);

    // 5. Light properties - ZERO ALLOCATION
    LightProperties.getLightProperties(light, worldPoint, tempLightProps);
    tempLightDir.set(tempLightProps.direction);

    // 6. Ambient check
    if (light instanceof MuratAmbientLight) {
      int r = (int) (surfR * ambientCoefficient * tempLightProps.intensity);
      int g = (int) (surfG * ambientCoefficient * tempLightProps.intensity);
      int b = (int) (surfB * ambientCoefficient * tempLightProps.intensity);
      r = r > 255 ? 255 : (r < 0 ? 0 : r);
      g = g > 255 ? 255 : (g < 0 ? 0 : g);
      b = b > 255 ? 255 : (b < 0 ? 0 : b);
      return (0xFF000000 | (r << 16) | (g << 8) | b);
    }

    // 7. Diffuse
    double NdotL = Math.max(0.0, tempLocalNormal.dot(tempLightDir));
    int lightColor = tempLightProps.color;
    int lightR = (lightColor >> 16) & 0xFF;
    int lightG = (lightColor >> 8) & 0xFF;
    int lightB = lightColor & 0xFF;
    double intensity = tempLightProps.intensity;

    double diffFactor = diffuseCoefficient * NdotL * intensity;
    int diffR = (int) (surfR * diffFactor * lightR / 255.0);
    int diffG = (int) (surfG * diffFactor * lightG / 255.0);
    int diffB = (int) (surfB * diffFactor * lightB / 255.0);

    // 8. Ambient
    double ambFactor = ambientCoefficient * intensity;
    int ambR = (int) (surfR * ambFactor * lightR / 255.0);
    int ambG = (int) (surfG * ambFactor * lightG / 255.0);
    int ambB = (int) (surfB * ambFactor * lightB / 255.0);

    // 9. Combine
    int r = ambR + diffR;
    int g = ambG + diffG;
    int b = ambB + diffB;

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
    sb.append("MoonSurfaceMaterial moonsurfacematerial {\n");
    sb.append("        moonColor = ")
        .append(ColorUtil.toColorString(moonColorPacked))
        .append(";\n");
    sb.append("    }");
    return sb.toString();
  }
}
