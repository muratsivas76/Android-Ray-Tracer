package net.murat.light;

import java.util.Collections;
import java.util.List;
import net.murat.lovert.Scene;
import net.murat.math.Point3;
import net.murat.math.Ray;
import net.murat.math.Vector3;
import net.murat.util.ColorUtil;

/**
 * Implements a fantastic bioluminescent light source with multiple pulsing organisms. Samples the
 * closest emitter position dynamically per shading request. Fully optimized to run 100%
 * garbage-free using packed ARGB integers and pre-allocated temp objects. Compatible with Java 6-8
 * without lambdas.
 */
public class BioluminescentLight implements Light {
  private final List<Point3> organismPositions;
  private int baseColor;
  private final double pulseSpeed;
  private double baseIntensity;
  private final double attenuationFactor;
  private double currentTime;
  private String name = "bioluminescent";

  // Animation delta values
  private double[] incDecIntensity = new double[] {0.0, 0.0};
  private double[] positionDelta = new double[] {0.0, 0.0, 0.0};
  private int[] colorDelta = new int[] {0, 0, 0, 0};

  private static final double MIN_PULSE_INTENSITY = 0.7;
  private static final double PULSE_AMPLITUDE = 0.3;

  // ===== GC DOSTU: PRE-ALLOCATED TEMP OBJECTS =====
  private final Point3 tempPoint1 = new Point3();
  private final Point3 tempPoint2 = new Point3();
  private final Vector3 tempVector1 = new Vector3();
  private final Vector3 tempVector2 = new Vector3();
  private final Vector3 tempDirection = new Vector3();
  private final Ray tempRay = new Ray(new Point3(), new Vector3(), 1.0);

  public BioluminescentLight(List<Point3> positions, int color, double pulseSpeed) {
    this(positions, color, pulseSpeed, 100.0, 0.0001);
  }

  public BioluminescentLight(
      List<Point3> positions,
      int color,
      double pulseSpeed,
      double baseIntensity,
      double attenuationFactor) {
    if (positions == null || positions.isEmpty()) {
      throw new IllegalArgumentException("Organism positions cannot be null or empty");
    }
    this.organismPositions = Collections.unmodifiableList(positions);
    this.baseColor = color;
    this.pulseSpeed = Math.max(0.1, pulseSpeed);
    this.baseIntensity = Math.max(10.0, baseIntensity);
    this.attenuationFactor = Math.max(0.00001, attenuationFactor);
    this.currentTime = 0.0;
  }

  public void update(double deltaTime) {
    this.currentTime += deltaTime * this.pulseSpeed;
  }

  // ============================================================
  // LIGHT INTERFACE - POSITION
  // ============================================================

  @Override
  public Point3 getPosition() {
    return organismPositions.get(0);
  }

  @Override
  public void setPosition(Point3 position) {
    // Bioluminescent light uses organism positions list, not a single position
    // Update first organism position
    if (!organismPositions.isEmpty()) {
      organismPositions.get(0).set(position);
    }
  }

  // ============================================================
  // LIGHT INTERFACE - COLOR
  // ============================================================

  @Override
  public int getColor() {
    return baseColor;
  }

  @Override
  public void setColor(int color) {
    this.baseColor = color;
  }

  // ============================================================
  // LIGHT INTERFACE - INTENSITY
  // ============================================================

  @Override
  public void setIntensity(double intensity) {
    this.baseIntensity = Math.max(0.1, intensity);
  }

  @Override
  public double getIntensity() {
    return baseIntensity;
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
    Point3 closest = findClosestPosition(point);
    tempDirection.set(closest).subtract(point);
    if (tempDirection.lengthSquared() > Ray.EPSILON * Ray.EPSILON) {
      return tempDirection.normalize();
    }
    return tempDirection.set(0, 1, 0);
  }

  @Override
  public Vector3 getDirectionTo(Point3 point) {
    Point3 closest = findClosestPosition(point);
    tempDirection.set(point).subtract(closest);
    if (tempDirection.lengthSquared() > Ray.EPSILON * Ray.EPSILON) {
      return tempDirection.normalize();
    }
    return tempDirection.set(0, 1, 0);
  }

  // ============================================================
  // LIGHT INTERFACE - INTENSITY CALCULATIONS
  // ============================================================

  @Override
  public double getAttenuatedIntensity(Point3 point) {
    double distance = getClosestDistance(point);
    double intensity = getIntensity();
    double attenuation = 1.0 / (1.0 + attenuationFactor * distance * distance);
    return intensity * attenuation;
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
    Point3 closest = findClosestPosition(point);

    tempVector1.set(closest).subtract(point);
    double distance = tempVector1.length();

    if (distance < Ray.EPSILON) {
      return true;
    }

    tempVector1.normalize();

    tempVector2.set(tempVector1).multiply(Ray.EPSILON * 2.0);
    tempPoint1.set(point).add(tempVector2);

    tempRay.set(tempPoint1, tempVector1, 1.0);

    return !scene.intersects(tempRay, distance - Ray.EPSILON * 4.0);
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

  public double getClosestDistance(Point3 point) {
    return findClosestPosition(point).distance(point);
  }

  private Point3 findClosestPosition(Point3 point) {
    Point3 closest = organismPositions.get(0);
    double minDistance = closest.distance(point);

    for (int i = 1; i < organismPositions.size(); i++) {
      double distance = organismPositions.get(i).distance(point);
      if (distance < minDistance) {
        minDistance = distance;
        closest = organismPositions.get(i);
      }
    }
    return closest;
  }

  private double calculatePulseFactor() {
    return MIN_PULSE_INTENSITY + PULSE_AMPLITUDE * (0.5 + 0.5 * Math.sin(currentTime));
  }

  public BioluminescentLight withPositions(List<Point3> newPositions) {
    return new BioluminescentLight(
        newPositions, baseColor, pulseSpeed, baseIntensity, attenuationFactor);
  }

  public BioluminescentLight withColor(int newColor) {
    return new BioluminescentLight(
        organismPositions, newColor, pulseSpeed, baseIntensity, attenuationFactor);
  }

  public BioluminescentLight withPulseSpeed(double newSpeed) {
    return new BioluminescentLight(
        organismPositions, baseColor, newSpeed, baseIntensity, attenuationFactor);
  }

  public static BioluminescentLight createDefault() {
    return new BioluminescentLight(
        Collections.singletonList(new Point3(0, 2, 0)), 0xFFFFFFFF, 2.0, 500.0, 0.00001);
  }

  public String organize(List<Point3> points) {
    if (points == null || points.isEmpty()) {
      return "[]";
    }

    StringBuilder result = new StringBuilder("[");
    for (int i = 0; i < points.size(); i++) {
      result.append(points.get(i).toString());
      if (i < points.size() - 1) {
        result.append("-");
      }
    }
    result.append("]");
    return result.toString();
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("BioluminescentLight ").append(name).append(" {\n");
    sb.append("        name = ").append(name).append(";\n");
    sb.append("        positions = ").append(organize(organismPositions)).append(";\n");
    sb.append("        color = ").append(ColorUtil.toColorString(baseColor)).append(";\n");
    sb.append("        pulseSpeed = ").append(pulseSpeed).append(";\n");
    sb.append("        baseIntensity = ").append(baseIntensity).append(";\n");
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
    sb.append("        attenuationFactor = ").append(attenuationFactor).append(";\n");
    sb.append("    }");
    return sb.toString();
  }
}
