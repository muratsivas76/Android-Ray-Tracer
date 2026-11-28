package net.murat.material;

import net.murat.light.Light;
import net.murat.light.LightProperties;
import net.murat.math.*;
import net.murat.util.ColorUtil;

/**
 * AmberMaterial - a uniform, resin/glass-like translucent material.
 *
 * <p>Fix notes (compared to the previous revision): 1. The previous implementation transformed
 * `point` and `normal` into local object space using inverseObjectTransform /
 * inverseTransposeTransform, but then combined them with `light.direction` and `viewerPoint`, both
 * of which are ALWAYS given in world space by the ray tracer. Mixing a local-space normal with a
 * world-space light direction in a dot product is mathematically meaningless once the object's
 * transform contains any rotation or non-uniform scale - this was the root cause of the broken
 * look. 2. The view direction was computed as (world viewerPoint - local point), which is invalid
 * whenever the transform has any translation: subtracting points expressed in two different spaces
 * does not produce a meaningful vector. 3. Since AmberMaterial's shading does not depend on any
 * object-local pattern (unlike e.g. CheckerboardMaterial), there is no need to enter local space at
 * all. All lighting math is now done entirely in world space, exactly as the ray tracer provides
 * it. 4. Added a Blinn-Phong specular term to better sell the glassy/resinous look of amber (sharp,
 * small highlight facing the light).
 *
 * <p>setObjectTransform(...) is kept (Material interface requires it) and still stores the
 * transform/inverse/inverse-transpose, in case future pattern-based features need local space. It
 * is simply not used by getColorAt() anymore.
 */
public class AmberMaterial implements Material {
  private int baseColor;
  private double transparency;
  private double reflectivity;
  private double indexOfRefraction;
  private Matrix4 objectTransform;
  private Matrix4 inverseObjectTransform;
  private Matrix4 inverseTransposeTransform;

  private int baseR;
  private int baseG;
  private int baseB;

  /** Specular shininess exponent - higher = smaller, sharper highlight. */
  private double shininess = 64.0;

  /** Specular intensity multiplier. */
  private double specularStrength = 0.5;

  // ===== GC-FRIENDLY: PRE-ALLOCATED TEMP OBJECTS =====
  private final Vector3 tempNormal = new Vector3();
  private final Vector3 tempViewDir = new Vector3();
  private final Vector3 tempLightDir = new Vector3();
  private final Vector3 tempHalfVec = new Vector3();
  private final Point3 tempLocalPoint = new Point3();
  private final LightProperties tempLightProps = new LightProperties();

  public AmberMaterial() {
    this.baseColor = 0xFFFFB038;
    this.baseR = (baseColor >> 16) & 0xFF;
    this.baseG = (baseColor >> 8) & 0xFF;
    this.baseB = (baseColor) & 0xFF;

    this.transparency = 0.5;
    this.reflectivity = 0.25;
    this.indexOfRefraction = 1.52;
    this.objectTransform = new Matrix4();
    this.inverseObjectTransform = new Matrix4();
    this.inverseTransposeTransform = new Matrix4();
  }

  public AmberMaterial(
      int baseColor, double indexOfRefraction, double reflectivity, double transparency) {
    this.baseColor = baseColor;
    this.baseR = (baseColor >> 16) & 0xFF;
    this.baseG = (baseColor >> 8) & 0xFF;
    this.baseB = baseColor & 0xFF;

    this.transparency = transparency;
    this.reflectivity = reflectivity;
    this.indexOfRefraction = indexOfRefraction;
    this.objectTransform = new Matrix4();
    this.inverseObjectTransform = new Matrix4();
    this.inverseTransposeTransform = new Matrix4();
  }

  @Override
  public int getColorAt(Point3 point, Vector3 normal, Light light, Point3 viewerPoint) {
    // point, normal and viewerPoint are already in world space, as supplied
    // by the ray tracer - no local-space transform is needed here.
    tempNormal.set(normal).normalize();

    // 1. Light properties - ZERO ALLOCATION
    LightProperties.getLightProperties(light, point, tempLightProps);
    tempLightDir.set(tempLightProps.direction);

    int lightColor = tempLightProps.color;
    int lightR = (lightColor >> 16) & 0xFF;
    int lightG = (lightColor >> 8) & 0xFF;
    int lightB = lightColor & 0xFF;

    // 2. Diffuse term - both vectors are now in world space.
    double diffuse = Math.max(0.0, tempNormal.dot(tempLightDir));

    // 3. View direction - world point to world viewer, correct subtraction.
    tempViewDir.set(viewerPoint).subtract(point).normalize();

    // 4. Rim term (Fresnel-like edge glow).
    // double rim = Math.pow(1.0 - Math.max(0.0, tempNormal.dot(tempViewDir)), 2.0);
    // double t = Math.max(0.0, Math.min(1.0, (thresholdHigh - nv) / (thresholdHigh -
    // thresholdLow)));
    // double rim = t * t * (3 - 2 * t);
    double rt = 1.0 - Math.max(0.0, tempNormal.dot(tempViewDir));
    double rim = rt * rt * (3.0 - 2.0 * rt);

    // 5. Specular term (Blinn-Phong half-vector) - sells the glassy resin look.
    tempHalfVec.set(tempLightDir).add(tempViewDir).normalize();
    double specAngle = Math.max(0.0, tempNormal.dot(tempHalfVec));
    // double specular = Math.pow(specAngle, shininess);
    double specular = Math.pow(specAngle, shininess) * Math.min(1.0, diffuse * 10.0);

    double ambient = 0.1;
    double intensity = tempLightProps.intensity;
    double totalLight =
        ambient + intensity * (0.7 * diffuse + 0.3 * rim) + intensity * specularStrength * specular;

    int r = (int) (baseR * totalLight * lightR / 255.0);
    int g = (int) (baseG * totalLight * lightG / 255.0);
    int b = (int) (baseB * totalLight * lightB / 255.0);

    r = r > 255 ? 255 : (r < 0 ? 0 : r);
    g = g > 255 ? 255 : (g < 0 ? 0 : g);
    b = b > 255 ? 255 : (b < 0 ? 0 : b);

    return (0xFF000000 | (r << 16) | (g << 8) | b);
  }

  @Override
  public double getTransparency() {
    return transparency;
  }

  @Override
  public double getReflectivity() {
    return reflectivity;
  }

  @Override
  public double getIndexOfRefraction() {
    return indexOfRefraction;
  }

  @Override
  public void setObjectTransform(Matrix4 tm) {
    if (tm == null) {
      this.objectTransform = new Matrix4();
    } else {
      this.objectTransform = tm;
    }
    this.inverseObjectTransform = this.objectTransform.inverse();
    this.inverseTransposeTransform = this.inverseObjectTransform.transpose();
  }

  public void setBaseColor(int baseColor) {
    this.baseColor = baseColor;
    this.baseR = (baseColor >> 16) & 0xFF;
    this.baseG = (baseColor >> 8) & 0xFF;
    this.baseB = baseColor & 0xFF;
  }

  public int getBaseColor() {
    return this.baseColor;
  }

  public void setShininess(double shininess) {
    this.shininess = shininess;
  }

  public double getShininess() {
    return this.shininess;
  }

  public void setSpecularStrength(double specularStrength) {
    this.specularStrength = specularStrength;
  }

  public double getSpecularStrength() {
    return this.specularStrength;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("AmberMaterial ambermaterial {\n");
    sb.append("        baseColor = ").append(ColorUtil.toColorString(baseColor)).append(";\n");
    sb.append("        ior = ").append(indexOfRefraction).append(";\n");
    sb.append("        reflectivity = ").append(reflectivity).append(";\n");
    sb.append("        transparency = ").append(transparency).append(";\n");
    sb.append("    }");
    return sb.toString();
  }
}
