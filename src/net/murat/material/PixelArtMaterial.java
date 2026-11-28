package net.murat.material;

import net.murat.light.*;
import net.murat.math.*;
import net.murat.util.ColorUtil;

/** Pixel art material with correct lighting. Zero-allocation using packed ARGB integers. */
public class PixelArtMaterial implements Material {
  private final int[] palettePacked;
  private final double pixelSize;
  private final double invPixelSize;
  private Matrix4 objectInverseTransform;
  private Matrix4 inverseTransposeTransform;

  private final double ambientCoefficient;
  private final double diffuseCoefficient;
  private final double specularCoefficient;
  private final double shininess;
  private final double reflectivity;
  private final double transparency;
  private final double ior;

  // ===== GC DOSTU: PRE-ALLOCATED TEMP OBJECTS =====
  private final Point3 tempLocalPoint = new Point3();
  private final Vector3 tempLocalNormal = new Vector3();
  private final Vector3 tempTransformedNormal = new Vector3();
  private final Vector3 tempViewDir = new Vector3();
  private final Vector3 tempLightDir = new Vector3();
  private final Vector3 tempReflectDir = new Vector3();
  private final Point3 tempViewerPoint = new Point3();
  private final LightProperties tempLightProps = new LightProperties();

  public PixelArtMaterial(
      int[] palette,
      double pixelSize,
      double ambient,
      double diffuse,
      double specular,
      double shininess,
      double reflectivity,
      double ior,
      double transparency,
      Matrix4 objectInverseTransform) {
    this.palettePacked =
        palette != null && palette.length >= 2
            ? palette
            : new int[] {0xFFFF0000, 0xFF00FF00, 0xFF0000FF, 0xFFFFFF00};
    this.pixelSize = Math.max(0.01, pixelSize);
    this.invPixelSize = 1.0 / this.pixelSize;
    this.ambientCoefficient = clamp(ambient);
    this.diffuseCoefficient = clamp(diffuse);
    this.specularCoefficient = clamp(specular);
    this.shininess = Math.max(1, shininess);
    this.reflectivity = clamp(reflectivity);
    this.ior = ior;
    this.transparency = clamp(transparency);
    this.objectInverseTransform =
        objectInverseTransform != null ? objectInverseTransform : new Matrix4();
    this.inverseTransposeTransform = this.objectInverseTransform.inverseTransposeForNormal();
  }

  public PixelArtMaterial(int[] palette, double pixelSize, Matrix4 objectInverseTransform) {
    this(palette, pixelSize, 0.1, 0.7, 0.2, 10.0, 0.0, 1.0, 0.0, objectInverseTransform);
  }

  @Override
  public void setObjectTransform(Matrix4 tm) {
    if (tm == null) tm = new Matrix4();
    this.objectInverseTransform = tm;
    this.inverseTransposeTransform = tm.inverseTransposeForNormal();
  }

  private double clamp(double value) {
    return value > 1.0 ? 1.0 : (value < 0.0 ? 0.0 : value);
  }

  private int getPixelColor(double u, double v) {
    u = u - Math.floor(u);
    v = v - Math.floor(v);
    if (u < 0) u += 1.0;
    if (v < 0) v += 1.0;

    int x = (int) (u * invPixelSize);
    int y = (int) (v * invPixelSize);
    int hash = (x * 7919 + y * 7901) % palettePacked.length;
    int idx = hash >= 0 ? hash : -hash;
    return palettePacked[idx % palettePacked.length];
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

    // 3. UV mapping
    double absX = Math.abs(tempLocalNormal.x);
    double absY = Math.abs(tempLocalNormal.y);
    double absZ = Math.abs(tempLocalNormal.z);

    double u, v;
    if (absX > absY && absX > absZ) {
      u = tempLocalPoint.y;
      v = tempLocalPoint.z;
    } else if (absY > absX && absY > absZ) {
      u = tempLocalPoint.x;
      v = tempLocalPoint.z;
    } else {
      u = tempLocalPoint.x;
      v = tempLocalPoint.y;
    }

    // 4. Base pixel color
    int baseColor = getPixelColor(u, v);
    int baseR = (baseColor >> 16) & 0xFF;
    int baseG = (baseColor >> 8) & 0xFF;
    int baseB = baseColor & 0xFF;

    // 5. Light properties - ZERO ALLOCATION
    LightProperties.getLightProperties(light, worldPoint, tempLightProps);
    tempLightDir.set(tempLightProps.direction);

    int lightColor = tempLightProps.color;
    int lightR = (lightColor >> 16) & 0xFF;
    int lightG = (lightColor >> 8) & 0xFF;
    int lightB = lightColor & 0xFF;
    double intensity = tempLightProps.intensity;

    // 6. Ambient
    int ambR = (int) (baseR * ambientCoefficient * lightR / 255.0 * intensity);
    int ambG = (int) (baseG * ambientCoefficient * lightG / 255.0 * intensity);
    int ambB = (int) (baseB * ambientCoefficient * lightB / 255.0 * intensity);

    if (light instanceof MuratAmbientLight) {
      ambR = clamp(ambR);
      ambG = clamp(ambG);
      ambB = clamp(ambB);
      return (0xFF000000 | (ambR << 16) | (ambG << 8) | ambB);
    }

    // 7. Diffuse
    double NdotL = Math.max(0.0, tempLocalNormal.dot(tempLightDir));
    double diffFactor = diffuseCoefficient * NdotL * intensity;
    int diffR = (int) (baseR * diffFactor * lightR / 255.0);
    int diffG = (int) (baseG * diffFactor * lightG / 255.0);
    int diffB = (int) (baseB * diffFactor * lightB / 255.0);

    // 8. View direction
    tempViewerPoint.set(viewerPos);
    tempViewDir.set(tempViewerPoint);
    tempViewDir.subtract(worldPoint);
    tempViewDir.normalize();

    // 9. Reflection direction
    tempReflectDir.set(tempLightDir);
    tempReflectDir.negate();
    tempReflectDir.reflect(tempLocalNormal, tempReflectDir);

    // 10. Specular
    double RdotV = Math.max(0.0, tempReflectDir.dot(tempViewDir));
    double specFactor = Math.pow(RdotV, shininess) * specularCoefficient * intensity;
    int specR = (int) (255 * specFactor * lightR / 255.0);
    int specG = (int) (255 * specFactor * lightG / 255.0);
    int specB = (int) (255 * specFactor * lightB / 255.0);

    // 11. Combine
    int r = ambR + diffR + specR;
    int g = ambG + diffG + specG;
    int b = ambB + diffB + specB;

    r = clamp(r);
    g = clamp(g);
    b = clamp(b);

    return (0xFF000000 | (r << 16) | (g << 8) | b);
  }

  private int clamp(int value) {
    return Math.max(0, Math.min(255, value));
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

  private String toColorStringArray(int[] palette) {
    StringBuilder sb = new StringBuilder("palette = [");
    final int size = palette.length;
    for (int i = 0; i < size; i++) {
      sb.append(ColorUtil.toColorString(palette[i]));
      if (i != (size - 1)) sb.append(", ");
    }
    sb.append("];");
    return sb.toString();
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("PixelArtMaterial pixelartmaterial {\n");
    sb.append("        ").append(toColorStringArray(palettePacked)).append("\n");
    sb.append("        pixelSize = ").append(pixelSize).append(";\n");
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
