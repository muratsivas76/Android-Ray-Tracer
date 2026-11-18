package net.murat.light;

import java.util.Random;
import net.murat.lovert.Scene;
import net.murat.math.Point3;
import net.murat.math.Ray;
import net.murat.math.Vector3;
import net.murat.util.ColorUtil;

/**
 * License GPL-3.0 Implements a spherical light source that emits light uniformly from its surface.
 * Produces soft shadows and realistic illumination for spherical light sources. Fully optimized to
 * run 100% garbage-free using packed ARGB integers and pre-allocated temp objects.
 *
 * @author Murat iNAN
 * @version 2.0
 */
public class SphereLight implements Light {
  private Point3 center;
  private final double radius;
  private int color;
  private double intensity;
  private final int sampleCount;
  private final Random random;
  private String name = "sphereLight";

  // Animation delta values
  private double[] incDecIntensity = new double[] {0.0, 0.0};
  private double[] positionDelta = new double[] {0.0, 0.0, 0.0};
  private int[] colorDelta = new int[] {0, 0, 0, 0};

  // ===== GC DOSTU: PRE-ALLOCATED TEMP OBJECTS =====
  private final Point3 tempPoint1 = new Point3();
  private final Point3 tempPoint2 = new Point3();
  private final Vector3 tempVector1 = new Vector3();
  private final Vector3 tempVector2 = new Vector3();
  private final Vector3 tempDirection = new Vector3();
  private final Ray tempRay = new Ray(new Point3(), new Vector3(), 1.0);

  public SphereLight(Point3 center, double radius, int color, double intensity, int sampleCount) {
    this.center = center;
    this.radius = Math.max(0, radius);
    this.color = color;
    this.intensity = Math.max(0, intensity);
    this.sampleCount = Math.max(1, sampleCount);
    this.random = new Random();
  }

  // ============================================================
  // LIGHT INTERFACE - POSITION
  // ============================================================

  @Override
  public Point3 getPosition() {
    return center;
  }

  @Override
  public void setPosition(Point3 position) {
    this.center.set(position);
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
    tempDirection.set(center).subtract(point);
    if (tempDirection.lengthSquared() < Ray.EPSILON * Ray.EPSILON) {
      return tempDirection.set(0, 0, 0);
    }
    return tempDirection.normalize();
  }

  @Override
  public Vector3 getDirectionTo(Point3 point) {
    tempDirection.set(point).subtract(center);
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
    double distance = getDistanceTo(point);
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

    for (int i = 0; i < sampleCount; i++) {
      generateSamplePoint(tempPoint2);

      tempVector1.set(tempPoint2).subtract(point);
      double distance = tempVector1.length();

      if (distance > Ray.EPSILON) {
        tempVector1.normalize();
      } else {
        tempVector1.set(0, 0, 0);
      }

      tempVector2.set(tempVector1).multiply(Ray.EPSILON * 10.0);
      tempPoint1.set(point).add(tempVector2);

      tempRay.set(tempPoint1, tempVector1, 1.0);

      if (!scene.intersects(tempRay, distance - Ray.EPSILON)) {
        visibleSamples++;
      }
    }

    return visibleSamples > (sampleCount / 2);
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

  private void generateSamplePoint(Point3 targetPoint) {
    double theta = 2.0 * Math.PI * random.nextDouble();
    double phi = Math.acos(1.0 - 2.0 * random.nextDouble());

    double sx = center.x + radius * Math.sin(phi) * Math.cos(theta);
    double sy = center.y + radius * Math.sin(phi) * Math.sin(theta);
    double sz = center.z * Math.cos(phi);

    targetPoint.set(sx, sy, sz);
  }

  public double getDistanceTo(Point3 point) {
    return center.distance(point);
  }

  public double getRadius() {
    return radius;
  }

  public int getSampleCount() {
    return sampleCount;
  }

  // ============================================================
  // UTILITY METHODS
  // ============================================================

  public SphereLight withCenter(Point3 newCenter) {
    return new SphereLight(newCenter, radius, color, intensity, sampleCount);
  }

  public SphereLight withColor(int newColor) {
    return new SphereLight(center, radius, newColor, intensity, sampleCount);
  }

  public SphereLight withIntensity(double newIntensity) {
    return new SphereLight(center, radius, color, newIntensity, sampleCount);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("SphereLight ").append(name).append(" {\n");
    sb.append("        name = ").append(name).append(";\n");
    sb.append("        center = ").append(center).append(";\n");
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
    sb.append("        radius = ").append(radius).append(";\n");
    sb.append("        sampleCount = ").append(sampleCount).append(";\n");
    sb.append("    }");
    return sb.toString();
  }
}
