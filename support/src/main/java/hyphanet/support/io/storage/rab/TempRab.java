package hyphanet.support.io.storage.rab;

import com.uber.nullaway.annotations.EnsuresNonNull;
import hyphanet.support.GlobalCleaner;
import hyphanet.support.io.ResumeContext;
import hyphanet.support.io.storage.TempStorage;
import hyphanet.support.io.storage.TempStorageRamTracker;
import hyphanet.support.io.storage.bucket.TempBucket;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.ref.Cleaner;
import java.lang.ref.WeakReference;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static hyphanet.support.io.storage.TempStorageManager.TRACE_STORAGE_LEAKS;

/**
 * A proxy class for {@link Rab} that allows switching the underlying {@link Rab} instance.
 *
 * <p>This class acts as an intermediary, forwarding read and write operations to an underlying
 * {@link Rab}. The key feature is its ability to replace the underlying buffer with a new one, for
 * instance, during data migration or buffer resizing, without changing the external reference to
 * the {@link TempRab} itself. This provides a level of indirection and flexibility, particularly
 * useful in scenarios where the backing storage might need to be dynamically altered.
 *
 * <p>Thread safety is ensured through a {@link ReadWriteLock}, protecting access to the underlying
 * buffer and related state variables.
 *
 * <p>Unlike a TempBucket, the size is fixed, so migrate only happens on the migration thread.
 */
public class TempRab implements Rab, TempStorage {

  private static final Logger logger = LoggerFactory.getLogger(TempRab.class);

  public TempRab(
      TempStorageRamTracker ramTracker,
      byte[] initialContents,
      int offset,
      int size,
      long creationTime,
      RabFactory migrateToFactory,
      boolean readOnly)
      throws IOException {
    this(
        ramTracker,
        new ByteArrayRab(initialContents, offset, size, readOnly),
        size,
        creationTime,
        migrateToFactory,
        null);
    hasMigrated = false;
  }

  public TempRab(
      TempStorageRamTracker ramTracker,
      Rab underlying,
      long creationTime,
      RabFactory migrateToFactory,
      boolean migrated,
      @Nullable TempBucket tempBucket)
      throws IOException {
    this(ramTracker, underlying, underlying.size(), creationTime, migrateToFactory, tempBucket);
    hasMigrated = hasFreedRam = migrated;
  }

  public TempRab(
      TempStorageRamTracker ramTracker, int size, long creationTime, RabFactory migrateToFactory)
      throws IOException {
    this(ramTracker, new ByteArrayRab(size), size, creationTime, migrateToFactory, null);
    hasMigrated = false;
  }

  /**
   * Constructs a {@link TempRab} with an initial underlying {@link Rab}.
   *
   * @param initialWrap The initial {@link Rab} to wrap. This becomes the initially active
   *     underlying storage.
   * @param size The logical size of this proxy buffer. This size might be smaller than the actual
   *     size of the {@code initialWrap}, effectively limiting the accessible region.
   * @throws IOException If the size of the {@code initialWrap} is less than the specified {@code
   *     size}, indicating an invalid initial configuration.
   */
  private TempRab(
      TempStorageRamTracker ramTracker,
      Rab initialWrap,
      long size,
      long creationTime,
      RabFactory migrateToFactory,
      @Nullable TempBucket tempBucket)
      throws IOException {
    this.ramTracker = ramTracker;
    this.underlying = initialWrap;

    this.size = size;

    this.creationTime = creationTime;
    this.migrateToFactory = migrateToFactory;
    this.tempBucket = tempBucket;

    if (underlying.size() < size) {
      throw new IOException("Underlying must be >= size given");
    }

    registerCleaner();

    this.ramTracker.takeRam(size);
    this.ramTracker.addToRamStorageQueue(getReference());
  }

  /**
   * {@inheritDoc}
   *
   * <p>Returns the logical size of this proxy buffer, as specified during construction. This size
   * might be smaller than the size of the underlying {@link Rab}, effectively representing a view
   * or a limited region of the underlying storage.
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
    lock.readLock().lock();
    try {
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
    lock.readLock().lock();
    try {
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
   * <p>Closes this proxy buffer and the underlying {@link Rab}.
   *
   * <p>This operation is idempotent and thread-safe. Subsequent operations after closing may throw
   * {@link IOException}.
   *
   * <p><b>Locking:</b> Acquires a write lock to ensure exclusive access during closing, preventing
   * concurrent operations from interfering.
   */
  @Override
  public void close() {
    lock.writeLock().lock();
    try {
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
   * Checks if the underlying {@link Rab} has been disposed.
   *
   * <p><b>Thread Safety:</b> Uses a read lock to ensure consistent access to the {@code underlying}
   * field.
   *
   * @return {@code true} if the underlying buffer is {@code null}, indicating it has been disposed;
   *     {@code false} otherwise.
   */
  public boolean hasBeenDisposed() {
    lock.readLock().lock();
    try {
      return underlying == null;
    } finally {
      lock.readLock().unlock();
    }
  }

  /**
   * {@inheritDoc}
   *
   * <p>Obtains a {@link RabLock} for this proxy buffer. This method manages locking at both the
   * proxy level and the underlying {@link Rab} level to ensure consistent state during lock
   * operations and migrations.
   *
   * @return A {@link RabLock} associated with this proxy buffer. Closing this lock will release the
   *     lock on the underlying buffer when the last proxy-level lock is closed.
   * @throws IOException If the buffer is already closed when attempting to acquire a lock.
   */
  @Override
  @EnsuresNonNull("underlyingLock")
  public RabLock lockOpen() throws IOException {
    lock.writeLock().lock();
    try {
      if (closed || underlying == null) {
        throw new IOException("Already closed");
      }
      lockOpenCount++;
      assert lockOpenCount != 1 || (underlyingLock == null);
      if (underlyingLock == null) {
        underlyingLock = underlying.lockOpen();
      }
      return new RabLock() {

        @Override
        protected void innerUnlock() {
          externalUnlock();
        }
      };
    } finally {
      lock.writeLock().unlock();
    }
  }

  @Override
  public void dispose() {
    if (!innerDispose()) {
      return;
    }
    logger.info("Disposed {}", this);
    if (tempBucket != null) {
      // Tell the TempBucket to prevent log spam. Don't call free().
      tempBucket.onDisposed();
    }
  }

  @Override
  public WeakReference<TempStorage> getReference() {
    return weakRef;
  }

  @Override
  public long creationTime() {
    return creationTime;
  }

  @Override
  public boolean migrateToDisk() throws IOException {
    synchronized (this) {
      if (hasMigrated) {
        return false;
      }
      hasMigrated = true;
    }
    migrate();
    return true;
  }

  public synchronized boolean hasMigrated() {
    return hasMigrated;
  }

  @Override
  public void onResume(ResumeContext context) {
    // Not persistent.
    throw new UnsupportedOperationException();
  }

  @Override
  public void storeTo(DataOutputStream dos) throws IOException {
    throw new UnsupportedOperationException();
  }

  /**
   * For unit tests only. Retrieves the currently underlying {@link Rab}.
   *
   * <p><b>Thread Safety:</b> Uses a read lock to ensure thread-safe access.
   *
   * @return The current underlying {@link Rab} instance.
   */
  Rab getUnderlying() {
    lock.readLock().lock();
    try {
      return underlying;
    } finally {
      lock.readLock().unlock();
    }
  }

  /**
   * Disposes the underlying {@link Rab} and sets the internal reference to {@code null}.
   *
   * <p><b>Thread Safety:</b> Acquires a write lock to ensure exclusive access during the freeing
   * process.
   *
   * @return {@code true} if the underlying buffer was successfully freed (or was already null);
   *     {@code false} if it was already freed.
   */
  protected boolean innerDispose() {
    lock.writeLock().lock();
    try {
      // Write lock as we're going to change the underlying pointer.
      closed = true; // Effectively ...
      if (underlying == null) {
        return false;
      }
      cleanable.clean();
    } finally {
      lock.writeLock().unlock();
    }
    afterDisposeUnderlying();
    return true;
  }

  /**
   * Called when an external {@link RabLock} obtained via {@link #lockOpen()} is closed.
   *
   * <p>Manages the count of open locks ({@code lockOpenCount}) and releases the lock on the
   * underlying {@link Rab} when the last proxy-level lock is closed.
   *
   * <p><b>Thread Safety:</b> Acquires a write lock to protect access to {@code lockOpenCount} and
   * {@code underlyingLock}.
   */
  protected void externalUnlock() {
    lock.writeLock().lock();
    try {
      lockOpenCount--;
      if (lockOpenCount == 0 && underlyingLock != null) {
        underlyingLock.unlock();
      }

    } finally {
      lock.writeLock().unlock();
    }
  }

  /**
   * Migrates the data from the current underlying {@link Rab} to a new one.
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
   *   <li><b>Creates Successor:</b> Calls the abstract method {@link #innerMigrate(Rab)} to create
   *       a new {@link Rab} which should contain the same data as the current underlying buffer.
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
   * @throws NullPointerException If {@link #innerMigrate(Rab)} returns {@code null}.
   */
  protected final void migrate() throws IOException {
    lock.writeLock().lock();
    try {
      if (closed) {
        return;
      }
      if (underlying == null) {
        throw new IOException("Already freed");
      }
      Rab successor = innerMigrate(underlying);
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
      if (lockOpenCount > 0 && underlyingLock != null) {
        underlyingLock.unlock();
      }

      cleanable.clean();
      underlying = successor;
      underlyingLock = newLock;
    } finally {
      lock.writeLock().unlock();
    }
    afterDisposeUnderlying();
  }

  protected Rab innerMigrate(Rab underlying) throws IOException {
    ByteArrayRab b = (ByteArrayRab) underlying;
    byte[] buf = b.getBuffer();
    return migrateToFactory.makeRab(buf, 0, (int) size(), b.isReadOnly());
  }

  protected void afterDisposeUnderlying() {
    // Called when the in-RAM storage has been freed.
    synchronized (this) {
      if (hasFreedRam) {
        return;
      }
      hasFreedRam = true;
    }
  }

  @EnsuresNonNull("cleanable")
  protected void registerCleaner() {
    cleanable =
        GlobalCleaner.getInstance()
            .register(
                this,
                new CleaningAction(
                    getReference(), getUnderlying(), tempBucket != null, ramTracker, size));
  }

  static class CleaningAction implements Runnable {
    CleaningAction(
        WeakReference<TempStorage> thisRef,
        Rab underlyingRab,
        boolean hasTempBucket,
        TempStorageRamTracker ramTracker,
        long size) {
      this.thisRef = thisRef;
      this.underlyingRab = underlyingRab;
      this.hasTempBucket = hasTempBucket;
      this.ramTracker = ramTracker;
      this.size = size;
    }

    @Override
    public void run() {
      if (hasTempBucket) {
        return;
      }
      if (underlyingRab != null) {
        String msg = "TempRandomAccessBuffer not freed, size={} : {}";
        if (TRACE_STORAGE_LEAKS) {
          logger.error(msg, size, this, new Throwable());
        } else {
          logger.error(msg, size, this);
        }
        underlyingRab.dispose();

        synchronized (ramTracker) {
          if (ramTracker.ramStorageRefInQueue(thisRef)) {
            ramTracker.freeRam(size);
            ramTracker.removeFromRamStorageQueue(thisRef);
          }
        }
      }
      logger.info("Cleaner run.");
    }

    private final WeakReference<TempStorage> thisRef;
    private final Rab underlyingRab;
    private final boolean hasTempBucket;
    private final TempStorageRamTracker ramTracker;
    private final long size;
  }

  protected final RabFactory migrateToFactory;
  protected final long creationTime;

  /**
   * Kept in RAM so that finalizer is called on the TempBucket when *both* the
   * TempRandomAccessBuffer *and* the TempBucket are no longer reachable, in which case we will free
   * from the TempBucket. If this is null, then the TempRAB can free in finalizer.
   */
  protected final @Nullable TempBucket tempBucket;

  private final TempStorageRamTracker ramTracker;

  /**
   * The logical size of this proxy buffer.
   *
   * <p>This value is set during construction and represents the accessible size of the buffer,
   * which may be less than the actual size of the underlying {@link Rab}.
   */
  private final long size;

  /**
   * Read/write lock to protect concurrent access to the {@code underlying} {@link Rab}, {@code
   * lockOpenCount}, and {@code closed} flag.
   *
   * <p>A read lock is acquired for read and write operations ({@link #pread(long, byte[], int,
   * int)}, {@link #pwrite(long, byte[], int, int)}, {@link #hasBeenDisposed()}, {@link
   * #getUnderlying()}). A write lock is acquired for operations that modify the internal state,
   * such as {@link #close()}, {@link #dispose()}, {@link #innerDispose()}, {@link
   * #externalUnlock()}, {@link #migrate()}, and {@link #lockOpen()}.
   */
  private final ReadWriteLock lock = new ReentrantReadWriteLock();

  private final WeakReference<TempStorage> weakRef = new WeakReference<>(this);
  protected boolean hasMigrated = false;

  /** If false, there is in-memory storage that needs to be freed. */
  protected boolean hasFreedRam = false;

  /**
   * The underlying {@link Rab} instance to which operations are delegated.
   *
   * <p>This reference can be switched during migration, allowing the proxy to point to a new
   * underlying storage. It can be {@code null} if the buffer has been disposed.
   */
  private Rab underlying;

  /**
   * Counter for the number of currently active {@link RabLock}s obtained via {@link #lockOpen()}.
   *
   * <p>This is used to manage the lifecycle of the lock on the underlying {@link Rab}. The
   * underlying lock is acquired when {@code lockOpenCount} transitions from 0 to 1 and released
   * when it transitions from 1 to 0.
   */
  private int lockOpenCount;

  /**
   * The {@link RabLock} obtained on the underlying {@link Rab} when the first {@link RabLock} is
   * requested on this proxy.
   *
   * <p>This lock is held as long as there is at least one active {@link RabLock} on this proxy
   * (i.e., {@code lockOpenCount > 0}). It is used to ensure that the underlying buffer remains
   * locked during migrations when external locks are held.
   */
  private transient @Nullable RabLock underlyingLock;

  /**
   * Flag indicating whether this proxy buffer has been closed.
   *
   * <p>Once closed, further operations (except {@link #close()} and {@link #dispose()}) may throw
   * {@link IOException}.
   */
  private boolean closed;

  private Cleaner.Cleanable cleanable;

  // Default hashCode() and equals() i.e. comparison by identity are correct for this type.

}
