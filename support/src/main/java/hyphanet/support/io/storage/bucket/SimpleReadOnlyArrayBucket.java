package hyphanet.support.io.storage.bucket;

import hyphanet.support.io.ResumeContext;
import hyphanet.support.io.storage.AbstractStorage;
import hyphanet.support.io.storage.rab.ArrayRab;
import hyphanet.support.io.storage.rab.Rab;
import java.io.*;
import java.util.Arrays;

/**
 * Simple read-only array bucket. Just an adapter class to save some RAM. Wraps a byte[], offset,
 * length into a Bucket. Read-only. ArrayBucket on the other hand is a chain of byte[]'s.
 *
 * <p>Not serializable as it doesn't copy. Should only be used for short-lived hacks for that
 * reason.
 */
public class SimpleReadOnlyArrayBucket extends AbstractStorage implements RandomAccessBucket {

  private static final long serialVersionUID = 1L;

  public SimpleReadOnlyArrayBucket(byte[] buf, int offset, int length) {
    this.buf = buf;
    this.offset = offset;
    this.length = length;
  }

  public SimpleReadOnlyArrayBucket(byte[] buf) {
    this(buf, 0, buf.length);
  }

  @Override
  public OutputStream getOutputStream() throws IOException {
    throw new IOException("Read only");
  }

  @Override
  public OutputStream getOutputStreamUnbuffered() throws IOException {
    throw new IOException("Read only");
  }

  @Override
  public InputStream getInputStreamUnbuffered() throws IOException {
    return new ByteArrayInputStream(buf, offset, length);
  }

  @Override
  public InputStream getInputStream() throws IOException {
    return getInputStreamUnbuffered();
  }

  @Override
  public String getName() {
    return "SimpleReadOnlyArrayBucket: len=" + length + ' ' + super.toString();
  }

  @Override
  public long size() {
    return length;
  }

  @Override
  public boolean isReadOnly() {
    return true;
  }

  @Override
  public void setReadOnly() {
    // Already read-only
  }

  @Override
  public RandomAccessBucket createShadow() {
    if (buf.length < 256 * 1024) {
      return new SimpleReadOnlyArrayBucket(Arrays.copyOfRange(buf, offset, offset + length));
    }
    return null;
  }

  @Override
  public void onResume(ResumeContext context) {
    // Not persistent.
    throw new UnsupportedOperationException();
  }

  @Override
  public void storeTo(DataOutputStream dos) {
    // Not persistent.
    throw new UnsupportedOperationException();
  }

  @Override
  public Rab toRandomAccessBuffer() throws IOException {
    var rab = new ArrayRab(buf, offset, length, true);
    rab.setReadOnly();
    return rab;
  }

  final byte[] buf;
  final int offset;
  final int length;
}
