package net.murat.material;

import android.graphics.Bitmap;
import android.graphics.Canvas;
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
 * Represents a physically-inspired dielectric (glass-like) material. Fully self-contained shading
 * model with specular color control. Android adaptation: Uses Canvas and Paint for overlay texture
 * generation.
 */
public class DielectricTextMaterial implements Material {

  // ============================================================
  // DIELECTRIC PROPERTIES
  // ============================================================
  private int baseColor;
  private int specularColor;
  private double transparency;
  private double reflectivity;
  private double ior;
  private double shininess = 300.0;
  private double specularStrength = 3.0;

  private int baseR, baseG, baseB, baseA;
  private int specR, specG, specB, specA;
  private double baseAlphaFactor;
  private double specAlphaFactor;

  // ============================================================
  // OVERLAY PROPERTIES
  // ============================================================
  private String overlayText;
  private int textColorPacked;
  private int gradientColorPacked;
  private String gradientType;
  private int bgColorPacked;
  private String fontFamily;
  private int fontStyle;
  private int fontSize;
  private int textUOffset;
  private int textVOffset;

  private Bitmap overlayImage;
  private int imageWidth;
  private int imageHeight;
  private int imageUOffset;
  private int imageVOffset;
  private String imagePath = "textures/turkeyFlag.png";

  private Bitmap texture;
  private int[] texturePixels;
  private int texWidth;
  private int texHeight;
  private boolean hasOverlay = false;

  // ============================================================
  // TRANSFORM MATRICES
  // ============================================================
  private Matrix4 objectTransform;
  private Matrix4 inverseObjectTransform;
  private Matrix4 inverseTransposeTransform;
  private boolean hasTransform = false;

  // ============================================================
  // GC-FRIENDLY TEMP OBJECTS
  // ============================================================
  private final Vector3 tempNormal = new Vector3();
  private final Vector3 tempViewDir = new Vector3();
  private final Vector3 tempLightDir = new Vector3();
  private final Vector3 tempHalfVec = new Vector3();
  private final Point3 tempPoint = new Point3();
  private final LightProperties tempLightProps = new LightProperties();
  private final Vector3 tempTexNormal = new Vector3();

  // ============================================================
  // CONSTRUCTORS
  // ============================================================

  public DielectricTextMaterial() {
    this(0xFFFFFFFF, 0xFFFFFFFF, 0.5, 0.25, 1.52, 300.0, 3.0);
  }

  public DielectricTextMaterial(
      int baseColor,
      int specularColor,
      double transparency,
      double reflectivity,
      double ior,
      double shininess,
      double specularStrength) {
    this(
        baseColor,
        specularColor,
        transparency,
        reflectivity,
        ior,
        shininess,
        specularStrength,
        null,
        0xFFFFFFFF,
        0x00000000,
        "horizontal",
        0x00000000,
        "Arial",
        Typeface.NORMAL,
        48,
        0,
        0,
        null,
        0,
        0,
        0,
        0);
  }

  public DielectricTextMaterial(
      int baseColor,
      double transparency,
      double reflectivity,
      double ior,
      double shininess,
      double specularStrength) {
    this(baseColor, 0xFFFFFFFF, transparency, reflectivity, ior, shininess, specularStrength);
  }

  public DielectricTextMaterial(
      int baseColor,
      int specularColor,
      double transparency,
      double reflectivity,
      double ior,
      double shininess,
      double specularStrength,
      String overlayText,
      int textColor,
      int gradientColor,
      String gradientType,
      int bgColor,
      String fontFamily,
      int fontStyle,
      int fontSize,
      int textUOffset,
      int textVOffset,
      Bitmap overlayImage,
      int imageWidth,
      int imageHeight,
      int imageUOffset,
      int imageVOffset) {

    this.baseColor = baseColor;
    this.specularColor = specularColor;
    this.transparency = transparency;
    this.reflectivity = reflectivity;
    this.ior = ior;
    this.shininess = shininess;
    this.specularStrength = specularStrength;

    setBaseColor(baseColor);
    setSpecularColor(specularColor);

    this.objectTransform = new Matrix4();
    this.inverseObjectTransform = new Matrix4();
    this.inverseTransposeTransform = new Matrix4();

    this.overlayText = overlayText;
    this.textColorPacked = textColor;
    this.gradientColorPacked = gradientColor;
    this.gradientType = gradientType != null ? gradientType : "horizontal";
    this.bgColorPacked = bgColor;
    this.fontFamily = fontFamily != null ? fontFamily.replaceAll("_", " ") : "Arial";
    this.fontStyle = fontStyle;
    this.fontSize = fontSize > 0 ? fontSize : 48;
    this.textUOffset = textUOffset;
    this.textVOffset = textVOffset;

    this.overlayImage = overlayImage;
    this.imageWidth = imageWidth;
    this.imageHeight = imageHeight;
    this.imageUOffset = imageUOffset;
    this.imageVOffset = imageVOffset;

    this.hasOverlay =
        (this.overlayText != null && !this.overlayText.isEmpty()) || (this.overlayImage != null);

    if (this.hasOverlay) {
      this.texture = createOverlayTexture();
      if (this.texture != null) {
        this.texWidth = this.texture.getWidth();
        this.texHeight = this.texture.getHeight();
        this.texturePixels = new int[texWidth * texHeight];
        this.texture.getPixels(this.texturePixels, 0, texWidth, 0, 0, texWidth, texHeight);
      }
    }
  }

  // ============================================================
  // OVERLAY TEXTURE CREATION (Android Canvas)
  // ============================================================

  private Bitmap createOverlayTexture() {
    final int size = 1024;
    Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(bitmap);

    // 1. Background
    canvas.drawColor(bgColorPacked);

    // 2. Overlay Image
    if (overlayImage != null) {
      int imgX = ((size - imageWidth) / 2) + imageUOffset;
      int imgY = ((size - imageHeight) / 2) + imageVOffset;
      Rect dstRect = new Rect(imgX, imgY, imgX + imageWidth, imgY + imageHeight);
      Paint imgPaint = new Paint();
      imgPaint.setFilterBitmap(true);
      canvas.drawBitmap(overlayImage, null, dstRect, imgPaint);
    }

    // 3. Overlay Text
    if (overlayText != null && !overlayText.isEmpty()) {
      Paint textPaint = new Paint();
      textPaint.setAntiAlias(true);

      // Map AWT Font styles to Android Typeface
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

      // Measure text
      float textWidth = textPaint.measureText(overlayText);
      Paint.FontMetrics fm = textPaint.getFontMetrics();
      float textHeight = fm.descent - fm.ascent;

      float x = ((size - textWidth) / 2f) + textUOffset;
      float y = ((size - textHeight) / 2f) + (fm.ascent * 2) + (textHeight / 3f) + textVOffset;

      // Apply gradient or solid color
      if (gradientColorPacked != 0x00000000) {
        int[] colors = new int[] {textColorPacked, gradientColorPacked};
        float[] positions = new float[] {0f, 1f};
        Shader shader = null;

        String gt = gradientType != null ? gradientType.toLowerCase() : "horizontal";
        if ("vertical".equals(gt)) {
          shader =
              new LinearGradient(
                  x,
                  y - fm.ascent,
                  x,
                  y - fm.ascent + textHeight / 2,
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

      canvas.drawText(overlayText, x, y, textPaint);
    }

    return bitmap;
  }

  // ============================================================
  // TEXTURE MAPPING
  // ============================================================

  private int getTextureColor(Vector3 worldNormal) {
    if (texturePixels == null || !hasOverlay) return 0x00000000;

    double nx = worldNormal.x;
    double ny = worldNormal.y;
    double nz = worldNormal.z;

    double phi = Math.atan2(nz, nx);
    double theta = Math.asin(Math.max(-1.0, Math.min(1.0, ny)));

    double u = 1.0 - (phi + Math.PI) / (2 * Math.PI);
    double v = 1.0 - (theta + Math.PI / 2) / Math.PI;

    u = (u + 0.25) % 1.0;

    int texX = (int) (u * texWidth) % texWidth;
    if (texX < 0) texX += texWidth;

    int texY = (int) (v * texHeight);
    if (texY < 0 || texY >= texHeight) {
      return 0x00000000;
    }

    return texturePixels[texY * texWidth + texX]; // Fast array lookup
  }

  // ============================================================
  // MAIN SHADING
  // ============================================================

  @Override
  public int getColorAt(Point3 point, Vector3 normal, Light light, Point3 viewerPoint) {
    Point3 worldPoint = tempPoint;
    Vector3 worldNormal = tempNormal;

    if (hasTransform) {
      worldPoint.set(point);
      objectTransform.transformPoint(point, worldPoint);

      worldNormal.set(normal);
      objectTransform.transformNormal(normal, worldNormal);
      worldNormal.normalize();
    } else {
      worldPoint.set(point);
      worldNormal.set(normal).normalize();
    }

    LightProperties.getLightProperties(light, worldPoint, tempLightProps);
    tempLightDir.set(tempLightProps.direction).normalize();

    int lightColor = tempLightProps.color;
    int lightR = (lightColor >> 16) & 0xFF;
    int lightG = (lightColor >> 8) & 0xFF;
    int lightB = lightColor & 0xFF;

    tempViewDir.set(viewerPoint).subtract(worldPoint).normalize();

    tempHalfVec.set(tempLightDir).add(tempViewDir).normalize();
    double specAngle = Math.max(0.0, worldNormal.dot(tempHalfVec));
    double specular = Math.pow(specAngle, shininess) * specularStrength * tempLightProps.intensity;

    double specScale = 180.0;
    double specR_f = specScale * specular * lightR / 255.0 * specR / 255.0 * specAlphaFactor;
    double specG_f = specScale * specular * lightG / 255.0 * specG / 255.0 * specAlphaFactor;
    double specB_f = specScale * specular * lightB / 255.0 * specB / 255.0 * specAlphaFactor;

    double nv = Math.max(0.0, worldNormal.dot(tempViewDir));
    double fresnel = Vector3.calculateFresnel(tempViewDir, worldNormal, ior);
    double rt = 1.0 - nv;
    double rim = rt * rt * (3.0 - 2.0 * rt);
    double internalGlow = Math.pow(nv, 2.0);

    double ambient = 0.05;
    double totalLight =
        ambient
            + (transparency * internalGlow * 0.05)
            + (rim * reflectivity * 0.2)
            + (fresnel * 0.1);

    int r = (int) (baseR * totalLight * lightR / 255.0 * baseAlphaFactor + specR_f);
    int g = (int) (baseG * totalLight * lightG / 255.0 * baseAlphaFactor + specG_f);
    int b = (int) (baseB * totalLight * lightB / 255.0 * baseAlphaFactor + specB_f);

    r = Math.min(255, Math.max(0, r));
    g = Math.min(255, Math.max(0, g));
    b = Math.min(255, Math.max(0, b));

    if (hasOverlay && texturePixels != null) {
      int texColor = getTextureColor(worldNormal);
      int texA = (texColor >> 24) & 0xFF;

      if (texA > 0) {
        int texR = (texColor >> 16) & 0xFF;
        int texG = (texColor >> 8) & 0xFF;
        int texB = texColor & 0xFF;

        double alphaFactor = texA / 255.0;
        double invAlpha = 1.0 - alphaFactor;

        r = (int) (r * invAlpha + texR * alphaFactor);
        g = (int) (g * invAlpha + texG * alphaFactor);
        b = (int) (b * invAlpha + texB * alphaFactor);

        r = Math.min(255, Math.max(0, r));
        g = Math.min(255, Math.max(0, g));
        b = Math.min(255, Math.max(0, b));
      }
    }

    return (0xFF000000 | (r << 16) | (g << 8) | b);
  }

  // ============================================================
  // GETTERS / SETTERS
  // ============================================================

  @Override
  public double getTransparency() {
    return transparency;
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
  public void setObjectTransform(Matrix4 tm) {
    if (tm == null) {
      this.objectTransform = new Matrix4();
      this.hasTransform = false;
    } else {
      this.objectTransform = tm;
      this.hasTransform = true;
    }
    this.inverseObjectTransform = this.objectTransform.inverse();
    this.inverseTransposeTransform = this.inverseObjectTransform.inverseTransposeForNormal();
  }

  public int getBaseColor() {
    return baseColor;
  }

  public void setBaseColor(int baseColor) {
    this.baseColor = baseColor;
    this.baseA = (baseColor >>> 24) & 0xFF;
    this.baseR = (baseColor >> 16) & 0xFF;
    this.baseG = (baseColor >> 8) & 0xFF;
    this.baseB = baseColor & 0xFF;
    this.baseAlphaFactor = baseA / 255.0;
  }

  public int getSpecularColor() {
    return specularColor;
  }

  public void setSpecularColor(int specularColor) {
    this.specularColor = specularColor;
    this.specA = (specularColor >>> 24) & 0xFF;
    this.specR = (specularColor >> 16) & 0xFF;
    this.specG = (specularColor >> 8) & 0xFF;
    this.specB = specularColor & 0xFF;
    this.specAlphaFactor = specA / 255.0;
  }

  public int getBaseColorAlpha() {
    return baseA;
  }

  public int getSpecularColorAlpha() {
    return specA;
  }

  public void setTransparency(double transparency) {
    this.transparency = transparency;
  }

  public void setReflectivity(double reflectivity) {
    this.reflectivity = reflectivity;
  }

  public void setIndexOfRefraction(double ior) {
    this.ior = ior;
  }

  public void setShininess(double shininess) {
    this.shininess = shininess;
  }

  public double getShininess() {
    return shininess;
  }

  public void setSpecularStrength(double specularStrength) {
    this.specularStrength = specularStrength;
  }

  public double getSpecularStrength() {
    return specularStrength;
  }

  public String getOverlayText() {
    return overlayText;
  }

  public Bitmap getOverlayImage() {
    return overlayImage;
  }

  public String getImagePath() {
    return imagePath;
  }

  public void setImagePath(String imagePath) {
    this.imagePath = imagePath;
  }

  public boolean hasOverlay() {
    return hasOverlay;
  }

  // ============================================================
  // toString()
  // ============================================================

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("DielectricTextMaterial dielectrictextmaterial {\n");
    sb.append("        baseColor = ").append(ColorUtil.toColorString(baseColor)).append(";\n");
    sb.append("        specularColor = ")
        .append(ColorUtil.toColorString(specularColor))
        .append(";\n");
    sb.append("        transparency = ").append(transparency).append(";\n");
    sb.append("        reflectivity = ").append(reflectivity).append(";\n");
    sb.append("        ior = ").append(ior).append(";\n");
    sb.append("        shininess = ").append(shininess).append(";\n");
    sb.append("        specularStrength = ").append(specularStrength).append(";\n");

    if (hasOverlay) {
      sb.append("\n");
      sb.append("        // ===== OVERLAY PROPERTIES =====\n");
      sb.append("        imagePath = ").append(imagePath).append(";\n");
      sb.append("        overlayText = ")
          .append(overlayText != null ? overlayText : "null")
          .append(";\n");
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
      sb.append("        imageWidth = ").append(imageWidth).append(";\n");
      sb.append("        imageHeight = ").append(imageHeight).append(";\n");
      sb.append("        imageUOffset = ").append(imageUOffset).append(";\n");
      sb.append("        imageVOffset = ").append(imageVOffset).append(";\n");
    }

    sb.append("    }");
    return sb.toString();
  }
}
