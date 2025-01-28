/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package hyphanet.support.io.stream;

import java.io.IOException;
import java.io.InputStream;

/**
 * An {@link InputStream} that always returns end of stream. This stream provides no data and is
 * typically used for testing or as a placeholder where an input stream is required but no actual
 * data needs to be read.
 */
public class NullInputStream extends InputStream {
  /**
   * Constructs a {@link NullInputStream}. No resources are initialized as this stream does not
   * provide any data.
   */
  public NullInputStream() {
    // No resources to initialize
  }

  /**
   * {@inheritDoc}
   *
   * <p>Always returns {@code -1}, indicating the end of the stream. This method effectively
   * provides no data.
   *
   * @return Always returns {@code -1}, indicating the end of the stream.
   */
  @Override
  public int read() {
    return -1;
  }

  /**
   * {@inheritDoc}
   *
   * <p>As this is a {@link NullInputStream}, no data is actually read and the end of the stream is
   * immediately reached.
   *
   * @return Returns {@code -1} if {@code len} is greater than 0, indicating the end of the stream
   *     is reached immediately. Returns {@code 0} if {@code len} is 0, as per the {@link
   *     InputStream} contract.
   */
  @Override
  public int read(byte[] b, int off, int len) throws IOException {
    if (len == 0) {
      return 0;
    }
    return -1;
  }

  /**
   * {@inheritDoc}
   *
   * <p>In a {@link NullInputStream}, skipping is a no-op, as there is no data to skip. However, the
   * method still adheres to the {@link InputStream} contract by returning the number of bytes
   * actually skipped.
   *
   * @return Returns {@code n} if {@code n} is non-negative, indicating that the requested number of
   *     bytes were "skipped". Returns {@code 0} if {@code n} is negative, as per the {@link
   *     InputStream} contract.
   */
  @Override
  public long skip(long n) throws IOException {
    return n >= 0 ? n : 0;
  }

  /**
   * {@inheritDoc}
   *
   * <p>As {@link NullInputStream} holds no resources, this method performs no action.
   */
  @Override
  public void close() throws IOException {
    // No resources to close
  }

  /**
   * {@inheritDoc}
   *
   * <p>Mark is not supported by {@link NullInputStream} because it represents an empty stream.
   * There is no meaningful position to mark in a stream that always returns end of file. Calling
   * this method will have no effect.
   */
  @Override
  public synchronized void mark(int readLimit) {
    // Mark is not supported
  }

  /**
   * {@inheritDoc}
   *
   * @throws IOException In the case of {@link NullInputStream}, it always throws an {@link
   *     IOException} because {@link #mark(int)} is not supported.
   */
  @Override
  public synchronized void reset() throws IOException {
    throw new IOException("Mark not supported");
  }
}
