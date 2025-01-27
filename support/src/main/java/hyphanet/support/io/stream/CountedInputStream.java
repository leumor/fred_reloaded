package hyphanet.support.io.stream;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * A {@code CountedInputStream} wraps another input stream and counts the number of bytes read from
 * it. It extends {@link java.io.FilterInputStream} and provides a method to retrieve the current
 * byte count.
 */
public class CountedInputStream extends FilterInputStream {

  /**
   * Creates a {@link CountedInputStream} that wraps the given input stream.
   *
   * @param in the underlying input stream to be counted.
   * @throws IllegalStateException if the provided input stream is {@code null}.
   */
  public CountedInputStream(InputStream in) {
    super(in);
    if (in == null) {
      throw new IllegalStateException("null fed to CountedInputStream");
    }
  }

  /**
   * Returns the total number of bytes read from this input stream so far.
   *
   * @return the number of bytes read.
   */
  public final long count() {
    return count;
  }

  /**
   * {@inheritDoc}
   *
   * <p>This implementation reads a single byte from the underlying input stream and increments the
   * byte count if a byte is successfully read (i.e., not -1).
   */
  @Override
  public int read() throws IOException {
    int ret = super.read();
    if (ret != -1) {
      ++count;
    }
    return ret;
  }

  /**
   * {@inheritDoc}
   *
   * <p>This implementation reads up to {@code len} bytes from the underlying input stream into the
   * byte array {@code buf} starting at offset {@code off}. If bytes are successfully read (i.e.,
   * not -1), the byte count is incremented by the number of bytes read.
   */
  @Override
  public int read(byte[] buf, int off, int len) throws IOException {
    int ret = in.read(buf, off, len);
    if (ret != -1) {
      count += ret;
    }
    return ret;
  }

  /**
   * {@inheritDoc}
   *
   * <p>This implementation reads bytes from the underlying input stream into the byte array {@code
   * buf}. If bytes are successfully read (i.e., not -1), the byte count is incremented by the
   * number of bytes read.
   */
  @Override
  public int read(byte[] buf) throws IOException {
    int ret = in.read(buf);
    if (ret != -1) {
      count += ret;
    }
    return ret;
  }

  /**
   * {@inheritDoc}
   *
   * <p>This implementation skips up to {@code n} bytes from the underlying input stream. If bytes
   * are skipped successfully (i.e., return value is greater than 0), the byte count is incremented
   * by the number of bytes skipped.
   */
  @Override
  public long skip(long n) throws IOException {
    long l = in.skip(n);
    if (l > 0) {
      count += l;
    }
    return l;
  }

  /**
   * The total number of bytes read from the underlying input stream. This counter is incremented by
   * each {@link #read()} and {@link #skip(long)} operation that successfully reads or skips bytes.
   * It is protected to allow subclasses to access and potentially modify the count, although direct
   * modification is generally discouraged.
   */
  protected long count = 0;
}
