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
 * TexturedCheckerboardMaterial - Checkerboard with text/image overlay. Android adaptation: Uses
 * Bitmap, Canvas, and Paint for texture generation.
 */
public class TexturedCheckerboardMaterial implements Material {
  private final int color1Packed;
  private final int color2Packed;
  private final double size;

  private final String text;
  private final int textColorPacked;
  private final int gradientColorPacked;
  private final String gradientType;
  private final int bgColorPacked;
  private final String fontFamily;
  private final int fontStyle;
  private final int fontSize;
  private final int textUOffset;
  private final int textVOffset;

  private final Bitmap imageObject;
  private final int imageWidth;
  private final int imageHeight;
  private final int imageUOffset;
  private final int imageVOffset;

  private final double ambientCoeff;
  private final double diffuseCoeff;
  private final double specularCoeff;
  private final double shininess;
  private final int specularColorPacked;

  private final double reflectivity;
  private final double ior;
  private final double transparency;

  private Matrix4 objectInverseTransform;
  private Matrix4 inverseTransposeTransform;

  private Bitmap texture;
  private int[] texturePixels;
  private int texWidth;
  private int texHeight;

  // Pre-cached color components
  private final int color1R, color1G, color1B;
  private final int color2R, color2G, color2B;

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

  public TexturedCheckerboardMaterial(
      int color1,
      int color2,
      double size,
      String text,
      int textColor,
      int gradientColor,
      String gradientType,
      int bgColor,
      String fontFamily,
      int fontStyle,
      int fontSize,
      int textUOffset,
      int textVOffset,
      Bitmap imageObject,
      int imageWidth,
      int imageHeight,
      int imageUOffset,
      int imageVOffset,
      double ambientCoeff,
      double diffuseCoeff,
      double specularCoeff,
      double shininess,
      int specularColor,
      double reflectivity,
      double ior,
      double transparency,
      Matrix4 objectInverseTransform) {

    this.color1Packed = color1;
    this.color2Packed = color2;
    this.size = size;

    this.text = text != null ? text.replaceAll("_", " ") : null;
    this.textColorPacked = textColor;
    this.gradientColorPacked = gradientColor;
    this.gradientType = gradientType != null ? gradientType.toLowerCase() : "horizontal";
    this.bgColorPacked = bgColor;

    this.fontFamily = fontFamily != null ? fontFamily.replaceAll("_", " ") : "Arial";
    this.fontStyle = fontStyle;
    this.fontSize = fontSize;
    this.textUOffset = textUOffset;
    this.textVOffset = textVOffset;

    this.imageObject = imageObject;
    this.imageWidth = imageWidth;
    this.imageHeight = imageHeight;
    this.imageUOffset = imageUOffset;
    this.imageVOffset = imageVOffset;

    this.ambientCoeff = ambientCoeff;
    this.diffuseCoeff = diffuseCoeff;
    this.specularCoeff = specularCoeff;
    this.shininess = shininess;
    this.specularColorPacked = specularColor;

    this.reflectivity = reflectivity;
    this.ior = ior;
    this.transparency = transparency;

    this.objectInverseTransform =
        objectInverseTransform != null ? objectInverseTransform : new Matrix4();
    this.inverseTransposeTransform = this.objectInverseTransform.inverseTransposeForNormal();

    this.color1R = (color1 >> 16) & 0xFF;
    this.color1G = (color1 >> 8) & 0xFF;
    this.color1B = color1 & 0xFF;

    this.color2R = (color2 >> 16) & 0xFF;
    this.color2G = (color2 >> 8) & 0xFF;
    this.color2B = color2 & 0xFF;

    this.texture = createTexture();
    if (this.texture != null) {
      this.texWidth = this.texture.getWidth();
      this.texHeight = this.texture.getHeight();
      this.texturePixels = new int[texWidth * texHeight];
      this.texture.getPixels(this.texturePixels, 0, texWidth, 0, 0, texWidth, texHeight);
    }
  }

  private Bitmap createTexture() {
    final int TEX_SIZE = 1024;
    Bitmap bmp = Bitmap.createBitmap(TEX_SIZE, TEX_SIZE, Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(bmp);

    // 1. Clear transparent / background
    canvas.drawColor(Color.TRANSPARENT);
    int bgA = (bgColorPacked >> 24) & 0xFF;
    if (bgA > 0) {
      canvas.drawColor(bgColorPacked);
    }

    // 2. Draw checkerboard
    int cellSize = (int) (TEX_SIZE * size / 10.0);
    if (cellSize < 1) cellSize = 1;

    Paint cellPaint = new Paint();
    for (int y = 0; y < TEX_SIZE; y += cellSize) {
      for (int x = 0; x < TEX_SIZE; x += cellSize) {
        boolean useColor1 = ((x / cellSize) + (y / cellSize)) % 2 == 0;
        cellPaint.setColor(useColor1 ? color1Packed : color2Packed);
        canvas.drawRect(x, y, x + cellSize, y + cellSize, cellPaint);
      }
    }

    // 3. Draw image object
    if (imageObject != null) {
      int imgX = ((TEX_SIZE - imageWidth) / 2) + imageUOffset - imageWidth - (imageWidth / 4);
      int imgY = ((TEX_SIZE - imageHeight) / 2) + imageVOffset - (imageHeight / 2);
      Rect dstRect = new Rect(imgX, imgY, imgX + imageWidth, imgY + imageHeight);
      Paint imgPaint = new Paint();
      imgPaint.setFilterBitmap(true);
      canvas.drawBitmap(imageObject, null, dstRect, imgPaint);
    }

    // 4. Draw text
    if (text != null && !text.trim().isEmpty()) {
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

      float x = ((TEX_SIZE - textWidth) / 2f) + textUOffset - textWidth - (textWidth / 4f);
      float y = ((TEX_SIZE - textHeight) / 2f) + (fm.ascent * 2f) + textVOffset;

      if (gradientColorPacked != 0) {
        int[] colors = new int[] {textColorPacked, gradientColorPacked};
        float[] positions = new float[] {0f, 1f};
        Shader shader = null;

        if ("vertical".equals(gradientType)) {
          shader =
              new LinearGradient(
                  x,
                  y - fm.ascent,
                  x,
                  y + textHeight / 2f,
                  colors,
                  positions,
                  Shader.TileMode.CLAMP);
        } else if ("diagonal".equals(gradientType)) {
          shader =
              new LinearGradient(
                  x,
                  y - fm.ascent,
                  x + textWidth / 3f,
                  y + textHeight / 5f,
                  colors,
                  positions,
                  Shader.TileMode.CLAMP);
        } else { // horizontal
          shader =
              new LinearGradient(
                  x,
                  y - fm.ascent,
                  x + textWidth / 3f,
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

  @Override
  public void setObjectTransform(Matrix4 tm) {
    if (tm != null) {
      this.objectInverseTransform = tm;
      this.inverseTransposeTransform = tm.inverseTransposeForNormal();
    }
  }

  @Override
  public int getColorAt(Point3 worldPoint, Vector3 worldNormal, Light light, Point3 viewerPos) {
    if (texturePixels == null) {
      return color1Packed;
    }

    // 1. Transform to local space
    objectInverseTransform.transformPoint(worldPoint, tempLocalPoint);

    // 2. Transform normal
    inverseTransposeTransform.transformDirection(worldNormal, tempTransformedNormal);
    tempLocalNormal.set(tempTransformedNormal);
    tempLocalNormal.normalize();

    // 3. Spherical UV mapping
    double phi = Math.atan2(tempLocalNormal.z, tempLocalNormal.x);
    double theta = Math.asin(tempLocalNormal.y);

    double u = 1.0 - (phi + Math.PI) / (2 * Math.PI);
    double v = (theta + Math.PI / 2) / Math.PI;
    v = 1.0 - v;

    int texX = (int) (u * texWidth) % texWidth;
    int texY = (int) (v * texHeight);
    if (texY < 0) texY = 0;
    if (texY >= texHeight) texY = texHeight - 1;

    int texColor = texturePixels[texY * texWidth + texX];
    int alpha = (texColor >> 24) & 0xFF;

    int baseR, baseG, baseB;
    if (alpha == 0) {
      double scaledX = tempLocalPoint.x * size;
      double scaledY = tempLocalPoint.y * size;
      double scaledZ = tempLocalPoint.z * size;

      int ix = (int) Math.floor(scaledX);
      int iy = (int) Math.floor(scaledY);
      int iz = (int) Math.floor(scaledZ);

      boolean isColor1 = ((ix + iy + iz) % 2 == 0);
      if (isColor1) {
        baseR = color1R;
        baseG = color1G;
        baseB = color1B;
      } else {
        baseR = color2R;
        baseG = color2G;
        baseB = color2B;
      }
    } else {
      baseR = (texColor >> 16) & 0xFF;
      baseG = (texColor >> 8) & 0xFF;
      baseB = texColor & 0xFF;
    }

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
      int r = (int) (baseR * ambientCoeff * lightR / 255.0 * intensity);
      int g = (int) (baseG * ambientCoeff * lightG / 255.0 * intensity);
      int b = (int) (baseB * ambientCoeff * lightB / 255.0 * intensity);
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
    double diffFactor = diffuseCoeff * NdotL * intensity;
    int diffR = (int) (baseR * diffFactor * lightR / 255.0);
    int diffG = (int) (baseG * diffFactor * lightG / 255.0);
    int diffB = (int) (baseB * diffFactor * lightB / 255.0);

    // 8. Reflection direction
    tempReflectDir.set(tempLightDir);
    tempReflectDir.negate();
    tempReflectDir.reflect(tempLocalNormal, tempReflectDir);

    // 9. Specular
    double RdotV = Math.max(0.0, tempReflectDir.dot(tempViewDir));
    double specFactor = Math.pow(RdotV, shininess) * specularCoeff * intensity;

    int specR = (int) (((specularColorPacked >> 16) & 0xFF) * specFactor * lightR / 255.0);
    int specG = (int) (((specularColorPacked >> 8) & 0xFF) * specFactor * lightG / 255.0);
    int specB = (int) ((specularColorPacked & 0xFF) * specFactor * lightB / 255.0);

    specR = clamp(specR);
    specG = clamp(specG);
    specB = clamp(specB);

    // 10. Combine
    int r = (int) (baseR * ambientCoeff * lightR / 255.0 * intensity) + diffR + specR;
    int g = (int) (baseG * ambientCoeff * lightG / 255.0 * intensity) + diffG + specG;
    int b = (int) (baseB * ambientCoeff * lightB / 255.0 * intensity) + diffB + specB;

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

  public void setImagePath(String npath) {
    this.imagePath = npath;
  }

  public String getImagePath() {
    return this.imagePath;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("TexturedCheckerboardMaterial texturedcheckerboardmaterial {\n");
    sb.append("        color1 = ").append(ColorUtil.toColorString(color1Packed)).append(";\n");
    sb.append("        color2 = ").append(ColorUtil.toColorString(color2Packed)).append(";\n");
    sb.append("        size = ").append(size).append(";\n");
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
    sb.append("        textUOffset = ").append(textUOffset).append(";\n");
    sb.append("        textVOffset = ").append(textVOffset).append(";\n");
    sb.append("        imagePath = ").append(getImagePath()).append(";\n");
    sb.append("        imageWidth = ").append(imageWidth).append(";\n");
    sb.append("        imageHeight = ").append(imageHeight).append(";\n");
    sb.append("        imageUOffset = ").append(imageUOffset).append(";\n");
    sb.append("        imageVOffset = ").append(imageVOffset).append(";\n");
    sb.append("        ambientCoeff = ").append(ambientCoeff).append(";\n");
    sb.append("        diffuseCoeff = ").append(diffuseCoeff).append(";\n");
    sb.append("        specularCoeff = ").append(specularCoeff).append(";\n");
    sb.append("        shininess = ").append(shininess).append(";\n");
    sb.append("        specularColor = ")
        .append(ColorUtil.toColorString(specularColorPacked))
        .append(";\n");
    sb.append("        reflectivity = ").append(reflectivity).append(";\n");
    sb.append("        ior = ").append(ior).append(";\n");
    sb.append("        transparency = ").append(transparency).append(";\n");
    sb.append("    }");
    return sb.toString();
  }
}
