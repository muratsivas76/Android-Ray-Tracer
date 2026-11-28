package net.murat.gui;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Utility class for generating and managing application directories. Ensures all required folders
 * exist before the application starts writing data.
 */
public class GenerateFolders {

  private static final String TAG = "GenerateFolders";

  /** Private constructor to prevent instantiation. */
  private GenerateFolders() {
    // Utility class, no instances allowed
  }

  /**
   * Creates necessary folders for the application. Uses mkdirs() to ensure parent directories are
   * also created if they don't exist.
   *
   * @param MAINPATH The base path where the folders should be created.
   */
  public static void generateFolders(String MAINPATH) {
    File f = new File(MAINPATH + "/carets");
    if (!f.exists()) f.mkdirs();

    f = new File(MAINPATH + "/carets/raytracer");
    if (!f.exists()) f.mkdirs();

    f = new File(MAINPATH + "/carets/raytracer/textures");
    if (!f.exists()) f.mkdirs();

    f = new File(MAINPATH + "/carets/raytracer/scenes");
    if (!f.exists()) f.mkdirs();

    f = new File(MAINPATH + "/carets/raytracer/images");
    if (!f.exists()) f.mkdirs();

    f = new File(MAINPATH + "/carets/raytracer/info");
    if (!f.exists()) f.mkdirs();

    // New folder for video outputs
    f = new File(MAINPATH + "/carets/raytracer/movies");
    if (!f.exists()) f.mkdirs();

    // New folder for custom fonts (e.g., for WordMaterial)
    f = new File(MAINPATH + "/carets/raytracer/ttf");
    if (!f.exists()) f.mkdirs();
  }

  /**
   * Copies the default asset files to the application's working directory if they do not already
   * exist. This ensures the user has the necessary templates and settings files on first launch.
   *
   * @param context The application context used to access the AssetsManager.
   * @param MAINPATH The base application path (e.g., Download/carets/raytracer).
   */
  public static void copyAssetsToDownload(Context context, String MAINPATH) {
    AssetManager assetManager = context.getAssets();

    // --- info/ ---
    // 1. Application settings
    copySingleAsset(assetManager, "settings.txt", MAINPATH + "/info/settings.txt");
    // 2. Full reference documentation
    copySingleAsset(assetManager, "reference.txt", MAINPATH + "/info/reference.txt");
    // 3. Arabic Latin chars guide
    copySingleAsset(assetManager, "ar_lt_charGuide.txt", MAINPATH + "/info/ar_lt_charGuide.txt");

    // --- scenes/ ---
    // 4. Sample scene (sphere + checkerboard floor)
    copySingleAsset(assetManager, "sample.txt", MAINPATH + "/scenes/sample.txt");
    // 5. Empty new-scene template used by the "N: New Scene" action
    copySingleAsset(assetManager, "aaTemplate.txt", MAINPATH + "/scenes/aaTemplate.txt");
    // 6. Quick-start scene (plane + checkerboard + 2 lights)
    copySingleAsset(assetManager, "template.txt", MAINPATH + "/scenes/template.txt");
    // 7.
    copySingleAsset(assetManager, "overlays.txt", MAINPATH + "/scenes/overlays.txt");

    // --- scenes/ (CSG examples) ---
    // 8. Union CSG example
    copySingleAsset(assetManager, "csgUnionExample.txt", MAINPATH + "/scenes/csgUnionExample.txt");
    // 9. Difference CSG example
    copySingleAsset(
        assetManager, "csgDifferenceExample.txt", MAINPATH + "/scenes/csgDifferenceExample.txt");
    // 10. Intersection CSG example
    copySingleAsset(
        assetManager,
        "csgIntersectionExample.txt",
        MAINPATH + "/scenes/csgIntersectionExample.txt");
  }

  /**
   * Helper method to copy a single asset file to a specific absolute path.
   *
   * @param assetManager The AssetManager instance.
   * @param assetName The name of the file inside the assets folder.
   * @param destPath The absolute destination path.
   */
  private static void copySingleAsset(
      AssetManager assetManager, String assetName, String destPath) {
    File destFile = new File(destPath);

    // Do nothing if the file already exists (Do not overwrite user changes)
    if (destFile.exists()) {
      Log.d(TAG, "Asset already exists, skipping: " + destPath);
      return;
    }

    InputStream in = null;
    OutputStream out = null;

    try {
      in = assetManager.open(assetName);

      // Ensure the parent directory exists just in case
      File parentDir = destFile.getParentFile();
      if (parentDir != null && !parentDir.exists()) {
        parentDir.mkdirs();
      }

      out = new FileOutputStream(destFile);
      byte[] buffer = new byte[4096];
      int read;
      while ((read = in.read(buffer)) != -1) {
        out.write(buffer, 0, read);
      }
      out.flush();
      Log.d(TAG, "Asset copied successfully: " + destPath);

    } catch (IOException e) {
      Log.e(TAG, "Failed to copy asset: " + assetName, e);
    } finally {
      // Close streams silently
      if (in != null) {
        try {
          in.close();
        } catch (IOException ignored) {
        }
      }
      if (out != null) {
        try {
          out.close();
        } catch (IOException ignored) {
        }
      }
    }
  }
}
