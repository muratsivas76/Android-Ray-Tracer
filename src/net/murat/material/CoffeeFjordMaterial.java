package net.murat.material;

import net.murat.light.*;
import net.murat.math.*;
import net.murat.util.ColorUtil;

public class CoffeeFjordMaterial implements Material {
  private final int coffeeColorPacked;
  private final int fjordColorPacked;
  private final double blendIntensity;
  private Matrix4 objectTransform;

  // Pre-computed inverse matrix to save billions of dynamic CPU iterations inside the tight loop
  private Matrix4 inverseTransform;
  private Matrix4 inverseTransposeTransform;

  // Pre-allocated static global constant to prevent allocation overhead in deep execution cycles
  private static final int SPECULAR_TINT = 0xFFFFF5E1;

  private final double ambientCoeff = 0.45;
  private final double diffuseCoeff = 0.8;
  private final double specularCoeff = 0.25;
  private final double shininess = 35.0;
  private final double reflectivity = 0.15;
  private final double ior = 2.1;
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

  public CoffeeFjordMaterial() {
    this(0xFF4A2C1D, 0xFF1E90FF, 0.4);
  }

  public CoffeeFjordMaterial(int coffeeColor, int fjordColor, double blendIntensity) {
    this.coffeeColorPacked = coffeeColor;
    this.fjordColorPacked = fjordColor;
    this.blendIntensity = Math.max(0.0, Math.min(1.0, blendIntensity));

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

    // 3. Calculate marble effect using tempLocalPoint
    int surfaceColor = calculateMarbleEffect(tempLocalPoint);

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

  private int calculateMarbleEffect(Point3 point) {
    double x = point.x * 6.0;
    double y = point.y * 6.0;
    double z = point.z * 6.0;

    double turbulence =
        Math.sin(x * 2.0 + Math.sin(y * 3.0) + Math.sin(z * 1.5 + Math.cos(x * 2.5)));
    double pattern = (turbulence + 2.0) * 0.25;

    if (pattern < blendIntensity) {
      double ratio = pattern / blendIntensity;
      return ColorUtil.blendColors(coffeeColorPacked, fjordColorPacked, ratio);
    } else {
      double ratio = (pattern - blendIntensity) / (1.0 - blendIntensity);

      int coffeeR = (coffeeColorPacked >> 16) & 0xFF;
      int coffeeG = (coffeeColorPacked >> 8) & 0xFF;
      int coffeeB = coffeeColorPacked & 0xFF;

      int r = coffeeR - 30;
      int g = coffeeG - 20;
      int b = coffeeB - 10;

      int darkCoffee = ColorUtil.createColor(r < 0 ? 0 : r, g < 0 ? 0 : g, b < 0 ? 0 : b);

      return ColorUtil.blendColors(fjordColorPacked, darkCoffee, ratio);
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
    sb.append("CoffeeFjordMaterial coffeefjordmaterial {\n");
    sb.append("        coffeeColor = ")
        .append(ColorUtil.toColorString(coffeeColorPacked))
        .append(";\n");
    sb.append("        fjordColor = ")
        .append(ColorUtil.toColorString(fjordColorPacked))
        .append(";\n");
    sb.append("        blendIntensity = ").append(blendIntensity).append(";\n");
    sb.append("    }");
    return sb.toString();
  }
}
