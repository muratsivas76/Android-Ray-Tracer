package net.murat.material;

import net.murat.light.*;
import net.murat.math.*;
import net.murat.util.ColorUtil;

public class CarpetTextureMaterial implements Material {
  private final int baseColorPacked;
  private final int patternColorPacked;
  private Matrix4 objectTransform;
  private Matrix4 inverseTransform;

  // Phong parameters optimized for carpet/wool surface
  private final double ambientCoeff = 0.5;
  private final double diffuseCoeff = 0.7;
  private final double specularCoeff = 0.1;
  private final double shininess = 10.0;
  private final int specularColorPacked = 0xFFC8C8C8;
  private final double reflectivity = 0.05;
  private final double ior = 1.2;
  private final double transparency = 0.0;

  // ===== GC DOSTU: PRE-ALLOCATED TEMP OBJECTS =====
  private final Point3 tempLocalPoint = new Point3();
  private final Vector3 tempPiledNormal = new Vector3();
  private final Vector3 tempViewDir = new Vector3();
  private final Vector3 tempLightDir = new Vector3();
  private final Vector3 tempReflectDir = new Vector3();
  private final Point3 tempViewerPoint = new Point3();
  private final Vector3 tempPileEffect = new Vector3();
  private final Vector3 tempResult = new Vector3();
  private final LightProperties tempLightProps = new LightProperties();

  public CarpetTextureMaterial() {
    this(0xFFAA0000, 0xFF282828);
  }

  public CarpetTextureMaterial(int baseColor, int patternColor) {
    this.baseColorPacked = baseColor;
    this.patternColorPacked = patternColor;
    this.objectTransform = Matrix4.identity();
    this.inverseTransform = Matrix4.identity();
  }

  @Override
  public void setObjectTransform(Matrix4 tm) {
    if (tm == null) tm = new Matrix4();
    this.objectTransform = tm;
    this.inverseTransform = tm.inverse();
  }

  @Override
  public int getColorAt(Point3 worldPoint, Vector3 worldNormal, Light light, Point3 viewerPos) {
    // 1. Get carpet pattern color - ZERO ALLOCATION (FIXED!)
    int surfaceColor = calculateCarpetColor(worldPoint);

    // 2. Get light properties - ZERO ALLOCATION (FIXED!)
    LightProperties.getLightProperties(light, worldPoint, tempLightProps);

    // 3. Simulate carpet pile effect - ZERO ALLOCATION (FIXED!)
    simulatePileEffect(worldPoint, worldNormal, tempPiledNormal);

    // 4. Ambient
    int ambient = ColorUtil.multiplyColors(surfaceColor, tempLightProps.color, ambientCoeff);

    if (light instanceof MuratAmbientLight) {
      return ambient;
    }

    // 5. Light direction
    tempLightDir.set(tempLightProps.direction);

    // 6. Diffuse
    double NdotL = Math.max(0, tempPiledNormal.dot(tempLightDir));
    int diffuse =
        ColorUtil.multiplyColors(
            surfaceColor, tempLightProps.color, diffuseCoeff * NdotL * tempLightProps.intensity);

    // 7. View direction - ZERO ALLOCATION (FIXED!)
    tempViewerPoint.set(viewerPos);
    tempViewDir.set(tempViewerPoint);
    tempViewDir.subtract(worldPoint);
    tempViewDir.normalize();

    // 8. Reflection direction - ZERO ALLOCATION (FIXED!)
    tempReflectDir.set(tempLightDir);
    tempReflectDir.negate();
    tempReflectDir.reflect(tempPiledNormal, tempReflectDir);

    // 9. Specular
    double RdotV = Math.max(0, tempReflectDir.dot(tempViewDir));
    double specFactor = Math.pow(RdotV, shininess) * tempLightProps.intensity;
    int specular =
        ColorUtil.multiplyColors(
            specularColorPacked, tempLightProps.color, specularCoeff * specFactor);

    return ColorUtil.combineColors(ambient, diffuse, specular);
  }

  private int calculateCarpetColor(Point3 worldPoint) {
    // Transform to object space - ZERO ALLOCATION (FIXED!)
    inverseTransform.transformPoint(worldPoint, tempLocalPoint);

    double u = tempLocalPoint.x * 6.0;
    double v = tempLocalPoint.z * 6.0;

    return createKilimPattern(u, v);
  }

  private int createKilimPattern(double u, double v) {
    double tileU = (u % 1 + 1) % 1;
    double tileV = (v % 1 + 1) % 1;

    if (tileU < 0.08 || tileU > 0.92 || tileV < 0.08 || tileV > 0.92) {
      return patternColorPacked;
    }

    if (Math.abs(tileU - 0.5) + Math.abs(tileV - 0.5) < 0.25) {
      return patternColorPacked;
    }

    if ((Math.abs(tileU - 0.25) < 0.06 && Math.abs(tileV - 0.25) < 0.06)
        || (Math.abs(tileU - 0.75) < 0.06 && Math.abs(tileV - 0.75) < 0.06)
        || (Math.abs(tileU - 0.25) < 0.06 && Math.abs(tileV - 0.75) < 0.06)
        || (Math.abs(tileU - 0.75) < 0.06 && Math.abs(tileV - 0.25) < 0.06)) {
      return patternColorPacked;
    }

    if (Math.abs(tileU - 0.5) < 0.02 || Math.abs(tileV - 0.5) < 0.02) {
      return patternColorPacked;
    }

    return baseColorPacked;
  }

  /** ZERO ALLOCATION: Simulate carpet pile effect */
  private void simulatePileEffect(Point3 point, Vector3 originalNormal, Vector3 target) {
    double pileU = point.x * 8.0;
    double pileV = point.z * 8.0;

    double pileX = Math.sin(pileU * 2.0) * 0.1;
    double pileY = 1.0;
    double pileZ = Math.cos(pileV * 2.0) * 0.1;

    // Build pile effect vector - ZERO ALLOCATION (FIXED!)
    tempPileEffect.set(pileX, pileY, pileZ);
    tempPileEffect.normalize();

    // Add to original normal - ZERO ALLOCATION (FIXED!)
    target.set(originalNormal);
    target.add(tempPileEffect);
    target.normalize();
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
    sb.append("CarpetTextureMaterial carpettexturematerial {\n");
    sb.append("        baseColor = ")
        .append(ColorUtil.toColorString(baseColorPacked))
        .append(";\n");
    sb.append("        patternColor = ")
        .append(ColorUtil.toColorString(patternColorPacked))
        .append(";\n");
    sb.append("    }");
    return sb.toString();
  }
}
