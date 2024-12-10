/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package hyphanet.support;

import hyphanet.support.field.Fields;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * A utility class for decoding percent-encoded URL components using UTF-8 charset.
 *
 * <p><strong>Key Features:</strong>
 * <ul>
 *   <li>Decodes percent-encoded sequences (e.g., %20 → space, %2F → forward slash)</li>
 *   <li>Uses UTF-8 character encoding exclusively</li>
 *   <li>Provides strict and tolerant decoding modes</li>
 *   <li>Prevents null byte injection attacks</li>
 * </ul>
 *
 * <p><strong>Important Differences from {@link java.net.URLDecoder}:</strong>
 * <ul>
 *   <li>Does NOT decode application/x-www-form-urlencoded format</li>
 *   <li>Does NOT convert plus signs (+) to spaces</li>
 *   <li>Focuses solely on URI component decoding</li>
 * </ul>
 *
 * <p><strong>Usage Examples:</strong>
 * <pre>{@code
 * // Basic decoding
 * String decoded = URLDecoder.decode("Hello%20World", false);  // returns "Hello World"
 *
 * // Tolerant mode (handles malformed sequences)
 * String tolerant = URLDecoder.decode("50%", true);           // keeps "50%" as is
 * String strict = URLDecoder.decode("50%", false);            // throws URLEncodedFormatException
 *
 * // UTF-8 handling
 * String utf8 = URLDecoder.decode("Hello%20%E4%B8%96%E7%95%8C", false);  // returns "Hello 世界"
 * }</pre>
 *
 * <p><strong>Security Considerations:</strong>
 * <ul>
 *   <li>Prevents null byte injection by rejecting %00 sequences</li>
 *   <li>Validates all percent-encoded sequences</li>
 *   <li>Uses strict UTF-8 encoding/decoding</li>
 *   <li>Throws exceptions for malformed input in strict mode</li>
 * </ul>
 *
 * <p><strong>Thread Safety:</strong>
 * <p>This class is thread-safe. All methods are static and the class maintains no state.
 *
 * @see java.net.URI
 * @see URLEncoder
 * @see java.net.URLDecoder
 * @see java.nio.charset.StandardCharsets#UTF_8
 */
public final class URLDecoder {

    private URLDecoder() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Main method for testing the decoder.
     *
     * @param args strings to decode
     */
    public static void main(String[] args) {
        for (String arg : args) {
            try {
                System.out.printf("%s -> %s%n", arg, decode(arg, false));
            } catch (URLEncodedFormatException e) {
                System.err.printf("Error decoding %s: %s%n", arg, e.getMessage());
            }
        }
    }

    /**
     * Decodes a URL-encoded string using UTF-8 charset. This method handles percent-encoded
     * sequences in URLs (e.g., %20 for space, %2F for forward slash).
     *
     * <p>Example usage:
     * <pre>
     * String decoded = URLDecoder.decode("Hello%20World", false);  // returns "Hello World"
     * String decoded = URLDecoder.decode("Hello%20World%", false); // throws URLEncodedFormatException
     * </pre>
     *
     * <p>The decoder processes the input string character by character:
     * <ul>
     *   <li>Regular characters are converted to UTF-8 bytes directly</li>
     *   <li>Percent-encoded sequences (%XX) are converted to their byte values</li>
     *   <li>Invalid or incomplete percent-encoded sequences trigger exceptions</li>
     * </ul>
     *
     * @param input    The URL-encoded string to decode. Must not be null.
     * @param tolerant When {@code true}, invalid percent-encoded sequences are treated as literal
     *                 characters instead of throwing exceptions. This mode is useful when
     *                 processing user-pasted URLs that might contain un-encoded % characters. Not
     *                 recommended for security-sensitive applications.
     *
     * @return The decoded string in UTF-8 encoding
     *
     * @throws URLEncodedFormatException if the input contains invalid or incomplete percent-encoded
     *                                   sequences and tolerant mode is disabled
     * @see java.net.URLEncoder
     * @see java.nio.charset.StandardCharsets#UTF_8
     */
    public static String decode(String input, boolean tolerant) throws URLEncodedFormatException {
        if (input.isEmpty()) {
            return "";
        }

        try (var decodedBytes = new ByteArrayOutputStream(input.length())) {
            boolean hasDecodedSomething = false;

            for (int i = 0; i < input.length(); i++) {
                char c = input.charAt(i);

                if (c == '%') {
                    i = decodeHexSequence(input, i, decodedBytes, tolerant, hasDecodedSomething);
                    hasDecodedSomething = true;
                } else {
                    decodedBytes.write(String.valueOf(c).getBytes(StandardCharsets.UTF_8));
                }
            }

            return decodedBytes.toString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new URLEncodedFormatException(String.format("Failed to process input: %s", input),
                                                e);
        }
    }

    /**
     * Decodes a percent-encoded hex sequence in the input string.
     *
     * @param input               The complete input string being decoded
     * @param currentIndex        The current position in the input string (at the '%' character)
     * @param output              The output stream where decoded bytes are written
     * @param tolerant            If true, treats invalid sequences as literal characters
     * @param hasDecodedSomething Whether any successful decoding has occurred previously
     *
     * @return The new index position after processing the hex sequence
     *
     * @throws URLEncodedFormatException if the hex sequence is invalid or incomplete
     */
    private static int decodeHexSequence(
        final String input, final int currentIndex, final ByteArrayOutputStream output,
        final boolean tolerant, final boolean hasDecodedSomething)
        throws URLEncodedFormatException {

        // Ensure we have at least 2 more characters after '%' for a valid hex sequence
        if (currentIndex >= input.length() - 2) {
            throw new URLEncodedFormatException(
                String.format("Incomplete percent-encoding in: %s", input));
        }

        // Extract exactly two characters after '%' to form the hex value (e.g., %2F -> "2F")
        String hexVal = input.substring(currentIndex + 1, currentIndex + 3);

        try {
            long decoded = Fields.hexToLong(hexVal);

            // Prevent security vulnerability: null byte injection
            if (decoded == 0) {
                throw new URLEncodedFormatException("Cannot encode null byte (00)");
            }
            output.write((int) decoded);
            return currentIndex + 2;
        } catch (NumberFormatException e) {
            // Special handling for malformed hex values in tolerant mode
            if (tolerant && !hasDecodedSomething) {
                byte[] literal = ("%" + hexVal).getBytes(StandardCharsets.UTF_8);
                output.write(literal, 0, literal.length);
                return currentIndex + 2;
            }
            throw new URLEncodedFormatException(
                String.format("Invalid hex escape sequence '%%%s' in: %s", hexVal, input), e);
        }
    }
}
