package net.murat.material;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.Typeface;

import net.murat.light.Light;
import net.murat.light.LightProperties;
import net.murat.math.*;
import net.murat.util.ColorUtil;

/**
 * Sphere material that paints a word and an optional image on a spherical texture.
 *
 * <p>By default (followObjectRotation = false) the texture is looked up from the world-space
 * normal, so only the shape itself rotates and the texture stays fixed in the scene. When
 * followObjectRotation is true the texture is looked up from the object-space hit position, so the
 * text and the image rotate, scale and translate together with the sphere.
 *
 * <p>Matrix convention: the matrix received through {@link #setObjectTransform(Matrix4)} is the
 * inverse (world space to object space) transform, the same convention WordMaterial uses.
 */
public class SphereWordTextureMaterial implements Material {

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

  private final double reflectivity;
  private final double ior;
  private final double baseTransparency;
  private double currentTransparency = 0.0;

  private final int bgR, bgG, bgB, bgA;
  private final int textR, textG, textB;
  private final int gradR, gradG, gradB;

  // If true, the texture follows the object's transform (rotate, scale, translate).
  // If false, the texture stays fixed in world space (legacy behavior).
  private boolean followObjectRotation = false;

  // objectTransform is object -> world, inverseObjectTransform is world -> object.
  private Matrix4 objectTransform;
  private Matrix4 inverseObjectTransform;

  private static class ScratchData {
    final Vector3 tempNormal = new Vector3();
    final Vector3 tempViewDir = new Vector3();
    final Vector3 tempLightDir = new Vector3();
    final Vector3 tempReflectDir = new Vector3();
    final Vector3 tempDir = new Vector3();
    final LightProperties tempLightProps = new LightProperties();
    final Point3 tempLocalPoint = new Point3();
    final Point3 tempViewerPoint = new Point3();
  }

  private final ThreadLocal<ScratchData> threadLocalScratch =
      new ThreadLocal<ScratchData>() {
        @Override
        protected ScratchData initialValue() {
          return new ScratchData();
        }
      };

  private int[] texturePixels;
  private boolean[] textMask;
  private boolean[] imageMask;
  private final int textureSize = 1024;
  private String imagePath = "textures/turkeyFlag.png";

  public SphereWordTextureMaterial(
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
        0.3,
        1.0,
        0.0,
        0,
        0,
        null,
        0,
        0,
        0,
        0);
  }

  public SphereWordTextureMaterial(
      String word,
      int textColor,
      int gradientColor,
      String gradientType,
      int bgColor,
      String fontFamily,
      int fontStyle,
      int fontSize,
      double reflectivity,
      double ior,
      double transparency,
      int uOffset,
      int vOffset,
      Bitmap imageObject,
      int imageWidth,
      int imageHeight,
      int imageUOffset,
      int imageVOffset) {
    this(
        word,
        textColor,
        gradientColor,
        gradientType,
        bgColor,
        fontFamily,
        fontStyle,
        fontSize,
        reflectivity,
        ior,
        transparency,
        uOffset,
        vOffset,
        imageObject,
        imageWidth,
        imageHeight,
        imageUOffset,
        imageVOffset,
        false);
  }

  /**
   * Full constructor with the followObjectRotation flag.
   *
   * @param followObjectRotation true: the texture rotates, scales and moves with the sphere; false:
   *     only the shape moves and the texture stays fixed in world space (legacy behavior)
   */
  public SphereWordTextureMaterial(
      String word,
      int textColor,
      int gradientColor,
      String gradientType,
      int bgColor,
      String fontFamily,
      int fontStyle,
      int fontSize,
      double reflectivity,
      double ior,
      double transparency,
      int uOffset,
      int vOffset,
      Bitmap imageObject,
      int imageWidth,
      int imageHeight,
      int imageUOffset,
      int imageVOffset,
      boolean followObjectRotation) {

    this.word = word;
    this.textColor = textColor;
    this.gradientColor = gradientColor;
    this.gradientType = (gradientType != null) ? gradientType : "horizontal";
    this.bgColor = bgColor;
    this.fontFamily = fontFamily.replaceAll("_", " ");
    this.fontStyle = fontStyle;
    this.fontSize = fontSize;
    this.reflectivity = Math.min(1.0, Math.max(0.0, reflectivity));
    this.ior = Math.max(1.0, ior);
    this.baseTransparency = Math.min(1.0, Math.max(0.0, transparency));
    this.uOffset = uOffset;
    this.vOffset = vOffset;
    this.imageObject = imageObject;
    this.imageWidth = imageWidth;
    this.imageHeight = imageHeight;
    this.imageUOffset = imageUOffset;
    this.imageVOffset = imageVOffset;
    this.followObjectRotation = followObjectRotation;

    this.bgR = (this.bgColor >> 16) & 0xFF;
    this.bgG = (this.bgColor >> 8) & 0xFF;
    this.bgB = this.bgColor & 0xFF;
    this.bgA = (this.bgColor >> 24) & 0xFF;

    this.textR = (this.textColor >> 16) & 0xFF;
    this.textG = (this.textColor >> 8) & 0xFF;
    this.textB = this.textColor & 0xFF;

    this.gradR = (this.gradientColor >> 16) & 0xFF;
    this.gradG = (this.gradientColor >> 8) & 0xFF;
    this.gradB = this.gradientColor & 0xFF;

    this.objectTransform = new Matrix4();
    this.inverseObjectTransform = new Matrix4();

    this.texturePixels = createTexture();
  }

  private int[] createTexture() {
    int size = textureSize;

    // Final canvas
    Bitmap bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(bmp);

    // Background
    canvas.drawColor(bgColor);

    // Image layer
    Bitmap imageLayer = null;
    if (imageObject != null) {
      imageLayer = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
      Canvas imgCanvas = new Canvas(imageLayer);

      int imgX = (size - imageWidth) / 2 + imageUOffset;
      int imgY = (size - imageHeight) / 2 + imageVOffset;
      Rect dstRect = new Rect(imgX, imgY, imgX + imageWidth, imgY + imageHeight);
      Paint imgPaint = new Paint();
      imgPaint.setFilterBitmap(true);
      imgCanvas.drawBitmap(imageObject, null, dstRect, imgPaint);

      canvas.drawBitmap(imageLayer, 0, 0, null);
    }

    // Text layer
    Bitmap textLayer = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
    Canvas textCanvas = new Canvas(textLayer);

    Paint textPaint = new Paint();
    textPaint.setAntiAlias(true);

    int tfStyle = Typeface.NORMAL;
    if ((fontStyle & 1) != 0) tfStyle |= Typeface.BOLD;
    if ((fontStyle & 2) != 0) tfStyle |= Typeface.ITALIC;

    Typeface tf = Typeface.create(OurFontFactory.getFont(fontFamily), tfStyle);
    //Typeface tf = Typeface.create(fontFamily, tfStyle);
    textPaint.setTypeface(tf);
    textPaint.setTextSize(Math.max(1, fontSize));

    float textWidth = textPaint.measureText(word);
    Paint.FontMetrics fm = textPaint.getFontMetrics();
    float textAscent = -fm.ascent; // positive value
    float textDescent = fm.descent;

    float textX = (size - textWidth) / 2f + uOffset;
    float textY = (size + textAscent - textDescent) / 2f + vOffset;

    // Paint/Shader for text
    if (gradientColor == 0x00000000) {
      textPaint.setColor(textColor);
    } else {
      String type = gradientType.toLowerCase();
      if ("vertical".equals(type)) {
        Shader shader =
            new LinearGradient(
                textX,
                textY - textAscent,
                textX,
                textY + textDescent,
                new int[] {textColor, gradientColor},
                null,
                Shader.TileMode.CLAMP);
        textPaint.setShader(shader);
      } else if ("radial".equals(type)) {
        float cx = textX + textWidth / 2f;
        float cy = textY - textAscent / 2f;
        float radius = Math.max(1f, textWidth / 2f);
        Shader shader =
            new RadialGradient(
                cx, cy, radius, new int[] {textColor, gradientColor}, null, Shader.TileMode.CLAMP);
        textPaint.setShader(shader);
      } else { // horizontal
        Shader shader =
            new LinearGradient(
                textX,
                textY,
                textX + textWidth,
                textY,
                new int[] {textColor, gradientColor},
                null,
                Shader.TileMode.CLAMP);
        textPaint.setShader(shader);
      }
    }

    textCanvas.drawText(word, textX, textY, textPaint);
    canvas.drawBitmap(textLayer, 0, 0, null);

    // Extract pixels from final canvas
    int[] pixels = new int[size * size];
    bmp.getPixels(pixels, 0, size, 0, 0, size, size);

    // Build text mask
    int[] textLayerPixels = new int[size * size];
    textLayer.getPixels(textLayerPixels, 0, size, 0, 0, size, size);
    boolean[] tMask = new boolean[size * size];
    for (int i = 0; i < textLayerPixels.length; i++) {
      tMask[i] = ((textLayerPixels[i] >>> 24) & 0xFF) > 10;
    }
    this.textMask = tMask;

    // Build image mask
    boolean[] iMask = new boolean[size * size];
    if (imageLayer != null) {
      int[] imageLayerPixels = new int[size * size];
      imageLayer.getPixels(imageLayerPixels, 0, size, 0, 0, size, size);
      for (int i = 0; i < imageLayerPixels.length; i++) {
        iMask[i] = ((imageLayerPixels[i] >>> 24) & 0xFF) > 10;
      }
      imageLayer.recycle();
    }
    this.imageMask = iMask;

    textLayer.recycle();
    bmp.recycle(); // We only need the pixels, not the bitmap

    return pixels;
  }

  /**
   * Computes the texture pixel index for a surface hit.
   *
   * <p>Legacy mode: the direction comes from the world-space normal, so the texture is fixed in
   * world space. Follow mode: the direction comes from the hit position in object space, so the
   * texture moves together with the sphere (rotate, scale and translate are all handled by the
   * received world-to-object matrix).
   */
  private int getTextureIndex(Point3 worldPoint, Vector3 worldNormal) {
    ScratchData scratch = threadLocalScratch.get();

    boolean haveDirection = false;
    if (followObjectRotation && worldPoint != null) {
      // The stored matrix maps world space to object space, use it as is
      inverseObjectTransform.transformPoint(worldPoint, scratch.tempLocalPoint);
      double lx = scratch.tempLocalPoint.x;
      double ly = scratch.tempLocalPoint.y;
      double lz = scratch.tempLocalPoint.z;
      if (lx * lx + ly * ly + lz * lz > 1e-12) {
        scratch.tempDir.set(lx, ly, lz).normalize();
        haveDirection = true;
      }
    }
    if (!haveDirection) {
      scratch.tempDir.set(worldNormal.x, worldNormal.y, worldNormal.z).normalize();
    }

    double phi = Math.atan2(scratch.tempDir.z, scratch.tempDir.x);
    double theta = Math.acos(clamp(scratch.tempDir.y, -1.0, 1.0));

    double u = 1.0 - (phi + Math.PI) / (2 * Math.PI);
    double v = theta / Math.PI;

    u = Math.max(0, Math.min(1, u));
    v = Math.max(0, Math.min(1, v));

    int texX = (int) (u * textureSize);
    int texY = (int) (v * textureSize);
    texX = Math.min(texX, textureSize - 1);
    texY = Math.min(texY, textureSize - 1);

    return texY * textureSize + texX;
  }

  private int getTextureColorAt(int index) {
    if (texturePixels == null) return 0x00000000;
    return texturePixels[index];
  }

  private boolean isSpecialPixelAt(int index) {
    if (textMask == null && imageMask == null) return false;

    if (textMask != null && index < textMask.length && textMask[index]) return true;
    if (imageMask != null && index < imageMask.length && imageMask[index]) return true;

    return false;
  }

  private static double clamp(double value, double min, double max) {
    return Math.max(min, Math.min(max, value));
  }

  /**
   * Receives the inverse (world to object) transform, the same convention WordMaterial uses. The
   * matrix is stored as received and is not inverted again. The forward (object to world) matrix
   * is derived from it.
   */
  @Override
  public void setObjectTransform(Matrix4 tm) {
    this.inverseObjectTransform = (tm != null) ? tm : new Matrix4();
    Matrix4 forward = this.inverseObjectTransform.inverse();
    this.objectTransform = (forward != null) ? forward : new Matrix4();
  }

  public void setFollowObjectRotation(boolean followObjectRotation) {
    this.followObjectRotation = followObjectRotation;
  }

  public boolean isFollowObjectRotation() {
    return followObjectRotation;
  }

  @Override
  public int getColorAt(Point3 point, Vector3 normal, Light light, Point3 viewerPoint) {
    int index = getTextureIndex(point, normal);
    int texColor = getTextureColorAt(index);
    int alpha = (texColor >> 24) & 0xFF;

    if (isSpecialPixelAt(index)) {
      currentTransparency = 0.0;
      return 0xFF000000 | (texColor & 0x00FFFFFF);
    }

    if (alpha < 5) {
      currentTransparency = 1.0;
      return 0x00000000;
    }

    currentTransparency = 1.0 - (alpha / 255.0);

    int litColor = applyDimmedLighting(texColor, point, normal, light, viewerPoint);
    return (alpha << 24) | (litColor & 0x00FFFFFF);
  }

  private int applyDimmedLighting(
      int baseColor, Point3 point, Vector3 normal, Light light, Point3 viewerPoint) {
    ScratchData scratch = threadLocalScratch.get();
    scratch.tempNormal.set(normal).normalize();

    LightProperties.getLightProperties(light, point, scratch.tempLightProps);
    scratch.tempLightDir.set(scratch.tempLightProps.direction);

    int lightColor = scratch.tempLightProps.color;
    double intensity = scratch.tempLightProps.intensity;

    int baseR = (baseColor >> 16) & 0xFF;
    int baseG = (baseColor >> 8) & 0xFF;
    int baseB = baseColor & 0xFF;

    int lightR = (lightColor >> 16) & 0xFF;
    int lightG = (lightColor >> 8) & 0xFF;
    int lightB = lightColor & 0xFF;

    double diffuse = Math.max(0.0, scratch.tempNormal.dot(scratch.tempLightDir));

    double specular = 0.0;
    if (reflectivity > 0.0 && viewerPoint != null) {
      scratch.tempViewerPoint.set(viewerPoint);
      scratch.tempViewDir.set(scratch.tempViewerPoint).subtract(point).normalize();
      scratch
          .tempReflectDir
          .set(scratch.tempLightDir)
          .negate()
          .reflect(scratch.tempNormal, scratch.tempReflectDir);
      double RdotV = Math.max(0.0, scratch.tempReflectDir.dot(scratch.tempViewDir));
      specular = Math.pow(RdotV, 32.0) * reflectivity * 0.3;
    }

    double ambient = 0.1;
    double totalLight = ambient + intensity * (0.5 * diffuse + specular);
    totalLight = Math.min(1.0, totalLight);

    int r = (int) Math.min(255, baseR * totalLight * lightR / 255.0);
    int g = (int) Math.min(255, baseG * totalLight * lightG / 255.0);
    int b = (int) Math.min(255, baseB * totalLight * lightB / 255.0);

    return (0xFF << 24) | (r << 16) | (g << 8) | b;
  }

  @Override
  public double getTransparency() {
    return currentTransparency;
  }

  @Override
  public double getReflectivity() {
    return reflectivity;
  }

  @Override
  public double getIndexOfRefraction() {
    return ior;
  }

  public void setImagePath(String npath) {
    this.imagePath = npath;
  }

  public String getImagePath() {
    return this.imagePath;
  }

  public String getWord() {
    return word;
  }

  public int getTextColor() {
    return textColor;
  }

  public int getGradientColor() {
    return gradientColor;
  }

  public String getGradientType() {
    return gradientType;
  }

  public int getBgColor() {
    return bgColor;
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

  public int getUOffset() {
    return uOffset;
  }

  public int getVOffset() {
    return vOffset;
  }

  public Bitmap getImageObject() {
    return imageObject;
  }

  public int getImageWidth() {
    return imageWidth;
  }

  public int getImageHeight() {
    return imageHeight;
  }

  public int getImageUOffset() {
    return imageUOffset;
  }

  public int getImageVOffset() {
    return imageVOffset;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("SphereWordTextureMaterial spherewordtexturematerial {\n");
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
    sb.append("        reflectivity = ").append(reflectivity).append(";\n");
    sb.append("        ior = ").append(ior).append(";\n");
    sb.append("        transparency = ").append(baseTransparency).append(";\n");
    sb.append("        uOffset = ").append(uOffset).append(";\n");
    sb.append("        vOffset = ").append(vOffset).append(";\n");
    sb.append("        imageWidth = ").append(imageWidth).append(";\n");
    sb.append("        imageHeight = ").append(imageHeight).append(";\n");
    sb.append("        imageUOffset = ").append(imageUOffset).append(";\n");
    sb.append("        imageVOffset = ").append(imageVOffset).append(";\n");
    sb.append("        followObjectRotation = ").append(followObjectRotation).append(";\n");
    sb.append("    }");
    return sb.toString();
  }
}
