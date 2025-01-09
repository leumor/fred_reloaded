package hyphanet.support.io.randomaccessbuffer;

import hyphanet.support.io.*;
import hyphanet.support.io.util.FileSystem;
import org.apache.commons.lang3.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Iterator;
import java.util.LinkedHashSet;


/**
 * A thread-safe implementation of {@link RandomAccessBuffer} that manages a pool of file
 * descriptors for efficient file access. This class provides random access to files while
 * limiting the number of simultaneously open file descriptors through a pooling mechanism.
 *
 * <p>The pooling mechanism helps prevent reaching system limits on open file descriptors by
 * automatically closing least recently used files when necessary. Files are reopened on demand
 * when accessed.</p>
 *
 * <p><b>Thread Safety:</b> This class is thread-safe. All file operations are synchronized
 * to prevent concurrent access issues. The file descriptor pool is also synchronized.</p>
 *
 * <p><b>Usage Example:</b></p>
 * <pre>
 * Path filePath = Paths.get("example.dat");
 * try (PooledFile file = new PooledFile(filePath, false, 1024, new Random(), -1, true)) {
 *     byte[] data = new byte[100];
 *     file.pread(0, data, 0, data.length);
 * }
 * </pre>
 *
 * @see RandomAccessBuffer
 */
public class PooledFile implements RandomAccessBuffer, Serializable {

    /**
     * Magic number for serialization validation
     */
    public static final int MAGIC = 0x297c550a;

    /**
     * Version number for serialization format
     */
    public static final int VERSION = 1;

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Default file descriptor tracker with a pool size of 100
     */
    private static final FdTracker DEFAULT_FDTRACKER = new FdTracker(100);

    private static final Logger logger = LoggerFactory.getLogger(PooledFile.class);

    /**
     * Creates a new PooledFile instance with the specified parameters.
     *
     * @param path             The path to the file
     * @param readOnly         Whether the file should be opened in read-only mode
     * @param forceLength      The required length of the file, or -1 if no specific length is
     *                         required
     * @param persistentTempID The temporary file ID for persistence, or -1
     * @param deleteOnFree     Whether to delete the file when freed
     *
     * @throws IOException If file operations fail
     */
    public PooledFile(
        Path path,
        boolean readOnly,
        long forceLength,
        long persistentTempID,
        boolean deleteOnFree
    ) throws IOException {
        this(path, readOnly, forceLength, persistentTempID, deleteOnFree, DEFAULT_FDTRACKER);
    }

    /**
     * Creates a new PooledFile with initial contents.
     *
     * @param path             The path to create the file at
     * @param initialContents  The initial contents to write
     * @param offset           The offset in initialContents to start from
     * @param size             The number of bytes to write
     * @param persistentTempID The temporary file ID for persistence
     * @param deleteOnFree     Whether to delete the file when freed
     * @param readOnly         Whether the file should be opened in read-only mode
     *
     * @throws IOException If file operations fail
     */
    public PooledFile(
        Path path,
        byte[] initialContents,
        int offset,
        int size,
        long persistentTempID,
        boolean deleteOnFree,
        boolean readOnly
    ) throws IOException {
        this.path = path;
        this.readOnly = readOnly;
        this.length = size;
        this.persistentTempID = persistentTempID;
        this.deleteOnFree = deleteOnFree;
        this.fds = DEFAULT_FDTRACKER;
        lockLevel = 0;
        RabLock lock = lockOpen(true);
        try {
            var byteBuffer = ByteBuffer.wrap(initialContents, offset, size);
            while (byteBuffer.remaining() > 0) {
                //noinspection ResultOfMethodCallIgnored
                channel.write(byteBuffer, 0);
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Constructor used during resumption of stored files.
     *
     * @param dis                   Data input stream containing serialized data
     * @param fg                    Filename generator for temporary files
     * @param persistentFileTracker Tracker for persistent files
     *
     * @throws StorageFormatException If the stored format is invalid
     * @throws IOException            If I/O operations fail
     * @throws ResumeFailedException  If resumption fails
     */
    public PooledFile(
        DataInputStream dis,
        FilenameGenerator fg,
        PersistentFileTracker persistentFileTracker
    ) throws StorageFormatException, IOException, ResumeFailedException {
        int version = dis.readInt();
        if (version != VERSION) {
            throw new StorageFormatException("Bad version");
        }
        var tmpPath = Path.of(dis.readUTF());
        readOnly = dis.readBoolean();
        length = dis.readLong();
        persistentTempID = dis.readLong();
        deleteOnFree = dis.readBoolean();
        secureDelete = deleteOnFree && dis.readBoolean();
        fds = DEFAULT_FDTRACKER;
        if (length < 0) {
            throw new StorageFormatException("Bad length");
        }
        if (persistentTempID != -1) {
            // File must exist!
            if (!Files.exists(tmpPath)) {
                // Maybe moved after the last checkpoint?
                tmpPath = fg.getPath(persistentTempID);
                if (Files.exists(tmpPath)) {
                    persistentFileTracker.register(tmpPath);
                    this.path = tmpPath;
                    return;
                }
            }
            this.path = fg.maybeMove(tmpPath, persistentTempID);
            if (!Files.exists(tmpPath)) {
                throw new ResumeFailedException("Persistent tempfile lost " + tmpPath);
            }
        } else {
            this.path = tmpPath;
            if (!Files.exists(tmpPath)) {
                throw new ResumeFailedException("Lost file " + tmpPath);
            }
        }
    }

    /**
     * Creates a new PooledFile with a custom FdTracker.
     *
     * @param path             The file path
     * @param readOnly         Whether the file is read-only
     * @param forceLength      Required length or -1
     * @param persistentTempID Temporary file ID
     * @param deleteOnFree     Whether to delete on free
     * @param fds              Custom file descriptor tracker
     *
     * @throws IOException If file operations fail
     */
    PooledFile(
        Path path,
        boolean readOnly,
        long forceLength,
        long persistentTempID,
        boolean deleteOnFree,
        FdTracker fds
    ) throws IOException {
        this.path = path;
        this.readOnly = readOnly;
        this.persistentTempID = persistentTempID;
        this.deleteOnFree = deleteOnFree;
        this.fds = fds;
        lockLevel = 0;
        // Check the parameters and get the length.
        // Also, unlock() adds to the closeables queue, which is essential.
        RabLock lock = lockOpen();
        try {
            long currentLength = channel.size();
            if (forceLength >= 0 && forceLength != currentLength) {
                if (readOnly) {
                    throw new IOException("Read only but wrong length");
                }
                // Preallocate space. We want predictable disk usage, not minimal disk
                // usage, especially for downloads.
                // TODO: Check if we need WrapperKeepalive here
                //                try (WrapperKeepalive wrapperKeepalive = new
                //                WrapperKeepalive()) {
                //                    wrapperKeepalive.start();
                // freenet-mobile-changed: Passing file descriptor to avoid using
                // reflection
                try (var fis = new FileInputStream(path.toFile())) {
                    FileDescriptor fd = fis.getFD();
                    Fallocate.forChannel(channel, fd, forceLength)
                             .fromOffset(currentLength)
                             .execute();
                }
                //                }
                currentLength = forceLength;
            }
            this.length = currentLength;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Protected constructor for serialization.
     */
    protected PooledFile() {
        // For serialization.
        readOnly = false;
        length = 0;
        persistentTempID = -1;
        deleteOnFree = false;
        // use the default fdtracker to avoid having one fd tracker per P F R A Buffer
        fds = DEFAULT_FDTRACKER;
    }

    /**
     * Gets the size of this file in bytes.
     *
     * @return the total size of the file in bytes
     */
    @Override
    public long size() {
        return length;
    }

    /**
     * Reads data from the file at the specified position.
     *
     * <p>This method ensures thread safety through synchronization and proper
     * file channel management.</p>
     *
     * @param fileOffset position in the file to read from
     * @param buf        buffer to store the read data
     * @param bufOffset  starting position in the buffer
     * @param readLength number of bytes to read
     *
     * @throws IOException               if an I/O error occurs
     * @throws IllegalArgumentException  if fileOffset is negative
     * @throws IndexOutOfBoundsException if buffer parameters are invalid or read extends past
     *                                   EOF
     */
    @Override
    public void pread(long fileOffset, byte[] buf, int bufOffset, int readLength)
        throws IOException {
        if (fileOffset < 0) {
            throw new IllegalArgumentException("fileOffset cannot be negative");
        }
        if (bufOffset < 0 || readLength < 0 || bufOffset + readLength > buf.length) {
            throw new IndexOutOfBoundsException("Invalid buffer parameters");
        }
        if (fileOffset + readLength > this.length) {
            throw new IndexOutOfBoundsException("Read past end of file");
        }

        RabLock lock = lockOpen();
        try {
            // FIXME: If two PooledFile's are reading from and writing to the same file, the
            //  data might be corrupted, as they're using different channels. The synchronized
            //  block below only synchronizes on the same channel object.
            var byteBuffer = ByteBuffer.wrap(buf, bufOffset, readLength);
            while (byteBuffer.hasRemaining()) {
                var bytesRead = channel.read(byteBuffer, fileOffset + byteBuffer.position());
                if (bytesRead < 0) {
                    throw new IOException("Unexpected end of file");
                }
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Writes data to the file at the specified position.
     *
     * <p>This method ensures thread safety through synchronization and proper
     * file channel management.</p>
     *
     * @param fileOffset  position in the file to write to
     * @param buf         buffer containing the data to write
     * @param bufOffset   starting position in the buffer
     * @param writeLength number of bytes to write
     *
     * @throws IOException               if an I/O error occurs or file is read-only
     * @throws IllegalArgumentException  if fileOffset is negative
     * @throws IndexOutOfBoundsException if buffer parameters are invalid or write extends past
     *                                   EOF
     */
    @Override
    public void pwrite(long fileOffset, byte[] buf, int bufOffset, int writeLength)
        throws IOException {
        if (fileOffset < 0) {
            throw new IllegalArgumentException();
        }
        if (readOnly) {
            throw new IOException("Read only");
        }
        if (bufOffset < 0 || writeLength < 0 || bufOffset + writeLength > buf.length) {
            throw new IndexOutOfBoundsException("Invalid buffer parameters");
        }
        if (fileOffset + writeLength > this.length) {
            throw new IOException("Write past end of file");
        }

        RabLock lock = lockOpen();
        try {
            // FIXME: If two PooledFile's are reading from and writing to the same file, the
            //  data might be corrupted, as they're using different channels. The synchronized
            //  block below only synchronizes on the same channel object.
            var byteBuffer = ByteBuffer.wrap(buf, bufOffset, writeLength);
            while (byteBuffer.hasRemaining()) {
                var bytesWritten = channel.write(
                    byteBuffer,
                    fileOffset + byteBuffer.position()
                );
                if (bytesWritten == 0) {
                    throw new IOException("Failed to write to file. Should not happen.");
                }
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Closes this file and releases associated resources.
     *
     * <p>This method ensures proper cleanup of resources by:</p>
     * <ul>
     *   <li>Checking lock status</li>
     *   <li>Removing from the closables collection</li>
     *   <li>Closing the channel</li>
     * </ul>
     *
     * <p>Multiple calls to close() are safe and will not throw exceptions.</p>
     *
     * @throws IllegalStateException if the file is still locked
     */
    @Override
    public void close() {
        logger.info("Closing {}", this);
        synchronized (fds) {
            if (lockLevel != 0) {
                throw new IllegalStateException("Must unlock first!");
            }
            if (closed) {
                return; // Avoid double closing
            }
            closed = true;
            // Essential to avoid memory leak!
            // Potentially slow but only happens on close(). Plus the size of closables is
            // bounded anyway by the fd limit.
            fds.closables.remove(this);
            closeChannel();
        }
    }

    @Override
    public RabLock lockOpen() throws IOException {
        return lockOpen(false);
    }

    /**
     * Sets whether this file should be securely deleted when disposed.
     *
     * @param secureDelete true to enable secure deletion, false for normal deletion
     */
    public void setSecureDelete(boolean secureDelete) {
        this.secureDelete = secureDelete;
    }

    /**
     * Disposes of this file, optionally deleting it from disk.
     *
     * <p>If deleteOnFree is true, the file will be deleted using either secure
     * or normal deletion based on the secureDelete setting.</p>
     */
    @Override
    public void dispose() {
        close();
        if (!deleteOnFree) {
            return;
        }
        try {
            if (secureDelete) {
                FileSystem.secureDelete(path);
            } else {
                Files.delete(path);
            }
        } catch (IOException e) {
            logger.error("Unable to delete temporary file {} : {}", path, e.getMessage());
        }
    }

    /**
     * Validates and restores the file state after deserialization.
     *
     * @param context the resume context containing restoration information
     *
     * @throws ResumeFailedException if the file cannot be properly restored
     */
    @Override
    public void onResume(ResumeContext context) throws ResumeFailedException {
        if (!Files.exists(path)) {
            throw new ResumeFailedException("File does not exist: " + path);
        }
        try {
            if (length > Files.size(path)) {
                throw new ResumeFailedException("Bad length");
            }
        } catch (IOException e) {
            throw new ResumeFailedException("Bad length");
        }
        if (persistentTempID != -1) {
            context.getPersistentFileTracker().register(path);
        }
    }

    public String toString() {
        return super.toString() + ":" + path;
    }

    /**
     * {@inheritDoc}
     * <p>
     * Stores the file state for serialization.
     *
     * @param dos the output stream to write the state to
     *
     * @throws IOException if writing fails
     */
    @Override
    public void storeTo(DataOutputStream dos) throws IOException {
        dos.writeInt(MAGIC);
        dos.writeInt(VERSION);
        dos.writeUTF(path.toString());
        dos.writeBoolean(readOnly);
        dos.writeLong(length);
        dos.writeLong(persistentTempID);
        dos.writeBoolean(deleteOnFree);
        if (deleteOnFree) {
            dos.writeBoolean(secureDelete);
        }
    }

    /**
     * Generates a hash code for this file based on its properties.
     *
     * @return a hash code value for this file
     */
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (deleteOnFree ? 1231 : 1237);
        result = prime * result + ((path == null) ? 0 : path.hashCode());
        result = prime * result + Long.hashCode(length);
        result = prime * result + Long.hashCode(persistentTempID);
        result = prime * result + (readOnly ? 1231 : 1237);
        result = prime * result + (secureDelete ? 1231 : 1237);
        return result;
    }

    /**
     * Compares this file with another object for equality.
     *
     * <p>Two PooledFile instances are considered equal if they represent
     * the same file on disk with identical properties.</p>
     *
     * @param obj the object to compare with
     *
     * @return true if the objects are equal, false otherwise
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof PooledFile other)) {
            return false;
        }
        return deleteOnFree == other.deleteOnFree && path.equals(other.path) &&
               length == other.length && persistentTempID == other.persistentTempID &&
               readOnly == other.readOnly && secureDelete == other.secureDelete;
    }

    /**
     * Checks if this file is currently open.
     *
     * @return true if the file channel is open, false otherwise
     */
    boolean isOpen() {
        synchronized (fds) {
            return channel != null;
        }
    }

    /**
     * Checks if this file is currently locked.
     *
     * @return true if the lock level is greater than 0, false otherwise
     *
     * @see #lockOpen()
     */
    boolean isLocked() {
        synchronized (fds) {
            return lockLevel != 0;
        }
    }

    /**
     * Closes the underlying file channel. This method is exposed for testing purposes only.
     *
     * <p>The file must not be locked when calling this method.</p>
     *
     * @throws IllegalStateException if the file is currently locked
     */
    protected void closeChannel() {
        synchronized (fds) {
            if (lockLevel != 0) {
                throw new IllegalStateException();
            }
            if (channel == null) {
                return;
            }
            try {
                channel.close();
            } catch (IOException e) {
                logger.error("Error closing {} : {}", this, e, e);
            } finally {
                channel = null;
                fds.totalOpenFDs--;
            }
        }
    }

    /**
     * Acquires a file lock on the file to keep it open and prevent closure by the pool.
     *
     * <p>This lock isn't a concurrency lock for the file contents (that's handled by
     * synchronization within {@link #pread} and {@link #pwrite}), but rather a lock to control
     * the opening and closing of the underlying {@link FileChannel}.</p>
     *
     * <p><b>Important:</b> The caller MUST call {@code unlock()} on the returned lock
     * when finished to prevent resource leaks and allow proper pool management.</p>
     *
     * <p>If the pool has reached its maximum open files limit, this method will block
     * until a file descriptor becomes available.</p>
     *
     * @param forceWrite Whether to force write access even for read-only files
     *
     * @return A {@link RabLock} instance that must be unlocked when operations are complete
     *
     * @throws IOException If the file cannot be opened or locked
     */
    private RabLock lockOpen(boolean forceWrite) throws IOException {
        RabLock lock = new RabLock() {
            @Override
            protected void innerUnlock() {
                PooledFile.this.unlock();
            }
        };
        synchronized (fds) {
            while (true) {
                fds.closables.remove(this);
                if (closed) {
                    throw new IOException("Already closed " + this);
                }
                if (channel != null) {
                    lockLevel++; // Already open, may or may not be already locked.
                    return lock;
                } else if (fds.totalOpenFDs < fds.maxOpenFDs) {
                    StandardOpenOption[] openOptions = {StandardOpenOption.READ};
                    if (!readOnly || forceWrite) {
                        openOptions = ArrayUtils.add(openOptions, StandardOpenOption.WRITE);
                    }
                    channel = FileChannel.open(path, openOptions);
                    lockLevel++;
                    fds.totalOpenFDs++;
                    return lock;
                }

                PooledFile closable = pollFirstClosable();
                if (closable != null) {
                    closable.closeChannel();
                    continue;
                }

                try {
                    fds.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    /**
     * Returns the first closeable file from the pool.
     *
     * <p>This method helps manage the pool of open file descriptors by identifying
     * the least recently used file that can be closed.</p>
     *
     * @return the first PooledFile that can be closed, or null if none available
     */
    private PooledFile pollFirstClosable() {
        synchronized (fds) {
            Iterator<PooledFile> it = fds.closables.iterator();
            if (it.hasNext()) {
                PooledFile first = it.next();
                it.remove();
                return first;
            }
            return null;
        }
    }

    /**
     * Releases the lock on this file, allowing it to be closed by the pool if no other locks
     * are held.
     *
     * <p>This method decrements the lock level and updates the file's position
     * in the closables collection for pool management.</p>
     */
    private void unlock() {
        synchronized (fds) {
            lockLevel--;
            if (lockLevel == 0) {
                fds.closables.add(this);
                fds.notifyAll();
            }
        }
    }

    @Serial
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        path = Paths.get(in.readUTF());
    }

    @Serial
    private void writeObject(ObjectOutputStream out) throws IOException {
        out.defaultWriteObject();
        out.writeUTF(path.toString());
    }

    /**
     * Tracks the number of open file descriptors and manages the pool of files.
     *
     * <p>This class maintains:</p>
     * <ul>
     *   <li>The total number of currently open file descriptors</li>
     *   <li>The maximum allowed open file descriptors</li>
     *   <li>A collection of closeable files ordered by last access</li>
     * </ul>
     */
    static class FdTracker implements Serializable {
        /**
         * Creates a new FdTracker with the specified limit.
         *
         * @param maxOpenFDs maximum number of open file descriptors allowed
         */
        FdTracker(int maxOpenFDs) {
            this.maxOpenFDs = maxOpenFDs;
        }

        /**
         * Set the size of the fd pool
         */
        synchronized void setMaxFDs(int max) {
            if (max <= 0) {
                throw new IllegalArgumentException();
            }
            maxOpenFDs = max;
            notifyAll();
        }

        /**
         * How many fd's are open right now? Mainly for tests but also for stats.
         */
        synchronized int getOpenFDs() {
            return totalOpenFDs;
        }

        synchronized int getClosableFDs() {
            return closables.size();
        }

        /**
         * Collection of files that can be closed, ordered by last access time
         */
        private final LinkedHashSet<PooledFile> closables = new LinkedHashSet<>();

        /**
         * Maximum number of file descriptors allowed to be open simultaneously
         */
        private int maxOpenFDs;

        /**
         * Current count of open file descriptors
         */
        private int totalOpenFDs = 0;
    }

    /**
     * Whether this file is read-only
     */
    private final boolean readOnly;

    /**
     * The length of the file in bytes
     */
    private final long length;

    /**
     * The persistent temporary file ID, or -1 if not persistent
     */
    private final long persistentTempID;

    /**
     * Whether to delete the file when the random access buffer is closed
     */
    private final boolean deleteOnFree;

    /**
     * The file descriptor tracker managing this file
     */
    private final transient FdTracker fds;

    /**
     * The path to the file on disk
     */
    private transient Path path;

    /**
     * Current lock level - number of active locks. > 0 means locked. Synchronized on
     * {@link #fds}.
     */
    private int lockLevel;

    /**
     * The underlying file channel for I/O operations
     */
    private transient FileChannel channel;

    /**
     * Whether this random access buffer has been closed
     */
    private boolean closed;

    /**
     * Whether to use secure deletion when freeing the file
     */
    private boolean secureDelete;

}
