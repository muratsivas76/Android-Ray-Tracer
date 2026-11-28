package net.murat.material;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.Typeface;
import net.murat.light.Light;
import net.murat.light.LightProperties;
import net.murat.light.MuratAmbientLight;
import net.murat.math.Matrix4;
import net.murat.math.Point3;
import net.murat.math.Vector3;
import net.murat.util.ColorUtil;

/**
 * Material class that generates textures with rendered text and optional image on the fly. Android
 * adaptation: Uses Bitmap, Canvas, and Paint for texture generation.
 */
public class WordMaterial implements Material {
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
  private boolean isTriangleEtc = false;
  private double billboardWidth = 1.0;
  private double billboardHeight = 1.0;

  private String text;
  private int foregroundColor; // packed ARGB
  private int backgroundColor; // packed ARGB
  private Typeface typeface;
  private int fontSize;
  private int fontStyle;
  private String fontFamily;
  private boolean gradientEnabled;
  private int gradientColor; // packed ARGB
  private Bitmap wordImage;
  private int width;
  private int height;

  // ===== GC FRIENDLY: PRE-ALLOCATED TEMP OBJECTS =====
  private final Point3 tempLocalPoint = new Point3();
  private final LightProperties tempLightProps = new LightProperties();

  private String imagePath = "null";

  // ============================================================
  // CONSTRUCTORS
  // ============================================================

  public WordMaterial(String text) {
    this(
        text,
        Color.WHITE,
        Color.TRANSPARENT,
        "Arial",
        Typeface.BOLD,
        48,
        false,
        0x00000000,
        null,
        256,
        256);
  }

  public WordMaterial(
      String text,
      int foregroundColor,
      int backgroundColor,
      String fontFamily,
      int fontStyle,
      int fontSize) {
    this(
        text,
        foregroundColor,
        backgroundColor,
        fontFamily,
        fontStyle,
        fontSize,
        false,
        0x00000000,
        null,
        256,
        256);
  }

  public WordMaterial(
      String text,
      int foregroundColor,
      int backgroundColor,
      String fontFamily,
      int fontStyle,
      int fontSize,
      int gradientColor) {
    this(
        text,
        foregroundColor,
        backgroundColor,
        fontFamily,
        fontStyle,
        fontSize,
        true,
        gradientColor,
        null,
        256,
        256);
  }

  public WordMaterial(
      String text,
      int foregroundColor,
      int backgroundColor,
      String fontFamily,
      int fontStyle,
      int fontSize,
      Bitmap wordImage) {
    this(
        text,
        foregroundColor,
        backgroundColor,
        fontFamily,
        fontStyle,
        fontSize,
        false,
        0x00000000,
        wordImage,
        wordImage != null ? 384 : 256,
        wordImage != null ? 384 : 256);
  }

  public WordMaterial(
      String text,
      int foregroundColor,
      int backgroundColor,
      String fontFamily,
      int fontStyle,
      int fontSize,
      int width,
      int height) {
    this(
        text,
        foregroundColor,
        backgroundColor,
        fontFamily,
        fontStyle,
        fontSize,
        false,
        0x00000000,
        null,
        width,
        height);
  }

  public WordMaterial(
      String sentence,
      int textColor,
      String fontFamily,
      int fontStyle,
      int fontSize,
      int maxWidth,
      double uOffset,
      double vOffset) {
    this.text = sentence;
    this.foregroundColor = textColor;
    this.backgroundColor = Color.TRANSPARENT;
    this.fontFamily = fontFamily;
    this.fontStyle = fontStyle;
    this.fontSize = fontSize;
    this.uOffset = uOffset;
    this.vOffset = vOffset;

    Typeface xfft = Typeface.create(OurFontFactory.getFont(fontFamily), fontStyle);

    Paint measurePaint = new Paint();
    measurePaint.setTypeface(xfft);
    measurePaint.setTextSize(fontSize);
    
    float textWidth = measurePaint.measureText(sentence);
    Paint.FontMetrics fm = measurePaint.getFontMetrics();
    float textHeight = fm.descent - fm.ascent;

    this.width = Math.min((int) textWidth + 40, maxWidth);
    this.height = (int) textHeight + 20;

    this.texture =
        createTextImage(
            sentence,
            textColor,
            backgroundColor,
            fontFamily,
            fontStyle,
            fontSize,
            false,
            0x00000000,
            null,
            width,
            height);
    cachePixels();
  }

  public WordMaterial(
      String[] lines,
      int textColor,
      String fontFamily,
      int fontStyle,
      int fontSize,
      int maxWidth,
      double uOffset,
      double vOffset) {
    this.text = String.join(" ", lines);
    this.foregroundColor = textColor;
    this.backgroundColor = Color.TRANSPARENT;
    this.fontFamily = fontFamily;
    this.fontStyle = fontStyle;
    this.fontSize = fontSize;
    this.uOffset = uOffset;
    this.vOffset = vOffset;
    
    Typeface xfft = Typeface.create(OurFontFactory.getFont(fontFamily), fontStyle);

    Paint measurePaint = new Paint();
    measurePaint.setTypeface(xfft);
    measurePaint.setTextSize(fontSize);

    float maxLineWidth = 0;
    for (String line : lines) {
      maxLineWidth = Math.max(maxLineWidth, measurePaint.measureText(line));
    }
    Paint.FontMetrics fm = measurePaint.getFontMetrics();
    float lineHeight = fm.descent - fm.ascent;

    this.width = Math.min((int) maxLineWidth + 40, maxWidth);
    this.height = (int) (lineHeight * lines.length) + 20;

    this.texture =
        createMultiLineTextImage(
            lines, textColor, backgroundColor, fontFamily, fontStyle, fontSize, width, height);
    cachePixels();
  }

  public WordMaterial(
      String text,
      int foregroundColor,
      int backgroundColor,
      String fontFamily,
      int fontStyle,
      int fontSize,
      boolean useGradient,
      int gradientColor,
      Bitmap wordImage,
      int width,
      int height,
      double uOffset,
      double vOffset) {
    this.text = text;
    this.foregroundColor = foregroundColor;
    this.backgroundColor = backgroundColor;
    this.fontFamily = fontFamily;
    this.fontStyle = fontStyle;
    this.fontSize = fontSize;
    this.gradientEnabled = useGradient;
    this.gradientColor = gradientColor;
    this.wordImage = wordImage;
    this.width = width;
    this.height = height;
    this.uOffset = uOffset;
    this.vOffset = vOffset;

    this.texture =
        createTextImage(
            text,
            foregroundColor,
            backgroundColor,
            fontFamily,
            fontStyle,
            fontSize,
            useGradient,
            gradientColor,
            wordImage,
            width,
            height);
    cachePixels();
  }

  public WordMaterial(
      String text,
      int foregroundColor,
      int backgroundColor,
      String fontFamily,
      int fontStyle,
      int fontSize,
      boolean useGradient,
      int gradientColor,
      Bitmap wordImage,
      int width,
      int height) {
    this(
        text,
        foregroundColor,
        backgroundColor,
        fontFamily,
        fontStyle,
        fontSize,
        useGradient,
        gradientColor,
        wordImage,
        width,
        height,
        0.0,
        0.0);
  }

  private void cachePixels() {
    if (texture != null) {
      this.texWidth = texture.getWidth();
      this.texHeight = texture.getHeight();
      this.texturePixels = new int[texWidth * texHeight];
      texture.getPixels(this.texturePixels, 0, texWidth, 0, 0, texWidth, texHeight);
    }
  }

  // ============================================================
  // STATIC HELPER METHODS
  // ============================================================

  private static Bitmap createTextImage(
      String text,
      int fgColor,
      int bgColor,
      String fontFamily,
      int fontStyle,
      int fontSize,
      boolean useGradient,
      int gradientColor,
      Bitmap wordImage,
      int width,
      int height) {

    Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(bmp);

    // 1. Clear transparent
    canvas.drawColor(Color.TRANSPARENT);

    // 2. Draw background color
    canvas.drawColor(bgColor);

    // 3. Draw word image if provided
    if (wordImage != null) {
      int imageSize = Math.min(width, height) - 64;
      int imageX = (width - imageSize) / 2;
      int imageY = 32;

      Rect dstRect = new Rect(imageX, imageY, imageX + imageSize, imageY + imageSize);
      Paint imgPaint = new Paint();
      imgPaint.setFilterBitmap(true);
      canvas.drawBitmap(wordImage, null, dstRect, imgPaint);
    }

    // 4. Set up text paint
    Paint textPaint = new Paint();
    textPaint.setAntiAlias(true);

    int tfStyle = Typeface.NORMAL;
    if ((fontStyle & 1) != 0) tfStyle |= Typeface.BOLD;
    if ((fontStyle & 2) != 0) tfStyle |= Typeface.ITALIC;

    //Typeface tf = Typeface.create(fontFamily, tfStyle);
    Typeface tf = Typeface.create(OurFontFactory.getFont(fontFamily), tfStyle);
    textPaint.setTypeface(tf);
    textPaint.setTextSize(fontSize);

    // 5. Gradient or solid color
    if (useGradient && gradientColor != 0x00000000) {
      int textY = (wordImage != null) ? (height * 2 / 3) : (height / 2);
      Shader shader =
          new LinearGradient(
              0,
              textY,
              width,
              textY + 50,
              new int[] {makeOpaque(fgColor), makeOpaque(gradientColor)},
              null,
              Shader.TileMode.CLAMP);
      textPaint.setShader(shader);
    } else {
      textPaint.setColor(makeOpaque(fgColor));
    }

    // 6. Draw text centered
    float textWidth = textPaint.measureText(text);
    Paint.FontMetrics fm = textPaint.getFontMetrics();
    float textX = (width - textWidth) / 2f;
    float textY;
    if (wordImage != null) {
      textY = height * 3f / 4f;
    } else {
      float textHeight = fm.descent - fm.ascent;
      textY = (height - textHeight) / 2f + fm.ascent;
    }

    canvas.drawText(text, textX, textY, textPaint);

    return bmp;
  }

  private static int makeOpaque(int color) {
    return (0xFF000000 | (color & 0x00FFFFFF));
  }

  private static Bitmap createMultiLineTextImage(
      String[] lines,
      int fgColor,
      int bgColor,
      String fontFamily,
      int fontStyle,
      int fontSize,
      int width,
      int height) {

    Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(bmp);

    canvas.drawColor(Color.TRANSPARENT);
    canvas.drawColor(bgColor);

    Paint textPaint = new Paint();
    textPaint.setAntiAlias(true);
    textPaint.setColor(fgColor);

    int tfStyle = Typeface.NORMAL;
    if ((fontStyle & 1) != 0) tfStyle |= Typeface.BOLD;
    if ((fontStyle & 2) != 0) tfStyle |= Typeface.ITALIC;
    
    Typeface xfft = Typeface.create(OurFontFactory.getFont(fontFamily), tfStyle);
    textPaint.setTypeface(xfft);
    textPaint.setTextSize(fontSize);

    Paint.FontMetrics fm = textPaint.getFontMetrics();
    float lineHeight = fm.descent - fm.ascent;
    float startY = (height - (lineHeight * lines.length)) / 2f + fm.ascent;

    for (int i = 0; i < lines.length; i++) {
      String line = lines[i];
      float textWidth = textPaint.measureText(line);
      float textX = (width - textWidth) / 2f;
      float textY = startY + (i * lineHeight);
      canvas.drawText(line, textX, textY, textPaint);
    }

    return bmp;
  }

  // ============================================================
  // MATERIAL INTERFACE
  // ============================================================

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
      return 0x00000000;
    }

    objectInverseTransform.transformPoint(point, tempLocalPoint);

    double u, v;
    if (isTriangleEtc) {
      u = tempLocalPoint.x + 0.5;
      v = tempLocalPoint.z + 0.5;
    } else {
      u = (tempLocalPoint.x / billboardWidth) + 0.5;
      v = (tempLocalPoint.y / billboardHeight) + 0.5;
      v = 1.0 - v;
    }

    double scaledU = u / uScale + uOffset;
    double scaledV = v / vScale + vOffset;

    double finalU, finalV;
    if (isRepeatTexture) {
      finalU = scaledU - Math.floor(scaledU);
      finalV = scaledV - Math.floor(scaledV);
    } else {
      if (scaledU < 0.0 || scaledU > 1.0 || scaledV < 0.0 || scaledV > 1.0) {
        return 0x00000000;
      }
      finalU = scaledU;
      finalV = scaledV;
    }

    int px = (int) (finalU * (texWidth - 1));
    int py = (int) (finalV * (texHeight - 1));

    px = Math.max(0, Math.min(px, texWidth - 1));
    py = Math.max(0, Math.min(py, texHeight - 1));

    int argb = texturePixels[py * texWidth + px];
    int alpha = (argb >> 24) & 0xFF;
    int baseR = (argb >> 16) & 0xFF;
    int baseG = (argb >> 8) & 0xFF;
    int baseB = argb & 0xFF;

    LightProperties.getLightProperties(light, point, tempLightProps);

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
      double NdotL = Math.max(0.0, normal.dot(tempLightProps.direction));
      double factor = NdotL * intensity;
      r = (int) (baseR * factor * lightR / 255.0);
      g = (int) (baseG * factor * lightG / 255.0);
      b = (int) (baseB * factor * lightB / 255.0);
    }

    r = Math.max(0, Math.min(255, r));
    g = Math.max(0, Math.min(255, g));
    b = Math.max(0, Math.min(255, b));

    this.transparency = 1.0 - (alpha / 255.0);

    return (alpha << 24) | (r << 16) | (g << 8) | b;
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

  // ============================================================
  // GETTERS & SETTERS
  // ============================================================

  public void setTriangleEtc(boolean nbool) {
    this.isTriangleEtc = nbool;
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

  public String getText() {
    return text;
  }

  public int getForegroundColor() {
    return foregroundColor;
  }

  public int getBackgroundColor() {
    return backgroundColor;
  }

  public String getFontFamily() {
    return fontFamily;
  }

  public int getFontStyle() {
    return fontStyle;
  }

  public int getFontSize() {
    return fontSize;
  }

  public boolean isGradientEnabled() {
    return gradientEnabled;
  }

  public int getGradientColor() {
    return gradientColor;
  }

  public Bitmap getWordImage() {
    return wordImage;
  }

  public int getWidth() {
    return width;
  }

  public void setWidth(int width) {
    this.width = width;
    regenerateTexture();
  }

  public int getHeight() {
    return height;
  }

  public void setHeight(int height) {
    this.height = height;
    regenerateTexture();
  }

  public void setSize(int width, int height) {
    this.width = width;
    this.height = height;
    regenerateTexture();
  }

  public void setText(String newText) {
    this.text = newText;
    regenerateTexture();
  }

  public void setColors(int newForeground, int newBackground) {
    this.foregroundColor = newForeground;
    this.backgroundColor = newBackground;
    regenerateTexture();
  }

  public void setFont(String newFontFamily, int newFontStyle, int newFontSize) {
    this.fontFamily = newFontFamily;
    this.fontStyle = newFontStyle;
    this.fontSize = newFontSize;
    regenerateTexture();
  }

  public void setGradient(boolean useGradient, int newGradientColor) {
    this.gradientEnabled = useGradient;
    this.gradientColor = newGradientColor;
    regenerateTexture();
  }

  public void setWordImage(Bitmap newWordImage) {
    this.wordImage = newWordImage;
    regenerateTexture();
  }

  public void regenerateTexture() {
    this.texture =
        createTextImage(
            text,
            foregroundColor,
            backgroundColor,
            fontFamily,
            fontStyle,
            fontSize,
            gradientEnabled,
            gradientColor,
            wordImage,
            width,
            height);
    cachePixels();
  }

  public void setImagePath(String npath) {
    this.imagePath = npath;
  }

  public String getImagePath() {
    return this.imagePath;
  }

  // ============================================================
  // TO STRING
  // ============================================================

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("WordMaterial wordmaterial {\n");
    sb.append("        text = ").append(text).append(";\n");
    sb.append("        foregroundColor = ")
        .append(ColorUtil.toColorString(foregroundColor))
        .append(";\n");
    sb.append("        backgroundColor = ")
        .append(ColorUtil.toColorString(backgroundColor))
        .append(";\n");
    sb.append("        fontName = ").append(fontFamily).append(";\n");
    sb.append("        fontStyle = ").append(fontStyle).append(";\n");
    sb.append("        fontSize = ").append(fontSize).append(";\n");
    sb.append("        useGradient = ").append(gradientEnabled).append(";\n");
    sb.append("        gradientColor = ")
        .append(ColorUtil.toColorString(gradientColor))
        .append(";\n");
    sb.append("        width = ").append(width).append(";\n");
    sb.append("        height = ").append(height).append(";\n");
    sb.append("        uOffset = ").append(uOffset).append(";\n");
    sb.append("        vOffset = ").append(vOffset).append(";\n");
    sb.append("        billboardWidth = ").append(billboardWidth).append(";\n");
    sb.append("        billboardHeight = ").append(billboardHeight).append(";\n");
    sb.append("    }");
    return sb.toString();
  }
}
