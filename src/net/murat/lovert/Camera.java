package net.murat.lovert;

import net.murat.math.Point3;
import net.murat.math.Vector3;

/**
 * Represents the 3D camera viewpoint parameters for the ray tracer. Supports both perspective and
 * orthographic projections with serialization. Fully compatible with single-threaded
 * high-performance loops.
 */
public class Camera extends Object implements java.io.Serializable {

  private Point3 cameraPosition = new Point3(0, 1, 5);
  private Point3 lookAt = new Point3(0, 0, -5);

  private Vector3 upVector = new Vector3(0, 1, 0);

  private boolean orthographic = false;

  private double fov = 60.0;
  private double orthographicScale = 1.0;

  private int maxRecursionDepth = 2;

  private boolean isReflective = true;
  private boolean isRefractive = true;
  private boolean shadowsEnabled = true;
  private boolean selfShadowsEnabled = false;
  private boolean useBackgroundColor = false;

  private int ssGrid = 1;

  private Point3[] animationPoints = new Point3[] {new Point3(0, 0, 0), new Point3(0, 0, 0)};

  public Camera() {
    super();
  }

  /**
   * PERFORMANCE METHOD: Copies all properties from another camera instance into this one without
   * creating any new objects on the heap. Perfect for in-place updates.
   *
   * @param other The source camera to copy from.
   * @return This camera instance for method chaining.
   */
  public Camera set(Camera other) {
    this.cameraPosition.set(other.cameraPosition);
    this.lookAt.set(other.lookAt);
    this.upVector.set(other.upVector);

    this.orthographic = other.orthographic;
    this.fov = other.fov;
    this.orthographicScale = other.orthographicScale;
    this.maxRecursionDepth = other.maxRecursionDepth;

    this.isReflective = other.isReflective;
    this.isRefractive = other.isRefractive;
    this.shadowsEnabled = other.shadowsEnabled;
    this.selfShadowsEnabled = other.selfShadowsEnabled;
    this.useBackgroundColor = other.useBackgroundColor;

    this.animationPoints[0].set(other.animationPoints[0]);
    this.animationPoints[1].set(other.animationPoints[1]);

    return this;
  }

  public Camera copy() {
    Camera copy = new Camera();

    // Using explicit mutable set() allocations to guarantee deep clone behavior safely
    copy.cameraPosition =
        new Point3(this.cameraPosition.x, this.cameraPosition.y, this.cameraPosition.z);
    copy.lookAt = new Point3(this.lookAt.x, this.lookAt.y, this.lookAt.z);
    copy.upVector = new Vector3(this.upVector.x, this.upVector.y, this.upVector.z);

    copy.orthographic = this.orthographic;
    copy.fov = this.fov;
    copy.orthographicScale = this.orthographicScale;
    copy.maxRecursionDepth = this.maxRecursionDepth;

    copy.isReflective = this.isReflective;
    copy.isRefractive = this.isRefractive;
    copy.shadowsEnabled = this.shadowsEnabled;
    copy.selfShadowsEnabled = this.selfShadowsEnabled;
    copy.useBackgroundColor = this.useBackgroundColor;

    copy.animationPoints =
        new Point3[] {
          new Point3(
              this.animationPoints[0].x, this.animationPoints[0].y, this.animationPoints[0].z),
          new Point3(
              this.animationPoints[1].x, this.animationPoints[1].y, this.animationPoints[1].z)
        };

    return copy;
  }

  // Getters
  public Point3 getCameraPosition() {
    return cameraPosition;
  }

  public Point3 getLookAt() {
    return lookAt;
  }

  public Vector3 getUpVector() {
    return upVector;
  }

  public double getFov() {
    return fov;
  }

  public int getMaxRecursionDepth() {
    return maxRecursionDepth;
  }

  public double getOrthographicScale() {
    return this.orthographicScale;
  }

  public boolean isOrthographic() {
    return this.orthographic;
  }

  public boolean isReflective() {
    return this.isReflective;
  }

  public boolean isRefractive() {
    return this.isRefractive;
  }

  public boolean isShadowsEnabled() {
    return shadowsEnabled;
  }

  public boolean isSelfShadowsEnabled() {
    return selfShadowsEnabled;
  }

  public boolean isUseBackgroundColor() {
    return useBackgroundColor;
  }

  public Point3[] getAnimationPoints() {
    return this.animationPoints;
  }

  public int getSupersamplingGrid() {
    return this.ssGrid;
  }

  // Setters
  public void setCameraPosition(Point3 pv) {
    this.cameraPosition = pv;
  }

  public void setLookAt(Point3 pv) {
    this.lookAt = pv;
  }

  public void setUpVector(Vector3 vv) {
    this.upVector = vv;
  }

  public void setFov(double dbl) {
    this.fov = dbl;
  }

  public void setMaxRecursionDepth(int mr) {
    this.maxRecursionDepth = mr;
  }

  public void setOrthographicScale(double scl) {
    this.orthographicScale = scl;
  }

  public void setOrthographic(boolean bb) {
    this.orthographic = bb;
  }

  public void setReflective(boolean rb) {
    this.isReflective = rb;
  }

  public void setRefractive(boolean rb) {
    this.isRefractive = rb;
  }

  public void setShadowsEnabled(boolean enabled) {
    this.shadowsEnabled = enabled;
  }

  public void setSelfShadowsEnabled(boolean enabled) {
    this.selfShadowsEnabled = enabled;
  }

  public void setUseBackgroundColor(boolean enabled) {
    this.useBackgroundColor = enabled;
  }

  public void setAnimationPoints(Point3[] pxyz) {
    this.animationPoints = pxyz;
  }

  public void setSupersamplingGrid(int grid) {
    this.ssGrid = Math.max(1, grid);
  }

  @Override
  public String toString() {
    // Streamlined with StringBuilder to preserve optimal string creation speed uyarısız
    StringBuilder sb = new StringBuilder();
    sb.append("Camera mainCamera {\n");
    sb.append("    position = ").append(cameraPosition).append(";\n");
    sb.append("    lookAt = ").append(lookAt).append(";\n");
    sb.append("    upVector = ").append(upVector).append(";\n");
    sb.append("    fov = ").append(fov).append(";\n");
    sb.append("    orthographic = ").append(orthographic).append(";\n");
    sb.append("    maxRecursionDepth = ").append(maxRecursionDepth).append(";\n");
    sb.append("    ssGridCount = ").append(ssGrid).append(";\n");
    sb.append("    firstAnimationPoint = ").append(animationPoints[0]).append(";\n");
    sb.append("    secondAnimationPoint = ").append(animationPoints[1]).append(";\n");
    sb.append("    reflective = ").append(isReflective).append(";\n");
    sb.append("    refractive = ").append(isRefractive).append(";\n");
    sb.append("    shadowsEnabled = ").append(shadowsEnabled).append(";\n");
    sb.append("    selfShadowsEnabled = ").append(selfShadowsEnabled).append(";\n");
    sb.append("    useBackgroundColor = ").append(useBackgroundColor).append(";\n");
    sb.append("}\n");
    return sb.toString();
  }
}
