package net.murat.light;

import net.murat.lovert.Scene;
import net.murat.math.Point3;
import net.murat.math.Vector3;
import net.murat.util.ColorUtil;

/**
 * Represents an ambient light source providing uniform background illumination. Fully updated to
 * use primitive packed ARGB integers for zero-allocation performance. Compatible with Java 6-8
 * without lambdas.
 */
public class MuratAmbientLight implements Light {
  private int color;
  private double intensity;
  private String name = "ambient";

  // Animation delta values
  private double[] incDecIntensity = new double[] {0.0, 0.0};
  private double[] positionDelta = new double[] {0.0, 0.0, 0.0};
  private int[] colorDelta = new int[] {0, 0, 0, 0};

  private static final Vector3 ZERO_VECTOR = new Vector3(0, 0, 0);

  public MuratAmbientLight(int color, double intensity) {
    this.color = color;
    this.intensity = Math.max(0, Math.min(1, intensity));
  }

  // ============================================================
  // LIGHT INTERFACE - POSITION
  // ============================================================

  @Override
  public Point3 getPosition() {
    return null;
  }

  @Override
  public void setPosition(Point3 position) {
    // Ambient light has no position
  }

  // ============================================================
  // LIGHT INTERFACE - COLOR
  // ============================================================

  @Override
  public int getColor() {
    return color;
  }

  @Override
  public void setColor(int color) {
    this.color = color;
  }

  // ============================================================
  // LIGHT INTERFACE - INTENSITY
  // ============================================================

  @Override
  public void setIntensity(double intensity) {
    this.intensity = intensity;
  }

  @Override
  public double getIntensity() {
    return intensity;
  }

  @Override
  public void setIncDecIntensity(double[] delta) {
    if (delta != null && delta.length >= 2) {
      this.incDecIntensity[0] = delta[0];
      this.incDecIntensity[1] = delta[1];
    }
  }

  @Override
  public double[] getIncDecIntensity() {
    return this.incDecIntensity;
  }

  // ============================================================
  // LIGHT INTERFACE - DIRECTION
  // ============================================================

  @Override
  public Vector3 getDirectionAt(Point3 point) {
    return ZERO_VECTOR;
  }

  @Override
  public Vector3 getDirectionTo(Point3 point) {
    return ZERO_VECTOR;
  }

  // ============================================================
  // LIGHT INTERFACE - INTENSITY CALCULATIONS
  // ============================================================

  @Override
  public double getAttenuatedIntensity(Point3 point) {
    return intensity;
  }

  @Override
  public double getIntensityAt(Point3 point) {
    return intensity;
  }

  // ============================================================
  // LIGHT INTERFACE - VISIBILITY
  // ============================================================

  @Override
  public boolean isVisibleFrom(Point3 point, Scene scene) {
    return true;
  }

  // ============================================================
  // LIGHT INTERFACE - IDENTIFICATION
  // ============================================================

  @Override
  public String getName() {
    return this.name;
  }

  @Override
  public void setName(String name) {
    this.name = name;
  }

  // ============================================================
  // LIGHT INTERFACE - POSITION ANIMATION
  // ============================================================

  @Override
  public void setPositionDelta(double[] delta) {
    this.positionDelta = delta;
  }

  @Override
  public double[] getPositionDelta() {
    return this.positionDelta;
  }

  // ============================================================
  // LIGHT INTERFACE - COLOR ANIMATION
  // ============================================================

  @Override
  public void setColorDelta(int[] delta) {
    this.colorDelta = delta;
  }

  @Override
  public int[] getColorDelta() {
    return this.colorDelta;
  }

  // ============================================================
  // UTILITY METHODS
  // ============================================================

  public MuratAmbientLight withColor(int newColor) {
    return new MuratAmbientLight(newColor, intensity);
  }

  public MuratAmbientLight withIntensity(double newIntensity) {
    return new MuratAmbientLight(color, newIntensity);
  }

  public static MuratAmbientLight createDefault() {
    return new MuratAmbientLight(0xFFC8DCFF, 0.15);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("MuratAmbientLight ").append(name).append(" {\n");
    sb.append("        name = ").append(name).append(";\n");
    sb.append("        color = ").append(ColorUtil.toColorString(color)).append(";\n");
    sb.append("        intensity = ").append(intensity).append(";\n");
    sb.append("        firstAnimationIntensity = ").append(incDecIntensity[0]).append(";\n");
    sb.append("        secondAnimationIntensity = ").append(incDecIntensity[1]).append(";\n");
    sb.append("        positionDelta = [");
    sb.append(positionDelta[0])
        .append(", ")
        .append(positionDelta[1])
        .append(", ")
        .append(positionDelta[2]);
    sb.append("];\n");
    sb.append("        colorDelta = [");
    sb.append(colorDelta[0]).append(", ").append(colorDelta[1]);
    sb.append(", ").append(colorDelta[2]).append(", ").append(colorDelta[3]);
    sb.append("];\n");
    sb.append("    }");
    return sb.toString();
  }
}
