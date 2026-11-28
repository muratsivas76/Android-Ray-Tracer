package net.murat.material;

import net.murat.light.*;
import net.murat.math.*;
import net.murat.util.ColorUtil;

/** BaklavaMaterial - Golden layered pastry with syrup shine */
public class BaklavaMaterial implements Material {
  private final int pastryColorPacked;
  private final int syrupColorPacked;
  private final int nutColorPacked;
  private final double layers;
  private final double syrupiness;
  private Matrix4 objectInverseTransform;
  private Matrix4 inverseTransposeTransform;

  // ===== GC DOSTU: PRE-ALLOCATED TEMP OBJECTS =====
  private final Point3 tempLocalPoint = new Point3();
  private final Vector3 tempLocalNormal = new Vector3();
  private final Vector3 tempTransformedNormal = new Vector3();
  private final Vector3 tempLightDir = new Vector3();
  private final Vector3 tempViewDir = new Vector3();
  private final Vector3 tempHalfVec = new Vector3();
  private final Point3 tempViewerPoint = new Point3();
  private final LightProperties tempLightProps = new LightProperties();

  public BaklavaMaterial(
      int pastryColor,
      int syrupColor,
      int nutColor,
      double layers,
      double syrupiness,
      Matrix4 invTransform) {
    this.pastryColorPacked = pastryColor;
    this.syrupColorPacked = syrupColor;
    this.nutColorPacked = nutColor;
    this.layers = Math.max(1.0, Math.min(20.0, layers));
    this.syrupiness = Math.max(0.0, Math.min(1.0, syrupiness));
    this.objectInverseTransform = invTransform != null ? invTransform : new Matrix4();
    this.inverseTransposeTransform = this.objectInverseTransform.inverseTransposeForNormal();
  }

  // Classic baklava
  public BaklavaMaterial(Matrix4 invTransform) {
    this(0xFFFFDF00, 0xFFD2B48C, 0xFF8B4513, 12.0, 0.8, invTransform);
  }

  // Extra syrupy baklava
  public static BaklavaMaterial createSyrupyBaklava(Matrix4 invTransform) {
    return new BaklavaMaterial(0xFFFFD700, 0xFFCD853F, 0xFFA0522D, 15.0, 0.95, invTransform);
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

    // 2. Transform normal - ZERO ALLOCATION (FIXED!)
    inverseTransposeTransform.transformDirection(worldNormal, tempTransformedNormal);
    tempLocalNormal.set(tempTransformedNormal);
    tempLocalNormal.normalize();

    // 3. Layered pastry pattern using tempLocalPoint
    double layerPattern = Math.sin(tempLocalPoint.y * layers * 8.0) * 0.6;
    double nutPattern = Math.cos(tempLocalPoint.x * 15.0) * Math.sin(tempLocalPoint.z * 12.0);

    // 4. Determine material component
    int baseColor;
    if (nutPattern > 0.5) {
      baseColor = nutColorPacked;
    } else if (layerPattern > 0.3) {
      baseColor = pastryColorPacked;
    } else {
      baseColor = syrupColorPacked;
    }

    // 5. Syrup effect
    int syrupyColor = ColorUtil.blendColors(baseColor, syrupColorPacked, (float) syrupiness * 0.4f);

    // 6. Get light properties - ZERO ALLOCATION (FIXED!)
    LightProperties.getLightProperties(light, worldPoint, tempLightProps);
    tempLightDir.set(tempLightProps.direction);

    // 7. View direction - ZERO ALLOCATION
    tempViewerPoint.set(viewPos);
    tempViewDir.set(tempViewerPoint);
    tempViewDir.subtract(worldPoint);
    tempViewDir.normalize();

    // 8. Diffuse
    double NdotL = Math.max(0.0, tempLocalNormal.dot(tempLightDir));

    // 9. Half vector for specular - ZERO ALLOCATION
    tempHalfVec.set(tempLightDir);
    tempHalfVec.add(tempViewDir);
    tempHalfVec.normalize();
    double NdotH = Math.max(0.0, tempLocalNormal.dot(tempHalfVec));

    // 10. Specular
    double shininess = 32.0 * syrupiness;
    double specular = Math.pow(NdotH, shininess) * syrupiness;

    // 11. Diffuse with golden enhancement
    double diffuse = NdotL * (0.7 + 0.3 * syrupiness);

    // 12. Color mixing
    int ambient =
        ColorUtil.multiplyColors(
            syrupyColor, tempLightProps.color, 0.25 * tempLightProps.intensity);
    int diffuseColor =
        ColorUtil.multiplyColors(
            syrupyColor, tempLightProps.color, diffuse * tempLightProps.intensity);
    int specularColor =
        ColorUtil.multiplyColors(
            tempLightProps.color, ColorUtil.WHITE, specular * tempLightProps.intensity);

    int combined = ColorUtil.combineColors(ambient, diffuseColor, specularColor);

    // 13. Golden glow effect
    if (syrupiness > 0.6) {
      int goldenGlow = ColorUtil.multiply(pastryColorPacked, 0.2f);
      combined = ColorUtil.add(combined, goldenGlow);
    }

    return combined;
  }

  @Override
  public double getReflectivity() {
    return syrupiness * 0.3;
  }

  @Override
  public double getIndexOfRefraction() {
    return 1.4;
  }

  @Override
  public double getTransparency() {
    return syrupiness * 0.1;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("BaklavaMaterial baklavamaterial {\n");
    sb.append("        pastryColor = ")
        .append(ColorUtil.toColorString(pastryColorPacked))
        .append(";\n");
    sb.append("        syrupColor = ")
        .append(ColorUtil.toColorString(syrupColorPacked))
        .append(";\n");
    sb.append("        nutColor = ").append(ColorUtil.toColorString(nutColorPacked)).append(";\n");
    sb.append("        layers = ").append(layers).append(";\n");
    sb.append("        syrupiness = ").append(syrupiness).append(";\n");
    sb.append("    }");
    return sb.toString();
  }
}
