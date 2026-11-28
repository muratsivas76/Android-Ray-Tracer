package net.murat.material;

import net.murat.light.*;
import net.murat.math.*;
import net.murat.util.ColorUtil;

/**
 * VikingMetalMaterial - Weathered metal with rust pattern. Zero-allocation using packed ARGB
 * integers.
 */
public class VikingMetalMaterial implements Material {
  private final int baseColorPacked;
  private final int rustColorPacked;
  private final double rustDensity;
  private Matrix4 objectTransform;
  private Matrix4 inverseTransform;
  private Matrix4 inverseTransposeTransform;

  // Pre-cached color components
  private final int baseR, baseG, baseB;
  private final int rustR, rustG, rustB;

  private final double ambientCoeff = 0.4;
  private final double diffuseCoeff = 0.7;
  private final double specularCoeff = 0.15;
  private final double shininess = 15.0;
  private final double reflectivity = 0.08;
  private final double ior = 2.8;
  private final double transparency = 0.0;

  private static final int SPECULAR_COLOR = 0xFFD3D3D3;

  // ===== GC DOSTU: PRE-ALLOCATED TEMP OBJECTS =====
  private final Point3 tempLocalPoint = new Point3();
  private final Vector3 tempLocalNormal = new Vector3();
  private final Vector3 tempTransformedNormal = new Vector3();
  private final Vector3 tempViewDir = new Vector3();
  private final Vector3 tempLightDir = new Vector3();
  private final Vector3 tempReflectDir = new Vector3();
  private final Point3 tempViewerPoint = new Point3();
  private final LightProperties tempLightProps = new LightProperties();

  public VikingMetalMaterial() {
    this(0xFF222222, 0xFFBB5522, 0.35);
  }

  public VikingMetalMaterial(int baseColor, int rustColor, double rustDensity) {
    this.baseColorPacked = baseColor;
    this.rustColorPacked = rustColor;
    this.rustDensity = Math.max(0, Math.min(1, rustDensity));

    this.baseR = (baseColor >> 16) & 0xFF;
    this.baseG = (baseColor >> 8) & 0xFF;
    this.baseB = baseColor & 0xFF;

    this.rustR = (rustColor >> 16) & 0xFF;
    this.rustG = (rustColor >> 8) & 0xFF;
    this.rustB = rustColor & 0xFF;

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

    // 3. Calculate rust pattern
    int surfaceColor = calculateRustPattern(tempLocalPoint);
    int surfR = (surfaceColor >> 16) & 0xFF;
    int surfG = (surfaceColor >> 8) & 0xFF;
    int surfB = surfaceColor & 0xFF;

    // 4. Light properties - ZERO ALLOCATION
    LightProperties.getLightProperties(light, worldPoint, tempLightProps);
    tempLightDir.set(tempLightProps.direction);

    int lightColor = tempLightProps.color;
    int lightR = (lightColor >> 16) & 0xFF;
    int lightG = (lightColor >> 8) & 0xFF;
    int lightB = lightColor & 0xFF;
    double intensity = tempLightProps.intensity;

    // 5. Ambient check
    int ambient = ColorUtil.multiplyColors(surfaceColor, tempLightProps.color, ambientCoeff);

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

    int specR = (int) (((SPECULAR_COLOR >> 16) & 0xFF) * specFactor * lightR / 255.0);
    int specG = (int) (((SPECULAR_COLOR >> 8) & 0xFF) * specFactor * lightG / 255.0);
    int specB = (int) ((SPECULAR_COLOR & 0xFF) * specFactor * lightB / 255.0);

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

  private int calculateRustPattern(Point3 point) {
    double noise = simplexNoise3D(point.x * 5, point.y * 5, point.z * 5);
    double t = (noise + 1) * 0.5;

    int r, g, b;
    if (t < rustDensity) {
      double ratio = t / rustDensity;
      int rustDarkR = (int) (rustR * 0.8);
      int rustDarkG = (int) (rustG * 0.9);
      int rustDarkB = (int) (rustB * 1.1);
      r = (int) (rustR + (rustDarkR - rustR) * ratio);
      g = (int) (rustG + (rustDarkG - rustG) * ratio);
      b = (int) (rustB + (rustDarkB - rustB) * ratio);
    } else {
      double ratio = (t - rustDensity) / (1 - rustDensity);
      int baseLightR = (int) (baseR * 1.2);
      int baseLightG = (int) (baseG * 1.1);
      int baseLightB = (int) (baseB * 0.9);
      r = (int) (baseR + (baseLightR - baseR) * ratio);
      g = (int) (baseG + (baseLightG - baseG) * ratio);
      b = (int) (baseB + (baseLightB - baseB) * ratio);
    }

    r = clamp(r);
    g = clamp(g);
    b = clamp(b);

    return (0xFF000000 | (r << 16) | (g << 8) | b);
  }

  private double simplexNoise3D(double x, double y, double z) {
    double value = Math.sin(x * 0.431) + Math.sin(y * 0.723) + Math.sin(z * 0.327);
    value += Math.sin(x * 1.531 + y * 0.927) * 0.5;
    value += Math.sin(y * 1.231 + z * 1.627) * 0.25;
    return value % 1.0;
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
    sb.append("VikingMetalMaterial vikingmetalmaterial {\n");
    sb.append("        baseColor = ")
        .append(ColorUtil.toColorString(baseColorPacked))
        .append(";\n");
    sb.append("        rustColor = ")
        .append(ColorUtil.toColorString(rustColorPacked))
        .append(";\n");
    sb.append("        rustDensity = ").append(rustDensity).append(";\n");
    sb.append("    }");
    return sb.toString();
  }
}
