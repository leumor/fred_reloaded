package hyphanet.support.io.storage.rab;

import hyphanet.crypt.key.MasterSecret;
import hyphanet.support.io.FilenameGenerator;
import hyphanet.support.io.PersistentFileTracker;
import hyphanet.support.io.ResumeContext;
import hyphanet.support.io.ResumeFailedException;
import hyphanet.support.io.storage.Storage;
import hyphanet.support.io.storage.bucket.BucketTools;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A thread-safe interface for RAB (random access buffer) operations with a fixed length. It
 * provides locking mechanisms and persistence capabilities.
 *
 * <p>This interface provides basic random access operations for reading and writing data at
 * specific positions in a file-like structure. All implementations must guarantee thread-safety
 * either through serialization of reads or native parallel read support.
 *
 * <p>This interface allows temporary locking of the underlying resource to prevent premature
 * closure of file descriptors, especially in pooled scenarios. While locking doesn't provide
 * explicit concurrency guarantees, implementations must ensure thread-safety through appropriate
 * synchronization mechanisms. Implementations must register with {@link
 * BucketTools#restoreRabFrom(DataInputStream, FilenameGenerator, PersistentFileTracker,
 * MasterSecret)}.
 *
 * @author toad
 * @since 1.0
 */
public interface Rab extends Storage {

  /** Abstract base class for implementing RAB locking mechanisms. */
  abstract class RabLock {

    /** Creates a new lock instance in a locked state. */
    protected RabLock() {
      locked = true;
    }

    /**
     * Unlocks the RAB lock.
     *
     * @throws IllegalStateException if the lock is already unlocked
     */
    public final void unlock() {
      lock.lock();
      try {
        if (!locked) {
          throw new IllegalStateException("Already unlocked");
        }
        locked = false;
      } finally {
        lock.unlock();
      }
      innerUnlock();
    }

    /**
     * Template method for implementing specific unlock behavior. Implementations should handle
     * resource cleanup and state management.
     */
    protected abstract void innerUnlock();

    private final ReentrantLock lock = new ReentrantLock();
    private boolean locked;
  }

  /**
   * Returns the total size of this buffer in bytes.
   *
   * @return the size of the buffer in bytes
   */
  long size();

  /**
   * Reads a block of data from a specific location in the buffer.
   *
   * <p>This method guarantees to read the entire requested range or throw an exception, similar to
   * {@link java.io.DataInputStream#readFully(byte[], int, int)}. The operation will fail if the
   * buffer is closed.
   *
   * @param fileOffset the offset within the buffer to read from
   * @param buf the destination array to write the data into
   * @param bufOffset the starting position in the destination array
   * @param length the number of bytes to read
   * @throws IOException if the required number of bytes cannot be read
   * @throws IllegalArgumentException if fileOffset is negative
   * @throws IndexOutOfBoundsException if the specified offsets or length would cause buffer
   *     overflow
   */
  void pread(long fileOffset, byte[] buf, int bufOffset, int length) throws IOException;

  /**
   * Writes a block of data to a specific location in the buffer.
   *
   * <p>This method writes the specified range of bytes from the source array to the buffer at the
   * given offset.
   *
   * @param fileOffset the offset within the buffer to write to
   * @param buf the source array containing the data to write
   * @param bufOffset the starting position in the source array
   * @param length the number of bytes to write
   * @throws IOException if the write operation fails
   * @throws IllegalArgumentException if fileOffset is negative
   * @throws IndexOutOfBoundsException if the specified offsets or length would cause buffer
   *     overflow
   */
  void pwrite(long fileOffset, byte[] buf, int bufOffset, int length) throws IOException;

  /**
   * Closes this buffer and releases any system resources associated with it.
   *
   * <p>Once closed, further read or write operations will throw an {@link IOException}.
   */
  @Override
  void close();

  /**
   * Releases the underlying resources and securely deletes data associated with this buffer.
   *
   * <p>This method may perform no operation in some implementations. Callers should ensure that the
   * object becomes eligible for garbage collection after calling this method.
   */
  boolean dispose();

  /**
   * Acquires a lock on the RAB to keep it open.
   *
   * <p><strong>Note:</strong> This method may block until a slot becomes available, which could
   * potentially lead to deadlocks if not used carefully.
   *
   * @return a {@link RabLock} instance representing the acquired lock
   * @throws IOException if the lock cannot be acquired due to I/O errors
   */
  RabLock lockOpen() throws IOException;

  /**
   * Handles post-serialization resume operations.
   *
   * <p>This method is called after deserialization to perform necessary initialization steps, such
   * as registering with temporary file management systems.
   *
   * @param context the resume context containing necessary state information
   * @throws ResumeFailedException if the resume operation cannot be completed
   * @see ResumeContext
   */
  void onResume(ResumeContext context) throws ResumeFailedException;

  /**
   * Serializes the RAB state to the provided output stream.
   *
   * <p>Implementations must write a unique magic value for proper reconstruction during
   * deserialization, and add a clause to {@link BucketTools#restoreRabFrom(DataInputStream,
   * FilenameGenerator, PersistentFileTracker, MasterSecret)}. The serialization format should be
   * versioned if necessary.
   *
   * @param dos the output stream to write the state to
   * @throws IOException if the serialization fails
   * @throws UnsupportedOperationException if serialization is not supported
   */
  void storeTo(DataOutputStream dos) throws IOException;

  /**
   * Compares this RAB with another object for equality.
   *
   * <p>Implementations must override this method to properly compare RABs, especially during
   * splitfile insert resume operations.
   *
   * @param o the object to compare with
   * @return {@code true} if the objects represent the same stored content
   */
  @Override
  boolean equals(Object o);

  /**
   * Generates a hash code consistent with {@link #equals(Object)}.
   *
   * @return a hash code value for this RAF
   */
  @Override
  int hashCode();
}
