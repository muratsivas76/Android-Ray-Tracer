package net.murat.light;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;
import java.io.IOException;
import java.io.InputStream;
import net.murat.lovert.Scene;
import net.murat.math.Point3;
import net.murat.math.Ray;
import net.murat.math.Vector3;
import net.murat.util.ColorUtil;

/**
 * ProjectorLight - casts an image/texture onto surfaces like a slide projector. Android adaptation
 * using Bitmap and int[] arrays for GC-friendly texture sampling.
 *
 * @author Murat
 * @version 1.0
 */
public class ProjectorLight implements Light {

  private static final String TAG = "ProjectorLight";

  // ===== PROPERTIES =====

  private Point3 position;
  private Vector3 direction;
  private int color;
  private double intensity;
  private double angle; // Cone angle in radians
  private double falloff; // Edge softness (0.0 = hard, 1.0 = soft)

  private Bitmap texture;
  private int[] texturePixels;
  private int textureWidth;
  private int textureHeight;

  private String name = "projector";
  private String imagePath = "textures/turkeyFlag.png";

  // Texture scaling
  private double uScale = 0.5;
  private double vScale = 0.5;

  // Attenuation
  private final double constantAttenuation;
  private final double linearAttenuation;
  private final double quadraticAttenuation;

  // Animation delta values
  private double[] incDecIntensity = new double[] {0.0, 0.0};
  private double[] positionDelta = new double[] {0.0, 0.0, 0.0};
  private int[] colorDelta = new int[] {0, 0, 0, 0};

  // ===== GC FRIENDLY: PRE-ALLOCATED TEMP OBJECTS =====
  private final Point3 tempPoint1 = new Point3();
  private final Vector3 tempVector1 = new Vector3();
  private final Vector3 tempVector2 = new Vector3();
  private final Vector3 tempDirection = new Vector3();
  private final Vector3 tempToPoint = new Vector3();
  private final Vector3 tempUp = new Vector3();
  private final Vector3 tempRight = new Vector3();
  private final Ray tempRay = new Ray(new Point3(), new Vector3(), 1.0);

  // ===== CONSTRUCTORS =====

  public ProjectorLight(
      Point3 position,
      Vector3 direction,
      int color,
      double intensity,
      double angleDegrees,
      double falloff,
      Bitmap texture) {
    this(position, direction, color, intensity, angleDegrees, falloff, texture, 1.0, 0.0, 0.0);
  }

  public ProjectorLight(
      Point3 position,
      Vector3 direction,
      int color,
      double intensity,
      double angleDegrees,
      double falloff,
      Bitmap texture,
      double constantAttenuation,
      double linearAttenuation,
      double quadraticAttenuation) {
    this.position = position;
    this.direction = new Vector3();
    this.direction.set(direction);
    this.direction.normalize();
    this.color = color;
    this.intensity = Math.max(0, intensity);
    this.angle = Math.toRadians(angleDegrees);
    this.falloff = Math.max(0, Math.min(1, falloff));
    this.constantAttenuation = Math.max(0, constantAttenuation);
    this.linearAttenuation = Math.max(0, linearAttenuation);
    this.quadraticAttenuation = Math.max(0, quadraticAttenuation);

    this.uScale = 0.5;
    this.vScale = 0.5;

    setTexture(texture); // Use the setter to handle pixel extraction
    logDebugInfo();
  }

  /** Creates a projector from an asset file path. */
  public static ProjectorLight fromAsset(
      Point3 position,
      Vector3 direction,
      int color,
      double intensity,
      double angleDegrees,
      double falloff,
      AssetManager assets,
      String imagePath)
      throws IOException {
    InputStream is = assets.open(imagePath);
    Bitmap img = BitmapFactory.decodeStream(is);
    is.close();
    ProjectorLight light =
        new ProjectorLight(position, direction, color, intensity, angleDegrees, falloff, img);
    light.setImagePath(imagePath);
    return light;
  }

  // ===== TEXTURE MANAGEMENT =====

  public void setTexture(Bitmap bitmap) {
    this.texture = bitmap;
    if (bitmap == null) {
      this.texturePixels = null;
      this.textureWidth = 0;
      this.textureHeight = 0;
      return;
    }
    this.textureWidth = bitmap.getWidth();
    this.textureHeight = bitmap.getHeight();
    this.texturePixels = new int[textureWidth * textureHeight];
    bitmap.getPixels(texturePixels, 0, textureWidth, 0, 0, textureWidth, textureHeight);
  }

  public Bitmap getTexture() {
    return texture;
  }

  private void logDebugInfo() {
    Log.d(TAG, "Texture loaded: " + (texture != null));
    if (texture != null) {
      Log.d(TAG, "Texture size: " + textureWidth + "x" + textureHeight);
      Log.d(TAG, "Angle: " + Math.toDegrees(angle) + " degrees");
    }
  }

  private void updateBasis() {
    tempUp.set(0, 1, 0);
    if (Math.abs(direction.dot(tempUp)) > 0.99) {
      tempUp.set(1, 0, 0);
    }
    tempRight.cross(direction, tempUp).normalize();
    tempUp.cross(tempRight, direction).normalize();
  }

  private double getDistanceTo(Point3 point) {
    return position.distance(point);
  }

  private Vector3 getDirectionTo(Point3 point, Vector3 target) {
    target.set(point).subtract(position);
    if (target.lengthSquared() < Ray.EPSILON * Ray.EPSILON) {
      return target.set(0, 0, 0);
    }
    return target.normalize();
  }

  // ============================================================
  // LIGHT INTERFACE IMPLEMENTATION
  // ============================================================

  @Override
  public Point3 getPosition() {
    return position;
  }

  @Override
  public void setPosition(Point3 position) {
    this.position.set(position);
  }

  @Override
  public int getColor() {
    return color;
  }

  @Override
  public void setColor(int color) {
    this.color = color;
  }

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

  @Override
  public double getAttenuatedIntensity(Point3 point) {
    double baseIntensity = intensity;

    tempToPoint.set(point).subtract(position);
    if (tempToPoint.lengthSquared() < Ray.EPSILON * Ray.EPSILON) {
      return baseIntensity;
    }
    tempToPoint.normalize();

    double cosAngle = tempToPoint.dot(direction);
    double cosHalfAngle = Math.cos(angle);

    if (cosAngle < cosHalfAngle) return 0.0;

    double edgeFactor = 1.0;
    if (falloff > 0 && cosHalfAngle < 1.0) {
      double normalizedAngle = (cosAngle - cosHalfAngle) / (1.0 - cosHalfAngle);
      edgeFactor = Math.pow(normalizedAngle, 1.0 / (1.0 + falloff * 2.0));
    }

    double distance = position.distance(point);
    double attenuation =
        constantAttenuation
            + linearAttenuation * distance
            + quadraticAttenuation * distance * distance;
    if (attenuation > 0) edgeFactor /= attenuation;

    return baseIntensity * edgeFactor;
  }

  @Override
  public double getIntensityAt(Point3 point) {
    return getAttenuatedIntensity(point);
  }

  @Override
  public boolean isVisibleFrom(Point3 point, Scene scene) {
    getDirectionTo(point, tempVector1);
    double distance = getDistanceTo(point);
    tempVector2.set(tempVector1).multiply(Ray.EPSILON * 10.0);
    tempPoint1.set(point).add(tempVector2);
    tempRay.set(tempPoint1, tempVector1, 1.0);
    return !scene.intersects(tempRay, distance - Ray.EPSILON);
  }

  @Override
  public String getName() {
    return this.name;
  }

  @Override
  public void setName(String name) {
    this.name = name;
  }

  @Override
  public void setPositionDelta(double[] delta) {
    this.positionDelta = delta;
  }

  @Override
  public double[] getPositionDelta() {
    return this.positionDelta;
  }

  @Override
  public void setColorDelta(int[] delta) {
    this.colorDelta = delta;
  }

  @Override
  public int[] getColorDelta() {
    return this.colorDelta;
  }

  // ============================================================
  // PROJECTOR SPECIFIC METHODS
  // ============================================================

  public int getColorAt(Point3 point) {
    double rawX = point.x - position.x;
    double rawY = point.y - position.y;
    double rawZ = point.z - position.z;

    tempToPoint.set(rawX, rawY, rawZ);
    double distance = tempToPoint.length();
    if (distance < Ray.EPSILON) return color;
    tempToPoint.normalize();

    double cosAngle = tempToPoint.dot(direction);
    double cosHalfAngle = Math.cos(angle);
    if (cosAngle < cosHalfAngle) return 0x00000000;

    if (texturePixels != null) {
      double u = (tempToPoint.x + 1.0) * 0.5;
      double v = (tempToPoint.z + 1.0) * 0.5;

      u = 0.5 + (u - 0.5) / uScale;
      v = 0.5 + (v - 0.5) / vScale;

      u = Math.max(0, Math.min(1, u));
      v = Math.max(0, Math.min(1, v));

      int x = (int) (u * textureWidth) % textureWidth;
      int y = (int) (v * textureHeight) % textureHeight;
      x = Math.max(0, Math.min(textureWidth - 1, x));
      y = Math.max(0, Math.min(textureHeight - 1, y));

      return texturePixels[y * textureWidth + x]; // Fast array lookup
    }

    return color;
  }

  // ============================================================
  // SCALE SETTERS AND GETTERS
  // ============================================================

  public void setUScale(double uScale) {
    this.uScale = Math.max(0.01, Math.min(1.0, uScale));
  }

  public double getUScale() {
    return uScale;
  }

  public void setVScale(double vScale) {
    this.vScale = Math.max(0.01, Math.min(1.0, vScale));
  }

  public double getVScale() {
    return vScale;
  }

  public void setScale(double uScale, double vScale) {
    setUScale(uScale);
    setVScale(vScale);
  }

  public void setScale(double scale) {
    setUScale(scale);
    setVScale(scale);
  }

  public void setDirection(Vector3 direction) {
    this.direction.set(direction);
    this.direction.normalize();
    updateBasis();
  }

  public Vector3 getDirection() {
    return direction;
  }

  public void setAngle(double angleDegrees) {
    this.angle = Math.toRadians(angleDegrees);
  }

  public double getAngle() {
    return Math.toDegrees(angle);
  }

  public void setFalloff(double falloff) {
    this.falloff = Math.max(0, Math.min(1, falloff));
  }

  public double getFalloff() {
    return falloff;
  }

  public void setImagePath(String imagePath) {
    this.imagePath = imagePath;
  }

  public String getImagePath() {
    return imagePath;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("ProjectorLight ").append(name).append(" {\n");
    sb.append("        name = ").append(name).append(";\n");
    sb.append("        position = ").append(position).append(";\n");
    sb.append("        direction = ").append(direction).append(";\n");
    sb.append("        color = ").append(ColorUtil.toColorString(color)).append(";\n");
    sb.append("        intensity = ").append(intensity).append(";\n");
    sb.append("        angle = ").append(Math.toDegrees(angle)).append(";\n");
    sb.append("        falloff = ").append(falloff).append(";\n");
    sb.append("        uScale = ").append(uScale).append(";\n");
    sb.append("        vScale = ").append(vScale).append(";\n");
    sb.append("        texture = ").append(imagePath).append(";\n");
    sb.append("        constantAttenuation = ").append(constantAttenuation).append(";\n");
    sb.append("        linearAttenuation = ").append(linearAttenuation).append(";\n");
    sb.append("        quadraticAttenuation = ").append(quadraticAttenuation).append(";\n");
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
