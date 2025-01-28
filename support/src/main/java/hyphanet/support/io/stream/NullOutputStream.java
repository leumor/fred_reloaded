/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package hyphanet.support.io.stream;

import java.io.OutputStream;

/**
 * An {@link OutputStream} that discards all data written to it. This stream acts as a black hole
 * for data, similar to <code>/dev/null</code> on Unix-like systems. It is useful in situations
 * where an {@link OutputStream} is required, but the output data is not needed or should be
 * ignored.
 */
public class NullOutputStream extends OutputStream {
  /**
   * {@inheritDoc}
   *
   * <p>This implementation does nothing, effectively discarding the byte.
   */
  @Override
  public void write(int b) {
    // Nothing to write
  }

  /**
   * {@inheritDoc}
   *
   * <p>This implementation does nothing, effectively discarding the buffer.
   */
  @Override
  public void write(byte[] buf, int off, int len) {
    // Nothing to write
  }
}
