package net.murat.material;

import net.murat.light.*;
import net.murat.math.*;
import net.murat.util.ColorUtil;

public class BrunostCheeseMaterial implements Material {
  private final int cheeseColorPacked;
  private final int caramelColorPacked;
  private final double caramelAmount;
  private Matrix4 objectTransform;

  // Pre-computed inverse matrix to eliminate dynamic CPU overhead inside tight loops
  private Matrix4 inverseTransform;
  private Matrix4 inverseTransposeTransform;

  // Pre-allocated static immutable constants to prevent dynamic garbage array/object polling
  private static final int SPECULAR_TINT = 0xFFFFEC8B;
  private static final int CONST_WHITE = 0xFFFFFFFF;

  private final double ambientCoeff = 0.5;
  private final double diffuseCoeff = 0.9;
  private final double specularCoeff = 0.25;
  private final double shininess = 30.0;
  private final double reflectivity = 0.15;
  private final double ior = 1.55;
  private final double transparency = 0.0;

  // ===== GC DOSTU: PRE-ALLOCATED TEMP OBJECTS =====
  private final Point3 tempLocalPoint = new Point3();
  private final Vector3 tempLocalNormal = new Vector3();
  private final Vector3 tempTransformedNormal = new Vector3();
  private final Vector3 tempViewDir = new Vector3();
  private final Vector3 tempLightDir = new Vector3();
  private final Vector3 tempReflectDir = new Vector3();
  private final Point3 tempViewerPoint = new Point3();
  private final LightProperties tempLightProps = new LightProperties();

  public BrunostCheeseMaterial() {
    this(0xFFD2691E, 0xFF8B4513, 0.4);
  }

  public BrunostCheeseMaterial(int cheeseColor, int caramelColor, double caramelAmount) {
    this.cheeseColorPacked = cheeseColor;
    this.caramelColorPacked = caramelColor;
    this.caramelAmount = Math.max(0.0, Math.min(1.0, caramelAmount));
    this.objectTransform = Matrix4.identity();
    this.inverseTransform = Matrix4.identity();
    this.inverseTransposeTransform = Matrix4.identity();
  }

  @Override
  public void setObjectTransform(Matrix4 tm) {
    if (tm == null) tm = new Matrix4();
    this.objectTransform = tm;
    this.inverseTransform = tm.inverse();
    this.inverseTransposeTransform = this.inverseTransform.inverseTransposeForNormal();
  }

  @Override
  public int getColorAt(Point3 worldPoint, Vector3 worldNormal, Light light, Point3 viewerPos) {
    // 1. Transform to local space - ZERO ALLOCATION
    inverseTransform.transformPoint(worldPoint, tempLocalPoint);

    // 2. Transform normal - ZERO ALLOCATION (FIXED!)
    inverseTransposeTransform.transformDirection(worldNormal, tempTransformedNormal);
    tempLocalNormal.set(tempTransformedNormal);
    tempLocalNormal.normalize();

    // 3. Calculate cheese texture using tempLocalPoint
    int surfaceColor = calculateCheeseTexture(tempLocalPoint);

    // 4. Get light properties - ZERO ALLOCATION (FIXED!)
    LightProperties.getLightProperties(light, worldPoint, tempLightProps);

    // 5. Ambient
    int ambient = ColorUtil.multiplyColors(surfaceColor, tempLightProps.color, ambientCoeff);

    if (light instanceof MuratAmbientLight) {
      return ambient;
    }

    // 6. Light direction - ZERO ALLOCATION
    tempLightDir.set(tempLightProps.direction);

    // 7. Diffuse
    double NdotL = Math.max(0.0, tempLocalNormal.dot(tempLightDir));
    int diffuse =
        ColorUtil.multiplyColors(
            surfaceColor, tempLightProps.color, diffuseCoeff * NdotL * tempLightProps.intensity);

    // 8. View direction - ZERO ALLOCATION
    tempViewerPoint.set(viewerPos);
    tempViewDir.set(tempViewerPoint);
    tempViewDir.subtract(worldPoint);
    tempViewDir.normalize();

    // 9. Reflection direction - ZERO ALLOCATION
    tempReflectDir.set(tempLightDir);
    tempReflectDir.negate();
    tempReflectDir.reflect(tempLocalNormal, tempReflectDir);

    // 10. Specular
    double RdotV = Math.max(0.0, tempReflectDir.dot(tempViewDir));
    double specFactor = Math.pow(RdotV, shininess) * tempLightProps.intensity;

    int specular =
        ColorUtil.multiplyColors(SPECULAR_TINT, tempLightProps.color, specularCoeff * specFactor);

    return ColorUtil.combineColors(ambient, diffuse, specular);
  }

  private int calculateCheeseTexture(Point3 point) {
    double x = point.x * 8.0;
    double y = point.y * 8.0;
    double z = point.z * 8.0;

    double cheeseBase = Math.sin(x * 1.2 + Math.cos(y * 0.8)) * 0.5 + 0.5;
    double caramelVein1 = Math.abs(Math.sin(x * 3.0 + y * 2.0 + z * 1.5));
    double caramelVein2 = Math.abs(Math.cos(x * 2.5 + y * 1.8 + z * 2.0));
    double textureNoise = Math.sin(x * 5.0 + y * 4.0 + z * 3.0) * 0.2 + 0.8;

    double veinPattern = (caramelVein1 * 0.6 + caramelVein2 * 0.4);

    if (veinPattern < caramelAmount) {
      double intensity = veinPattern / caramelAmount;
      int veinColor =
          ColorUtil.blendColors(
              caramelColorPacked, ColorUtil.darkenColor(caramelColorPacked, 0.3), intensity);
      return ColorUtil.addColorVariation(veinColor, intensity);
    } else {
      double intensity = (veinPattern - caramelAmount) / (1.0 - caramelAmount);
      int texturedCheese = ColorUtil.multiplyColors(cheeseColorPacked, CONST_WHITE, textureNoise);
      return ColorUtil.blendColors(
          texturedCheese, ColorUtil.lightenColor(cheeseColorPacked, 0.1), intensity);
    }
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
    sb.append("BrunostCheeseMaterial brunostcheesematerial {\n");
    sb.append("        cheeseColor = ")
        .append(ColorUtil.toColorString(cheeseColorPacked))
        .append(";\n");
    sb.append("        caramelColor = ")
        .append(ColorUtil.toColorString(caramelColorPacked))
        .append(";\n");
    sb.append("        caramelAmount = ").append(caramelAmount).append(";\n");
    sb.append("    }");
    return sb.toString();
  }
}
