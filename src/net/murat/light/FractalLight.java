package net.murat.light;

import net.murat.lovert.Scene;
import net.murat.math.Point3;
import net.murat.math.Ray;
import net.murat.math.Vector3;
import net.murat.util.ColorUtil;

/**
 * Implements a procedural fractal light source driven by Ken Perlin's Improved Noise algorithm.
 * Dynamically modulates light intensity in 3D space using layered octaves. Fully optimized to run
 * 100% garbage-free using packed ARGB integers and pre-allocated temp objects. Compatible with Java
 * 6-8 without lambdas.
 */
public class FractalLight implements Light {
  private Point3 position;
  private int baseColor;
  private double baseIntensity;
  private final int octaves;
  private final double persistence;
  private final double frequency;
  private final double[] randoms;
  private final int[] permutations;
  private String name = "fractal";

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

  public FractalLight(Point3 position, int color, double intensity) {
    this(position, color, intensity, 4, 0.5, 0.1);
  }

  public FractalLight(
      Point3 position,
      int color,
      double intensity,
      int octaves,
      double persistence,
      double frequency) {
    this.position = position;
    this.baseColor = color;
    this.baseIntensity = Math.max(0, intensity);
    this.octaves = Math.max(1, octaves);
    this.persistence = Math.max(0, Math.min(1, persistence));
    this.frequency = Math.max(0.001, frequency);
    this.randoms = new double[256];
    this.permutations = new int[512];

    initializeNoise();
  }

  private void initializeNoise() {
    for (int i = 0; i < 256; i++) {
      randoms[i] = Math.random() * 2.0 - 1.0;
      permutations[i] = i;
    }

    for (int i = 255; i > 0; i--) {
      int index = (int) (Math.random() * (i + 1));
      int temp = permutations[i];
      permutations[i] = permutations[index];
      permutations[index] = temp;
    }

    System.arraycopy(permutations, 0, permutations, 256, 256);
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
    double noise = fractalNoise(point.x, point.y, point.z);
    return baseIntensity * (0.3 + 0.7 * noise);
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
    double distance = position.distance(point);

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

  private double fractalNoise(double x, double y, double z) {
    double total = 0;
    double amplitude = 1.0;
    double maxAmplitude = 0;
    double freq = frequency;

    for (int i = 0; i < octaves; i++) {
      total += improvedNoise(x * freq, y * freq, z * freq) * amplitude;
      maxAmplitude += amplitude;
      amplitude *= persistence;
      freq *= 2.0;
    }

    return total / maxAmplitude;
  }

  private double improvedNoise(double x, double y, double z) {
    int xi = (int) Math.floor(x) & 255;
    int yi = (int) Math.floor(y) & 255;
    int zi = (int) Math.floor(z) & 255;

    double xf = x - Math.floor(x);
    double yf = y - Math.floor(y);
    double zf = z - Math.floor(z);

    double u = fade(xf);
    double v = fade(yf);
    double w = fade(zf);

    int aaa = permutations[permutations[permutations[xi] + yi] + zi];
    int aba = permutations[permutations[permutations[xi] + yi + 1] + zi];
    int aab = permutations[permutations[permutations[xi] + yi] + zi + 1];
    int abb = permutations[permutations[permutations[xi] + yi + 1] + zi + 1];
    int baa = permutations[permutations[permutations[xi + 1] + yi] + zi];
    int bba = permutations[permutations[permutations[xi + 1] + yi + 1] + zi];
    int bab = permutations[permutations[permutations[xi + 1] + yi] + zi + 1];
    int bbb = permutations[permutations[permutations[xi + 1] + yi + 1] + zi + 1];

    double x1 = lerp(u, grad(aaa, xf, yf, zf), grad(baa, xf - 1, yf, zf));
    double x2 = lerp(u, grad(aba, xf, yf - 1, zf), grad(bba, xf - 1, yf - 1, zf));
    double y1 = lerp(v, x1, x2);

    x1 = lerp(u, grad(aab, xf, yf, zf - 1), grad(bab, xf - 1, yf, zf - 1));
    x2 = lerp(u, grad(abb, xf, yf - 1, zf - 1), grad(bbb, xf - 1, yf - 1, zf - 1));
    double y2 = lerp(v, x1, x2);

    return (lerp(w, y1, y2) + 1.0) / 2.0;
  }

  private double fade(double t) {
    return t * t * t * (t * (t * 6.0 - 15.0) + 10.0);
  }

  private double lerp(double t, double a, double b) {
    return a + t * (b - a);
  }

  private double grad(int hash, double x, double y, double z) {
    int h = hash & 15;
    double u = h < 8 ? x : y;
    double v = h < 4 ? y : (h == 12 || h == 14 ? x : z);
    return ((h & 1) == 0 ? u : -u) + ((h & 2) == 0 ? v : -v);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("FractalLight ").append(name).append(" {\n");
    sb.append("        name = ").append(name).append(";\n");
    sb.append("        position = ").append(position).append(";\n");
    sb.append("        color = ").append(ColorUtil.toColorString(baseColor)).append(";\n");
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
    sb.append("        octaves = ").append(octaves).append(";\n");
    sb.append("        persistence = ").append(persistence).append(";\n");
    sb.append("        frequency = ").append(frequency).append(";\n");
    sb.append("    }");
    return sb.toString();
  }
}
