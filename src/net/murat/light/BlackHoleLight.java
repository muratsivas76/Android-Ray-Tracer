package net.murat.light;

import net.murat.lovert.Scene;
import net.murat.math.Point3;
import net.murat.math.Ray;
import net.murat.math.Vector3;
import net.murat.util.ColorUtil;

/**
 * Implements a unique black hole light source with gravitational warping simulations. Fully
 * optimized to run 100% garbage-free in high-performance rendering loops. Compatible with Java 6-8
 * without lambdas.
 */
public class BlackHoleLight implements Light {
  private Point3 singularity;
  private final double eventHorizonRadius;
  private int accretionColor;
  private double baseIntensity;
  private String name = "blackhole";

  // Animation delta values
  private double[] incDecIntensity = new double[] {0.0, 0.0};
  private double[] positionDelta = new double[] {0.0, 0.0, 0.0};
  private int[] colorDelta = new int[] {0, 0, 0, 0};

  private static final double GRAVITATIONAL_WARP_FACTOR = 2.0;

  // ===== GC DOSTU: PRE-ALLOCATED TEMP OBJECTS =====
  private final Point3 tempPoint1 = new Point3();
  private final Vector3 tempVector1 = new Vector3();
  private final Vector3 tempVector2 = new Vector3();
  private final Vector3 tempDirection = new Vector3();
  private final Ray tempRay = new Ray(new Point3(), new Vector3(), 1.0);

  public BlackHoleLight(Point3 singularity, double radius, int color) {
    this(singularity, radius, color, 1.5);
  }

  public BlackHoleLight(Point3 singularity, double radius, int color, double intensity) {
    if (singularity == null) {
      throw new IllegalArgumentException("Singularity point cannot be null");
    }
    this.singularity = singularity;
    this.eventHorizonRadius = Math.max(0.1, radius);
    this.accretionColor = color;
    this.baseIntensity = Math.max(0, intensity);
  }

  // ============================================================
  // LIGHT INTERFACE - POSITION
  // ============================================================

  @Override
  public Point3 getPosition() {
    return singularity;
  }

  @Override
  public void setPosition(Point3 position) {
    this.singularity.set(position);
  }

  // ============================================================
  // LIGHT INTERFACE - COLOR
  // ============================================================

  @Override
  public int getColor() {
    return accretionColor;
  }

  @Override
  public void setColor(int color) {
    this.accretionColor = color;
  }

  // ============================================================
  // LIGHT INTERFACE - INTENSITY
  // ============================================================

  @Override
  public void setIntensity(double intensity) {
    this.baseIntensity = intensity;
  }

  @Override
  public double getIntensity() {
    return baseIntensity;
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
    tempDirection.set(singularity).subtract(point);
    double dist = tempDirection.length();
    if (dist < Ray.EPSILON) {
      return tempDirection.set(0, 0, 0);
    }
    double warpFactor = GRAVITATIONAL_WARP_FACTOR / (1.0 - Math.exp(-dist / eventHorizonRadius));
    return tempDirection.normalize().multiply(warpFactor);
  }

  @Override
  public Vector3 getDirectionTo(Point3 point) {
    tempDirection.set(point).subtract(singularity);
    double dist = tempDirection.length();
    if (dist < Ray.EPSILON) {
      return tempDirection.set(0, 0, 0);
    }
    return tempDirection.normalize();
  }

  // ============================================================
  // LIGHT INTERFACE - INTENSITY CALCULATIONS
  // ============================================================

  @Override
  public double getAttenuatedIntensity(Point3 point) {
    double distance = singularity.distance(point);
    if (distance < eventHorizonRadius) {
      return 0.0;
    }
    return baseIntensity * (1.0 - eventHorizonRadius / distance);
  }

  @Override
  public double getIntensityAt(Point3 point) {
    return getAttenuatedIntensity(point);
  }

  // ============================================================
  // LIGHT INTERFACE - VISIBILITY
  // ============================================================

  @Override
  public boolean isVisibleFrom(Point3 point, Scene scene) {
    if (isPointBeyondEventHorizon(point)) {
      return false;
    }

    getDirectionTo(point, tempVector1);

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
  // PRIVATE HELPERS
  // ============================================================

  public double getEventHorizonRadius() {
    return eventHorizonRadius;
  }

  public boolean isPointBeyondEventHorizon(Point3 point) {
    return singularity.distance(point) < eventHorizonRadius;
  }

  public Vector3 getDirectionTo(Point3 point, Vector3 target) {
    target.set(point).subtract(singularity);
    if (target.lengthSquared() < Ray.EPSILON * Ray.EPSILON) {
      return target.set(0, 0, 0);
    }
    return target.normalize();
  }

  public BlackHoleLight withSingularity(Point3 newSingularity) {
    return new BlackHoleLight(newSingularity, eventHorizonRadius, accretionColor, baseIntensity);
  }

  public BlackHoleLight withRadius(double newRadius) {
    return new BlackHoleLight(singularity, newRadius, accretionColor, baseIntensity);
  }

  public BlackHoleLight withColor(int newColor) {
    return new BlackHoleLight(singularity, eventHorizonRadius, newColor, baseIntensity);
  }

  public BlackHoleLight withIntensity(double newIntensity) {
    return new BlackHoleLight(singularity, eventHorizonRadius, accretionColor, newIntensity);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("BlackHoleLight ").append(name).append(" {\n");
    sb.append("        name = ").append(name).append(";\n");
    sb.append("        singularity = ").append(singularity).append(";\n");
    sb.append("        radius = ").append(eventHorizonRadius).append(";\n");
    sb.append("        color = ").append(ColorUtil.toColorString(accretionColor)).append(";\n");
    sb.append("        intensity = ").append(baseIntensity).append(";\n");
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
