package hyphanet.support.io.stream;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * An output stream that proxies another output stream, but prevents closing the underlying stream.
 * This is useful when you want to pass an {@code OutputStream} to a component that might close it,
 * but you need to keep the underlying stream open. For example, when wrapping a system stream like
 * {@code System.out} or {@code System.err}.
 *
 * <p>Closing this stream will only flush the underlying stream, but not actually close it.
 */
public class NoCloseProxyOutputStream extends FilterOutputStream {

  /**
   * Constructs a {@link NoCloseProxyOutputStream} that proxies the given output stream.
   *
   * @param out The underlying {@link OutputStream} to proxy. This stream will be used for all write
   *     operations, and its {@code flush()} method will be called when {@code close()} is invoked
   *     on this proxy stream. However, its {@code close()} method will never be called by this
   *     proxy.
   */
  public NoCloseProxyOutputStream(OutputStream out) {
    super(out);
  }

  /**
   * {@inheritDoc}
   *
   * <p>This implementation simply delegates to the {@link #out} field's {@code write(byte[], int,
   * int)} method.
   */
  @Override
  public void write(byte[] buf, int offset, int length) throws IOException {
    out.write(buf, offset, length);
  }

  /**
   * {@inheritDoc}
   *
   * <p>This implementation prevents closing the underlying output stream ({@link #out}). Instead of
   * closing the underlying stream, this method only flushes it by calling {@code flush()} on the
   * proxied output stream.
   *
   * @throws IOException If an I/O error occurs during the flush operation.
   */
  @Override
  public void close() throws IOException {
    // Don't close the underlying stream.
    // It probably makes debugging easier to flush it.
    flush();
  }
}
