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
import net.murat.light.MuratAmbientLight;
import net.murat.math.Matrix4;
import net.murat.math.Point3;
import net.murat.math.Vector3;
import net.murat.util.ColorUtil;

/**
 * TextDielectricMaterial - ULTRA OPTIMIZED Android adaptation: Uses Bitmap, Canvas, and Paint for
 * texture generation.
 */
public class TextDielectricMaterial implements Material {

  // ============================================================
  // TEXTURE PARAMETERS
  // ============================================================
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

  private String imagePath = "textures/turkeyFlag.png";

  // ============================================================
  // GC-FRIENDLY BUFFERS
  // ============================================================
  private final double[] normalBuffer = new double[3];
  private final double[] lightDirBuffer = new double[3];
  private final double[] viewDirBuffer = new double[3];
  private final double[] reflectDirBuffer = new double[3];
  private final Point3 tempPoint = new Point3(0, 0, 0);
  private final Vector3 tempLightDir = new Vector3(0, 0, 0);
  private final Vector3 tempViewDir = new Vector3(0, 0, 0);

  // ============================================================
  // MATERIAL PROPERTIES
  // ============================================================
  private int diffuseColorPacked;
  private double indexOfRefraction;
  private double transparency;
  private double reflectivity;
  private int filterColorInsidePacked;
  private int filterColorOutsidePacked;
  private Matrix4 objectTransform;
  private Matrix4 inverseTransform;
  private double currentReflectivity;
  private double currentTransparency;

  // ============================================================
  // PRE-COMPUTED CONSTANTS
  // ============================================================
  private static final double SPECULAR_POWER = 140.0;
  private static final double CENTER_THRESHOLD = 0.85;
  private static final double CENTER_DAMPENING_FACTOR = 0.55;
  private static final double OVERLAY_BASELINE = 1.15;
  private static final double OVERLAY_DIFFUSE_CONTRIB = 0.35;
  private static final double FRESNEL_REFLECT_BOOST = 0.7;
  private static final double FRESNEL_TRANSPARENCY_FACTOR = 0.15;
  private static final double SKY_GLOW_POWER = 1.4;
  private static final double SKY_GLOW_INTENSITY = 110.0;
  private static final double SKY_GLOW_GREEN = 0.45;
  private static final double SKY_GLOW_BLUE = 1.00;
  private static final double FILTER_STRENGTH_MULT = 0.6;
  private static final double FILTER_BLEND_MULT = 1.3;
  private static final double FILTER_APPLY_STRENGTH = 0.4;

  private int diffR, diffG, diffB;

  // ============================================================
  // CONSTRUCTORS
  // ============================================================
  public TextDielectricMaterial(
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
      double ior,
      double transparency,
      double reflectivity,
      int filterColorInside,
      int filterColorOutside) {

    this.word = convertToNorwegianText(word).replaceAll("_", " ");
    this.textColorPacked = textColor;
    this.gradientColorPacked = gradientColor;
    this.gradientType = gradientType != null ? gradientType : "horizontal";
    this.bgColorPacked = bgColor;
    this.fontFamily = fontFamily.replaceAll("_", " ");
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
    this.indexOfRefraction = ior;
    this.transparency = transparency;
    this.reflectivity = reflectivity;
    this.filterColorInsidePacked = filterColorInside;
    this.filterColorOutsidePacked = filterColorOutside;

    this.diffR = (diffuseColor >> 16) & 0xFF;
    this.diffG = (diffuseColor >> 8) & 0xFF;
    this.diffB = diffuseColor & 0xFF;

    this.currentReflectivity = reflectivity;
    this.currentTransparency = transparency;
    this.objectTransform = new Matrix4().identity();
    this.inverseTransform = new Matrix4().identity();

    this.texture = createTexture();
    if (this.texture != null) {
      this.texWidth = this.texture.getWidth();
      this.texHeight = this.texture.getHeight();
      this.texturePixels = new int[texWidth * texHeight];
      this.texture.getPixels(this.texturePixels, 0, texWidth, 0, 0, texWidth, texHeight);
    }
  }

  public TextDielectricMaterial(
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
        1.5,
        0.8,
        0.1,
        0xFFFFFFFF,
        0xFFFFFFFF);
  }

  // ============================================================
  // TEXTURE CREATION
  // ============================================================
  private Bitmap createTexture() {
    final int size = 1024;
    Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(bmp);

    // 1. Clear transparent
    canvas.drawColor(Color.TRANSPARENT);

    // 2. Background color
    int bgA = (bgColorPacked >> 24) & 0xFF;
    if (bgA > 0) {
      canvas.drawColor(bgColorPacked);
    }

    // 3. Draw image object
    if (imageObject != null) {
      int imgX = ((size - imageWidth) / 2) + imageUOffset;
      int imgY = ((size - imageHeight) / 2) + imageVOffset;
      Rect dstRect = new Rect(imgX, imgY, imgX + imageWidth, imgY + imageHeight);
      Paint imgPaint = new Paint();
      imgPaint.setFilterBitmap(true);
      canvas.drawBitmap(imageObject, null, dstRect, imgPaint);
    }

    // 4. Draw text
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

    if (gradientColorPacked != 0x00000000) {
      int[] colors = new int[] {textColorPacked, gradientColorPacked};
      float[] positions = new float[] {0f, 1f};
      Shader shader = null;

      String gt = gradientType.toLowerCase();
      if (gt.equals("vertical")) {
        shader =
            new LinearGradient(
                x, y - fm.ascent, x, y + textHeight / 2f, colors, positions, Shader.TileMode.CLAMP);
      } else if (gt.equals("diagonal")) {
        shader =
            new LinearGradient(
                x,
                y - fm.ascent,
                x + textWidth / 3f,
                y + textHeight / 5f,
                colors,
                positions,
                Shader.TileMode.CLAMP);
      } else {
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

  public void setImagePath(String npath) {
    this.imagePath = npath;
  }

  public String getImagePath() {
    return this.imagePath;
  }

  // ============================================================
  // TRANSFORM - CACHED INVERSE
  // ============================================================
  @Override
  public void setObjectTransform(Matrix4 tm) {
    this.objectTransform = (tm != null) ? tm : new Matrix4().identity();
    Matrix4 inv = this.objectTransform.inverse();
    if (inv != null) {
      this.inverseTransform.set(inv);
    } else {
      this.inverseTransform.identity();
    }
  }

  // ============================================================
  // TEXTURE MAPPING
  // ============================================================
  private int getTextureColor(Point3 localPoint, Vector3 worldNormal) {
    if (texturePixels == null) return diffuseColorPacked;

    normalBuffer[0] = worldNormal.x;
    normalBuffer[1] = worldNormal.y;
    normalBuffer[2] = worldNormal.z;

    if (objectTransform != null) {
      objectTransform.transformNormalAndNormalize(normalBuffer);
    }

    double phi = Math.atan2(normalBuffer[2], normalBuffer[0]);
    double theta = Math.asin(Math.max(-1.0, Math.min(1.0, normalBuffer[1])));

    double u = 1.0 - (phi + Math.PI) / (2 * Math.PI);
    double v = 1.0 - (theta + Math.PI / 2) / Math.PI;

    u = (u + 0.25) % 1.0;

    int texX = (int) (u * texWidth) % texWidth;
    if (texX < 0) texX += texWidth;

    int texY = (int) (v * texHeight);
    if (texY < 0 || texY >= texHeight) {
      return 0x00000000;
    }

    return texturePixels[texY * texWidth + texX];
  }

  // ============================================================
  // MAIN SHADING
  // ============================================================
  @Override
  public int getColorAt(Point3 point, Vector3 normal, Light light, Point3 viewerPoint) {
    inverseTransform.transformPoint(point, tempPoint);
    int textureColor = getTextureColor(tempPoint, normal);
    int alpha = (textureColor >> 24) & 0xFF;

    int baseR = (textureColor >> 16) & 0xFF;
    int baseG = (textureColor >> 8) & 0xFF;
    int baseB = textureColor & 0xFF;

    tempLightDir.set(light.getDirectionTo(point));
    tempLightDir.negate();
    tempLightDir.normalize();
    lightDirBuffer[0] = tempLightDir.x;
    lightDirBuffer[1] = tempLightDir.y;
    lightDirBuffer[2] = tempLightDir.z;

    tempViewDir.set(viewerPoint.x - point.x, viewerPoint.y - point.y, viewerPoint.z - point.z);
    tempViewDir.normalize();
    viewDirBuffer[0] = tempViewDir.x;
    viewDirBuffer[1] = tempViewDir.y;
    viewDirBuffer[2] = tempViewDir.z;

    double cosTheta =
        viewDirBuffer[0] * normal.x + viewDirBuffer[1] * normal.y + viewDirBuffer[2] * normal.z;
    if (cosTheta < 0) cosTheta = -cosTheta;

    double centerDampening = 1.0;
    if (cosTheta > CENTER_THRESHOLD) {
      double t = (cosTheta - CENTER_THRESHOLD) / (1.0 - CENTER_THRESHOLD);
      centerDampening = 1.0 - (t * CENTER_DAMPENING_FACTOR);
    }

    double NdotL =
        normal.x * lightDirBuffer[0] + normal.y * lightDirBuffer[1] + normal.z * lightDirBuffer[2];
    double diffuseFactor = ((NdotL > 0.4) ? NdotL : 0.4) * centerDampening;

    double r0 = (1.0 - indexOfRefraction) / (1.0 + indexOfRefraction);
    r0 = r0 * r0;
    double oneMinusCos = 1.0 - cosTheta;
    double fresnel =
        r0 + (1.0 - r0) * oneMinusCos * oneMinusCos * oneMinusCos * oneMinusCos * oneMinusCos;

    currentReflectivity = reflectivity + (fresnel * FRESNEL_REFLECT_BOOST);
    if (currentReflectivity > 0.85) currentReflectivity = 0.85;

    currentTransparency = transparency * (1.0 - fresnel * FRESNEL_TRANSPARENCY_FACTOR);
    if (currentTransparency < 0.1) currentTransparency = 0.1;

    double intensity = light.getIntensity();
    int lightColor = light.getColor();
    int lightR = (lightColor >> 16) & 0xFF;
    int lightG = (lightColor >> 8) & 0xFF;
    int lightB = lightColor & 0xFF;

    double diffRVal, diffGVal, diffBVal;

    if (alpha < 128) {
      double diffFactor = diffuseFactor * intensity;
      diffRVal = baseR * diffFactor * lightR / 255.0;
      diffGVal = baseG * diffFactor * lightG / 255.0;
      diffBVal = baseB * diffFactor * lightB / 255.0;

      if (light instanceof MuratAmbientLight) {
        double upFactor = normal.y;
        if (upFactor < 0) upFactor = 0;
        double skyGlow =
            Math.pow(upFactor, SKY_GLOW_POWER) * SKY_GLOW_INTENSITY * intensity * (1.0 - fresnel);
        diffGVal += skyGlow * SKY_GLOW_GREEN;
        diffBVal += skyGlow * SKY_GLOW_BLUE;
      }
    } else {
      double overlayFactor = OVERLAY_BASELINE + (diffuseFactor * OVERLAY_DIFFUSE_CONTRIB);
      diffRVal = baseR * overlayFactor * intensity;
      diffGVal = baseG * overlayFactor * intensity;
      diffBVal = baseB * overlayFactor * intensity;
    }

    double NdotL2 =
        lightDirBuffer[0] * normal.x + lightDirBuffer[1] * normal.y + lightDirBuffer[2] * normal.z;
    reflectDirBuffer[0] = lightDirBuffer[0] - 2.0 * NdotL2 * normal.x;
    reflectDirBuffer[1] = lightDirBuffer[1] - 2.0 * NdotL2 * normal.y;
    reflectDirBuffer[2] = lightDirBuffer[2] - 2.0 * NdotL2 * normal.z;

    double len =
        reflectDirBuffer[0] * reflectDirBuffer[0]
            + reflectDirBuffer[1] * reflectDirBuffer[1]
            + reflectDirBuffer[2] * reflectDirBuffer[2];
    if (len > 1e-20) {
      len = Math.sqrt(len);
      reflectDirBuffer[0] /= len;
      reflectDirBuffer[1] /= len;
      reflectDirBuffer[2] /= len;
    }

    double RdotV =
        reflectDirBuffer[0] * viewDirBuffer[0]
            + reflectDirBuffer[1] * viewDirBuffer[1]
            + reflectDirBuffer[2] * viewDirBuffer[2];
    if (RdotV < 0) RdotV = 0;

    double specFactor = Math.pow(RdotV, SPECULAR_POWER) * intensity;
    specFactor *= centerDampening;
    if (specFactor > 1.0) specFactor = 1.0;

    double specR = lightR * 0.94 * specFactor;
    double specG = lightG * specFactor;
    double specB = lightB * specFactor;

    int r = (int) (diffRVal + specR);
    int g = (int) (diffGVal + specG);
    int b = (int) (diffBVal + specB);
    if (r > 255) r = 255;
    else if (r < 0) r = 0;
    if (g > 255) g = 255;
    else if (g < 0) g = 0;
    if (b > 255) b = 255;
    else if (b < 0) b = 0;

    if (alpha < 128
        && fresnel > 0.12
        && filterColorInsidePacked != 0
        && filterColorOutsidePacked != 0) {
      int insideR = (filterColorInsidePacked >> 16) & 0xFF;
      int insideG = (filterColorInsidePacked >> 8) & 0xFF;
      int insideB = filterColorInsidePacked & 0xFF;
      int outsideR = (filterColorOutsidePacked >> 16) & 0xFF;
      int outsideG = (filterColorOutsidePacked >> 8) & 0xFF;
      int outsideB = filterColorOutsidePacked & 0xFF;

      double blend = fresnel * FILTER_BLEND_MULT;
      if (blend > 1.0) blend = 1.0;
      double strength = fresnel * FILTER_STRENGTH_MULT;
      if (strength > 0.8) strength = 0.8;

      double filtR = insideR * (1.0 - blend) + outsideR * blend;
      double filtG = insideG * (1.0 - blend) + outsideG * blend;
      double filtB = insideB * (1.0 - blend) + outsideB * blend;

      double invStrength = 1.0 - strength * FILTER_APPLY_STRENGTH;
      r = (int) (r * invStrength + filtR * strength * 0.6);
      g = (int) (g * invStrength + filtG * strength * 0.6);
      b = (int) (b * invStrength + filtB * strength * 0.6);
      if (r > 255) r = 255;
      else if (r < 0) r = 0;
      if (g > 255) g = 255;
      else if (g < 0) g = 0;
      if (b > 255) b = 255;
      else if (b < 0) b = 0;
    }

    return (0xFF000000 | (r << 16) | (g << 8) | b);
  }

  private int clamp(int value) {
    if (value > 255) return 255;
    if (value < 0) return 0;
    return value;
  }

  // ============================================================
  // GETTERS
  // ============================================================
  @Override
  public double getIndexOfRefraction() {
    return indexOfRefraction;
  }

  @Override
  public double getTransparency() {
    return currentTransparency;
  }

  @Override
  public double getReflectivity() {
    return currentReflectivity;
  }

  public int getFilterColorInsidePacked() {
    return filterColorInsidePacked;
  }

  public int getFilterColorOutsidePacked() {
    return filterColorOutsidePacked;
  }

  public int getDiffuseColorPacked() {
    return diffuseColorPacked;
  }

  @Override
  public String toString() {
    StringBuffer sb = new StringBuffer();
    sb.append("TextDielectricMaterial textdielectricmaterial {\n");
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
    sb.append("        ior = ").append(indexOfRefraction).append(";\n");
    sb.append("        transparency = ").append(currentTransparency).append(";\n");
    sb.append("        reflectivity = ").append(currentReflectivity).append(";\n");
    sb.append("        filterColorInside = ")
        .append(ColorUtil.toColorString(filterColorInsidePacked))
        .append(";\n");
    sb.append("        filterColorOutside = ")
        .append(ColorUtil.toColorString(filterColorOutsidePacked))
        .append(";\n");
    sb.append("    }");
    return sb.toString();
  }
}
