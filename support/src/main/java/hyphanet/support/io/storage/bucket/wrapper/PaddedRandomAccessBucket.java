package hyphanet.support.io.storage.bucket.wrapper;

import hyphanet.crypt.key.MasterSecret;
import hyphanet.support.io.FilenameGenerator;
import hyphanet.support.io.PersistentFileTracker;
import hyphanet.support.io.ResumeContext;
import hyphanet.support.io.ResumeFailedException;
import hyphanet.support.io.storage.AbstractStorage;
import hyphanet.support.io.storage.StorageFormatException;
import hyphanet.support.io.storage.bucket.Bucket;
import hyphanet.support.io.storage.bucket.BucketTools;
import hyphanet.support.io.storage.bucket.NullBucket;
import hyphanet.support.io.storage.bucket.RandomAccessBucket;
import hyphanet.support.io.storage.rab.PaddedRab;
import hyphanet.support.io.storage.rab.Rab;
import hyphanet.support.io.util.Stream;
import java.io.*;

/**
 * Pads a {@link RandomAccessBucket} bucket to the next power of 2 file size.
 *
 * <p>Self-terminating formats are incompatible with {@link AeadCryptBucket} Bucket as it requires
 * knowing the real length of the data. This class utilizes {@link Stream#fill(OutputStream, long)}
 * for padding, which provides reasonably random data faster than {@link java.security.SecureRandom}
 * and is significantly more secure than using {@link java.util.Random}.
 *
 * @see Stream#fill(OutputStream, long)
 * @see RandomAccessBucket
 * @see Bucket
 */
public class PaddedRandomAccessBucket extends AbstractStorage
    implements RandomAccessBucket, Serializable {

  /** Magic number for serialization verification. */
  public static final int MAGIC = 0x95c42e34;

  /** Version number for serialization compatibility. */
  static final int VERSION = 1;

  @Serial private static final long serialVersionUID = 1L;

  /** Minimum padded size for buckets, ensuring a minimum size even for small data. */
  private static final long MIN_PADDED_SIZE = 1024;

  /**
   * Creates a {@link PaddedRandomAccessBucket} Bucket for an empty underlying bucket.
   *
   * <p>Assumes the underlying bucket is initially empty.
   *
   * @param underlying The {@link RandomAccessBucket} bucket to pad.
   */
  public PaddedRandomAccessBucket(RandomAccessBucket underlying) {
    this(underlying, 0);
  }

  /**
   * Creates a {@link PaddedRandomAccessBucket} Bucket with a specified initial data size.
   *
   * @param underlying The underlying {@link RandomAccessBucket} bucket.
   * @param size The actual size of the data currently in the underlying bucket.
   */
  public PaddedRandomAccessBucket(RandomAccessBucket underlying, long size) {
    this.underlying = underlying;
    this.size = size;
  }

  /**
   * Constructs a {@link PaddedRandomAccessBucket} Bucket from a serialized state.
   *
   * <p>This constructor is used to restore a {@link PaddedRandomAccessBucket} Bucket from a {@link
   * DataInputStream}, typically during application restart or recovery. It reads version
   * information, size, read-only status, and restores the underlying bucket using {@link
   * BucketTools#restoreFrom}.
   *
   * @param dis The {@link DataInputStream} to read the serialized data from.
   * @param fg The {@link FilenameGenerator} for creating new file names if needed during bucket
   *     restoration.
   * @param persistentFileTracker The {@link PersistentFileTracker} for managing persistent files
   *     associated with the bucket.
   * @param masterKey The {@link MasterSecret} for decryption if the bucket is encrypted.
   * @throws IOException If an I/O error occurs during deserialization.
   * @throws StorageFormatException If the serialized data is in an invalid format (e.g., version
   *     mismatch).
   * @throws ResumeFailedException If the underlying bucket fails to resume its state.
   * @see BucketTools#restoreFrom(DataInputStream, FilenameGenerator, PersistentFileTracker,
   *     MasterSecret)
   */
  public PaddedRandomAccessBucket(
      DataInputStream dis,
      FilenameGenerator fg,
      PersistentFileTracker persistentFileTracker,
      MasterSecret masterKey)
      throws IOException, StorageFormatException, ResumeFailedException {
    int version = dis.readInt();
    if (version != VERSION) {
      throw new StorageFormatException("Bad version");
    }
    size = dis.readLong();
    readOnly = dis.readBoolean();
    underlying =
        (RandomAccessBucket) BucketTools.restoreFrom(dis, fg, persistentFileTracker, masterKey);
  }

  /** Constructor for serialization purposes. */
  protected PaddedRandomAccessBucket() {
    underlying = new NullBucket();
    size = 0;
  }

  @Override
  public OutputStream getOutputStream() throws IOException {
    OutputStream os;
    synchronized (this) {
      if (outputStreamOpen) {
        throw new IOException("Already have an OutputStream for " + this);
      }
      os = underlying.getOutputStream();
      outputStreamOpen = true;
      size = 0;
    }
    return new MyOutputStream(os);
  }

  @Override
  public OutputStream getOutputStreamUnbuffered() throws IOException {
    OutputStream os;
    synchronized (this) {
      if (outputStreamOpen) {
        throw new IOException("Already have an OutputStream for " + this);
      }
      os = underlying.getOutputStreamUnbuffered();
      outputStreamOpen = true;
      size = 0;
    }
    return new MyOutputStream(os);
  }

  @Override
  public InputStream getInputStream() throws IOException {
    return new MyInputStream(underlying.getInputStream());
  }

  @Override
  public InputStream getInputStreamUnbuffered() throws IOException {
    return new MyInputStream(underlying.getInputStreamUnbuffered());
  }

  /**
   * {@inheritDoc}
   *
   * <p>Prepends "Padded:" to the underlying bucket's name.
   */
  @Override
  public String getName() {
    return "Padded:" + underlying.getName();
  }

  /**
   * {@inheritDoc}
   *
   * <p>Returns the actual size of the data written to the bucket, not the padded size on disk.
   */
  @Override
  public synchronized long size() {
    return size;
  }

  @Override
  public synchronized boolean isReadOnly() {
    return readOnly;
  }

  @Override
  public synchronized void setReadOnly() {
    readOnly = true;
  }

  @Override
  public void dispose() {
    if (!setDisposed()) {
      return;
    }
    underlying.dispose();
  }

  @Override
  public void close() {
    if (!setClosed()) {
      return;
    }
    underlying.close();
  }

  /**
   * {@inheritDoc}
   *
   * <p>Creates a shadow copy of the underlying bucket and wraps it in a new {@link
   * PaddedRandomAccessBucket} Bucket. The shadow copy is read-only and shares the same underlying
   * storage.
   *
   * @return A new {@code PaddedRandomAccess} Bucket instance representing the shadow copy.
   */
  @Override
  public RandomAccessBucket createShadow() {
    RandomAccessBucket shadow = underlying.createShadow();
    PaddedRandomAccessBucket ret = new PaddedRandomAccessBucket(shadow, size);
    ret.setReadOnly();
    return ret;
  }

  @Override
  public void onResume(ResumeContext context) throws ResumeFailedException {
    underlying.onResume(context);
  }

  @Override
  public void storeTo(DataOutputStream dos) throws IOException {
    dos.writeInt(MAGIC);
    dos.writeInt(VERSION);
    dos.writeLong(size);
    dos.writeBoolean(readOnly);
    underlying.storeTo(dos);
  }

  /**
   * {@inheritDoc}
   *
   * <p>Converts the underlying bucket to a {@link Rab} and then wraps it in a {@link PaddedRab}
   * buffer to reflect the un-padded size. The original bucket and the returned buffer are set to
   * read-only.
   *
   * @throws IOException if an I/O error occurs or if the output stream is still open.
   */
  @Override
  public Rab toRandomAccessBuffer() throws IOException {
    synchronized (this) {
      if (outputStreamOpen) {
        throw new IOException("Must close first");
      }
      readOnly = true;
    }
    underlying.setReadOnly();
    Rab u = underlying.toRandomAccessBuffer();
    return new PaddedRab(u, size);
  }

  /**
   * Returns the underlying {@link RandomAccessBucket} bucket.
   *
   * <p>This method provides access to the wrapped {@link RandomAccessBucket} bucket. It is intended
   * for advanced use cases where direct interaction with the underlying bucket is necessary.
   *
   * @return The underlying {@link RandomAccessBucket} bucket.
   */
  public RandomAccessBucket getUnderlying() {
    return underlying;
  }

  /**
   * Output stream implementation that pads the underlying stream to a power of 2 size upon closing.
   *
   * <p>This class extends {@link FilterOutputStream} to intercept write and close operations. It
   * tracks the unpadded size and, upon closing, pads the output stream to the next power of 2 size
   * using {@link Stream#fill(OutputStream, long)}.
   *
   * @see PaddedRandomAccessBucket
   * @see Stream#fill(OutputStream, long)
   */
  private class MyOutputStream extends FilterOutputStream {

    /**
     * Constructs a {@code MyOutputStream}.
     *
     * @param os The underlying {@link OutputStream} to filter.
     */
    MyOutputStream(OutputStream os) {
      super(os);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Overrides the write(int) method to update the un-padded size after each byte written.
     *
     * @param b The byte to write.
     * @throws IOException if an I/O error occurs.
     */
    @Override
    public void write(int b) throws IOException {
      out.write(b);
      synchronized (PaddedRandomAccessBucket.this) {
        size++;
      }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Overrides the write(byte[]) method to update the un-padded size after writing the buffer.
     *
     * @param buf The buffer to write.
     * @throws IOException if an I/O error occurs or if the stream is already closed.
     */
    @Override
    public void write(byte[] buf) throws IOException {
      out.write(buf);
      synchronized (PaddedRandomAccessBucket.this) {
        if (closed) {
          throw new IOException("Already closed");
        }
        size += buf.length;
      }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Overrides the write(byte[], int, int) method to update the un-padded size after writing a
     * portion of the buffer.
     *
     * @param buf The buffer to write.
     * @param offset The start offset in the buffer.
     * @param length The number of bytes to write.
     * @throws IOException if an I/O error occurs or if the stream is already closed.
     */
    @Override
    public void write(byte[] buf, int offset, int length) throws IOException {
      out.write(buf, offset, length);
      synchronized (PaddedRandomAccessBucket.this) {
        if (closed) {
          throw new IOException("Already closed");
        }
        size += length;
      }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Overrides the close() method to pad the output stream to a power of 2 size before closing
     * the underlying stream. It calculates the padding needed and uses {@link Stream#fill} to add
     * padding bytes. Finally, it closes the underlying output stream and resets the {@link
     * #outputStreamOpen} flag in the enclosing {@link PaddedRandomAccessBucket} Bucket.
     *
     * @throws IOException if an I/O error occurs during padding or closing.
     * @see Stream#fill(OutputStream, long)
     */
    @Override
    public void close() throws IOException {
      try {
        long padding;
        synchronized (PaddedRandomAccessBucket.this) {
          if (closed) {
            return;
          }
          closed = true;
          long paddedLength = paddedLength(size);
          padding = paddedLength - size;
        }
        Stream.fill(out, padding);
        out.close();
      } finally {
        synchronized (PaddedRandomAccessBucket.this) {
          outputStreamOpen = false;
        }
      }
    }

    @Override
    public String toString() {
      return "TrivialPaddedBucketOutputStream:" + out + "(" + PaddedRandomAccessBucket.this + ")";
    }

    /**
     * Calculates the next power of 2 size for padding.
     *
     * <p>If the given size is less than {@link #MIN_PADDED_SIZE}, the minimum padded size is
     * returned. If the size is already a power of 2, it is returned directly. Otherwise, the next
     * power of 2 greater than the size is calculated and returned.
     *
     * @param size The current size.
     * @return The next power of 2 size, or {@link #MIN_PADDED_SIZE} if the size is smaller.
     */
    private long paddedLength(long size) {
      long paddedSize = Math.max(size, MIN_PADDED_SIZE);
      if (Long.bitCount(paddedSize) == 1) {
        return paddedSize;
      }
      return Long.highestOneBit(paddedSize) << 1;
    }

    /** Flag to indicate if this stream is closed. */
    private boolean closed;
  }

  /**
   * Input stream implementation that limits reading to the un-padded size of the bucket.
   *
   * <p>This class extends {@link FilterInputStream} to control read operations. It ensures that
   * reading does not exceed the originally written (un-padded) size of the bucket. It maintains a
   * counter to track the number of bytes read and stops reading when the un-padded size is reached.
   *
   * @see PaddedRandomAccessBucket
   */
  private class MyInputStream extends FilterInputStream {

    /**
     * Constructs a {@link MyInputStream}.
     *
     * @param is The underlying {@link InputStream} to filter.
     */
    public MyInputStream(InputStream is) {
      super(is);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Overrides the read() method to limit reading to the un-padded size. Returns -1 if the end
     * of the un-padded data is reached.
     *
     * @return The byte read, or -1 if the end of the un-padded data is reached.
     * @throws IOException if an I/O error occurs.
     */
    @Override
    public int read() throws IOException {
      synchronized (PaddedRandomAccessBucket.this) {
        if (counter >= size) {
          return -1;
        }
      }
      int ret = in.read();
      synchronized (PaddedRandomAccessBucket.this) {
        counter++;
      }
      return ret;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Overrides the read(byte[], int, int) method to limit reading to the un-padded size.
     * Adjusts the requested length if reading beyond the un-padded size is attempted.
     *
     * @param buf The buffer to read into.
     * @param offset The start offset in the buffer.
     * @param length The maximum number of bytes to read.
     * @return The number of bytes read, or -1 if the end of the un-padded data is reached.
     * @throws IOException if an I/O error occurs.
     */
    @Override
    public int read(byte[] buf, int offset, int length) throws IOException {
      synchronized (PaddedRandomAccessBucket.this) {
        if (length < 0) {
          return -1;
        }
        if (length == 0) {
          return 0;
        }
        if (counter >= size) {
          return -1;
        }
        if (counter + length >= size) {
          length = (int) Math.min(length, size - counter);
        }
      }
      int ret = in.read(buf, offset, length);
      synchronized (PaddedRandomAccessBucket.this) {
        if (ret > 0) {
          counter += ret;
        }
      }
      return ret;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Overrides the skip(long) method to limit skipping to the un-padded size. Adjusts the
     * requested length if skipping beyond the un-padded size is attempted.
     *
     * @param length The maximum number of bytes to skip.
     * @return The actual number of bytes skipped.
     * @throws IOException if an I/O error occurs.
     */
    @Override
    public long skip(long length) throws IOException {
      synchronized (PaddedRandomAccessBucket.this) {
        if (counter >= size) {
          return -1;
        }
        if (counter + length >= size) {
          length = (int) Math.min(length, counter + length - size);
        }
      }
      long ret = in.skip(length);
      synchronized (PaddedRandomAccessBucket.this) {
        if (ret > 0) {
          counter += ret;
        }
      }
      return ret;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Overrides available() to return the number of bytes available up to the un-padded size.
     * Ensures that available bytes do not exceed the remaining un-padded data.
     *
     * @return The number of bytes that can be read without blocking, up to the un-padded size.
     * @throws IOException if an I/O error occurs.
     */
    @Override
    public synchronized int available() throws IOException {
      long max = size - counter;
      int ret = in.available();
      if (max < ret) {
        ret = (int) max;
      }
      return Math.max(ret, 0);
    }

    /** Counter to track the number of bytes read from the stream. */
    private long counter;
  }

  /** The underlying {@link RandomAccessBucket} bucket that is being padded. */
  private final RandomAccessBucket underlying;

  /** The actual size of the un-padded data in the bucket. */
  private long size;

  /** Transient flag to indicate if an output stream is currently open. */
  private transient boolean outputStreamOpen;

  /** Flag to indicate if this bucket is read-only. */
  private boolean readOnly;
}
