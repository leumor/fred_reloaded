/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package hyphanet.support;

import java.nio.charset.StandardCharsets;
import org.jspecify.annotations.Nullable;

/**
 * Encodes strings for use in URIs according to RFC 3986.
 *
 * <p>This encoder differs from {@link java.net.URLEncoder} in several ways:
 *
 * <ul>
 *   <li>Does not convert spaces to '+' characters
 *   <li>Optionally preserves non-ASCII characters
 *   <li>Follows URI encoding rules rather than application/x-www-form-urlencoded format
 * </ul>
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * // Basic encoding
 * String encoded = URLEncoder.encode("Hello World!", true);  // Result: "Hello%20World%21"
 *
 * // Preserve non-ASCII characters
 * String encoded = URLEncoder.encode("Hello 世界", false);   // Result: "Hello%20世界"
 * }</pre>
 *
 * @see java.net.URI
 * @see URLDecoder
 */
public final class URLEncoder {
  // Moved here from FProxy by amphibian
  public static final String SAFE_URL_CHARACTERS =
      "*-_./0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ_abcdefghijklmnopqrstuvwxyz";

  /** Private constructor to prevent instantiation of utility class. */
  private URLEncoder() {
    throw new UnsupportedOperationException("Utility class");
  }

  /**
   * Encodes a string for inclusion in a URI with default settings.
   *
   * @param url The string to encode
   * @param force Characters that must be encoded regardless of their safety
   * @param ascii If true, encode all non-ASCII characters
   * @return The encoded string
   */
  public static String encode(String url, @Nullable String force, boolean ascii) {
    return encode(url, force, ascii, "");
  }

  /**
   * Encodes a string for inclusion in a URI with full control over encoding parameters.
   *
   * <p>The encoding process follows these rules:
   *
   * <ul>
   *   <li>Safe characters (defined in {@link #SAFE_URL_CHARACTERS}) are left unchanged
   *   <li>Characters in {@code extraSafeChars} are left unchanged
   *   <li>Characters in {@code force} are always encoded
   *   <li>Non-ASCII characters are encoded if {@code ascii} is true
   *   <li>All other characters are percent-encoded using UTF-8
   * </ul>
   *
   * @param url The string to encode
   * @param force Characters that must be encoded regardless of their safety (can be null)
   * @param ascii If true, encode all non-ASCII characters
   * @param extraSafeChars Additional characters to consider safe
   * @return The encoded string
   */
  public static String encode(
      final String url,
      final @Nullable String force,
      final boolean ascii,
      final String extraSafeChars) {

    StringBuilder encoded = new StringBuilder(url.length() * 2);

    for (int i = 0; i < url.length(); ++i) {
      char c = url.charAt(i);

      if (isCharacterSafe(c, force, ascii, extraSafeChars)) {
        encoded.append(c);
      } else {
        encodeCharacter(c, encoded);
      }
    }

    return encoded.toString();
  }

  /**
   * Encodes a string for inclusion in a URI with minimal parameters.
   *
   * @param url The string to encode
   * @param ascii If true, encode all non-ASCII characters
   * @return The encoded string
   */
  public static String encode(String url, boolean ascii) {
    return encode(url, null, ascii);
  }

  /** Determines if a character should be left unencoded. */
  private static boolean isCharacterSafe(
      final char c,
      final @Nullable String force,
      final boolean ascii,
      final String extraSafeChars) {

    return (SAFE_URL_CHARACTERS.indexOf(c) >= 0
            || (!ascii && isValidNonAsciiChar(c))
            || extraSafeChars.indexOf(c) >= 0)
        && (force == null || force.indexOf(c) < 0);
  }

  /** Checks if a character is a valid non-ASCII character. */
  private static boolean isValidNonAsciiChar(final char c) {
    return c >= 128
        && Character.isDefined(c)
        && !Character.isISOControl(c)
        && !Character.isSpaceChar(c);
  }

  /** Encodes a single character to its percent-encoded form. */
  private static void encodeCharacter(final char c, final StringBuilder builder) {
    for (byte b : String.valueOf(c).getBytes(StandardCharsets.UTF_8)) {
      int x = b & 0xFF;
      if (x < 16) {
        builder.append("%0");
      } else {
        builder.append('%');
      }
      builder.append(Integer.toHexString(x));
    }
  }
}
