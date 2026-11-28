package net.murat.material;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.Typeface;
import net.murat.light.Light;
import net.murat.math.*;
import net.murat.util.ColorUtil;

/**
 * FilteredDielectricMaterial - Combines text rendering capability with dielectric material
 * properties. Android adaptation: Uses Bitmap, Canvas, and Paint for texture generation.
 */
public class FilteredDielectricMaterial implements Material {

  // Text properties
  private final String word;
  private final int textColor;
  private final int gradientColor;
  private final String gradientType;
  private final int bgColor;
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

  // Masks recording which texels belong to the text / image
  private boolean[] textMask;
  private boolean[] imageMask;
  private static final int textureSize = 1024;

  private String imagePath = "textures/turkeyFlag.png";

  // Dielectric properties
  private int diffuseColor;
  private double indexOfRefraction;
  private double transparency;
  private double reflectivity;
  private int filterColorInside;
  private int filterColorOutside;
  private Matrix4 objectTransform;
  private double currentTransparency;

  /** Constructor with text and dielectric properties */
  public FilteredDielectricMaterial(
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

    this.word = word;
    this.textColor = textColor;
    this.gradientColor = gradientColor;
    this.gradientType = gradientType != null ? gradientType : "horizontal";
    this.bgColor = bgColor;
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

    this.diffuseColor = diffuseColor;
    this.indexOfRefraction = ior;
    this.transparency = transparency;
    this.reflectivity = reflectivity;
    this.filterColorInside = filterColorInside;
    this.filterColorOutside = filterColorOutside;

    this.currentTransparency = transparency;
    this.objectTransform = new Matrix4().identity();

    this.texture = createTexture();
  }

  /** Simplified constructor */
  public FilteredDielectricMaterial(
      String word, int textColor, String fontFamily, int fontStyle, int fontSize) {
    this(
        word,
        textColor,
        0xFF00DD00,
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
        0xFFEEEEEE,
        1.5,
        0.8,
        0.1,
        0xFFFFFFFF,
        0xFFFFFFFF);
  }

  /** Creates the texture using Android Canvas. */
  private Bitmap createTexture() {
    final int size = textureSize;
    Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(bmp);

    // 1. Clear background
    canvas.drawColor(bgColor);

    // 2. Draw Image Layer (if provided)
    Bitmap imageLayer = null;
    if (imageObject != null) {
      imageLayer = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
      Canvas imgCanvas = new Canvas(imageLayer);

      int imgX = ((size - imageWidth) / 2) + imageUOffset;
      int imgY = ((size - imageHeight) / 2) + imageVOffset;
      Rect dstRect = new Rect(imgX, imgY, imgX + imageWidth, imgY + imageHeight);

      Paint imgPaint = new Paint();
      imgPaint.setFilterBitmap(true);
      imgCanvas.drawBitmap(imageObject, null, dstRect, imgPaint);

      canvas.drawBitmap(imageLayer, 0, 0, null);
    }

    // 3. Draw Text Layer
    Bitmap textLayer = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
    Canvas textCanvas = new Canvas(textLayer);

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
    float y = ((size - textHeight) / 2f) + (fm.ascent * 2) + (textHeight / 3f) + vOffset;

    // Gradient Shader
    int[] colors = new int[] {textColor, gradientColor};
    float[] positions = new float[] {0f, 1f};
    Shader shader = null;

    String gt = gradientType.toLowerCase();
    if ("vertical".equals(gt)) {
      shader =
          new LinearGradient(
              x,
              y - fm.ascent,
              x,
              y - fm.ascent + textHeight / 2f,
              colors,
              positions,
              Shader.TileMode.CLAMP);
    } else if ("diagonal".equals(gt)) {
      shader =
          new LinearGradient(
              x,
              y - fm.ascent,
              x + textWidth / 3f,
              y - fm.ascent + textHeight / 5f,
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

    textCanvas.drawText(word, x, y, textPaint);

    canvas.drawBitmap(textLayer, 0, 0, null);

    // 4. Extract Pixels for fast rendering
    this.texWidth = size;
    this.texHeight = size;
    this.texturePixels = new int[size * size];
    bmp.getPixels(this.texturePixels, 0, size, 0, 0, size, size);

    // 5. Build Masks
    int[] textLayerPixels = new int[size * size];
    textLayer.getPixels(textLayerPixels, 0, size, 0, 0, size, size);
    boolean[] tMask = new boolean[size * size];
    for (int i = 0; i < textLayerPixels.length; i++) {
      tMask[i] = ((textLayerPixels[i] >>> 24) & 0xFF) > 10;
    }
    this.textMask = tMask;

    boolean[] iMask = new boolean[size * size];
    if (imageLayer != null) {
      int[] imageLayerPixels = new int[size * size];
      imageLayer.getPixels(imageLayerPixels, 0, size, 0, 0, size, size);
      for (int i = 0; i < imageLayerPixels.length; i++) {
        iMask[i] = ((imageLayerPixels[i] >>> 24) & 0xFF) > 10;
      }
    }
    this.imageMask = iMask;

    // Cleanup intermediate bitmaps to help GC
    if (imageLayer != null) imageLayer.recycle();
    textLayer.recycle();

    return bmp;
  }

  /** Checks if a normal maps to a special pixel using the pre-built masks. */
  private boolean isSpecialPixel(Vector3 worldNormal) {
    if (textMask == null && imageMask == null) return false;

    Vector3 dir = worldNormal.normalize();
    double phi = Math.atan2(dir.z, dir.x);
    double theta = Math.asin(dir.y);

    double u = 1.0 - (phi + Math.PI) / (2 * Math.PI);
    double v = (theta + Math.PI / 2) / Math.PI;
    v = 1.0 - v;
    u = (u + 0.25) % 1.0;

    int texX = (int) (u * textureSize) % textureSize;
    if (texX < 0) texX += textureSize;

    int texY = (int) (v * textureSize);
    if (texY < 0 || texY >= textureSize) return false;

    int index = texY * textureSize + texX;

    if (textMask != null && index < textMask.length && textMask[index]) return true;
    if (imageMask != null && index < imageMask.length && imageMask[index]) return true;

    return false;
  }

  @Override
  public int getColorAt(Point3 point, Vector3 normal, Light light, Point3 viewerPoint) {
    Point3 localPoint = objectTransform.inverse().transformPoint(point);

    if (isSpecialPixel(normal)) {
      int textureColor = getTextureColor(localPoint, normal);
      currentTransparency = 0.0;
      return 0xFF000000 | (textureColor & 0x00FFFFFF);
    }

    int textureColor = getTextureColor(localPoint, normal);

    Vector3 lightDir = light.getDirectionTo(point).normalize();
    double diffuseFactor = Math.max(0.3, normal.dot(lightDir));

    Vector3 viewDir = point.subtractToVector(viewerPoint).normalize();
    double fresnel = Vector3.calculateFresnel(viewDir, normal, 1.0, indexOfRefraction);

    int diffuse = ColorUtil.multiplyColor(textureColor, diffuseFactor * light.getIntensity());

    Vector3 reflectDir = lightDir.reflect(normal);
    double specularFactor = Math.pow(Math.max(0, viewDir.dot(reflectDir)), 128);
    int specular =
        ColorUtil.multiplyColor(light.getColor(), specularFactor * 1.2 * light.getIntensity());

    int result = ColorUtil.add(diffuse, specular);

    double swirl1 = Math.sin(normal.y * 3.0 + normal.x * 2.0);
    double swirl2 = Math.sin(normal.x * 4.0 - normal.z * 2.5 + 1.0);
    double swirlRaw = swirl1 * 0.6 + swirl2 * 0.4;
    double swirl = swirlRaw * 0.5 + 0.5;

    int rimColor = ColorUtil.blendColors(filterColorInside, filterColorOutside, swirl);

    result = ColorUtil.blendColors(result, rimColor, (float) (swirl * 0.5));

    currentTransparency = transparency;

    return ColorUtil.clampColor(result);
  }

  private int getTextureColor(Point3 localPoint, Vector3 worldNormal) {
    if (texturePixels == null) return textColor;

    Vector3 dir = worldNormal.normalize();
    double phi = Math.atan2(dir.z, dir.x);
    double theta = Math.asin(dir.y);

    double u = 1.0 - (phi + Math.PI) / (2 * Math.PI);
    double v = (theta + Math.PI / 2) / Math.PI;
    v = 1.0 - v;
    u = (u + 0.25) % 1.0;

    int texX = (int) (u * texWidth) % texWidth;
    if (texX < 0) texX += texWidth;

    int texY = (int) (v * texHeight);
    if (texY < 0 || texY >= texHeight) {
      return 0x00000000;
    }

    return texturePixels[texY * texWidth + texX];
  }

  @Override
  public void setObjectTransform(Matrix4 tm) {
    this.objectTransform = (tm != null) ? tm : new Matrix4();
  }

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
    return reflectivity;
  }

  public int getFilterColorInside() {
    return filterColorInside;
  }

  public int getFilterColorOutside() {
    return filterColorOutside;
  }

  public void setFilterColorInside(int filterInside) {
    this.filterColorInside = filterInside;
  }

  public void setFilterColorOutside(int filterOutside) {
    this.filterColorOutside = filterOutside;
  }

  public int getDiffuseColor() {
    return diffuseColor;
  }

  public void setDiffuseColor(int color) {
    this.diffuseColor = color;
  }

  public void setIndexOfRefraction(double ior) {
    this.indexOfRefraction = ior;
  }

  public void setTransparency(double transparency) {
    this.transparency = transparency;
  }

  public void setReflectivity(double reflectivity) {
    this.reflectivity = reflectivity;
  }

  public void setImagePath(String npath) {
    this.imagePath = npath;
  }

  public String getImagePath() {
    return this.imagePath;
  }

  @Override
  public String toString() {
    StringBuffer sb = new StringBuffer();
    sb.append("FilteredDielectricMaterial filtereddielectricmaterial {\n");
    sb.append("        imagePath = ").append(getImagePath()).append(";\n");
    sb.append("        word = ").append(word).append(";\n");
    sb.append("        textColor = ").append(ColorUtil.toColorString(textColor)).append(";\n");
    sb.append("        gradientColor = ")
        .append(ColorUtil.toColorString(gradientColor))
        .append(";\n");
    sb.append("        gradientType = ").append(gradientType).append(";\n");
    sb.append("        bgColor = ").append(ColorUtil.toColorString(bgColor)).append(";\n");
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
        .append(ColorUtil.toColorString(diffuseColor))
        .append(";\n");
    sb.append("        ior = ").append(indexOfRefraction).append(";\n");
    sb.append("        transparency = ").append(transparency).append(";\n");
    sb.append("        reflectivity = ").append(reflectivity).append(";\n");
    sb.append("        filterColorInside = ")
        .append(ColorUtil.toColorString(filterColorInside))
        .append(";\n");
    sb.append("        filterColorOutside = ")
        .append(ColorUtil.toColorString(filterColorOutside))
        .append(";\n");
    sb.append("    }");
    return sb.toString();
  }
}
