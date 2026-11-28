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
 *
 * <p>Matrix convention: the matrix received through {@link #setObjectTransform(Matrix4)} is the
 * inverse (world space to object space) transform, the same convention WordMaterial uses. The
 * texture is looked up from the object-space hit position, so it follows rotate, scale and
 * translate of the shape. Lighting always uses the world-space normal.
 *
 * <p>Alpha: when the diffuse color is fully opaque (0xFF alpha, the default) the material behaves
 * exactly as before. When the diffuse color has an alpha below 0xFF, the empty parts of the texture
 * show the diffuse color with that alpha, so a diffuse color of 0x00000000 is fully transparent and
 * only the text and image stay visible.
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

  // Per-pixel transparency, set by getColorAt (starts at the base transparency)
  private double currentTransparency;

  // objectTransform is object -> world, inverseTransform is world -> object (as received),
  // inverseTransposeTransform maps world-space normals to object space.
  private Matrix4 objectTransform;
  private Matrix4 inverseTransform;
  private Matrix4 inverseTransposeTransform;

  private final int specR, specG, specB;

  // ===== GC FRIENDLY: PRE-ALLOCATED TEMP OBJECTS =====
  private final Point3 tempLocalPoint = new Point3();
  private final Vector3 tempLocalNormal = new Vector3();
  private final Vector3 tempWorldNormal = new Vector3();
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
    this.currentTransparency = this.transparency;

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
    result = result.replace("AE", "Æ");
    result = result.replace("O/", "Ø");
    result = result.replace("A0", "Å");
    result = result.replace("ae", "æ");
    result = result.replace("o/", "ø");
    result = result.replace("a0", "å");
    return result;
  }

  /**
   * Receives the inverse (world to object) transform, the same convention WordMaterial uses. The
   * matrix is stored as received and is not inverted again. The forward matrix and the normal
   * matrix are derived from it.
   */
  @Override
  public void setObjectTransform(Matrix4 tm) {
    Matrix4 given = (tm != null) ? tm : Matrix4.identity();
    this.inverseTransform = given;

    Matrix4 forward = given.inverse();
    this.objectTransform = (forward != null) ? forward : Matrix4.identity();

    Matrix4 normalMatrix = given.inverseTransposeForNormal();
    this.inverseTransposeTransform = (normalMatrix != null) ? normalMatrix : Matrix4.identity();
  }

  /** True when the diffuse color has an alpha below 0xFF, which enables per-pixel transparency. */
  private boolean isAlphaAwareDiffuse() {
    return ((diffuseColorPacked >>> 24) & 0xFF) != 0xFF;
  }

  /**
   * Fills tempLocalNormal with the object-space direction used for the texture lookup. The
   * object-space hit position is used (the sphere is centered at the local origin), so rotate,
   * scale and translate of the shape are all followed. The transformed normal is only a fallback.
   */
  private void resolveLocalDirection(Point3 point, Vector3 normal) {
    inverseTransform.transformPoint(point, tempLocalPoint);

    double lx = tempLocalPoint.x;
    double ly = tempLocalPoint.y;
    double lz = tempLocalPoint.z;

    if (lx * lx + ly * ly + lz * lz > 1e-12) {
      tempLocalNormal.set(lx, ly, lz);
    } else {
      inverseTransposeTransform.transformDirection(normal, tempTransformedNormal);
      tempLocalNormal.set(tempTransformedNormal);
    }
    tempLocalNormal.normalize();
  }

  @Override
  public int getColorAt(Point3 point, Vector3 normal, Light light, Point3 viewerPos) {
    // World-space normal: used for lighting only
    tempWorldNormal.set(normal);
    tempWorldNormal.normalize();

    // Object-space direction: used for the texture lookup only
    resolveLocalDirection(point, normal);

    int baseColor = getTextureColor(tempLocalNormal);
    int baseAlpha = (baseColor >>> 24) & 0xFF;

    int outAlpha;
    if (isAlphaAwareDiffuse()) {
      if (baseAlpha < 5) {
        currentTransparency = 1.0;
        return 0x00000000;
      }
      currentTransparency = 1.0 - (1.0 - transparency) * (baseAlpha / 255.0);
      outAlpha = baseAlpha << 24;
    } else {
      currentTransparency = transparency;
      outAlpha = 0xFF000000;
    }

    int diffuseR = (baseColor >> 16) & 0xFF;
    int diffuseG = (baseColor >> 8) & 0xFF;
    int diffuseB = baseColor & 0xFF;

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
      return (outAlpha | (ambR << 16) | (ambG << 8) | ambB);
    }

    double NdotL = Math.max(0, tempWorldNormal.dot(tempLightDir));
    double diffFactor = diffuseCoefficient * NdotL * intensity;
    int diffR2 = (int) (diffuseR * diffFactor * lightR / 255.0);
    int diffG2 = (int) (diffuseG * diffFactor * lightG / 255.0);
    int diffB2 = (int) (diffuseB * diffFactor * lightB / 255.0);

    int sR = 0;
    int sG = 0;
    int sB = 0;
    if (viewerPos != null) {
      tempViewerPoint.set(viewerPos);
      tempViewDir.set(tempViewerPoint);
      tempViewDir.subtract(point);
      tempViewDir.normalize();

      tempReflectDir.set(tempLightDir);
      tempReflectDir.negate();
      tempReflectDir.reflect(tempWorldNormal, tempReflectDir);

      double RdotV = Math.max(0, tempReflectDir.dot(tempViewDir));
      double specFactor = Math.pow(RdotV, shininess) * specularCoefficient * intensity;
      sR = (int) (specR * specFactor * lightR / 255.0);
      sG = (int) (specG * specFactor * lightG / 255.0);
      sB = (int) (specB * specFactor * lightB / 255.0);
    }

    int r = ambR + diffR2 + sR;
    int g = ambG + diffG2 + sG;
    int b = ambB + diffB2 + sB;

    r = clamp(r);
    g = clamp(g);
    b = clamp(b);

    return (outAlpha | (r << 16) | (g << 8) | b);
  }

  /**
   * Returns the base color (ARGB) for an object-space direction.
   *
   * <p>Opaque diffuse (legacy): texture pixels with alpha below 10 use the diffuse color, other
   * pixels use the texture color as is. Diffuse with alpha below 0xFF: the texture is composited
   * over the diffuse color, so the result keeps a meaningful alpha (0 where nothing is drawn and
   * the diffuse color is 0x00000000).
   */
  private int getTextureColor(Vector3 localDir) {
    if (texturePixels == null) {
      return diffuseColorPacked;
    }

    double phi = Math.atan2(localDir.z, localDir.x);
    double theta = Math.asin(clamp(localDir.y, -1.0, 1.0));

    double u = 1.0 - (phi + Math.PI) / (2 * Math.PI);
    double v = (theta + Math.PI / 2) / Math.PI;
    v = 1.0 - v;
    u = (u + 0.25) % 1.0;

    int texX = (int) (u * texWidth);
    texX = texX % texWidth;
    if (texX < 0) texX += texWidth;

    int texY = (int) (v * texHeight);

    int raw = 0x00000000;
    if (texY >= 0 && texY < texHeight) {
      raw = texturePixels[texY * texWidth + texX];
    }
    int rawAlpha = (raw >>> 24) & 0xFF;

    if (!isAlphaAwareDiffuse()) {
      if (rawAlpha < 10) {
        return diffuseColorPacked;
      }
      return raw;
    }

    // Alpha-aware path: composite the texture over the diffuse color
    if (rawAlpha == 0) {
      return diffuseColorPacked;
    }
    if (rawAlpha == 255) {
      return raw;
    }

    int diffuseAlpha = (diffuseColorPacked >>> 24) & 0xFF;
    if (diffuseAlpha == 0) {
      return raw;
    }

    double ap = rawAlpha / 255.0;
    double ad = diffuseAlpha / 255.0;
    double ao = ap + ad * (1.0 - ap);

    double pr = (raw >> 16) & 0xFF;
    double pg = (raw >> 8) & 0xFF;
    double pb = raw & 0xFF;
    double dr = (diffuseColorPacked >> 16) & 0xFF;
    double dg = (diffuseColorPacked >> 8) & 0xFF;
    double db = diffuseColorPacked & 0xFF;

    int r = clamp((int) Math.round((pr * ap + dr * ad * (1.0 - ap)) / ao));
    int g = clamp((int) Math.round((pg * ap + dg * ad * (1.0 - ap)) / ao));
    int b = clamp((int) Math.round((pb * ap + db * ad * (1.0 - ap)) / ao));
    int a = clamp((int) Math.round(ao * 255.0));

    return (a << 24) | (r << 16) | (g << 8) | b;
  }

  private int clamp(int value) {
    return Math.max(0, Math.min(255, value));
  }

  private static double clamp(double value, double min, double max) {
    return Math.max(min, Math.min(max, value));
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
    return currentTransparency;
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
