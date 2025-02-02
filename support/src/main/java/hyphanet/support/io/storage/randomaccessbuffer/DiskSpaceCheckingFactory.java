package hyphanet.support.io.storage.randomaccessbuffer;

import hyphanet.support.io.DiskSpaceChecker;
import hyphanet.support.io.stream.InsufficientDiskSpaceException;
import hyphanet.support.io.util.FilePath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A Random Access Buffer {@link Factory} implementation that wraps another {@link Factory} and
 * performs disk space checks before creating {@link RandomAccessBuffer} instances.
 *
 * <p>This factory ensures that there is sufficient disk space available before allowing the
 * creation of new buffers, preventing potential {@link InsufficientDiskSpaceException} errors
 * during write operations. It uses a {@link DiskSpaceChecker} interface (implemented by itself) to
 * perform these checks.
 *
 * <p>Thread safety is ensured by using a {@link ReentrantLock} to synchronize disk space checks and
 * buffer creations, preventing race conditions and ensuring accurate free space estimations.
 */
public class DiskSpaceCheckingFactory implements Factory, DiskSpaceChecker {

  /**
   * Global lock used to synchronize disk space checks and prevent fragmentation.
   *
   * <p>This lock is acquired before any disk space check or {@link RandomAccessBuffer} creation to
   * ensure that operations are performed atomically and that free space estimations are accurate.
   *
   * <p><b>LOCKING:</b> This lock is used to synchronize operations across the entire factory.
   *
   * <p>TODO: Ideally, locking would be per-filesystem to improve concurrency, but current
   * implementation uses a single global lock for simplicity and correctness.
   *
   * @see ReentrantLock
   */
  private static final Lock lock = new ReentrantLock(true);

  private static final Logger logger = LoggerFactory.getLogger(DiskSpaceCheckingFactory.class);

  /**
   * Constructs a {@link DiskSpaceCheckingFactory}.
   *
   * @param underlying The underlying {@link Factory} to delegate buffer creation to. Must not be
   *     {@code null}.
   * @param dir The directory to check for disk space. Must not be {@code null}.
   * @param minDiskSpace The minimum disk space in bytes that must be available. Must be
   *     non-negative.
   * @throws NullPointerException if {@code underlying} or {@code dir} is {@code null}.
   */
  public DiskSpaceCheckingFactory(Factory underlying, Path dir, long minDiskSpace) {
    if (minDiskSpace < 0) {
      throw new IllegalArgumentException("Minimum disk space must be non-negative");
    }

    this.underlying = underlying;
    this.dir = dir;
    this.minDiskSpace = minDiskSpace;
  }

  /**
   * Sets the minimum disk space required.
   *
   * @param min The new minimum disk space in bytes. Must be non-negative.
   * @throws IllegalArgumentException if {@code min} is negative.
   */
  public void setMinDiskSpace(long min) {
    if (min < 0) {
      throw new IllegalArgumentException("Minimum disk space must be non-negative");
    }
    minDiskSpace = min;
  }

  /**
   * {@inheritDoc}
   *
   * @throws InsufficientDiskSpaceException if there is not enough disk space available.
   * @implSpec This implementation first checks if there is enough disk space available in the
   *     directory specified during construction. If sufficient space is available ({@code size} +
   *     {@link #minDiskSpace}), it delegates the {@link RandomAccessBuffer} creation to the
   *     underlying {@link Factory}. The disk space check and buffer creation are performed under a
   *     global lock to ensure atomicity.
   * @see #lock
   * @see #getUsableSpace(Path)
   */
  @Override
  public RandomAccessBuffer makeRab(long size) throws IOException {
    lock.lock();
    try {
      if (getUsableSpace(dir) > size + minDiskSpace) {
        return underlying.makeRab(size);
      }
      throw new InsufficientDiskSpaceException();
    } finally {
      lock.unlock();
    }
  }

  /**
   * {@inheritDoc}
   *
   * @throws InsufficientDiskSpaceException if there is not enough disk space available.
   * @implSpec This implementation first checks if there is enough disk space available in the
   *     directory specified during construction. If sufficient space is available ({@code size} +
   *     {@link #minDiskSpace}), it delegates the {@link RandomAccessBuffer} creation to the
   *     underlying {@link Factory}. The disk space check and buffer creation are performed under a
   *     global lock to ensure atomicity.
   * @see #lock
   * @see #getUsableSpace(Path)
   */
  @Override
  public RandomAccessBuffer makeRab(byte[] initialContents, int offset, int size, boolean readOnly)
      throws IOException {
    lock.lock();
    try {
      if (getUsableSpace(dir) > size + minDiskSpace) {
        return underlying.makeRab(initialContents, offset, size, readOnly);
      }
      throw new InsufficientDiskSpaceException();
    } finally {
      lock.unlock();
    }
  }

  /**
   * {@inheritDoc}
   *
   * @return A string representation of this factory, including the string representation of the
   *     underlying factory.
   */
  @Override
  public String toString() {
    return "%s:%s".formatted(super.toString(), underlying);
  }

  /**
   * Creates a new {@link PooledFile} for a specified file path.
   *
   * <p>The file at the given path must exist and be 0 bytes long prior to calling this method. If
   * the file does not meet these criteria or if a {@link PooledFile} cannot be created due to
   * insufficient disk space or other I/O errors, the file will be deleted.
   *
   * @param path The path to the file for which to create a {@link PooledFile}. Must not be {@code
   *     null}.
   * @param size The expected size of the {@link PooledFile} in bytes.
   * @param random A {@link Random} instance (not used in this method, but kept for interface
   *     compatibility).
   * @return A new {@link PooledFile} instance.
   * @throws InsufficientDiskSpaceException If there is not enough disk space available.
   * @throws IOException If the file does not exist, is not zero bytes long, or if some other disk
   *     I/O error occurs.
   * @see #lock
   * @see #getUsableSpace(Path)
   */
  public PooledFile createFileRab(Path path, long size, Random random) throws IOException {
    lock.lock();
    PooledFile ret = null;
    try {
      if (!Files.exists(path)) {
        throw new IOException("File does not exist");
      }
      if (Files.size(path) != 0) {
        throw new IOException("File is wrong length");
      }
      // FIXME ideally we would have separate locks for each filesystem ...
      if (getUsableSpace(dir) > size + minDiskSpace) {
        ret = new PooledFile(path, false, size, -1, true);
        return ret;
      }
      throw new InsufficientDiskSpaceException();
    } finally {
      if (ret == null) {
        Files.delete(path);
      }
      lock.unlock();
    }
  }

  /**
   * {@inheritDoc}
   *
   * @implSpec This method checks if the given {@code path} is a child of the configured directory
   *     {@link #dir}. If it is not a child, it logs an error and returns {@code true} (disk space
   *     check is bypassed). Otherwise, it acquires the global {@link #lock}, retrieves the usable
   *     disk space using {@link #getUsableSpace(Path)}, and checks if there is enough space
   *     available considering {@code toWrite}, {@code bufferSize}, and {@link #minDiskSpace}. The
   *     result of the disk space check is then returned. The global lock is always released in a
   *     {@code finally} block.
   * @see #lock
   * @see #getUsableSpace(Path)
   * @see FilePath#isParent(Path, Path)
   */
  @Override
  public boolean checkDiskSpace(Path path, int toWrite, int bufferSize) {
    if (!FilePath.isParent(dir, path)) {
      logger.error("Not checking disk space because {} is not child of {}", path, dir);
      return true;
    }
    lock.lock();
    try {
      var usableSpace = getUsableSpace(path);
      if (usableSpace == -1) {
        return false;
      }
      return usableSpace - (toWrite + bufferSize) >= minDiskSpace;
    } finally {
      lock.unlock();
    }
  }

  /**
   * Gets the usable disk space for the filesystem containing the given path.
   *
   * @param path The path to the file or directory for which to check disk space. Must not be {@code
   *     null}.
   * @return The usable disk space in bytes, or -1 if an {@link IOException} occurs while retrieving
   *     disk space information.
   * @see Files#getFileStore(Path)
   * @see java.nio.file.FileStore#getUsableSpace()
   */
  private static long getUsableSpace(Path path) {
    try {
      return Files.getFileStore(path).getUsableSpace();
    } catch (IOException e) {
      logger.error("Unable to check disk space for {}", path, e);
      return -1;
    }
  }

  /**
   * The underlying {@link Factory} used to create {@link RandomAccessBuffer} instances after disk
   * space checks. This factory delegates the actual buffer creation to the wrapped factory if
   * sufficient disk space is available.
   */
  private final Factory underlying;

  /**
   * The directory on which disk space checks are performed. This directory is used to determine the
   * filesystem and check for available space.
   */
  private final Path dir;

  /**
   * The minimum disk space required to be available in bytes. If the available disk space falls
   * below this threshold, {@link InsufficientDiskSpaceException} will be thrown.
   */
  private volatile long minDiskSpace;
}
