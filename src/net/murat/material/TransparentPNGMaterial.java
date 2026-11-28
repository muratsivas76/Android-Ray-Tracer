package net.murat.material;

import android.graphics.Bitmap;
import net.murat.light.Light;
import net.murat.light.LightProperties;
import net.murat.light.MuratAmbientLight;
import net.murat.math.Matrix4;
import net.murat.math.Point3;
import net.murat.math.Vector3;

/**
 * TransparentPNGMaterial - Textures a surface with a transparent PNG image. Android adaptation:
 * Uses Bitmap + int[] for fast, GC-free sampling.
 */
public class TransparentPNGMaterial implements Material {
  private Bitmap texture;
  private int[] texturePixels;
  private int texWidth;
  private int texHeight;

  private Matrix4 objectInverseTransform = new Matrix4();
  private double transparency = 1.0;

  private double uOffset = 0.0;
  private double vOffset = 0.0;
  private double uScale = 1.0;
  private double vScale = 1.0;
  private boolean isRepeatTexture = false;
  private double billboardWidth = 1.0;
  private double billboardHeight = 1.0;

  // ===== GC FRIENDLY: PRE-ALLOCATED TEMP OBJECTS =====
  private final Point3 tempLocalPoint = new Point3();
  private final Vector3 tempLightDir = new Vector3();
  private final LightProperties tempLightProps = new LightProperties();

  private String imagePath = "textures/turkeyFlag.png";

  public TransparentPNGMaterial(Bitmap texture) {
    setTexture(texture);
  }

  public TransparentPNGMaterial(
      Bitmap texture,
      double uOffset,
      double vOffset,
      double uScale,
      double vScale,
      boolean isRepeatTexture) {
    setTexture(texture);
    this.uOffset = uOffset;
    this.vOffset = vOffset;
    this.uScale = (uScale > 0.0) ? uScale : 1.0;
    this.vScale = (vScale > 0.0) ? vScale : 1.0;
    this.isRepeatTexture = isRepeatTexture;
  }

  public TransparentPNGMaterial() {
    this.texture = null;
    this.texturePixels = null;
  }

  public void setTexture(Bitmap texture) {
    this.texture = texture;
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
  }

  @Override
  public void setObjectTransform(Matrix4 inverseTransform) {
    if (inverseTransform != null) {
      this.objectInverseTransform = inverseTransform;
    } else {
      this.objectInverseTransform = new Matrix4();
    }
  }

  @Override
  public int getColorAt(Point3 point, Vector3 normal, Light light, Point3 viewerPos) {
    if (texturePixels == null) {
      transparency = 1.0;
      return 0x00000000;
    }

    objectInverseTransform.transformPoint(point, tempLocalPoint);

    double u = (tempLocalPoint.x / billboardWidth) + 0.5;
    double v = (tempLocalPoint.y / billboardHeight) + 0.5;
    v = 1.0 - v;

    double scaledU = u / uScale + uOffset;
    double scaledV = v / vScale + vOffset;

    double finalU, finalV;
    if (isRepeatTexture) {
      finalU = scaledU - Math.floor(scaledU);
      finalV = scaledV - Math.floor(scaledV);
    } else {
      if (scaledU < 0.0 || scaledU > 1.0 || scaledV < 0.0 || scaledV > 1.0) {
        transparency = 1.0;
        return 0x00000000;
      }
      finalU = scaledU;
      finalV = scaledV;
    }

    int px = (int) (finalU * (texWidth - 1));
    int py = (int) (finalV * (texHeight - 1));

    px = Math.max(0, Math.min(texWidth - 1, px));
    py = Math.max(0, Math.min(texHeight - 1, py));

    // Fast array lookup
    int argb = texturePixels[py * texWidth + px];
    int alpha = (argb >> 24) & 0xFF;
    int baseR = (argb >> 16) & 0xFF;
    int baseG = (argb >> 8) & 0xFF;
    int baseB = argb & 0xFF;

    if (alpha < 5) {
      transparency = 1.0;
      return 0x00000000;
    }

    LightProperties.getLightProperties(light, point, tempLightProps);
    tempLightDir.set(tempLightProps.direction);

    int lightColor = tempLightProps.color;
    int lightR = (lightColor >> 16) & 0xFF;
    int lightG = (lightColor >> 8) & 0xFF;
    int lightB = lightColor & 0xFF;
    double intensity = tempLightProps.intensity;

    int r, g, b;
    if (light instanceof MuratAmbientLight) {
      r = (int) (baseR * intensity * lightR / 255.0);
      g = (int) (baseG * intensity * lightG / 255.0);
      b = (int) (baseB * intensity * lightB / 255.0);
    } else {
      double NdotL = Math.max(0.0, normal.dot(tempLightDir));
      double factor = NdotL * intensity;
      r = (int) (baseR * factor * lightR / 255.0);
      g = (int) (baseG * factor * lightG / 255.0);
      b = (int) (baseB * factor * lightB / 255.0);
    }

    r = clamp(r);
    g = clamp(g);
    b = clamp(b);

    transparency = 0.0;

    return (0xFF000000 | (r << 16) | (g << 8) | b);
  }

  private int clamp(int value) {
    return Math.max(0, Math.min(255, value));
  }

  @Override
  public double getReflectivity() {
    return 0.0;
  }

  @Override
  public double getIndexOfRefraction() {
    return 1.0;
  }

  @Override
  public double getTransparency() {
    return transparency;
  }

  public double getUOffset() {
    return uOffset;
  }

  public void setUOffset(double uOffset) {
    this.uOffset = uOffset;
  }

  public double getVOffset() {
    return vOffset;
  }

  public void setVOffset(double vOffset) {
    this.vOffset = vOffset;
  }

  public double getUScale() {
    return uScale;
  }

  public void setUScale(double uScale) {
    this.uScale = (uScale > 0.0) ? uScale : 1.0;
  }

  public double getVScale() {
    return vScale;
  }

  public void setVScale(double vScale) {
    this.vScale = (vScale > 0.0) ? vScale : 1.0;
  }

  public boolean isRepeatTexture() {
    return isRepeatTexture;
  }

  public void setRepeatTexture(boolean repeat) {
    this.isRepeatTexture = repeat;
  }

  public Bitmap getTexture() {
    return texture;
  }

  public double getBillboardWidth() {
    return billboardWidth;
  }

  public void setBillboardWidth(double billboardWidth) {
    this.billboardWidth = billboardWidth;
  }

  public double getBillboardHeight() {
    return billboardHeight;
  }

  public void setBillboardHeight(double billboardHeight) {
    this.billboardHeight = billboardHeight;
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
    sb.append("TransparentPNGMaterial transparentpngmaterial {\n");
    sb.append("        imagePath = ").append(getImagePath()).append(";\n");
    sb.append("        uOffset = ").append(uOffset).append(";\n");
    sb.append("        vOffset = ").append(vOffset).append(";\n");
    sb.append("        uScale = ").append(uScale).append(";\n");
    sb.append("        vScale = ").append(vScale).append(";\n");
    sb.append("        isRepeatTexture = ").append(isRepeatTexture).append(";\n");
    sb.append("        billboardWidth = ").append(billboardWidth).append(";\n");
    sb.append("        billboardHeight = ").append(billboardHeight).append(";\n");
    sb.append("    }");
    return sb.toString();
  }
}
