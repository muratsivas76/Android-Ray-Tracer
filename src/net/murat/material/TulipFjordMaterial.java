package net.murat.material;

import net.murat.light.*;
import net.murat.math.*;
import net.murat.util.ColorUtil;

/**
 * TulipFjordMaterial - Cultural fusion of Turkish tulip and Norwegian fjord. Zero-allocation using
 * packed ARGB integers.
 */
public class TulipFjordMaterial implements Material {
  private final int tulipColorPacked;
  private final int fjordColorPacked;
  private final int stemColorPacked;
  private final double bloomIntensity;
  private Matrix4 objectTransform;
  private Matrix4 inverseTransform;
  private Matrix4 inverseTransposeTransform;

  // Pre-cached color components
  private final int tulipR, tulipG, tulipB;
  private final int fjordR, fjordG, fjordB;
  private final int stemR, stemG, stemB;

  private final double ambientCoeff = 0.4;
  private final double diffuseCoeff = 0.75;
  private final double specularCoeff = 0.35;
  private final double shininess = 50.0;
  private final double reflectivity = 0.2;
  private final double ior = 1.55;
  private final double transparency = 0.1;

  private static final int SPECULAR_COLOR = 0xFFFFF5EE;

  // ===== GC DOSTU: PRE-ALLOCATED TEMP OBJECTS =====
  private final Point3 tempLocalPoint = new Point3();
  private final Vector3 tempLocalNormal = new Vector3();
  private final Vector3 tempTransformedNormal = new Vector3();
  private final Vector3 tempViewDir = new Vector3();
  private final Vector3 tempLightDir = new Vector3();
  private final Vector3 tempReflectDir = new Vector3();
  private final Point3 tempViewerPoint = new Point3();
  private final LightProperties tempLightProps = new LightProperties();

  public TulipFjordMaterial() {
    this(0xFFFF0000, 0xFF007FFF, 0xFF228B22, 0.6);
  }

  public TulipFjordMaterial(int tulipColor, int fjordColor, int stemColor, double bloomIntensity) {
    this.tulipColorPacked = tulipColor;
    this.fjordColorPacked = fjordColor;
    this.stemColorPacked = stemColor;
    this.bloomIntensity = Math.max(0, Math.min(1, bloomIntensity));

    this.tulipR = (tulipColor >> 16) & 0xFF;
    this.tulipG = (tulipColor >> 8) & 0xFF;
    this.tulipB = tulipColor & 0xFF;

    this.fjordR = (fjordColor >> 16) & 0xFF;
    this.fjordG = (fjordColor >> 8) & 0xFF;
    this.fjordB = fjordColor & 0xFF;

    this.stemR = (stemColor >> 16) & 0xFF;
    this.stemG = (stemColor >> 8) & 0xFF;
    this.stemB = stemColor & 0xFF;

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
    if (inverseTransform == null) {
      return 0xFF000000;
    }

    // 1. Transform to local space
    inverseTransform.transformPoint(worldPoint, tempLocalPoint);

    // 2. Transform normal
    inverseTransposeTransform.transformDirection(worldNormal, tempTransformedNormal);
    tempLocalNormal.set(tempTransformedNormal);
    tempLocalNormal.normalize();

    // 3. Calculate pattern
    int surfaceColor = calculateTulipFjordPattern(tempLocalPoint, tempLocalNormal, viewerPos);
    int surfR = (surfaceColor >> 16) & 0xFF;
    int surfG = (surfaceColor >> 8) & 0xFF;
    int surfB = surfaceColor & 0xFF;

    // 4. Light properties - ZERO ALLOCATION
    LightProperties.getLightProperties(light, worldPoint, tempLightProps);
    tempLightDir.set(tempLightProps.direction);

    int lightColor = tempLightProps.color;
    int lightR = (lightColor >> 16) & 0xFF;
    int lightG = (lightColor >> 8) & 0xFF;
    int lightB = lightColor & 0xFF;
    double intensity = tempLightProps.intensity;

    // 5. Ambient check
    int ambient = ColorUtil.multiplyColors(surfaceColor, tempLightProps.color, ambientCoeff);

    if (light instanceof MuratAmbientLight) {
      return ambient;
    }

    // 6. View direction - ZERO ALLOCATION
    tempViewerPoint.set(viewerPos);
    tempViewDir.set(tempViewerPoint);
    tempViewDir.subtract(worldPoint);
    tempViewDir.normalize();

    // 7. Diffuse
    double NdotL = Math.max(0.0, tempLocalNormal.dot(tempLightDir));
    double diffFactor = diffuseCoeff * NdotL * intensity;
    int diffR = (int) (surfR * diffFactor * lightR / 255.0);
    int diffG = (int) (surfG * diffFactor * lightG / 255.0);
    int diffB = (int) (surfB * diffFactor * lightB / 255.0);

    // 8. Reflection direction - ZERO ALLOCATION
    tempReflectDir.set(tempLightDir);
    tempReflectDir.negate();
    tempReflectDir.reflect(tempLocalNormal, tempReflectDir);

    // 9. Specular
    double RdotV = Math.max(0.0, tempReflectDir.dot(tempViewDir));
    double specFactor = Math.pow(RdotV, shininess) * specularCoeff * intensity;

    int specR = (int) (((SPECULAR_COLOR >> 16) & 0xFF) * specFactor * lightR / 255.0);
    int specG = (int) (((SPECULAR_COLOR >> 8) & 0xFF) * specFactor * lightG / 255.0);
    int specB = (int) ((SPECULAR_COLOR & 0xFF) * specFactor * lightB / 255.0);

    specR = clamp(specR);
    specG = clamp(specG);
    specB = clamp(specB);

    // 10. Combine
    int r = ((ambient >> 16) & 0xFF) + diffR + specR;
    int g = ((ambient >> 8) & 0xFF) + diffG + specG;
    int b = (ambient & 0xFF) + diffB + specB;

    r = clamp(r);
    g = clamp(g);
    b = clamp(b);

    return (0xFF000000 | (r << 16) | (g << 8) | b);
  }

  private int calculateTulipFjordPattern(Point3 point, Vector3 normal, Point3 viewerPos) {
    double x = point.x * 10.0;
    double y = point.y * 10.0;
    double z = point.z * 10.0;

    double petal1 = Math.sin(x * 1.8 + Math.cos(y * 1.2) * 2.5);
    double petal2 = Math.abs(Math.cos(x * 2.0 + y * 1.5) + Math.sin(y * 1.8 + z * 1.0));
    double petal3 = Math.sin(x * 2.5 + y * 2.0) * Math.cos(y * 1.2 + z * 0.8);

    double fjord1 = Math.sin(x * 1.5 + Math.sin(y * 0.7) * 1.8);
    double fjord2 = Math.cos(y * 1.3 + Math.cos(x * 0.9) * 2.2);
    double fjord3 = Math.abs(Math.sin(x * 2.2 + y * 1.6 + z * 1.3));

    double tulipWeight = bloomIntensity;
    double fjordWeight = 1.0 - bloomIntensity;

    double combinedPattern =
        (petal1 * 0.25 + petal2 * 0.2 + petal3 * 0.15) * tulipWeight
            + (fjord1 * 0.2 + fjord2 * 0.15 + fjord3 * 0.05) * fjordWeight;

    double normalizedPattern = (combinedPattern + 1.0) * 0.5;

    // View effect - ZERO ALLOCATION
    tempViewerPoint.set(viewerPos);
    tempViewDir.set(tempViewerPoint);
    tempViewDir.subtract(point);
    tempViewDir.normalize();

    double viewAngle = Math.abs(tempViewDir.dot(normal));
    double viewEffect = Math.pow(viewAngle, 0.7);

    int r, g, b;
    if (normalizedPattern < 0.3) {
      double depth = normalizedPattern / 0.3;
      int darkR = (int) (fjordR * 0.6);
      int darkG = (int) (fjordG * 0.6);
      int darkB = (int) (fjordB * 0.6);
      r = (int) (fjordR + (darkR - fjordR) * depth);
      g = (int) (fjordG + (darkG - fjordG) * depth);
      b = (int) (fjordB + (darkB - fjordB) * depth);
    } else if (normalizedPattern < 0.6) {
      double intensity = (normalizedPattern - 0.3) / 0.3;
      int lightR = (int) (tulipR + (255 - tulipR) * 0.3);
      int lightG = (int) (tulipG + (255 - tulipG) * 0.3);
      int lightB = (int) (tulipB + (255 - tulipB) * 0.3);
      r = (int) (tulipR + (lightR - tulipR) * intensity);
      g = (int) (tulipG + (lightG - tulipG) * intensity);
      b = (int) (tulipB + (lightB - tulipB) * intensity);
      double factor = viewEffect;
      r = (int) (r * factor);
      g = (int) (g * factor);
      b = (int) (b * factor);
    } else if (normalizedPattern < 0.8) {
      r = stemR;
      g = stemG;
      b = stemB;
    } else {
      double intensity = (normalizedPattern - 0.8) / 0.2;
      r = (int) (fjordR + (255 - fjordR) * intensity * 0.5);
      g = (int) (fjordG + (255 - fjordG) * intensity * 0.5);
      b = (int) (fjordB + (255 - fjordB) * intensity * 0.5);
      double factor = intensity * 0.7 * viewEffect;
      r = (int) (r + (255 - r) * factor);
      g = (int) (g + (255 - g) * factor);
      b = (int) (b + (255 - b) * factor);
    }

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
    sb.append("TulipFjordMaterial tulipfjordmaterial {\n");
    sb.append("        tulipColor = ")
        .append(ColorUtil.toColorString(tulipColorPacked))
        .append(";\n");
    sb.append("        fjordColor = ")
        .append(ColorUtil.toColorString(fjordColorPacked))
        .append(";\n");
    sb.append("        stemColor = ")
        .append(ColorUtil.toColorString(stemColorPacked))
        .append(";\n");
    sb.append("        bloomIntensity = ").append(bloomIntensity).append(";\n");
    sb.append("    }");
    return sb.toString();
  }
}
