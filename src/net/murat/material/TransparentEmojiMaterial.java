package net.murat.material;

import android.graphics.Bitmap;
import net.murat.light.Light;
import net.murat.light.LightProperties;
import net.murat.math.Matrix4;
import net.murat.math.Point3;
import net.murat.math.Vector3;
import net.murat.util.ColorUtil;

/**
 * TransparentEmojiMaterial - Textured emoji over a procedural checkerboard background. Android
 * adaptation: Uses Bitmap + int[] for fast, GC-free sampling.
 */
public class TransparentEmojiMaterial implements Material {
  private final Bitmap image;
  private final int[] imagePixels;
  private final int imgWidth;
  private final int imgHeight;

  private double transparency;
  private final int checkerColor1Packed;
  private final int checkerColor2Packed;
  private final double checkerSize;
  private final double uOffset;
  private final double vOffset;
  private final double uScale;
  private final double vScale;
  private final boolean isRepeatTexture;
  private final boolean isMessy;

  private Matrix4 objectInverseTransform;
  private double billboardWidth = 1.0;
  private double billboardHeight = 1.0;

  private final int checker1R, checker1G, checker1B, checker1A;
  private final int checker2R, checker2G, checker2B, checker2A;

  private static final int OPAQUE_THRESHOLD = 200;
  private static final int CHECKER_SUPERSAMPLE = 4;
  private static final double CHECKER_AA_FOOTPRINT_COEFF = 0.02;

  private final Point3 tempLocalPoint = new Point3();
  private final Vector3 tempLightDir = new Vector3();
  private final LightProperties tempLightProps = new LightProperties();

  private String imagePath = "textures/turkeyFlag.png";

  public TransparentEmojiMaterial(
      Bitmap image,
      int checkerColor1,
      int checkerColor2,
      double checkerSize,
      double uOffset,
      double vOffset,
      double uScale,
      double vScale,
      boolean isRepeatTexture,
      boolean isMessy) {

    this.image = image;
    if (image != null) {
      this.imgWidth = image.getWidth();
      this.imgHeight = image.getHeight();
      this.imagePixels = new int[imgWidth * imgHeight];
      image.getPixels(this.imagePixels, 0, imgWidth, 0, 0, imgWidth, imgHeight);
    } else {
      this.imgWidth = 0;
      this.imgHeight = 0;
      this.imagePixels = null;
    }

    this.checkerColor1Packed = checkerColor1;
    this.checkerColor2Packed = checkerColor2;
    this.checkerSize = Math.max(0.01, checkerSize);
    this.uOffset = uOffset;
    this.vOffset = vOffset;
    this.uScale = Math.max(0.01, uScale);
    this.vScale = Math.max(0.01, vScale);
    this.isRepeatTexture = isRepeatTexture;
    this.isMessy = isMessy;
    this.objectInverseTransform = new Matrix4();

    this.checker1R = (checkerColor1 >> 16) & 0xFF;
    this.checker1G = (checkerColor1 >> 8) & 0xFF;
    this.checker1B = checkerColor1 & 0xFF;
    this.checker1A = (checkerColor1 >> 24) & 0xFF;

    this.checker2R = (checkerColor2 >> 16) & 0xFF;
    this.checker2G = (checkerColor2 >> 8) & 0xFF;
    this.checker2B = checkerColor2 & 0xFF;
    this.checker2A = (checkerColor2 >> 24) & 0xFF;
  }

  public TransparentEmojiMaterial(Bitmap image, double checkerSize, double uScale, double vScale) {
    this(image, 0xFFC8C8C8, 0xFF969696, checkerSize, 0.0, 0.0, uScale, vScale, false, false);
  }

  public TransparentEmojiMaterial(Bitmap image, double uScale, double vScale) {
    this(image, 0.1, uScale, vScale);
  }

  public TransparentEmojiMaterial(Bitmap image) {
    this(image, 0.1, 1.0, 1.0);
  }

  public TransparentEmojiMaterial() {
    this(null, 0.1, 1.0, 1.0);
  }

  @Override
  public void setObjectTransform(Matrix4 tm) {
    if (tm == null) tm = new Matrix4();
    this.objectInverseTransform = tm;
  }

  private int getBackgroundCheckerboardColor(double u, double v) {
    int ix = (int) Math.floor(u / checkerSize);
    int iy = (int) Math.floor(v / checkerSize);
    return ((ix + iy) % 2 == 0) ? checkerColor1Packed : checkerColor2Packed;
  }

  private double getCheckerCoverage(double u, double v, double footprint) {
    double half = footprint * 0.5;
    double u0 = u - half;
    double u1 = u + half;
    double v0 = v - half;
    double v1 = v + half;

    int color1Count = 0;
    int total = CHECKER_SUPERSAMPLE * CHECKER_SUPERSAMPLE;

    for (int i = 0; i < CHECKER_SUPERSAMPLE; i++) {
      for (int j = 0; j < CHECKER_SUPERSAMPLE; j++) {
        double su = u0 + (u1 - u0) * (i + 0.5) / CHECKER_SUPERSAMPLE;
        double sv = v0 + (v1 - v0) * (j + 0.5) / CHECKER_SUPERSAMPLE;
        int ix = (int) Math.floor(su / checkerSize);
        int iy = (int) Math.floor(sv / checkerSize);
        if ((ix + iy) % 2 == 0) color1Count++;
      }
    }

    return color1Count / (double) total;
  }

  @Override
  public int getColorAt(Point3 point, Vector3 normal, Light light, Point3 viewerPos) {
    objectInverseTransform.transformPoint(point, tempLocalPoint);

    double u_bg = (tempLocalPoint.x / billboardWidth) + 0.5;
    double v_bg = (tempLocalPoint.y / billboardHeight) + 0.5;
    v_bg = 1.0 - v_bg;

    double footprint = checkerSize * CHECKER_AA_FOOTPRINT_COEFF;
    double coverage = getCheckerCoverage(u_bg, v_bg, footprint);

    int bgR = (int) (checker1R * coverage + checker2R * (1.0 - coverage));
    int bgG = (int) (checker1G * coverage + checker2G * (1.0 - coverage));
    int bgB = (int) (checker1B * coverage + checker2B * (1.0 - coverage));
    int bgA = isMessy ? (int) (checker1A * coverage + checker2A * (1.0 - coverage)) : 255;

    int baseR = bgR;
    int baseG = bgG;
    int baseB = bgB;
    int outA = bgA;

    if (imagePixels != null) {
      double scaledU = u_bg / uScale + uOffset;
      double scaledV = v_bg / vScale + vOffset;

      double finalU, finalV;
      boolean withinTexture = true;
      if (isRepeatTexture) {
        finalU = scaledU - Math.floor(scaledU);
        finalV = scaledV - Math.floor(scaledV);
      } else {
        if (scaledU < 0.0 || scaledU > 1.0 || scaledV < 0.0 || scaledV > 1.0) {
          withinTexture = false;
          finalU = 0.0;
          finalV = 0.0;
        } else {
          finalU = scaledU;
          finalV = scaledV;
        }
      }

      if (withinTexture) {
        int x = (int) (finalU * (imgWidth - 1));
        int y = (int) (finalV * (imgHeight - 1));

        x = Math.max(0, Math.min(imgWidth - 1, x));
        y = Math.max(0, Math.min(imgHeight - 1, y));

        // Fast array lookup
        int argb = imagePixels[y * imgWidth + x];
        int alpha = (argb >> 24) & 0xFF;

        if (alpha >= OPAQUE_THRESHOLD) {
          baseR = (argb >> 16) & 0xFF;
          baseG = (argb >> 8) & 0xFF;
          baseB = argb & 0xFF;
          outA = bgA;
        } else if (alpha <= 5) {
          baseR = bgR;
          baseG = bgG;
          baseB = bgB;
          outA = bgA;
        } else {
          int imgR = (argb >> 16) & 0xFF;
          int imgG = (argb >> 8) & 0xFF;
          int imgB = argb & 0xFF;

          float alphaF = alpha / 255f;
          float invAlpha = 1.0f - alphaF;

          baseR = (int) (imgR * alphaF + bgR * invAlpha);
          baseG = (int) (imgG * alphaF + bgG * invAlpha);
          baseB = (int) (imgB * alphaF + bgB * invAlpha);

          float bgAf = bgA / 255f;
          outA = clamp((int) ((alphaF + bgAf * invAlpha) * 255f));
        }
      }
    }

    transparency = 1.0 - (outA / 255.0);

    LightProperties.getLightProperties(light, point, tempLightProps);
    tempLightDir.set(tempLightProps.direction);

    int lightColor = tempLightProps.color;
    int lightR = (lightColor >> 16) & 0xFF;
    int lightG = (lightColor >> 8) & 0xFF;
    int lightB = lightColor & 0xFF;
    double intensity = tempLightProps.intensity;

    int r = (int) (baseR * intensity * lightR / 255.0);
    int g = (int) (baseG * intensity * lightG / 255.0);
    int b = (int) (baseB * intensity * lightB / 255.0);

    r = clamp(r);
    g = clamp(g);
    b = clamp(b);

    return (outA << 24) | (r << 16) | (g << 8) | b;
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

  public boolean isItRepeatTexture() {
    return true;
  }

  public void setImagePath(String npath) {
    this.imagePath = npath;
  }

  public String getImagePath() {
    return this.imagePath;
  }

  public Bitmap getImage() {
    return image;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("TransparentEmojiMaterial transparentemojimaterial {\n");
    sb.append("        imagePath = ").append(getImagePath()).append(";\n");
    sb.append("        checkerColor1 = ")
        .append(ColorUtil.toColorString(checkerColor1Packed))
        .append(";\n");
    sb.append("        checkerColor2 = ")
        .append(ColorUtil.toColorString(checkerColor2Packed))
        .append(";\n");
    sb.append("        checkerSize = ").append(checkerSize).append(";\n");
    sb.append("        uOffset = ").append(uOffset).append(";\n");
    sb.append("        vOffset = ").append(vOffset).append(";\n");
    sb.append("        uScale = ").append(uScale).append(";\n");
    sb.append("        vScale = ").append(vScale).append(";\n");
    sb.append("        isRepeatTexture = ").append(isRepeatTexture).append(";\n");
    sb.append("        isMessy = ").append(isMessy).append(";\n");
    sb.append("        billboardWidth = ").append(billboardWidth).append(";\n");
    sb.append("        billboardHeight = ").append(billboardHeight).append(";\n");
    sb.append("    }");
    return sb.toString();
  }
}
