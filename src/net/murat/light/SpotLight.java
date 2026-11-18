package net.murat.light;

import net.murat.lovert.Scene;
import net.murat.math.Point3;
import net.murat.math.Ray;
import net.murat.math.Vector3;
import net.murat.util.ColorUtil;

/**
 * Represents a spotlight source with inner and outer cone angle attenuation. Fully optimized to run
 * 100% garbage-free in high-performance rendering loops. Compatible with Java 6-8 without lambdas.
 */
public class SpotLight implements Light {
  private Point3 position;
  private Vector3 direction;
  private int color;
  private double intensity;
  private final double cosInnerCone;
  private final double cosOuterCone;
  private final double constantAttenuation;
  private final double linearAttenuation;
  private final double quadraticAttenuation;
  private String name = "spot";

  // Animation delta values
  private double[] incDecIntensity = new double[] {0.0, 0.0};
  private double[] positionDelta = new double[] {0.0, 0.0, 0.0};
  private int[] colorDelta = new int[] {0, 0, 0, 0};

  // ===== GC DOSTU: PRE-ALLOCATED TEMP OBJECTS =====
  private final Point3 tempPoint1 = new Point3();
  private final Vector3 tempVector1 = new Vector3();
  private final Vector3 tempVector2 = new Vector3();
  private final Vector3 tempDirection = new Vector3();
  private final Ray tempRay = new Ray(new Point3(), new Vector3(), 1.0);

  public SpotLight(
      Point3 position,
      Vector3 direction,
      int color,
      double intensity,
      double innerConeAngle,
      double outerConeAngle) {
    this(position, direction, color, intensity, innerConeAngle, outerConeAngle, 1.0, 0.1, 0.01);
  }

  public SpotLight(
      Point3 position,
      Vector3 direction,
      int color,
      double intensity,
      double innerConeAngle,
      double outerConeAngle,
      double constantAttenuation,
      double linearAttenuation,
      double quadraticAttenuation) {
    this.position = position;
    this.direction = direction.normalize();
    this.color = color;
    this.intensity = intensity;
    this.cosInnerCone = Math.cos(Math.toRadians(innerConeAngle / 2));
    this.cosOuterCone = Math.cos(Math.toRadians(outerConeAngle / 2));
    this.constantAttenuation = constantAttenuation;
    this.linearAttenuation = linearAttenuation;
    this.quadraticAttenuation = quadraticAttenuation;
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
    double attenuation =
        constantAttenuation
            + linearAttenuation * distance
            + quadraticAttenuation * distance * distance;
    double coneFactor = calculateConeFactor(point);
    return intensity * coneFactor / Math.max(attenuation, Ray.EPSILON);
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
    getDirectionTo(point, tempVector1);
    double distance = getDistanceTo(point);

    tempVector2.set(tempVector1).multiply(Ray.EPSILON * 10.0);
    tempPoint1.set(point).add(tempVector2);

    tempRay.set(tempPoint1, tempVector1, 1.0);

    return !scene.intersects(tempRay, distance - Ray.EPSILON);
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

  public Vector3 getDirectionTo(Point3 point, Vector3 target) {
    target.set(point).subtract(position);
    if (target.lengthSquared() < Ray.EPSILON * Ray.EPSILON) {
      return target.set(0, 0, 0);
    }
    return target.normalize();
  }

  public double getDistanceTo(Point3 point) {
    return position.distance(point);
  }

  private double calculateConeFactor(Point3 point) {
    getDirectionTo(point, tempVector1);
    double dot = tempVector1.dot(direction);

    if (dot >= cosInnerCone) return 1.0;
    if (dot <= cosOuterCone) return 0.0;

    return (dot - cosOuterCone) / (cosInnerCone - cosOuterCone);
  }

  public double getInnerConeAngle() {
    return Math.toDegrees(Math.acos(cosInnerCone)) * 2;
  }

  public double getOuterConeAngle() {
    return Math.toDegrees(Math.acos(cosOuterCone)) * 2;
  }

  // ============================================================
  // UTILITY METHODS
  // ============================================================

  public SpotLight withPosition(Point3 newPosition) {
    return new SpotLight(
        newPosition,
        direction,
        color,
        intensity,
        getInnerConeAngle(),
        getOuterConeAngle(),
        constantAttenuation,
        linearAttenuation,
        quadraticAttenuation);
  }

  public SpotLight withDirection(Vector3 newDirection) {
    return new SpotLight(
        position,
        newDirection,
        color,
        intensity,
        getInnerConeAngle(),
        getOuterConeAngle(),
        constantAttenuation,
        linearAttenuation,
        quadraticAttenuation);
  }

  public SpotLight withColor(int newColor) {
    return new SpotLight(
        position,
        direction,
        newColor,
        intensity,
        getInnerConeAngle(),
        getOuterConeAngle(),
        constantAttenuation,
        linearAttenuation,
        quadraticAttenuation);
  }

  public SpotLight withIntensity(double newIntensity) {
    return new SpotLight(
        position,
        direction,
        color,
        newIntensity,
        getInnerConeAngle(),
        getOuterConeAngle(),
        constantAttenuation,
        linearAttenuation,
        quadraticAttenuation);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("SpotLight ").append(name).append(" {\n");
    sb.append("        name = ").append(name).append(";\n");
    sb.append("        position = ").append(position).append(";\n");
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
    sb.append("        innerConeAngle = ").append(cosInnerCone).append(";\n");
    sb.append("        outerConeAngle = ").append(cosOuterCone).append(";\n");
    sb.append("        constantAttenuation = ").append(constantAttenuation).append(";\n");
    sb.append("        linearAttenuation = ").append(linearAttenuation).append(";\n");
    sb.append("        quadraticAttenuation = ").append(quadraticAttenuation).append(";\n");
    sb.append("    }");
    return sb.toString();
  }
}
