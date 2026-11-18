package net.murat.material;

import android.graphics.Bitmap;
import net.murat.light.Light;
import net.murat.light.LightProperties;
import net.murat.light.MuratAmbientLight;
import net.murat.math.*;

/**
 * ImageTextureMaterial - Applies Bitmap as texture with Phong lighting. Android adaptation: Uses
 * Bitmap + int[] for fast, GC-free texture sampling.
 */
public class ImageTextureMaterial implements Material {
  private final Bitmap image;

  // Cached pixel array for fast sampling
  private final int[] imagePixels;
  private final int imgWidth;
  private final int imgHeight;

  private final double uScale;
  private final double vScale;
  private final double uOffset;
  private final double vOffset;

  private final int specularColorPacked;
  private final double shininess;
  private final double ambientCoefficient;
  private final double diffuseCoefficient;
  private final double specularCoefficient;
  private final double reflectivity;
  private final double ior;
  private double transparency;

  private Matrix4 objectInverseTransform;
  private Matrix4 inverseTransposeTransform;

  private final int specR, specG, specB;

  // ===== GC FRIENDLY: PRE-ALLOCATED TEMP OBJECTS =====
  private final Point3 tempLocalPoint = new Point3();
  private final Vector3 tempLocalNormal = new Vector3();
  private final Vector3 tempTransformedNormal = new Vector3();
  private final Vector3 tempViewDir = new Vector3();
  private final Vector3 tempLightDir = new Vector3();
  private final Vector3 tempReflectDir = new Vector3();
  private final Point3 tempViewerPoint = new Point3();
  private final LightProperties tempLightProps = new LightProperties();

  private String imagePath = "textures/turkeyFlag.png";

  public ImageTextureMaterial(
      Bitmap image,
      double uScale,
      double vScale,
      double uOffset,
      double vOffset,
      double ambientCoefficient,
      double diffuseCoefficient,
      double specularCoefficient,
      double shininess,
      double reflectivity,
      double ior,
      Matrix4 objectInverseTransform) {

    this.image = image;

    if (image != null) {
      this.imgWidth = image.getWidth();
      this.imgHeight = image.getHeight();
      this.imagePixels = new int[imgWidth * imgHeight];
      image.getPixels(this.imagePixels, 0, imgWidth, 0, 0, imgWidth, imgHeight);
    } else {
      this.imgWidth = 0;
      this.imgHeight = 0;
      this.imagePixels = null;
    }

    this.uScale = uScale;
    this.vScale = vScale;
    this.uOffset = uOffset;
    this.vOffset = vOffset;
    this.ambientCoefficient = ambientCoefficient;
    this.diffuseCoefficient = diffuseCoefficient;
    this.specularCoefficient = specularCoefficient;
    this.shininess = shininess;
    this.reflectivity = clamp01(reflectivity);
    this.ior = Math.max(1.0, ior);
    this.objectInverseTransform =
        objectInverseTransform != null ? objectInverseTransform : new Matrix4();
    this.inverseTransposeTransform = this.objectInverseTransform.inverseTransposeForNormal();
    this.specularColorPacked = 0xFFFFFFFF;
    this.transparency = 0.0;

    this.specR = 255;
    this.specG = 255;
    this.specB = 255;
  }

  public ImageTextureMaterial(
      Bitmap image,
      double uScale,
      double vScale,
      double uOffset,
      double vOffset,
      double ambientCoefficient,
      double diffuseCoefficient,
      double specularCoefficient,
      double shininess,
      Matrix4 objectInverseTransform) {
    this(
        image,
        uScale,
        vScale,
        uOffset,
        vOffset,
        ambientCoefficient,
        diffuseCoefficient,
        specularCoefficient,
        shininess,
        0.0,
        1.0,
        objectInverseTransform);
  }

  public ImageTextureMaterial(Bitmap image, Matrix4 objectInverseTransform) {
    this(image, 1.0, 1.0, 0.0, 0.0, 0.1, 0.7, 0.7, 32.0, objectInverseTransform);
  }

  public ImageTextureMaterial(Bitmap image, double scale, Matrix4 objectInverseTransform) {
    this(image, scale, scale, 0.0, 0.0, 0.1, 0.7, 0.7, 32.0, objectInverseTransform);
  }

  @Override
  public void setObjectTransform(Matrix4 tm) {
    if (tm == null) tm = new Matrix4();
    this.objectInverseTransform = tm;
    this.inverseTransposeTransform = tm.inverseTransposeForNormal();
  }

  @Override
  public int getColorAt(Point3 worldPoint, Vector3 worldNormal, Light light, Point3 viewerPos) {
    if (objectInverseTransform == null || imagePixels == null) {
      return 0x00000000;
    }

    // 1. Transform to local space
    objectInverseTransform.transformPoint(worldPoint, tempLocalPoint);

    // 2. Transform normal
    inverseTransposeTransform.transformDirection(worldNormal, tempTransformedNormal);
    tempLocalNormal.set(tempTransformedNormal);
    tempLocalNormal.normalize();

    // 3. Get texture color
    int textureColor = getTextureColor(tempLocalPoint, tempLocalNormal);

    // 4. Alpha check - transparent
    int alpha = (textureColor >> 24) & 0xFF;
    if (alpha < 6) {
      transparency = 1.0;
      return 0x00000000;
    } else {
      transparency = 0.0;
    }

    // 5. Light properties
    LightProperties.getLightProperties(light, worldPoint, tempLightProps);
    tempLightDir.set(tempLightProps.direction);

    // 6. Ambient check
    if (light instanceof MuratAmbientLight) {
      int r = (int) (((textureColor >> 16) & 0xFF) * ambientCoefficient * tempLightProps.intensity);
      int g = (int) (((textureColor >> 8) & 0xFF) * ambientCoefficient * tempLightProps.intensity);
      int b = (int) ((textureColor & 0xFF) * ambientCoefficient * tempLightProps.intensity);
      r = r > 255 ? 255 : (r < 0 ? 0 : r);
      g = g > 255 ? 255 : (g < 0 ? 0 : g);
      b = b > 255 ? 255 : (b < 0 ? 0 : b);
      return (0xFF000000 | (r << 16) | (g << 8) | b);
    }

    // 7. Diffuse
    double NdotL = Math.max(0, tempLocalNormal.dot(tempLightDir));
    double diffuseFactor = diffuseCoefficient * NdotL * tempLightProps.intensity;

    // 8. View direction
    tempViewerPoint.set(viewerPos);
    tempViewDir.set(tempViewerPoint);
    tempViewDir.subtract(worldPoint);
    tempViewDir.normalize();

    // 9. Reflection direction
    tempReflectDir.set(tempLightDir);
    tempReflectDir.negate();
    tempReflectDir.reflect(tempLocalNormal, tempReflectDir);

    // 10. Specular
    double RdotV = Math.max(0, tempReflectDir.dot(tempViewDir));
    double specFactor = Math.pow(RdotV, shininess) * specularCoefficient * tempLightProps.intensity;

    // 11. Combine - manual channel ops
    int texR = (textureColor >> 16) & 0xFF;
    int texG = (textureColor >> 8) & 0xFF;
    int texB = textureColor & 0xFF;

    int lightColor = tempLightProps.color;
    int lightR = (lightColor >> 16) & 0xFF;
    int lightG = (lightColor >> 8) & 0xFF;
    int lightB = lightColor & 0xFF;

    int diffR = (int) (texR * diffuseFactor * lightR / 255.0);
    int diffG = (int) (texG * diffuseFactor * lightG / 255.0);
    int diffB = (int) (texB * diffuseFactor * lightB / 255.0);

    int specR = (int) (this.specR * specFactor * lightR / 255.0);
    int specG = (int) (this.specG * specFactor * lightG / 255.0);
    int specB = (int) (this.specB * specFactor * lightB / 255.0);

    int ambR = (int) (texR * ambientCoefficient * tempLightProps.intensity * lightR / 255.0);
    int ambG = (int) (texG * ambientCoefficient * tempLightProps.intensity * lightG / 255.0);
    int ambB = (int) (texB * ambientCoefficient * tempLightProps.intensity * lightB / 255.0);

    int r = ambR + diffR + specR;
    int g = ambG + diffG + specG;
    int b = ambB + diffB + specB;

    r = r > 255 ? 255 : (r < 0 ? 0 : r);
    g = g > 255 ? 255 : (g < 0 ? 0 : g);
    b = b > 255 ? 255 : (b < 0 ? 0 : b);

    return (0xFF000000 | (r << 16) | (g << 8) | b);
  }

  private int getTextureColor(Point3 localPoint, Vector3 localNormal) {
    if (imagePixels == null || imgWidth == 0 || imgHeight == 0) {
      return 0x00000000;
    }

    double absX = Math.abs(localNormal.x);
    double absY = Math.abs(localNormal.y);
    double absZ = Math.abs(localNormal.z);

    double u, v;
    if (absX >= absY && absX >= absZ) {
      u = localPoint.y;
      v = localPoint.z;
    } else if (absY >= absX && absY >= absZ) {
      u = localPoint.x;
      v = localPoint.z;
    } else {
      u = localPoint.x;
      v = localPoint.y;
    }

    u = ((u * uScale) + uOffset) % 1.0;
    v = ((v * vScale) + vOffset) % 1.0;
    if (u < 0) u += 1.0;
    if (v < 0) v += 1.0;
    v = 1.0 - v;

    double x = u * (imgWidth - 1);
    double y = v * (imgHeight - 1);

    int x0 = (int) Math.floor(x);
    int y0 = (int) Math.floor(y);
    int x1 = (int) Math.ceil(x);
    int y1 = (int) Math.ceil(y);

    x0 = wrapCoordinate(x0, imgWidth);
    y0 = wrapCoordinate(y0, imgHeight);
    x1 = wrapCoordinate(x1, imgWidth);
    y1 = wrapCoordinate(y1, imgHeight);

    // Fast array lookups instead of image.getRGB()
    int c00 = imagePixels[y0 * imgWidth + x0];
    int c10 = imagePixels[y0 * imgWidth + x1];
    int c01 = imagePixels[y1 * imgWidth + x0];
    int c11 = imagePixels[y1 * imgWidth + x1];

    int a00 = (c00 >> 24) & 0xFF;
    int a10 = (c10 >> 24) & 0xFF;
    int a01 = (c01 >> 24) & 0xFF;
    int a11 = (c11 >> 24) & 0xFF;

    if (a00 < 6 && a10 < 6 && a01 < 6 && a11 < 6) {
      return 0x00000000;
    }

    double tx = x - x0;
    double ty = y - y0;
    double invTx = 1.0 - tx;
    double invTy = 1.0 - ty;

    int r =
        (int)
            (invTx * invTy * ((c00 >> 16) & 0xFF)
                + tx * invTy * ((c10 >> 16) & 0xFF)
                + invTx * ty * ((c01 >> 16) & 0xFF)
                + tx * ty * ((c11 >> 16) & 0xFF));
    int g =
        (int)
            (invTx * invTy * ((c00 >> 8) & 0xFF)
                + tx * invTy * ((c10 >> 8) & 0xFF)
                + invTx * ty * ((c01 >> 8) & 0xFF)
                + tx * ty * ((c11 >> 8) & 0xFF));
    int b =
        (int)
            (invTx * invTy * (c00 & 0xFF)
                + tx * invTy * (c10 & 0xFF)
                + invTx * ty * (c01 & 0xFF)
                + tx * ty * (c11 & 0xFF));
    int a = (int) (invTx * invTy * a00 + tx * invTy * a10 + invTx * ty * a01 + tx * ty * a11);

    r = r > 255 ? 255 : (r < 0 ? 0 : r);
    g = g > 255 ? 255 : (g < 0 ? 0 : g);
    b = b > 255 ? 255 : (b < 0 ? 0 : b);
    a = a > 255 ? 255 : (a < 0 ? 0 : a);

    return (a << 24) | (r << 16) | (g << 8) | b;
  }

  private int wrapCoordinate(int coord, int max) {
    if (coord < 0) return max - 1 - ((-coord - 1) % max);
    return coord % max;
  }

  private static double clamp01(double value) {
    return Math.max(0.0, Math.min(1.0, value));
  }

  @Override
  public double getReflectivity() {
    return reflectivity;
  }

  @Override
  public double getIndexOfRefraction() {
    return ior;
  }

  @Override
  public double getTransparency() {
    return transparency;
  }

  public void setTransparency(double transparency) {
    this.transparency = clamp01(transparency);
  }

  public void setImagePath(String npath) {
    this.imagePath = npath;
  }

  public String getImagePath() {
    return this.imagePath;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("ImageTextureMaterial imagetexturematerial {\n");
    sb.append("        imagePath = ").append(getImagePath()).append(";\n");
    sb.append("        uScale = ").append(uScale).append(";\n");
    sb.append("        vScale = ").append(vScale).append(";\n");
    sb.append("        uOffset = ").append(uOffset).append(";\n");
    sb.append("        vOffset = ").append(vOffset).append(";\n");
    sb.append("        ambientCoefficient = ").append(ambientCoefficient).append(";\n");
    sb.append("        diffuseCoefficient = ").append(diffuseCoefficient).append(";\n");
    sb.append("        specularCoefficient = ").append(specularCoefficient).append(";\n");
    sb.append("        shininess = ").append(shininess).append(";\n");
    sb.append("        reflectivity = ").append(reflectivity).append(";\n");
    sb.append("        ior = ").append(ior).append(";\n");
    sb.append("    }");
    return sb.toString();
  }
}
