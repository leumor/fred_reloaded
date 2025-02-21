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
import hyphanet.support.io.storage.DelayedDisposable;
import hyphanet.support.io.storage.StorageFormatException;
import hyphanet.support.io.storage.bucket.Bucket;
import hyphanet.support.io.storage.bucket.BucketTools;
import hyphanet.support.io.storage.bucket.RandomAccessible;
import java.io.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link DelayedDisposeBucket} class provides a mechanism to delay the disposal of an
 * underlying {@link Bucket} resource. It implements both {@link Bucket} and {@link
 * DelayedDisposable}, ensuring that actual disposal occurs only when certain conditions are met
 * (for example, confirming data persistence).
 *
 * <p>When calling {@link #dispose()}, the resource will not be immediately freed. Instead, disposal
 * is decided by {@link PersistentFileTracker}. This allows safe cleanup, preventing premature
 * resource deallocation and potential data loss.
 *
 * <p>This class also supports migration to a {@link RandomAccessible} bucket, if necessary, and
 * avoids disposing of the resource in the migrated state.
 *
 * <p><b>Usage Note:</b> Invoking methods on an already disposed or migrated instance will throw an
 * {@link IOException} to indicate that the resource is no longer valid. Callers should ensure the
 * correct disposal life cycle.
 *
 * @see Bucket
 * @see DelayedDisposable
 * @see RandomAccessible
 */
public class DelayedDisposeBucket implements Bucket, Serializable, DelayedDisposable {

  /**
   * The magic number used to identify serialized instances of this class.
   *
   * <p>This numeric constant is written during serialization and read back to verify correct data
   * format.
   */
  public static final int MAGIC = 0x4e9c9a03;

  /**
   * The version number for the format used to serialize {@link DelayedDisposeBucket} instances.
   *
   * <p>This allows backward-compatible changes if the class structure evolves in future versions.
   */
  public static final int VERSION = 1;

  @Serial private static final long serialVersionUID = 1L;

  private static final Logger logger = LoggerFactory.getLogger(DelayedDisposeBucket.class);

  /**
   * Constructs a new {@code DelayedDispose} wrapping the given {@code Bucket}.
   *
   * @param factory The {@code PersistentFileTracker} instance managing file operations.
   * @param bucket The underlying {@code Bucket} to be managed with delayed disposal.
   */
  public DelayedDisposeBucket(PersistentFileTracker factory, Bucket bucket) {
    this.factory = factory;
    this.bucket = bucket;
    this.createdCommitID = factory.commitID();
    if (bucket == null) {
      throw new NullPointerException();
    }
  }

  /**
   * Constructs a {@link DelayedDisposeBucket} instance from serialized data. Used to restore a
   * {@link DelayedDisposeBucket} after application restart.
   *
   * @param dis The {@link DataInputStream} to read serialized data from.
   * @param fg A generator used for creating file names if needed.
   * @param persistentFileTracker Reference to the tracker managing persistent files.
   * @param masterKey The master key used for any necessary cryptographic operations.
   * @throws StorageFormatException If the serialized version does not match expected format.
   * @throws IOException If an I/O error occurs during data restoration.
   * @throws ResumeFailedException If the bucket cannot be properly resumed.
   */
  public DelayedDisposeBucket(
      DataInputStream dis,
      FilenameGenerator fg,
      PersistentFileTracker persistentFileTracker,
      MasterSecret masterKey)
      throws StorageFormatException, IOException, ResumeFailedException {
    int version = dis.readInt();
    if (version != VERSION) {
      throw new StorageFormatException("Bad version");
    }
    bucket = BucketTools.restoreFrom(dis, fg, persistentFileTracker, masterKey);
  }

  @Override
  public synchronized boolean toDispose() {
    return disposed;
  }

  @Override
  public OutputStream getOutputStream() throws IOException {
    synchronized (this) {
      if (migrated) {
        throw new IOException("Already migrated to a RandomAccessBucket");
      }
      if (disposed) {
        throw new IOException("Already freed");
      }
    }
    return bucket.getOutputStream();
  }

  @Override
  public OutputStream getOutputStreamUnbuffered() throws IOException {
    synchronized (this) {
      if (migrated) {
        throw new IOException("Already migrated to a RandomAccessBucket");
      }
      if (disposed) {
        throw new IOException("Already freed");
      }
    }
    return bucket.getOutputStreamUnbuffered();
  }

  @Override
  public InputStream getInputStream() throws IOException {
    synchronized (this) {
      if (migrated) {
        throw new IOException("Already migrated to a RandomAccessBucket");
      }
      if (disposed) {
        throw new IOException("Already freed");
      }
    }
    return bucket.getInputStream();
  }

  @Override
  public InputStream getInputStreamUnbuffered() throws IOException {
    synchronized (this) {
      if (migrated) {
        throw new IOException("Already migrated to a RandomAccessBucket");
      }
      if (disposed) {
        throw new IOException("Already freed");
      }
    }
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
   * Returns a reference to the underlying {@link Bucket} if it has not been disposed or migrated.
   *
   * @return The underlying {@link Bucket}, or {@code null} if unavailable.
   */
  public synchronized Bucket getUnderlying() {
    if (disposed) {
      return null;
    }
    if (migrated) {
      return null;
    }
    return bucket;
  }

  /**
   * Closes this {@link DelayedDisposeBucket}. Since the disposal is delayed, this method does not
   * immediately free resources unless the appropriate lifecycle step has been reached.
   */
  @Override
  public void close() {
    // Do nothing. The disposal of underlying bucket is delayed.
  }

  /**
   * Marks this {@link DelayedDisposeBucket} for disposal. Resources will remain allocated until
   * {@link #realDispose()} is called. If already disposed or migrated, this method has no effect.
   */
  @Override
  public void dispose() {
    synchronized (this) {
      if (disposed || migrated) {
        return;
      }
      disposed = true;
    }
    logger.info("Freeing {} underlying={}", this, bucket);
    this.factory.delayedDispose(this, createdCommitID);
  }

  @Override
  public String toString() {
    return super.toString() + ":" + bucket;
  }

  @Override
  public Bucket createShadow() {
    return bucket.createShadow();
  }

  @Override
  public void realDispose() {
    bucket.dispose();
  }

  /**
   * Called after an application restart to resume the state of this {@link DelayedDisposeBucket}.
   * Restores references to any needed factory or context objects.
   *
   * @param context The {@link ResumeContext} containing necessary runtime support.
   * @throws ResumeFailedException If resumption fails and this bucket cannot be reinitialized.
   */
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

  /**
   * Attempts to migrate the underlying {@link Bucket} to a {@link RandomAccessible} type. If
   * successful, further operations on this instance should be avoided, and the resulting {@link
   * RandomAccessible} will be managed by a new wrapper.
   *
   * @return A {@link RandomAccessible} if migration succeeds, or {@code null} if not possible.
   * @throws IOException If the resource is already freed (disposed).
   */
  public synchronized RandomAccessible toRandomAccessBucket() throws IOException {
    if (disposed) {
      throw new IOException("Already freed");
    }
    if (bucket instanceof RandomAccessible) {
      migrated = true;
      return new DelayedDisposeRandomAccessBucket(factory, (RandomAccessible) bucket);
      // Underlying file is already registered.
    }
    return null;
  }

  /**
   * The underlying {@code Bucket} which has its disposal delayed. All {@link Bucket} operations are
   * delegated to this field when the bucket is neither disposed nor migrated.
   */
  private final Bucket bucket;

  /**
   * Tracks the factory managing persistent file operations. Used during creation and upon resume to
   * ensure that the reference is reestablished.
   *
   * <p>Marked {@code transient} because it is not serialized. It is resolved as part of the resume
   * process.
   */
  // Only set on construction and on onResume() on startup. So shouldn't need locking.
  private transient PersistentFileTracker factory;

  /**
   * Indicates whether the underlying resource has been marked for disposal. If {@code true}, the
   * resource should not be accessed or migrated. However, actual resource cleanup only happens
   * after calling {@link #realDispose()}.
   */
  private boolean disposed;

  /**
   * Indicates whether the underlying {@link Bucket} has been migrated to a {@link RandomAccessible}
   * implementation. Once migrated, the original bucket becomes invalid for further I/O operations
   * and should not be disposed again.
   */
  private boolean migrated;

  /**
   * Records the commit ID active when this {@link DelayedDisposeBucket} was created. Used to ensure
   * consistency for delayed disposal operations.
   */
  private transient long createdCommitID;
}
