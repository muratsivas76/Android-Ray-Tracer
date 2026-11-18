package net.murat.gui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.util.Log;
import android.util.TypedValue;
import android.view.Surface;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.murat.material.OurFontFactory;
import net.murat.uikit.MColorPicker;
import net.murat.uikit.MEditor;
import net.murat.uikit.MFilePicker;
import net.murat.uikit.MOptionPane;

/**
 * Main activity for the Android Ray Tracer.
 *
 * <p>Architecture: - Top bar: Exit, To Movie, Generate, Save Img - Center: ImageView (render
 * output) + MEditor (scene text) - Bottom bar: Add, Set, Select Scene, Save Scene - Uses
 * ExecutorService for background rendering / file I/O / video encoding to prevent UI ANR.
 */
public class RayTracerAndroid extends Activity {

  private static final String TAG = "RayTracerAndroid";

  // UI Components
  private ImageView imageView;
  private MEditor editor;
  private View loadingOverlay;
  private Button btnExit, btnToMovie, btnGenerate, btnSaveImage;
  private Button btnAdd, btnSet, btnSelectScene, btnSaveScene;

  // Background & System Tools
  private MFilePicker filePicker;
  private MFilePicker moviePicker;
  private ExecutorService backgroundExecutor;
  private Handler mainHandler;

  // Paths
  private String PATH = "";
  private String MAINPATH = "";
  private File SAVEDIR = null;
  private File MOVIEDIR = null;

  private static final String NEW_SCENE_TEMPLATE =
      "# ==========\n"
          + "# New Scene\n"
          + "# ==========\n"
          + "\n"
          + "# Camera Settings\n"
          + "Camera mainCamera {\n"
          + "    position = P(0.00, 1.00, 5.00);\n"
          + "    lookAt = P(0.00, 0.00, -5.00);\n"
          + "    upVector = V(0.00, 1.00, 0.00);\n"
          + "    fov = 60.0;\n"
          + "    orthographic = false;\n"
          + "    maxRecursionDepth = 1;\n"
          + "    ssGridCount = 1;\n"
          + "    reflective = true;\n"
          + "    refractive = true;\n"
          + "    shadowsEnabled = true;\n"
          + "    selfShadowsEnabled = false;\n"
          + "    useBackgroundColor = false;\n"
          + "}\n"
          + "\n"
          + "# Renderer Settings\n"
          + "Renderer Settings {\n"
          + "    width = 640;\n"
          + "    height = 480;\n"
          + "    backgroundColor = 0xFF0000FF;\n"
          + "}\n"
          + "\n"
          + "# Lighting\n"
          + "MuratAmbientLight muratAmbient {\n"
          + "    name = ambient;\n"
          + "    color = 0xFFFFFFFF;\n"
          + "    intensity = 1.0;\n"
          + "}\n"
          + "\n"
          + "MuratPointLight muratPoint {\n"
          + "    name = point;\n"
          + "    position = P(2.000, 5.000, 2.000);\n"
          + "    color = 0xFFFFFFFF;\n"
          + "    intensity = 1.0;\n"
          + "    constantAttenuation = 1.0;\n"
          + "    linearAttenuation = 0.1;\n"
          + "    quadraticAttenuation = 0.01;\n"
          + "}\n"
          + "\n"
          + "# Geometries/Shapes\n"
          + "\n"
          + "-END-\n";

  // Dynamic FPS set by the user
  private float currentMovieFps = 1.0f;

  // Settings limits (populated by loadSettings())
  private int maxWidth = 640;
  private int maxHeight = 480;
  private int maxDepth = 4;
  private int maxSampling = 2;

  // Last applied editor visual settings (used by Set dialog to prefill values)
  private String currentFontFace = "default";
  private int currentFontStyle = 0;
  private float currentLineSpacing = 1.1f;

  // State
  private Bitmap currentRenderedBitmap = null;

  // Video Constants
  private static final int MAX_MOVIE_FRAMES = 50;
  private static final int MOVIE_REQUEST_CODE = 200;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.main);

    // 1. Initialize paths and generate base folders
    PATH = getMainPathName();
    GenerateFolders.generateFolders(PATH);
    MAINPATH = PATH + "/carets/raytracer";
    SAVEDIR = new File(MAINPATH + "/images");
    MOVIEDIR = new File(MAINPATH + "/movies");

    // 2. Copy default asset files if they do not exist
    GenerateFolders.copyAssetsToDownload(this, MAINPATH);

    // 3. Initialize base directories for textures and fonts
    Utilities.setBaseDir(new File(MAINPATH));
    OurFontFactory.setBaseDir(MAINPATH);

    // 4. Initialize UI components
    initUI();

    // 5. Load settings.txt and apply visual settings to MEditor
    loadSettings();

    // 6. Initialize background tools and file pickers
    backgroundExecutor = Executors.newSingleThreadExecutor();
    mainHandler = new Handler(Looper.getMainLooper());

    filePicker = new MFilePicker(this);
    filePicker.setInitialDirectory(MAINPATH + "/scenes");

    moviePicker = new MFilePicker(this);
    moviePicker.setMultiSelectionEnabled(true);
    moviePicker.setMimeTypes(new String[] {"image/jpeg", "image/jpg"});
    moviePicker.setInitialDirectory(MAINPATH + "/images");

    // 7. Setup button click listeners
    setupListeners();
  }

  /**
   * Resolves the base download path (e.g., Download/) without exposing the internal
   * Android/data/... structure.
   */
  private String getMainPathName() {
    File path = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
    String px = path.getAbsolutePath();

    String exclude = "Android/data/" + getPackageName() + "/files/";
    px = px.replace(exclude, "");

    return px;
  }

  private void initUI() {
    imageView = (ImageView) findViewById(R.id.image_view);
    editor = (MEditor) findViewById(R.id.editor);
    loadingOverlay = findViewById(R.id.loading_overlay);

    btnExit = (Button) findViewById(R.id.btn_exit);
    btnToMovie = (Button) findViewById(R.id.btn_to_movie);
    btnGenerate = (Button) findViewById(R.id.btn_generate);
    btnSaveImage = (Button) findViewById(R.id.btn_save_image);

    btnAdd = (Button) findViewById(R.id.btn_add);
    btnSet = (Button) findViewById(R.id.btn_set);
    btnSelectScene = (Button) findViewById(R.id.btn_select_scene);
    btnSaveScene = (Button) findViewById(R.id.btn_save_scene);
  }

  private void setupListeners() {
    // 1. EXIT
    btnExit.setOnClickListener(
        new View.OnClickListener() {
          @Override
          public void onClick(View v) {
            executeExit();
          }
        });

    // 2. TO MOVIE (Multi-select JPGs) - Fixed FPS description
    btnToMovie.setOnClickListener(
        new View.OnClickListener() {
          @Override
          public void onClick(View v) {
            MOptionPane pane = new MOptionPane(RayTracerAndroid.this);
            pane.showInputDialog(
                "Enter FPS (frames per second).\n"
                    + "1 = 1 frame/sec (normal)\n"
                    + "0.5 = 1 frame per 2s (slow motion)\n"
                    + "2 = 2 frames/sec (faster):",
                "1",
                new MOptionPane.OnInputListener() {
                  @Override
                  public void onResult(String input) {
                    if (input == null || input.trim().isEmpty()) return;
                    try {
                      float parsedFps = Float.parseFloat(input.trim());
                      if (parsedFps <= 0) throw new NumberFormatException();
                      currentMovieFps = parsedFps;
                      moviePicker.showOpenDialog(MOVIE_REQUEST_CODE);
                    } catch (NumberFormatException e) {
                      Toast.makeText(RayTracerAndroid.this, "Invalid number!", Toast.LENGTH_SHORT)
                          .show();
                    }
                  }
                });
          }
        });

    // 3. GENERATE
    btnGenerate.setOnClickListener(
        new View.OnClickListener() {
          @Override
          public void onClick(View v) {
            executeGenerate();
          }
        });

    // 4. SAVE IMAGE
    btnSaveImage.setOnClickListener(
        new View.OnClickListener() {
          @Override
          public void onClick(View v) {
            executeSaveImage();
          }
        });

    // 5. ADD (Insert template into editor)
    btnAdd.setOnClickListener(
        new View.OnClickListener() {
          @Override
          public void onClick(View v) {
            showAddDialog();
          }
        });

    // 6. SET (Temporary MEditor visual settings dialog)
    btnSet.setOnClickListener(
        new View.OnClickListener() {
          @Override
          public void onClick(View v) {
            showSetDialog();
          }
        });

    // 7. SELECT SCENE
    btnSelectScene.setOnClickListener(
        new View.OnClickListener() {
          @Override
          public void onClick(View v) {
            filePicker.setMimeTypes(new String[] {"text/plain"});
            filePicker.setMultiSelectionEnabled(false);
            filePicker.showOpenDialog(MFilePicker.OPEN_REQUEST_CODE);
          }
        });

    // 8. SAVE SCENE
    btnSaveScene.setOnClickListener(
        new View.OnClickListener() {
          @Override
          public void onClick(View v) {
            filePicker.setMimeTypes(new String[] {"text/plain"});
            filePicker.showSaveDialog(MFilePicker.SAVE_REQUEST_CODE);
          }
        });
  }

  // =========================================================================
  // SETTINGS MANAGEMENT
  // =========================================================================

  /**
   * Reads settings.txt from MAINPATH/info/ and applies visual settings to MEditor. Limit values
   * (maxWidth, maxHeight, etc.) are also loaded here. If the file is missing or parsing fails,
   * defaults are used.
   *
   * <p>Font face resolution rules: - null / empty / "default" -> Typeface.DEFAULT (no TTF lookup) -
   * "arial" -> ttf/arial.ttf (extension auto-added) - "arial.ttf" -> ttf/arial.ttf (extension
   * already present) - missing or unreadable TTF -> Typeface.DEFAULT fallback (with warning log)
   */
  private void loadSettings() {
    File settingsFile = new File(MAINPATH, "info/settings.txt");
    if (!settingsFile.exists()) {
      Log.w(TAG, "settings.txt not found, using defaults.");
      return;
    }

    Properties props = new Properties();
    try (FileInputStream fis = new FileInputStream(settingsFile)) {
      props.load(fis);

      // ---- Renderer limits ----
      maxWidth = parseIntSafe(props.getProperty("maxWidth"), 640);
      maxHeight = parseIntSafe(props.getProperty("maxHeight"), 480);
      maxDepth = parseIntSafe(props.getProperty("maxDepth"), 4);
      maxSampling = parseIntSafe(props.getProperty("maxSampling"), 2);

      // ---- MEditor visual settings ----
      currentFontFace = props.getProperty("fontFace", "default");
      currentFontStyle = parseIntSafe(props.getProperty("fontStyle"), 0);
      float fontSize = parseFloatSafe(props.getProperty("fontSize"), 20.0f);
      int fgColor = parseColorSafe(props.getProperty("foregroundColor"), 0xFF000000);
      int bgColor = parseColorSafe(props.getProperty("backgroundColor"), 0xFFEFEFEF);
      currentLineSpacing = parseFloatSafe(props.getProperty("lineSpacing"), 1.1f);

      // ---- Apply to MEditor ----
      if (editor != null) {
        editor.setTextSize(fontSize);
        editor.setTextColor(fgColor);
        int borderPx = (int) (2 * getResources().getDisplayMetrics().density);
        editor.setBackgroundWithBorder(bgColor, 0xFF00AA00, borderPx);
        editor.setLineSpacing(0, currentLineSpacing);

        Typeface baseTypeface = resolveTypeface(currentFontFace);

        int style = Typeface.NORMAL;
        if (currentFontStyle == 1) style = Typeface.BOLD;
        else if (currentFontStyle == 2) style = Typeface.ITALIC;
        else if (currentFontStyle == 3) style = Typeface.BOLD_ITALIC;

        editor.setTypeface(baseTypeface, style);
      }
    } catch (Exception e) {
      Log.e(TAG, "Error loading settings.txt", e);
    }
  }

  private int parseIntSafe(String val, int def) {
    if (val == null || val.trim().isEmpty()) return def;
    try {
      return Integer.parseInt(val.trim());
    } catch (Exception e) {
      return def;
    }
  }

  private float parseFloatSafe(String val, float def) {
    if (val == null || val.trim().isEmpty()) return def;
    try {
      return Float.parseFloat(val.trim());
    } catch (Exception e) {
      return def;
    }
  }

  private int parseColorSafe(String val, int def) {
    if (val == null || val.trim().isEmpty()) return def;
    try {
      return (int) Long.parseLong(val.trim().replace("0x", ""), 16);
    } catch (Exception e) {
      return def;
    }
  }

  /**
   * Resolves a font face name to a Typeface. - null / empty / "default" -> Typeface.DEFAULT -
   * otherwise -> MAINPATH/ttf/<name>[.ttf], or DEFAULT if missing.
   */
  private Typeface resolveTypeface(String face) {
    Typeface base = Typeface.DEFAULT;
    try {
      if (face == null || face.trim().isEmpty() || "default".equalsIgnoreCase(face.trim())) {
        return base;
      }
      String fileName = face.trim();
      if (!fileName.toLowerCase(Locale.US).endsWith(".ttf")) {
        fileName = fileName + ".ttf";
      }
      File ttfFile = new File(MAINPATH + "/ttf/" + fileName);
      if (ttfFile.exists()) {
        base = Typeface.createFromFile(ttfFile);
      } else {
        Log.w(
            TAG,
            "Font not found: "
                + ttfFile.getAbsolutePath()
                + " — falling back to Typeface.DEFAULT.");
      }
    } catch (Exception e) {
      Log.e(TAG, "Failed to load font: " + face + " — falling back to Typeface.DEFAULT.", e);
    }
    return base;
  }

  // =========================================================================
  // ADD DIALOG (Shape + Material / Light)
  // =========================================================================

  /**
   * ADD button: Inserts a template into the editor.
   *
   * <p>Flow: 1. First dialog lists Shapes (S:) and Lights (L:) alphabetically, grouped S-first then
   * L. CSG shapes are excluded. 2. If a Light is chosen, its template is inserted directly below
   * the "#Lighting" line in the editor. If that line is missing, a Toast is shown and the operation
   * is aborted. 3. If a Shape is chosen, a second dialog lists Materials (M:) alphabetically. The
   * shape template (which contains a default RandomMaterial block) is patched by replacing that
   * block with the chosen material's template, and the result is inserted above the "-END-" line.
   * If "-END-" is missing, a Toast is shown and the operation is aborted. Cancelling the material
   * dialog aborts too.
   */
  private void showAddDialog() {
    // ---- Build Shape + Light list (S first, then L), each sorted ----
    final List<String> shapeEntries = new ArrayList<String>();
    for (String s : Utilities.SHAPE_TYPES) {
      if ("Custom".equals(s)) continue;
      if (isCsgShapeName(s)) continue; // CSG excluded from Add dialog
      shapeEntries.add("S: " + s);
    }
    Collections.sort(
        shapeEntries,
        new Comparator<String>() {
          @Override
          public int compare(String a, String b) {
            return a.compareToIgnoreCase(b);
          }
        });

    final List<String> lightEntries = new ArrayList<String>();
    for (String l : Utilities.LIGHT_TYPES) {
      if ("Custom".equals(l)) continue;
      lightEntries.add("L: " + l);
    }
    Collections.sort(
        lightEntries,
        new Comparator<String>() {
          @Override
          public int compare(String a, String b) {
            return a.compareToIgnoreCase(b);
          }
        });

    final List<String> firstList = new ArrayList<String>();
    // Prepend the special "New Scene" entry.
    firstList.add(0, "N: New Scene");
    firstList.addAll(shapeEntries);
    firstList.addAll(lightEntries);

    if (firstList.isEmpty()) {
      Toast.makeText(this, "No entries available.", Toast.LENGTH_SHORT).show();
      return;
    }

    final String[] firstItems = firstList.toArray(new String[0]);

    MOptionPane pane = new MOptionPane(this);
    pane.showSingleChoiceDialog(
        "Select a Shape (S) or Light (L) to add:",
        firstItems,
        new MOptionPane.OnChoiceListener() {
          @Override
          public void onResult(int index) {
            if (index < 0) return;
            String selected = firstItems[index];

            if (selected.startsWith("N: ")) {
              showNewSceneConfirmDialog();
              return;
            }

            // ---- Light path ----
            if (selected.startsWith("L: ")) {
              String type = selected.substring(3);
              String template = Utilities.getLightTemplate(type);
              if (template == null || template.isEmpty()) {
                Toast.makeText(RayTracerAndroid.this, "No template available.", Toast.LENGTH_SHORT)
                    .show();
                return;
              }
              insertBelowLightingMarker(formatLightBlock(type, template));
              return;
            }

            // ---- Shape path ----
            if (selected.startsWith("S: ")) {
              final String shapeType = selected.substring(3);
              final String shapeTemplate = Utilities.getShapeTemplate(shapeType);
              if (shapeTemplate == null || shapeTemplate.isEmpty()) {
                Toast.makeText(RayTracerAndroid.this, "No template available.", Toast.LENGTH_SHORT)
                    .show();
                return;
              }
              // Ask for the material in a second dialog.
              showMaterialPickerForShape(shapeType, shapeTemplate);
            }
          }
        });
  }

  /**
   * Asks the user to confirm clearing the editor and loading a fresh scene. If confirmed, tries to
   * load scenes/aaTemplate.txt; if that file is missing, falls back to the built-in
   * NEW_SCENE_TEMPLATE constant.
   */
  private void showNewSceneConfirmDialog() {
    MOptionPane pane = new MOptionPane(this);
    pane.showConfirmDialog(
        "Do you want to remove/delete the current text and start a new scene? This action cannot be"
            + " undone.",
        new MOptionPane.OnConfirmListener() {
          @Override
          public void onResult(int option) {
            if (option != MOptionPane.YES_OPTION) return;

            String template = NEW_SCENE_TEMPLATE;
            File templateFile = new File(MAINPATH + "/scenes/aaTemplate.txt");
            if (templateFile.exists()) {
              try {
                StringBuilder sb = new StringBuilder();
                BufferedReader reader =
                    new BufferedReader(
                        new InputStreamReader(new FileInputStream(templateFile), "UTF-8"));
                String line;
                while ((line = reader.readLine()) != null) {
                  sb.append(line).append("\n");
                }
                reader.close();
                template = sb.toString();
              } catch (Exception e) {
                Log.w(TAG, "Failed to read aaTemplate.txt, using built-in fallback.", e);
              }
            } else {
              Log.w(TAG, "aaTemplate.txt not found, using built-in fallback.");
            }

            editor.setEditorText(template);
            Toast.makeText(RayTracerAndroid.this, "New scene created.", Toast.LENGTH_SHORT).show();
          }
        });
  }

  /**
   * Second stage of the Add flow: shows the Material list and, upon selection, patches the shape
   * template and inserts it above "-END-".
   */
  private void showMaterialPickerForShape(final String shapeType, final String shapeTemplate) {
    final List<String> materialEntries = new ArrayList<String>();
    for (String m : Utilities.MATERIAL_TYPES) {
      if ("Custom".equals(m)) continue;
      materialEntries.add("M: " + m);
    }
    Collections.sort(
        materialEntries,
        new Comparator<String>() {
          @Override
          public int compare(String a, String b) {
            return a.compareToIgnoreCase(b);
          }
        });

    if (materialEntries.isEmpty()) {
      Toast.makeText(this, "No materials available.", Toast.LENGTH_SHORT).show();
      return;
    }

    final String[] items = materialEntries.toArray(new String[0]);

    MOptionPane pane = new MOptionPane(this);
    pane.showSingleChoiceDialog(
        "Select a Material (M) for " + shapeType + ":",
        items,
        new MOptionPane.OnChoiceListener() {
          @Override
          public void onResult(int index) {
            if (index < 0) return; // Cancel = abort, no insertion.
            String selected = items[index];
            String matType = selected.substring(3);
            String matTemplate = Utilities.getMaterialTemplate(matType);
            if (matTemplate == null || matTemplate.isEmpty()) {
              Toast.makeText(RayTracerAndroid.this, "No template available.", Toast.LENGTH_SHORT)
                  .show();
              return;
            }

            String patched =
                patchShapeTemplateWithMaterial(shapeType, shapeTemplate, matType, matTemplate);

            insertAboveEndMarker(formatShapeBlock(shapeType, patched));
          }
        });
  }

  /**
   * Replaces the placeholder material block inside the shape template with the chosen material's
   * block. The placeholder is expected to be a "material = RandomMaterial randommaterial { ... };"
   * section produced by Utilities.getShapeTemplate(). If the placeholder is not found, the material
   * block is appended just before the closing brace of the shape.
   */
  private String patchShapeTemplateWithMaterial(
      String shapeType, String shapeTemplate, String materialType, String materialTemplate) {
    // Build the material block exactly as the reference format specifies:
    // 4 spaces, "material = <Type> <typeLower> {", then the body, then "    };"
    String materialBlock = buildMaterialBlock(materialType, materialTemplate);

    // Try to locate the placeholder RandomMaterial block.
    String placeholder = "material = RandomMaterial randommaterial {";
    int start = shapeTemplate.indexOf(placeholder);
    if (start < 0) {
      // No placeholder: append material block before the final closing brace.
      int closeIdx = shapeTemplate.lastIndexOf('}');
      if (closeIdx < 0) {
        // Malformed shape template; append at the end just in case.
        return shapeTemplate + "\n" + materialBlock + "\n";
      }
      return shapeTemplate.substring(0, closeIdx)
          + materialBlock
          + "\n"
          + shapeTemplate.substring(closeIdx);
    }

    // Find the closing "};" that terminates the placeholder block.
    int bodyStart = shapeTemplate.indexOf('{', start);
    if (bodyStart < 0) {
      return shapeTemplate;
    }
    int depth = 1;
    int i = bodyStart + 1;
    while (i < shapeTemplate.length() && depth > 0) {
      char c = shapeTemplate.charAt(i);
      if (c == '{') depth++;
      else if (c == '}') depth--;
      i++;
    }
    // i is now positioned just after the closing '}' of the placeholder.
    // Extend past an optional ';' directly after it.
    if (i < shapeTemplate.length() && shapeTemplate.charAt(i) == ';') {
      i++;
    }

    return shapeTemplate.substring(0, start) + materialBlock + shapeTemplate.substring(i);
  }

  /**
   * Builds a "material = <Type> <typeLower> { ... };" block with the standard reference indentation
   * (4 spaces on header and footer).
   */
  private String buildMaterialBlock(String materialType, String materialTemplate) {
    String lowerType = toLowerFirstChar(materialType);
    StringBuilder sb = new StringBuilder();
    sb.append("material = ").append(materialType).append(" ").append(lowerType).append(" {\n");
    String[] lines = materialTemplate.split("\n");
    for (int i = 0; i < lines.length; i++) {
      String line = lines[i];
      if (line.isEmpty()) continue;
      sb.append("    ").append(line).append("\n");
    }
    sb.append("};\n");
    return sb.toString();
  }

  /**
   * Formats a Light block according to the reference format: no leading indent on the header,
   * 4-space indent inside, no trailing ';'.
   */
  private String formatLightBlock(String lightType, String lightTemplate) {
    String fullClassName = lightType;
    if (!fullClassName.endsWith("Light")) {
      fullClassName = fullClassName + "Light";
    }
    StringBuilder sb = new StringBuilder();
    sb.append(fullClassName).append(" ").append(toLowerFirstChar(lightType)).append(" {\n");
    String[] lines = lightTemplate.split("\n");
    for (int i = 0; i < lines.length; i++) {
      String line = lines[i];
      if (line.isEmpty()) continue;
      sb.append("    ").append(line).append("\n");
    }
    sb.append("}\n");
    return sb.toString();
  }

  /**
   * Formats a Shape block according to the reference format: no leading indent on the header,
   * 4-space indent inside, no trailing ';'.
   */
  private String formatShapeBlock(String shapeType, String shapeBody) {
    StringBuilder sb = new StringBuilder();
    sb.append(shapeType).append(" ").append(toLowerFirstChar(shapeType)).append(" {\n");
    String[] lines = shapeBody.split("\n");
    for (int i = 0; i < lines.length; i++) {
      String line = lines[i];
      if (line.isEmpty()) continue;
      sb.append("    ").append(line).append("\n");
    }
    sb.append("}");
    return sb.toString();
  }

  /**
   * Normalizes blank-line spacing around the markers. 1. Collapses "# Lighting\n\n" -> "#
   * Lighting\n" (same for "#Lighting"). 2. Collapses any run of 3 or more consecutive newlines into
   * exactly 2. 3. Ensures exactly one blank line before "-END-".
   */
  private String normalizeBlockSpacing(String input) {
    String s = input;

    // 1. Marker + extra blank line -> marker + one newline only
    s = s.replace("# Lighting\n\n", "# Lighting\n");
    s = s.replace("#Lighting\n\n", "#Lighting\n");

    // 2. Collapse 3+ consecutive newlines into exactly 2.
    while (true) {
      String prev = s;
      s = s.replace("\n\n\n", "\n\n");
      if (s.equals(prev)) break;
    }

    // 3. Ensure exactly one blank line before "-END-".
    int idx = s.indexOf("-END-");
    if (idx >= 0) {
      int lineStart = s.lastIndexOf('\n', idx);
      if (lineStart < 0) lineStart = 0;
      else lineStart += 1;

      String before = s.substring(0, lineStart);
      String after = s.substring(lineStart);

      while (before.endsWith("\n")) {
        before = before.substring(0, before.length() - 1);
      }
      s = before + "\n\n" + after;
    }

    return s;
  }

  /**
   * Inserts the given text directly below the "#Lighting" marker line. Ensures exactly one blank
   * line between the marker and the new block.
   */
  private void insertBelowLightingMarker(String blockText) {
    String full = editor.getFullText();
    int markerIdx = full.indexOf("# Lighting");
    if (markerIdx < 0) markerIdx = full.indexOf("#Lighting");
    if (markerIdx < 0) {
      Toast.makeText(this, "# Lighting not found", Toast.LENGTH_LONG).show();
      return;
    }
    int lineEnd = full.indexOf('\n', markerIdx);
    String before;
    String after;
    if (lineEnd < 0) {
      before = full;
      after = "";
    } else {
      before = full.substring(0, lineEnd + 1);
      after = full.substring(lineEnd + 1);
    }
    String newText = before + "\n" + blockText + "\n" + after;
    newText = normalizeBlockSpacing(newText);
    editor.setEditorText(newText, true);
  }

  /**
   * Inserts the given text directly above the "-END-" marker line. Ensures exactly one blank line
   * between the previous content and the new block.
   */
  private void insertAboveEndMarker(String blockText) {
    String full = editor.getFullText();
    int markerIdx = full.indexOf("-END-");
    if (markerIdx < 0) {
      Toast.makeText(this, "-END- not found", Toast.LENGTH_LONG).show();
      return;
    }
    int lineStart = full.lastIndexOf('\n', markerIdx);
    String before;
    String after;
    if (lineStart < 0) {
      before = "";
      after = full;
    } else {
      before = full.substring(0, lineStart);
      after = full.substring(lineStart);
    }
    String newText = before + "\n\n" + blockText + "\n\n" + after;
    newText = normalizeBlockSpacing(newText);
    editor.setEditorText(newText, true);
  }

  /** Returns true if the given name is one of the CSG shape type names. */
  private boolean isCsgShapeName(String name) {
    return "UnionCSG".equals(name)
        || "IntersectionCSG".equals(name)
        || "DifferenceCSG".equals(name);
  }

  /** Returns the given identifier with its first character lowercased. */
  private String toLowerFirstChar(String s) {
    if (s == null || s.isEmpty()) return s;
    return Character.toLowerCase(s.charAt(0)) + s.substring(1);
  }

  // =========================================================================
  // SET DIALOG (Temporary MEditor Visual Settings)
  // =========================================================================

  /**
   * SET button: Opens a temporary dialog to edit MEditor visual settings. Settings are applied
   * immediately on Apply and are NOT persisted to settings.txt (they last only for the current
   * session).
   *
   * <p>The dialog pre-fills fields with the last applied values (currentFontFace, currentFontStyle,
   * currentLineSpacing, and the persisted fg/bg colors). On a fresh app start these come from
   * settings.txt via loadSettings().
   *
   * <p>Font face resolution: - "default" (or empty) -> system default typeface - otherwise ->
   * MAINPATH/ttf/<name>[.ttf]
   */
  private void showSetDialog() {
    LinearLayout root = new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    int pad = (int) (16 * getResources().getDisplayMetrics().density);
    root.setPadding(pad, pad, pad, pad);

    // ---- Font Face ----
    TextView tvFontFace = new TextView(this);
    tvFontFace.setText("Font Face (default = system, otherwise ttf/<name>.ttf):");
    root.addView(tvFontFace);
    final EditText etFontFace = new EditText(this);
    etFontFace.setSingleLine(true);
    etFontFace.setText(currentFontFace);
    root.addView(etFontFace);

    // ---- Font Style ----
    TextView tvFontStyle = new TextView(this);
    tvFontStyle.setText("Font Style:");
    root.addView(tvFontStyle);
    final String[] styleNames = {"Normal", "Bold", "Italic", "Bold+Italic"};
    final Spinner spFontStyle = new Spinner(this);
    ArrayAdapter<String> styleAdapter =
        new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, styleNames);
    styleAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    spFontStyle.setAdapter(styleAdapter);
    // Pre-select last applied style.
    if (currentFontStyle >= 0 && currentFontStyle < styleNames.length) {
      spFontStyle.setSelection(currentFontStyle);
    }
    root.addView(spFontStyle);

    // ---- Font Size ----
    TextView tvFontSize = new TextView(this);
    tvFontSize.setText("Font Size (sp):");
    root.addView(tvFontSize);
    final EditText etFontSize = new EditText(this);
    etFontSize.setSingleLine(true);
    etFontSize.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
    float currentSizeSp =
        TypedValue.deriveDimension(
            TypedValue.COMPLEX_UNIT_SP, editor.getTextSize(), getResources().getDisplayMetrics());
    etFontSize.setText(String.valueOf(currentSizeSp));
    root.addView(etFontSize);

    // ---- Foreground Color ----
    TextView tvFg = new TextView(this);
    tvFg.setText("Foreground Color:");
    root.addView(tvFg);
    final int[] pickedFg = {editor.getPersistedTextColor()};
    final Button btnFgPick = new Button(this);
    btnFgPick.setText("FG: #" + Integer.toHexString(pickedFg[0]).toUpperCase(Locale.US));
    root.addView(btnFgPick);

    // ---- Background Color ----
    TextView tvBg = new TextView(this);
    tvBg.setText("Background Color:");
    root.addView(tvBg);
    final int[] pickedBg = {editor.getPersistedBackgroundColor()};
    final Button btnBgPick = new Button(this);
    btnBgPick.setText("BG: #" + Integer.toHexString(pickedBg[0]).toUpperCase(Locale.US));
    root.addView(btnBgPick);

    // ---- Line Spacing ----
    TextView tvLineSpacing = new TextView(this);
    tvLineSpacing.setText("Line Spacing Multiplier:");
    root.addView(tvLineSpacing);
    final EditText etLineSpacing = new EditText(this);
    etLineSpacing.setSingleLine(true);
    etLineSpacing.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
    etLineSpacing.setText(String.valueOf(currentLineSpacing));
    root.addView(etLineSpacing);

    // ---- Color pickers ----
    btnFgPick.setOnClickListener(
        new View.OnClickListener() {
          @Override
          public void onClick(View v) {
            MColorPicker picker = new MColorPicker(RayTracerAndroid.this);
            picker.showColorPickerDialog(
                new MColorPicker.OnColorSelectedListener() {
                  @Override
                  public void onColorSelected(int color) {
                    pickedFg[0] = color;
                    btnFgPick.setText("FG: #" + Integer.toHexString(color).toUpperCase(Locale.US));
                  }
                });
          }
        });
    btnBgPick.setOnClickListener(
        new View.OnClickListener() {
          @Override
          public void onClick(View v) {
            MColorPicker picker = new MColorPicker(RayTracerAndroid.this);
            picker.showColorPickerDialog(
                new MColorPicker.OnColorSelectedListener() {
                  @Override
                  public void onColorSelected(int color) {
                    pickedBg[0] = color;
                    btnBgPick.setText("BG: #" + Integer.toHexString(color).toUpperCase(Locale.US));
                  }
                });
          }
        });

    // ---- Dialog ----
    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    builder.setTitle("Editor Settings (Temporary)");
    builder.setView(root);
    builder.setCancelable(true);

    builder.setPositiveButton(
        "Apply",
        new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            // Font face
            String face = etFontFace.getText().toString().trim();
            Typeface tf = resolveTypeface(face);
            currentFontFace = face;

            // Font style
            int style = Typeface.NORMAL;
            int sel = spFontStyle.getSelectedItemPosition();
            if (sel == 1) style = Typeface.BOLD;
            else if (sel == 2) style = Typeface.ITALIC;
            else if (sel == 3) style = Typeface.BOLD_ITALIC;
            currentFontStyle = sel;

            // Font size
            float size;
            try {
              size = Float.parseFloat(etFontSize.getText().toString().trim());
            } catch (NumberFormatException e) {
              size = 20.0f;
            }
            if (size <= 0) size = 20.0f;

            // Line spacing
            float ls;
            try {
              ls = Float.parseFloat(etLineSpacing.getText().toString().trim());
            } catch (NumberFormatException e) {
              ls = 1.1f;
            }
            if (ls <= 0) ls = 1.1f;
            currentLineSpacing = ls;

            // Apply to editor. Background uses border-preserving helper so
            // the green border stays visible (see MEditor.setBackgroundWithBorder).
            editor.setTypeface(tf, style);
            editor.setTextSize(size);
            editor.setTextColor(pickedFg[0]);
            int borderPx = (int) (2 * getResources().getDisplayMetrics().density);
            editor.setBackgroundWithBorder(pickedBg[0], 0xFF00AA00, borderPx);
            editor.setLineSpacing(0, ls);

            Toast.makeText(
                    RayTracerAndroid.this,
                    "Editor settings applied (temporary).",
                    Toast.LENGTH_SHORT)
                .show();
          }
        });
    builder.setNegativeButton("Cancel", null);
    builder.show();
  }

  /** Shows a dialog with copyable text content. */
  private void showCopyableDialog(String title, String content) {
    MOptionPane pane = new MOptionPane(this);
    pane.showInputDialog(
        title,
        content,
        new MOptionPane.OnInputListener() {
          @Override
          public void onResult(String input) {
            // User may copy from the input field. No further action needed.
          }
        });
  }

  // =========================================================================
  // BUTTON ACTIONS
  // =========================================================================

  private void executeExit() {
    if (backgroundExecutor != null) {
      backgroundExecutor.shutdownNow();
    }
    finishAndRemoveTask();
  }

  /**
   * Renders the current editor content as a ray traced image. Parses the scene text, renders it
   * with the MuratRayTracer engine, and displays the resulting Bitmap in the ImageView.
   */
  private void executeGenerate() {
    final String sceneText = editor.getFullText();
    if (sceneText.trim().isEmpty()) {
      Toast.makeText(this, "Scene text is empty!", Toast.LENGTH_SHORT).show();
      return;
    }

    showLoading(true);

    backgroundExecutor.execute(
        new Runnable() {
          @Override
          public void run() {
            try {
              SceneParser parser = new SceneParser(maxWidth, maxHeight, maxDepth, maxSampling);
              final long startTime = System.currentTimeMillis();
              final Bitmap renderedBitmap = parser.renderSceneFromText(sceneText);
              final long elapsedMs = System.currentTimeMillis() - startTime;

              if (renderedBitmap == null) {
                mainHandler.post(
                    new Runnable() {
                      @Override
                      public void run() {
                        showLoading(false);
                        Toast.makeText(
                                RayTracerAndroid.this,
                                "Render aborted. Max size is " + maxWidth + "x" + maxHeight,
                                Toast.LENGTH_LONG)
                            .show();
                      }
                    });
                return;
              }

              currentRenderedBitmap = renderedBitmap;
              final int rw = renderedBitmap.getWidth();
              final int rh = renderedBitmap.getHeight();

              mainHandler.post(
                  new Runnable() {
                    @Override
                    public void run() {
                      imageView.setImageBitmap(currentRenderedBitmap);
                      showLoading(false);
                      Toast.makeText(
                              RayTracerAndroid.this,
                              "Render complete: " + elapsedMs + " ms (" + rw + "x" + rh + ")",
                              Toast.LENGTH_SHORT)
                          .show();
                    }
                  });

            } catch (final Exception e) {
              mainHandler.post(
                  new Runnable() {
                    @Override
                    public void run() {
                      showLoading(false);
                      Toast.makeText(
                              RayTracerAndroid.this,
                              "Render failed: " + e.getMessage(),
                              Toast.LENGTH_LONG)
                          .show();
                    }
                  });
            }
          }
        });
  }

  private void executeSaveImage() {
    if (currentRenderedBitmap == null) {
      Toast.makeText(this, "No image rendered yet. Press Generate first.", Toast.LENGTH_SHORT)
          .show();
      return;
    }

    showLoading(true);

    backgroundExecutor.execute(
        new Runnable() {
          @Override
          public void run() {
            try {
              if (SAVEDIR != null && !SAVEDIR.exists()) SAVEDIR.mkdirs();

              SimpleDateFormat sdf = new SimpleDateFormat("yyMMdd_HHmmss", Locale.US);
              String fileName = "rt_" + sdf.format(new Date()) + ".jpg";
              File outputFile = new File(SAVEDIR, fileName);

              FileOutputStream fos = new FileOutputStream(outputFile);
              currentRenderedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos);
              fos.flush();
              fos.close();

              final String savedPath = outputFile.getAbsolutePath();

              mainHandler.post(
                  new Runnable() {
                    @Override
                    public void run() {
                      showLoading(false);
                      Toast.makeText(
                              RayTracerAndroid.this, "Image saved: " + savedPath, Toast.LENGTH_LONG)
                          .show();
                    }
                  });

            } catch (final Exception e) {
              mainHandler.post(
                  new Runnable() {
                    @Override
                    public void run() {
                      showLoading(false);
                      Toast.makeText(
                              RayTracerAndroid.this,
                              "Save failed: " + e.getMessage(),
                              Toast.LENGTH_LONG)
                          .show();
                    }
                  });
            }
          }
        });
  }

  // =========================================================================
  // ACTIVITY RESULTS & FILE HANDLING
  // =========================================================================

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);

    if (requestCode == MOVIE_REQUEST_CODE) {
      if (moviePicker.handleResult(requestCode, resultCode, data)) {
        List<Uri> selectedUris = moviePicker.getSelectedUris();
        if (selectedUris.isEmpty()) return;

        if (selectedUris.size() > MAX_MOVIE_FRAMES) {
          Toast.makeText(this, "Max " + MAX_MOVIE_FRAMES + " images allowed!", Toast.LENGTH_LONG)
              .show();
          return;
        }

        Collections.sort(
            selectedUris,
            new Comparator<Uri>() {
              @Override
              public int compare(Uri u1, Uri u2) {
                String n1 = getFileNameFromUri(u1);
                String n2 = getFileNameFromUri(u2);
                return n1.compareToIgnoreCase(n2);
              }
            });

        generateMovie(selectedUris);
      }
      return;
    }

    if (filePicker.handleResult(requestCode, resultCode, data)) {
      Uri uri = filePicker.getSelectedUri();
      if (uri == null) return;

      if (requestCode == MFilePicker.OPEN_REQUEST_CODE) {
        loadSceneFromUri(uri);
      } else if (requestCode == MFilePicker.SAVE_REQUEST_CODE) {
        checkExtensionAndSaveScene(uri);
      }
    }
  }

  // =========================================================================
  // TO MOVIE LOGIC (MediaCodec + MediaMuxer)
  // =========================================================================

  /**
   * Creates an MP4 video from a list of JPG image URIs using Android's native MediaCodec API.
   *
   * <p>FPS handling: KEY_FRAME_RATE is only a metadata hint and requires an integer. We pin it to
   * 30 so the encoder never re-times the stream. The actual playback speed is controlled entirely
   * by presentationTimeUs, which is incremented by 1_000_000 / currentMovieFps per frame. This
   * makes values like 0.5 (2 seconds per frame) and 2.0 (0.5 seconds per frame) produce the
   * expected 4x speed difference.
   */
  private void generateMovie(final List<Uri> imageUris) {
    showLoading(true);

    backgroundExecutor.execute(
        new Runnable() {
          @Override
          public void run() {
            MediaCodec codec = null;
            MediaMuxer muxer = null;
            Surface inputSurface = null;

            try {
              // 1. Create output file
              if (MOVIEDIR != null && !MOVIEDIR.exists()) MOVIEDIR.mkdirs();
              SimpleDateFormat sdf = new SimpleDateFormat("yyMMdd_HHmmss", Locale.US);
              String videoFileName = "rt_" + sdf.format(new Date()) + ".mp4";
              final String outputPath = new File(MOVIEDIR, videoFileName).getAbsolutePath();

              // 2. Configure Video Format (H.264, 320x240)
              // MediaFormat format = MediaFormat.createVideoFormat("video/avc", 320, 240);
              // 2. Configure Video Format (H.264)
              // Video resolution follows the user's max render size,
              // clamped to 800x600 for performance/safety.
              int videoWidth = maxWidth;
              int videoHeight = maxHeight;
              if (videoWidth > 800 || videoHeight > 600) {
                videoWidth = 800;
                videoHeight = 600;
                mainHandler.post(
                    new Runnable() {
                      @Override
                      public void run() {
                        Toast.makeText(
                                RayTracerAndroid.this,
                                "Video resolution clamped to 800x600 for performance.",
                                Toast.LENGTH_LONG)
                            .show();
                      }
                    });
              }
              MediaFormat format =
                  MediaFormat.createVideoFormat("video/avc", videoWidth, videoHeight);

              format.setInteger(
                  MediaFormat.KEY_COLOR_FORMAT,
                  MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
              format.setInteger(MediaFormat.KEY_BIT_RATE, 2000000); // 2 Mbps
              // KEY_FRAME_RATE is a hint; pinning to 30 avoids encoder re-timing.
              format.setInteger(MediaFormat.KEY_FRAME_RATE, 30);
              format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);

              // 3. Create and configure MediaCodec
              codec = MediaCodec.createEncoderByType("video/avc");
              codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
              inputSurface = codec.createInputSurface();
              codec.start();

              // 4. Create and configure MediaMuxer
              muxer = new MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

              MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
              int trackIndex = -1;
              boolean muxerStarted = false;

              long presentationTimeUs = 0;
              long frameDurationUs = (long) (1000000L / currentMovieFps);

              // 5. Loop through frames and render to Surface
              for (int i = 0; i < imageUris.size(); i++) {
                Uri uri = imageUris.get(i);

                InputStream is = getContentResolver().openInputStream(uri);
                Bitmap frameBitmap = BitmapFactory.decodeStream(is);
                is.close();

                if (frameBitmap == null) continue;

                Canvas canvas = inputSurface.lockCanvas(null);
                canvas.drawColor(Color.BLACK);

                // float scale = Math.min(320f / frameBitmap.getWidth(),
                //        240f / frameBitmap.getHeight());
                // int newW = (int) (frameBitmap.getWidth() * scale);
                // int newH = (int) (frameBitmap.getHeight() * scale);
                // int left = (320 - newW) / 2;
                // int top = (240 - newH) / 2;

                float scale =
                    Math.min(
                        (float) videoWidth / frameBitmap.getWidth(),
                        (float) videoHeight / frameBitmap.getHeight());
                int newW = (int) (frameBitmap.getWidth() * scale);
                int newH = (int) (frameBitmap.getHeight() * scale);
                int left = (videoWidth - newW) / 2;
                int top = (videoHeight - newH) / 2;

                Rect dstRect = new Rect(left, top, left + newW, top + newH);
                canvas.drawBitmap(frameBitmap, null, dstRect, new Paint());

                inputSurface.unlockCanvasAndPost(canvas);
                frameBitmap.recycle();

                // 6. Dequeue and encode frames
                int outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, 10000);
                while (outputBufferIndex >= 0) {
                  ByteBuffer outputBuffer = codec.getOutputBuffer(outputBufferIndex);

                  if (outputBuffer != null && bufferInfo.size > 0) {
                    if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                      if (!muxerStarted) {
                        trackIndex = muxer.addTrack(codec.getOutputFormat());
                        muxer.start();
                        muxerStarted = true;
                      }

                      outputBuffer.position(bufferInfo.offset);
                      outputBuffer.limit(bufferInfo.offset + bufferInfo.size);
                      bufferInfo.presentationTimeUs = presentationTimeUs;
                      muxer.writeSampleData(trackIndex, outputBuffer, bufferInfo);

                      presentationTimeUs += frameDurationUs;
                    }
                  }

                  codec.releaseOutputBuffer(outputBufferIndex, false);
                  outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, 0);
                }
              }

              // 7. Send EOS (End of Stream) signal
              codec.signalEndOfInputStream();
              boolean eosReceived = false;
              while (!eosReceived) {
                int outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, 10000);
                if (outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER) {
                  continue;
                } else if (outputBufferIndex >= 0) {
                  ByteBuffer outputBuffer = codec.getOutputBuffer(outputBufferIndex);
                  if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0
                      && bufferInfo.size > 0) {
                    if (outputBuffer != null && muxerStarted) {
                      outputBuffer.position(bufferInfo.offset);
                      outputBuffer.limit(bufferInfo.offset + bufferInfo.size);
                      bufferInfo.presentationTimeUs = presentationTimeUs;
                      muxer.writeSampleData(trackIndex, outputBuffer, bufferInfo);
                    }
                  }
                  if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    eosReceived = true;
                  }
                  codec.releaseOutputBuffer(outputBufferIndex, false);
                }
              }

              mainHandler.post(
                  new Runnable() {
                    @Override
                    public void run() {
                      showLoading(false);
                      Toast.makeText(
                              RayTracerAndroid.this,
                              "Video saved: " + outputPath,
                              Toast.LENGTH_LONG)
                          .show();
                    }
                  });

            } catch (final Exception e) {
              mainHandler.post(
                  new Runnable() {
                    @Override
                    public void run() {
                      showLoading(false);
                      Toast.makeText(
                              RayTracerAndroid.this,
                              "Movie creation failed: " + e.getMessage(),
                              Toast.LENGTH_LONG)
                          .show();
                    }
                  });
            } finally {
              if (codec != null) {
                try {
                  codec.stop();
                } catch (Exception ignored) {
                }
                try {
                  codec.release();
                } catch (Exception ignored) {
                }
              }
              if (inputSurface != null) {
                try {
                  inputSurface.release();
                } catch (Exception ignored) {
                }
              }
              if (muxer != null) {
                try {
                  muxer.stop();
                } catch (Exception ignored) {
                }
                try {
                  muxer.release();
                } catch (Exception ignored) {
                }
              }
            }
          }
        });
  }

  // =========================================================================
  // SCENE FILE HANDLING
  // =========================================================================

  private void loadSceneFromUri(final Uri uri) {
    showLoading(true);
    backgroundExecutor.execute(
        new Runnable() {
          @Override
          public void run() {
            try {
              InputStream is = getContentResolver().openInputStream(uri);
              BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
              StringBuilder sb = new StringBuilder();
              String line;
              while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
              }
              reader.close();
              is.close();

              final String content = sb.toString();
              mainHandler.post(
                  new Runnable() {
                    @Override
                    public void run() {
                      editor.setEditorText(content);
                      showLoading(false);
                      Toast.makeText(RayTracerAndroid.this, "Scene loaded", Toast.LENGTH_SHORT)
                          .show();
                    }
                  });
            } catch (Exception e) {
              mainHandler.post(
                  new Runnable() {
                    @Override
                    public void run() {
                      showLoading(false);
                      Toast.makeText(
                              RayTracerAndroid.this, "Failed to load scene", Toast.LENGTH_LONG)
                          .show();
                    }
                  });
            }
          }
        });
  }

  private void checkExtensionAndSaveScene(final Uri uri) {
    String fileName = getFileNameFromUri(uri);
    if (fileName == null || !fileName.toLowerCase(Locale.US).endsWith(".txt")) {
      Toast.makeText(this, "Invalid extension! Please use .txt", Toast.LENGTH_LONG).show();
      return;
    }

    final String content = editor.getFullText();
    showLoading(true);

    backgroundExecutor.execute(
        new Runnable() {
          @Override
          public void run() {
            try {
              FileOutputStream fos = (FileOutputStream) getContentResolver().openOutputStream(uri);
              fos.write(content.getBytes("UTF-8"));
              fos.flush();
              fos.close();

              mainHandler.post(
                  new Runnable() {
                    @Override
                    public void run() {
                      showLoading(false);
                      Toast.makeText(RayTracerAndroid.this, "Scene saved", Toast.LENGTH_SHORT)
                          .show();
                    }
                  });
            } catch (Exception e) {
              mainHandler.post(
                  new Runnable() {
                    @Override
                    public void run() {
                      showLoading(false);
                      Toast.makeText(
                              RayTracerAndroid.this, "Failed to save scene", Toast.LENGTH_LONG)
                          .show();
                    }
                  });
            }
          }
        });
  }

  // =========================================================================
  // UTILITIES
  // =========================================================================

  private String getFileNameFromUri(Uri uri) {
    String result = null;
    if ("content".equals(uri.getScheme())) {
      Cursor cursor = getContentResolver().query(uri, null, null, null, null);
      try {
        if (cursor != null && cursor.moveToFirst()) {
          int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
          if (index != -1) result = cursor.getString(index);
        }
      } finally {
        if (cursor != null) cursor.close();
      }
    }
    if (result == null) {
      result = uri.getPath();
      int cut = result.lastIndexOf('/');
      if (cut != -1) result = result.substring(cut + 1);
    }
    return result;
  }

  private void showLoading(final boolean show) {
    mainHandler.post(
        new Runnable() {
          @Override
          public void run() {
            loadingOverlay.setVisibility(show ? View.VISIBLE : View.GONE);

            btnAdd.setEnabled(!show);
            btnSet.setEnabled(!show);
            btnSelectScene.setEnabled(!show);
            btnSaveScene.setEnabled(!show);
            btnGenerate.setEnabled(!show);
            btnSaveImage.setEnabled(!show);
            btnToMovie.setEnabled(!show);

            if (editor != null) {
              editor.setExternalReadOnly(show);
            }
          }
        });
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    if (backgroundExecutor != null) {
      backgroundExecutor.shutdownNow();
      backgroundExecutor = null;
    }
    imageView = null;
    editor = null;
    currentRenderedBitmap = null;
    filePicker = null;
    moviePicker = null;
    mainHandler = null;
  }
}
