import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Adds a "# Overlays" section header to scene files that do not have one yet.
 *
 * Usage: java AddOverlays <inputDir> <outputDir>
 *
 * Rules:
 * - Every *.txt file in inputDir is read as UTF-8.
 * - If the file already contains "# Overlays" (or "#Overlays"), its content is left untouched
 *   and copied to outputDir as-is.
 * - Otherwise "# Overlays" is inserted directly above the first "-END-" line, with one blank
 *   line before and after the marker.
 * - If a file has no "-END-" line, it is copied unchanged and a warning is printed.
 * - Original line endings (\n or \r\n) and a UTF-8 BOM, if any, are preserved.
 */
public class AddOverlays {

  private static final Charset UTF8 = Charset.forName("UTF-8");
  private static final Pattern END_LINE = Pattern.compile("(?m)^[ \\t]*-END-[ \\t]*\\r?$");

  public static void main(String[] args) {
    if (args.length != 2) {
      System.err.println("Usage: java AddOverlays <inputDir> <outputDir>");
      System.exit(1);
    }

    File inDir = new File(args[0]);
    File outDir = new File(args[1]);

    if (!inDir.isDirectory()) {
      System.err.println("Input directory not found: " + inDir.getAbsolutePath());
      System.exit(1);
    }

    try {
      if (inDir.getCanonicalFile().equals(outDir.getCanonicalFile())) {
        System.err.println("Input and output directories must be different.");
        System.exit(1);
      }
    } catch (IOException e) {
      System.err.println("Cannot resolve directories: " + e.getMessage());
      System.exit(1);
    }

    if (!outDir.exists() && !outDir.mkdirs()) {
      System.err.println("Cannot create output directory: " + outDir.getAbsolutePath());
      System.exit(1);
    }

    File[] files =
        inDir.listFiles(
            new FilenameFilter() {
              @Override
              public boolean accept(File dir, String name) {
                return name.toLowerCase(java.util.Locale.US).endsWith(".txt")
                    && new File(dir, name).isFile();
              }
            });

    if (files == null || files.length == 0) {
      System.out.println("No .txt files found in " + inDir.getAbsolutePath());
      return;
    }

    Arrays.sort(files);

    int added = 0;
    int skipped = 0;
    int warned = 0;
    int failed = 0;

    for (int i = 0; i < files.length; i++) {
      File f = files[i];
      try {
        String text = new String(Files.readAllBytes(f.toPath()), UTF8);
        String result;

        if (hasOverlayMarker(text)) {
          result = text;
          skipped++;
          System.out.println("SKIP (already has # Overlays): " + f.getName());
        } else {
          String patched = insertOverlayMarker(text);
          if (patched == null) {
            result = text;
            warned++;
            System.out.println("WARN (no -END- line, copied unchanged): " + f.getName());
          } else {
            result = patched;
            added++;
            System.out.println("ADDED: " + f.getName());
          }
        }

        Files.write(new File(outDir, f.getName()).toPath(), result.getBytes(UTF8));

      } catch (IOException e) {
        failed++;
        System.err.println("FAILED: " + f.getName() + " -> " + e.getMessage());
      }
    }

    System.out.println();
    System.out.println(
        "Done. added=" + added + ", skipped=" + skipped + ", warnings=" + warned
            + ", failed=" + failed);
  }

  /** Same markers that RayTracerAndroid.insertBelowOverlayMarker() accepts. */
  private static boolean hasOverlayMarker(String text) {
    return text.contains("# Overlays") || text.contains("#Overlays");
  }

  /**
   * Inserts "# Overlays" above the first "-END-" line.
   *
   * @return the patched text, or null if no "-END-" line exists.
   */
  private static String insertOverlayMarker(String text) {
    Matcher m = END_LINE.matcher(text);
    if (!m.find()) {
      return null;
    }

    String nl = text.contains("\r\n") ? "\r\n" : "\n";

    int idx = m.start();
    String before = text.substring(0, idx);
    String after = text.substring(idx);

    // Drop trailing blank space so the spacing is exactly one blank line.
    int end = before.length();
    while (end > 0) {
      char c = before.charAt(end - 1);
      if (c == '\n' || c == '\r' || c == ' ' || c == '\t') {
        end--;
      } else {
        break;
      }
    }
    before = before.substring(0, end);

    StringBuilder sb = new StringBuilder(text.length() + 32);
    sb.append(before);
    if (before.length() > 0) {
      sb.append(nl).append(nl);
    }
    sb.append("# Overlays").append(nl).append(nl);
    sb.append(after);
    return sb.toString();
  }
  
}
