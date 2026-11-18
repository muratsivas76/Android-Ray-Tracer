package net.murat.material;

import net.murat.light.Light;
import net.murat.light.LightProperties;
import net.murat.math.*;

/**
 * FractalFireMaterial - Procedural fire with Julia set fractal. Zero-allocation using packed ARGB
 * integers.
 */
public class FractalFireMaterial implements Material {
  private final int iterations;
  private final double chaos;
  private final long startTime;
  private final double scale;
  private final double speed;

  // ===== GC DOSTU: PRE-ALLOCATED TEMP OBJECTS =====
  private final Vector3 tempLightDir = new Vector3();
  private final LightProperties tempLightProps = new LightProperties();

  public FractalFireMaterial(int iterations, double chaos, double scale, double speed) {
    this.iterations = Math.max(5, Math.min(30, iterations));
    this.chaos = Math.max(0.1, Math.min(2.0, chaos));
    this.scale = Math.max(0.5, Math.min(3.0, scale));
    this.speed = Math.max(0.1, Math.min(2.0, speed));
    this.startTime = System.currentTimeMillis();
  }

  @Override
  public int getColorAt(Point3 point, Vector3 normal, Light light, Point3 viewerPos) {
    // 1. Time-based animation
    double time = (System.currentTimeMillis() - startTime) * 0.0005 * speed;

    // 2. Scale the point and offset from center
    double x = (point.x - 0.5) * scale;
    double y = (point.y - 0.5) * scale;
    double z = (point.z - 0.5) * scale * 0.5;

    // 3. Dynamic chaos parameters
    double cx = -0.7 + Math.sin(time * 0.7) * chaos;
    double cy = 0.27 + Math.cos(time * 0.5) * chaos;
    double cz = Math.sin(time * 0.3) * chaos * 0.5;

    // 4. 3D Fractal calculation (Julia Set)
    int i;
    for (i = 0; i < iterations; i++) {
      double xSq = x * x;
      double ySq = y * y;
      double zSq = z * z;

      if ((xSq + ySq + zSq) > 4.0) {
        break;
      }

      double nx = xSq - ySq - zSq + cx;
      double ny = 2.0 * x * y + cy;
      double nz = 2.0 * x * z + cz;
      x = nx;
      y = ny;
      z = nz;
    }

    // 5. Color palette (fire-like tones)
    double ratio = (double) i / iterations;
    double rRatio = 0.3 + ratio * 3.0;
    double gRatio = ratio * 1.5;
    double bRatio = ratio * 0.3;

    int r = (int) (255.0 * (rRatio > 1.0 ? 1.0 : rRatio));
    int g = (int) (255.0 * (gRatio > 1.0 ? 1.0 : gRatio));
    int b = (int) (255.0 * (bRatio > 1.0 ? 1.0 : bRatio));

    // 6. Lighting - ZERO ALLOCATION
    if (light != null) {
      LightProperties.getLightProperties(light, point, tempLightProps);
      tempLightDir.set(tempLightProps.direction);

      double dot = normal.dot(tempLightDir);
      double dotClamp = dot > 0.2 ? dot : 0.2;
      double intensity = tempLightProps.intensity;

      r = (int) (r * dotClamp * intensity);
      g = (int) (g * dotClamp * intensity);
      b = (int) (b * dotClamp * intensity);

      // Apply light color
      int lightR = (tempLightProps.color >> 16) & 0xFF;
      int lightG = (tempLightProps.color >> 8) & 0xFF;
      int lightB = tempLightProps.color & 0xFF;

      r = r * lightR / 255;
      g = g * lightG / 255;
      b = b * lightB / 255;
    }

    // 7. Final color adjustments (more vibrant)
    r += 20;
    g += 10;
    b -= 10;

    r = r > 255 ? 255 : (r < 0 ? 0 : r);
    g = g > 255 ? 255 : (g < 0 ? 0 : g);
    b = b > 255 ? 255 : (b < 0 ? 0 : b);

    return (0xFF000000 | (r << 16) | (g << 8) | b);
  }

  @Override
  public double getReflectivity() {
    return 0.1;
  }

  @Override
  public double getIndexOfRefraction() {
    return 1.0;
  }

  @Override
  public double getTransparency() {
    return 0.0;
  }

  @Override
  public void setObjectTransform(Matrix4 tm) {}

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("FractalFireMaterial fractalfirematerial {\n");
    sb.append("        iterations = ").append(iterations).append(";\n");
    sb.append("        chaos = ").append(chaos).append(";\n");
    sb.append("        scale = ").append(scale).append(";\n");
    sb.append("        speed = ").append(speed).append(";\n");
    sb.append("    }");
    return sb.toString();
  }
}
