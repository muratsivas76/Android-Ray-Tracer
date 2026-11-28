package net.murat.material;

import net.murat.light.*;
import net.murat.math.*;
import net.murat.util.ColorUtil;

/**
 * DamaskCeramicMaterial - Ceramic with damask pattern (floral/motif). Zero-allocation using packed
 * ARGB integers.
 */
public class DamaskCeramicMaterial implements Material {
  private final int primaryColorPacked;
  private final int secondaryColorPacked;
  private final double shininess;
  private final double ambientCoeff;
  private final double specularCoeff;
  private final double reflectivity = 0.1;

  private Matrix4 objectInverseTransform;
  private Matrix4 inverseTransposeTransform;

  // Pre-cached color components
  private final int primaryR, primaryG, primaryB;
  private final int secondaryR, secondaryG, secondaryB;

  // ===== GC DOSTU: PRE-ALLOCATED TEMP OBJECTS =====
  private final Point3 tempLocalPoint = new Point3();
  private final Vector3 tempLocalNormal = new Vector3();
  private final Vector3 tempTransformedNormal = new Vector3();
  private final Vector3 tempViewDir = new Vector3();
  private final Vector3 tempLightDir = new Vector3();
  private final Vector3 tempReflectDir = new Vector3();
  private final Point3 tempViewerPoint = new Point3();
  private final LightProperties tempLightProps = new LightProperties();

  public DamaskCeramicMaterial(
      int primaryColor, int secondaryColor, double shininess, Matrix4 invTransform) {
    this(primaryColor, secondaryColor, shininess, 0.1, 0.8, invTransform);
  }

  public DamaskCeramicMaterial(
      int primaryColor,
      int secondaryColor,
      double shininess,
      double ambient,
      double specular,
      Matrix4 invTransform) {
    this.primaryColorPacked = primaryColor;
    this.secondaryColorPacked = secondaryColor;
    this.shininess = Math.max(1.0, shininess);
    this.ambientCoeff = ambient;
    this.specularCoeff = specular;
    this.objectInverseTransform = invTransform != null ? invTransform : new Matrix4();
    this.inverseTransposeTransform = this.objectInverseTransform.inverseTransposeForNormal();

    this.primaryR = (primaryColor >> 16) & 0xFF;
    this.primaryG = (primaryColor >> 8) & 0xFF;
    this.primaryB = primaryColor & 0xFF;

    this.secondaryR = (secondaryColor >> 16) & 0xFF;
    this.secondaryG = (secondaryColor >> 8) & 0xFF;
    this.secondaryB = secondaryColor & 0xFF;
  }

  @Override
  public void setObjectTransform(Matrix4 tm) {
    if (tm == null) tm = new Matrix4();
    this.objectInverseTransform = tm;
    this.inverseTransposeTransform = tm.inverseTransposeForNormal();
  }

  @Override
  public int getColorAt(Point3 worldPoint, Vector3 worldNormal, Light light, Point3 viewPos) {
    // 1. Transform to local space - ZERO ALLOCATION
    objectInverseTransform.transformPoint(worldPoint, tempLocalPoint);

    // 2. Transform normal - ZERO ALLOCATION
    inverseTransposeTransform.transformDirection(worldNormal, tempTransformedNormal);
    tempLocalNormal.set(tempTransformedNormal);
    tempLocalNormal.normalize();

    // 3. Damask pattern
    double pattern =
        Math.sin(tempLocalPoint.x * 5.0)
            * Math.cos(tempLocalPoint.y * 7.0)
            * Math.sin(tempLocalPoint.z * 3.0);

    int baseR, baseG, baseB;
    if (pattern > 0.0) {
      baseR = primaryR;
      baseG = primaryG;
      baseB = primaryB;
    } else {
      baseR = secondaryR;
      baseG = secondaryG;
      baseB = secondaryB;
    }

    // 4. Light properties - ZERO ALLOCATION
    LightProperties.getLightProperties(light, worldPoint, tempLightProps);
    tempLightDir.set(tempLightProps.direction);

    // 5. Ambient check
    if (light instanceof MuratAmbientLight) {
      int r = (int) (baseR * ambientCoeff * tempLightProps.intensity);
      int g = (int) (baseG * ambientCoeff * tempLightProps.intensity);
      int b = (int) (baseB * ambientCoeff * tempLightProps.intensity);
      r = r > 255 ? 255 : (r < 0 ? 0 : r);
      g = g > 255 ? 255 : (g < 0 ? 0 : g);
      b = b > 255 ? 255 : (b < 0 ? 0 : b);
      return (0xFF000000 | (r << 16) | (g << 8) | b);
    }

    // 6. View direction - ZERO ALLOCATION
    tempViewerPoint.set(viewPos);
    tempViewDir.set(tempViewerPoint);
    tempViewDir.subtract(worldPoint);
    tempViewDir.normalize();

    // 7. Reflection direction - ZERO ALLOCATION
    tempReflectDir.set(tempLightDir);
    tempReflectDir.negate();
    tempReflectDir.reflect(tempLocalNormal, tempReflectDir);

    // 8. Lighting components
    double NdotL = Math.max(0.0, tempLocalNormal.dot(tempLightDir));
    double RdotV = Math.max(0.0, tempReflectDir.dot(tempViewDir));
    double intensity = tempLightProps.intensity;
    int lightColor = tempLightProps.color;

    int lightR = (lightColor >> 16) & 0xFF;
    int lightG = (lightColor >> 8) & 0xFF;
    int lightB = lightColor & 0xFF;

    // Ambient
    double ambFactor = ambientCoeff * intensity;
    int ambR = (int) (baseR * ambFactor * lightR / 255.0);
    int ambG = (int) (baseG * ambFactor * lightG / 255.0);
    int ambB = (int) (baseB * ambFactor * lightB / 255.0);

    // Diffuse
    double diffFactor = NdotL * intensity;
    int diffR = (int) (baseR * diffFactor * lightR / 255.0);
    int diffG = (int) (baseG * diffFactor * lightG / 255.0);
    int diffB = (int) (baseB * diffFactor * lightB / 255.0);

    // Specular
    double specFactor = Math.pow(RdotV, shininess) * specularCoeff * intensity;
    int specR = (int) (255 * specFactor * lightR / 255.0);
    int specG = (int) (255 * specFactor * lightG / 255.0);
    int specB = (int) (255 * specFactor * lightB / 255.0);

    // 9. Combine
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
    return 1.4;
  }

  @Override
  public double getTransparency() {
    return 0.0;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("DamaskCeramicMaterial damaskceramicmaterial {\n");
    sb.append("        primary = ")
        .append(ColorUtil.toColorString(primaryColorPacked))
        .append(";\n");
    sb.append("        secondary = ")
        .append(ColorUtil.toColorString(secondaryColorPacked))
        .append(";\n");
    sb.append("        shininess = ").append(shininess).append(";\n");
    sb.append("        ambient = ").append(ambientCoeff).append(";\n");
    sb.append("        specular = ").append(specularCoeff).append(";\n");
    sb.append("    }");
    return sb.toString();
  }
}
