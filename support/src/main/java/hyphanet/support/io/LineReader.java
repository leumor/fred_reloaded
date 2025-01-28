/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package hyphanet.support.io;

import hyphanet.support.io.stream.LineReadingInputStream;
import java.io.IOException;
import org.jspecify.annotations.Nullable;

/**
 * An interface for reading text lines from a stream with configurable character encoding support
 * and built-in protection against excessive memory consumption.
 *
 * <p>This interface provides functionality to read lines of text from an input source, with support
 * for:
 *
 * <ul>
 *   <li>Multiple character encodings (UTF-8 and ISO-8859-1)
 *   <li>Both Unix (\n) and Windows (\r\n) line endings
 *   <li>Configurable maximum line length to prevent memory exhaustion
 *   <li>Adjustable buffer sizes for performance optimization
 * </ul>
 *
 * <p><strong>Line Termination:</strong><br>
 * A line is considered terminated by either a line feed character ("\n") or a carriage return
 * followed by a line feed ("\r\n"). The line termination characters are stripped from the returned
 * string.
 *
 * <p><strong>Example usage:</strong>
 *
 * <pre>{@code
 * try (InputStream input = new FileInputStream("data.txt")) {
 *     LineReader reader = new LineReadingInputStream(input);
 *
 *     // Read a UTF-8 encoded line with max length of 1000 bytes
 *     String line = reader.readLine(1000, 128, true);
 *
 *     // Read an ISO-8859-1 encoded line
 *     String isoLine = reader.readLine(500, 128, false);
 * } catch (TooLongException e) {
 *     // Handle lines exceeding maximum length
 * } catch (IOException e) {
 *     // Handle other I/O errors
 * }
 * }</pre>
 *
 * @see LineReadingInputStream
 */
public interface LineReader {

  /**
   * Reads a single line of text from the underlying stream using the specified encoding and size
   * constraints.
   *
   * <p>This method blocks until one of the following conditions occurs:
   *
   * <ul>
   *   <li>A line terminator is detected (\n or \r\n)
   *   <li>The end of the stream is reached
   *   <li>The maximum line length is exceeded
   * </ul>
   *
   * @param maxLength the maximum allowed length of a line in bytes. If a line exceeds this length,
   *     an {@link IOException} will be thrown to prevent memory exhaustion
   * @param bufferSize the initial size of the internal read buffer in bytes. This value may affect
   *     performance but not functionality. The actual buffer may grow up to maxLength if needed
   * @param utf if {@code true}, decode the input as UTF-8; if {@code false}, decode as ISO-8859-1
   * @return the line of text without any line termination characters, or {@code null} if the end of
   *     e end of the stream has been reached with no data read or if maxLength is less than 1
   * @throws IOException if an I/O error occurs while reading from the underlying stream
   */
  @Nullable String readLine(int maxLength, int bufferSize, boolean utf) throws IOException;
}
