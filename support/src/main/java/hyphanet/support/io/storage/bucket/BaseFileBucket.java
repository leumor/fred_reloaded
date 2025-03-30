package hyphanet.support.io.storage.bucket;

import hyphanet.support.io.storage.AbstractStorage;
import hyphanet.support.io.storage.StorageFormatException;
import hyphanet.support.io.storage.rab.PooledFileRab;
import hyphanet.support.io.storage.rab.Rab;
import hyphanet.support.io.stream.NullInputStream;
import hyphanet.support.io.util.FileSystem;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base class for file-based buckets.
 *
 * <p>This class provides common functionality for managing file-based buckets, including handling
 * temporary files, managing input/output streams, and performing basic file operations.
 */
public abstract class BaseFileBucket extends AbstractStorage implements RandomAccessBucket {
  /** Magic number to identify the file type. */
  public static final int MAGIC = 0xc4b7533d;

  /** Version number of the file format. */
  static final int VERSION = 1;

  private static final Logger logger = LoggerFactory.getLogger(BaseFileBucket.class);

  /**
   * The temporary directory used for storing temporary files. Initialized lazily by {@link
   * #initializeTempDir()}.
   */
  private static @Nullable Path tempDir = initializeTempDir();

  /**
   * Default constructor for BaseFile.
   *
   * @throws AssertionError if {@link #createFileOnly()} and {@link #tempFileAlreadyExists()} are
   *     both true.
   */
  protected BaseFileBucket() {
    assert !(createFileOnly() && tempFileAlreadyExists()); // Mutually incompatible!
  }

  /**
   * Constructor for BaseFile that reads data from a DataInputStream.
   *
   * <p>Reads and validates the magic number and version information from the stream.
   *
   * @param dis the DataInputStream to read from.
   * @throws IOException if an I/O error occurs.
   * @throws StorageFormatException if the data in the stream is not in the expected format.
   */
  protected BaseFileBucket(DataInputStream dis) throws IOException, StorageFormatException {
    // Not constructed directly, so we DO need to read the magic value.
    int magic = dis.readInt();
    if (magic != MAGIC) {
      throw new StorageFormatException("Bad magic");
    }
    int version = dis.readInt();
    if (version != VERSION) {
      throw new StorageFormatException("Bad version");
    }
    var closed = dis.readBoolean();
    if (closed) {
      setClosed();
    }
  }

  /**
   * Returns the directory used for temporary files.
   *
   * @return The temporary directory path as a String, or an empty string if not set.
   */
  public static synchronized String getTempDir() {
    if (tempDir == null) {
      return "";
    }
    return tempDir.toString();
  }

  /**
   * Sets the temporary file directory.
   *
   * @param dirName the path to the temporary directory.
   * @throws IllegalArgumentException if the specified directory does not exist or is not writable.
   */
  public static synchronized void setTempDir(String dirName) {
    Path dir = Path.of(dirName);
    if (!Files.isDirectory(dir) || !Files.isWritable(dir)) {
      throw new IllegalArgumentException("Bad Temp Directory: " + dir.toAbsolutePath());
    }
    tempDir = dir;
  }

  /**
   * {@inheritDoc}
   *
   * <p>Returns an unbuffered output stream to the underlying file.
   *
   * <p>If {@link #createFileOnly()} is true and the file does not already exist, a new file is
   * created. If {@link #tempFileAlreadyExists()} is true and the file does not exist or is not
   * accessible, a {@link FileNotFoundException} is thrown.
   *
   * <p>If there are open streams on this bucket, a warning is logged.
   *
   * <p>If {@link #tempFileAlreadyExists()} is false, the data is written to a temporary file first
   * and then renamed to the target file on close.
   *
   * @return An unbuffered output stream to the underlying file.
   * @throws IOException if an I/O error occurs or if the bucket is read-only or already freed.
   */
  @Override
  public OutputStream getOutputStreamUnbuffered() throws IOException {
    synchronized (this) {
      var path = getPath();
      if (closed()) {
        throw new IOException("File already closed: " + this);
      }
      if (isReadOnly()) {
        throw new IOException("Bucket is read-only: " + this);
      }

      if (
      // Fail if file already exists
      createFileOnly()
          &&
          // Ignore if we're just clobbering our own file after a previous
          // getOutputStream()
          fileRestartCounter == 0) {
        Files.createFile(path);
      }
      if (tempFileAlreadyExists()
          && !(Files.exists(path) && Files.isReadable(path) && Files.isWritable(path))) {
        throw new FileNotFoundException(path.toString());
      }

      if (!streams.isEmpty()) {
        logger.error(
            "Streams open on {} while opening an output stream!: {}",
            this,
            streams,
            new Exception("debug"));
      }

      boolean rename = !tempFileAlreadyExists();
      var tempFile = rename ? getTempFilePath() : path;
      long streamNumber = ++fileRestartCounter;

      var os = new FileBucketOutputStream(tempFile, streamNumber);

      logger.debug("Creating Unbuffered FileBucketOutputStream {}", os);

      addStream(os);
      return os;
    }
  }

  /**
   * {@inheritDoc}
   *
   * <p>Returns a buffered output stream to the underlying file.
   *
   * @return A buffered output stream to the underlying file.
   * @throws IOException if an I/O error occurs.
   */
  @Override
  public OutputStream getOutputStream() throws IOException {
    return new BufferedOutputStream(getOutputStreamUnbuffered());
  }

  /**
   * {@inheritDoc}
   *
   * <p>Returns an unbuffered input stream to the underlying file.
   *
   * <p>If the file does not exist, a {@link NullInputStream} is returned.
   *
   * @return An unbuffered input stream to the underlying file, or a {@link NullInputStream} if the
   *     file does not exist.
   * @throws IOException if an I/O error occurs or if the bucket is already freed.
   */
  @Override
  public synchronized InputStream getInputStreamUnbuffered() throws IOException {
    if (closed()) {
      throw new IOException("File already freed: " + this);
    }

    var path = getPath();
    if (!Files.exists(path)) {
      logger.info("File does not exist: {} for {}", path, this);
      return new NullInputStream();
    }

    var is = new FileBucketInputStream(path);
    addStream(is);
    logger.debug("Creating Unbuffered FileBucketInputStream {}", is);
    return is;
  }

  /**
   * {@inheritDoc}
   *
   * <p>Returns a buffered input stream to the underlying file.
   *
   * @return A buffered input stream to the underlying file, or null if the file does not exist.
   * @throws IOException if an I/O error occurs.
   */
  @Override
  public InputStream getInputStream() throws IOException {
    var is = getInputStreamUnbuffered();
    if (is instanceof NullInputStream) {
      return is;
    }
    return new BufferedInputStream(is);
  }

  /**
   * {@inheritDoc}
   *
   * @return the name of the file.
   */
  @Override
  public synchronized String getName() {
    return getPath().getFileName().toString();
  }

  /**
   * {@inheritDoc}
   *
   * <p>Returns the size of the underlying file.
   *
   * @return The size of the file in bytes, or 0 if an error occurs.
   */
  @Override
  public synchronized long size() {
    try {
      return Files.size(getPath());
    } catch (Exception e) {
      return 0;
    }
  }

  /**
   * Splits the bucket into multiple read-only buckets of a specified size.
   *
   * @param splitSize the size of each split bucket.
   * @return An array of read-only buckets.
   * @throws IllegalArgumentException if the total size is too large for the specified split size.
   */
  public synchronized Bucket[] split(int splitSize) {
    long length = size();
    if (length > ((long) Integer.MAX_VALUE) * splitSize) {
      throw new IllegalArgumentException("Way too big!: " + length + " for " + splitSize);
    }
    int bucketCount = (int) (length / splitSize);
    if (length % splitSize > 0) {
      bucketCount++;
    }
    var path = getPath();
    return Arrays.stream(new int[bucketCount])
        .mapToObj(
            i -> {
              long startAt = (long) i * splitSize;
              long endAt = Math.min(startAt + splitSize, length);
              try {
                return new ReadOnlyFileSliceBucket(path, startAt, endAt - startAt);
              } catch (IOException e) {
                throw new IllegalStateException(e);
              }
            })
        .toArray(Bucket[]::new);
  }

  /** Closes the bucket and releases any associated resources. */
  @Override
  public void close() {
    if (!setClosed()) {
      return;
    }

    Set<Closeable> toClose;
    logger.info("Closing File Bucket {}", this);

    synchronized (this) {
      toClose = new HashSet<>(streams);
      streams.clear();
    }
    closeStreams(toClose);
  }

  @Override
  public void dispose() {
    if (setDisposed()) {
      return;
    }

    close();

    var path = getPath();
    if ((deleteOnDispose()) && Files.exists(path)) {
      logger.debug("Deleting bucket {}", path);
      deleteFile();
      if (Files.exists(path)) {
        logger.error("Delete failed on bucket");
      }
    }
  }

  /**
   * Returns a string representation of the BaseFile object.
   *
   * @return A string representation of the object.
   */
  @Override
  public synchronized String toString() {
    return String.format(
        "%s:%s:streams=%d",
        super.toString(),
        Optional.ofNullable(getPath()).map(Path::toString).orElse("???"),
        streams.isEmpty() ? 0 : streams.size());
  }

  /**
   * Returns the path object this buckets data is kept in.
   *
   * @return The path object.
   */
  public abstract Path getPath();

  /**
   * {@inheritDoc}
   *
   * <p>Stores the bucket's metadata to the specified DataOutputStream.
   *
   * @param dos the DataOutputStream to write to.
   * @throws IOException if an I/O error occurs.
   */
  @Override
  public void storeTo(DataOutputStream dos) throws IOException {
    dos.writeInt(MAGIC);
    dos.writeInt(VERSION);
    dos.writeBoolean(closed());
  }

  /**
   * {@inheritDoc}
   *
   * <p>Converts the bucket to a RandomAccessBuffer.
   *
   * <p>The bucket must not be empty and will be set to read-only after this operation.
   *
   * @return A RandomAccessBuffer representing the bucket's data.
   * @throws IOException if an I/O error occurs, if the bucket is already freed, or if the bucket is
   *     empty.
   */
  @Override
  public Rab toRandomAccessBuffer() throws IOException {
    if (closed()) {
      throw new IOException("Already closed");
    }
    setReadOnly();
    long size = size();
    if (size == 0) {
      throw new IOException("Must not be empty");
    }
    return new PooledFileRab(getPath(), true, size, getPersistentTempID(), deleteOnDispose());
  }

  /**
   * Sets the deleteOnExit flag for the specified file.
   *
   * @param file the file to set the flag for.
   */
  protected void setDeleteOnExit(File file) {
    file.deleteOnExit();
  }

  /**
   * If true, then the file is temporary and must already exist, so we will just open it. Otherwise,
   * we will create a temporary file and then rename it over the target. Incompatible with
   * createFileOnly()!
   *
   * @return True if the file is temporary and must already exist, false otherwise.
   */
  protected abstract boolean tempFileAlreadyExists();

  /**
   * If true, we will fail if the file already exist. Incompatible with tempFileAlreadyExists()!
   *
   * @return True if the file must not already exist, false otherwise.
   */
  protected abstract boolean createFileOnly();

  /**
   * Returns whether the underlying file should be deleted on JVM exit.
   *
   * @return True if the file should be deleted on exit, false otherwise.
   */
  protected abstract boolean deleteOnExit();

  /**
   * Returns whether the underlying file should be deleted when the bucket is disposed.
   *
   * @return True if the file should be deleted on dispose, false otherwise.
   */
  protected abstract boolean deleteOnDispose();

  /**
   * Creates a temporary file in the same directory as this file.
   *
   * @return The path to the temporary file.
   * @throws IOException if an I/O error occurs.
   */
  protected Path getTempFilePath() throws IOException {
    var bucketPath = getPath();
    return FileSystem.createTempFile(
        bucketPath.getFileName().toString(), ".hyphanet-tmp", bucketPath.getParent());
  }

  /**
   * Actually delete the underlying file. Called by finalizer, will not be called twice. But length
   * must still be valid when calling it.
   */
  protected synchronized void deleteFile() {
    var path = getPath();

    logger.info("Deleting {} for {}", path, this);

    try {
      Files.delete(path);
    } catch (Exception e) {
      logger.error("Delete failed on bucket {}", path, e);
    }
  }

  /**
   * Returns a persistent temporary ID for the bucket.
   *
   * <p>Default implementation returns -1.
   *
   * @return The persistent temporary ID.
   */
  protected long getPersistentTempID() {
    return -1;
  }

  /**
   * Closes the specified set of streams.
   *
   * @param toClose the set of streams to close.
   */
  private void closeStreams(Set<Closeable> toClose) {
    if (toClose.isEmpty()) {
      return;
    }

    logger.info("Streams open free()ing {} : {}", this, toClose);
    for (Closeable stream : toClose) {
      try {
        stream.close();
      } catch (Exception e) {
        logger.error("Caught closing stream in free()", e);
      }
    }
  }

  /**
   * Initializes the temporary directory.
   *
   * <p>Tries the following locations in order:
   *
   * <ol>
   *   <li>The directory specified by the <code>java.io.tmpdir</code> system property.
   *   <li>OS-specific temporary directories (e.g., /tmp, /var/tmp on Linux/FreeBSD, C:\TEMP,
   *       C:\WINDOWS\TEMP on Windows).
   *   <li>The current working directory.
   * </ol>
   *
   * @return The initialized temporary directory path.
   */
  private static Path initializeTempDir() {
    // Try the Java property first
    String dir = System.getProperty("java.io.tmpdir");
    if (dir != null) {
      return Path.of(dir);
    }

    // Try OS-specific locations
    Path osSpecificTemp = findOsSpecificTempDir();
    if (osSpecificTemp != null) {
      return osSpecificTemp;
    }

    // Last resort - use current working directory
    return Path.of(System.getProperty("user.dir"));
  }

  /**
   * Finds an OS-specific temporary directory.
   *
   * @return The path to an OS-specific temporary directory, or null if none is found.
   */
  private static @Nullable Path findOsSpecificTempDir() {
    String os = System.getProperty("os.name");
    if (os == null) {
      return null;
    }

    String[] candidates = null;
    if (os.equalsIgnoreCase("Linux") || os.equalsIgnoreCase("FreeBSD")) {
      candidates = new String[] {"/tmp", "/var/tmp"};
    } else if (os.equalsIgnoreCase("Windows")) {
      candidates = new String[] {"C:\\TEMP", "C:\\WINDOWS\\TEMP"};
    }
    if (candidates == null) {
      return null;
    }

    for (String candidate : candidates) {
      Path path = Path.of(candidate);
      if (Files.isDirectory(path) && Files.isWritable(path)) {
        return path;
      }
    }
    return null;
  }

  /**
   * Adds a stream to the set of open streams for this bucket.
   *
   * @param stream the stream to add.
   */
  private synchronized void addStream(Closeable stream) {
    // BaseFileBucket is a very common object, and often very long-lived,
    // so we need to minimize memory usage even at the cost of frequent allocations.
    streams.add(stream);
  }

  /**
   * Removes a stream from the set of open streams for this bucket.
   *
   * @param stream the stream to remove.
   */
  private synchronized void removeStream(Closeable stream) {
    // Race condition is possible
    if (streams.isEmpty()) {
      return;
    }
    streams.remove(stream);
  }

  /**
   * Internal OutputStream implementation for BaseFile.
   *
   * <p>If createFileOnly is set, we won't overwrite an existing file, and we write to a temp file
   * then rename over the target. Note that we can't use createNewFile then new FOS() because while
   * createNewFile is atomic, the combination is not, so if we do it we are vulnerable to symlink
   * attacks.
   */
  class FileBucketOutputStream extends OutputStream {

    /**
     * Constructs a new FileBucketOutputStream.
     *
     * @param tempFilePath the path to the temporary file.
     * @param restartCount the restart counter value.
     * @throws IOException if an I/O error occurs.
     */
    protected FileBucketOutputStream(Path tempFilePath, long restartCount) throws IOException {
      super();
      if (deleteOnExit()) {
        outputStream = Files.newOutputStream(tempFilePath, StandardOpenOption.DELETE_ON_CLOSE);
      } else {
        outputStream = Files.newOutputStream(tempFilePath);
      }
      logger
          .atInfo()
          .setMessage("Writing to {} for {} : {}")
          .addArgument(tempFilePath)
          .addArgument(BaseFileBucket.this::getPath)
          .addArgument(this)
          .log();
      this.tempFilePath = tempFilePath;
      this.restartCount = restartCount;
      closed = false;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Writes the specified byte array to the output stream.
     *
     * @param b the byte array to write.
     * @throws IOException if an I/O error occurs.
     */
    @Override
    public void write(byte[] b) throws IOException {
      synchronized (BaseFileBucket.this) {
        confirmWriteSynchronized();
        outputStream.write(b);
      }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Writes a portion of the specified byte array to the output stream.
     *
     * @param b the byte array to write.
     * @param off the offset in the byte array.
     * @param len the number of bytes to write.
     * @throws IOException if an I/O error occurs.
     */
    @Override
    public void write(byte[] b, int off, int len) throws IOException {
      synchronized (BaseFileBucket.this) {
        confirmWriteSynchronized();
        outputStream.write(b, off, len);
      }
    }

    /**
     * {@inheritDoc}
     *
     * <p>Writes the specified byte to the output stream.
     *
     * @param b the byte to write.
     * @throws IOException if an I/O error occurs.
     */
    @Override
    public void write(int b) throws IOException {
      synchronized (BaseFileBucket.this) {
        confirmWriteSynchronized();
        outputStream.write(b);
      }
    }

    /**
     * Closes the output stream.
     *
     * <p>If {@link #tempFileAlreadyExists()} is false, the temporary file is renamed to the target
     * file.
     *
     * @throws IOException if an I/O error occurs or if the rename operation fails.
     */
    @Override
    public void close() throws IOException {
      Path path;
      synchronized (this) {
        if (closed) {
          return;
        }
        closed = true;
        path = getPath();
      }
      boolean renaming = !tempFileAlreadyExists();
      removeStream(this);

      logger.info("Closing Bucket OutputStream {}", BaseFileBucket.this);

      try {
        outputStream.close();
      } catch (IOException e) {
        logger.info("Failed closing {} : {}", BaseFileBucket.this, e, e);
        if (renaming) {
          try {
            Files.delete(tempFilePath);
          } catch (Exception e2) {
            // Ignore
          }
          throw e;
        }
      }

      if (renaming && !FileSystem.moveTo(tempFilePath, path)) {
        // getOutputStream() creates the file as a marker, so DON'T check for its
        // existence,
        // even if createFileOnly() is true.
        try {
          Files.delete(tempFilePath);
        } catch (Exception e) {
          // Ignore
        }
        logger.info("Deleted, cannot rename file for {}", this);
        throw new IOException("Cannot rename file");
      }
    }

    /**
     * Returns a string representation of the FileBucketOutputStream.
     *
     * @return A string representation of the object.
     */
    @Override
    public String toString() {
      return super.toString() + ":" + BaseFileBucket.this;
    }

    /**
     * Confirms that a write operation is allowed.
     *
     * @throws IOException if the bucket is read-only or already freed.
     * @throws IllegalStateException if writing to the file after a restart.
     */
    protected void confirmWriteSynchronized() throws IOException {
      synchronized (BaseFileBucket.this) {
        if (fileRestartCounter > restartCount) {
          throw new IllegalStateException("writing to file after restart");
        }
        if (closed()) {
          throw new IOException("writing to file after it has been freed");
        }
      }
      if (isReadOnly()) {
        throw new IOException("File is read-only");
      }
    }

    /** The restart counter value at the time the stream was created. */
    private final long restartCount;

    /** The path to the temporary file. */
    private final Path tempFilePath;

    /** The underlying output stream. */
    private final OutputStream outputStream;

    /** Flag indicating whether the stream has been closed. */
    private boolean closed;
  }

  /** Internal InputStream implementation for BaseFile. */
  class FileBucketInputStream extends InputStream {

    /**
     * Constructs a new FileBucketInputStream.
     *
     * @param path the path to the file.
     * @throws IOException if an I/O error occurs.
     */
    public FileBucketInputStream(Path path) throws IOException {
      super();
      inputStream = Files.newInputStream(path);
    }

    /**
     * {@inheritDoc}
     *
     * @return The next byte of data, or -1 if the end of the stream is reached.
     * @throws IOException if an I/O error occurs.
     */
    @Override
    public int read() throws IOException {
      return inputStream.read();
    }

    /**
     * {@inheritDoc}
     *
     * @param b The buffer into which the data is read.
     * @param off The start offset in array b at which the data is written.
     * @param len The maximum number of bytes to read.
     * @return The total number of bytes read into the buffer, or -1 if there is no more data
     *     because the end of the stream has been reached.
     * @throws IOException if an I/O error occurs.
     */
    @Override
    public int read(byte[] b, int off, int len) throws IOException {
      return inputStream.read(b, off, len);
    }

    /**
     * Closes the input stream.
     *
     * @throws IOException if an I/O error occurs.
     */
    @Override
    public void close() throws IOException {
      synchronized (this) {
        if (closed) {
          return;
        }
        closed = true;
      }
      removeStream(this);
      super.close();
    }

    /**
     * Returns a string representation of the FileBucketInputStream.
     *
     * @return A string representation of the object.
     */
    @Override
    public String toString() {
      return super.toString() + ":" + BaseFileBucket.this;
    }

    /** The underlying input stream. */
    private final InputStream inputStream;

    /** Flag indicating whether the stream has been closed. */
    boolean closed;
  }

  /**
   * Vector of streams ({@link FileBucketInputStream} or {@link FileBucketOutputStream}) which are
   * open to this file. So we can be sure they are all closed when we free it. Can be null.
   */
  private final Set<Closeable> streams = ConcurrentHashMap.newKeySet();

  /** Counter for output stream restarts. Incremented each time getOutputStream() is called. */
  protected volatile long fileRestartCounter;
}
