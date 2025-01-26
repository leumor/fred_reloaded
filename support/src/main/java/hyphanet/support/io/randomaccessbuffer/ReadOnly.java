package hyphanet.support.io.randomaccessbuffer;

import hyphanet.crypt.key.MasterSecret;
import hyphanet.support.io.*;
import hyphanet.support.io.bucket.BucketTools;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * A decorator class that provides read-only access to an underlying
 * {@link RandomAccessBuffer}. This class implements the decorator pattern to prevent write
 * operations on the wrapped buffer. Any attempt to write to this buffer will result in an
 * {@link IOException}.
 *
 * @see RandomAccessBuffer
 */
public class ReadOnly implements RandomAccessBuffer {

    /**
     * The magic number used for serialization identification. This value is written to the
     * output stream during serialization and verified during deserialization.
     */
    public final static int MAGIC = 0x648d24da;

    /**
     * Creates a read-only wrapper around an existing {@link RandomAccessBuffer}.
     *
     * @param underlying the buffer to be wrapped in a read-only interface
     */
    public ReadOnly(RandomAccessBuffer underlying) {
        this.underlying = underlying;
    }

    /**
     * Constructs a read-only buffer by deserializing from an input stream. The magic number
     * should have been read by the caller before invoking this constructor.
     *
     * @param dis                   the input stream containing the serialized buffer data
     * @param fg                    the filename generator for creating temporary files
     * @param persistentFileTracker tracks persistent files in the system
     * @param masterSecret          the master secret for cryptographic operations
     *
     * @throws IOException            if there's an error reading from the stream
     * @throws StorageFormatException if the stored data format is invalid
     * @throws ResumeFailedException  if the buffer cannot be properly restored
     */
    public ReadOnly(
        DataInputStream dis,
        FilenameGenerator fg,
        PersistentFileTracker persistentFileTracker,
        MasterSecret masterSecret
    ) throws IOException, StorageFormatException, ResumeFailedException {
        // Caller has already read magic
        this.underlying = BucketTools.restoreRabFrom(
            dis,
            fg,
            persistentFileTracker,
            masterSecret
        );
    }

    @Override
    public long size() {
        return underlying.size();
    }

    @Override
    public void pread(long fileOffset, byte[] buf, int bufOffset, int length)
        throws IOException {
        underlying.pread(fileOffset, buf, bufOffset, length);
    }

    /**
     * {@inheritDoc}
     *
     * @throws IOException always, as this is a read-only buffer
     */
    @Override
    public void pwrite(long fileOffset, byte[] buf, int bufOffset, int length)
        throws IOException {
        throw new IOException("Read only");
    }

    @Override
    public void close() {
        underlying.close();
    }

    @Override
    public void dispose() {
        underlying.dispose();
    }

    @Override
    public RabLock lockOpen() throws IOException {
        return underlying.lockOpen();
    }

    @Override
    public void onResume(ResumeContext context) throws ResumeFailedException {
        underlying.onResume(context);
    }

    /**
     * {@inheritDoc} Writes the magic number followed by the underlying buffer's serialized
     * state.
     */
    @Override
    public void storeTo(DataOutputStream dos) throws IOException {
        dos.writeInt(MAGIC);
        underlying.storeTo(dos);
    }

    /**
     * {@inheritDoc} The hash code is delegated to the underlying buffer.
     */
    @Override
    public int hashCode() {
        return underlying.hashCode();
    }

    /**
     * {@inheritDoc} Two ReadOnly buffers are equal if their underlying buffers are equal.
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
        ReadOnly other = (ReadOnly) obj;
        return underlying.equals(other.underlying);
    }

    /**
     * The underlying buffer that this class wraps in a read-only interface.
     */
    private final RandomAccessBuffer underlying;

}
