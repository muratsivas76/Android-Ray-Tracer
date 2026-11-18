/**
 * ColorDiffuseMaterial.java Package: net.murat.material
 *
 * <p>Water material - physically-based water/liquid with Fresnel effect, specular highlights, and
 * subtle blue tint. Based on AmberMaterial quality standards with Blinn-Phong shading.
 */
package net.murat.material;

import net.murat.light.Light;
import net.murat.light.LightProperties;
import net.murat.math.*;
import net.murat.util.ColorUtil;

public class ColorDiffuseMaterial implements Material {

  private int baseColor;
  private double transparency;
  private double reflectivity;
  private double indexOfRefraction;
  private Matrix4 objectTransform;
  private Matrix4 inverseObjectTransform;
  private Matrix4 inverseTransposeTransform;

  private int baseR;
  private int baseG;
  private int baseB;

  private double shininess = 128.0;
  private double specularStrength = 0.8;

  private final Vector3 tempNormal = new Vector3();
  private final Vector3 tempViewDir = new Vector3();
  private final Vector3 tempLightDir = new Vector3();
  private final Vector3 tempHalfVec = new Vector3();
  private final LightProperties tempLightProps = new LightProperties();

  public ColorDiffuseMaterial() {
    this.baseColor = 0xFF4488CC;
    this.baseR = 0x44;
    this.baseG = 0x88;
    this.baseB = 0xCC;
    this.transparency = 0.90;
    this.reflectivity = 0.10;
    this.indexOfRefraction = 1.33;
    this.objectTransform = new Matrix4();
    this.inverseObjectTransform = new Matrix4();
    this.inverseTransposeTransform = new Matrix4();
  }

  public ColorDiffuseMaterial(
      int baseColor, double indexOfRefraction, double reflectivity, double transparency) {
    this.baseColor = baseColor;
    this.baseR = (baseColor >> 16) & 0xFF;
    this.baseG = (baseColor >> 8) & 0xFF;
    this.baseB = baseColor & 0xFF;
    this.transparency = transparency;
    this.reflectivity = reflectivity;
    this.indexOfRefraction = indexOfRefraction;
    this.objectTransform = new Matrix4();
    this.inverseObjectTransform = new Matrix4();
    this.inverseTransposeTransform = new Matrix4();
  }

  @Override
  public int getColorAt(Point3 point, Vector3 normal, Light light, Point3 viewerPoint) {
    tempNormal.set(normal).normalize();

    LightProperties.getLightProperties(light, point, tempLightProps);
    tempLightDir.set(tempLightProps.direction);

    int lightColor = tempLightProps.color;
    int lightR = (lightColor >> 16) & 0xFF;
    int lightG = (lightColor >> 8) & 0xFF;
    int lightB = lightColor & 0xFF;

    tempViewDir.set(viewerPoint).subtract(point).normalize();

    double fresnel = calculateFresnel(tempNormal, tempViewDir);

    double diffuse = Math.max(0.0, tempNormal.dot(tempLightDir));

    tempHalfVec.set(tempLightDir).add(tempViewDir).normalize();
    double specAngle = Math.max(0.0, tempNormal.dot(tempHalfVec));
    double specular = Math.pow(specAngle, shininess);

    double rim = Math.pow(1.0 - Math.max(0.0, tempNormal.dot(tempViewDir)), 3.0);

    double ambient = 0.02 + fresnel * 0.08;
    double intensity = tempLightProps.intensity;

    double totalLight =
        ambient
            + intensity * (0.5 * diffuse + 0.3 * rim)
            + intensity * specularStrength * specular * (1.0 - fresnel * 0.3)
            + intensity * fresnel * 0.2;

    int r = (int) (baseR * totalLight * lightR / 255.0);
    int g = (int) (baseG * totalLight * lightG / 255.0);
    int b = (int) (baseB * totalLight * lightB / 255.0);

    r = r > 255 ? 255 : (r < 0 ? 0 : r);
    g = g > 255 ? 255 : (g < 0 ? 0 : g);
    b = b > 255 ? 255 : (b < 0 ? 0 : b);

    return (0xFF000000 | (r << 16) | (g << 8) | b);
  }

  private double calculateFresnel(Vector3 normal, Vector3 viewDir) {
    double cosTheta = Math.abs(viewDir.dot(normal));
    if (cosTheta < 0.0) cosTheta = 0.0;
    if (cosTheta > 1.0) cosTheta = 1.0;

    double r0 = (1.0 - indexOfRefraction) / (1.0 + indexOfRefraction);
    r0 = r0 * r0;

    double fresnel = r0 + (1.0 - r0) * Math.pow(1.0 - cosTheta, 5.0);

    if (fresnel < 0.0) return 0.0;
    if (fresnel > 1.0) return 1.0;
    return fresnel;
  }

  @Override
  public double getTransparency() {
    return transparency;
  }

  @Override
  public double getReflectivity() {
    return reflectivity;
  }

  @Override
  public double getIndexOfRefraction() {
    return indexOfRefraction;
  }

  @Override
  public void setObjectTransform(Matrix4 tm) {
    if (tm == null) {
      this.objectTransform = new Matrix4();
    } else {
      this.objectTransform = tm;
    }
    this.inverseObjectTransform = this.objectTransform.inverse();
    this.inverseTransposeTransform = this.inverseObjectTransform.inverseTransposeForNormal();
  }

  public void setBaseColor(int baseColor) {
    this.baseColor = baseColor;
    this.baseR = (baseColor >> 16) & 0xFF;
    this.baseG = (baseColor >> 8) & 0xFF;
    this.baseB = baseColor & 0xFF;
  }

  public int getBaseColor() {
    return this.baseColor;
  }

  public void setShininess(double shininess) {
    this.shininess = shininess;
  }

  public double getShininess() {
    return this.shininess;
  }

  public void setSpecularStrength(double specularStrength) {
    this.specularStrength = specularStrength;
  }

  public double getSpecularStrength() {
    return this.specularStrength;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("ColorDiffuseMaterial colordiffusematerial {\n");
    sb.append("        baseColor = ").append(ColorUtil.toColorString(baseColor)).append(";\n");
    sb.append("        ior = ").append(indexOfRefraction).append(";\n");
    sb.append("        reflectivity = ").append(reflectivity).append(";\n");
    sb.append("        transparency = ").append(transparency).append(";\n");
    sb.append("    }");
    return sb.toString();
  }
}
