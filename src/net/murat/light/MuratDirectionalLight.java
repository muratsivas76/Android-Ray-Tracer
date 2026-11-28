package net.murat.light;

import net.murat.lovert.Scene;
import net.murat.math.Point3;
import net.murat.math.Ray;
import net.murat.math.Vector3;
import net.murat.util.ColorUtil;

/**
 * Represents a directional light source (like the sun) with parallel light rays. Fully optimized to
 * run 100% garbage-free in high-performance rendering loops. Compatible with Java 6-8 without
 * lambdas.
 */
public class MuratDirectionalLight implements Light {
  private Vector3 direction;
  private int color;
  private double intensity;
  private String name = "directional";

  // Animation delta values
  private double[] incDecIntensity = new double[] {0.0, 0.0};
  private double[] positionDelta = new double[] {0.0, 0.0, 0.0};
  private int[] colorDelta = new int[] {0, 0, 0, 0};

  private static final double MIN_DIRECTION_LENGTH = 1e-6;

  // ===== GC DOSTU: PRE-ALLOCATED TEMP OBJECTS =====
  private final Point3 tempPoint1 = new Point3();
  private final Vector3 tempVector1 = new Vector3();
  private final Vector3 tempVector2 = new Vector3();
  private final Vector3 tempDirection = new Vector3();
  private final Ray tempRay = new Ray(new Point3(), new Vector3(), 1.0);

  public MuratDirectionalLight(Vector3 direction, int color, double intensity) {
    if (direction == null || direction.length() < MIN_DIRECTION_LENGTH) {
      throw new IllegalArgumentException("Direction cannot be null or zero-length vector");
    }
    this.direction = direction.normalize();
    this.color = color;
    this.intensity = Math.max(0, intensity);
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
    // Directional light has no position
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
    this.incDecIntensity = delta;
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
    tempDirection.set(direction).negate();
    return tempDirection;
  }

  @Override
  public Vector3 getDirectionTo(Point3 point) {
    tempDirection.set(direction);
    return tempDirection;
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
    tempVector1.set(direction);

    tempVector2.set(tempVector1).multiply(Ray.EPSILON * 10.0);
    tempPoint1.set(point).add(tempVector2);

    tempRay.set(tempPoint1, tempVector1, 1.0);

    return !scene.intersects(tempRay, Double.POSITIVE_INFINITY);
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

  public Vector3 getDirection() {
    return direction;
  }

  public MuratDirectionalLight withDirection(Vector3 newDirection) {
    return new MuratDirectionalLight(newDirection, color, intensity);
  }

  public MuratDirectionalLight withColor(int newColor) {
    return new MuratDirectionalLight(direction, newColor, intensity);
  }

  public MuratDirectionalLight withIntensity(double newIntensity) {
    return new MuratDirectionalLight(direction, color, newIntensity);
  }

  public static MuratDirectionalLight createDefault() {
    return new MuratDirectionalLight(new Vector3(-1, -1, -1).normalize(), 0xFFFFFFE6, 0.8);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("MuratDirectionalLight ").append(name).append(" {\n");
    sb.append("        name = ").append(name).append(";\n");
    sb.append("        direction = ").append(direction).append(";\n");
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
