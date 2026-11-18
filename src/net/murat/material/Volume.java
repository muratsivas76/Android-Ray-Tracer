package net.murat.material;

import net.murat.util.ColorUtil;

/**
 * Represents a participating medium (volume) with absorption and scattering. Supports both legacy
 * single-color absorption and chromatic (per-channel) absorption where each RGB channel attenuates
 * at its own rate.
 *
 * @author Murat
 * @version 2.0 - Fixed with working chromatic absorption
 */
public class Volume {

  // =========================================================================
  // FIELDS
  // =========================================================================

  /** Legacy absorption color (0x00RRGGBB). Used when chromatic is false. */
  private final int absorption;

  /** Legacy absorption density. Used when chromatic is false. */
  private final double absorptionDensity;

  /** Whether chromatic (per-channel) absorption is enabled. */
  private final boolean chromaticAbsorption;

  /** Chromatic absorption color (0x00RRGGBB) - determines per-channel density scaling. */
  private final int chromaticColor;

  /** Chromatic absorption density - base density multiplied by color channels. */
  private final double chromaticDensity;

  /** Scattering color (0x00RRGGBB). */
  private final int scattering;

  /** Scattering density coefficient. */
  private final double scatteringDensity;

  /** Strength multiplier derived from scattering color alpha channel. */
  private final double scatteringStrength;

  /** Priority flag for volume boundary handling. */
  private final boolean isEnteringHasPriority;

  // =========================================================================
  // CONSTRUCTORS - LEGACY MODE
  // =========================================================================

  public Volume(
      int absorption,
      int scattering,
      double absorptionDensity,
      double scatteringDensity,
      boolean isEnteringHasPriority) {
    this.absorption = absorption & 0x00FFFFFF;
    this.absorptionDensity = ColorUtil.clampDouble(absorptionDensity, 0.0, Double.MAX_VALUE);
    this.chromaticAbsorption = false;
    this.chromaticColor = 0x00000000;
    this.chromaticDensity = 0.0;

    this.scattering = scattering & 0x00FFFFFF;
    this.scatteringDensity = ColorUtil.clampDouble(scatteringDensity, 0.0, Double.MAX_VALUE);
    this.scatteringStrength = extractStrength(scattering);
    this.isEnteringHasPriority = isEnteringHasPriority;
  }

  public Volume(int absorption, int scattering, double density) {
    this(absorption, scattering, density, density, true);
  }

  public Volume(double density) {
    this(0x00000000, 0x00000000, density, density, true);
  }

  // =========================================================================
  // CONSTRUCTORS - CHROMATIC MODE
  // =========================================================================

  /**
   * Chromatic absorption: each RGB channel attenuates at its own rate. Density for each channel =
   * chromaticDensity * (channelValue / 255.0).
   *
   * @param chromaticColor 0x00RRGGBB - per-channel density scaling
   * @param chromaticDensity base density (>= 0)
   * @param scattering scattering color
   * @param scatteringDensity scattering density
   * @param isEnteringHasPriority priority flag
   */
  public Volume(
      int chromaticColor,
      double chromaticDensity,
      int scattering,
      double scatteringDensity,
      boolean isEnteringHasPriority) {
    this.chromaticAbsorption = true;
    this.chromaticColor = chromaticColor & 0x00FFFFFF;
    this.chromaticDensity = ColorUtil.clampDouble(chromaticDensity, 0.0, Double.MAX_VALUE);
    this.absorption = 0x00000000;
    this.absorptionDensity = 0.0;

    this.scattering = scattering & 0x00FFFFFF;
    this.scatteringDensity = ColorUtil.clampDouble(scatteringDensity, 0.0, Double.MAX_VALUE);
    this.scatteringStrength = extractStrength(scattering);
    this.isEnteringHasPriority = isEnteringHasPriority;
  }

  public Volume(int chromaticColor, double chromaticDensity) {
    this(chromaticColor, chromaticDensity, 0x00000000, 0.0, true);
  }

  // =========================================================================
  // PRIVATE HELPERS
  // =========================================================================

  private static double extractStrength(int packedColor) {
    int alphaByte = (packedColor >>> 24) & 0xFF;
    if (alphaByte == 0x00) {
      return 1.0;
    }
    return alphaByte / 255.0;
  }

  // =========================================================================
  // APPLY - ESKI SISTEM MANTIGI + CHROMATIC
  // =========================================================================

  public int apply(int incomingColor, double distance) {
    if (distance <= 0.0) {
      return incomingColor;
    }

    int result = incomingColor;

    // --- ABSORPTION (ESKI GIBI: blendColors ile karisim) ---
    if (chromaticAbsorption && chromaticColor != 0x00000000 && chromaticDensity > 0.0) {
      // Chromatic: her kanal ayri soğurulur
      int r = (chromaticColor >> 16) & 0xFF;
      int g = (chromaticColor >> 8) & 0xFF;
      int b = chromaticColor & 0xFF;

      // Her kanal icin ayri transmittance
      double densityR = chromaticDensity * (r / 255.0);
      double densityG = chromaticDensity * (g / 255.0);
      double densityB = chromaticDensity * (b / 255.0);

      double tR = Math.exp(-densityR * distance);
      double tG = Math.exp(-densityG * distance);
      double tB = Math.exp(-densityB * distance);

      // Her kanali ayri isle
      int incR = (result >> 16) & 0xFF;
      int incG = (result >> 8) & 0xFF;
      int incB = result & 0xFF;

      // Absorption: distance arttıkça soğurma artar (1 - transmittance)
      double absR = (1.0 - tR) * (r / 255.0);
      double absG = (1.0 - tG) * (g / 255.0);
      double absB = (1.0 - tB) * (b / 255.0);

      result =
          0xFF000000
              | (ColorUtil.clamp((int) (incR * (1.0 - absR))) << 16)
              | (ColorUtil.clamp((int) (incG * (1.0 - absG))) << 8)
              | ColorUtil.clamp((int) (incB * (1.0 - absB)));

    } else if (!chromaticAbsorption && absorption != 0x00000000 && absorptionDensity > 0.0) {
      // Legacy: single color blended with incoming (ESKI GIBI)
      double transmittance = Math.exp(-absorptionDensity * distance);
      int absorptionColor = absorption | 0xFF000000;
      double effectiveTint = (1.0 - transmittance) * extractStrength(absorption);
      int tintColor = ColorUtil.multiplyDouble(absorptionColor, effectiveTint);
      result = ColorUtil.blendColors(result, tintColor, (float) effectiveTint);
    }

    // --- SCATTERING (ESKI GIBI: addEnergyConserving ile ekle) ---
    if (scatteringDensity > 0.0 && scattering != 0x00000000 && scatteringStrength > 0.0) {
      double scatteringTransmittance = Math.exp(-scatteringDensity * distance);
      double effectiveScatter = scatteringTransmittance * scatteringStrength;
      int scatteredLight = ColorUtil.multiplyDouble(scattering | 0xFF000000, effectiveScatter);
      result = ColorUtil.addEnergyConserving(result, scatteredLight);
    }

    return result;
  }

  // =========================================================================
  // GETTERS
  // =========================================================================

  public boolean isChromaticAbsorption() {
    return chromaticAbsorption;
  }

  public int getAbsorption() {
    return absorption;
  }

  public double getAbsorptionDensity() {
    return absorptionDensity;
  }

  public int getChromaticColor() {
    return chromaticColor;
  }

  public double getChromaticDensity() {
    return chromaticDensity;
  }

  public int getScattering() {
    return scattering;
  }

  public double getScatteringDensity() {
    return scatteringDensity;
  }

  public double getScatteringStrength() {
    return scatteringStrength;
  }

  public boolean getEnteringHasPriority() {
    return isEnteringHasPriority;
  }

  public boolean hasEffect() {
    boolean hasAbsorption;
    if (chromaticAbsorption) {
      hasAbsorption = chromaticColor != 0x00000000 && chromaticDensity > 0.0;
    } else {
      hasAbsorption = absorption != 0x00000000 && absorptionDensity > 0.0;
    }
    boolean hasScattering =
        scatteringDensity > 0.0 && scattering != 0x00000000 && scatteringStrength > 0.0;
    return hasAbsorption || hasScattering;
  }

  // =========================================================================
  // TO STRING
  // =========================================================================

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("volumeChromaticAbsorption = ").append(chromaticAbsorption).append(";\n");
    sb.append("        volumeChromatic = ")
        .append(ColorUtil.toColorString(chromaticColor))
        .append(";\n");
    sb.append("        volumeChromaticDensity = ").append(chromaticDensity).append(";\n");
    sb.append("        volumeAbsorption = ")
        .append(ColorUtil.toColorString(absorption))
        .append(";\n");
    sb.append("        volumeAbsorptionDensity = ").append(absorptionDensity).append(";\n");
    sb.append("        volumeScattering = ")
        .append(ColorUtil.toColorString(scattering))
        .append(";\n");
    sb.append("        volumeScatteringDensity = ").append(scatteringDensity).append(";\n");
    sb.append("        isEnteringHasPriority = ").append(isEnteringHasPriority);
    return sb.toString();
  }
}
