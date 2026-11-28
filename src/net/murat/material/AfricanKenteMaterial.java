package net.murat.material;

import net.murat.light.Light;
import net.murat.light.LightProperties;
import net.murat.math.*;
import net.murat.util.ColorUtil;

/**
 * African Kente fabric material with striped patterns. Matte fabric appearance with low specular
 * highlights.
 */
public class AfricanKenteMaterial implements Material {
  private final int[] stripeColors;
  private final double scale;
  private final int phase;
  private Matrix4 objectInverseTransform;
  private Matrix4 inverseTransposeTransform;

  private final double ambientCoefficient = 0.5;
  private final double diffuseCoefficient = 0.8;
  private final double specularCoefficient = 0.05;
  private final double shininess = 5.0;
  private final double reflectivity = 0.1;
  private final double ior = 1.0;
  private final double transparency = 0.0;

  // Pre-allocated temp objects for zero-allocation rendering
  private final Point3 tempLocalPoint = new Point3();
  private final Vector3 tempViewDir = new Vector3();
  private final Vector3 tempHalfway = new Vector3();
  private final Vector3 tempLightDir = new Vector3();
  private final LightProperties tempLightProps = new LightProperties();

  public AfricanKenteMaterial() {
    this(new int[] {0xFFFF1A1A, 0xFFF2E633, 0xFF1A9933, 0xFF000000}, 8.0, 0, new Matrix4());
  }

  public AfricanKenteMaterial(
      int[] stripeColors, double scale, int phase, Matrix4 objectInverseTransform) {
    this.stripeColors = stripeColors.clone();
    this.scale = Math.max(0.1, scale);
    this.phase = phase;
    this.objectInverseTransform =
        objectInverseTransform != null ? objectInverseTransform : new Matrix4();
    this.inverseTransposeTransform = this.objectInverseTransform.inverseTransposeForNormal();
  }

  @Override
  public void setObjectTransform(Matrix4 tm) {
    if (tm == null) {
      tm = new Matrix4();
    }
    this.objectInverseTransform = tm;
    this.inverseTransposeTransform = tm.inverseTransposeForNormal();
  }

  @Override
  public int getColorAt(Point3 worldPoint, Vector3 worldNormal, Light light, Point3 viewerPos) {
    // Transform to local space
    objectInverseTransform.transformPoint(worldPoint, tempLocalPoint);

    // Stripe pattern
    double x = tempLocalPoint.x * scale;
    int stripeIndex = ((int) Math.floor(x) + phase) % stripeColors.length;
    if (stripeIndex < 0) {
      stripeIndex += stripeColors.length;
    }

    int patternColor = stripeColors[stripeIndex];

    // Light properties
    LightProperties.getLightProperties(light, worldPoint, tempLightProps);
    tempLightDir.set(tempLightProps.direction);

    // Diffuse
    double NdotL = Math.max(0, worldNormal.dot(tempLightDir));
    double diffuseFactor = diffuseCoefficient * NdotL * tempLightProps.intensity;
    int diffuse = ColorUtil.multiply(patternColor, (float) diffuseFactor);
    diffuse = ColorUtil.multiplyColors(diffuse, tempLightProps.color, 1.0);

    // View direction
    tempViewDir.set(viewerPos);
    tempViewDir.subtract(worldPoint);
    tempViewDir.normalize();

    // Halfway vector
    tempHalfway.set(tempLightDir);
    tempHalfway.add(tempViewDir);
    tempHalfway.normalize();

    // Specular
    double NdotH = Math.max(0, worldNormal.dot(tempHalfway));
    double specularIntensity = Math.pow(NdotH, shininess);
    double specularFactor = specularCoefficient * specularIntensity * tempLightProps.intensity;

    int specular = ColorUtil.multiply(0xFFFFFFFF, (float) specularFactor);
    specular = ColorUtil.multiplyColors(specular, tempLightProps.color, 1.0);

    // Ambient
    double ambientFactor = ambientCoefficient * tempLightProps.intensity;
    int ambient = ColorUtil.multiply(patternColor, (float) ambientFactor);
    ambient = ColorUtil.multiplyColors(ambient, tempLightProps.color, 1.0);

    // Combine
    int result = ColorUtil.add(ambient, diffuse);
    result = ColorUtil.add(result, specular);

    return ColorUtil.clampColor(result);
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

  public int[] getStripeColors() {
    return stripeColors.clone();
  }

  public double getScale() {
    return scale;
  }

  public int getPhase() {
    return phase;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("AfricanKenteMaterial africankentematerial {\n");
    sb.append("        stripeCount = ").append(stripeColors.length).append(";\n");
    sb.append("        scale = ").append(scale).append(";\n");
    sb.append("        phase = ").append(phase).append(";\n");
    sb.append("    }");
    return sb.toString();
  }
}
