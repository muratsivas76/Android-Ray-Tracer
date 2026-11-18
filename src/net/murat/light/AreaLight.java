package net.murat.light;

import java.util.Random;
import net.murat.lovert.Scene;
import net.murat.math.Point3;
import net.murat.math.Ray;
import net.murat.math.Vector3;
import net.murat.util.ColorUtil;

/**
 * License GPL-3.0 Implements a rectangular area light source for soft shadow effects. Samples
 * multiple points across the light surface for realistic illumination. Fully optimized to run 100%
 * garbage-free using packed ARGB integers and pre-allocated temp objects.
 *
 * @author Murat iNAN
 * @version 2.0
 */
public class AreaLight implements Light {
  private Point3 position;
  private final Vector3 normal;
  private final Vector3 right;
  private final Vector3 up;
  private final double width;
  private final double height;
  private int color;
  private double intensity;
  private final int samplesU;
  private final int samplesV;
  private final Random random;
  private String name = "area";

  // Animation delta values
  private double[] incDecIntensity = new double[] {0.0, 0.0};
  private double[] positionDelta = new double[] {0.0, 0.0, 0.0};
  private int[] colorDelta = new int[] {0, 0, 0, 0};

  // ===== GC DOSTU: PRE-ALLOCATED TEMP OBJECTS =====
  private final Point3 tempPoint1 = new Point3();
  private final Point3 tempPoint2 = new Point3();
  private final Vector3 tempVector1 = new Vector3();
  private final Vector3 tempVector2 = new Vector3();
  private final Vector3 tempVector3 = new Vector3();
  private final Vector3 tempDirection = new Vector3();
  private final Ray tempRay = new Ray(new Point3(), new Vector3(), 1.0);

  public AreaLight(
      Point3 position,
      Vector3 normal,
      double width,
      double height,
      int color,
      double intensity,
      int samplesU,
      int samplesV) {
    this.position = position;
    this.normal = normal.normalize();
    this.right = calculateRightVector(this.normal);
    this.up = this.normal.cross(this.right).normalize();
    this.width = Math.max(0, width);
    this.height = Math.max(0, height);
    this.color = color;
    this.intensity = Math.max(0, intensity);
    this.samplesU = Math.max(1, samplesU);
    this.samplesV = Math.max(1, samplesV);
    this.random = new Random();
  }

  private Vector3 calculateRightVector(Vector3 normal) {
    if (Math.abs(normal.y) > 0.9) {
      return new Vector3(1, 0, 0);
    } else {
      return normal.cross(new Vector3(0, 1, 0)).normalize();
    }
  }

  // ============================================================
  // LIGHT INTERFACE - POSITION
  // ============================================================

  @Override
  public Point3 getPosition() {
    return position;
  }

  @Override
  public void setPosition(Point3 position) {
    this.position.set(position);
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
  public double getIntensity() {
    return intensity;
  }

  @Override
  public void setIntensity(double intensity) {
    this.intensity = intensity;
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
    tempDirection.set(position).subtract(point);
    if (tempDirection.lengthSquared() < Ray.EPSILON * Ray.EPSILON) {
      return tempDirection.set(0, 0, 0);
    }
    return tempDirection.normalize();
  }

  @Override
  public Vector3 getDirectionTo(Point3 point) {
    tempDirection.set(point).subtract(position);
    if (tempDirection.lengthSquared() < Ray.EPSILON * Ray.EPSILON) {
      return tempDirection.set(0, 0, 0);
    }
    return tempDirection.normalize();
  }

  // ============================================================
  // LIGHT INTERFACE - INTENSITY CALCULATIONS
  // ============================================================

  @Override
  public double getAttenuatedIntensity(Point3 point) {
    double distance = position.distance(point);
    double attenuation = 1.0 + 0.1 * distance + 0.01 * distance * distance;
    return intensity / Math.max(attenuation, Ray.EPSILON);
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
    int visibleSamples = 0;
    int totalSamples = samplesU * samplesV;

    for (int i = 0; i < samplesU; i++) {
      for (int j = 0; j < samplesV; j++) {
        // Generate sample point - ZERO ALLOCATION
        generateSamplePoint(i, j, tempPoint2);

        // Direction from point to sample
        tempVector1.set(tempPoint2).subtract(point);
        double distance = tempVector1.length();

        if (distance > Ray.EPSILON) {
          tempVector1.normalize();
        } else {
          tempVector1.set(0, 0, 0);
        }

        // Offset shadow ray origin - ZERO ALLOCATION
        tempVector2.set(tempVector1).multiply(Ray.EPSILON * 10.0);
        tempPoint1.set(point).add(tempVector2);

        // Configure shadow ray
        tempRay.set(tempPoint1, tempVector1, 1.0);

        // Fire shadow test
        if (!scene.intersects(tempRay, distance - Ray.EPSILON)) {
          visibleSamples++;
        }
      }
    }

    return visibleSamples > 0;
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

  /** Generates a stratified sample point on the light surface - ZERO ALLOCATION */
  private void generateSamplePoint(int i, int j, Point3 targetPoint) {
    double u = ((i + random.nextDouble()) / samplesU) - 0.5;
    double v = ((j + random.nextDouble()) / samplesV) - 0.5;

    tempVector1.set(right).multiply(u * width);
    tempVector2.set(up).multiply(v * height);
    tempVector1.add(tempVector2);

    targetPoint.set(position).add(tempVector1);
  }

  public double getDistanceTo(Point3 point) {
    return position.distance(point);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("AreaLight ").append(name).append(" {\n");
    sb.append("        name = ").append(name).append(";\n");
    sb.append("        position = ").append(position).append(";\n");
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
    sb.append("        normal = ").append(normal).append(";\n");
    sb.append("        width = ").append(width).append(";\n");
    sb.append("        height = ").append(height).append(";\n");
    sb.append("        samplesU = ").append(samplesU).append(";\n");
    sb.append("        samplesV = ").append(samplesV).append(";\n");
    sb.append("    }");
    return sb.toString();
  }
}
