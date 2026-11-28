package net.murat.light;

import net.murat.lovert.Scene;
import net.murat.math.Point3;
import net.murat.math.Ray;
import net.murat.math.Vector3;
import net.murat.util.ColorUtil;

/**
 * Represents a pulsating point light source that changes position and intensity dynamically over
 * time. Fully optimized to run 100% garbage-free using packed ARGB integers and instance-level
 * scratchpads. Compatible with Java 6-8 without lambdas.
 */
public class PulsatingPointLight implements Light {
  private final Point3 initialPosition;
  private int baseColor;
  private double baseIntensity;
  private final double pulsationSpeed;
  private final double movementSpeed;
  private final double movementAmplitude;
  private final double constantAttenuation;
  private final double linearAttenuation;
  private final double quadraticAttenuation;

  private String name = "pulsating";
  private double currentTime;

  // Animation delta values
  private double[] incDecIntensity = new double[] {0.0, 0.0};
  private double[] positionDelta = new double[] {0.0, 0.0, 0.0};
  private int[] colorDelta = new int[] {0, 0, 0, 0};

  // ===== GC DOSTU: PRE-ALLOCATED TEMP OBJECTS =====
  private final Point3 currentPosition = new Point3();
  private final Point3 tempPoint1 = new Point3();
  private final Vector3 tempVector1 = new Vector3();
  private final Vector3 tempVector2 = new Vector3();
  private final Vector3 tempDirection = new Vector3();
  private final Ray tempRay = new Ray(new Point3(), new Vector3(), 1.0);

  public PulsatingPointLight(
      Point3 initialPosition,
      int baseColor,
      double baseIntensity,
      double pulsationSpeed,
      double movementSpeed,
      double movementAmplitude) {
    this(
        initialPosition,
        baseColor,
        baseIntensity,
        pulsationSpeed,
        movementSpeed,
        movementAmplitude,
        1.0,
        0.1,
        0.01);
  }

  public PulsatingPointLight(
      Point3 initialPosition,
      int baseColor,
      double baseIntensity,
      double pulsationSpeed,
      double movementSpeed,
      double movementAmplitude,
      double constantAttenuation,
      double linearAttenuation,
      double quadraticAttenuation) {
    this.initialPosition = initialPosition;
    this.baseColor = baseColor;
    this.baseIntensity = Math.max(0, baseIntensity);
    this.pulsationSpeed = Math.max(0, pulsationSpeed);
    this.movementSpeed = Math.max(0, movementSpeed);
    this.movementAmplitude = Math.max(0, movementAmplitude);
    this.constantAttenuation = Math.max(0, constantAttenuation);
    this.linearAttenuation = Math.max(0, linearAttenuation);
    this.quadraticAttenuation = Math.max(0, quadraticAttenuation);
    this.currentTime = 0;

    // Initialize current position
    this.currentPosition.set(initialPosition);
  }

  public void update(double deltaTime) {
    this.currentTime += deltaTime;
  }

  // ============================================================
  // LIGHT INTERFACE - POSITION
  // ============================================================

  @Override
  public Point3 getPosition() {
    double offsetX = Math.sin(currentTime * movementSpeed) * movementAmplitude;
    double offsetY = Math.cos(currentTime * movementSpeed * 0.7) * movementAmplitude * 0.5;
    double offsetZ = Math.sin(currentTime * movementSpeed * 0.3) * movementAmplitude * 0.3;

    currentPosition.set(
        initialPosition.x + offsetX, initialPosition.y + offsetY, initialPosition.z + offsetZ);
    return currentPosition;
  }

  @Override
  public void setPosition(Point3 position) {
    this.currentPosition.set(position);
  }

  // ============================================================
  // LIGHT INTERFACE - COLOR
  // ============================================================

  @Override
  public int getColor() {
    double pulsationFactor = 0.7 + 0.3 * Math.sin(currentTime * pulsationSpeed);

    int r = clampColor(((baseColor >> 16) & 0xFF) * pulsationFactor);
    int g = clampColor(((baseColor >> 8) & 0xFF) * pulsationFactor);
    int b = clampColor((baseColor & 0xFF) * pulsationFactor);

    return 0xFF000000 | (r << 16) | (g << 8) | b;
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
    this.baseIntensity = intensity;
  }

  @Override
  public double getIntensity() {
    this.baseIntensity =
        this.baseIntensity * (0.8 + 0.2 * Math.sin(currentTime * pulsationSpeed * 1.3));
    return this.baseIntensity;
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
    double attenuation = calculateAttenuation(distance);
    double pulsationFactor = 0.5 + 0.5 * Math.sin(currentTime * pulsationSpeed * 1.7);
    return getIntensity() * pulsationFactor / Math.max(attenuation, Ray.EPSILON);
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
    target.set(point).subtract(getPosition());
    if (target.lengthSquared() < Ray.EPSILON * Ray.EPSILON) {
      return target.set(0, 0, 0);
    }
    return target.normalize();
  }

  public double getDistanceTo(Point3 point) {
    return getPosition().distance(point);
  }

  private double calculateAttenuation(double distance) {
    return constantAttenuation
        + linearAttenuation * distance
        + quadraticAttenuation * distance * distance;
  }

  private int clampColor(double value) {
    return (int) Math.max(0, Math.min(255, value));
  }

  // ============================================================
  // UTILITY METHODS
  // ============================================================

  public PulsatingPointLight withPosition(Point3 newPosition) {
    return new PulsatingPointLight(
        newPosition,
        baseColor,
        baseIntensity,
        pulsationSpeed,
        movementSpeed,
        movementAmplitude,
        constantAttenuation,
        linearAttenuation,
        quadraticAttenuation);
  }

  public PulsatingPointLight withColor(int newColor) {
    return new PulsatingPointLight(
        initialPosition,
        newColor,
        baseIntensity,
        pulsationSpeed,
        movementSpeed,
        movementAmplitude,
        constantAttenuation,
        linearAttenuation,
        quadraticAttenuation);
  }

  public PulsatingPointLight withIntensity(double newIntensity) {
    return new PulsatingPointLight(
        initialPosition,
        baseColor,
        newIntensity,
        pulsationSpeed,
        movementSpeed,
        movementAmplitude,
        constantAttenuation,
        linearAttenuation,
        quadraticAttenuation);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("PulsatingPointLight ").append(name).append(" {\n");
    sb.append("        name = ").append(name).append(";\n");
    sb.append("        initialPosition = ").append(initialPosition).append(";\n");
    sb.append("        baseColor = ").append(ColorUtil.toColorString(baseColor)).append(";\n");
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
    sb.append("        pulsationSpeed = ").append(pulsationSpeed).append(";\n");
    sb.append("        movementSpeed = ").append(movementSpeed).append(";\n");
    sb.append("        movementAmplitude = ").append(movementAmplitude).append(";\n");
    sb.append("        constantAttenuation = ").append(constantAttenuation).append(";\n");
    sb.append("        linearAttenuation = ").append(linearAttenuation).append(";\n");
    sb.append("        quadraticAttenuation = ").append(quadraticAttenuation).append(";\n");
    sb.append("    }");
    return sb.toString();
  }
}
