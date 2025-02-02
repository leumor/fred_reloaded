package hyphanet.support.io.storage.randomaccessbuffer;

import java.io.IOException;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * A proxy class for {@link RandomAccessBuffer} that allows switching the underlying {@link
 * RandomAccessBuffer} instance.
 *
 * <p>This class acts as an intermediary, forwarding read and write operations to an underlying
 * {@link RandomAccessBuffer}. The key feature is its ability to replace the underlying buffer with
 * a new one, for instance, during data migration or buffer resizing, without changing the external
 * reference to the {@link SwitchableProxy} itself. This provides a level of indirection and
 * flexibility, particularly useful in scenarios where the backing storage might need to be
 * dynamically altered.
 *
 * <p>Thread safety is ensured through a {@link ReadWriteLock}, protecting access to the underlying
 * buffer and related state variables.
 */
public abstract class SwitchableProxy implements RandomAccessBuffer {

  /**
   * Constructs a {@link SwitchableProxy} with an initial underlying {@link RandomAccessBuffer}.
   *
   * @param initialWrap The initial {@link RandomAccessBuffer} to wrap. This becomes the initially
   *     active underlying storage.
   * @param size The logical size of this proxy buffer. This size might be smaller than the actual
   *     size of the {@code initialWrap}, effectively limiting the accessible region.
   * @throws IOException If the size of the {@code initialWrap} is less than the specified {@code
   *     size}, indicating an invalid initial configuration.
   */
  protected SwitchableProxy(RandomAccessBuffer initialWrap, long size) throws IOException {
    this.underlying = initialWrap;
    this.size = size;
    if (underlying.size() < size) {
      throw new IOException("Underlying must be >= size given");
    }
  }

  /**
   * {@inheritDoc}
   *
   * <p>Returns the logical size of this proxy buffer, as specified during construction. This size
   * might be smaller than the size of the underlying {@link RandomAccessBuffer}, effectively
   * representing a view or a limited region of the underlying storage.
   *
   * @return The logical size of this proxy buffer in bytes.
   */
  @Override
  public long size() {
    return size;
  }

  /**
   * {@inheritDoc}
   *
   * <p>Reads a sequence of bytes from this buffer starting at the given file offset into the
   * provided byte buffer.
   *
   * <p>This operation is performed atomically with respect to migrations and freeing of the
   * underlying buffer due to the read lock acquisition.
   *
   * @throws IOException If an I/O error occurs, such as reading past the end of the buffer, or if
   *     the buffer is already closed.
   * @throws IllegalArgumentException If {@code fileOffset} is negative.
   */
  @Override
  public void pread(long fileOffset, byte[] buf, int bufOffset, int length) throws IOException {
    if (fileOffset < 0) {
      throw new IllegalArgumentException();
    }
    if (fileOffset + length > size) {
      throw new IOException("Tried to read past end of file");
    }
    try {
      lock.readLock().lock();
      if (underlying == null || closed) {
        throw new IOException("Already closed");
      }
      underlying.pread(fileOffset, buf, bufOffset, length);
    } finally {
      lock.readLock().unlock();
    }
  }

  /**
   * {@inheritDoc}
   *
   * <p>Writes a sequence of bytes to this buffer starting at the given file offset from the
   * provided byte buffer.
   *
   * <p>This operation is performed atomically with respect to migrations and freeing of the
   * underlying buffer due to the read lock acquisition.
   *
   * @throws IOException If an I/O error occurs, such as writing past the end of the buffer, or if
   *     the buffer is already closed.
   * @throws IllegalArgumentException If {@code fileOffset} is negative.
   */
  @Override
  public void pwrite(long fileOffset, byte[] buf, int bufOffset, int length) throws IOException {
    if (fileOffset < 0) {
      throw new IllegalArgumentException();
    }
    if (fileOffset + length > size) {
      throw new IOException("Tried to write past end of file");
    }
    try {
      lock.readLock().lock();
      if (underlying == null || closed) {
        throw new IOException("Already closed");
      }
      underlying.pwrite(fileOffset, buf, bufOffset, length);
    } finally {
      lock.readLock().unlock();
    }
  }

  /**
   * {@inheritDoc}
   *
   * <p>Closes this proxy buffer and the underlying {@link RandomAccessBuffer}.
   *
   * <p>This operation is idempotent and thread-safe. Subsequent operations after closing may throw
   * {@link IOException}.
   *
   * <p><b>Locking:</b> Acquires a write lock to ensure exclusive access during closing, preventing
   * concurrent operations from interfering.
   */
  @Override
  public void close() {
    try {
      lock.writeLock().lock();
      if (underlying == null) {
        return;
      }
      if (closed) {
        return;
      }
      closed = true;
      underlying.close();
    } finally {
      lock.writeLock().unlock();
    }
  }

  /**
   * {@inheritDoc}
   *
   * <p>Disposes of this proxy buffer, freeing any associated resources. This operation typically
   * delegates to {@link #innerDispose()}.
   */
  @Override
  public void dispose() {
    innerDispose();
  }

  /**
   * Checks if the underlying {@link RandomAccessBuffer} has been disposed.
   *
   * <p><b>Thread Safety:</b> Uses a read lock to ensure consistent access to the {@code underlying}
   * field.
   *
   * @return {@code true} if the underlying buffer is {@code null}, indicating it has been disposed;
   *     {@code false} otherwise.
   */
  public boolean hasBeenDisposed() {
    try {
      lock.readLock().lock();
      return underlying == null;
    } finally {
      lock.readLock().unlock();
    }
  }

  /**
   * {@inheritDoc}
   *
   * <p>Obtains a {@link RabLock} for this proxy buffer. This method manages locking at both the
   * proxy level and the underlying {@link RandomAccessBuffer} level to ensure consistent state
   * during lock operations and migrations.
   *
   * @return A {@link RabLock} associated with this proxy buffer. Closing this lock will release the
   *     lock on the underlying buffer when the last proxy-level lock is closed.
   * @throws IOException If the buffer is already closed when attempting to acquire a lock.
   */
  @Override
  public RabLock lockOpen() throws IOException {
    try {
      lock.writeLock().lock();
      if (closed || underlying == null) {
        throw new IOException("Already closed");
      }
      RabLock rabLock =
          new RabLock() {

            @Override
            protected void innerUnlock() {
              externalUnlock();
            }
          };
      lockOpenCount++;
      if (lockOpenCount == 1) {
        assert (underlyingLock == null);
        underlyingLock = underlying.lockOpen();
      }
      return rabLock;
    } finally {
      lock.writeLock().unlock();
    }
  }

  /**
   * For unit tests only. Retrieves the currently underlying {@link RandomAccessBuffer}.
   *
   * <p><b>Thread Safety:</b> Uses a read lock to ensure thread-safe access.
   *
   * @return The current underlying {@link RandomAccessBuffer} instance.
   */
  RandomAccessBuffer getUnderlying() {
    try {
      lock.readLock().lock();
      return underlying;
    } finally {
      lock.readLock().unlock();
    }
  }

  /**
   * Disposes the underlying {@link RandomAccessBuffer} and sets the internal reference to {@code
   * null}.
   *
   * <p><b>Thread Safety:</b> Acquires a write lock to ensure exclusive access during the freeing
   * process.
   *
   * @return {@code true} if the underlying buffer was successfully freed (or was already null);
   *     {@code false} if it was already freed.
   */
  protected boolean innerDispose() {
    try {
      // Write lock as we're going to change the underlying pointer.
      lock.writeLock().lock();
      closed = true; // Effectively ...
      if (underlying == null) {
        return false;
      }
      underlying.dispose();
      underlying = null;
    } finally {
      lock.writeLock().unlock();
    }
    afterDisposeUnderlying();
    return true;
  }

  /**
   * Called after disposing the underlying storage in {@link #innerDispose()} or during migration in
   * {@link #migrate()}.
   *
   * <p>Subclasses can override this method to perform any cleanup or post-processing actions
   * required after the underlying storage is freed. The default implementation does nothing.
   */
  protected void afterDisposeUnderlying() {
    // Do nothing.
  }

  /**
   * Called when an external {@link RabLock} obtained via {@link #lockOpen()} is closed.
   *
   * <p>Manages the count of open locks ({@code lockOpenCount}) and releases the lock on the
   * underlying {@link RandomAccessBuffer} when the last proxy-level lock is closed.
   *
   * <p><b>Thread Safety:</b> Acquires a write lock to protect access to {@code lockOpenCount} and
   * {@code underlyingLock}.
   */
  protected void externalUnlock() {
    try {
      lock.writeLock().lock();
      lockOpenCount--;
      if (lockOpenCount == 0) {
        underlyingLock.unlock();
        underlyingLock = null;
      }
    } finally {
      lock.writeLock().unlock();
    }
  }

  /**
   * Migrates the data from the current underlying {@link RandomAccessBuffer} to a new one.
   *
   * <p>This is a core operation in {@code SwitchableProxy}, allowing the replacement of the
   * underlying storage.
   *
   * <p><b>Migration Process:</b>
   *
   * <ol>
   *   <li><b>Locking:</b> Acquires a write lock to ensure exclusive access during migration.
   *   <li><b>Checks State:</b> Verifies that the proxy is not closed and the underlying buffer is
   *       not null.
   *   <li><b>Creates Successor:</b> Calls the abstract method {@link
   *       #innerMigrate(RandomAccessBuffer)} to create a new {@link RandomAccessBuffer} which
   *       should contain the same data as the current underlying buffer.
   *   <li><b>Lock Management:</b> If there are active {@link RabLock}s ({@code lockOpenCount > 0}):
   *       <ul>
   *         <li>Attempts to acquire a new lock on the successor buffer using {@code
   *             successor.lockOpen()}. If this fails, the successor is closed and disposed of, and
   *             an {@link IOException} is thrown.
   *         <li>Releases the lock on the old underlying buffer ({@code underlyingLock.unlock()}).
   *       </ul>
   *   <li><b>Switch Underlying:</b> Closes and disposes of the old underlying buffer, and then
   *       replaces it with the new successor buffer, updating {@code underlying} and {@code
   *       underlyingLock}.
   *   <li><b>Post-Migration Hook:</b> Calls {@link #afterDisposeUnderlying()} to allow subclasses
   *       to perform actions after the underlying buffer has been switched.
   * </ol>
   *
   * @throws IOException If an I/O error occurs during migration, such as if creating the new
   *     underlying buffer or locking it fails.
   * @throws NullPointerException If {@link #innerMigrate(RandomAccessBuffer)} returns {@code null}.
   */
  protected final void migrate() throws IOException {
    try {
      lock.writeLock().lock();
      if (closed) {
        return;
      }
      if (underlying == null) {
        throw new IOException("Already freed");
      }
      RandomAccessBuffer successor = innerMigrate(underlying);
      if (successor == null) {
        throw new NullPointerException();
      }
      RabLock newLock = null;
      if (lockOpenCount > 0) {
        try {
          newLock = successor.lockOpen();
        } catch (IOException e) {
          successor.close();
          successor.dispose();
          throw e;
        }
      }
      if (lockOpenCount > 0) {
        underlyingLock.unlock();
      }
      underlying.close();
      underlying.dispose();
      underlying = successor;
      underlyingLock = newLock;
    } finally {
      lock.writeLock().unlock();
    }
    afterDisposeUnderlying();
  }

  /**
   * Abstract method to be implemented by subclasses to create a new {@link RandomAccessBuffer}
   * containing the same data as the current {@code underlying} buffer.
   *
   * <p>This method is called by {@link #migrate()} to obtain the replacement underlying buffer.
   * Subclasses are responsible for implementing the logic to copy or transfer the data from the old
   * buffer to the new one.
   *
   * @param underlying The current underlying {@link RandomAccessBuffer} whose data needs to be
   *     migrated to a new buffer.
   * @return A new {@link RandomAccessBuffer} containing the data from the {@code underlying}
   *     buffer. Must not return {@code null}.
   * @throws IOException If the migration process fails.
   */
  protected abstract RandomAccessBuffer innerMigrate(RandomAccessBuffer underlying)
      throws IOException;

  /**
   * The logical size of this proxy buffer.
   *
   * <p>This value is set during construction and represents the accessible size of the buffer,
   * which may be less than the actual size of the underlying {@link RandomAccessBuffer}.
   */
  private final long size;

  /**
   * Read/write lock to protect concurrent access to the {@code underlying} {@link
   * RandomAccessBuffer}, {@code lockOpenCount}, and {@code closed} flag.
   *
   * <p>A read lock is acquired for read and write operations ({@link #pread(long, byte[], int,
   * int)}, {@link #pwrite(long, byte[], int, int)}, {@link #hasBeenDisposed()}, {@link
   * #getUnderlying()}). A write lock is acquired for operations that modify the internal state,
   * such as {@link #close()}, {@link #dispose()}, {@link #innerDispose()}, {@link
   * #externalUnlock()}, {@link #migrate()}, and {@link #lockOpen()}.
   */
  private final ReadWriteLock lock = new ReentrantReadWriteLock();

  /**
   * The underlying {@link RandomAccessBuffer} instance to which operations are delegated.
   *
   * <p>This reference can be switched during migration, allowing the proxy to point to a new
   * underlying storage. It can be {@code null} if the buffer has been disposed.
   */
  private RandomAccessBuffer underlying;

  /**
   * Counter for the number of currently active {@link RabLock}s obtained via {@link #lockOpen()}.
   *
   * <p>This is used to manage the lifecycle of the lock on the underlying {@link
   * RandomAccessBuffer}. The underlying lock is acquired when {@code lockOpenCount} transitions
   * from 0 to 1 and released when it transitions from 1 to 0.
   */
  private int lockOpenCount;

  /**
   * The {@link RabLock} obtained on the underlying {@link RandomAccessBuffer} when the first {@link
   * RabLock} is requested on this proxy.
   *
   * <p>This lock is held as long as there is at least one active {@link RabLock} on this proxy
   * (i.e., {@code lockOpenCount > 0}). It is used to ensure that the underlying buffer remains
   * locked during migrations when external locks are held.
   */
  private RabLock underlyingLock;

  /**
   * Flag indicating whether this proxy buffer has been closed.
   *
   * <p>Once closed, further operations (except {@link #close()} and {@link #dispose()}) may throw
   * {@link IOException}.
   */
  private boolean closed;

  // Default hashCode() and equals() i.e. comparison by identity are correct for this type.

}
