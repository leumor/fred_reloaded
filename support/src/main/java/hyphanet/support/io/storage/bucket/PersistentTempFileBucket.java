package hyphanet.support.io.storage.bucket;

import hyphanet.support.io.FilenameGenerator;
import hyphanet.support.io.PersistentFileTracker;
import hyphanet.support.io.ResumeContext;
import hyphanet.support.io.ResumeFailedException;
import hyphanet.support.io.storage.StorageFormatException;
import hyphanet.support.io.stream.DiskSpaceCheckingOutputStream;
import java.io.*;
import java.nio.file.Files;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link PersistentTempFileBucket} is a specialized {@link TempFileBucket} designed for
 * persistent temporary storage. Unlike regular temporary files which might be deleted on JVM exit
 * or system restart, persistent temporary files are intended to survive restarts and are managed by
 * a {@link PersistentFileTracker}. This type of bucket is crucial for operations that need to
 * preserve data across sessions, such as persistent downloads or pending uploads.
 *
 * <p>Key features of {@link PersistentTempFileBucket}:
 *
 * <ul>
 *   <li><b>Persistence:</b> Files are not deleted on JVM exit and are tracked for cleanup by the
 *       {@link PersistentFileTracker}.
 *   <li><b>Disk Space Checking:</b> Output streams for this bucket are wrapped with {@link
 *       DiskSpaceCheckingOutputStream} to ensure sufficient disk space before writing data,
 *       preventing potential {@link IOException} due to disk full errors.
 *   <li><b>Shadow Copies:</b> Supports creation of shadow copies that are also persistent, but do
 *       not delete the underlying file on disposal.
 *   <li><b>Registration with Tracker:</b> Upon resumption, buckets register themselves with the
 *       {@link PersistentFileTracker} to be managed during node lifecycle, preventing accidental
 *       deletion during startup cleanup.
 * </ul>
 *
 * <p>{@link PersistentTempFileBucket} extends {@link TempFileBucket} and inherits its file-based
 * bucket functionalities, adding persistence and disk space management capabilities. It is
 * typically used in conjunction with {@link PersistentTempFileBucketFactory} which creates and
 * manages these buckets.
 *
 * @see TempFileBucket
 * @see PersistentTempFileBucketFactory
 * @see PersistentFileTracker
 * @see DiskSpaceCheckingOutputStream
 */
public class PersistentTempFileBucket extends TempFileBucket implements Serializable {

  /**
   * Magic number for {@link PersistentTempFileBucket} files, used for file format identification.
   * This magic number is written to the file header to ensure that the file is indeed a {@link
   * PersistentTempFileBucket} and not some other type of file.
   */
  public static final int MAGIC = 0x2ffdd4cf;

  /**
   * Default buffer size used for buffered streams in {@link PersistentTempFileBucket}. This buffer
   * size is employed when creating buffered input or output streams to improve I/O performance by
   * reducing the number of disk access operations.
   */
  static final int BUFFER_SIZE = 4096;

  @Serial private static final long serialVersionUID = 1L;
  private static final Logger logger = LoggerFactory.getLogger(PersistentTempFileBucket.class);

  /**
   * Constructs a new {@link PersistentTempFileBucket}. Uses the provided ID and {@link
   * FilenameGenerator} to create a persistent temporary file. Files created with this constructor
   * are marked for deletion on dispose (when {@link #dispose()} is called).
   *
   * @param id The unique identifier for this persistent temporary file. Used by the {@link
   *     FilenameGenerator}.
   * @param generator The {@link FilenameGenerator} responsible for creating file paths.
   * @param tracker The {@link PersistentFileTracker} to register this bucket with for persistent
   *     file management.
   * @see #PersistentTempFileBucket(long, FilenameGenerator, PersistentFileTracker, boolean)
   */
  public PersistentTempFileBucket(
      long id, FilenameGenerator generator, PersistentFileTracker tracker) {
    this(id, generator, tracker, true);
  }

  /**
   * Constructor for the {@link PersistentTempFileBucket} object. Subclasses can call this
   * constructor. Allows specifying whether the bucket should be deleted when {@link #dispose()} is
   * called.
   *
   * @param id The unique identifier for this persistent temporary file.
   * @param generator The {@link FilenameGenerator} responsible for creating file paths.
   * @param tracker The {@link PersistentFileTracker} to register this bucket with.
   * @param deleteOnDispose Set to {@code true} if you want the bucket to be deleted when {@link
   *     #dispose()} is called, or {@code false} for shadow copies or when deletion is managed
   *     externally.
   * @see #PersistentTempFileBucket(long, FilenameGenerator, PersistentFileTracker)
   * @see #dispose()
   */
  protected PersistentTempFileBucket(
      long id,
      FilenameGenerator generator,
      PersistentFileTracker tracker,
      boolean deleteOnDispose) {
    super(id, generator, deleteOnDispose);
    this.tracker = tracker;
  }

  /**
   * Default constructor for serialization purposes.
   *
   * <p><strong>Note:</strong> Should only be used during deserialization.
   */
  protected PersistentTempFileBucket() {
    // For serialization.
  }

  /**
   * Constructor for deserialization from a {@link DataInputStream}. Reads and validates the data
   * from the stream to reconstruct a {@link PersistentTempFileBucket} object.
   *
   * @param dis The {@link DataInputStream} to read from.
   * @throws IOException If an I/O error occurs during reading.
   * @throws StorageFormatException If the data in the stream is not in the expected format (e.g.,
   *     bad magic number or version).
   */
  protected PersistentTempFileBucket(DataInputStream dis)
      throws IOException, StorageFormatException {
    super(dis);
  }

  /**
   * {@inheritDoc}
   *
   * <p>Overrides {@link TempFileBucket#getOutputStreamUnbuffered()} to provide an unbuffered output
   * stream that also performs disk space checking using {@link DiskSpaceCheckingOutputStream}. This
   * ensures that write operations are only attempted if there is sufficient disk space available,
   * preventing {@link IOException} due to disk full conditions.
   *
   * @return An unbuffered {@link OutputStream} wrapped with disk space checking.
   * @throws IOException If an I/O error occurs or if disk space is insufficient.
   */
  @Override
  public OutputStream getOutputStreamUnbuffered() throws IOException {
    OutputStream os = super.getOutputStreamUnbuffered();
    os = new DiskSpaceCheckingOutputStream(os, tracker, getPath(), BUFFER_SIZE);
    return os;
  }

  /**
   * {@inheritDoc}
   *
   * <p>Overrides {@link TempFileBucket#getOutputStream()} to provide a buffered output stream with
   * a default buffer size ({@link #BUFFER_SIZE}). The underlying unbuffered output stream is
   * obtained from {@link #getOutputStreamUnbuffered()}, which includes disk space checking.
   *
   * @return A buffered {@link OutputStream} with disk space checking.
   * @throws IOException If an I/O error occurs or if disk space is insufficient.
   */
  @Override
  public OutputStream getOutputStream() throws IOException {
    return new BufferedOutputStream(getOutputStreamUnbuffered(), BUFFER_SIZE);
  }

  /**
   * {@inheritDoc}
   *
   * <p>Overrides {@link TempFileBucket#createShadow()} to ensure that shadow copies of {@link
   * PersistentTempFileBucket} are also persistent and have {@link #deleteOnExit()} set to {@code
   * false}. This is crucial for maintaining the persistent nature of shadow copies.
   *
   * <p>When creating a shadow copy, it also verifies that the original file exists to prevent
   * issues if the original bucket's file has been unexpectedly removed.
   *
   * @return A new {@link PersistentTempFileBucket} instance representing the read-only shadow copy.
   */
  @Override
  public RandomAccessible createShadow() {
    PersistentTempFileBucket ret =
        new PersistentTempFileBucket(filenameId, generator, tracker, false);
    ret.setReadOnly();
    if (!Files.exists(getPath())) {
      logger.error("File does not exist when creating shadow: {}", getPath());
    }
    return ret;
  }

  /**
   * {@inheritDoc}
   *
   * <p>Overrides {@link TempFileBucket#deleteOnExit()} to always return {@code false}. {@link
   * PersistentTempFileBucket}s are designed to be persistent and should not be deleted on JVM exit.
   * Deletion is managed by the {@link PersistentFileTracker} and typically occurs during controlled
   * cleanup processes.
   *
   * @return {@code false} always, indicating that persistent temporary files should not be deleted
   *     on JVM exit.
   */
  @Override
  protected boolean deleteOnExit() {
    // DO NOT DELETE ON EXIT !!!!
    return false;
  }

  /**
   * {@inheritDoc}
   *
   * <p>Overrides {@link TempFileBucket#innerResume(ResumeContext)} to extend the resumption process
   * for persistent temporary files. In addition to the base class resumption logic, it also
   * registers the bucket's file path with the {@link PersistentFileTracker}. This registration is
   * essential for the tracker to manage the lifecycle of persistent temporary files across restarts
   * and prevent them from being inadvertently deleted during startup cleanup.
   *
   * @param context The {@link ResumeContext} providing necessary runtime support for resuming.
   * @throws ResumeFailedException If the resumption process fails, including failures in the base
   *     class resumption or tracker registration.
   */
  protected void innerResume(ResumeContext context) throws ResumeFailedException {
    super.innerResume(context);
    logger.info("Resuming {}", this, new Exception("debug"));
    tracker = context.getPersistentFileTracker();
    tracker.register(getPath());
  }

  /**
   * {@inheritDoc}
   *
   * <p>Overrides {@link TempFileBucket#persistent()} to indicate that {@link
   * PersistentTempFileBucket} is persistent. This method returns {@code true} to signify that this
   * bucket type is designed to store data persistently across application restarts.
   *
   * @return {@code true}, indicating that {@link PersistentTempFileBucket} is persistent.
   */
  @Override
  protected boolean persistent() {
    return true;
  }

  /**
   * {@inheritDoc}
   *
   * <p>Overrides {@link TempFileBucket#magic()} to return the specific magic number for {@link
   * PersistentTempFileBucket} ({@link #MAGIC}). This magic number is used during serialization to
   * identify the file type and ensure that it is correctly recognized as a {@link
   * PersistentTempFileBucket} during deserialization.
   *
   * @return The magic number {@link #MAGIC} for {@link PersistentTempFileBucket}.
   */
  protected int magic() {
    return MAGIC;
  }

  /**
   * {@inheritDoc}
   *
   * <p>Overrides {@link TempFileBucket#getPersistentTempID()} to return the filename ID of this
   * bucket. This ID is used as a persistent identifier for the temporary file, particularly useful
   * for tracking and resuming operations across restarts.
   *
   * @return The filename ID ({@link #filenameId}) of this persistent temporary bucket.
   */
  @Override
  protected long getPersistentTempID() {
    return filenameId;
  }

  /**
   * Transient reference to the {@link PersistentFileTracker}. This tracker is responsible for
   * managing the lifecycle of persistent temporary files, including registration, cleanup, and
   * tracking. It is injected during resumption and is used for disk space checking and file
   * lifecycle management. Marked as transient as it's obtained from {@link ResumeContext} on
   * startup and should not be serialized directly.
   */
  transient PersistentFileTracker tracker;
}
