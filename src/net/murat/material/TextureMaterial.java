package net.murat.material;

import android.graphics.Bitmap;
import net.murat.light.Light;
import net.murat.light.LightProperties;
import net.murat.light.MuratAmbientLight;
import net.murat.math.Matrix4;
import net.murat.math.Point3;
import net.murat.math.Vector3;

/**
 * TextureMaterial - Textures a surface with a Bitmap. Android adaptation: Uses Bitmap + int[] for
 * fast, GC-free sampling.
 *
 * @author Murat
 * @version 2.3
 */
public class TextureMaterial implements Material {

  // =========================================================================
  // PROPERTIES
  // =========================================================================

  private final Bitmap texture;
  private final int[] texturePixels;
  private final int width;
  private final int height;
  private final boolean isTile;
  private final boolean sphericalMapping;

  private double transparency = 1.0;
  private Matrix4 objectTransform = Matrix4.identity();
  private Matrix4 inverseTransform = Matrix4.identity();

  // Ellipsoid radii for proper UV mapping (set via transform)
  private double radiusX = 1.0;
  private double radiusY = 1.0;
  private double radiusZ = 1.0;

  // UV offset and scale for fine-tuning
  private double uOffset = 0.0;
  private double vOffset = 0.0;
  private double uScale = 1.0;
  private double vScale = 1.0;

  // =========================================================================
  // GC-FRIENDLY SCRATCH POOL
  // =========================================================================

  private final Point3 tempLocalPoint = new Point3();
  private final Vector3 tempNormal = new Vector3();
  private final Vector3 tempLightDir = new Vector3();
  private final LightProperties tempLightProps = new LightProperties();

  private String imagePath = "textures/turkeyFlag.png";

  // =========================================================================
  // CONSTRUCTORS
  // =========================================================================

  public TextureMaterial(Bitmap texture) {
    this(texture, true, false);
  }

  public TextureMaterial(Bitmap texture, boolean isTile) {
    this(texture, isTile, false);
  }

  public TextureMaterial(Bitmap texture, boolean isTile, boolean sphericalMapping) {
    if (texture == null) {
      throw new IllegalArgumentException("Texture cannot be null");
    }
    this.texture = texture;
    this.width = texture.getWidth();
    this.height = texture.getHeight();
    this.isTile = isTile;
    this.sphericalMapping = sphericalMapping;

    // Cache pixel data for fast, GC-free sampling
    this.texturePixels = new int[width * height];
    texture.getPixels(this.texturePixels, 0, width, 0, 0, width, height);
  }

  // =========================================================================
  // MATERIAL INTERFACE
  // =========================================================================

  @Override
  public void setObjectTransform(Matrix4 tm) {
    if (tm != null) {
      this.objectTransform = tm;
      this.inverseTransform = tm.inverse();

      this.radiusX = tm.getScaleX();
      this.radiusY = tm.getScaleY();
      this.radiusZ = tm.getScaleZ();

      if (radiusX < 0.0001) radiusX = 1.0;
      if (radiusY < 0.0001) radiusY = 1.0;
      if (radiusZ < 0.0001) radiusZ = 1.0;
    } else {
      this.objectTransform = Matrix4.identity();
      this.inverseTransform = Matrix4.identity();
      this.radiusX = 1.0;
      this.radiusY = 1.0;
      this.radiusZ = 1.0;
    }
  }

  public void setUVOffsetScale(double uOffset, double vOffset, double uScale, double vScale) {
    this.uOffset = uOffset;
    this.vOffset = vOffset;
    this.uScale = uScale;
    this.vScale = vScale;
  }

  @Override
  public int getColorAt(Point3 point, Vector3 normal, Light light, Point3 viewerPos) {
    if (texturePixels == null) {
      transparency = 1.0;
      return 0x00000000;
    }

    double u, v;

    // STEP 1: Compute raw (u, v)
    if (sphericalMapping) {
      inverseTransform.transformPoint(point, tempLocalPoint);

      double nx = tempLocalPoint.x / radiusX;
      double ny = tempLocalPoint.y / radiusY;
      double nz = tempLocalPoint.z / radiusZ;

      double len = Math.sqrt(nx * nx + ny * ny + nz * nz);

      if (len > 0.0001) {
        nx /= len;
        ny /= len;
        nz /= len;
        u = 0.5 + Math.atan2(nz, nx) / (2.0 * Math.PI);
        v = 0.5 - Math.asin(ny) / Math.PI;
      } else {
        u = 0.5;
        v = 0.5;
      }
    } else {
      inverseTransform.transformPoint(point, tempLocalPoint);

      if (isTile) {
        u = tempLocalPoint.x;
        v = 1.0 - tempLocalPoint.y;
      } else {
        u = (tempLocalPoint.x + 1.0) / 2.0;
        v = 1.0 - (tempLocalPoint.y / radiusY + 1.0) / 2.0;
      }
    }

    // STEP 2: Apply scale/offset
    u = u * uScale + uOffset;
    v = v * vScale + vOffset;

    // STEP 3: Resolve final UV
    if (isTile) {
      u = u - Math.floor(u);
      v = v - Math.floor(v);
    } else {
      u = clamp(u);
      v = clamp(v);
    }

    // STEP 4: Sample the texture
    int x = (int) (u * (width - 1));
    int y = (int) (v * (height - 1));

    x = Math.max(0, Math.min(width - 1, x));
    y = Math.max(0, Math.min(height - 1, y));

    // Fast array lookup instead of texture.getRGB()
    int argb = texturePixels[y * width + x];
    int alpha = (argb >> 24) & 0xFF;
    int baseR = (argb >> 16) & 0xFF;
    int baseG = (argb >> 8) & 0xFF;
    int baseB = argb & 0xFF;

    if (alpha < 5) {
      transparency = 1.0;
      return 0x00000000;
    }

    // STEP 5: Lighting
    LightProperties.getLightProperties(light, point, tempLightProps);
    tempLightDir.set(tempLightProps.direction);

    int lightColor = tempLightProps.color;
    int lightR = (lightColor >> 16) & 0xFF;
    int lightG = (lightColor >> 8) & 0xFF;
    int lightB = lightColor & 0xFF;
    double intensity = tempLightProps.intensity;

    int r, g, b;
    if (light instanceof MuratAmbientLight) {
      r = (int) (baseR * intensity * lightR / 255.0);
      g = (int) (baseG * intensity * lightG / 255.0);
      b = (int) (baseB * intensity * lightB / 255.0);
    } else {
      double NdotL = Math.max(0.0, normal.dot(tempLightDir));
      double factor = NdotL * intensity;
      r = (int) (baseR * factor * lightR / 255.0);
      g = (int) (baseG * factor * lightG / 255.0);
      b = (int) (baseB * factor * lightB / 255.0);
    }

    r = clamp(r);
    g = clamp(g);
    b = clamp(b);

    transparency = 0.0;

    return (0xFF000000 | (r << 16) | (g << 8) | b);
  }

  // =========================================================================
  // UTILITY METHODS
  // =========================================================================

  private double clamp(double value) {
    return Math.max(0.0, Math.min(1.0, value));
  }

  private int clamp(int value) {
    return Math.max(0, Math.min(255, value));
  }

  // =========================================================================
  // GETTERS
  // =========================================================================

  @Override
  public double getReflectivity() {
    return 0.0;
  }

  @Override
  public double getIndexOfRefraction() {
    return 1.0;
  }

  @Override
  public double getTransparency() {
    return transparency;
  }

  public boolean isTile() {
    return isTile;
  }

  public boolean isSphericalMapping() {
    return sphericalMapping;
  }

  public double getUOffset() {
    return uOffset;
  }

  public double getVOffset() {
    return vOffset;
  }

  public double getUScale() {
    return uScale;
  }

  public double getVScale() {
    return vScale;
  }

  public void setImagePath(String npath) {
    this.imagePath = npath;
  }

  public String getImagePath() {
    return this.imagePath;
  }

  public Bitmap getTexture() {
    return texture;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("TextureMaterial texturematerial {\n");
    sb.append("        imagePath = ").append(getImagePath()).append(";\n");
    sb.append("        isTile = ").append(isTile).append(";\n");
    sb.append("        sphericalMapping = ").append(sphericalMapping).append(";\n");
    sb.append("        uOffset = ").append(uOffset).append(";\n");
    sb.append("        vOffset = ").append(vOffset).append(";\n");
    sb.append("        uScale = ").append(uScale).append(";\n");
    sb.append("        vScale = ").append(vScale).append(";\n");
    sb.append("    }");
    return sb.toString();
  }
}
