package net.murat.shape;

import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import net.murat.material.Material;
import net.murat.math.*;
import net.murat.util.ColorUtil;

/**
 * A 2D quad in 3D space for displaying transparent images (e.g., emojis). Android adaptation: Uses
 * Bitmap and int[] for fast, GC-free texture sampling.
 */
public class EmojiBillboard implements EMShape {

  // =========================================================================
  // CONSTANTS
  // =========================================================================

  private static final Vector3 LOCAL_NORMAL = new Vector3(0, 0, 1);

  private static final List<IntersectionInterval> EMPTY_INTERVAL_LIST =
      java.util.Collections.emptyList();

  // =========================================================================
  // BILLBOARD PROPERTIES
  // =========================================================================

  private final double width;
  private final double height;
  private final boolean isRectangle;

  // Android Bitmap replacing BufferedImage
  private Bitmap texture;

  // Cached pixel data for fast sampling during render (GC-free)
  private int[] texturePixels;
  private int textureWidth;
  private int textureHeight;

  private String imagePath = "textures/turkeyFlag.png";

  private int shadowColor = 0xFF000000;
  private double shadowBias = 0.001;
  private Matrix4[] animTransforms = new Matrix4[] {new Matrix4(), new Matrix4()};

  private String otherAnimationInfo = "veofr,0e0fr,veofr";
  private boolean isVisibleSpecial = false;
  private boolean isVisible = true;
  private boolean isShadowEnable = true;
  private boolean isShadowOnly = false;
  private boolean isReflective = true;
  private boolean isRefractive = true;

  private Matrix4 transform = Matrix4.identity();
  private Matrix4 inverseTransform;
  private Matrix4 inverseTransposeTransformForNormal;
  private Material material;

  private String name = "emojibillboard";

  // Mesh cache
  private List<Triangle> cachedTriangles;

  // =========================================================================
  // GC-FREE SCRATCH POOL
  // =========================================================================

  private final Point3 tempLocalHit = new Point3();
  private final Point3 tempWorldHit = new Point3();
  private final Vector3 tempNormal = new Vector3();
  private final Ray tempLocalRay = new Ray(new Point3(), new Vector3(), 1.0);
  private final Point3 tempOrigin = new Point3();
  private final Vector3 tempDir = new Vector3();
  private final Vector3 normalVec = new Vector3(0, 0, 1);
  private final Intersection singleHit = new Intersection();
  private final IntersectionInterval singleInterval =
      new IntersectionInterval(0, 0, singleHit, singleHit);
  private final Point3 localPoint = new Point3();

  // =========================================================================
  // CONSTRUCTORS
  // =========================================================================

  public EmojiBillboard(
      double width, double height, boolean isRectangle, boolean isVisible, Bitmap texture) {
    this.width = width;
    this.height = height;
    this.isRectangle = isRectangle;
    this.isVisible = isVisible;
    setTexture(texture);
    updateTransforms();
  }

  public EmojiBillboard(double width, double height) {
    this(width, height, true, true, null);
  }

  public EmojiBillboard(double size) {
    this(size, size);
  }

  public EmojiBillboard() {
    this(1.0, 1.0);
  }

  // =========================================================================
  // TEXTURE MANAGEMENT
  // =========================================================================

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

  /** Loads a texture from Android assets. */
  public void loadTextureFromAssets(AssetManager assets, String path) {
    this.imagePath = path;
    InputStream is = null;
    try {
      is = assets.open(path);
      Bitmap decoded = BitmapFactory.decodeStream(is);
      setTexture(decoded);
    } catch (IOException e) {
      this.texture = null;
      this.texturePixels = null;
      this.textureWidth = 0;
      this.textureHeight = 0;
    } finally {
      if (is != null) {
        try {
          is.close();
        } catch (IOException ignored) {
        }
      }
    }
  }

  /** Fast texture lookup by UV in [0,1]. GC-free. */
  public int sampleTexture(double u, double v) {
    if (texturePixels == null) return 0;
    int x = (int) (u * (textureWidth - 1));
    int y = (int) (v * (textureHeight - 1));
    if (x < 0) x = 0;
    else if (x >= textureWidth) x = textureWidth - 1;
    if (y < 0) y = 0;
    else if (y >= textureHeight) y = textureHeight - 1;
    return texturePixels[y * textureWidth + x];
  }

  // =========================================================================
  // EMShape INTERFACE METHODS
  // =========================================================================

  @Override
  public void setTransform(Matrix4 transform) {
    this.transform = new Matrix4(transform);
    updateTransforms();
    cachedTriangles = null;
  }

  @Override
  public Matrix4 getTransform() {
    return this.transform;
  }

  @Override
  public Matrix4 getInverseTransform() {
    return this.inverseTransform;
  }

  private void updateTransforms() {
    this.inverseTransform = this.transform.inverse();
    this.inverseTransposeTransformForNormal = this.transform.inverse().transpose();
  }

  @Override
  public void setMaterial(Material material) {
    this.material = material;
  }

  @Override
  public Material getMaterial() {
    return this.material;
  }

  // =========================================================================
  // INTERSECTION METHODS
  // =========================================================================

  @Override
  public boolean intersect(Ray ray, Intersection hit) {
    if (inverseTransform == null) {
      return false;
    }

    inverseTransform.transformPoint(ray.getOrigin(), tempOrigin);
    inverseTransform.transformDirection(ray.getDirection(), tempDir);
    tempDir.normalize();
    tempLocalRay.set(tempOrigin, tempDir, 1.0);

    Vector3 dir = tempLocalRay.getDirection();
    if (Math.abs(dir.z) < Ray.EPSILON) {
      return false;
    }

    double t = -tempLocalRay.getOrigin().z / dir.z;
    if (t < Ray.EPSILON) {
      return false;
    }

    tempLocalRay.pointAtParameter(t, tempLocalHit);
    double x = tempLocalHit.x;
    double y = tempLocalHit.y;

    boolean inside;
    if (isRectangle) {
      double halfWidth = width / 2.0;
      double halfHeight = height / 2.0;
      inside = (Math.abs(x) <= halfWidth && Math.abs(y) <= halfHeight);
    } else {
      double radiusX = width / 2.0;
      double radiusY = height / 2.0;
      double nx = x / radiusX;
      double ny = y / radiusY;
      inside = (nx * nx + ny * ny <= 1.0);
    }

    if (!inside) {
      return false;
    }

    ray.pointAtParameter(t, tempWorldHit);
    Vector3 normal = getNormalAt(tempWorldHit);
    hit.set(tempWorldHit, normal, t, this);

    return true;
  }

  @Deprecated
  public double intersect(Ray ray) {
    if (inverseTransform == null) {
      return Double.POSITIVE_INFINITY;
    }

    inverseTransform.transformPoint(ray.getOrigin(), tempOrigin);
    inverseTransform.transformDirection(ray.getDirection(), tempDir);
    tempDir.normalize();
    tempLocalRay.set(tempOrigin, tempDir, 1.0);

    Vector3 dir = tempLocalRay.getDirection();
    if (Math.abs(dir.z) < Ray.EPSILON) {
      return Double.POSITIVE_INFINITY;
    }

    double t = -tempLocalRay.getOrigin().z / dir.z;
    if (t < Ray.EPSILON) {
      return Double.POSITIVE_INFINITY;
    }

    tempLocalRay.pointAtParameter(t, tempLocalHit);
    double x = tempLocalHit.x;
    double y = tempLocalHit.y;

    boolean inside;
    if (isRectangle) {
      double halfWidth = width / 2.0;
      double halfHeight = height / 2.0;
      inside = (Math.abs(x) <= halfWidth && Math.abs(y) <= halfHeight);
    } else {
      double radiusX = width / 2.0;
      double radiusY = height / 2.0;
      double nx = x / radiusX;
      double ny = y / radiusY;
      inside = (nx * nx + ny * ny <= 1.0);
    }

    return inside ? t : Double.POSITIVE_INFINITY;
  }

  @Override
  public List<IntersectionInterval> intersectAll(Ray ray) {
    if (intersect(ray, singleHit)) {
      double t = singleHit.getT();
      singleInterval.setEntry(t, singleHit);
      singleInterval.setExit(t, singleHit);
      return java.util.Collections.singletonList(singleInterval);
    }
    return EMPTY_INTERVAL_LIST;
  }

  // =========================================================================
  // NORMAL CALCULATION
  // =========================================================================

  @Override
  public Vector3 getNormalAt(Point3 worldPoint) {
    if (inverseTransposeTransformForNormal == null) {
      return LOCAL_NORMAL;
    }

    tempNormal.set(0, 0, 1);
    inverseTransposeTransformForNormal.transformDirection(tempNormal, tempNormal);
    tempNormal.normalize();

    return tempNormal;
  }

  // =========================================================================
  // GETTERS/SETTERS
  // =========================================================================

  public double getWidth() {
    return width;
  }

  public double getHeight() {
    return height;
  }

  public void setImagePath(String npath) {
    this.imagePath = npath;
  }

  public String getImagePath() {
    return this.imagePath;
  }

  @Override
  public void setAnimationTransforms(Matrix4[] atm) {
    this.animTransforms = atm;
  }

  @Override
  public Matrix4[] getAnimationTransforms() {
    return this.animTransforms;
  }

  @Override
  public int getShadowColor() {
    return this.shadowColor;
  }

  @Override
  public void setShadowColor(int color) {
    this.shadowColor = color;
  }

  @Override
  public double getShadowBias() {
    return this.shadowBias;
  }

  @Override
  public void setShadowBias(double bias) {
    this.shadowBias = bias;
  }

  @Override
  public String getNameInfo() {
    return ("EmojiBillboard: " + getTransform().toString());
  }

  @Override
  public String getOtherAnimationInfo() {
    return this.otherAnimationInfo;
  }

  @Override
  public void setOtherAnimationInfo(String str) {
    this.otherAnimationInfo = str;
  }

  @Override
  public boolean isVisible() {
    return this.isVisible;
  }

  @Override
  public boolean isShadowEnable() {
    return this.isShadowEnable;
  }

  @Override
  public boolean isShadowOnly() {
    return this.isShadowOnly;
  }

  @Override
  public boolean isReflective() {
    return this.isReflective;
  }

  @Override
  public boolean isRefractive() {
    return this.isRefractive;
  }

  @Override
  public void setVisible(boolean visible) {
    this.isVisible = visible;
  }

  @Override
  public void setShadowEnable(boolean enable) {
    this.isShadowEnable = enable;
  }

  @Override
  public void setShadowOnly(boolean only) {
    this.isShadowOnly = only;
  }

  @Override
  public void setReflective(boolean rfl) {
    this.isReflective = rfl;
  }

  @Override
  public void setRefractive(boolean rfr) {
    this.isRefractive = rfr;
  }

  @Override
  public boolean isVisibleSpecial() {
    return this.isVisibleSpecial;
  }

  @Override
  public void setVisibleSpecial(boolean visible) {
    this.isVisibleSpecial = visible;
  }

  @Override
  public String getName() {
    return this.name;
  }

  @Override
  public void setName(String name) {
    this.name = name;
  }

  // =========================================================================
  // MESH METHODS
  // =========================================================================

  @Override
  public List<Triangle> generateMesh() {
    if (cachedTriangles != null) {
      return cachedTriangles;
    }

    List<Triangle> triangles = new ArrayList<Triangle>();
    double hw = width / 2.0;
    double hh = height / 2.0;

    Point3 v1 = new Point3(-hw, -hh, 0);
    Point3 v2 = new Point3(hw, -hh, 0);
    Point3 v3 = new Point3(hw, hh, 0);
    Point3 v4 = new Point3(-hw, hh, 0);

    triangles.add(new Triangle(v1, v2, v3));
    triangles.add(new Triangle(v1, v3, v4));

    cachedTriangles = triangles;
    return triangles;
  }

  @Override
  public List<Triangle> getTriangles() {
    return cachedTriangles;
  }

  // =========================================================================
  // TO STRING / OBJ EXPORT
  // =========================================================================

  @Override
  public String toObjString() {
    StringBuilder sb = new StringBuilder();
    sb.append("# Murat Ray Tracer Mesh File\n");
    sb.append("# Shape: EmojiBillboard\n");
    sb.append("# Width: ").append(width).append("\n");
    sb.append("# Height: ").append(height).append("\n");
    sb.append("# ImagePath: ").append(imagePath).append("\n");
    sb.append("# Generated: ").append(new java.util.Date()).append("\n");

    if (material != null) {
      sb.append("# Material: ").append(material.getClass().getSimpleName()).append("\n");
    }
    sb.append("\n");

    List<Triangle> triangles = generateMesh();
    if (triangles == null || triangles.isEmpty()) {
      sb.append("# No triangles generated\n");
      return sb.toString();
    }

    Matrix4 trans = (transform != null) ? transform : new Matrix4();

    List<Point3> allVerts = new ArrayList<Point3>();
    for (Triangle tri : triangles) {
      allVerts.add(trans.transformPoint(tri.getV0()));
      allVerts.add(trans.transformPoint(tri.getV1()));
      allVerts.add(trans.transformPoint(tri.getV2()));
    }

    List<Point3> uniqueVerts = new ArrayList<Point3>();
    int[] indexMap = new int[allVerts.size()];
    final double TOLERANCE = 0.0001;

    for (int i = 0; i < allVerts.size(); i++) {
      Point3 v = allVerts.get(i);
      int found = -1;
      for (int j = 0; j < uniqueVerts.size(); j++) {
        Point3 u = uniqueVerts.get(j);
        if (Math.abs(u.x - v.x) < TOLERANCE
            && Math.abs(u.y - v.y) < TOLERANCE
            && Math.abs(u.z - v.z) < TOLERANCE) {
          found = j;
          break;
        }
      }
      if (found == -1) {
        found = uniqueVerts.size();
        uniqueVerts.add(new Point3(v.x, v.y, v.z));
      }
      indexMap[i] = found;
    }

    sb.append("# ===== VERTICES =====\n");
    for (Point3 v : uniqueVerts) {
      sb.append(String.format(java.util.Locale.US, "v %.6f %.6f %.6f\n", v.x, v.y, v.z));
    }
    sb.append("\n");

    sb.append("# ===== TRIANGLES =====\n");
    for (int i = 0; i < triangles.size(); i++) {
      int base = i * 3;
      sb.append(
          String.format(
              java.util.Locale.US,
              "f %d %d %d\n",
              indexMap[base] + 1,
              indexMap[base + 1] + 1,
              indexMap[base + 2] + 1));
    }
    sb.append("\n");

    if (material != null) {
      sb.append("# ===== MATERIAL =====\n");
      sb.append("material = ").append(material.toString()).append("\n");
    }

    sb.append("\n# ===== END =====\n");
    return sb.toString();
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("EmojiBillboard " + name + " {\n");
    sb.append("    name = " + name + ";\n");
    sb.append("    imagePath = " + getImagePath() + ";\n");
    sb.append("    width = " + width + ";\n");
    sb.append("    height = " + height + ";\n");
    sb.append("    isRectangle = " + isRectangle + ";\n");
    sb.append("\n");
    sb.append("    " + getTransform().toString() + "\n");
    sb.append("\n");
    sb.append("    firstAnim_" + animTransforms[0].toString() + "\n");
    sb.append("    secondAnim_" + animTransforms[1].toString() + "\n");
    sb.append("    otherAnimationInfo = " + otherAnimationInfo + ";\n");
    sb.append("\n");
    sb.append("    shadowColor = " + ColorUtil.toColorString(shadowColor) + ";\n");
    sb.append("    shadowBias = " + shadowBias + ";\n");
    sb.append("\n");
    sb.append("    isVisibleSpecial = " + isVisibleSpecial + ";\n");
    sb.append("    isVisible = " + isVisible + ";\n");
    sb.append("    isShadowEnable = " + isShadowEnable + ";\n");
    sb.append("    isShadowOnly = " + isShadowOnly + ";\n");
    sb.append("    isReflective = " + isReflective + ";\n");
    sb.append("    isRefractive = " + isRefractive + ";\n");
    sb.append("\n");
    sb.append("    material = " + (material != null ? material.toString() : "null") + ";\n}");
    return sb.toString();
  }
}
