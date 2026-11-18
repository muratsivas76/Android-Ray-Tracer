package net.murat.material;

import net.murat.light.Light;
import net.murat.light.LightProperties;
import net.murat.light.MuratAmbientLight;
import net.murat.math.*;
import net.murat.util.ColorUtil;

/**
 * LambertMaterial - Matte, non-glossy surface with ambient and diffuse lighting. Zero-allocation
 * using packed ARGB integers.
 */
public class LambertMaterial implements Material {
  private final int diffuseColorPacked;
  private final double ambientCoefficient;
  private final double diffuseCoefficient;

  private double reflectivity = 0.0;
  private final double ior = 1.0;
  private final double transparency = 0.0;

  // Pre-cached color components
  private final int diffR, diffG, diffB;

  // ===== GC DOSTU: PRE-ALLOCATED TEMP OBJECTS =====
  private final Vector3 tempLightDir = new Vector3();
  private final LightProperties tempLightProps = new LightProperties();

  public LambertMaterial(int color) {
    this(color, 0.1, 1.0, 0.0);
  }

  public LambertMaterial(int color, double ambientCoeff, double diffuseCoeff) {
    this(color, ambientCoeff, diffuseCoeff, 0.0);
  }

  public LambertMaterial(int color, double ambientCoeff, double diffuseCoeff, double reflectivity) {
    this.diffuseColorPacked = color;
    this.ambientCoefficient = ambientCoeff;
    this.diffuseCoefficient = diffuseCoeff;

    this.diffR = (color >> 16) & 0xFF;
    this.diffG = (color >> 8) & 0xFF;
    this.diffB = color & 0xFF;

    this.reflectivity = reflectivity;
  }

  @Override
  public void setObjectTransform(Matrix4 tm) {
    // Lambertian shading uses world-space lighting
  }

  @Override
  public int getColorAt(Point3 point, Vector3 normal, Light light, Point3 viewerPos) {
    // 1. Light properties - ZERO ALLOCATION
    LightProperties.getLightProperties(light, point, tempLightProps);
    tempLightDir.set(tempLightProps.direction);

    int lightColor = tempLightProps.color;
    double intensity = tempLightProps.intensity;

    int lightR = (lightColor >> 16) & 0xFF;
    int lightG = (lightColor >> 8) & 0xFF;
    int lightB = lightColor & 0xFF;

    // 2. Ambient component
    int ambR = (int) (diffR * ambientCoefficient * lightR / 255.0);
    int ambG = (int) (diffG * ambientCoefficient * lightG / 255.0);
    int ambB = (int) (diffB * ambientCoefficient * lightB / 255.0);

    // 3. Ambient light check
    if (light instanceof MuratAmbientLight) {
      ambR = ambR > 255 ? 255 : (ambR < 0 ? 0 : ambR);
      ambG = ambG > 255 ? 255 : (ambG < 0 ? 0 : ambG);
      ambB = ambB > 255 ? 255 : (ambB < 0 ? 0 : ambB);
      return (0xFF000000 | (ambR << 16) | (ambG << 8) | ambB);
    }

    // 4. Diffuse component
    double NdotL = Math.max(0, normal.dot(tempLightDir));
    double diffFactor = diffuseCoefficient * NdotL * intensity;

    int diffR = (int) (this.diffR * diffFactor * lightR / 255.0);
    int diffG = (int) (this.diffG * diffFactor * lightG / 255.0);
    int diffB = (int) (this.diffB * diffFactor * lightB / 255.0);

    // 5. Combine ambient + diffuse
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
    sb.append("LambertMaterial lambertmaterial {\n");
    sb.append("        color = ").append(ColorUtil.toColorString(diffuseColorPacked)).append(";\n");
    sb.append("        ambient = ").append(ambientCoefficient).append(";\n");
    sb.append("        diffuse = ").append(diffuseCoefficient).append(";\n");
    sb.append("        reflectivity = ").append(reflectivity).append(";\n");
    sb.append("    }");
    return sb.toString();
  }
}
