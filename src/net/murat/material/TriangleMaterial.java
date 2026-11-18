package net.murat.material;

import net.murat.light.*;
import net.murat.math.*;
import net.murat.util.ColorUtil;

/**
 * TriangleMaterial - Repeating triangular pattern with Phong lighting. Zero-allocation using packed
 * ARGB integers.
 */
public class TriangleMaterial implements Material {
  private final int color1Packed;
  private final int color2Packed;
  private final double triangleSize;
  private Matrix4 objectInverseTransform;
  private Matrix4 inverseTransposeTransform;

  // Pre-cached color components
  private final int color1R, color1G, color1B;
  private final int color2R, color2G, color2B;

  private final double ambientCoefficient;
  private final double diffuseCoefficient;
  private final double specularCoefficient;
  private final double shininess;
  private final double reflectivity;
  private final double ior;
  private final double transparency;
  private final int specularColorPacked = 0xFFFFFFFF;

  // ===== GC DOSTU: PRE-ALLOCATED TEMP OBJECTS =====
  private final Point3 tempLocalPoint = new Point3();
  private final Vector3 tempLocalNormal = new Vector3();
  private final Vector3 tempTransformedNormal = new Vector3();
  private final Vector3 tempViewDir = new Vector3();
  private final Vector3 tempLightDir = new Vector3();
  private final Vector3 tempReflectDir = new Vector3();
  private final Point3 tempViewerPoint = new Point3();
  private final LightProperties tempLightProps = new LightProperties();

  public TriangleMaterial(
      int color1,
      int color2,
      double triangleSize,
      double ambientCoefficient,
      double diffuseCoefficient,
      double specularCoefficient,
      double shininess,
      double reflectivity,
      double ior,
      double transparency,
      Matrix4 objectInverseTransform) {
    this.color1Packed = color1;
    this.color2Packed = color2;
    this.triangleSize = triangleSize;
    this.objectInverseTransform =
        objectInverseTransform != null ? objectInverseTransform : new Matrix4();
    this.inverseTransposeTransform = this.objectInverseTransform.inverseTransposeForNormal();

    this.ambientCoefficient = ambientCoefficient;
    this.diffuseCoefficient = diffuseCoefficient;
    this.specularCoefficient = specularCoefficient;
    this.shininess = shininess;
    this.reflectivity = reflectivity;
    this.ior = ior;
    this.transparency = transparency;

    this.color1R = (color1 >> 16) & 0xFF;
    this.color1G = (color1 >> 8) & 0xFF;
    this.color1B = color1 & 0xFF;

    this.color2R = (color2 >> 16) & 0xFF;
    this.color2G = (color2 >> 8) & 0xFF;
    this.color2B = color2 & 0xFF;
  }

  public TriangleMaterial(
      int color1, int color2, double triangleSize, Matrix4 objectInverseTransform) {
    this(color1, color2, triangleSize, 0.1, 0.8, 0.1, 10.0, 0.0, 1.0, 0.0, objectInverseTransform);
  }

  @Override
  public void setObjectTransform(Matrix4 tm) {
    if (tm == null) tm = new Matrix4();
    this.objectInverseTransform = tm;
    this.inverseTransposeTransform = tm.inverseTransposeForNormal();
  }

  private int getPatternColor(double u, double v) {
    u = u / triangleSize;
    v = v / triangleSize;

    double u_normalized = (u % 1.0 + 1.0) % 1.0;
    double v_normalized = (v % 1.0 + 1.0) % 1.0;

    int checkX = (int) Math.floor(u);
    int checkY = (int) Math.floor(v);

    int r, g, b;
    if ((checkX + checkY) % 2 == 0) {
      if (u_normalized + v_normalized < 1.0) {
        r = color1R;
        g = color1G;
        b = color1B;
      } else {
        r = color2R;
        g = color2G;
        b = color2B;
      }
    } else {
      if (u_normalized + v_normalized < 1.0) {
        r = color2R;
        g = color2G;
        b = color2B;
      } else {
        r = color1R;
        g = color1G;
        b = color1B;
      }
    }
    return (0xFF000000 | (r << 16) | (g << 8) | b);
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
    double absNx = Math.abs(tempLocalNormal.x);
    double absNy = Math.abs(tempLocalNormal.y);
    double absNz = Math.abs(tempLocalNormal.z);

    double u, v;
    double eps = 1e-10;
    if (absNx > absNy && absNx > absNz) {
      u = tempLocalPoint.y + eps;
      v = tempLocalPoint.z + eps;
    } else if (absNy > absNx && absNy > absNz) {
      u = tempLocalPoint.x + eps;
      v = tempLocalPoint.z + eps;
    } else {
      u = tempLocalPoint.x + eps;
      v = tempLocalPoint.y + eps;
    }

    // 4. Pattern color
    int patternColor = getPatternColor(u, v);
    int patR = (patternColor >> 16) & 0xFF;
    int patG = (patternColor >> 8) & 0xFF;
    int patB = patternColor & 0xFF;

    // 5. Light properties - ZERO ALLOCATION
    LightProperties.getLightProperties(light, worldPoint, tempLightProps);
    tempLightDir.set(tempLightProps.direction);

    int lightColor = tempLightProps.color;
    int lightR = (lightColor >> 16) & 0xFF;
    int lightG = (lightColor >> 8) & 0xFF;
    int lightB = lightColor & 0xFF;
    double intensity = tempLightProps.intensity;

    // 6. Ambient check
    int ambR = (int) (patR * ambientCoefficient * lightR / 255.0 * intensity);
    int ambG = (int) (patG * ambientCoefficient * lightG / 255.0 * intensity);
    int ambB = (int) (patB * ambientCoefficient * lightB / 255.0 * intensity);

    if (light instanceof MuratAmbientLight) {
      ambR = clamp(ambR);
      ambG = clamp(ambG);
      ambB = clamp(ambB);
      return (0xFF000000 | (ambR << 16) | (ambG << 8) | ambB);
    }

    // 7. View direction - ZERO ALLOCATION
    tempViewerPoint.set(viewerPos);
    tempViewDir.set(tempViewerPoint);
    tempViewDir.subtract(worldPoint);
    tempViewDir.normalize();

    // 8. Diffuse
    double NdotL = Math.max(0.0, tempLocalNormal.dot(tempLightDir));
    double diffFactor = diffuseCoefficient * NdotL * intensity;
    int diffR = (int) (patR * diffFactor * lightR / 255.0);
    int diffG = (int) (patG * diffFactor * lightG / 255.0);
    int diffB = (int) (patB * diffFactor * lightB / 255.0);

    // 9. Reflection direction - ZERO ALLOCATION
    tempReflectDir.set(tempLightDir);
    tempReflectDir.negate();
    tempReflectDir.reflect(tempLocalNormal, tempReflectDir);

    // 10. Specular
    double RdotV = Math.max(0.0, tempReflectDir.dot(tempViewDir));
    double specFactor = Math.pow(RdotV, shininess) * specularCoefficient * intensity;

    int specR = (int) (((specularColorPacked >> 16) & 0xFF) * specFactor * lightR / 255.0);
    int specG = (int) (((specularColorPacked >> 8) & 0xFF) * specFactor * lightG / 255.0);
    int specB = (int) ((specularColorPacked & 0xFF) * specFactor * lightB / 255.0);

    specR = clamp(specR);
    specG = clamp(specG);
    specB = clamp(specB);

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

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("TriangleMaterial trianglematerial {\n");
    sb.append("        color1 = ").append(ColorUtil.toColorString(color1Packed)).append(";\n");
    sb.append("        color2 = ").append(ColorUtil.toColorString(color2Packed)).append(";\n");
    sb.append("        triangleSize = ").append(triangleSize).append(";\n");
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
