package net.murat.material;

import java.util.Random;
import net.murat.light.Light;
import net.murat.light.LightProperties;
import net.murat.light.MuratAmbientLight;
import net.murat.math.*;

/**
 * RandomMaterial - Randomly generated material properties. Zero-allocation using packed ARGB
 * integers.
 */
public class RandomMaterial implements Material {
  private final int diffuseColorPacked;
  private final int specularColorPacked;
  private final double ambientCoeff;
  private final double diffuseCoeff;
  private final double specularCoeff;
  private final double shininess;
  private final double reflectivity;
  private final double transparency;
  private Matrix4 objectInverseTransform;
  private Matrix4 inverseTransposeTransform;

  // Pre-cached color components
  private final int diffR, diffG, diffB;
  private final int specR, specG, specB;

  // ===== GC DOSTU: PRE-ALLOCATED TEMP OBJECTS =====
  private final Vector3 tempViewDir = new Vector3();
  private final Vector3 tempLightDir = new Vector3();
  private final Vector3 tempHalfway = new Vector3();
  private final Point3 tempViewerPoint = new Point3();
  private final LightProperties tempLightProps = new LightProperties();

  private static final Random RAND = new Random();

  public RandomMaterial(Matrix4 invTransform) {
    this.diffuseColorPacked = randomColor();
    this.specularColorPacked = randomColor();
    this.ambientCoeff = randomInRange(0.1, 0.3);
    this.diffuseCoeff = randomInRange(0.5, 1.0);
    this.specularCoeff = randomInRange(0.1, 0.9);
    this.shininess = randomInRange(5, 150);
    this.reflectivity = randomInRange(0, 0.5);
    this.transparency = RAND.nextBoolean() ? randomInRange(0, 0.3) : 0;
    this.objectInverseTransform = invTransform != null ? invTransform : new Matrix4();
    this.inverseTransposeTransform = this.objectInverseTransform.inverseTransposeForNormal();

    this.diffR = (diffuseColorPacked >> 16) & 0xFF;
    this.diffG = (diffuseColorPacked >> 8) & 0xFF;
    this.diffB = diffuseColorPacked & 0xFF;

    this.specR = (specularColorPacked >> 16) & 0xFF;
    this.specG = (specularColorPacked >> 8) & 0xFF;
    this.specB = specularColorPacked & 0xFF;
  }

  @Override
  public void setObjectTransform(Matrix4 tm) {
    if (tm == null) tm = new Matrix4();
    this.objectInverseTransform = tm;
    this.inverseTransposeTransform = tm.inverseTransposeForNormal();
  }

  @Override
  public int getColorAt(Point3 worldPoint, Vector3 normal, Light light, Point3 viewPos) {
    // 1. Light properties - ZERO ALLOCATION
    LightProperties.getLightProperties(light, worldPoint, tempLightProps);
    tempLightDir.set(tempLightProps.direction);

    int lightColor = tempLightProps.color;
    int lightR = (lightColor >> 16) & 0xFF;
    int lightG = (lightColor >> 8) & 0xFF;
    int lightB = lightColor & 0xFF;
    double intensity = tempLightProps.intensity;

    // 2. Ambient check
    if (light instanceof MuratAmbientLight) {
      int r = (int) (diffR * ambientCoeff * lightR / 255.0 * intensity);
      int g = (int) (diffG * ambientCoeff * lightG / 255.0 * intensity);
      int b = (int) (diffB * ambientCoeff * lightB / 255.0 * intensity);
      r = clamp(r);
      g = clamp(g);
      b = clamp(b);
      return (0xFF000000 | (r << 16) | (g << 8) | b);
    }

    // 3. Diffuse
    double NdotL = Math.max(0.0, normal.dot(tempLightDir));
    double diffFactor = diffuseCoeff * NdotL * intensity;
    int diffR = (int) (this.diffR * diffFactor * lightR / 255.0);
    int diffG = (int) (this.diffG * diffFactor * lightG / 255.0);
    int diffB = (int) (this.diffB * diffFactor * lightB / 255.0);

    // 4. View direction - ZERO ALLOCATION
    tempViewerPoint.set(viewPos);
    tempViewDir.set(tempViewerPoint);
    tempViewDir.subtract(worldPoint);
    tempViewDir.normalize();

    // 5. Halfway vector - ZERO ALLOCATION
    tempHalfway.set(tempLightDir);
    tempHalfway.add(tempViewDir);
    tempHalfway.normalize();

    // 6. Specular
    double NdotH = Math.max(0.0, normal.dot(tempHalfway));
    double specFactor = Math.pow(NdotH, shininess) * specularCoeff * intensity;
    int specR = (int) (this.specR * specFactor * lightR / 255.0);
    int specG = (int) (this.specG * specFactor * lightG / 255.0);
    int specB = (int) (this.specB * specFactor * lightB / 255.0);

    // 7. Ambient
    int ambR = (int) (this.diffR * ambientCoeff * lightR / 255.0 * intensity);
    int ambG = (int) (this.diffG * ambientCoeff * lightG / 255.0 * intensity);
    int ambB = (int) (this.diffB * ambientCoeff * lightB / 255.0 * intensity);

    // 8. Combine
    int r = ambR + diffR + specR;
    int g = ambG + diffG + specG;
    int b = ambB + diffB + specB;

    r = clamp(r);
    g = clamp(g);
    b = clamp(b);

    return (0xFF000000 | (r << 16) | (g << 8) | b);
  }

  private int clamp(int value) {
    return Math.max(0, Math.min(255, value));
  }

  // Helper methods - sadece constructor'da çağrılır, render loop'unda değil!
  private static int randomColor() {
    return (0xFF000000 | (RAND.nextInt(256) << 16) | (RAND.nextInt(256) << 8) | RAND.nextInt(256));
  }

  private static double randomInRange(double min, double max) {
    return min + (max - min) * RAND.nextDouble();
  }

  @Override
  public double getReflectivity() {
    return reflectivity;
  }

  @Override
  public double getIndexOfRefraction() {
    return 1.0 + RAND.nextDouble();
  }

  @Override
  public double getTransparency() {
    return transparency;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("RandomMaterial randommaterial {\n    }");
    return sb.toString();
  }
}
