package net.murat.gui;

import android.graphics.Bitmap;
import android.util.Log;
import java.io.*;
import net.murat.light.*;
import net.murat.lovert.*;
import net.murat.material.*;
import net.murat.math.*;
import net.murat.shape.*;
import net.murat.util.*;

public class SceneParser {

  private static final String TAG = "SceneParser";

  private final java.util.Map<String, EMShape> shapes =
      new java.util.LinkedHashMap<String, EMShape>();
  private final java.util.Map<String, Light> lights = new java.util.LinkedHashMap<String, Light>();
  private final java.util.List<EMShape> sceneShapes = new java.util.ArrayList<EMShape>();
  private final java.util.List<Light> sceneLights = new java.util.ArrayList<Light>();

  private Camera camera = new Camera();
  private int bgColor = 0xFF0000FF;

  // Default dimensions (overridable via Renderer Settings block, clamped to limits)
  private int width = 640;
  private int height = 480;

  // Configurable limits. The defaults match settings.txt defaults.
  private int maxWidth = 640;
  private int maxHeight = 480;
  private int maxDepth = 4;
  private int maxSampling = 2;

  // Render timing measurement, exposed via getLastRenderTimeMs().
  private long lastRenderTimeMs = 0L;

  private int shapeCounter = 0;

  private final int materlen = "Material".length();

  /** Default constructor. Uses built-in limits: 640x480, depth 4, sampling 2. */
  public SceneParser() {
    this(640, 480, 4, 2);
  }

  /**
   * Constructor with explicit limits, driven by settings.txt values. Values are validated:
   * non-positive inputs fall back to the defaults.
   *
   * @param maxWidth Maximum allowed render width in pixels.
   * @param maxHeight Maximum allowed render height in pixels.
   * @param maxDepth Maximum allowed recursion depth.
   * @param maxSampling Maximum allowed supersampling grid size.
   */
  public SceneParser(int maxWidth, int maxHeight, int maxDepth, int maxSampling) {
    this.maxWidth = (maxWidth > 0) ? maxWidth : 640;
    this.maxHeight = (maxHeight > 0) ? maxHeight : 480;
    this.maxDepth = (maxDepth > 0) ? maxDepth : 4;
    this.maxSampling = (maxSampling > 0) ? maxSampling : 2;

    camera.setCameraPosition(new Point3(0, 0, 5));
    camera.setLookAt(new Point3(0, 0, 0));
    camera.setUpVector(new Vector3(0, 1, 0));
    camera.setFov(60.0);
    camera.setOrthographic(false);
    camera.setReflective(true);
    camera.setRefractive(true);
    camera.setShadowsEnabled(true);
    camera.setMaxRecursionDepth(3);
    camera.setSupersamplingGrid(1);
    camera.setAnimationPoints(new Point3[] {new Point3(0, 0, 0), new Point3(0, 0, 0)});
  }

  // =====================================================================
  // ANDROID ENTRY POINTS
  // =====================================================================
  // The desktop version took a File or filename. Android version takes
  // a String (the scene text from MEditor) so it works without filesystem
  // plumbing. A File-based variant is kept for convenience.

  /** Renders scene from a text string (Android primary entry point). */
  public Bitmap renderScene(String text, boolean unusedMarker) throws IOException {
    parseSceneText(text);
    return render();
  }

  /** Renders scene from a text string. Returns null on parse/render failure. */
  public Bitmap renderSceneFromText(String text) {
    try {
      parseSceneText(text);
      return render();
    } catch (Exception e) {
      Log.e(TAG, "renderSceneFromText error", e);
      return null;
    }
  }

  /** Renders scene from a file path. Kept for completeness. */
  public Bitmap renderScene(String filename) throws IOException {
    parseSceneFile(filename);
    return render();
  }

  /** Renders scene from a File. Kept for completeness. */
  public Bitmap renderScene(File file) throws IOException {
    parseSceneFile(file);
    return render();
  }

  private void parseSceneFile(String filename) throws IOException {
    parseSceneFile(new File(filename));
  }

  private void parseSceneFile(File file) throws IOException {
    shapes.clear();
    lights.clear();
    sceneShapes.clear();
    sceneLights.clear();
    shapeCounter = 0;

    java.util.List<String> lines = readFileLines(file);
    parseSections(lines);
    buildScene();
  }

  private java.util.List<String> readFileLines(File file) throws IOException {
    java.util.List<String> lines = new java.util.ArrayList<String>();
    try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
      String line;
      while ((line = reader.readLine()) != null) {
        line = line.trim();
        if (!line.isEmpty() && !line.startsWith("//") && !line.startsWith("#")) {
          lines.add(line);
        }
      }
    }
    return lines;
  }

  private void parseSceneText(String text) throws IOException {
    shapes.clear();
    lights.clear();
    sceneShapes.clear();
    sceneLights.clear();
    shapeCounter = 0;

    java.util.List<String> lines = readTextLines(text);
    parseSections(lines);
    buildScene();
  }

  private java.util.List<String> readTextLines(String text) throws IOException {
    java.util.List<String> lines = new java.util.ArrayList<String>();
    try (BufferedReader reader = new BufferedReader(new StringReader(text))) {
      String line;
      while ((line = reader.readLine()) != null) {
        line = line.trim();
        if (!line.isEmpty() && !line.startsWith("//") && !line.startsWith("#")) {
          lines.add(line);
        }
      }
    }
    return lines;
  }

  // =====================================================================
  // SECTION DETECTION
  // =====================================================================

  private boolean isSectionStart(String line) {
    if (line.startsWith("Camera")) return true;
    if (line.startsWith("Renderer Settings")) return true;
    if (line.contains("Light") && line.endsWith("{")) return true;
    if (Utilities.isShapeLine(line)) return true;
    return false;
  }

  private void parseSection(String content) {
    String firstLine = content.split("\n")[0].trim();

    if (firstLine.startsWith("Camera")) {
      parseCamera(content);
    } else if (firstLine.startsWith("Renderer Settings")) {
      parseRenderer(content);
    } else if (firstLine.contains("Light")) {
      parseLight(content);
    } else if (Utilities.isShapeLine(firstLine)) {
      parseShape(content);
    }
  }

  // =====================================================================
  // CAMERA PARSER
  // =====================================================================

  private void parseCamera(String content) {
    try {
      Point3 position = Utilities.parsePoint3(content, "position");
      Point3 lookAt = Utilities.parsePoint3(content, "lookAt");
      Vector3 upVector = Utilities.parseVector3(content, "upVector");
      double fov = Utilities.parseDouble(content, "fov");
      boolean orthographic = Utilities.parseBoolean(content, "orthographic");
      int maxRecursionDepth = Utilities.parseInt(content, "maxRecursionDepth");
      int ssg = Utilities.parseInt(content, "ssGridCount");
      Point3 a1p = Utilities.parsePoint3(content, "firstAnimationPoint");
      Point3 a2p = Utilities.parsePoint3(content, "secondAnimationPoint");
      boolean reflective = Utilities.parseBoolean(content, "reflective");
      boolean refractive = Utilities.parseBoolean(content, "refractive");
      boolean shadowsEnabled = Utilities.parseBoolean(content, "shadowsEnabled");
      boolean selfShadowsEnabled = Utilities.parseBoolean(content, "selfShadowsEnabled");
      boolean useBackgroundColor = Utilities.parseBoolean(content, "useBackgroundColor");

      // Silent clamp: depth and sampling cannot exceed the configured limits.
      if (maxRecursionDepth > maxDepth) {
        Log.w(
            TAG,
            "maxRecursionDepth "
                + maxRecursionDepth
                + " exceeds limit "
                + maxDepth
                + ", clamping.");
        maxRecursionDepth = maxDepth;
      }
      if (ssg > maxSampling) {
        Log.w(TAG, "ssGridCount " + ssg + " exceeds limit " + maxSampling + ", clamping.");
        ssg = maxSampling;
      }

      camera.setCameraPosition(position);
      camera.setLookAt(lookAt);
      camera.setUpVector(upVector);
      camera.setFov(fov);
      camera.setOrthographic(orthographic);
      camera.setMaxRecursionDepth(maxRecursionDepth);
      camera.setSupersamplingGrid(ssg);
      camera.setAnimationPoints(new Point3[] {a1p, a2p});
      camera.setReflective(reflective);
      camera.setRefractive(refractive);
      camera.setShadowsEnabled(shadowsEnabled);
      camera.setSelfShadowsEnabled(selfShadowsEnabled);
      camera.setUseBackgroundColor(useBackgroundColor);

    } catch (Exception e) {
      Log.e(TAG, "Camera parse error: " + e.getMessage(), e);
    }
  }

  // =====================================================================
  // RENDERER PARSER
  // =====================================================================

  private void parseRenderer(String content) {
    try {
      int parsedWidth = Utilities.parseInt(content, "width");
      int parsedHeight = Utilities.parseInt(content, "height");

      // Reject oversized renders: mark as invalid so render() returns null
      // and the caller can surface a Toast notification.
      if (parsedWidth > maxWidth || parsedHeight > maxHeight) {
        Log.e(
            TAG,
            "Renderer size "
                + parsedWidth
                + "x"
                + parsedHeight
                + " exceeds limit "
                + maxWidth
                + "x"
                + maxHeight
                + ". Render aborted.");
        width = 0;
        height = 0;
      } else if (parsedWidth <= 0 || parsedHeight <= 0) {
        Log.e(
            TAG, "Invalid renderer size " + parsedWidth + "x" + parsedHeight + ". Render aborted.");
        width = 0;
        height = 0;
      } else {
        width = parsedWidth;
        height = parsedHeight;
      }

      bgColor = Utilities.parseColor(content, "backgroundColor");

      Log.d(TAG, "Renderer parsed: " + width + "x" + height);
    } catch (Exception e) {
      Log.e(TAG, "Renderer parse error: " + e.getMessage(), e);
    }
  }

  // =====================================================================
  // LIGHT PARSER
  // =====================================================================

  private void parseLight(String content) {
    try {
      String[] lines = content.split("\n");
      String firstLine = lines[0].trim();
      String[] header = firstLine.split("\\s+");

      if (header.length < 2) {
        Log.e(TAG, "Light parse error: malformed header -> " + firstLine);
        return;
      }

      String lightType = header[0];
      String lightName = header[1];

      final String LIGHT_SUFFIX = "Light";
      if (lightType.endsWith(LIGHT_SUFFIX)) {
        lightType = lightType.substring(0, lightType.length() - LIGHT_SUFFIX.length());
      }

      Light light = Utilities.createLightFromText(lightType, content);
      if (light != null) {
        String uniqueName = lightName + "_" + lights.size();
        lights.put(uniqueName, light);
      } else {
        Log.e(
            TAG,
            "Light parse error: createLightFromText returned null for type '" + lightType + "'");
      }
    } catch (Exception e) {
      Log.e(TAG, "Light parse error: " + e.getMessage(), e);
    }
  }

  // =====================================================================
  // SHAPE PARSER
  // =====================================================================

  private void parseShape(String content) {
    try {
      EMShape shape = null;
      String shapeType = "";
      String originalName = "";

      String[] lines = content.split("\n");
      if (lines.length > 0) {
        String firstLine = lines[0].trim();
        String[] parts = firstLine.split("\\s+");
        if (parts.length >= 2) {
          shapeType = parts[0];
          originalName = parts[1];
        }
      }

      boolean isCSG = shapeType.contains("CSG");
      if (shapeType.equalsIgnoreCase("Mesh")) {
        shape = parseMeshShape(content);
        if (shape == null) return;

        String uniqueName = originalName + "_" + (shapeCounter++);
        shapes.put(uniqueName, shape);
      } else if (isCSG) {
        shape = parseCSGShape(content);
        if (shape == null) return;

        String uniqueName = originalName + "_" + (shapeCounter++);
        shapes.put(uniqueName, shape);
      } else {
        shape = Utilities.createShapeFromText(shapeType, content);
        if (shape != null) {
          Matrix4 transform = parseTransformFromContent(content);
          shape.setTransform(transform);

          Material material = extractMaterialFromShape(content);
          if (material != null) {
            material.setObjectTransform(transform.inverse());
            shape.setMaterial(material);
          }

          String uniqueName = originalName + "_" + (shapeCounter++);
          shapes.put(uniqueName, shape);
        }
      }

    } catch (Exception e) {
      Log.e(TAG, "Shape parse error: " + e.getMessage(), e);
    }
  }

  /** Parse Mesh shape from content */
  private Mesh parseMeshShape(String content) {
    try {
      java.util.List<Point3> vertices = new java.util.ArrayList<>();
      java.util.List<Triangle> triangles = new java.util.ArrayList<>();
      String meshName = "mesh";
      Matrix4 transform = Matrix4.identity();
      Matrix4 firstAnim = Matrix4.identity();
      Matrix4 secondAnim = Matrix4.identity();
      String otherAnimationInfo = "veofr,0e0fr,veofr";
      int shadowColor = 0xFF000000;
      double shadowBias = 0.001;
      boolean isVisibleSpecial = true;
      boolean isVisible = true;
      boolean isShadowEnable = true;
      boolean isShadowOnly = false;
      boolean isReflective = true;
      boolean isRefractive = true;
      Material material = null;

      String[] lines = content.split("\n");
      final int linesLength = lines.length;

      for (int i = 0; i < linesLength; i++) {
        String line = lines[i].trim();

        if (line.startsWith("v ")) {
          String[] parts = line.split("\\s+");
          if (parts.length >= 4) {
            try {
              double x = Double.parseDouble(parts[1]);
              double y = Double.parseDouble(parts[2]);
              double z = Double.parseDouble(parts[3]);
              vertices.add(new Point3(x, y, z));
            } catch (NumberFormatException ex) {
            }
          }
        } else if (line.startsWith("f ")) {
          String[] parts = line.split("\\s+");
          if (parts.length >= 4) {
            try {
              int i1 = Integer.parseInt(parts[1]) - 1;
              int i2 = Integer.parseInt(parts[2]) - 1;
              int i3 = Integer.parseInt(parts[3]) - 1;
              if (i1 >= 0
                  && i1 < vertices.size()
                  && i2 >= 0
                  && i2 < vertices.size()
                  && i3 >= 0
                  && i3 < vertices.size()) {
                triangles.add(new Triangle(vertices.get(i1), vertices.get(i2), vertices.get(i3)));
              }
            } catch (NumberFormatException ex) {
            }
          }
        } else if (line.startsWith("name = ")) {
          meshName = line.substring("name = ".length()).replace(";", "").trim();
        } else if (line.startsWith("transform = ")) {
          String transformStr = line.substring("transform = ".length()).replace(";", "").trim();
          try {
            transform = Matrix4.createMatrixFromString(transformStr);
          } catch (Exception ex) {
          }
        } else if (line.startsWith("firstAnim_")) {
          String animStr = line.substring(line.indexOf("=") + 1).replace(";", "").trim();
          try {
            firstAnim = Matrix4.createMatrixFromString(animStr);
          } catch (Exception ex) {
          }
        } else if (line.startsWith("secondAnim_")) {
          String animStr = line.substring(line.indexOf("=") + 1).replace(";", "").trim();
          try {
            secondAnim = Matrix4.createMatrixFromString(animStr);
          } catch (Exception ex) {
          }
        } else if (line.startsWith("otherAnimationInfo = ")) {
          otherAnimationInfo =
              line.substring("otherAnimationInfo = ".length()).replace(";", "").trim();
        } else if (line.startsWith("shadowColor = ")) {
          String colorStr = line.substring("shadowColor = ".length()).replace(";", "").trim();
          try {
            shadowColor = Utilities.parseColor("shadowColor = " + colorStr);
          } catch (Exception ex) {
          }
        } else if (line.startsWith("shadowBias = ")) {
          String biasStr = line.substring("shadowBias = ".length()).replace(";", "").trim();
          try {
            shadowBias = Double.parseDouble(biasStr);
          } catch (NumberFormatException ex) {
          }
        } else if (line.startsWith("isVisibleSpecial = ")) {
          isVisibleSpecial =
              Boolean.parseBoolean(
                  line.substring("isVisibleSpecial = ".length()).replace(";", "").trim());
        } else if (line.startsWith("isVisible = ")) {
          isVisible =
              Boolean.parseBoolean(line.substring("isVisible = ".length()).replace(";", "").trim());
        } else if (line.startsWith("isShadowEnable = ")) {
          isShadowEnable =
              Boolean.parseBoolean(
                  line.substring("isShadowEnable = ".length()).replace(";", "").trim());
        } else if (line.startsWith("isShadowOnly = ")) {
          isShadowOnly =
              Boolean.parseBoolean(
                  line.substring("isShadowOnly = ".length()).replace(";", "").trim());
        } else if (line.startsWith("isReflective = ")) {
          isReflective =
              Boolean.parseBoolean(
                  line.substring("isReflective = ".length()).replace(";", "").trim());
        } else if (line.startsWith("isRefractive = ")) {
          isRefractive =
              Boolean.parseBoolean(
                  line.substring("isRefractive = ".length()).replace(";", "").trim());
        }
      }

      if (triangles.isEmpty()) {
        Log.e(TAG, "No triangles found in Mesh");
        return null;
      }

      Mesh mesh = new Mesh(triangles);
      mesh.setName(meshName);
      mesh.setTransform(transform);
      mesh.setAnimationTransforms(new Matrix4[] {firstAnim, secondAnim});
      mesh.setOtherAnimationInfo(otherAnimationInfo);
      mesh.setShadowColor(shadowColor);
      mesh.setShadowBias(shadowBias);
      mesh.setVisibleSpecial(isVisibleSpecial);
      mesh.setVisible(isVisible);
      mesh.setShadowEnable(isShadowEnable);
      mesh.setShadowOnly(isShadowOnly);
      mesh.setReflective(isReflective);
      mesh.setRefractive(isRefractive);

      material = extractMaterialFromShape(content);
      if (material != null) {
        material.setObjectTransform(transform.inverse());
        mesh.setMaterial(material);
      }

      return mesh;

    } catch (Exception e) {
      Log.e(TAG, "Mesh parse error: " + e.getMessage(), e);
      return null;
    }
  }

  // =====================================================================
  // SECTION SCANNER
  // =====================================================================

  private void parseSections(java.util.List<String> lines) {
    StringBuilder currentSection = new StringBuilder();
    int braceCount = 0;
    boolean inSection = false;

    for (int i = 0; i < lines.size(); i++) {
      String line = lines.get(i);
      if (line.equals("-END-")) break;

      if (!inSection && isSectionStart(line)) {
        inSection = true;
        currentSection = new StringBuilder();
        braceCount = 0;
      }

      if (inSection) {
        currentSection.append(line).append("\n");
        for (char c : line.toCharArray()) {
          if (c == '{') braceCount++;
          if (c == '}') braceCount--;
        }

        if (braceCount == 0 && currentSection.length() > 0) {
          parseSection(currentSection.toString());
          inSection = false;
        }
      }
    }
  }

  // =====================================================================
  // CSG DELEGATION
  // =====================================================================

  private EMShape parseCSGShape(String content) {
    return Utilities.parseCSGShape(content);
  }

  // =====================================================================
  // TRANSFORM PARSING
  // =====================================================================

  private Matrix4 parseTransformFromContent(String content) {
    try {
      String transformStr = Utilities.extractValue(content, "transform");
      if (transformStr == null) return Matrix4.identity();

      return Matrix4.createMatrixFromString(transformStr);

    } catch (Exception e) {
      Log.e(TAG, "Transform parse error: " + e.getMessage(), e);
      return Matrix4.identity();
    }
  }

  // =====================================================================
  // MATERIAL EXTRACTION FROM SHAPE
  // =====================================================================

  private Material extractMaterialFromShape(String shapeContent) {
    try {
      int materialStart = shapeContent.indexOf("material = ");
      if (materialStart < 0) return null;

      String remaining = shapeContent.substring(materialStart + "material = ".length());
      int braceStart = remaining.indexOf("{");
      if (braceStart < 0) return null;

      int braceCount = 1;
      int currentPos = braceStart + 1;

      while (currentPos < remaining.length() && braceCount > 0) {
        char c = remaining.charAt(currentPos);
        if (c == '{') braceCount++;
        if (c == '}') braceCount--;
        currentPos++;
      }

      if (braceCount == 0) {
        String materialBlock = "material = " + remaining.substring(0, currentPos);
        return parseMaterialContent(materialBlock);
      }
    } catch (Exception e) {
      Log.e(TAG, "Material extraction error: " + e.getMessage(), e);
    }
    return null;
  }

  private Material parseMaterialContent(String content) {
    try {
      String cleanContent = content.replace("material = ", "").trim();
      String[] lines = cleanContent.split("\n");
      if (lines.length == 0) return new AmberMaterial();

      String firstLine = lines[0].trim();
      String materialType = firstLine.split(" ")[0];

      if (materialType.endsWith("Material")) {
        materialType = materialType.substring(0, materialType.length() - "Material".length());
      }

      return Utilities.createMaterialFromText(materialType, cleanContent);

    } catch (Exception e) {
      Log.e(TAG, "Material parse error: " + e.getMessage(), e);
    }

    return new AmberMaterial();
  }

  // =====================================================================
  // BUILD & RENDER
  // =====================================================================

  private void buildScene() {
    sceneLights.addAll(lights.values());
    sceneShapes.addAll(shapes.values());

    Log.d(
        TAG,
        "Scene built with " + sceneShapes.size() + " shapes and " + sceneLights.size() + " lights");

    for (EMShape shape : sceneShapes) {
      Log.d(TAG, "Scene shape: " + shape.getClass().getSimpleName());
    }
  }

  private Bitmap render() {
    // Render is aborted when the requested size is invalid or exceeds the
    // configured limits. Returning null lets the caller surface a Toast.
    if (width <= 0 || height <= 0) {
      Log.e(TAG, "Render aborted: invalid size " + width + "x" + height);
      lastRenderTimeMs = 0L;
      return null;
    }

    try {
      long startTime = System.currentTimeMillis();

      Scene scene = new Scene();

      Log.d(TAG, "Adding " + sceneShapes.size() + " shapes to scene");
      for (EMShape shape : sceneShapes) {
        scene.addShape(shape);
        Log.d(TAG, "Added to scene: " + shape.getClass().getSimpleName());
      }

      Log.d(TAG, "Adding " + sceneLights.size() + " lights to scene");
      for (Light light : sceneLights) {
        scene.addLight(light);
      }

      MuratRayTracer tracer = new MuratRayTracer(scene, width, height, bgColor);
      tracer.setCamera(camera);

      Log.d(TAG, "Starting render...");
      Bitmap result = tracer.render();

      lastRenderTimeMs = System.currentTimeMillis() - startTime;
      Log.d(TAG, "Render completed in " + lastRenderTimeMs + " ms");

      return result;
    } catch (Exception e) {
      Log.e(TAG, "Render error: " + e.getMessage(), e);
      lastRenderTimeMs = 0L;
      return null;
    }
  }

  // =====================================================================
  // GETTERS
  // =====================================================================

  public Camera getCamera() {
    return camera;
  }

  public int getBackgroundColor() {
    return bgColor;
  }

  public int getWidth() {
    return width;
  }

  public int getHeight() {
    return height;
  }

  public java.util.Map<String, EMShape> getShapes() {
    return shapes;
  }

  public java.util.Map<String, Light> getLights() {
    return lights;
  }

  public java.util.List<EMShape> getSceneShapes() {
    return sceneShapes;
  }

  public java.util.List<Light> getSceneLights() {
    return sceneLights;
  }

  /** Returns the elapsed time of the last render in milliseconds, or 0 if none. */
  public long getLastRenderTimeMs() {
    return lastRenderTimeMs;
  }
}
