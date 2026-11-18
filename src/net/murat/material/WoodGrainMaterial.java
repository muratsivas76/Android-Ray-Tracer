package net.murat.material;

import net.murat.light.Light;
import net.murat.light.LightProperties;
import net.murat.light.MuratAmbientLight;
import net.murat.math.*;
import net.murat.util.ColorUtil;

/**
 * WoodGrainMaterial - Procedural wood grain texture. Zero-allocation using packed ARGB integers.
 */
public class WoodGrainMaterial implements Material {
  private final int woodColorPacked;
  private Matrix4 objectInverseTransform;
  private Matrix4 inverseTransposeTransform;

  // Pre-cached color components
  private final int woodR, woodG, woodB;

  private final double ambientCoefficient = 0.4;
  private final double diffuseCoefficient = 0.8;
  private final double specularCoefficient = 0.1;
  private final double shininess = 20.0;
  private final double reflectivity = 0.05;
  private final double ior = 1.0;
  private final double transparency = 0.0;

  // ===== GC DOSTU: PRE-ALLOCATED TEMP OBJECTS =====
  private final Point3 tempLocalPoint = new Point3();
  private final Vector3 tempLocalNormal = new Vector3();
  private final Vector3 tempTransformedNormal = new Vector3();
  private final Vector3 tempLightDir = new Vector3();
  private final LightProperties tempLightProps = new LightProperties();

  public WoodGrainMaterial() {
    this.woodColorPacked = 0xFF8B4513;
    this.woodR = 139;
    this.woodG = 69;
    this.woodB = 19;
    this.objectInverseTransform = new Matrix4();
    this.inverseTransposeTransform = new Matrix4();
  }

  public WoodGrainMaterial(int woodColor) {
    this.woodColorPacked = woodColor;
    this.woodR = (woodColor >> 16) & 0xFF;
    this.woodG = (woodColor >> 8) & 0xFF;
    this.woodB = woodColor & 0xFF;
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

    // 1. Transform to local space
    objectInverseTransform.transformPoint(worldPoint, tempLocalPoint);

    // 2. Transform normal
    inverseTransposeTransform.transformDirection(worldNormal, tempTransformedNormal);
    tempLocalNormal.set(tempTransformedNormal);
    tempLocalNormal.normalize();

    // 3. Wood grain pattern
    double woodGrain = Math.sin(tempLocalPoint.x * 20 + Math.sin(tempLocalPoint.y * 5) * 2);
    double ringPattern = Math.sin(tempLocalPoint.z * 30 + woodGrain * 5);
    double woodDarkness = 0.6 + ringPattern * 0.4;

    int surfR = (int) (woodR * woodDarkness);
    int surfG = (int) (woodG * woodDarkness);
    int surfB = (int) (woodB * woodDarkness);
    surfR = clamp(surfR);
    surfG = clamp(surfG);
    surfB = clamp(surfB);
    int surfaceColor = (0xFF000000 | (surfR << 16) | (surfG << 8) | surfB);

    // 4. Light properties - ZERO ALLOCATION
    LightProperties.getLightProperties(light, worldPoint, tempLightProps);
    tempLightDir.set(tempLightProps.direction);

    int lightColor = tempLightProps.color;
    int lightR = (lightColor >> 16) & 0xFF;
    int lightG = (lightColor >> 8) & 0xFF;
    int lightB = lightColor & 0xFF;
    double intensity = tempLightProps.intensity;

    // 5. Ambient check
    if (light instanceof MuratAmbientLight) {
      int r = (int) (surfR * ambientCoefficient * lightR / 255.0 * intensity);
      int g = (int) (surfG * ambientCoefficient * lightG / 255.0 * intensity);
      int b = (int) (surfB * ambientCoefficient * lightB / 255.0 * intensity);
      r = clamp(r);
      g = clamp(g);
      b = clamp(b);
      return (0xFF000000 | (r << 16) | (g << 8) | b);
    }

    // 6. Diffuse
    double NdotL = Math.max(0.0, tempLocalNormal.dot(tempLightDir));
    double diffFactor = diffuseCoefficient * NdotL * intensity;
    int diffR = (int) (surfR * diffFactor * lightR / 255.0);
    int diffG = (int) (surfG * diffFactor * lightG / 255.0);
    int diffB = (int) (surfB * diffFactor * lightB / 255.0);

    // 7. Ambient
    int ambR = (int) (surfR * ambientCoefficient * lightR / 255.0 * intensity);
    int ambG = (int) (surfG * ambientCoefficient * lightG / 255.0 * intensity);
    int ambB = (int) (surfB * ambientCoefficient * lightB / 255.0 * intensity);

    // 8. Combine
    int r = ambR + diffR;
    int g = ambG + diffG;
    int b = ambB + diffB;

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
    sb.append("WoodGrainMaterial woodgrainmaterial {\n");
    sb.append("        woodColor = ")
        .append(ColorUtil.toColorString(woodColorPacked))
        .append(";\n");
    sb.append("    }");
    return sb.toString();
  }
}
