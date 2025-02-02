/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package hyphanet.support.io;

import java.io.BufferedReader;

/**
 * Utility class providing adapter implementations for various types of line readers. This class
 * cannot be instantiated and only provides static factory methods for creating {@link LineReader}
 * instances.
 *
 * @author Freenet Contributors
 */
public final class ReaderUtil {

  private ReaderUtil() {
    throw new AssertionError("Utility class should not be instantiated");
  }

  /**
   * Creates a {@link LineReader} that adapts a {@link BufferedReader}. The returned reader
   * delegates all line reading operations to the provided {@link BufferedReader} instance.
   *
   * @param br the {@link BufferedReader} to adapt
   * @return a new {@link LineReader} instance that wraps the provided reader
   * @see BufferedReader#readLine()
   */
  public static LineReader fromBufferedReader(final BufferedReader br) {
    return (maxLength, bufferSize, utf) -> br.readLine();
  }

  /**
   * Creates a {@link LineReader} that reads from a String array. The returned reader will
   * sequentially read through the array, returning one line at a time. When all lines have been
   * read, it returns {@code null}.
   *
   * <p>Note: This implementation maintains an internal counter and is not thread-safe.
   *
   * @param lines the array of strings to be read
   * @return a new {@link LineReader} instance that reads from the array
   */
  public static LineReader fromStringArray(final String[] lines) {
    return new LineReader() {
      @Override
      public String readLine(int maxLength, int bufferSize, boolean utf) {
        return (++currentLine < lines.length) ? lines[currentLine] : null;
      }

      private int currentLine = -1;
    };
  }
}
