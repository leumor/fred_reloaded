/**
 * This code is part of Freenet. It is distributed under the GNU General Public License, version 2
 * (or at your option any later version). See http://www.gnu.org/ for further details of the GPL.
 */
package hyphanet.support.io.storage.bucket.wrapper;

import hyphanet.crypt.key.MasterSecret;
import hyphanet.support.io.FilenameGenerator;
import hyphanet.support.io.PersistentFileTracker;
import hyphanet.support.io.ResumeContext;
import hyphanet.support.io.ResumeFailedException;
import hyphanet.support.io.storage.AbstractStorage;
import hyphanet.support.io.storage.DelayedDisposable;
import hyphanet.support.io.storage.StorageFormatException;
import hyphanet.support.io.storage.bucket.Bucket;
import hyphanet.support.io.storage.bucket.BucketTools;
import hyphanet.support.io.storage.bucket.NullBucket;
import hyphanet.support.io.storage.bucket.RandomAccessBucket;
import hyphanet.support.io.storage.rab.DelayedDisposeRab;
import hyphanet.support.io.storage.rab.Rab;
import java.io.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A wrapper class that implements delayed disposal of RandomAccessible buckets, ensuring that
 * actual resource cleanup occurs only after all necessary data has been written to persistent
 * storage. This class is particularly useful when dealing with temporary files or resources that
 * need to be maintained until specific conditions are met.
 *
 * <h2>Key Features:</h2>
 *
 * <ul>
 *   <li>Implements delayed disposal mechanism
 *   <li>Supports serialization for persistence
 *   <li>Provides random access capabilities
 *   <li>Maintains thread safety for critical operations
 * </ul>
 *
 * @see RandomAccessBucket
 * @see DelayedDisposable
 * @see Bucket
 */
public class DelayedDisposeRandomAccessBucket extends AbstractStorage
    implements RandomAccessBucket, DelayedDisposable {

  /** Magic number used for serialization format verification */
  public static final int MAGIC = 0xa28f2a2d;

  /** Version number for serialization format */
  public static final int VERSION = 1;

  @Serial private static final long serialVersionUID = 1L;

  private static final Logger logger =
      LoggerFactory.getLogger(DelayedDisposeRandomAccessBucket.class);

  /**
   * Creates a new DelayedDisposeRandomAccess instance.
   *
   * @param factory The tracker responsible for managing persistent files
   * @param bucket The underlying RandomAccessible bucket to be wrapped
   */
  public DelayedDisposeRandomAccessBucket(
      PersistentFileTracker factory, RandomAccessBucket bucket) {
    this.factory = factory;
    this.bucket = bucket;
    this.createdCommitID = factory.commitID();
    if (bucket == null) {
      throw new NullPointerException();
    }
  }

  /**
   * Restores a DelayedDisposeRandomAccess instance from a data stream.
   *
   * @param dis The input stream containing the serialized data
   * @param fg Generator for creating unique filenames
   * @param persistentFileTracker Tracker for managing persistent files
   * @param masterKey Master encryption key for secure operations
   * @throws StorageFormatException if the stored format is invalid
   * @throws IOException if an I/O error occurs
   * @throws ResumeFailedException if restoration fails
   */
  public DelayedDisposeRandomAccessBucket(
      DataInputStream dis,
      FilenameGenerator fg,
      PersistentFileTracker persistentFileTracker,
      MasterSecret masterKey)
      throws StorageFormatException, IOException, ResumeFailedException {
    int version = dis.readInt();
    if (version != VERSION) {
      throw new StorageFormatException("Bad version");
    }
    bucket =
        (RandomAccessBucket) BucketTools.restoreFrom(dis, fg, persistentFileTracker, masterKey);
    factory = persistentFileTracker;
  }

  @Override
  public boolean toDispose() {
    return disposed();
  }

  @Override
  public OutputStream getOutputStream() throws IOException {
    checkDisposed();
    return bucket.getOutputStream();
  }

  @Override
  public OutputStream getOutputStreamUnbuffered() throws IOException {
    checkDisposed();
    return bucket.getOutputStreamUnbuffered();
  }

  @Override
  public InputStream getInputStream() throws IOException {
    checkDisposed();
    return bucket.getInputStream();
  }

  @Override
  public InputStream getInputStreamUnbuffered() throws IOException {
    checkDisposed();
    return bucket.getInputStreamUnbuffered();
  }

  @Override
  public String getName() {
    return bucket.getName();
  }

  @Override
  public long size() {
    return bucket.size();
  }

  @Override
  public boolean isReadOnly() {
    return bucket.isReadOnly();
  }

  @Override
  public void setReadOnly() {
    bucket.setReadOnly();
  }

  /**
   * Retrieves the underlying bucket if not freed.
   *
   * @return The underlying bucket, or {@link NullBucket} if the bucket has been freed
   */
  public synchronized Bucket getUnderlying() {
    if (disposed()) {
      return new NullBucket();
    }
    return bucket;
  }

  @Override
  public void dispose() {
    if (!setDisposed()) {
      return;
    }
    close();

    logger.info("Freeing {} underlying={}", this, bucket, new Exception("debug"));
    this.factory.delayedDispose(this, createdCommitID);
  }

  @Override
  public String toString() {
    return super.toString() + ":" + bucket;
  }

  @Override
  public RandomAccessBucket createShadow() {
    return bucket.createShadow();
  }

  @Override
  public void realDispose() {
    bucket.dispose();
  }

  @Override
  public void onResume(ResumeContext context) throws ResumeFailedException {
    this.factory = context.getPersistentBucketFactory();
    bucket.onResume(context);
  }

  @Override
  public void storeTo(DataOutputStream dos) throws IOException {
    dos.writeInt(MAGIC);
    dos.writeInt(VERSION);
    bucket.storeTo(dos);
  }

  @Override
  public Rab toRandomAccessBuffer() throws IOException {
    checkDisposed();
    setReadOnly();
    return new DelayedDisposeRab(bucket.toRandomAccessBuffer(), factory);
  }

  private void checkDisposed() throws IOException {
    if (disposed()) {
      throw new IOException("Already disposed");
    }
  }

  /** The underlying bucket being wrapped */
  private final RandomAccessBucket bucket;

  /** Factory for tracking persistent files */
  // Only set on construction and on onResume() on startup. So shouldn't need locking.
  private transient PersistentFileTracker factory;

  /** Commit ID at the time of creation */
  private transient long createdCommitID;
}
