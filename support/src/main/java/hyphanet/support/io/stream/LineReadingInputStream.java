/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package hyphanet.support.io.stream;

import hyphanet.base.HexUtil;
import hyphanet.support.io.LineReader;
import java.io.EOFException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.jspecify.annotations.Nullable;

/**
 * A specialized {@link FilterInputStream} implementation that provides line reading capabilities
 * with support for both UTF-8 and ISO-8859-1 encodings.
 *
 * <p>Key features:
 *
 * <ul>
 *   <li>Support for both UTF-8 and ISO-8859-1 character encodings
 *   <li>Automatic handling of Unix (\n) and Windows (\r\n) line endings
 *   <li>Built-in protection against memory exhaustion through configurable line length limits
 *   <li>Optimized reading strategies based on underlying stream capabilities (mark/reset support)
 * </ul>
 *
 * <p><strong>Memory Safety:</strong><br>
 * This implementation includes safeguards against denial-of-service attacks and out-of-memory
 * conditions when processing untrusted input by enforcing a maximum line length.
 *
 * <p><strong>Example usage:</strong>
 *
 * <pre>{@code
 * try (FileInputStream fileStream = new FileInputStream("data.txt");
 *      LineReadingInputStream reader = new LineReadingInputStream(fileStream)) {
 *
 *     // Read a UTF-8 encoded line with 1KB maximum length
 *     String line = reader.readLine(1024, 128, true);
 *
 *     if (line != null) {
 *         // Process the line
 *     }
 * }
 * }</pre>
 *
 * @see LineReader
 * @see FilterInputStream
 * @see TooLongException
 */
@SuppressWarnings("java:S4929")
public class LineReadingInputStream extends FilterInputStream implements LineReader {

  /** Minimum buffer size for reading operations in bytes */
  private static final int MIN_BUFFER_SIZE = 128;

  /** Default buffer size for reading operations in bytes */
  private static final int DEFAULT_BUFFER_SIZE = 1024;

  /**
   * Creates a new LineReadingInputStream that reads from the specified input stream.
   *
   * @param in the underlying input stream to read from
   */
  public LineReadingInputStream(InputStream in) {
    super(in);
  }

  /**
   * {@inheritDoc}
   *
   * <p>This implementation provides two different reading strategies based on whether the
   * underlying stream supports marking:
   *
   * <ul>
   *   <li>If marking is supported, uses an optimized bulk reading strategy
   *   <li>If marking is not supported, falls back to byte-by-byte reading
   * </ul>
   *
   * <p>The method automatically handles both Unix (\n) and Windows (\r\n) line endings, stripping
   * them from the returned string.
   */
  @Override
  public @Nullable String readLine(int maxLength, int bufferSize, boolean utf) throws IOException {
    if (maxLength < 1) {
      return null;
    }

    if (maxLength <= bufferSize) {
      bufferSize = maxLength + 1; // Buffer too big, shrink it (add 1 for the optional \r)
    }

    return markSupported()
        ? readLineWithMarking(maxLength, bufferSize, utf)
        : readLineWithoutMarking(maxLength, bufferSize, utf);
  }

  /**
   * Reads a line when the underlying stream does not support marking.
   *
   * <p>This method reads the input stream byte by byte until one of the following conditions is
   * met:
   *
   * <ul>
   *   <li>A line terminator is encountered (\n)
   *   <li>The end of stream is reached
   *   <li>The maximum line length is exceeded
   * </ul>
   *
   * @param maxLength maximum allowed length of a line in bytes
   * @param bufferSize initial size of the read buffer
   * @param utf if true, decode as UTF-8; if false, decode as ISO-8859-1
   * @return the line of text, or null if end of stream is reached immediately
   * @throws IOException if an I/O error occurs
   * @throws TooLongException if the line length exceeds maxLength
   */
  protected @Nullable String readLineWithoutMarking(int maxLength, int bufferSize, boolean utf)
      throws IOException {
    byte[] buf = new byte[calculateBufferSize(maxLength, bufferSize)];
    int ctr = 0;

    while (true) {
      int x = read();
      if (x == -1) {
        return handleEndOfStream(buf, ctr, -1, utf);
      }

      if (x == '\n') {
        return createLineString(buf, ctr, utf);
      }

      if (ctr >= maxLength) {
        throw createTooLongException(buf, ctr, maxLength, utf);
      }

      if (ctr >= buf.length) {
        buf = Arrays.copyOf(buf, Math.min(buf.length * 2, maxLength));
      }

      buf[ctr++] = (byte) x;
    }
  }

  /**
   * Reads a line when the underlying stream supports marking.
   *
   * <p>This method uses an optimized bulk reading strategy that:
   *
   * <ul>
   *   <li>Reads data in larger chunks for better performance
   *   <li>Uses mark/reset to handle line terminators efficiently
   *   <li>Dynamically resizes the buffer as needed up to maxLength
   * </ul>
   *
   * @param maxLength maximum allowed length of a line in bytes
   * @param bufferSize initial size of the read buffer
   * @param utf if true, decode as UTF-8; if false, decode as ISO-8859-1
   * @return the line of text, or null if end of stream is reached immediately
   * @throws IOException if an I/O error occurs
   * @throws TooLongException if the line length exceeds maxLength
   */
  private @Nullable String readLineWithMarking(int maxLength, int bufferSize, boolean utf)
      throws IOException {
    byte[] buf = new byte[calculateBufferSize(maxLength, bufferSize)];
    int ctr = 0;

    mark(maxLength + 2); // Account for possible \r\n

    while (true) {
      int bytesRead = read(buf, ctr, buf.length - ctr);

      if (bytesRead <= 0) {
        return handleEndOfStream(buf, ctr, bytesRead, utf);
      }

      // WARNING: this is definitely safe with UTF_8 and ISO_8859_1, it may not be
      // safe with some
      // wierd ones.
      int end = ctr + bytesRead;
      for (; ctr < end; ctr++) {
        if (buf[ctr] == '\n') {
          String line = createLineString(buf, ctr, utf);
          reset();
          skipNBytes((long) ctr + 1);
          return line;
        }
      }

      if (ctr >= maxLength) {
        throw createTooLongException(buf, ctr, maxLength, utf);
      }

      if ((buf.length < maxLength) && (buf.length - ctr < bufferSize)) {
        buf = Arrays.copyOf(buf, Math.min(buf.length * 2, maxLength));
      }
    }
  }

  /**
   * Calculates the optimal buffer size based on the given constraints, balancing memory usage and
   * performance.
   *
   * <p>The buffer size calculation follows these principles:
   *
   * <ul>
   *   <li>Never exceeds the maximum line length (to prevent memory waste)
   *   <li>Maintains a minimum buffer size for efficiency (128 bytes)
   *   <li>Caps the maximum buffer size to a reasonable default (1024 bytes)
   *   <li>Respects the requested buffer size when it falls within these bounds
   * </ul>
   *
   * <p>This approach is optimal because it:
   *
   * <ul>
   *   <li>Minimizes memory overhead for small line lengths
   *   <li>Provides good performance for typical line lengths through adequate buffering
   *   <li>Prevents excessive memory allocation for very large lines
   *   <li>Adapts to both marking and non-marking input streams efficiently
   * </ul>
   *
   * @param maxLength maximum allowed length of a line
   * @param requestedSize requested buffer size from the caller
   * @return the optimal buffer size that balances memory usage and performance
   */
  @SuppressWarnings("java:S6885")
  private static int calculateBufferSize(int maxLength, int requestedSize) {
    return Math.max(
        Math.min(MIN_BUFFER_SIZE, maxLength), Math.min(DEFAULT_BUFFER_SIZE, requestedSize));
  }

  /**
   * Handles end-of-stream conditions when reading a line.
   *
   * @param buf the buffer containing the partial line
   * @param ctr number of bytes read so far
   * @param bytesRead result of the last read operation
   * @param utf encoding flag
   * @return the final line or null if no data was read
   * @throws EOFException if an unexpected end of stream is encountered
   */
  private @Nullable String handleEndOfStream(byte[] buf, int ctr, int bytesRead, boolean utf)
      throws EOFException {
    if (ctr == 0 && bytesRead < 0) {
      return null;
    }
    if (bytesRead == 0) {
      // Don't busy-loop. Probably a socket closed or something.
      // If not, it's not a salvageable situation; either way throw.
      throw new EOFException("Unexpected end of stream");
    }
    return createLineString(buf, ctr, utf);
  }

  /**
   * Creates a string from the buffer contents, handling CR/LF line endings.
   *
   * <p>This method automatically detects and removes carriage return characters when they precede
   * line feeds.
   *
   * @param buf the buffer containing the line data
   * @param endPos the position of the line end
   * @param utf if true, decode as UTF-8; if false, decode as ISO-8859-1
   * @return the decoded string without line termination characters
   */
  private String createLineString(byte[] buf, int endPos, boolean utf) {
    if (endPos == 0) {
      return "";
    }
    boolean hasCR = (endPos > 0 && buf[endPos - 1] == '\r');
    return new String(
        buf,
        0,
        hasCR ? endPos - 1 : endPos,
        utf ? StandardCharsets.UTF_8 : StandardCharsets.ISO_8859_1);
  }

  /**
   * Creates a {@link TooLongException} with detailed diagnostic information.
   *
   * @param buf the buffer containing the partial line
   * @param ctr number of bytes read
   * @param maxLength maximum allowed length
   * @param utf encoding flag
   * @return a new TooLongException with detailed error message
   */
  private static TooLongException createTooLongException(
      byte[] buf, int ctr, int maxLength, boolean utf) {
    return new TooLongException(
        String.format(
            "Line exceeded maximum length of %d bytes%n%s%n%s",
            maxLength,
            HexUtil.bytesToHex(buf, 0, ctr),
            new String(buf, 0, ctr, utf ? StandardCharsets.UTF_8 : StandardCharsets.ISO_8859_1)));
  }
}
