package hyphanet.support.io.bucket;

import hyphanet.crypt.key.MasterSecret;
import hyphanet.support.io.*;
import hyphanet.support.io.util.Stream;
import java.io.*;

/**
 * Pads a bucket to the next power of 2 file size. Note that self-terminating formats do not work
 * with {@link AeadCrypt} Bucket; it needs to know the real length. This pads with {@link
 * Stream#fill(OutputStream, long)}, which is reasonably random but is faster than using
 * SecureRandom, and vastly more secure than using a non-secure Random.
 */
public class Padded implements Bucket, Serializable {

  /** Magic number to identify {@code PaddedBucket} data in storage. */
  static final int MAGIC = 0xdaff6185;

  /** Version number of the {@code PaddedBucket} serialization format. */
  static final int VERSION = 1;

  @Serial private static final long serialVersionUID = 1L;

  /** Minimum size to which a bucket will be padded, even if the data is smaller. */
  private static final long MIN_PADDED_SIZE = 1024;

  /**
   * Creates a {@link Padded} Bucket wrapping the given underlying bucket. Assumes the underlying
   * bucket is initially empty.
   *
   * @param underlying The bucket to be padded. Must not be null.
   */
  public Padded(Bucket underlying) {
    this(underlying, 0);
  }

  /**
   * Creates a {@link Padded} Bucket wrapping the given underlying bucket, with a known initial data
   * size.
   *
   * @param underlying The underlying bucket to be padded.
   * @param size The actual size of the data currently in the underlying bucket.
   */
  public Padded(Bucket underlying, long size) {
    this.underlying = underlying;
    this.size = size;
  }

  /** Constructor for serialization purposes. */
  protected Padded() {
    underlying = null;
    size = 0;
  }

  /**
   * Constructor for restoring a {@link Padded} Bucket from a {@link DataInputStream}. This
   * constructor is used during bucket restoration from persistent storage.
   *
   * @param dis The {@link DataInputStream} to read the {@link Padded} Bucket data from.
   * @param fg The {@link FilenameGenerator} for generating filenames if needed.
   * @param persistentFileTracker The {@link PersistentFileTracker} for tracking persistent files if
   *     needed.
   * @param masterKey The {@link MasterSecret} for decryption if the bucket is encrypted.
   * @throws IOException If an I/O error occurs during reading from the {@link DataInputStream}.
   * @throws StorageFormatException If the data in the {@link DataInputStream} is not in the
   *     expected format.
   * @throws ResumeFailedException If the bucket restoration fails for any reason.
   */
  protected Padded(
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
    underlying = BucketTools.restoreFrom(dis, fg, persistentFileTracker, masterKey);
  }

  /**
   * {@inheritDoc}
   *
   * <p>This implementation synchronizes access to ensure only one output stream is open at a time.
   * It also resets the internal size counter to 0 when a new output stream is requested.
   */
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

  /**
   * {@inheritDoc}
   *
   * <p>This implementation synchronizes access to ensure only one output stream is open at a time.
   * It also resets the internal size counter to 0 when a new output stream is requested.
   */
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

  /**
   * {@inheritDoc}
   *
   * <p>Returns a {@link MyInputStream} which wraps the underlying bucket's input stream and
   * enforces the padded size limit.
   */
  @Override
  public InputStream getInputStream() throws IOException {
    return new MyInputStream(underlying.getInputStream());
  }

  /**
   * {@inheritDoc}
   *
   * <p>Returns a {@link MyInputStream} which wraps the underlying bucket's unbuffered input stream
   * and enforces the padded size limit.
   */
  @Override
  public InputStream getInputStreamUnbuffered() throws IOException {
    return new MyInputStream(underlying.getInputStreamUnbuffered());
  }

  /**
   * {@inheritDoc}
   *
   * <p>Prepends "Padded:" to the underlying bucket's name to indicate it's a padded bucket.
   */
  @Override
  public String getName() {
    return "Padded:" + underlying.getName();
  }

  /**
   * {@inheritDoc}
   *
   * <p>Returns the actual size of the data written to the bucket, not the padded size. This size is
   * tracked internally and updated during write operations. Access to the size is synchronized for
   * thread safety.
   */
  @Override
  public synchronized long size() {
    return size;
  }

  /**
   * {@inheritDoc}
   *
   * <p>Access to the read-only status is synchronized for thread safety.
   */
  @Override
  public synchronized boolean isReadOnly() {
    return readOnly;
  }

  /**
   * {@inheritDoc}
   *
   * <p>Sets the bucket to read-only. This operation is irreversible. Access to the read-only status
   * is synchronized for thread safety.
   */
  @Override
  public synchronized void setReadOnly() {
    readOnly = true;
  }

  /**
   * {@inheritDoc}
   *
   * <p>Disposes of the underlying bucket, releasing its resources.
   */
  @Override
  public void dispose() {
    underlying.dispose();
  }

  /**
   * {@inheritDoc}
   *
   * <p>Closes the underlying bucket, releasing associated system resources.
   */
  @Override
  public void close() {
    underlying.close();
  }

  /**
   * {@inheritDoc}
   *
   * <p>Creates a shadow copy of the underlying bucket and wraps it in a new {@code PaddedBucket}.
   * The shadow padded bucket is set to read-only and shares the same external storage as the
   * original.
   */
  @Override
  public Bucket createShadow() {
    Bucket shadow = underlying.createShadow();
    Padded ret = new Padded(shadow, size);
    ret.setReadOnly();
    return ret;
  }

  /**
   * {@inheritDoc}
   *
   * <p>Calls {@link Bucket#onResume} on the underlying bucket to allow it to perform any necessary
   * resumption tasks.
   */
  @Override
  public void onResume(ResumeContext context) throws ResumeFailedException {
    underlying.onResume(context);
  }

  /**
   * {@inheritDoc}
   *
   * <p>Stores the {@link Padded} Bucket's reconstruction data to the provided {@link
   * DataOutputStream}. This includes the version, size, read-only status, and the underlying
   * bucket's data.
   *
   * @throws IOException If an I/O error occurs while writing to the {@link DataOutputStream}.
   */
  @Override
  public void storeTo(DataOutputStream dos) throws IOException {
    dos.writeInt(MAGIC);
    dos.writeInt(VERSION);
    dos.writeLong(size);
    dos.writeBoolean(readOnly);
    underlying.storeTo(dos);
  }

  /** Output stream implementation that pads the underlying stream to a power of 2 size on close. */
  private class MyOutputStream extends FilterOutputStream {

    /**
     * Constructs a {@link MyOutputStream} wrapping the given output stream.
     *
     * @param os The underlying output stream to wrap.
     */
    MyOutputStream(OutputStream os) {
      super(os);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Writes a single byte to the underlying stream and increments the tracked size. The size
     * update is synchronized to ensure thread safety.
     */
    @Override
    public void write(int b) throws IOException {
      out.write(b);
      synchronized (Padded.this) {
        size++;
      }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Writes a byte array to the underlying stream and increments the tracked size by the length
     * of the array. The size update is synchronized to ensure thread safety.
     */
    @Override
    public void write(byte[] buf) throws IOException {
      out.write(buf);
      synchronized (Padded.this) {
        size += buf.length;
      }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Writes a portion of a byte array to the underlying stream and increments the tracked size
     * by the number of bytes written. The size update is synchronized to ensure thread safety.
     */
    @Override
    public void write(byte[] buf, int offset, int length) throws IOException {
      out.write(buf, offset, length);
      synchronized (Padded.this) {
        size += length;
      }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Pads the underlying stream to the next power of 2 size before closing it. Padding is done
     * using {@link Stream#fill(OutputStream, long)}. After padding, the underlying stream is
     * closed. The {@link #outputStreamOpen} flag is reset in a finally block to ensure it's always
     * reset even if padding fails.
     *
     * @throws IOException If an I/O error occurs during padding or closing the underlying stream.
     */
    @Override
    public void close() throws IOException {
      try {
        long padding;
        synchronized (Padded.this) {
          long paddedLength = paddedLength(size);
          padding = paddedLength - size;
        }
        Stream.fill(out, padding);
        out.close();
      } finally {
        synchronized (Padded.this) {
          outputStreamOpen = false;
        }
      }
    }

    @Override
    public String toString() {
      return "TrivialPaddedBucketOutputStream:" + out + "(" + Padded.this + ")";
    }

    /**
     * Calculates the padded length for a given size. The padded length is the next power of 2
     * greater than or equal to the given size, or {@link #MIN_PADDED_SIZE} if the size is smaller.
     *
     * @param size The actual data size.
     * @return The padded length, which is a power of 2.
     */
    private long paddedLength(long size) {
      long paddedSize = Math.max(size, MIN_PADDED_SIZE);
      if (Long.bitCount(paddedSize) == 1) {
        return paddedSize;
      }
      return Long.highestOneBit(paddedSize) << 1;
    }
  }

  /** Input stream implementation that limits reading to the un-padded size of the bucket. */
  private class MyInputStream extends FilterInputStream {

    /**
     * Constructs a {@link MyInputStream} wrapping the given input stream.
     *
     * @param is The underlying input stream to wrap.
     */
    MyInputStream(InputStream is) {
      super(is);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Reads a single byte from the underlying stream, but only if the current read position is
     * within the un-padded size. The read position is tracked by the {@link #counter} field and is
     * synchronized for thread safety.
     */
    @Override
    public int read() throws IOException {
      synchronized (Padded.this) {
        if (counter >= size) {
          return -1;
        }
      }
      int ret = in.read();
      synchronized (Padded.this) {
        counter++;
      }
      return ret;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Reads up to {@code length} bytes from the underlying stream into the provided buffer, but
     * only up to the un-padded size. The number of bytes to read is limited by the remaining
     * un-padded size. The read position is tracked by the {@link #counter} field and is
     * synchronized for thread safety.
     */
    @Override
    public int read(byte[] buf, int offset, int length) throws IOException {
      if (length < 0) {
        return -1;
      }
      if (length == 0) {
        return 0;
      }

      int bytesToRead;

      synchronized (Padded.this) {
        if (counter >= size) {
          return -1;
        }
        bytesToRead = (int) Math.min(length, size - counter);
      }
      int ret = in.read(buf, offset, bytesToRead);
      synchronized (Padded.this) {
        if (ret > 0) {
          counter += ret;
        }
      }
      return ret;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Skips up to {@code length} bytes in the underlying stream, but only up to the un-padded
     * size. The number of bytes to skip is limited by the remaining un-padded size. The read
     * position is tracked by the {@link #counter} field and is synchronized for thread safety.
     */
    @Override
    public long skip(long length) throws IOException {
      if (counter >= size || length <= 0) {
        return 0;
      }
      long bytesToSkip;
      synchronized (Padded.this) {
        bytesToSkip = Math.min(length, size - counter);
      }
      long ret = in.skip(bytesToSkip);
      synchronized (Padded.this) {
        if (ret > 0) {
          counter += ret;
        }
      }
      return ret;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Returns the number of bytes available to read from the underlying stream, but limited to
     * the remaining un-padded size. The available bytes are clamped to the un-padded size to
     * prevent reading beyond the intended data. The read position is tracked by the {@link
     * #counter} field and is synchronized for thread safety.
     */
    @Override
    public int available() throws IOException {
      long max;
      synchronized (Padded.this) {
        max = size - counter;
      }
      int ret = in.available();
      return Math.clamp(ret, 0, (int) max);
    }

    /**
     * Counter to track the number of bytes read from this input stream. This is used to enforce the
     * un-padded size limit.
     */
    private long counter;
  }

  /**
   * The underlying bucket that is being padded. All operations are delegated to this bucket, with
   * padding applied on output streams and size limiting on input streams.
   */
  private final Bucket underlying;

  /**
   * The actual size of the data stored in the underlying bucket, before padding. This size is
   * tracked during write operations and used to limit reads. It is not the padded size of the
   * underlying storage.
   */
  private long size;

  /**
   * Transient flag to indicate if an output stream is currently open for this bucket. Used to
   * prevent opening multiple output streams concurrently.
   */
  private transient boolean outputStreamOpen;

  /** Flag to indicate if this bucket is read-only. If true, write operations will be prevented. */
  private boolean readOnly;
}
