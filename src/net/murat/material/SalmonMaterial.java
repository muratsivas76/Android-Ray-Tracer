package net.murat.material;

import net.murat.light.Light;
import net.murat.light.LightProperties;
import net.murat.math.*;
import net.murat.util.ColorUtil;

public class SalmonMaterial implements Material {

  private int fleshColor;
  private int skinColor;
  private int fatColor;
  private double freshness;
  private double oiliness;

  private int fleshR, fleshG, fleshB;
  private int skinR, skinG, skinB;
  private int fatR, fatG, fatB;

  private Matrix4 objectTransform;
  private Matrix4 inverseObjectTransform;
  private Matrix4 inverseTransposeTransform;

  private final Vector3 tempNormal = new Vector3();
  private final Vector3 tempViewDir = new Vector3();
  private final Vector3 tempLightDir = new Vector3();
  private final Vector3 tempHalfVec = new Vector3();
  private final LightProperties tempLightProps = new LightProperties();

  public static final int SALMON_PINK = 0xFFFF91A4;
  public static final int SALMON_SKIN = 0xFFC86450;
  public static final int SALMON_FAT = 0xFFFFDCB4;
  public static final int SMOKED_SALMON = 0xFFFFA07A;

  public SalmonMaterial() {
    this(SALMON_PINK, SALMON_SKIN, SALMON_FAT, 0.9, 0.7);
  }

  public SalmonMaterial(
      int fleshColor, int skinColor, int fatColor, double freshness, double oiliness) {
    this.fleshColor = ColorUtil.adjustSaturation(fleshColor, 1.1f);
    this.skinColor = skinColor;
    this.fatColor = fatColor;
    this.freshness = Math.max(0.0, Math.min(1.0, freshness));
    this.oiliness = Math.max(0.0, Math.min(1.0, oiliness));

    this.fleshR = (this.fleshColor >> 16) & 0xFF;
    this.fleshG = (this.fleshColor >> 8) & 0xFF;
    this.fleshB = this.fleshColor & 0xFF;

    this.skinR = (this.skinColor >> 16) & 0xFF;
    this.skinG = (this.skinColor >> 8) & 0xFF;
    this.skinB = this.skinColor & 0xFF;

    this.fatR = (this.fatColor >> 16) & 0xFF;
    this.fatG = (this.fatColor >> 8) & 0xFF;
    this.fatB = this.fatColor & 0xFF;

    this.objectTransform = new Matrix4();
    this.inverseObjectTransform = new Matrix4();
    this.inverseTransposeTransform = new Matrix4();
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

  @Override
  public int getColorAt(Point3 point, Vector3 normal, Light light, Point3 viewerPoint) {
    tempNormal.set(normal).normalize();

    LightProperties.getLightProperties(light, point, tempLightProps);
    tempLightDir.set(tempLightProps.direction);

    int lightColor = tempLightProps.color;
    int lightR = (lightColor >> 16) & 0xFF;
    int lightG = (lightColor >> 8) & 0xFF;
    int lightB = lightColor & 0xFF;

    // Pattern - salmon fibrous structure
    double fiberPattern = Math.sin(point.x * 20.0 + point.y * 15.0) * 0.4;
    double marbling = Math.cos(point.y * 25.0) * Math.sin(point.z * 18.0) * 0.3;
    double patternValue = fiberPattern + marbling;
    double absPattern = patternValue > 0.0 ? patternValue : -patternValue;

    int baseR, baseG, baseB;
    if (absPattern > 0.8) {
      baseR = fatR;
      baseG = fatG;
      baseB = fatB;
    } else if (absPattern > 0.6) {
      baseR = skinR;
      baseG = skinG;
      baseB = skinB;
    } else {
      baseR = fleshR;
      baseG = fleshG;
      baseB = fleshB;
    }

    double diffuse = Math.max(0.0, tempNormal.dot(tempLightDir));

    tempViewDir.set(viewerPoint).subtract(point).normalize();

    double rim = Math.pow(1.0 - Math.max(0.0, tempNormal.dot(tempViewDir)), 2.0);

    tempHalfVec.set(tempLightDir).add(tempViewDir).normalize();
    double specAngle = Math.max(0.0, tempNormal.dot(tempHalfVec));
    double shininess = 20.0 + 60.0 * oiliness;
    double specular = Math.pow(specAngle, shininess) * oiliness;

    double ambient = 0.08;
    double intensity = tempLightProps.intensity;

    double totalLight =
        ambient + intensity * (0.6 * diffuse + 0.2 * rim) + intensity * 0.6 * specular;

    int r = (int) ((baseR * totalLight) * lightR / 255.0);
    int g = (int) ((baseG * totalLight) * lightG / 255.0);
    int b = (int) ((baseB * totalLight) * lightB / 255.0);

    // Freshness effect
    if (freshness > 0.7) {
      r = Math.min(255, r + 5);
      g = Math.min(255, g + 3);
    }

    r = r > 255 ? 255 : (r < 0 ? 0 : r);
    g = g > 255 ? 255 : (g < 0 ? 0 : g);
    b = b > 255 ? 255 : (b < 0 ? 0 : b);

    return (0xFF000000 | (r << 16) | (g << 8) | b);
  }

  @Override
  public double getTransparency() {
    return 0.05 * (1.0 - freshness);
  }

  @Override
  public double getReflectivity() {
    return oiliness * 0.4;
  }

  @Override
  public double getIndexOfRefraction() {
    return 1.33;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("SalmonMaterial salmonmaterial {\n");
    sb.append("        fleshColor = ").append(ColorUtil.toColorString(fleshColor)).append(";\n");
    sb.append("        skinColor = ").append(ColorUtil.toColorString(skinColor)).append(";\n");
    sb.append("        fatColor = ").append(ColorUtil.toColorString(fatColor)).append(";\n");
    sb.append("        freshness = ").append(freshness).append(";\n");
    sb.append("        oiliness = ").append(oiliness).append(";\n");
    sb.append("    }");
    return sb.toString();
  }
}
