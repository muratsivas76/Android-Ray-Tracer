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
import android.text.Editable;
import android.text.InputType;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.util.Log;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Surface;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import net.murat.light.Light;
import net.murat.lovert.*;
import net.murat.material.Material;
import net.murat.material.OurFontFactory;
import net.murat.math.*;
import net.murat.shape.CSGShape;
import net.murat.shape.EMShape;
import net.murat.tool.*;
import net.murat.uikit.*;
import net.murat.util.*;

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
  private Button btnToMovie, btnGenerate, btnSaveImage;
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

  private String searchTerm = "";

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
          + "    firstAnimationPoint = P(0.00, 0.00, 0.00);\n"
          + "    secondAnimationPoint = P(0.00, 0.00, 0.00);\n"
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
          + "MuratAmbientLight ambient {\n"
          + "    name = ambient;\n"
          + "    color = 0xFFFFFFFF;\n"
          + "    intensity = 1.0;\n"
          + "    firstAnimationIntensity = 0.0;\n"
          + "    secondAnimationIntensity = 0.0;\n"
          + "    positionDelta = [0.0, 0.0, 0.0];\n"
          + "    colorDelta = [0, 0, 0, 0];\n"
          + "}\n"
          + "\n"
          + "MuratPointLight point {\n"
          + "    name = point;\n"
          + "    position = P(2.000, 5.000, 2.000);\n"
          + "    color = 0xFFFFFFFF;\n"
          + "    intensity = 1.0;\n"
          + "    firstAnimationIntensity = 0.0;\n"
          + "    secondAnimationIntensity = 0.0;\n"
          + "    positionDelta = [0.0, 0.0, 0.0];\n"
          + "    colorDelta = [0, 0, 0, 0];\n"
          + "    constantAttenuation = 1.0;\n"
          + "    linearAttenuation = 0.1;\n"
          + "    quadraticAttenuation = 0.01;\n"
          + "}\n"
          + "\n"
          + "# Geometries/Shapes\n"
          + "Plane plane {\n"
		  + "  name = plane;\n"
          + "  pointOnPlane = P(0.00, 0.00, 0.00);\n"
          + "  normal = V(0.00, 1.00, 0.00);\n"
          + "  transform = translate(0.000, -2.000, 0.000) * rotate(0.000, -0.000, 0.000) * scale(1.000, 1.000, 1.000);\n"
          + "  shadowColor = 0xFF000000;\n"
          + "  shadowBias = 0.001;\n"
          + "  isVisibleSpecial = true;\n"
          + "  isVisible = true;\n"
          + "  isShadowEnable = false;\n"
          + "  isShadowOnly = false;\n"
          + "  isReflective = true;\n"
          + "  isRefractive = true;\n"
          + "  material = CheckerboardMaterial checkerboardmaterial {\n"
          + "    color1 = 0xFFFFFFFF;\n"
          + "    color2 = 0xFFFF0000;\n"
          + "    size = 1.0;\n"
          + "    ambientCoeff = 0.4;\n"
          + "    diffuseCoeff = 0.7;\n"
          + "    specularCoeff = 0.2;\n"
          + "    shininess = 32.0;\n"
          + "    specularColor = 0xFFFFFFFF;\n"
          + "    reflectivity = 0.0;\n"
          + "    ior = 1.0;\n"
          + "    transparency = 0.0;\n"
          + "  };\n"
          + "}\n"
          + "\n"
          + "# Overlays\n"
          + "\n"
          + "-END-\n";

  // Dynamic FPS set by the user
  private float currentMovieFps = 1.0f;

  // Settings limits (populated by loadSettings())
  private int maxWidth = 640;
  private int maxHeight = 480;
  private int maxDepth = 4;
  private int maxSampling = 2;

  private boolean isBusy = false;

  // Last applied editor visual settings (used by Set dialog to prefill values)
  private String currentFontFace = "default";
  private int currentFontStyle = 0;
  private float currentLineSpacing = 1.1f;
  private int currentMaxAnimationFramesCount = 40;

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
    SceneParser.setBaseDir(new File(MAINPATH));

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
  } // end of onCreate

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    getMenuInflater().inflate(R.menu.menu, menu);
    return true;
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    int id = item.getItemId();

    if (id == R.id.menu_clear_all) {
      executeClearAll();
      return true;
    } else if (id == R.id.menu_trim_final) {
      executeTrimFinal();
      return true;
    } else if (id == R.id.menu_change_find_term) {
      executeChangeFindTerm();
      return true;
    } else if (id == R.id.menu_find) {
      executeFind();
      return true;
    } else if (id == R.id.menu_regex) {
      executeRegex();
      return true;
    } else if (id == R.id.menu_animation) {
      executeAnimation();
      return true;
    } else if (id == R.id.menu_delete_object) {
      executeDeleteObjectByName();
      return true;
    } else if (id == R.id.menu_exit) {
      executeExit();
      return true;
    }

    return super.onOptionsItemSelected(item);
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

    btnToMovie = (Button) findViewById(R.id.btn_to_movie);
    btnGenerate = (Button) findViewById(R.id.btn_generate);
    btnSaveImage = (Button) findViewById(R.id.btn_save_image);

    btnAdd = (Button) findViewById(R.id.btn_add);
    btnSet = (Button) findViewById(R.id.btn_set);
    btnSelectScene = (Button) findViewById(R.id.btn_select_scene);
    btnSaveScene = (Button) findViewById(R.id.btn_save_scene);
  }

  private void setupListeners() {
    // 1. TO MOVIE (Multi-select JPGs) - Fixed FPS description
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

    // 2. GENERATE
    btnGenerate.setOnClickListener(
        new View.OnClickListener() {
          @Override
          public void onClick(View v) {
            executeGenerate();
          }
        });

    // 3. SAVE IMAGE
    btnSaveImage.setOnClickListener(
        new View.OnClickListener() {
          @Override
          public void onClick(View v) {
            executeSaveImage();
          }
        });

    // 4. ADD (Insert template into editor)
    btnAdd.setOnClickListener(
        new View.OnClickListener() {
          @Override
          public void onClick(View v) {
            showAddDialog();
          }
        });

    // 5. SET (Temporary MEditor visual settings dialog)
    btnSet.setOnClickListener(
        new View.OnClickListener() {
          @Override
          public void onClick(View v) {
            showSetDialog();
          }
        });

    // 6. SELECT SCENE
    btnSelectScene.setOnClickListener(
        new View.OnClickListener() {
          @Override
          public void onClick(View v) {
            filePicker.setMimeTypes(new String[] {"text/plain"});
            filePicker.setMultiSelectionEnabled(false);
            filePicker.showOpenDialog(MFilePicker.OPEN_REQUEST_CODE);
          }
        });

    // 7. SAVE SCENE
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
      currentMaxAnimationFramesCount =
          parseIntSafe(props.getProperty("maxAnimationFramesCount"), 40);

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

  // MENU OPERATIONS
  /** Prompts the user for confirmation and clears all text inside the editor. */
  private void executeClearAll() {
    if (isBusy) {
      return;
    }

    if (editor == null || editor.getFullText().isEmpty()) {
      Toast.makeText(this, "Editor is already empty.", Toast.LENGTH_SHORT).show();
      return;
    }

    MOptionPane pane = new MOptionPane(this);
    pane.showConfirmDialog(
        "Do you want to delete all text? This action cannot be undone.",
        new MOptionPane.OnConfirmListener() {
          @Override
          public void onResult(int option) {
            if (option == MOptionPane.YES_OPTION) {
              if (isBusy) {
                return;
              }
              editor.setEditorText("");
              Toast.makeText(RayTracerAndroid.this, "All text cleared.", Toast.LENGTH_SHORT).show();
            }
          }
        });
  }

  /** Removes trailing whitespace from the end of each line while preserving leading indentation. */
  private void executeTrimFinal() {
    if (isBusy) {
      return;
    }

    if (editor == null) {
      return;
    }

    final String rawText = editor.getFullText();
    if (rawText.isEmpty()) {
      Toast.makeText(this, "Editor is empty.", Toast.LENGTH_SHORT).show();
      return;
    }

    showLoading(true);

    backgroundExecutor.execute(
        new Runnable() {
          @Override
          public void run() {
            try {
              String[] lines = rawText.split("\n", -1);
              StringBuilder sb = new StringBuilder(rawText.length());

              for (int i = 0; i < lines.length; i++) {
                String line = lines[i];
                int end = line.length();
                while (end > 0
                    && (line.charAt(end - 1) == ' '
                        || line.charAt(end - 1) == '\t'
                        || line.charAt(end - 1) == '\r')) {
                  end--;
                }
                sb.append(line, 0, end);
                if (i < lines.length - 1) {
                  sb.append('\n');
                }
              }

              final String trimmedText = sb.toString();

              mainHandler.post(
                  new Runnable() {
                    @Override
                    public void run() {
                      showLoading(false);
                      if (!trimmedText.equals(rawText)) {
                        editor.setEditorText(trimmedText, true);
                        Toast.makeText(
                                RayTracerAndroid.this,
                                "Trailing line spaces trimmed.",
                                Toast.LENGTH_SHORT)
                            .show();
                      } else {
                        Toast.makeText(
                                RayTracerAndroid.this,
                                "No trailing spaces found.",
                                Toast.LENGTH_SHORT)
                            .show();
                      }
                    }
                  });
            } catch (final Throwable e) {
              mainHandler.post(
                  new Runnable() {
                    @Override
                    public void run() {
                      showLoading(false);
                      Toast.makeText(
                              RayTracerAndroid.this,
                              "Trim failed: " + e.getMessage(),
                              Toast.LENGTH_LONG)
                          .show();
                    }
                  });
            }
          }
        });
  }

  /** Prompts the user to enter or update the active search term (supports regular expressions). */
  private void executeChangeFindTerm() {
    if (isBusy) {
      return;
    }

    MOptionPane pane = new MOptionPane(this);
    pane.showInputDialog(
        "Enter search term (regex supported):",
        searchTerm,
        new MOptionPane.OnInputListener() {
          @Override
          public void onResult(String input) {
            if (isBusy) {
              return;
            }

            // User cancelled the dialog
            if (input == null) {
              return;
            }

            if (input.isEmpty()) {
              Toast.makeText(
                      RayTracerAndroid.this, "Search term cannot be empty.", Toast.LENGTH_SHORT)
                  .show();
              return;
            }

            searchTerm = input;
            Toast.makeText(
                    RayTracerAndroid.this, "Search term updated: " + searchTerm, Toast.LENGTH_SHORT)
                .show();
          }
        });
  }

  /**
   * Searches for the active searchTerm starting from the current caret position. Highlights the
   * matched text with a yellow background and moves the caret to the end of the match.
   */
  private void executeFind() {
    if (isBusy) {
      return;
    }

    if (editor == null) {
      return;
    }

    if (searchTerm == null || searchTerm.isEmpty()) {
      Toast.makeText(this, "Search term is empty. Set it first.", Toast.LENGTH_SHORT).show();
      return;
    }

    Editable editable = editor.getText();
    if (editable == null || editable.length() == 0) {
      Toast.makeText(this, "Editor is empty.", Toast.LENGTH_SHORT).show();
      return;
    }

    // Clear any previous yellow highlight spans
    BackgroundColorSpan[] existingSpans =
        editable.getSpans(0, editable.length(), BackgroundColorSpan.class);
    for (int i = 0; i < existingSpans.length; i++) {
      editable.removeSpan(existingSpans[i]);
    }

    int startPos = editor.getSelectionStart();
    if (startPos < 0) {
      startPos = 0;
    }

    String fullText = editable.toString();
    if (startPos > fullText.length()) {
      startPos = fullText.length();
    }

    try {
      Pattern pattern = Pattern.compile(searchTerm);
      Matcher matcher = pattern.matcher(fullText);

      if (matcher.find(startPos)) {
        int matchStart = matcher.start();
        int matchEnd = matcher.end();

        editable.setSpan(
            new BackgroundColorSpan(Color.YELLOW),
            matchStart,
            matchEnd,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        // Enable focus if first-tap guard is still active so caret moves visibly
        editor.setFocusable(true);
        editor.setFocusableInTouchMode(true);
        editor.requestFocus();
        editor.setSelection(matchEnd);
      } else {
        Toast.makeText(this, "No match found from current position.", Toast.LENGTH_SHORT).show();
      }
    } catch (PatternSyntaxException e) {
      Toast.makeText(this, "Invalid regex: " + e.getDescription(), Toast.LENGTH_LONG).show();
    }
  }

  /**
   * Performs a two-step Regex Find & Replace All operation. Applies to the selected text if a
   * selection exists, otherwise applies to the entire editor text. Supports $1, $2 group
   * backreferences and escape sequences (\n, \r, \t).
   */
  private void executeRegex() {
    if (isBusy) {
      return;
    }

    if (editor == null) {
      return;
    }

    final String fullText = editor.getFullText();
    if (fullText.isEmpty()) {
      Toast.makeText(this, "Editor is empty.", Toast.LENGTH_SHORT).show();
      return;
    }

    int rawStart = editor.getSelectionStart();
    int rawEnd = editor.getSelectionEnd();
    final boolean hasSelection = (rawStart >= 0 && rawEnd >= 0 && rawStart != rawEnd);
    final int selStart = hasSelection ? Math.min(rawStart, rawEnd) : 0;
    final int selEnd = hasSelection ? Math.max(rawStart, rawEnd) : fullText.length();

    String scopeLabel = hasSelection ? "Selection" : "All Text";

    MOptionPane pane = new MOptionPane(this);
    pane.showInputDialog(
        "Regex Find (" + scopeLabel + " - regex allowed):",
        searchTerm,
        new MOptionPane.OnInputListener() {
          @Override
          public void onResult(final String srcInput) {
            if (isBusy || srcInput == null) {
              return;
            }

            if (srcInput.isEmpty()) {
              Toast.makeText(
                      RayTracerAndroid.this, "Source pattern cannot be empty.", Toast.LENGTH_SHORT)
                  .show();
              return;
            }

            MOptionPane dstPane = new MOptionPane(RayTracerAndroid.this);
            dstPane.showInputDialog(
                "Regex Replace With (regex/$1/\\n/\\t allowed):",
                "",
                new MOptionPane.OnInputListener() {
                  @Override
                  public void onResult(final String dstInput) {
                    if (isBusy || dstInput == null) {
                      return;
                    }

                    try {
                      String targetSlice = fullText.substring(selStart, selEnd);
                      Pattern pattern = Pattern.compile(srcInput);
                      Matcher matcher = pattern.matcher(targetSlice);

                      // Count matches first for user feedback
                      int matchCount = 0;
                      while (matcher.find()) {
                        matchCount++;
                      }

                      if (matchCount == 0) {
                        Toast.makeText(
                                RayTracerAndroid.this, "No matches found.", Toast.LENGTH_SHORT)
                            .show();
                        return;
                      }

                      // Decode \n, \r, \t while preserving $1, $2 group references
                      String decodedReplacement = unescapeReplacementTokens(dstInput);
                      matcher.reset();
                      String replacedSlice = matcher.replaceAll(decodedReplacement);

                      String updatedFullText =
                          fullText.substring(0, selStart)
                              + replacedSlice
                              + fullText.substring(selEnd);

                      searchTerm = srcInput;
                      editor.setEditorText(updatedFullText, true);

                      Toast.makeText(
                              RayTracerAndroid.this,
                              "Replaced " + matchCount + " occurrence(s).",
                              Toast.LENGTH_SHORT)
                          .show();

                    } catch (PatternSyntaxException e) {
                      Toast.makeText(
                              RayTracerAndroid.this,
                              "Invalid regex pattern: " + e.getDescription(),
                              Toast.LENGTH_LONG)
                          .show();
                    } catch (IllegalArgumentException | IndexOutOfBoundsException e) {
                      Toast.makeText(
                              RayTracerAndroid.this,
                              "Invalid replacement group/syntax: " + e.getMessage(),
                              Toast.LENGTH_LONG)
                          .show();
                    }
                  }
                });
          }
        });
  }

  /**
   * Converts literal \n, \r, and \t sequences entered in a single-line dialog into actual newline,
   * space, and tab characters, while keeping regex group tokens ($1, $2) intact.
   */
  private String unescapeReplacementTokens(String raw) {
    if (raw == null || raw.isEmpty()) {
      return "";
    }
    StringBuilder sb = new StringBuilder(raw.length());
    int len = raw.length();
    for (int i = 0; i < len; i++) {
      char c = raw.charAt(i);
      if (c == '\\' && i + 1 < len) {
        char next = raw.charAt(i + 1);
        if (next == 'n') {
          sb.append('\n');
          i++;
          continue;
        } else if (next == 'r') {
          // Converted to space as requested
          sb.append(' ');
          i++;
          continue;
        } else if (next == 't') {
          sb.append('\t');
          i++;
          continue;
        } else if (next == '\\') {
          // Preserve escaped backslash for Matcher.replaceAll
          sb.append("\\\\");
          i++;
          continue;
        }
      }
      sb.append(c);
    }
    return sb.toString();
  }

  // ANIMATION
  private void executeAnimation() {
    if (isBusy) {
      return;
    }
    if (editor == null || editor.getFullText().trim().isEmpty()) {
      Toast.makeText(this, "Scene text is empty!", Toast.LENGTH_SHORT).show();
      return;
    }

    MOptionPane pane = new MOptionPane(this);
    pane.showInputDialog(
        "Total frames (2-" + currentMaxAnimationFramesCount + "):",
        "10",
        new MOptionPane.OnInputListener() {
          @Override
          public void onResult(String input) {
            if (isBusy || input == null || input.trim().isEmpty()) {
              return;
            }
            int n;
            try {
              n = Integer.parseInt(input.trim());
            } catch (NumberFormatException e) {
              Toast.makeText(RayTracerAndroid.this, "Invalid number!", Toast.LENGTH_SHORT).show();
              return;
            }
            if (n < 2 || n > currentMaxAnimationFramesCount) {
              Toast.makeText(
                      RayTracerAndroid.this,
                      "Frames must be between 2 and " + currentMaxAnimationFramesCount,
                      Toast.LENGTH_LONG)
                  .show();
              return;
            }
            runAnimation(editor.getFullText(), n);
          }
        });
  }

  private void runAnimation(final String sceneText, final int totalFrames) {
    showLoading(true); // isBusy = true, UI locked

    backgroundExecutor.execute(
        new Runnable() {
          @Override
          public void run() {
            // Scene objects owned by the parser (empty until the scene is loaded)
            List<EMShape> animShapes = new ArrayList<EMShape>();
            List<Light> animLights = new ArrayList<Light>();

            Camera camera = null;
            Point3 originalCamPos = null;

            // Original state snapshots (restored in finally)
            java.util.List<Matrix4> originalTransforms = new ArrayList<Matrix4>();
            java.util.List<Matrix4[]> originalAnimTransforms = new ArrayList<Matrix4[]>();
            java.util.List<AnimationInfo> originalAnimInfos = new ArrayList<AnimationInfo>();
            java.util.List<Double> originalIntensities = new ArrayList<Double>();
            java.util.List<Point3> originalLightPositions = new ArrayList<Point3>();
            java.util.List<Integer> originalLightColors = new ArrayList<Integer>();
            boolean stateCaptured = false;

            try {
              // 1. Parse the scene only (no render). Shapes, lights and camera live in the parser.
              SceneParser parser = new SceneParser(maxWidth, maxHeight, maxDepth, maxSampling);
              parser.loadScene(sceneText);

              if (parser.getWidth() <= 0 || parser.getHeight() <= 0) {
                postToast("Animation aborted. Max size is " + maxWidth + "x" + maxHeight);
                return;
              }

              animShapes = parser.getSceneShapes();
              animLights = parser.getSceneLights();
              if (animShapes.isEmpty()) {
                postToast("No shapes in scene.");
                return;
              }

              camera = parser.getCamera();
              originalCamPos = camera.getCameraPosition();

              // Camera deltas come from the animation points. Missing points mean "no movement".
              Point3[] ap = camera.getAnimationPoints();
              Point3 camDelta1 =
                  (ap != null && ap.length > 0 && ap[0] != null) ? ap[0] : new Point3(0, 0, 0);
              Point3 camDelta2 =
                  (ap != null && ap.length > 1 && ap[1] != null) ? ap[1] : new Point3(0, 0, 0);

              // 2. Output folder: SAVEDIR/anim_yyMMdd_HHmmss
              SimpleDateFormat sdf = new SimpleDateFormat("yyMMdd_HHmmss", Locale.US);
              File animDir = new File(SAVEDIR, "anim_" + sdf.format(new Date()));
              if (!animDir.exists() && !animDir.mkdirs()) {
                throw new IllegalStateException("Cannot create " + animDir.getAbsolutePath());
              }

              // 3. Save the original state
              for (int i = 0; i < animShapes.size(); i++) {
                EMShape shape = animShapes.get(i);
                originalTransforms.add(shape.getTransform());
                originalAnimTransforms.add(shape.getAnimationTransforms());
                originalAnimInfos.add(
                    new AnimationInfo(
                        shape.isVisible(),
                        shape.isShadowEnable(),
                        shape.isShadowOnly(),
                        shape.isReflective(),
                        shape.isRefractive()));
              }
              for (int i = 0; i < animLights.size(); i++) {
                Light light = animLights.get(i);
                originalIntensities.add(light.getIntensity());
                Point3 p = light.getPosition();
                originalLightPositions.add(p != null ? new Point3(p.x, p.y, p.z) : null);
                originalLightColors.add(light.getColor());
              }
              stateCaptured = true;

              // First-half end values (updated at the last frame of the first half)
              java.util.List<Matrix4> firstHalfEndTransforms =
                  new ArrayList<Matrix4>(originalTransforms);
              java.util.List<Double> firstHalfEndIntensities =
                  new ArrayList<Double>(originalIntensities);
              java.util.List<Point3> firstHalfEndPositions = new ArrayList<Point3>();
              for (int i = 0; i < originalLightPositions.size(); i++) {
                Point3 p = originalLightPositions.get(i);
                firstHalfEndPositions.add(p != null ? new Point3(p.x, p.y, p.z) : null);
              }
              java.util.List<Integer> firstHalfEndColors =
                  new ArrayList<Integer>(originalLightColors);

              final int halfFrames = totalFrames / 2;
              boolean cancelled = false;
              int savedFrames = 0;

              // 4. Frame loop
              for (int frame = 0; frame < totalFrames; frame++) {
                if (Thread.currentThread().isInterrupted()) {
                  cancelled = true;
                  break;
                }

                boolean isFirstHalf = frame < halfFrames;
                int animIndex = isFirstHalf ? 0 : 1;
                int relativeFrame = isFirstHalf ? frame : frame - halfFrames;
                int transformFrame = relativeFrame + 1; // same logic for shapes, camera, lights
                boolean isHalfEndFrame = isFirstHalf && frame == halfFrames - 1;

                // ---- Camera ----
                Point3 baseCam;
                Point3 delta;
                if (isFirstHalf) {
                  baseCam = originalCamPos;
                  delta = camDelta1;
                } else {
                  baseCam =
                      new Point3(
                          originalCamPos.x + camDelta1.x * halfFrames,
                          originalCamPos.y + camDelta1.y * halfFrames,
                          originalCamPos.z + camDelta1.z * halfFrames);
                  delta = camDelta2;
                }
                // A new Point3 each frame avoids mutable aliasing with originalCamPos
                camera.setCameraPosition(
                    new Point3(
                        baseCam.x + delta.x * transformFrame,
                        baseCam.y + delta.y * transformFrame,
                        baseCam.z + delta.z * transformFrame));

                // ---- Shapes ----
                for (int i = 0; i < animShapes.size(); i++) {
                  EMShape shape = animShapes.get(i);

                  Matrix4 baseTransform =
                      isFirstHalf ? originalTransforms.get(i) : firstHalfEndTransforms.get(i);

                  // Scale the per-frame animation delta by the frame counter
                  Matrix4[] animArr = originalAnimTransforms.get(i);
                  Matrix4 animT =
                      (animArr != null && animArr.length > animIndex) ? animArr[animIndex] : null;

                  Matrix4 newTransform;
                  if (animT != null) {
                    Matrix4 scaledAnimT = new Matrix4();
                    scaledAnimT.setTx(animT.getTx() * transformFrame);
                    scaledAnimT.setTy(animT.getTy() * transformFrame);
                    scaledAnimT.setTz(animT.getTz() * transformFrame);
                    scaledAnimT.setRx(animT.getRx() * transformFrame);
                    scaledAnimT.setRy(animT.getRy() * transformFrame);
                    scaledAnimT.setRz(animT.getRz() * transformFrame);
                    // Scale is multiplicative around 1, so only the delta from 1 is multiplied
                    scaledAnimT.setSx(((animT.getSx() - 1) * transformFrame) + 1);
                    scaledAnimT.setSy(((animT.getSy() - 1) * transformFrame) + 1);
                    scaledAnimT.setSz(((animT.getSz() - 1) * transformFrame) + 1);
                    newTransform = Matrix4.add(baseTransform, scaledAnimT);
                  } else {
                    newTransform = baseTransform;
                  }
                  shape.setTransform(newTransform);

                  if (isHalfEndFrame) {
                    firstHalfEndTransforms.set(i, newTransform);
                  }

                  // Per-frame flags from otherAnimationInfo, falling back to the original flags
                  AnimationInfo ainfo;
                  String oinfo = shape.getOtherAnimationInfo();
                  String[] oinfos = (oinfo == null) ? new String[0] : oinfo.split(",");
                  if (oinfos.length > 0 && frame < oinfos.length) {
                    ainfo = createAnimationInfo(oinfos[frame].trim().replaceAll(" ", ""));
                  } else {
                    ainfo = originalAnimInfos.get(i);
                  }
                  shape.setVisible(ainfo.isVisible());
                  shape.setShadowEnable(ainfo.isShadowEnable());
                  shape.setShadowOnly(ainfo.isShadowOnly());
                  shape.setReflective(ainfo.isReflective());
                  shape.setRefractive(ainfo.isRefractive());

                  // Keep procedural materials in sync with the new object transform
                  // Material mat = shape.getMaterial();
                  // if (mat != null) {
                  // mat.setObjectTransform(newTransform.inverse());
                  // }

                  // Keep procedural materials in sync with the new object transform.
                  // CSG shapes do this themselves inside setTransform().
                  if (!(shape instanceof CSGShape)) {
                    Material mat = shape.getMaterial();
                    if (mat != null) {
                      mat.setObjectTransform(newTransform.inverse());
                    }
                  }
                }

                // ---- Lights ----
                for (int i = 0; i < animLights.size(); i++) {
                  Light light = animLights.get(i);

                  // Intensity (uses relativeFrame, same as the desktop version)
                  double[] incDec = light.getIncDecIntensity();
                  if (incDec != null && incDec.length >= 2) {
                    double baseIntensity = originalIntensities.get(i);
                    double newIntensity;
                    if (isFirstHalf) {
                      newIntensity = baseIntensity + (incDec[0] * relativeFrame);
                      if (isHalfEndFrame) {
                        firstHalfEndIntensities.set(i, newIntensity);
                      }
                    } else {
                      double firstHalfMax = baseIntensity + (incDec[0] * halfFrames);
                      newIntensity = firstHalfMax + (incDec[1] * relativeFrame);
                    }
                    light.setIntensity(newIntensity);
                  }

                  // Position (uses transformFrame)
                  double[] posDelta = light.getPositionDelta();
                  Point3 originalPos = originalLightPositions.get(i);
                  if (originalPos != null && posDelta != null && posDelta.length >= 3) {
                    Point3 base = isFirstHalf ? originalPos : firstHalfEndPositions.get(i);
                    if (base == null) {
                      base = originalPos;
                    }
                    Point3 newPos =
                        new Point3(
                            base.x + posDelta[0] * transformFrame,
                            base.y + posDelta[1] * transformFrame,
                            base.z + posDelta[2] * transformFrame);
                    if (isHalfEndFrame) {
                      firstHalfEndPositions.set(i, new Point3(newPos.x, newPos.y, newPos.z));
                    }
                    light.setPosition(newPos);
                  }

                  // Color (uses transformFrame)
                  int[] colDelta = light.getColorDelta();
                  if (colDelta != null && colDelta.length >= 4) {
                    int baseColor =
                        isFirstHalf ? originalLightColors.get(i) : firstHalfEndColors.get(i);
                    int newColor = ColorUtil.addColorDelta(baseColor, colDelta, transformFrame);
                    if (isHalfEndFrame) {
                      firstHalfEndColors.set(i, newColor);
                    }
                    light.setColor(newColor);
                  }
                }

                // ---- Render the current in-memory scene (no re-parse) ----
                final Bitmap frameBitmap = parser.renderCurrentScene();
                if (frameBitmap == null) {
                  throw new IllegalStateException("Frame " + (frame + 1) + " render failed.");
                }

                // ---- Save: rt_yyMMdd_HHmmss_NNN.jpg ----
                // The sequence suffix prevents name collisions when two frames finish in the
                // same second, and keeps name-based ordering intact for the To Movie picker.
                String fileName =
                    "rt_"
                        + sdf.format(new Date())
                        + "_"
                        + String.format(Locale.US, "%03d", frame + 1)
                        + ".jpg";
                FileOutputStream fos = new FileOutputStream(new File(animDir, fileName));
                try {
                  frameBitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos);
                  fos.flush();
                } finally {
                  fos.close();
                }
                savedFrames++;

                // ---- Show the frame on the UI thread ----
                currentRenderedBitmap = frameBitmap;
                mainHandler.post(
                    new Runnable() {
                      @Override
                      public void run() {
                        imageView.setImageBitmap(frameBitmap);
                      }
                    });
              }

              if (cancelled) {
                postToast("Animation cancelled. " + savedFrames + " frame(s) saved.");
              } else {
                postToast("Animation saved: " + animDir.getAbsolutePath());
              }

            } catch (final Throwable e) {
              Log.e(TAG, "Animation failed", e);
              postToast("Animation failed: " + e.getMessage());
            } finally {
              // Restore the original scene state (scene objects are shared by reference)
              if (stateCaptured) {
                if (camera != null && originalCamPos != null) {
                  camera.setCameraPosition(originalCamPos);
                }
                for (int i = 0; i < animShapes.size(); i++) {
                  EMShape shape = animShapes.get(i);
                  shape.setTransform(originalTransforms.get(i));
                  shape.setAnimationTransforms(originalAnimTransforms.get(i));
                  AnimationInfo ai = originalAnimInfos.get(i);
                  shape.setVisible(ai.isVisible());
                  shape.setShadowEnable(ai.isShadowEnable());
                  shape.setShadowOnly(ai.isShadowOnly());
                  shape.setReflective(ai.isReflective());
                  shape.setRefractive(ai.isRefractive());

                  // Material mat = shape.getMaterial();
                  // if (mat != null) {
                  // mat.setObjectTransform(originalTransforms.get(i).inverse());
                  // }

                  if (!(shape instanceof CSGShape)) {
                    Material mat = shape.getMaterial();
                    if (mat != null) {
                      mat.setObjectTransform(originalTransforms.get(i).inverse());
                    }
                  }
                }
                for (int i = 0; i < animLights.size(); i++) {
                  Light light = animLights.get(i);
                  light.setIntensity(originalIntensities.get(i));
                  Point3 op = originalLightPositions.get(i);
                  if (op != null) {
                    light.setPosition(op);
                  }
                  light.setColor(originalLightColors.get(i));
                }
              }
              showLoading(false); // isBusy = false, UI unlocked
            }
          }
        });
  }

  /** Shows a long toast from any thread. */
  private void postToast(final String msg) {
    mainHandler.post(
        new Runnable() {
          @Override
          public void run() {
            Toast.makeText(RayTracerAndroid.this, msg, Toast.LENGTH_LONG).show();
          }
        });
  }

  /**
   * Parses a 5-character animation flag string into an AnimationInfo. Position 0: 'v' = visible
   * Position 1: 'e' = shadow enabled Position 2: 'o' = shadow only Position 3: 'f' = reflective
   * Position 4: 'r' = refractive Strings shorter than 5 characters return the defaults (true, true,
   * false, true, true).
   */
  private AnimationInfo createAnimationInfo(String mtext) {
    AnimationInfo ainfo = new AnimationInfo(true, true, false, true, true);
    if (mtext == null || mtext.length() < 5) return ainfo;

    String text = mtext.toLowerCase(Locale.US);

    boolean isVisibleShape = true;
    boolean isShadowEnableShape = true;
    boolean isShadowOnlyShape = false;
    boolean isReflectiveShape = true;
    boolean isRefractiveShape = true;

    char chr = text.charAt(0);
    if (chr != 'v') isVisibleShape = false;
    chr = text.charAt(1);
    if (chr != 'e') isShadowEnableShape = false;
    chr = text.charAt(2);
    if (chr == 'o') isShadowOnlyShape = true;
    chr = text.charAt(3);
    if (chr != 'f') isReflectiveShape = false;
    chr = text.charAt(4);
    if (chr != 'r') isRefractiveShape = false;

    ainfo =
        new AnimationInfo(
            isVisibleShape,
            isShadowEnableShape,
            isShadowOnlyShape,
            isReflectiveShape,
            isRefractiveShape);

    return ainfo;
  }

  ////////////

  // =========================================================================
  // DELETE OBJECT BY NAME
  // =========================================================================

  /**
   * Prompts the user for a block name and removes the first matching definition block (Shape,
   * Light, CSG, or Material) from the editor text.
   */
  private void executeDeleteObjectByName() {
    if (isBusy) {
      return;
    }

    if (editor == null) {
      return;
    }

    MOptionPane pane = new MOptionPane(this);
    pane.showInputDialog(
        "Enter object name to delete:",
        new MOptionPane.OnInputListener() {
          @Override
          public void onResult(String input) {
            if (isBusy) {
              return;
            }

            // User cancelled the dialog
            if (input == null) {
              return;
            }

            final String name = input.trim();
            if (name.isEmpty()) {
              return;
            }

            // Protected blocks: silently ignore
            if ("mainCamera".equals(name) || "Settings".equals(name)) {
              return;
            }

            final String source = editor.getFullText();
            if (source == null || source.isEmpty()) {
              return;
            }

            final String result = deleteNamedBlock(source, name);
            if (result == null) {
              return;
            }

            editor.setEditorText(result, true);
          }
        });
  }

  /**
   * Removes the first definition block whose declared name equals {@code name}.
   *
   * @param text The current editor text
   * @param name The declared block name to remove
   * @return The modified text, or {@code null} if no matching block was found or braces are
   *     unbalanced
   */
  private String deleteNamedBlock(String text, String name) {
    if (text == null || name == null || name.isEmpty()) {
      return null;
    }

    // Keep trailing empty lines to preserve original layout
    final String[] lines = text.split("\n", -1);

    // 1. Locate the first matching definition header
    int startLine = -1;
    for (int i = 0; i < lines.length; i++) {
      if (isDefinitionHeaderFor(lines[i], name)) {
        startLine = i;
        break;
      }
    }
    if (startLine < 0) {
      return null;
    }

    // 2. Balance braces to locate the closing line of the block
    int endLine = -1;
    int depth = 0;
    boolean seenOpen = false;

    for (int i = startLine; i < lines.length; i++) {
      final String scan = stripLineComment(lines[i]);
      for (int c = 0; c < scan.length(); c++) {
        final char ch = scan.charAt(c);
        if (ch == '{') {
          depth++;
          seenOpen = true;
        } else if (ch == '}') {
          depth--;
          if (seenOpen && depth == 0) {
            endLine = i;
            break;
          }
          // Guard against a stray closing brace
          if (depth < 0) {
            return null;
          }
        }
      }
      if (endLine >= 0) {
        break;
      }
    }

    // Unbalanced braces: abort silently to prevent scene corruption
    if (endLine < 0) {
      return null;
    }

    // 3. Reconstruct text excluding the removed block lines
    final StringBuilder sb = new StringBuilder(text.length());
    for (int i = 0; i < lines.length; i++) {
      if (i >= startLine && i <= endLine) {
        continue;
      }
      if (sb.length() > 0) {
        sb.append('\n');
      }
      sb.append(lines[i]);
    }

    // 4. Collapse double blank lines left at the removal seam
    return collapseBlankSeam(sb.toString());
  }

  /**
   * Checks if {@code line} is a definition header declaring {@code name}. Supports "Type name {"
   * and "material = Type name {" formats.
   */
  private boolean isDefinitionHeaderFor(String line, String name) {
    if (line == null) {
      return false;
    }

    final String s = stripLineComment(line).trim();
    if (s.isEmpty()) {
      return false;
    }

    // Material form: "material = Type name {"
    if (s.startsWith("material")
        && s.length() > 8
        && (s.charAt(8) == ' ' || s.charAt(8) == '\t' || s.charAt(8) == '=')) {
      final int eq = s.indexOf('=');
      if (eq < 0) {
        return false;
      }
      return headerDeclaresName(s.substring(eq + 1).trim(), name);
    }

    // Shape, Light, or CSG form: "Type name {"
    return headerDeclaresName(s, name);
  }

  /** Verifies whether the second whitespace-separated token before '{' matches {@code name}. */
  private boolean headerDeclaresName(String headerBody, String name) {
    if (headerBody == null) {
      return false;
    }

    final String s = headerBody.trim();
    if (s.isEmpty()) {
      return false;
    }

    final int brace = s.indexOf('{');
    if (brace < 0) {
      return false;
    }

    final String beforeBrace = s.substring(0, brace).trim();
    if (beforeBrace.isEmpty()) {
      return false;
    }

    final String[] tokens = beforeBrace.split("\\s+");
    if (tokens.length < 2) {
      return false;
    }

    return tokens[1].equals(name);
  }

  /** Strips inline "///" comment tails so trailing markers do not affect brace balancing. */
  private String stripLineComment(String line) {
    if (line == null) {
      return "";
    }
    final int idx = line.indexOf("///");
    if (idx < 0) {
      return line;
    }
    return line.substring(0, idx);
  }

  /**
   * Collapses consecutive blank lines left after block deletion into a single blank line and trims
   * leading or trailing blank lines.
   */
  private String collapseBlankSeam(String text) {
    if (text == null) {
      return null;
    }

    final String[] lines = text.split("\n", -1);
    final StringBuilder sb = new StringBuilder(text.length());
    boolean previousBlank = false;

    for (int i = 0; i < lines.length; i++) {
      final String current = lines[i];
      final boolean currentBlank = current.trim().isEmpty();

      if (currentBlank) {
        if (sb.length() == 0 || previousBlank) {
          previousBlank = true;
          continue;
        }
        previousBlank = true;
      } else {
        previousBlank = false;
      }

      if (sb.length() > 0) {
        sb.append('\n');
      }
      sb.append(current);
    }

    return sb.toString();
  }

  private void executeExit() {
    if (backgroundExecutor != null) {
      backgroundExecutor.shutdownNow();
    }
    finishAndRemoveTask();
  }

  //////////////////

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
    firstList.add("S: CSG");
    firstList.add("O: OverlayText");
    firstList.add("O: OverlayImage");
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
              if ("CSG".equals(shapeType)) {
                showCsgDialog();
                return;
              }
              final String shapeTemplate = Utilities.getShapeTemplate(shapeType);
              if (shapeTemplate == null || shapeTemplate.isEmpty()) {
                Toast.makeText(RayTracerAndroid.this, "No template available.", Toast.LENGTH_SHORT)
                    .show();
                return;
              }
              // Ask for the material in a second dialog.
              showMaterialPickerForShape(shapeType, shapeTemplate);
            }

            if (selected.startsWith("O: ")) {
              final String shapeType = selected.substring(3);
              if ("OverlayText".equals(shapeType)) {
                showOverlayTextDialog();
                return;
              }
              if ("OverlayImage".equals(shapeType)) {
                showOverlayImageDialog();
                return;
              }
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

            insertAboveOverlayMarker(formatShapeBlock(shapeType, patched));
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
   * Resolves the base object name from a template's "name = ...;" line, falling back to
   * toLowerFirstChar(fallbackType) if missing or blank.
   */
  private String extractTemplateName(String template, String fallbackType) {
    String fallback = toLowerFirstChar(fallbackType);
    if (template == null || template.isEmpty()) {
      return fallback;
    }

    String[] lines = template.split("\n");
    for (int i = 0; i < lines.length; i++) {
      String line = lines[i].trim();
      if (line.startsWith("name")) {
        int eqIdx = line.indexOf('=');
        if (eqIdx >= 0) {
          String rawVal = line.substring(eqIdx + 1);
          int semiIdx = rawVal.indexOf(';');
          if (semiIdx >= 0) {
            rawVal = rawVal.substring(0, semiIdx);
          }
          int commentIdx = rawVal.indexOf("//");
          if (commentIdx >= 0) {
            rawVal = rawVal.substring(0, commentIdx);
          }
          String cleaned = rawVal.trim().replace("\"", "").replace("'", "");
          if (!cleaned.isEmpty()) {
            return cleaned;
          }
        }
      }
    }
    return fallback;
  }

  /**
   * Returns a unique object name based on baseName by checking existing block headers (e.g. "Type
   * name {") in the current editor text. If baseName is unused, returns baseName; otherwise tries
   * baseName2, baseName3, ...
   */
  private String resolveUniqueObjectName(String baseName) {
    if (baseName == null || baseName.isEmpty()) {
      return baseName;
    }
    if (editor == null) {
      return baseName;
    }

    String fullText = editor.getFullText();
    if (fullText == null || fullText.isEmpty()) {
      return baseName;
    }

    if (!isObjectNameInUse(fullText, baseName)) {
      return baseName;
    }

    int suffix = 2;
    while (suffix < 10000) {
      String candidate = baseName + suffix;
      if (!isObjectNameInUse(fullText, candidate)) {
        return candidate;
      }
      suffix++;
    }
    return baseName;
  }

  /**
   * Checks whether any definition header line in sceneText uses targetName. Handles plain headers
   * ("Type name {") and prefixed ones: CSG children ("left = Type name {", "right = ...") and
   * material blocks ("material = Type name {").
   */
  private boolean isObjectNameInUse(String sceneText, String targetName) {
    String[] lines = sceneText.split("\n");
    for (int i = 0; i < lines.length; i++) {
      String trimmed = lines[i].trim();
      if (trimmed.isEmpty() || trimmed.startsWith("#")) {
        continue;
      }
      int braceIdx = trimmed.indexOf('{');
      if (braceIdx < 0) {
        continue;
      }
      String beforeBrace = trimmed.substring(0, braceIdx).trim();

      // Drop a "left =", "right =" or "material =" prefix
      int eqIdx = beforeBrace.indexOf('=');
      if (eqIdx >= 0) {
        beforeBrace = beforeBrace.substring(eqIdx + 1).trim();
      }

      String[] tokens = beforeBrace.split("\\s+");
      if (tokens.length >= 2 && tokens[1].equals(targetName)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Returns a name based on baseName that is unused in the whole scene text (plain shapes, lights
   * and CSG children all count) and that also differs from the two reserved names.
   */
  private String resolveUniqueChildName(String baseName, String reservedA, String reservedB) {
    String full = (editor != null) ? editor.getFullText() : "";
    if (full == null) {
      full = "";
    }
    String candidate = baseName;
    int suffix = 2;
    while (suffix < 10000
        && (isObjectNameInUse(full, candidate)
            || candidate.equals(reservedA)
            || candidate.equals(reservedB))) {
      candidate = baseName + suffix;
      suffix++;
    }
    return candidate;
  }

  /** Replaces the first "name = ...;" assignment line inside template lines with resolvedName. */
  private String applyNameToTemplate(String template, String resolvedName) {
    if (template == null || template.isEmpty() || resolvedName == null || resolvedName.isEmpty()) {
      return template;
    }

    String[] lines = template.split("\n", -1);
    boolean replaced = false;
    StringBuilder sb = new StringBuilder(template.length());

    for (int i = 0; i < lines.length; i++) {
      String line = lines[i];
      String trimmed = line.trim();
      if (!replaced && trimmed.startsWith("name")) {
        int eqIdx = line.indexOf('=');
        if (eqIdx >= 0) {
          String prefix = line.substring(0, eqIdx + 1);
          String afterEq = line.substring(eqIdx + 1);
          int semiIdx = afterEq.indexOf(';');
          String suffix = (semiIdx >= 0) ? afterEq.substring(semiIdx) : ";";
          sb.append(prefix).append(" ").append(resolvedName).append(suffix);
          replaced = true;
        } else {
          sb.append(line);
        }
      } else {
        sb.append(line);
      }

      if (i < lines.length - 1) {
        sb.append('\n');
      }
    }

    return sb.toString();
  }

  /**
   * Formats a Light block according to the reference format: no leading indent on the header,
   * 4-space indent inside, no trailing ';'. Object name matches the resolved "name" field.
   */
  private String formatLightBlock(String lightType, String lightTemplate) {
    String fullClassName = lightType;
    if (!fullClassName.endsWith("Light")) {
      fullClassName = fullClassName + "Light";
    }
    String baseName = extractTemplateName(lightTemplate, lightType);
    String objectName = resolveUniqueObjectName(baseName);
    String updatedTemplate = applyNameToTemplate(lightTemplate, objectName);

    StringBuilder sb = new StringBuilder();
    sb.append(fullClassName).append(" ").append(objectName).append(" {\n");
    String[] lines = updatedTemplate.split("\n");
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
   * 4-space indent inside, no trailing ';'. Object name matches the resolved "name" field.
   */
  private String formatShapeBlock(String shapeType, String shapeBody) {
    String baseName = extractTemplateName(shapeBody, shapeType);
    String objectName = resolveUniqueObjectName(baseName);
    String updatedBody = applyNameToTemplate(shapeBody, objectName);

    StringBuilder sb = new StringBuilder();
    sb.append(shapeType).append(" ").append(objectName).append(" {\n");
    String[] lines = updatedBody.split("\n");
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
   * Inserts the given text directly below the "#Overlays" marker line. Ensures exactly one blank
   * line between the marker and the new block.
   */
  private void insertBelowOverlayMarker(String blockText) {
    String full = editor.getFullText();
    int markerIdx = full.indexOf("# Overlays");
    if (markerIdx < 0) markerIdx = full.indexOf("#Overlays");
    if (markerIdx < 0) {
      Toast.makeText(this, "# Overlays not found", Toast.LENGTH_LONG).show();
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

  private void insertAboveOverlayMarker(String blockText) {
    String full = editor.getFullText();
    int markerIdx = full.indexOf("# Overlays");
    if (markerIdx < 0) {
      markerIdx = full.indexOf("#Overlays");
    }

    if (markerIdx < 0) {
      Toast.makeText(this, "#Overlays not found", Toast.LENGTH_LONG).show();
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
  // CSG DIALOG AND BLOCK BUILDER
  // =========================================================================
  private void showCsgDialog() {
    if (isBusy) {
      return;
    }

    final String[] csgTypes = {"Union", "Intersection", "Difference"};

    // Basic shape types ("null" first, then sorted non-CSG/non-Custom shapes)
    List<String> baseShapes = new ArrayList<String>();
    for (String s : Utilities.SHAPE_TYPES) {
      if ("Custom".equals(s) || isCsgShapeName(s)) {
        continue;
      }
      baseShapes.add(s);
    }
    Collections.sort(
        baseShapes,
        new Comparator<String>() {
          @Override
          public int compare(String a, String b) {
            return a.compareToIgnoreCase(b);
          }
        });
    baseShapes.add(0, "null");
    final String[] shapeOptions = baseShapes.toArray(new String[baseShapes.size()]);

    // Material types (sorted, without "Custom")
    List<String> matList = new ArrayList<String>();
    for (String m : Utilities.MATERIAL_TYPES) {
      if ("Custom".equals(m)) {
        continue;
      }
      matList.add(m);
    }
    Collections.sort(
        matList,
        new Comparator<String>() {
          @Override
          public int compare(String a, String b) {
            return a.compareToIgnoreCase(b);
          }
        });
    final String[] materialOptions = matList.toArray(new String[matList.size()]);

    float density = getResources().getDisplayMetrics().density;
    int pad = (int) (18 * density);
    int topMargin = (int) (12 * density);

    LinearLayout root = new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setPadding(pad, pad, pad, pad);

    // CSG type (default: Union, the first entry)
    root.addView(createCsgLabel("CSG Type:", 0));
    final Spinner spType = new Spinner(this);
    spType.setAdapter(createReadableSpinnerAdapter(csgTypes));
    spType.setSelection(indexOfOption(csgTypes, "Union"));
    root.addView(spType);

    // Left shape (default: Sphere)
    root.addView(createCsgLabel("Left Shape (required):", topMargin));
    final Spinner spLeft = new Spinner(this);
    spLeft.setAdapter(createReadableSpinnerAdapter(shapeOptions));
    spLeft.setSelection(indexOfOption(shapeOptions, "Sphere"));
    root.addView(spLeft);

    // Left material (default: AfricanKente)
    root.addView(createCsgLabel("Left Material:", topMargin));
    final Spinner spLeftMat = new Spinner(this);
    spLeftMat.setAdapter(createReadableSpinnerAdapter(materialOptions));
    spLeftMat.setSelection(indexOfOption(materialOptions, "AfricanKente"));
    root.addView(spLeftMat);

    // Right shape (default: Sphere)
    root.addView(createCsgLabel("Right Shape (required):", topMargin));
    final Spinner spRight = new Spinner(this);
    spRight.setAdapter(createReadableSpinnerAdapter(shapeOptions));
    spRight.setSelection(indexOfOption(shapeOptions, "Sphere"));
    root.addView(spRight);

    // Right material (default: BrunostCheese)
    root.addView(createCsgLabel("Right Material:", topMargin));
    final Spinner spRightMat = new Spinner(this);
    spRightMat.setAdapter(createReadableSpinnerAdapter(materialOptions));
    spRightMat.setSelection(indexOfOption(materialOptions, "BrunostCheese"));
    root.addView(spRightMat);

    // The dialog is long: make it scrollable
    android.widget.ScrollView scroll = new android.widget.ScrollView(this);
    scroll.addView(root);

    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    builder.setTitle("Add CSG Object");
    builder.setView(scroll);
    builder.setCancelable(true);

    builder.setPositiveButton(
        "OK",
        new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            if (isBusy) {
              return;
            }

            String selectedType = (String) spType.getSelectedItem();
            String leftShapeType = (String) spLeft.getSelectedItem();
            String rightShapeType = (String) spRight.getSelectedItem();
            String leftMat = (String) spLeftMat.getSelectedItem();
            String rightMat = (String) spRightMat.getSelectedItem();

            if (leftShapeType == null
                || "null".equals(leftShapeType)
                || rightShapeType == null
                || "null".equals(rightShapeType)) {
              Toast.makeText(
                      RayTracerAndroid.this, "CSG requires 2 valid shapes.", Toast.LENGTH_SHORT)
                  .show();
              return;
            }
            if (selectedType == null || leftMat == null || rightMat == null) {
              return;
            }

            // No shared material: every child keeps its own material
            String csgBlock =
                buildCsgBlock(selectedType, leftShapeType, rightShapeType, leftMat, rightMat, null);
            if (csgBlock != null && !csgBlock.isEmpty()) {
              insertAboveOverlayMarker(csgBlock);
            }
          }
        });

    builder.setNegativeButton("Cancel", null);
    builder.show();
  }

  /** Returns the index of wanted inside options, or 0 if it is not found. */
  private int indexOfOption(String[] options, String wanted) {
    final int opslen = options.length;
    for (int i = 0; i < opslen; i++) {
      if (wanted.equals(options[i])) {
        return i;
      }
    }
    return 0;
  }

  private void showOverlayTextDialog() {
    if (isBusy) {
      return;
    }

    // 1. Font style options
    final String[] fontStyles = {"NORMAL", "BOLD", "ITALIC", "BOLD_ITALIC"};

    // 2. Anchor options
    final String[] anchors = {
      "TOP_LEFT", "TOP_CENTER", "TOP_RIGHT",
      "CENTER_LEFT", "CENTER", "CENTER_RIGHT",
      "BOTTOM_LEFT", "BOTTOM_CENTER", "BOTTOM_RIGHT"
    };

    // 3. Build dialog layout
    float density = getResources().getDisplayMetrics().density;
    int pad = (int) (18 * density);
    int topMargin = (int) (12 * density);

    LinearLayout root = new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setPadding(pad, pad, pad, pad);

    // Text input
    root.addView(createCsgLabel("Text:", 0));
    final EditText etText = new EditText(this);
    etText.setHint("Brunost Scene");
    etText.setText("Murat Inan");
    root.addView(etText);

    // Font face input
    root.addView(createCsgLabel("Font Face (system family or .ttf name):", topMargin));
    final EditText etFontFace = new EditText(this);
    etFontFace.setText("sans-serif");
    root.addView(etFontFace);

    // Font style spinner
    root.addView(createCsgLabel("Font Style:", topMargin));
    final Spinner spStyle = new Spinner(this);
    spStyle.setAdapter(createReadableSpinnerAdapter(fontStyles));
    root.addView(spStyle);

    // Size input (ratio of image height)
    root.addView(createCsgLabel("Size (ratio of image height, e.g. 0.04):", topMargin));
    final EditText etSize = new EditText(this);
    etSize.setText("0.04");
    etSize.setInputType(
        android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
    root.addView(etSize);

    // Color input (ARGB hex)
    root.addView(createCsgLabel("Color (ARGB hex, e.g. 0xFFFFFFFF):", topMargin));
    final EditText etColor = new EditText(this);
    etColor.setText("0xFFFFFFFF");
    root.addView(etColor);

    // Anchor spinner
    root.addView(createCsgLabel("Anchor:", topMargin));
    final Spinner spAnchor = new Spinner(this);
    spAnchor.setAdapter(createReadableSpinnerAdapter(anchors));
    spAnchor.setSelection(8); // BOTTOM_RIGHT
    root.addView(spAnchor);

    // Margin X input
    root.addView(createCsgLabel("Margin X (ratio, e.g. 0.02):", topMargin));
    final EditText etMarginX = new EditText(this);
    etMarginX.setText("0.02");
    etMarginX.setInputType(
        android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
    root.addView(etMarginX);

    // Margin Y input
    root.addView(createCsgLabel("Margin Y (ratio, e.g. 0.02):", topMargin));
    final EditText etMarginY = new EditText(this);
    etMarginY.setText("0.02");
    etMarginY.setInputType(
        android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
    root.addView(etMarginY);

    // Alpha input
    root.addView(createCsgLabel("Alpha (0..1, e.g. 1.0):", topMargin));
    final EditText etAlpha = new EditText(this);
    etAlpha.setText("1.0");
    etAlpha.setInputType(
        android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
    root.addView(etAlpha);

    // Rotate input (degrees)
    root.addView(createCsgLabel("Rotate (degrees, e.g. -30.0):", topMargin));
    final EditText etRotate = new EditText(this);
    etRotate.setText("0.0");
    etRotate.setInputType(
        android.text.InputType.TYPE_CLASS_NUMBER
            | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            | android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);
    root.addView(etRotate);

    // Wrap the vertical form in a ScrollView so every field (including alpha and rotate)
    // remains reachable on short screens. AlertDialog does not scroll its content by default.
    ScrollView scroll = new ScrollView(this);
    scroll.addView(root);

    // 4. Show AlertDialog
    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    builder.setTitle("Add Overlay Text");
    builder.setView(scroll);
    builder.setCancelable(true);

    builder.setPositiveButton(
        "OK",
        new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            if (isBusy) {
              return;
            }

            // Optional Arabic / Latin transliteration prefixes.
            String text = etText.getText().toString();   
            String fontFace = etFontFace.getText().toString().trim();
            String fontStyle = (String) spStyle.getSelectedItem();
            String anchor = (String) spAnchor.getSelectedItem();

            // Validate mandatory fields.
            if (text.length() == 0) {
              Toast.makeText(RayTracerAndroid.this, "Text is required.", Toast.LENGTH_SHORT).show();
              return;
            }
            if (fontFace.length() == 0) {
              fontFace = "sans-serif";
            }

            // Parse numeric fields; abort with a message if any value is malformed.
            float size, marginX, marginY, alpha, rotate;
            int color;
            try {
              size = Float.parseFloat(etSize.getText().toString().trim());
              marginX = Float.parseFloat(etMarginX.getText().toString().trim());
              marginY = Float.parseFloat(etMarginY.getText().toString().trim());
              alpha = Float.parseFloat(etAlpha.getText().toString().trim());
              rotate = Float.parseFloat(etRotate.getText().toString().trim());
              String colorStr = etColor.getText().toString().trim();
              if (colorStr.startsWith("0x") || colorStr.startsWith("0X")) {
                colorStr = colorStr.substring(2);
              }
              color = (int) Long.parseLong(colorStr, 16);
            } catch (NumberFormatException e) {
              Toast.makeText(RayTracerAndroid.this, "Invalid numeric value.", Toast.LENGTH_SHORT)
                  .show();
              return;
            }

            // Build the overlay block and insert it below the overlay marker in the editor.
            String block =
                buildOverlayTextBlock(
                    text, fontFace, fontStyle, size, color, anchor, marginX, marginY, alpha,
                    rotate);
            if (block != null && !block.isEmpty()) {
              insertBelowOverlayMarker(block);
            }
          }
        });

    builder.setNegativeButton("Cancel", null);
    builder.show();
  }

/**
   * Displays a configuration dialog to build an OverlayImage block (file, width, alpha, anchor,
   * margins, rotate, flipX, flipY).
   */
  private void showOverlayImageDialog() {
    if (isBusy) {
      return;
    }

    // 1. Anchor options
    final String[] anchors = {
      "TOP_LEFT", "TOP_CENTER", "TOP_RIGHT",
      "CENTER_LEFT", "CENTER", "CENTER_RIGHT",
      "BOTTOM_LEFT", "BOTTOM_CENTER", "BOTTOM_RIGHT"
    };

    // 2. Build dialog layout
    float density = getResources().getDisplayMetrics().density;
    int pad = (int) (18 * density);
    int topMargin = (int) (12 * density);

    LinearLayout root = new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setPadding(pad, pad, pad, pad);

    // File input
    root.addView(createCsgLabel("Image File (e.g. textures/turkeyFlag.png):", 0));
    final EditText etFile = new EditText(this);
    etFile.setHint("textures/logo.png");
    etFile.setText("textures/turkeyFlag.png");
    root.addView(etFile);

    // Width input (ratio)
    root.addView(
        createCsgLabel("Width (ratio of image width, e.g. 0.15; -1 = native size):", topMargin));
    final EditText etWidth = new EditText(this);
    etWidth.setText("0.15");
    etWidth.setInputType(
        android.text.InputType.TYPE_CLASS_NUMBER
            | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            | android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);
    root.addView(etWidth);

    // Alpha input
    root.addView(createCsgLabel("Alpha (0..1, e.g. 0.7):", topMargin));
    final EditText etAlpha = new EditText(this);
    etAlpha.setText("1.0");
    etAlpha.setInputType(
        android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
    root.addView(etAlpha);

    // Anchor spinner
    root.addView(createCsgLabel("Anchor:", topMargin));
    final Spinner spAnchor = new Spinner(this);
    spAnchor.setAdapter(createReadableSpinnerAdapter(anchors));
    spAnchor.setSelection(0); // TOP_LEFT
    root.addView(spAnchor);

    // Margin X input
    root.addView(createCsgLabel("Margin X (ratio, e.g. 0.02):", topMargin));
    final EditText etMarginX = new EditText(this);
    etMarginX.setText("0.02");
    etMarginX.setInputType(
        android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
    root.addView(etMarginX);

    // Margin Y input
    root.addView(createCsgLabel("Margin Y (ratio, e.g. 0.02):", topMargin));
    final EditText etMarginY = new EditText(this);
    etMarginY.setText("0.02");
    etMarginY.setInputType(
        android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
    root.addView(etMarginY);

    // Rotate input (degrees)
    root.addView(createCsgLabel("Rotate (degrees, e.g. 15.0):", topMargin));
    final EditText etRotate = new EditText(this);
    etRotate.setText("0.0");
    etRotate.setInputType(
        android.text.InputType.TYPE_CLASS_NUMBER
            | android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            | android.text.InputType.TYPE_NUMBER_FLAG_SIGNED);
    root.addView(etRotate);

    // Flip X checkbox (horizontal mirror)
    final CheckBox cbFlipX = new CheckBox(this);
    cbFlipX.setText("Flip X (horizontal mirror)");
    cbFlipX.setChecked(false);
    root.addView(cbFlipX);

    // Flip Y checkbox (vertical mirror)
    final CheckBox cbFlipY = new CheckBox(this);
    cbFlipY.setText("Flip Y (vertical mirror)");
    cbFlipY.setChecked(false);
    root.addView(cbFlipY);

    // Wrap the vertical form in a ScrollView so every field remains reachable on short screens.
    // AlertDialog does not scroll its content by default.
    ScrollView scroll = new ScrollView(this);
    scroll.addView(root);

    // 3. Show AlertDialog
    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    builder.setTitle("Add Overlay Image");
    builder.setView(scroll);
    builder.setCancelable(true);

    builder.setPositiveButton(
        "OK",
        new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialog, int which) {
            if (isBusy) {
              return;
            }

            String file = etFile.getText().toString().trim();
            String anchor = (String) spAnchor.getSelectedItem();

            if (file.length() == 0) {
              Toast.makeText(RayTracerAndroid.this, "Image file is required.", Toast.LENGTH_SHORT)
                  .show();
              return;
            }

            float width, alpha, marginX, marginY, rotate;
            try {
              width = Float.parseFloat(etWidth.getText().toString().trim());
              alpha = Float.parseFloat(etAlpha.getText().toString().trim());
              marginX = Float.parseFloat(etMarginX.getText().toString().trim());
              marginY = Float.parseFloat(etMarginY.getText().toString().trim());
              rotate = Float.parseFloat(etRotate.getText().toString().trim());
            } catch (NumberFormatException e) {
              Toast.makeText(RayTracerAndroid.this, "Invalid numeric value.", Toast.LENGTH_SHORT)
                  .show();
              return;
            }

            boolean flipX = cbFlipX.isChecked();
            boolean flipY = cbFlipY.isChecked();

            String block =
                buildOverlayImageBlock(
                    file, width, alpha, anchor, marginX, marginY, rotate, flipX, flipY);
            if (block != null && !block.isEmpty()) {
              insertBelowOverlayMarker(block);
            }
          }
        });

    builder.setNegativeButton("Cancel", null);
    builder.show();
  }

  /**
   * Builds an OverlayText block string from the given parameters. The returned block is inserted
   * above the -END- marker by the caller.
   */
  private String buildOverlayTextBlock(
      String text,
      String fontFace,
      String fontStyle,
      float size,
      int color,
      String anchor,
      float marginX,
      float marginY,
      float alpha,
      float rotate) {
    // StringBuilder sb = new StringBuilder();
    // sb.append("OverlayText overlayText {\n");
    String objectName = resolveUniqueObjectName("overlayText");
    StringBuilder sb = new StringBuilder();
    sb.append("OverlayText ").append(objectName).append(" {\n");
    sb.append("    text = \"").append(text).append("\";\n");
    sb.append("    fontFace = ").append(fontFace).append(";\n");
    sb.append("    fontStyle = ").append(fontStyle).append(";\n");
    sb.append("    size = ").append(size).append(";\n");
    sb.append("    color = 0x")
        .append(Integer.toHexString(color).toUpperCase(java.util.Locale.US))
        .append(";\n");
    sb.append("    anchor = ").append(anchor).append(";\n");
    sb.append("    marginX = ").append(marginX).append(";\n");
    sb.append("    marginY = ").append(marginY).append(";\n");
    sb.append("    alpha = ").append(alpha).append(";\n");
    sb.append("    rotate = ").append(rotate).append(";\n");
    sb.append("}\n");
    return sb.toString();
  }

  /** Builds an OverlayImage block string from the given parameters. */
  private String buildOverlayImageBlock(
      String file,
      float width,
      float alpha,
      String anchor,
      float marginX,
      float marginY,
      float rotate,
      boolean flipX,
      boolean flipY) {
    // StringBuilder sb = new StringBuilder();
    // sb.append("OverlayImage overlayImage {\n");
    String objectName = resolveUniqueObjectName("overlayImage");
    StringBuilder sb = new StringBuilder();
    sb.append("OverlayImage ").append(objectName).append(" {\n");
    sb.append("    file = \"").append(file).append("\";\n");
    sb.append("    width = ").append(width).append(";\n");
    sb.append("    alpha = ").append(alpha).append(";\n");
    sb.append("    anchor = ").append(anchor).append(";\n");
    sb.append("    marginX = ").append(marginX).append(";\n");
    sb.append("    marginY = ").append(marginY).append(";\n");
    sb.append("    rotate = ").append(rotate).append(";\n");
    sb.append("    flipX = ").append(flipX).append(";\n");
    sb.append("    flipY = ").append(flipY).append(";\n");
    sb.append("}\n");
    return sb.toString();
  }

  /** Creates a clearly visible, bold label for the CSG dialog fields. */
  private TextView createCsgLabel(String text, int topMarginPx) {
    TextView tv = new TextView(this);
    tv.setText(text);
    tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
    tv.setTypeface(null, Typeface.BOLD);
    tv.setTextColor(Color.BLACK);
    if (topMarginPx > 0) {
      LinearLayout.LayoutParams lp =
          new LinearLayout.LayoutParams(
              LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
      lp.topMargin = topMarginPx;
      tv.setLayoutParams(lp);
    }
    return tv;
  }

  /**
   * Creates a high-contrast, large-font ArrayAdapter for Spinners so items never appear blank
   * regardless of the system light/dark theme.
   */
  private ArrayAdapter<String> createReadableSpinnerAdapter(final String[] items) {
    final float density = getResources().getDisplayMetrics().density;
    final int hPad = (int) (12 * density);
    final int vPad = (int) (14 * density);

    return new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, items) {
      @Override
      public View getView(int position, View convertView, android.view.ViewGroup parent) {
        TextView tv = (TextView) super.getView(position, convertView, parent);
        tv.setText(items[position]);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        tv.setTextColor(Color.BLACK);
        tv.setBackgroundColor(0xFFF2F2F2);
        tv.setPadding(hPad, vPad, hPad, vPad);
        return tv;
      }

      @Override
      public View getDropDownView(int position, View convertView, android.view.ViewGroup parent) {
        TextView tv = (TextView) super.getDropDownView(position, convertView, parent);
        tv.setText(items[position]);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        tv.setTextColor(Color.BLACK);
        tv.setBackgroundColor(Color.WHITE);
        tv.setPadding(hPad, vPad, hPad, vPad);
        return tv;
      }
    };
  }

  /**
   * Builds the CSG scene block. Each child gets its own material block. A CSG-level material line
   * is written only when sharedMaterialType is not null (old behavior).
   */
  private String buildCsgBlock(
      String csgOpType,
      String leftShapeType,
      String rightShapeType,
      String leftMaterialType,
      String rightMaterialType,
      String sharedMaterialType) {
    String leftRawTemplate = Utilities.getShapeTemplate(leftShapeType);
    String rightRawTemplate = Utilities.getShapeTemplate(rightShapeType);

    if (leftRawTemplate == null
        || leftRawTemplate.isEmpty()
        || rightRawTemplate == null
        || rightRawTemplate.isEmpty()) {
      Toast.makeText(this, "Shape template not available.", Toast.LENGTH_SHORT).show();
      return "";
    }

    String csgClassName = csgOpType + "CSG";
    String csgLowerName = resolveUniqueObjectName(toLowerFirstChar(csgClassName));

    // Child names are unique in the whole scene (inside and outside CSG blocks) and differ
    // from the CSG name and from each other: sphere, sphere2, sphere3, ...
    String leftChildName =
        resolveUniqueChildName(
            extractTemplateName(leftRawTemplate, leftShapeType), csgLowerName, null);
    String rightChildName =
        resolveUniqueChildName(
            extractTemplateName(rightRawTemplate, rightShapeType), csgLowerName, leftChildName);

    List<String> leftCoreLines = extractCoreShapeLines(leftRawTemplate);
    List<String> rightCoreLines = extractCoreShapeLines(rightRawTemplate);

    StringBuilder sb = new StringBuilder();
    sb.append(csgClassName).append(" ").append(csgLowerName).append(" {\n");
    sb.append("    name = ").append(csgLowerName).append(";\n");

    appendCsgChild(sb, "left", leftShapeType, leftChildName, leftCoreLines, leftMaterialType);
    appendCsgChild(sb, "right", rightShapeType, rightChildName, rightCoreLines, rightMaterialType);
    sb.append("\n");

    // CSG-level transform, animation and shared visibility/shadow properties
    sb.append("    transform = translate(0, 0, 0) * rotate(0, 0, 0) * scale(1, 1, 1);\n");
    sb.append("    firstAnimMatrix = translate(0.000, 0.000, 0.000) * rotate(0.000, 0.000, 0.000)")
        .append(" * scale(1.000, 1.000, 1.000);\n");
    sb.append("    secondAnimMatrix = translate(0.000, 0.000, 0.000) * rotate(0.000, 0.000, 0.000)")
        .append(" * scale(1.000, 1.000, 1.000);\n");
    sb.append("    otherAnimationInfo = ve0fr,ve0fr;\n\n");

    sb.append("    shadowColor = 0xFF000000;\n");
    sb.append("    shadowBias = 0.001;\n");
    sb.append("    isVisibleSpecial = true;\n");
    sb.append("    isVisible = true;\n");
    sb.append("    isShadowEnable = true;\n");
    sb.append("    isShadowOnly = false;\n");
    sb.append("    isReflective = true;\n");
    sb.append("    isRefractive = true;\n");

    // Optional CSG-level material: overrides the children's own materials
    if (sharedMaterialType != null) {
      String matRawTemplate = Utilities.getMaterialTemplate(sharedMaterialType);
      sb.append("\n");

      String sharedMatName = resolveUniqueObjectName(toLowerFirstChar(sharedMaterialType));
      sb.append("    material = ")
          .append(sharedMaterialType)
          .append("Material ")
          .append(sharedMatName)
          .append("material {\n");

      if (matRawTemplate != null && !matRawTemplate.isEmpty()) {
        String[] matLines = matRawTemplate.split("\n");
        for (int i = 0; i < matLines.length; i++) {
          String mLine = matLines[i].trim();
          if (mLine.isEmpty()) {
            continue;
          }
          sb.append("        ").append(mLine).append("\n");
        }
      }
      sb.append("    };\n");
    }

    sb.append("}");
    return sb.toString();
  }

  /**
   * Filters a shape template so only its core geometry parameters and animation lines remain,
   * stripping shadow/visibility properties that belong to the parent CSG block and any embedded
   * default material block (children get their material from the dialog).
   */
  private List<String> extractCoreShapeLines(String shapeTemplate) {
    List<String> result = new ArrayList<String>();
    String[] lines = shapeTemplate.split("\n");
    boolean skipping = false;
    int skipDepth = 0;

    for (int i = 0; i < lines.length; i++) {
      String line = lines[i].trim();
      if (line.isEmpty()) {
        continue;
      }

      if (skipping) {
        skipDepth += countChar(line, '{') - countChar(line, '}');
        if (skipDepth <= 0) {
          skipping = false;
        }
        continue;
      }

      if (line.startsWith("material")) {
        skipDepth = countChar(line, '{') - countChar(line, '}');
        skipping = skipDepth > 0;
        continue;
      }

      if (line.startsWith("shadowColor")
          || line.startsWith("shadowBias")
          || line.startsWith("isVisibleSpecial")
          || line.startsWith("isVisible")
          || line.startsWith("isShadowEnable")
          || line.startsWith("isShadowOnly")
          || line.startsWith("isReflective")
          || line.startsWith("isRefractive")) {
        continue;
      }

      if (line.startsWith("firstAnim")
          || line.startsWith("secondAnim")
          || line.startsWith("otherAnimationInfo")) {
        continue;
      }

      result.add(line);
    }
    return result;
  }

  /** Appends one child block ("left = ..." or "right = ...") including its own material. */
  private void appendCsgChild(
      StringBuilder sb,
      String side,
      String shapeType,
      String childName,
      List<String> coreLines,
      String materialType) {
    sb.append("    ")
        .append(side)
        .append(" = ")
        .append(shapeType)
        .append(" ")
        .append(childName)
        .append(" {\n");

    // The "name" field always equals the header name
    sb.append("            name = ").append(childName).append(";\n");

    // The template's own "name" and "transform" lines are dropped: a CSG child gets exactly one
    // of each, written here. Every other core line is kept as is.
    for (int i = 0; i < coreLines.size(); i++) {
      String line = coreLines.get(i);
      if (isKeyLine(line, "name") || isKeyLine(line, "transform")) {
        continue;
      }
      sb.append("            ").append(line).append("\n");
    }

    // Left child starts at x = -1, right child at x = +1, so the two shapes overlap
    // but are visibly separate. Edit this line to place the child inside the CSG.
    String startX = "left".equals(side) ? "-1.000" : "1.000";
    sb.append("            transform = translate(")
        .append(startX)
        .append(", 0.000, 0.000) * rotate(0.000, 0.000, 0.000) * scale(1.000, 1.000, 1.000);\n");

    if (materialType != null) {
      String matTemplate = Utilities.getMaterialTemplate(materialType);
      sb.append("            material = ")
          .append(materialType)
          .append("Material ")
          .append(toLowerFirstChar(materialType))
          .append("material {\n");
      if (matTemplate != null && !matTemplate.isEmpty()) {
        String[] matLines = matTemplate.split("\n");
        for (int i = 0; i < matLines.length; i++) {
          String mLine = matLines[i].trim();
          if (mLine.isEmpty()) {
            continue;
          }
          sb.append("                ").append(mLine).append("\n");
        }
      }
      sb.append("            };\n");
    }

    sb.append("            };\n");
  }

  /** True if the line is an assignment of exactly the given key ("key = value;"). */
  private boolean isKeyLine(String line, String key) {
    int eq = line.indexOf('=');
    return eq > 0 && line.substring(0, eq).trim().equals(key);
  }

  private int countChar(String s, char c) {
    int n = 0;
    for (int i = 0; i < s.length(); i++) {
      if (s.charAt(i) == c) {
        n++;
      }
    }
    return n;
  }

  //////////////

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
    @SuppressWarnings("deprecation")
    float currentSizeSp = editor.getTextSize() / getResources().getDisplayMetrics().scaledDensity;
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

            } catch (final Throwable e) {
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

            } catch (final Throwable e) {
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

  /** Holds the mutable muxing state shared between generateMovie() and drainEncoder(). */
  private static class MovieEncoderState {
    MediaMuxer muxer;
    int trackIndex = -1;
    boolean muxerStarted = false;
    long presentationTimeUs = 0;
    long frameDurationUs = 1000000L;
    int writtenSamples = 0;
  }

  /**
   * Drains all available output from the encoder into the muxer.
   *
   * <p>The muxer is started ONLY when INFO_OUTPUT_FORMAT_CHANGED arrives, which is the only
   * reliable moment to get the final output format. When endOfStream is true, this method blocks
   * until the END_OF_STREAM flag is seen (or throws after about 5 seconds of silence).
   */
  private void drainEncoder(MediaCodec codec, MovieEncoderState state, boolean endOfStream) {
    MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
    int idleCount = 0;

    while (true) {
      int index = codec.dequeueOutputBuffer(info, 10000);

      if (index == MediaCodec.INFO_TRY_AGAIN_LATER) {
        // No output available right now.
        if (!endOfStream) {
          return;
        }
        idleCount++;
        if (idleCount > 500) { // 500 * 10 ms = about 5 seconds
          throw new IllegalStateException("Encoder timeout while waiting for end of stream.");
        }

      } else if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
        // The real output format is known now. Add the track and start the muxer.
        if (state.muxerStarted) {
          throw new IllegalStateException("Encoder output format changed twice.");
        }
        state.trackIndex = state.muxer.addTrack(codec.getOutputFormat());
        state.muxer.start();
        state.muxerStarted = true;

      } else if (index >= 0) {
        idleCount = 0;
        ByteBuffer outputBuffer = codec.getOutputBuffer(index);

        // Codec config data is already embedded in the track format, so do not write it.
        if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
          info.size = 0;
        }

        if (info.size > 0 && outputBuffer != null) {
          if (!state.muxerStarted) {
            throw new IllegalStateException("Muxer has not been started yet.");
          }
          outputBuffer.position(info.offset);
          outputBuffer.limit(info.offset + info.size);
          info.presentationTimeUs = state.presentationTimeUs;
          state.muxer.writeSampleData(state.trackIndex, outputBuffer, info);
          state.presentationTimeUs += state.frameDurationUs;
          state.writtenSamples++;
        }

        boolean endReached = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
        codec.releaseOutputBuffer(index, false);
        if (endReached) {
          return;
        }
      }
      // Any other negative value (e.g. deprecated INFO_OUTPUT_BUFFERS_CHANGED) is ignored.
    }
  }

  /** Draws the bitmap centered and scaled to fit (letterboxed) onto the encoder input surface. */
  private void drawFrameToSurface(Surface surface, Bitmap bitmap, int videoWidth, int videoHeight) {
    Canvas canvas = surface.lockCanvas(null);
    try {
      canvas.drawColor(Color.BLACK);

      float scale =
          Math.min(
              (float) videoWidth / bitmap.getWidth(), (float) videoHeight / bitmap.getHeight());
      int newW = (int) (bitmap.getWidth() * scale);
      int newH = (int) (bitmap.getHeight() * scale);
      int left = (videoWidth - newW) / 2;
      int top = (videoHeight - newH) / 2;

      Rect dstRect = new Rect(left, top, left + newW, top + newH);
      canvas.drawBitmap(bitmap, null, dstRect, new Paint());
    } finally {
      surface.unlockCanvasAndPost(canvas);
    }
  }

  /**
   * Creates an MP4 video from a list of JPG image URIs using MediaCodec + MediaMuxer.
   *
   * <p>Playback speed is controlled only by presentationTimeUs (1_000_000 / fps per frame).
   * KEY_FRAME_RATE is pinned to 30 because it is just a hint and must be an integer.
   *
   * <p>The muxer is started when the encoder reports INFO_OUTPUT_FORMAT_CHANGED, and the file is
   * finalized (muxer.stop) BEFORE the success toast is shown. If anything fails, the incomplete
   * output file is deleted so no zero-byte MP4 is left behind.
   */
  private void generateMovie(final List<Uri> imageUris) {
    showLoading(true);

    // Capture the FPS now so later UI changes cannot affect this running job.
    final float fps = currentMovieFps;

    backgroundExecutor.execute(
        new Runnable() {
          @Override
          public void run() {
            MediaCodec codec = null;
            Surface inputSurface = null;
            MovieEncoderState state = new MovieEncoderState();
            File outputFile = null;
            Bitmap lastBitmap = null;
            boolean success = false;

            try {
              // 1. Create output file
              if (MOVIEDIR != null && !MOVIEDIR.exists()) MOVIEDIR.mkdirs();
              SimpleDateFormat sdf = new SimpleDateFormat("yyMMdd_HHmmss", Locale.US);
              String videoFileName = "rt_" + sdf.format(new Date()) + ".mp4";
              outputFile = new File(MOVIEDIR, videoFileName);
              final String outputPath = outputFile.getAbsolutePath();

              // 2. Decide video size: follow max render size, clamp to 800x600
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
              // H.264 encoders require even dimensions.
              videoWidth = videoWidth & ~1;
              videoHeight = videoHeight & ~1;
              if (videoWidth < 2) videoWidth = 2;
              if (videoHeight < 2) videoHeight = 2;

              // 3. Configure the encoder
              MediaFormat format =
                  MediaFormat.createVideoFormat("video/avc", videoWidth, videoHeight);
              format.setInteger(
                  MediaFormat.KEY_COLOR_FORMAT,
                  MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
              format.setInteger(MediaFormat.KEY_BIT_RATE, 2000000); // 2 Mbps
              format.setInteger(MediaFormat.KEY_FRAME_RATE, 30);
              format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);

              codec = MediaCodec.createEncoderByType("video/avc");
              codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
              inputSurface = codec.createInputSurface();
              codec.start();

              // 4. Create the muxer (it is started later, on INFO_OUTPUT_FORMAT_CHANGED)
              state.muxer = new MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
              state.frameDurationUs = (long) (1000000L / fps);
              state.presentationTimeUs = 0;

              // 5. Render each image to the surface and drain the encoder
              for (int i = 0; i < imageUris.size(); i++) {
                Uri uri = imageUris.get(i);

                InputStream is = getContentResolver().openInputStream(uri);
                if (is == null) continue;
                Bitmap frameBitmap;
                try {
                  frameBitmap = BitmapFactory.decodeStream(is);
                } finally {
                  is.close();
                }
                if (frameBitmap == null) continue;

                drawFrameToSurface(inputSurface, frameBitmap, videoWidth, videoHeight);

                // Keep only the most recent bitmap (needed for the final duplicate frame).
                if (lastBitmap != null) {
                  lastBitmap.recycle();
                }
                lastBitmap = frameBitmap;

                drainEncoder(codec, state, false);
              }

              if (lastBitmap == null) {
                throw new IllegalStateException("No valid image could be decoded.");
              }

              // 6. Draw the last frame once more so it stays visible for a full frame duration
              drawFrameToSurface(inputSurface, lastBitmap, videoWidth, videoHeight);
              drainEncoder(codec, state, false);

              // 7. Signal end of stream and wait until the encoder is fully drained
              codec.signalEndOfInputStream();
              drainEncoder(codec, state, true);

              if (!state.muxerStarted || state.writtenSamples == 0) {
                throw new IllegalStateException("Encoder produced no output.");
              }

              // 8. Finalize the file BEFORE telling the user it is saved
              codec.stop();
              codec.release();
              codec = null;

              inputSurface.release();
              inputSurface = null;

              state.muxer.stop();
              state.muxer.release();
              state.muxer = null;

              success = true;

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

            } catch (final Throwable e) {
              Log.e(TAG, "Movie creation failed", e);
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
              // Safety cleanup: on the success path these are already null / released.
              if (lastBitmap != null) {
                lastBitmap.recycle();
              }
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
              if (state.muxer != null) {
                if (state.muxerStarted) {
                  try {
                    state.muxer.stop();
                  } catch (Exception ignored) {
                  }
                }
                try {
                  state.muxer.release();
                } catch (Exception ignored) {
                }
              }
              // Never leave a broken / zero-byte MP4 behind.
              if (!success && outputFile != null && outputFile.exists()) {
                outputFile.delete();
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
              java.io.OutputStream os = getContentResolver().openOutputStream(uri, "wt");
              if (os == null) throw new Exception("Cannot open output stream");
              try {
                os.write(content.getBytes("UTF-8"));
                os.flush();
              } finally {
                os.close();
              }

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
    // Update immediately so synchronous UI actions are blocked without post() delay
    this.isBusy = show;

    mainHandler.post(
        new Runnable() {
          @Override
          public void run() {
            RayTracerAndroid.this.isBusy = show;
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

            // Refresh options menu state if needed
            invalidateOptionsMenu();
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
  }
}
