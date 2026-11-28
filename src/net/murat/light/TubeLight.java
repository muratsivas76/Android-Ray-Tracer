package net.murat.light;

import java.util.Random;
import net.murat.lovert.Scene;
import net.murat.math.Point3;
import net.murat.math.Ray;
import net.murat.math.Vector3;
import net.murat.util.ColorUtil;

/**
 * License GPL-3.0 Implements a linear tube light source for fluorescent-like lighting. Emits light
 * along a line segment with soft shadow effects. Fully optimized to run 100% garbage-free using
 * packed ARGB integers and pre-allocated temp objects.
 *
 * @author Murat iNAN
 * @version 2.0
 */
public class TubeLight implements Light {
  private final Point3 startPoint;
  private final Point3 endPoint;
  private final double radius;
  private int color;
  private double intensity;
  private final int sampleCount;
  private final Random random;
  private final Vector3 direction;
  private final double length;
  private String name = "tube";

  // Animation delta values
  private double[] incDecIntensity = new double[] {0.0, 0.0};
  private double[] positionDelta = new double[] {0.0, 0.0, 0.0};
  private int[] colorDelta = new int[] {0, 0, 0, 0};

  // ===== GC DOSTU: PRE-ALLOCATED TEMP OBJECTS =====
  private final Point3 midpoint = new Point3();
  private final Point3 tempPoint1 = new Point3();
  private final Point3 tempPoint2 = new Point3();
  private final Vector3 tempVector1 = new Vector3();
  private final Vector3 tempVector2 = new Vector3();
  private final Vector3 tempVector3 = new Vector3();
  private final Vector3 tempDirection = new Vector3();
  private final Ray tempRay = new Ray(new Point3(), new Vector3(), 1.0);

  public TubeLight(
      Point3 startPoint,
      Point3 endPoint,
      double radius,
      int color,
      double intensity,
      int sampleCount) {
    this.startPoint = startPoint;
    this.endPoint = endPoint;
    this.radius = Math.max(0, radius);
    this.color = color;
    this.intensity = Math.max(0, intensity);
    this.sampleCount = Math.max(1, sampleCount);
    this.random = new Random();
    this.direction = endPoint.subtractToVector(startPoint);
    this.length = direction.length();
  }

  // ============================================================
  // LIGHT INTERFACE - POSITION
  // ============================================================

  @Override
  public Point3 getPosition() {
    midpoint.set(startPoint);
    tempVector1.set(direction).multiply(0.5);
    midpoint.add(tempVector1);
    return midpoint;
  }

  @Override
  public void setPosition(Point3 position) {
    // Tube light uses start/end points, not a single position
    // Update both start and end points to shift the tube
    double offsetX = position.x - getPosition().x;
    double offsetY = position.y - getPosition().y;
    double offsetZ = position.z - getPosition().z;
    startPoint.x += offsetX;
    startPoint.y += offsetY;
    startPoint.z += offsetZ;
    endPoint.x += offsetX;
    endPoint.y += offsetY;
    endPoint.z += offsetZ;
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
    tempDirection.set(getPosition()).subtract(point);
    if (tempDirection.lengthSquared() < Ray.EPSILON * Ray.EPSILON) {
      return tempDirection.set(0, 0, 0);
    }
    return tempDirection.normalize();
  }

  @Override
  public Vector3 getDirectionTo(Point3 point) {
    tempDirection.set(point).subtract(getPosition());
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
    double attenuation = 1.0 + 0.05 * distance + 0.005 * distance * distance;
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
      double sampleDistance = tempVector1.length();

      if (sampleDistance > Ray.EPSILON) {
        tempVector1.normalize();
      } else {
        tempVector1.set(0, 0, 0);
      }

      tempVector2.set(tempVector1).multiply(Ray.EPSILON * 10.0);
      tempPoint1.set(point).add(tempVector2);

      tempRay.set(tempPoint1, tempVector1, 1.0);

      if (!scene.intersects(tempRay, sampleDistance - Ray.EPSILON)) {
        visibleSamples++;
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
  // SURFACE SAMPLING
  // ============================================================

  private void generateSamplePoint(Point3 targetPoint) {
    double t = random.nextDouble();
    tempPoint1.set(startPoint);
    tempVector1.set(direction).multiply(t);
    tempPoint1.add(tempVector1);

    tempVector1.set(direction).normalize();
    generatePerpendicularVector(tempVector1, tempVector2);
    tempVector2.normalize();

    double angle = 2.0 * Math.PI * random.nextDouble();
    tempVector2.rotateAround(tempVector1, angle);
    tempVector2.multiply(radius);

    targetPoint.set(tempPoint1).add(tempVector2);
  }

  private void generatePerpendicularVector(Vector3 dir, Vector3 target) {
    if (Math.abs(dir.x) > 0.9) {
      double tx = -dir.z;
      double ty = 0;
      double tz = dir.x;
      target.set(tx, ty, tz);
    } else {
      double tx = 0;
      double ty = dir.z;
      double tz = -dir.y;
      target.set(tx, ty, tz);
    }
  }

  // ============================================================
  // UTILITY METHODS
  // ============================================================

  public double getDistanceTo(Point3 point) {
    return getPosition().distance(point);
  }

  public Point3 getStartPoint() {
    return startPoint;
  }

  public Point3 getEndPoint() {
    return endPoint;
  }

  public double getRadius() {
    return radius;
  }

  public double getLength() {
    return length;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("TubeLight ").append(name).append(" {\n");
    sb.append("        name = ").append(name).append(";\n");
    sb.append("        position = ").append(getPosition()).append(";\n");
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
    sb.append("        startPoint = ").append(startPoint).append(";\n");
    sb.append("        endPoint = ").append(endPoint).append(";\n");
    sb.append("        radius = ").append(radius).append(";\n");
    sb.append("        length = ").append(length).append(";\n");
    sb.append("        sampleCount = ").append(sampleCount).append(";\n");
    sb.append("    }");
    return sb.toString();
  }
}
