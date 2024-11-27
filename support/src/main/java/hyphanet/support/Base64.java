package hyphanet.support;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;


/**
 * A utility class for Base64 encoding and decoding operations with support for both standard and
 * URL-safe variants.
 *
 * <p>This implementation provides two Base64 variants:</p>
 * <ul>
 *   <li>A modified URL-safe version using '~' and '-' instead of '+' and '/'</li>
 *   <li>The standard Base64 implementation following RFC 4648</li>
 * </ul>
 *
 * <p>The class supports optional padding with '=' characters and UTF-8 string encoding/decoding.</p>
 *
 * <p>Example usage:</p>
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
     * URL-safe Base64 alphabet
     */
    private static final char[] BASE64_ALPHABET =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789~-".toCharArray();

    /**
     * Standard Base64 alphabet as per RFC 4648
     */
    private static final char[] BASE64_STANDARD_ALPHABET =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".toCharArray();

    /**
     * Lookup table for URL-safe decoding
     */
    private static final byte[] BASE64_REVERSE = new byte[128];

    /**
     * Lookup table for standard Base64 decoding
     */
    private static final byte[] BASE64_STANDARD_REVERSE = new byte[128];

    /**
     * Marker for invalid Base64 characters
     */
    private static final byte INVALID_CHAR = (byte) 0xFF;

    // Initialize reverse lookup tables
    static {
        Arrays.fill(BASE64_REVERSE, INVALID_CHAR);
        Arrays.fill(BASE64_STANDARD_REVERSE, INVALID_CHAR);

        for (int i = 0; i < BASE64_ALPHABET.length; i++) {
            BASE64_REVERSE[BASE64_ALPHABET[i]] = (byte) i;
            BASE64_STANDARD_REVERSE[BASE64_STANDARD_ALPHABET[i]] = (byte) i;
        }
    }

    /**
     * Private constructor to prevent instantiation of utility class.
     *
     * @throws UnsupportedOperationException always
     */
    private Base64() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Encodes a byte array using URL-safe Base64 encoding without padding.
     *
     * @param in the byte array to encode
     *
     * @return the Base64 encoded string
     */
    public static String encode(byte[] in) {
        return encode(in, false);
    }

    /**
     * Encodes a byte array using URL-safe Base64 encoding with optional padding.
     *
     * @param in        the byte array to encode
     * @param equalsPad true to append padding characters, false otherwise
     *
     * @return the Base64 encoded string
     */
    public static String encode(byte[] in, boolean equalsPad) {
        return encode(in, equalsPad, BASE64_ALPHABET);
    }

    /**
     * Encodes a UTF-8 string using URL-safe Base64 encoding without padding.
     *
     * @param in the string to encode
     *
     * @return the Base64 encoded string
     */
    public static String encodeUTF8(String in) {
        return encodeUTF8(in, false);
    }

    /**
     * Encodes a UTF-8 string using URL-safe Base64 encoding with optional padding.
     *
     * @param in        the string to encode
     * @param equalsPad true to append padding characters, false otherwise
     *
     * @return the Base64 encoded string
     */
    public static String encodeUTF8(String in, boolean equalsPad) {
        return encode(in.getBytes(StandardCharsets.UTF_8), equalsPad, BASE64_ALPHABET);
    }

    /**
     * Encodes a UTF-8 string using standard Base64 encoding without padding.
     *
     * @param in the string to encode
     *
     * @return the Base64 encoded string
     */
    public static String encodeStandardUTF8(String in) {
        return encodeStandard(in.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Encodes a byte array using standard Base64 encoding with padding.
     *
     * @param in the byte array to encode
     *
     * @return the standard Base64 encoded string
     */
    public static String encodeStandard(byte[] in) {
        return encode(in, true, BASE64_STANDARD_ALPHABET);
    }

    /**
     * Decodes a URL-safe Base64 encoded string into a byte array.
     *
     * @param inStr the Base64 encoded string
     *
     * @return the decoded byte array
     *
     * @throws IllegalBase64Exception if the input is not valid Base64
     */
    public static byte[] decode(String inStr) throws IllegalBase64Exception {
        return decode(inStr, BASE64_REVERSE);
    }

    /**
     * Decodes a URL-safe Base64 encoded string into a UTF-8 string.
     *
     * @param inStr the Base64 encoded string
     *
     * @return the decoded UTF-8 string
     *
     * @throws IllegalBase64Exception if the input is not valid Base64
     */
    public static String decodeUTF8(String inStr) throws IllegalBase64Exception {
        return new String(decode(inStr), StandardCharsets.UTF_8);
    }

    /**
     * Decodes a standard Base64 encoded string into a byte array.
     *
     * @param inStr the standard Base64 encoded string
     *
     * @return the decoded byte array
     *
     * @throws IllegalBase64Exception if the input is not valid Base64
     */
    public static byte[] decodeStandard(String inStr) throws IllegalBase64Exception {
        return decode(inStr, BASE64_STANDARD_REVERSE);
    }

    /**
     * Internal method to encode a byte array using the specified Base64 alphabet.
     *
     * @param in        the byte array to encode
     * @param equalsPad whether to append padding characters
     * @param alphabet  the Base64 alphabet to use
     *
     * @return the Base64 encoded string
     */
    private static String encode(byte[] in, boolean equalsPad, char[] alphabet) {
        int inLength = in.length;
        char[] out = new char[((inLength + 2) / 3) * 4];
        int remainder = inLength % 3;
        int outPos = 0;

        for (int i = 0; i < inLength; ) {
            int chunk = ((in[i++] & 0xFF) << 16) | ((i < inLength ? in[i++] & 0xFF : 0) << 8) |
                        (i < inLength ? in[i++] & 0xFF : 0);

            out[outPos++] = alphabet[(chunk >> 18) & 0x3F];
            out[outPos++] = alphabet[(chunk >> 12) & 0x3F];
            out[outPos++] = alphabet[(chunk >> 6) & 0x3F];
            out[outPos++] = alphabet[chunk & 0x3F];
        }

        int outLen = switch (remainder) {
            case 1 -> out.length - 2;
            case 2 -> out.length - 1;
            default -> out.length;
        };

        if (equalsPad) {
            Arrays.fill(out, outLen, out.length, '=');
            outLen = out.length;
        }

        return new String(out, 0, outLen);
    }

    /**
     * Internal method to decode a Base64 string using the specified reverse lookup table.
     *
     * @param inStr           the Base64 encoded string
     * @param reverseAlphabet the reverse lookup table for decoding
     *
     * @return the decoded byte array
     *
     * @throws IllegalBase64Exception if the input is not valid Base64
     */
    private static byte[] decode(String inStr, byte[] reverseAlphabet) throws IllegalBase64Exception {
        char[] in = inStr.toCharArray();
        int inLength = in.length;

        // Strip trailing equals signs.
        while (inLength > 0 && in[inLength - 1] == '=') {
            inLength--;
        }

        int blocks = inLength / 4;
        int remainder = inLength & 3;

        if (remainder == 1) {
            throw new IllegalBase64Exception("Invalid Base64 length");
        }

        // blocks * 4 and blocks * 3 are the length of the input and output
        // sequences respectively, not including any partial block at the end.
        int outLen = blocks * 3 + switch (remainder) {
            case 2 -> 1;
            case 3 -> 2;
            default -> 0;
        };

        try {
            byte[] out = new byte[outLen];
            int outPos = 0;
            int inPos = 0;

            while (inPos < blocks * 4) {
                int chunk = decodeChunk(in, inPos, reverseAlphabet);
                out[outPos++] = (byte) (chunk >> 16);
                out[outPos++] = (byte) (chunk >> 8);
                out[outPos++] = (byte) chunk;
                inPos += 4;
            }

            if (remainder > 0) {
                int chunk = decodePartialChunk(in, inPos, remainder, reverseAlphabet);
                if (remainder == 2) {
                    out[outPos] = (byte) (chunk >> 16);
                } else { // reminder == 3
                    out[outPos++] = (byte) (chunk >> 16);
                    out[outPos] = (byte) (chunk >> 8);
                }
            }

            return out;
        } catch (ArrayIndexOutOfBoundsException e) {
            throw new IllegalBase64Exception("Invalid Base64 character");
        }
    }

    /**
     * Internal method to decode a complete 4-character chunk of Base64 data.
     *
     * @param in              the character array containing Base64 data
     * @param pos             starting position in the array
     * @param reverseAlphabet the reverse lookup table to use
     *
     * @return decoded 24-bit value
     *
     * @throws IllegalBase64Exception if invalid characters are encountered
     */
    private static int decodeChunk(char[] in, int pos, byte[] reverseAlphabet)
        throws IllegalBase64Exception {
        int chunk = 0;
        for (int i = 0; i < 4; i++) {
            int value = reverseAlphabet[in[pos + i]] & 0xFF;
            if (value == 0xFF) {
                throw new IllegalBase64Exception("Invalid Base64 character");
            }
            chunk = (chunk << 6) | value;
        }
        return chunk;
    }

    /**
     * Internal method to decode a partial chunk of Base64 data.
     *
     * @param in              the character array containing Base64 data
     * @param pos             starting position in the array
     * @param len             length of the partial chunk (2 or 3)
     * @param reverseAlphabet the reverse lookup table to use
     *
     * @return decoded partial chunk value
     *
     * @throws IllegalBase64Exception if invalid characters are encountered
     */
    private static int decodePartialChunk(char[] in, int pos, int len, byte[] reverseAlphabet)
        throws IllegalBase64Exception {
        int chunk = 0;
        for (int i = 0; i < len; i++) {
            int value = reverseAlphabet[in[pos + i]] & 0xFF;
            if (value == 0xFF) {
                throw new IllegalBase64Exception("Invalid Base64 character");
            }
            chunk = (chunk << 6) | value;
        }
        return chunk << (6 * (4 - len));
    }
}

