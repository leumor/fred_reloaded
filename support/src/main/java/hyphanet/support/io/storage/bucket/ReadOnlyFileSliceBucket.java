/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package hyphanet.support.io.storage.bucket;

import hyphanet.support.io.storage.StorageFormatException;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Objects;

/**
 * Provides a read-only view of a slice of a file, representing a continuous portion of the file
 * from a specified starting position with a defined length. This implementation is immutable and
 * thread-safe.
 *
 * <p>The slice maintains a view into the underlying file without loading the entire content into
 * memory, making it memory efficient for large files.
 *
 * <p><b>Thread Safety:</b> This class is thread-safe. Multiple threads can safely read from the
 * same instance concurrently.
 *
 * <p><b>Note:</b> The underlying file must exist and remain unchanged during the lifetime of this
 * slice. If the file is modified or deleted, subsequent operations may fail.
 *
 * <p>FIXME: implement a hash verifying version of this.
 *
 * @see Bucket
 */
public class ReadOnlyFileSliceBucket implements Bucket, Serializable {

  /** The magic number used for serialization validation. */
  static final int MAGIC = 0x99e54c4;

  /** The version number for serialization format. */
  static final int VERSION = 1;

  @Serial private static final long serialVersionUID = 1L;

  /**
   * Creates a new read-only slice of a file.
   *
   * @param path the path to the source file
   * @param startAt the starting position in the file (must be non-negative)
   * @param length the length of the slice (must be non-negative)
   * @throws IOException if the file cannot be accessed or if the slice parameters are invalid
   * @throws IllegalArgumentException if startAt or length are negative or if the slice extends
   *     beyond the file bounds
   */
  public ReadOnlyFileSliceBucket(Path path, long startAt, long length) throws IOException {
    validateParameters(path, startAt, length);

    this.path = path;
    this.startAt = startAt;
    this.length = length;
  }

  /**
   * Deserializes a ReadOnlyFileSlice from the given input stream.
   *
   * @param dis the DataInputStream to read from
   * @throws StorageFormatException if the serialized format is invalid
   * @throws IOException if an I/O error occurs during reading
   */
  protected ReadOnlyFileSliceBucket(DataInputStream dis)
      throws StorageFormatException, IOException {
    int version = dis.readInt();
    if (version != VERSION) {
      throw new StorageFormatException("Bad version");
    }

    path = Path.of(dis.readUTF());
    startAt = dis.readLong();
    length = dis.readLong();

    validateParameters(path, startAt, length);
  }

  /**
   * {@inheritDoc}
   *
   * @throws UnsupportedOperationException always, as this bucket is read-only
   */
  @Override
  public OutputStream getOutputStream() {
    throw new UnsupportedOperationException("Bucket is read-only");
  }

  /**
   * {@inheritDoc}
   *
   * @throws UnsupportedOperationException always, as this bucket is read-only
   */
  @Override
  public OutputStream getOutputStreamUnbuffered() {
    throw new UnsupportedOperationException("Bucket is read-only");
  }

  /**
   * {@inheritDoc}
   *
   * @return a buffered InputStream for reading the slice content
   * @throws IOException if an I/O error occurs while opening the file
   */
  @Override
  public InputStream getInputStream() throws IOException {
    var unbuffered = getInputStreamUnbuffered();
    return unbuffered != null ? new BufferedInputStream(unbuffered) : null;
  }

  /**
   * {@inheritDoc}
   *
   * @return an unbuffered InputStream for reading the slice content
   * @throws IOException if an I/O error occurs while opening the file
   */
  @Override
  public InputStream getInputStreamUnbuffered() throws IOException {
    return new SliceInputStream();
  }

  @Override
  public String getName() {
    return "ROFS:" + path.toAbsolutePath() + ':' + startAt + ':' + length;
  }

  @Override
  public long size() {
    return length;
  }

  @Override
  public boolean isReadOnly() {
    return true;
  }

  @Override
  public void setReadOnly() {
    // Already read-only
  }

  @Override
  public void close() {
    // No resources to close
  }

  /**
   * Creates a new instance that shares the same underlying file and slice parameters.
   *
   * @return a new ReadOnlyFileSlice instance, or null if creation fails
   */
  @Override
  public Bucket createShadow() {
    try {
      return new ReadOnlyFileSliceBucket(path, startAt, length);
    } catch (IOException e) {
      return null;
    }
  }

  @Override
  public void storeTo(DataOutputStream dos) throws IOException {
    dos.writeInt(MAGIC);
    dos.writeInt(VERSION);
    dos.writeUTF(path.toString());
    dos.writeLong(startAt);
    dos.writeLong(length);
  }

  /**
   * Validates the slice parameters against the file.
   *
   * @param path the file path to validate
   * @param startAt the starting position
   * @param length the slice length
   * @throws IOException if the file cannot be accessed
   * @throws IllegalArgumentException if the parameters are invalid
   * @throws FileNotFoundException if the file does not exist
   */
  private static void validateParameters(Path path, long startAt, long length) throws IOException {
    if (startAt < 0) {
      throw new IllegalArgumentException("startAt must be non-negative");
    }
    if (length < 0) {
      throw new IllegalArgumentException("length must be non-negative");
    }

    if (!Files.exists(path)) {
      throw new FileNotFoundException("File does not exist: " + path);
    }

    if (Files.size(path) < startAt + length) {
      throw new IllegalArgumentException("Slice extends beyond file bounds");
    }
  }

  @Serial
  private void writeObject(ObjectOutputStream out) throws IOException {
    out.defaultWriteObject();
    out.writeUTF(path.toString());
  }

  @Serial
  private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
    in.defaultReadObject();
    path = Paths.get(in.readUTF());
  }

  /**
   * Provides stream access to the file slice. This implementation ensures that only the specified
   * portion of the file is accessible through the stream.
   */
  private class SliceInputStream extends InputStream {

    /**
     * Creates a new input stream for reading the slice.
     *
     * @throws IOException if the file cannot be opened or accessed
     */
    SliceInputStream() throws IOException {
      try {
        channel = Files.newByteChannel(path, StandardOpenOption.READ);
        channel.position(startAt);
        position = 0;
        singleByteBuffer = ByteBuffer.allocate(1);
      } catch (FileNotFoundException e) {
        throw new IOException("Failed to create input stream", e);
      }
    }

    @Override
    public int read() throws IOException {
      if (position >= length) {
        return -1;
      }

      singleByteBuffer.clear();
      int bytesRead = channel.read(singleByteBuffer);

      if (bytesRead == -1) {
        return -1;
      }

      position++;
      singleByteBuffer.flip();
      return singleByteBuffer.get() & 0xFF;
    }

    @Override
    public int read(byte[] buf, int offset, int len) throws IOException {
      Objects.checkFromIndexSize(offset, len, buf.length);

      if (position >= length) {
        return -1;
      }

      int toRead = (int) Math.min(len, length - position);
      ByteBuffer buffer = ByteBuffer.wrap(buf, offset, toRead);
      int bytesRead = channel.read(buffer);

      if (bytesRead > 0) {
        position += bytesRead;
      }

      return bytesRead;
    }

    @Override
    public void close() throws IOException {
      channel.close();
    }

    /**
     * The channel for reading from the underlying file. Provides random access capabilities for
     * efficient slice reading.
     */
    private final SeekableByteChannel channel;

    /**
     * Buffer used for single-byte read operations. Allocated once and reused to improve
     * performance.
     */
    private final ByteBuffer singleByteBuffer;

    /**
     * The current position within the slice, relative to startAt. Used to track read progress and
     * enforce slice boundaries.
     */
    private long position;
  }

  /**
   * The starting position of this slice in the file. This value must be non-negative and within the
   * file bounds.
   */
  private final long startAt;

  /**
   * The length of this slice in bytes. This value must be non-negative and the slice must not
   * extend beyond the file bounds.
   */
  private final long length;

  /**
   * The path to the underlying file. This field is transient as Path is not serializable, and is
   * reconstructed during deserialization.
   */
  private transient Path path;
}
