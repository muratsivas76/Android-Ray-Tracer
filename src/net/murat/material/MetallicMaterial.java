package net.murat.material;

import net.murat.light.*;
import net.murat.math.*;
import net.murat.util.ColorUtil;

public class MetallicMaterial implements Material {
  private int albedoPacked;
  private double metallic;
  private double roughness;
  private double reflectivity;
  private double ior;
  private double transparency;
  private int emissionColorPacked;
  private double emissionIntensity;
  private double aoStrength;
  private double fresnelReflectance;

  private double shininess;
  private Matrix4 objectInverseTransform;
  private Matrix4 inverseTransposeTransform;

  private final Point3 tempViewerPoint = new Point3();
  private final Vector3 tempViewDir = new Vector3();
  private final Vector3 tempReflectDir = new Vector3();
  private final Vector3 tempLightDir = new Vector3();
  private final Vector3 tempHalfDir = new Vector3();
  private final LightProperties tempLightProps = new LightProperties();

  // FULL PBR CONSTRUCTOR
  public MetallicMaterial(
      int albedo,
      double metallic,
      double roughness,
      double reflectivity,
      double ior,
      double transparency,
      int emissionColor,
      double emissionIntensity,
      double aoStrength,
      double fresnelReflectance,
      Matrix4 objectInverseTransform) {
    this.albedoPacked = albedo;
    this.metallic = metallic;
    this.roughness = roughness;
    this.reflectivity = reflectivity;
    this.ior = ior;
    this.transparency = transparency;
    this.emissionColorPacked = emissionColor;
    this.emissionIntensity = emissionIntensity;
    this.aoStrength = aoStrength;
    this.fresnelReflectance = fresnelReflectance;
    this.objectInverseTransform =
        objectInverseTransform != null ? objectInverseTransform : new Matrix4();
    this.inverseTransposeTransform = this.objectInverseTransform.inverseTransposeForNormal();
    this.shininess = Math.max(1.0, 2.0 / (roughness * roughness) - 2.0);
  }

  @Override
  public void setObjectTransform(Matrix4 tm) {
    this.objectInverseTransform = tm != null ? tm : new Matrix4();
    this.inverseTransposeTransform = this.objectInverseTransform.inverseTransposeForNormal();
  }

  @Override
  public int getColorAt(Point3 point, Vector3 normal, Light light, Point3 viewerPos) {
    LightProperties.getLightProperties(light, point, tempLightProps);

    tempLightDir.set(tempLightProps.direction);
    double intensity = tempLightProps.intensity;
    int lightColor = tempLightProps.color;

    tempViewerPoint.set(viewerPos);
    tempViewDir.set(tempViewerPoint);
    tempViewDir.subtract(point);
    tempViewDir.normalize();

    double NdotL = Math.max(0.0, normal.dot(tempLightDir));
    double NdotV = Math.max(0.0, normal.dot(tempViewDir));

    // Diffuse
    double diffFactor = (1.0 - metallic) * 0.7 + 0.3;
    double diffIntensity = diffFactor * intensity * NdotL;

    // Specular (Blinn-Phong)
    tempHalfDir.set(tempLightDir);
    tempHalfDir.add(tempViewDir);
    tempHalfDir.normalize();
    double NdotH = Math.max(0.0, normal.dot(tempHalfDir));
    double specIntensity = Math.pow(NdotH, shininess) * intensity * 0.8;

    // Extract RGB components from albedo and light
    int albedoR = (albedoPacked >> 16) & 0xFF;
    int albedoG = (albedoPacked >> 8) & 0xFF;
    int albedoB = albedoPacked & 0xFF;

    int lightR = (lightColor >> 16) & 0xFF;
    int lightG = (lightColor >> 8) & 0xFF;
    int lightB = lightColor & 0xFF;

    // Diffuse calculation
    int diffR = (int) (albedoR * lightR * diffIntensity / 255.0);
    int diffG = (int) (albedoG * lightG * diffIntensity / 255.0);
    int diffB = (int) (albedoB * lightB * diffIntensity / 255.0);

    // Specular calculation
    int specColor = albedoPacked;
    int specR = (specColor >> 16) & 0xFF;
    int specG = (specColor >> 8) & 0xFF;
    int specB = specColor & 0xFF;

    int specR_final = (int) (specR * lightR * specIntensity / 255.0);
    int specG_final = (int) (specG * lightG * specIntensity / 255.0);
    int specB_final = (int) (specB * lightB * specIntensity / 255.0);

    // Ambient (10%)
    int ambR = (int) (albedoR * 0.1);
    int ambG = (int) (albedoG * 0.1);
    int ambB = (int) (albedoB * 0.1);

    // Combine
    int finalR = Math.min(255, diffR + specR_final + ambR);
    int finalG = Math.min(255, diffG + specG_final + ambG);
    int finalB = Math.min(255, diffB + specB_final + ambB);

    // Emission
    if (emissionIntensity > 0.0) {
      int emR = ((emissionColorPacked >> 16) & 0xFF);
      int emG = ((emissionColorPacked >> 8) & 0xFF);
      int emB = (emissionColorPacked & 0xFF);
      finalR = Math.min(255, finalR + (int) (emR * emissionIntensity));
      finalG = Math.min(255, finalG + (int) (emG * emissionIntensity));
      finalB = Math.min(255, finalB + (int) (emB * emissionIntensity));
    }

    return (0xFF << 24) | (finalR << 16) | (finalG << 8) | finalB;
  }

  private int getAmbientAndEmission() {
    int ambient = ColorUtil.multiplyColor(albedoPacked, 0.1);
    if (emissionIntensity > 0.0) {
      int emission = ColorUtil.multiplyColor(emissionColorPacked, emissionIntensity);
      return ColorUtil.add(ambient, emission);
    }
    return ambient;
  }

  @Override
  public double getReflectivity() {
    return metallic > 0.5 ? 0.9 : reflectivity;
  }

  @Override
  public double getIndexOfRefraction() {
    return metallic > 0.5 ? 0.0 : ior;
  }

  @Override
  public double getTransparency() {
    return transparency;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("albedo = ").append(ColorUtil.toColorString(albedoPacked)).append(";\n");
    sb.append("metallic = ").append(metallic).append(";\n");
    sb.append("roughness = ").append(roughness).append(";\n");
    sb.append("reflectivity = ").append(reflectivity).append(";\n");
    sb.append("ior = ").append(ior).append(";\n");
    sb.append("transparency = ").append(transparency).append(";\n");
    sb.append("emissionColor = ")
        .append(ColorUtil.toColorString(emissionColorPacked))
        .append(";\n");
    sb.append("emissionIntensity = ").append(emissionIntensity).append(";\n");
    sb.append("aoStrength = ").append(aoStrength).append(";\n");
    sb.append("fresnelReflectance = ").append(fresnelReflectance).append(";\n");
    sb.append("    }");
    return sb.toString();
  }
}
