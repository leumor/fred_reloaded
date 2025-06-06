package hyphanet.support;

import java.util.Locale;
import java.util.Set;

/**
 * Utility class for validating strings in various contexts such as filenames, Unicode characters,
 * and text formatting.
 *
 * <p>This class provides methods to check string validity across different operating systems and
 * contexts, including Windows, macOS, and Unix systems. It also includes checks for IDN
 * (Internationalized Domain Names) blacklist characters and various Unicode-related validations.
 *
 * <p>This class is immutable and thread-safe.
 *
 * @author Freenet Contributors
 * @version 2.0
 * @since 1.0
 */
public final class StringValidityChecker {

  /**
   * Set of characters blacklisted in IDN (Internationalized Domain Names). Source: <a
   * href="http://kb.mozillazine.org/Network.IDN.blacklist_chars">Mozilla IDN Blacklist</a>
   */
  private static final Set<Character> IDN_BLACKLIST =
      Set.of(
          (char) 0x0020, // SPACE
          (char) 0x00A0, // NO-BREAK SPACE
          (char) 0x00BC, // VULGAR FRACTION ONE QUARTER
          (char) 0x00BD, // VULGAR FRACTION ONE HALF
          (char) 0x01C3, // LATIN LETTER RETROFLEX CLICK
          (char) 0x0337, // COMBINING SHORT SOLIDUS OVERLAY
          (char) 0x0338, // COMBINING LONG SOLIDUS OVERLAY
          (char) 0x05C3, // HEBREW PUNCTUATION SOF PASUQ
          (char) 0x05F4, // HEBREW PUNCTUATION GERSHAYIM
          (char) 0x06D4, // ARABIC FULL STOP
          (char) 0x0702, // SYRIAC SUBLINEAR FULL STOP
          (char) 0x115F, // HANGUL CHOSEONG FILLER
          (char) 0x1160, // HANGUL JUNGSEONG FILLER
          (char) 0x2000, // EN QUAD
          (char) 0x2001, // EM QUAD
          (char) 0x2002, // EN SPACE
          (char) 0x2003, // EM SPACE
          (char) 0x2004, // THREE-PER-EM SPACE
          (char) 0x2005, // FOUR-PER-EM SPACE
          (char) 0x2006, // SIX-PER-EM-SPACE
          (char) 0x2007, // FIGURE SPACE
          (char) 0x2008, // PUNCTUATION SPACE
          (char) 0x2009, // THIN SPACE
          (char) 0x200A, // HAIR SPACE
          (char) 0x200B, // ZERO WIDTH SPACE
          (char) 0x2024, // ONE DOT LEADER
          (char) 0x2027, // HYPHENATION POINT
          (char) 0x2028, // LINE SEPARATOR
          (char) 0x2029, // PARAGRAPH SEPARATOR
          (char) 0x202F, // NARROW NO-BREAK SPACE
          (char) 0x2039, // SINGLE LEFT-POINTING ANGLE QUOTATION MARK
          (char) 0x203A, // SINGLE RIGHT-POINTING ANGLE QUOTATION MARK
          (char) 0x2044, // FRACTION SLASH
          (char) 0x205F, // MEDIUM MATHEMATICAL SPACE
          (char) 0x2154, // VULGAR FRACTION TWO THIRDS
          (char) 0x2155, // VULGAR FRACTION ONE FIFTH
          (char) 0x2156, // VULGAR FRACTION TWO FIFTHS
          (char) 0x2159, // VULGAR FRACTION ONE SIXTH
          (char) 0x215A, // VULGAR FRACTION FIVE SIXTHS
          (char) 0x215B, // VULGAR FRACTION ONE EIGTH
          (char) 0x215F, // FRACTION NUMERATOR ONE
          (char) 0x2215, // DIVISION SLASH
          (char) 0x23AE, // INTEGRAL EXTENSION
          (char) 0x29F6, // SOLIDUS WITH OVERBAR
          (char) 0x29F8, // BIG SOLIDUS
          (char) 0x2AFB, // TRIPLE SOLIDUS BINARY RELATION
          (char) 0x2AFD, // DOUBLE SOLIDUS OPERATOR
          (char) 0x2FF0, // IDEOGRAPHIC DESCRIPTION CHARACTER LEFT TO RIGHT
          (char) 0x2FF1, // IDEOGRAPHIC DESCRIPTION CHARACTER ABOVE TO BELOW
          (char) 0x2FF2, // IDEOGRAPHIC DESCRIPTION CHARACTER LEFT TO MIDDLE AND RIGHT
          (char) 0x2FF3, // IDEOGRAPHIC DESCRIPTION CHARACTER ABOVE TO MIDDLE AND BELOW
          (char) 0x2FF4, // IDEOGRAPHIC DESCRIPTION CHARACTER FULL SURROUND
          (char) 0x2FF5, // IDEOGRAPHIC DESCRIPTION CHARACTER SURROUND FROM ABOVE
          (char) 0x2FF6, // IDEOGRAPHIC DESCRIPTION CHARACTER SURROUND FROM BELOW
          (char) 0x2FF7, // IDEOGRAPHIC DESCRIPTION CHARACTER SURROUND FROM LEFT
          (char) 0x2FF8, // IDEOGRAPHIC DESCRIPTION CHARACTER SURROUND FROM UPPER LEFT
          (char) 0x2FF9, // IDEOGRAPHIC DESCRIPTION CHARACTER SURROUND FROM UPPER RIGHT
          (char) 0x2FFA, // IDEOGRAPHIC DESCRIPTION CHARACTER SURROUND FROM LOWER LEFT
          (char) 0x2FFB, // IDEOGRAPHIC DESCRIPTION CHARACTER OVERLAID
          (char) 0x3000, // IDEOGRAPHIC SPACE
          (char) 0x3002, // IDEOGRAPHIC FULL STOP
          (char) 0x3014, // LEFT TORTOISE SHELL BRACKET
          (char) 0x3015, // RIGHT TORTOISE SHELL BRACKET
          (char) 0x3033, // VERTICAL KANA REPEAT MARK UPPER HALF
          (char) 0x3164, // HANGUL FILLER
          (char) 0x321D, // PARENTHESIZED KOREAN CHARACTER OJEON
          (char) 0x321E, // PARENTHESIZED KOREAN CHARACTER O HU
          (char) 0x33AE, // SQUARE RAD OVER S
          (char) 0x33AF, // SQUARE RAD OVER S SQUARED
          (char) 0x33C6, // SQUARE C OVER KG
          (char) 0x33DF, // SQUARE A OVER M
          (char) 0xFE14, // PRESENTATION FORM FOR VERTICAL SEMICOLON
          (char) 0xFE15, // PRESENTATION FORM FOR VERTICAL EXCLAMATION MARK
          (char) 0xFE3F, // PRESENTATION FORM FOR VERTICAL LEFT ANGLE BRACKET
          (char) 0xFE5D, // SMALL LEFT TORTOISE SHELL BRACKET
          (char) 0xFE5E, // SMALL RIGHT TORTOISE SHELL BRACKET
          (char) 0xFEFF, // ZERO-WIDTH NO-BREAK SPACE
          (char) 0xFF0E, // FULLWIDTH FULL STOP
          (char) 0xFF0F, // FULL WIDTH SOLIDUS
          (char) 0xFF61, // HALFWIDTH IDEOGRAPHIC FULL STOP
          (char) 0xFFA0, // HALFWIDTH HANGUL FILLER
          (char) 0xFFF9, // INTERLINEAR ANNOTATION ANCHOR
          (char) 0xFFFA, // INTERLINEAR ANNOTATION SEPARATOR
          (char) 0xFFFB, // INTERLINEAR ANNOTATION TERMINATOR
          (char) 0xFFFC, // OBJECT REPLACEMENT CHARACTER
          (char) 0xFFFD // REPLACEMENT CHARACTER
          );

  /**
   * Set of reserved printable characters that cannot be used in Windows filenames. Source: <a
   * href="http://en.wikipedia.org/w/index.php?title=Filename&oldid=344618757">Wikipedia - Filename
   * article</a>
   */
  private static final Set<Character> WINDOWS_RESERVED_PRINTABLE_FILENAME_CHARS =
      Set.of('/', '\\', '?', '*', ':', '|', '\"', '<', '>');

  /**
   * Set of reserved filenames in Windows operating system. These names cannot be used as filenames
   * regardless of extension. Source: <a
   * href="http://en.wikipedia.org/w/index.php?title=Filename&oldid=344618757">Wikipedia - Filename
   * article</a>
   */
  private static final Set<String> WINDOWS_RESERVED_FILENAMES =
      Set.of(
          "aux", "clock$", "com1", "com2", "com3", "com4", "com5", "com6", "com7", "com8", "com9",
          "con", "lpt1", "lpt2", "lpt3", "lpt4", "lpt5", "lpt6", "lpt7", "lpt8", "lpt9", "nul",
          "prn");

  /**
   * Set of reserved printable characters that cannot be used in macOS filenames. Source: <a
   * href="http://en.wikipedia.org/w/index.php?title=Filename&oldid=344618757">Wikipedia - Filename
   * article</a>
   */
  private static final Set<Character> MACOS_RESERVED_PRINTABLE_FILENAME_CHARS = Set.of(':', '/');

  /**
   * Checks if a character is a reserved printable character in Windows filenames.
   *
   * <p><strong>Note:</strong> This method does not check for control characters, which are also
   * forbidden in filenames.
   *
   * @param c The character to check
   * @return true if the character is reserved in Windows filenames
   */
  public static boolean isWindowsReservedPrintableFilenameCharacter(final char c) {
    return WINDOWS_RESERVED_PRINTABLE_FILENAME_CHARS.contains(c);
  }

  /**
   * Checks if a filename is reserved in Windows.
   *
   * <p>For files with multiple dots, only the part before the first dot is checked. For example,
   * "con.blah.txt" is considered reserved because "con" is reserved.
   *
   * @param filename The filename to check
   * @return true if the filename is reserved in Windows
   * @throws NullPointerException if filename is null
   */
  public static boolean isWindowsReservedFilename(final String filename) {
    String namePart = filename.toLowerCase(Locale.ROOT);
    int nameEnd = namePart.indexOf('.');
    if (nameEnd == -1) {
      nameEnd = namePart.length();
    }

    return WINDOWS_RESERVED_FILENAMES.contains(namePart.substring(0, nameEnd));
  }

  /**
   * Checks if a character is a reserved printable character in macOS filenames.
   *
   * <p><strong>Note:</strong> This method does not check for control characters, which are also
   * forbidden in filenames.
   *
   * @param c The character to check
   * @return true if the character is reserved in macOS filenames
   */
  public static boolean isMacOSReservedPrintableFilenameCharacter(Character c) {
    return MACOS_RESERVED_PRINTABLE_FILENAME_CHARS.contains(c);
  }

  /**
   * Checks if a character is a reserved printable character in Unix filenames.
   *
   * @param c The character to check
   * @return true if the character is reserved in Unix filenames
   */
  public static boolean isUnixReservedPrintableFilenameCharacter(final char c) {
    return c == '/';
  }

  /**
   * Checks if a string contains any characters from the IDN blacklist.
   *
   * @param text The text to check
   * @return true if the text contains no blacklisted characters
   * @throws NullPointerException if text is null
   */
  public static boolean containsNoIDNBlacklistCharacters(final String text) {
    return text.chars().mapToObj(c -> (char) c).noneMatch(IDN_BLACKLIST::contains);
  }

  /**
   * Checks if a string contains any line breaks (including Unicode line separators).
   *
   * @param text The text to check
   * @return true if the text contains no line breaks
   * @throws NullPointerException if text is null
   */
  public static boolean containsNoLinebreaks(final String text) {
    return text.chars()
        .noneMatch(
            c ->
                Character.getType(c) == Character.LINE_SEPARATOR
                    || Character.getType(c) == Character.PARAGRAPH_SEPARATOR
                    || c == '\n'
                    || c == '\r');
  }

  /**
   * Checks for invalid Unicode characters in the text.
   *
   * <p>This method checks for:
   *
   * <ul>
   *   <li>Invalid Unicode values (FFFE/FFFF)
   *   <li>Surrogate code points
   * </ul>
   *
   * @param text The text to check
   * @return true if the text contains no invalid characters
   * @throws NullPointerException if text is null
   */
  public static boolean containsNoInvalidCharacters(String text) {
    for (int i = 0; i < text.length(); ) {
      int c = text.codePointAt(i);
      i += Character.charCount(c);

      if ((c & 0xFFFE) == 0xFFFE || Character.getType(c) == Character.SURROGATE) {
        return false;
      }
    }

    return true;
  }

  /**
   * Checks for control characters (including tab, LF, and CR) in the string.
   *
   * <p>Control characters are those with the Unicode category 'Cc'.
   *
   * @param text The text to check
   * @return true if the text contains no control characters
   * @throws NullPointerException if text is null
   */
  public static boolean containsNoControlCharacters(String text) {
    return text.chars().noneMatch(c -> Character.getType(c) == Character.CONTROL);
  }

  /**
   * Checks for invalid formatting characters and their combinations in text.
   *
   * <p>This method validates:
   *
   * <ul>
   *   <li>Directional formatting characters (LTR, RTL embeddings and overrides)
   *   <li>Annotation characters and their nesting
   *   <li>Proper pairing of formatting characters
   * </ul>
   *
   * @param text The text to check
   * @return true if the text contains no invalid formatting
   * @throws NullPointerException if text is null
   */
  public static boolean containsNoInvalidFormatting(String text) {
    int dirCount = 0;
    boolean inAnnotatedText = false;
    boolean inAnnotation = false;

    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      switch (c) {
        // Directional formatting
        case '\u202A': // LEFT-TO-RIGHT EMBEDDING
        case '\u202B': // RIGHT-TO-LEFT EMBEDDING
        case '\u202D': // LEFT-TO-RIGHT OVERRIDE
        case '\u202E': // RIGHT-TO-LEFT OVERRIDE
          dirCount++;
          break;

        case '\u202C': // POP DIRECTIONAL FORMATTING
          dirCount--;
          if (dirCount < 0) {
            return false;
          }
          break;

        // Annotation characters
        case '\uFFF9': // INTERLINEAR ANNOTATION ANCHOR
          if (inAnnotatedText || inAnnotation) {
            return false;
          }
          inAnnotatedText = true;
          break;

        case '\uFFFA': // INTERLINEAR ANNOTATION SEPARATOR
          if (!inAnnotatedText) {
            return false;
          }
          inAnnotatedText = false;
          inAnnotation = true;
          break;

        case '\uFFFB': // INTERLINEAR ANNOTATION TERMINATOR
          if (!inAnnotation) {
            return false;
          }
          inAnnotation = false;
          break;
      }
    }

    return dirCount == 0 && !inAnnotatedText && !inAnnotation;
  }

  /**
   * Checks if a string contains only Latin letters and numbers.
   *
   * <p>This method considers the following ranges:
   *
   * <ul>
   *   <li>a-z (lowercase letters)
   *   <li>A-Z (uppercase letters)
   *   <li>0-9 (numbers)
   * </ul>
   *
   * @param text The string to check
   * @return true if the string contains only Latin letters and numbers
   * @throws NullPointerException if text is null
   * @since 1.0
   */
  public static boolean isLatinLettersAndNumbersOnly(final String text) {
    return text.chars()
        .allMatch(c -> (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9'));
  }
}
