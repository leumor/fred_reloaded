package hyphanet.support.io.bucket;

import hyphanet.crypt.key.MasterSecret;
import hyphanet.support.io.*;
import hyphanet.support.io.randomaccessbuffer.RandomAccessBuffer;
import hyphanet.support.io.stream.RabInputStream;
import java.io.*;

/**
 * This class implements a {@link Bucket} and {@link RandomAccessible} interface using a {@link
 * RandomAccessBuffer} as its underlying storage. It is inherently read-only after construction.
 *
 * <p><b>Implementation Details:</b>
 *
 * <ul>
 *   <li><b>Read-Only:</b> RabBucket is designed to be read-only after creation. Modifications are
 *       not supported, reflecting the nature of its underlying {@link RandomAccessBuffer}.
 *   <li><b>Resource Management:</b> RabBucket directly manages the lifecycle of the provided {@link
 *       RandomAccessBuffer}. Closing or disposing of the RabBucket will close or dispose the
 *       underlying buffer.
 *   <li><b>Shadow Copies:</b> Shadow copy creation is not supported as RabBucket is intended to be
 *       a lightweight wrapper around an existing {@link RandomAccessBuffer}.
 * </ul>
 *
 * @see Bucket
 * @see RandomAccessible
 * @see RandomAccessBuffer
 */
public class Rab implements Bucket, RandomAccessible {

  /** Magic number used to identify {@link Rab} data in serialized form. */
  static final int MAGIC = 0x892a708a;

  /**
   * Constructs a new {@link Rab} Bucket wrapping an existing {@link RandomAccessBuffer}.
   *
   * <p>The newly created {@link Rab} Bucket will be read-only and operate on the provided {@link
   * #underlying} buffer. The size of the bucket is determined by the current size of the {@link
   * #underlying} buffer.
   *
   * @param underlying The {@link RandomAccessBuffer} to use as the underlying storage.
   */
  public Rab(RandomAccessBuffer underlying) {
    this.underlying = underlying;
    size = underlying.size();
  }

  /**
   * Constructs a {@link Rab} Bucket by restoring it from a {@link DataInputStream}.
   *
   * <p>This constructor is used to reconstruct a {@link Rab} Bucket from a serialized state,
   * typically during application restart or recovery. It delegates the actual restoration of the
   * underlying {@link RandomAccessBuffer} to {@link BucketTools#restoreRabFrom(DataInputStream,
   * FilenameGenerator, PersistentFileTracker, MasterSecret)}.
   *
   * @param dis The {@link DataInputStream} to read the bucket data from.
   * @param fg The {@link FilenameGenerator} for generating filenames if needed during restoration.
   * @param persistentFileTracker The {@link PersistentFileTracker} for managing persistent files.
   * @param masterKey The {@link MasterSecret} for decryption if the bucket is encrypted.
   * @throws IOException if an I/O error occurs during reading from the input stream or restoring
   *     the buffer.
   * @throws StorageFormatException if the data in the input stream is not in the expected format
   *     for a {@link Rab}.
   * @throws ResumeFailedException if the resumption process fails and the bucket cannot be properly
   *     initialized.
   * @see BucketTools#restoreRabFrom(DataInputStream, FilenameGenerator, PersistentFileTracker,
   *     MasterSecret)
   */
  Rab(
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
   * @throws IOException Always throws {@link IOException} because writing to a {@link Rab} Bucket
   *     is not supported. {@link Rab} Bucket instances are read-only.
   */
  @Override
  public OutputStream getOutputStream() throws IOException {
    throw new IOException("Not supported");
  }

  /**
   * {@inheritDoc}
   *
   * @throws IOException Always throws {@link IOException} because writing to a {@link Rab} Bucket
   *     is not supported. {@link Rab} Bucket instances are read-only.
   */
  @Override
  public OutputStream getOutputStreamUnbuffered() throws IOException {
    throw new IOException("Not supported");
  }

  /**
   * {@inheritDoc}
   *
   * <p>Returns a {@link BufferedInputStream} wrapping the unbuffered input stream from the
   * underlying {@link RandomAccessBuffer}. This provides efficient buffered reading from the
   * bucket's data.
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
   * <p>Creates an unbuffered {@link InputStream} to read data from the underlying {@link
   * RandomAccessBuffer}. The stream is implemented using {@link RabInputStream} which is optimized
   * for reading from {@link RandomAccessBuffer} efficiently without unnecessary copying.
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
   * <p>Always returns {@code null} as {@link Rab} Bucket does not have a specific name associated
   * with it, as it's typically a wrapper around an anonymous {@link RandomAccessBuffer}.
   *
   * @return {@code null}
   */
  @Override
  public String getName() {
    return null;
  }

  /**
   * {@inheritDoc}
   *
   * <p>Returns the current size of the data in the underlying {@link RandomAccessBuffer}. This size
   * is fixed at the time of {@link Rab} Bucket creation and does not change as {@link Rab} Bucket
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
   * <p>Always returns {@code true} because {@link Rab} Bucket is designed to be read-only.
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
   * <p>This method has no effect as {@link Rab} Bucket is always read-only. Calling this method
   * will not change the read-only status of the bucket.
   */
  @Override
  public void setReadOnly() {
    // Ignore.
  }

  /**
   * {@inheritDoc}
   *
   * <p>Disposes of the underlying {@link RandomAccessBuffer}, releasing any resources held by it.
   * This operation should be called when the {@link Rab} Bucket and its associated data are no
   * longer needed.
   *
   * @see RandomAccessBuffer#dispose()
   */
  @Override
  public void dispose() {
    underlying.dispose();
  }

  /**
   * {@inheritDoc}
   *
   * <p>Closes the underlying {@link RandomAccessBuffer}, releasing any resources associated with
   * it. After closing, further operations on the {@link Rab} Bucket might throw {@link
   * IOException}.
   *
   * @throws IOException if an I/O error occurs during closing the underlying buffer.
   * @see RandomAccessBuffer#close()
   */
  @Override
  public void close() {
    underlying.close();
  }

  /**
   * {@inheritDoc}
   *
   * <p>Shadow copies are not supported for {@link Rab} Bucket. This method always returns {@code
   * null}.
   *
   * @return {@code null}, as shadow copies are not supported.
   */
  @Override
  public RandomAccessible createShadow() {
    return null;
  }

  /**
   * {@inheritDoc}
   *
   * <p>Delegates the {@code onResume} operation to the underlying {@link RandomAccessBuffer}. This
   * allows the underlying buffer to perform any necessary resumption tasks after a restart, such as
   * re-registering with persistent storage trackers.
   *
   * @param context The {@link ResumeContext} providing runtime support for resuming.
   * @throws ResumeFailedException if the underlying buffer's resumption process fails.
   * @see RandomAccessBuffer#onResume(ResumeContext)
   */
  @Override
  public void onResume(ResumeContext context) throws ResumeFailedException {
    underlying.onResume(context);
  }

  /**
   * {@inheritDoc}
   *
   * <p>Stores the {@link Rab} Bucket's reconstruction data to the provided {@link
   * DataOutputStream}. This method writes the {@link #MAGIC} number followed by the reconstruction
   * data of the underlying {@link RandomAccessBuffer} to the output stream. This allows for the
   * {@link Rab} Bucket to be restored later using the {@link #Rab(DataInputStream,
   * FilenameGenerator, PersistentFileTracker, MasterSecret)} constructor.
   *
   * @param dos The {@link DataOutputStream} to write the bucket data to.
   * @throws IOException if an I/O error occurs during writing to the output stream.
   * @see RandomAccessBuffer#storeTo(DataOutputStream)
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
   * <p>Returns the underlying {@link RandomAccessBuffer} directly. This method provides efficient
   * access to the random access buffer representation of the bucket's data without copying.
   *
   * @return The underlying {@link RandomAccessBuffer}.
   */
  @Override
  public RandomAccessBuffer toRandomAccessBuffer() {
    return underlying;
  }

  /**
   * The size of the data stored in this bucket, which is determined by the size of the underlying
   * {@link RandomAccessBuffer} at the time of {@link Rab} Bucket creation.
   *
   * <p>This value is constant throughout the lifecycle of the {@link Rab} Bucket as it is
   * read-only.
   */
  final long size;

  /**
   * The underlying {@link RandomAccessBuffer} that provides the storage for this {@link Rab}
   * Bucket.
   *
   * <p>All data read and write operations are delegated to this {@link RandomAccessBuffer}. The
   * lifecycle of this buffer is managed by the {@link Rab} Bucket.
   */
  private final RandomAccessBuffer underlying;
}
