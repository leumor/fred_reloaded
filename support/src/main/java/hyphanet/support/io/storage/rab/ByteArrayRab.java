package hyphanet.support.io.storage.rab;

import hyphanet.support.io.ResumeContext;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.Serial;
import java.io.Serializable;
import java.util.Arrays;

/**
 * An implementation of {@link Rab} that stores data in a byte array in memory. This class provides
 * thread-safe random access operations through synchronization.
 *
 * <p>The buffer can be set to read-only mode, after which write operations will throw an {@link
 * IOException}. All operations are synchronized to ensure thread safety.
 *
 * @see Rab
 */
public class ByteArrayRab implements Rab, Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /**
   * Creates a new ByteArray with a copy of the provided data.
   *
   * @param padded the byte array to copy data from
   */
  public ByteArrayRab(byte[] padded) {
    this.data = Arrays.copyOf(padded, padded.length);
  }

  /**
   * Creates a new ByteArray of specified size filled with zeros.
   *
   * @param size the size of the buffer in bytes
   * @throws IllegalArgumentException if size is negative
   */
  public ByteArrayRab(int size) {
    if (size < 0) {
      throw new IllegalArgumentException("Size cannot be negative: " + size);
    }
    this.data = new byte[size];
  }

  /**
   * Creates a new ByteArray with data copied from a portion of an existing array.
   *
   * @param initialContents the source array to copy from
   * @param offset the starting position in the source array
   * @param size the number of bytes to copy
   * @param readOnly if true, the buffer will be read-only
   * @throws IllegalArgumentException if offset or size are invalid
   */
  public ByteArrayRab(byte[] initialContents, int offset, int size, boolean readOnly) {
    if (offset < 0 || size < 0 || offset + size > initialContents.length) {
      throw new IllegalArgumentException(
          "Invalid parameters: offset=%d, size=%d, array length=%d"
              .formatted(offset, size, initialContents.length));
    }
    data = Arrays.copyOfRange(initialContents, offset, offset + size);
    this.readOnly = readOnly;
  }

  /** Protected constructor for serialization purposes. Creates an uninitialized buffer. */
  protected ByteArrayRab() {
    // For serialization.
    data = null;
  }

  /**
   * {@inheritDoc}
   *
   * <p>Marks this buffer as closed. Further operations will fail.
   */
  @Override
  public synchronized void close() {
    closed = true;
  }

  /**
   * {@inheritDoc}
   *
   * <p>Performs a synchronized read operation from the internal buffer.
   */
  @Override
  public synchronized void pread(long fileOffset, byte[] buf, int bufOffset, int length)
      throws IOException {
    validateState();
    validateReadOperation(fileOffset, length);
    System.arraycopy(data, (int) fileOffset, buf, bufOffset, length);
  }

  /**
   * {@inheritDoc}
   *
   * <p>Performs a synchronized write operation to the internal buffer.
   */
  @Override
  public synchronized void pwrite(long fileOffset, byte[] buf, int bufOffset, int length)
      throws IOException {
    validateState();
    validateWriteOperation(fileOffset, length);
    System.arraycopy(buf, bufOffset, data, (int) fileOffset, length);
  }

  /**
   * {@inheritDoc}
   *
   * <p>Returns the size of the internal buffer.
   */
  @Override
  public long size() {
    return data.length;
  }

  /** Sets this buffer to read-only mode. Once set, this cannot be reversed. */
  public synchronized void setReadOnly() {
    readOnly = true;
  }

  /**
   * Checks if this buffer is in read-only mode.
   *
   * @return true if the buffer is read-only, false otherwise
   */
  public synchronized boolean isReadOnly() {
    return readOnly;
  }

  /**
   * {@inheritDoc}
   *
   * <p>Returns a dummy lock as this implementation is always open.
   */
  @Override
  public RabLock lockOpen() {
    return new RabLock() {
      @Override
      protected void innerUnlock() {
        // Always open, no action needed
      }
    };
  }

  /**
   * {@inheritDoc}
   *
   * <p>No-op as there are no resources to dispose.
   */
  @Override
  public boolean dispose() {
    // No resources to dispose
    return false;
  }

  /**
   * {@inheritDoc}
   *
   * <p>No-op as no resume action is needed.
   */
  @Override
  public void onResume(ResumeContext context) {
    // No resume action needed
  }

  /**
   * {@inheritDoc}
   *
   * <p>Not supported in this implementation.
   *
   * @throws UnsupportedOperationException always
   */
  @Override
  public void storeTo(DataOutputStream dos) {
    throw new UnsupportedOperationException("Serialization not supported");
  }

  /**
   * Returns the internal buffer array. Package-private method for internal use.
   *
   * @return the internal byte array
   */
  public byte[] getBuffer() {
    return data;
  }

  /**
   * Validates that the buffer is not closed.
   *
   * @throws IOException if the buffer is closed
   */
  private void validateState() throws IOException {
    if (closed) {
      throw new IOException("Buffer is closed");
    }
  }

  /**
   * Validates read operation parameters.
   *
   * @param fileOffset the offset to start reading from
   * @param length the number of bytes to read
   * @throws IOException if the read operation would exceed buffer bounds
   * @throws IllegalArgumentException if fileOffset is negative
   */
  private void validateReadOperation(long fileOffset, int length) throws IOException {
    if (fileOffset < 0) {
      throw new IllegalArgumentException("Cannot read before zero: " + fileOffset);
    }
    if (fileOffset + length > data.length) {
      throw new IOException(
          "Cannot read after end: trying to read from %d to %d on block length %d"
              .formatted(fileOffset, fileOffset + length, data.length));
    }
  }

  /**
   * Validates write operation parameters and read-only status.
   *
   * @param fileOffset the offset to start writing to
   * @param length the number of bytes to write
   * @throws IOException if the write operation would exceed buffer bounds or buffer is read-only
   * @throws IllegalArgumentException if fileOffset is negative
   */
  private void validateWriteOperation(long fileOffset, int length) throws IOException {
    validateReadOperation(fileOffset, length);
    if (readOnly) {
      throw new IOException("Buffer is read-only");
    }
  }

  /** The internal byte array storing the buffer's data */
  private final byte[] data;

  /** Flag indicating if this buffer is read-only */
  private volatile boolean readOnly;

  /** Flag indicating if this buffer is closed */
  private volatile boolean closed;

  // Default hashCode() and equals() are correct for this type.

}
