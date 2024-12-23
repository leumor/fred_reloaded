/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package hyphanet.support.io;

import hyphanet.support.io.stream.LineReadingInputStream;

import java.io.IOException;

/**
 * Exception thrown when attempting to read a line that exceeds the maximum allowed length.
 * <p>
 * This exception is typically thrown by {@link LineReadingInputStream} when encountering a
 * line that is longer than the specified maximum length. This helps prevent denial-of-service
 * attacks and out-of-memory conditions when processing untrusted input.
 * </p>
 *
 * <p>Example usage:</p>
 * <pre>{@code
 * try {
 *     String line = inputStream.readLine(1000, 128, true);
 * } catch (TooLongException e) {
 *     // Handle the case where the line is too long
 *     System.err.println("Line exceeded maximum length: " + e.getMessage());
 * }
 * }</pre>
 */
public class TooLongException extends IOException {
    private static final long serialVersionUID = -1;

    TooLongException(String s) {
        super(s);
    }
}