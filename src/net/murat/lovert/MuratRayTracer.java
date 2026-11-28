package net.murat.lovert;

import android.graphics.Bitmap;
import android.graphics.Canvas;

import net.murat.light.*;
import net.murat.material.*;
import net.murat.math.*;
import net.murat.shape.*;
import net.murat.util.*;

/**
 * <h1>MuratRayTracer - Complete Ray Tracing Demo</h1>
 *
 * <p>Full demonstration of the ray tracing engine including all setup steps.
 *
 * <h2>Core Engine Features</h2>
 *
 * <p>This ray tracing engine is fully redesigned to be <b>100% garbage-free</b> during render()
 * execution: every Point3/Vector3/Ray/Intersection instance that is needed during pixel tracing is
 * pre-allocated once in the constructor and reused for every single pixel and every recursion
 * depth.
 *
 * @author Murat iNAN, muratsivas76@gmail.com
 * @version 3.0
 * @since 2026-06-17
 */
public class MuratRayTracer {

  // =========================================================================
  // CONSTANTS
  // =========================================================================

  /**
   * Maximum supported recursion depth for the pre-allocated stacks. This value is hard-coded to 8
   * to balance quality and performance. Higher values would require recompilation with larger
   * arrays.
   */
  public static final int MAX_DEPTH = 11;

  /** Fully opaque black color as packed ARGB integer. Format: 0xFF000000 */
  private static final int BLACK = 0xFF000000;

  // =========================================================================
  // SCENE & RENDER SETTINGS
  // =========================================================================

  /** The scene to render containing shapes and lights. */
  private Scene scene;

  /** Output image width in pixels. */
  private int width;

  /** Output image height in pixels. */
  private int height;

  /** Background color as packed ARGB (0xAARRGGBB). */
  private int backgroundColorPacked;

  /** Maximum recursion depth for reflection/refraction rays, clamped to MAX_DEPTH. */
  private int maxRecursionDepth = 2;

  /** Camera configuration object containing view parameters. */
  private Camera camera = new Camera();

  // Camera parameters cached for API compatibility and quick access.

  /** Camera position in world space. */
  private Point3 cameraPosition;

  /** Point the camera is looking at. */
  private Point3 lookAt;

  /** Up vector defining camera orientation. */
  private Vector3 upVector;

  /** Field of view in degrees. */
  private double fov;

  /** Flag indicating orthographic projection mode. */
  private boolean isOrthographic;

  /** Flag enabling/disabling reflections. */
  private boolean isReflective;

  /** Scale factor for orthographic projection. */
  private double orthographicScale = 2.0;

  // =========================================================================
  // LIGHTING
  // =========================================================================

  /**
   * Synthetic full-intensity ambient light used only to probe a material's alpha/transparency
   * channel. This light is not used for actual lighting calculations, only for determining how
   * transparent a material appears.
   */
  private final Light TRANSPARENCY_LIGHT = new MuratAmbientLight(0xFFFFFFFF, 1.0);

  private final Material DIFMAT = new DiffuseMaterial(0xFF0000FF);

  // =========================================================================
  // GC-FREE SCRATCH POOL - CAMERA RAY GENERATION
  // =========================================================================

  /** Camera Z axis (view direction) from camera to lookAt. */
  private final Vector3 camZAxis = new Vector3();

  /** Camera X axis (right vector) perpendicular to view direction. */
  private final Vector3 camXAxis = new Vector3();

  /** Camera Y axis (up vector) perpendicular to view and right. */
  private final Vector3 camYAxis = new Vector3();

  /** Temporary up vector used for cross product calculations. */
  private final Vector3 camUpScratch = new Vector3();

  /** Temporary direction vector for ray construction. */
  private final Vector3 camDirScratch = new Vector3();

  /** Scaled X axis for orthographic projection calculations. */
  private final Vector3 camScaledX = new Vector3();

  /** Scaled Y axis for orthographic projection calculations. */
  private final Vector3 camScaledY = new Vector3();

  /** Camera origin scratch point for ray construction. */
  private final Point3 camOriginScratch = new Point3();

  /** Light properties scratch object reused across all light calculations. */
  private final LightProperties lightPropsScratch = new LightProperties();

  private final EMShape[] opaqueCasterHolder = new EMShape[1];

  private final Ray primaryRayScratch = new Ray(new Point3(), new Vector3(), 1.0);

  private final Ray volumeExitRayScratch = new Ray(new Point3(), new Vector3(), 1.0);
  private final Vector3 volumeOffsetScratch = new Vector3();
  private final Point3 volumeExitOriginScratch = new Point3();
  private final Intersection volumeExitIntersectionScratch = new Intersection();

  // =========================================================================
  // GC-FREE SCRATCH POOL - DEPTH-INDEXED (must survive across recursive calls)
  // =========================================================================

  /** World-space hit point at each recursion depth. */
  private final Point3[] hitPointStack = new Point3[MAX_DEPTH];

  /** Raw geometric surface normal (as returned by the shape) at each depth. */
  private final Vector3[] rawNormalStack = new Vector3[MAX_DEPTH];

  /** Direction-corrected normal (flipped to face the incoming ray) at each depth. */
  private final Vector3[] correctedNormalStack = new Vector3[MAX_DEPTH];

  /** Reflection direction at each depth, computed once and reused. */
  private final Vector3[] reflectDirStack = new Vector3[MAX_DEPTH];

  /** Refraction direction at each depth, computed once and reused. */
  private final Vector3[] refractDirStack = new Vector3[MAX_DEPTH];

  /** Intersection result object at each depth. */
  private final Intersection[] intersectionStack = new Intersection[MAX_DEPTH];

  /** Outgoing ray object at each depth (camera ray at depth 0, secondary rays beyond). */
  private final Ray[] rayStack = new Ray[MAX_DEPTH];

  /** Generic scratch vector at each depth for various calculations. */
  private final Vector3[] scratchVec = new Vector3[MAX_DEPTH];

  /** Generic per-depth scratch vector for offset calculations. */
  private final Vector3[] offsetVectorStack = new Vector3[MAX_DEPTH];

  /** Generic per-depth scratch point for offset calculations. */
  private final Point3[] offsetPointStack = new Point3[MAX_DEPTH];

  /** Temporary intersection objects for shadow and intersection tests. */
  private final Intersection[] tempIntersectionStack = new Intersection[MAX_DEPTH];

  // =========================================================================
  // GC-FREE SCRATCH POOL - SHADOW / DIRECT LIGHTING
  // =========================================================================

  /** Light direction vector scratch for lighting calculations. */
  private final Vector3 lightDirScratch = new Vector3();

  /** Shadow offset vector for bias calculations. */
  private final Vector3 shadowOffsetScratch = new Vector3();

  /** Shadow origin point for shadow ray testing. */
  private final Point3 shadowOriginScratch = new Point3();

  /** Reusable shadow test ray object. */
  private final Ray shadowTestRay = new Ray(new Point3(), new Vector3(), 1.0);

  // =========================================================================
  // CONSTRUCTOR
  // =========================================================================

  /**
   * Creates a new ray tracer bound to the given scene and output resolution. All scratch objects
   * are pre-allocated here to ensure zero allocations during the render() method.
   *
   * @param scene the scene to render
   * @param width output image width in pixels
   * @param height output image height in pixels
   * @param backgroundColor packed ARGB color used wherever no geometry is hit
   */
  public MuratRayTracer(Scene scene, int width, int height, int backgroundColor) {
    this.scene = scene;
    this.width = width;
    this.height = height;
    this.backgroundColorPacked = backgroundColor;
    this.cameraPosition = new Point3(0, 1, 5);
    this.lookAt = new Point3(0, 0, -5);
    this.upVector = new Vector3(0, 1, 0);
    this.fov = 60.0;
    this.isOrthographic = false;
    this.isReflective = true;

    // Pre-allocate every scratch object used during recursive tracing.
    for (int i = 0; i < MAX_DEPTH; i++) {
      hitPointStack[i] = new Point3();
      rawNormalStack[i] = new Vector3();
      correctedNormalStack[i] = new Vector3();
      reflectDirStack[i] = new Vector3();
      refractDirStack[i] = new Vector3();
      scratchVec[i] = new Vector3();
      intersectionStack[i] = new Intersection();
      rayStack[i] = new Ray(new Point3(), new Vector3(), 1.0);
      offsetVectorStack[i] = new Vector3();
      offsetPointStack[i] = new Point3();
      tempIntersectionStack[i] = new Intersection();
    }
  }

  // =========================================================================
  // PUBLIC METHODS (Setters/Getters)
  // =========================================================================

  /**
   * Sets the maximum recursion depth for reflection and refraction rays. Value is clamped between 1
   * and MAX_DEPTH.
   *
   * @param depth the maximum recursion depth
   */
  public void setMaxRecursionDepth(int depth) {
    this.maxRecursionDepth = Math.max(1, Math.min(MAX_DEPTH, depth));
    camera.setMaxRecursionDepth(this.maxRecursionDepth);
  }

  /**
   * Sets the camera position in world space.
   *
   * @param position the new camera position
   */
  public void setCameraPosition(Point3 position) {
    this.cameraPosition = position;
    camera.setCameraPosition(this.cameraPosition);
  }

  /**
   * Sets the point the camera is looking at.
   *
   * @param lookAt the new look-at point
   */
  public void setLookAt(Point3 lookAt) {
    this.lookAt = lookAt;
    camera.setLookAt(this.lookAt);
  }

  /**
   * Sets the up vector defining camera orientation.
   *
   * @param upVector the new up vector
   */
  public void setUpVector(Vector3 upVector) {
    this.upVector = upVector;
    camera.setUpVector(this.upVector);
  }

  /**
   * Enables or disables reflection calculations.
   *
   * @param isrf true to enable reflections, false to disable
   */
  public void setReflective(boolean isrf) {
    this.isReflective = isrf;
    camera.setReflective(this.isReflective);
  }

  /**
   * Sets the field of view angle in degrees.
   *
   * @param fov the new field of view
   */
  public void setFov(double fov) {
    this.fov = fov;
    camera.setFov(this.fov);
  }

  /**
   * Enables or disables orthographic projection.
   *
   * @param ortho true for orthographic, false for perspective
   */
  public void setOrthographic(boolean ortho) {
    this.isOrthographic = ortho;
    camera.setOrthographic(this.isOrthographic);
  }

  /**
   * Sets the scale factor for orthographic projection.
   *
   * @param scale the orthographic scale
   */
  public void setOrthographicScale(double scale) {
    this.orthographicScale = scale;
    camera.setOrthographicScale(this.orthographicScale);
  }

  /**
   * Returns the current maximum recursion depth.
   *
   * @return the maximum recursion depth
   */
  public int getMaxRecursionDepth() {
    if (camera == null) {
      return maxRecursionDepth;
    }
    return camera.getMaxRecursionDepth();
  }

  /**
   * Returns the current camera configuration.
   *
   * @return the camera object
   */
  public Camera getCamera() {
    return this.camera;
  }

  /**
   * Sets the camera configuration.
   *
   * @param cmr the new camera object
   */
  public void setCamera(Camera cmr) {
    if (cmr == null) {
      cmr = new Camera();
    }
    this.camera = cmr;
  }

  // =========================================================================
  // CAMERA RAY GENERATION (garbage-free)
  // =========================================================================

  /**
   * Builds the primary camera ray for the given normalized screen coordinates (range roughly [-1,
   * 1]) and stores it in rayStack[0]. No object is allocated here; every intermediate vector is a
   * pre-allocated scratch field.
   *
   * @param screenX normalized screen X coordinate in [-1, 1]
   * @param screenY normalized screen Y coordinate in [-1, 1]
   * @return the generated camera ray (stored in rayStack[0])
   */
  private Ray generateCameraRay(double screenX, double screenY) {
    // Camera basis vectors (right-handed: z points from lookAt toward the camera).
    camZAxis.set(camera.getCameraPosition()).subtract(camera.getLookAt()).normalize();

    camUpScratch.set(camera.getUpVector());
    camUpScratch.cross(camZAxis, camXAxis);
    camXAxis.normalize();

    camZAxis.cross(camXAxis, camYAxis);
    camYAxis.normalize();

    if (camera.isOrthographic()) {
      double worldScreenWidth = camera.getOrthographicScale();
      double worldScreenHeight = worldScreenWidth / ((double) width / height);

      camOriginScratch.set(camera.getCameraPosition());

      camScaledX.set(camXAxis).multiply(screenX * worldScreenWidth / 2.0);
      camOriginScratch.add(camScaledX);

      camScaledY.set(camYAxis).multiply(screenY * worldScreenHeight / 2.0);
      camOriginScratch.add(camScaledY);

      camDirScratch.set(camZAxis).negate();

      return primaryRayScratch.set(camOriginScratch, camDirScratch, 1.0);
    } else {
      double aspectRatio = (double) width / height;
      double tanHalfFov = Math.tan(Math.toRadians(camera.getFov()) / 2);

      camScaledX.set(camXAxis).multiply(screenX * aspectRatio * tanHalfFov);
      camScaledY.set(camYAxis).multiply(screenY * tanHalfFov);

      camDirScratch.set(camScaledX).add(camScaledY).subtract(camZAxis).normalize();

      return primaryRayScratch.set(camera.getCameraPosition(), camDirScratch, 1.0);
    }
  }

  // =========================================================================
  // RENDER
  // =========================================================================

  /**
   * Renders the bound scene and returns the resulting image. Uses stratified jittered supersampling
   * (ssGrid x ssGrid per pixel) to eliminate aliasing/moire on high-frequency textures viewed
   * through refractive/reflective curved geometry (e.g. checkerboard through a glass sphere). No
   * allocation happens inside the per-pixel loop except the Bitmap itself, which is created once.
   *
   * @return the rendered Bitmap
   */
  public Bitmap render() {
    Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    int[] pixels = new int[width * height];

    final int grid = (camera != null) ? camera.getSupersamplingGrid() : 1;
    final double invGrid = 1.0 / grid;
    final int sampleCount = grid * grid;

    // System.out.println("=== RENDERING STARTED ===");
    // long startTime = System.currentTimeMillis();

    Ray cameraRay = null;

    for (int y = 0; y < height; y++) {
      for (int x = 0; x < width; x++) {

        long sumA = 0, sumR = 0, sumG = 0, sumB = 0;

        for (int sy = 0; sy < grid; sy++) {
          for (int sx = 0; sx < grid; sx++) {
            // Stratified jitter: sub-pixel offset within cell (sx, sy) of the
            // grid x grid grid covering this pixel. Avoids the residual
            // aliasing a plain uniform grid (without jitter) would still show.
            double jitterX = (sx + 0.5) * invGrid;
            double jitterY = (sy + 0.5) * invGrid;

            double ndcX = (x + jitterX) / width;
            double ndcY = (y + jitterY) / height;
            double screenX = 2 * ndcX - 1;
            double screenY = 1 - 2 * ndcY;

            cameraRay = generateCameraRay(screenX, screenY);
            int sampleColor = traceRay(cameraRay, 0, 1.0);

            sumA += (sampleColor >>> 24) & 0xFF;
            sumR += (sampleColor >>> 16) & 0xFF;
            sumG += (sampleColor >>> 8) & 0xFF;
            sumB += sampleColor & 0xFF;
          }
        }

        int avgA = (int) (sumA / sampleCount);
        int avgR = (int) (sumR / sampleCount);
        int avgG = (int) (sumG / sampleCount);
        int avgB = (int) (sumB / sampleCount);

        int finalColor = (avgA << 24) | (avgR << 16) | (avgG << 8) | avgB;
        pixels[y * width + x] = finalColor;
      }
    }

    bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
    
    final java.util.List<Overlay> overlays = scene.getOverlays();
    final int tsize = overlays.size();
    if (tsize > 0) {
      Canvas overlayCanvas = new Canvas(bitmap);
      for (int i = 0; i < tsize; i++) {
        Overlay o = overlays.get(i);
        if (o == null) continue;
        o.draw(overlayCanvas, width, height);
      }
    }
    
    // long endTime = System.currentTimeMillis();
    // System.out.println("Rendering completed in " + (endTime - startTime) + " ms.");

    return bitmap;
  }

  // For calling from outside
  public int traceRayOutside(Ray ray, int depth, double attenuationFactor) {
    return traceRay(ray, depth, attenuationFactor);
  }

  // End of traceRayOutside

  // =========================================================================
  // TRACE RAY - STACK-BASED, GARBAGE-FREE RECURSION
  // =========================================================================

  /**
   * Traces a single ray through the scene, handling direct lighting, shadows, reflection and
   * refraction recursively. All temporary state needed across the recursive call boundary is read
   * from/written to depth-indexed scratch arrays; nothing is allocated here.
   *
   * @param ray the ray to trace (already positioned in world space)
   * @param depth current recursion depth (0 = primary camera ray)
   * @param attenuationFactor accumulated energy attenuation from parent rays
   * @return packed ARGB color contributed by this ray
   */
  private int traceRay(Ray ray, int depth, double attenuationFactor) {
    if (attenuationFactor < Ray.MIN_ATTENUATION) {
      return BLACK;
    }
    if (depth > camera.getMaxRecursionDepth()) {
      return camera.isUseBackgroundColor() ? backgroundColorPacked : BLACK;
    }

    Point3 myHitPoint = hitPointStack[depth];
    Vector3 myRawNormal = rawNormalStack[depth];
    Vector3 N = correctedNormalStack[depth];
    Intersection myIntersection = intersectionStack[depth];
    Ray myRay = rayStack[depth];
    Vector3 myOffsetVector = offsetVectorStack[depth];
    Point3 myOffsetPoint = offsetPointStack[depth];

    EMShape shape = null;
    Material material = null;

    boolean hit = findClosestIntersection(ray, myIntersection);

    if (hit) {
      shape = myIntersection.getShape();
      //material = shape.getMaterial();
      material = myIntersection.getMaterial();   // child surface material (CSG)
      if (material == null) {
        material = shape.getMaterial();
      }
      myHitPoint.set(myIntersection.getPoint());
      myRawNormal.set(myIntersection.getNormal()).normalize();
    }

    if (!hit) {
      if (depth > 0 && depth - 1 < intersectionStack.length) {
        EMShape prevShape = intersectionStack[depth - 1].getShape();
        if (prevShape != null) {
          //Material prevMaterial = prevShape.getMaterial();
          Material prevMaterial = intersectionStack[depth - 1].getMaterial();
          if (prevMaterial == null) {
            prevMaterial = prevShape.getMaterial();
          }
          if (prevMaterial instanceof DielectricMaterial) {
            DielectricMaterial dm = (DielectricMaterial) prevMaterial;
            return ColorUtil.multiplyDouble(dm.getBaseColor(), attenuationFactor);
          } else if (prevMaterial instanceof DielectricTextMaterial) {
            DielectricTextMaterial dm = (DielectricTextMaterial) prevMaterial;
            return ColorUtil.multiplyDouble(dm.getBaseColor(), attenuationFactor);
          } else if (prevMaterial instanceof FilteredDielectricMaterial) {
            FilteredDielectricMaterial fdm = (FilteredDielectricMaterial) prevMaterial;
            return ColorUtil.multiplyDouble(backgroundColorPacked, attenuationFactor);
          } else if (prevMaterial instanceof RajaMaterial) {
            return ColorUtil.multiplyDouble(backgroundColorPacked, attenuationFactor);
          } else {
          }
        }
      }

      if (depth == 0) {
        return backgroundColorPacked;
      } else {
        if (camera.isUseBackgroundColor()) {
          return ColorUtil.multiplyDouble(backgroundColorPacked, attenuationFactor);
        } else {
          return BLACK;
        }
      }
    }

    double reflectionBias = shape.getShadowBias();
    double refractionBias = shape.getShadowBias();

    if (material == null) {
      material = DIFMAT;
    }

    boolean isTrueDielectric =
        (material instanceof DielectricMaterial)
            || (material instanceof DielectricTextMaterial)
            || (material instanceof FilteredDielectricMaterial)
            || (material instanceof RajaMaterial && material.getTransparency() > 0.5);

    // Special visibility: shape acts as a "see-through" pass-through surface
    if (!shape.isVisibleSpecial()) {
      int materialColor =
          material.getColorAt(myHitPoint, myRawNormal, TRANSPARENCY_LIGHT, ray.getOrigin());
      int alpha = (materialColor >> 24) & 0xFF;

      if (alpha < 255) {
        myOffsetVector.set(ray.getDirection()).multiply(Ray.BIAS);
        myOffsetPoint.set(myHitPoint).add(myOffsetVector);
        myRay.set(myOffsetPoint, ray.getDirection(), ray.getEnergy());
        return traceRay(myRay, depth, attenuationFactor);
      } else {
        return materialColor;
      }
    }

    // Transparency / alpha blending against whatever lies behind this shape.
    int materialColor =
        material.getColorAt(myHitPoint, myRawNormal, TRANSPARENCY_LIGHT, ray.getOrigin());
    int alpha = (materialColor >> 24) & 0xFF;

    if (alpha < 255 && !isTrueDielectric) {
      double transparency = material.getTransparency();
      if (transparency > 0.1) {
        myOffsetVector.set(ray.getDirection()).multiply(refractionBias);
        myOffsetPoint.set(myHitPoint).add(myOffsetVector);
        myRay.set(myOffsetPoint, ray.getDirection(), ray.getEnergy());

        int behindColor = traceRay(myRay, depth, attenuationFactor * transparency);

        double alphaFactor = alpha * 0.00392156862745098;
        int r =
            (int)
                (((materialColor >> 16) & 0xFF) * alphaFactor
                    + ((behindColor >> 16) & 0xFF) * (1.0 - alphaFactor));
        int g =
            (int)
                (((materialColor >> 8) & 0xFF) * alphaFactor
                    + ((behindColor >> 8) & 0xFF) * (1.0 - alphaFactor));
        int b =
            (int)
                ((materialColor & 0xFF) * alphaFactor + (behindColor & 0xFF) * (1.0 - alphaFactor));

        return (0xFF000000 | (r << 16) | (g << 8) | b);
      }
    }

    // Normal direction correction
    boolean entering = ray.getDirection().dot(myRawNormal) < 0;
    N.set(myRawNormal);
    if (!entering) {
      N.negate();
    }

    int finalColorPacked = 0xFF000000;
    boolean allowShadows = ((camera.isSelfShadowsEnabled()) || (depth == 0));

    double surfaceFactor = 0.0;

    if (isTrueDielectric) {
      surfaceFactor = 1.0 - material.getReflectivity();
    } else {
      surfaceFactor = (1.0 - material.getReflectivity()) * (1.0 - material.getTransparency());
    }

    // Direct lighting (diffuse + specular from all non-ambient lights)
    int directLightingColor =
        calculateDirectLighting(myHitPoint, N, material, ray, shape, allowShadows);
    directLightingColor = ColorUtil.multiplyDouble(directLightingColor, surfaceFactor);
    finalColorPacked = ColorUtil.addEnergyConserving(finalColorPacked, directLightingColor);

    // Ambient contribution
    java.util.List lights = scene.getLights();
    int lightCount = lights.size();
    for (int i = 0; i < lightCount; i++) {
      Light light = (Light) lights.get(i);
      if (light instanceof MuratAmbientLight) {
        int ambientColor = material.getColorAt(myHitPoint, N, light, ray.getOrigin());
        ambientColor = ColorUtil.multiplyDouble(ambientColor, surfaceFactor);
        finalColorPacked = ColorUtil.addEnergyConserving(finalColorPacked, ambientColor);
      }
    }

    // Apply attenuation to final color
    finalColorPacked = ColorUtil.multiplyDouble(finalColorPacked, attenuationFactor);

    double n1_fresnel = entering ? 1.0 : material.getIndexOfRefraction();
    double n2_fresnel = entering ? material.getIndexOfRefraction() : 1.0;
    double fresnel = Vector3.calculateFresnel(ray.getDirection(), N, n1_fresnel, n2_fresnel);

    if (shouldCalculateReflections(material, shape)) {
      Vector3 D = ray.getDirection();
      double dotDN = D.x * N.x + D.y * N.y + D.z * N.z;

      Vector3 reflectDir = reflectDirStack[depth];
      reflectDir.x = D.x - 2.0 * dotDN * N.x;
      reflectDir.y = D.y - 2.0 * dotDN * N.y;
      reflectDir.z = D.z - 2.0 * dotDN * N.z;
      reflectDir.normalize();

      double baseReflectivity = material.getReflectivity();

      boolean wantsFresnelBoost =
          isTrueDielectric
              || (material instanceof RajaMaterial && material.getTransparency() > 0.4);

      double effectiveReflectivity =
          wantsFresnelBoost
              ? baseReflectivity + (1.0 - baseReflectivity) * Math.pow(fresnel, 3)
              : baseReflectivity;

      double newReflectedAttenuation = attenuationFactor * effectiveReflectivity;

      myOffsetVector.set(N).multiply(reflectionBias);
      myOffsetPoint.set(myHitPoint).add(myOffsetVector);

      myRay.set(myOffsetPoint, reflectDir, ray.getEnergy());
      int reflectedColor = traceRay(myRay, depth + 1, newReflectedAttenuation);

      if (isTrueDielectric) {
        if (material instanceof FilteredDielectricMaterial) {
          FilteredDielectricMaterial fdm = (FilteredDielectricMaterial) material;
          int filterColor = entering ? fdm.getFilterColorInside() : fdm.getFilterColorOutside();

          double rimMask = Math.pow(fresnel, 3.0);
          reflectedColor =
              ColorUtil.blendColors(reflectedColor, filterColor, (float) (rimMask * 0.7));
        } else if (material instanceof RajaMaterial) {
          RajaMaterial rajaMat = (RajaMaterial) material;
          int rawTextureColor = rajaMat.getTexture().sample(myHitPoint, 0.0, 0.0);
          reflectedColor = ColorUtil.tint(reflectedColor, rawTextureColor);
        }
      } else if (material instanceof RajaMaterial) {
        RajaMaterial rajaMat = (RajaMaterial) material;
        if (rajaMat.getReflectivity() > 0.3) {
          int rawTextureColor = rajaMat.getTexture().sample(myHitPoint, 0.0, 0.0);
          reflectedColor = ColorUtil.tint(reflectedColor, rawTextureColor);
        }
      }
      finalColorPacked = ColorUtil.addEnergyConserving(finalColorPacked, reflectedColor);
    }

    // =========================================================================
    // REFRACTION
    // =========================================================================
    if (shouldCalculateRefractions(material, shape)) {
      Vector3 refractedDir = refractDirStack[depth];
      double n1 = entering ? 1.0 : material.getIndexOfRefraction();
      double n2 = entering ? material.getIndexOfRefraction() : 1.0;

      if (ray.getDirection().refract(N, n1, n2, refractedDir)) {
        double biasSign = entering ? -1.0 : 1.0;
        myOffsetVector.set(N).multiply(refractionBias * biasSign);
        myOffsetPoint.set(myHitPoint).add(myOffsetVector);

        double newRefractedAttenuation =
            attenuationFactor * material.getTransparency() * (1.0 - fresnel);

        myRay.set(myOffsetPoint, refractedDir, ray.getEnergy());
        int refractedColor = traceRay(myRay, depth + 1, newRefractedAttenuation);

        if (material instanceof FilteredDielectricMaterial) {
          FilteredDielectricMaterial fdm = (FilteredDielectricMaterial) material;
          int filterColor = entering ? fdm.getFilterColorInside() : fdm.getFilterColorOutside();
          float blend = (float) (fresnel * 0.4);
          refractedColor = ColorUtil.blendColors(refractedColor, filterColor, blend);
        } else if (material instanceof DielectricMaterial) {
          if (entering) {
            int glassColor = ((DielectricMaterial) material).getBaseColor();
            refractedColor = ColorUtil.tint(refractedColor, glassColor);
          }
        } else if (material instanceof DielectricTextMaterial) {
          if (entering) {
            int glassColor = ((DielectricTextMaterial) material).getBaseColor();
            refractedColor = ColorUtil.tint(refractedColor, glassColor);
          }
        } else if (material instanceof RajaMaterial) {
          RajaMaterial rajaMat = (RajaMaterial) material;

          int rawTextureColor = rajaMat.getTexture().sample(myHitPoint, 0.0, 0.0);
          refractedColor = ColorUtil.tint(refractedColor, rawTextureColor);

          if (rajaMat.hasVolume()) {
            Ray refractedProbeRay = volumeExitRayScratch;
            refractedProbeRay.set(myHitPoint, refractedDir, ray.getEnergy());

            Vector3 innerNormal = scratchVec[depth];

            boolean useEnteringNormal =
                rajaMat.getVolume().getEnteringHasPriority() ? entering : !entering;

            if (useEnteringNormal) {
              innerNormal.set(N).negate();
            } else {
              innerNormal.set(N);
            }

            double insideDistance =
                calculateVolumeDistance(refractedProbeRay, innerNormal, myHitPoint, shape);
            if (insideDistance > 0.0) {
              refractedColor = rajaMat.applyVolume(refractedColor, insideDistance);
            }
          }
        } else {
          refractedColor = ColorUtil.tint(refractedColor, materialColor);
        }

        refractedColor = ColorUtil.multiplyDouble(refractedColor, (1.0 - fresnel));
        finalColorPacked = ColorUtil.addEnergyConserving(finalColorPacked, refractedColor);

      } else {
        // Total internal reflection
        Vector3 D = ray.getDirection();
        double dotDN = D.x * N.x + D.y * N.y + D.z * N.z;
        Vector3 tirReflectDir = reflectDirStack[depth];
        tirReflectDir.x = D.x - 2.0 * dotDN * N.x;
        tirReflectDir.y = D.y - 2.0 * dotDN * N.y;
        tirReflectDir.z = D.z - 2.0 * dotDN * N.z;
        tirReflectDir.normalize();

        double biasSign = entering ? -1.0 : 1.0;
        myOffsetVector.set(N).multiply(refractionBias * -biasSign);
        myOffsetPoint.set(myHitPoint).add(myOffsetVector);

        double tirAttenuation = attenuationFactor * material.getTransparency();
        myRay.set(myOffsetPoint, tirReflectDir, ray.getEnergy());
        int tirColor = traceRay(myRay, depth + 1, tirAttenuation);

        finalColorPacked = ColorUtil.addEnergyConserving(finalColorPacked, tirColor);
      }
    }

    return ColorUtil.clampColor(finalColorPacked);
  } // End of traceRay

  // =========================================================================
  // HELPER METHODS
  // =========================================================================

  private double calculateVolumeDistance(
      Ray ray, Vector3 entryNormal, Point3 hitPoint, EMShape shape) {
    volumeOffsetScratch.set(ray.getDirection()).multiply(Ray.BIAS);
    volumeExitOriginScratch.set(hitPoint).add(volumeOffsetScratch);

    volumeOffsetScratch.set(entryNormal).multiply(Ray.BIAS);
    volumeExitOriginScratch.add(volumeOffsetScratch);

    volumeExitRayScratch.set(volumeExitOriginScratch, ray.getDirection(), ray.getEnergy());
    volumeExitIntersectionScratch.reset();

    if (shape.intersect(volumeExitRayScratch, volumeExitIntersectionScratch)) {
      double distance = volumeExitIntersectionScratch.getDistance();

      if (distance > Ray.EPSILON) {
        Vector3 exitHitNormal = volumeExitIntersectionScratch.getNormal();
        double alignment = entryNormal.dot(exitHitNormal);

        if (alignment > 0.95) {
          return 0.5;
        }

        return distance + Ray.BIAS;
      }
    }

    return 0.5; // fallback
  }

  /**
   * Decides whether a reflection ray should be spawned for the given material/shape combination,
   * based on camera/shape reflective flags and the material's reflectivity coefficient.
   */
  private boolean shouldCalculateReflections(Material material, EMShape shape) {
    if (!camera.isReflective() || !shape.isReflective()) {
      return false;
    }
    return material.getReflectivity() > Ray.EPSILON;
  }

  /**
   * Decides whether a refraction ray should be spawned for the given material/shape combination,
   * based on camera/shape refractive flags and the material's transparency coefficient.
   */
  private boolean shouldCalculateRefractions(Material material, EMShape shape) {
    if (!camera.isRefractive() || !shape.isRefractive()) {
      return false;
    }
    return material.getTransparency() > Ray.EPSILON;
  }

  /** Returns whether shadow testing is currently enabled on the camera. */
  private boolean shouldCalculateShadows() {
    return camera.isShadowsEnabled();
  }

  /**
   * Computes the direct lighting contribution (diffuse + specular, per the material's own
   * getColorAt implementation) for every non-ambient light in the scene, applying shadow testing
   * per light.
   */
  private int calculateDirectLighting(
      Point3 point,
      Vector3 normal,
      Material material,
      Ray ray,
      EMShape tracerShape,
      boolean allowShadows) {

    int directLightingColorPacked = 0xFF000000;
    double shadowBias = tracerShape.getShadowBias();

    java.util.List lights = scene.getLights();
    int lightCount = lights.size();

    for (int i = 0; i < lightCount; i++) {
      Light light = (Light) lights.get(i);
      if (light instanceof MuratAmbientLight) continue;

      LightProperties.getLightProperties(light, point, lightPropsScratch);

      if (lightPropsScratch.intensity <= 0.0) continue;

      lightDirScratch.set(lightPropsScratch.direction);

      double distance = Double.POSITIVE_INFINITY;
      Point3 lightPos = light.getPosition();
      if (lightPos != null) {
        distance = lightPos.distance(point);
      }

      shadowOffsetScratch.set(normal).multiply(shadowBias);
      shadowOriginScratch.set(point).add(shadowOffsetScratch);

      int contribution = material.getColorAt(point, normal, light, ray.getOrigin());

      if (!allowShadows || !shouldCalculateShadows()) {
        directLightingColorPacked =
            ColorUtil.addEnergyConserving(directLightingColorPacked, contribution);
      } else {
        EMShape shadowCaster =
            findShadowCaster(shadowOriginScratch, lightDirScratch, distance, tracerShape);

        if (shadowCaster == null) {
          directLightingColorPacked =
              ColorUtil.addEnergyConserving(directLightingColorPacked, contribution);
        } else {
          directLightingColorPacked =
              ColorUtil.addEnergyConserving(
                  directLightingColorPacked,
                  ColorUtil.applyShadowColor(contribution, shadowCaster.getShadowColor()));
        }
      }
    }
    return directLightingColorPacked;
  }

  /**
   * Finds the closest shape blocking the path from shadowOrigin toward the given light direction,
   * within lightDistance. Returns null if the light is unobstructed.
   */
  private EMShape findShadowCaster(
      Point3 shadowOrigin, Vector3 lightDir, double lightDistance, EMShape currentShape) {
    shadowTestRay.set(shadowOrigin, lightDir, 1.0);

    EMShape closestShape = null;
    double closestDist = Double.POSITIVE_INFINITY;

    java.util.List<EMShape> shapes = scene.getShapes();
    int size = shapes.size();

    for (int i = 0; i < size; i++) {
      EMShape shape = shapes.get(i);

      if (shape == currentShape) continue;
      if (!shape.isShadowOnly() && !shape.isVisible()) continue;
      if (!shape.isShadowEnable()) continue;

      // Use tempIntersectionStack to avoid conflicts with main intersection
      Intersection tempInt = tempIntersectionStack[i % MAX_DEPTH];
      tempInt.reset();
      if (shape.intersect(shadowTestRay, tempInt)) {
        double dist = tempInt.getDistance();

        if (dist > Ray.BIAS && dist < closestDist) {
          closestDist = dist;
          closestShape = shape;
        }
      }
    }

    if (closestShape == null) return null;
    if (lightDistance == Double.POSITIVE_INFINITY) return closestShape;
    if (closestDist < (lightDistance - Ray.BIAS)) return closestShape;

    return null;
  }

  /**
   * Finds the closest shape hit by the given ray across the entire scene.
   *
   * @param ray the ray to test
   * @param outIntersection the intersection result will be stored here
   * @return true if an intersection was found
   */
  private boolean findClosestIntersection(Ray ray, Intersection outIntersection) {
    EMShape closestShape = null;
    double closestDist = Double.POSITIVE_INFINITY;
    boolean found = false;

    java.util.List<EMShape> shapes = scene.getShapes();
    int size = shapes.size();

    for (int i = 0; i < size; i++) {
      EMShape shape = shapes.get(i);
      if (!shape.isVisible() && !shape.isShadowOnly()) continue;
      if (shape.isShadowOnly()) continue;

      Intersection tempInt = tempIntersectionStack[i % MAX_DEPTH];
      tempInt.reset(); // CRITICAL: Must reset before use
      if (shape.intersect(ray, tempInt)) {
        double dist = tempInt.getDistance();
        if (dist > Ray.EPSILON && dist < closestDist) {
          closestDist = dist;
          closestShape = shape;
          outIntersection.set(tempInt);
          found = true;
        }
      }
    }

    return found;
  }

  /** Returns a string representation of the renderer configuration. */
  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("Renderer Settings {\n");
    sb.append("    width = ").append(width).append(";\n");
    sb.append("    height = ").append(height).append(";\n");
    sb.append("    backgroundColor = ")
        .append(ColorUtil.toColorString(backgroundColorPacked))
        .append(";\n");
    sb.append("}\n");
    return sb.toString();
  }

  // =========================================================================
  // MAIN - EMPTY (not used)
  // =========================================================================

  /**
   * Main method - intentionally left empty. Use TestTracer class for rendering examples.
   *
   * @param args command line arguments (ignored)
   */
  public static void main(String[] args) {
    // This main method is intentionally left empty.
    // Use TestTracer class for rendering examples.
  }
}
