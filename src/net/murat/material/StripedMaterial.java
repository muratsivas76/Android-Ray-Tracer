package net.murat.material;

import net.murat.light.*;
import net.murat.math.*;
import net.murat.util.ColorUtil;

public class StripedMaterial implements Material {

  private final int color1;
  private final int color2;
  private final double stripeSize;
  private final StripeDirection direction;

  private int color1R, color1G, color1B;
  private int color2R, color2G, color2B;

  private final double ambientCoefficient;
  private final double diffuseCoefficient;
  private final double specularCoefficient;
  private final double shininess;
  private final double reflectivity;
  private final double ior;
  private final double transparency;

  private Matrix4 objectTransform;
  private Matrix4 inverseObjectTransform;

  private final Vector3 tempNormal = new Vector3();
  private final Vector3 tempViewDir = new Vector3();
  private final Vector3 tempLightDir = new Vector3();
  private final Vector3 tempReflectDir = new Vector3();
  private final LightProperties tempLightProps = new LightProperties();

  public StripedMaterial(
      int color1,
      int color2,
      double stripeSize,
      StripeDirection direction,
      double ambientCoefficient,
      double diffuseCoefficient,
      double specularCoefficient,
      double shininess,
      double reflectivity,
      double ior,
      double transparency) {
    this.color1 = color1;
    this.color2 = color2;
    this.stripeSize = stripeSize;
    this.direction = direction;

    this.color1R = (this.color1 >> 16) & 0xFF;
    this.color1G = (this.color1 >> 8) & 0xFF;
    this.color1B = this.color1 & 0xFF;

    this.color2R = (this.color2 >> 16) & 0xFF;
    this.color2G = (this.color2 >> 8) & 0xFF;
    this.color2B = this.color2 & 0xFF;

    this.ambientCoefficient = ambientCoefficient;
    this.diffuseCoefficient = diffuseCoefficient;
    this.specularCoefficient = specularCoefficient;
    this.shininess = shininess;
    this.reflectivity = reflectivity;
    this.ior = ior;
    this.transparency = transparency;

    this.objectTransform = new Matrix4();
    this.inverseObjectTransform = new Matrix4();
  }

  public StripedMaterial(int color1, int color2, double stripeSize, StripeDirection direction) {
    this(color1, color2, stripeSize, direction, 0.1, 0.8, 0.1, 10.0, 0.0, 1.0, 0.0);
  }

  public StripedMaterial(int color1, int color2, double stripeSize) {
    this(color1, color2, stripeSize, StripeDirection.VERTICAL);
  }

  @Override
  public void setObjectTransform(Matrix4 tm) {
    if (tm == null) {
      this.objectTransform = new Matrix4();
    } else {
      this.objectTransform = tm;
    }
    this.inverseObjectTransform = this.objectTransform.inverse();
  }

  private int getPatternColor(double u, double v) {
    double value;
    switch (direction) {
      case HORIZONTAL:
        value = v;
        break;
      case VERTICAL:
        value = u;
        break;
      case DIAGONAL:
        value = (u + v) / 2.0;
        break;
      default:
        value = u;
        break;
    }

    int r, g, b;
    if (Math.floor(value + 1e-10) % 2 == 0) {
      r = color1R;
      g = color1G;
      b = color1B;
    } else {
      r = color2R;
      g = color2G;
      b = color2B;
    }

    return (0xFF000000 | (r << 16) | (g << 8) | b);
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

    Point3 localPoint = inverseObjectTransform.transformPoint(point);

    double absX = Math.abs(localPoint.x);
    double absY = Math.abs(localPoint.y);
    double absZ = Math.abs(localPoint.z);

    double u, v;
    if (absX >= absY && absX >= absZ) {
      u = (localPoint.y + 1.0) * 0.5;
      v = (localPoint.z + 1.0) * 0.5;
    } else if (absY >= absX && absY >= absZ) {
      u = (localPoint.x + 1.0) * 0.5;
      v = (localPoint.z + 1.0) * 0.5;
    } else {
      u = (localPoint.x + 1.0) * 0.5;
      v = (localPoint.y + 1.0) * 0.5;
    }

    u *= stripeSize;
    v *= stripeSize;

    int patternColor = getPatternColor(u, v);
    int baseR = (patternColor >> 16) & 0xFF;
    int baseG = (patternColor >> 8) & 0xFF;
    int baseB = patternColor & 0xFF;

    double diffuse = Math.max(0.0, tempNormal.dot(tempLightDir));

    tempViewDir.set(viewerPoint).subtract(point).normalize();

    tempReflectDir.set(tempLightDir).negate().reflect(tempNormal, tempReflectDir);
    double RdotV = Math.max(0.0, tempReflectDir.dot(tempViewDir));
    double specular = Math.pow(RdotV, shininess);

    double ambient = ambientCoefficient;
    double intensity = tempLightProps.intensity;

    double totalLight =
        ambient
            + intensity * (diffuseCoefficient * diffuse)
            + intensity * (specularCoefficient * specular);

    int r = (int) ((baseR * totalLight) * lightR / 255.0);
    int g = (int) ((baseG * totalLight) * lightG / 255.0);
    int b = (int) ((baseB * totalLight) * lightB / 255.0);

    r = r > 255 ? 255 : (r < 0 ? 0 : r);
    g = g > 255 ? 255 : (g < 0 ? 0 : g);
    b = b > 255 ? 255 : (b < 0 ? 0 : b);

    return (0xFF000000 | (r << 16) | (g << 8) | b);
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
    return ior;
  }

  public int getColor1() {
    return color1;
  }

  public int getColor2() {
    return color2;
  }

  public double getStripeSize() {
    return stripeSize;
  }

  public StripeDirection getDirection() {
    return direction;
  }

  public double getAmbientCoefficient() {
    return ambientCoefficient;
  }

  public double getDiffuseCoefficient() {
    return diffuseCoefficient;
  }

  public double getSpecularCoefficient() {
    return specularCoefficient;
  }

  public double getShininess() {
    return shininess;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("StripedMaterial stripedmaterial {\n");
    sb.append("        color1 = ").append(ColorUtil.toColorString(color1)).append(";\n");
    sb.append("        color2 = ").append(ColorUtil.toColorString(color2)).append(";\n");
    sb.append("        stripeSize = ").append(stripeSize).append(";\n");
    sb.append("        direction = ").append(direction).append(";\n");
    sb.append("        ambientCoefficient = ").append(ambientCoefficient).append(";\n");
    sb.append("        diffuseCoefficient = ").append(diffuseCoefficient).append(";\n");
    sb.append("        specularCoefficient = ").append(specularCoefficient).append(";\n");
    sb.append("        shininess = ").append(shininess).append(";\n");
    sb.append("        reflectivity = ").append(reflectivity).append(";\n");
    sb.append("        ior = ").append(ior).append(";\n");
    sb.append("        transparency = ").append(transparency).append(";\n");
    sb.append("    }");
    return sb.toString();
  }
}
