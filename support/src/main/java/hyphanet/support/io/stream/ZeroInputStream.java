package hyphanet.support.io.stream;

import java.io.InputStream;
import java.util.Arrays;
import java.util.Objects;

/**
 * An {@code InputStream} that always returns zero bytes. This stream does not represent any actual
 * data source and solely produces a stream of zero values. It can be useful for testing purposes,
 * or as a placeholder when an input stream is required but no actual data needs to be read.
 */
public class ZeroInputStream extends InputStream {

  /**
   * {@inheritDoc}
   *
   * <p>This implementation always returns 0, representing a zero byte. It does not indicate the end
   * of the stream, as this stream is designed to be infinite in its zero output.
   *
   * @return always returns 0, representing a zero byte.
   */
  @Override
  public int read() {
    return 0;
  }

  /**
   * {@inheritDoc}
   *
   * <p>This implementation fills the provided buffer with zero bytes. It first validates the {@code
   * offset} and {@code length} arguments using {@link Objects#checkFromIndexSize(int, int, int)} to
   * ensure they are within the bounds of the buffer.
   *
   * <p>If {@code length} is 0, no action is performed and 0 is returned immediately. Otherwise, the
   * method uses {@link Arrays#fill(byte[], int, int, byte)} to efficiently fill the specified
   * portion of the buffer with zero bytes (represented as the byte value 0).
   *
   * @return the number of zero bytes read into the buffer, which is equal to {@code length} if
   *     {@code length > 0}, or 0 if {@code length == 0}.
   */
  @Override
  public int read(byte[] buf, int offset, int length) {
    Objects.checkFromIndexSize(offset, length, buf.length);

    if (length == 0) {
      return 0;
    }
    Arrays.fill(buf, offset, offset + length, (byte) 0);

    return length;
  }
}
