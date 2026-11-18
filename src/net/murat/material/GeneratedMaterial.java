package net.murat.material;

import net.murat.light.Light;
import net.murat.light.LightProperties;
import net.murat.math.*;
import net.murat.util.ColorUtil;

/**
 * GeneratedMaterial - a fully configurable material created from user-defined parameters via the
 * MaterialGenerator/UI system.
 *
 * <p>Supports multiple procedural patterns (solid, checkerboard, vertical gradient, wood rings,
 * marble, brick, stripes, dots, noise, fractal) with configurable scale and frequency.
 *
 * <p>All lighting calculations are performed entirely in world space, exactly as the ray tracer
 * provides them, ensuring correct results regardless of object transforms.
 *
 * <p>Fully GC-friendly with zero allocation during getColorAt().
 */
public class GeneratedMaterial implements Material {
  // ===== BASE PROPERTIES =====
  private int color1;
  private int color2;
  private double reflectivity;
  private double ior;
  private double transparency;
  private double shininess;
  private double roughness;

  // ===== PATTERN PROPERTIES =====
  private PatternType pattern;
  private double patternScale;
  private double patternFrequency;

  // ===== PHYSICAL PROPERTIES =====
  private MaterialType materialType;
  private boolean useFresnel;
  private double fresnelF0;

  // ===== SPECIAL EFFECTS =====
  private boolean useNormalMap;
  private boolean useDisplacement;
  private double displacementScale;

  // ===== TRANSFORM =====
  private Matrix4 objectTransform;

  // ===== GC-FRIENDLY: PRE-ALLOCATED TEMP OBJECTS =====
  private final Vector3 tempNormal = new Vector3();
  private final Vector3 tempViewDir = new Vector3();
  private final Vector3 tempLightDir = new Vector3();
  private final Vector3 tempHalfVec = new Vector3();
  private final LightProperties tempLightProps = new LightProperties();

  // ===== CONSTRUCTORS =====

  /** Full constructor with all parameters. */
  public GeneratedMaterial(
      int color1,
      int color2,
      double reflectivity,
      double ior,
      double transparency,
      double shininess,
      double roughness,
      PatternType pattern,
      double patternScale,
      double patternFrequency,
      MaterialType materialType,
      boolean useFresnel,
      double fresnelF0,
      boolean useNormalMap,
      boolean useDisplacement,
      double displacementScale) {

    this.color1 = color1;
    this.color2 = color2;
    this.reflectivity = reflectivity;
    this.ior = ior;
    this.transparency = transparency;
    this.shininess = shininess;
    this.roughness = roughness;
    this.pattern = pattern;
    this.patternScale = patternScale;
    this.patternFrequency = patternFrequency;
    this.materialType = materialType;
    this.useFresnel = useFresnel;
    this.fresnelF0 = fresnelF0;
    this.useNormalMap = useNormalMap;
    this.useDisplacement = useDisplacement;
    this.displacementScale = displacementScale;

    this.objectTransform = new Matrix4();
  }

  /** Simplified constructor with sensible defaults. */
  public GeneratedMaterial(int color1, int color2) {
    this(
        color1,
        color2,
        0.0,
        1.0,
        0.0,
        32.0,
        0.5,
        PatternType.SOLID,
        1.0,
        1.0,
        MaterialType.PLASTIC,
        false,
        0.04,
        false,
        false,
        0.0);
  }

  /** Default constructor - solid white plastic. */
  public GeneratedMaterial() {
    this(ColorUtil.WHITE, ColorUtil.BLACK);
  }

  // ===== PATTERN CALCULATION =====

  /**
   * Calculates the pattern color at the given world-space point. Uses the normal for gradient-based
   * patterns. Zero-allocation, purely mathematical.
   */
  private int calculatePattern(Point3 point, Vector3 normal) {
    double x = point.getX();
    double y = point.getY();
    double z = point.getZ();

    switch (pattern) {
      case SOLID:
        return color1;

      case CHECKERBOARD:
        {
          double u = x * patternScale;
          double v = z * patternScale;
          double checker = (Math.floor(u) + Math.floor(v)) % 2;
          return (checker < 0.5) ? color1 : color2;
        }

      case VERTICAL_GRADIENT:
        {
          double ny = normal.getY();
          double t = (ny + 1.0) * 0.5;
          t = Math.min(1.0, Math.max(0.0, t));
          return ColorUtil.lerp(color2, color1, (float) t);
        }

      case HORIZONTAL_GRADIENT:
        {
          double nx = normal.getX();
          double t = (nx + 1.0) * 0.5;
          t = Math.min(1.0, Math.max(0.0, t));
          return ColorUtil.lerp(color2, color1, (float) t);
        }

      case RADIAL_GRADIENT:
        {
          double radius = Math.sqrt(x * x + z * z) * patternScale;
          double t = radius - Math.floor(radius);
          return ColorUtil.lerp(color2, color1, (float) t);
        }

      case WOOD_RINGS:
        {
          double radius = Math.sqrt(x * x + z * z);
          double rings = Math.sin(radius * patternFrequency * 20.0 + y * 0.5) * 0.5 + 0.5;
          rings = rings * rings;
          return ColorUtil.lerp(color2, color1, (float) rings);
        }

      case MARBLE:
        {
          double noise = x * 0.5 + y * 0.3 + z * 0.7;
          double marble = Math.sin(noise * patternFrequency * 10.0 + y * 3.0) * 0.5 + 0.5;
          marble = Math.pow(marble, 3.0);
          return ColorUtil.lerp(color2, color1, (float) marble);
        }

      case BRICK:
        {
          double bx = x * patternScale;
          double bz = z * patternScale;
          double row = Math.floor(bz / 0.5);
          double col = Math.floor(bx / 0.5 + (row % 2) * 0.5);
          double brick = ((row % 2) + (col % 2)) % 2;
          return (brick < 0.5) ? color1 : color2;
        }

      case STRIPES:
        {
          double stripe = Math.sin(x * patternFrequency * 20.0) * 0.5 + 0.5;
          stripe = Math.min(1.0, Math.max(0.0, stripe));
          return ColorUtil.lerp(color2, color1, (float) stripe);
        }

      case DOTS:
        {
          double dx = (x * patternScale) % 1.0;
          double dz = (z * patternScale) % 1.0;
          if (dx < 0.0) dx += 1.0;
          if (dz < 0.0) dz += 1.0;
          double dist = Math.sqrt(dx * dx + dz * dz);
          return (dist < 0.3) ? color1 : color2;
        }

      case NOISE:
        {
          double noiseVal = simpleNoise(x * patternScale, z * patternScale);
          return ColorUtil.lerp(color2, color1, (float) noiseVal);
        }

      case FRACTAL:
        {
          double value = 0.0;
          double amplitude = 0.5;
          double frequency = patternScale;
          for (int i = 0; i < 4; i++) {
            value += amplitude * simpleNoise(x * frequency, z * frequency);
            frequency *= 2.0;
            amplitude *= 0.5;
          }
          value = Math.min(1.0, Math.max(0.0, value));
          return ColorUtil.lerp(color2, color1, (float) value);
        }

      default:
        return color1;
    }
  }

  /** Simple 2D value noise for procedural patterns. Zero-allocation, purely mathematical. */
  private double simpleNoise(double x, double z) {
    double hash = Math.sin(x * 127.1 + z * 311.7) * 43758.5453;
    return hash - Math.floor(hash);
  }

  // ===== MATERIAL INTERFACE IMPLEMENTATION =====
  @Override
  public int getColorAt(Point3 point, Vector3 normal, Light light, Point3 viewerPoint) {
    tempNormal.set(normal).normalize();

    int baseColor = calculatePattern(point, tempNormal);

    LightProperties.getLightProperties(light, point, tempLightProps);
    tempLightDir.set(tempLightProps.direction);
    int lightColor = tempLightProps.color;

    double nDotL = Math.max(0.0, tempNormal.dot(tempLightDir));
    double diffuse = nDotL * (1.0 - roughness * 0.3);

    tempViewDir.set(viewerPoint).subtract(point).normalize();

    tempHalfVec.set(tempLightDir).add(tempViewDir).normalize();
    double nDotH = Math.max(0.0, tempNormal.dot(tempHalfVec));
    double specular = Math.pow(nDotH, shininess) * (1.0 - roughness);

    double ambient = 0.15;
    double intensity = tempLightProps.intensity;

    double fresnelWeight = 0.0;
    if (useFresnel && transparency > 0.1) {
      double cosTheta = Math.max(0.0, tempNormal.dot(tempViewDir));
      fresnelWeight = fresnelF0 + (1.0 - fresnelF0) * Math.pow(1.0 - cosTheta, 5.0);
    }

    double surfaceWeight = (1.0 - reflectivity) * (1.0 - transparency);
    double totalLight =
        ambient * surfaceWeight
            + intensity * (0.7 * diffuse * surfaceWeight * (1.0 - fresnelWeight))
            + intensity * specular * 0.3 * surfaceWeight;
    int litColor = ColorUtil.multiplyColors(baseColor, lightColor);
    litColor = ColorUtil.multiplyColor(litColor, totalLight);

    return (0xFF << 24) | (litColor & 0x00FFFFFF);
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
  public void setObjectTransform(Matrix4 tm) {
    if (tm == null) {
      this.objectTransform = new Matrix4();
    } else {
      this.objectTransform = tm;
    }
  }

  // ===== SETTERS (for UI updates) =====

  public void setColor1(int color1) {
    this.color1 = color1;
  }

  public void setColor2(int color2) {
    this.color2 = color2;
  }

  public void setReflectivity(double reflectivity) {
    this.reflectivity = Math.min(1.0, Math.max(0.0, reflectivity));
  }

  public void setIor(double ior) {
    this.ior = Math.max(1.0, ior);
  }

  public void setTransparency(double transparency) {
    this.transparency = Math.min(1.0, Math.max(0.0, transparency));
  }

  public void setShininess(double shininess) {
    this.shininess = Math.max(1.0, shininess);
  }

  public void setRoughness(double roughness) {
    this.roughness = Math.min(1.0, Math.max(0.0, roughness));
  }

  public void setPattern(PatternType pattern) {
    this.pattern = pattern;
  }

  public void setPatternScale(double patternScale) {
    this.patternScale = Math.max(0.1, patternScale);
  }

  public void setPatternFrequency(double patternFrequency) {
    this.patternFrequency = Math.max(0.1, patternFrequency);
  }

  public void setMaterialType(MaterialType materialType) {
    this.materialType = materialType;
  }

  public void setUseFresnel(boolean useFresnel) {
    this.useFresnel = useFresnel;
  }

  public void setFresnelF0(double fresnelF0) {
    this.fresnelF0 = Math.min(1.0, Math.max(0.0, fresnelF0));
  }

  public void setUseNormalMap(boolean useNormalMap) {
    this.useNormalMap = useNormalMap;
  }

  public void setUseDisplacement(boolean useDisplacement) {
    this.useDisplacement = useDisplacement;
  }

  public void setDisplacementScale(double displacementScale) {
    this.displacementScale = displacementScale;
  }

  // ===== GETTERS =====

  public int getColor1() {
    return color1;
  }

  public int getColor2() {
    return color2;
  }

  public double getShininess() {
    return shininess;
  }

  public double getRoughness() {
    return roughness;
  }

  public PatternType getPattern() {
    return pattern;
  }

  public double getPatternScale() {
    return patternScale;
  }

  public double getPatternFrequency() {
    return patternFrequency;
  }

  public MaterialType getMaterialType() {
    return materialType;
  }

  public boolean getUseFresnel() {
    return useFresnel;
  }

  public double getFresnelF0() {
    return fresnelF0;
  }

  public boolean getUseNormalMap() {
    return useNormalMap;
  }

  public boolean getUseDisplacement() {
    return useDisplacement;
  }

  public double getDisplacementScale() {
    return displacementScale;
  }

  // ===== TOSTRING =====

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("GeneratedMaterial generatedmaterial {\n");
    sb.append("        color1 = ").append(ColorUtil.toColorString(color1)).append(";\n");
    sb.append("        color2 = ").append(ColorUtil.toColorString(color2)).append(";\n");
    sb.append("        ior = ").append(ior).append(";\n");
    sb.append("        reflectivity = ").append(reflectivity).append(";\n");
    sb.append("        transparency = ").append(transparency).append(";\n");
    sb.append("        shininess = ").append(shininess).append(";\n");
    sb.append("        roughness = ").append(roughness).append(";\n");
    sb.append("        pattern = ").append(pattern).append(";\n");
    sb.append("        patternScale = ").append(patternScale).append(";\n");
    sb.append("        patternFrequency = ").append(patternFrequency).append(";\n");
    sb.append("        materialType = ").append(materialType).append(";\n");
    sb.append("        useFresnel = ").append(useFresnel).append(";\n");
    sb.append("        fresnelF0 = ").append(fresnelF0).append(";\n");
    sb.append("        useNormalMap = ").append(useNormalMap).append(";\n");
    sb.append("        useDisplacement = ").append(useDisplacement).append(";\n");
    sb.append("        displacementScale = ").append(displacementScale).append(";\n");
    sb.append(
        "        #PATTERNS: SOLID, CHECKERBOARD, VERTICAL_GRADIENT, HORIZONTAL_GRADIENT,"
            + " RADIAL_GRADIENT, WOOD_RINGS, MARBLE, BRICK, STRIPES, DOTS, NOISE, FRACTAL\n");
    sb.append(
        "        #TYPES: PLASTIC, METAL, GLASS, SNOW_GLOBE, CARPET, WOOD, MARBLE, LEATHER, FABRIC,"
            + " RUBBER, CUSTOM\n");
    sb.append("    }");

    return sb.toString();
  }
}
