package net.murat.material;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.Typeface;
import net.murat.light.*;
import net.murat.math.*;
import net.murat.util.ColorUtil;

/**
 * PhongTextMaterial - Combines text/image rendering with Phong lighting. Android adaptation: Uses
 * Bitmap, Canvas, and Paint for texture generation.
 */
public class PhongTextMaterial implements Material {

  // --- TEXTURE PROPERTIES ---
  private final String word;
  private final int textColorPacked;
  private final int gradientColorPacked;
  private final String gradientType;
  private final int bgColorPacked;
  private final String fontFamily;
  private final int fontStyle;
  private final int fontSize;
  private final int uOffset;
  private final int vOffset;
  private final Bitmap imageObject;
  private final int imageWidth;
  private final int imageHeight;
  private final int imageUOffset;
  private final int imageVOffset;

  private Bitmap texture;
  private int[] texturePixels;
  private int texWidth;
  private int texHeight;

  // --- PHONG PROPERTIES ---
  private int diffuseColorPacked;
  private final int specularColorPacked;
  private final double shininess;
  private final double ambientCoefficient;
  private final double diffuseCoefficient;
  private final double specularCoefficient;
  private final double reflectivity;
  private final double ior;
  private final double transparency;
  private Matrix4 objectTransform;
  private Matrix4 inverseTransform;
  private Matrix4 inverseTransposeTransform;

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

  public PhongTextMaterial(
      String word,
      int textColor,
      int gradientColor,
      String gradientType,
      int bgColor,
      String fontFamily,
      int fontStyle,
      int fontSize,
      int uOffset,
      int vOffset,
      Bitmap imageObject,
      int imageWidth,
      int imageHeight,
      int imageUOffset,
      int imageVOffset,
      int diffuseColor,
      int specularColor,
      double shininess,
      double ambientCoefficient,
      double diffuseCoefficient,
      double specularCoefficient,
      double reflectivity,
      double ior,
      double transparency) {

    this.word = convertToNorwegianText(word).replaceAll("_", " ");
    this.textColorPacked = textColor;
    this.gradientColorPacked = gradientColor;
    this.gradientType = gradientType != null ? gradientType : "horizontal";
    this.bgColorPacked = bgColor;
    this.fontFamily = fontFamily != null ? fontFamily.replaceAll("_", " ") : "Arial";
    this.fontStyle = fontStyle;
    this.fontSize = fontSize;
    this.uOffset = uOffset;
    this.vOffset = vOffset;
    this.imageObject = imageObject;
    this.imageWidth = imageWidth;
    this.imageHeight = imageHeight;
    this.imageUOffset = imageUOffset;
    this.imageVOffset = imageVOffset;

    this.diffuseColorPacked = diffuseColor;
    this.specularColorPacked = specularColor;
    this.shininess = shininess;
    this.ambientCoefficient = clamp01(ambientCoefficient);
    this.diffuseCoefficient = clamp01(diffuseCoefficient);
    this.specularCoefficient = clamp01(specularCoefficient);
    this.reflectivity = clamp01(reflectivity);
    this.ior = Math.max(1.0, ior);
    this.transparency = clamp01(transparency);

    this.specR = (specularColor >> 16) & 0xFF;
    this.specG = (specularColor >> 8) & 0xFF;
    this.specB = specularColor & 0xFF;

    this.objectTransform = Matrix4.identity();
    this.inverseTransform = Matrix4.identity();
    this.inverseTransposeTransform = Matrix4.identity();

    this.texture = createTexture();
    if (this.texture != null) {
      this.texWidth = this.texture.getWidth();
      this.texHeight = this.texture.getHeight();
      this.texturePixels = new int[texWidth * texHeight];
      this.texture.getPixels(this.texturePixels, 0, texWidth, 0, 0, texWidth, texHeight);
    }
  }

  public PhongTextMaterial(
      String word, int textColor, String fontFamily, int fontStyle, int fontSize) {
    this(
        word,
        textColor,
        0x00000000,
        "horizontal",
        0x00000000,
        fontFamily,
        fontStyle,
        fontSize,
        0,
        0,
        null,
        0,
        0,
        0,
        0,
        0xFFE5E5E5,
        0xFFFFFFFF,
        32.0,
        0.1,
        0.7,
        0.7,
        0.0,
        1.0,
        0.0);
  }

  private Bitmap createTexture() {
    final int size = 1024;
    Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(bmp);

    canvas.drawColor(Color.TRANSPARENT);

    int bgA = (bgColorPacked >> 24) & 0xFF;
    if (bgA > 0) {
      canvas.drawColor(bgColorPacked);
    }

    if (imageObject != null) {
      int imgX = ((size - imageWidth) / 2) + imageUOffset;
      int imgY = ((size - imageHeight) / 2) + imageVOffset;
      Rect dstRect = new Rect(imgX, imgY, imgX + imageWidth, imgY + imageHeight);
      Paint imgPaint = new Paint();
      imgPaint.setFilterBitmap(true);
      canvas.drawBitmap(imageObject, null, dstRect, imgPaint);
    }

    Paint textPaint = new Paint();
    textPaint.setAntiAlias(true);

    int tfStyle = Typeface.NORMAL;
    if ((fontStyle & 1) != 0) tfStyle |= Typeface.BOLD;
    if ((fontStyle & 2) != 0) tfStyle |= Typeface.ITALIC;

    Typeface tf;
    try {
	  tf = Typeface.create(OurFontFactory.getFont(fontFamily), tfStyle);
      //tf = Typeface.create(fontFamily, tfStyle);
    } catch (Exception e) {
      tf = Typeface.DEFAULT;
    }
    textPaint.setTypeface(tf);
    textPaint.setTextSize(fontSize);

    float textWidth = textPaint.measureText(word);
    Paint.FontMetrics fm = textPaint.getFontMetrics();
    float textHeight = fm.descent - fm.ascent;

    float x = ((size - textWidth) / 2f) + uOffset;
    float y = ((size - textHeight) / 2f) + (fm.ascent * 2f) + (textHeight / 3f) + vOffset;

    if (gradientColorPacked != 0x00000000 && gradientColorPacked != 0) {
      int[] colors = new int[] {textColorPacked, gradientColorPacked};
      float[] positions = new float[] {0f, 1f};
      Shader shader =
          new LinearGradient(
              x, y - fm.ascent, x + textWidth / 3f, y, colors, positions, Shader.TileMode.CLAMP);
      textPaint.setShader(shader);
    } else {
      textPaint.setColor(textColorPacked);
    }

    canvas.drawText(word, x, y, textPaint);

    return bmp;
  }

  public static String convertToNorwegianText(String input) {
    if (input == null || input.isEmpty()) return input;
    String result = input;
    result = result.replace("AE", "\u00C6");
    result = result.replace("O/", "\u00D8");
    result = result.replace("A0", "\u00C5");
    result = result.replace("ae", "\u00E6");
    result = result.replace("o/", "\u00F8");
    result = result.replace("a0", "\u00E5");
    return result;
  }

  @Override
  public void setObjectTransform(Matrix4 tm) {
    this.objectTransform = (tm != null) ? tm : Matrix4.identity();
    this.inverseTransform = this.objectTransform.inverse();
    this.inverseTransposeTransform = this.inverseTransform.inverseTransposeForNormal();
  }

  @Override
  public int getColorAt(Point3 point, Vector3 normal, Light light, Point3 viewerPos) {
    inverseTransform.transformPoint(point, tempLocalPoint);

    inverseTransposeTransform.transformDirection(normal, tempTransformedNormal);
    tempLocalNormal.set(tempTransformedNormal);
    tempLocalNormal.normalize();

    int textureColor = getTextureColor(tempLocalPoint, tempLocalNormal);
    int alpha = (textureColor >> 24) & 0xFF;

    int diffuseR, diffuseG, diffuseB;
    if (alpha < 10) {
      diffuseR = (diffuseColorPacked >> 16) & 0xFF;
      diffuseG = (diffuseColorPacked >> 8) & 0xFF;
      diffuseB = diffuseColorPacked & 0xFF;
    } else {
      diffuseR = (textureColor >> 16) & 0xFF;
      diffuseG = (textureColor >> 8) & 0xFF;
      diffuseB = textureColor & 0xFF;
    }

    LightProperties.getLightProperties(light, point, tempLightProps);
    tempLightDir.set(tempLightProps.direction);

    int lightColor = tempLightProps.color;
    int lightR = (lightColor >> 16) & 0xFF;
    int lightG = (lightColor >> 8) & 0xFF;
    int lightB = lightColor & 0xFF;
    double intensity = tempLightProps.intensity;

    int ambR = (int) (diffuseR * ambientCoefficient * lightR / 255.0 * intensity);
    int ambG = (int) (diffuseG * ambientCoefficient * lightG / 255.0 * intensity);
    int ambB = (int) (diffuseB * ambientCoefficient * lightB / 255.0 * intensity);

    if (light instanceof MuratAmbientLight) {
      ambR = clamp(ambR);
      ambG = clamp(ambG);
      ambB = clamp(ambB);
      return (0xFF000000 | (ambR << 16) | (ambG << 8) | ambB);
    }

    tempViewerPoint.set(viewerPos);
    tempViewDir.set(tempViewerPoint);
    tempViewDir.subtract(point);
    tempViewDir.normalize();

    double NdotL = Math.max(0, tempLocalNormal.dot(tempLightDir));
    double diffFactor = diffuseCoefficient * NdotL * intensity;
    int diffR2 = (int) (diffuseR * diffFactor * lightR / 255.0);
    int diffG2 = (int) (diffuseG * diffFactor * lightG / 255.0);
    int diffB2 = (int) (diffuseB * diffFactor * lightB / 255.0);

    tempReflectDir.set(tempLightDir);
    tempReflectDir.negate();
    tempReflectDir.reflect(tempLocalNormal, tempReflectDir);

    double RdotV = Math.max(0, tempReflectDir.dot(tempViewDir));
    double specFactor = Math.pow(RdotV, shininess) * specularCoefficient * intensity;
    int sR = (int) (specR * specFactor * lightR / 255.0);
    int sG = (int) (specG * specFactor * lightG / 255.0);
    int sB = (int) (specB * specFactor * lightB / 255.0);

    int r = ambR + diffR2 + sR;
    int g = ambG + diffG2 + sG;
    int b = ambB + diffB2 + sB;

    r = clamp(r);
    g = clamp(g);
    b = clamp(b);

    return (0xFF000000 | (r << 16) | (g << 8) | b);
  }

  private int getTextureColor(Point3 localPoint, Vector3 localNormal) {
    if (texturePixels == null) {
      return diffuseColorPacked;
    }

    tempLocalNormal.set(localNormal);
    tempLocalNormal.normalize();

    double phi = Math.atan2(tempLocalNormal.z, tempLocalNormal.x);
    double theta = Math.asin(tempLocalNormal.y);

    double u = 1.0 - (phi + Math.PI) / (2 * Math.PI);
    double v = (theta + Math.PI / 2) / Math.PI;
    v = 1.0 - v;
    u = (u + 0.25) % 1.0;

    int texX = (int) (u * texWidth);
    texX = texX % texWidth;
    if (texX < 0) texX += texWidth;

    int texY = (int) (v * texHeight);
    if (texY < 0 || texY >= texHeight) {
      return diffuseColorPacked;
    }

    int color = texturePixels[texY * texWidth + texX];
    int alpha = (color >> 24) & 0xFF;

    if (alpha < 10) {
      return diffuseColorPacked;
    }

    return color;
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

  public int getDiffuseColorPacked() {
    return diffuseColorPacked;
  }

  public void setDiffuseColorPacked(int color) {
    this.diffuseColorPacked = color;
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

  public void setImagePath(String npath) {
    this.imagePath = npath;
  }

  public String getImagePath() {
    return this.imagePath;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("PhongTextMaterial phongtextmaterial {\n");
    sb.append("        imagePath = ").append(getImagePath()).append(";\n");
    sb.append("        word = ").append(word).append(";\n");
    sb.append("        textColor = ")
        .append(ColorUtil.toColorString(textColorPacked))
        .append(";\n");
    sb.append("        gradientColor = ")
        .append(ColorUtil.toColorString(gradientColorPacked))
        .append(";\n");
    sb.append("        gradientType = ").append(gradientType).append(";\n");
    sb.append("        bgColor = ").append(ColorUtil.toColorString(bgColorPacked)).append(";\n");
    sb.append("        fontFamily = ").append(fontFamily).append(";\n");
    sb.append("        fontStyle = ").append(fontStyle).append(";\n");
    sb.append("        fontSize = ").append(fontSize).append(";\n");
    sb.append("        uOffset = ").append(uOffset).append(";\n");
    sb.append("        vOffset = ").append(vOffset).append(";\n");
    sb.append("        imageWidth = ").append(imageWidth).append(";\n");
    sb.append("        imageHeight = ").append(imageHeight).append(";\n");
    sb.append("        imageUOffset = ").append(imageUOffset).append(";\n");
    sb.append("        imageVOffset = ").append(imageVOffset).append(";\n");
    sb.append("        diffuseColor = ")
        .append(ColorUtil.toColorString(diffuseColorPacked))
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
    sb.append("    }");
    return sb.toString();
  }
}
