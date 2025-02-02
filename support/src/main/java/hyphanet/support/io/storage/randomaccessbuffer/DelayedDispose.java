package hyphanet.support.io.storage.randomaccessbuffer;

import hyphanet.crypt.key.MasterSecret;
import hyphanet.support.io.FilenameGenerator;
import hyphanet.support.io.PersistentFileTracker;
import hyphanet.support.io.ResumeContext;
import hyphanet.support.io.ResumeFailedException;
import hyphanet.support.io.storage.DelayedDisposable;
import hyphanet.support.io.storage.StorageFormatException;
import hyphanet.support.io.storage.bucket.BucketTools;

import java.io.*;

/**
 * Implements delayed disposal functionality for RandomAccessBuffer instances.
 * <p>
 * This class wraps a RandomAccessBuffer and provides delayed disposal capabilities, ensuring
 * that the underlying resource is not disposed until explicitly requested through the
 * appropriate mechanisms. It maintains thread safety through synchronization and supports
 * serialization for persistence.
 * </p>
 *
 * @see RandomAccessBuffer
 * @see DelayedDisposable
 */
public class DelayedDispose implements RandomAccessBuffer, Serializable, DelayedDisposable {

    /**
     * Magic number for serialization verification
     */
    public static final int MAGIC = 0x3fb645de;

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Creates a new DelayedDispose wrapper for a RandomAccessBuffer.
     *
     * @param rab     the RandomAccessBuffer to wrap
     * @param factory the PersistentFileTracker managing this buffer
     */
    public DelayedDispose(RandomAccessBuffer rab, PersistentFileTracker factory) {
        underlying = rab;
        this.createdCommitID = factory.commitID();
        this.factory = factory;
    }

    /**
     * Reconstructs a DelayedDispose instance from serialized data.
     *
     * @param dis                   the input stream containing serialized data
     * @param fg                    the filename generator for temporary files
     * @param persistentFileTracker the tracker managing persistent files
     * @param masterSecret          the master secret for encryption
     *
     * @throws IOException            if an I/O error occurs
     * @throws StorageFormatException if the stored format is invalid
     * @throws ResumeFailedException  if resumption fails
     */
    public DelayedDispose(
        DataInputStream dis,
        FilenameGenerator fg,
        PersistentFileTracker persistentFileTracker,
        MasterSecret masterSecret
    ) throws IOException, StorageFormatException, ResumeFailedException {
        underlying = BucketTools.restoreRabFrom(dis, fg, persistentFileTracker, masterSecret);
        factory = persistentFileTracker;
    }

    @Override
    public long size() {
        return underlying.size();
    }

    /**
     * {@inheritDoc}
     *
     * @throws IOException if the buffer has been disposed or if an I/O error occurs
     */
    @Override
    public void pread(long fileOffset, byte[] buf, int bufOffset, int length)
        throws IOException {
        synchronized (this) {
            if (disposed) {
                throw new IOException("Already disposed");
            }
        }
        underlying.pread(fileOffset, buf, bufOffset, length);
    }

    /**
     * {@inheritDoc}
     *
     * @throws IOException if the buffer has been disposed or if an I/O error occurs
     */
    @Override
    public void pwrite(long fileOffset, byte[] buf, int bufOffset, int length)
        throws IOException {
        synchronized (this) {
            if (disposed) {
                throw new IOException("Already disposed");
            }
        }
        underlying.pwrite(fileOffset, buf, bufOffset, length);
    }

    @Override
    public void close() {
        synchronized (this) {
            if (disposed) {
                return;
            }
        }
        underlying.close();
    }

    @Override
    public void dispose() {
        synchronized (this) {
            if (disposed) {
                return;
            }
            disposed = true;
        }
        this.factory.delayedDispose(this, createdCommitID);
    }

    /**
     * {@inheritDoc}
     *
     * @throws IOException if the buffer has been disposed or if an I/O error occurs
     */
    @Override
    public RabLock lockOpen() throws IOException {
        synchronized (this) {
            if (disposed) {
                throw new IOException("Already disposed");
            }
        }
        return underlying.lockOpen();
    }

    @Override
    public void onResume(ResumeContext context) throws ResumeFailedException {
        this.factory = context.getPersistentTempBucketFactory();
        underlying.onResume(context);
    }

    @Override
    public void storeTo(DataOutputStream dos) throws IOException {
        dos.writeInt(MAGIC);
        underlying.storeTo(dos);
    }

    @Override
    public boolean toDispose() {
        return disposed;
    }

    /**
     * Retrieves the underlying RandomAccessBuffer.
     *
     * @return the underlying buffer, or {@code null} if disposed
     */
    public RandomAccessBuffer getUnderlying() {
        if (disposed) {
            return null;
        }
        return underlying;
    }

    @Override
    public void realDispose() {
        underlying.dispose();
    }

    @Override
    public int hashCode() {
        return underlying.hashCode();
    }

    /**
     * {@inheritDoc}
     * <p>
     * Two DelayedDispose Random Access Buffer instances are considered equal if they wrap the
     * same underlying RandomAccessBuffer. This is particularly important during resume
     * operations where multiple wrappers might exist for the same underlying buffer.
     * </p>
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        DelayedDispose other = (DelayedDispose) obj;
        return underlying.equals(other.underlying);
    }

    /**
     * The underlying RandomAccessBuffer being wrapped
     *
     * @see RandomAccessBuffer
     */
    final RandomAccessBuffer underlying;

    /**
     * Flag indicating whether this buffer has been marked for disposal
     */
    private boolean disposed;

    /**
     * The file tracker managing this buffer's lifecycle
     */
    private transient PersistentFileTracker factory;

    /**
     * The commit ID at the time this buffer was created
     */
    private transient long createdCommitID;

}
