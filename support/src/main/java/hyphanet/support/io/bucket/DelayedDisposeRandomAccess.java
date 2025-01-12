/**
 * This code is part of Freenet. It is distributed under the GNU General Public License,
 * version 2 (or at your option any later version). See http://www.gnu.org/ for further details
 * of the GPL.
 */
package hyphanet.support.io.bucket;

import hyphanet.crypt.key.MasterSecret;
import hyphanet.support.io.*;
import hyphanet.support.io.randomaccessbuffer.RandomAccessBuffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;

/**
 * A wrapper class that implements delayed disposal of RandomAccessible buckets, ensuring that
 * actual resource cleanup occurs only after all necessary data has been written to persistent
 * storage. This class is particularly useful when dealing with temporary files or resources
 * that need to be maintained until specific conditions are met.
 *
 * <h2>Key Features:</h2>
 * <ul>
 *   <li>Implements delayed disposal mechanism</li>
 *   <li>Supports serialization for persistence</li>
 *   <li>Provides random access capabilities</li>
 *   <li>Maintains thread safety for critical operations</li>
 * </ul>
 *
 * @see RandomAccessible
 * @see DelayedDisposable
 * @see Bucket
 */
public class DelayedDisposeRandomAccess
    implements Bucket, Serializable, RandomAccessible, DelayedDisposable {

    /**
     * Magic number used for serialization format verification
     */
    public static final int MAGIC = 0xa28f2a2d;

    /**
     * Version number for serialization format
     */
    public static final int VERSION = 1;

    @Serial
    private static final long serialVersionUID = 1L;

    private static final Logger logger =
        LoggerFactory.getLogger(DelayedDisposeRandomAccess.class);

    /**
     * Creates a new DelayedDisposeRandomAccess instance.
     *
     * @param factory The tracker responsible for managing persistent files
     * @param bucket  The underlying RandomAccessible bucket to be wrapped
     */
    public DelayedDisposeRandomAccess(PersistentFileTracker factory, RandomAccessible bucket) {
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
     * @param dis                   The input stream containing the serialized data
     * @param fg                    Generator for creating unique filenames
     * @param persistentFileTracker Tracker for managing persistent files
     * @param masterKey             Master encryption key for secure operations
     *
     * @throws StorageFormatException if the stored format is invalid
     * @throws IOException            if an I/O error occurs
     * @throws ResumeFailedException  if restoration fails
     */
    protected DelayedDisposeRandomAccess(
        DataInputStream dis,
        FilenameGenerator fg,
        PersistentFileTracker persistentFileTracker,
        MasterSecret masterKey
    ) throws StorageFormatException, IOException, ResumeFailedException {
        int version = dis.readInt();
        if (version != VERSION) {
            throw new StorageFormatException("Bad version");
        }
        bucket = (RandomAccessible) BucketTools.restoreFrom(
            dis,
            fg,
            persistentFileTracker,
            masterKey
        );
    }

    @Override
    public boolean toDispose() {
        return disposed;
    }

    @Override
    public OutputStream getOutputStream() throws IOException {
        synchronized (this) {
            if (disposed) {
                throw new IOException("Already freed");
            }
        }
        return bucket.getOutputStream();
    }

    @Override
    public OutputStream getOutputStreamUnbuffered() throws IOException {
        synchronized (this) {
            if (disposed) {
                throw new IOException("Already freed");
            }
        }
        return bucket.getOutputStreamUnbuffered();
    }

    @Override
    public InputStream getInputStream() throws IOException {
        synchronized (this) {
            if (disposed) {
                throw new IOException("Already freed");
            }
        }
        return bucket.getInputStream();
    }

    @Override
    public InputStream getInputStreamUnbuffered() throws IOException {
        synchronized (this) {
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
     * Retrieves the underlying bucket if not freed.
     *
     * @return The underlying bucket, or null if the bucket has been freed
     */
    public synchronized Bucket getUnderlying() {
        if (disposed) {
            return null;
        }
        return bucket;
    }

    @Override
    public void close() {
        // Do nothing. The disposal of underlying bucket is delayed.
    }

    @Override
    public void dispose() {
        synchronized (this) {
            if (disposed) {
                return;
            }
            disposed = true;
        }
        logger.info("Freeing {} underlying={}", this, bucket, new Exception("debug"));
        this.factory.delayedDispose(this, createdCommitID);
    }

    @Override
    public String toString() {
        return super.toString() + ":" + bucket;
    }

    @Override
    public RandomAccessible createShadow() {
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
    public RandomAccessBuffer toRandomAccessBuffer() throws IOException {
        synchronized (this) {
            if (disposed) {
                throw new IOException("Already freed");
            }
        }
        setReadOnly();
        return new hyphanet.support.io.randomaccessbuffer.DelayedDispose(
            bucket.toRandomAccessBuffer(),
                                                                         factory
        );
    }

    /**
     * The underlying bucket being wrapped
     */
    private final RandomAccessible bucket;

    /**
     * Factory for tracking persistent files
     */
    // Only set on construction and on onResume() on startup. So shouldn't need locking.
    private transient PersistentFileTracker factory;

    /**
     * Flag indicating whether this bucket has been disposed
     */
    private boolean disposed;

    /**
     * Commit ID at the time of creation
     */
    private transient long createdCommitID;

}