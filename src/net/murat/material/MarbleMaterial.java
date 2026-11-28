package net.murat.material;

import net.murat.light.*;
import net.murat.math.*;
import net.murat.util.ColorUtil;

/**
 * MarbleMaterial - Natural marble with veining patterns using Perlin noise. Zero-allocation using
 * packed ARGB integers.
 */
public class MarbleMaterial implements Material {
  private final int baseColorPacked;
  private final int veinColorPacked;
  private final int specularColorPacked;
  private final double scale;
  private final double veinDensity;
  private final double turbulence;

  private Matrix4 objectInverseTransform;
  private Matrix4 inverseTransposeTransform;

  // Pre-cached color components
  private final int baseR, baseG, baseB;
  private final int veinR, veinG, veinB;
  private final int specR, specG, specB;

  private final double ambientCoefficient;
  private final double diffuseCoefficient;
  private final double specularCoefficient;
  private final double shininess;
  private final double reflectivity;
  private final double ior;
  private final double transparency;

  // ===== GC DOSTU: PRE-ALLOCATED TEMP OBJECTS =====
  private final Point3 tempLocalPoint = new Point3();
  private final Vector3 tempLocalNormal = new Vector3();
  private final Vector3 tempTransformedNormal = new Vector3();
  private final Vector3 tempViewDir = new Vector3();
  private final Vector3 tempLightDir = new Vector3();
  private final Vector3 tempReflectDir = new Vector3();
  private final Point3 tempViewerPoint = new Point3();
  private final LightProperties tempLightProps = new LightProperties();

  public MarbleMaterial(
      int baseColor,
      int veinColor,
      double scale,
      double veinDensity,
      double turbulence,
      double ambientCoefficient,
      double diffuseCoefficient,
      double specularCoefficient,
      double shininess,
      double reflectivity,
      double ior,
      double transparency,
      Matrix4 objectInverseTransform) {

    this.baseColorPacked = baseColor;
    this.veinColorPacked = veinColor;
    this.specularColorPacked = 0xFFF0F0FF;
    this.scale = scale;
    this.veinDensity = Math.max(0, Math.min(1, veinDensity));
    this.turbulence = Math.max(0, Math.min(1, turbulence));
    this.objectInverseTransform =
        objectInverseTransform != null ? objectInverseTransform : new Matrix4();
    this.inverseTransposeTransform = this.objectInverseTransform.inverseTransposeForNormal();

    this.baseR = (baseColor >> 16) & 0xFF;
    this.baseG = (baseColor >> 8) & 0xFF;
    this.baseB = baseColor & 0xFF;

    this.veinR = (veinColor >> 16) & 0xFF;
    this.veinG = (veinColor >> 8) & 0xFF;
    this.veinB = veinColor & 0xFF;

    this.specR = 240;
    this.specG = 240;
    this.specB = 255;

    this.ambientCoefficient = ambientCoefficient;
    this.diffuseCoefficient = diffuseCoefficient;
    this.specularCoefficient = specularCoefficient;
    this.shininess = shininess;
    this.reflectivity = reflectivity;
    this.ior = ior;
    this.transparency = transparency;
  }

  public MarbleMaterial(
      int baseColor,
      int veinColor,
      double scale,
      double veinDensity,
      double turbulence,
      Matrix4 objectInverseTransform) {
    this(
        baseColor,
        veinColor,
        scale,
        veinDensity,
        turbulence,
        0.1,
        0.7,
        0.2,
        50.0,
        0.15,
        1.5,
        0.05,
        objectInverseTransform);
  }

  @Override
  public void setObjectTransform(Matrix4 tm) {
    if (tm == null) tm = new Matrix4();
    this.objectInverseTransform = tm;
    this.inverseTransposeTransform = tm.inverseTransposeForNormal();
  }

  private int getMarbleColor(Point3 localPoint) {
    double x = localPoint.x * scale;
    double y = localPoint.y * scale;
    double z = localPoint.z * scale;

    double noise = turbulence(x, y, z, turbulence);
    double marblePattern = Math.sin(x + noise * 10.0) * 0.5 + 0.5;
    marblePattern = Math.pow(marblePattern, 1.0 + (veinDensity * 3.0));

    int r = (int) (baseR + (veinR - baseR) * marblePattern);
    int g = (int) (baseG + (veinG - baseG) * marblePattern);
    int b = (int) (baseB + (veinB - baseB) * marblePattern);

    r = r > 255 ? 255 : (r < 0 ? 0 : r);
    g = g > 255 ? 255 : (g < 0 ? 0 : g);
    b = b > 255 ? 255 : (b < 0 ? 0 : b);

    return (0xFF000000 | (r << 16) | (g << 8) | b);
  }

  private double turbulence(double x, double y, double z, double turbulenceFactor) {
    double t = 0.0;
    double size = 0.5;
    while (size >= 0.01) {
      t += Math.abs(improvedNoise(x / size, y / size, z / size)) * size;
      size /= 2.0;
    }
    return t * turbulenceFactor;
  }

  private double improvedNoise(double x, double y, double z) {
    int xi = (int) Math.floor(x) & 255;
    int yi = (int) Math.floor(y) & 255;
    int zi = (int) Math.floor(z) & 255;
    x -= Math.floor(x);
    y -= Math.floor(y);
    z -= Math.floor(z);
    double u = fade(x);
    double v = fade(y);
    double w = fade(z);

    int a = xi + yi * 256 + zi * 256 * 256;
    int aa = a % 256;
    int ab = (a + 1) % 256;
    int ba = (a + 256) % 256;
    int bb = (a + 257) % 256;

    double lerp1 = lerp(u, grad(aa, x, y, z), grad(ab, x - 1, y, z));
    double lerp2 = lerp(u, grad(ba, x, y - 1, z), grad(bb, x - 1, y - 1, z));
    return lerp(v, lerp1, lerp2);
  }

  private double fade(double t) {
    return t * t * t * (t * (t * 6 - 15) + 10);
  }

  private double lerp(double t, double a, double b) {
    return a + t * (b - a);
  }

  private double grad(int hash, double x, double y, double z) {
    int h = hash & 15;
    double u = h < 8 ? x : y;
    double v = h < 4 ? y : (h == 12 || h == 14 ? x : z);
    return ((h & 1) == 0 ? u : -u) + ((h & 2) == 0 ? v : -v);
  }

  @Override
  public int getColorAt(Point3 worldPoint, Vector3 worldNormal, Light light, Point3 viewerPos) {
    if (objectInverseTransform == null) {
      return 0xFF000000;
    }

    // 1. Transform to local space
    objectInverseTransform.transformPoint(worldPoint, tempLocalPoint);

    // 2. Transform normal
    inverseTransposeTransform.transformDirection(worldNormal, tempTransformedNormal);
    tempLocalNormal.set(tempTransformedNormal);
    tempLocalNormal.normalize();

    // 3. Get marble color
    int marbleColor = getMarbleColor(tempLocalPoint);
    int marbleR = (marbleColor >> 16) & 0xFF;
    int marbleG = (marbleColor >> 8) & 0xFF;
    int marbleB = marbleColor & 0xFF;

    // 4. Light properties
    LightProperties.getLightProperties(light, worldPoint, tempLightProps);
    tempLightDir.set(tempLightProps.direction);

    int lightColor = tempLightProps.color;
    double intensity = tempLightProps.intensity;
    int lightR = (lightColor >> 16) & 0xFF;
    int lightG = (lightColor >> 8) & 0xFF;
    int lightB = lightColor & 0xFF;

    // 5. Ambient
    int ambR = (int) (marbleR * ambientCoefficient * lightR / 255.0);
    int ambG = (int) (marbleG * ambientCoefficient * lightG / 255.0);
    int ambB = (int) (marbleB * ambientCoefficient * lightB / 255.0);

    if (light instanceof MuratAmbientLight) {
      ambR = ambR > 255 ? 255 : (ambR < 0 ? 0 : ambR);
      ambG = ambG > 255 ? 255 : (ambG < 0 ? 0 : ambG);
      ambB = ambB > 255 ? 255 : (ambB < 0 ? 0 : ambB);
      return (0xFF000000 | (ambR << 16) | (ambG << 8) | ambB);
    }

    // 6. View direction
    tempViewerPoint.set(viewerPos);
    tempViewDir.set(tempViewerPoint);
    tempViewDir.subtract(worldPoint);
    tempViewDir.normalize();

    // 7. Diffuse
    double NdotL = Math.max(0, tempLocalNormal.dot(tempLightDir));
    double diffFactor = diffuseCoefficient * NdotL * intensity;
    int diffR = (int) (marbleR * diffFactor * lightR / 255.0);
    int diffG = (int) (marbleG * diffFactor * lightG / 255.0);
    int diffB = (int) (marbleB * diffFactor * lightB / 255.0);

    // 8. Reflection direction
    tempReflectDir.set(tempLightDir);
    tempReflectDir.negate();
    tempReflectDir.reflect(tempLocalNormal, tempReflectDir);

    // 9. Specular
    double RdotV = Math.max(0, tempReflectDir.dot(tempViewDir));
    double specFactor = Math.pow(RdotV, shininess) * specularCoefficient * intensity;
    int specR = (int) (this.specR * specFactor * lightR / 255.0);
    int specG = (int) (this.specG * specFactor * lightG / 255.0);
    int specB = (int) (this.specB * specFactor * lightB / 255.0);

    // 10. Combine
    int r = ambR + diffR + specR;
    int g = ambG + diffG + specG;
    int b = ambB + diffB + specB;

    r = r > 255 ? 255 : (r < 0 ? 0 : r);
    g = g > 255 ? 255 : (g < 0 ? 0 : g);
    b = b > 255 ? 255 : (b < 0 ? 0 : b);

    return (0xFF000000 | (r << 16) | (g << 8) | b);
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
    sb.append("MarbleMaterial marblematerial {\n");
    sb.append("        baseColor = ")
        .append(ColorUtil.toColorString(baseColorPacked))
        .append(";\n");
    sb.append("        veinColor = ")
        .append(ColorUtil.toColorString(veinColorPacked))
        .append(";\n");
    sb.append("        scale = ").append(scale).append(";\n");
    sb.append("        veinDensity = ").append(veinDensity).append(";\n");
    sb.append("        turbulence = ").append(turbulence).append(";\n");
    sb.append("        ambient = ").append(ambientCoefficient).append(";\n");
    sb.append("        diffuse = ").append(diffuseCoefficient).append(";\n");
    sb.append("        specular = ").append(specularCoefficient).append(";\n");
    sb.append("        shininess = ").append(shininess).append(";\n");
    sb.append("        reflectivity = ").append(reflectivity).append(";\n");
    sb.append("        ior = ").append(ior).append(";\n");
    sb.append("        transparency = ").append(transparency).append(";\n");
    sb.append("    }");
    return sb.toString();
  }
}
