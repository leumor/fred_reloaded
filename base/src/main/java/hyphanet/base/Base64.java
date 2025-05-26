package hyphanet.base;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * A utility class for Base64 encoding and decoding operations with support for both standard and
 * URL-safe variants.
 *
 * <p>This implementation provides two Base64 variants:
 *
 * <ul>
 *   <li>A modified URL-safe version using '~' and '-' instead of '+' and '/'
 *   <li>The standard Base64 implementation following RFC 4648
 * </ul>
 *
 * <p>The class supports optional padding with '=' characters and UTF-8 string encoding/decoding.
 *
 * <p>Example usage:
 *
 * <pre>
 *   // Encoding
 *   String encoded = Base64.encode(byteArray);
 *   String encodedWithPadding = Base64.encode(byteArray, true);
 *
 *   // Decoding
 *   byte[] decoded = Base64.decode(encodedString);
 *   String decodedString = Base64.decodeUTF8(encodedString);
 * </pre>
 *
 * @author Stephen Blackheath
 */
public final class Base64 {
  /**
   * The URL-safe Base64 alphabet. This alphabet uses '~' (tilde) instead of '+' (plus) and '-'
   * (hyphen) instead of '/' (slash) compared to the standard Base64 alphabet. See RFC 4648, Section
   * 5: "Base 64 Encoding with URL and Filename Safe Alphabet".
   */
  private static final char[] BASE64_ALPHABET =
      "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789~-".toCharArray();

  /**
   * The standard Base64 alphabet as defined in RFC 4648, Section 4. This alphabet uses '+' (plus)
   * and '/' (slash) characters.
   */
  private static final char[] BASE64_STANDARD_ALPHABET =
      "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".toCharArray();

  /**
   * A reverse lookup table for the URL-safe Base64 alphabet ({@link #BASE64_ALPHABET}). The table
   * maps ASCII values of Base64 characters to their 6-bit integer equivalents. Invalid characters
   * are mapped to {@link #INVALID_CHAR}.
   */
  private static final byte[] BASE64_REVERSE = new byte[128];

  /**
   * A reverse lookup table for the standard Base64 alphabet ({@link #BASE64_STANDARD_ALPHABET}).
   * The table maps ASCII values of Base64 characters to their 6-bit integer equivalents. Invalid
   * characters are mapped to {@link #INVALID_CHAR}.
   */
  private static final byte[] BASE64_STANDARD_REVERSE = new byte[128];

  /**
   * Marker byte used in reverse lookup tables ({@link #BASE64_REVERSE}, {@link
   * #BASE64_STANDARD_REVERSE}) to indicate that a character is not a valid Base64 character. The
   * value {@code (byte) 0xFF} is used, which is -1 in signed byte representation.
   */
  private static final byte INVALID_CHAR = (byte) 0xFF;

  // Initialize reverse lookup tables
  // This static block populates BASE64_REVERSE and BASE64_STANDARD_REVERSE
  // lookup tables for efficient decoding.
  static {
    Arrays.fill(BASE64_REVERSE, INVALID_CHAR);
    Arrays.fill(BASE64_STANDARD_REVERSE, INVALID_CHAR);

    // Populate the reverse lookup tables.
    // For each character in the alphabets, its ASCII value is used as an index
    // in the reverse table, and the value stored is its 6-bit integer representation.
    for (int i = 0; i < BASE64_ALPHABET.length; i++) {
      BASE64_REVERSE[BASE64_ALPHABET[i]] = (byte) i;
    }
    for (int i = 0; i < BASE64_STANDARD_ALPHABET.length; i++) {
      BASE64_STANDARD_REVERSE[BASE64_STANDARD_ALPHABET[i]] = (byte) i;
    }
  }

  /**
   * Private constructor to prevent instantiation of this utility class.
   *
   * <p>This class is designed to be used statically and should not be instantiated.
   *
   * @throws UnsupportedOperationException always, to indicate that instantiation is not supported.
   */
  private Base64() {
    throw new UnsupportedOperationException("Utility class - should not be instantiated");
  }

  /**
   * Encodes the given byte array into a Base64 string using the URL-safe alphabet. No padding
   * characters ('=') are appended to the output.
   *
   * @param in the byte array to encode. Must not be {@code null}.
   * @return the URL-safe Base64 encoded string.
   * @see #encode(byte[], boolean)
   * @see #BASE64_ALPHABET
   */
  public static String encode(byte[] in) {
    return encode(in, false);
  }

  /**
   * Encodes the given byte array into a Base64 string using the URL-safe alphabet.
   *
   * @param in the byte array to encode. Must not be {@code null}.
   * @param equalsPad if {@code true}, padding characters ('=') are appended to the output to ensure
   *     the encoded string length is a multiple of 4. If {@code false}, no padding is added.
   * @return the URL-safe Base64 encoded string, optionally padded.
   * @see #BASE64_ALPHABET
   */
  public static String encode(byte[] in, boolean equalsPad) {
    return encode(in, equalsPad, BASE64_ALPHABET);
  }

  /**
   * Encodes the given string into a Base64 string using the URL-safe alphabet and UTF-8 character
   * encoding. No padding characters ('=') are appended to the output.
   *
   * @param in the string to encode. Must not be {@code null}.
   * @return the URL-safe Base64 encoded string.
   * @see #encodeUTF8(String, boolean)
   * @see #BASE64_ALPHABET
   */
  public static String encodeUTF8(String in) {
    return encodeUTF8(in, false);
  }

  /**
   * Encodes the given string into a Base64 string using the URL-safe alphabet and UTF-8 character
   * encoding.
   *
   * @param in the string to encode. Must not be {@code null}.
   * @param equalsPad if {@code true}, padding characters ('=') are appended to the output to ensure
   *     the encoded string length is a multiple of 4. If {@code false}, no padding is added.
   * @return the URL-safe Base64 encoded string, optionally padded.
   * @see #BASE64_ALPHABET
   */
  public static String encodeUTF8(String in, boolean equalsPad) {
    return encode(in.getBytes(StandardCharsets.UTF_8), equalsPad, BASE64_ALPHABET);
  }

  /**
   * Encodes the given string into a Base64 string using the standard RFC 4648 alphabet and UTF-8
   * character encoding. Padding characters ('=') are always appended to the output to ensure the
   * encoded string length is a multiple of 4.
   *
   * @param in the string to encode. Must not be {@code null}.
   * @return the standard Base64 encoded string with padding.
   * @see #encodeStandard(byte[])
   * @see #BASE64_STANDARD_ALPHABET
   */
  public static String encodeStandardUTF8(String in) {
    return encodeStandard(in.getBytes(StandardCharsets.UTF_8));
  }

  /**
   * Encodes the given byte array into a Base64 string using the standard RFC 4648 alphabet. Padding
   * characters ('=') are always appended to the output to ensure the encoded string length is a
   * multiple of 4.
   *
   * @param in the byte array to encode. Must not be {@code null}.
   * @return the standard Base64 encoded string with padding.
   * @see #BASE64_STANDARD_ALPHABET
   */
  public static String encodeStandard(byte[] in) {
    return encode(in, true, BASE64_STANDARD_ALPHABET);
  }

  /**
   * Decodes a Base64 encoded string (URL-safe alphabet) into a byte array. This method expects a
   * string encoded using the URL-safe alphabet ({@link #BASE64_ALPHABET}). It handles strings with
   * or without padding characters ('=').
   *
   * @param inStr the URL-safe Base64 encoded string to decode. Must not be {@code null}.
   * @return the decoded byte array.
   * @throws IllegalBase64Exception if the input string {@code inStr} contains invalid Base64
   *     characters or has an invalid length for Base64 encoding.
   * @see #BASE64_REVERSE
   */
  public static byte[] decode(String inStr) throws IllegalBase64Exception {
    return decode(inStr, BASE64_REVERSE);
  }

  /**
   * Decodes a Base64 encoded string (URL-safe alphabet) into a UTF-8 string. This method expects a
   * string encoded using the URL-safe alphabet ({@link #BASE64_ALPHABET}). It handles strings with
   * or without padding characters ('=').
   *
   * @param inStr the URL-safe Base64 encoded string to decode. Must not be {@code null}.
   * @return the decoded string, interpreted as UTF-8.
   * @throws IllegalBase64Exception if the input string {@code inStr} contains invalid Base64
   *     characters or has an invalid length for Base64 encoding.
   * @see #decode(String)
   */
  public static String decodeUTF8(String inStr) throws IllegalBase64Exception {
    return new String(decode(inStr), StandardCharsets.UTF_8);
  }

  /**
   * Decodes a Base64 encoded string (standard RFC 4648 alphabet) into a byte array. This method
   * expects a string encoded using the standard alphabet ({@link #BASE64_STANDARD_ALPHABET}). It
   * handles strings with or without padding characters ('=').
   *
   * @param inStr the standard Base64 encoded string to decode. Must not be {@code null}.
   * @return the decoded byte array.
   * @throws IllegalBase64Exception if the input string {@code inStr} contains invalid Base64
   *     characters or has an invalid length for Base64 encoding.
   * @see #BASE64_STANDARD_REVERSE
   */
  public static byte[] decodeStandard(String inStr) throws IllegalBase64Exception {
    return decode(inStr, BASE64_STANDARD_REVERSE);
  }

  /**
   * Core encoding logic. Encodes a byte array into a Base64 string using a specified alphabet.
   *
   * <p>This method processes the input byte array in 3-byte chunks, converting each chunk into 4
   * Base64 characters. If the input array length is not a multiple of 3, padding (either with
   * Base64 characters or '=') is handled according to {@code equalsPad} and the Base64
   * specification.
   *
   * @param in the byte array to encode. Must not be {@code null}.
   * @param equalsPad if {@code true}, '=' characters are used for padding to make the output length
   *     a multiple of 4. If {@code false}, no '=' padding is added, and the output may be shorter.
   * @param alphabet the character array representing the Base64 alphabet to use for encoding (e.g.,
   *     {@link #BASE64_ALPHABET} or {@link #BASE64_STANDARD_ALPHABET}).
   * @return the Base64 encoded string.
   */
  private static String encode(byte[] in, boolean equalsPad, char[] alphabet) {
    int inLength = in.length;
    // Calculate output length: 4 chars for every 3 input bytes, rounded up.
    char[] out = new char[((inLength + 2) / 3) * 4];
    int remainder = inLength % 3; // Number of bytes in the last, possibly partial, chunk.
    int outPos = 0; // Current position in the output character array.

    // Process full 3-byte chunks.
    for (int i = 0; i < inLength; ) {
      // Combine 3 bytes into a 24-bit integer.
      // If fewer than 3 bytes remain, pad with zero bits.
      int chunk =
          ((in[i++] & 0xFF) << 16)
              | ((i < inLength ? in[i++] & 0xFF : 0) << 8)
              | (i < inLength ? in[i++] & 0xFF : 0);

      // Extract four 6-bit values from the 24-bit integer and map to Base64 chars.
      out[outPos++] = alphabet[(chunk >> 18) & 0x3F];
      out[outPos++] = alphabet[(chunk >> 12) & 0x3F];
      out[outPos++] = alphabet[(chunk >> 6) & 0x3F];
      out[outPos++] = alphabet[chunk & 0x3F];
    }

    // Determine the actual length of the Base64 encoded content, before '=' padding.
    int outLen = out.length; // Assume full length initially.
    if (!equalsPad) { // If not using '=', adjust length based on remainder.
      outLen =
          switch (remainder) {
            case 1 -> out.length - 2; // Last 2 chars are from padding bits.
            case 2 -> out.length - 1; // Last 1 char is from padding bits.
            default -> out.length; // No remainder, full length.
          };
    } else { // If using '=', fill trailing characters with '=' if needed.
      if (remainder == 1) { // 1 byte input -> 2 Base64 chars + "=="
        out[out.length - 2] = '=';
        out[out.length - 1] = '=';
      } else if (remainder == 2) { // 2 bytes input -> 3 Base64 chars + "="
        out[out.length - 1] = '=';
      }
    }
    // For non-equalsPad cases, the 'out' array might be longer than needed.
    // For equalsPad, outLen is always out.length.
    return new String(out, 0, outLen);
  }

  /**
   * Core decoding logic. Decodes a Base64 encoded string into a byte array using a specified
   * reverse lookup table.
   *
   * <p>This method processes the input string in 4-character chunks, converting each chunk back
   * into 3 bytes. It handles optional '=' padding characters at the end of the string.
   *
   * @param inStr the Base64 encoded string to decode. Must not be {@code null}. It may or may not
   *     include trailing '=' padding characters.
   * @param reverseAlphabet the byte array mapping Base64 character ASCII values to their 6-bit
   *     integer equivalents (e.g., {@link #BASE64_REVERSE} or {@link #BASE64_STANDARD_REVERSE}).
   *     Invalid characters should map to {@link #INVALID_CHAR}.
   * @return the decoded byte array.
   * @throws IllegalBase64Exception if the input string {@code inStr} contains non-Base64
   *     characters, has an invalid length (e.g., a remainder of 1 after stripping padding), or if
   *     an {@link ArrayIndexOutOfBoundsException} occurs due to an invalid character indexing into
   *     {@code reverseAlphabet} (which implies an invalid character not caught by the {@code
   *     INVALID_CHAR} check, though robust reverse tables should prevent this).
   */
  private static byte[] decode(String inStr, byte[] reverseAlphabet) throws IllegalBase64Exception {
    char[] in = inStr.toCharArray();
    int inLength = in.length;

    // Strip trailing equals signs to determine the actual data length.
    while (inLength > 0 && in[inLength - 1] == '=') {
      inLength--;
    }

    int blocks = inLength / 4; // Number of full 4-character blocks.
    int remainder = inLength & 3; // Remainder characters (0, 2, or 3). A remainder of 1 is invalid.

    if (remainder == 1) {
      throw new IllegalBase64Exception(
          "Invalid Base64 encoded string length: "
              + inStr.length()
              + " (1 char remainder after stripping padding)");
    }

    // Calculate the length of the decoded output.
    // Each 4-char block -> 3 bytes.
    // A 2-char remainder -> 1 byte.
    // A 3-char remainder -> 2 bytes.
    int outLen =
        blocks * 3
            + switch (remainder) {
              case 2 -> 1; // XX== -> 1 byte
              case 3 -> 2; // XXX= -> 2 bytes
              default -> 0; // No remainder
            };

    try {
      byte[] out = new byte[outLen];
      int outPos = 0; // Current position in the output byte array.
      int inPos = 0; // Current position in the input character array.

      // Process all full 4-character blocks.
      while (inPos < blocks * 4) {
        int chunk = decodeChunk(in, inPos, reverseAlphabet);
        out[outPos++] = (byte) (chunk >> 16); // First byte from chunk
        out[outPos++] = (byte) (chunk >> 8); // Second byte from chunk
        out[outPos++] = (byte) chunk; // Third byte from chunk
        inPos += 4;
      }

      // Process the remaining 2 or 3 characters, if any.
      if (remainder > 0) {
        int chunk = decodePartialChunk(in, inPos, remainder, reverseAlphabet);
        if (remainder == 2) { // XX (corresponds to XX==)
          out[outPos] = (byte) (chunk >> 16); // First byte from chunk
        } else { // remainder == 3 (corresponds to XXX=)
          out[outPos++] = (byte) (chunk >> 16); // First byte from chunk
          out[outPos] = (byte) (chunk >> 8); // Second byte from chunk
        }
      }

      return out;
    } catch (ArrayIndexOutOfBoundsException _) {
      // This typically means a character in `inStr` was not a valid Base64 character
      // and its ASCII value was outside the bounds of `reverseAlphabet`, or a logic error.
      // The `decodeChunk` and `decodePartialChunk` should ideally throw IllegalBase64Exception
      // before this happens if `INVALID_CHAR` is checked correctly.
      throw new IllegalBase64Exception(
          "Invalid character encountered during Base64 decoding. "
              + "Input may contain characters not in the expected Base64 alphabet.");
    }
  }

  /**
   * Decodes a full 4-character Base64 chunk into a 24-bit integer. Each character represents 6
   * bits. 4 characters * 6 bits/char = 24 bits.
   *
   * @param in the character array containing the Base64 input string.
   * @param pos the starting position of the 4-character chunk in {@code in}.
   * @param reverseAlphabet the reverse lookup table (e.g., {@link #BASE64_REVERSE}) to map Base64
   *     characters to their 6-bit integer values.
   * @return the 24-bit integer representing the decoded chunk.
   * @throws IllegalBase64Exception if any character in the chunk is not a valid Base64 character
   *     according to the {@code reverseAlphabet}.
   */
  private static int decodeChunk(char[] in, int pos, byte[] reverseAlphabet)
      throws IllegalBase64Exception {
    int chunk = 0;
    for (int i = 0; i < 4; i++) {
      // Lookup character in reverse alphabet. '& 0xFF' converts signed byte to unsigned int.
      int value = reverseAlphabet[in[pos + i]] & 0xFF;
      if (value == (INVALID_CHAR & 0xFF)) { // Check against unsigned INVALID_CHAR
        throw new IllegalBase64Exception(
            "Invalid Base64 character: '" + in[pos + i] + "' at position " + (pos + i));
      }
      chunk = (chunk << 6) | value; // Append 6 bits to the chunk.
    }
    return chunk;
  }

  /**
   * Decodes a partial Base64 chunk (2 or 3 characters) from the end of a Base64 string.
   *
   * <p>- A 2-character chunk (e.g., "XX" from "XX==") decodes to 1 byte (8 bits). The 2 chars
   * provide 12 bits; the significant 8 bits form the byte. - A 3-character chunk (e.g., "XXX" from
   * "XXX=") decodes to 2 bytes (16 bits). The 3 chars provide 18 bits; the significant 16 bits form
   * the two bytes.
   *
   * <p>The resulting integer is shifted left so that the significant bits are in the most
   * significant positions, aligning with how {@link #decode(String, byte[])} extracts bytes (i.e.,
   * {@code chunk >> 16} and {@code chunk >> 8}).
   *
   * @param in the character array containing the Base64 input string.
   * @param pos the starting position of the partial chunk in {@code in}.
   * @param len the length of the partial chunk (must be 2 or 3).
   * @param reverseAlphabet the reverse lookup table (e.g., {@link #BASE64_REVERSE}) to map Base64
   *     characters to their 6-bit integer values.
   * @return an integer where the most significant bits represent the decoded data. For len=2, the
   *     top 8 bits (of 12 decoded bits) are significant. For len=3, the top 16 bits (of 18 decoded
   *     bits) are significant. The result is shifted: {@code (decoded_bits) << (6 * (4 - len))}.
   * @throws IllegalBase64Exception if any character in the chunk is not a valid Base64 character
   *     according to the {@code reverseAlphabet}.
   */
  private static int decodePartialChunk(char[] in, int pos, int len, byte[] reverseAlphabet)
      throws IllegalBase64Exception {
    int chunk = 0;
    for (int i = 0; i < len; i++) {
      // Lookup character in reverse alphabet. '& 0xFF' converts signed byte to unsigned int.
      int value = reverseAlphabet[in[pos + i]] & 0xFF;
      if (value == (INVALID_CHAR & 0xFF)) { // Check against unsigned INVALID_CHAR
        throw new IllegalBase64Exception(
            "Invalid Base64 character: '" + in[pos + i] + "' at position " + (pos + i));
      }
      chunk = (chunk << 6) | value; // Append 6 bits to the chunk.
    }
    // Shift the decoded bits to the most significant positions.
    // If len is 2 (12 bits), shift left by 12 (6 * (4-2)) to fill 24 bits like XXXXXX XXXXXX 000000
    // 000000.
    // If len is 3 (18 bits), shift left by 6  (6 * (4-3)) to fill 24 bits like XXXXXX XXXXXX XXXXXX
    // 000000.
    // This alignment allows the caller to extract bytes using `>> 16` and `>> 8`.
    return chunk << (6 * (4 - len));
  }
}
