package net.murat.tool;

/**
 * Platform-independent text conversion utilities for producing Latin-script
 * special characters (Turkish, Norwegian, Spanish and common accented forms)
 * from plain ASCII shorthand patterns.
 *
 * <p>The shorthand works by repeating a base letter: for example {@code "ss"}
 * becomes {@code "\u015F"} and {@code "aaa"} becomes {@code "\u00E1"}. The
 * class has no dependency on any UI toolkit, so it can be used from Swing,
 * JavaFX, Android, web back ends or command-line tools alike.</p>
 *
 * <p>All methods are stateless and thread-safe.</p>
 *
 * @author Murat iNAN, muratsivas76@gmail.com
 */
public class LatinTool extends Object implements java.io.Serializable {
  
  private static final long serialVersionUID = 1L;
  
  /** Creates a new instance. All functionality is exposed through static methods. */
  public LatinTool () {}

  /**
   * Converts ASCII shorthand patterns in the given text into their Latin
   * special-character equivalents.
   *
   * <p>Replacements are applied in a fixed order, from the longest patterns to
   * the shortest, so that a longer pattern is never partially consumed by a
   * shorter one (for example {@code "AAAAA"} is handled before {@code "AAAA"},
   * {@code "AAA"} and so on). Text that matches no pattern is left unchanged.</p>
   *
   * <p>Supported groups:</p>
   * <ul>
   *   <li>5 characters: Norwegian A-ring ({@code AAAAA}, {@code aaaaa})</li>
   *   <li>4 characters: circumflex vowels ({@code AAAA}, {@code IIII},
   *       {@code UUUU} and lowercase forms)</li>
   *   <li>3 characters: acute accents, tilde N, inverted punctuation and the
   *       bracket keywords {@code KAVISLIAC}/{@code KAVISLIKAPA}</li>
   *   <li>4 characters: Norwegian AE and O-slash ({@code AAEE}, {@code OOEE}
   *       and lowercase forms)</li>
   *   <li>Bracket keywords {@code KOSELIAC}/{@code KOSELIKAPA} and the
   *       double quote pair {@code ""} (converted to an apostrophe)</li>
   *   <li>2 characters: Turkish letters ({@code CC}, {@code GG}, {@code II},
   *       {@code OO}, {@code SS}, {@code UU} and lowercase forms)</li>
   * </ul>
   *
   * @param text the text containing shorthand patterns; may be {@code null}
   * @return the converted text, or an empty string if {@code text} is
   *         {@code null} or empty
   */
  public static final String toLatin(String text) {
    if (text == null || text.isEmpty()) {
      return "";
    }

    String t = text;

    // Norwegian (5-char patterns)
    t = t.replace("AAAAA", "\u00C5");  // A with ring above
    t = t.replace("aaaaa", "\u00E5");  // a with ring above

    // Circumflex vowels (4-char patterns)
    t = t.replace("AAAA", "\u00C2");
    t = t.replace("IIII", "\u00CE");
    t = t.replace("UUUU", "\u00DB");
    t = t.replace("aaaa", "\u00E2");
    t = t.replace("iiii", "\u00EE");
    t = t.replace("uuuu", "\u00FB");

    // Acute accents, tilde and inverted punctuation (3-char patterns)
    t = t.replace("AAA", "\u00C1");
    t = t.replace("EEE", "\u00C9");
    t = t.replace("III", "\u00CD");
    t = t.replace("OOO", "\u00D3");
    t = t.replace("UUU", "\u00DA");
    t = t.replace("NNN", "\u00D1");
    t = t.replace("???", "\u00BF");
    t = t.replace("!!!", "\u00A1");
    t = t.replace("aaa", "\u00E1");
    t = t.replace("eee", "\u00E9");
    t = t.replace("iii", "\u00ED");
    t = t.replace("ooo", "\u00F3");
    t = t.replace("uuu", "\u00FA");
    t = t.replace("nnn", "\u00F1");

    // Curly brace keywords
    t = t.replace("KAVISLIAC", "{");
    t = t.replace("KAVISLIKAPA", "}");

    // Norwegian special combinations (4-char patterns)
    t = t.replace("AAEE", "\u00C6");   // AE ligature
    t = t.replace("aaee", "\u00E6");
    t = t.replace("OOEE", "\u00D8");   // O with stroke
    t = t.replace("ooee", "\u00F8");

    // Apostrophe and square bracket keywords
    t = t.replace("\"\"", "'");
    t = t.replace("KOSELIAC", "[");
    t = t.replace("KOSELIKAPA", "]");

    // Turkish (2-char patterns)
    t = t.replace("CC", "\u00C7");
    t = t.replace("GG", "\u011E");
    t = t.replace("II", "\u0130");
    t = t.replace("OO", "\u00D6");
    t = t.replace("SS", "\u015E");
    t = t.replace("UU", "\u00DC");
    t = t.replace("cc", "\u00E7");
    t = t.replace("gg", "\u011F");
    t = t.replace("ii", "\u0131");
    t = t.replace("oo", "\u00F6");
    t = t.replace("ss", "\u015F");
    t = t.replace("uu", "\u00FC");

    return t;
  }
  
  /**
   * Replaces every "[U+XXXX]" (and "[U+XXXXX]") token in the input with the corresponding Unicode
   * code point. Hex digits are case-insensitive.
   *
   * <p>Examples: "[U+1F34B]cu" -> "🍋cu" "selam [U+1F600]!" -> "selam 😀!"
   *
   * <p>Malformed tokens (no closing ']', non-hex digits, out-of-range code points) are left
   * untouched so the caller can see what went wrong.
   *
   * @param s input string, may be null
   * @return string with all valid [U+...] tokens converted to real characters
   */
  public static final String emojied(String s) {
    if (s == null || s.isEmpty()) {
      return s;
    }

    StringBuilder out = new StringBuilder(s.length());
    int i = 0;
    int n = s.length();

    while (i < n) {
      // Look for the "[U+" prefix.
      if (
         (s.charAt(i) == '[') && 
         ((i + 2) < n) &&
         ((s.charAt(i + 1) == 'U') || (s.charAt(i + 1) == 'u')) &&
         (s.charAt(i + 2) == '+') ) {
        int close = s.indexOf(']', i + 3);
        if (close > i + 3) {
          String hex = s.substring(i + 3, close);
          if (isHex(hex)) {
            try {
              int codePoint = Integer.parseInt(hex, 16);
              if (Character.isValidCodePoint(codePoint)) {
                out.appendCodePoint(codePoint);
                i = close + 1;
                continue;
              }
            } catch (NumberFormatException ignored) {
              // Fall through: leave the token as-is.
            }
          }
        }
      }
      out.append(s.charAt(i));
      i++;
    }

    return out.toString();
  }
  
  public static final String stripEdgeQuotes(String val) {
    if (val.isEmpty()) return val;

    // İlk karakter tek ya da çift tırnak ise sil
    char first = val.charAt(0);
    if (first == '"' || first == '\'') {
        val = val.substring(1);
    }

    // Son karakter tek ya da çift tırnak ise sil
    if (!val.isEmpty()) {
        char last = val.charAt(val.length() - 1);
        if (last == '"' || last == '\'') {
            val = val.substring(0, val.length() - 1);
        }
    }

    return val;
  }
  
  /**
   * Case-insensitive prefix check.
   *
   * @param s the text to test; may be {@code null}
   * @param prefix the prefix to look for
   * @return {@code true} if {@code s} starts with {@code prefix}, ignoring case
   */
  public static final boolean startsWithIgnoreCase(String s, String prefix) {
    return s != null && prefix != null && s.regionMatches(true, 0, prefix, 0, prefix.length());
  }

  /**
   * Strips N quote characters from the start and up to N from the end, where N is the number of
   * leading quote characters. Quotes in the middle, or a lone trailing quote, are left alone.
   *
   * @param s the text to strip; may be {@code null}
   * @return the text without the symmetric edge quotes
   */
  public static final String stripSymmetricQuotes(String s) {
    if (s == null || s.isEmpty()) {
      return s;
    }
    int n = 0;
    while (n < s.length() && (s.charAt(n) == '"' || s.charAt(n) == '\'')) {
      n++;
    }
    if (n == 0) {
      return s;
    }
    String rest = s.substring(n);
    int end = rest.length();
    int removed = 0;
    while (removed < n
        && end > 0
        && (rest.charAt(end - 1) == '"' || rest.charAt(end - 1) == '\'')) {
      end--;
      removed++;
    }
    return rest.substring(0, end);
  }
  
  /**
   * Splits a string into leading quotes, core text and trailing quotes. Up to as many trailing
   * quotes as there are leading quotes are separated, so a lone trailing quote stays in the core.
   *
   * @param s the text to split; may be {@code null}
   * @return a three-element array: {leadingQuotes, core, trailingQuotes}
   */
  public static final String[] splitQuoted(String s) {
    if (s == null) {
      return new String[] {"", "", ""};
    }
    int n = 0;
    while (n < s.length() && isQuote(s.charAt(n))) {
      n++;
    }
    if (n == 0 || n == s.length()) {
      return new String[] {"", s, ""};
    }
    String rest = s.substring(n);
    int end = rest.length();
    int removed = 0;
    while (removed < n && end > 0 && isQuote(rest.charAt(end - 1))) {
      end--;
      removed++;
    }
    return new String[] {s.substring(0, n), rest.substring(0, end), rest.substring(end)};
  }

  private static boolean isQuote(char c) {
    return c == '"' || c == '\'';
  }
  
  /** Returns true iff every character is a hex digit and the string is non-empty. */
  private static boolean isHex(String s) {
    if (s == null || s.isEmpty()) {
      return false;
    }
    for (int k = 0; k < s.length(); k++) {
      if (Character.digit(s.charAt(k), 16) < 0) {
        return false;
      }
    }
    return true;
  }
  
  /**
   * Adjusts the given text: strips one edge quote on each side, then converts
   * sections introduced by "ar:::" (via {@link ArabicTool#toArabic(String)}) or
   * "lt:::" (via {@link #toLatin(String)}). "[U+XXXX]" tokens are converted to
   * real characters in every section and are never passed through the
   * transliteration tables. Markers are case-insensitive. A section runs until
   * the next marker or the end of the text; text before the first marker is
   * only emoji-converted.
   *
   * @param oldText the text to adjust; may be {@code null}
   * @return the adjusted text, or an empty string if {@code oldText} is
   *         {@code null} or empty
   */
  public static String adjustedText(String oldText) {
    if (oldText == null || oldText.isEmpty()) {
      return "";
    }

    // Remove one leading and one trailing single/double quote.
    String text = stripEdgeQuotes(oldText);

    final int n = text.length();
    StringBuilder out = new StringBuilder(n);

    // Section modes: 0 = plain, 1 = Arabic, 2 = Latin special characters.
    int mode = 0;
    int segStart = 0;
    int i = 0;

    while (i <= n) {
      int nextMode = -1;
      int markLen = 0;

      if (i == n) {
        nextMode = 3; // end of text
      } else if (text.regionMatches(true, i, "ar:::", 0, 5)) {
        nextMode = 1;
        markLen = 5;
      } else if (text.regionMatches(true, i, "lt:::", 0, 5)) {
        nextMode = 2;
        markLen = 5;
      }

      if (nextMode != -1) {
        out.append(convertSegment(text.substring(segStart, i), mode));

        if (nextMode == 3) {
          break;
        }
        segStart = i + markLen;
        i = segStart;
        mode = nextMode;
        continue;
      }
      i++;
    }

    return out.toString();
  }

  /**
   * Converts one section. "[U+XXXX]" tokens are turned into real characters
   * and skipped by the transliteration; only the text between tokens is
   * transliterated according to the mode.
   *
   * @param seg  the section text
   * @param mode 0 = plain, 1 = Arabic, 2 = Latin special characters
   * @return the converted section
   */
  private static String convertSegment(String seg, int mode) {
    if (seg.isEmpty()) {
      return "";
    }
    if (mode == 0) {
      return emojied(seg);
    }

    final int n = seg.length();
    StringBuilder sb = new StringBuilder(n);
    int plainStart = 0;
    int i = 0;

    while (i < n) {
      if (seg.charAt(i) == '['
          && (i + 2) < n
          && (seg.charAt(i + 1) == 'U' || seg.charAt(i + 1) == 'u')
          && seg.charAt(i + 2) == '+') {
        int close = seg.indexOf(']', i + 3);
        if (close > i + 3) {
          String token = seg.substring(i, close + 1);
          String converted = emojied(token);
          // emojied leaves malformed tokens untouched, so a change means success.
          if (!converted.equals(token)) {
            sb.append(transliterate(seg.substring(plainStart, i), mode));
            sb.append(converted);
            i = close + 1;
            plainStart = i;
            continue;
          }
        }
      }
      i++;
    }

    sb.append(transliterate(seg.substring(plainStart), mode));
    return sb.toString();
  }

  /** Applies the transliteration for the given mode (1 = Arabic, 2 = Latin). */
  private static String transliterate(String s, int mode) {
    if (s.isEmpty()) {
      return "";
    }
    return (mode == 1) ? ArabicTool.toArabic(s) : toLatin(s);
  }
  
  /**
   * Returns a short description of this class.
   *
   * @return a human-readable description
   */
  @Override
  public String toString () {
    return "Latin special character conversion utilities";
  }

}
