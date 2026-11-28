package net.murat.material;

import net.murat.light.*;
import net.murat.math.*;
import net.murat.util.ColorUtil;

/**
 * AnodizedMetalMaterial - Iridescent anodized metal effect with zero allocation. Features
 * angle-dependent color shifting and metallic reflections.
 */
public class AnodizedMetalMaterial implements Material {
  private int baseColor;
  private Matrix4 objectTransform;
  private int baseR, baseG, baseB;
  private final double ambientCoeff;
  private final double diffuseCoeff;
  private final double specularCoeff;
  private final double shininess;
  private final int specularColor;
  private final double reflectivity;
  private final double ior;
  private final double transparency;
  private final double iridescenceStrength;
  private final double iridescenceSpeed;

  // Pre-extracted specular color channels
  private final int specR, specG, specB;

  // Pre-allocated temp objects - GC friendly
  private final Vector3 tempViewDir = new Vector3();
  private final Vector3 tempLightDir = new Vector3();
  private final Vector3 tempHalfVec = new Vector3();
  private final LightProperties tempLightProps = new LightProperties();

  public AnodizedMetalMaterial() {
    this(0xFF3232C8, 0.4, 0.6, 1.2, 150.0, 0xFFFFFFFF, 0.7, 2.4, 0.0, 0.9, 3.0);
  }

  public AnodizedMetalMaterial(int baseColor) {
    this(baseColor, 0.4, 0.6, 1.2, 150.0, 0xFFFFFFFF, 0.7, 2.4, 0.0, 0.9, 3.0);
  }

  public AnodizedMetalMaterial(
      int baseColor,
      double ambientCoeff,
      double diffuseCoeff,
      double specularCoeff,
      double shininess,
      int specularColor,
      double reflectivity,
      double ior,
      double transparency,
      double iridescenceStrength,
      double iridescenceSpeed) {
    this.baseColor = baseColor;
    this.baseR = (baseColor >> 16) & 0xFF;
    this.baseG = (baseColor >> 8) & 0xFF;
    this.baseB = baseColor & 0xFF;
    this.ambientCoeff = Math.max(0.0, Math.min(1.0, ambientCoeff));
    this.diffuseCoeff = Math.max(0.0, Math.min(2.0, diffuseCoeff));
    this.specularCoeff = Math.max(0.0, Math.min(3.0, specularCoeff));
    this.shininess = Math.max(10.0, shininess);
    this.specularColor = specularColor;
    this.reflectivity = Math.max(0.0, Math.min(1.0, reflectivity));
    this.ior = Math.max(1.0, ior);
    this.transparency = Math.max(0.0, Math.min(1.0, transparency));
    this.iridescenceStrength = Math.max(0.0, Math.min(1.0, iridescenceStrength));
    this.iridescenceSpeed = Math.max(0.0, iridescenceSpeed);
    this.objectTransform = new Matrix4();

    // Cache specular color channels
    this.specR = (specularColor >> 16) & 0xFF;
    this.specG = (specularColor >> 8) & 0xFF;
    this.specB = specularColor & 0xFF;
  }

  @Override
  public void setObjectTransform(Matrix4 tm) {
    if (tm == null) tm = new Matrix4();
    this.objectTransform = tm;
  }

  @Override
  public int getColorAt(Point3 point, Vector3 normal, Light light, Point3 viewerPoint) {
    // Calculate iridescent surface color based on view angle
    int surfColor = calculateIridescentColor(point, normal, viewerPoint);
    int surfR = (surfColor >> 16) & 0xFF;
    int surfG = (surfColor >> 8) & 0xFF;
    int surfB = surfColor & 0xFF;

    // Get light properties
    LightProperties.getLightProperties(light, point, tempLightProps);
    tempLightDir.set(tempLightProps.direction);

    int lightR = (tempLightProps.color >> 16) & 0xFF;
    int lightG = (tempLightProps.color >> 8) & 0xFF;
    int lightB = tempLightProps.color & 0xFF;

    // View direction
    tempViewDir.set(viewerPoint).subtract(point).normalize();

    // Normalize normal (just in case)
    normal.normalize();

    // Fresnel effect - stronger at grazing angles
    double NdotV = Math.max(0.0, normal.dot(tempViewDir));
    double fresnel = Math.pow(1.0 - NdotV, 3.0);

    // Diffuse term with metallic falloff
    double NdotL = Math.max(0.0, normal.dot(tempLightDir));
    double diffuse = NdotL * (1.0 - fresnel * 0.5);

    // Specular (Blinn-Phong) with metallic intensity
    tempHalfVec.set(tempLightDir).add(tempViewDir).normalize();
    double NdotH = Math.max(0.0, normal.dot(tempHalfVec));
    double specular = Math.pow(NdotH, shininess);

    // Enhanced specular for metal
    double specularIntensity = specular * (1.0 + fresnel * 0.5);

    double intensity = tempLightProps.intensity;

    // Ambient with metallic characteristic
    double ambientTerm = ambientCoeff * (0.5 + 0.5 * NdotV);

    // Diffuse term
    double diffuseTerm = intensity * diffuseCoeff * diffuse;

    // Specular term
    double specularTerm = intensity * specularCoeff * specularIntensity;

    // Combine surface color with lighting
    double surfaceLight = ambientTerm + diffuseTerm;

    int r = (int) (surfR * surfaceLight * lightR / 255.0);
    int g = (int) (surfG * surfaceLight * lightG / 255.0);
    int b = (int) (surfB * surfaceLight * lightB / 255.0);

    // Add specular highlight (tinted with specular color)
    r += (int) (specR * specularTerm * lightR / 255.0);
    g += (int) (specG * specularTerm * lightG / 255.0);
    b += (int) (specB * specularTerm * lightB / 255.0);

    // Clamp
    r = Math.min(255, Math.max(0, r));
    g = Math.min(255, Math.max(0, g));
    b = Math.min(255, Math.max(0, b));

    return 0xFF000000 | (r << 16) | (g << 8) | b;
  }

  private int calculateIridescentColor(Point3 worldPoint, Vector3 normal, Point3 viewerPos) {
    // Calculate view angle
    tempViewDir.set(viewerPos).subtract(worldPoint).normalize();
    double NdotV = Math.max(0.0, Math.min(1.0, tempViewDir.dot(normal)));

    // Angle factor for iridescence
    double angleFactor = (1.0 - NdotV) * iridescenceSpeed;

    // Multiple wave interference simulation
    double rShift = Math.sin(angleFactor * 2.1 + 0.0) * 0.5 + 0.5;
    double gShift = Math.sin(angleFactor * 2.7 + 1.2) * 0.5 + 0.5;
    double bShift = Math.sin(angleFactor * 3.3 + 2.4) * 0.5 + 0.5;

    // Secondary harmonics for more complex color shifts
    double rShift2 = Math.sin(angleFactor * 4.2 + 0.5) * 0.3 + 0.3;
    double gShift2 = Math.sin(angleFactor * 4.8 + 1.7) * 0.3 + 0.3;
    double bShift2 = Math.sin(angleFactor * 5.4 + 2.9) * 0.3 + 0.3;

    // Combine shifts
    double rFinal = Math.min(1.0, rShift * 0.7 + rShift2 * 0.3);
    double gFinal = Math.min(1.0, gShift * 0.7 + gShift2 * 0.3);
    double bFinal = Math.min(1.0, bShift * 0.7 + bShift2 * 0.3);

    // Apply strength
    double strength = iridescenceStrength;
    double invStrength = 1.0 - strength;

    // Enhanced color mixing with saturation boost
    // int r = (int) ((baseR * invStrength + (baseR * rFinal * 1.2) * strength));
    // int g = (int) ((baseG * invStrength + (baseG * gFinal * 1.2) * strength));
    // int b = (int) ((baseB * invStrength + (baseB * bFinal * 1.2) * strength));

    // Independent iridescent color
    int irR = (int) (255 * rFinal);
    int irG = (int) (255 * gFinal);
    int irB = (int) (255 * bFinal);

    int r = (int) (baseR * invStrength + irR * strength);
    int g = (int) (baseG * invStrength + irG * strength);
    int b = (int) (baseB * invStrength + irB * strength);

    r = Math.min(255, Math.max(0, r));
    g = Math.min(255, Math.max(0, g));
    b = Math.min(255, Math.max(0, b));

    return 0xFF000000 | (r << 16) | (g << 8) | b;
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
    sb.append("AnodizedMetalMaterial anodizedmetalmaterial {\n");
    sb.append("        baseColor = ").append(ColorUtil.toColorString(baseColor)).append(";\n");
    sb.append("        ambientCoeff = ").append(ambientCoeff).append(";\n");
    sb.append("        diffuseCoeff = ").append(diffuseCoeff).append(";\n");
    sb.append("        specularCoeff = ").append(specularCoeff).append(";\n");
    sb.append("        shininess = ").append(shininess).append(";\n");
    sb.append("        specularColor = ")
        .append(ColorUtil.toColorString(specularColor))
        .append(";\n");
    sb.append("        reflectivity = ").append(reflectivity).append(";\n");
    sb.append("        ior = ").append(ior).append(";\n");
    sb.append("        transparency = ").append(transparency).append(";\n");
    sb.append("        iridescenceStrength = ").append(iridescenceStrength).append(";\n");
    sb.append("        iridescenceSpeed = ").append(iridescenceSpeed).append(";\n");
    sb.append("    }");
    return sb.toString();
  }
}
