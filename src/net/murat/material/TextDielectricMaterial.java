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
 * TextDielectricMaterial - ULTRA OPTIMIZED Android adaptation: uses Bitmap, Canvas and Paint for
 * texture generation.
 *
 * <p>Compatibility contract: every public constructor and method signature, the constructor
 * parameter order and the layout of {@link #toString()} are unchanged. All additions are new
 * methods or private members.
 *
 * <p>Default behaviour ("standard mode") keeps the original look: a 1024x1024 texture,
 * nearest-texel lookup and a hard alpha threshold between glass and ink. Optional extras, all OFF
 * by default:
 *
 * <ul>
 *   <li>{@link #setHighQuality(boolean)}: 2048x1024 texture (matches the 2:1 sphere mapping),
 *       bilinear lookup, soft glass/ink blend and filtered image down-scaling.
 *   <li>{@link #setFullWidthGradient(boolean)}: the text gradient spans the whole text instead of
 *       only its first third.
 * </ul>
 *
 * <p>Alpha handling: the alpha channel of every colour parameter is honoured.
 *
 * <ul>
 *   <li>Texture colours (textColor, gradientColor, bgColor, image): in standard mode a texel with
 *       alpha equal/bigger 128 is ink and is blended over the glass with its own opacity (alpha / 255); a
 *       texel with alpha less 128 stays pure glass. In high quality mode the interpolated alpha is
 *       used directly as coverage, so the whole 0..255 range works.
 *   <li>Semi-transparent ink also raises the transparency reported to the renderer, so rays
 *       continue through the pixel.
 *   <li>Filter colours: their alpha scales the tint strength. For backwards compatibility an alpha
 *       of 0 (the old 0xRRGGBB style) is treated as fully opaque; use at least 0x01 to get a real
 *       alpha. The same rule applies to diffuseColor when no texture exists.
 * </ul>
 *
 * <p>Dielectric filters (filterColorInside / filterColorOutside) are always active: on glass
 * pixels the colour is blended toward the filter colour, on ink pixels (text, image, bgColor) a
 * gentle multiplicative tint is applied so detail and sharpness are preserved.
 *
 * <p>Thread safety: shading uses no shared mutable scratch state, so one instance may be shared
 * by several render threads. Setters that rebuild the texture or change tuning values must be
 * called before rendering starts.
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

  // Texel storage. The Bitmap itself is recycled right after copying (halves memory use).
  private int[] texturePixels;
  private int texWidth;
  private int texHeight;

  private String imagePath = "textures/turkeyFlag.png";

  // Optional quality switches (see class comment). Both default to the original behaviour.
  private boolean highQuality = false;
  private boolean fullWidthGradient = false;

  // ============================================================
  // PER-THREAD SCRATCH STATE
  // ============================================================
  /**
   * Holds everything that used to live in shared instance buffers. One instance per render thread,
   * so concurrent getColorAt() calls can never corrupt each other.
   */
  private static final class Scratch {
    final double[] n = new double[3]; // transformed normal
    // [0..2] R, G, B (0..255); [3] ink coverage (0..1); [4] ink opacity (0..1)
    final double[] tex = new double[5];
    double reflectivity; // value of the last getColorAt() on this thread
    double transparency; // value of the last getColorAt() on this thread
  }

  private final ThreadLocal<Scratch> scratch =
      new ThreadLocal<Scratch>() {
        @Override
        protected Scratch initialValue() {
          Scratch s = new Scratch();
          s.reflectivity = TextDielectricMaterial.this.reflectivity;
          s.transparency = TextDielectricMaterial.this.transparency;
          return s;
        }
      };

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

  // ============================================================
  // CONSTANTS
  // ============================================================
  private static final int STD_TEX_SIZE = 1024;
  private static final int HQ_TEX_WIDTH = 2048;
  private static final int HQ_TEX_HEIGHT = 1024;

  // NOTE: specularPow() is hand-unrolled for exactly this exponent (140 = 128 + 8 + 4).
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

  // ============================================================
  // FILTER TUNING (instance values, adjustable through setters)
  // ============================================================
  private static final double DEFAULT_FILTER_BASE_STRENGTH = 0.55;
  private static final double DEFAULT_FILTER_FRESNEL_BOOST = 0.45;
  private static final double DEFAULT_FILTER_MAX_STRENGTH = 0.90;
  private static final double DEFAULT_FILTER_INK_STRENGTH = 0.45;
  private static final double DEFAULT_FILTER_INSIDE_CUTOFF = 0.55;
  private static final double DEFAULT_FILTER_INSIDE_RAMP = 0.30;

  private double filterBaseStrength = DEFAULT_FILTER_BASE_STRENGTH; // tint amount on glass
  private double filterFresnelBoost = DEFAULT_FILTER_FRESNEL_BOOST; // extra tint at grazing angles
  private double filterMaxStrength = DEFAULT_FILTER_MAX_STRENGTH; // upper clamp for the tint
  private double filterInkStrength = DEFAULT_FILTER_INK_STRENGTH; // tint on text/image/bg
  private double filterInsideCutoff = DEFAULT_FILTER_INSIDE_CUTOFF; // inside/outside switch point
  private double filterInsideRamp = DEFAULT_FILTER_INSIDE_RAMP; // soft transition width

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

    // Null-safe: a missing word becomes empty text, a missing font a generic family.
    String converted = convertToNorwegianText(word);
    this.word = (converted != null ? converted : "").replaceAll("_", " ");
    this.fontFamily = (fontFamily != null ? fontFamily : "sans-serif").replaceAll("_", " ");

    this.textColorPacked = textColor;
    this.gradientColorPacked = gradientColor;
    this.gradientType = gradientType != null ? gradientType : "horizontal";
    this.bgColorPacked = bgColor;
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

    this.objectTransform = new Matrix4().identity();
    this.inverseTransform = new Matrix4().identity();

    rebuildTexture();
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

  /** Renders the texture, copies its texels into an int[] and releases the Bitmap. */
  private void rebuildTexture() {
    Bitmap bmp = createTexture();
    if (bmp == null) {
      texturePixels = null;
      return;
    }
    int w = bmp.getWidth();
    int h = bmp.getHeight();
    int[] pixels = new int[w * h];
    // getPixels() returns non-premultiplied ARGB, so colour and alpha stay independent.
    bmp.getPixels(pixels, 0, w, 0, 0, w, h);
    bmp.recycle();

    this.texWidth = w;
    this.texHeight = h;
    this.texturePixels = pixels;
  }

  private Bitmap createTexture() {
    final boolean hq = highQuality;
    final int w = hq ? HQ_TEX_WIDTH : STD_TEX_SIZE;
    final int h = hq ? HQ_TEX_HEIGHT : STD_TEX_SIZE;

    Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
    Canvas canvas = new Canvas(bmp);

    // 1. Clear transparent
    canvas.drawColor(Color.TRANSPARENT);

    // 2. Background color (its alpha is kept, so a semi-transparent bgColor stays semi-transparent)
    int bgA = (bgColorPacked >> 24) & 0xFF;
    if (bgA > 0) {
      canvas.drawColor(bgColorPacked);
    }

    // 3. Draw image object
    if (imageObject != null && imageWidth > 0 && imageHeight > 0) {
      int imgX = ((w - imageWidth) / 2) + imageUOffset;
      int imgY = ((h - imageHeight) / 2) + imageVOffset;

      if (hq) {
        // High quality: filtered halving steps first, then a 1:1 draw (no aliasing).
        Bitmap scaled = scaleHighQuality(imageObject, imageWidth, imageHeight);
        Paint imgPaint =
            new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
        canvas.drawBitmap(scaled, imgX, imgY, imgPaint);
        if (scaled != imageObject) scaled.recycle();
      } else {
        // Standard: exactly the original path.
        Rect dstRect = new Rect(imgX, imgY, imgX + imageWidth, imgY + imageHeight);
        Paint imgPaint = new Paint();
        imgPaint.setFilterBitmap(true);
        canvas.drawBitmap(imageObject, null, dstRect, imgPaint);
      }
    }

    // 4. Draw text
    Paint textPaint = new Paint();
    textPaint.setAntiAlias(true);
    if (hq) {
      textPaint.setSubpixelText(true);
      textPaint.setLinearText(true);
    }

    int tfStyle = Typeface.NORMAL;
    if ((fontStyle & 1) != 0) tfStyle |= Typeface.BOLD;
    if ((fontStyle & 2) != 0) tfStyle |= Typeface.ITALIC;

    Typeface tf;
    try {
      tf = Typeface.create(OurFontFactory.getFont(fontFamily), tfStyle);
    } catch (Exception e) {
      tf = Typeface.DEFAULT;
    }
    textPaint.setTypeface(tf);
    textPaint.setTextSize(fontSize);

    float textWidth = textPaint.measureText(word);
    Paint.FontMetrics fm = textPaint.getFontMetrics();
    float textHeight = fm.descent - fm.ascent;

    float x = ((w - textWidth) / 2f) + uOffset;
    float y = ((h - textHeight) / 2f) + (fm.ascent * 2f) + (textHeight / 3f) + vOffset;

    if (gradientColorPacked != 0x00000000) {
      // Gradient stop colours keep their alpha, so the gradient may fade in or out.
      int[] colors = new int[] {textColorPacked, gradientColorPacked};
      float[] positions = new float[] {0f, 1f};
      Shader shader;

      String gt = gradientType.toLowerCase();
      if (fullWidthGradient) {
        // Gradient spans the complete text box (top-left to bottom-right as needed).
        float top = y + fm.ascent;
        float bottom = y + fm.descent;
        if (gt.equals("vertical")) {
          shader = new LinearGradient(x, top, x, bottom, colors, positions, Shader.TileMode.CLAMP);
        } else if (gt.equals("diagonal")) {
          shader =
              new LinearGradient(
                  x, top, x + textWidth, bottom, colors, positions, Shader.TileMode.CLAMP);
        } else {
          shader =
              new LinearGradient(
                  x, top, x + textWidth, top, colors, positions, Shader.TileMode.CLAMP);
        }
      } else {
        // Original gradient geometry.
        if (gt.equals("vertical")) {
          shader =
              new LinearGradient(
                  x,
                  y - fm.ascent,
                  x,
                  y + textHeight / 2f,
                  colors,
                  positions,
                  Shader.TileMode.CLAMP);
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
      }
      textPaint.setShader(shader);
    } else {
      // setColor() keeps the alpha byte of textColorPacked.
      textPaint.setColor(textColorPacked);
    }

    canvas.drawText(word, x, y, textPaint);

    return bmp;
  }

  /** Down-scales in halving steps with filtering; a single big jump aliases badly. */
  private static Bitmap scaleHighQuality(Bitmap src, int dw, int dh) {
    Bitmap cur = src;
    while (cur.getWidth() / 2 >= dw && cur.getHeight() / 2 >= dh) {
      Bitmap next =
          Bitmap.createScaledBitmap(cur, cur.getWidth() / 2, cur.getHeight() / 2, true);
      if (cur != src) cur.recycle();
      cur = next;
    }
    if (cur.getWidth() != dw || cur.getHeight() != dh) {
      Bitmap next = Bitmap.createScaledBitmap(cur, dw, dh, true);
      if (cur != src) cur.recycle();
      cur = next;
    }
    return cur;
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
  // OPTIONAL QUALITY SWITCHES (call before rendering)
  // ============================================================

  /**
   * Enables or disables high quality mode (2048x1024 texture, bilinear lookup, soft glass/ink
   * blend, filtered image scaling). Default is false, which keeps the original look. Rebuilds the
   * texture when the value changes.
   */
  public void setHighQuality(boolean enabled) {
    if (this.highQuality != enabled) {
      this.highQuality = enabled;
      rebuildTexture();
    }
  }

  public boolean isHighQuality() {
    return highQuality;
  }

  /**
   * When true the text gradient spans the whole text instead of only its first third. Default is
   * false (original geometry). Rebuilds the texture when the value changes.
   */
  public void setFullWidthGradient(boolean enabled) {
    if (this.fullWidthGradient != enabled) {
      this.fullWidthGradient = enabled;
      rebuildTexture();
    }
  }

  public boolean isFullWidthGradient() {
    return fullWidthGradient;
  }

  // ============================================================
  // FILTER TUNING SETTERS (call before rendering)
  // ============================================================

  /** Tint amount on glass pixels, 0..1. Default 0.55. */
  public void setFilterBaseStrength(double v) {
    this.filterBaseStrength = clamp01(v);
  }

  /** Extra tint added by the Fresnel term (stronger at grazing angles), 0..1. Default 0.45. */
  public void setFilterFresnelBoost(double v) {
    this.filterFresnelBoost = clamp01(v);
  }

  /** Upper clamp of the total tint amount, 0..1. Default 0.90. */
  public void setFilterMaxStrength(double v) {
    this.filterMaxStrength = clamp01(v);
  }

  /**
   * How strongly the filter tints text / image / bgColor pixels, 0..1. 0 leaves them untouched,
   * 1 applies the full multiplicative tint. Default 0.45.
   */
  public void setFilterInkStrength(double v) {
    this.filterInkStrength = clamp01(v);
  }

  /** cos(theta) around which the inside/outside filters swap, 0..1. Default 0.55. */
  public void setFilterInsideCutoff(double v) {
    this.filterInsideCutoff = clamp01(v);
  }

  /** Width of the soft inside/outside transition, must be positive. Default 0.30. */
  public void setFilterInsideRamp(double v) {
    this.filterInsideRamp = (v > 1e-6) ? v : 1e-6;
  }

  private static double clamp01(double v) {
    if (v < 0.0) return 0.0;
    if (v > 1.0) return 1.0;
    return v;
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

  /**
   * Looks up the texture colour for a surface normal (spherical mapping) and stores it in
   * scratch.tex as R, G, B (0..255), ink coverage (0..1) and ink opacity (0..1).
   *
   * <p>Coverage says WHERE there is ink (as opposed to bare glass); opacity says how opaque that
   * ink is. The product of the two is the real amount of the pixel that is covered.
   *
   * <p>Standard mode: nearest texel, coverage is 1 when texel alpha >= 128 and 0 otherwise (the
   * original hard glass/ink split); opacity is the texel alpha for ink texels. High quality mode:
   * bilinear lookup, coverage is the smooth interpolated alpha and opacity is 1 (the alpha is
   * already contained in the coverage).
   */
  private void sampleTexture(Vector3 worldNormal, Scratch s) {
    final double[] tex = s.tex;
    final int[] pixels = texturePixels;
    final int tw = texWidth;
    final int th = texHeight;

    if (pixels == null) {
      // No texture: behave like the original (diffuse colour, fully covered). The alpha of the
      // diffuse colour is honoured; alpha 0 means "legacy 0xRRGGBB", i.e. opaque.
      tex[0] = (diffuseColorPacked >> 16) & 0xFF;
      tex[1] = (diffuseColorPacked >> 8) & 0xFF;
      tex[2] = diffuseColorPacked & 0xFF;
      tex[3] = 1.0;
      tex[4] = alpha01(diffuseColorPacked);
      return;
    }

    final double[] n = s.n;
    n[0] = worldNormal.x;
    n[1] = worldNormal.y;
    n[2] = worldNormal.z;

    if (objectTransform != null) {
      objectTransform.transformNormalAndNormalize(n);
    }

    double phi = Math.atan2(n[2], n[0]);
    double theta = Math.asin(Math.max(-1.0, Math.min(1.0, n[1])));

    double u = 1.0 - (phi + Math.PI) / (2 * Math.PI);
    double v = 1.0 - (theta + Math.PI / 2) / Math.PI;

    u = (u + 0.25) % 1.0;
    if (u < 0) u += 1.0;

    if (!highQuality) {
      // ---- Standard: nearest texel, original glass/ink split ----
      int texX = (int) (u * tw) % tw;
      if (texX < 0) texX += tw;

      int texY = (int) (v * th);
      int px = (texY < 0 || texY >= th) ? 0x00000000 : pixels[texY * tw + texX];

      int alpha = (px >> 24) & 0xFF;
      boolean isInk = alpha >= 128;
      tex[0] = (px >> 16) & 0xFF;
      tex[1] = (px >> 8) & 0xFF;
      tex[2] = px & 0xFF;
      tex[3] = isInk ? 1.0 : 0.0;
      tex[4] = isInk ? alpha / 255.0 : 1.0; // texel alpha becomes the ink opacity
      return;
    }

    // ---- High quality: bilinear, premultiplied-alpha aware ----
    double fx = u * tw - 0.5;
    double fy = v * th - 0.5;
    int x0 = (int) Math.floor(fx);
    int y0 = (int) Math.floor(fy);
    double tx = fx - x0;
    double ty = fy - y0;
    int x1 = x0 + 1;
    int y1 = y0 + 1;

    // U wraps around the sphere, V clamps at the poles.
    x0 = ((x0 % tw) + tw) % tw;
    x1 = ((x1 % tw) + tw) % tw;
    y0 = y0 < 0 ? 0 : (y0 >= th ? th - 1 : y0);
    y1 = y1 < 0 ? 0 : (y1 >= th ? th - 1 : y1);

    int p00 = pixels[y0 * tw + x0];
    int p10 = pixels[y0 * tw + x1];
    int p01 = pixels[y1 * tw + x0];
    int p11 = pixels[y1 * tw + x1];

    double w00 = (1 - tx) * (1 - ty);
    double w10 = tx * (1 - ty);
    double w01 = (1 - tx) * ty;
    double w11 = tx * ty;

    double a00 = ((p00 >>> 24) & 0xFF) * w00;
    double a10 = ((p10 >>> 24) & 0xFF) * w10;
    double a01 = ((p01 >>> 24) & 0xFF) * w01;
    double a11 = ((p11 >>> 24) & 0xFF) * w11;
    double sumA = a00 + a10 + a01 + a11;

    if (sumA <= 1e-9) {
      tex[0] = 0;
      tex[1] = 0;
      tex[2] = 0;
      tex[3] = 0;
      tex[4] = 1.0;
      return;
    }

    // Weight colour by alpha so fully transparent texels (RGB = 0) never darken the edges.
    double sumR =
        ((p00 >> 16) & 0xFF) * a00
            + ((p10 >> 16) & 0xFF) * a10
            + ((p01 >> 16) & 0xFF) * a01
            + ((p11 >> 16) & 0xFF) * a11;
    double sumG =
        ((p00 >> 8) & 0xFF) * a00
            + ((p10 >> 8) & 0xFF) * a10
            + ((p01 >> 8) & 0xFF) * a01
            + ((p11 >> 8) & 0xFF) * a11;
    double sumB =
        (p00 & 0xFF) * a00 + (p10 & 0xFF) * a10 + (p01 & 0xFF) * a01 + (p11 & 0xFF) * a11;

    tex[0] = sumR / sumA;
    tex[1] = sumG / sumA;
    tex[2] = sumB / sumA;
    tex[3] = sumA / 255.0; // smooth coverage, already contains the texel alpha
    tex[4] = 1.0; // nothing left to apply on top
  }

  // ============================================================
  // SHADING HELPERS
  // ============================================================

  /** x^140 via repeated squaring (140 = 128 + 8 + 4), much cheaper than Math.pow. */
  private static double specularPow(double x) {
    if (x <= 0.0) return 0.0;
    double x2 = x * x;
    double x4 = x2 * x2;
    double x8 = x4 * x4;
    double x16 = x8 * x8;
    double x32 = x16 * x16;
    double x64 = x32 * x32;
    double x128 = x64 * x64;
    return x128 * x8 * x4;
  }

  /**
   * 1.0 -> perpendicular view (inside filter), 0.0 -> grazing view (outside filter), with a soft
   * transition around filterInsideCutoff.
   */
  private double filterMix(double cosTheta) {
    double t = (cosTheta - (filterInsideCutoff - filterInsideRamp * 0.5)) / filterInsideRamp;
    if (t < 0.0) t = 0.0;
    if (t > 1.0) t = 1.0;
    return t;
  }

  /**
   * Alpha of a packed ARGB colour as 0..1. An alpha byte of 0 is treated as fully opaque so old
   * 0xRRGGBB style values keep working; use an alpha of at least 0x01 for real transparency.
   */
  private static double alpha01(int c) {
    int a = (c >>> 24) & 0xFF;
    return (a == 0) ? 1.0 : a / 255.0;
  }

  private static int clamp255(double v) {
    int i = (int) v;
    if (i > 255) return 255;
    if (i < 0) return 0;
    return i;
  }

  // ============================================================
  // MAIN SHADING
  // ============================================================
  @Override
  public int getColorAt(Point3 point, Vector3 normal, Light light, Point3 viewerPoint) {
    final Scratch s = scratch.get();

    sampleTexture(normal, s);
    final double[] tex = s.tex;
    final double ink = tex[3]; // 0 = glass, 1 = text / image / background
    final double inkOpacity = tex[4]; // opacity of the ink itself (from the colour alpha)
    final double eff = ink * inkOpacity; // real covered fraction of the pixel
    final double baseR = tex[0];
    final double baseG = tex[1];
    final double baseB = tex[2];

    // Direction towards the light (normalized, local variables only).
    Vector3 ld = light.getDirectionTo(point);
    double lx = -ld.x;
    double ly = -ld.y;
    double lz = -ld.z;
    double ll = Math.sqrt(lx * lx + ly * ly + lz * lz);
    if (ll > 1e-12) {
      lx /= ll;
      ly /= ll;
      lz /= ll;
    }

    // Direction towards the viewer.
    double vx = viewerPoint.x - point.x;
    double vy = viewerPoint.y - point.y;
    double vz = viewerPoint.z - point.z;
    double vl = Math.sqrt(vx * vx + vy * vy + vz * vz);
    if (vl > 1e-12) {
      vx /= vl;
      vy /= vl;
      vz /= vl;
    }

    double cosTheta = vx * normal.x + vy * normal.y + vz * normal.z;
    if (cosTheta < 0) cosTheta = -cosTheta;

    double centerDampening = 1.0;
    if (cosTheta > CENTER_THRESHOLD) {
      double t = (cosTheta - CENTER_THRESHOLD) / (1.0 - CENTER_THRESHOLD);
      centerDampening = 1.0 - (t * CENTER_DAMPENING_FACTOR);
    }

    double NdotL = normal.x * lx + normal.y * ly + normal.z * lz;
    double diffuseFactor = ((NdotL > 0.4) ? NdotL : 0.4) * centerDampening;

    // Fresnel (Schlick approximation)
    double r0 = (1.0 - indexOfRefraction) / (1.0 + indexOfRefraction);
    r0 = r0 * r0;
    double oneMinusCos = 1.0 - cosTheta;
    double om2 = oneMinusCos * oneMinusCos;
    double fresnel = r0 + (1.0 - r0) * om2 * om2 * oneMinusCos;

    double curRefl = reflectivity + (fresnel * FRESNEL_REFLECT_BOOST);
    if (curRefl > 0.85) curRefl = 0.85;
    double curTrans = transparency * (1.0 - fresnel * FRESNEL_TRANSPARENCY_FACTOR);
    if (curTrans < 0.1) curTrans = 0.1;
    // Semi-transparent ink lets part of the ray through: move the transparency toward 1 by the
    // amount of ink that is NOT opaque. Fully opaque ink (inkOpacity = 1) changes nothing.
    curTrans += (1.0 - curTrans) * ink * (1.0 - inkOpacity);
    s.reflectivity = curRefl;
    s.transparency = curTrans;

    double intensity = light.getIntensity();
    int lightColor = light.getColor();
    int lightR = (lightColor >> 16) & 0xFF;
    int lightG = (lightColor >> 8) & 0xFF;
    int lightB = lightColor & 0xFF;

    // ---- Diffuse: glass branch and ink branch, blended by the covered fraction ----
    double diffRVal = 0.0;
    double diffGVal = 0.0;
    double diffBVal = 0.0;

    if (eff < 1.0) {
      // Glass branch (original alpha < 128 path). Under semi-transparent ink (ink >= 1) the glass
      // behind it has no texture colour of its own, so its diffuse base is black; only the sky
      // glow below contributes there.
      double glassBase = (ink >= 1.0) ? 0.0 : 1.0;
      double diffFactor = diffuseFactor * intensity;
      double gR = glassBase * baseR * diffFactor * lightR / 255.0;
      double gG = glassBase * baseG * diffFactor * lightG / 255.0;
      double gB = glassBase * baseB * diffFactor * lightB / 255.0;

      if (light instanceof MuratAmbientLight) {
        double upFactor = normal.y;
        if (upFactor < 0) upFactor = 0;
        double skyGlow =
            Math.pow(upFactor, SKY_GLOW_POWER) * SKY_GLOW_INTENSITY * intensity * (1.0 - fresnel);
        gG += skyGlow * SKY_GLOW_GREEN;
        gB += skyGlow * SKY_GLOW_BLUE;
      }

      diffRVal += gR * (1.0 - eff);
      diffGVal += gG * (1.0 - eff);
      diffBVal += gB * (1.0 - eff);
    }

    if (eff > 0.0) {
      // Ink branch (original alpha >= 128 path), weighted by coverage * opacity.
      double overlayFactor = OVERLAY_BASELINE + (diffuseFactor * OVERLAY_DIFFUSE_CONTRIB);
      double k = overlayFactor * intensity * eff;
      diffRVal += baseR * k;
      diffGVal += baseG * k;
      diffBVal += baseB * k;
    }

    // ---- Specular ----
    // Reflection vector, same formula and sign convention as the original.
    double reflX = lx - 2.0 * NdotL * normal.x;
    double reflY = ly - 2.0 * NdotL * normal.y;
    double reflZ = lz - 2.0 * NdotL * normal.z;

    double len = reflX * reflX + reflY * reflY + reflZ * reflZ;
    if (len > 1e-20) {
      len = Math.sqrt(len);
      reflX /= len;
      reflY /= len;
      reflZ /= len;
    }

    double RdotV = reflX * vx + reflY * vy + reflZ * vz;
    if (RdotV < 0) RdotV = 0;

    double specFactor = specularPow(RdotV) * intensity;
    specFactor *= centerDampening;
    if (specFactor > 1.0) specFactor = 1.0;

    double specR = lightR * 0.94 * specFactor;
    double specG = lightG * specFactor;
    double specB = lightB * specFactor;

    int r = clamp255(diffRVal + specR);
    int g = clamp255(diffGVal + specG);
    int b = clamp255(diffBVal + specB);

    // ------------------------------------------------------------
    // DIELECTRIC FILTER (inside / outside)
    // Always active (no Fresnel gate). Glass pixels blend toward the filter colour; ink pixels
    // (text / image / bgColor) get a gentle multiplicative tint that keeps detail and sharpness.
    // A white filter is a no-op on ink pixels. The alpha of the filter colours scales the tint
    // strength (alpha 0 = legacy 0xRRGGBB = fully opaque).
    // ------------------------------------------------------------
    boolean hasInside = filterColorInsidePacked != 0;
    boolean hasOutside = filterColorOutsidePacked != 0;

    if (hasInside || hasOutside) {
      int useIn = hasInside ? filterColorInsidePacked : filterColorOutsidePacked;
      int useOut = hasOutside ? filterColorOutsidePacked : filterColorInsidePacked;

      double mix = filterMix(cosTheta); // 1 = inside, 0 = outside

      double filtR = ((useIn >> 16) & 0xFF) * mix + ((useOut >> 16) & 0xFF) * (1.0 - mix);
      double filtG = ((useIn >> 8) & 0xFF) * mix + ((useOut >> 8) & 0xFF) * (1.0 - mix);
      double filtB = (useIn & 0xFF) * mix + (useOut & 0xFF) * (1.0 - mix);

      double strength = filterBaseStrength + fresnel * filterFresnelBoost;
      if (strength > filterMaxStrength) strength = filterMaxStrength;

      // Filter alpha: blended between the inside and outside colours like the RGB channels.
      strength *= alpha01(useIn) * mix + alpha01(useOut) * (1.0 - mix);

      double glassW = strength * (1.0 - eff); // weight of the "blend toward filter" part
      double inkK = strength * filterInkStrength * eff; // weight of the multiplicative part

      double fr = r * (1.0 - glassW) + filtR * glassW;
      double fg = g * (1.0 - glassW) + filtG * glassW;
      double fb = b * (1.0 - glassW) + filtB * glassW;

      fr *= (1.0 - inkK) + inkK * (filtR / 255.0);
      fg *= (1.0 - inkK) + inkK * (filtG / 255.0);
      fb *= (1.0 - inkK) + inkK * (filtB / 255.0);

      r = clamp255(fr);
      g = clamp255(fg);
      b = clamp255(fb);
    }

    return (0xFF000000 | (r << 16) | (g << 8) | b);
  }

  // ============================================================
  // GETTERS
  // ============================================================
  @Override
  public double getIndexOfRefraction() {
    return indexOfRefraction;
  }

  /** Transparency computed by the last getColorAt() call on the calling thread. */
  @Override
  public double getTransparency() {
    return scratch.get().transparency;
  }

  /** Reflectivity computed by the last getColorAt() call on the calling thread. */
  @Override
  public double getReflectivity() {
    return scratch.get().reflectivity;
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

  /**
   * Layout of the original properties is unchanged. transparency / reflectivity print the
   * configured material values instead of whatever the last rendered pixel happened to compute,
   * so a saved scene reloads with the values that were originally set. The optional extras
   * (highQuality, fullWidthGradient, filter tuning) are appended after filterColorOutside and
   * only when they differ from their defaults.
   */
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
    sb.append("        transparency = ").append(transparency).append(";\n");
    sb.append("        reflectivity = ").append(reflectivity).append(";\n");
    sb.append("        filterColorInside = ")
        .append(ColorUtil.toColorString(filterColorInsidePacked))
        .append(";\n");
    sb.append("        filterColorOutside = ")
        .append(ColorUtil.toColorString(filterColorOutsidePacked))
        .append(";\n");

    // Optional extras: written ONLY when they differ from the defaults, so scenes that do not use
    // them produce exactly the same output as before.
    if (highQuality) {
      sb.append("        highQuality = true;\n");
    }
    if (fullWidthGradient) {
      sb.append("        fullWidthGradient = true;\n");
    }
    if (filterBaseStrength != DEFAULT_FILTER_BASE_STRENGTH) {
      sb.append("        filterBaseStrength = ").append(filterBaseStrength).append(";\n");
    }
    if (filterFresnelBoost != DEFAULT_FILTER_FRESNEL_BOOST) {
      sb.append("        filterFresnelBoost = ").append(filterFresnelBoost).append(";\n");
    }
    if (filterMaxStrength != DEFAULT_FILTER_MAX_STRENGTH) {
      sb.append("        filterMaxStrength = ").append(filterMaxStrength).append(";\n");
    }
    if (filterInkStrength != DEFAULT_FILTER_INK_STRENGTH) {
      sb.append("        filterInkStrength = ").append(filterInkStrength).append(";\n");
    }
    if (filterInsideCutoff != DEFAULT_FILTER_INSIDE_CUTOFF) {
      sb.append("        filterInsideCutoff = ").append(filterInsideCutoff).append(";\n");
    }
    if (filterInsideRamp != DEFAULT_FILTER_INSIDE_RAMP) {
      sb.append("        filterInsideRamp = ").append(filterInsideRamp).append(";\n");
    }

    sb.append("    }");
    return sb.toString();
  }
  
}
