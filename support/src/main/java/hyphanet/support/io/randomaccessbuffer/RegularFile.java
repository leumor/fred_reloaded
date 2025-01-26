package hyphanet.support.io.randomaccessbuffer;

import hyphanet.support.io.ResumeContext;
import hyphanet.support.io.ResumeFailedException;
import hyphanet.support.io.StorageFormatException;
import hyphanet.support.io.util.FileSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.EnumSet;
import java.util.Set;

/**
 * A simple implementation of {@link RandomAccessBuffer} that uses a regular file as the
 * backing store.
 * <p>
 * This class provides basic random access operations for reading and writing data at specific
 * positions in a file. It is backed by a {@link FileChannel} for efficient I/O operations.
 * </p>
 * <p>
 * The file can be opened in read-only or read-write mode. In read-write mode, the file is
 * truncated to the specified length upon creation.
 * </p>
 */
public class RegularFile implements RandomAccessBuffer, Serializable {

    /**
     * Magic number used to identify serialized {@link RegularFile} objects.
     */
    public static final int MAGIC = 0xdd0f4ab2;

    /**
     * Version number of the serialized format.
     */
    public static final int VERSION = 1;

    @Serial
    private static final long serialVersionUID = 1L;
    private static final Logger logger = LoggerFactory.getLogger(RegularFile.class);

    /**
     * Constructs a new {@link RegularFile} with the specified path, length, and read-only
     * mode.
     *
     * @param filePath the path to the file
     * @param length   the length of the file in bytes
     * @param readOnly {@code true} to open the file in read-only mode, {@code false} for
     *                 read-write mode
     *
     * @throws IOException if an I/O error occurs while opening or creating the file
     */
    public RegularFile(Path filePath, long length, boolean readOnly) throws IOException {
        this.length = length;
        this.path = filePath;
        this.readOnly = readOnly;

        Set<StandardOpenOption> options = readOnly ? EnumSet.of(StandardOpenOption.READ) :
            EnumSet.of(StandardOpenOption.READ, StandardOpenOption.WRITE);

        this.channel = FileChannel.open(filePath, options);
        if (channel.size() != length) {
            channel.truncate(length);
        }

    }

    /**
     * Constructs a new {@link RegularFile} with the specified path and read-only mode.
     * <p>
     * The length of the file is determined by reading the file's size from the file system.
     * </p>
     *
     * @param filePath the path to the file
     * @param readOnly {@code true} to open the file in read-only mode, {@code false} for
     *                 read-write mode
     *
     * @throws IOException if an I/O error occurs while opening or reading the file
     */
    public RegularFile(Path filePath, boolean readOnly) throws IOException {
        this(filePath, Files.size(filePath), readOnly);
    }

    /**
     * Constructs a new {@link RegularFile} by deserializing from the provided input stream.
     *
     * @param dis the input stream to read from
     *
     * @throws IOException            if an I/O error occurs while reading from the input
     *                                stream
     * @throws StorageFormatException if the data in the input stream is not a valid
     *                                {@link RegularFile}
     * @throws ResumeFailedException  if the file specified in the input stream does not exist
     *                                or has an incorrect length
     */
    public RegularFile(DataInputStream dis)
        throws IOException, StorageFormatException, ResumeFailedException {
        int version = dis.readInt();
        if (version != VERSION) {
            throw new StorageFormatException("Bad version");
        }
        path = Path.of(dis.readUTF());
        readOnly = dis.readBoolean();
        length = dis.readLong();
        secureDelete = dis.readBoolean();
        if (length < 0) {
            throw new StorageFormatException("Bad length");
        }
        // Have to check here because we need the RAF immediately.
        if (!Files.exists(path)) {
            throw new ResumeFailedException("File does not exist");
        }
        try {
            if (length > Files.size(path)) {
                throw new ResumeFailedException("Bad length");
            }
        } catch (IOException e) {
            throw new ResumeFailedException("Unable to verify file size");
        }

        initializeChannel();
    }

    @Override
    public void pread(long fileOffset, byte[] buf, int bufOffset, int length)
        throws IOException {
        validateReadParameters(fileOffset, length);
        ByteBuffer buffer = ByteBuffer.wrap(buf, bufOffset, length);
        while (buffer.hasRemaining()) {
            int read = channel.read(buffer, fileOffset + buffer.position());
            if (read < 0) {
                throw new IOException("Unexpected end of file");
            }
        }
    }

    @Override
    public void pwrite(long fileOffset, byte[] buf, int bufOffset, int length)
        throws IOException {
        validateWriteParameters(fileOffset, length);
        ByteBuffer buffer = ByteBuffer.wrap(buf, bufOffset, length);
        while (buffer.hasRemaining()) {
            var bytesWritten = channel.write(buffer, fileOffset + buffer.position());
            if (bytesWritten == 0) {
                throw new IOException("Failed to write to file. Should not happen.");
            }
        }
    }

    @Override
    public long size() {
        return length;
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        synchronized (this) {
            if (closed) {
                return;
            }
            closed = true;
        }
        try {
            channel.close();
        } catch (IOException e) {
            logger.error("Could not close channel for {}: {}", path, e.getMessage(), e);
        }
    }

    @Override
    public RabLock lockOpen() {
        return new RabLock() {
            @Override
            protected void innerUnlock() {
                // Do nothing. RAFW is always open.
            }
        };
    }

    @Override
    public void dispose() {
        close();
        try {
            if (secureDelete) {
                FileSystem.secureDelete(path);
            } else {
                Files.delete(path);
            }
        } catch (IOException e) {
            logger.error("Unable to delete temporary file{} : {}", path, e, e);
        }
    }

    /**
     * Sets whether to securely delete the file on {@link #dispose()}.
     *
     * @param secureDelete {@code true} to securely delete the file, {@code false} to delete it
     *                     normally
     */
    public void setSecureDelete(boolean secureDelete) {
        this.secureDelete = secureDelete;
    }

    @Override
    public void onResume(ResumeContext context) throws ResumeFailedException {
        if (!Files.exists(path)) {
            throw new ResumeFailedException("File does not exist any more");
        }
        try {
            if (Files.size(path) != length) {
                throw new ResumeFailedException("File is wrong length");
            }
        } catch (IOException e) {
            throw new ResumeFailedException("Unable to get file length");
        }
        initializeChannel();
    }

    @Override
    public void storeTo(DataOutputStream dos) throws IOException {
        dos.writeInt(MAGIC);
        dos.writeInt(VERSION);
        dos.writeUTF(path.toString());
        dos.writeBoolean(readOnly);
        dos.writeLong(length);
        dos.writeBoolean(secureDelete);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((path == null) ? 0 : path.hashCode());
        result = prime * result + Long.hashCode(length);
        result = prime * result + (readOnly ? 1231 : 1237);
        result = prime * result + (secureDelete ? 1231 : 1237);
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof RegularFile other)) {
            return false;
        }
        return path.equals(other.path) && length == other.length &&
               readOnly == other.readOnly && secureDelete == other.secureDelete;
    }

    /**
     * Initializes the {@link FileChannel} for this file.
     *
     * @throws ResumeFailedException if the file channel cannot be initialized
     */
    private void initializeChannel() throws ResumeFailedException {
        try {
            Set<StandardOpenOption> options = readOnly ? EnumSet.of(StandardOpenOption.READ) :
                EnumSet.of(StandardOpenOption.READ, StandardOpenOption.WRITE);
            this.channel = FileChannel.open(path, options);
        } catch (IOException e) {
            throw new ResumeFailedException("Failed to initialize file channel");
        }
    }

    /**
     * Validates the parameters for a read operation.
     *
     * @param fileOffset the offset within the file to read from
     * @param length     the number of bytes to read
     *
     * @throws IllegalArgumentException if the file offset is negative or if the requested read
     *                                  would exceed the file's length
     */
    private void validateReadParameters(long fileOffset, int length) {
        if (fileOffset < 0) {
            throw new IllegalArgumentException("Negative file offset");
        }
        if (fileOffset + length > this.length) {
            throw new IllegalArgumentException(String.format(
                "Length limit exceeded reading %d bytes from %d of %d",
                length,
                fileOffset,
                this.length
            ));
        }
    }

    /**
     * Validates the parameters for a write operation.
     *
     * @param fileOffset the offset within the file to write to
     * @param length     the number of bytes to write
     *
     * @throws IOException              if the file is read-only
     * @throws IllegalArgumentException if the file offset is negative or if the requested
     *                                  write would exceed the file's length
     */
    private void validateWriteParameters(long fileOffset, int length) throws IOException {
        validateReadParameters(fileOffset, length);
        if (readOnly) {
            throw new IOException("File is read-only");
        }
    }

    /**
     * The path to the file.
     */
    final Path path;

    /**
     * The length of the file in bytes.
     */
    private final long length;

    /**
     * Whether the file is read-only.
     */
    private final boolean readOnly;

    /**
     * The {@link FileChannel} used to access the file.
     */
    private transient FileChannel channel;

    /**
     * Whether the file has been closed.
     */
    private boolean closed = false;

    /**
     * Whether to securely delete the file on {@link #dispose()}.
     */
    private boolean secureDelete;

}
