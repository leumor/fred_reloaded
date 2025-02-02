package hyphanet.support.io.storage.randomaccessbuffer;

import hyphanet.crypt.key.MasterSecret;
import hyphanet.support.io.FilenameGenerator;
import hyphanet.support.io.PersistentFileTracker;
import hyphanet.support.io.ResumeContext;
import hyphanet.support.io.ResumeFailedException;
import hyphanet.support.io.storage.StorageFormatException;
import hyphanet.support.io.storage.bucket.BucketTools;

import java.io.*;
import java.util.Objects;


/**
 * A wrapper class that provides a padded view of a RandomAccessBuffer with a specified real
 * size. This class ensures that operations cannot exceed the real size limit, even if the
 * underlying buffer is larger.
 *
 * <h2>Usage</h2>
 * This class is particularly useful when you need to:
 * <ul>
 *   <li>Limit access to a specific portion of a larger buffer</li>
 *   <li>Ensure operations don't exceed a predetermined size</li>
 *   <li>Maintain size constraints during serialization/deserialization</li>
 * </ul>
 *
 * @see RandomAccessBuffer
 */
public class Padded implements RandomAccessBuffer, Serializable {

    /**
     * Magic number used for serialization verification
     */
    public static final int MAGIC = 0x1eaaf330;

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Creates a new padded buffer with a specified size limit.
     *
     * @param rab      the underlying RandomAccessBuffer to wrap
     * @param realSize the actual size limit for this buffer
     *
     * @throws IllegalArgumentException if realSize is negative or exceeds the underlying
     *                                  buffer size
     */
    public Padded(RandomAccessBuffer rab, long realSize) {
        this.rab = rab;
        if (realSize < 0) {
            throw new IllegalArgumentException("Real size cannot be negative: " + realSize);
        }
        if (realSize > rab.size()) {
            throw new IllegalArgumentException(("Real size %d exceeds underlying buffer size" +
                                                " " + "%d").formatted(realSize, rab.size()));
        }
        this.realSize = realSize;
    }

    /**
     * Restores a padded buffer from a serialized form.
     *
     * @param dis                   the input stream containing the serialized data
     * @param fg                    generator for temporary filenames
     * @param persistentFileTracker tracker for persistent file management
     * @param masterSecret          master secret for cryptographic operations
     *
     * @throws ResumeFailedException  if the restoration cannot be completed
     * @throws IOException            if an I/O error occurs
     * @throws StorageFormatException if the serialized format is invalid
     */
    public Padded(
        DataInputStream dis,
        FilenameGenerator fg,
        PersistentFileTracker persistentFileTracker,
        MasterSecret masterSecret
    ) throws ResumeFailedException, IOException, StorageFormatException {
        realSize = dis.readLong();
        if (realSize < 0) {
            throw new StorageFormatException("Invalid negative length: " + realSize);
        }
        rab = BucketTools.restoreRabFrom(dis, fg, persistentFileTracker, masterSecret);
        if (realSize > rab.size()) {
            throw new ResumeFailedException(
                "Padded file size %d is smaller than expected length %d".formatted(
                    rab.size(),
                    realSize
                ));
        }
    }

    /**
     * Returns the real size of this padded buffer.
     *
     * @return the size limit of this buffer
     */
    @Override
    public long size() {
        return realSize;
    }

    /**
     * Reads data from the padded buffer, ensuring operations don't exceed the real size.
     *
     * @param fileOffset position in the buffer to read from
     * @param buf        destination array for the read data
     * @param bufOffset  starting position in the destination array
     * @param length     number of bytes to read
     *
     * @throws IOException              if the read operation fails or exceeds the size limit
     * @throws IllegalArgumentException if fileOffset is negative
     */
    @Override
    public void pread(long fileOffset, byte[] buf, int bufOffset, int length)
        throws IOException {
        if (fileOffset < 0) {
            throw new IllegalArgumentException("Negative file offset: " + fileOffset);
        }
        if (fileOffset + length > realSize) {
            throw new IOException(
                "Read operation exceeds size limit: offset=%d, length=%d, limit=%d".formatted(
                    fileOffset,
                    length,
                    realSize
                ));
        }
        rab.pread(fileOffset, buf, bufOffset, length);
    }

    /**
     * Writes data to the padded buffer, ensuring operations don't exceed the real size.
     *
     * @param fileOffset position in the buffer to write to
     * @param buf        source array containing the data
     * @param bufOffset  starting position in the source array
     * @param length     number of bytes to write
     *
     * @throws IOException              if the write operation fails or exceeds the size limit
     * @throws IllegalArgumentException if fileOffset is negative
     */
    @Override
    public void pwrite(long fileOffset, byte[] buf, int bufOffset, int length)
        throws IOException {
        if (fileOffset < 0) {
            throw new IllegalArgumentException("Negative file offset: " + fileOffset);
        }
        if (fileOffset + length > realSize) {
            throw new IOException(
                "Write operation exceeds size limit: offset=%d, length=%d, limit=%d".formatted(
                    fileOffset,
                    length,
                    realSize
                ));
        }
        rab.pwrite(fileOffset, buf, bufOffset, length);
    }

    /**
     * Closes the underlying buffer.
     */
    @Override
    public void close() {
        rab.close();
    }

    /**
     * Disposes of the underlying buffer's resources.
     */
    @Override
    public void dispose() {
        rab.dispose();
    }

    /**
     * Acquires a lock on the underlying buffer.
     *
     * @return a lock object representing the acquired lock
     *
     * @throws IOException if the lock cannot be acquired
     */
    @Override
    public RabLock lockOpen() throws IOException {
        return rab.lockOpen();
    }

    @Override
    public void onResume(ResumeContext context) throws ResumeFailedException {
        rab.onResume(context);
    }

    @Override
    public void storeTo(DataOutputStream dos) throws IOException {
        dos.writeInt(MAGIC);
        dos.writeLong(realSize);
        rab.storeTo(dos);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + rab.hashCode();
        result = prime * result + Long.hashCode(realSize);
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof Padded other) {
            return realSize == other.realSize && Objects.equals(rab, other.rab);
        }
        return false;
    }

    /**
     * The underlying RandomAccessBuffer being wrapped
     */
    final RandomAccessBuffer rab;

    /**
     * The actual size limit for this padded buffer
     */
    final long realSize;

}
