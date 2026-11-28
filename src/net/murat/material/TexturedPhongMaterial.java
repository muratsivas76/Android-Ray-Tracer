package net.murat.material;

import android.graphics.Bitmap;
import net.murat.light.*;
import net.murat.math.Matrix4;
import net.murat.math.Point3;
import net.murat.math.Vector3;
import net.murat.util.ColorUtil;

/**
 * TexturedPhongMaterial - Phong material with texture mapping. Android adaptation: Uses Bitmap +
 * int[] for fast, GC-free sampling.
 */
public class TexturedPhongMaterial implements Material {
  private final int baseDiffuseColorPacked;
  private final int specularColorPacked;
  private final double shininess;
  private final double ambientCoefficient;
  private final double diffuseCoefficient;
  private final double specularCoefficient;
  private final double reflectivity;
  private final double ior;
  private final double transparency;

  private final Bitmap texture;
  private final int[] texturePixels;
  private final int texWidth;
  private final int texHeight;

  private final double uOffset;
  private final double vOffset;
  private final double uScale;
  private final double vScale;

  private Matrix4 objectInverseTransform;
  private Matrix4 inverseTransposeTransform;

  // Pre-cached color components
  private final int baseR, baseG, baseB;
  private final int specR, specG, specB;

  // ===== GC FRIENDLY: PRE-ALLOCATED TEMP OBJECTS =====
  private final Point3 tempLocalPoint = new Point3();
  private final Vector3 tempLocalNormal = new Vector3();
  private final Vector3 tempTransformedNormal = new Vector3();
  private final Vector3 tempViewDir = new Vector3();
  private final Vector3 tempLightDir = new Vector3();
  private final Vector3 tempReflectDir = new Vector3();
  private final Point3 tempViewerPoint = new Point3();
  private final LightProperties tempLightProps = new LightProperties();

  private String imagePath = "textures/turkeyFlag.png";

  public TexturedPhongMaterial(
      int baseDiffuseColor,
      int specularColor,
      double shininess,
      double ambientCoefficient,
      double diffuseCoefficient,
      double specularCoefficient,
      double reflectivity,
      double ior,
      double transparency,
      Bitmap texture,
      double uOffset,
      double vOffset,
      double uScale,
      double vScale,
      Matrix4 objectInverseTransform) {
    this.baseDiffuseColorPacked = baseDiffuseColor;
    this.specularColorPacked = specularColor;
    this.shininess = shininess;
    this.ambientCoefficient = clamp01(ambientCoefficient);
    this.diffuseCoefficient = clamp01(diffuseCoefficient);
    this.specularCoefficient = clamp01(specularCoefficient);
    this.reflectivity = clamp01(reflectivity);
    this.ior = Math.max(1.0, ior);
    this.transparency = clamp01(transparency);
    this.texture = texture;
    this.uOffset = uOffset;
    this.vOffset = vOffset;
    this.uScale = uScale;
    this.vScale = vScale;
    this.objectInverseTransform =
        objectInverseTransform != null ? objectInverseTransform : new Matrix4();
    this.inverseTransposeTransform = this.objectInverseTransform.inverseTransposeForNormal();

    // Cache pixel data for fast sampling
    if (texture != null) {
      this.texWidth = texture.getWidth();
      this.texHeight = texture.getHeight();
      this.texturePixels = new int[texWidth * texHeight];
      texture.getPixels(this.texturePixels, 0, texWidth, 0, 0, texWidth, texHeight);
    } else {
      this.texWidth = 0;
      this.texHeight = 0;
      this.texturePixels = null;
    }

    this.baseR = (baseDiffuseColor >> 16) & 0xFF;
    this.baseG = (baseDiffuseColor >> 8) & 0xFF;
    this.baseB = baseDiffuseColor & 0xFF;

    this.specR = (specularColor >> 16) & 0xFF;
    this.specG = (specularColor >> 8) & 0xFF;
    this.specB = specularColor & 0xFF;
  }

  public TexturedPhongMaterial(
      int baseDiffuseColor, Bitmap texture, Matrix4 objectInverseTransform) {
    this(
        baseDiffuseColor,
        0xFFFFFFFF,
        32.0,
        0.1,
        0.7,
        0.7,
        0.0,
        1.0,
        0.0,
        texture,
        0.0,
        0.0,
        1.0,
        1.0,
        objectInverseTransform);
  }

  public TexturedPhongMaterial(
      int baseDiffuseColor,
      int specularColor,
      double shininess,
      double ambientCoefficient,
      double diffuseCoefficient,
      double specularCoefficient,
      double reflectivity,
      double ior,
      double transparency,
      Bitmap texture,
      Matrix4 objectInverseTransform) {
    this(
        baseDiffuseColor,
        specularColor,
        shininess,
        ambientCoefficient,
        diffuseCoefficient,
        specularCoefficient,
        reflectivity,
        ior,
        transparency,
        texture,
        0.0,
        0.0,
        1.0,
        1.0,
        objectInverseTransform);
  }

  public TexturedPhongMaterial(int diffuseColor, Matrix4 objectInverseTransform) {
    this(
        diffuseColor,
        0xFFFFFFFF,
        32.0,
        0.1,
        0.7,
        0.7,
        0.0,
        1.0,
        0.0,
        null,
        0.0,
        0.0,
        1.0,
        1.0,
        objectInverseTransform);
  }

  @Override
  public void setObjectTransform(Matrix4 tm) {
    if (tm == null) tm = new Matrix4();
    this.objectInverseTransform = tm;
    this.inverseTransposeTransform = tm.inverseTransposeForNormal();
  }

  private int getTextureColor(Point3 localPoint, Vector3 localNormal) {
    if (texturePixels == null) {
      return baseDiffuseColorPacked;
    }
    if (localNormal == null) {
      return baseDiffuseColorPacked;
    }

    double absNx = Math.abs(localNormal.x);
    double absNy = Math.abs(localNormal.y);
    double absNz = Math.abs(localNormal.z);

    double u, v;
    double eps = 1e-10;
    if (absNx > absNy && absNx > absNz) {
      u = localPoint.y + eps;
      v = localPoint.z + eps;
    } else if (absNy > absNx && absNy > absNz) {
      u = localPoint.x + eps;
      v = localPoint.z + eps;
    } else {
      u = localPoint.x + eps;
      v = localPoint.y + eps;
    }

    u = (u * uScale) + uOffset;
    v = (v * vScale) + vOffset;

    u = (u % 1.0 + 1.0) % 1.0;
    v = (v % 1.0 + 1.0) % 1.0;

    int texX = (int) (u * (texWidth - 1));
    int texY = (int) (v * (texHeight - 1));

    texX = Math.min(Math.max(0, texX), texWidth - 1);
    texY = Math.min(Math.max(0, texY), texHeight - 1);

    // Fast array lookup instead of texture.getRGB()
    return texturePixels[texY * texWidth + texX] | 0xFF000000;
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

    // 3. Get texture color
    int textureColor = getTextureColor(tempLocalPoint, tempLocalNormal);
    int texR = (textureColor >> 16) & 0xFF;
    int texG = (textureColor >> 8) & 0xFF;
    int texB = textureColor & 0xFF;

    // 4. Light properties
    LightProperties.getLightProperties(light, worldPoint, tempLightProps);
    tempLightDir.set(tempLightProps.direction);

    int lightColor = tempLightProps.color;
    int lightR = (lightColor >> 16) & 0xFF;
    int lightG = (lightColor >> 8) & 0xFF;
    int lightB = lightColor & 0xFF;
    double intensity = tempLightProps.intensity;

    // 5. Ambient check
    if (light instanceof MuratAmbientLight) {
      int r = (int) (texR * ambientCoefficient * lightR / 255.0 * intensity);
      int g = (int) (texG * ambientCoefficient * lightG / 255.0 * intensity);
      int b = (int) (texB * ambientCoefficient * lightB / 255.0 * intensity);
      r = clamp(r);
      g = clamp(g);
      b = clamp(b);
      return (0xFF000000 | (r << 16) | (g << 8) | b);
    }

    // 6. View direction
    tempViewerPoint.set(viewerPos);
    tempViewDir.set(tempViewerPoint);
    tempViewDir.subtract(worldPoint);
    tempViewDir.normalize();

    // 7. Diffuse
    double NdotL = Math.max(0.0, tempLocalNormal.dot(tempLightDir));
    double diffFactor = diffuseCoefficient * NdotL * intensity;
    int diffR = (int) (texR * diffFactor * lightR / 255.0);
    int diffG = (int) (texG * diffFactor * lightG / 255.0);
    int diffB = (int) (texB * diffFactor * lightB / 255.0);

    // 8. Reflection direction
    tempReflectDir.set(tempLightDir);
    tempReflectDir.negate();
    tempReflectDir.reflect(tempLocalNormal, tempReflectDir);

    // 9. Specular
    double RdotV = Math.max(0.0, tempReflectDir.dot(tempViewDir));
    double specFactor = Math.pow(RdotV, shininess) * specularCoefficient * intensity;
    int specR = (int) (this.specR * specFactor * lightR / 255.0);
    int specG = (int) (this.specG * specFactor * lightG / 255.0);
    int specB = (int) (this.specB * specFactor * lightB / 255.0);

    // 10. Combine
    int r = (int) (texR * ambientCoefficient * lightR / 255.0 * intensity) + diffR + specR;
    int g = (int) (texG * ambientCoefficient * lightG / 255.0 * intensity) + diffG + specG;
    int b = (int) (texB * ambientCoefficient * lightB / 255.0 * intensity) + diffB + specB;

    r = clamp(r);
    g = clamp(g);
    b = clamp(b);

    return (0xFF000000 | (r << 16) | (g << 8) | b);
  }

  private int clamp(int value) {
    return Math.max(0, Math.min(255, value));
  }

  private double clamp01(double val) {
    return Math.min(1.0, Math.max(0.0, val));
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

  public int getBaseDiffuseColorPacked() {
    return baseDiffuseColorPacked;
  }

  public int getSpecularColorPacked() {
    return specularColorPacked;
  }

  public double getShininess() {
    return shininess;
  }

  public Bitmap getTexture() {
    return texture;
  }

  public double getUOffset() {
    return uOffset;
  }

  public double getVOffset() {
    return vOffset;
  }

  public double getUScale() {
    return uScale;
  }

  public double getVScale() {
    return vScale;
  }

  public void setImagePath(String npath) {
    this.imagePath = npath;
  }

  public String getImagePath() {
    return this.imagePath;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("TexturedPhongMaterial texturedphongmaterial {\n");
    sb.append("        baseDiffuseColor = ")
        .append(ColorUtil.toColorString(baseDiffuseColorPacked))
        .append(";\n");
    sb.append("        specularColor = ")
        .append(ColorUtil.toColorString(specularColorPacked))
        .append(";\n");
    sb.append("        shininess = ").append(shininess).append(";\n");
    sb.append("        ambientCoefficient = ").append(ambientCoefficient).append(";\n");
    sb.append("        diffuseCoefficient = ").append(diffuseCoefficient).append(";\n");
    sb.append("        specularCoefficient = ").append(specularCoefficient).append(";\n");
    sb.append("        reflectivity = ").append(reflectivity).append(";\n");
    sb.append("        ior = ").append(ior).append(";\n");
    sb.append("        transparency = ").append(transparency).append(";\n");
    sb.append("        imagePath = ").append(getImagePath()).append(";\n");
    sb.append("        uOffset = ").append(uOffset).append(";\n");
    sb.append("        vOffset = ").append(vOffset).append(";\n");
    sb.append("        uScale = ").append(uScale).append(";\n");
    sb.append("        vScale = ").append(vScale).append(";\n");
    sb.append("    }");
    return sb.toString();
  }
}
