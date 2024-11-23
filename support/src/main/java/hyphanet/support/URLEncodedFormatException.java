/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package hyphanet.support;

import java.io.Serial;

/**
 * Exception thrown when attempting to decode an improperly formatted URL-encoded string.
 *
 * <p>This exception is thrown in scenarios such as:
 * <ul>
 *   <li>Incomplete percent-encoded sequences (e.g., "abc%2" or "test%")</li>
 *   <li>Invalid hex digits in percent-encoded sequences (e.g., "%ZZ" or "%G2")</li>
 *   <li>Null byte encoding attempts ("%00")</li>
 *   <li>General malformed URL encoding patterns</li>
 * </ul>
 *
 * <p>Example usage:
 * <pre>{@code
 * try {
 *     String decoded = URLDecoder.decode("invalid%ZZ", false);
 * } catch (URLEncodedFormatException e) {
 *     // Handle invalid encoding
 * }
 * }</pre>
 *
 * @see URLDecoder
 */
public class URLEncodedFormatException extends Exception {
    /**
     * Constructs a new URLEncodedFormatException with no detail message.
     */
    URLEncodedFormatException() {
    }

    /**
     * Constructs a new URLEncodedFormatException with the specified detail message.
     *
     * @param message the detail message describing the cause of the exception. Can be retrieved using
     *                the {@link #getMessage()} method.
     */
    URLEncodedFormatException(String message) {
        super(message);
    }

    /**
     * Constructs a new URLEncodedFormatException with the specified detail message and cause.
     *
     * @param message the detail message describing the cause of the exception
     * @param cause   the underlying cause of the exception
     *
     * @since 2.0
     */
    public URLEncodedFormatException(final String message, final Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructs a new URLEncodedFormatException with the specified cause.
     *
     * @param cause the underlying cause of the exception
     *
     * @since 2.0
     */
    public URLEncodedFormatException(final Throwable cause) {
        super(cause);
    }

    /**
     * Ensures consistent serialization across different versions. Using -1L as this is a custom
     * implementation.
     */
    @Serial
    private static final long serialVersionUID = -1;
}
