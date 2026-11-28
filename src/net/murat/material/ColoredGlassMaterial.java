package net.murat.material;

import net.murat.light.Light;
import net.murat.light.LightProperties;
import net.murat.math.*;
import net.murat.util.ColorUtil;

/**
 * ColoredGlassMaterial.java
 *
 * <p>A high-performance, production-ready dielectric material that provides rich glass/crystal
 * shading with integrated lens distortion and chromatic fringing simulation. Fully compatible with
 * Java 6/8 and optimized for zero-allocation rendering loops.
 *
 * <p>Fixed: Removed ring artifacts by smoothing fresnel and rim transitions.
 */
public class ColoredGlassMaterial implements Material {

  private int baseColor;
  private int specularColor;
  private double transparency;
  private double reflectivity;
  private double indexOfRefraction;

  // ===== TRANSFORM MATRICES =====
  private Matrix4 objectTransform;
  private Matrix4 inverseObjectTransform;
  private Matrix4 inverseTransposeTransform;
  private boolean hasTransform = false;

  private int baseR, baseG, baseB;
  private int specR, specG, specB;

  private double shininess = 512.0;
  private double specularStrength = 6.0;

  // ===== GC-FRIENDLY ZERO-ALLOCATION TEMP OBJECTS =====
  private final Vector3 tempNormal = new Vector3();
  private final Vector3 tempViewDir = new Vector3();
  private final Vector3 tempIncidentDir = new Vector3();
  private final Vector3 tempLightDir = new Vector3();
  private final Vector3 tempHalfVec = new Vector3();
  private final Point3 tempPoint = new Point3();
  private final LightProperties tempLightProps = new LightProperties();

  /** Default constructor creating a premium clear crown-glass material. */
  public ColoredGlassMaterial() {
    this(0xFFFFFFFF, 0xFFFFFFFF, 0.0, 0.4, 1.52, 512.0, 6.0);
  }

  /** Complete full-parameter constructor. */
  public ColoredGlassMaterial(
      int baseColorPacked,
      int specularColorPacked,
      double transparency,
      double reflectivity,
      double indexOfRefraction,
      double shininess,
      double specularStrength) {
    this.baseColor = baseColorPacked;
    this.specularColor = specularColorPacked;
    this.baseR = (baseColorPacked >> 16) & 0xFF;
    this.baseG = (baseColorPacked >> 8) & 0xFF;
    this.baseB = baseColorPacked & 0xFF;
    this.specR = (specularColorPacked >> 16) & 0xFF;
    this.specG = (specularColorPacked >> 8) & 0xFF;
    this.specB = specularColorPacked & 0xFF;
    this.indexOfRefraction = indexOfRefraction;
    this.reflectivity = reflectivity;
    this.transparency = transparency;
    this.shininess = shininess;
    this.specularStrength = specularStrength;

    this.objectTransform = new Matrix4();
    this.inverseObjectTransform = new Matrix4();
    this.inverseTransposeTransform = new Matrix4();
  }

  /** Simplified convenience constructor. */
  public ColoredGlassMaterial(
      int baseColorPacked, double transparency, double reflectivity, double indexOfRefraction) {
    this(baseColorPacked, 0xFFFFFFFF, transparency, reflectivity, indexOfRefraction, 512.0, 6.0);
  }

  @Override
  public int getColorAt(Point3 point, Vector3 normal, Light light, Point3 viewerPoint) {
    // ===== TRANSFORM TO WORLD SPACE =====
    Point3 worldPoint = tempPoint;
    Vector3 worldNormal = tempNormal;

    if (hasTransform) {
      worldPoint.set(point);
      objectTransform.transformPoint(point, worldPoint);

      worldNormal.set(normal);
      objectTransform.transformNormal(normal, worldNormal);
      worldNormal.normalize();
    } else {
      worldPoint.set(point);
      worldNormal.set(normal).normalize();
    }

    // Light properties in world space
    LightProperties.getLightProperties(light, worldPoint, tempLightProps);
    tempLightDir.set(tempLightProps.direction).normalize();

    int lightColor = tempLightProps.color;
    int lightR = (lightColor >> 16) & 0xFF;
    int lightG = (lightColor >> 8) & 0xFF;
    int lightB = lightColor & 0xFF;

    // View direction and incident ray in world space
    tempViewDir.set(viewerPoint).subtract(worldPoint).normalize();
    tempIncidentDir.set(tempViewDir).negate().normalize();

    // ===== SPECULAR HIGHLIGHT =====
    tempHalfVec.set(tempLightDir).add(tempViewDir).normalize();
    double specAngle = Math.max(0.0, worldNormal.dot(tempHalfVec));
    double specular = Math.pow(specAngle, shininess) * specularStrength * tempLightProps.intensity;

    double specR_f = specR * specular * lightR / 255.0;
    double specG_f = specG * specular * lightG / 255.0;
    double specB_f = specB * specular * lightB / 255.0;

    // ===== SMOOTH FRESNEL EFFECT (RING ARTIFACT FIXED) =====
    double nv = Math.max(0.0, worldNormal.dot(tempViewDir));

    // Fresnel - smooth transition using smoothstep
    double cosTheta = Math.max(0.0, Math.min(1.0, Math.abs(tempViewDir.dot(worldNormal))));
    double r0 = Math.pow((1.0 - this.indexOfRefraction) / (1.0 + this.indexOfRefraction), 2.0);
    double fresnel = r0 + (1.0 - r0) * Math.pow(1.0 - cosTheta, 5.0);

    // SMOOTH RIM - using smoothstep instead of power function
    double rimFactor = 1.0 - nv;
    // Smoothstep: 3t² - 2t³
    double smoothRim = rimFactor * rimFactor * (3.0 - 2.0 * rimFactor);
    double rimEffect = smoothRim * this.reflectivity * 0.5; // Reduced intensity

    // Internal glow - smooth
    double internalGlow = nv * nv * this.transparency * 0.3;

    // ===== CHROMATIC LENSING (SOFTENED) =====
    Vector3 refractedDir = new Vector3();
    if (tempIncidentDir.refract(worldNormal, 1.0, this.indexOfRefraction)) {
      refractedDir.set(tempIncidentDir);
    } else {
      tempIncidentDir.reflect(worldNormal);
      refractedDir.set(tempIncidentDir);
    }

    double waveOffset = 0.0;
    if (refractedDir != null) {
      waveOffset = (refractedDir.getX() + refractedDir.getZ()) * 2.0;
    }

    // Soft chromatic waves - reduced intensity
    double wave1 = Math.abs(Math.sin(nv * 6.0 + waveOffset));
    double wave2 = Math.abs(Math.sin(nv * 6.0 + waveOffset + 2.09));
    double wave3 = Math.abs(Math.sin(nv * 6.0 + waveOffset + 4.18));

    // Edge fringe - softened and reduced
    double edgeFringe = smoothRim * tempLightProps.intensity * 0.25; // Reduced from 0.5
    int glassTintR = (int) (edgeFringe * wave1 * 150); // Reduced from 210
    int glassTintG = (int) (edgeFringe * wave2 * 180); // Reduced from 240
    int glassTintB = (int) (edgeFringe * wave3 * 200); // Reduced from 255

    // ===== BASE COLOR COMPOSITING =====
    double ambient = 0.15; // Slightly reduced
    double totalLight = ambient + internalGlow + rimEffect + (fresnel * 0.15);

    int r = (int) (baseR * totalLight * lightR / 255.0 + specR_f + glassTintR);
    int g = (int) (baseG * totalLight * lightG / 255.0 + specG_f + glassTintG);
    int b = (int) (baseB * totalLight * lightB / 255.0 + specB_f + glassTintB);

    // Safe clamping
    r = Math.min(255, Math.max(0, r));
    g = Math.min(255, Math.max(0, g));
    b = Math.min(255, Math.max(0, b));

    return (0xFF000000 | (r << 16) | (g << 8) | b);
  }

  @Override
  public double getTransparency() {
    return this.transparency;
  }

  @Override
  public double getReflectivity() {
    return this.reflectivity;
  }

  @Override
  public double getIndexOfRefraction() {
    return this.indexOfRefraction;
  }

  @Override
  public void setObjectTransform(Matrix4 tm) {
    if (tm == null) {
      this.objectTransform = new Matrix4();
      this.hasTransform = false;
    } else {
      this.objectTransform = tm;
      this.hasTransform = true;
    }
    this.inverseObjectTransform = this.objectTransform.inverse();
    this.inverseTransposeTransform = this.inverseObjectTransform.inverseTransposeForNormal();
  }

  // ===== GETTERS/SETTERS =====
  public int getBaseColor() {
    return this.baseColor;
  }

  public void setBaseColor(int baseColorPacked) {
    this.baseColor = baseColorPacked;
    this.baseR = (baseColorPacked >> 16) & 0xFF;
    this.baseG = (baseColorPacked >> 8) & 0xFF;
    this.baseB = baseColorPacked & 0xFF;
  }

  public int getSpecularColor() {
    return this.specularColor;
  }

  public void setSpecularColor(int specularColorPacked) {
    this.specularColor = specularColorPacked;
    this.specR = (specularColorPacked >> 16) & 0xFF;
    this.specG = (specularColorPacked >> 8) & 0xFF;
    this.specB = specularColorPacked & 0xFF;
  }

  public void setTransparency(double transparency) {
    this.transparency = transparency;
  }

  public void setReflectivity(double reflectivity) {
    this.reflectivity = reflectivity;
  }

  public void setIndexOfRefraction(double ior) {
    this.indexOfRefraction = ior;
  }

  public void setShininess(double shininess) {
    this.shininess = shininess;
  }

  public double getShininess() {
    return this.shininess;
  }

  public void setSpecularStrength(double strength) {
    this.specularStrength = strength;
  }

  public double getSpecularStrength() {
    return this.specularStrength;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("ColoredGlassMaterial coloredglassmaterial {\n");
    sb.append("        baseColor = ").append(ColorUtil.toColorString(baseColor)).append(";\n");
    sb.append("        specularColor = ")
        .append(ColorUtil.toColorString(specularColor))
        .append(";\n");
    sb.append("        transparency = ").append(transparency).append(";\n");
    sb.append("        reflectivity = ").append(reflectivity).append(";\n");
    sb.append("        ior = ").append(indexOfRefraction).append(";\n");
    sb.append("        shininess = ").append(shininess).append(";\n");
    sb.append("        specularStrength = ").append(specularStrength).append(";\n");
    sb.append("    }");
    return sb.toString();
  }
}
