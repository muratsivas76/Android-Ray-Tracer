package net.murat.material;

import net.murat.light.Light;
import net.murat.light.LightProperties;
import net.murat.math.*;

public class StarryNightMaterial implements Material {

  private final int baseColor;
  private final int starColor;

  private final int baseR, baseG, baseB;
  private final int starR, starG, starB;

  private Matrix4 objectTransform;
  private Matrix4 inverseObjectTransform;
  private Matrix4 inverseTransposeTransform;

  private final Vector3 tempNormal = new Vector3();
  private final Vector3 tempViewDir = new Vector3();
  private final Vector3 tempLightDir = new Vector3();
  private final Vector3 tempHalfVec = new Vector3();
  private final LightProperties tempLightProps = new LightProperties();

  public StarryNightMaterial() {
    this(0xFF0A0A32, 0xFFFFFFC8);
  }

  public StarryNightMaterial(int baseColor, int starColor) {
    this.baseColor = baseColor;
    this.starColor = starColor;

    this.baseR = (this.baseColor >> 16) & 0xFF;
    this.baseG = (this.baseColor >> 8) & 0xFF;
    this.baseB = this.baseColor & 0xFF;

    this.starR = (this.starColor >> 16) & 0xFF;
    this.starG = (this.starColor >> 8) & 0xFF;
    this.starB = this.starColor & 0xFF;

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

    // Star pattern
    double starPattern =
        Math.sin(point.x * 100) * Math.cos(point.y * 100) * Math.sin(point.z * 100);
    boolean isStar = Math.abs(starPattern) > 0.9;

    int baseR = isStar ? this.starR : this.baseR;
    int baseG = isStar ? this.starG : this.baseG;
    int baseB = isStar ? this.starB : this.baseB;

    double diffuse = Math.max(0.0, tempNormal.dot(tempLightDir));

    tempViewDir.set(viewerPoint).subtract(point).normalize();

    double rim = Math.pow(1.0 - Math.max(0.0, tempNormal.dot(tempViewDir)), 2.0);

    tempHalfVec.set(tempLightDir).add(tempViewDir).normalize();
    double specAngle = Math.max(0.0, tempNormal.dot(tempHalfVec));
    double specular = Math.pow(specAngle, 30.0) * 0.2;

    double ambient = 0.3;
    double intensity = tempLightProps.intensity;

    double totalLight = ambient + intensity * (0.7 * diffuse + 0.2 * rim) + intensity * specular;

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
    return 0.0;
  }

  @Override
  public double getReflectivity() {
    return 0.1;
  }

  @Override
  public double getIndexOfRefraction() {
    return 1.0;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("StarryNightMaterial starrynightmaterial = {\n");
    sb.append("    }");
    return sb.toString();
  }
}
