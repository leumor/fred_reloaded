package hyphanet.support.io.storage.bucket;

import hyphanet.support.io.ResumeContext;
import hyphanet.support.io.storage.AbstractStorage;
import hyphanet.support.io.storage.RamStorage;
import hyphanet.support.io.storage.rab.ArrayRab;
import hyphanet.support.io.storage.rab.Rab;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

// FIXME: No synchronization, should there be?

/**
 * A memory-based implementation of the {@link Bucket} interface that stores data in a byte array.
 * This implementation provides in-memory storage capabilities with basic read and write operations.
 *
 * <p>The bucket can be marked as read-only, after which write operations will throw exceptions.
 * Once closed, all operations will throw exceptions.
 *
 * @author oskar
 */
public class ArrayBucket extends AbstractStorage
    implements RandomAccessBucket, RamStorage, Serializable {
  @Serial private static final long serialVersionUID = 1L;

  /** Constructs a new Array bucket with default name "ArrayBucket". */
  public ArrayBucket() {
    this("ArrayBucket");
  }

  /**
   * Constructs a new Array bucket with initial data.
   *
   * @param initdata The initial byte array to store in the bucket
   */
  public ArrayBucket(byte[] initdata) {
    this("ArrayBucket");
    data = initdata;
  }

  /**
   * Constructs a new Array bucket with the specified name.
   *
   * @param name The name identifier for this bucket
   */
  public ArrayBucket(String name) {
    data = new byte[0];
    this.name = name;
  }

  /**
   * {@inheritDoc}
   *
   * @throws IOException if the bucket is read-only or has been closed
   */
  @Override
  public OutputStream getOutputStream() throws IOException {
    if (readOnly) {
      throw new IOException("Read only");
    }
    if (closed()) {
      throw new IOException("Already closed");
    }
    return new ArrayBucketOutputStream();
  }

  /**
   * {@inheritDoc}
   *
   * @throws IOException if the bucket has been closed
   */
  @Override
  public InputStream getInputStream() throws IOException {
    if (closed()) {
      throw new IOException("Already closed");
    }
    return new ByteArrayInputStream(data);
  }

  /**
   * Returns the string representation of the stored data using UTF-8 encoding.
   *
   * @return A string containing the bucket's data in UTF-8 encoding
   */
  @Override
  public String toString() {
    return new String(data, StandardCharsets.UTF_8);
  }

  @Override
  public long size() {
    return data.length;
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public boolean isReadOnly() {
    return readOnly;
  }

  @Override
  public void setReadOnly() {
    readOnly = true;
  }

  @Override
  public byte[] toByteArray() throws IOException {
    if (closed()) {
      throw new IOException("Already closed");
    }
    long sz = size();
    int size = (int) sz;
    return Arrays.copyOf(data, size);
  }

  @Override
  public RandomAccessBucket createShadow() {
    return new NullBucket();
  }

  @Override
  public void onResume(ResumeContext context) {
    // Do nothing.
  }

  /**
   * {@inheritDoc}
   *
   * @throws UnsupportedOperationException always, as Array buckets don't support storage
   */
  @Override
  public void storeTo(DataOutputStream dos) {
    // Should not be used for persistent requests.
    throw new UnsupportedOperationException("Serialization not supported");
  }

  @Override
  public Rab toRandomAccessBuffer() {
    readOnly = true;
    return new ArrayRab(data, 0, data.length, true);
  }

  @Override
  public InputStream getInputStreamUnbuffered() throws IOException {
    return getInputStream();
  }

  @Override
  public OutputStream getOutputStreamUnbuffered() throws IOException {
    return getOutputStream();
  }

  /**
   * Inner class that extends ByteArrayOutputStream to manage writing to the bucket's internal
   * storage.
   */
  private class ArrayBucketOutputStream extends ByteArrayOutputStream {
    /** Constructs a new ArrayBucketOutputStream instance. */
    public ArrayBucketOutputStream() {
      super();
    }

    /**
     * Closes the stream and updates the bucket's internal data.
     *
     * @throws IOException if the bucket is read-only
     */
    @Override
    public synchronized void close() throws IOException {
      if (hasBeenClosed) {
        return;
      }
      if (readOnly) {
        throw new IOException("Read only");
      }

      data = super.toByteArray();
      // FIXME maybe we should throw on write instead? :)
      hasBeenClosed = true;
    }

    /** Flag indicating whether this stream has been closed. */
    private boolean hasBeenClosed = false;
  }

  /** The name identifier of this bucket. */
  private final String name;

  /**
   * The internal byte array storing the bucket's data. Updated atomically as a whole reference by
   * {@link ArrayBucketOutputStream}, not individual items. So only volatile is needed, not
   * AtomicReferenceArray.
   */
  @SuppressWarnings("java:S3077")
  private volatile byte[] data;

  /** Flag indicating whether this bucket is read-only. */
  private boolean readOnly;
}
