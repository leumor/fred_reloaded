package hyphanet.support.io.stream;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * A {@link CountedOutputStream} wraps another {@link OutputStream} and counts the number of bytes
 * written through it. This class extends {@link FilterOutputStream} and provides a method to
 * retrieve the count of bytes written.
 */
public class CountedOutputStream extends FilterOutputStream {

  /**
   * Constructs a new {@link CountedOutputStream} that wraps the given {@link OutputStream}.
   *
   * @param out the underlying {@link OutputStream} to filter.
   */
  public CountedOutputStream(OutputStream out) {
    super(out);
  }

  /**
   * {@inheritDoc}
   *
   * <p>This method writes the specified byte to the underlying output stream and increments the
   * count of written bytes.
   */
  @Override
  public void write(int x) throws IOException {
    super.write(x);
    written++;
  }

  /**
   * {@inheritDoc}
   *
   * <p>This method writes {@code length} bytes from the specified byte array starting at offset
   * {@code offset} to the underlying output stream and increments the count of written bytes by
   * {@code length}.
   */
  @Override
  public void write(byte[] buf, int offset, int length) throws IOException {
    out.write(buf, offset, length);
    written += length;
  }

  /**
   * Returns the total number of bytes written to this stream so far.
   *
   * @return the number of bytes written.
   */
  public long written() {
    return written;
  }

  /**
   * The counter for the number of bytes written to the underlying output stream. This counter is
   * incremented by the {@link #write(int)}, {@link #write(byte[])}, and {@link #write(byte[], int,
   * int)} methods.
   */
  private long written;
}
