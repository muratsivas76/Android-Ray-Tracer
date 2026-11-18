package net.murat.material;

import net.murat.light.Light;
import net.murat.light.LightProperties;
import net.murat.math.*;
import net.murat.util.ColorUtil;

public class DiamondMaterial implements Material {
  private int baseColor;
  private int specularColor;
  private double transparency;
  private double reflectivity;
  private double indexOfRefraction;
  private double dispersionStrength;
  private double fireEffect;

  private int fireColor1;
  private int fireColor2;
  private int fireColor3;

  // ===== TRANSFORM MATRICES =====
  private Matrix4 objectTransform;
  private Matrix4 inverseObjectTransform;
  private Matrix4 inverseTransposeTransform;
  private boolean hasTransform = false;

  private int baseR, baseG, baseB;
  private int specR, specG, specB;

  private double shininess = 512.0;
  private double specularStrength = 6.0;

  // ===== GC-FRIENDLY TEMP OBJECTS =====
  private final Vector3 tempNormal = new Vector3();
  private final Vector3 tempViewDir = new Vector3();
  private final Vector3 tempLightDir = new Vector3();
  private final Vector3 tempHalfVec = new Vector3();
  private final Point3 tempPoint = new Point3();
  private final LightProperties tempLightProps = new LightProperties();

  /** Full constructor with custom fire colors AND specular color. */
  public DiamondMaterial(
      int baseColorPacked,
      int specularColorPacked,
      double ior,
      double reflectivity,
      double transparency,
      double dispersionStrength,
      double fireEffect,
      int fireColor1,
      int fireColor2,
      int fireColor3) {
    this.baseColor = baseColorPacked;
    this.specularColor = specularColorPacked;
    this.baseR = (baseColorPacked >> 16) & 0xFF;
    this.baseG = (baseColorPacked >> 8) & 0xFF;
    this.baseB = baseColorPacked & 0xFF;
    this.specR = (specularColorPacked >> 16) & 0xFF;
    this.specG = (specularColorPacked >> 8) & 0xFF;
    this.specB = specularColorPacked & 0xFF;
    this.indexOfRefraction = ior;
    this.reflectivity = reflectivity;
    this.transparency = transparency;
    this.dispersionStrength = dispersionStrength;
    this.fireEffect = fireEffect;
    this.fireColor1 = fireColor1;
    this.fireColor2 = fireColor2;
    this.fireColor3 = fireColor3;
    this.objectTransform = new Matrix4();
    this.inverseObjectTransform = new Matrix4();
    this.inverseTransposeTransform = new Matrix4();
  }

  /** Constructor with default fire colors and white specular. */
  public DiamondMaterial(
      int baseColorPacked,
      double ior,
      double reflectivity,
      double transparency,
      double dispersionStrength,
      double fireEffect) {
    this(
        baseColorPacked,
        0xFFFFFFFF,
        ior,
        reflectivity,
        transparency,
        dispersionStrength,
        fireEffect,
        0xFFFF4444,
        0xFF44FF44,
        0xFF4444FF);
  }

  public DiamondMaterial(int baseColorPacked, double ior) {
    this(baseColorPacked, ior, 0.2, 0.95, 0.3, 0.8);
  }

  public DiamondMaterial(double ior) {
    this(0xFFB9F2FF, ior);
  }

  public DiamondMaterial() {
    this(2.42);
  }

  @Override
  public int getColorAt(Point3 point, Vector3 normal, Light light, Point3 viewerPoint) {
    // ===== TRANSFORM =====
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

    // Light properties (world space)
    LightProperties.getLightProperties(light, worldPoint, tempLightProps);
    tempLightDir.set(tempLightProps.direction).normalize();

    int lightColor = tempLightProps.color;
    int lightR = (lightColor >> 16) & 0xFF;
    int lightG = (lightColor >> 8) & 0xFF;
    int lightB = lightColor & 0xFF;

    // View direction (world space)
    tempViewDir.set(viewerPoint).subtract(worldPoint).normalize();

    // ===== SPECULAR =====
    tempHalfVec.set(tempLightDir).add(tempViewDir).normalize();
    double specAngle = Math.max(0.0, worldNormal.dot(tempHalfVec));
    double specular = Math.pow(specAngle, shininess) * specularStrength * tempLightProps.intensity;

    double specR_f = specR * specular * lightR / 255.0;
    double specG_f = specG * specular * lightG / 255.0;
    double specB_f = specB * specular * lightB / 255.0;

    // ===== FRESNEL =====
    double nv = Math.max(0.0, worldNormal.dot(tempViewDir));
    double fresnel = calculateFresnel(tempViewDir, worldNormal);
    double rim = Math.pow(1.0 - nv, 3.0) * 0.7;
    double internalGlow = Math.pow(nv, 2.0) * 0.4;

    // ===== DISPERSION / FIRE EFFECT =====
    int dispR = 0, dispG = 0, dispB = 0;
    if (dispersionStrength > 0.0 && fireEffect > 0.0) {
      double intensity = tempLightProps.intensity;
      double fakeFacetPattern =
          Math.sin(worldPoint.getX() * 5.0)
              * Math.cos(worldPoint.getY() * 5.0)
              * Math.sin(worldPoint.getZ() * 5.0);

      double rainbowBase =
          (fakeFacetPattern * 0.5 + 0.5) * dispersionStrength * fireEffect * intensity;

      int fR1 = (fireColor1 >> 16) & 0xFF;
      int fG1 = (fireColor1 >> 8) & 0xFF;
      int fB1 = fireColor1 & 0xFF;

      int fR2 = (fireColor2 >> 16) & 0xFF;
      int fG2 = (fireColor2 >> 8) & 0xFF;
      int fB2 = fireColor2 & 0xFF;

      int fR3 = (fireColor3 >> 16) & 0xFF;
      int fG3 = (fireColor3 >> 8) & 0xFF;
      int fB3 = fireColor3 & 0xFF;

      double wave1 = Math.abs(Math.sin(nv * 8.0));
      double wave2 = Math.abs(Math.sin(nv * 8.0 + 2.09));
      double wave3 = Math.abs(Math.sin(nv * 8.0 + 4.18));

      dispR = (int) (rainbowBase * (fR1 * wave1 + fR2 * wave2 + fR3 * wave3));
      dispG = (int) (rainbowBase * (fG1 * wave1 + fG2 * wave2 + fG3 * wave3));
      dispB = (int) (rainbowBase * (fB1 * wave1 + fB2 * wave2 + fB3 * wave3));
    }

    // ===== BASE COLOR =====
    double ambient = 0.25;
    double totalLight =
        ambient + (transparency * internalGlow) + (rim * reflectivity) + (fresnel * 0.2);

    int r = (int) (baseR * totalLight * lightR / 255.0 + specR_f + dispR);
    int g = (int) (baseG * totalLight * lightG / 255.0 + specG_f + dispG);
    int b = (int) (baseB * totalLight * lightB / 255.0 + specB_f + dispB);

    r = Math.min(255, Math.max(0, r));
    g = Math.min(255, Math.max(0, g));
    b = Math.min(255, Math.max(0, b));

    return (0xFF000000 | (r << 16) | (g << 8) | b);
  }

  private double calculateFresnel(Vector3 viewDir, Vector3 normal) {
    double cosTheta = Math.max(0.0, Math.min(1.0, Math.abs(viewDir.dot(normal))));
    double n1 = 1.0;
    double n2 = indexOfRefraction;
    double r0 = Math.pow((n1 - n2) / (n1 + n2), 2.0);
    return r0 + (1.0 - r0) * Math.pow(1.0 - cosTheta, 5.0);
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
      this.hasTransform = false;
    } else {
      this.objectTransform = tm;
      this.hasTransform = true;
    }
    this.inverseObjectTransform = this.objectTransform.inverse();
    this.inverseTransposeTransform = this.inverseObjectTransform.inverseTransposeForNormal();
  }

  // ===== SETTERS/GETTERS =====

  public int getBaseColor() {
    return baseColor;
  }

  public void setBaseColor(int baseColor) {
    this.baseColor = baseColor;
    this.baseR = (baseColor >> 16) & 0xFF;
    this.baseG = (baseColor >> 8) & 0xFF;
    this.baseB = baseColor & 0xFF;
  }

  public int getSpecularColor() {
    return specularColor;
  }

  public void setSpecularColor(int specularColor) {
    this.specularColor = specularColor;
    this.specR = (specularColor >> 16) & 0xFF;
    this.specG = (specularColor >> 8) & 0xFF;
    this.specB = specularColor & 0xFF;
  }

  public double getDispersionStrength() {
    return dispersionStrength;
  }

  public void setDispersionStrength(double dispersionStrength) {
    this.dispersionStrength = dispersionStrength;
  }

  public double getFireEffect() {
    return fireEffect;
  }

  public void setFireEffect(double fireEffect) {
    this.fireEffect = fireEffect;
  }

  public int getFireColor1() {
    return fireColor1;
  }

  public void setFireColor1(int fireColor1) {
    this.fireColor1 = fireColor1;
  }

  public int getFireColor2() {
    return fireColor2;
  }

  public void setFireColor2(int fireColor2) {
    this.fireColor2 = fireColor2;
  }

  public int getFireColor3() {
    return fireColor3;
  }

  public void setFireColor3(int fireColor3) {
    this.fireColor3 = fireColor3;
  }

  public void setShininess(double shininess) {
    this.shininess = shininess;
  }

  public double getShininess() {
    return shininess;
  }

  public void setSpecularStrength(double specularStrength) {
    this.specularStrength = specularStrength;
  }

  public double getSpecularStrength() {
    return specularStrength;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("DiamondMaterial diamondmaterial {\n");
    sb.append("        baseColor = ").append(ColorUtil.toColorString(baseColor)).append(";\n");
    sb.append("        specularColor = ")
        .append(ColorUtil.toColorString(specularColor))
        .append(";\n");
    sb.append("        ior = ").append(indexOfRefraction).append(";\n");
    sb.append("        reflectivity = ").append(reflectivity).append(";\n");
    sb.append("        transparency = ").append(transparency).append(";\n");
    sb.append("        dispersionStrength = ").append(dispersionStrength).append(";\n");
    sb.append("        fireEffect = ").append(fireEffect).append(";\n");
    sb.append("        fireColor1 = ").append(ColorUtil.toColorString(fireColor1)).append(";\n");
    sb.append("        fireColor2 = ").append(ColorUtil.toColorString(fireColor2)).append(";\n");
    sb.append("        fireColor3 = ").append(ColorUtil.toColorString(fireColor3)).append(";\n");
    sb.append("        shininess = ").append(shininess).append(";\n");
    sb.append("        specularStrength = ").append(specularStrength).append(";\n");
    sb.append("    }");
    return sb.toString();
  }
}
