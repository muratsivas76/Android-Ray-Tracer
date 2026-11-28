package net.murat.material;

import net.murat.light.Light;
import net.murat.light.LightProperties;
import net.murat.light.MuratAmbientLight;
import net.murat.math.*;

/**
 * FractalBarkMaterial - Tree bark with fractal noise pattern. Zero-allocation using packed ARGB
 * integers.
 */
public class FractalBarkMaterial implements Material {
  private Matrix4 objectInverseTransform;
  private Matrix4 inverseTransposeTransform;
  private double roughness;
  private final double reflectivity;

  // ===== GC DOSTU: PRE-ALLOCATED TEMP OBJECTS =====
  private final Point3 tempLocalPoint = new Point3();
  private final Vector3 tempLocalNormal = new Vector3();
  private final Vector3 tempTransformedNormal = new Vector3();
  private final Vector3 tempLightDir = new Vector3();
  private final LightProperties tempLightProps = new LightProperties();

  public FractalBarkMaterial(Matrix4 objectInverseTransform) {
    this(objectInverseTransform, 0.7);
  }

  public FractalBarkMaterial(Matrix4 objectInverseTransform, double roughness) {
    this.objectInverseTransform =
        objectInverseTransform != null ? objectInverseTransform : new Matrix4();
    this.inverseTransposeTransform = this.objectInverseTransform.inverseTransposeForNormal();
    this.roughness = clamp(roughness, 0.0, 1.0);
    this.reflectivity = clamp(0.1 * this.roughness, 0.0, 1.0);
  }

  @Override
  public void setObjectTransform(Matrix4 tm) {
    if (tm == null) tm = new Matrix4();
    this.objectInverseTransform = tm;
    this.inverseTransposeTransform = tm.inverseTransposeForNormal();
  }

  /** Fractal pattern between 0.0-1.0 - ZERO ALLOCATION */
  private double fractalNoise(Point3 p) {
    return clamp(
        Math.abs(Math.sin(30.0 * p.x) * Math.cos(20.0 * p.z) * Math.sin(5.0 * p.y)), 0.0, 1.0);
  }

  @Override
  public int getColorAt(Point3 worldPoint, Vector3 worldNormal, Light light, Point3 viewerPos) {
    // 1. Transform to local space - ZERO ALLOCATION
    objectInverseTransform.transformPoint(worldPoint, tempLocalPoint);

    // 2. Transform normal - ZERO ALLOCATION
    inverseTransposeTransform.transformDirection(worldNormal, tempTransformedNormal);
    tempLocalNormal.set(tempTransformedNormal);
    tempLocalNormal.normalize();

    // 3. Fractal noise
    double n = fractalNoise(tempLocalPoint);

    // 4. Light properties - ZERO ALLOCATION
    LightProperties.getLightProperties(light, worldPoint, tempLightProps);
    tempLightDir.set(tempLightProps.direction);
    double intensity = tempLightProps.intensity;
    int lightColor = tempLightProps.color;

    // 5. Ambient check
    if (light instanceof MuratAmbientLight) {
      int r = (int) ((100.0 + 100.0 * n) * 0.5 * intensity);
      int g = (int) ((70.0 + 50.0 * n) * 0.5 * intensity);
      int b = (int) ((40.0 + 20.0 * n) * 0.5 * intensity);
      r = clamp(r, 0, 255);
      g = clamp(g, 0, 255);
      b = clamp(b, 0, 255);
      return (0xFF000000 | (r << 16) | (g << 8) | b);
    }

    // 6. Extract light channels
    int lightR = (lightColor >> 16) & 0xFF;
    int lightG = (lightColor >> 8) & 0xFF;
    int lightB = lightColor & 0xFF;

    // 7. Base color from fractal noise
    int baseR = clamp((int) (100.0 + 100.0 * n), 0, 255);
    int baseG = clamp((int) (70.0 + 50.0 * n), 0, 255);
    int baseB = clamp((int) (40.0 + 20.0 * n), 0, 255);

    // 8. Diffuse lighting: N · L
    double NdotL = clamp(tempLocalNormal.dot(tempLightDir), 0.1, 1.0);
    double lightingFactor = NdotL * intensity;

    // 9. Apply lighting with light color
    int finalR = clamp((int) (baseR * lightingFactor * lightR / 255.0), 0, 255);
    int finalG = clamp((int) (baseG * lightingFactor * lightG / 255.0), 0, 255);
    int finalB = clamp((int) (baseB * lightingFactor * lightB / 255.0), 0, 255);

    return (0xFF000000 | (finalR << 16) | (finalG << 8) | finalB);
  }

  // Helper clamp methods
  private static double clamp(double value, double min, double max) {
    return value > max ? max : (value < min ? min : value);
  }

  private static int clamp(int value, int min, int max) {
    return value > max ? max : (value < min ? min : value);
  }

  @Override
  public double getReflectivity() {
    return reflectivity;
  }

  @Override
  public double getIndexOfRefraction() {
    return 1.2;
  }

  @Override
  public double getTransparency() {
    return 0.0;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("FractalBarkMaterial fractalbarkmaterial {\n");
    sb.append("        roughness = ").append(roughness).append(";\n");
    sb.append("    }");
    return sb.toString();
  }
}
