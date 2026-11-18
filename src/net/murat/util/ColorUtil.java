package net.murat.util;

import android.graphics.Typeface;

/**
 * Utility class for RGB color operations. All methods work exclusively with packed integer ARGB
 * colors to eliminate object allocation. Format: (A &lt;&lt; 24) | (R &lt;&lt; 16) | (G &lt;&lt; 8)
 * | B Compatible with Java 6-8 without lambdas.
 */
public final class ColorUtil {

  private ColorUtil() {} // Prevent instantiation

  // Color constants as packed ARGB integers (all fully opaque, alpha = 255)
  public static final int BLACK = 0xFF000000;
  public static final int WHITE = 0xFFFFFFFF;
  public static final int RED = 0xFFFF0000;
  public static final int GREEN = 0xFF00FF00;
  public static final int BLUE = 0xFF0000FF;

  /**
   * Linear interpolation between two packed RGB colors (t=0: c1, t=1: c2) Only RGB components are
   * interpolated, result is fully opaque.
   */
  public static int lerp(int c1, int c2, float t) {
    t = clamp(t, 0.0f, 1.0f);
    int r1 = (c1 >> 16) & 0xFF;
    int g1 = (c1 >> 8) & 0xFF;
    int b1 = c1 & 0xFF;

    int r2 = (c2 >> 16) & 0xFF;
    int g2 = (c2 >> 8) & 0xFF;
    int b2 = c2 & 0xFF;

    int r = r1 + (int) ((r2 - r1) * t);
    int g = g1 + (int) ((g2 - g1) * t);
    int b = b1 + (int) ((b2 - b1) * t);

    return (255 << 24) | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
  }

  /**
   * Component-wise multiplication of two packed RGB colors. Result is always fully opaque
   * (alpha=255).
   */
  public static int multiply(int c1, int c2) {
    int r = (((c1 >> 16) & 0xFF) * ((c2 >> 16) & 0xFF)) / 255;
    int g = (((c1 >> 8) & 0xFF) * ((c2 >> 8) & 0xFF)) / 255;
    int b = ((c1 & 0xFF) * (c2 & 0xFF)) / 255;

    return (255 << 24) | (r << 16) | (g << 8) | b;
  }

  /** Multiplies packed RGB color by a scalar factor. Result is always fully opaque (alpha=255). */
  public static int multiply(int c, float scalar) {
    scalar = Math.max(0.0f, scalar);
    int r = clamp((int) (((c >> 16) & 0xFF) * scalar));
    int g = clamp((int) (((c >> 8) & 0xFF) * scalar));
    int b = clamp((int) ((c & 0xFF) * scalar));

    return (255 << 24) | (r << 16) | (g << 8) | b;
  }

  /** Reinhard Tone Mapping Operator. */
  public static float reinhardToneMap(float color) {
    return color / (1.0f + color);
  }

  public static int gammaCorrect(int color, float gamma) {
    float invGamma = 1.0f / gamma;
    float r = (float) Math.pow(((color >> 16) & 0xFF) / 255.0, invGamma);
    float g = (float) Math.pow(((color >> 8) & 0xFF) / 255.0, invGamma);
    float b = (float) Math.pow((color & 0xFF) / 255.0, invGamma);
    float a = ((color >> 24) & 0xFF) / 255.0f;

    // Clamp to guarantee safety against NaN or extreme values
    r = Math.max(0.0f, Math.min(1.0f, r));
    g = Math.max(0.0f, Math.min(1.0f, g));
    b = Math.max(0.0f, Math.min(1.0f, b));
    a = Math.max(0.0f, Math.min(1.0f, a));

    return ((int) (a * 255) << 24)
        | ((int) (r * 255) << 16)
        | ((int) (g * 255) << 8)
        | (int) (b * 255);
  }

  private static float linearToSrgb(float linear) {
    if (linear <= 0.0031308f) {
      return linear * 12.92f;
    } else {
      return (float) (1.055f * Math.pow(linear, 1.0 / 2.4) - 0.055f);
    }
  }

  /**
   * Multiplies two colors with alpha blending. Preserves alpha channel from the first color.
   *
   * @param color1 First packed ARGB color
   * @param color2 Second packed ARGB color
   * @param factor Multiplication factor (0.0 - 1.0)
   * @return Blended packed ARGB color
   */
  public static int multiplyWithAlpha(int color1, int color2, float factor) {
    // Extract components from color1
    int a1 = (color1 >> 24) & 0xFF;
    int r1 = (color1 >> 16) & 0xFF;
    int g1 = (color1 >> 8) & 0xFF;
    int b1 = color1 & 0xFF;

    // Extract components from color2
    int r2 = (color2 >> 16) & 0xFF;
    int g2 = (color2 >> 8) & 0xFF;
    int b2 = color2 & 0xFF;

    // Multiply channels and apply factor
    int r = (int) (r1 * r2 / 255.0 * factor);
    int g = (int) (g1 * g2 / 255.0 * factor);
    int b = (int) (b1 * b2 / 255.0 * factor);

    // Clamp
    r = Math.max(0, Math.min(255, r));
    g = Math.max(0, Math.min(255, g));
    b = Math.max(0, Math.min(255, b));

    return (a1 << 24) | (r << 16) | (g << 8) | b;
  }

  /** sRGB to linear conversion for a single channel. */
  public static float srgbToLinear(float srgb) {
    if (srgb <= 0.04045f) {
      return srgb / 12.92f;
    } else {
      return (float) Math.pow((srgb + 0.055f) / 1.055f, 2.4f);
    }
  }

  public static int sRGBToLinear(int srgbColor, float gamma) {
    if (gamma == 1f) return srgbColor;

    float r = ((srgbColor >> 16) & 0xFF) / 255.0f;
    float g = ((srgbColor >> 8) & 0xFF) / 255.0f;
    float b = (srgbColor & 0xFF) / 255.0f;
    int a = (srgbColor >> 24) & 0xFF;

    r = clamp(r, 0.0f, 1.0f);
    g = clamp(g, 0.0f, 1.0f);
    b = clamp(b, 0.0f, 1.0f);

    r = (r <= 0.04045f) ? (r / 12.92f) : (float) Math.pow((r + 0.055f) / 1.055f, gamma);
    g = (g <= 0.04045f) ? (g / 12.92f) : (float) Math.pow((g + 0.055f) / 1.055f, gamma);
    b = (b <= 0.04045f) ? (b / 12.92f) : (float) Math.pow((b + 0.055f) / 1.055f, gamma);

    return (a << 24) | ((int) (r * 255) << 16) | ((int) (g * 255) << 8) | (int) (b * 255);
  }

  public static int sRGBToLinearExtra(int base, float gamma) {
    if (gamma == 1f) return base;

    if (gamma <= 0) gamma = 2.2f;

    float r = ((base >> 16) & 0xFF) / 255.0f;
    float g = ((base >> 8) & 0xFF) / 255.0f;
    float b = (base & 0xFF) / 255.0f;
    int alpha = (base >> 24) & 0xFF;

    r = (float) Math.pow(r, 1.0f / gamma);
    g = (float) Math.pow(g, 1.0f / gamma);
    b = (float) Math.pow(b, 1.0f / gamma);

    int red = (int) (Math.min(Math.max(r * 255, 0), 255));
    int green = (int) (Math.min(Math.max(g * 255, 0), 255));
    int blue = (int) (Math.min(Math.max(b * 255, 0), 255));

    return (alpha << 24) | (red << 16) | (green << 8) | blue;
  }

  /**
   * Energy-conserving linear interpolation between two packed ARGB colors. Used for reflection
   * compositing: result = local * (1 - t) + reflected * t Unlike ColorUtil.add(), this does not
   * double-count light energy when blending a surface's local lighting with its reflected
   * contribution.
   *
   * @param colorA base/local color (used when t = 0)
   * @param colorB blend/reflected color (used when t = 1)
   * @param t blend factor, clamped to [0, 1] (typically the material's reflectivity)
   * @return packed ARGB result with alpha forced to 0xFF
   */
  public static int interpolate(int colorA, int colorB, float t) {
    if (t < 0f) t = 0f;
    if (t > 1f) t = 1f;

    int aR = (colorA >> 16) & 0xFF;
    int aG = (colorA >> 8) & 0xFF;
    int aB = colorA & 0xFF;

    int bR = (colorB >> 16) & 0xFF;
    int bG = (colorB >> 8) & 0xFF;
    int bB = colorB & 0xFF;

    int r = (int) (aR * (1.0f - t) + bR * t);
    int g = (int) (aG * (1.0f - t) + bG * t);
    int b = (int) (aB * (1.0f - t) + bB * t);

    r = (r < 0) ? 0 : (r > 255 ? 255 : r);
    g = (g < 0) ? 0 : (g > 255 ? 255 : g);
    b = (b < 0) ? 0 : (b > 255 ? 255 : b);

    return 0xFF000000 | (r << 16) | (g << 8) | b;
  }

  /** Apply exposure and tone mapping to linear color. */
  public static int applyExposureAndToneMapping(int linearColor, float exposure) {
    float r = clamp(((linearColor >> 16) & 0xFF) / 255.0f, 0.0f, 1.0f);
    float g = clamp(((linearColor >> 8) & 0xFF) / 255.0f, 0.0f, 1.0f);
    float b = clamp((linearColor & 0xFF) / 255.0f, 0.0f, 1.0f);
    int a = (linearColor >> 24) & 0xFF;

    // Exposure adjustment
    r *= exposure;
    g *= exposure;
    b *= exposure;

    // ACES filmic tone mapping
    r = clamp(acesToneMap(r), 0.0f, 1.0f);
    g = clamp(acesToneMap(g), 0.0f, 1.0f);
    b = clamp(acesToneMap(b), 0.0f, 1.0f);

    return (a << 24) | ((int) (r * 255) << 16) | ((int) (g * 255) << 8) | (int) (b * 255);
  }

  /** Apply tone mapping to a linear color. */
  public static int applyToneMapping(int linearColor, float exposure) {
    float r = clamp(((linearColor >> 16) & 0xFF) / 255.0f, 0.0f, 1.0f);
    float g = clamp(((linearColor >> 8) & 0xFF) / 255.0f, 0.0f, 1.0f);
    float b = clamp((linearColor & 0xFF) / 255.0f, 0.0f, 1.0f);
    int a = (linearColor >> 24) & 0xFF;

    // Exposure adjustment
    r *= exposure;
    g *= exposure;
    b *= exposure;

    // Reinhard tone mapping
    r = clamp(reinhardToneMap(r), 0.0f, 1.0f);
    g = clamp(reinhardToneMap(g), 0.0f, 1.0f);
    b = clamp(reinhardToneMap(b), 0.0f, 1.0f);

    return (a << 24) | ((int) (r * 255) << 16) | ((int) (g * 255) << 8) | (int) (b * 255);
  }

  public static int linearToSRGB(int linearColor) {
    float r = clamp(((linearColor >> 16) & 0xFF) / 255.0f, 0.0f, 1.0f);
    float g = clamp(((linearColor >> 8) & 0xFF) / 255.0f, 0.0f, 1.0f);
    float b = clamp((linearColor & 0xFF) / 255.0f, 0.0f, 1.0f);
    int a = (linearColor >> 24) & 0xFF;

    // Linear to sRGB conversion
    r = clamp(linearToSrgb(r), 0.0f, 1.0f);
    g = clamp(linearToSrgb(g), 0.0f, 1.0f);
    b = clamp(linearToSrgb(b), 0.0f, 1.0f);

    return (a << 24) | ((int) (r * 255) << 16) | ((int) (g * 255) << 8) | (int) (b * 255);
  }

  private static float acesToneMap(float x) {
    float a = 2.51f;
    float b = 0.03f;
    float c = 2.43f;
    float d = 0.59f;
    float e = 0.14f;
    return Math.max(0.0f, Math.min(1.0f, (x * (a * x + b)) / (x * (c * x + d) + e)));
  }

  public static String toColorString(int color) {
    StringBuilder sb = new StringBuilder();
    sb.append("0x");
    sb.append(String.format("%08X", color));
    return sb.toString();
  }

  /**
   * Returns a string representation of a Typeface for debug/display purposes. Note: Typeface
   * doesn't expose font family or size directly like AWT Font did, so we only output the style.
   *
   * @param typeface The Typeface to describe
   * @return A formatted string like "Typeface [style=1]"
   */
  public static String toFontString(Typeface typeface) {
    if (typeface == null) return "Typeface [null]";
    StringBuilder sb = new StringBuilder();
    sb.append("Typeface [style=");
    sb.append(typeface.getStyle());
    sb.append("]");
    return sb.toString();
  }

  public static int applyShadowColor(int original, int shadowColor) {
    float alpha = ((shadowColor >> 24) & 0xFF) / 255.0f;

    float shadowR = ((shadowColor >> 16) & 0xFF) / 255.0f;
    float shadowG = ((shadowColor >> 8) & 0xFF) / 255.0f;
    float shadowB = (shadowColor & 0xFF) / 255.0f;

    float origR = ((original >> 16) & 0xFF) / 255.0f;
    float origG = ((original >> 8) & 0xFF) / 255.0f;
    float origB = (original & 0xFF) / 255.0f;

    float shadowStrength = 0.5f + 0.5f * alpha; // alpha=1.0 → 1.0, alpha=0.0 → 0.5
    float resultR = origR * (1.0f - shadowStrength * (1.0f - shadowR));
    float resultG = origG * (1.0f - shadowStrength * (1.0f - shadowG));
    float resultB = origB * (1.0f - shadowStrength * (1.0f - shadowB));

    return (255 << 24)
        | (clamp((int) (resultR * 255)) << 16)
        | (clamp((int) (resultG * 255)) << 8)
        | clamp((int) (resultB * 255));
  }

  public static int enhanceBrightnessAndContrast(
      int color, float brightnessFactor, float contrastFactor) {
    int r = (color >> 16) & 0xFF;
    int g = (color >> 8) & 0xFF;
    int b = color & 0xFF;
    int a = (color >> 24) & 0xFF;

    // Apply brightness
    r = Math.min(255, (int) (r * brightnessFactor));
    g = Math.min(255, (int) (g * brightnessFactor));
    b = Math.min(255, (int) (b * brightnessFactor));

    // Apply contrast
    float contrast = (contrastFactor - 1.0f) / 2.0f;
    r = (int) ((r - 128) * contrastFactor + 128 + contrast * 255);
    g = (int) ((g - 128) * contrastFactor + 128 + contrast * 255);
    b = (int) ((b - 128) * contrastFactor + 128 + contrast * 255);

    // Clamp values
    r = Math.max(0, Math.min(255, r));
    g = Math.max(0, Math.min(255, g));
    b = Math.max(0, Math.min(255, b));

    return (a << 24) | (r << 16) | (g << 8) | b;
  }

  /** Scales RGB components by a factor (0.0-1.0). Result is always fully opaque (alpha=255). */
  public static int multiplyColor(int color, double factor) {
    factor = Math.max(0, Math.min(1, factor));
    int r = (int) (((color >> 16) & 0xFF) * factor);
    int g = (int) (((color >> 8) & 0xFF) * factor);
    int b = (int) ((color & 0xFF) * factor);

    return (255 << 24) | (r << 16) | (g << 8) | b;
  }

  public static int multiplyColorFloat(int color, float factor) {
    factor = Math.max(0f, Math.min(1f, factor));
    int r = (int) (((color >> 16) & 0xFF) * factor);
    int g = (int) (((color >> 8) & 0xFF) * factor);
    int b = (int) ((color & 0xFF) * factor);

    return (255 << 24) | (r << 16) | (g << 8) | b;
  }

  public static int multiplyColors(int color1, int color2) {
    float r = (((color1 >> 16) & 0xFF) / 255.0f) * (((color2 >> 16) & 0xFF) / 255.0f);
    float g = (((color1 >> 8) & 0xFF) / 255.0f) * (((color2 >> 8) & 0xFF) / 255.0f);
    float b = ((color1 & 0xFF) / 255.0f) * ((color2 & 0xFF) / 255.0f);

    return (255 << 24) | ((int) (r * 255) << 16) | ((int) (g * 255) << 8) | (int) (b * 255);
  }

  /** Multiplies two colors with a scaling factor. Result is always fully opaque (alpha=255). */
  public static int multiplyColors(int base, int light, double factor) {
    int r =
        (int)
            Math.min(
                255,
                Math.max(0, (((base >> 16) & 0xFF) * ((light >> 16) & 0xFF)) / 255.0 * factor));
    int g =
        (int)
            Math.min(
                255, Math.max(0, (((base >> 8) & 0xFF) * ((light >> 8) & 0xFF)) / 255.0 * factor));
    int b = (int) Math.min(255, Math.max(0, ((base & 0xFF) * (light & 0xFF)) / 255.0 * factor));

    return (255 << 24) | (r << 16) | (g << 8) | b;
  }

  /**
   * Creates a packed ARGB color from double values with robust validation. Result is always fully
   * opaque (alpha=255).
   */
  public static int createColor(double r, double g, double b) {
    if (Double.isNaN(r) || Double.isNaN(g) || Double.isNaN(b)) {
      return BLACK;
    }

    return (255 << 24) | (clamp((int) r) << 16) | (clamp((int) g) << 8) | clamp((int) b);
  }

  public static int clampColorValue(int value) {
    if (value < 0) return 0;
    if (value > 255) return 255;
    return value;
  }

  public static float clampFloatColorValue(float value) {
    if (value < 0f) return 0f;
    if (value > 1f) return 1f;
    return value;
  }

  public static int clampColor(int color) {
    int r = Math.max(0, Math.min(255, (color >> 16) & 0xFF));
    int g = Math.max(0, Math.min(255, (color >> 8) & 0xFF));
    int b = Math.max(0, Math.min(255, color & 0xFF));
    int a = (color >> 24) & 0xFF;
    return (a << 24) | (r << 16) | (g << 8) | b;
  }

  /**
   * Scales the RGB channels of an ARGB color by the given factor (alpha kept untouched). factor is
   * clamped to [0, 1] is NOT enforced here on purpose — callers may want >1.0 for HDR-ish boosts,
   * but channel output is always clamped to [0,255].
   */
  public static int scaleColorIntensity(int argb, double factor) {
    int a = (argb >> 24) & 0xFF;
    int r = (argb >> 16) & 0xFF;
    int g = (argb >> 8) & 0xFF;
    int b = argb & 0xFF;

    r = (int) Math.round(r * factor);
    g = (int) Math.round(g * factor);
    b = (int) Math.round(b * factor);

    r = Math.max(0, Math.min(255, r));
    g = Math.max(0, Math.min(255, g));
    b = Math.max(0, Math.min(255, b));

    return (a << 24) | (r << 16) | (g << 8) | b;
  }

  // Overload for float values
  public static int createColor(float r, float g, float b) {
    return createColor((double) r, (double) g, (double) b);
  }

  // Overload for int values
  public static int createColor(int r, int g, int b) {
    return (255 << 24)
        | (Math.min(255, Math.max(0, r)) << 16)
        | (Math.min(255, Math.max(0, g)) << 8)
        | Math.min(255, Math.max(0, b));
  }

  // Interpolate between two colors
  public static int interpolateColor(int c1, int c2, double t) {
    return blendColors(c1, c2, t);
  }

  // Add noise/variation to a color
  public static int addColorVariation(int color, double variation) {
    double noise = 0.9 + Math.sin(variation * 15.0) * 0.1;
    int r = (int) (((color >> 16) & 0xFF) * noise);
    int g = (int) (((color >> 8) & 0xFF) * noise);
    int b = (int) ((color & 0xFF) * noise);
    return (255 << 24) | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
  }

  // Darken a color by specified amount (0.0 - 1.0)
  public static int darkenColor(int color, double amount) {
    amount = Math.max(0, Math.min(1, amount));
    int r = (int) (((color >> 16) & 0xFF) * (1 - amount));
    int g = (int) (((color >> 8) & 0xFF) * (1 - amount));
    int b = (int) ((color & 0xFF) * (1 - amount));
    return (255 << 24) | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
  }

  // Lighten a color by specified amount (0.0 - 1.0)
  public static int lightenColor(int color, double amount) {
    amount = Math.max(0, Math.min(1, amount));
    int cr = (color >> 16) & 0xFF;
    int cg = (color >> 8) & 0xFF;
    int cb = color & 0xFF;

    int r = (int) (cr + (255 - cr) * amount);
    int g = (int) (cg + (255 - cg) * amount);
    int b = (int) (cb + (255 - cb) * amount);
    return (255 << 24) | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
  }

  /** Color addition (RGB only). Result is always fully opaque (alpha=255). */
  public static int add(int c1, int c2) {
    int r = Math.min(255, ((c1 >> 16) & 0xFF) + ((c2 >> 16) & 0xFF));
    int g = Math.min(255, ((c1 >> 8) & 0xFF) + ((c2 >> 8) & 0xFF));
    int b = Math.min(255, (c1 & 0xFF) + (c2 & 0xFF));
    return (255 << 24) | (r << 16) | (g << 8) | b;
  }

  // Null-safe version of add
  public static int addSafe(Integer c1, Integer c2) {
    if (c1 == null && c2 == null) return BLACK;
    if (c1 == null) return c2.intValue();
    if (c2 == null) return c1.intValue();
    return add(c1.intValue(), c2.intValue());
  }

  /** Clamps float value between [min, max]. */
  public static float clamp(float value, float min, float max) {
    return Math.max(min, Math.min(max, value));
  }

  public static double clampDouble(double value, double min, double max) {
    return Math.max(min, Math.min(max, value));
  }

  /** Bilinear interpolation between four colors. Result is always fully opaque (alpha=255). */
  public static int bilinearInterpolate(int c00, int c10, int c01, int c11, double tx, double ty) {
    double invTx = 1.0 - tx;
    double invTy = 1.0 - ty;

    int r =
        (int)
            (invTx * invTy * ((c00 >> 16) & 0xFF)
                + tx * invTy * ((c10 >> 16) & 0xFF)
                + invTx * ty * ((c01 >> 16) & 0xFF)
                + tx * ty * ((c11 >> 16) & 0xFF));

    int g =
        (int)
            (invTx * invTy * ((c00 >> 8) & 0xFF)
                + tx * invTy * ((c10 >> 8) & 0xFF)
                + invTx * ty * ((c01 >> 8) & 0xFF)
                + tx * ty * ((c11 >> 8) & 0xFF));

    int b =
        (int)
            (invTx * invTy * (c00 & 0xFF)
                + tx * invTy * (c10 & 0xFF)
                + invTx * ty * (c01 & 0xFF)
                + tx * ty * (c11 & 0xFF));

    return (255 << 24) | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
  }

  /**
   * Converts RGB components to HSB (Hue, Saturation, Brightness) color model.
   *
   * @param r Red component (0-255)
   * @param g Green component (0-255)
   * @param b Blue component (0-255)
   * @return float array containing [hue, saturation, brightness] where hue is in range 0-1,
   *     saturation 0-1, brightness 0-1
   */
  public static float[] rgbToHsb(int r, int g, int b) {
    float[] hsb = new float[3];
    float rf = r / 255f;
    float gf = g / 255f;
    float bf = b / 255f;

    float max = Math.max(rf, Math.max(gf, bf));
    float min = Math.min(rf, Math.min(gf, bf));
    float delta = max - min;

    // Calculate Hue
    if (delta == 0) {
      hsb[0] = 0;
    } else if (max == rf) {
      hsb[0] = ((gf - bf) / delta) % 6f;
    } else if (max == gf) {
      hsb[0] = ((bf - rf) / delta) + 2f;
    } else { // max == bf
      hsb[0] = ((rf - gf) / delta) + 4f;
    }
    hsb[0] = (hsb[0] / 6f) % 1f;
    if (hsb[0] < 0) hsb[0] += 1f;

    // Calculate Saturation
    hsb[1] = (max == 0) ? 0 : delta / max;

    // Calculate Brightness
    hsb[2] = max;

    return hsb;
  }

  /**
   * Converts HSB (Hue, Saturation, Brightness) values to RGB.
   *
   * @param h Hue (0-1)
   * @param s Saturation (0-1)
   * @param b Brightness (0-1)
   * @return RGB as int in 0x00RRGGBB format (alpha is not set)
   */
  public static int hsbToRgb(float h, float s, float b) {
    if (s == 0) {
      int val = Math.round(b * 255);
      return (val << 16) | (val << 8) | val;
    }

    h = h * 6f;
    if (h == 6f) h = 0f;
    int i = (int) Math.floor(h);
    float f = h - i;

    float p = b * (1 - s);
    float q = b * (1 - s * f);
    float t = b * (1 - s * (1 - f));

    float r, g, bl;
    switch (i) {
      case 0:
        r = b;
        g = t;
        bl = p;
        break;
      case 1:
        r = q;
        g = b;
        bl = p;
        break;
      case 2:
        r = p;
        g = b;
        bl = t;
        break;
      case 3:
        r = p;
        g = q;
        bl = b;
        break;
      case 4:
        r = t;
        g = p;
        bl = b;
        break;
      case 5:
      default:
        r = b;
        g = p;
        bl = q;
        break;
    }

    int red = Math.round(r * 255);
    int green = Math.round(g * 255);
    int blue = Math.round(bl * 255);

    return (red << 16) | (green << 8) | blue;
  }

  /**
   * APPLY LIGHT COLOR AS SOFT TINT Preserves original color quality while adding light color tone
   */
  public static int applyLightAsSoftTint(int color, int lightColor, double intensity) {
    // If light is too dim, return original
    if (intensity < 0.01) return color;

    // Extract light color components
    int lr = (lightColor >> 16) & 0xFF;
    int lg = (lightColor >> 8) & 0xFF;
    int lb = lightColor & 0xFF;

    // Calculate light luminance
    double lightLum = (0.299 * lr + 0.587 * lg + 0.114 * lb) / 255.0;

    // If light is white or very dim, return original
    if (lightLum > 0.95 || lightLum < 0.05) return color;

    // Normalize light color (preserve hue, normalize brightness)
    double invLum = 1.0 / lightLum;
    int nr = (int) (lr * invLum);
    int ng = (int) (lg * invLum);
    int nb = (int) (lb * invLum);
    int normalizedLight =
        (0xFF << 24) | (Math.min(255, nr) << 16) | (Math.min(255, ng) << 8) | Math.min(255, nb);

    // Tint strength: 0.15 = subtle, 0.3 = strong
    float tintStrength = 0.15f * (float) intensity;
    tintStrength = Math.min(0.3f, tintStrength);

    // Blend original color with normalized light color
    return blendColors(color, normalizedLight, tintStrength);
  }

  /**
   * Extracts the alpha component from a color integer.
   *
   * @param color Color in 0xAARRGGBB format
   * @return Alpha value (0-255)
   */
  public static int getAlpha(int color) {
    return (color >> 24) & 0xFF;
  }

  /**
   * Extracts the red component from a color integer.
   *
   * @param color Color in 0xAARRGGBB format
   * @return Red value (0-255)
   */
  public static int getRed(int color) {
    return (color >> 16) & 0xFF;
  }

  /**
   * Extracts the green component from a color integer.
   *
   * @param color Color in 0xAARRGGBB format
   * @return Green value (0-255)
   */
  public static int getGreen(int color) {
    return (color >> 8) & 0xFF;
  }

  /**
   * Extracts the blue component from a color integer.
   *
   * @param color Color in 0xAARRGGBB format
   * @return Blue value (0-255)
   */
  public static int getBlue(int color) {
    return color & 0xFF;
  }

  /**
   * Combines individual components into a single color integer.
   *
   * @param alpha Alpha component (0-255)
   * @param red Red component (0-255)
   * @param green Green component (0-255)
   * @param blue Blue component (0-255)
   * @return Color in 0xAARRGGBB format
   */
  public static int createColor(int alpha, int red, int green, int blue) {
    return (alpha << 24) | (red << 16) | (green << 8) | blue;
  }

  /**
   * Enhances color quality by adjusting saturation while preventing color shifts. Uses careful
   * saturation boost to maintain visual quality.
   *
   * @param color Input color in 0xAARRGGBB format
   * @param saturationFactor Factor to multiply saturation by (recommended: 1.0-1.8)
   * @return Enhanced color in 0xAARRGGBB format with preserved alpha
   */
  public static int enhanceColorQuality(int color, float saturationFactor) {
    int r = getRed(color);
    int g = getGreen(color);
    int b = getBlue(color);
    int a = getAlpha(color);

    float[] hsb = rgbToHsb(r, g, b);
    // Careful saturation boost to avoid color shifts
    hsb[1] = Math.min(0.9f, hsb[1] * saturationFactor);

    int rgb = hsbToRgb(hsb[0], hsb[1], hsb[2]);
    return (a << 24) | (rgb & 0x00FFFFFF);
  }

  /**
   * Applies advanced color blending based on Fresnel effect. Designed to prevent green edge
   * artifacts in rendering.
   *
   * @param inside Inside color in 0xAARRGGBB format
   * @param outside Outside color in 0xAARRGGBB format
   * @param fresnel Fresnel factor (typically 0.0-1.0)
   * @return Blended color in 0xAARRGGBB format
   */
  public static int applyAdvancedColorFilter(int inside, int outside, double fresnel) {
    double blendFactor = Math.min(1.0, fresnel * 1.3);

    int insideR = getRed(inside);
    int insideG = getGreen(inside);
    int insideB = getBlue(inside);
    int insideA = getAlpha(inside);

    int outsideR = getRed(outside);
    int outsideG = getGreen(outside);
    int outsideB = getBlue(outside);

    float r = (float) (insideR * (1 - blendFactor) + outsideR * blendFactor);
    float g = (float) (insideG * (1 - blendFactor) + outsideG * blendFactor);
    float b = (float) (insideB * (1 - blendFactor) + outsideB * blendFactor);

    int resultR = Math.round(r);
    int resultG = Math.round(g);
    int resultB = Math.round(b);

    return createColor(insideA, resultR, resultG, resultB);
  }

  /**
   * Performs safe color blending with compensation to prevent color artifacts. Uses balanced
   * blending ratios to maintain color integrity.
   *
   * @param base Base color in 0xAARRGGBB format
   * @param filter Filter/overlay color in 0xAARRGGBB format
   * @param strength Blending strength (0.0-1.0)
   * @return Blended color in 0xAARRGGBB format
   */
  public static int blendColorsWithCompensation(int base, int filter, double strength) {
    int baseR = getRed(base);
    int baseG = getGreen(base);
    int baseB = getBlue(base);
    int baseA = getAlpha(base);

    int filterR = getRed(filter);
    int filterG = getGreen(filter);
    int filterB = getBlue(filter);
    int filterA = getAlpha(filter);

    // Balanced blending to prevent color artifacts
    float blend = (float) Math.min(0.8, strength);

    float finalR = baseR * (1 - blend * 0.4f) + filterR * blend * 0.6f;
    float finalG = baseG * (1 - blend * 0.4f) + filterG * blend * 0.6f;
    float finalB = baseB * (1 - blend * 0.4f) + filterB * blend * 0.6f;
    float finalA = baseA * (1 - blend * 0.4f) + filterA * blend * 0.6f;

    int resultR = Math.round(finalR);
    int resultG = Math.round(finalG);
    int resultB = Math.round(finalB);
    int resultA = Math.round(finalA);

    return createColor(resultA, resultR, resultG, resultB);
  }

  /**
   * Linearly interpolates between two colors.
   *
   * @param color1 First color in 0xAARRGGBB format
   * @param color2 Second color in 0xAARRGGBB format
   * @param t Interpolation factor (0.0 = color1, 1.0 = color2)
   * @return Interpolated color in 0xAARRGGBB format
   */
  public static int lerpColor(int color1, int color2, float t) {
    int r1 = getRed(color1);
    int g1 = getGreen(color1);
    int b1 = getBlue(color1);
    int a1 = getAlpha(color1);

    int r2 = getRed(color2);
    int g2 = getGreen(color2);
    int b2 = getBlue(color2);
    int a2 = getAlpha(color2);

    float r = r1 + (r2 - r1) * t;
    float g = g1 + (g2 - g1) * t;
    float b = b1 + (b2 - b1) * t;
    float a = a1 + (a2 - a1) * t;

    return createColor(Math.round(a), Math.round(r), Math.round(g), Math.round(b));
  }

  /**
   * Converts a color to grayscale.
   *
   * @param color Input color in 0xAARRGGBB format
   * @return Grayscale version preserving alpha
   */
  public static int toGrayscale(int color) {
    int r = getRed(color);
    int g = getGreen(color);
    int b = getBlue(color);
    int a = getAlpha(color);

    // Standard luminance formula
    int gray = Math.round(0.299f * r + 0.587f * g + 0.114f * b);
    return createColor(a, gray, gray, gray);
  }

  /**
   * Inverts a color (complementary color).
   *
   * @param color Input color in 0xAARRGGBB format
   * @return Inverted color preserving alpha
   */
  public static int invertColor(int color) {
    int r = getRed(color);
    int g = getGreen(color);
    int b = getBlue(color);
    int a = getAlpha(color);

    return createColor(a, 255 - r, 255 - g, 255 - b);
  }

  /**
   * Tints the source color with the tint color using channel-wise multiplication. This simulates a
   * colored filter effect - like looking through colored glass.
   *
   * @param source The original color (e.g., refracted ray color from behind the object)
   * @param tint The tinting color (e.g., material's own color)
   * @return The tinted color, with alpha preserved from the source
   */
  public static int tint(int source, int tint) {
    int srcA = (source >> 24) & 0xFF;
    int srcR = (source >> 16) & 0xFF;
    int srcG = (source >> 8) & 0xFF;
    int srcB = source & 0xFF;

    int tintR = (tint >> 16) & 0xFF;
    int tintG = (tint >> 8) & 0xFF;
    int tintB = tint & 0xFF;

    int resultR = (srcR * tintR) / 255;
    int resultG = (srcG * tintG) / 255;
    int resultB = (srcB * tintB) / 255;

    return (srcA << 24) | (resultR << 16) | (resultG << 8) | resultB;
  }

  /**
   * Applies a tint to a color by blending toward the tint color. This preserves luminance while
   * shifting hue - unlike multiply which darkens.
   *
   * @param source The original color (0xAARRGGBB)
   * @param tint The tint color to apply (0xAARRGGBB)
   * @param strength The tint strength (0.0 = no tint, 1.0 = full tint)
   * @return Tinted color with original alpha preserved
   */
  public static int applyTint(int source, int tint, float strength) {
    strength = Math.max(0.0f, Math.min(1.0f, strength));

    int a = (source >> 24) & 0xFF;
    int r = (source >> 16) & 0xFF;
    int g = (source >> 8) & 0xFF;
    int b = source & 0xFF;

    int tr = (tint >> 16) & 0xFF;
    int tg = (tint >> 8) & 0xFF;
    int tb = tint & 0xFF;

    // Calculate original luminance
    float lum = 0.299f * r + 0.587f * g + 0.114f * b;

    // Blend toward tint color
    float resultR = r * (1.0f - strength) + tr * strength;
    float resultG = g * (1.0f - strength) + tg * strength;
    float resultB = b * (1.0f - strength) + tb * strength;

    // Preserve original luminance
    float newLum = 0.299f * resultR + 0.587f * resultG + 0.114f * resultB;
    if (newLum > 0.001f) {
      float scale = lum / newLum;
      resultR = Math.min(255, Math.max(0, resultR * scale));
      resultG = Math.min(255, Math.max(0, resultG * scale));
      resultB = Math.min(255, Math.max(0, resultB * scale));
    }

    return (a << 24) | ((int) resultR << 16) | ((int) resultG << 8) | (int) resultB;
  }

  /** Simple blend between two colors (preserves alpha from source). */
  public static int blendTint(int source, int tint, float strength) {
    strength = Math.max(0.0f, Math.min(1.0f, strength));

    int a = (source >> 24) & 0xFF;
    int r = (source >> 16) & 0xFF;
    int g = (source >> 8) & 0xFF;
    int b = source & 0xFF;

    int tr = (tint >> 16) & 0xFF;
    int tg = (tint >> 8) & 0xFF;
    int tb = tint & 0xFF;

    int resultR = (int) (r * (1.0f - strength) + tr * strength);
    int resultG = (int) (g * (1.0f - strength) + tg * strength);
    int resultB = (int) (b * (1.0f - strength) + tb * strength);

    return (a << 24) | (clamp(resultR) << 16) | (clamp(resultG) << 8) | clamp(resultB);
  }

  /**
   * Multiplies packed RGB color by a double factor with float precision. Elena ile aynı
   * hassasiyette.
   */
  public static int multiplyDouble(int color, double factor) {
    if (factor <= 0) return BLACK;
    if (factor >= 1) return color;

    int r = (int) (((color >> 16) & 0xFF) * factor);
    int g = (int) (((color >> 8) & 0xFF) * factor);
    int b = (int) ((color & 0xFF) * factor);

    return (255 << 24) | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
  }

  /**
   * Multiplies two colors with alpha blending preserving source alpha. Elena'nın FloatColor
   * multiply işlemi ile aynı.
   */
  public static int multiplyWithAlphaFloat(int color1, int color2, float alpha) {
    int a = (int) (((color1 >> 24) & 0xFF) * alpha);
    int r = (int) (((color1 >> 16) & 0xFF) * ((color2 >> 16) & 0xFF) / 255.0f);
    int g = (int) (((color1 >> 8) & 0xFF) * ((color2 >> 8) & 0xFF) / 255.0f);
    int b = (int) ((color1 & 0xFF) * (color2 & 0xFF) / 255.0f);

    return (a << 24) | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
  }

  /**
   * Energy-conserving addition of two colors. Prevents over-brightening like Elena's
   * FloatColor.add()
   */
  public static int addEnergyConserving(int c1, int c2) {
    int r = Math.min(255, ((c1 >> 16) & 0xFF) + ((c2 >> 16) & 0xFF));
    int g = Math.min(255, ((c1 >> 8) & 0xFF) + ((c2 >> 8) & 0xFF));
    int b = Math.min(255, (c1 & 0xFF) + (c2 & 0xFF));
    return (255 << 24) | (r << 16) | (g << 8) | b;
  }

  /**
   * Clamps double value between 0 and 1, then converts to int color. Elena'nın FloatColor.clamp01()
   * ile aynı.
   */
  public static int clampDoubleToColor(double value) {
    if (value < 0) return 0;
    if (value > 1) return 255;
    return (int) (value * 255);
  }

  /** Converts float color components to packed ARGB int. Elena'nın FloatColor.toARGB() ile aynı. */
  public static int floatToARGB(float r, float g, float b) {
    int ir = clamp((int) (r * 255));
    int ig = clamp((int) (g * 255));
    int ib = clamp((int) (b * 255));
    return (255 << 24) | (ir << 16) | (ig << 8) | ib;
  }

  /**
   * Converts float color components with alpha to packed ARGB int. Elena'nın FloatColor.toARGB()
   * ile aynı.
   */
  public static int floatToARGB(float r, float g, float b, float a) {
    int ia = clamp((int) (a * 255));
    int ir = clamp((int) (r * 255));
    int ig = clamp((int) (g * 255));
    int ib = clamp((int) (b * 255));
    return (ia << 24) | (ir << 16) | (ig << 8) | ib;
  }

  /**
   * Multiplies color by double factor with proper clamping. Elena'nın FloatColor.multiply() ile
   * aynı.
   */
  public static int multiplyDoubleFactor(int color, double factor) {
    if (factor <= 0) return BLACK;
    if (factor >= 1) return color;

    int r = (int) Math.min(255, ((color >> 16) & 0xFF) * factor);
    int g = (int) Math.min(255, ((color >> 8) & 0xFF) * factor);
    int b = (int) Math.min(255, (color & 0xFF) * factor);

    return (255 << 24) | (r << 16) | (g << 8) | b;
  }

  /** Tints source color with tint color, preserving alpha. Elena'nın ColorUtil.tint() ile aynı. */
  public static int tintWithAlpha(int source, int tint, float alpha) {
    int srcA = (source >> 24) & 0xFF;
    int srcR = (source >> 16) & 0xFF;
    int srcG = (source >> 8) & 0xFF;
    int srcB = source & 0xFF;

    int tintR = (tint >> 16) & 0xFF;
    int tintG = (tint >> 8) & 0xFF;
    int tintB = tint & 0xFF;

    int resultR = (int) ((srcR * tintR / 255.0f) * alpha);
    int resultG = (int) ((srcG * tintG / 255.0f) * alpha);
    int resultB = (int) ((srcB * tintB / 255.0f) * alpha);

    return (srcA << 24) | (clamp(resultR) << 16) | (clamp(resultG) << 8) | clamp(resultB);
  }

  /** Adds two colors with alpha blending support. Preserves alpha from first color. */
  public static int addWithAlpha(int c1, int c2) {
    int a = (c1 >> 24) & 0xFF;
    int r = Math.min(255, ((c1 >> 16) & 0xFF) + ((c2 >> 16) & 0xFF));
    int g = Math.min(255, ((c1 >> 8) & 0xFF) + ((c2 >> 8) & 0xFF));
    int b = Math.min(255, (c1 & 0xFF) + (c2 & 0xFF));
    return (a << 24) | (r << 16) | (g << 8) | b;
  }

  /** Multiplies color by float factor with alpha preservation. */
  public static int multiplyFloatPreserveAlpha(int color, float factor) {
    int a = (color >> 24) & 0xFF;
    int r = (int) (((color >> 16) & 0xFF) * factor);
    int g = (int) (((color >> 8) & 0xFF) * factor);
    int b = (int) ((color & 0xFF) * factor);

    r = Math.max(0, Math.min(255, r));
    g = Math.max(0, Math.min(255, g));
    b = Math.max(0, Math.min(255, b));

    return (a << 24) | (r << 16) | (g << 8) | b;
  }

  /** Blends two colors with alpha channel support. */
  public static int blendWithAlpha(int src, int dst, float alpha) {
    int srcA = (src >> 24) & 0xFF;
    int srcR = (src >> 16) & 0xFF;
    int srcG = (src >> 8) & 0xFF;
    int srcB = src & 0xFF;

    int dstR = (dst >> 16) & 0xFF;
    int dstG = (dst >> 8) & 0xFF;
    int dstB = dst & 0xFF;

    float invAlpha = 1.0f - alpha;
    int r = (int) (srcR * alpha + dstR * invAlpha);
    int g = (int) (srcG * alpha + dstG * invAlpha);
    int b = (int) (srcB * alpha + dstB * invAlpha);

    return (255 << 24) | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
  }

  // =========================================================================
  // PACK/UNPACK - ARGB SIRASI (A-R-G-B)
  // =========================================================================

  /** Double'dan ARGB'ye. Alpha = 255 (opak). Sıra: A-R-G-B */
  public static int packDouble(double r, double g, double b) {
    int ir = clamp((int) (r * 255));
    int ig = clamp((int) (g * 255));
    int ib = clamp((int) (b * 255));
    return (0xFF << 24) | (ir << 16) | (ig << 8) | ib;
  }

  /** Double'dan ARGB'ye. Alpha belirt. Sıra: A-R-G-B Parametreler: (alpha, red, green, blue) */
  public static int packDouble(double a, double r, double g, double b) {
    int ia = clamp((int) (a * 255));
    int ir = clamp((int) (r * 255));
    int ig = clamp((int) (g * 255));
    int ib = clamp((int) (b * 255));
    return (ia << 24) | (ir << 16) | (ig << 8) | ib;
  }

  /** Float'dan ARGB'ye. Alpha = 255 (opak). Sıra: A-R-G-B */
  public static int packFloat(float r, float g, float b) {
    return packDouble((double) r, (double) g, (double) b);
  }

  /** Float'dan ARGB'ye. Alpha belirt. Sıra: A-R-G-B Parametreler: (alpha, red, green, blue) */
  public static int packFloat(float a, float r, float g, float b) {
    return packDouble((double) a, (double) r, (double) g, (double) b);
  }

  /** ARGB'den Double'a. Dizi döner: [A, R, G, B] Sıra: A-R-G-B */
  public static double[] unpackDouble(int color) {
    return new double[] {
      ((color >> 24) & 0xFF) / 255.0, // A
      ((color >> 16) & 0xFF) / 255.0, // R
      ((color >> 8) & 0xFF) / 255.0, // G
      (color & 0xFF) / 255.0 // B
    };
  }

  /** ARGB'den Float'a. Dizi döner: [A, R, G, B] Sıra: A-R-G-B */
  public static float[] unpackFloat(int color) {
    return new float[] {
      ((color >> 24) & 0xFF) / 255.0f, // A
      ((color >> 16) & 0xFF) / 255.0f, // R
      ((color >> 8) & 0xFF) / 255.0f, // G
      (color & 0xFF) / 255.0f // B
    };
  }

  /** Blends two colors with given ratio (0.0-1.0). Result is always fully opaque (alpha=255). */
  public static int blendColors(int color1, int color2, float ratio) {
    ratio = clamp(ratio, 0.0f, 1.0f);
    int r = (int) (((color1 >> 16) & 0xFF) * (1.0f - ratio) + ((color2 >> 16) & 0xFF) * ratio);
    int g = (int) (((color1 >> 8) & 0xFF) * (1.0f - ratio) + ((color2 >> 8) & 0xFF) * ratio);
    int b = (int) ((color1 & 0xFF) * (1.0f - ratio) + (color2 & 0xFF) * ratio);
    return (255 << 24) | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
  }

  // Double ratio version
  public static int blendColors(int color1, int color2, double ratio) {
    ratio = Math.max(0.0, Math.min(1.0, ratio));
    int r = (int) (((color1 >> 16) & 0xFF) * (1.0 - ratio) + ((color2 >> 16) & 0xFF) * ratio);
    int g = (int) (((color1 >> 8) & 0xFF) * (1.0 - ratio) + ((color2 >> 8) & 0xFF) * ratio);
    int b = (int) ((color1 & 0xFF) * (1.0 - ratio) + (color2 & 0xFF) * ratio);
    return (255 << 24) | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
  }

  /** Smooth blending using smoothstep function. Result is always fully opaque (alpha=255). */
  public static int smoothBlend(int c1, int c2, double ratio) {
    ratio = Math.max(0.0, Math.min(1.0, ratio));
    double smoothRatio = ratio * ratio * (3.0 - 2.0 * ratio);
    return blendColors(c1, c2, smoothRatio);
  }

  /** Calculates luminance (brightness) of color using ITU-R BT.709 standard. */
  public static double luminance(int color) {
    return (0.2126 * ((color >> 16) & 0xFF)
            + 0.7152 * ((color >> 8) & 0xFF)
            + 0.0722 * (color & 0xFF))
        / 255.0;
  }

  /** Adjusts color contrast. Result is always fully opaque (alpha=255). */
  public static int adjustContrast(int color, float contrast) {
    float factor = (259f * (contrast + 255f)) / (255f * (259f - contrast));
    int red = adjustComponent(((color >> 16) & 0xFF), factor);
    int green = adjustComponent(((color >> 8) & 0xFF), factor);
    int blue = adjustComponent((color & 0xFF), factor);
    return (255 << 24) | (clamp(red) << 16) | (clamp(green) << 8) | clamp(blue);
  }

  private static int adjustComponent(int component, float factor) {
    float normalized = component / 255f;
    float adjusted = 0.5f + factor * (normalized - 0.5f);
    return (int) (adjusted * 255f);
  }

  /** Adjusts color brightness (exposure). Result is always fully opaque (alpha=255). */
  public static int adjustExposure(int color, float exposure) {
    float r = (((color >> 16) & 0xFF) / 255.0f) * exposure;
    float g = (((color >> 8) & 0xFF) / 255.0f) * exposure;
    float b = ((color & 0xFF) / 255.0f) * exposure;
    return (255 << 24)
        | ((int) (clamp(r, 0f, 1f) * 255) << 16)
        | ((int) (clamp(g, 0f, 1f) * 255) << 8)
        | (int) (clamp(b, 0f, 1f) * 255);
  }

  /** Adjusts color saturation. Result is always fully opaque (alpha=255). */
  public static int adjustSaturation(int color, float saturation) {
    float r = ((color >> 16) & 0xFF) / 255.0f;
    float g = ((color >> 8) & 0xFF) / 255.0f;
    float b = (color & 0xFF) / 255.0f;
    float luminance = 0.2126f * r + 0.7152f * g + 0.0722f * b;

    float resR = clamp(luminance + (r - luminance) * saturation, 0f, 1f);
    float resG = clamp(luminance + (g - luminance) * saturation, 0f, 1f);
    float resB = clamp(luminance + (b - luminance) * saturation, 0f, 1f);
    return (255 << 24)
        | ((int) (resR * 255) << 16)
        | ((int) (resG * 255) << 8)
        | (int) (resB * 255);
  }

  /** Inverts color (negative). Result is always fully opaque (alpha=255). */
  public static int invert(int color) {
    int r = 255 - ((color >> 16) & 0xFF);
    int g = 255 - ((color >> 8) & 0xFF);
    int b = 255 - (color & 0xFF);
    return (255 << 24) | (r << 16) | (g << 8) | b;
  }

  /**
   * Combines multiple colors via additive blending. Rewritten to avoid varargs array allocation —
   * uses index loop on the provided array reference directly. Result is always fully opaque
   * (alpha=255).
   */
  public static int combineColors(int... colors) {
    int r = 0, g = 0, b = 0;
    int len = colors.length;
    for (int i = 0; i < len; i++) {
      int c = colors[i];
      r += (c >> 16) & 0xFF;
      g += (c >> 8) & 0xFF;
      b += c & 0xFF;
      // Early clamp to prevent overflow accumulation across many colors
      if (r > 255) r = 255;
      if (g > 255) g = 255;
      if (b > 255) b = 255;
    }
    return (255 << 24) | (r << 16) | (g << 8) | b;
  }

  /**
   * Enhances color saturation without java.awt.Color allocation. Uses a direct luminance-based
   * desaturation blend in RGB space. Result is always fully opaque (alpha=255).
   */
  public static int enhanceColorSaturation(int color, float saturationFactor) {
    int a = (color >> 24) & 0xFF;
    float r = ((color >> 16) & 0xFF) / 255.0f;
    float g = ((color >> 8) & 0xFF) / 255.0f;
    float b = (color & 0xFF) / 255.0f;

    // ITU-R BT.709 luminance as the grayscale anchor point
    float lum = 0.2126f * r + 0.7152f * g + 0.0722f * b;

    // Blend toward grayscale (saturationFactor=1.0 → no change, >1.0 → boost, <1.0 → desaturate)
    float resR = clamp(lum + (r - lum) * saturationFactor, 0.0f, 1.0f);
    float resG = clamp(lum + (g - lum) * saturationFactor, 0.0f, 1.0f);
    float resB = clamp(lum + (b - lum) * saturationFactor, 0.0f, 1.0f);

    return (a << 24) | ((int) (resR * 255) << 16) | ((int) (resG * 255) << 8) | (int) (resB * 255);
  }

  public static int applyChromaticAbsorption(
      int incomingColor,
      double distance,
      double absorptionDensityR,
      double absorptionDensityG,
      double absorptionDensityB) {
    int r = (incomingColor >> 16) & 0xFF;
    int g = (incomingColor >> 8) & 0xFF;
    int b = incomingColor & 0xFF;

    double tR = Math.exp(-absorptionDensityR * distance);
    double tG = Math.exp(-absorptionDensityG * distance);
    double tB = Math.exp(-absorptionDensityB * distance);

    r = clamp((int) (r * tR));
    g = clamp((int) (g * tG));
    b = clamp((int) (b * tB));

    return 0xFF000000 | (r << 16) | (g << 8) | b;
  }

  /**
   * Shifts hue without java.awt.Color allocation. Converts RGB → HSV, shifts hue, converts back —
   * all in primitive float arithmetic. Result is always fully opaque (alpha=255).
   */
  public static int shiftHue(int color, float hueShift) {
    float r = ((color >> 16) & 0xFF) / 255.0f;
    float g = ((color >> 8) & 0xFF) / 255.0f;
    float b = (color & 0xFF) / 255.0f;

    // RGB → HSV
    float max = Math.max(r, Math.max(g, b));
    float min = Math.min(r, Math.min(g, b));
    float delta = max - min;

    float h = 0.0f;
    float s = (max == 0.0f) ? 0.0f : delta / max;
    float v = max;

    if (delta > 0.0f) {
      if (max == r) {
        h = (g - b) / delta;
        if (h < 0.0f) h += 6.0f;
      } else if (max == g) {
        h = 2.0f + (b - r) / delta;
      } else {
        h = 4.0f + (r - g) / delta;
      }
      h /= 6.0f; // Normalize to [0, 1]
    }

    // Shift hue and wrap to [0, 1]
    h += hueShift / 360.0f;
    h = h - (float) Math.floor(h); // equivalent to % 1.0f, handles negatives safely

    // HSV → RGB
    float resR, resG, resB;
    if (s == 0.0f) {
      // Achromatic — no hue
      resR = resG = resB = v;
    } else {
      float hSector = h * 6.0f;
      int sector = (int) hSector;
      float frac = hSector - sector;
      float p = v * (1.0f - s);
      float q = v * (1.0f - s * frac);
      float t = v * (1.0f - s * (1.0f - frac));

      switch (sector % 6) {
        case 0:
          resR = v;
          resG = t;
          resB = p;
          break;
        case 1:
          resR = q;
          resG = v;
          resB = p;
          break;
        case 2:
          resR = p;
          resG = v;
          resB = t;
          break;
        case 3:
          resR = p;
          resG = q;
          resB = v;
          break;
        case 4:
          resR = t;
          resG = p;
          resB = v;
          break;
        default:
          resR = v;
          resG = p;
          resB = q;
          break;
      }
    }

    return (255 << 24)
        | (clamp((int) (resR * 255)) << 16)
        | (clamp((int) (resG * 255)) << 8)
        | clamp((int) (resB * 255));
  }

  /** Adjusts color temperature (warm/cool). Result is always fully opaque (alpha=255). */
  public static int adjustTemperature(int color, float temperature) {
    temperature = clamp(temperature, -1f, 1f);
    float r = ((color >> 16) & 0xFF) / 255.0f;
    float g = ((color >> 8) & 0xFF) / 255.0f;
    float b = (color & 0xFF) / 255.0f;
    if (temperature > 0) {
      r += temperature;
      g += temperature * 0.5f;
    } else {
      b -= temperature;
    }
    return (255 << 24)
        | ((int) (clamp(r, 0f, 1f) * 255) << 16)
        | ((int) (clamp(g, 0f, 1f) * 255) << 8)
        | (int) (clamp(b, 0f, 1f) * 255);
  }

  /** Converts to black and white based on threshold. Result is always fully opaque (alpha=255). */
  public static int toBlackAndWhite(int color, int threshold) {
    int luminanceVal = (int) (luminance(color) * 255);
    return luminanceVal > threshold ? WHITE : BLACK;
  }

  /** Converts to sepia tone. Result is always fully opaque (alpha=255). */
  public static int toSepia(int color) {
    int r = (color >> 16) & 0xFF;
    int g = (color >> 8) & 0xFF;
    int b = color & 0xFF;
    int tr = (int) (0.393 * r + 0.769 * g + 0.189 * b);
    int tg = (int) (0.349 * r + 0.686 * g + 0.168 * b);
    int tb = (int) (0.272 * r + 0.534 * g + 0.131 * b);
    return (255 << 24) | (clamp(tr) << 16) | (clamp(tg) << 8) | clamp(tb);
  }

  /** Calculates Euclidean distance between two colors. */
  public static double colorDistance(int c1, int c2) {
    double rDiff = ((c1 >> 16) & 0xFF) - ((c2 >> 16) & 0xFF);
    double gDiff = ((c1 >> 8) & 0xFF) - ((c2 >> 8) & 0xFF);
    double bDiff = (c1 & 0xFF) - (c2 & 0xFF);
    return Math.sqrt(rDiff * rDiff + gDiff * gDiff + bDiff * bDiff);
  }

  /** Sets new alpha value for existing color. */
  public static int setAlpha(int color, int alpha) {
    alpha = clamp(alpha, 0, 255);
    return (alpha << 24) | (color & 0x00FFFFFF);
  }

  /** Sets new alpha value (float 0.0-1.0). */
  public static int setAlpha(int color, float alpha) {
    int alphaInt = clamp((int) (alpha * 255));
    return (alphaInt << 24) | (color & 0x00FFFFFF);
  }

  /** Clamp integer value to [min, max]. */
  public static int clamp(int value, int min, int max) {
    return Math.max(min, Math.min(max, value));
  }

  public static int clamp(int value) {
    return Math.max(0, Math.min(255, value));
  }

  public static double clampDoubleColorValue(double value) {
    return Math.max(0.0, Math.min(1.0, value));
  }

  /** Scales color by factor (0.0-1.0). Result is always fully opaque (alpha=255). */
  public static int scale(int color, double factor) {
    factor = Math.max(0.0, Math.min(1.0, factor));
    int r = (int) (((color >> 16) & 0xFF) * factor);
    int g = (int) (((color >> 8) & 0xFF) * factor);
    int b = (int) ((color & 0xFF) * factor);
    return (255 << 24) | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
  }

  /** Applies lighting model (diffuse only). Result is always fully opaque (alpha=255). */
  public static int applyLighting(int baseColor, int lightColor, double intensity, double NdotL) {
    int r =
        (int)
            (((baseColor >> 16) & 0xFF) * ((lightColor >> 16) & 0xFF) / 255.0 * intensity * NdotL);
    int g =
        (int) (((baseColor >> 8) & 0xFF) * ((lightColor >> 8) & 0xFF) / 255.0 * intensity * NdotL);
    int b = (int) ((baseColor & 0xFF) * (lightColor & 0xFF) / 255.0 * intensity * NdotL);
    return (255 << 24) | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
  }

  /**
   * Applies lighting with ambient and diffuse components. Result is always fully opaque
   * (alpha=255).
   */
  public static int applyLightingX(int base, int light, double diffuse, double ambient) {
    int r = (int) (((base >> 16) & 0xFF) * (ambient + diffuse * ((light >> 16) & 0xFF) / 255.0));
    int g = (int) (((base >> 8) & 0xFF) * (ambient + diffuse * ((light >> 8) & 0xFF) / 255.0));
    int b = (int) ((base & 0xFF) * (ambient + diffuse * (light & 0xFF) / 255.0));
    return (255 << 24) | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
  }
}
