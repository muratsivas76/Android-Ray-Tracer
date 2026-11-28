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
import net.murat.math.*;
import net.murat.util.ColorUtil;

/**
 * IsotropicMetalTextMaterial - Metal with text/image texture. Android adaptation: Uses Bitmap,
 * Canvas, and Paint for texture generation.
 */
public class IsotropicMetalTextMaterial implements Material {

  // Metal properties
  private final int metalColorPacked;
  private final double roughness;
  private final double reflectivity;
  private final double ior;
  private final double transparency;
  private Matrix4 objectInverseTransform;
  private Matrix4 inverseTransposeTransform;

  private final int metalR, metalG, metalB;

  // Text/Image properties
  private final String text;
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

  // Phong constants
  private final double ambientCoefficient = 0.15;
  private final double diffuseCoefficient = 0.4;
  private final double specularCoefficient = 0.8;
  private final double shininess = 100.0;
  private final int specularColorPacked = 0xFFF0F0FA;

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

  public IsotropicMetalTextMaterial() {
    this(
        0xFFB3B3BF,
        0.2,
        0.7,
        1.0,
        0.0,
        new Matrix4(),
        "Hello",
        0xFFFFFFFF,
        0x00000000,
        "horizontal",
        0x00000000,
        "Arial",
        Typeface.BOLD,
        48,
        0,
        0,
        null,
        0,
        0,
        0,
        0);
  }

  public IsotropicMetalTextMaterial(
      int metalColor,
      double roughness,
      double reflectivity,
      String text,
      int textColor,
      String fontFamily,
      int fontStyle,
      int fontSize) {
    this(
        metalColor,
        roughness,
        reflectivity,
        1.0,
        0.0,
        new Matrix4(),
        text,
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
        0);
  }

  public IsotropicMetalTextMaterial(
      int metalColor,
      double roughness,
      double reflectivity,
      double ior,
      double transparency,
      Matrix4 objectInverseTransform,
      String text,
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
      int imageVOffset) {

    this.metalColorPacked = metalColor;
    this.metalR = (metalColor >> 16) & 0xFF;
    this.metalG = (metalColor >> 8) & 0xFF;
    this.metalB = metalColor & 0xFF;

    this.roughness = Math.max(0.01, Math.min(1.0, roughness));
    this.reflectivity = Math.max(0.0, Math.min(1.0, reflectivity));
    this.ior = ior;
    this.transparency = Math.max(0.0, Math.min(1.0, transparency));
    this.objectInverseTransform =
        objectInverseTransform != null ? objectInverseTransform : new Matrix4();
    this.inverseTransposeTransform = this.objectInverseTransform.inverseTransposeForNormal();

    this.text = convertToNorwegianText(text != null ? text.replaceAll("_", " ") : "");
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

    this.texture = createTexture();
    if (this.texture != null) {
      this.texWidth = this.texture.getWidth();
      this.texHeight = this.texture.getHeight();
      this.texturePixels = new int[texWidth * texHeight];
      this.texture.getPixels(this.texturePixels, 0, texWidth, 0, 0, texWidth, texHeight);
    }
  }

  /** Creates texture using Android Canvas. */
  private Bitmap createTexture() {
    final int size = 1024;
    Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(bmp);

    // 1. Clear to transparent (default for ARGB_8888)
    canvas.drawColor(Color.TRANSPARENT);

    // 2. Background Color
    int bgA = (bgColorPacked >> 24) & 0xFF;
    if (bgA > 0) {
      canvas.drawColor(bgColorPacked);
    }

    // 3. Draw background image if provided
    if (imageObject != null) {
      int imgX = ((size - imageWidth) / 2) + imageUOffset;
      int imgY = ((size - imageHeight) / 2) + imageVOffset;
      Rect dstRect = new Rect(imgX, imgY, imgX + imageWidth, imgY + imageHeight);
      Paint imgPaint = new Paint();
      imgPaint.setFilterBitmap(true);
      canvas.drawBitmap(imageObject, null, dstRect, imgPaint);
    }

    // 4. Draw text if provided
    if (text != null && !text.isEmpty()) {
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

      float textWidth = textPaint.measureText(text);
      Paint.FontMetrics fm = textPaint.getFontMetrics();
      float textHeight = fm.descent - fm.ascent;

      float x = ((size - textWidth) / 2f) + uOffset;
      float y = ((size - textHeight) / 2f) + fm.ascent + vOffset;

      // Apply gradient or solid color
      if (gradientColorPacked != 0x00000000 && gradientColorPacked != 0) {
        int[] colors = new int[] {textColorPacked, gradientColorPacked};
        float[] positions = new float[] {0f, 1f};
        Shader shader = null;

        String type = gradientType.toLowerCase();
        if (type.equals("vertical")) {
          shader =
              new LinearGradient(
                  x, y - fm.ascent, x, y + textHeight, colors, positions, Shader.TileMode.CLAMP);
        } else if (type.equals("diagonal")) {
          shader =
              new LinearGradient(
                  x,
                  y - fm.ascent,
                  x + textWidth,
                  y + textHeight,
                  colors,
                  positions,
                  Shader.TileMode.CLAMP);
        } else { // horizontal
          shader =
              new LinearGradient(
                  x,
                  y - fm.ascent,
                  x + textWidth,
                  y - fm.ascent,
                  colors,
                  positions,
                  Shader.TileMode.CLAMP);
        }
        textPaint.setShader(shader);
      } else {
        textPaint.setColor(textColorPacked);
      }

      canvas.drawText(text, x, y, textPaint);
    }

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
    if (tm == null) tm = new Matrix4();
    this.objectInverseTransform = tm;
    this.inverseTransposeTransform = tm.inverseTransposeForNormal();
  }

  @Override
  public int getColorAt(Point3 worldPoint, Vector3 worldNormal, Light light, Point3 viewerPos) {
    objectInverseTransform.transformPoint(worldPoint, tempLocalPoint);

    inverseTransposeTransform.transformDirection(worldNormal, tempTransformedNormal);
    tempLocalNormal.set(tempTransformedNormal);
    tempLocalNormal.normalize();

    int textureColor = getTextureColor(tempLocalPoint, tempLocalNormal);
    int alpha = (textureColor >> 24) & 0xFF;

    LightProperties.getLightProperties(light, worldPoint, tempLightProps);
    tempLightDir.set(tempLightProps.direction);

    tempViewerPoint.set(viewerPos);
    tempViewDir.set(tempViewerPoint);
    tempViewDir.subtract(worldPoint);
    tempViewDir.normalize();

    if (alpha > 76) {
      double brightness =
          Math.max(0.4, Math.min(1.0, 0.6 + 0.4 * Math.max(0, tempLocalNormal.dot(tempLightDir))));
      int r = (int) (((textureColor >> 16) & 0xFF) * brightness);
      int g = (int) (((textureColor >> 8) & 0xFF) * brightness);
      int b = (int) ((textureColor & 0xFF) * brightness);
      r = r > 255 ? 255 : (r < 0 ? 0 : r);
      g = g > 255 ? 255 : (g < 0 ? 0 : g);
      b = b > 255 ? 255 : (b < 0 ? 0 : b);
      return (0xFF000000 | (r << 16) | (g << 8) | b);
    }

    double NdotL = Math.max(0, tempLocalNormal.dot(tempLightDir));
    double NdotH = Math.max(0, tempLocalNormal.dot(tempViewDir));

    int lightColor = tempLightProps.color;
    int lightR = (lightColor >> 16) & 0xFF;
    int lightG = (lightColor >> 8) & 0xFF;
    int lightB = lightColor & 0xFF;
    double intensity = tempLightProps.intensity;

    double diffFactor = diffuseCoefficient * NdotL * intensity;
    int diffR = (int) (metalR * diffFactor * lightR / 255.0);
    int diffG = (int) (metalG * diffFactor * lightG / 255.0);
    int diffB = (int) (metalB * diffFactor * lightB / 255.0);

    tempReflectDir.set(tempLightDir);
    tempReflectDir.negate();
    tempReflectDir.reflect(tempLocalNormal, tempReflectDir);
    double RdotV = Math.max(0, tempReflectDir.dot(tempViewDir));
    double specIntensity = Math.pow(RdotV, shininess * (1.0 - roughness));
    double specFactor = specularCoefficient * specIntensity * intensity;

    int specR = (int) (240 * specFactor * lightR / 255.0);
    int specG = (int) (240 * specFactor * lightG / 255.0);
    int specB = (int) (250 * specFactor * lightB / 255.0);

    double ambFactor = ambientCoefficient * intensity;
    int ambR = (int) (metalR * ambFactor * lightR / 255.0);
    int ambG = (int) (metalG * ambFactor * lightG / 255.0);
    int ambB = (int) (metalB * ambFactor * lightB / 255.0);

    int r = ambR + diffR + specR;
    int g = ambG + diffG + specG;
    int b = ambB + diffB + specB;

    r = r > 255 ? 255 : (r < 0 ? 0 : r);
    g = g > 255 ? 255 : (g < 0 ? 0 : g);
    b = b > 255 ? 255 : (b < 0 ? 0 : b);

    return (0xFF000000 | (r << 16) | (g << 8) | b);
  }

  private int getTextureColor(Point3 localPoint, Vector3 worldNormal) {
    if (texturePixels == null) return textColorPacked;

    tempLocalNormal.set(worldNormal);
    tempLocalNormal.normalize();

    double phi = Math.atan2(tempLocalNormal.z, tempLocalNormal.x);
    double theta = Math.asin(tempLocalNormal.y);

    double u = 1.0 - (phi + Math.PI) / (2 * Math.PI);
    double v = (theta + Math.PI / 2) / Math.PI;
    v = 1.0 - v;
    u = (u + 0.25) % 1.0;

    int texX = (int) (u * texWidth);
    texX = Math.max(0, Math.min(texWidth - 1, texX));
    int texY = (int) (v * texHeight);
    texY = Math.max(0, Math.min(texHeight - 1, texY));

    return texturePixels[texY * texWidth + texX];
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

  public int getMetalColorPacked() {
    return metalColorPacked;
  }

  public double getRoughness() {
    return roughness;
  }

  public String getText() {
    return text;
  }

  public int getTextColorPacked() {
    return textColorPacked;
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
    sb.append("IsotropicMetalTextMaterial isotropicmetaltextmaterial {\n");
    sb.append("        imagePath = ").append(getImagePath()).append(";\n");
    sb.append("        metalColor = ")
        .append(ColorUtil.toColorString(metalColorPacked))
        .append(";\n");
    sb.append("        roughness = ").append(roughness).append(";\n");
    sb.append("        reflectivity = ").append(reflectivity).append(";\n");
    sb.append("        ior = ").append(ior).append(";\n");
    sb.append("        transparency = ").append(transparency).append(";\n");
    sb.append("        text = ").append(text).append(";\n");
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
    sb.append("    }");
    return sb.toString();
  }
}
