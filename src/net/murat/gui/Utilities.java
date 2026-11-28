package net.murat.gui;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

import net.murat.light.*;
import net.murat.lovert.*;
import net.murat.material.*;
import net.murat.math.*;
import net.murat.shape.*;
import net.murat.tool.*;
import net.murat.util.*;

public class Utilities extends Object {

  private static final String TAG = "RayTracerUtilities";
  //public static final String ARABIC = "AR:::";
  //public static final String LATIN = "LT:::";

  /** Base directory for loading textures: Download/carets/raytracer */
  private static File baseDir = null;

  public static void setBaseDir(File dir) {
    baseDir = dir;
  }

  public static File getBaseDir() {
    return baseDir;
  }

  protected static java.util.List<EMShape> shapes = new ArrayList<EMShape>();
  protected static java.util.List<Light> lights = new ArrayList<Light>();
  protected static java.util.List<Overlay> textInfos = new ArrayList<Overlay>();

  /** Shape types available in the scene - only from the file list */
  public static final String[] SHAPE_TYPES = {
    "Plane",
    "Sphere",
    "Box",
    "Disc",
    "Cone",
    "Cylinder",
    "EmojiBillboard",
    "Torus",
    "TorusKnot",
    "Triangle",
    "UnionCSG",
    "IntersectionCSG",
    "DifferenceCSG",
    "Custom"
  };

  /** Material types available in the scene - only from the file list */
  public static final String[] MATERIAL_TYPES = {
    "Checkerboard",
    "Lambert",
    "Amber",
    "Raja",
    "Dielectric",
    "FilteredDielectric",
    "Texture",
    "TransparentPNG",
    "ImageTexture",
    "Diamond",
    "Generated",
    "Rainbow",
    "AfricanKente",
    "AnodizedMetal",
    "AuroraCeramic",
    "Baklava",
    "BrunostCheese",
    "CalligraphyRune",
    "CarpetTexture",
    "CeramicTile",
    "Cini",
    "CircleTexture",
    "CoffeeFjord",
    "ColorDiffuse",
    "ColoredGlass",
    "DamaskCeramic",
    "DielectricText",
    "Diffuse",
    "FractalBark",
    "FractalFire",
    "HamamSauna",
    "HexagonalHoneycomb",
    "Hokusai",
    "HotCopper",
    "IsotropicMetalText",
    "KilimRosemaling",
    "LavaFlow",
    "Marble",
    "Metallic",
    "MoonSurface",
    "Mosaic",
    "Nazar",
    "NordicWeave",
    "NordicWood",
    "NorwegianRose",
    "OpticalIllusion",
    "Orbital",
    "Phong",
    "PhongText",
    "PixelArt",
    "Random",
    "RectangleChecker",
    "RuneStone",
    "Salmon",
    "Simit",
    "SphereWordTexture",
    "StarryNight",
    "Striped",
    "TelemarkPattern",
    "TextDielectric",
    "TexturedCheckerboard",
    "TexturedPhong",
    "Translucent",
    "TransparentEmoji",
    "Triangle",
    "TulipFjord",
    "TurkishTile",
    "VikingMetal",
    "VikingRune",
    "Wave",
    "WoodGrain",
    "Word",
    "XRay",
    "Custom"
  };

  /** Light types available in the scene - only from the file list */
  public static final String[] LIGHT_TYPES = {
    "MuratAmbient", "MuratPoint",
    "MuratDirectional", "Spot",
    "Area", "Sphere",
    "Tube", "Bioluminescent",
    "BlackHole", "Fractal",
    "PulsatingPoint", "Projector",
    "Custom"
  };

  /**
   * public static final String VAL_SHAPE_STR = "<html><body><font color=\"#00DD00\"
   * size=\"5\"><b>Enter anim incdec transform vals in this" + " format: [tx ty tz]*[rx ry rz]*[sx
   * sy sz]:[tx ty tz]*[rx ry rz]*[sx sy" + " sz]</b></font></body></html>"; public static final
   * String VAL_SHAPE_STR_TR = "<html><body><font color=\"#00DD00\" size=\"5\"><b>Enter transform
   * vals in this format: [tx" + " ty tz]*[rx ry rz]*[sx sy sz]</b></font></body></html>"; public
   * static final String USER_INPUT = "User input"; public static final String DEFFADES =
   * "0xFF0000FF:0.03:0.04"; public static final String FADEVALS = "<html><body><font
   * size=\"5\">baseColor:fade:temp (Ex," + " 0xFF0000FF:0.03:0.04)</font></body></html>"; public
   * static final String ENTERCOLOR = "<html><body><font color=\"#0000DD\" size=\"5\">Enter Color:
   * HexARGB or" + " Float|IntRGBAcomma</font></body></html>"; public static final String ENTERBIAS
   * = "<html><body><font color=\"#0000DD\" size=\"5\">Enter Shadow BIAS" + "
   * double:</font></body></html>"; public static final String ENTERVISIBILITY = "<html><body><font
   * color=\"#0000DD\" size=\"5\">Enter" + " Visibility0/VisibilitySpecial1(0|1:e/y/v/t visible,<br>
   * otherwise" + " invisible:</font></body></html>"; public static final String ENTERINTENSITY =
   * "<html><body><font color=\"red\" size=\"5\">Enter new intensity double" + "
   * value:</font></body></html>"; public static final String INTPREVAL = "1.0"; public static final
   * String ENTERSHADOWONLY = "<html><body><font color=\"#0000DD\" size=\"5\">Enter
   * ShadowOnly(e/y/v/t onlyShadow," + " otherwise notOnlyShadow:</font></body></html>"; public
   * static final String ENTERSHADOWENABLE = "<html><body><font color=\"#0000DD\" size=\"5\">Enter
   * ShadowEnable(e/y/v/t enabled, otherwise" + " notEnabled:</font></body></html>"; public static
   * final String ENTERREFR = "<html><body><font color=\"#0000DD\" size=\"5\">Enter
   * Reflectivity0/Refractivity1(e/y/v/t" + " yes, otherwise not:
   *
   * <p>Example: 0:e
   *
   * <p>Example: 1:n</font></body></html>"; public static final String ENTEROTHERANIMS =
   * "<html><body><font color=\"red\" size=\"5\">Enter info of each frame." + "
   *
   * <p>v=visibile
   *
   * <p>e=shadowEnable
   *
   * <p>o=shadowOnly
   *
   * <p>f=reflective
   *
   * <p>r=refractive
   *
   * <p>
   *
   * <p>Example:" + " veofr,00000,veofr</font></body></html>";
   */
  private static final int CHECKER_COLOR1 = 0xFFFF0000;

  private static final int CHECKER_COLOR2 = 0xFFFFFFFF;

  /** Default constructor */
  public Utilities() {
    super();
  }

  /**
   * Checks if a line contains material definition
   *
   * @param line the line to check
   * @return true if material line
   */
  public static boolean isMaterialLine(String line) {
    line = line.toLowerCase();
    return line.contains("material")
        && !line.contains("renderer settings")
        && !line.contains("camera");
  }

  /**
   * Checks if a line contains shape definition
   *
   * @param line the line to check
   * @return true if shape line
   */
  public static boolean isShapeLine(String line) {
    String cleanLine = line;
    if (cleanLine.startsWith("Mesh")) return true;

    for (String type : SHAPE_TYPES) {
      if (cleanLine.startsWith(type)) return true;
    }
    return false;
  }

  public static boolean isOverlayLine(String line) {
    String cleanLine = line.trim();
    if (cleanLine.startsWith("Overlay")) {
      return true;
    }

    return false;
  }

  /**
   * Checks if a line contains light definition
   *
   * @param line the line to check
   * @return true if light line
   */
  public static boolean isLightLine(String line) {
    String cleanLine = line.trim();
    for (String type : LIGHT_TYPES) {
      if (cleanLine.contains(type) && cleanLine.endsWith("{")) return true;
    }
    return false;
  }

  /**
   * Extracts shape name from shape description
   *
   * @param shapeDesc the shape description string
   * @return the shape name
   */
  public static String getShapeName(String shapeDesc) {
    String[] lines = shapeDesc.split("\n");
    if (lines.length > 0) {
      String firstLine = lines[0].trim();
      String[] parts = firstLine.split("\\s+");
      if (parts.length >= 2) {
        return parts[1];
      }
    }
    return "unknown_shape";
  }

  /**
   * Gets a shortened version of a string for display
   *
   * @param old the original string
   * @return shortened string
   */
  public static String getShortName(String old) {
    int index = old.indexOf("{");
    if (index < 0) return old;

    final int oldlen = old.length();
    if (oldlen <= 96) {
      return (old.replaceAll("\n", " "));
    }
    if (oldlen > 96) {
      return (old.substring(0, 96).replaceAll("\n", " "));
    }

    String shpName = old.substring(0, index);
    shpName = shpName.trim();

    return shpName;
  }

  public static ImageCoordinates createImageCoordinates(String val) {
    String value = val.trim();
    value = value.replace("\"", "");
    value = value.replace(" ", "");

    String[] values = value.split(",");
    if (values == null) return null;
    if (values.length < 5) return null;

    String ipath = values[0];
    int xi = 0;
    int yi = 0;
    int wi = 1;
    int hi = 1;

    try {
      xi = Integer.parseInt(values[1]);
      yi = Integer.parseInt(values[2]);
      wi = Integer.parseInt(values[3]);
      hi = Integer.parseInt(values[4]);
    } catch (NumberFormatException nfe) {
      Log.e(TAG, "createImageCoordinates parse error", nfe);
      return null;
    }

    ImageCoordinates icoord = new ImageCoordinates(ipath, xi, yi, wi, hi);
    return icoord;
  }

  /**
   * Extracts name from material or shape definition
   *
   * @param oldstr the definition string
   * @return the name
   */
  public static final String getNameFromMaterialShape(String oldstr) {
    int index = oldstr.indexOf("{");
    if (index < 0) return "Unknown";

    String str = oldstr.substring(0, index);
    str = str.trim();
    str = str.replaceAll("  ", " ");
    String[] split = str.split(" ");
    if (split == null) return "Unknown";
    if (split.length < 2) return "Unknown";

    return split[1].trim();
  }

  /**
   * Parses XYZ transform string for animation
   *
   * @param input the input string
   * @return array of two transform strings
   */
  public static final String[] parseXYZTransform(String input) {
    if (input == null || input.trim().isEmpty()) {
      return new String[] {"", ""};
    }

    String[] parts = input.split(":");
    if (parts.length != 2) {
      return new String[] {"", ""};
    }

    String firstPart = parts[0].trim();
    String secondPart = parts[1].trim();

    secondPart = secondPart.replace(";", "").trim();

    String firstTransform = formatTransform(firstPart, "first");
    String secondTransform = formatTransform(secondPart, "second");

    return new String[] {firstTransform, secondTransform};
  }

  /**
   * Parses single XYZ transform string
   *
   * @param input the input string
   * @return formatted transform string
   */
  public static final String parseSingleXYZTransform(String input) {
    if (input == null || input.trim().isEmpty()) {
      return "nullTransform";
    }

    input = input.replace(";", "").trim();

    String fTransform = formatTransform(input, "Shape transform");

    return fTransform;
  }

  /**
   * Formats transform data into readable string
   *
   * @param transformData the transform data
   * @param partName the part name for error messages
   * @return formatted transform string
   */
  private static final String formatTransform(String transformData, String partName) {
    if (transformData.isEmpty()) {
      return "";
    }

    try {
      String cleanData = transformData.replace("[", "").replace("]", "").replace("*", " ");

      String[] values = cleanData.trim().split("\\s+");

      if (values.length != 9) {
        return "// Error: " + partName + " must be 9 values.";
      }

      double tx = Utilities.parseDouble(values[0]);
      double ty = Utilities.parseDouble(values[1]);
      double tz = Utilities.parseDouble(values[2]);
      double rx = Utilities.parseDouble(values[3]);
      double ry = Utilities.parseDouble(values[4]);
      double rz = Utilities.parseDouble(values[5]);
      double sx = Utilities.parseDouble(values[6]);
      double sy = Utilities.parseDouble(values[7]);
      double sz = Utilities.parseDouble(values[8]);

      return String.format(
          java.util.Locale.US,
          "transform = translate(%.3f, %.3f, %.3f) * rotate(%.3f, %.3f, %.3f) * scale(%.3f, %.3f,"
              + " %.3f)",
          tx,
          ty,
          tz,
          rx,
          ry,
          rz,
          sx,
          sy,
          sz);

    } catch (NumberFormatException e) {
      return e.getMessage();
    } catch (Exception e) {
      return e.getMessage();
    }
  }

  // ===== PARSE METHODS =====

  /**
   * Parses a double value from a block
   *
   * @param block the block to parse
   * @param key the key to search for
   * @return the double value
   */
  public static double parseDouble(String block, String key) {
    String[] lines = block.split("\n");
    for (String line : lines) {
      if (line.contains(key + " =")) {
        String val = line.split("=")[1].split(";")[0].split("//")[0].trim();
        return Utilities.parseDouble(val);
      }
    }
    return 0.0;
  }

  // ============================================================
  // OPTIONAL PROPERTY HELPERS
  // Return null / the given default when the line is missing or malformed, so scene files
  // that do not contain the new TextDielectric properties keep loading exactly as before.
  // ============================================================

  /** Returns the raw value of "key = value;" (quotes stripped), or null if the key is absent. */
  private static String optValue(String text, String key) {
    if (text == null || key == null) return null;
    java.util.regex.Matcher m =
        java.util.regex.Pattern.compile(
                "(?<![A-Za-z0-9_])"
                    + java.util.regex.Pattern.quote(key)
                    + "\\s*=\\s*([^;\\r\\n]+?)\\s*;")
            .matcher(text);
    if (!m.find()) return null;

    String val = m.group(1).trim();
    if (val.length() >= 2 && val.startsWith("\"") && val.endsWith("\"")) {
      val = val.substring(1, val.length() - 1);
    }
    return val;
  }

  /** Returns the double value of the key, or null if the key is missing or not a number. */
  private static Double optDouble(String text, String key) {
    String val = optValue(text, key);
    if (val == null) return null;
    try {
      return Double.valueOf(val);
    } catch (NumberFormatException e) {
      return null;
    }
  }

  /** Returns true/false for "true"/"false" (case-insensitive), or def if missing. */
  private static boolean optBoolean(String text, String key, boolean def) {
    String val = optValue(text, key);
    if (val == null) return def;
    return "true".equalsIgnoreCase(val);
  }
  
  /**
   * Parses an int value from a block
   *
   * @param block the block to parse
   * @param key the key to search for
   * @return the int value
   */
  public static int parseInt(String block, String key) {
    String[] lines = block.split("\n");
    for (String line : lines) {
      if (line.contains(key + " =")) {
        String val = line.split("=")[1].split(";")[0].split("//")[0].trim();
        return Integer.parseInt(val);
      }
    }
    return 0;
  }

  /**
   * Parses a boolean value from a block
   *
   * @param block the block to parse
   * @param key the key to search for
   * @return the boolean value
   */
  public static boolean parseBoolean(String block, String key) {
    String[] lines = block.split("\n");
    for (String line : lines) {
      if (line.contains(key + " =")) {
        String val = line.split("=")[1].split(";")[0].split("//")[0].trim();
        return Boolean.parseBoolean(val);
      }
    }
    return false;
  }

  /**
   * Parses a boolean value from a string
   *
   * @param block the string to parse
   * @return the boolean value
   */
  public static boolean parseBoolean(String block) {
    String val = block.replace("\"", "").replace("'", "");
    return Boolean.parseBoolean(val);
  }

  /**
   * Parses a string value from a block
   *
   * @param block the block to parse
   * @param key the key to search for
   * @return the string value
   */
   public static String parseString(String block, String key) {
     String[] lines = block.split("\n");
     String marker = key + " =";
     for (String line : lines) {
      int at = line.indexOf(marker);
      if (at >= 0) {
        String val = line.substring(at + marker.length()).split(";")[0].split("//")[0].trim();

        // Remove exactly one quote from each edge (the syntax quotes).
        //val = LatinTool.stripEdgeQuotes(val);

        // Separate any remaining literal quotes from the text so the prefix can be detected.
        //String[] q = LatinTool.splitQuoted(val);
        //String core = q[1];

        //if (LatinTool.startsWithIgnoreCase(core, ARABIC)) {
        //  val = q[0] + ArabicTool.toArabic(core.substring(ARABIC.length())) + q[2];
        //} else if (LatinTool.startsWithIgnoreCase(core, LATIN)) {
        //  val = q[0] + LatinTool.toLatin(core.substring(LATIN.length())) + q[2];
        //}
        //return LatinTool.emojied(val);
        return LatinTool.adjustedText(val);
      }
    }
    return "";
  }

  /**
   * Parses a Point3 from a block
   *
   * @param block the block to parse
   * @param key the key to search for
   * @return the Point3
   */
  public static Point3 parsePoint3(String block, String key) {
    String[] lines = block.split("\n");
    for (String line : lines) {
      if (line.contains(key + " =")) {
        String val = line.split("=")[1].split(";")[0].split("//")[0].trim();
        if (val.startsWith("P(") && val.endsWith(")")) {
          val = val.substring(2, val.length() - 1);
        }
        return parsePoint3(val);
      }
    }
    return new Point3(0, 0, 0);
  }

  /**
   * Parses a Point3 from a string
   *
   * @param s the string to parse
   * @return the Point3
   */
  public static Point3 parsePoint3(String s) {
    try {
      String[] p = s.split(",");
      if (p.length == 3) {
        return new Point3(
            Utilities.parseDouble(p[0].trim()),
            Utilities.parseDouble(p[1].trim()),
            Utilities.parseDouble(p[2].trim()));
      }
    } catch (Exception e) {
      Log.e(TAG, "parsePoint3 error", e);
    }
    return new Point3(0, 0, 0);
  }

  /**
   * Parses a list of Point3 from a block
   *
   * @param block the block to parse
   * @param key the key to search for
   * @return the list of Point3
   */
  private static java.util.List<Point3> parsePoints3(String block, String key) {
    String[] lines = block.split("\n");
    for (String line : lines) {
      if (line.contains(key + " =")) {
        String val = line.split("=")[1].split(";")[0].split("//")[0].trim();
        java.util.List<Point3> points = new ArrayList<Point3>();

        if (val.startsWith("[") && val.endsWith("]")) {
          val = val.substring(1, val.length() - 1);
        }

        String[] pointStrs = val.split("-");
        for (String pointStr : pointStrs) {
          pointStr = pointStr.trim();
          if (pointStr.startsWith("P(") && pointStr.endsWith(")")) {
            pointStr = pointStr.substring(2, pointStr.length() - 1);
          }
          points.add(parsePoint3(pointStr));
        }
        return points;
      }
    }
    return java.util.Arrays.asList(new Point3(0, 0, 0));
  }

  /**
   * Parses a Vector3 from a block
   *
   * @param block the block to parse
   * @param key the key to search for
   * @return the Vector3
   */
  public static Vector3 parseVector3(String block, String key) {
    String[] lines = block.split("\n");
    for (String line : lines) {
      if (line.contains(key + " =")) {
        String val = line.split("=")[1].split(";")[0].split("//")[0].trim();
        if (val.startsWith("V(") && val.endsWith(")")) {
          val = val.substring(2, val.length() - 1);
        }
        return parseVector3(val);
      }
    }
    return new Vector3(0, 0, 0);
  }

  /**
   * Parses a Vector3 from a string
   *
   * @param s the string to parse
   * @return the Vector3
   */
  public static Vector3 parseVector3(String s) {
    try {
      String[] v = s.split(",");
      if (v.length == 3) {
        return new Vector3(
            Utilities.parseDouble(v[0].trim()),
            Utilities.parseDouble(v[1].trim()),
            Utilities.parseDouble(v[2].trim()));
      }
    } catch (Exception e) {
      Log.e(TAG, "parseVector3 error", e);
    }
    return new Vector3(0, 0, 0);
  }

  /**
   * Parses a color as int (0xAARRGGBB) from a block
   *
   * @param block the block to parse
   * @param key the key to search for
   * @return the color as int
   */
  public static int parseColor(String block, String key) {
    String[] lines = block.split("\n");
    for (String line : lines) {
      if (line.contains(key + " =")) {
        String val = line.split("=")[1].split(";")[0].split("//")[0].trim();
        return parseColor(val);
      }
    }
    return 0xFFFFFFFF;
  }

  /**
   * Parses a color as int (0xAARRGGBB) from a string
   *
   * @param colorStr the color string
   * @return the color as int
   */
  public static int parseColor(String colorStr) {
    if (colorStr == null || colorStr.isEmpty()) {
      return 0xFF000000;
    }

    colorStr = colorStr.trim();

    try {
      // Hex format - 0xAARRGGBB or #AARRGGBB
      if (colorStr.startsWith("0x") || colorStr.startsWith("0X")) {
        return (int) Long.parseLong(colorStr.substring(2), 16);
      }
      if (colorStr.startsWith("#")) {
        String hex = colorStr.substring(1);
        if (hex.length() == 8) {
          return (int) Long.parseLong(hex, 16);
        } else if (hex.length() == 6) {
          return 0xFF000000 | Integer.parseInt(hex, 16);
        } else if (hex.length() == 4) {
          // #ARGB -> #AARRGGBB
          String a = hex.substring(0, 1) + hex.substring(0, 1);
          String r = hex.substring(1, 2) + hex.substring(1, 2);
          String g = hex.substring(2, 3) + hex.substring(2, 3);
          String b = hex.substring(3, 4) + hex.substring(3, 4);
          return (int) Long.parseLong(a + r + g + b, 16);
        } else if (hex.length() == 3) {
          // #RGB -> #RRGGBB
          String r = hex.substring(0, 1) + hex.substring(0, 1);
          String g = hex.substring(1, 2) + hex.substring(1, 2);
          String b = hex.substring(2, 3) + hex.substring(2, 3);
          return 0xFF000000 | Integer.parseInt(r + g + b, 16);
        }
      }

      // Integer format: 255,0,0 or 255,0,0,255
      if (colorStr.contains(",") && !colorStr.contains("f")) {
        String[] parts = colorStr.split(",");
        for (int i = 0; i < parts.length; i++) {
          parts[i] = parts[i].trim();
        }
        if (parts.length == 3) {
          int r = Integer.parseInt(parts[0]);
          int g = Integer.parseInt(parts[1]);
          int b = Integer.parseInt(parts[2]);
          return 0xFF000000 | (r << 16) | (g << 8) | b;
        } else if (parts.length == 4) {
          int r = Integer.parseInt(parts[0]);
          int g = Integer.parseInt(parts[1]);
          int b = Integer.parseInt(parts[2]);
          int a = Integer.parseInt(parts[3]);
          return (a << 24) | (r << 16) | (g << 8) | b;
        }
      }

      // Named colors
      return parseNamedColor(colorStr);

    } catch (Exception e) {
      Log.e(TAG, "Error parsing color: " + colorStr, e);
      return 0xFFFFFFFF;
    }
  }

  /**
   * Parses a named color
   *
   * @param name the color name
   * @return the color as int
   */
  private static int parseNamedColor(String name) {
    String lower = name.toLowerCase();
    if ("black".equals(lower)) return 0xFF000000;
    if ("white".equals(lower)) return 0xFFFFFFFF;
    if ("red".equals(lower)) return 0xFFFF0000;
    if ("green".equals(lower)) return 0xFF00FF00;
    if ("blue".equals(lower)) return 0xFF0000FF;
    if ("yellow".equals(lower)) return 0xFFFFFF00;
    if ("cyan".equals(lower)) return 0xFF00FFFF;
    if ("magenta".equals(lower)) return 0xFFFF00FF;
    if ("orange".equals(lower)) return 0xFFFFA500;
    if ("pink".equals(lower)) return 0xFFFFC0CB;
    if ("gray".equals(lower)) return 0xFF808080;
    if ("darkgray".equals(lower)) return 0xFF404040;
    if ("lightgray".equals(lower)) return 0xFFD3D3D3;
    return 0xFFFFFFFF;
  }

  /**
   * Parses a Matrix4 animation transform from a block
   *
   * @param block the block to parse
   * @param key the key to search for
   * @return the Matrix4
   */
  public static Matrix4 parseAnimationTransform(String block, String key) {
    String[] lines = block.split("\n");
    for (String line : lines) {
      if (line.contains(key + " =")) {
        String val = line.split("=")[1].split(";")[0].split("//")[0].trim();
        val = val.replace("\"", "").replace("'", "");
        return Matrix4.createMatrixFromString(val);
      }
    }
    return new Matrix4();
  }

  /**
   * Extracts a value from a block
   *
   * @param block the block to parse
   * @param key the key to search for
   * @return the value string
   */
  public static String extractValue(String block, String key) {
    String[] lines = block.split("\n");
    for (String line : lines) {
      if (line.contains(key + " =")) {
        String value = line.split("=", 2)[1].trim();
        if (value.contains("//")) {
          value = value.split("//")[0].trim();
        }
        return value;
      }
    }
    return null;
  }

  /**
   * Loads an image from a relative path under baseDir. Android version: uses BitmapFactory instead
   * of ImageIO.
   */
  private static Bitmap loadImage(String path) {
    if (path == null || path.trim().isEmpty()) {
      return null;
    }
    String cleanPath = path.trim().replace("\"", "").replace("'", "");
    if ("null".equals(cleanPath)) return null;

    if (baseDir == null) {
      Log.e(TAG, "loadImage: baseDir not set!");
      return null;
    }

    File f = new File(baseDir, cleanPath);
    if (f.exists()) {
      Bitmap bmp = BitmapFactory.decodeFile(f.getAbsolutePath());
      if (bmp != null) return bmp;
    }

    // Fallback: try just the filename inside baseDir/textures/
    File fallback = new File(new File(baseDir, "textures"), new File(cleanPath).getName());
    if (fallback.exists()) {
      Bitmap bmp = BitmapFactory.decodeFile(fallback.getAbsolutePath());
      if (bmp != null) return bmp;
    }

    Log.w(TAG, "Texture not found: " + f.getAbsolutePath());
    return null;
  }

  /**
   * Creates a date string
   *
   * @return the date string
   */
  protected static String createDateString() {
    java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("ddMMyy", java.util.Locale.US);
    return sdf.format(new java.util.Date());
  }

  /**
   * Gets shape template for a given type
   *
   * @param type the shape type
   * @return template string
   */
  public static String getShapeTemplate(String type) {
    if ("Box".equals(type)) {
      return "name = box;\n"
          + "width = 2.0;\n"
          + "height = 2.0;\n"
          + "depth = 2.0;\n"
          + "shadowColor = 0xFF000000;\n"
          + "shadowBias = 0.001;\n\n"
          + "transform = translate(0, 0, 0) * rotate(0, 0, 0) * scale(1, 1, 1);\n\n"
          + "firstAnimMatrix = translate(0, 0, 0) * rotate(0, 0, 0) * scale(1, 1, 1);\n"
          + "secondAnimMatrix = translate(0, 0, 0) * rotate(0, 0, 0) * scale(1, 1, 1);\n"
          + "otherAnimationInfo = ve0fr;\n\n"
          + "isVisibleSpecial = true;\n"
          + "isVisible = true;\n"
          + "isShadowOnly = false;\n"
          + "isShadowEnable = true;\n"
          + "isReflective = true;\n"
          + "isRefractive = true;";
    }
    if ("Cone".equals(type)) {
      return "name = cone;\n"
          + "radius = 1.0;\n"
          + "height = 2.0;\n"
          + "shadowColor = 0xFF000000;\n"
          + "shadowBias = 0.001;\n\n"
          + "transform = translate(0, 0, 0) * rotate(0, 0, 0) * scale(1, 1, 1);\n\n"
          + "firstAnimMatrix = translate(0, 0, 0) * rotate(0, 0, 0) * scale(1, 1, 1);\n"
          + "secondAnimMatrix = translate(0, 0, 0) * rotate(0, 0, 0) * scale(1, 1, 1);\n"
          + "otherAnimationInfo = ve0fr;\n\n"
          + "isVisibleSpecial = true;\n"
          + "isVisible = true;\n"
          + "isShadowOnly = false;\n"
          + "isShadowEnable = true;\n"
          + "isReflective = true;\n"
          + "isRefractive = true;";
    }
    if ("Cylinder".equals(type)) {
      return "name = cylinder;\n"
          + "radius = 1.0;\n"
          + "height = 2.0;\n"
          + "shadowColor = 0xFF000000;\n"
          + "shadowBias = 0.001;\n\n"
          + "transform = translate(0, 0, 0) * rotate(0, 0, 0) * scale(1, 1, 1);\n\n"
          + "firstAnimMatrix = translate(0, 0, 0) * rotate(0, 0, 0) * scale(1, 1, 1);\n"
          + "secondAnimMatrix = translate(0, 0, 0) * rotate(0, 0, 0) * scale(1, 1, 1);\n"
          + "otherAnimationInfo = ve0fr;\n\n"
          + "isVisibleSpecial = true;\n"
          + "isVisible = true;\n"
          + "isShadowOnly = false;\n"
          + "isShadowEnable = true;\n"
          + "isReflective = true;\n"
          + "isRefractive = true;";
    }
    if ("Disc".equals(type)) {
      return "name = disc;\n"
          + "radius = 1.0;\n"
          + "shadowColor = 0xFF000000;\n"
          + "shadowBias = 0.001;\n\n"
          + "transform = translate(0, 0, 0) * rotate(0, 0, 0) * scale(1, 1, 1);\n\n"
          + "firstAnimMatrix = translate(0, 0, 0) * rotate(0, 0, 0) * scale(1, 1, 1);\n"
          + "secondAnimMatrix = translate(0, 0, 0) * rotate(0, 0, 0) * scale(1, 1, 1);\n"
          + "otherAnimationInfo = ve0fr;\n\n"
          + "isVisibleSpecial = true;\n"
          + "isVisible = true;\n"
          + "isShadowOnly = false;\n"
          + "isShadowEnable = true;\n"
          + "isReflective = true;\n"
          + "isRefractive = true;";
    }
    if ("EmojiBillboard".equals(type)) {
      return "name = emojibillboard;\n"
          + "width = 20.0;\n"
          + "height = 10.0;\n"
          + "isRectangle = false;\n"
          + "imagePath = \"textures/turkeyFlag.png\";\n"
          + "shadowColor = 0xFF000000;\n"
          + "shadowBias = 0.001;\n\n"
          + "transform = translate(0, 2, -8) * rotate(0, 0, 0) * scale(1, 1, 1);\n\n"
          + "firstAnimMatrix = translate(0, 0, 0) * rotate(0, 0, 0) * scale(1, 1, 1);\n"
          + "secondAnimMatrix = translate(0, 0, 0) * rotate(0, 0, 0) * scale(1, 1, 1);\n"
          + "otherAnimationInfo = ve0fr;\n\n"
          + "isVisibleSpecial = false;\n"
          + "isVisible = true;\n"
          + "isShadowOnly = false;\n"
          + "isShadowEnable = true;\n"
          + "isReflective = true;\n"
          + "isRefractive = true;";
    }
    if ("Plane".equals(type)) {
      return "name = plane;\n"
          + "pointOnPlane = P(0,0,0);\n"
          + "normal = V(0,1,0);\n"
          + "shadowColor = 0xFF000000;\n"
          + "shadowBias = 0.001;\n\n"
          + "transform = translate(0, -1.5, 0) * rotate(0, 0, 0) * scale(1, 1, 1);\n\n"
          + "firstAnimMatrix = translate(0, 0, 0) * rotate(0, 0, 0) * scale(1, 1, 1);\n"
          + "secondAnimMatrix = translate(0, 0, 0) * rotate(0, 0, 0) * scale(1, 1, 1);\n"
          + "otherAnimationInfo = ve0fr;\n\n"
          + "isVisibleSpecial = true;\n"
          + "isVisible = true;\n"
          + "isShadowOnly = false;\n"
          + "isShadowEnable = false;\n"
          + "isReflective = true;\n"
          + "isRefractive = true;";
    }
    if ("Sphere".equals(type)) {
      return "name = sphere;\n"
          + "radius = 2.0;\n"
          + "shadowColor = 0xFF000000;\n"
          + "shadowBias = 0.001;\n\n"
          + "transform = translate(0, 0.5, 0) * rotate(0, 0, 0) * scale(1, 1, 1);\n\n"
          + "firstAnimMatrix = translate(0, 0, 0) * rotate(0, 0, 0) * scale(1, 1, 1);\n"
          + "secondAnimMatrix = translate(0, 0, 0) * rotate(0, 0, 0) * scale(1, 1, 1);\n"
          + "otherAnimationInfo = ve0fr;\n\n"
          + "isVisibleSpecial = true;\n"
          + "isVisible = true;\n"
          + "isShadowOnly = false;\n"
          + "isShadowEnable = true;\n"
          + "isReflective = true;\n"
          + "isRefractive = true;";
    }
    if ("Torus".equals(type)) {
      return "name = torus;\n"
          + "majorRadius = 2.0;\n"
          + "minorRadius = 0.5;\n"
          + "shadowColor = 0xFF000000;\n"
          + "shadowBias = 0.001;\n\n"
          + "transform = translate(0, 0, 0) * rotate(0, 0, 0) * scale(1, 1, 1);\n\n"
          + "firstAnimMatrix = translate(0, 0, 0) * rotate(0, 0, 0) * scale(1, 1, 1);\n"
          + "secondAnimMatrix = translate(0, 0, 0) * rotate(0, 0, 0) * scale(1, 1, 1);\n"
          + "otherAnimationInfo = ve0fr;\n\n"
          + "isVisibleSpecial = true;\n"
          + "isVisible = true;\n"
          + "isShadowOnly = false;\n"
          + "isShadowEnable = true;\n"
          + "isReflective = true;\n"
          + "isRefractive = true;";
    }
    if ("TorusKnot".equals(type)) {
      return "name = torusknot;\n"
          + "R = 2.0;\n"
          + "r = 0.5;\n"
          + "p = 2;\n"
          + "q = 3;\n"
          + "shadowColor = 0xFF000000;\n"
          + "shadowBias = 0.001;\n\n"
          + "transform = translate(0, 0, 0) * rotate(0, 0, 0) * scale(1, 1, 1);\n\n"
          + "firstAnimMatrix = translate(0, 0, 0) * rotate(0, 0, 0) * scale(1, 1, 1);\n"
          + "secondAnimMatrix = translate(0, 0, 0) * rotate(0, 0, 0) * scale(1, 1, 1);\n"
          + "otherAnimationInfo = ve0fr;\n\n"
          + "isVisibleSpecial = true;\n"
          + "isVisible = true;\n"
          + "isShadowOnly = false;\n"
          + "isShadowEnable = true;\n"
          + "isReflective = true;\n"
          + "isRefractive = true;";
    }
    if ("Triangle".equals(type)) {
      return "name = triangle;\n"
          + "v0 = P(-1,0,0);\n"
          + "v1 = P(1,0,0);\n"
          + "v2 = P(0,1,0);\n"
          + "shadowColor = 0xFF000000;\n"
          + "shadowBias = 0.001;\n\n"
          + "transform = translate(0, 0, 0) * rotate(0, 0, 0) * scale(1, 1, 1);\n\n"
          + "firstAnimMatrix = translate(0, 0, 0) * rotate(0, 0, 0) * scale(1, 1, 1);\n"
          + "secondAnimMatrix = translate(0, 0, 0) * rotate(0, 0, 0) * scale(1, 1, 1);\n"
          + "otherAnimationInfo = ve0fr;\n\n"
          + "isVisibleSpecial = true;\n"
          + "isVisible = true;\n"
          + "isShadowOnly = false;\n"
          + "isShadowEnable = true;\n"
          + "isReflective = true;\n"
          + "isRefractive = true;";
    }
    return "";
  }

  /**
   * Creates an EMShape from text definition
   *
   * @param type the shape type
   * @param text the definition text
   * @return the created EMShape
   */
  public static EMShape createShapeFromText(String type, String text) {
    try {
      if ("Box".equals(type)) {
        double w = parseDouble(text, "width");
        double h = parseDouble(text, "height");
        double d = parseDouble(text, "depth");
        EMShape shp = new net.murat.shape.Box(w, h, d);
        Matrix4 xanimTransform = parseAnimationTransform(text, "firstAnimMatrix");
        Matrix4 yanimTransform = parseAnimationTransform(text, "secondAnimMatrix");
        shp.setAnimationTransforms(new Matrix4[] {xanimTransform, yanimTransform});
        int shdwColor = parseColor(text, "shadowColor");
        double shdwBias = parseDouble(text, "shadowBias");
        shp.setShadowColor(shdwColor);
        shp.setShadowBias(shdwBias);
        String otherAnimationInfo = parseString(text, "otherAnimationInfo");
        boolean isVisible = parseBoolean(text, "isVisible");
        boolean isShadowEnable = parseBoolean(text, "isShadowEnable");
        boolean isShadowOnly = parseBoolean(text, "isShadowOnly");
        boolean isReflective = parseBoolean(text, "isReflective");
        boolean isRefractive = parseBoolean(text, "isRefractive");
        shp.setOtherAnimationInfo(otherAnimationInfo);
        shp.setVisible(isVisible);
        shp.setShadowEnable(isShadowEnable);
        shp.setShadowOnly(isShadowOnly);
        shp.setReflective(isReflective);
        shp.setRefractive(isRefractive);
        boolean isVisibleSpecial = parseBoolean(text, "isVisibleSpecial");
        shp.setVisibleSpecial(isVisibleSpecial);
        String name = parseString(text, "name");
        shp.setName(name);
        return shp;
      }
      if ("Cone".equals(type)) {
        double radius = parseDouble(text, "radius");
        double height = parseDouble(text, "height");
        EMShape shp = new Cone(radius, height);
        Matrix4 xanimTransform = parseAnimationTransform(text, "firstAnimMatrix");
        Matrix4 yanimTransform = parseAnimationTransform(text, "secondAnimMatrix");
        shp.setAnimationTransforms(new Matrix4[] {xanimTransform, yanimTransform});
        int shdwColor = parseColor(text, "shadowColor");
        double shdwBias = parseDouble(text, "shadowBias");
        shp.setShadowColor(shdwColor);
        shp.setShadowBias(shdwBias);
        String otherAnimationInfo = parseString(text, "otherAnimationInfo");
        boolean isVisible = parseBoolean(text, "isVisible");
        boolean isShadowEnable = parseBoolean(text, "isShadowEnable");
        boolean isShadowOnly = parseBoolean(text, "isShadowOnly");
        boolean isReflective = parseBoolean(text, "isReflective");
        boolean isRefractive = parseBoolean(text, "isRefractive");
        shp.setOtherAnimationInfo(otherAnimationInfo);
        shp.setVisible(isVisible);
        shp.setShadowEnable(isShadowEnable);
        shp.setShadowOnly(isShadowOnly);
        shp.setReflective(isReflective);
        shp.setRefractive(isRefractive);
        boolean isVisibleSpecial = parseBoolean(text, "isVisibleSpecial");
        shp.setVisibleSpecial(isVisibleSpecial);
        String name = parseString(text, "name");
        shp.setName(name);
        return shp;
      }
      if ("Cylinder".equals(type)) {
        if (text.contains("startPoint")) {
          Point3 start = parsePoint3(text, "startPoint");
          Point3 end = parsePoint3(text, "endPoint");
          double rad = parseDouble(text, "radius");
          double ht = parseDouble(text, "height");
          EMShape shp = new Cylinder(start, end, rad, ht);
          Matrix4 xanimTransform = parseAnimationTransform(text, "firstAnimMatrix");
          Matrix4 yanimTransform = parseAnimationTransform(text, "secondAnimMatrix");
          shp.setAnimationTransforms(new Matrix4[] {xanimTransform, yanimTransform});
          int shdwColor = parseColor(text, "shadowColor");
          double shdwBias = parseDouble(text, "shadowBias");
          shp.setShadowColor(shdwColor);
          shp.setShadowBias(shdwBias);
          String otherAnimationInfo = parseString(text, "otherAnimationInfo");
          boolean isVisible = parseBoolean(text, "isVisible");
          boolean isShadowEnable = parseBoolean(text, "isShadowEnable");
          boolean isShadowOnly = parseBoolean(text, "isShadowOnly");
          boolean isReflective = parseBoolean(text, "isReflective");
          boolean isRefractive = parseBoolean(text, "isRefractive");
          shp.setOtherAnimationInfo(otherAnimationInfo);
          shp.setVisible(isVisible);
          shp.setShadowEnable(isShadowEnable);
          shp.setShadowOnly(isShadowOnly);
          shp.setReflective(isReflective);
          shp.setRefractive(isRefractive);
          boolean isVisibleSpecial = parseBoolean(text, "isVisibleSpecial");
          shp.setVisibleSpecial(isVisibleSpecial);
          String name = parseString(text, "name");
          shp.setName(name);
          return shp;
        } else {
          double rad = parseDouble(text, "radius");
          double ht = parseDouble(text, "height");
          EMShape shp = new Cylinder(rad, ht);
          Matrix4 xanimTransform = parseAnimationTransform(text, "firstAnimMatrix");
          Matrix4 yanimTransform = parseAnimationTransform(text, "secondAnimMatrix");
          shp.setAnimationTransforms(new Matrix4[] {xanimTransform, yanimTransform});
          int shdwColor = parseColor(text, "shadowColor");
          double shdwBias = parseDouble(text, "shadowBias");
          shp.setShadowColor(shdwColor);
          shp.setShadowBias(shdwBias);
          String otherAnimationInfo = parseString(text, "otherAnimationInfo");
          boolean isVisible = parseBoolean(text, "isVisible");
          boolean isShadowEnable = parseBoolean(text, "isShadowEnable");
          boolean isShadowOnly = parseBoolean(text, "isShadowOnly");
          boolean isReflective = parseBoolean(text, "isReflective");
          boolean isRefractive = parseBoolean(text, "isRefractive");
          shp.setOtherAnimationInfo(otherAnimationInfo);
          shp.setVisible(isVisible);
          shp.setShadowEnable(isShadowEnable);
          shp.setShadowOnly(isShadowOnly);
          shp.setReflective(isReflective);
          shp.setRefractive(isRefractive);
          boolean isVisibleSpecial = parseBoolean(text, "isVisibleSpecial");
          shp.setVisibleSpecial(isVisibleSpecial);
          String name = parseString(text, "name");
          shp.setName(name);
          return shp;
        }
      }
      if ("EmojiBillboard".equals(type)) {
        double width = parseDouble(text, "width");
        double height = parseDouble(text, "height");
        boolean isRect = parseBoolean(text, "isRectangle");
        boolean isVisible = parseBoolean(text, "isVisible");
        String path = parseString(text, "imagePath");
        Bitmap img = loadImage(path);
        EMShape shp = new EmojiBillboard(width, height, isRect, isVisible, img);
        ((EmojiBillboard) shp).setImagePath(path);
        Matrix4 xanimTransform = parseAnimationTransform(text, "firstAnimMatrix");
        Matrix4 yanimTransform = parseAnimationTransform(text, "secondAnimMatrix");
        shp.setAnimationTransforms(new Matrix4[] {xanimTransform, yanimTransform});
        int shdwColor = parseColor(text, "shadowColor");
        double shdwBias = parseDouble(text, "shadowBias");
        shp.setShadowColor(shdwColor);
        shp.setShadowBias(shdwBias);
        String otherAnimationInfo = parseString(text, "otherAnimationInfo");
        boolean isShadowEnable = parseBoolean(text, "isShadowEnable");
        boolean isShadowOnly = parseBoolean(text, "isShadowOnly");
        boolean isReflective = parseBoolean(text, "isReflective");
        boolean isRefractive = parseBoolean(text, "isRefractive");
        shp.setOtherAnimationInfo(otherAnimationInfo);
        shp.setVisible(isVisible);
        shp.setShadowEnable(isShadowEnable);
        shp.setShadowOnly(isShadowOnly);
        shp.setReflective(isReflective);
        shp.setRefractive(isRefractive);
        boolean isVisibleSpecial = parseBoolean(text, "isVisibleSpecial");
        shp.setVisibleSpecial(isVisibleSpecial);
        String name = parseString(text, "name");
        shp.setName(name);
        return shp;
      }
      if ("Plane".equals(type)) {
        Point3 point = parsePoint3(text, "pointOnPlane");
        Vector3 normal = parseVector3(text, "normal");
        EMShape shp = new Plane(point, normal);
        Matrix4 xanimTransform = parseAnimationTransform(text, "firstAnimMatrix");
        Matrix4 yanimTransform = parseAnimationTransform(text, "secondAnimMatrix");
        shp.setAnimationTransforms(new Matrix4[] {xanimTransform, yanimTransform});
        int shdwColor = parseColor(text, "shadowColor");
        double shdwBias = parseDouble(text, "shadowBias");
        shp.setShadowColor(shdwColor);
        shp.setShadowBias(shdwBias);
        String otherAnimationInfo = parseString(text, "otherAnimationInfo");
        boolean isVisible = parseBoolean(text, "isVisible");
        boolean isShadowEnable = parseBoolean(text, "isShadowEnable");
        boolean isShadowOnly = parseBoolean(text, "isShadowOnly");
        boolean isReflective = parseBoolean(text, "isReflective");
        boolean isRefractive = parseBoolean(text, "isRefractive");
        shp.setOtherAnimationInfo(otherAnimationInfo);
        shp.setVisible(isVisible);
        shp.setShadowEnable(isShadowEnable);
        shp.setShadowOnly(isShadowOnly);
        shp.setReflective(isReflective);
        shp.setRefractive(isRefractive);
        boolean isVisibleSpecial = parseBoolean(text, "isVisibleSpecial");
        shp.setVisibleSpecial(isVisibleSpecial);
        String name = parseString(text, "name");
        shp.setName(name);
        return shp;
      }
      if ("Disc".equals(type)) {
        double radius = parseDouble(text, "radius");
        EMShape shp = new Disc(radius);
        Matrix4 xanimTransform = parseAnimationTransform(text, "firstAnimMatrix");
        Matrix4 yanimTransform = parseAnimationTransform(text, "secondAnimMatrix");
        shp.setAnimationTransforms(new Matrix4[] {xanimTransform, yanimTransform});
        int shdwColor = parseColor(text, "shadowColor");
        double shdwBias = parseDouble(text, "shadowBias");
        shp.setShadowColor(shdwColor);
        shp.setShadowBias(shdwBias);
        String otherAnimationInfo = parseString(text, "otherAnimationInfo");
        boolean isVisible = parseBoolean(text, "isVisible");
        boolean isShadowEnable = parseBoolean(text, "isShadowEnable");
        boolean isShadowOnly = parseBoolean(text, "isShadowOnly");
        boolean isReflective = parseBoolean(text, "isReflective");
        boolean isRefractive = parseBoolean(text, "isRefractive");
        shp.setOtherAnimationInfo(otherAnimationInfo);
        shp.setVisible(isVisible);
        shp.setShadowEnable(isShadowEnable);
        shp.setShadowOnly(isShadowOnly);
        shp.setReflective(isReflective);
        shp.setRefractive(isRefractive);
        boolean isVisibleSpecial = parseBoolean(text, "isVisibleSpecial");
        shp.setVisibleSpecial(isVisibleSpecial);
        String name = parseString(text, "name");
        shp.setName(name);
        return shp;
      }
      if ("Sphere".equals(type)) {
        double r = parseDouble(text, "radius");
        EMShape shp = new Sphere(r);
        Matrix4 xanimTransform = parseAnimationTransform(text, "firstAnimMatrix");
        Matrix4 yanimTransform = parseAnimationTransform(text, "secondAnimMatrix");
        shp.setAnimationTransforms(new Matrix4[] {xanimTransform, yanimTransform});
        int shdwColor = parseColor(text, "shadowColor");
        double shdwBias = parseDouble(text, "shadowBias");
        shp.setShadowColor(shdwColor);
        shp.setShadowBias(shdwBias);
        String otherAnimationInfo = parseString(text, "otherAnimationInfo");
        boolean isVisible = parseBoolean(text, "isVisible");
        boolean isShadowEnable = parseBoolean(text, "isShadowEnable");
        boolean isShadowOnly = parseBoolean(text, "isShadowOnly");
        boolean isReflective = parseBoolean(text, "isReflective");
        boolean isRefractive = parseBoolean(text, "isRefractive");
        shp.setOtherAnimationInfo(otherAnimationInfo);
        shp.setVisible(isVisible);
        shp.setShadowEnable(isShadowEnable);
        shp.setShadowOnly(isShadowOnly);
        shp.setReflective(isReflective);
        shp.setRefractive(isRefractive);
        boolean isVisibleSpecial = parseBoolean(text, "isVisibleSpecial");
        shp.setVisibleSpecial(isVisibleSpecial);
        String name = parseString(text, "name");
        shp.setName(name);
        return shp;
      }
      if ("Triangle".equals(type)) {
        Point3 v0 = parsePoint3(text, "v0");
        Point3 v1 = parsePoint3(text, "v1");
        Point3 v2 = parsePoint3(text, "v2");
        EMShape shp = new Triangle(v0, v1, v2);
        Matrix4 xanimTransform = parseAnimationTransform(text, "firstAnimMatrix");
        Matrix4 yanimTransform = parseAnimationTransform(text, "secondAnimMatrix");
        shp.setAnimationTransforms(new Matrix4[] {xanimTransform, yanimTransform});
        int shdwColor = parseColor(text, "shadowColor");
        double shdwBias = parseDouble(text, "shadowBias");
        shp.setShadowColor(shdwColor);
        shp.setShadowBias(shdwBias);
        String otherAnimationInfo = parseString(text, "otherAnimationInfo");
        boolean isVisible = parseBoolean(text, "isVisible");
        boolean isShadowEnable = parseBoolean(text, "isShadowEnable");
        boolean isShadowOnly = parseBoolean(text, "isShadowOnly");
        boolean isReflective = parseBoolean(text, "isReflective");
        boolean isRefractive = parseBoolean(text, "isRefractive");
        shp.setOtherAnimationInfo(otherAnimationInfo);
        shp.setVisible(isVisible);
        shp.setShadowEnable(isShadowEnable);
        shp.setShadowOnly(isShadowOnly);
        shp.setReflective(isReflective);
        shp.setRefractive(isRefractive);
        boolean isVisibleSpecial = parseBoolean(text, "isVisibleSpecial");
        shp.setVisibleSpecial(isVisibleSpecial);
        String name = parseString(text, "name");
        shp.setName(name);
        return shp;
      }
      if ("Torus".equals(type)) {
        double major = parseDouble(text, "majorRadius");
        double minor = parseDouble(text, "minorRadius");
        EMShape shp = new Torus(major, minor);
        Matrix4 xanimTransform = parseAnimationTransform(text, "firstAnimMatrix");
        Matrix4 yanimTransform = parseAnimationTransform(text, "secondAnimMatrix");
        shp.setAnimationTransforms(new Matrix4[] {xanimTransform, yanimTransform});
        int shdwColor = parseColor(text, "shadowColor");
        double shdwBias = parseDouble(text, "shadowBias");
        shp.setShadowColor(shdwColor);
        shp.setShadowBias(shdwBias);
        String otherAnimationInfo = parseString(text, "otherAnimationInfo");
        boolean isVisible = parseBoolean(text, "isVisible");
        boolean isShadowEnable = parseBoolean(text, "isShadowEnable");
        boolean isShadowOnly = parseBoolean(text, "isShadowOnly");
        boolean isReflective = parseBoolean(text, "isReflective");
        boolean isRefractive = parseBoolean(text, "isRefractive");
        shp.setOtherAnimationInfo(otherAnimationInfo);
        shp.setVisible(isVisible);
        shp.setShadowEnable(isShadowEnable);
        shp.setShadowOnly(isShadowOnly);
        shp.setReflective(isReflective);
        shp.setRefractive(isRefractive);
        boolean isVisibleSpecial = parseBoolean(text, "isVisibleSpecial");
        shp.setVisibleSpecial(isVisibleSpecial);
        String name = parseString(text, "name");
        shp.setName(name);
        return shp;
      }
      if ("TorusKnot".equals(type)) {
        double R = parseDouble(text, "R");
        double r = parseDouble(text, "r");
        int p = parseInt(text, "p");
        int q = parseInt(text, "q");
        EMShape shp = new TorusKnot(R, r, p, q);
        Matrix4 xanimTransform = parseAnimationTransform(text, "firstAnimMatrix");
        Matrix4 yanimTransform = parseAnimationTransform(text, "secondAnimMatrix");
        shp.setAnimationTransforms(new Matrix4[] {xanimTransform, yanimTransform});
        int shdwColor = parseColor(text, "shadowColor");
        double shdwBias = parseDouble(text, "shadowBias");
        shp.setShadowColor(shdwColor);
        shp.setShadowBias(shdwBias);
        String otherAnimationInfo = parseString(text, "otherAnimationInfo");
        boolean isVisible = parseBoolean(text, "isVisible");
        boolean isShadowEnable = parseBoolean(text, "isShadowEnable");
        boolean isShadowOnly = parseBoolean(text, "isShadowOnly");
        boolean isReflective = parseBoolean(text, "isReflective");
        boolean isRefractive = parseBoolean(text, "isRefractive");
        shp.setOtherAnimationInfo(otherAnimationInfo);
        shp.setVisible(isVisible);
        shp.setShadowEnable(isShadowEnable);
        shp.setShadowOnly(isShadowOnly);
        shp.setReflective(isReflective);
        shp.setRefractive(isRefractive);
        boolean isVisibleSpecial = parseBoolean(text, "isVisibleSpecial");
        shp.setVisibleSpecial(isVisibleSpecial);
        String name = parseString(text, "name");
        shp.setName(name);
        return shp;
      }
      if ("UnionCSG".equals(type)
          || "IntersectionCSG".equals(type)
          || "DifferenceCSG".equals(type)) {
        return parseCSGShape(text);
      }
      return new Sphere(1.0);
    } catch (Exception e) {
      Log.e(TAG, "Shape parse error: " + e.getMessage(), e);
      return null;
    }
  }

  /**
   * Gets light template for a given type
   *
   * @param type the light type
   * @return template string
   */
  public static String getLightTemplate(String type) {
    if ("MuratAmbient".equals(type)) {
      return "name = ambient;\n"
          + "color = 0xFFDDEEFF;\n"
          + "intensity = 1.0;\n"
          + "firstAnimationIntensity = 0.0;\n"
          + "secondAnimationIntensity = 0.0;\n"
          + "positionDelta = [0.0, 0.0, 0.0];\n"
          + "colorDelta = [0, 0, 0, 0];";
    }
    if ("MuratPoint".equals(type)) {
      return "name = point;\n"
          + "position = P(2.000, 5.000, 2.000);\n"
          + "color = 0xFFFFFFFF;\n"
          + "intensity = 1.0;\n"
          + "firstAnimationIntensity = 0.0;\n"
          + "secondAnimationIntensity = 0.0;\n"
          + "positionDelta = [0.0, 0.0, 0.0];\n"
          + "colorDelta = [0, 0, 0, 0];\n"
          + "constantAttenuation = 1.0;\n"
          + "linearAttenuation = 0.1;\n"
          + "quadraticAttenuation = 0.01;";
    }
    if ("MuratDirectional".equals(type)) {
      return "name = directional;\n"
          + "direction = V(-1.000,-1.000,-1.000);\n"
          + "color = 0xFFE8E8E8;\n"
          + "intensity = 0.9;\n"
          + "firstAnimationIntensity = 0.0;\n"
          + "secondAnimationIntensity = 0.0;\n"
          + "positionDelta = [0.0, 0.0, 0.0];\n"
          + "colorDelta = [0, 0, 0, 0];";
    }
    if ("Spot".equals(type)) {
      return "name = spot;\n"
          + "position = P(0,0,5);\n"
          + "direction = V(0,0,-1);\n"
          + "color = 0xFFFFFF00;\n"
          + "intensity = 1.0;\n"
          + "firstAnimationIntensity = 0.0;\n"
          + "secondAnimationIntensity = 0.0;\n"
          + "positionDelta = [0.0, 0.0, 0.0];\n"
          + "colorDelta = [0, 0, 0, 0];\n"
          + "innerConeAngle = 40.0;\n"
          + "outerConeAngle = 20.0;\n"
          + "constantAttenuation = 1.0;\n"
          + "linearAttenuation = 0.1;\n"
          + "quadraticAttenuation = 0.05;";
    }
    if ("Sphere".equals(type)) {
      return "name = sphering;\n"
          + "position = P(0.000, 2.000, 0.000);\n"
          + "color = 0xFFFFF2E6;\n"
          + "intensity = 1.0;\n"
          + "firstAnimationIntensity = 0.0;\n"
          + "secondAnimationIntensity = 0.0;\n"
          + "positionDelta = [0.0, 0.0, 0.0];\n"
          + "colorDelta = [0, 0, 0, 0];\n"
          + "radius = 0.3;\n"
          + "sampleCount = 12;";
    }
    if ("Tube".equals(type)) {
      return "name = tube;\n"
          + "position = P(0.000, 3.000, 0.000);\n"
          + "color = 0xFFFFFFFF;\n"
          + "intensity = 0.8;\n"
          + "firstAnimationIntensity = 0.0;\n"
          + "secondAnimationIntensity = 0.0;\n"
          + "positionDelta = [0.0, 0.0, 0.0];\n"
          + "colorDelta = [0, 0, 0, 0];\n"
          + "startPoint = P(-1.000, 3.000, 0.000);\n"
          + "endPoint = P(1.000, 3.000, 0.000);\n"
          + "radius = 0.1;\n"
          + "length = 2.0;\n"
          + "sampleCount = 8;";
    }
    if ("Area".equals(type)) {
      return "name = area;\n"
          + "position = P(0.000, 4.000, 0.000);\n"
          + "color = 0xFFFFFFFF;\n"
          + "intensity = 0.9;\n"
          + "firstAnimationIntensity = 0.0;\n"
          + "secondAnimationIntensity = 0.0;\n"
          + "positionDelta = [0.0, 0.0, 0.0];\n"
          + "colorDelta = [0, 0, 0, 0];\n"
          + "normal = V(0.000, -1.000, 0.000);\n"
          + "width = 3.0;\n"
          + "height = 2.0;\n"
          + "samplesU = 6;\n"
          + "samplesV = 4;";
    }
    if ("Bioluminescent".equals(type)) {
      return "name = bioluminescent;\n"
          + "positions = [P(0,1,0)];\n"
          + "color = 0xFF00FF00;\n"
          + "pulseSpeed = 0.5;\n"
          + "baseIntensity = 1.0;\n"
          + "firstAnimationIntensity = 0.0;\n"
          + "secondAnimationIntensity = 0.0;\n"
          + "positionDelta = [0.0, 0.0, 0.0];\n"
          + "colorDelta = [0, 0, 0, 0];\n"
          + "attenuationFactor = 0.1;";
    }
    if ("BlackHole".equals(type)) {
      return "name = blackhole;\n"
          + "singularity = P(0,0,0);\n"
          + "radius = 1.0;\n"
          + "color = 0xFF000000;\n"
          + "intensity = 1.0;"
          + "firstAnimationIntensity = 0.0;\n"
          + "secondAnimationIntensity = 0.0;\n"
          + "positionDelta = [0.0, 0.0, 0.0];\n"
          + "colorDelta = [0, 0, 0, 0];";
    }
    if ("PulsatingPoint".equals(type)) {
      return "name = pulsating;\n"
          + "initialPosition = P(5,5,5);\n"
          + "baseColor = 0xFFFF0000;\n"
          + "baseIntensity = 1.0;\n"
          + "firstAnimationIntensity = 0.0;\n"
          + "secondAnimationIntensity = 0.0;\n"
          + "positionDelta = [0.0, 0.0, 0.0];\n"
          + "colorDelta = [0, 0, 0, 0];\n"
          + "pulsationSpeed = 0.5;\n"
          + "movementSpeed = 0.1;\n"
          + "movementAmplitude = 1.0;";
    }
    if ("Fractal".equals(type)) {
      return "name = fractal;\n"
          + "position = P(5,5,5);\n"
          + "color = 0xFFFF00FF;\n"
          + "intensity = 1.0;\n"
          + "firstAnimationIntensity = 0.0;\n"
          + "secondAnimationIntensity = 0.0;\n"
          + "positionDelta = [0.0, 0.0, 0.0];\n"
          + "colorDelta = [0, 0, 0, 0];\n"
          + "octaves = 4;\n"
          + "persistence = 0.5;\n"
          + "frequency = 2.0;";
    }
    if ("Projector".equals(type)) {
      return "name = projector;\n"
          + "position = P(0,5,-1);\n"
          + "direction = V(0,-1,0);\n"
          + "color = 0xFFFFFFFF;\n"
          + "intensity = 2.0;\n"
          + "firstAnimationIntensity = 0.0;\n"
          + "secondAnimationIntensity = 0.0;\n"
          + "positionDelta = [0.0, 0.0, 0.0];\n"
          + "colorDelta = [0, 0, 0, 0];\n"
          + "angle = 90.0;\n"
          + "falloff = 0.3;\n"
          + "texture = \"textures/turkeyFlag.png\";\n"
          + "constantAttenuation = 1.0;\n"
          + "linearAttenuation = 0.0;\n"
          + "quadraticAttenuation = 0.0;\n"
          + "uScale = 0.6;\n"
          + "vScale = 0.6;";
    }
    return "";
  }

  /**
   * Creates a Light from text definition
   *
   * @param type the light type
   * @param text the definition text
   * @return the created Light
   */
  public static Light createLightFromText(String type, String text) {
    try {
      if ("Bioluminescent".equals(type)) {
        List<Point3> positions = parsePoints3(text, "positions");
        int color = parseColor(text, "color");
        double ps = parseDouble(text, "pulseSpeed");
        double bi = parseDouble(text, "baseIntensity");
        double af = parseDouble(text, "attenuationFactor");
        double dx = parseDouble(text, "firstAnimationIntensity");
        double dy = parseDouble(text, "secondAnimationIntensity");
        double[] posDelta = parseDoubleArray(text, "positionDelta");
        int[] colDelta = parseIntArray(text, "colorDelta");
        Light lg = new BioluminescentLight(positions, color, ps, bi, af);
        lg.setIncDecIntensity(new double[] {dx, dy});
        lg.setPositionDelta(posDelta);
        lg.setColorDelta(colDelta);
        String name = parseString(text, "name");
        lg.setName(name);
        return lg;
      }
      if ("BlackHole".equals(type)) {
        Point3 singularity = parsePoint3(text, "singularity");
        double radius = parseDouble(text, "radius");
        int color = parseColor(text, "color");
        double intensity = parseDouble(text, "intensity");
        double dx = parseDouble(text, "firstAnimationIntensity");
        double dy = parseDouble(text, "secondAnimationIntensity");
        double[] posDelta = parseDoubleArray(text, "positionDelta");
        int[] colDelta = parseIntArray(text, "colorDelta");
        Light lg = new BlackHoleLight(singularity, radius, color, intensity);
        lg.setIncDecIntensity(new double[] {dx, dy});
        lg.setPositionDelta(posDelta);
        lg.setColorDelta(colDelta);
        String name = parseString(text, "name");
        lg.setName(name);
        return lg;
      }
      if ("MuratDirectional".equals(type)) {
        Vector3 dir = parseVector3(text, "direction");
        int color = parseColor(text, "color");
        double intensity = parseDouble(text, "intensity");
        double dx = parseDouble(text, "firstAnimationIntensity");
        double dy = parseDouble(text, "secondAnimationIntensity");
        double[] posDelta = parseDoubleArray(text, "positionDelta");
        int[] colDelta = parseIntArray(text, "colorDelta");
        Light lg = new MuratDirectionalLight(dir, color, intensity);
        lg.setIncDecIntensity(new double[] {dx, dy});
        lg.setPositionDelta(posDelta);
        lg.setColorDelta(colDelta);
        String name = parseString(text, "name");
        lg.setName(name);
        return lg;
      }
      if ("MuratAmbient".equals(type)) {
        int color = parseColor(text, "color");
        double intensity = parseDouble(text, "intensity");
        double dx = parseDouble(text, "firstAnimationIntensity");
        double dy = parseDouble(text, "secondAnimationIntensity");
        double[] posDelta = parseDoubleArray(text, "positionDelta");
        int[] colDelta = parseIntArray(text, "colorDelta");
        Light lg = new MuratAmbientLight(color, intensity);
        lg.setIncDecIntensity(new double[] {dx, dy});
        lg.setPositionDelta(posDelta);
        lg.setColorDelta(colDelta);
        String name = parseString(text, "name");
        lg.setName(name);
        return lg;
      }
      if ("Fractal".equals(type)) {
        Point3 pos = parsePoint3(text, "position");
        int color = parseColor(text, "color");
        double intensity = parseDouble(text, "intensity");
        double dx = parseDouble(text, "firstAnimationIntensity");
        double dy = parseDouble(text, "secondAnimationIntensity");
        double[] posDelta = parseDoubleArray(text, "positionDelta");
        int[] colDelta = parseIntArray(text, "colorDelta");
        Light lg;
        if (text.contains("octaves")) {
          int octaves = parseInt(text, "octaves");
          double persistence = parseDouble(text, "persistence");
          double frequency = parseDouble(text, "frequency");
          lg = new FractalLight(pos, color, intensity, octaves, persistence, frequency);
        } else {
          lg = new FractalLight(pos, color, intensity);
        }
        lg.setIncDecIntensity(new double[] {dx, dy});
        lg.setPositionDelta(posDelta);
        lg.setColorDelta(colDelta);
        String name = parseString(text, "name");
        lg.setName(name);
        return lg;
      }
      if ("MuratPoint".equals(type)) {
        Point3 position = parsePoint3(text, "position");
        int color = parseColor(text, "color");
        double intensity = parseDouble(text, "intensity");
        double constantAttenuation = parseDouble(text, "constantAttenuation");
        double linearAttenuation = parseDouble(text, "linearAttenuation");
        double quadraticAttenuation = parseDouble(text, "quadraticAttenuation");
        double dx = parseDouble(text, "firstAnimationIntensity");
        double dy = parseDouble(text, "secondAnimationIntensity");
        double[] posDelta = parseDoubleArray(text, "positionDelta");
        int[] colDelta = parseIntArray(text, "colorDelta");
        Light lg =
            new MuratPointLight(
                position,
                color,
                intensity,
                constantAttenuation,
                linearAttenuation,
                quadraticAttenuation);
        lg.setIncDecIntensity(new double[] {dx, dy});
        lg.setPositionDelta(posDelta);
        lg.setColorDelta(colDelta);
        String name = parseString(text, "name");
        lg.setName(name);
        return lg;
      }
      if ("PulsatingPoint".equals(type)) {
        Point3 initialPos = parsePoint3(text, "initialPosition");
        int baseColor = parseColor(text, "baseColor");
        double baseIntensity = parseDouble(text, "baseIntensity");
        double pulsationSpeed = parseDouble(text, "pulsationSpeed");
        double movementSpeed = parseDouble(text, "movementSpeed");
        double movementAmplitude = parseDouble(text, "movementAmplitude");
        double dx = parseDouble(text, "firstAnimationIntensity");
        double dy = parseDouble(text, "secondAnimationIntensity");
        double[] posDelta = parseDoubleArray(text, "positionDelta");
        int[] colDelta = parseIntArray(text, "colorDelta");
        Light lg;
        if (text.contains("constantAttenuation")) {
          double c = parseDouble(text, "constantAttenuation");
          double l = parseDouble(text, "linearAttenuation");
          double q = parseDouble(text, "quadraticAttenuation");
          lg =
              new PulsatingPointLight(
                  initialPos,
                  baseColor,
                  baseIntensity,
                  pulsationSpeed,
                  movementSpeed,
                  movementAmplitude,
                  c,
                  l,
                  q);
        } else {
          lg =
              new PulsatingPointLight(
                  initialPos,
                  baseColor,
                  baseIntensity,
                  pulsationSpeed,
                  movementSpeed,
                  movementAmplitude);
        }
        lg.setIncDecIntensity(new double[] {dx, dy});
        lg.setPositionDelta(posDelta);
        lg.setColorDelta(colDelta);
        String name = parseString(text, "name");
        lg.setName(name);
        return lg;
      }
      if ("Spot".equals(type)) {
        Point3 position = parsePoint3(text, "position");
        Vector3 direction = parseVector3(text, "direction");
        int color = parseColor(text, "color");
        double intensity = parseDouble(text, "intensity");
        double inner = parseDouble(text, "innerConeAngle");
        double outer = parseDouble(text, "outerConeAngle");
        double dx = parseDouble(text, "firstAnimationIntensity");
        double dy = parseDouble(text, "secondAnimationIntensity");
        double[] posDelta = parseDoubleArray(text, "positionDelta");
        int[] colDelta = parseIntArray(text, "colorDelta");
        Light lg;
        if (text.contains("constantAttenuation")) {
          double c = parseDouble(text, "constantAttenuation");
          double l = parseDouble(text, "linearAttenuation");
          double q = parseDouble(text, "quadraticAttenuation");
          lg = new SpotLight(position, direction, color, intensity, inner, outer, c, l, q);
        } else {
          lg = new SpotLight(position, direction, color, intensity, inner, outer);
        }
        lg.setIncDecIntensity(new double[] {dx, dy});
        lg.setPositionDelta(posDelta);
        lg.setColorDelta(colDelta);
        String name = parseString(text, "name");
        lg.setName(name);
        return lg;
      }
      if ("Sphere".equals(type)) {
        Point3 position = parsePoint3(text, "position");
        int color = parseColor(text, "color");
        double intensity = parseDouble(text, "intensity");
        double radius = parseDouble(text, "radius");
        double sampleCount = parseDouble(text, "sampleCount");
        double dx = parseDouble(text, "firstAnimationIntensity");
        double dy = parseDouble(text, "secondAnimationIntensity");
        double[] posDelta = parseDoubleArray(text, "positionDelta");
        int[] colDelta = parseIntArray(text, "colorDelta");
        Light lg = new SphereLight(position, radius, color, intensity, (int) sampleCount);
        lg.setIncDecIntensity(new double[] {dx, dy});
        lg.setPositionDelta(posDelta);
        lg.setColorDelta(colDelta);
        String name = parseString(text, "name");
        lg.setName(name);
        return lg;
      }
      if ("Tube".equals(type)) {
        Point3 position = parsePoint3(text, "position");
        int color = parseColor(text, "color");
        double intensity = parseDouble(text, "intensity");
        Point3 startPoint = parsePoint3(text, "startPoint");
        Point3 endPoint = parsePoint3(text, "endPoint");
        double radius = parseDouble(text, "radius");
        double length = parseDouble(text, "length");
        double sampleCount = parseDouble(text, "sampleCount");
        double dx = parseDouble(text, "firstAnimationIntensity");
        double dy = parseDouble(text, "secondAnimationIntensity");
        double[] posDelta = parseDoubleArray(text, "positionDelta");
        int[] colDelta = parseIntArray(text, "colorDelta");
        Light lg = new TubeLight(startPoint, endPoint, radius, color, intensity, (int) sampleCount);
        lg.setIncDecIntensity(new double[] {dx, dy});
        lg.setPositionDelta(posDelta);
        lg.setColorDelta(colDelta);
        String name = parseString(text, "name");
        lg.setName(name);
        return lg;
      }
      if ("Projector".equals(type)) {
        Point3 position = parsePoint3(text, "position");
        Vector3 direction = parseVector3(text, "direction");
        int color = parseColor(text, "color");
        double intensity = parseDouble(text, "intensity");
        double angle = parseDouble(text, "angle");
        double falloff = parseDouble(text, "falloff");
        String texturePath = parseString(text, "texture");
        double constantAttenuation = parseDouble(text, "constantAttenuation");
        double linearAttenuation = parseDouble(text, "linearAttenuation");
        double quadraticAttenuation = parseDouble(text, "quadraticAttenuation");
        double dx = parseDouble(text, "firstAnimationIntensity");
        double dy = parseDouble(text, "secondAnimationIntensity");
        double[] posDelta = parseDoubleArray(text, "positionDelta");
        int[] colDelta = parseIntArray(text, "colorDelta");
        String name = parseString(text, "name");

        // UV Scale parameters (optional, with defaults)
        double uScale = parseDouble(text, "uScale");
        double vScale = parseDouble(text, "vScale");

        Bitmap texture = loadImage(texturePath);

        Light lg =
            new ProjectorLight(
                position,
                direction,
                color,
                intensity,
                angle,
                falloff,
                texture,
                constantAttenuation,
                linearAttenuation,
                quadraticAttenuation);

        // Set UV scale if the light is a ProjectorLight
        if (lg instanceof ProjectorLight) {
          ProjectorLight projector = (ProjectorLight) lg;
          projector.setScale(uScale, vScale);
          projector.setImagePath(texturePath);
        }

        lg.setIncDecIntensity(new double[] {dx, dy});
        lg.setPositionDelta(posDelta);
        lg.setColorDelta(colDelta);
        lg.setName(name);

        return lg;
      }
      if ("Area".equals(type)) {
        Point3 position = parsePoint3(text, "position");
        int color = parseColor(text, "color");
        double intensity = parseDouble(text, "intensity");
        Vector3 normal = parseVector3(text, "normal");
        double width = parseDouble(text, "width");
        double height = parseDouble(text, "height");
        double samplesU = parseDouble(text, "samplesU");
        double samplesV = parseDouble(text, "samplesV");
        double dx = parseDouble(text, "firstAnimationIntensity");
        double dy = parseDouble(text, "secondAnimationIntensity");
        double[] posDelta = parseDoubleArray(text, "positionDelta");
        int[] colDelta = parseIntArray(text, "colorDelta");
        Light lg =
            new AreaLight(
                position, normal, width, height, color, intensity, (int) samplesU, (int) samplesV);
        lg.setIncDecIntensity(new double[] {dx, dy});
        lg.setPositionDelta(posDelta);
        lg.setColorDelta(colDelta);
        String name = parseString(text, "name");
        lg.setName(name);
        return lg;
      }
      return new MuratPointLight(new Point3(5, 5, 5), 0xFFFFFFFF, 1.0);
    } catch (Exception e) {
      Log.e(TAG, "Light parse error: " + e.getMessage(), e);
      return new MuratPointLight(new Point3(5, 5, 5), 0xFFFFFFFF, 1.0);
    }
  }

  /**
   * Gets material template for a given type
   *
   * @param type the material type
   * @return template string
   */
  public static String getMaterialTemplate(String type) {
    if ("AfricanKente".equals(type)) return "ior = 1.0;";
    if ("Amber".equals(type))
      return "baseColor = 0xFFFFB038;\nior = 1.0;\nreflectivity = 0.2;\ntransparency = 0.2;";
    if ("AnodizedMetal".equals(type))
      return "baseColor = 0xFFFF6B35;\n"
          + "ambientCoeff = 0.35;\n"
          + "diffuseCoeff = 0.5;\n"
          + "specularCoeff = 1.0;\n"
          + "shininess = 100.0;\n"
          + "specularColor = 0xFFFFEE88;\n"
          + "reflectivity = 0.5;\n"
          + "ior = 2.0;\n"
          + "transparency = 0.0;\n"
          + "iridescenceStrength = 0.4;\n"
          + "iridescenceSpeed = 1.8;";
    if ("AuroraCeramic".equals(type))
      return "baseColor = 0xFFFFFFFF;\nauroraColor = 0xFF00FF00;\nauroraIntensity = 1.0;";
    if ("Baklava".equals(type))
      return "pastryColor = 0xFFFFDF00;\n"
          + "syrupColor = 0xFFD2B48C;\n"
          + "nutColor = 0xFF8B4513;\n"
          + "layers = 10.0;\n"
          + "syrupiness = 0.5;";
    if ("BrunostCheese".equals(type))
      return "cheeseColor = 0xFFD2691E;\ncaramelColor = 0xFFFFA500;\ncaramelAmount = 0.5;";
    if ("CalligraphyRune".equals(type))
      return "parchmentColor = 0xFFF5DEB3;\n"
          + "inkColor = 0xFF000000;\n"
          + "goldLeafColor = 0xFFFFD700;\n"
          + "writingIntensity = 1.0;";
    if ("CarpetTexture".equals(type)) return "baseColor = 0xFF8B4513;\npatternColor = 0xFFFFD700;";
    if ("CeramicTile".equals(type))
      return "tileColor = 0xFFFFFFFF;\n"
          + "groutColor = 0xFF464650;\n"
          + "tileSize = 0.6;\n"
          + "groutWidth = 0.003;\n"
          + "tileRoughness = 0.05;\n"
          + "groutRoughness = 1.0;\n"
          + "tileSpecular = 1.0;\n"
          + "groutSpecular = 0.5;\n"
          + "fresnelIntensity = 0.0;\n"
          + "normalMicroFacet = 0.02;\n"
          + "reflectionSharpness = 0.98;\n"
          + "energyConservation = 1.0;";
    if ("Checkerboard".equals(type))
      return "color1 = 0xFFFFFFFF;\n"
          + "color2 = 0xFFFF0000;\n"
          + "size = 1.0;\n"
          + "ambientCoeff = 0.4;\n"
          + "diffuseCoeff = 0.7;\n"
          + "specularCoeff = 0.2;\n"
          + "shininess = 32.0;\n"
          + "specularColor = 0xFFFFFFFF;\n"
          + "reflectivity = 0.0;\n"
          + "ior = 1.0;\n"
          + "transparency = 0.0;";
    if ("Cini".equals(type))
      return "baseColor = 0xFFFFFFFF;\npatternColor = 0xFF0090EE;\nglossiness = 0.8;";
    if ("CircleTexture".equals(type))
      return "solidColor = 0xFFFF0000;\n"
          + "patternColor = 0xFF00FF00;\n"
          + "patternSize = 0.5;\n"
          + "ambientCoefficient = 0.1;\n"
          + "diffuseCoefficient = 0.8;\n"
          + "specularCoefficient = 0.1;\n"
          + "shininess = 10.0;\n"
          + "reflectivity = 0.0;\n"
          + "ior = 1.0;\n"
          + "transparency = 0.0;";
    if ("CoffeeFjord".equals(type))
      return "coffeeColor = 0xFF6F4E37;\nfjordColor = 0xFF00CED1;\nblendIntensity = 0.5;";
    if ("ColorDiffuse".equals(type))
      return "baseColor = 0xFF4488CC;\nior = 1.33;\nreflectivity = 0.10;\ntransparency = 0.10;";
    if ("ColoredGlass".equals(type))
      return "baseColor = 0xFF4488FF;\n"
          + "specularColor = 0xFFFF4444;\n"
          + "transparency = 0.0;\n"
          + "reflectivity = 0.3;\n"
          + "ior = 1.52;\n"
          + "shininess = 512.0;\n"
          + "specularStrength = 6.0;";
    if ("DamaskCeramic".equals(type))
      return "primary = 0xFFFF6347;\n"
          + "secondary = 0xFF4682B4;\n"
          + "shininess = 50.0;\n"
          + "ambient = 0.2;\n"
          + "specular = 0.8;";
    if ("Diamond".equals(type))
      return "ior = 2.42;\n"
          + "reflectivity = 0.2;\n"
          + "transparency = 0.0;\n"
          + "dispersionStrength = 0.3;\n"
          + "fireEffect = 0.75;\n"
          + "shininess = 100.0;\n"
          + "specularStrength = 2.0;\n"
          + "baseColor = 0xFFB9F2FF;\n"
          + "specularColor = 0xFF00DD00;\n"
          + "fireColor1 = 0xFFFF4444;\n"
          + "fireColor2 = 0xFF44FF44;\n"
          + "fireColor3 = 0xFF4444FF;";
    if ("Dielectric".equals(type))
      return "baseColor = 0xFFFFFFFF;\n"
          + "specularColor = 0xFFFFFFFF;\n"
          + "transparency = 0.75;\n"
          + "reflectivity = 0.35;\n"
          + "ior = 1.52;\n"
          + "shininess = 200.0;\n"
          + "specularStrength = 2.8;";
    if ("DielectricText".equals(type))
      return "baseColor = 0xFFFFFFFF;\n"
          + "specularColor = 0xFFFFFFFF;\n"
          + "transparency = 0.05;\n"
          + "reflectivity = 0.5;\n"
          + "ior = 1.5;\n"
          + "shininess = 200.0;\n"
          + "specularStrength = 2.8;\n"
          + "overlayText = HELLO;\n"
          + "textColor = 0xFFFFFFFF;\n"
          + "gradientColor = 0x00000000;\n"
          + "gradientType = horizontal;\n"
          + "bgColor = 0x00000000;\n"
          + "fontFamily = Arial;\n"
          + "fontStyle = 0;\n"
          + "fontSize = 48;\n"
          + "textUOffset = 0;\n"
          + "textVOffset = 0;\n"
          + "imagePath = textures/turkeyFlag.png;\n"
          + "imageWidth = 256;\n"
          + "imageHeight = 256;\n"
          + "imageUOffset = 0;\n"
          + "imageVOffset = 0;";
    if ("Diffuse".equals(type))
      return "color = 0xFFFF0000;\n"
          + "diffuseCoefficient = 0.8;\n"
          + "reflectivity = 0.0;\n"
          + "ior = 1.0;\n"
          + "transparency = 0.0;\n"
          + "roughness = 0.5;\n"
          + "ambientStrength = 0.1;\n"
          + "rimStrength = 0.2;";
    if ("FilteredDielectric".equals(type))
      return "imagePath = \"textures/turkeyFlag.png\";\n"
          + "word = \"TEXT\";\n"
          + "textColor = 0xFFFF0000;\n"
          + "gradientColor = 0xFF00DD00;\n"
          + "gradientType = \"horizontal\";\n"
          + "bgColor = 0x00000000;\n"
          + "fontFamily = \"Arial\";\n"
          + "fontStyle = 1;\n"
          + "fontSize = 50;\n"
          + "uOffset = 0;\n"
          + "vOffset = 200;\n"
          + "imageWidth = 192;\n"
          + "imageHeight = 192;\n"
          + "imageUOffset = 0;\n"
          + "imageVOffset = -60;\n"
          + "diffuseColor = 0x00000000;\n"
          + "ior = 1.5;\n"
          + "transparency = 0.75;\n"
          + "reflectivity = 0.0;\n"
          + "filterColorInside = 0xFF0000FF;\n"
          + "filterColorOutside = 0xFF00DD00;";
    if ("FractalBark".equals(type)) return "roughness = 0.5;";
    if ("FractalFire".equals(type))
      return "iterations = 5;\nchaos = 0.5;\nscale = 1.0;\nspeed = 0.1;";
    if ("Generated".equals(type))
      return "color1 = 0xFFFFFFFF;\n"
          + "color2 = 0xFF000000;\n"
          + "ior = 1.0;\n"
          + "reflectivity = 0.0;\n"
          + "transparency = 0.0;\n"
          + "shininess = 32.0;\n"
          + "roughness = 0.5;\n"
          + "pattern = SOLID;\n"
          + "patternScale = 1.0;\n"
          + "patternFrequency = 1.0;\n"
          + "materialType = PLASTIC;\n"
          + "useFresnel = false;\n"
          + "fresnelF0 = 0.04;\n"
          + "useNormalMap = false;\n"
          + "useDisplacement = false;\n"
          + "displacementScale = 0.0;\n"
          + "#PATTERNS: SOLID, CHECKERBOARD, VERTICAL_GRADIENT, HORIZONTAL_GRADIENT,"
          + " RADIAL_GRADIENT, WOOD_RINGS, MARBLE, BRICK, STRIPES, DOTS, NOISE, FRACTAL\n"
          + "#TYPES: PLASTIC, METAL, GLASS, SNOW_GLOBE, CARPET, WOOD, MARBLE, LEATHER,"
          + " FABRIC, RUBBER, CUSTOM";
    if ("HamamSauna".equals(type))
      return "marbleColor = 0xFFF0F0F0;\n"
          + "woodColor = 0xFF8B4513;\n"
          + "steamColor = 0xFFB0E0E6;\n"
          + "steamIntensity = 0.5;";
    if ("HexagonalHoneycomb".equals(type))
      return "primary = 0xFFFFD700;\n"
          + "secondary = 0xFF8B4513;\n"
          + "borderColor = 0xFF000000;\n"
          + "cellSize = 0.2;\n"
          + "borderWidth = 0.1;\n"
          + "ambientStrength = 0.3;\n"
          + "specularStrength = 0.5;\n"
          + "shininess = 30.0;\n"
          + "reflectivity = 0.07";
    if ("Hokusai".equals(type))
      return "waterColor = 0xFF1A3380;\n"
          + "foamColor = 0xFFFFFFFF;\n"
          + "scale = 4.0;\n"
          + "mode = 1; //0 planes and 1 spheres\n"
          + "radius = 1.0;\n"
          + "ambientCoefficient = 0.6;\n"
          + "reflectivity = 0.1;";
    if ("HotCopper".equals(type))
      return "copperColor = 0xFFB87333;\npatinaColor = 0xFF4A7B4A;\npatinaAmount = 0.3;";
    if ("ImageTexture".equals(type))
      return "imagePath = \"textures/turkeyFlag.png\";\n"
          + "uScale = 1.0;\n"
          + "vScale = 1.0;\n"
          + "uOffset = 0.0;\n"
          + "vOffset = 0.0;\n"
          + "ambientCoefficient = 0.5;\n"
          + "diffuseCoefficient = 0.7;\n"
          + "specularCoefficient = 0.2;\n"
          + "shininess = 32.0;\n"
          + "reflectivity = 0.0;\n"
          + "ior = 1.0;";
    if ("IsotropicMetalText".equals(type))
      return "imagePath = \"textures/turkeyFlag.png\";\n"
          + "metalColor = 0xFFB3B3BF;\n"
          + "roughness = 0.2;\n"
          + "reflectivity = 0.4;\n"
          + "ior = 1.0;\n"
          + "transparency = 0.0;\n"
          + "text = \"[u+2764]\";\n"
          + "textColor = 0xFFFF0000;\n"
          + "gradientColor = 0xFF00DD00;\n"
          + "gradientType = \"horizontal\";\n"
          + "bgColor = 0x00000000;\n"
          + "fontFamily = \"Arial\";\n"
          + "fontStyle = 1;\n"
          + "fontSize = 52;\n"
          + "uOffset = 0;\n"
          + "vOffset = -30;\n"
          + "imageWidth = 256;\n"
          + "imageHeight = 256;\n"
          + "imageUOffset = 0;\n"
          + "imageVOffset = 0;";
    if ("Rainbow".equals(type))
      return "baseColor = 0xFFFFFFFF;\n"
          + "rainbowIntensity = 1.0;\n"
          + "rainbowMode = \"position\";\n"
          + "rainbowScale = 1.0;\n"
          + "rainbowOffset = 0.0;\n"
          + "rainbowSaturation = 1.0;\n"
          + "reflectivity = 0.3;\n"
          + "transparency = 0.0;";
    if ("KilimRosemaling".equals(type))
      return "kilimColor = 0xFFFF6347;\n"
          + "rosemalingColor = 0xFF4682B4;\n"
          + "accentColor = 0xFFFFD700;\n"
          + "patternIntensity = 1.0;";
    if ("Lambert".equals(type))
      return "color = 0xFFFF0000;\nambient = 0.3;\ndiffuse = 1.0;\nreflectivity = 0.0;";
    if ("LavaFlow".equals(type))
      return "hotColor = 0xFFFF4500;\ncoolColor = 0xFFFF8C00;\nflowSpeed = 0.1;";
    if ("Marble".equals(type))
      return "baseColor = 0xFFF0F0F0;\n"
          + "veinColor = 0xFF8B8B8B;\n"
          + "scale = 1.0;\n"
          + "veinDensity = 0.5;\n"
          + "turbulence = 0.1;\n"
          + "ambient = 0.3;\n"
          + "diffuse = 0.7;\n"
          + "specular = 0.4;\n"
          + "shininess = 60.0;\n"
          + "reflectivity = 0.2;\n"
          + "ior = 1.0;\n"
          + "transparency = 0.0;";
    if ("Metallic".equals(type))
      return "albedo = 0xFFD4AF37;\n"
          + "metallic = 0.0;\n"
          + "roughness = 0.2;\n"
          + "reflectivity = 0.3;\n"
          + "ior = 0.0;\n"
          + "transparency = 0.0;\n"
          + "emissionColor = 0xFFD4AF37;\n"
          + "emissionIntensity = 0.3;\n"
          + "aoStrength = 1.0;\n"
          + "fresnelReflectance = 1.0;";
    if ("MoonSurface".equals(type)) return "ior = 1.0;";
    if ("Mosaic".equals(type))
      return "baseColor = 0xFFFFFFFF;\n"
          + "tileColor = 0xFF000000;\n"
          + "tileSize = 0.5;\n"
          + "groutWidth = 0.1;\n"
          + "randomness = 0.1;";
    if ("Nazar".equals(type))
      return "blueColor = 0xFF1E90FF;\n"
          + "whiteColor = 0xFFFFFFFF;\n"
          + "glowIntensity = 0.3;\n"
          + "transparency = 0.25;\n"
          + "reflectivity = 0.4;";
    if ("NordicWeave".equals(type))
      return "primaryColor = 0xFFFF6347;\n"
          + "secondaryColor = 0xFF4682B4;\n"
          + "accentColor = 0xFFFFD700;\n"
          + "patternScale = 1.0;";
    if ("NordicWood".equals(type))
      return "woodColor = 0xFFDEB887;\ngrainColor = 0xFF8B4513;\ngrainIntensity = 0.5;";
    if ("NorwegianRose".equals(type))
      return "woodColor = 0xFF654321;\nroseColor = 0xFFC83232;\npatternScale = 4.0;";
    if ("OpticalIllusion".equals(type))
      return "color1 = 0xFFFF0000;\n"
          + "color2 = 0xFF0000FF;\n"
          + "frequency = 10.0;\n"
          + "smoothness = 0.5;\n"
          + "ambient = 0.2;\n"
          + "diffuse = 0.6;\n"
          + "specular = 0.4;\n"
          + "shininess = 40.0;\n"
          + "reflectivity = 0.3;\n"
          + "ior = 1.0;\n"
          + "transparency = 0.0;";
    if ("Orbital".equals(type))
      return "centerColor = 0xFFFF0000;\n"
          + "orbitColor = 0xFF0000FF;\n"
          + "ringWidth = 0.1;\n"
          + "ringCount = 3;";
    if ("Phong".equals(type))
      return "diffuseColor = 0xFFFF0000;\n"
          + "specularColor = 0xFFFFFFFF;\n"
          + "shininess = 50.0;\n"
          + "ambientCoefficient = 0.1;\n"
          + "diffuseCoefficient = 0.7;\n"
          + "specularCoefficient = 0.8;\n"
          + "reflectivity = 0.0;\n"
          + "ior = 1.0;\n"
          + "transparency = 0.0;";
    if ("PhongText".equals(type))
      return "word = \" [u+2764]\";\n"
          + "textColor = 0xFFFF0000;\n"
          + "gradientColor = 0x00000000;\n"
          + "gradientType = horizontal;\n"
          + "bgColor = 0x00000000;\n"
          + "fontFamily = Arial;\n"
          + "fontStyle = 1;\n"
          + "fontSize = 100;\n"
          + "uOffset = 0;\n"
          + "vOffset = 175;\n"
          + "diffuseColor = 0xFF00DD00;\n"
          + "specularColor = 0xFF0000FF;\n"
          + "shininess = 50.0;\n"
          + "ambientCoefficient = 0.3;\n"
          + "diffuseCoefficient = 0.7;\n"
          + "specularCoefficient = 0.7;\n"
          + "reflectivity = 0.10;\n"
          + "ior = 1.0;\n"
          + "transparency = 0.0;";
    if ("PixelArt".equals(type))
      return "palette = [0xFF000000, 0xFFFFFFFF, 0xFFFF0000, 0xFF00FF00, 0xFF0000FF];\n"
          + "pixelSize = 0.05;\n"
          + "ambient = 0.1;\n"
          + "diffuse = 0.7;\n"
          + "specular = 0.2;\n"
          + "shininess = 10.0;\n"
          + "reflectivity = 0.0;\n"
          + "ior = 1.0;\n"
          + "transparency = 0.0;";
    if ("Raja".equals(type))
      return "textureType = plain|damier|photo;\n"
          + "textureColor = 0xFFFFFFFF;\n"
          + "checkerColor = 0x00000000;\n"
          + "textureScale = 16.0;\n"
          + "textureOffset = 0.0;\n"
          + "imagePath = \"textures/turkey.png\";\n"
          + "diffuse = 0.3;\n"
          + "specular = 0.3;\n"
          + "ior = 1.5;\n"
          + "shininess = 64.0;\n"
          + "roughness = 0.0;\n"
          + "metalness = 0.0;\n"
          + "reflectivity = 0.15;\n"
          + "transparency = 0.9;\n"
          + "volumeChromaticAbsorption = true;\n"
          + "volumeChromatic = 0x00FFFFFF;\n"
          + "volumeChromaticDensity = 0.2;\n"
          + "volumeAbsorption = 0x00000000;\n"
          + "volumeAbsorptionDensity = 0.2;\n"
          + "volumeScattering = 0x00000000;\n"
          + "volumeScatteringDensity = 0.2;\n"
          + "isEnteringHasPriority = true;";
    if ("Random".equals(type)) return "ior = 1.0;";
    if ("RectangleChecker".equals(type))
      return "color1 = 0xFFFF0000;\n"
          + "color2 = 0xFF0000FF;\n"
          + "rectWidth = 1.0;\n"
          + "rectHeight = 0.5;\n"
          + "ambientCoefficient = 0.1;\n"
          + "diffuseCoefficient = 0.7;\n"
          + "specularCoefficient = 0.2;\n"
          + "shininess = 10.0;\n"
          + "reflectivity = 0.0;\n"
          + "ior = 1.0;\n"
          + "transparency = 0.0;";
    if ("RuneStone".equals(type))
      return "stoneColor = 0xFF8B8B8B;\nruneColor = 0xFFFFD700;\nruneDensity = 0.5;";
    if ("Salmon".equals(type))
      return "fleshColor = 0xFFFF91A4;\n"
          + "skinColor = 0xFFC86450;\n"
          + "fatColor = 0xFFFFDCB4;\n"
          + "freshness = 0.9;\n"
          + "oiliness = 0.7;";
    if ("Simit".equals(type))
      return "crustColor = 0xFFD2B48C;\n"
          + "sesameColor = 0xFFF5DEB3;\n"
          + "softColor = 0xFFFFF8DC;\n"
          + "crispiness = 0.8;\n"
          + "sesameDensity = 0.7;";
    if ("SphereWordTexture".equals(type))
      return "imagePath = \"textures/turkeyFlag.png\";\n"
          + "word = \"TEXT\";\n"
          + "textColor = 0xFFFF0000;\n"
          + "gradientColor = 0xFF00DD00;\n"
          + "gradientType = \"horizontal\";\n"
          + "bgColor = 0x440000FF;\n"
          + "fontFamily = \"Arial\";\n"
          + "fontStyle = 1;\n"
          + "fontSize = 100;\n"
          + "reflectivity = 0.0;\n"
          + "ior = 1.0;\n"
          + "transparency = 0.0;\n"
          + "uOffset = -250;\n"
          + "vOffset = 90;\n"
          + "imageWidth = 256;\n"
          + "imageHeight = 256;\n"
          + "imageUOffset = -250;\n"
          + "imageVOffset = -100;\n"
          + "followObjectRotation = true;";
    if ("StarryNight".equals(type)) return "ior = 1.0;";
    if ("Striped".equals(type))
      return "color1 = 0xFFFF0000;\n"
          + "color2 = 0xFF0000FF;\n"
          + "stripeSize = 5.0;\n"
          + "direction = HORIZONTAL;\n"
          + "ambientCoefficient = 0.1;\n"
          + "diffuseCoefficient = 0.8;\n"
          + "specularCoefficient = 0.1;\n"
          + "shininess = 10.0;\n"
          + "reflectivity = 0.0;\n"
          + "ior = 1.0;\n"
          + "transparency = 0.0;";
    if ("TelemarkPattern".equals(type))
      return "baseColor = 0xFFFF6347;\n"
          + "patternColor = 0xFF4682B4;\n"
          + "accentColor = 0xFFFFD700;\n"
          + "patternScale = 1.0;";
    if ("TextDielectric".equals(type))
      return "imagePath = \"textures/turkeyFlag.png\";\n"
          + "word = \"TEXT\";\n"
          + "textColor = 0xFFFF0000;\n"
          + "gradientColor = 0xFF00DD00;\n"
          + "gradientType = \"horizontal\";\n"
          + "bgColor = 0xFF000000;\n"
          + "fontFamily = \"Arial\";\n"
          + "fontStyle = 1;\n"
          + "fontSize = 50;\n"
          + "uOffset = 0;\n"
          + "vOffset = 0;\n"
          + "imageWidth = 256;\n"
          + "imageHeight = 256;\n"
          + "imageUOffset = 0;\n"
          + "imageVOffset = 0;\n"
          + "diffuseColor = 0xFFFF0000;\n"
          + "ior = 1.5;\n"
          + "transparency = 0.05;\n"
          + "reflectivity = 0.20;\n"
          + "filterColorInside = 0x00000000;\n"
          + "filterColorOutside = 0x00000000;\n"
          + "highQuality = false;\n"
          + "fullWidthGradient = true;\n"
          + "filterBaseStrength = 0.55;\n"
          + "filterFresnelBoost = 0.45;\n"
          + "filterMaxStrength = 0.9;\n"
          + "filterInkStrength = 0.45;\n"
          + "filterInsideCutoff = 0.55;\n"
          + "filterInsideRamp = 0.3;";
    if ("TexturedCheckerboard".equals(type))
      return "imagePath = \"textures/turkeyFlag.png\";\n"
          + "color1 = 0xFFFFFFFF;\n"
          + "color2 = 0xFFAAAAAA;\n"
          + "size = 0.25;\n"
          + "text = \"HELLO\";\n"
          + "textColor = 0xFF00FF00;\n"
          + "gradientColor = 0xFFFF0000;\n"
          + "gradientType = \"HORIZONTAL\";\n"
          + "bgColor = 0x00000000;\n"
          + "fontFamily = \"Arial\";\n"
          + "fontStyle = 1;\n"
          + "fontSize = 50;\n"
          + "textUOffset = -45;\n"
          + "textVOffset = 180;\n"
          + "imageWidth = 256;\n"
          + "imageHeight = 256;\n"
          + "imageUOffset = 90;\n"
          + "imageVOffset = 60;\n"
          + "ambientCoeff = 0.4;\n"
          + "diffuseCoeff = 0.7;\n"
          + "specularCoeff = 0.2;\n"
          + "shininess = 32.0;\n"
          + "specularColor = 0xFFFFFFFF;\n"
          + "reflectivity = 0.0;\n"
          + "ior = 1.0;\n"
          + "transparency = 0.0;";
    if ("TexturedPhong".equals(type))
      return "imagePath = \"textures/turkeyFlag.png\";\n"
          + "baseDiffuseColor = 0xFFFFFFFF;\n"
          + "specularColor = 0xFFFFFFFF;\n"
          + "shininess = 32.0;\n"
          + "ambientCoefficient = 0.6;\n"
          + "diffuseCoefficient = 0.6;\n"
          + "specularCoefficient = 0.4;\n"
          + "reflectivity = 0.0;\n"
          + "ior = 1.0;\n"
          + "transparency = 0.20;\n"
          + "uOffset = 0.0;\n"
          + "vOffset = 0.0;\n"
          + "uScale = 1.0;\n"
          + "vScale = 1.0;";
    if ("Texture".equals(type))
      return "name = texture;\n"
          + "imagePath = textures/turkeyFlag.png;\n"
          + "isTile = true;\n"
          + "sphericalMapping = false;\n"
          + "uOffset = 0.0;\n"
          + "vOffset = 0.0;\n"
          + "uScale = 1.0;\n"
          + "vScale = 1.0;";
    if ("Translucent".equals(type))
      return "name = translucent;\n"
          + "baseColor = 0xCC88CCFF;\n"
          + "attenuation = 0xCC88CCFF;\n"
          + "opacity = 0.5;\n"
          + "roughness = 0.0;\n"
          + "ior = 1.52;\n"
          + "reflectivity = 0.3;\n"
          + "transparency = 0.5;\n"
          + "shininess = 64.0;\n"
          + "specularStrength = 0.8;";
    if ("TransparentEmoji".equals(type))
      return "imagePath = \"textures/turkeyFlag.png\";\n"
          + "billboardWidth = 20.0;\n"
          + "billboardHeight = 10.0;\n"
          + "checkerColor1 = 0xFFFF0000;\n"
          + "checkerColor2 = 0xFFFFFF00;\n"
          + "checkerSize = 0.1;\n"
          + "uOffset = 0.0;\n"
          + "vOffset = 0.0;\n"
          + "uScale = 1.0;\n"
          + "vScale = 1.0;\n"
          + "isRepeatTexture = false;";
    if ("TransparentPNG".equals(type))
      return "imagePath = \"textures/turkeyFlag.png\";\n"
          + "billboardWidth = 20.0;\n"
          + "billboardHeight = 10.0;\n"
          + "uOffset = 0.0;\n"
          + "vOffset = 0.0;\n"
          + "uScale = 1.0;\n"
          + "vScale = 1.0;\n"
          + "isRepeatTexture = false;";
    if ("Triangle".equals(type))
      return "color1 = 0xFFFF0000;\n"
          + "color2 = 0xFF0000FF;\n"
          + "triangleSize = 1.0;\n"
          + "ambientCoefficient = 0.1;\n"
          + "diffuseCoefficient = 0.7;\n"
          + "specularCoefficient = 0.2;\n"
          + "shininess = 32.0;\n"
          + "reflectivity = 0.0;\n"
          + "ior = 1.0;\n"
          + "transparency = 0.0;";
    if ("TulipFjord".equals(type))
      return "tulipColor = 0xFFFF6347;\n"
          + "fjordColor = 0xFF00CED1;\n"
          + "stemColor = 0xFF228B22;\n"
          + "bloomIntensity = 1.0;";
    if ("TurkishTile".equals(type))
      return "baseColor = 0xFFFF6347;\npatternColor = 0xFF4682B4;\ntileSize = 1.0;";
    if ("VikingMetal".equals(type))
      return "baseColor = 0xFFC0C0C0;\nrustColor = 0xFF8B4513;\nrustDensity = 0.3;";
    if ("VikingRune".equals(type))
      return "stoneColor = 0xFF8B8B8B;\nruneColor = 0xFFFFD700;\nruneDepth = 0.1;";
    if ("Wave".equals(type))
      return "colorA = 0xFFFF0000;\n"
          + "colorB = 0xFF0000FF;\n"
          + "frequency = 2.5;\n"
          + "amplitude = 0.5;\n"
          + "specularStrength = 0.5;";
    if ("WoodGrain".equals(type)) return "woodColor = 0xFF8B4513;";
    if ("Word".equals(type))
      return "text = \"[U+2764]\";\n"
          + "foregroundColor = 0xFFFF0000;\n"
          + "backgroundColor = 0x100000AA;\n"
          + "fontName = \"default\";\n"
          + "fontSize = 30;\n"
          + "fontStyle = 1;\n"
          + "useGradient = true;\n"
          + "gradientColor = 0xFF00DD00;\n"
          + "width = 300;\n"
          + "height = 300;\n"
          + "uOffset = 0.0;\n"
          + "vOffset = -0.2;\n"
          + "billboardWidth = 5.0;\n"
          + "billboardHeight = 5.0;";
    if ("XRay".equals(type))
      return "baseColor = 0xFF00FFFF;\ntransparency = 0.8;\nreflectivity = 0.2;";
    return "";
  }

  /**
   * Creates a Material from text definition
   *
   * @param type the material type
   * @param text the definition text
   * @return the created Material
   */
  public static Material createMaterialFromText(String type, String text) {
    try {
      if ("AfricanKente".equals(type)) return new AfricanKenteMaterial();
      if ("Amber".equals(type)) {
        int baseColor = parseColor(text, "baseColor");
        double ior = parseDouble(text, "ior");
        double reflectivity = parseDouble(text, "reflectivity");
        double transparency = parseDouble(text, "transparency");
        return new AmberMaterial(baseColor, ior, reflectivity, transparency);
      }
      if ("AnodizedMetal".equals(type)) {
        int baseColor = parseColor(text, "baseColor");
        double ambientCoeff = parseDouble(text, "ambientCoeff");
        double diffuseCoeff = parseDouble(text, "diffuseCoeff");
        double specularCoeff = parseDouble(text, "specularCoeff");
        double shininess = parseDouble(text, "shininess");
        int specularColor = parseColor(text, "specularColor");
        double reflectivity = parseDouble(text, "reflectivity");
        double ior = parseDouble(text, "ior");
        double transparency = parseDouble(text, "transparency");
        double iridescenceStrength = parseDouble(text, "iridescenceStrength");
        double iridescenceSpeed = parseDouble(text, "iridescenceSpeed");
        return new AnodizedMetalMaterial(
            baseColor,
            ambientCoeff,
            diffuseCoeff,
            specularCoeff,
            shininess,
            specularColor,
            reflectivity,
            ior,
            transparency,
            iridescenceStrength,
            iridescenceSpeed);
      }
      if ("AuroraCeramic".equals(type)) {
        int base = parseColor(text, "baseColor");
        int aurora = parseColor(text, "auroraColor");
        double intensity = parseDouble(text, "auroraIntensity");
        return new AuroraCeramicMaterial(base, aurora, intensity);
      }
      if ("Baklava".equals(type)) {
        int pastryColor = parseColor(text, "pastryColor");
        int syrupColor = parseColor(text, "syrupColor");
        int nutColor = parseColor(text, "nutColor");
        double layers = parseDouble(text, "layers");
        double syrupiness = parseDouble(text, "syrupiness");
        return new BaklavaMaterial(
            pastryColor, syrupColor, nutColor, layers, syrupiness, Matrix4.identity());
      }
      if ("BrunostCheese".equals(type)) {
        int cheese = parseColor(text, "cheeseColor");
        int caramel = parseColor(text, "caramelColor");
        double amount = parseDouble(text, "caramelAmount");
        return new BrunostCheeseMaterial(cheese, caramel, amount);
      }
      if ("CalligraphyRune".equals(type)) {
        int parchment = parseColor(text, "parchmentColor");
        int ink = parseColor(text, "inkColor");
        int gold = parseColor(text, "goldLeafColor");
        double writing = parseDouble(text, "writingIntensity");
        return new CalligraphyRuneMaterial(parchment, ink, gold, writing);
      }
      if ("CarpetTexture".equals(type)) {
        int base = parseColor(text, "baseColor");
        int pattern = parseColor(text, "patternColor");
        return new CarpetTextureMaterial(base, pattern);
      }
      if ("CeramicTile".equals(type)) {
        int tileColor = parseColor(text, "tileColor");
        int groutColor = parseColor(text, "groutColor");
        double tileSize = parseDouble(text, "tileSize");
        double groutWidth = parseDouble(text, "groutWidth");
        double tileRoughness = parseDouble(text, "tileRoughness");
        double groutRoughness = parseDouble(text, "groutRoughness");
        double tileSpecular = parseDouble(text, "tileSpecular");
        double groutSpecular = parseDouble(text, "groutSpecular");
        double fresnelIntensity = parseDouble(text, "fresnelIntensity");
        double normalMicroFacet = parseDouble(text, "normalMicroFacet");
        double reflectionSharpness = parseDouble(text, "reflectionSharpness");
        double energyConservation = parseDouble(text, "energyConservation");
        return new CeramicTileMaterial(
            tileColor,
            groutColor,
            tileSize,
            groutWidth,
            tileRoughness,
            groutRoughness,
            tileSpecular,
            groutSpecular,
            fresnelIntensity,
            normalMicroFacet,
            reflectionSharpness,
            energyConservation);
      }
      if ("Checkerboard".equals(type)) {
        int c1 = parseColor(text, "color1");
        int c2 = parseColor(text, "color2");
        double size = parseDouble(text, "size");
        double ambientCoeff = parseDouble(text, "ambientCoeff");
        double diffuseCoeff = parseDouble(text, "diffuseCoeff");
        double specularCoeff = parseDouble(text, "specularCoeff");
        double shininess = parseDouble(text, "shininess");
        int specularColor = parseColor(text, "specularColor");
        double reflectivity = parseDouble(text, "reflectivity");
        double ior = parseDouble(text, "ior");
        double transparency = parseDouble(text, "transparency");
        return new CheckerboardMaterial(
            c1,
            c2,
            size,
            ambientCoeff,
            diffuseCoeff,
            specularCoeff,
            shininess,
            specularColor,
            reflectivity,
            ior,
            transparency,
            Matrix4.identity());
      }
      if ("Cini".equals(type)) {
        int base = parseColor(text, "baseColor");
        int pattern = parseColor(text, "patternColor");
        double glossiness = parseDouble(text, "glossiness");
        return new CiniMaterial(base, pattern, glossiness, Matrix4.identity());
      }
      if ("CircleTexture".equals(type)) {
        int solid = parseColor(text, "solidColor");
        int pattern = parseColor(text, "patternColor");
        double patternSize = parseDouble(text, "patternSize");
        double ambient = parseDouble(text, "ambientCoefficient");
        double diffuse = parseDouble(text, "diffuseCoefficient");
        double specular = parseDouble(text, "specularCoefficient");
        double shininess = parseDouble(text, "shininess");
        double reflectivity = parseDouble(text, "reflectivity");
        double ior = parseDouble(text, "ior");
        double transparency = parseDouble(text, "transparency");
        return new CircleTextureMaterial(
            solid,
            pattern,
            patternSize,
            ambient,
            diffuse,
            specular,
            shininess,
            reflectivity,
            ior,
            transparency,
            Matrix4.identity());
      }
      if ("CoffeeFjord".equals(type)) {
        int coffee = parseColor(text, "coffeeColor");
        int fjord = parseColor(text, "fjordColor");
        double blend = parseDouble(text, "blendIntensity");
        return new CoffeeFjordMaterial(coffee, fjord, blend);
      }
      if ("ColorDiffuse".equals(type)) {
        int baseColor = parseColor(text, "baseColor");
        double ior = parseDouble(text, "ior");
        double reflectivity = parseDouble(text, "reflectivity");
        double transparency = parseDouble(text, "transparency");
        return new ColorDiffuseMaterial(baseColor, ior, reflectivity, transparency);
      }
      if ("ColoredGlass".equals(type)) {
        int baseColor = parseColor(text, "baseColor");
        int specularColor = parseColor(text, "specularColor");
        double transparency = parseDouble(text, "transparency");
        double reflectivity = parseDouble(text, "reflectivity");
        double ior = parseDouble(text, "ior");
        double shininess = parseDouble(text, "shininess");
        double specularStrength = parseDouble(text, "specularStrength");
        return new ColoredGlassMaterial(
            baseColor, specularColor, transparency, reflectivity, ior, shininess, specularStrength);
      }
      if ("DamaskCeramic".equals(type)) {
        int primary = parseColor(text, "primary");
        int secondary = parseColor(text, "secondary");
        double shininess = parseDouble(text, "shininess");
        double ambient = parseDouble(text, "ambient");
        double specular = parseDouble(text, "specular");
        return new DamaskCeramicMaterial(
            primary, secondary, shininess, ambient, specular, Matrix4.identity());
      }
      if ("Diamond".equals(type)) {
        double ior = parseDouble(text, "ior");
        double reflectivity = parseDouble(text, "reflectivity");
        double transparency = parseDouble(text, "transparency");
        double dispersionStrength = parseDouble(text, "dispersionStrength");
        double fireEffect = parseDouble(text, "fireEffect");
        double shininess = parseDouble(text, "shininess");
        double specularStrength = parseDouble(text, "specularStrength");
        int baseColor = parseColor(text, "baseColor");
        int specularColor = parseColor(text, "specularColor");
        int fireColor1 = parseColor(text, "fireColor1");
        int fireColor2 = parseColor(text, "fireColor2");
        int fireColor3 = parseColor(text, "fireColor3");
        return new DiamondMaterial(
            baseColor,
            specularColor,
            ior,
            reflectivity,
            transparency,
            dispersionStrength,
            fireEffect,
            fireColor1,
            fireColor2,
            fireColor3);
      }
      if ("Dielectric".equals(type)) {
        int baseColor = parseColor(text, "baseColor");
        int specularColor = parseColor(text, "specularColor");
        double transparency = parseDouble(text, "transparency");
        double reflectivity = parseDouble(text, "reflectivity");
        double ior = parseDouble(text, "ior");
        double shininess = parseDouble(text, "shininess");
        double specularStrength = parseDouble(text, "specularStrength");
        return new DielectricMaterial(
            baseColor, specularColor, transparency, reflectivity, ior, shininess, specularStrength);
      }
      if ("DielectricText".equals(type)) {
        int baseColor = parseColor(text, "baseColor");
        int specularColor = parseColor(text, "specularColor");
        double transparency = parseDouble(text, "transparency");
        double reflectivity = parseDouble(text, "reflectivity");
        double ior = parseDouble(text, "ior");
        double shininess = parseDouble(text, "shininess");
        double specularStrength = parseDouble(text, "specularStrength");
        String overlayText = parseString(text, "overlayText");
        int textColor = parseColor(text, "textColor");
        int gradientColor = parseColor(text, "gradientColor");
        String gradientType = parseString(text, "gradientType");
        int bgColor = parseColor(text, "bgColor");
        String fontFamily = parseString(text, "fontFamily");
        int fontStyle = parseInt(text, "fontStyle");
        int fontSize = parseInt(text, "fontSize");
        int textUOffset = parseInt(text, "textUOffset");
        int textVOffset = parseInt(text, "textVOffset");
        String imagePath = parseString(text, "imagePath");
        int imageWidth = parseInt(text, "imageWidth");
        int imageHeight = parseInt(text, "imageHeight");
        int imageUOffset = parseInt(text, "imageUOffset");
        int imageVOffset = parseInt(text, "imageVOffset");
        Bitmap overlayImage = null;
        if (imagePath != null && !imagePath.isEmpty() && !"null".equals(imagePath)) {
          overlayImage = loadImage(imagePath);
        }
        boolean hasOverlay =
            (overlayText != null && !overlayText.isEmpty() && !"null".equals(overlayText))
                || overlayImage != null;
        DielectricTextMaterial dm;
        if (hasOverlay) {
          dm =
              new DielectricTextMaterial(
                  baseColor,
                  specularColor,
                  transparency,
                  reflectivity,
                  ior,
                  shininess,
                  specularStrength,
                  overlayText,
                  textColor,
                  gradientColor,
                  gradientType,
                  bgColor,
                  fontFamily,
                  fontStyle,
                  fontSize,
                  textUOffset,
                  textVOffset,
                  overlayImage,
                  imageWidth,
                  imageHeight,
                  imageUOffset,
                  imageVOffset);
        } else {
          dm =
              new DielectricTextMaterial(
                  baseColor,
                  specularColor,
                  transparency,
                  reflectivity,
                  ior,
                  shininess,
                  specularStrength);
        }
        if (imagePath != null && !imagePath.isEmpty() && !"null".equals(imagePath)) {
          dm.setImagePath(imagePath);
        }
        return dm;
      }
      if ("Diffuse".equals(type)) {
        int color = parseColor(text, "color");
        double diffuseCoefficient = parseDouble(text, "diffuseCoefficient");
        double reflectivity = parseDouble(text, "reflectivity");
        double ior = parseDouble(text, "ior");
        double transparency = parseDouble(text, "transparency");
        double roughness = parseDouble(text, "roughness");
        double ambientStrength = parseDouble(text, "ambientStrength");
        double rimStrength = parseDouble(text, "rimStrength");
        DiffuseMaterial mat =
            new DiffuseMaterial(color, diffuseCoefficient, reflectivity, ior, transparency);
        mat.setRoughness(roughness);
        mat.setAmbientStrength(ambientStrength);
        mat.setRimStrength(rimStrength);
        return mat;
      }
      if ("FilteredDielectric".equals(type)) {
        String wordT = parseString(text, "word");
        int textColorT = parseColor(text, "textColor");
        int gradientColorT = parseColor(text, "gradientColor");
        String gradientTypeT = parseString(text, "gradientType");
        int bgColorT = parseColor(text, "bgColor");
        String fontFamilyT = parseString(text, "fontFamily");
        int fontStyleT = parseInt(text, "fontStyle");
        int fontSizeT = parseInt(text, "fontSize");
        int uOffsetT = parseInt(text, "uOffset");
        int vOffsetT = parseInt(text, "vOffset");
        String imagePathT = parseString(text, "imagePath");
        int imageWidthT = parseInt(text, "imageWidth");
        int imageHeightT = parseInt(text, "imageHeight");
        int imageUOffsetT = parseInt(text, "imageUOffset");
        int imageVOffsetT = parseInt(text, "imageVOffset");
        int diffuseColorT = parseColor(text, "diffuseColor");
        double iorT = parseDouble(text, "ior");
        double transT = parseDouble(text, "transparency");
        double reflectT = parseDouble(text, "reflectivity");
        int filterColorInsideT = parseColor(text, "filterColorInside");
        int filterColorOutsideT = parseColor(text, "filterColorOutside");
        Bitmap imageObjectT = null;
        if (imagePathT != null && !imagePathT.isEmpty()) {
          imageObjectT = loadImage(imagePathT);
        }
        FilteredDielectricMaterial fdm =
            new FilteredDielectricMaterial(
                wordT,
                textColorT,
                gradientColorT,
                gradientTypeT,
                bgColorT,
                fontFamilyT,
                fontStyleT,
                fontSizeT,
                uOffsetT,
                vOffsetT,
                imageObjectT,
                imageWidthT,
                imageHeightT,
                imageUOffsetT,
                imageVOffsetT,
                diffuseColorT,
                iorT,
                transT,
                reflectT,
                filterColorInsideT,
                filterColorOutsideT);
        fdm.setImagePath(imagePathT);
        return fdm;
      }
      if ("FractalBark".equals(type)) {
        double roughness = parseDouble(text, "roughness");
        return new FractalBarkMaterial(Matrix4.identity(), roughness);
      }
      if ("FractalFire".equals(type)) {
        int iterations = parseInt(text, "iterations");
        double chaos = parseDouble(text, "chaos");
        double scale = parseDouble(text, "scale");
        double speed = parseDouble(text, "speed");
        return new FractalFireMaterial(iterations, chaos, scale, speed);
      }
      if ("Generated".equals(type)) {
        int color1 = parseColor(text, "color1");
        int color2 = parseColor(text, "color2");
        double ior = parseDouble(text, "ior");
        double reflectivity = parseDouble(text, "reflectivity");
        double transparency = parseDouble(text, "transparency");
        double shininess = parseDouble(text, "shininess");
        double roughness = parseDouble(text, "roughness");
        String patternStr = parseString(text, "pattern");
        double patternScale = parseDouble(text, "patternScale");
        double patternFrequency = parseDouble(text, "patternFrequency");
        String materialTypeStr = parseString(text, "materialType");
        boolean useFresnel = parseBoolean(text, "useFresnel");
        double fresnelF0 = parseDouble(text, "fresnelF0");
        boolean useNormalMap = parseBoolean(text, "useNormalMap");
        boolean useDisplacement = parseBoolean(text, "useDisplacement");
        double displacementScale = parseDouble(text, "displacementScale");
        PatternType pattern = PatternType.valueOf(patternStr);
        MaterialType materialType = MaterialType.valueOf(materialTypeStr);
        return new GeneratedMaterial(
            color1,
            color2,
            reflectivity,
            ior,
            transparency,
            shininess,
            roughness,
            pattern,
            patternScale,
            patternFrequency,
            materialType,
            useFresnel,
            fresnelF0,
            useNormalMap,
            useDisplacement,
            displacementScale);
      }
      if ("HamamSauna".equals(type)) {
        int marble = parseColor(text, "marbleColor");
        int wood = parseColor(text, "woodColor");
        int steam = parseColor(text, "steamColor");
        double steamInt = parseDouble(text, "steamIntensity");
        return new HamamSaunaMaterial(marble, wood, steam, steamInt);
      }
      if ("HexagonalHoneycomb".equals(type)) {
        int prim = parseColor(text, "primary");
        int sec = parseColor(text, "secondary");
        int borderColor = parseColor(text, "borderColor");
        double cellSize = parseDouble(text, "cellSize");
        double borderWidth = parseDouble(text, "borderWidth");
        double ambientStrength = parseDouble(text, "ambientStrength");
        double specularStrength = parseDouble(text, "specularStrength");
        double shininess = parseDouble(text, "shininess");
        double reflectivity = parseDouble(text, "reflectivity");
        return new HexagonalHoneycombMaterial(
            prim,
            sec,
            borderColor,
            cellSize,
            borderWidth,
            ambientStrength,
            specularStrength,
            shininess,
            reflectivity);
      }
      if ("Hokusai".equals(type)) {
        int waterColor = parseColor(text, "waterColor");
        int foamColor = parseColor(text, "foamColor");
        double scale = parseDouble(text, "scale");
        int mode = parseInt(text, "mode");
        double radius = parseDouble(text, "radius");
        double ambientCoefficient = parseDouble(text, "ambientCoefficient");
        double reflectivity = parseDouble(text, "reflectivity");
        return new HokusaiMaterial(
            waterColor,
            foamColor,
            scale,
            mode,
            radius,
            reflectivity,
            ambientCoefficient,
            Matrix4.identity());
      }
      if ("HotCopper".equals(type)) {
        int copper = parseColor(text, "copperColor");
        int patina = parseColor(text, "patinaColor");
        double patinaAmt = parseDouble(text, "patinaAmount");
        return new HotCopperMaterial(copper, patina, patinaAmt);
      }
      if ("ImageTexture".equals(type)) {
        String p = parseString(text, "imagePath");
        Bitmap im = loadImage(p);
        double uScale = parseDouble(text, "uScale");
        double vScale = parseDouble(text, "vScale");
        double uOffset = parseDouble(text, "uOffset");
        double vOffset = parseDouble(text, "vOffset");
        double ambientCoefficient = parseDouble(text, "ambientCoefficient");
        double diffuseCoefficient = parseDouble(text, "diffuseCoefficient");
        double specularCoefficient = parseDouble(text, "specularCoefficient");
        double shininess = parseDouble(text, "shininess");
        double reflectivity = parseDouble(text, "reflectivity");
        double ior = parseDouble(text, "ior");
        ImageTextureMaterial itm =
            new ImageTextureMaterial(
                im,
                uScale,
                vScale,
                uOffset,
                vOffset,
                ambientCoefficient,
                diffuseCoefficient,
                specularCoefficient,
                shininess,
                reflectivity,
                ior,
                Matrix4.identity());
        itm.setImagePath(p);
        return itm;
      }
      if ("IsotropicMetalText".equals(type)) {
        String imagePathT = parseString(text, "imagePath");
        int metalColorT = parseColor(text, "metalColor");
        double roughnessT = parseDouble(text, "roughness");
        double reflectivityT = parseDouble(text, "reflectivity");
        double iorT = parseDouble(text, "ior");
        double transT = parseDouble(text, "transparency");
        String wordT = parseString(text, "text");
        int textColorT = parseColor(text, "textColor");
        int gradientColorT = parseColor(text, "gradientColor");
        String gradientTypeT = parseString(text, "gradientType");
        int bgColorT = parseColor(text, "bgColor");
        String fontFamilyT = parseString(text, "fontFamily");
        int fontStyleT = parseInt(text, "fontStyle");
        int fontSizeT = parseInt(text, "fontSize");
        int uOffsetT = parseInt(text, "uOffset");
        int vOffsetT = parseInt(text, "vOffset");
        int imageWidthT = parseInt(text, "imageWidth");
        int imageHeightT = parseInt(text, "imageHeight");
        int imageUOffsetT = parseInt(text, "imageUOffset");
        int imageVOffsetT = parseInt(text, "imageVOffset");
        Bitmap imageObjectT = null;
        if (imagePathT != null && !imagePathT.isEmpty()) {
          imageObjectT = loadImage(imagePathT);
        }
        IsotropicMetalTextMaterial imtm =
            new IsotropicMetalTextMaterial(
                metalColorT,
                roughnessT,
                reflectivityT,
                iorT,
                transT,
                new Matrix4(),
                wordT,
                textColorT,
                gradientColorT,
                gradientTypeT,
                bgColorT,
                fontFamilyT,
                fontStyleT,
                fontSizeT,
                uOffsetT,
                vOffsetT,
                imageObjectT,
                imageWidthT,
                imageHeightT,
                imageUOffsetT,
                imageVOffsetT);
        imtm.setImagePath(imagePathT);
        return imtm;
      }
      if ("KilimRosemaling".equals(type)) {
        int kilim = parseColor(text, "kilimColor");
        int rosemaling = parseColor(text, "rosemalingColor");
        int accent = parseColor(text, "accentColor");
        double intensity = parseDouble(text, "patternIntensity");
        return new KilimRosemalingMaterial(kilim, rosemaling, accent, intensity);
      }
      if ("Lambert".equals(type)) {
        int col = parseColor(text, "color");
        double ambient = parseDouble(text, "ambient");
        double diffuse = parseDouble(text, "diffuse");
        double reflectivity = parseDouble(text, "reflectivity");
        return new LambertMaterial(col, ambient, diffuse, reflectivity);
      }
      if ("LavaFlow".equals(type)) {
        int hot = parseColor(text, "hotColor");
        int cool = parseColor(text, "coolColor");
        double speed = parseDouble(text, "flowSpeed");
        return new LavaFlowMaterial(hot, cool, speed, Matrix4.identity());
      }
      if ("Marble".equals(type)) {
        int baseM = parseColor(text, "baseColor");
        int vein = parseColor(text, "veinColor");
        double scale = parseDouble(text, "scale");
        double density = parseDouble(text, "veinDensity");
        double turbulence = parseDouble(text, "turbulence");
        double ambientCoefficient = parseDouble(text, "ambient");
        double diffuseCoefficient = parseDouble(text, "diffuse");
        double specularCoefficient = parseDouble(text, "specular");
        double shininess = parseDouble(text, "shininess");
        double reflectivity = parseDouble(text, "reflectivity");
        double ior = parseDouble(text, "ior");
        double transparency = parseDouble(text, "transparency");
        return new MarbleMaterial(
            baseM,
            vein,
            scale,
            density,
            turbulence,
            ambientCoefficient,
            diffuseCoefficient,
            specularCoefficient,
            shininess,
            reflectivity,
            ior,
            transparency,
            Matrix4.identity());
      }
      if ("Metallic".equals(type)) {
        int albedo = parseColor(text, "albedo");
        double metallic = parseDouble(text, "metallic");
        double roughness = parseDouble(text, "roughness");
        double reflectivity = parseDouble(text, "reflectivity");
        double ior = parseDouble(text, "ior");
        double transparency = parseDouble(text, "transparency");
        int emissionColor = parseColor(text, "emissionColor");
        double emissionIntensity = parseDouble(text, "emissionIntensity");
        double aoStrength = parseDouble(text, "aoStrength");
        double fresnelReflectance = parseDouble(text, "fresnelReflectance");
        return new MetallicMaterial(
            albedo,
            metallic,
            roughness,
            reflectivity,
            ior,
            transparency,
            emissionColor,
            emissionIntensity,
            aoStrength,
            fresnelReflectance,
            new Matrix4());
      }
      if ("MoonSurface".equals(type)) return new MoonSurfaceMaterial();
      if ("Mosaic".equals(type)) {
        int baseMos = parseColor(text, "baseColor");
        int tile = parseColor(text, "tileColor");
        double tileSize = parseDouble(text, "tileSize");
        double groutWidth = parseDouble(text, "groutWidth");
        double randomness = parseDouble(text, "randomness");
        return new MosaicMaterial(baseMos, tile, tileSize, groutWidth, randomness);
      }
      if ("Nazar".equals(type)) {
        int blueColor = parseColor(text, "blueColor");
        int whiteColor = parseColor(text, "whiteColor");
        double glowIntensity = parseDouble(text, "glowIntensity");
        double transparency = parseDouble(text, "transparency");
        double reflectivity = parseDouble(text, "reflectivity");
        return new NazarMaterial(
            blueColor, whiteColor, glowIntensity, transparency, reflectivity, new Matrix4());
      }
      if ("NordicWeave".equals(type)) {
        int primary = parseColor(text, "primaryColor");
        int secondary = parseColor(text, "secondaryColor");
        int accent = parseColor(text, "accentColor");
        double scale = parseDouble(text, "patternScale");
        return new NordicWeaveMaterial(primary, secondary, accent, scale);
      }
      if ("NordicWood".equals(type)) {
        int wood = parseColor(text, "woodColor");
        int grain = parseColor(text, "grainColor");
        double intensity = parseDouble(text, "grainIntensity");
        return new NordicWoodMaterial(wood, grain, intensity);
      }
      if ("NorwegianRose".equals(type)) {
        int wood = parseColor(text, "woodColor");
        int rose = parseColor(text, "roseColor");
        double scale = 4.0;
        if (text.contains("patternScale")) {
          scale = parseDouble(text, "patternScale");
        }
        return new NorwegianRoseMaterial(wood, rose, scale);
      }
      if ("OpticalIllusion".equals(type)) {
        int c1 = parseColor(text, "color1");
        int c2 = parseColor(text, "color2");
        double freq = parseDouble(text, "frequency");
        double smooth = parseDouble(text, "smoothness");
        return new OpticalIllusionMaterial(c1, c2, freq, smooth, Matrix4.identity());
      }
      if ("Orbital".equals(type)) {
        int center = parseColor(text, "centerColor");
        int orbit = parseColor(text, "orbitColor");
        double ringWidth = parseDouble(text, "ringWidth");
        int ringCount = parseInt(text, "ringCount");
        return new OrbitalMaterial(center, orbit, ringWidth, ringCount, new Matrix4());
      }
      if ("Phong".equals(type)) {
        int diffuse = parseColor(text, "diffuseColor");
        int specular = parseColor(text, "specularColor");
        double shin = parseDouble(text, "shininess");
        double amb = parseDouble(text, "ambientCoefficient");
        double diffCoeff = parseDouble(text, "diffuseCoefficient");
        double specCoeff = parseDouble(text, "specularCoefficient");
        double reflect = parseDouble(text, "reflectivity");
        double ior = parseDouble(text, "ior");
        double trans = parseDouble(text, "transparency");
        return new PhongMaterial(
            diffuse, specular, shin, amb, diffCoeff, specCoeff, reflect, ior, trans);
      }
      if ("PhongText".equals(type)) {
        String word = parseString(text, "word");
        int textColor = parseColor(text, "textColor");
        int gradientColor = parseColor(text, "gradientColor");
        String gradientType = parseString(text, "gradientType");
        int bgColor = parseColor(text, "bgColor");
        String fontFamily = parseString(text, "fontFamily");
        int fontStyle = parseInt(text, "fontStyle");
        int fontSize = parseInt(text, "fontSize");
        int uOffset = parseInt(text, "uOffset");
        int vOffset = parseInt(text, "vOffset");
        int imageWidth = parseInt(text, "imageWidth");
        int imageHeight = parseInt(text, "imageHeight");
        int imageUOffset = parseInt(text, "imageUOffset");
        int imageVOffset = parseInt(text, "imageVOffset");
        int diffuse = parseColor(text, "diffuseColor");
        int specular = parseColor(text, "specularColor");
        double shin = parseDouble(text, "shininess");
        double amb = parseDouble(text, "ambientCoefficient");
        double diffCoeff = parseDouble(text, "diffuseCoefficient");
        double specCoeff = parseDouble(text, "specularCoefficient");
        double reflect = parseDouble(text, "reflectivity");
        double ior = parseDouble(text, "ior");
        double trans = parseDouble(text, "transparency");
        return new PhongTextMaterial(
            word,
            textColor,
            gradientColor,
            gradientType,
            bgColor,
            fontFamily,
            fontStyle,
            fontSize,
            uOffset,
            vOffset,
            null,
            imageWidth,
            imageHeight,
            imageUOffset,
            imageVOffset,
            diffuse,
            specular,
            shin,
            amb,
            diffCoeff,
            specCoeff,
            reflect,
            ior,
            trans);
      }
      if ("PixelArt".equals(type)) {
        String paletteStr = parseString(text, "palette");
        String[] colors = paletteStr.replaceAll("[\\[\\]#]", "").split(",");
        int[] palette = new int[colors.length];
        for (int i = 0; i < colors.length; i++) {
          palette[i] = parseColor(colors[i].trim());
        }
        double pixelSize = parseDouble(text, "pixelSize");
        return new PixelArtMaterial(palette, pixelSize, Matrix4.identity());
      }
      if ("Rainbow".equals(type)) {
        int baseColor = parseColor(text, "baseColor");
        double rainbowIntensity = parseDouble(text, "rainbowIntensity");
        String rainbowMode = parseString(text, "rainbowMode");
        double rainbowScale = parseDouble(text, "rainbowScale");
        double rainbowOffset = parseDouble(text, "rainbowOffset");
        double rainbowSaturation = parseDouble(text, "rainbowSaturation");
        double reflectivity = parseDouble(text, "reflectivity");
        double transparency = parseDouble(text, "transparency");
        return new RainbowMaterial(
            baseColor,
            rainbowIntensity,
            rainbowMode,
            rainbowScale,
            rainbowOffset,
            rainbowSaturation,
            reflectivity,
            transparency);
      }
      if ("Raja".equals(type)) {
        String textureType = parseString(text, "textureType");
        int textureColor1 = parseColor(text, "textureColor");
        int checkerColor = parseColor(text, "checkerColor");
        double textureScale = parseDouble(text, "textureScale");
        double textureOffset = parseDouble(text, "textureOffset");
        String imagePath = parseString(text, "imagePath");
        double diffuse = parseDouble(text, "diffuse");
        double specular = parseDouble(text, "specular");
        double ior = parseDouble(text, "ior");
        double shininess = parseDouble(text, "shininess");
        double roughness = parseDouble(text, "roughness");
        double metalness = parseDouble(text, "metalness");
        double reflectivity = parseDouble(text, "reflectivity");
        double transparency = parseDouble(text, "transparency");
        boolean volumeChromaticAbsorption = parseBoolean(text, "volumeChromaticAbsorption");
        int volumeAbsorption = parseColor(text, "volumeAbsorption");
        int volumeScattering = parseColor(text, "volumeScattering");
        double volumeAbsorptionDensity = parseDouble(text, "volumeAbsorptionDensity");
        int volumeChromaticColor = parseColor(text, "volumeChromatic");
        double volumeChromaticDensity = parseDouble(text, "volumeChromaticDensity");
        double volumeScatteringDensity = parseDouble(text, "volumeScatteringDensity");
        boolean isEnteringHasPriority = parseBoolean(text, "isEnteringHasPriority");
        RajaTexture texture = null;
        if (textureType.startsWith("plain")) {
          texture = new PlainTexture(textureColor1);
        } else if (textureType.startsWith("damier")) {
          texture = new DamierTexture(textureColor1, checkerColor, textureScale, textureOffset);
        } else if (textureType.startsWith("photo")) {
          Bitmap image = loadImage(imagePath);
          texture = new PhotoTexture(image, imagePath);
        } else {
          texture = new PlainTexture(textureColor1);
        }
        Volume volume = null;
        if (volumeChromaticAbsorption) {
          if (volumeChromaticColor != 0x00000000
              || volumeChromaticDensity > 0.0
              || volumeScatteringDensity > 0.0
              || volumeScattering != 0x00000000) {
            volume =
                new Volume(
                    volumeChromaticColor,
                    volumeChromaticDensity,
                    volumeScattering,
                    volumeScatteringDensity,
                    isEnteringHasPriority);
          }
        } else {
          if (volumeAbsorptionDensity > 0.0
              || volumeScatteringDensity > 0.0
              || volumeAbsorption != 0x00000000
              || volumeScattering != 0x00000000) {
            volume =
                new Volume(
                    volumeAbsorption,
                    volumeScattering,
                    volumeAbsorptionDensity,
                    volumeScatteringDensity,
                    isEnteringHasPriority);
          }
        }
        return new RajaMaterial(
            texture,
            diffuse,
            specular,
            ior,
            shininess,
            roughness,
            metalness,
            reflectivity,
            transparency,
            volume);
      }
      if ("Random".equals(type)) return new RandomMaterial(Matrix4.identity());
      if ("RectangleChecker".equals(type)) {
        int c1 = parseColor(text, "color1");
        int c2 = parseColor(text, "color2");
        double w = parseDouble(text, "rectWidth");
        double h = parseDouble(text, "rectHeight");
        double ambient = parseDouble(text, "ambientCoefficient");
        double diffuse = parseDouble(text, "diffuseCoefficient");
        double specular = parseDouble(text, "specularCoefficient");
        double shininess = parseDouble(text, "shininess");
        double reflectivity = parseDouble(text, "reflectivity");
        double ior = parseDouble(text, "ior");
        double transparency = parseDouble(text, "transparency");
        return new RectangleCheckerMaterial(
            c1,
            c2,
            w,
            h,
            ambient,
            diffuse,
            specular,
            shininess,
            reflectivity,
            ior,
            transparency,
            Matrix4.identity());
      }
      if ("RuneStone".equals(type)) {
        int stone = parseColor(text, "stoneColor");
        int rune = parseColor(text, "runeColor");
        double density = parseDouble(text, "runeDensity");
        return new RuneStoneMaterial(stone, rune, density);
      }
      if ("Salmon".equals(type)) {
        int fleshColor = parseColor(text, "fleshColor");
        int skinColor = parseColor(text, "skinColor");
        int fatColor = parseColor(text, "fatColor");
        double freshness = parseDouble(text, "freshness");
        double oiliness = parseDouble(text, "oiliness");
        return new SalmonMaterial(fleshColor, skinColor, fatColor, freshness, oiliness);
      }
      if ("Simit".equals(type)) {
        int crustColor = parseColor(text, "crustColor");
        int sesameColor = parseColor(text, "sesameColor");
        int softColor = parseColor(text, "softColor");
        double crispiness = parseDouble(text, "crispiness");
        double sesameDensity = parseDouble(text, "sesameDensity");
        return new SimitMaterial(crustColor, sesameColor, softColor, crispiness, sesameDensity);
      }
      if ("SphereWordTexture".equals(type)) {
        String imagePath = parseString(text, "imagePath");
        String word = parseString(text, "word");
        int textColor = parseColor(text, "textColor");
        int gradientColor = parseColor(text, "gradientColor");
        String gradientType = parseString(text, "gradientType");
        int bgColor = parseColor(text, "bgColor");
        String fontFamily = parseString(text, "fontFamily");
        int fontStyle = parseInt(text, "fontStyle");
        int fontSize = parseInt(text, "fontSize");
        double reflectivity = parseDouble(text, "reflectivity");
        double ior = parseDouble(text, "ior");
        double transparency = parseDouble(text, "transparency");
        int uOffset = parseInt(text, "uOffset");
        int vOffset = parseInt(text, "vOffset");
        int imageWidth = parseInt(text, "imageWidth");
        int imageHeight = parseInt(text, "imageHeight");
        int imageUOffset = parseInt(text, "imageUOffset");
        int imageVOffset = parseInt(text, "imageVOffset");
        boolean followObjectRotation = parseBoolean(text, "followObjectRotation");
        Bitmap imageObject = null;
        if (imagePath != null && !imagePath.isEmpty()) {
          imageObject = loadImage(imagePath);
        }
        SphereWordTextureMaterial swtm =
            new SphereWordTextureMaterial(
                word,
                textColor,
                gradientColor,
                gradientType,
                bgColor,
                fontFamily,
                fontStyle,
                fontSize,
                reflectivity,
                ior,
                transparency,
                uOffset,
                vOffset,
                imageObject,
                imageWidth,
                imageHeight,
                imageUOffset,
                imageVOffset,
                followObjectRotation);
        if (imagePath != null && !imagePath.isEmpty()) {
          swtm.setImagePath(imagePath);
        }
        return swtm;
      }
      if ("StarryNight".equals(type)) return new StarryNightMaterial();
      if ("Striped".equals(type)) {
        int c1 = parseColor(text, "color1");
        int c2 = parseColor(text, "color2");
        double size = parseDouble(text, "stripeSize");
        double ambient = parseDouble(text, "ambientCoefficient");
        double diffuse = parseDouble(text, "diffuseCoefficient");
        double specular = parseDouble(text, "specularCoefficient");
        double shininess = parseDouble(text, "shininess");
        double reflectivity = parseDouble(text, "reflectivity");
        double ior = parseDouble(text, "ior");
        double transparency = parseDouble(text, "transparency");
        StripeDirection dir = StripeDirection.VERTICAL;
        if (text.contains("direction")) {
          String dirStr = parseString(text, "direction");
          try {
            dir = StripeDirection.valueOf(dirStr.toUpperCase());
          } catch (Exception e) {
            dir = StripeDirection.VERTICAL;
          }
        }
        return new StripedMaterial(
            c1,
            c2,
            size,
            dir,
            ambient,
            diffuse,
            specular,
            shininess,
            reflectivity,
            ior,
            transparency);
      }
      if ("TelemarkPattern".equals(type)) {
        int base = parseColor(text, "baseColor");
        int pattern = parseColor(text, "patternColor");
        int accent = parseColor(text, "accentColor");
        double scale = parseDouble(text, "patternScale");
        return new TelemarkPatternMaterial(base, pattern, accent, scale);
      }
      if ("TextDielectric".equals(type)) {
        String wordT = parseString(text, "word");
        int textColorT = parseColor(text, "textColor");
        int gradientColorT = parseColor(text, "gradientColor");
        String gradientTypeT = parseString(text, "gradientType");
        int bgColorT = parseColor(text, "bgColor");
        String fontFamilyT = parseString(text, "fontFamily");
        int fontStyleT = parseInt(text, "fontStyle");
        int fontSizeT = parseInt(text, "fontSize");
        int uOffsetT = parseInt(text, "uOffset");
        int vOffsetT = parseInt(text, "vOffset");
        String imagePathT = parseString(text, "imagePath");
        int imageWidthT = parseInt(text, "imageWidth");
        int imageHeightT = parseInt(text, "imageHeight");
        int imageUOffsetT = parseInt(text, "imageUOffset");
        int imageVOffsetT = parseInt(text, "imageVOffset");
        int diffuseColorT = parseColor(text, "diffuseColor");
        double iorT = parseDouble(text, "ior");
        double transT = parseDouble(text, "transparency");
        double reflectT = parseDouble(text, "reflectivity");
        int filterColorInsideT = parseColor(text, "filterColorInside");
        int filterColorOutsideT = parseColor(text, "filterColorOutside");
        Bitmap imageObjectT = null;
        if (imagePathT != null && !imagePathT.isEmpty()) {
          imageObjectT = loadImage(imagePathT);
        }
        TextDielectricMaterial tdm =
            new TextDielectricMaterial(
                wordT,
                textColorT,
                gradientColorT,
                gradientTypeT,
                bgColorT,
                fontFamilyT,
                fontStyleT,
                fontSizeT,
                uOffsetT,
                vOffsetT,
                imageObjectT,
                imageWidthT,
                imageHeightT,
                imageUOffsetT,
                imageVOffsetT,
                diffuseColorT,
                iorT,
                transT,
                reflectT,
                filterColorInsideT,
                filterColorOutsideT);
        tdm.setImagePath(imagePathT);

        // Optional extras: each line may be missing, then the class default is kept.
        // Filter tuning first, texture-rebuilding switches last (they rebuild the texture once).
        Double v;
        if ((v = optDouble(text, "filterBaseStrength")) != null) tdm.setFilterBaseStrength(v);
        if ((v = optDouble(text, "filterFresnelBoost")) != null) tdm.setFilterFresnelBoost(v);
        if ((v = optDouble(text, "filterMaxStrength")) != null) tdm.setFilterMaxStrength(v);
        if ((v = optDouble(text, "filterInkStrength")) != null) tdm.setFilterInkStrength(v);
        if ((v = optDouble(text, "filterInsideCutoff")) != null) tdm.setFilterInsideCutoff(v);
        if ((v = optDouble(text, "filterInsideRamp")) != null) tdm.setFilterInsideRamp(v);

        if (optBoolean(text, "fullWidthGradient", false)) tdm.setFullWidthGradient(true);
        if (optBoolean(text, "highQuality", false)) tdm.setHighQuality(true);

        return tdm;
      }
      if ("TexturedCheckerboard".equals(type)) {
        int c1 = parseColor(text, "color1");
        int c2 = parseColor(text, "color2");
        double size = parseDouble(text, "size");
        String txt = parseString(text, "text");
        int textColor = parseColor(text, "textColor");
        int gradientColor = parseColor(text, "gradientColor");
        String gradientType = parseString(text, "gradientType");
        int bgColor = parseColor(text, "bgColor");
        String fontFamily = parseString(text, "fontFamily");
        int fontStyle = parseInt(text, "fontStyle");
        int fontSize = parseInt(text, "fontSize");
        int textUOffset = parseInt(text, "textUOffset");
        int textVOffset = parseInt(text, "textVOffset");
        String imagePath = parseString(text, "imagePath");
        int imageWidth = parseInt(text, "imageWidth");
        int imageHeight = parseInt(text, "imageHeight");
        int imageUOffset = parseInt(text, "imageUOffset");
        int imageVOffset = parseInt(text, "imageVOffset");
        double ambientCoeff = parseDouble(text, "ambientCoeff");
        double diffuseCoeff = parseDouble(text, "diffuseCoeff");
        double specularCoeff = parseDouble(text, "specularCoeff");
        double shininess = parseDouble(text, "shininess");
        int specularColor = parseColor(text, "specularColor");
        double reflectivity = parseDouble(text, "reflectivity");
        double ior = parseDouble(text, "ior");
        double transparency = parseDouble(text, "transparency");
        Bitmap imageObject = null;
        if (imagePath != null && !imagePath.isEmpty()) {
          imageObject = loadImage(imagePath);
        }
        TexturedCheckerboardMaterial tcm =
            new TexturedCheckerboardMaterial(
                c1,
                c2,
                size,
                txt,
                textColor,
                gradientColor,
                gradientType,
                bgColor,
                fontFamily,
                fontStyle,
                fontSize,
                textUOffset,
                textVOffset,
                imageObject,
                imageWidth,
                imageHeight,
                imageUOffset,
                imageVOffset,
                ambientCoeff,
                diffuseCoeff,
                specularCoeff,
                shininess,
                specularColor,
                reflectivity,
                ior,
                transparency,
                Matrix4.identity());
        tcm.setImagePath(imagePath);
        return tcm;
      }
      if ("TexturedPhong".equals(type)) {
        int base = parseColor(text, "baseDiffuseColor");
        String imgPath = parseString(text, "imagePath");
        Bitmap img = loadImage(imgPath);
        double trans = parseDouble(text, "transparency");
        int spec = parseColor(text, "specularColor");
        double shin = parseDouble(text, "shininess");
        double amb = parseDouble(text, "ambientCoefficient");
        double diff = parseDouble(text, "diffuseCoefficient");
        double specCoeff = parseDouble(text, "specularCoefficient");
        double reflect = parseDouble(text, "reflectivity");
        double ior = parseDouble(text, "ior");
        double uOffset = parseDouble(text, "uOffset");
        double vOffset = parseDouble(text, "vOffset");
        double uScale = parseDouble(text, "uScale");
        double vScale = parseDouble(text, "vScale");
        TexturedPhongMaterial tmt =
            new TexturedPhongMaterial(
                base,
                spec,
                shin,
                amb,
                diff,
                specCoeff,
                reflect,
                ior,
                trans,
                img,
                uOffset,
                vOffset,
                uScale,
                vScale,
                new Matrix4());
        tmt.setImagePath(imgPath);
        return tmt;
      }
      if ("Texture".equals(type)) {
        String imagePath = parseString(text, "imagePath");
        Bitmap img = loadImage(imagePath);
        boolean isTile = parseBoolean(text, "isTile");
        boolean sphericalMapping = parseBoolean(text, "sphericalMapping");
        double uOffset = parseDouble(text, "uOffset");
        double vOffset = parseDouble(text, "vOffset");
        double uScale = parseDouble(text, "uScale");
        double vScale = parseDouble(text, "vScale");
        TextureMaterial tm = new TextureMaterial(img, isTile, sphericalMapping);
        tm.setImagePath(imagePath);
        tm.setUVOffsetScale(uOffset, vOffset, uScale, vScale);
        return tm;
      }
      if ("Translucent".equals(type)) {
        int baseColor = parseColor(text, "baseColor");
        int attenuation = parseColor(text, "attenuation");
        double opacity = parseDouble(text, "opacity");
        double roughness = parseDouble(text, "roughness");
        double ior = parseDouble(text, "ior");
        double reflectivity = parseDouble(text, "reflectivity");
        double transparency = parseDouble(text, "transparency");
        double shininess = parseDouble(text, "shininess");
        double specularStrength = parseDouble(text, "specularStrength");
        return new TranslucentMaterial(
            baseColor,
            attenuation,
            opacity,
            roughness,
            ior,
            reflectivity,
            shininess,
            specularStrength);
      }
      if ("TransparentEmoji".equals(type)) {
        String imgP = parseString(text, "imagePath");
        Bitmap img = loadImage(imgP);
        double bwidth = parseDouble(text, "billboardWidth");
        double bheight = parseDouble(text, "billboardHeight");
        int c1 = parseColor(text, "checkerColor1");
        int c2 = parseColor(text, "checkerColor2");
        double size = parseDouble(text, "checkerSize");
        double uOff = parseDouble(text, "uOffset");
        double vOff = parseDouble(text, "vOffset");
        double uScale = parseDouble(text, "uScale");
        double vScale = parseDouble(text, "vScale");
        boolean repeat = parseBoolean(text, "isRepeatTexture");
        boolean messy = false;
        TransparentEmojiMaterial tem =
            new TransparentEmojiMaterial(
                img, c1, c2, size, uOff, vOff, uScale, vScale, repeat, messy);
        tem.setImagePath(imgP);
        tem.setBillboardHeight(bheight);
        tem.setBillboardWidth(bwidth);
        return tem;
      }
      if ("TransparentPNG".equals(type)) {
        String path = parseString(text, "imagePath");
        Bitmap image = loadImage(path);
        double bwidth = parseDouble(text, "billboardWidth");
        double bheight = parseDouble(text, "billboardHeight");
        double uOff = parseDouble(text, "uOffset");
        double vOff = parseDouble(text, "vOffset");
        double uScale = parseDouble(text, "uScale");
        double vScale = parseDouble(text, "vScale");
        boolean repeat = parseBoolean(text, "isRepeatTexture");
        TransparentPNGMaterial pm =
            new TransparentPNGMaterial(image, uOff, vOff, uScale, vScale, repeat);
        pm.setImagePath(path);
        pm.setBillboardHeight(bheight);
        pm.setBillboardWidth(bwidth);
        return pm;
      }
      if ("Triangle".equals(type)) {
        int c1 = parseColor(text, "color1");
        int c2 = parseColor(text, "color2");
        double size = parseDouble(text, "triangleSize");
        double ambientCoefficient = parseDouble(text, "ambientCoefficient");
        double diffuseCoefficient = parseDouble(text, "diffuseCoefficient");
        double specularCoefficient = parseDouble(text, "specularCoefficient");
        double shininess = parseDouble(text, "shininess");
        double reflectivity = parseDouble(text, "reflectivity");
        double ior = parseDouble(text, "ior");
        double transparency = parseDouble(text, "transparency");
        return new TriangleMaterial(
            c1,
            c2,
            size,
            ambientCoefficient,
            diffuseCoefficient,
            specularCoefficient,
            shininess,
            reflectivity,
            ior,
            transparency,
            Matrix4.identity());
      }
      if ("TulipFjord".equals(type)) {
        int tulip = parseColor(text, "tulipColor");
        int fjord = parseColor(text, "fjordColor");
        int stem = parseColor(text, "stemColor");
        double bloom = parseDouble(text, "bloomIntensity");
        return new TulipFjordMaterial(tulip, fjord, stem, bloom);
      }
      if ("TurkishTile".equals(type)) {
        int base = parseColor(text, "baseColor");
        int pattern = parseColor(text, "patternColor");
        double tileSize = parseDouble(text, "tileSize");
        return new TurkishTileMaterial(base, pattern, tileSize);
      }
      if ("VikingMetal".equals(type)) {
        int base = parseColor(text, "baseColor");
        int rust = parseColor(text, "rustColor");
        double density = parseDouble(text, "rustDensity");
        return new VikingMetalMaterial(base, rust, density);
      }
      if ("VikingRune".equals(type)) {
        int stone = parseColor(text, "stoneColor");
        int rune = parseColor(text, "runeColor");
        double depth = parseDouble(text, "runeDepth");
        return new VikingRuneMaterial(stone, rune, depth);
      }
      if ("Wave".equals(type)) {
        int colorA = parseColor(text, "colorA");
        int colorB = parseColor(text, "colorB");
        double frequency = parseDouble(text, "frequency");
        double amplitude = parseDouble(text, "amplitude");
        double specularStrength = parseDouble(text, "specularStrength");
        return new WaveMaterial(colorA, colorB, frequency, amplitude, specularStrength);
      }
      if ("WoodGrain".equals(type)) {
        int wcolor = parseColor(text, "woodColor");
        return new WoodGrainMaterial(wcolor);
      }
      if ("Word".equals(type)) {
        String textStr = parseString(text, "text");
        int fg = parseColor(text, "foregroundColor");
        int bg = parseColor(text, "backgroundColor");
        String fontName = parseString(text, "fontName");
        int fontSize = parseInt(text, "fontSize");
        int fontStyle = parseInt(text, "fontStyle");
        boolean useGradient = parseBoolean(text, "useGradient");
        int gradientColor = parseColor(text, "gradientColor");
        int width = parseInt(text, "width");
        int height = parseInt(text, "height");
        double uOffset = parseDouble(text, "uOffset");
        double vOffset = parseDouble(text, "vOffset");
        double billboardWidth = parseDouble(text, "billboardWidth");
        double billboardHeight = parseDouble(text, "billboardHeight");
        WordMaterial mw =
            new WordMaterial(
                textStr,
                fg,
                bg,
                fontName,
                fontStyle,
                fontSize,
                useGradient,
                gradientColor,
                null,
                width,
                height,
                uOffset,
                vOffset);
        mw.setBillboardWidth(billboardWidth);
        mw.setBillboardHeight(billboardHeight);
        return mw;
      }
      if ("XRay".equals(type)) {
        int base = parseColor(text, "baseColor");
        double trans = parseDouble(text, "transparency");
        double reflect = parseDouble(text, "reflectivity");
        return new XRayMaterial(base, trans, reflect);
      }
      if ("Custom".equals(type)) {
        String className = parseString(text, "className");
        Material mat = loadCustomMaterial(className);
        return mat;
      }
      return new AmberMaterial();
    } catch (Exception e) {
      Log.e(TAG, "Material parse error: " + e.getMessage(), e);
      return new AmberMaterial();
    }
  }

  public static EMShape loadCustomShape(String classPath) {
    Log.w(TAG, "loadCustomShape not supported on Android: " + classPath);
    return null;
  }

  public static Material loadCustomMaterial(String classPath) {
    Log.w(TAG, "loadCustomMaterial not supported on Android: " + classPath);
    return null;
  }

  public static Light loadCustomLight(String classPath) {
    Log.w(TAG, "loadCustomLight not supported on Android: " + classPath);
    return null;
  }

  /**
   * Converts a hexadecimal color string into a 32-bit ARGB integer.
   *
   * <p>Supports formats: "0xaarrggbb", "0XAARRGGBB", "aarrggbb", or "AARRGGBB". Performance
   * optimized using standard Java 6-8 compatible parsing.
   *
   * @param colorStr The hexadecimal representation of the color.
   * @return The parsed 32-bit ARGB integer value.
   */
  private static int getColorARGB(String colorStr) {
    if (colorStr == null) {
      return 0; // Return black/transparent if null
    }

    // Remove whitespace and convert to lowercase for uniform parsing
    String cleaned = colorStr.trim().toLowerCase();

    // Strip the "0x" prefix if present
    if (cleaned.startsWith("0x")) {
      cleaned = cleaned.substring(2);
    }

    // Parse as an unsigned long to safely handle alpha channel overflow in standard integer
    // radix 16 specifies hexadecimal base
    long parsedLong = Long.parseLong(cleaned, 16);

    // Cast to a standard 32-bit signed integer (ARGB format)
    return (int) parsedLong;
  }

  public static EMShape parseCSGShape(String text) {
    try {
      Log.d(TAG, "=== STARTING CSG PARSING ===");

      // CSG type
      String csgType = text.split("\\s+")[0];
      Log.d(TAG, "CSG Type: " + csgType);

      // Main blocks
      int firstBrace = text.indexOf('{');
      int lastBrace = text.lastIndexOf('}');

      if (firstBrace == -1 || lastBrace == -1) {
        Log.e(TAG, "CSG parse error: No braces found");
        return null;
      }

      String content = text.substring(firstBrace + 1, lastBrace).trim();
      Log.d(TAG, "CSG Content length: " + content.length());

      // Left and right blocks
      CSGBlocks blocks = extractCSGBlocks(content);
      if (blocks == null) {
        Log.e(TAG, "CSG parse error: Could not extract left/right blocks");
        return null;
      }

      // Left
      EMShape leftShape = parseCSGSubShape(blocks.leftBlock);
      if (leftShape == null) {
        Log.e(TAG, "CSG parse error: Failed to parse left shape");
        return null;
      }

      // Right
      EMShape rightShape = parseCSGSubShape(blocks.rightBlock);
      if (rightShape == null) {
        Log.e(TAG, "CSG parse error: Failed to parse right shape");
        return null;
      }

      // Transforms parse
      Matrix4 transform = parseCSGTransform(blocks.transformBlock);
      Matrix4 a1Transform = parseCSGTransform(blocks.a1TransformBlock);
      Matrix4 a2Transform = parseCSGTransform(blocks.a2TransformBlock);

      // Material parse
      // Material material = parseCSGMaterial(blocks.materialBlock);

      // Material parse: only a CSG-level "material =" line overrides the children
      Material material = null;
      if (blocks.materialBlock != null && !blocks.materialBlock.isEmpty()) {
        material = parseCSGMaterial(blocks.materialBlock);
      }
      if (material == null) {
        // No shared material: children keep their own. Amber only if a child has none.
        if (leftShape.getMaterial() == null) {
          leftShape.setMaterial(new AmberMaterial());
        }
        if (rightShape.getMaterial() == null) {
          rightShape.setMaterial(new AmberMaterial());
        }
      }

      int shdw = getColorARGB(blocks.shadowColorBlock);
      double bias = parseDouble(blocks.shadowBiasBlock);
      String otherAnimationInfo = blocks.otherAnimationInfoBlock;
      boolean isVisible = parseBoolean(blocks.isVisibleBlock);
      boolean isShadowEnable = parseBoolean(blocks.isShadowEnableBlock);
      boolean isShadowOnly = parseBoolean(blocks.isShadowOnlyBlock);
      boolean isReflective = parseBoolean(blocks.isReflectiveBlock);
      boolean isRefractive = parseBoolean(blocks.isRefractiveBlock);

      // CSG shape
      EMShape csgShape = createCSGShape(csgType, leftShape, rightShape);
      if (csgShape == null) {
        return null;
      }

      csgShape.setTransform(transform);
      csgShape.setAnimationTransforms(new Matrix4[] {a1Transform, a2Transform});
      csgShape.setShadowColor(shdw);
      csgShape.setShadowBias(bias);
      csgShape.setOtherAnimationInfo(otherAnimationInfo);
      csgShape.setVisible(isVisible);
      csgShape.setShadowEnable(isShadowEnable);
      csgShape.setShadowOnly(isShadowOnly);
      csgShape.setReflective(isReflective);
      csgShape.setRefractive(isRefractive);

      if (material != null) {
        csgShape.setMaterial(material);
      }

      Log.d(TAG, "=== CSG PARSING SUCCESSFUL ===");
      return csgShape;

    } catch (Exception e) {
      Log.e(TAG, "CSG parse error: " + e.getMessage(), e);
      return null;
    }
  }

  private static class CSGBlocks {
    String leftBlock;
    String rightBlock;
    String transformBlock;
    String a1TransformBlock;
    String a2TransformBlock;
    String materialBlock;
    String shadowColorBlock;
    String shadowBiasBlock;
    String otherAnimationInfoBlock;
    String isVisibleBlock;
    String isShadowOnlyBlock;
    String isShadowEnableBlock;
    String isReflectiveBlock;
    String isRefractiveBlock;
  }

  private static CSGBlocks extractCSGBlocks(String content) {
    CSGBlocks blocks = new CSGBlocks();

    try {
      // Left block
      int leftStart = content.indexOf("left = ");
      if (leftStart == -1) return null;

      String afterLeft = content.substring(leftStart + 7);
      blocks.leftBlock = extractCompleteBlock(afterLeft);
      if (blocks.leftBlock == null) return null;

      // Right block
      // The extracted block is trimmed, so its length may not match the raw
      // text. Locate it in the raw text and cut from its real end.
      int leftEnd = afterLeft.indexOf(blocks.leftBlock) + blocks.leftBlock.length();
      if (leftEnd < blocks.leftBlock.length()) return null; // safety
      String afterLeftBlock = afterLeft.substring(leftEnd);

      int rightStart = afterLeftBlock.indexOf("right = ");
      if (rightStart == -1) return null;

      String afterRight = afterLeftBlock.substring(rightStart + 8);
      blocks.rightBlock = extractCompleteBlock(afterRight);
      if (blocks.rightBlock == null) return null;

      // Remaining parts (transform and material)
      // Same trim mismatch as above: locate the right block in the raw text.
      int rightEnd = afterRight.indexOf(blocks.rightBlock) + blocks.rightBlock.length();
      if (rightEnd < blocks.rightBlock.length()) return null; // safety
      String remaining = afterRight.substring(rightEnd);

      // Transform blocks
      blocks.transformBlock = extractLineAfter(remaining, "transform = ");
      blocks.a1TransformBlock = extractLineAfter(remaining, "firstAnimMatrix = ");
      blocks.a2TransformBlock = extractLineAfter(remaining, "secondAnimMatrix = ");

      blocks.shadowColorBlock = extractLineAfter(remaining, "shadowColor = ");
      blocks.shadowBiasBlock = extractLineAfter(remaining, "shadowBias = ");

      blocks.otherAnimationInfoBlock = extractLineAfter(remaining, "otherAnimationInfo = ");

      blocks.isVisibleBlock = extractLineAfter(remaining, "isVisible = ");
      blocks.isShadowEnableBlock = extractLineAfter(remaining, "isShadowEnable = ");
      blocks.isShadowOnlyBlock = extractLineAfter(remaining, "isShadowOnly = ");
      blocks.isReflectiveBlock = extractLineAfter(remaining, "isReflective = ");
      blocks.isRefractiveBlock = extractLineAfter(remaining, "isRefractive = ");

      // Material block
      int materialStart = remaining.indexOf("material = ");
      if (materialStart != -1) {
        String materialContent = remaining.substring(materialStart + 11);
        blocks.materialBlock = extractCompleteBlock(materialContent);
      }

      return blocks;

    } catch (Exception e) {
      Log.e(TAG, "Block extraction error: " + e.getMessage(), e);
      return null;
    }
  }

  private static String extractCompleteBlock(String content) {
    content = content.trim();
    if (!content.contains("{")) {
      // Simple (Sphere, Box vb.)
      int semicolon = content.indexOf(';');
      if (semicolon != -1) {
        return content.substring(0, semicolon).trim();
      }
      return null;
    }

    // CSG or complex shapes
    int braceCount = 0;
    int startIndex = content.indexOf('{');
    int endIndex = -1;

    for (int i = startIndex; i < content.length(); i++) {
      char c = content.charAt(i);
      if (c == '{') braceCount++;
      if (c == '}') braceCount--;

      if (braceCount == 0) {
        endIndex = i;
        break;
      }
    }

    if (endIndex == -1) return null;
    return content.substring(0, endIndex + 1).trim();
  }

  private static String extractLineAfter(String content, String keyword) {
    int start = content.indexOf(keyword);
    if (start == -1) return "";

    String afterKeyword = content.substring(start + keyword.length());
    int end = afterKeyword.indexOf(';');
    if (end == -1) return "";

    return afterKeyword.substring(0, end).trim();
  }

  private static EMShape parseCSGSubShape(String block) {
    block = block.trim();

    // If is CSG
    if (block.contains("CSG") && block.contains("{")) {
      String[] lines = block.split("\n");
      if (lines.length > 0) {
        String firstLine = lines[0].trim();
        String shapeType = firstLine.split("\\s+")[0];

        // CSG recursive
        if (shapeType.contains("CSG")) {
          return parseCSGShape(block);
        }
      }
    }

    // Normal parse
    String[] lines = block.split("\n");
    if (lines.length == 0) return null;

    String firstLine = lines[0].trim();
    String[] parts = firstLine.split("\\s+");
    if (parts.length < 2) return null;

    String shapeType = parts[0];

    // EMShape shape = createShapeFromText(shapeType, block);
    // if (shape != null) {
    // Transforms
    // Matrix4 transform = parseTransformFromShapeBlock(block);
    // shape.setTransform(transform);
    // }

    // return shape;

    EMShape shape = createShapeFromText(shapeType, block);
    if (shape != null) {
      Matrix4 transform = parseTransformFromShapeBlock(block);
      shape.setTransform(transform);

      // Child's own material (optional)
      Material childMaterial = extractChildMaterial(block);
      if (childMaterial != null) {
        shape.setMaterial(childMaterial);
      }
    }

    return shape;
  }

  /** Reads "material = Type name { ... }" from a child shape block, or null if absent. */
  private static Material extractChildMaterial(String block) {
    int idx = block.indexOf("material = ");
    if (idx < 0) {
      return null;
    }
    String mb = extractCompleteBlock(block.substring(idx + "material = ".length()));
    if (mb == null || mb.isEmpty()) {
      return null;
    }
    return parseCSGMaterial(mb);
  }

  private static Matrix4 parseCSGTransform(String transformBlock) {
    try {
      if (transformBlock == null || transformBlock.isEmpty()) {
        return Matrix4.identity();
      }
      return Matrix4.createMatrixFromString(transformBlock);
    } catch (Exception e) {
      Log.e(TAG, "CSG transform parse error: " + e.getMessage(), e);
      return Matrix4.identity();
    }
  }

  private static Material parseCSGMaterial(String materialBlock) {
    try {
      if (materialBlock == null || materialBlock.isEmpty()) {
        return new AmberMaterial();
      }

      String[] lines = materialBlock.split("\n");
      if (lines.length == 0) return new AmberMaterial();

      String firstLine = lines[0].trim();
      String materialType = firstLine.split("\\s+")[0];

      // "Material" suffix remove
      if (materialType.endsWith("Material")) {
        materialType = materialType.substring(0, materialType.length() - "Material".length());
      }

      return createMaterialFromText(materialType, materialBlock);
    } catch (Exception e) {
      Log.e(TAG, "CSG material parse error: " + e.getMessage(), e);
      return new AmberMaterial();
    }
  }

  private static EMShape createCSGShape(String csgType, EMShape left, EMShape right) {
    switch (csgType) {
      case "UnionCSG":
        return new UnionCSG(left, right);
      case "IntersectionCSG":
        return new IntersectionCSG(left, right);
      case "DifferenceCSG":
        return new DifferenceCSG(left, right);
      default:
        Log.e(TAG, "Unknown CSG type: " + csgType);
        return null;
    }
  }

  /**
   * Finds matching bracket
   *
   * @param text the text to search
   * @param startIndex the start index
   * @return the matching bracket index
   */
  private static int findMatchingBracket(String text, int startIndex) {
    if (startIndex < 0 || startIndex >= text.length()) return -1;

    int count = 1;
    for (int i = startIndex + 1; i < text.length(); i++) {
      char c = text.charAt(i);
      if (c == '{') count++;
      if (c == '}') count--;
      if (count == 0) return i;
    }
    return -1;
  }

  public static double parseDouble(String text) {
    try {
      return Double.parseDouble(text.trim());
    } catch (Exception e) {
      return 0.0;
    }
  }

  /** Parses double array from text Format: [x, y, z] or [a, r, g, b] */
  private static double[] parseDoubleArray(String text, String key) {
    String value = parseString(text, key);
    if (value == null || value.isEmpty()) {
      return new double[] {0.0, 0.0, 0.0};
    }
    String cleaned = value.replace("[", "").replace("]", "").trim();
    String[] parts = cleaned.split(",");
    double[] result = new double[parts.length];
    for (int i = 0; i < parts.length; i++) {
      result[i] = Double.parseDouble(parts[i].trim());
    }
    return result;
  }

  /** Parses int array from text Format: [a, r, g, b] */
  private static int[] parseIntArray(String text, String key) {
    String value = parseString(text, key);
    if (value == null || value.isEmpty()) {
      return new int[] {0, 0, 0, 0};
    }
    String cleaned = value.replace("[", "").replace("]", "").trim();
    String[] parts = cleaned.split(",");
    int[] result = new int[parts.length];
    for (int i = 0; i < parts.length; i++) {
      result[i] = Integer.parseInt(parts[i].trim());
    }
    return result;
  }

  public static String doubleArrayToString(double[] vals, int minLength) {
    if (vals == null || vals.length < minLength) {
      if (minLength == 2) return "0:0";
      if (minLength == 3) return "0:0:0";
      return "0:0";
    }

    if (minLength == 2) {
      return vals[0] + ":" + vals[1];
    }

    if (minLength == 3) {
      return vals[0] + ":" + vals[1] + ":" + vals[2];
    }

    return vals[0] + ":" + vals[1];
  }

  public static String intArrayToString(int[] vals) {
    if (vals == null || vals.length < 4) {
      return "0:0:0:0";
    }
    return vals[0] + ":" + vals[1] + ":" + vals[2] + ":" + vals[3];
  }

  public static String extractMaterialProperties(String input) {
    if (input == null || input.isEmpty()) {
      return "";
    }

    StringBuilder result = new StringBuilder();
    String[] lines = input.split("\n");

    for (String line : lines) {
      String trimmedLine = line.trim();

      if (trimmedLine.isEmpty()) {
        continue;
      }

      if (trimmedLine.contains("material") && trimmedLine.contains("{")) {
        continue;
      }

      if (trimmedLine.equals("}") || trimmedLine.equals("};")) {
        continue;
      }

      if (result.length() > 0) {
        result.append("\n");
      }
      result.append(trimmedLine);
    }

    return result.toString();
  }

  public static Point3 pixelToWorld(
      int pixelX, int pixelY, int screenWidth, int screenHeight, Camera camera) {
    // Camera vectors
    Vector3 gaze = camera.getLookAt().minus(camera.getCameraPosition()).toVector().normalize();
    Vector3 u = camera.getUpVector().cross(gaze).normalize();
    Vector3 v = gaze.cross(u).normalize();

    // View plane parameters
    double aspectRatio = (double) screenWidth / screenHeight;
    double distance;

    // Perspektif projektion
    if (camera.getFov() <= 0) {
      distance = 1.0;
    } else {
      distance = 1.0 / Math.tan(Math.toRadians(camera.getFov()) / 2.0);
    }

    // Pixel coordinates [-1, 1] normalize
    double ndcX = 1.0 - (2.0 * pixelX / (screenWidth - 1)); // X reves
    double ndcY = 1.0 - (2.0 * pixelY / (screenHeight - 1));

    // Aspect ratio
    ndcX *= aspectRatio;

    Point3 cameraPosition = camera.getCameraPosition();

    // Point3.add()
    Point3 imagePoint =
        cameraPosition.add(u.times(ndcX)).add(v.times(ndcY)).add(gaze.times(distance));

    // Point3 to Vector3 and normalize
    Vector3 rayDirection = imagePoint.minus(cameraPosition).toVector().normalize();

    if (Math.abs(rayDirection.z) > 1e-6) {
      double t = -cameraPosition.z / rayDirection.z;
      return new Point3(
          cameraPosition.x + rayDirection.x * t, cameraPosition.y + rayDirection.y * t, 0.0);
    } else {
      return imagePoint;
    }
  }

  /**
   * Parses transform from shape block
   *
   * @param block the block to parse
   * @return the Matrix4
   */
  /**
   * private static Matrix4 parseTransformFromShapeBlock(String block) { try { String transformStr =
   * extractValue(block, "transform"); if (transformStr == null) return Matrix4.identity(); return
   * Matrix4.createMatrixFromString(transformStr); } catch (Exception e) { Log.e(TAG, "Inner
   * transform parse error: " + e.getMessage(), e); return Matrix4.identity(); } }
   */
  private static Matrix4 parseTransformFromShapeBlock(String block) {
    try {
      String[] lines = block.split("\n");
      for (int i = 0; i < lines.length; i++) {
        String t = lines[i].trim();
        // The line must START with "transform =", so that firstAnim_transform and
        // secondAnim_transform lines are never mistaken for the shape transform.
        if (t.startsWith("transform =")) {
          String value = t.substring(t.indexOf('=') + 1).trim();
          int c = value.indexOf("//");
          if (c >= 0) {
            value = value.substring(0, c).trim();
          }
          return Matrix4.createMatrixFromString(value);
        }
      }
      return Matrix4.identity();
    } catch (Exception e) {
      System.err.println("Inner transform parse error: " + e.getMessage());
      return Matrix4.identity();
    }
  }

  public static String readFileToString(File file) throws IOException {
    StringBuilder sb = new StringBuilder();
    BufferedReader reader =
        new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
    try {
      String line;
      while ((line = reader.readLine()) != null) {
        sb.append(line).append("\n");
      }
    } finally {
      reader.close();
    }
    return sb.toString();
  }

  public static void writeStringToFile(File file, String content) throws IOException {
    BufferedWriter writer =
        new BufferedWriter(new OutputStreamWriter(new FileOutputStream(file), "UTF-8"));
    try {
      writer.write(content);
    } finally {
      writer.close();
    }
  }

  /**
   * Main method
   *
   * @param args command line arguments
   */
  public static void main(String[] args) {}
}
