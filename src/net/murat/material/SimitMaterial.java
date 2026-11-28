package net.murat.material;

import net.murat.light.*;
import net.murat.math.*;
import net.murat.util.ColorUtil;

public class SimitMaterial implements Material {

  private final int crustColor;
  private final int sesameColor;
  private final int softColor;
  private final double crispiness;
  private final double sesameDensity;

  private final int crustR, crustG, crustB;
  private final int sesameR, sesameG, sesameB;
  private final int softR, softG, softB;

  private Matrix4 objectTransform;
  private Matrix4 inverseObjectTransform;
  private Matrix4 inverseTransposeTransform;

  private final Vector3 tempNormal = new Vector3();
  private final Vector3 tempViewDir = new Vector3();
  private final Vector3 tempLightDir = new Vector3();
  private final Vector3 tempHalfVec = new Vector3();
  private final LightProperties tempLightProps = new LightProperties();

  public static final int CRUST_GOLDEN = 0xFFD2B48C;
  public static final int SESAME_SEED = 0xFFF5DEB3;
  public static final int SOFT_INTERIOR = 0xFFFFF8DC;

  public SimitMaterial() {
    this(CRUST_GOLDEN, SESAME_SEED, SOFT_INTERIOR, 0.8, 0.7);
  }

  public SimitMaterial(
      int crustColor, int sesameColor, int softColor, double crispiness, double sesameDensity) {
    this.crustColor = crustColor;
    this.sesameColor = sesameColor;
    this.softColor = softColor;
    this.crispiness = Math.max(0.0, Math.min(1.0, crispiness));
    this.sesameDensity = Math.max(0.0, Math.min(1.0, sesameDensity));

    this.crustR = (this.crustColor >> 16) & 0xFF;
    this.crustG = (this.crustColor >> 8) & 0xFF;
    this.crustB = this.crustColor & 0xFF;

    this.sesameR = (this.sesameColor >> 16) & 0xFF;
    this.sesameG = (this.sesameColor >> 8) & 0xFF;
    this.sesameB = this.sesameColor & 0xFF;

    this.softR = (this.softColor >> 16) & 0xFF;
    this.softG = (this.softColor >> 8) & 0xFF;
    this.softB = this.softColor & 0xFF;

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

    // Simit texture patterns
    double crustPattern = Math.sin(point.x * 12.0) * Math.cos(point.y * 10.0);
    double sesamePattern = Math.sin(point.x * 25.0) * Math.cos(point.z * 20.0);
    double softPattern = Math.cos(point.y * 8.0) * Math.sin(point.z * 6.0);

    // Determine material component
    int baseR, baseG, baseB;
    if (sesamePattern > 0.8 - (sesameDensity * 0.3)) {
      baseR = sesameR;
      baseG = sesameG;
      baseB = sesameB;
    } else if (crustPattern > 0.4 - (crispiness * 0.2)) {
      baseR = crustR;
      baseG = crustG;
      baseB = crustB;
    } else {
      baseR = softR;
      baseG = softG;
      baseB = softB;
    }

    // Crispiness effect - darken
    baseR = (int) (baseR * (1.0 - crispiness * 0.1));
    baseG = (int) (baseG * (1.0 - crispiness * 0.1));
    baseB = (int) (baseB * (1.0 - crispiness * 0.1));

    double diffuse = Math.max(0.0, tempNormal.dot(tempLightDir));

    tempViewDir.set(viewerPoint).subtract(point).normalize();

    double rim = Math.pow(1.0 - Math.max(0.0, tempNormal.dot(tempViewDir)), 2.0);

    tempHalfVec.set(tempLightDir).add(tempViewDir).normalize();
    double specAngle = Math.max(0.0, tempNormal.dot(tempHalfVec));
    double shininess = 40.0 * (1.0 - crispiness * 0.5);
    double specular = Math.pow(specAngle, shininess) * (1.0 - crispiness) * 0.3;

    double ambient = 0.3;
    double intensity = tempLightProps.intensity;

    double totalLight = ambient + intensity * (0.6 * diffuse + 0.2 * rim) + intensity * specular;

    int r = (int) ((baseR * totalLight) * lightR / 255.0);
    int g = (int) ((baseG * totalLight) * lightG / 255.0);
    int b = (int) ((baseB * totalLight) * lightB / 255.0);

    // Freshly baked glow
    if (crispiness > 0.7) {
      r = Math.min(255, r + 10);
      g = Math.min(255, g + 8);
    }

    r = r > 255 ? 255 : (r < 0 ? 0 : r);
    g = g > 255 ? 255 : (g < 0 ? 0 : g);
    b = b > 255 ? 255 : (b < 0 ? 0 : b);

    return (0xFF000000 | (r << 16) | (g << 8) | b);
  }

  @Override
  public double getTransparency() {
    return 0.0;
  }

  @Override
  public double getReflectivity() {
    return 0.05;
  }

  @Override
  public double getIndexOfRefraction() {
    return 1.2;
  }

  public int getCrustColor() {
    return crustColor;
  }

  public int getSesameColor() {
    return sesameColor;
  }

  public int getSoftColor() {
    return softColor;
  }

  public double getCrispiness() {
    return crispiness;
  }

  public double getSesameDensity() {
    return sesameDensity;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("SimitMaterial simitmaterial {\n");
    sb.append("        crustColor = ").append(ColorUtil.toColorString(crustColor)).append(";\n");
    sb.append("        sesameColor = ").append(ColorUtil.toColorString(sesameColor)).append(";\n");
    sb.append("        softColor = ").append(ColorUtil.toColorString(softColor)).append(";\n");
    sb.append("        crispiness = ").append(crispiness).append(";\n");
    sb.append("        sesameDensity = ").append(sesameDensity).append(";\n");
    sb.append("    }");
    return sb.toString();
  }
}
