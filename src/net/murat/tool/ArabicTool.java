package net.murat.tool;

/**
 * Character constants and transliteration engines for converting between
 * a Latin-based keyboard layout and Arabic script (including Persian extensions).
 *
 * <p>The conversion is driven by two parallel tables: {@code latinchr} and
 * {@code arabicchr}. The character at index <em>n</em> in one table corresponds
 * to the character at index <em>n</em> in the other, so the ordering of both
 * tables must be kept in sync at all times.</p>
 *
 * <p>All methods are stateless and thread-safe.</p>
 *
 * @author Murat iNAN, muratsivas76@gmail.com
 */
public class ArabicTool extends Object implements java.io.Serializable {
   
  private static final long serialVersionUID = 1L;
  
  /** Creates a new instance. All functionality is exposed through static methods. */
  public ArabicTool () {}

  /**
   * Arabic-script characters. Index-aligned with {@link #latinchr}:
   * the order of entries here must match the Latin table exactly.
   */
  private static final char [] arabicchr = {
    /* Lafz-i Jalalah ligature */ '\uFDF2',
    /* Jim */   '\u062C' , /* Tha */  '\u062B' ,
    /* Ta */    '\u062A' , /* Ba */   '\u0628' ,
    /* Alef */  '\u0627' , /* Ra */   '\u0631' ,
    /* Dhal */  '\u0630' , /* Dal */  '\u062F' ,
    /* Kha */   '\u062E' , /* Ha */   '\u062D' ,
    /* Dad */   '\u0636' , /* Sad */  '\u0635' ,
    /* Shin */  '\u0634' , /* Sin */  '\u0633' ,
    /* Zay */   '\u0632' , /* Fa */   '\u0641' ,
    /* Ghayn */ '\u063A' , /* Ayn */  '\u0639' ,
    /* Dha */   '\u0638' , /* Tah */  '\u0637' ,
    /* Nun */   '\u0646' , /* Mim */  '\u0645' ,
    /* Lam */   '\u0644' , /* Kaf */  '\u0643' ,
    /* Qaf */   '\u0642' , /* Hamza */ '\u0621' ,
    /* Ya */    '\u064A' , /* Alef Maksura */ '\u0649' , /* Heh */ '\u0647' ,
    /* Waw */   '\u0648' , /* Teh Marbuta */  '\u0629' ,
    /* Hamza variants */ '\u0623' , '\u0624' , '\u0625' , '\u0626' ,

    /* Arabic-Indic digits */
    '\u0660', '\u0661', '\u0662', '\u0663',  '\u0664',
    '\u0665', '\u0666', '\u0667', '\u0668', '\u0669',

    /* Comma */ '\u060C' ,
    /* Full stop (Arabic) */ '\u06D4', /* Question mark */ '\u061F',
    /* Thousands separator (apostrophe) */ '\u066C', /* Maddah above */ '\u0653',
    /* Semicolon */ '\u061B', /* Tcheh */ '\u0686', /* Percent sign */ '\u066A',

    /* Slash */ '\u002F', /* Left parenthesis */ '\u0028', /* Right parenthesis */ '\u0029',
    /* Colon */ '\u003A', /* Exclamation mark */ '\u0021',
    /* Five-pointed star (asterisk) */ '\u066d',

    /* Fatha */          '\u064E' ,
    /* Damma */          '\u064F' ,
    /* Kasra */          '\u0650' ,
    /* Shadda */         '\u0651' ,
    /* Sukun */          '\u0652' ,
    /* Fathatan */       '\u064B' ,
    /* Dammatan */       '\u064C' ,
    /* Kasratan */       '\u064D' ,
    /* Superscript Alef (dagger alef) */ '\u0670',
    /* Subscript Alef */ '\u0656',

    /* Shin (alternate Latin mapping) */ '\u0634',

    /* --- Persian (Farsi) extensions --- */
    /* Peh */       '\u067E',
    /* Tcheh */     '\u0686',
    /* Jeh */       '\u0698',
    /* Gaf (Persian) */ '\u06AF'
  };

  /**
   * Latin characters used as the transliteration source/target.
   * Index-aligned with {@link #arabicchr}; the ordering is significant and
   * must not be changed independently of the Arabic table.
   */
  private static final char [] latinchr = {
    'W',
    'c', 'p', 't', 'b', 'a',
    'r', 'Z', 'd', 'j', 'h',
    'D', 'S', '\u015F', 's', 'z',
    'f', '\u011F', 'A', 'w', 'T',
    'n', 'm', 'l', 'k', 'g',
    'M', 'y', 'Y', 'Q', 'v',
    'P', 'R', 'F', 'N', 'K',
    '0', '1', '2', '3', '4',
    '5', '6', '7', '8', '9',
    ',', '-', '?', '\'', 'L',
    ';', '\u00E7', '%','/', '(',
    ')', ':', '!', '*',

    '\u00FC', '\u00F6', 'e', 'H', 'C',
    'X', 'B', 'J', '^', 'E', '\u015B',

    'V', '\u0107', 'I', 'G'
  };

  /** Number of mapped characters (length of the Latin table). */
  private static final int alen = latinchr.length;

  /**
   * Converts a Latin-layout string into its Arabic-script representation.
   *
   * <p>Each character is looked up in the Latin table and replaced by the
   * Arabic character at the same index. Characters with no mapping are
   * passed through unchanged. If a Latin character appears more than once
   * in the table, the first occurrence takes precedence.</p>
   *
   * @param str the Latin-layout text to convert; may be {@code null}
   * @return the converted text, or an empty string if {@code str} is
   *         {@code null} or empty
   */
  public static final String toArabic(String str) {
    if (str == null || str.isEmpty()) {
      return "";
    }

    char[] ch = str.toCharArray();
    final int chlen = ch.length;

    for (int i = 0; i < chlen; i++) {
      char sourceChar = ch[i];
      for (int j = 0; j < alen; j++) {
        if (sourceChar == latinchr[j]) {
          ch[i] = arabicchr[j];
          break;
        }
      }
    }

    return new String(ch);
  }

  /**
   * Converts an Arabic-script string back into its Latin-layout representation.
   *
   * <p>Each character is looked up in the Arabic table and replaced by the
   * Latin character at the same index. Characters with no mapping are
   * passed through unchanged. Where an Arabic character appears more than once
   * in the table (e.g. Shin, Tcheh), the first occurrence takes precedence,
   * so the reverse conversion is not guaranteed to be an exact inverse of
   * {@link #toArabic(String)} for those characters.</p>
   *
   * @param str the Arabic-script text to convert; may be {@code null}
   * @return the converted text, or an empty string if {@code str} is
   *         {@code null} or empty
   */
  public static final String fromArabic(String str) {
    if (str == null || str.isEmpty()) {
      return "";
    }

    char[] ch = str.toCharArray();
    final int chlen = ch.length;

    for (int i = 0; i < chlen; i++) {
      char sourceChar = ch[i];
      for (int j = 0; j < alen; j++) {
        if (sourceChar == arabicchr[j]) {
          ch[i] = latinchr[j];
          break;
        }
      }
    }

    return new String(ch);
  }

  /**
   * Returns the internal Arabic character table.
   *
   * <p><strong>Note:</strong> the returned array is the live internal table,
   * not a copy. Callers must treat it as read-only.</p>
   *
   * @return the Arabic character table, index-aligned with {@link #getLatinchr()}
   */
  public static final char [] getArabicchr () {
    return arabicchr;
  }

  /**
   * Returns the internal Latin character table.
   *
   * <p><strong>Note:</strong> the returned array is the live internal table,
   * not a copy. Callers must treat it as read-only.</p>
   *
   * @return the Latin character table, index-aligned with {@link #getArabicchr()}
   */
  public static final char [] getLatinchr() {
    return latinchr;
  }

  /**
   * Returns a short description of this class.
   *
   * @return a human-readable description
   */
  @Override
  public String toString () {
    return "Character constants for Arabic language";
  }

}
