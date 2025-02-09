/*
 *  This code is part of FProxy, an HTTP proxy server for Freenet.
 *  It is distributed under the GNU Public Licence (GPL) version 2.  See
 *  http://www.gnu.org/ for further details of the GPL.
 */
package hyphanet.support.io.storage.bucket;

import hyphanet.support.io.FilenameGenerator;
import hyphanet.support.io.ResumeContext;
import hyphanet.support.io.ResumeFailedException;
import hyphanet.support.io.storage.StorageFormatException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Temporary file handling. {@link TempFileBucket} Buckets start empty and are typically used for
 * short-lived storage.
 *
 * <p>This class extends {@link BaseFileBucket} and implements {@link Serializable} to support persistence
 * and recovery. It manages temporary files based on a provided {@link FilenameGenerator} to
 * determine file paths.
 *
 * <p><strong>Important:</strong> Instances of this class may or may not be deleted on JVM exit
 * depending on the constructor used and configuration. However, {@link TempFileBucket} buckets are
 * intended for temporary storage and are generally managed by the application lifecycle.
 *
 * @author giannij
 * @see BaseFileBucket
 * @see RandomAccessible
 * @see FilenameGenerator
 */
public class TempFileBucket extends BaseFileBucket implements Serializable {
  /** Version number for serialization compatibility. */
  static final int VERSION = 1;

  /**
   * Serialization version UID.
   *
   * <p><strong>Note:</strong> Should not be serialized directly, but needs to be Serializable to
   * save the parent state for {@link PersistentTempFileBucket}. Marked as {@code @Serial} to
   * indicate it is related to serialization but should not be serialized itself in typical
   * scenarios.
   */
  @Serial private static final long serialVersionUID = 1L;

  private static final Logger logger = LoggerFactory.getLogger(TempFileBucket.class);

  /**
   * Constructs a new {@link TempFileBucket}.
   *
   * <p>Uses the provided {@link FilenameGenerator} to create a path for the temporary file based on
   * the given ID. By default, files are marked for deletion on free (when {@link #dispose()} is
   * called or the object is garbage collected).
   *
   * @param id The unique identifier for this temporary file. Used by the {@link FilenameGenerator}.
   * @param generator The {@link FilenameGenerator} responsible for creating file paths.
   * @see #TempFileBucket(long, FilenameGenerator, boolean)
   */
  public TempFileBucket(long id, FilenameGenerator generator) {
    // deleteOnExit -> files get stuck in a big HashSet, whether
    // they are deleted. This grows without bound, it's a major memory
    // leak.
    this(id, generator, true);
    path = generator.getPath(id);
  }

  /**
   * Constructor for the {@link TempFileBucket} object. Subclasses can call this constructor.
   *
   * @param id The unique identifier for this temporary file.
   * @param generator The {@link FilenameGenerator} responsible for creating file paths.
   * @param deleteOnDispose Set to {@code true} if you want the bucket to be deleted when {@link
   *     #dispose()} is called, or {@code false} for shadow copies or persistent temporary files.
   * @see #TempFileBucket(long, FilenameGenerator)
   * @see #dispose()
   */
  protected TempFileBucket(long id, FilenameGenerator generator, boolean deleteOnDispose) {
    super();
    filenameId = id;
    this.generator = generator;
    this.deleteOnDispose = deleteOnDispose;
    path = generator.getPath(id);

    logger.debug("Initializing TempFileBucket({})", getPath());
  }

  /**
   * Default constructor for serialization purposes.
   *
   * <p><strong>Note:</strong> Should only be used during deserialization. The {@link
   * #deleteOnDispose} flag is set to {@code false} by default.
   */
  protected TempFileBucket() {
    // For serialization.
    deleteOnDispose = false;
  }

  /**
   * Constructor for deserialization from a {@link DataInputStream}.
   *
   * <p>Reads and validates the magic number, version, and other necessary data from the stream to
   * reconstruct a {@link TempFileBucket} object.
   *
   * @param dis The {@link DataInputStream} to read from.
   * @throws IOException If an I/O error occurs during reading.
   * @throws StorageFormatException If the data in the stream is not in the expected format (e.g.,
   *     bad magic number or version).
   */
  protected TempFileBucket(DataInputStream dis) throws IOException, StorageFormatException {
    super(dis);
    int version = dis.readInt();
    if (version != VERSION) {
      throw new StorageFormatException("Bad version");
    }
    filenameId = dis.readLong();
    if (filenameId == -1) {
      throw new StorageFormatException("Bad filename ID");
    }
    readOnly = dis.readBoolean();
    deleteOnDispose = dis.readBoolean();
    path = Path.of(dis.readUTF());
  }

  /**
   * {@inheritDoc}
   *
   * <p>Returns the {@link Path} associated with this {@link TempFileBucket}. If the path is not yet
   * initialized (transient after deserialization), it retrieves the path from the {@link
   * FilenameGenerator} using the {@link #filenameId}.
   *
   * @return The {@link Path} object representing the file location.
   */
  @Override
  public Path getPath() {
    if (path != null) {
      return path;
    }
    return generator.getPath(filenameId);
  }

  /**
   * {@inheritDoc}
   *
   * @return {@code true} if this {@link TempFileBucket} is read-only, {@code false} otherwise.
   */
  @Override
  public boolean isReadOnly() {
    return readOnly;
  }

  /**
   * {@inheritDoc}
   *
   * <p>Sets this {@link TempFileBucket} to read-only. Once set to read-only, this operation cannot be
   * reversed.
   */
  @Override
  public void setReadOnly() {
    readOnly = true;
  }

  /**
   * {@inheritDoc}
   *
   * <p>Creates a shallow, read-only shadow copy of this {@link TempFileBucket}. The shadow copy shares
   * the same underlying storage and {@link #filenameId}.
   *
   * <p><strong>Warning:</strong> If the original {@link TempFileBucket} is deleted or freed, the shadow
   * copy may become invalid and operations on it might fail.
   *
   * @return A new {@link TempFileBucket} instance representing the shadow copy, or {@code null} if shadow
   *     creation is not supported. In this implementation, it always returns a new {@link TempFileBucket}
   *     shadow instance.
   */
  @Override
  public RandomAccessible createShadow() {
    var ret = new TempFileBucket(filenameId, generator, false);
    ret.setReadOnly();
    if (!Files.exists(getPath())) {
      logger.error("File does not exist when creating shadow: {}", getPath());
    }
    return ret;
  }

  /**
   * {@inheritDoc}
   *
   * <p>Resumes the {@link TempFileBucket} after restart or deserialization. This method is called by the
   * framework to allow the {@link TempFileBucket} to perform any necessary initialization or recovery
   * steps.
   *
   * <p>For {@link TempFileBucket}, it checks if the file exists and potentially moves it to the correct
   * location using the {@link FilenameGenerator}.
   *
   * @param context The {@link ResumeContext} providing necessary runtime support for resuming.
   * @throws ResumeFailedException If the resumption process fails, indicating that the {@link
   *     TempFileBucket} cannot be properly initialized.
   * @throws UnsupportedOperationException if the bucket is not persistent and resumption is
   *     attempted.
   */
  @Override
  public final void onResume(ResumeContext context) throws ResumeFailedException {
    if (!persistent()) {
      throw new UnsupportedOperationException();
    }
    synchronized (this) {
      if (resumed) {
        return;
      }
      resumed = true;
    }
    super.onResume(context);
    innerResume(context);
  }

  /**
   * {@inheritDoc}
   *
   * <p>Stores the {@link TempFileBucket}'s state and metadata to the provided {@link DataOutputStream}
   * for persistence or recovery.
   *
   * <p>The stored data includes magic number, version, base class data, {@link #filenameId}, {@link
   * #readOnly} flag, {@link #deleteOnDispose} flag, and the file path as a UTF string.
   *
   * @param dos The {@link DataOutputStream} to write the state to.
   * @throws java.io.IOException If an I/O error occurs during writing.
   */
  @Override
  public void storeTo(DataOutputStream dos) throws IOException {
    dos.writeInt(magic());
    super.storeTo(dos);
    dos.writeInt(VERSION);
    dos.writeLong(filenameId);
    dos.writeBoolean(readOnly);
    dos.writeBoolean(deleteOnDispose);
    dos.writeUTF(path.toString());
  }

  /**
   * {@inheritDoc}
   *
   * @return A hash code value for this {@link TempFileBucket} object, based on {@link #deleteOnDispose},
   *     {@link #filenameId}, and {@link #readOnly} flags.
   */
  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + (deleteOnDispose ? 1231 : 1237);
    result = prime * result + Long.hashCode(filenameId);
    result = prime * result + (readOnly ? 1231 : 1237);
    return result;
  }

  /**
   * {@inheritDoc}
   *
   * <p>Compares this {@link TempFileBucket} to another object for equality. Two {@link TempFileBucket} objects
   * are considered equal if they are the same object or if they have the same class, {@link
   * #deleteOnDispose}, {@link #filenameId}, and {@link #readOnly} flags.
   *
   * @param obj The reference object with which to compare.
   * @return {@code true} if this object is the same as the {@code obj} argument; {@code false}
   *     otherwise.
   */
  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    TempFileBucket other = (TempFileBucket) obj;
    if (deleteOnDispose != other.deleteOnDispose) {
      return false;
    }
    if (filenameId != other.filenameId) {
      return false;
    }
    return readOnly == other.readOnly;
  }

  /**
   * {@inheritDoc}
   *
   * @return {@code false} because {@link TempFileBucket} does not enforce creation of a new file only. It
   *     is designed for temporary files which might be reused or overwritten.
   */
  @Override
  protected boolean createFileOnly() {
    return false;
  }

  /**
   * {@inheritDoc}
   *
   * @return The value of the {@link #deleteOnDispose} flag, indicating whether the file should be
   *     deleted when {@link #dispose()} is called.
   * @see #deleteOnDispose
   */
  @Override
  protected boolean deleteOnDispose() {
    return deleteOnDispose;
  }

  /**
   * {@inheritDoc}
   *
   * @return {@code false} because {@link TempFileBucket} manages its deletion lifecycle differently,
   *     typically through {@link #dispose()} or JVM restart cleanup, not relying on {@link
   *     File#deleteOnExit()}.
   * @see #deleteOnExit()
   */
  @Override
  protected boolean deleteOnExit() {
    // Temp files will be cleaned up on next restart.
    // File.deleteOnExit() is a hideous memory leak.
    // It should NOT be used for temp files.
    return false;
  }

  /**
   * Inner resume logic for subclasses to override and extend the resumption process.
   *
   * @param context The {@link ResumeContext} providing necessary runtime support for resuming.
   * @throws ResumeFailedException If the resumption process fails.
   * @see #onResume(ResumeContext)
   */
  protected void innerResume(ResumeContext context) throws ResumeFailedException {
    generator = context.getPersistentFg();
    if (path == null) {
      // Migrating from old temp file, possibly db4o era.
      path = generator.getPath(filenameId);
      checkExists(path);
    } else {
      // File must exist!
      if (!Files.exists(path)) {
        // Maybe moved after the last checkpoint?
        var newPath = generator.getPath(filenameId);
        if (Files.exists(newPath)) {
          path = newPath;
        }
        checkExists(path);
      }
      path = generator.maybeMove(path, filenameId);
    }
  }

  /**
   * Indicates whether this {@link TempFileBucket} is persistent across restarts.
   *
   * <p>For {@link TempFileBucket}, it always returns {@code false} as it is designed for temporary
   * storage. Subclasses like {@link PersistentTempFileBucket} may override this to return {@code
   * true}.
   *
   * @return {@code false} for {@link TempFileBucket}, indicating it is not persistent by default.
   */
  protected boolean persistent() {
    return false;
  }

  /**
   * {@inheritDoc}
   *
   * @return {@code true} because {@link TempFileBucket} is designed to work with existing temporary
   *     files, especially after restarts or deserialization where the file might already exist.
   */
  @Override
  protected boolean tempFileAlreadyExists() {
    return true;
  }

  /**
   * Returns the magic number specific to the subclass for file format identification during
   * persistence.
   *
   * <p><strong>Note:</strong> This method is intended to be overridden by subclasses to provide
   * their specific magic number. {@link TempFileBucket} itself does not define a specific magic number
   * and throws {@link UnsupportedOperationException}.
   *
   * @return The magic number as an integer.
   * @throws UnsupportedOperationException if the subclass does not implement this method.
   */
  protected int magic() {
    throw new UnsupportedOperationException();
  }

  /**
   * Checks if the file at the given {@link Path} exists. If not, it attempts to create the file.
   *
   * <p>This method is used during resumption ({@link #onResume(ResumeContext)}) to ensure the
   * temporary file exists, especially if it's expected to be persistent or needs to be recreated
   * after a restart.
   *
   * @param path The {@link Path} to the file to check and potentially create.
   * @throws ResumeFailedException If the file does not exist and cannot be created due to an {@link
   *     IOException}.
   * @see #onResume(ResumeContext)
   */
  private void checkExists(Path path) throws ResumeFailedException {
    // File must exist!
    try {
      if (!Files.exists(path)) {
        Files.createFile(path);
      }
    } catch (IOException e) {
      throw new ResumeFailedException(
          "Temp file " + path + " does not exist and cannot be created", e);
    }
  }

  @Serial
  private void writeObject(ObjectOutputStream out) throws IOException {
    out.defaultWriteObject();
    assert path != null;
    out.writeUTF(path.toString());
  }

  @Serial
  private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
    in.defaultReadObject();
    path = Paths.get(in.readUTF());
  }

  /**
   * Flag indicating whether to delete the underlying file when {@link #dispose()} is called.
   *
   * <p>If {@code true}, the file will be deleted when {@link #dispose()} is invoked. If {@code
   * false}, the file might be kept (e.g., for shadow copies or persistent temporary files).
   */
  private final boolean deleteOnDispose;

  /**
   * The {@link FilenameGenerator} used to generate file paths for this {@link TempFileBucket}.
   *
   * <p>This generator is responsible for creating consistent and potentially persistent file paths
   * based on the {@link #filenameId}.
   */
  protected transient FilenameGenerator generator;

  /**
   * Unique identifier for this temporary file.
   *
   * <p>Used by the {@link #generator} to create a unique file path. This ID is typically persistent
   * and used across restarts to locate the same temporary file.
   */
  long filenameId;

  /**
   * Flag indicating whether this {@link TempFileBucket} is read-only.
   *
   * <p>If {@code true}, write operations will be prevented. Set via {@link #setReadOnly()}.
   */
  private boolean readOnly;

  /**
   * The {@link Path} to the temporary file.
   *
   * <p>This path is managed by the {@link FilenameGenerator} and is where the actual file data is
   * stored.
   */
  private transient Path path;

  /**
   * Flag indicating whether the {@link TempFileBucket} has been resumed after restart or deserialization.
   *
   * <p>Used to ensure that resumption logic (e.g., in {@link #onResume(ResumeContext)}) is executed
   * only once.
   */
  private transient boolean resumed;
}
