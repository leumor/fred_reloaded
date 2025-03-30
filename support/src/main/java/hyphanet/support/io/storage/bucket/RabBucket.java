package hyphanet.support.io.storage.bucket;

import hyphanet.crypt.key.MasterSecret;
import hyphanet.support.io.FilenameGenerator;
import hyphanet.support.io.PersistentFileTracker;
import hyphanet.support.io.ResumeContext;
import hyphanet.support.io.ResumeFailedException;
import hyphanet.support.io.storage.AbstractStorage;
import hyphanet.support.io.storage.StorageFormatException;
import hyphanet.support.io.storage.rab.Rab;
import hyphanet.support.io.stream.RabInputStream;
import java.io.*;

/**
 * This class implements a {@link Bucket} and {@link RandomAccessBucket} interface using a {@link
 * Rab} as its underlying storage. It is inherently read-only after construction.
 *
 * <p><b>Implementation Details:</b>
 *
 * <ul>
 *   <li><b>Read-Only:</b> RabBucket is designed to be read-only after creation. Modifications are
 *       not supported, reflecting the nature of its underlying {@link Rab}.
 *   <li><b>Resource Management:</b> RabBucket directly manages the lifecycle of the provided {@link
 *       Rab}. Closing or disposing of the RabBucket will close or dispose the underlying buffer.
 *   <li><b>Shadow Copies:</b> Shadow copy creation is not supported as RabBucket is intended to be
 *       a lightweight wrapper around an existing {@link Rab}.
 * </ul>
 *
 * @see RandomAccessBucket
 * @see Rab
 */
public class RabBucket extends AbstractStorage implements RandomAccessBucket {

  /** Magic number used to identify {@link RabBucket} data in serialized form. */
  static final int MAGIC = 0x892a708a;

  /**
   * Constructs a new {@link RabBucket} Bucket wrapping an existing {@link Rab}.
   *
   * <p>The newly created {@link RabBucket} Bucket will be read-only and operate on the provided
   * {@link #underlying} buffer. The size of the bucket is determined by the current size of the
   * {@link #underlying} buffer.
   *
   * @param underlying The {@link Rab} to use as the underlying storage.
   */
  public RabBucket(Rab underlying) {
    this.underlying = underlying;
    size = underlying.size();
  }

  /**
   * Constructs a {@link RabBucket} Bucket by restoring it from a {@link DataInputStream}.
   *
   * <p>This constructor is used to reconstruct a {@link RabBucket} Bucket from a serialized state,
   * typically during application restart or recovery. It delegates the actual restoration of the
   * underlying {@link Rab} to {@link BucketTools#restoreRabFrom(DataInputStream, FilenameGenerator,
   * PersistentFileTracker, MasterSecret)}.
   *
   * @param dis The {@link DataInputStream} to read the bucket data from.
   * @param fg The {@link FilenameGenerator} for generating filenames if needed during restoration.
   * @param persistentFileTracker The {@link PersistentFileTracker} for managing persistent files.
   * @param masterKey The {@link MasterSecret} for decryption if the bucket is encrypted.
   * @throws IOException if an I/O error occurs during reading from the input stream or restoring
   *     the buffer.
   * @throws StorageFormatException if the data in the input stream is not in the expected format
   *     for a {@link RabBucket}.
   * @throws ResumeFailedException if the resumption process fails and the bucket cannot be properly
   *     initialized.
   * @see BucketTools#restoreRabFrom(DataInputStream, FilenameGenerator, PersistentFileTracker,
   *     MasterSecret)
   */
  RabBucket(
      DataInputStream dis,
      FilenameGenerator fg,
      PersistentFileTracker persistentFileTracker,
      MasterSecret masterKey)
      throws IOException, StorageFormatException, ResumeFailedException {
    underlying = BucketTools.restoreRabFrom(dis, fg, persistentFileTracker, masterKey);
    size = underlying.size();
  }

  /**
   * {@inheritDoc}
   *
   * @throws IOException Always throws {@link IOException} because writing to a {@link RabBucket}
   *     Bucket is not supported. {@link RabBucket} Bucket instances are read-only.
   */
  @Override
  public OutputStream getOutputStream() throws IOException {
    throw new IOException("Not supported");
  }

  /**
   * {@inheritDoc}
   *
   * @throws IOException Always throws {@link IOException} because writing to a {@link RabBucket}
   *     Bucket is not supported. {@link RabBucket} Bucket instances are read-only.
   */
  @Override
  public OutputStream getOutputStreamUnbuffered() throws IOException {
    throw new IOException("Not supported");
  }

  /**
   * {@inheritDoc}
   *
   * <p>Returns a {@link BufferedInputStream} wrapping the unbuffered input stream from the
   * underlying {@link Rab}. This provides efficient buffered reading from the bucket's data.
   *
   * @return A {@link BufferedInputStream} for reading data from this bucket.
   * @throws IOException if an I/O error occurs while creating the input stream.
   * @see #getInputStreamUnbuffered()
   */
  @Override
  public InputStream getInputStream() throws IOException {
    return new BufferedInputStream(getInputStreamUnbuffered());
  }

  /**
   * {@inheritDoc}
   *
   * <p>Creates an unbuffered {@link InputStream} to read data from the underlying {@link Rab}. The
   * stream is implemented using {@link RabInputStream} which is optimized for reading from {@link
   * Rab} efficiently without unnecessary copying.
   *
   * @return An unbuffered {@link InputStream} for reading data from this bucket.
   * @throws IOException if an I/O error occurs while creating the input stream.
   * @see RabInputStream
   */
  @Override
  public InputStream getInputStreamUnbuffered() throws IOException {
    return new RabInputStream(underlying, 0, underlying.size());
  }

  /**
   * {@inheritDoc}
   *
   * <p>Always returns empty string as {@link RabBucket} Bucket does not have a specific name
   * associated with it, as it's typically a wrapper around an anonymous {@link Rab}.
   *
   * @return empty string
   */
  @Override
  public String getName() {
    return "";
  }

  /**
   * {@inheritDoc}
   *
   * <p>Returns the current size of the data in the underlying {@link Rab}. This size is fixed at
   * the time of {@link RabBucket} Bucket creation and does not change as {@link RabBucket} Bucket
   * is read-only.
   *
   * @return The size in bytes of the data in the bucket.
   */
  @Override
  public long size() {
    return size;
  }

  /**
   * {@inheritDoc}
   *
   * <p>Always returns {@code true} because {@link RabBucket} Bucket is designed to be read-only.
   * Modifications are not supported after creation.
   *
   * @return {@code true}
   */
  @Override
  public boolean isReadOnly() {
    return true;
  }

  /**
   * {@inheritDoc}
   *
   * <p>This method has no effect as {@link RabBucket} Bucket is always read-only. Calling this
   * method will not change the read-only status of the bucket.
   */
  @Override
  public void setReadOnly() {
    // Ignore.
  }

  /**
   * {@inheritDoc}
   *
   * <p>Disposes of the underlying {@link Rab}, releasing any resources held by it. This operation
   * should be called when the {@link RabBucket} Bucket and its associated data are no longer
   * needed.
   *
   * @see Rab#dispose()
   */
  @Override
  public void dispose() {
    if (!setDisposed()) {
      return;
    }
    underlying.dispose();
  }

  /**
   * {@inheritDoc}
   *
   * <p>Closes the underlying {@link Rab}, releasing any resources associated with it. After
   * closing, further operations on the {@link RabBucket} Bucket might throw {@link IOException}.
   *
   * @see Rab#close()
   */
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
   * <p>Shadow copies are not supported for {@link RabBucket} Bucket. This method always returns
   * {@code null}.
   *
   * @return {@code null}, as shadow copies are not supported.
   */
  @Override
  public RandomAccessBucket createShadow() {
    return new NullBucket();
  }

  /**
   * {@inheritDoc}
   *
   * <p>Delegates the {@code onResume} operation to the underlying {@link Rab}. This allows the
   * underlying buffer to perform any necessary resumption tasks after a restart, such as
   * re-registering with persistent storage trackers.
   *
   * @param context The {@link ResumeContext} providing runtime support for resuming.
   * @throws ResumeFailedException if the underlying buffer's resumption process fails.
   * @see Rab#onResume(ResumeContext)
   */
  @Override
  public void onResume(ResumeContext context) throws ResumeFailedException {
    underlying.onResume(context);
  }

  /**
   * {@inheritDoc}
   *
   * <p>Stores the {@link RabBucket} Bucket's reconstruction data to the provided {@link
   * DataOutputStream}. This method writes the {@link #MAGIC} number followed by the reconstruction
   * data of the underlying {@link Rab} to the output stream. This allows for the {@link RabBucket}
   * Bucket to be restored later using the {@link #RabBucket(DataInputStream, FilenameGenerator,
   * PersistentFileTracker, MasterSecret)} constructor.
   *
   * @param dos The {@link DataOutputStream} to write the bucket data to.
   * @throws IOException if an I/O error occurs during writing to the output stream.
   * @see Rab#storeTo(DataOutputStream)
   * @see #MAGIC
   */
  @Override
  public void storeTo(DataOutputStream dos) throws IOException {
    dos.writeInt(MAGIC);
    underlying.storeTo(dos);
  }

  /**
   * {@inheritDoc}
   *
   * <p>Returns the underlying {@link Rab} directly. This method provides efficient access to the
   * random access buffer representation of the bucket's data without copying.
   *
   * @return The underlying {@link Rab}.
   */
  @Override
  public Rab toRandomAccessBuffer() {
    return underlying;
  }

  /**
   * The size of the data stored in this bucket, which is determined by the size of the underlying
   * {@link Rab} at the time of {@link RabBucket} Bucket creation.
   *
   * <p>This value is constant throughout the lifecycle of the {@link RabBucket} Bucket as it is
   * read-only.
   */
  final long size;

  /**
   * The underlying {@link Rab} that provides the storage for this {@link RabBucket} Bucket.
   *
   * <p>All data read and write operations are delegated to this {@link Rab}. The lifecycle of this
   * buffer is managed by the {@link RabBucket} Bucket.
   */
  private final Rab underlying;
}
